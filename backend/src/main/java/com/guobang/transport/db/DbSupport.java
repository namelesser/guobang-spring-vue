package com.guobang.transport.db;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class DbSupport {
    private DbSupport() {
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String trim(Object value) {
        return str(value).trim();
    }

    public static Integer intValue(Object value) {
        if (value == null || str(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(str(value).trim());
    }

    public static BigDecimal decimal(Object value) {
        if (value == null || str(value).isBlank()) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(str(value).trim());
    }

    public static LocalDate date(Object value) {
        if (value == null || str(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(str(value).substring(0, Math.min(10, str(value).length())));
    }

    public static Object nullableBlank(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s && s.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    public static List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(DbSupport::normalizeRow).toList();
    }

    public static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof BigDecimal bd) {
                out.put(entry.getKey(), bd);
            } else if (value instanceof Date d) {
                out.put(entry.getKey(), d.toLocalDate().toString());
            } else if (value instanceof Timestamp t) {
                LocalDateTime ldt = t.toLocalDateTime();
                out.put(entry.getKey(), ldt.toString().replace('T', ' '));
            } else if (value instanceof byte[] bytes) {
                out.put(entry.getKey(), bytes);
            } else {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    public static String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
