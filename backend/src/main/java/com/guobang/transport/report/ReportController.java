package com.guobang.transport.report;

import com.guobang.transport.common.Api;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    public Map<String, Object> monthly(@RequestParam String month,
                                       @RequestParam(required = false) String sender,
                                       @RequestParam(required = false) String receiver,
                                       @RequestParam(required = false) String company,
                                       @RequestParam(required = false, name = "plate_no") String plateNo) {
        return Api.ok(service.monthly(month, sender, receiver, company, plateNo));
    }
}
