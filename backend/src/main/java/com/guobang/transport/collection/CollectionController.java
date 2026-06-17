package com.guobang.transport.collection;

import com.guobang.transport.common.Api;
import java.util.Map;
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

@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping("/all")
    public Map<String, Object> all() {
        return Api.ok("collections", service.all());
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam String category) {
        return Api.ok("items", service.list(category));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        int id = service.create(String.valueOf(body.getOrDefault("category", "")), String.valueOf(body.getOrDefault("value", "")));
        return Api.ok("id", id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        if (!service.update(id, String.valueOf(body.getOrDefault("value", "")))) {
            return Api.error("集合项不存在或修改失败", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        if (!service.delete(id)) {
            return Api.error("集合项不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }
}
