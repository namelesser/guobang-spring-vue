package com.guobang.transport.record;

import com.guobang.transport.common.Api;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

/**
 * 运输记录控制器，提供记录的增删改查、审核和导出接口
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {
    private final RecordService service;

    /**
     * 分页查询运输记录
     *
     * @param params 查询参数（offset、limit 及筛选条件）
     * @return 记录列表和总数
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> params) {
        int offset = parseInt(params.get("offset"), 0); // 解析分页偏移量，缺省为 0
        int limit = parseInt(params.get("limit"), 50); // 解析每页条数，缺省为 50
        return Api.ok(service.list(params, offset, limit)); // 将全部查询参数（含筛选条件）传给 service 处理
    }

    /**
     * 获取记录详情
     *
     * @param id 记录 ID
     * @return 记录详情
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable int id) {
        Map<String, Object> record = service.get(id); // 按 ID 查询记录
        if (record == null) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND); // 记录不存在返回 404
        }
        Integer first = service.firstImageId(id); // 查询该记录关联的第一张图片 ID
        record.put("first_image_id", first == null ? "" : first); // 附加到返回数据中，前端用于展示缩略图
        return ResponseEntity.ok(Api.ok("record", record));
    }

    /**
     * 导出运输记录
     *
     * @param params 查询参数，format 支持 csv/xls
     * @return 文件下载响应
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> params) {
        String format = params.getOrDefault("format", "csv").toLowerCase(); // 获取导出格式，默认 CSV
        var rows = service.export(params); // 根据筛选条件查询导出数据
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()); // 生成时间戳用于文件名
        if ("xls".equals(format)) {
            return download(ExportSupport.xls(rows, ExportSupport.RECORD_COLUMNS, "运输记录"), // 生成 XLS 格式的 HTML 表格字节数组
                    "records_" + stamp + ".xls",
                    "application/vnd.ms-excel; charset=utf-8");
        }
        return download(ExportSupport.csv(rows, ExportSupport.RECORD_COLUMNS), // 生成 CSV 格式字节数组
                "records_" + stamp + ".csv",
                "text/csv; charset=utf-8");
    }

    /**
     * 手动创建运输记录
     *
     * @param body 记录数据
     * @return 新创建的记录 ID
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return Api.ok("id", service.createManual(body)); // 手动创建记录，自动标记为已审核，返回新记录 ID
    }

    /**
     * 更新运输记录
     *
     * @param id   记录 ID
     * @param body 更新数据
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        if (!service.update(id, body)) {
            return Api.error("更新失败", HttpStatus.NOT_FOUND); // 记录不存在时返回 404
        }
        return ResponseEntity.ok(Api.ok());
    }

    /**
     * 删除运输记录
     *
     * @param id 记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        if (!service.delete(id)) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND); // 删除失败说明记录不存在
        }
        return ResponseEntity.ok(Api.ok());
    }

    /**
     * 获取第一条未审核记录
     *
     * @return 未审核记录
     */
    @GetMapping("/unreviewed")
    public Map<String, Object> unreviewed() {
        return Api.ok(service.firstUnreviewed()); // 获取第一条未审核记录及其图片，供审核页面使用
    }

    /**
     * 获取未审核记录列表
     *
     * @param limit 最大返回数量
     * @return 未审核记录列表
     */
    @GetMapping("/unreviewed/list")
    public Map<String, Object> unreviewedList(@RequestParam(defaultValue = "300") int limit) {
        return Api.ok(service.unreviewedList(limit)); // 获取未审核记录列表，默认最多 300 条
    }

    /**
     * 审核记录
     *
     * @param id   记录 ID
     * @param body 审核备注
     * @return 审核结果
     */
    @PostMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable int id, @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null ? "" : String.valueOf(body.getOrDefault("review_note", "")); // 提取审核备注，请求体为空时默认空字符串
        return Api.ok(service.review(id, note)); // 执行审核，返回下一条未审核记录和剩余数量
    }

    /**
     * 解析整数，失败时返回默认值
     */
    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value); // 空值或空白直接返回默认值
        } catch (NumberFormatException ex) {
            return fallback; // 解析失败（非数字字符串）返回默认值
        }
    }

    /**
     * 构造文件下载响应
     */
    private static ResponseEntity<byte[]> download(byte[] content, String filename, String mediaType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"); // URL 编码文件名，空格用 %20 代替加号以兼容更多浏览器
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded); // 设置下载头，使用 RFC 5987 编码支持中文文件名
        headers.setContentType(MediaType.parseMediaType(mediaType)); // 设置响应内容类型
        return ResponseEntity.ok().headers(headers).body(content); // 返回带响应头的文件字节流
    }
}
