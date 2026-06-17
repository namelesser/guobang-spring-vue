package com.guobang.transport.ocr;

import com.guobang.transport.common.Api;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {
    private final OcrService service;

    public OcrController(OcrService service) {
        this.service = service;
    }

    @PostMapping("/scan")
    public Map<String, Object> scan(@RequestPart("image") MultipartFile image) throws Exception {
        return Api.ok(service.scan(image));
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam("record_id") int recordId) {
        return Api.ok(service.status(recordId));
    }
}
