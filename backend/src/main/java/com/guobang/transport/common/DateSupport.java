package com.guobang.transport.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class DateSupport {
    private DateSupport() {
        // 工具类禁止实例化
    }

    public static DateRange monthBounds(String month) {
        try {
            // 将null转换为空字符串并去除首尾空格
            YearMonth ym = YearMonth.parse(String.valueOf(month == null ? "" : month).trim());
            // 返回该月第一天到下个月第一天的日期范围（左闭右开）
            return new DateRange(ym.atDay(1), ym.plusMonths(1).atDay(1));
        } catch (DateTimeParseException ex) {
            // 月份格式不正确时抛出业务异常
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
    }

    public static LocalDate parseDate(Object value) {
        // 空值或空白字符串返回null
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        // 如果已经是LocalDate类型直接返回
        if (value instanceof LocalDate d) {
            return d;
        }
        // 转为字符串并去除首尾空格
        String text = String.valueOf(value).trim();
        // 截取前10个字符（YYYY-MM-DD格式）
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        try {
            // 解析日期字符串
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            // 日期格式不正确时抛出业务异常
            throw new BusinessException("记录日期格式应为 YYYY-MM-DD");
        }
    }

    public static void rejectFutureDate(Object value) {
        // 解析输入的日期
        LocalDate date = parseDate(value);
        // 获取当前日期
        LocalDate today = LocalDate.now();
        // 检查日期是否晚于今天
        if (date != null && date.isAfter(today)) {
            throw new BusinessException("记录日期不能晚于今天（" + today + "）");
        }
    }
}
