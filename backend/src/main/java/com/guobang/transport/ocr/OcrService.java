package com.guobang.transport.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guobang.transport.collection.CollectionService;
import com.guobang.transport.common.BusinessException;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.image.ImageService;
import com.guobang.transport.record.RecordService;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrService {
    private static final Pattern ORDER_PATTERN = Pattern.compile("(WG\\d{10,16}|M\\d+|\\d{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[年/-]?(\\d{1,2})[月/-]?(\\d{1,2})");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("净\\s*重[^0-9]{0,10}(\\d+(?:\\.\\d+)?)");
    private static final Pattern PLATE_PATTERN = Pattern.compile("([贵豫京沪粤鲁浙苏][A-Z]{1,2}\\d{4,5})");

    private final JdbcTemplate jdbc;
    private final ImageService imageService;
    private final RecordService recordService;
    private final CollectionService collectionService;
    private final Environment env;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public OcrService(JdbcTemplate jdbc, ImageService imageService, RecordService recordService,
                      CollectionService collectionService, Environment env, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.imageService = imageService;
        this.recordService = recordService;
        this.collectionService = collectionService;
        this.env = env;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> scan(MultipartFile image) throws Exception {
        String mime = Optional.ofNullable(image.getContentType()).orElse("image/jpeg");
        if (!List.of("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif").contains(mime)) {
            throw new BusinessException("只支持 jpg/png/webp/bmp/gif 图片", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        int maxPending = Integer.parseInt(env.getProperty("MAX_PENDING_OCR_TASKS", "500"));
        if (countTasks("pending") >= maxPending) {
            throw new BusinessException("OCR任务积压过多，请稍后再上传", HttpStatus.TOO_MANY_REQUESTS);
        }
        byte[] bytes = image.getBytes();
        if (bytes.length == 0) {
            throw new BusinessException("图片数据为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String fileName = Optional.ofNullable(image.getOriginalFilename()).filter(s -> !s.isBlank()).orElse("upload.jpg");
        int imageId = imageService.insert(fileName, bytes, mime);
        int recordId = recordService.insert(new LinkedHashMap<>(Map.of(
                "source", "ocr",
                "file_name", fileName,
                "image_id", String.valueOf(imageId),
                "reviewed", 0,
                "ocr_status", "pending"
        )));
        int taskId = createTask(recordId, imageId, fileName, mime, 0);
        return Map.of("id", recordId, "image_id", String.valueOf(imageId), "task_id", taskId, "status", "pending", "file_name", fileName);
    }

    @Transactional
    public Map<String, Object> enqueueImage(int imageId) {
        Map<String, Object> image = imageService.imageRow(imageId);
        if (image == null) {
            throw new BusinessException("图片不存在", HttpStatus.NOT_FOUND);
        }
        int maxPending = Integer.parseInt(env.getProperty("MAX_PENDING_OCR_TASKS", "500"));
        if (countTasks("pending") >= maxPending) {
            throw new BusinessException("OCR任务积压过多，请稍后再重试", HttpStatus.TOO_MANY_REQUESTS);
        }
        Map<String, Object> record = recordService.firstRecordByImageId(imageId);
        String fileName = DbSupport.trim(image.get("file_name")).isBlank() ? "image_" + imageId + ".jpg" : DbSupport.trim(image.get("file_name"));
        String mime = DbSupport.trim(image.get("mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(image.get("mime_type"));
        int recordId;
        if (record == null) {
            recordId = recordService.insert(new LinkedHashMap<>(Map.of(
                    "source", "ocr",
                    "file_name", fileName,
                    "image_id", String.valueOf(imageId),
                    "reviewed", 0,
                    "ocr_status", "pending"
            )));
        } else {
            recordId = ((Number) record.get("id")).intValue();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("file_name", fileName);
            fields.put("image_id", String.valueOf(imageId));
            fields.put("reviewed", 0);
            fields.put("reviewed_at", null);
            fields.put("review_note", "");
            recordService.updateOcrStatus(recordId, "pending", fields);
        }
        int taskId = createTask(recordId, imageId, fileName, mime, 0);
        return Map.of("record_id", recordId, "image_id", imageId, "task_id", taskId, "ocr_status", "pending");
    }

    public Map<String, Object> status(int recordId) {
        Map<String, Object> status = recordService.ocrStatus(recordId);
        if (status == null) {
            throw new BusinessException("记录不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> task = latestTask(recordId);
        Map<String, Object> out = new LinkedHashMap<>(status);
        out.put("task", task);
        return out;
    }

    public Map<String, Object> listTasks(String status, int limit, int offset) {
        limit = Math.min(Math.max(limit, 1), 500);
        offset = Math.max(offset, 0);
        List<Object> params = new ArrayList<>();
        String where = "";
        if (status != null && !status.isBlank()) {
            where = "WHERE t.status=?";
            params.add(status);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM ocr_tasks t " + where, Integer.class, params.toArray());
        List<Object> rowParams = new ArrayList<>(params);
        rowParams.add(limit);
        rowParams.add(offset);
        List<Map<String, Object>> rows = DbSupport.normalizeRows(jdbc.queryForList(
                """
                SELECT t.*, r.ocr_status AS record_ocr_status, r.file_name AS record_file_name
                FROM ocr_tasks t LEFT JOIN records r ON r.id=t.record_id
                """ + where + " ORDER BY t.id DESC LIMIT ? OFFSET ?",
                rowParams.toArray()
        ));
        return Map.of("items", rows, "total", total == null ? 0 : total, "limit", limit, "offset", offset);
    }

    public Map<String, Integer> taskStatusCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT status, COUNT(*) AS cnt FROM ocr_tasks GROUP BY status")) {
            out.put(DbSupport.str(row.get("status")), ((Number) row.get("cnt")).intValue());
        }
        return out;
    }

    public Map<String, Object> retry(int taskId) {
        Map<String, Object> task = getTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在", HttpStatus.NOT_FOUND);
        }
        int recordId = ((Number) task.get("record_id")).intValue();
        int imageId = ((Number) task.get("image_id")).intValue();
        String fileName = DbSupport.trim(task.get("file_name"));
        String mime = DbSupport.trim(task.get("mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(task.get("mime_type"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reviewed", 0);
        fields.put("reviewed_at", null);
        fields.put("review_note", "");
        fields.put("file_name", fileName);
        fields.put("image_id", String.valueOf(imageId));
        recordService.updateOcrStatus(recordId, "pending", fields);
        int newTaskId = createTask(recordId, imageId, fileName, mime, 0);
        return Map.of("record_id", recordId, "image_id", imageId, "task_id", newTaskId, "status", "pending");
    }

    @Scheduled(fixedDelay = 2000)
    public void processOnePendingTask() {
        Map<String, Object> task = claimNextTask();
        if (task == null) {
            return;
        }
        runTask(task);
    }

    @Transactional
    public int createTask(int recordId, int imageId, String fileName, String mimeType, int priority) {
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT id FROM ocr_tasks WHERE record_id=? AND image_id=? AND status IN ('pending', 'processing') ORDER BY id DESC LIMIT 1",
                recordId, imageId
        );
        if (!active.isEmpty()) {
            return ((Number) active.get(0).get("id")).intValue();
        }
        return jdbc.queryForObject(
                "INSERT INTO ocr_tasks(record_id, image_id, file_name, mime_type, priority, status) VALUES (?, ?, ?, ?, ?, 'pending') RETURNING id",
                Integer.class,
                recordId, imageId, fileName, mimeType, priority
        );
    }

    private Map<String, Object> claimNextTask() {
        int maxRetry = Integer.parseInt(env.getProperty("MAX_OCR_RETRY_COUNT", "3"));
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                WITH next_task AS (
                    SELECT id FROM ocr_tasks
                    WHERE status='pending' AND retry_count < ?
                    ORDER BY priority ASC, id ASC LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE ocr_tasks t
                SET status='processing', started_at=LOCALTIMESTAMP, finished_at=NULL, error_message=''
                FROM next_task
                WHERE t.id=next_task.id
                RETURNING t.*
                """,
                maxRetry
        );
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    private void runTask(Map<String, Object> task) {
        int taskId = ((Number) task.get("id")).intValue();
        int recordId = ((Number) task.get("record_id")).intValue();
        int imageId = ((Number) task.get("image_id")).intValue();
        String fileName = DbSupport.trim(task.get("file_name")).isBlank() ? "image_" + imageId + ".jpg" : DbSupport.trim(task.get("file_name"));
        try {
            recordService.updateOcrStatus(recordId, "processing", Map.of());
            ImageService.ImageData image = imageService.data(imageId);
            if (image == null || image.bytes() == null || image.bytes().length == 0) {
                throw new BusinessException("图片数据不存在", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            OcrTextResult result = recognize(image.bytes(), fileName);
            if (result.lines().isEmpty()) {
                recordService.updateOcrStatus(recordId, "error", Map.of(
                        "file_name", fileName,
                        "image_id", String.valueOf(imageId),
                        "ocr_text", "",
                        "review_note", "OCR引擎未识别到有效文字，请人工审核"
                ));
                markTaskFailed(taskId, "OCR 未识别到有效文字");
                return;
            }
            finalizeResult(taskId, recordId, imageId, fileName, result);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "OCR 处理失败" : ex.getMessage();
            recordService.updateOcrStatus(recordId, "error", Map.of("review_note", msg.substring(0, Math.min(500, msg.length()))));
            markTaskFailed(taskId, msg);
        }
    }

    private void finalizeResult(int taskId, int recordId, int imageId, String fileName, OcrTextResult result) {
        String text = cleanText(String.join("\n", result.lines()));
        Map<String, Object> fields = extractFields(text);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("image_id", String.valueOf(imageId));
        update.put("file_name", fileName);
        update.put("record_date", blankToNull(fields.get("record_date")));
        update.put("order_no", blankToNull(fields.get("order_no")));
        update.put("sender", blankToNull(fields.get("sender")));
        update.put("receiver", blankToNull(fields.get("receiver")));
        update.put("company", blankToNull(fields.get("company")));
        update.put("plate_no", blankToNull(fields.get("plate_no")));
        update.put("net_weight", blankToNull(fields.get("net_weight")));
        update.put("ocr_text", text);
        update.put("review_note", "OCR引擎：" + result.source() + "；解析入口：Java 通用解析");
        recordService.recalculatePricing(update);

        Map<String, Object> duplicate = recordService.duplicate(DbSupport.trim(update.get("order_no")), DbSupport.trim(update.get("company")), recordId);
        if (duplicate != null) {
            Map<String, Object> dupFields = new LinkedHashMap<>(update);
            dupFields.remove("order_no");
            dupFields.remove("company");
            dupFields.put("review_note", "重复记录：单号+开单公司已存在于 ID " + duplicate.get("id") + "，请人工核对");
            recordService.updateOcrStatus(recordId, "duplicate", dupFields);
            markTaskDuplicate(taskId, "重复记录：ID " + duplicate.get("id"));
            return;
        }
        recordService.updateOcrStatus(recordId, "done", update);
        markTaskDone(taskId);
    }

    private OcrTextResult recognize(byte[] image, String fileName) throws Exception {
        if (!env.getProperty("PADDLEOCR_AISTUDIO_TOKEN", "").isBlank()) {
            try {
                List<String> lines = paddle(image, fileName);
                if (!lines.isEmpty()) {
                    return new OcrTextResult("PaddleOCR API", lines);
                }
            } catch (Exception ignored) {
            }
        }
        if (!env.getProperty("BAIDU_OCR_API_KEY", "").isBlank() && !env.getProperty("BAIDU_OCR_SECRET_KEY", "").isBlank()) {
            List<String> lines = baidu(image);
            if (!lines.isEmpty()) {
                return new OcrTextResult("百度 OCR", lines);
            }
        }
        throw new BusinessException("未配置可用 OCR 引擎：请设置 PADDLEOCR_AISTUDIO_TOKEN 或 BAIDU_OCR_API_KEY/BAIDU_OCR_SECRET_KEY", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private List<String> paddle(byte[] image, String fileName) throws Exception {
        String token = env.getProperty("PADDLEOCR_AISTUDIO_TOKEN", "");
        String jobUrl = env.getProperty("PADDLEOCR_AISTUDIO_JOB_URL", "https://paddleocr.aistudio-app.com/api/v2/ocr/jobs");
        String model = env.getProperty("PADDLEOCR_AISTUDIO_MODEL", "PaddleOCR-VL-1.6");
        String boundary = "----guobang" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "model", model);
        writePart(body, boundary, "optionalPayload", "{\"useDocOrientationClassify\":false,\"useDocUnwarping\":false,\"useChartRecognition\":false}");
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest submit = HttpRequest.newBuilder(URI.create(jobUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        JsonNode submitted = mapper.readTree(httpClient.send(submit, HttpResponse.BodyHandlers.ofString()).body());
        String jobId = submitted.path("data").path("jobId").asText("");
        if (jobId.isBlank()) {
            return List.of();
        }
        long deadline = System.currentTimeMillis() + (long) (Double.parseDouble(env.getProperty("PADDLEOCR_AISTUDIO_TIMEOUT", "900")) * 1000);
        while (System.currentTimeMillis() < deadline) {
            HttpRequest statusReq = HttpRequest.newBuilder(URI.create(jobUrl + "/" + jobId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "bearer " + token)
                    .GET().build();
            JsonNode status = mapper.readTree(httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString()).body());
            String state = status.path("data").path("state").asText("");
            if ("done".equals(state)) {
                String jsonUrl = status.path("data").path("resultUrl").path("jsonUrl").asText("");
                String jsonl = httpClient.send(HttpRequest.newBuilder(URI.create(jsonUrl)).timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
                return extractAistudioLines(jsonl);
            }
            if ("failed".equals(state)) {
                return List.of();
            }
            Thread.sleep((long) (Double.parseDouble(env.getProperty("PADDLEOCR_AISTUDIO_POLL_INTERVAL", "4")) * 1000));
        }
        return List.of();
    }

    private List<String> baidu(byte[] image) throws Exception {
        String apiKey = env.getProperty("BAIDU_OCR_API_KEY", "");
        String secret = env.getProperty("BAIDU_OCR_SECRET_KEY", "");
        String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id="
                + enc(apiKey) + "&client_secret=" + enc(secret);
        JsonNode token = mapper.readTree(httpClient.send(HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String access = token.path("access_token").asText("");
        if (access.isBlank()) {
            return List.of();
        }
        String body = "image=" + enc(Base64.getEncoder().encodeToString(image)) + "&language_type=CHN_ENG";
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=" + enc(access)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode payload = mapper.readTree(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body());
        List<String> lines = new ArrayList<>();
        for (JsonNode item : payload.path("words_result")) {
            String words = item.path("words").asText("").trim();
            if (!words.isBlank()) {
                lines.add(words);
            }
        }
        return lines;
    }

    private Map<String, Object> extractFields(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("record_date", firstMatch(DATE_PATTERN, text).map(m -> "%s-%02d-%02d".formatted(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))).orElse(""));
        out.put("order_no", firstGroup(ORDER_PATTERN, text));
        out.put("net_weight", firstGroup(WEIGHT_PATTERN, text));
        out.put("plate_no", bestKnown(firstGroup(PLATE_PATTERN, text), collectionService.values("plate"), text));
        out.put("company", bestKnown(extractCompany(text), collectionService.values("company"), text));
        out.put("sender", bestKnown(extractSender(text, DbSupport.trim(out.get("company"))), collectionService.values("sender"), text));
        out.put("receiver", bestKnown(extractReceiver(text), collectionService.values("receiver"), text));
        return out;
    }

    private String extractCompany(String text) {
        for (String label : List.of("开单公司", "开票公司", "过磅单位", "销售单位", "供货单位")) {
            Matcher m = Pattern.compile(label + "[：:\\s]*([^\\n]+)").matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        for (String candidate : collectionService.values("company")) {
            if (text.replace("\n", "").contains(candidate)) {
                return candidate;
            }
        }
        Matcher m = Pattern.compile("[\\u4e00-\\u9fff]{4,}(?:有限公司|有限责任公司|砂石场|沙石场|建材厂|砖厂)").matcher(text);
        return m.find() ? m.group() : "";
    }

    private String extractSender(String text, String fallback) {
        Matcher m = Pattern.compile("发货单位[：:\\s]*([^\\n]+)").matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return fallback;
    }

    private String extractReceiver(String text) {
        Matcher m = Pattern.compile("收货单位[：:\\s]*([^\\n]+)").matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        for (String candidate : collectionService.values("receiver")) {
            if (text.replace("\n", "").contains(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private String bestKnown(String raw, List<String> candidates, String fullText) {
        String clean = normalize(raw);
        String full = normalize(fullText);
        for (String candidate : candidates) {
            String c = normalize(candidate);
            if (!c.isBlank() && (c.equals(clean) || full.contains(c) || clean.contains(c) || c.contains(clean))) {
                return candidate;
            }
        }
        return raw == null ? "" : raw.trim();
    }

    private static String normalize(String text) {
        return String.valueOf(text == null ? "" : text).replaceAll("[\\s:：,，.。;；|/\\\\\\-_*]+", "");
    }

    private static Optional<Matcher> firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher) : Optional.empty();
    }

    private static String firstGroup(Pattern pattern, String text) {
        return firstMatch(pattern, text).map(m -> m.group(1)).orElse("");
    }

    private static Object blankToNull(Object value) {
        String text = DbSupport.trim(value);
        return text.isBlank() ? null : text;
    }

    private static String cleanText(String text) {
        return text.replaceAll("(?i)<td[^>]*>", "\n")
                .replaceAll("(?i)</?tr[^>]*>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n", "\n")
                .trim();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void writePart(ByteArrayOutputStream body, String boundary, String name, String value) throws Exception {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private List<String> extractAistudioLines(String jsonl) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String raw : jsonl.split("\\R")) {
            if (raw.isBlank()) {
                continue;
            }
            JsonNode item = mapper.readTree(raw);
            for (JsonNode res : item.path("result").path("layoutParsingResults")) {
                String text = res.path("markdown").path("text").asText("");
                for (String line : text.split("\\R")) {
                    if (!line.trim().isBlank()) {
                        lines.add(line.trim());
                    }
                }
            }
        }
        return lines;
    }

    private Map<String, Object> latestTask(int recordId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, record_id, image_id, status, retry_count, error_message, created_at, started_at, finished_at, priority
                FROM ocr_tasks WHERE record_id=? ORDER BY id DESC LIMIT 1
                """,
                recordId
        );
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    private Map<String, Object> getTask(int taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ocr_tasks WHERE id=?", taskId);
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    private int countTasks(String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ocr_tasks WHERE status=?", Integer.class, status);
        return count == null ? 0 : count;
    }

    private void markTaskDone(int taskId) {
        jdbc.update("UPDATE ocr_tasks SET status='done', finished_at=LOCALTIMESTAMP WHERE id=?", taskId);
    }

    private void markTaskDuplicate(int taskId, String message) {
        jdbc.update("UPDATE ocr_tasks SET status='duplicate', error_message=?, finished_at=LOCALTIMESTAMP WHERE id=?", truncate(message), taskId);
    }

    private void markTaskFailed(int taskId, String message) {
        jdbc.update("UPDATE ocr_tasks SET status='error', retry_count=retry_count+1, error_message=?, finished_at=LOCALTIMESTAMP WHERE id=?", truncate(message), taskId);
    }

    private static String truncate(String message) {
        String text = message == null ? "" : message;
        return text.substring(0, Math.min(500, text.length()));
    }

    private record OcrTextResult(String source, List<String> lines) {
    }
}
