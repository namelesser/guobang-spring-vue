package com.guobang.transport.quality;

import com.guobang.transport.common.Api;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {
    private final DataQualityService service;

    public DataQualityController(DataQualityService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> check() {
        return Api.ok("report", service.report());
    }
}
