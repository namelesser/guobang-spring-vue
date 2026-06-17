package com.guobang.transport.ocr;

import com.guobang.transport.common.Api;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOcrController {
    private final OcrService ocrService;
    private final JdbcTemplate jdbc;

    public AdminOcrController(OcrService ocrService, JdbcTemplate jdbc) {
        this.ocrService = ocrService;
        this.jdbc = jdbc;
    }

    @GetMapping("/ocr/tasks")
    public Map<String, Object> tasks(@RequestParam(defaultValue = "") String status,
                                     @RequestParam(defaultValue = "100") int limit,
                                     @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> data = new LinkedHashMap<>(ocrService.listTasks(status, limit, offset));
        data.put("status_counts", ocrService.taskStatusCounts());
        return Api.ok(data);
    }

    @PostMapping("/ocr/tasks/{taskId}/retry")
    public Map<String, Object> retry(@PathVariable int taskId) {
        return Api.ok(ocrService.retry(taskId));
    }

    @PostMapping("/db/optimize")
    public Map<String, Object> optimize() {
        jdbc.execute("VACUUM ANALYZE");
        return Api.ok(Map.of("vacuum_analyze", true, "deleted_ocr_tasks", 0));
    }
}
