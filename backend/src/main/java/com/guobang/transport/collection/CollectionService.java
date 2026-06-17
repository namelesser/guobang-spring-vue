package com.guobang.transport.collection;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.db.DbSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CollectionService {
    private static final List<String> CATEGORIES = List.of("company", "sender", "receiver", "plate");
    private final JdbcTemplate jdbc;

    public CollectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean validCategory(String category) {
        return CATEGORIES.contains(category);
    }

    public List<Map<String, Object>> list(String category) {
        if (!validCategory(category)) {
            throw new BusinessException("无效的 category", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return DbSupport.normalizeRows(jdbc.queryForList(
                "SELECT * FROM collections WHERE category=? ORDER BY value",
                category
        ));
    }

    public Map<String, List<Map<String, Object>>> all() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            result.put(category, list(category));
        }
        return result;
    }

    public List<String> values(String category) {
        return list(category).stream()
                .map(row -> DbSupport.trim(row.get("value")))
                .filter(value -> !value.isBlank() && !"未知".equals(value))
                .toList();
    }

    public int create(String category, String value) {
        category = DbSupport.trim(category);
        value = DbSupport.trim(value);
        if (!validCategory(category) || value.isBlank()) {
            throw new BusinessException("category 和 value 不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        try {
            return jdbc.queryForObject(
                    "INSERT INTO collections(category, value) VALUES (?, ?) RETURNING id",
                    Integer.class,
                    category,
                    value
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("'" + value + "' 已在 " + category + " 集合中存在", HttpStatus.CONFLICT);
        }
    }

    public boolean update(int id, String value) {
        value = DbSupport.trim(value);
        if (value.isBlank()) {
            throw new BusinessException("value 不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        try {
            return jdbc.update("UPDATE collections SET value=? WHERE id=?", value, id) > 0;
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("'" + value + "' 已存在", HttpStatus.CONFLICT);
        }
    }

    public boolean delete(int id) {
        return jdbc.update("DELETE FROM collections WHERE id=?", id) > 0;
    }

    public void validateRecordCollections(Map<String, Object> data) {
        requireCollection("sender", "sender", "发货单位", data);
        requireCollection("receiver", "receiver", "收货单位", data);
        requireCollection("company", "company", "开单公司", data);
        requireCollection("plate_no", "plate", "车牌号", data);
    }

    private void requireCollection(String field, String category, String label, Map<String, Object> data) {
        String value = DbSupport.trim(data.get(field));
        if (value.isBlank()) {
            throw new BusinessException(label + "不能为空，且必须从基础资料集合中选择", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM collections WHERE category=? AND BTRIM(value)=?",
                Integer.class,
                category,
                value
        );
        if (count == null || count == 0) {
            throw new BusinessException(label + "“" + value + "”不在基础资料集合中，请先维护基础资料", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
