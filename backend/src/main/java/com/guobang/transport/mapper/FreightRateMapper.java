package com.guobang.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guobang.transport.rate.FreightRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FreightRateMapper extends BaseMapper<FreightRate> {

    // 根据起运地、目的地和生效日期查询匹配的运费单价
    @Select("""
            SELECT id, price_per_ton, note FROM freight_rates
            WHERE (#{origin} ILIKE '%' || origin || '%' OR origin ILIKE '%' || #{origin2} || '%')
              AND (#{dest} ILIKE '%' || destination || '%' OR destination ILIKE '%' || #{dest2} || '%')
              AND effective_from <= #{from}
              AND (effective_to IS NULL OR effective_to >= #{to})
            ORDER BY effective_from DESC LIMIT 1
            """)
    Map<String, Object> lookup(@Param("origin") String origin, @Param("origin2") String origin2,
                               @Param("dest") String dest, @Param("dest2") String dest2,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    // 查询指定条件下的重叠运费记录（排除指定ID），用于校验日期冲突
    @Select("""
            SELECT id, effective_from, effective_to
            FROM freight_rates
            WHERE origin=#{origin} AND destination=#{destination} AND sender=#{sender}
              AND effective_from <= #{effectiveTo}
              AND COALESCE(effective_to, DATE '9999-12-31') >= #{effectiveFrom}
              AND id != #{excludeId}
            ORDER BY effective_from DESC LIMIT 1
            """)
    Map<String, Object> findOverlapping(@Param("origin") String origin, @Param("destination") String destination,
                                        @Param("sender") String sender,
                                        @Param("effectiveFrom") LocalDate effectiveFrom,
                                        @Param("effectiveTo") LocalDate effectiveTo,
                                        @Param("excludeId") long excludeId);

    // 查询指定条件下的重叠运费记录（不排除ID），用于新增时校验
    @Select("""
            SELECT id, effective_from, effective_to
            FROM freight_rates
            WHERE origin=#{origin} AND destination=#{destination} AND sender=#{sender}
              AND effective_from <= #{effectiveTo}
              AND COALESCE(effective_to, DATE '9999-12-31') >= #{effectiveFrom}
            ORDER BY effective_from DESC LIMIT 1
            """)
    Map<String, Object> findOverlappingNoExclude(@Param("origin") String origin, @Param("destination") String destination,
                                                  @Param("sender") String sender,
                                                  @Param("effectiveFrom") LocalDate effectiveFrom,
                                                  @Param("effectiveTo") LocalDate effectiveTo);

    // 根据起运地查询所有不重复的发货方
    @Select("SELECT DISTINCT sender FROM freight_rates WHERE origin=#{origin} AND sender != ''")
    List<String> findSendersByOrigin(@Param("origin") String origin);

    // 根据起运地查询所有不重复的目的地
    @Select("SELECT DISTINCT destination FROM freight_rates WHERE origin=#{origin} AND destination != ''")
    List<String> findDestinationsByOrigin(@Param("origin") String origin);
}
