package com.guobang.transport.record;

import com.guobang.transport.common.Api;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/records")
public class RecordController {
    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 50);
        return Api.ok(service.list(params, offset, limit));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable int id) {
        Map<String, Object> record = service.get(id);
        if (record == null) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND);
        }
        Integer first = service.firstImageId(id);
        record.put("first_image_id", first == null ? "" : first);
        return ResponseEntity.ok(Api.ok("record", record));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> params) {
        String format = params.getOrDefault("format", "csv").toLowerCase();
        var rows = service.export(params);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        if ("xls".equals(format)) {
            return download(ExportSupport.xls(rows, ExportSupport.RECORD_COLUMNS, "运输记录"),
                    "records_" + stamp + ".xls",
                    "application/vnd.ms-excel; charset=utf-8");
        }
        return download(ExportSupport.csv(rows, ExportSupport.RECORD_COLUMNS),
                "records_" + stamp + ".csv",
                "text/csv; charset=utf-8");
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return Api.ok("id", service.createManual(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        if (!service.update(id, body)) {
            return Api.error("更新失败", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        if (!service.delete(id)) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok());
    }

    @GetMapping("/unreviewed")
    public Map<String, Object> unreviewed() {
        return Api.ok(service.firstUnreviewed());
    }

    @GetMapping("/unreviewed/list")
    public Map<String, Object> unreviewedList(@RequestParam(defaultValue = "300") int limit) {
        return Api.ok(service.unreviewedList(limit));
    }

    @PostMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable int id, @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null ? "" : String.valueOf(body.getOrDefault("review_note", ""));
        return Api.ok(service.review(id, note));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static ResponseEntity<byte[]> download(byte[] content, String filename, String mediaType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        headers.setContentType(MediaType.parseMediaType(mediaType));
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
