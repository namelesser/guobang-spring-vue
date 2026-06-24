package com.guobang.transport.record;

import com.guobang.transport.collection.CollectionService;
import com.guobang.transport.common.BusinessException;
import com.guobang.transport.common.DateRange;
import com.guobang.transport.common.DateSupport;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.rate.RateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordService {
    private static final Set<String> UPDATABLE = Set.of(
            "file_name", "image_id", "record_date", "order_no", "sender", "receiver", "company",
            "plate_no", "net_weight", "driver", "freight_rate", "detour_surcharge", "total_cost",
            "reviewed", "reviewed_at", "review_note", "note", "ocr_status", "ocr_text"
    );
    private static final Set<String> REQUIRED_COLLECTION_FIELDS = Set.of("sender", "receiver", "company", "plate_no");
    private static final Set<String> RATE_KEYS = Set.of("net_weight", "receiver", "company", "record_date", "detour_surcharge");
    private static final Set<String> DATE_COLUMNS = Set.of("record_date");
    private static final Set<String> DECIMAL_COLUMNS = Set.of("net_weight", "freight_rate", "detour_surcharge", "total_cost");
    public static final String REVIEWABLE_FILTER = """
            reviewed=0
            AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing')
            AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id)
                 OR COALESCE(records.image_id, '') != '')
            """;

    private final JdbcTemplate jdbc;
    private final CollectionService collectionService;
    private final RateService rateService;

    @Transactional
    public int createManual(Map<String, Object> body) {
        Map<String, Object> record = new LinkedHashMap<>(body);
        record.put("source", "manual");
        record.put("reviewed", 1);
        record.put("reviewed_at", LocalDateTime.now());
        recalculatePricing(record);
        return insert(record);
    }

    @Transactional
    public int insert(Map<String, Object> data) {
        DateSupport.rejectFutureDate(data.get("record_date"));
        validateDuplicate(data, null);
        if (shouldValidateCollections(data)) {
            collectionService.validateRecordCollections(data);
        }
        Integer id = jdbc.queryForObject(
                """
                INSERT INTO records (source, file_name, image_id, record_date, order_no, sender, receiver, company,
                    plate_no, net_weight, driver, freight_rate, detour_surcharge, total_cost, reviewed,
                    reviewed_at, review_note, note, ocr_status, ocr_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                Integer.class,
                DbSupport.nullableBlank(data.get("source")),
                data.get("file_name"),
                DbSupport.trim(data.get("image_id")),
                DbSupport.date(data.get("record_date")),
                DbSupport.nullableBlank(data.get("order_no")),
                DbSupport.nullableBlank(data.get("sender")),
                DbSupport.nullableBlank(data.get("receiver")),
                DbSupport.nullableBlank(data.get("company")),
                DbSupport.nullableBlank(data.get("plate_no")),
                DbSupport.decimal(data.get("net_weight")),
                data.get("driver"),
                DbSupport.decimal(data.get("freight_rate")),
                DbSupport.decimal(data.getOrDefault("detour_surcharge", 0)),
                DbSupport.decimal(data.get("total_cost")),
                data.getOrDefault("reviewed", 0),
                data.get("reviewed_at"),
                data.getOrDefault("review_note", ""),
                data.getOrDefault("note", ""),
                data.getOrDefault("ocr_status", ""),
                data.getOrDefault("ocr_text", "")
        );
        syncRecordImages(id, data.get("image_id"));
        return id;
    }

    public Map<String, Object> get(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM records WHERE id=?", id);
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    @Transactional
    public boolean update(int id, Map<String, Object> body) {
        Map<String, Object> existing = getRaw(id);
        if (existing == null) {
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>(body);
        data.remove("rotate_image_180");
        data.remove("rotate_degrees");

        Map<String, Object> merged = new LinkedHashMap<>(existing);
        data.forEach((k, v) -> {
            if (UPDATABLE.contains(k)) {
                merged.put(k, v);
            }
        });
        if (data.containsKey("record_date") || Objects.equals(data.get("reviewed"), 1)) {
            DateSupport.rejectFutureDate(merged.get("record_date"));
        }
        validateDuplicate(merged, id);
        if (shouldValidateCollections(data)) {
            collectionService.validateRecordCollections(merged);
        }
        if (data.keySet().stream().anyMatch(RATE_KEYS::contains)) {
            recalculatePricing(merged);
            data.put("freight_rate", merged.get("freight_rate"));
            data.put("total_cost", merged.get("total_cost"));
        } else if (data.containsKey("detour_surcharge") || data.containsKey("total_cost")) {
            data.put("total_cost", calculateFreightAmount(merged.get("net_weight"), merged.get("freight_rate"), merged.get("detour_surcharge")));
        }
        return updateColumns(id, data);
    }

    @Transactional
    public boolean updateColumns(int id, Map<String, Object> data) {
        List<String> fields = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String key : UPDATABLE) {
            if (data.containsKey(key)) {
                fields.add(key + "=?");
                params.add(convertColumnValue(key, data.get(key)));
            }
        }
        if (fields.isEmpty()) {
            return true;
        }
        fields.add("updated_at=LOCALTIMESTAMP");
        params.add(id);
        boolean ok = jdbc.update("UPDATE records SET " + String.join(", ", fields) + " WHERE id=?", params.toArray()) > 0;
        if (ok && data.containsKey("image_id")) {
            syncRecordImages(id, data.get("image_id"));
        }
        return ok;
    }

    @Transactional
    public boolean delete(int id) {
        return jdbc.update("DELETE FROM records WHERE id=?", id) > 0;
    }

    public Map<String, Object> list(Map<String, String> filters, int offset, int limit) {
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        FilterClause clause = filterClause(filters);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM records" + clause.where(), Integer.class, clause.params().toArray());
        Map<String, Object> summary = jdbc.queryForMap(
                """
                SELECT COUNT(*) as total_trips,
                       COALESCE(SUM(net_weight), 0) as total_weight,
                       COALESCE(SUM(total_cost), 0) as total_freight,
                       COALESCE(SUM(CASE WHEN reviewed=1 THEN 1 ELSE 0 END), 0) as reviewed_count,
                       COALESCE(SUM(CASE WHEN reviewed=0 THEN 1 ELSE 0 END), 0) as unreviewed_count
                FROM records
                """ + clause.where(),
                clause.params().toArray()
        );
        List<Object> params = new ArrayList<>(clause.params());
        params.add(limit);
        params.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM records" + clause.where() + " ORDER BY record_date DESC NULLS LAST, id DESC LIMIT ? OFFSET ?",
                params.toArray()
        );
        List<Map<String, Object>> records = DbSupport.normalizeRows(rows);
        Map<Integer, Integer> firstIds = batchFirstImageIds(records.stream().map(row -> (Integer) row.get("id")).toList());
        for (Map<String, Object> record : records) {
            Integer first = firstIds.get((Integer) record.get("id"));
            if (first == null) {
                first = firstImageIdFromText(record.get("image_id"));
            }
            record.put("first_image_id", first == null ? "" : String.valueOf(first));
        }
        return Map.of("records", records, "total", total == null ? 0 : total, "summary", DbSupport.normalizeRow(summary));
    }

    public List<Map<String, Object>> export(Map<String, String> filters) {
        FilterClause clause = filterClause(filters);
        return DbSupport.normalizeRows(jdbc.queryForList(
                """
                SELECT id, record_date, order_no, sender, receiver, company, plate_no, net_weight, driver,
                       freight_rate, detour_surcharge, total_cost, source, reviewed, reviewed_at,
                       review_note, note, ocr_status, file_name, image_id, created_at, updated_at
                FROM records
                """ + clause.where() + " ORDER BY record_date DESC NULLS LAST, id DESC",
                clause.params().toArray()
        ));
    }

    public Map<String, Object> firstUnreviewed() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM records WHERE " + REVIEWABLE_FILTER + " ORDER BY id ASC LIMIT 1");
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("record", null);
            empty.put("image_id", "");
            empty.put("total_unreviewed", 0);
            empty.put("current_index", 0);
            return empty;
        }
        Map<String, Object> row = DbSupport.normalizeRow(rows.get(0));
        Integer id = (Integer) row.get("id");
        Map<String, Object> stats = jdbc.queryForMap(
                "SELECT COUNT(*) as total, COUNT(*) FILTER (WHERE id <= ?) as current_index FROM records WHERE " + REVIEWABLE_FILTER,
                id
        );
        Integer imageId = firstImageId(id);
        if (imageId == null) {
            imageId = firstImageIdFromText(row.get("image_id"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("record", row);
        out.put("image_id", imageId == null ? "" : String.valueOf(imageId));
        out.put("total_unreviewed", stats.get("total"));
        out.put("current_index", stats.get("current_index"));
        return out;
    }

    public Map<String, Object> unreviewedList(int limit) {
        limit = Math.min(Math.max(limit, 1), 500);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM records WHERE " + REVIEWABLE_FILTER, Integer.class);
        List<Map<String, Object>> rows = DbSupport.normalizeRows(jdbc.queryForList(
                "SELECT * FROM records WHERE " + REVIEWABLE_FILTER + " ORDER BY id ASC LIMIT ?",
                limit
        ));
        Map<Integer, Integer> firstIds = batchFirstImageIds(rows.stream().map(row -> (Integer) row.get("id")).toList());
        for (Map<String, Object> row : rows) {
            Integer first = firstIds.get((Integer) row.get("id"));
            if (first == null) {
                first = firstImageIdFromText(row.get("image_id"));
            }
            row.put("first_image_id", first);
        }
        return Map.of("records", rows, "total", total == null ? 0 : total, "limit", limit);
    }

    @Transactional
    public Map<String, Object> review(int id, String submittedNote) {
        Map<String, Object> existing = getRaw(id);
        if (existing == null) {
            throw new BusinessException("记录不存在", HttpStatus.NOT_FOUND);
        }
        BigDecimal net = DbSupport.decimal(existing.get("net_weight"));
        BigDecimal rate = DbSupport.decimal(existing.get("freight_rate"));
        if (net == null || net.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("净重必须大于 0，才能标记已核对", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("运费单价必须大于 0，才能标记已核对", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        collectionService.validateRecordCollections(existing);
        String note = DbSupport.trim(submittedNote).isBlank() ? DbSupport.str(existing.get("review_note")) : submittedNote;
        updateColumns(id, Map.of(
                "reviewed", 1,
                "reviewed_at", LocalDateTime.now(),
                "review_note", note,
                "ocr_status", "done"
        ));
        renameRecordImages(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("next_record", adjacentUnreviewed(id));
        out.put("remaining", unreviewedCount());
        return out;
    }

    public int unreviewedCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM records WHERE " + REVIEWABLE_FILTER, Integer.class);
        return count == null ? 0 : count;
    }

    public Map<String, Object> adjacentUnreviewed(int currentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM records WHERE " + REVIEWABLE_FILTER + " AND id>? ORDER BY id ASC LIMIT 1",
                currentId
        );
        if (rows.isEmpty()) {
            rows = jdbc.queryForList("SELECT * FROM records WHERE " + REVIEWABLE_FILTER + " ORDER BY id ASC LIMIT 1");
        }
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    public Integer firstImageId(int recordId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT image_id FROM record_images WHERE record_id=? ORDER BY sort_order ASC, id ASC LIMIT 1",
                recordId
        );
        return rows.isEmpty() ? null : DbSupport.intValue(rows.get(0).get("image_id"));
    }

    public Map<Integer, Integer> batchFirstImageIds(List<Integer> ids) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT ON (record_id) record_id, image_id FROM record_images WHERE record_id IN ("
                        + DbSupport.placeholders(ids.size()) + ") ORDER BY record_id, sort_order ASC, id ASC",
                ids.toArray()
        );
        for (Map<String, Object> row : rows) {
            result.put(DbSupport.intValue(row.get("record_id")), DbSupport.intValue(row.get("image_id")));
        }
        return result;
    }

    public List<Integer> imageIdsForRecord(int recordId) {
        return jdbc.queryForList(
                "SELECT image_id FROM record_images WHERE record_id=? ORDER BY sort_order ASC, id ASC",
                Integer.class,
                recordId
        );
    }

    public Map<String, Object> firstRecordByImageId(int imageId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT r.* FROM record_images ri
                JOIN records r ON r.id = ri.record_id
                WHERE ri.image_id=?
                ORDER BY r.id ASC LIMIT 1
                """,
                imageId
        );
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    public int rereviewByImageId(int imageId) {
        return jdbc.update(
                """
                UPDATE records
                SET reviewed=0, reviewed_at=NULL, updated_at=LOCALTIMESTAMP
                WHERE id IN (SELECT record_id FROM record_images WHERE image_id=?)
                """,
                imageId
        );
    }

    @Transactional
    public void updateOcrStatus(int id, String status, Map<String, Object> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (fields != null) {
            data.putAll(fields);
        }
        data.put("ocr_status", status);
        if (!List.of("pending", "processing", "duplicate", "error").contains(status)) {
            Map<String, Object> merged = getRaw(id);
            if (merged != null) {
                merged.putAll(data);
                DateSupport.rejectFutureDate(merged.get("record_date"));
                validateDuplicate(merged, id);
            }
        }
        updateColumns(id, data);
    }

    public Map<String, Object> ocrStatus(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, ocr_status, file_name FROM records WHERE id=?", id);
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    public BigDecimal calculateFreightAmount(Object netWeight, Object freightRate, Object detourSurcharge) {
        BigDecimal net = DbSupport.decimal(netWeight);
        BigDecimal rate = DbSupport.decimal(freightRate);
        BigDecimal detour = DbSupport.decimal(detourSurcharge);
        if (net == null || rate == null) {
            return null;
        }
        if (detour == null) {
            detour = BigDecimal.ZERO;
        }
        return net.multiply(rate.add(detour)).setScale(4, RoundingMode.HALF_UP);
    }

    public Map<String, Object> duplicate(String orderNo, String company, Integer excludeId) {
        orderNo = DbSupport.trim(orderNo);
        company = DbSupport.trim(company);
        if (orderNo.isBlank() || company.isBlank()) {
            return null;
        }
        List<Object> params = new ArrayList<>(List.of(orderNo, company));
        String exclude = "";
        if (excludeId != null) {
            exclude = " AND id != ?";
            params.add(excludeId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, order_no, company FROM records WHERE order_no=? AND company=?" + exclude + " ORDER BY id ASC LIMIT 1",
                params.toArray()
        );
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    public void recalculatePricing(Map<String, Object> record) {
        Map<String, Object> rate = rateService.lookup(
                DbSupport.trim(record.get("company")),
                DbSupport.trim(record.get("receiver")),
                DbSupport.trim(record.get("record_date"))
        );
        if (rate == null) {
            record.put("freight_rate", null);
            record.put("total_cost", null);
            return;
        }
        record.put("freight_rate", rate.get("price_per_ton"));
        record.put("total_cost", calculateFreightAmount(record.get("net_weight"), rate.get("price_per_ton"), record.get("detour_surcharge")));
    }

    private Object convertColumnValue(String key, Object value) {
        if (DATE_COLUMNS.contains(key)) {
            return DbSupport.date(value);
        }
        if (DECIMAL_COLUMNS.contains(key)) {
            return DbSupport.decimal(value);
        }
        return value;
    }

    private boolean shouldValidateCollections(Map<String, Object> data) {
        if (Objects.equals(data.get("reviewed"), 1)) {
            return true;
        }
        return data.keySet().stream().anyMatch(REQUIRED_COLLECTION_FIELDS::contains);
    }

    private void validateDuplicate(Map<String, Object> data, Integer excludeId) {
        Map<String, Object> duplicate = duplicate(DbSupport.trim(data.get("order_no")), DbSupport.trim(data.get("company")), excludeId);
        if (duplicate != null) {
            throw new BusinessException("单号和开单公司已存在（记录 ID " + duplicate.get("id") + "）", HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> getRaw(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM records WHERE id=?", id);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    private void syncRecordImages(int recordId, Object imageIdValue) {
        jdbc.update("DELETE FROM record_images WHERE record_id=?", recordId);
        List<String> ids = splitImageIds(imageIdValue);
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).matches("\\d+")) {
                jdbc.update(
                        """
                        INSERT INTO record_images(record_id, image_id, sort_order)
                        VALUES (?, ?, ?) ON CONFLICT (record_id, image_id) DO NOTHING
                        """,
                        recordId,
                        Integer.parseInt(ids.get(i)),
                        i
                );
            }
        }
    }

    private void renameRecordImages(int recordId) {
        Map<String, Object> record = get(recordId);
        if (record == null) {
            return;
        }
        String company = safeFilenamePart(DbSupport.trim(record.get("company")).isBlank() ? "未知开票公司" : DbSupport.trim(record.get("company")));
        for (Integer imageId : imageIdsForRecord(recordId)) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT file_name FROM images WHERE id=?", imageId);
            if (rows.isEmpty()) {
                continue;
            }
            String ext = extension(DbSupport.trim(rows.get(0).get("file_name")));
            String orderNo = DbSupport.trim(record.get("order_no")).isBlank() ? "图片" + imageId : DbSupport.trim(record.get("order_no"));
            jdbc.update("UPDATE images SET file_name=? WHERE id=?", safeFilenamePart(company + "+" + orderNo) + ext, imageId);
        }
    }

    private FilterClause filterClause(Map<String, String> filters) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (has(filters, "month")) {
            DateRange range = DateSupport.monthBounds(filters.get("month"));
            where.add("record_date >= ? AND record_date < ?");
            params.add(range.start());
            params.add(range.end());
        }
        if (has(filters, "reviewed")) {
            where.add("reviewed=?");
            String val = filters.get("reviewed").toLowerCase().trim();
            // 支持 "true"/"false" 和 "1"/"0" 两种格式
            params.add("true".equals(val) ? 1 : "false".equals(val) ? 0 : Integer.parseInt(val));
        }
        if (has(filters, "source")) {
            where.add("source=?");
            params.add(filters.get("source"));
        }
        addIlike(where, params, "plate_no", filters.get("plate"));
        addIlike(where, params, "sender", filters.get("sender"));
        addIlike(where, params, "receiver", filters.get("receiver"));
        addIlike(where, params, "company", filters.get("company"));
        addIlike(where, params, "order_no", filters.get("order_no"));
        return new FilterClause(where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where), params);
    }

    private static boolean has(Map<String, String> filters, String key) {
        return filters != null && filters.get(key) != null && !filters.get(key).isBlank();
    }

    private static void addIlike(List<String> where, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.add(column + " ILIKE ?");
            params.add("%" + value.trim() + "%");
        }
    }

    private static List<String> splitImageIds(Object imageIdValue) {
        String text = DbSupport.str(imageIdValue);
        if (text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static Integer firstImageIdFromText(Object imageIdValue) {
        return splitImageIds(imageIdValue).stream()
                .filter(id -> id.matches("\\d+"))
                .findFirst()
                .map(Integer::parseInt)
                .orElse(null);
    }

    private static String extension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) {
            return ".jpg";
        }
        String ext = fileName.substring(idx);
        return Pattern.matches("\\.[A-Za-z0-9]{1,8}", ext) ? ext : ".jpg";
    }

    private static String safeFilenamePart(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("^[._]+|[._]+$", "");
    }

    private record FilterClause(String where, List<Object> params) {
    }
}
