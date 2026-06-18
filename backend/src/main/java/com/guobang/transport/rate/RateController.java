package com.guobang.transport.rate;

import com.guobang.transport.common.Api;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运价控制器，提供线路运价的增删改查接口
 */
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
public class RateController {
    private final RateService service;

    /**
     * 分页查询运价列表
     *
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 运价列表和总数
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "20") int limit) {
        // 调用服务层分页查询运价列表
        return Api.ok(service.list(offset, limit));
    }

    /**
     * 创建运价
     *
     * @param body 运价数据
     * @return 新创建的运价 ID
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        // 调用服务层创建运价，返回新记录 ID
        return Api.ok("id", service.create(body));
    }

    /**
     * 更新运价
     *
     * @param id   运价 ID
     * @param body 更新数据
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // 更新失败则返回 404
        if (!service.update(id, body)) {
            return Api.error("运价记录不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    /**
     * 删除运价
     *
     * @param id 运价 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        // 删除失败则返回 404
        if (!service.delete(id)) {
            return Api.error("运价记录不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    /**
     * 查询指定线路和日期的运价
     *
     * @param origin      发货地
     * @param destination 目的地
     * @param date        日期
     * @return 运价查询结果
     */
    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String origin, @RequestParam String destination, @RequestParam String date) {
        // 调用服务层按线路和日期查询运价
        Map<String, Object> rate = service.lookup(origin, destination, date);
        // 组装返回结果，标记是否找到匹配运价
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("found", rate != null);
        body.put("rate", rate);
        return Api.ok(body);
    }
}
