package com.guobang.transport.ocr;

import com.guobang.transport.common.Api;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 控制器，提供图片扫描和状态查询接口
 */
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {
    private final OcrService service;

    /**
     * 扫描图片并识别文字
     *
     * @param image 图片文件
     * @return OCR 识别结果
     */
    @PostMapping("/scan")
    public Map<String, Object> scan(@RequestPart("image") MultipartFile image) throws Exception {
        return Api.ok(service.scan(image)); // 调用OCR服务扫描图片并返回识别结果
    }

    /**
     * 查询记录的 OCR 状态
     *
     * @param recordId 记录 ID
     * @return OCR 状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam("record_id") int recordId) {
        return Api.ok(service.status(recordId)); // 查询指定记录的OCR处理状态
    }
}
