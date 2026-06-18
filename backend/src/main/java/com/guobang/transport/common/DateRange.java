package com.guobang.transport.common;

import java.time.LocalDate;
import java.time.YearMonth;

public record DateRange(LocalDate start, LocalDate end) {
    public static DateRange parseMonth(String month) {
        // 解析月份字符串为YearMonth对象
        YearMonth ym = YearMonth.parse(month);
        // 返回该月第一天到最后一天的日期范围
        return new DateRange(ym.atDay(1), ym.atEndOfMonth());
    }
}
