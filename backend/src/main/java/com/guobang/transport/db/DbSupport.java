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
        // 工具类禁止实例化
    }

    public static String str(Object value) {
        // 将任意值转为字符串，null返回空字符串
        return value == null ? "" : String.valueOf(value);
    }

    public static String trim(Object value) {
        // 转为字符串并去除首尾空格
        return str(value).trim();
    }

    public static Integer intValue(Object value) {
        // 空值或空白字符串返回null
        if (value == null || str(value).isBlank()) {
            return null;
        }
        // 如果是Number类型直接取整数值
        if (value instanceof Number number) {
            return number.intValue();
        }
        // 字符串类型解析为整数
        return Integer.parseInt(str(value).trim());
    }

    public static BigDecimal decimal(Object value) {
        // 空值或空白字符串返回null
        if (value == null || str(value).isBlank()) {
            return null;
        }
        // 已经是BigDecimal类型直接返回
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        // 其他Number类型转为BigDecimal
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        // 字符串类型解析为BigDecimal
        return new BigDecimal(str(value).trim());
    }

    public static LocalDate date(Object value) {
        // 空值或空白字符串返回null
        if (value == null || str(value).isBlank()) {
            return null;
        }
        // 已经是LocalDate类型直接返回
        if (value instanceof LocalDate d) {
            return d;
        }
        // SQL Date类型转为LocalDate
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        // 字符串类型截取前10个字符后解析为LocalDate
        return LocalDate.parse(str(value).substring(0, Math.min(10, str(value).length())));
    }

    public static String nullableBlank(Object value) {
        // null值直接返回null
        if (value == null) {
            return null;
        }
        // 空白字符串也返回null
        if (value instanceof String s && s.trim().isEmpty()) {
            return null;
        }
        // 其他情况转为字符串返回
        return value.toString();
    }

    public static List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        // 对每一行数据进行类型规范化处理
        return rows.stream().map(DbSupport::normalizeRow).toList();
    }

    public static Map<String, Object> normalizeRow(Map<String, Object> row) {
        // 创建输出Map保持插入顺序
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof BigDecimal bd) {
                // BigDecimal保持原样
                out.put(entry.getKey(), bd);
            } else if (value instanceof Date d) {
                // SQL Date转为ISO格式字符串
                out.put(entry.getKey(), d.toLocalDate().toString());
            } else if (value instanceof Timestamp t) {
                // Timestamp转为带空格分隔的日期时间字符串
                LocalDateTime ldt = t.toLocalDateTime();
                out.put(entry.getKey(), ldt.toString().replace('T', ' '));
            } else if (value instanceof byte[] bytes) {
                // 二进制数据保持原样
                out.put(entry.getKey(), bytes);
            } else {
                // 其他类型保持原样
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    public static String placeholders(int count) {
        // 用逗号连接指定数量的占位符
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
