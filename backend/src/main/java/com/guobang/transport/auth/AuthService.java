package com.guobang.transport.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {
    private final Environment environment;

    public AuthService(Environment environment) {
        this.environment = environment;
    }

    public String cookieName() {
        return env("TRANSPORT_AUTH_COOKIE", "transport_auth");
    }

    public long sessionSeconds() {
        return Long.parseLong(env("TRANSPORT_AUTH_SESSION_SECONDS", String.valueOf(7 * 24 * 60 * 60)));
    }

    public boolean secureCookie() {
        return "1".equals(env("TRANSPORT_AUTH_COOKIE_SECURE", "0"));
    }

    public String setupError() {
        if (!StringUtils.hasText(password())) {
            return "系统未配置 TRANSPORT_AUTH_PASSWORD，已拒绝访问";
        }
        if (!StringUtils.hasText(secret())) {
            return "系统未配置 TRANSPORT_AUTH_SECRET，已拒绝访问";
        }
        return "";
    }

    public boolean passwordMatches(String submitted) {
        String configured = password();
        if (!StringUtils.hasText(configured) || submitted == null) {
            return false;
        }
        return constantTimeEquals(configured, submitted);
    }

    public String issueToken() {
        long issuedAt = Instant.now().getEpochSecond();
        String payload = "v1:" + issuedAt;
        return payload + ":" + sign(payload);
    }

    public boolean verifyToken(String token) {
        if (StringUtils.hasText(setupError()) || !StringUtils.hasText(token)) {
            return false;
        }
        try {
            String[] parts = token.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                return false;
            }
            long issuedAt = Long.parseLong(parts[1]);
            if (issuedAt < 0 || Instant.now().getEpochSecond() - issuedAt > sessionSeconds()) {
                return false;
            }
            return constantTimeEquals(sign("v1:" + issuedAt), parts[2]);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean authenticated(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName().equals(cookie.getName())) {
                return verifyToken(cookie.getValue());
            }
        }
        return false;
    }

    private String password() {
        return env("TRANSPORT_AUTH_PASSWORD", "");
    }

    private String secret() {
        return env("TRANSPORT_AUTH_SECRET", "");
    }

    private String env(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签名认证 Cookie", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = String.valueOf(a).getBytes(StandardCharsets.UTF_8);
        byte[] right = String.valueOf(b).getBytes(StandardCharsets.UTF_8);
        int diff = left.length ^ right.length;
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            byte x = i < left.length ? left[i] : 0;
            byte y = i < right.length ? right[i] : 0;
            diff |= x ^ y;
        }
        return diff == 0;
    }
}
