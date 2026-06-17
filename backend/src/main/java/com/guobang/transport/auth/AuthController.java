package com.guobang.transport.auth;

import com.guobang.transport.common.Api;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletResponse response) {
        String setupError = authService.setupError();
        if (!setupError.isBlank()) {
            return Api.error(setupError, HttpStatus.SERVICE_UNAVAILABLE);
        }
        String password = String.valueOf(body == null ? "" : body.getOrDefault("password", ""));
        if (!authService.passwordMatches(password)) {
            return Api.error("密码错误", HttpStatus.UNAUTHORIZED);
        }
        Cookie cookie = new Cookie(authService.cookieName(), authService.issueToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(authService.secureCookie());
        cookie.setPath("/");
        cookie.setMaxAge(Math.toIntExact(authService.sessionSeconds()));
        response.addCookie(cookie);
        return ResponseEntity.ok(Api.ok(Map.of(
                "authenticated", true,
                "session_seconds", authService.sessionSeconds()
        )));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(authService.cookieName(), "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Api.ok(Map.of("authenticated", false));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        String setupError = authService.setupError();
        if (!setupError.isBlank()) {
            return Api.error(setupError, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return ResponseEntity.ok(Api.ok(Map.of("authenticated", authService.authenticated(request))));
    }
}
