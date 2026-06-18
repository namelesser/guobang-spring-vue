package com.guobang.transport.collection;

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
 * 基础资料控制器，提供开单公司、发货单位、收货单位、车牌号等基础数据的增删改查接口
 */
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {
    private final CollectionService service;

    /**
     * 获取所有基础资料（按分类分组）
     *
     * @return 按分类分组的基础资料
     */
    @GetMapping("/all")
    public Map<String, Object> all() {
        // 获取全部基础资料并按分类分组返回
        return Api.ok("collections", service.all());
    }

    /**
     * 查询基础资料列表
     * <p>当指定 category 时返回该分类的分页列表，否则返回所有分类</p>
     *
     * @param category 分类名称
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 基础资料列表
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String category,
                                    @RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "20") int limit) {
        // 指定分类时返回分页列表，否则返回全部分类
        if (category != null && !category.isEmpty()) {
            return Api.ok(service.list(category, offset, limit));
        }
        return Api.ok("collections", service.all());
    }

    /**
     * 创建基础资料
     *
     * @param body 包含 category 和 value 的数据
     * @return 新创建的记录 ID
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        // 调用服务层创建基础资料，返回新记录 ID
        int id = service.create(body);
        return Api.ok("id", id);
    }

    /**
     * 更新基础资料
     *
     * @param id   记录 ID
     * @param body 更新数据
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // 更新失败则返回 404
        if (!service.update(id, body)) {
            return Api.error("集合项不存在或修改失败", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    /**
     * 删除基础资料
     *
     * @param id 记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        // 删除失败则返回 404
        if (!service.delete(id)) {
            return Api.error("集合项不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }
}
