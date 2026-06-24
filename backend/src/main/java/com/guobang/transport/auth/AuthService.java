package com.guobang.transport.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务，负责 Cookie 认证、Token 签发和验证
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final Environment environment;
    private final AuthSettingsStore settingsStore;

    /**
     * 获取 Cookie 名称
     *
     * @return Cookie 名称
     */
    public String cookieName() {
        return env("TRANSPORT_AUTH_COOKIE", "transport_auth"); // 从环境变量获取 Cookie 名称，未配置时使用默认值
    }

    /**
     * 获取会话有效期（秒）
     *
     * @return 会话有效期
     */
    public long sessionSeconds() {
        // 从环境变量获取会话有效期（秒），默认 7 天
        return Long.parseLong(env("TRANSPORT_AUTH_SESSION_SECONDS", String.valueOf(7 * 24 * 60 * 60)));
    }

    /**
     * 是否使用安全 Cookie
     *
     * @return 是否安全 Cookie
     */
    public boolean secureCookie() {
        return "1".equals(env("TRANSPORT_AUTH_COOKIE_SECURE", "0")); // 环境变量为 "1" 时启用安全 Cookie（仅 HTTPS 传输）
    }

    /**
     * 检查认证配置是否完整
     *
     * @return 错误信息，如果配置完整则返回空字符串
     */
    public String setupError() {
        if (!StringUtils.hasText(password())) { // 检查登录密码是否已配置
            return "系统未配置登录密码数据，已拒绝访问";
        }
        if (!StringUtils.hasText(secret())) { // 检查签名密钥是否已配置
            return "系统未配置 TRANSPORT_AUTH_SECRET，已拒绝访问";
        }
        return ""; // 配置完整，返回空字符串表示无错误
    }

    /**
     * 验证密码是否匹配
     *
     * @param submitted 提交的密码
     * @return 是否匹配
     */
    public boolean passwordMatches(String submitted) {
        String configured = password(); // 获取系统配置的密码
        if (!StringUtils.hasText(configured) || submitted == null) {
            return false; // 配置为空或未提交密码，直接拒绝
        }
        return constantTimeEquals(configured, submitted); // 使用常量时间比较防止时序攻击
    }

    /**
     * 签发 Token
     *
     * @return Token 字符串
     */
    public String issueToken() {
        long issuedAt = Instant.now().getEpochSecond(); // 获取当前时间戳（秒）
        String payload = "v1:" + issuedAt; // 构造版本号+签发时间的载荷
        return payload + ":" + sign(payload); // 拼接 HMAC 签名后返回完整 Token
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token Token 字符串
     * @return 是否有效
     */
    public boolean verifyToken(String token) {
        if (StringUtils.hasText(setupError()) || !StringUtils.hasText(token)) {
            return false; // 系统未配置或 Token 为空，验证失败
        }
        try {
            String[] parts = token.split(":", 3); // 按冒号分割为 [版本, 时间戳, 签名]
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                return false; // 格式不正确或版本不匹配
            }
            long issuedAt = Long.parseLong(parts[1]); // 解析签发时间戳
            // 检查时间戳合法性及是否已过期
            if (issuedAt < 0 || Instant.now().getEpochSecond() - issuedAt > sessionSeconds()) {
                return false;
            }
            return constantTimeEquals(sign("v1:" + issuedAt), parts[2]); // 验证签名是否一致
        } catch (RuntimeException ex) {
            return false; // 解析异常视为无效 Token
        }
    }

    /**
     * 检查请求是否已认证
     *
     * @param request HTTP 请求
     * @return 是否已认证
     */
    public boolean authenticated(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false; // 请求中无 Cookie，未认证
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName().equals(cookie.getName())) {
                return verifyToken(cookie.getValue()); // 找到认证 Cookie，验证其 Token
            }
        }
        return false; // 未找到认证 Cookie
    }

    private String password() {
        return settingsStore.getAuthPassword().orElse(""); // 读取数据库中的登录密码配置
    }

    private String secret() {
        return env("TRANSPORT_AUTH_SECRET", ""); // 读取 HMAC 签名密钥配置
    }

    private String env(String key, String fallback) {
        return environment.getProperty(key, fallback); // 从 Spring 环境中读取配置项，支持默认值
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); // 获取 HmacSHA256 签名算法实例
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")); // 用密钥初始化
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))); // 计算签名并转为十六进制
        } catch (Exception ex) {
            throw new IllegalStateException("无法签名认证 Cookie", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = String.valueOf(a).getBytes(StandardCharsets.UTF_8);
        byte[] right = String.valueOf(b).getBytes(StandardCharsets.UTF_8);
        int diff = left.length ^ right.length; // 长度不同时 diff 非零
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            byte x = i < left.length ? left[i] : 0; // 越界时用 0 填充
            byte y = i < right.length ? right[i] : 0;
            diff |= x ^ y; // 逐字节异或，任何不同都会使 diff 非零
        }
        return diff == 0; // 所有字节相同才返回 true
    }
}
