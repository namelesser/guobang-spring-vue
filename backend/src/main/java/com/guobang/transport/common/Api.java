package com.guobang.transport.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class Api {
    private Api() {
    }

    public static Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        return body;
    }

    public static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> body = ok();
        body.put(key, value);
        return body;
    }

    public static Map<String, Object> ok(Map<String, ?> values) {
        Map<String, Object> body = ok();
        if (values != null) {
            body.putAll(values);
        }
        return body;
    }

    public static ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
