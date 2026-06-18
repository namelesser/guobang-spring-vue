package com.guobang.transport.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReportMapper {

    // 按公司分组统计运输趟次、总重量、总运费及平均单价
    @Select("""
            SELECT company, sender, receiver, plate_no,
                   COUNT(*) AS trips,
                   COALESCE(SUM(net_weight), 0) AS total_weight,
                   COALESCE(SUM(total_cost), 0) AS total_freight,
                   CASE WHEN SUM(net_weight) > 0 THEN ROUND(SUM(total_cost)/SUM(net_weight), 2) ELSE 0 END AS avg_rate
            FROM records
            WHERE record_date BETWEEN #{startDate} AND #{endDate}
              AND (#{company} = '' OR company = #{company})
              AND (#{sender} = '' OR sender = #{sender})
              AND (#{receiver} = '' OR receiver = #{receiver})
              AND (#{plateNo} = '' OR plate_no = #{plateNo})
            GROUP BY company, sender, receiver, plate_no
            ORDER BY total_freight DESC
            """)
    List<Map<String, Object>> byCompany(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                         @Param("company") String company, @Param("sender") String sender,
                                         @Param("receiver") String receiver, @Param("plateNo") String plateNo);

    // 统计运费最高的前5个收货方
    @Select("""
            SELECT receiver AS company,
                   COUNT(*) AS trips,
                   COALESCE(SUM(net_weight), 0) AS total_weight,
                   COALESCE(SUM(total_cost), 0) AS total_freight
            FROM records
            WHERE record_date BETWEEN #{startDate} AND #{endDate}
              AND (#{company} = '' OR company = #{company})
              AND (#{sender} = '' OR sender = #{sender})
              AND (#{receiver} = '' OR receiver = #{receiver})
              AND (#{plateNo} = '' OR plate_no = #{plateNo})
            GROUP BY receiver
            ORDER BY total_freight DESC
            LIMIT 5
            """)
    List<Map<String, Object>> top5Consignee(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                             @Param("company") String company, @Param("sender") String sender,
                                             @Param("receiver") String receiver, @Param("plateNo") String plateNo);

    // 统计指定条件下的总趟次、总重量和总运费汇总
    @Select("""
            SELECT COUNT(*) AS trips, COALESCE(SUM(net_weight), 0) AS total_weight, COALESCE(SUM(total_cost), 0) AS total_freight
            FROM records WHERE record_date BETWEEN #{startDate} AND #{endDate}
              AND (#{company} = '' OR company = #{company})
              AND (#{sender} = '' OR sender = #{sender})
              AND (#{receiver} = '' OR receiver = #{receiver})
              AND (#{plateNo} = '' OR plate_no = #{plateNo})
            """)
    Map<String, Object> totals(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                @Param("company") String company, @Param("sender") String sender,
                                @Param("receiver") String receiver, @Param("plateNo") String plateNo);

    // 按公司分组统计，支持关键词模糊搜索（发货方/收货方/公司名）
    @Select("""
            SELECT company, sender, receiver, plate_no,
                   COUNT(*) AS trips,
                   COALESCE(SUM(net_weight), 0) AS total_weight,
                   COALESCE(SUM(total_cost), 0) AS total_freight,
                   CASE WHEN SUM(net_weight) > 0 THEN ROUND(SUM(total_cost)/SUM(net_weight), 2) ELSE 0 END AS avg_rate
            FROM records
            WHERE record_date BETWEEN #{startDate} AND #{endDate}
              AND (#{company} = '' OR company = #{company})
              AND (#{sender} = '' OR sender = #{sender})
              AND (#{receiver} = '' OR receiver = #{receiver})
              AND (#{plateNo} = '' OR plate_no = #{plateNo})
              AND (COALESCE(sender,'')||COALESCE(receiver,'')||COALESCE(company,'')) ILIKE '%' || #{kw} || '%'
            GROUP BY company, sender, receiver, plate_no
            ORDER BY total_freight DESC
            """)
    List<Map<String, Object>> byCompanyWithKeyword(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                                    @Param("kw") String keyword, @Param("company") String company,
                                                    @Param("sender") String sender, @Param("receiver") String receiver,
                                                    @Param("plateNo") String plateNo);

    // 统计前5个收货方，支持关键词模糊搜索
    @Select("""
            SELECT receiver AS company,
                   COUNT(*) AS trips,
                   COALESCE(SUM(net_weight), 0) AS total_weight,
                   COALESCE(SUM(total_cost), 0) AS total_freight
            FROM records
            WHERE record_date BETWEEN #{startDate} AND #{endDate}
              AND (#{company} = '' OR company = #{company})
              AND (#{sender} = '' OR sender = #{sender})
              AND (#{receiver} = '' OR receiver = #{receiver})
              AND (#{plateNo} = '' OR plate_no = #{plateNo})
              AND (COALESCE(sender,'')||COALESCE(receiver,'')||COALESCE(company,'')) ILIKE '%' || #{kw} || '%'
            GROUP BY receiver
            ORDER BY total_freight DESC
            LIMIT 5
            """)
    List<Map<String, Object>> top5ConsigneeWithKeyword(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                                        @Param("kw") String keyword, @Param("company") String company,
                                                        @Param("sender") String sender, @Param("receiver") String receiver,
                                                        @Param("plateNo") String plateNo);
}
