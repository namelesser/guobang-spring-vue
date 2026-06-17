package com.guobang.transport.image;

import com.guobang.transport.common.Api;
import com.guobang.transport.ocr.OcrService;
import com.guobang.transport.record.ExportSupport;
import com.guobang.transport.record.RecordService;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageController {
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif");

    private final ImageService imageService;
    private final RecordService recordService;
    private final OcrService ocrService;

    public ImageController(ImageService imageService, RecordService recordService, OcrService ocrService) {
        this.imageService = imageService;
        this.recordService = recordService;
        this.ocrService = ocrService;
    }

    @GetMapping("/api/image/{recordId}")
    public ResponseEntity<Map<String, Object>> imageByRecord(@PathVariable int recordId) {
        Map<String, Object> record = recordService.get(recordId);
        if (record == null) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND);
        }
        Integer imageId = recordService.firstImageId(recordId);
        if (imageId == null) {
            String legacy = String.valueOf(record.getOrDefault("image_id", "")).split(",")[0].trim();
            imageId = legacy.matches("\\d+") ? Integer.parseInt(legacy) : null;
        }
        if (imageId == null) {
            return Api.error("无关联图片", HttpStatus.NOT_FOUND);
        }
        String dataUrl = imageService.dataUrl(imageId);
        if (dataUrl == null) {
            return Api.error("图片数据不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("image_base64", dataUrl));
    }

    @GetMapping("/api/records/{recordId}/image")
    public ResponseEntity<Map<String, Object>> recordImageAlias(@PathVariable int recordId) {
        return imageByRecord(recordId);
    }

    @GetMapping("/api/images")
    public Map<String, Object> list(@RequestParam Map<String, String> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 20);
        return Api.ok(imageService.list(params, offset, limit));
    }

    @GetMapping("/api/images/{imageId}/thumbnail")
    public ResponseEntity<Map<String, Object>> thumbnail(@PathVariable int imageId) {
        String dataUrl = imageService.thumbnailDataUrl(imageId);
        if (dataUrl == null) {
            return Api.error("图片不存在或缩略图生成失败", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("thumbnail_base64", dataUrl));
    }

    @GetMapping("/api/images/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> params) throws Exception {
        String format = params.getOrDefault("format", "zip").toLowerCase();
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        var rows = imageService.exportRows(params, "zip".equals(format));
        if ("csv".equals(format)) {
            return download(ExportSupport.csv(rows, ImageService.IMAGE_COLUMNS), "images_" + stamp + ".csv", "text/csv; charset=utf-8");
        }
        if ("xls".equals(format)) {
            return download(ExportSupport.xls(rows, ImageService.IMAGE_COLUMNS, "图片清单"), "images_" + stamp + ".xls", "application/vnd.ms-excel; charset=utf-8");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("images.csv"));
            zip.write(ExportSupport.csv(rows, ImageService.IMAGE_COLUMNS));
            zip.closeEntry();
            for (Map<String, Object> row : rows) {
                int imageId = ((Number) row.get("id")).intValue();
                ImageService.ImageData data = imageService.data(imageId);
                if (data == null || data.bytes() == null) {
                    continue;
                }
                String fileName = safeName(imageId + "_" + String.valueOf(row.getOrDefault("file_name", "image_" + imageId + ".jpg")));
                zip.putNextEntry(new ZipEntry("images/" + fileName));
                zip.write(data.bytes());
                zip.closeEntry();
            }
        }
        return download(out.toByteArray(), "images_" + stamp + ".zip", "application/zip");
    }

    @GetMapping("/api/images/{imageId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable int imageId) {
        String dataUrl = imageService.dataUrl(imageId);
        if (dataUrl == null) {
            return Api.error("图片不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("image_base64", dataUrl));
    }

    @PutMapping("/api/images/{imageId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int imageId, @RequestBody Map<String, Object> body) {
        String dataUrl = String.valueOf(body.getOrDefault("image_base64", ""));
        String mime = String.valueOf(body.getOrDefault("mime_type", "image/jpeg")).toLowerCase();
        String payload = dataUrl;
        if (dataUrl.contains(",")) {
            String[] parts = dataUrl.split(",", 2);
            mime = parts[0].replace("data:", "").replaceAll(";.*$", "").toLowerCase();
            payload = parts[1];
        }
        if (!ALLOWED_IMAGE_MIME_TYPES.contains(mime)) {
            return Api.error("只支持 jpg/png/webp/bmp/gif 图片", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (payload.isBlank()) {
            return Api.error("图片数据为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            return Api.error("图片数据格式不正确", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> row = imageService.imageRow(imageId);
        if (row == null) {
            return Api.error("图片不存在", HttpStatus.NOT_FOUND);
        }
        String fileName = String.valueOf(row.getOrDefault("file_name", "image_" + imageId + ".jpg"));
        imageService.updateData(imageId, fileName, bytes, mime);
        return ResponseEntity.ok(Api.ok(Map.of("image_id", imageId, "file_name", fileName, "size", bytes.length)));
    }

    @DeleteMapping("/api/images/{imageId}")
    public Map<String, Object> delete(@PathVariable int imageId) {
        return Api.ok(imageService.deleteImageAndRecords(imageId));
    }

    @PostMapping("/api/images/{imageId}/reocr")
    public Map<String, Object> reocr(@PathVariable int imageId) {
        return Api.ok(ocrService.enqueueImage(imageId));
    }

    @PostMapping("/api/images/{imageId}/rereview")
    public Map<String, Object> rereview(@PathVariable int imageId) {
        return Api.ok("updated_records", recordService.rereviewByImageId(imageId));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static ResponseEntity<byte[]> download(byte[] content, String filename, String mediaType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        headers.setContentType(MediaType.parseMediaType(mediaType));
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private static String safeName(String value) {
        String name = value.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        return name.isBlank() ? "image.jpg" : name;
    }
}
