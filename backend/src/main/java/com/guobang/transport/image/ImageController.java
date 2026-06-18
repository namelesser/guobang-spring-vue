package com.guobang.transport.image;

import com.guobang.transport.common.Api;
import com.guobang.transport.ocr.OcrService;
import com.guobang.transport.record.ExportSupport;
import com.guobang.transport.record.RecordService;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片控制器，提供图片的上传、查询、更新、删除和导出接口
 */
@RestController
@RequiredArgsConstructor
public class ImageController {
    /** 允许的图片 MIME 类型 */
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif");

    private final ImageService imageService;
    private final RecordService recordService;
    private final OcrService ocrService;

    /**
     * 根据记录 ID 获取关联图片
     *
     * @param recordId 记录 ID
     * @return 图片数据
     */
    @GetMapping("/api/image/{recordId}")
    public ResponseEntity<Map<String, Object>> imageByRecord(@PathVariable int recordId) {
        Map<String, Object> record = recordService.get(recordId); // 查询运输记录
        if (record == null) {
            return Api.error("记录不存在", HttpStatus.NOT_FOUND);
        }
        Integer imageId = recordService.firstImageId(recordId); // 优先通过关联表获取图片ID
        if (imageId == null) {
            String legacy = String.valueOf(record.getOrDefault("image_id", "")).split(",")[0].trim(); // 兼容旧数据：从image_id字段逗号分隔取第一个
            imageId = legacy.matches("\\d+") ? Integer.parseInt(legacy) : null;
        }
        if (imageId == null) {
            return Api.error("无关联图片", HttpStatus.NOT_FOUND);
        }
        String dataUrl = imageService.dataUrl(imageId); // 获取图片Data URL
        if (dataUrl == null) {
            return Api.error("图片数据不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("image_base64", dataUrl));
    }

    /**
     * 根据记录 ID 获取关联图片（别名路径）
     *
     * @param recordId 记录 ID
     * @return 图片数据
     */
    @GetMapping("/api/records/{recordId}/image")
    public ResponseEntity<Map<String, Object>> recordImageAlias(@PathVariable int recordId) {
        return imageByRecord(recordId); // 别名路径，复用imageByRecord逻辑
    }

    /**
     * 分页查询图片列表
     *
     * @param params 查询参数
     * @return 图片列表和总数
     */
    @GetMapping("/api/images")
    public Map<String, Object> list(@RequestParam Map<String, String> params) {
        int offset = parseInt(params.get("offset"), 0); // 解析分页偏移量，默认0
        int limit = parseInt(params.get("limit"), 20);  // 解析每页数量，默认20
        return Api.ok(imageService.list(params, offset, limit)); // 委托service查询
    }

    /**
     * 获取图片缩略图
     *
     * @param imageId 图片 ID
     * @return 缩略图数据
     */
    @GetMapping("/api/images/{imageId}/thumbnail")
    public ResponseEntity<Map<String, Object>> thumbnail(@PathVariable int imageId) {
        String dataUrl = imageService.thumbnailDataUrl(imageId); // 获取缩略图Data URL
        if (dataUrl == null) {
            return Api.error("图片不存在或缩略图生成失败", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("thumbnail_base64", dataUrl));
    }

    /**
     * 导出图片列表或图片压缩包
     *
     * @param params 查询参数，format 支持 csv/xls/zip
     * @return 文件下载响应
     */
    @GetMapping("/api/images/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> params) throws Exception {
        String format = params.getOrDefault("format", "zip").toLowerCase(); // 导出格式，默认zip
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()); // 时间戳用于文件名
        var rows = imageService.exportRows(params, "zip".equals(format)); // zip格式需要包含图片二进制数据
        if ("csv".equals(format)) {
            return download(ExportSupport.csv(rows, ImageService.IMAGE_COLUMNS), "images_" + stamp + ".csv", "text/csv; charset=utf-8"); // CSV导出
        }
        if ("xls".equals(format)) {
            return download(ExportSupport.xls(rows, ImageService.IMAGE_COLUMNS, "图片清单"), "images_" + stamp + ".xls", "application/vnd.ms-excel; charset=utf-8"); // Excel导出
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("images.csv")); // ZIP内包含CSV清单
            zip.write(ExportSupport.csv(rows, ImageService.IMAGE_COLUMNS));
            zip.closeEntry();
            for (Map<String, Object> row : rows) {
                int imageId = ((Number) row.get("id")).intValue();
                ImageService.ImageData data = imageService.data(imageId); // 逐个读取图片数据
                if (data == null || data.bytes() == null) {
                    continue;
                }
                String fileName = safeName(imageId + "_" + String.valueOf(row.getOrDefault("file_name", "image_" + imageId + ".jpg"))); // 构建安全文件名
                zip.putNextEntry(new ZipEntry("images/" + fileName));
                zip.write(data.bytes()); // 写入图片到ZIP
                zip.closeEntry();
            }
        }
        return download(out.toByteArray(), "images_" + stamp + ".zip", "application/zip"); // 返回ZIP文件
    }

    /**
     * 获取图片数据
     *
     * @param imageId 图片 ID
     * @return 图片数据
     */
    @GetMapping("/api/images/{imageId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable int imageId) {
        String dataUrl = imageService.dataUrl(imageId); // 获取图片Data URL
        if (dataUrl == null) {
            return Api.error("图片不存在", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(Api.ok("image_base64", dataUrl));
    }

    /**
     * 更新图片数据
     *
     * @param imageId 图片 ID
     * @param body    包含 image_base64 和 mime_type 的数据
     * @return 更新结果
     */
    @PutMapping("/api/images/{imageId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int imageId, @RequestBody Map<String, Object> body) {
        String dataUrl = String.valueOf(body.getOrDefault("image_base64", "")); // 获取Base64数据
        String mime = String.valueOf(body.getOrDefault("mime_type", "image/jpeg")).toLowerCase(); // 获取或默认MIME类型
        String payload = dataUrl;
        if (dataUrl.contains(",")) { // 解析Data URL格式：data:image/xxx;base64,xxx
            String[] parts = dataUrl.split(",", 2);
            mime = parts[0].replace("data:", "").replaceAll(";.*$", "").toLowerCase(); // 从Data URL头部提取MIME类型
            payload = parts[1]; // 提取纯Base64部分
        }
        if (!ALLOWED_IMAGE_MIME_TYPES.contains(mime)) { // 校验MIME类型白名单
            return Api.error("只支持 jpg/png/webp/bmp/gif 图片", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (payload.isBlank()) {
            return Api.error("图片数据为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload); // Base64解码为字节数组
        } catch (IllegalArgumentException ex) {
            return Api.error("图片数据格式不正确", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> row = imageService.imageRow(imageId); // 确认图片存在
        if (row == null) {
            return Api.error("图片不存在", HttpStatus.NOT_FOUND);
        }
        String fileName = String.valueOf(row.getOrDefault("file_name", "image_" + imageId + ".jpg")); // 保留原文件名
        imageService.updateData(imageId, fileName, bytes, mime); // 更新图片数据
        return ResponseEntity.ok(Api.ok(Map.of("image_id", imageId, "file_name", fileName, "size", bytes.length)));
    }

    /**
     * 删除图片及其关联记录
     *
     * @param imageId 图片 ID
     * @return 删除结果
     */
    @DeleteMapping("/api/images/{imageId}")
    public Map<String, Object> delete(@PathVariable int imageId) {
        return Api.ok(imageService.deleteImageAndRecords(imageId)); // 删除图片及清理关联记录
    }

    /**
     * 重新执行 OCR 识别
     *
     * @param imageId 图片 ID
     * @return OCR 任务 ID
     */
    @PostMapping("/api/images/{imageId}/reocr")
    public Map<String, Object> reocr(@PathVariable int imageId) {
        return Api.ok("task_id", ocrService.reocrImage(imageId)); // 触发重新OCR识别
    }

    /**
     * 重新审核关联记录
     *
     * @param imageId 图片 ID
     * @return 更新的记录数
     */
    @PostMapping("/api/images/{imageId}/rereview")
    public Map<String, Object> rereview(@PathVariable int imageId) {
        return Api.ok("updated_records", recordService.rereviewByImageId(imageId)); // 触发重新审核关联记录
    }

    /**
     * 解析整数，失败时返回默认值
     */
    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value); // 空值返回默认值
        } catch (NumberFormatException ex) {
            return fallback; // 解析失败返回默认值
        }
    }

    /**
     * 构造文件下载响应
     */
    private static ResponseEntity<byte[]> download(byte[] content, String filename, String mediaType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"); // URL编码文件名，空格转%20
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded); // 设置下载头，支持中文文件名
        headers.setContentType(MediaType.parseMediaType(mediaType)); // 设置内容类型
        return ResponseEntity.ok().headers(headers).body(content);
    }

    /**
     * 清理文件名中的非法字符
     */
    private static String safeName(String value) {
        String name = value.replaceAll("[\\\\/:*?\"<>|]+", "_").trim(); // 将非法文件名字符替换为下划线
        return name.isBlank() ? "image.jpg" : name; // 全部非法字符时返回默认名
    }
}
