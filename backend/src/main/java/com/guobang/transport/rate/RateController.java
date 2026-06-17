package com.guobang.transport.rate;

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
@RequestMapping("/api/rates")
public class RateController {
    private final RateService service;

    public RateController(RateService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Api.ok("rates", service.all());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return Api.ok("id", service.create(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        if (!service.update(id, body)) {
            return Api.error("运价记录不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        if (!service.delete(id)) {
            return Api.error("运价记录不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String origin, @RequestParam String destination, @RequestParam String date) {
        Map<String, Object> rate = service.lookup(origin, destination, date);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("found", rate != null);
        body.put("rate", rate);
        return Api.ok(body);
    }
}
