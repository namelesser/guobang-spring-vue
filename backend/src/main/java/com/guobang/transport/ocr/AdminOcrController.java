package com.guobang.transport.ocr;

import com.guobang.transport.common.Api;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 OCR 控制器，提供 OCR 任务管理和数据库维护接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOcrController {
    private final OcrService ocrService;
    private final JdbcTemplate jdbc;

    /**
     * 查询 OCR 任务列表
     *
     * @param status 任务状态筛选
     * @param limit  最大返回数量
     * @param offset 偏移量
     * @return 任务列表和状态统计
     */
    @GetMapping("/ocr/tasks")
    public Map<String, Object> tasks(@RequestParam(defaultValue = "") String status,
                                     @RequestParam(defaultValue = "100") int limit,
                                     @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> data = new LinkedHashMap<>(); // 构建返回数据容器
        data.put("tasks", ocrService.listTasks(status, limit, offset)); // 查询任务列表
        data.put("status_counts", ocrService.taskStatusCounts()); // 附带各状态的统计数量
        return Api.ok(data);
    }

    /**
     * 重试失败的 OCR 任务
     *
     * @param taskId 任务 ID
     * @return 重试结果
     */
    @PostMapping("/ocr/tasks/{taskId}/retry")
    public Map<String, Object> retry(@PathVariable int taskId) {
        return Api.ok(ocrService.retry(taskId)); // 重试指定的失败OCR任务
    }

    /**
     * 优化数据库（VACUUM ANALYZE）
     *
     * @return 优化结果
     */
    @PostMapping("/db/optimize")
    public Map<String, Object> optimize() {
        jdbc.execute("VACUUM ANALYZE"); // 执行PostgreSQL数据库优化回收空间并更新统计信息
        return Api.ok(Map.of("vacuum_analyze", true, "deleted_ocr_tasks", 0));
    }
}
