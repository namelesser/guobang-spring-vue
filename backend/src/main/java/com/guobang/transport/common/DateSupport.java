package com.guobang.transport.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class DateSupport {
    private DateSupport() {
    }

    public static DateRange monthBounds(String month) {
        try {
            YearMonth ym = YearMonth.parse(String.valueOf(month == null ? "" : month).trim());
            return new DateRange(ym.atDay(1), ym.plusMonths(1).atDay(1));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
    }

    public static LocalDate parseDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        String text = String.valueOf(value).trim();
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("记录日期格式应为 YYYY-MM-DD");
        }
    }

    public static void rejectFutureDate(Object value) {
        LocalDate date = parseDate(value);
        LocalDate today = LocalDate.now();
        if (date != null && date.isAfter(today)) {
            throw new BusinessException("记录日期不能晚于今天（" + today + "）");
        }
    }
}
