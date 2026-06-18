package com.guobang.transport.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> business(BusinessException ex) {
        // 将业务异常转换为标准错误响应
        return Api.error(ex.getMessage(), ex.status());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException ex) {
        // 提取最根本的异常原因信息
        String msg = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        // 判断是否为重复记录或唯一约束冲突
        if (msg != null && msg.toLowerCase().contains("duplicate")) {
            return Api.error("重复记录或唯一约束冲突", HttpStatus.CONFLICT);
        }
        // 其他数据完整性校验失败
        return Api.error("数据校验失败", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> uploadTooLarge() {
        // 返回文件大小超限错误
        return Api.error("图片超过上传大小限制", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        // 记录未捕获的异常日志
        log.error("Unhandled request error", ex);
        // 返回通用服务器内部错误
        return Api.error("服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
