package com.guobang.transport.rate;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.db.DbSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateService {
    private final JdbcTemplate jdbc;

    public RateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> all() {
        return DbSupport.normalizeRows(jdbc.queryForList(
                "SELECT * FROM freight_rates ORDER BY origin, destination, effective_from DESC"
        ));
    }

    public int create(Map<String, Object> body) {
        Map<String, Object> data = normalize(body);
        validatePeriod(data, null);
        return jdbc.queryForObject(
                """
                INSERT INTO freight_rates(origin, destination, price_per_ton, effective_from, effective_to, note)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """,
                Integer.class,
                data.get("origin"),
                data.get("destination"),
                data.get("price_per_ton"),
                data.get("effective_from"),
                data.get("effective_to"),
                data.get("note")
        );
    }

    public boolean update(int id, Map<String, Object> body) {
        Map<String, Object> existing = find(id);
        if (existing == null) {
            return false;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(existing);
        body.forEach((k, v) -> {
            if (v != null) {
                merged.put(k, v);
            }
        });
        Map<String, Object> normalized = normalize(merged);
        validatePeriod(normalized, id);

        List<String> fields = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String key : List.of("origin", "destination", "price_per_ton", "effective_from", "effective_to", "note")) {
            if (body.containsKey(key)) {
                fields.add(key + "=?");
                params.add(normalized.get(key));
            }
        }
        if (fields.isEmpty()) {
            return true;
        }
        params.add(id);
        return jdbc.update("UPDATE freight_rates SET " + String.join(", ", fields) + " WHERE id=?", params.toArray()) > 0;
    }

    public boolean delete(int id) {
        return jdbc.update("DELETE FROM freight_rates WHERE id=?", id) > 0;
    }

    public Map<String, Object> lookup(String origin, String destination, String onDate) {
        if (DbSupport.trim(origin).isBlank() || DbSupport.trim(destination).isBlank() || DbSupport.trim(onDate).isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, price_per_ton, note FROM freight_rates
                WHERE (? ILIKE '%' || origin || '%' OR origin ILIKE '%' || ? || '%')
                  AND (? ILIKE '%' || destination || '%' OR destination ILIKE '%' || ? || '%')
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                ORDER BY effective_from DESC LIMIT 1
                """,
                origin, origin, destination, destination, LocalDate.parse(onDate.substring(0, 10)), LocalDate.parse(onDate.substring(0, 10))
        );
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    public Map<String, Object> find(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM freight_rates WHERE id=?", id);
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    private Map<String, Object> normalize(Map<String, Object> input) {
        String origin = DbSupport.trim(input.get("origin"));
        String destination = DbSupport.trim(input.get("destination"));
        LocalDate from = DbSupport.date(input.get("effective_from"));
        LocalDate to = DbSupport.date(input.get("effective_to"));
        BigDecimal price = DbSupport.decimal(input.get("price_per_ton"));
        if (origin.isBlank() || destination.isBlank() || from == null || price == null) {
            throw new BusinessException("开单公司、收货单位、单价和生效日期不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("单价不能为负数", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("origin", origin);
        out.put("destination", destination);
        out.put("price_per_ton", price);
        out.put("effective_from", from);
        out.put("effective_to", to);
        out.put("note", DbSupport.trim(input.get("note")));
        return out;
    }

    private void validatePeriod(Map<String, Object> data, Integer excludeId) {
        LocalDate from = (LocalDate) data.get("effective_from");
        LocalDate to = (LocalDate) data.get("effective_to");
        if (to != null && to.isBefore(from)) {
            throw new BusinessException("失效日期不能早于生效日期", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<Object> params = new ArrayList<>(List.of(
                data.get("origin"),
                data.get("destination"),
                to == null ? LocalDate.of(9999, 12, 31) : to,
                from
        ));
        String excludeClause = "";
        if (excludeId != null) {
            excludeClause = " AND id != ?";
            params.add(excludeId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, effective_from, effective_to
                FROM freight_rates
                WHERE origin=?
                  AND destination=?
                  AND effective_from <= ?
                  AND COALESCE(effective_to, DATE '9999-12-31') >= ?
                """ + excludeClause + " ORDER BY effective_from DESC LIMIT 1",
                params.toArray()
        );
        if (!rows.isEmpty()) {
            Map<String, Object> row = DbSupport.normalizeRow(rows.get(0));
            throw new BusinessException(
                    "该线路日期区间与运价 ID " + row.get("id") + " 重叠（" + row.get("effective_from") + " 至 "
                            + (row.get("effective_to") == null ? "永久" : row.get("effective_to")) + "）",
                    HttpStatus.CONFLICT
            );
        }
    }
}
