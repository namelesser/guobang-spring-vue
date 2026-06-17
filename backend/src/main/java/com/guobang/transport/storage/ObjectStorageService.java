package com.guobang.transport.storage;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ObjectStorageService {
    private static final String DEFAULT_BUCKET = "logistics-system";
    private static final String DEFAULT_ENDPOINT = "https://s3.cn-north-1.jdcloud-oss.com";
    private static final String DEFAULT_REGION = "cn-north-1";
    private static final String DEFAULT_CREDENTIAL_SERVICE = "jd-object-storage";

    private final Environment env;
    private final JdbcTemplate jdbc;
    private S3Client cachedClient;
    private String cachedSignature;

    public ObjectStorageService(Environment env, JdbcTemplate jdbc) {
        this.env = env;
        this.jdbc = jdbc;
    }

    public boolean enabled() {
        String value = env("OBJECT_STORAGE_ENABLED", "1");
        return !value.equals("0") && !value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("no");
    }

    public String bucket() {
        return first("JD_OSS_BUCKET", "S3_BUCKET").orElse(DEFAULT_BUCKET);
    }

    public String objectKeyForImage(int imageId, String fileName) {
        String ext = extension(fileName);
        String date = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDate.now());
        String stem = safePart(fileName == null ? "image_" + imageId : fileName.replaceFirst("\\.[^.]+$", ""));
        return "images/" + date + "/" + imageId + "_" + stem + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + ext;
    }

    public String objectKeyForThumbnail(int imageId, String sourceKey) {
        String date = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDate.now());
        String base = sourceKey == null || sourceKey.isBlank() ? "image_" + imageId : sourceKey.substring(sourceKey.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
        return "thumbnails/" + date + "/" + imageId + "_" + safePart(base) + "_thumb.jpg";
    }

    public String put(String key, byte[] data, String contentType) {
        client().putObject(
                PutObjectRequest.builder().bucket(bucket()).key(key).contentType(contentType == null ? "application/octet-stream" : contentType).build(),
                RequestBody.fromBytes(data)
        );
        return key;
    }

    public byte[] get(String key) {
        return client().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket()).key(key).build()).asByteArray();
    }

    public void delete(String key) {
        if (key != null && !key.isBlank()) {
            client().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
        }
    }

    private synchronized S3Client client() {
        if (!enabled()) {
            throw new IllegalStateException("对象存储已禁用");
        }
        Credentials credentials = credentials();
        String signature = credentials.accessKey() + ":" + endpoint() + ":" + region();
        if (cachedClient != null && signature.equals(cachedSignature)) {
            return cachedClient;
        }
        cachedClient = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.of(region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.accessKey(), credentials.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        cachedSignature = signature;
        return cachedClient;
    }

    private Credentials credentials() {
        Optional<String> access = first("JD_OSS_ACCESS_KEY_ID", "AWS_ACCESS_KEY_ID", "S3_ACCESS_KEY_ID");
        Optional<String> secret = first("JD_OSS_SECRET_ACCESS_KEY", "AWS_SECRET_ACCESS_KEY", "S3_SECRET_ACCESS_KEY");
        if (access.isPresent() && secret.isPresent()) {
            return new Credentials(access.get(), secret.get());
        }
        try {
            String service = env("JD_OSS_CREDENTIAL_SERVICE", DEFAULT_CREDENTIAL_SERVICE);
            Map<String, Object> row = jdbc.queryForMap("SELECT account, password FROM credentials WHERE service=?", service);
            String account = String.valueOf(row.getOrDefault("account", "")).trim();
            String password = String.valueOf(row.getOrDefault("password", "")).trim();
            if (StringUtils.hasText(account) && StringUtils.hasText(password)) {
                return new Credentials(account, password);
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException("未找到京东云对象存储凭据");
    }

    private String endpoint() {
        return first("JD_OSS_ENDPOINT", "S3_ENDPOINT").orElse(DEFAULT_ENDPOINT);
    }

    private String region() {
        return first("JD_OSS_REGION", "AWS_DEFAULT_REGION", "S3_REGION").orElse(DEFAULT_REGION);
    }

    private Optional<String> first(String... names) {
        for (String name : names) {
            String value = env(name, "");
            if (StringUtils.hasText(value)) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private String env(String key, String fallback) {
        return env.getProperty(key, fallback).trim();
    }

    private static String safePart(String value) {
        String text = String.valueOf(value == null ? "file" : value).replaceAll("[^0-9A-Za-z._\\-\\u4e00-\\u9fff]+", "_").replaceAll("^[._]+|[._]+$", "");
        return text.isBlank() ? "file" : text.substring(0, Math.min(120, text.length()));
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return ".jpg";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) {
            return ".jpg";
        }
        String ext = fileName.substring(idx);
        return ext.matches("\\.[A-Za-z0-9]{1,10}") ? ext : ".jpg";
    }

    private record Credentials(String accessKey, String secretKey) {
    }
}
