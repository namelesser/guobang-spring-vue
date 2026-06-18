package com.guobang.transport.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/me"
    );

    private final AuthService authService;
    private final ObjectMapper mapper;

    public AuthFilter(AuthService authService, ObjectMapper mapper) {
        this.authService = authService; // 注入认证服务
        this.mapper = mapper; // 注入 JSON 序列化器
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI(); // 获取请求路径
        // 非 API 请求或公开路径直接放行
        if (!path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String setupError = authService.setupError(); // 检查认证配置是否完整
        if (!setupError.isBlank()) {
            writeError(response, 503, setupError); // 配置不完整，返回 503
            return;
        }
        if (!authService.authenticated(request)) {
            writeError(response, 401, "未登录或登录已过期"); // 未认证，返回 401
            return;
        }
        filterChain.doFilter(request, response); // 认证通过，继续处理请求
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); // 设置 HTTP 状态码
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 指定 JSON 响应类型
        response.setCharacterEncoding("UTF-8"); // 确保中文正确编码
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false); // 标记请求失败
        body.put("error", message); // 写入错误信息
        mapper.writeValue(response.getWriter(), body); // 序列化为 JSON 并写入响应
    }
}
