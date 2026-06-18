package com.guobang.transport.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(String message) {
        // 默认使用400状态码
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        // 调用父类构造函数设置异常信息
        super(message);
        // 设置HTTP状态码
        this.status = status;
    }

    public HttpStatus status() {
        // 返回异常对应的HTTP状态码
        return status;
    }
}
