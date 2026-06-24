package com.guobang.transport.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class Api {
    private Api() {
        // 工具类禁止实例化
    }

    public static Map<String, Object> ok() {
        // 创建成功响应体
        Map<String, Object> body = new LinkedHashMap<>();
        // 标记请求成功
        body.put("ok", true);
        return body;
    }

    public static Map<String, Object> ok(String key, Object value) {
        // 基于基础成功响应创建扩展响应
        Map<String, Object> body = ok();
        // 添加自定义键值对
        body.put(key, value);
        return body;
    }

    public static Map<String, Object> ok(Map<String, ?> values) {
        // 基于基础成功响应创建扩展响应
        Map<String, Object> body = ok();
        if (values != null) {
            // 合并所有键值对到响应体
            body.putAll(values);
        }
        return body;
    }

    public static ResponseEntity<Map<String, Object>> error(String message, HttpStatus status) {
        // 创建错误响应体
        Map<String, Object> body = new LinkedHashMap<>();
        // 标记请求失败
        body.put("ok", false);
        // 设置错误信息
        body.put("error", message);
        body.put("status", status.value());
        // 返回指定HTTP状态码的响应
        return ResponseEntity.status(status).body(body);
    }
}
