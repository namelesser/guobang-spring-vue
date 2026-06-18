package com.guobang.transport.report;

import com.guobang.transport.common.Api;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月度报表控制器，提供运输数据统计接口
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;

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
     * @param plate_no 车牌号
     * @return 月度报表数据
     */
    @GetMapping("/monthly")
    public Map<String, Object> monthly(@RequestParam String month,
                                       @RequestParam(required = false) String onDate,
                                       @RequestParam(required = false) String start,
                                       @RequestParam(required = false) String end,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String company,
                                       @RequestParam(required = false) String sender,
                                       @RequestParam(required = false) String receiver,
                                       @RequestParam(required = false) String plate_no) {
        // 调用服务层生成月度报表并返回
        return Api.ok(service.monthly(month, onDate, start, end, keyword, company, sender, receiver, plate_no));
    }
}
