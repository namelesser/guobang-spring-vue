package com.guobang.transport.quality;

import com.guobang.transport.common.Api;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据质量控制器，提供数据质量检查和详情查询接口
 */
@RestController
@RequestMapping("/api/data-quality")
@RequiredArgsConstructor
public class DataQualityController {
    private final DataQualityService service;

    /**
     * 执行数据质量检查
     *
     * @return 检查报告
     */
    @GetMapping
    public Map<String, Object> check() {
        // 调用服务层执行数据质量检查并返回报告
        return Api.ok("report", service.get());
    }

    /**
     * 获取指定检查项的详情（分页）
     *
     * @param checkId 检查项 ID
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 检查详情
     */
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail(
            @RequestParam String checkId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        // 调用服务层获取指定检查项的分页详情
        Map<String, Object> result = service.detail(checkId, offset, limit);
        // 未知检查类型返回 400 错误
        if (result == null) {
            return Api.error("未知检查类型: " + checkId, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(Api.ok(result));
    }
}
