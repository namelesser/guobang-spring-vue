package com.guobang.transport.auth;

import com.guobang.transport.common.Api;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器，提供登录、登出和认证状态查询接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * 用户登录
     *
     * @param body     请求体，包含 password 字段
     * @param response HTTP 响应
     * @return 认证结果
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletResponse response) {
        String setupError = authService.setupError(); // 检查认证配置是否完整
        if (!setupError.isBlank()) {
            return Api.error(setupError, HttpStatus.SERVICE_UNAVAILABLE); // 配置不完整，返回 503
        }
        String password = String.valueOf(body == null ? "" : body.getOrDefault("password", "")); // 提取请求体中的密码
        if (!authService.passwordMatches(password)) {
            return Api.error("密码错误", HttpStatus.UNAUTHORIZED); // 密码不匹配，返回 401
        }
        // 创建认证 Cookie 并写入响应
        Cookie cookie = new Cookie(authService.cookieName(), authService.issueToken());
        cookie.setHttpOnly(true); // 防止 JavaScript 读取 Cookie
        cookie.setSecure(authService.secureCookie()); // 根据配置决定是否仅 HTTPS 传输
        cookie.setPath("/");
        cookie.setMaxAge(Math.toIntExact(authService.sessionSeconds())); // 设置 Cookie 过期时间
        response.addCookie(cookie);
        return ResponseEntity.ok(Api.ok(Map.of(
                "authenticated", true,
                "session_seconds", authService.sessionSeconds() // 返回会话有效期供前端使用
        )));
    }

    /**
     * 用户登出
     *
     * @param response HTTP 响应
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(authService.cookieName(), ""); // 创建同名空 Cookie
        cookie.setPath("/");
        cookie.setMaxAge(0); // 立即过期，浏览器会删除该 Cookie
        response.addCookie(cookie);
        return Api.ok(Map.of("authenticated", false)); // 返回未认证状态
    }

    /**
     * 查询当前认证状态
     *
     * @param request HTTP 请求
     * @return 认证状态
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        String setupError = authService.setupError(); // 检查认证配置是否完整
        if (!setupError.isBlank()) {
            return Api.error(setupError, HttpStatus.SERVICE_UNAVAILABLE); // 配置不完整，返回 503
        }
        // 验证请求中的 Cookie 并返回认证状态
        return ResponseEntity.ok(Api.ok(Map.of("authenticated", authService.authenticated(request))));
    }
}
