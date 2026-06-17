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
        this.authService = authService;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String setupError = authService.setupError();
        if (!setupError.isBlank()) {
            writeError(response, 503, setupError);
            return;
        }
        if (!authService.authenticated(request)) {
            writeError(response, 401, "未登录或登录已过期");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        mapper.writeValue(response.getWriter(), body);
    }
}
