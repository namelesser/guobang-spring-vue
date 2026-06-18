package com.guobang.transport.report;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.common.DateRange;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.mapper.ReportMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 月度报表服务，负责按公司、收货单位等维度统计运输数据
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportMapper reportMapper;

    /**
     * 生成月度报表
     *
     * @param month    月份（yyyy-MM）
     * @param onDate   指定日期
     * @param start    开始日期
     * @param end      结束日期
     * @param keyword  搜索关键词
     * @param company  开单公司
     * @param sender   发货单位
     * @param receiver 收货单位
     * @param plateNo  车牌号
     * @return 月度报表数据
     */
    public Map<String, Object> monthly(String month, String onDate, String start, String end,
                                        String keyword, String company, String sender, String receiver, String plateNo) {
        // 优先级：month > onDate > start/end > 默认当月
        DateRange range;
        if (month != null && !month.isBlank()) {
            // 按月份解析日期范围
            range = DateRange.parseMonth(month.trim());
        } else if (onDate != null && !onDate.isBlank()) {
            // 指定日期：取该日期所在月份的第一天和最后一天
            LocalDate ld = LocalDate.parse(onDate.trim().substring(0, 10));
            range = new DateRange(ld.withDayOfMonth(1), ld.withDayOfMonth(ld.lengthOfMonth()));
        } else if (start != null || end != null) {
            // 自定义起止日期
            range = new DateRange(start == null ? null : LocalDate.parse(start.trim().substring(0, 10)),
                    end == null ? null : LocalDate.parse(end.trim().substring(0, 10)));
        } else {
            // 默认取当月
            LocalDate today = LocalDate.now();
            range = new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        }
        LocalDate startDate = range.start();
        LocalDate endDate = range.end();
        // 开始和结束日期不能为空
        if (startDate == null || endDate == null) {
            throw new BusinessException("开始日期和结束日期不能为空", HttpStatus.BAD_REQUEST);
        }

        // 对筛选条件做去空格处理
        String co = company == null ? "" : company.trim();
        String se = sender == null ? "" : sender.trim();
        String re = receiver == null ? "" : receiver.trim();
        String pl = plateNo == null ? "" : plateNo.trim();

        // 判断是否有关键词搜索
        boolean hasKw = keyword != null && !keyword.isBlank();
        List<Map<String, Object>> rows;
        List<Map<String, Object>> topRows;
        Map<String, Object> totals;

        // 根据是否带关键词选择不同的查询方法
        if (hasKw) {
            rows = reportMapper.byCompanyWithKeyword(startDate, endDate, keyword.trim(), co, se, re, pl);
            topRows = reportMapper.top5ConsigneeWithKeyword(startDate, endDate, keyword.trim(), co, se, re, pl);
        } else {
            rows = reportMapper.byCompany(startDate, endDate, co, se, re, pl);
            topRows = reportMapper.top5Consignee(startDate, endDate, co, se, re, pl);
        }
        // 查询汇总数据（总车次、总重量、总运费）
        totals = reportMapper.totals(startDate, endDate, co, se, re, pl);

        // 组装报表结果
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("period", Map.of("start", startDate.toString(), "end", endDate.toString()));
        // 汇总数据为空时使用默认零值
        Map<String, Object> gt = totals == null ? Map.of("trips", 0, "total_weight", 0, "total_freight", 0) : DbSupport.normalizeRow(totals);
        report.put("grand_total", gt);
        report.put("groups", DbSupport.normalizeRows(rows));
        report.put("top5_consignee", DbSupport.normalizeRows(topRows));
        return report;
    }
}
