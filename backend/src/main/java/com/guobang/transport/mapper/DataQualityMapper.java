package com.guobang.transport.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DataQualityMapper {

    // 统计发货方与公司名不一致的记录数量
    @Select("SELECT COUNT(*) FROM records WHERE sender IS NOT NULL AND company IS NOT NULL AND sender != company")
    int countSenderCompanyMismatch();

    // 查询发货方与公司名不一致的记录详情
    @Select("""
            SELECT r.id, r.sender, r.company, r.record_date, r.order_no
            FROM records r
            WHERE r.sender IS NOT NULL AND r.company IS NOT NULL AND r.sender != r.company
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> senderCompanyMismatch();

    // 统计重量异常的记录数量（>=100或<=0）
    @Select("SELECT COUNT(*) FROM records WHERE net_weight >= 100 OR net_weight <= 0")
    int countSuspiciousWeight();

    // 查询重量异常的记录详情
    @Select("""
            SELECT r.id, r.net_weight, r.sender, r.receiver, r.record_date, r.order_no
            FROM records r
            WHERE r.net_weight >= 100 OR r.net_weight <= 0
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> suspiciousWeight();

    // 统计未审核记录数量
    @Select("SELECT COUNT(*) FROM records WHERE reviewed = 0")
    int countUnreviewed();

    // 查询未审核记录详情
    @Select("""
            SELECT r.id, r.sender, r.receiver, r.company, r.record_date, r.order_no
            FROM records r
            WHERE r.reviewed = 0
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> unreviewedRecords();

    // 数据质量汇总统计（总记录数、未审核数、未审核比例、缺失重量数、异常重量数）
    @Select("""
            SELECT
              COUNT(*) AS total_records,
              COUNT(CASE WHEN reviewed = 0 THEN 1 END) AS unreviewed_count,
              COALESCE(SUM(CASE WHEN reviewed = 0 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) AS unreviewed_ratio,
              COUNT(CASE WHEN net_weight IS NULL THEN 1 END) AS missing_net_weight_count,
              COUNT(CASE WHEN net_weight >= 100 OR net_weight <= 0 THEN 1 END) AS suspicious_weight_count
            FROM records
            """)
    Map<String, Object> summary();

    // 按收货方类型分组统计运输类型分布（焦化厂、热电厂等）
    @Select("""
            SELECT
              CASE
                WHEN receiver ILIKE '%焦化厂%' OR receiver ILIKE '%焦化%' THEN '焦化厂'
                WHEN receiver ILIKE '%热电厂%' OR receiver ILIKE '%电厂%' OR receiver ILIKE '%热电%' THEN '热电厂'
                WHEN receiver ILIKE '%钢铁厂%' OR receiver ILIKE '%钢铁%' THEN '钢铁厂'
                WHEN receiver ILIKE '%水泥厂%' OR receiver ILIKE '%水泥%' THEN '水泥厂'
                WHEN receiver ILIKE '%物流%' OR receiver ILIKE '%物流园%' THEN '物流公司'
                WHEN receiver ILIKE '%煤矿%' OR receiver ILIKE '%煤业%' THEN '煤矿'
                WHEN receiver ILIKE '%洗煤厂%' OR receiver ILIKE '%洗煤%' THEN '洗煤厂'
                WHEN receiver ILIKE '%搅拌站%' THEN '搅拌站'
                ELSE '其他'
              END AS type,
              COUNT(*) AS cnt,
              COALESCE(SUM(net_weight), 0) AS total_weight
            FROM records
            GROUP BY type
            ORDER BY cnt DESC
            """)
    List<Map<String, Object>> transportTypeStats();

    // 统计发货方不在下拉选项集合中的记录数量
    @Select("SELECT COUNT(*) FROM records r WHERE r.sender IS NOT NULL AND r.sender != '' "
            + "AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'sender' AND c.value = r.sender)")
    int countSenderNotInCollection();

    // 统计收货方不在下拉选项集合中的记录数量
    @Select("SELECT COUNT(*) FROM records r WHERE r.receiver IS NOT NULL AND r.receiver != '' "
            + "AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'receiver' AND c.value = r.receiver)")
    int countReceiverNotInCollection();

    // 统计车牌号不在下拉选项集合中的记录数量
    @Select("SELECT COUNT(*) FROM records r WHERE r.plate_no IS NOT NULL AND r.plate_no != '' "
            + "AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'plate_no' AND c.value = r.plate_no)")
    int countPlateNoNotInCollection();

    // 查询发货方不在下拉选项集合中的记录详情
    @Select("""
            SELECT r.id, r.sender AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.sender IS NOT NULL AND r.sender != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'sender' AND c.value = r.sender)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> senderNotInCollection();

    // 查询收货方不在下拉选项集合中的记录详情
    @Select("""
            SELECT r.id, r.receiver AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.receiver IS NOT NULL AND r.receiver != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'receiver' AND c.value = r.receiver)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> receiverNotInCollection();

    // 查询车牌号不在下拉选项集合中的记录详情
    @Select("""
            SELECT r.id, r.plate_no AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.plate_no IS NOT NULL AND r.plate_no != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'plate_no' AND c.value = r.plate_no)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> plateNoNotInCollection();

    // 查询重复订单号的记录（同一订单号和公司出现多次）
    @Select("""
            SELECT r.id, r.order_no, r.company
            FROM records r
            WHERE r.order_no IS NOT NULL AND r.order_no != ''
              AND EXISTS (
                SELECT 1 FROM records r2
                WHERE r2.order_no = r.order_no AND r2.company = r.company AND r2.id < r.id
              )
            ORDER BY r.order_no, r.id
            """)
    List<Map<String, Object>> duplicateOrders();

    // 分页查询发货方与公司名不一致的记录
    @Select("""
            SELECT r.id, r.sender, r.company, r.record_date, r.order_no
            FROM records r
            WHERE r.sender IS NOT NULL AND r.company IS NOT NULL AND r.sender != r.company
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> senderCompanyMismatchPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 分页查询未审核记录
    @Select("""
            SELECT r.id, r.sender, r.receiver, r.company, r.record_date, r.order_no
            FROM records r
            WHERE r.reviewed = 0
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> unreviewedRecordsPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 分页查询发货方不在下拉选项集合中的记录
    @Select("""
            SELECT r.id, r.sender AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.sender IS NOT NULL AND r.sender != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'sender' AND c.value = r.sender)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> senderNotInCollectionPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 分页查询收货方不在下拉选项集合中的记录
    @Select("""
            SELECT r.id, r.receiver AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.receiver IS NOT NULL AND r.receiver != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'receiver' AND c.value = r.receiver)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> receiverNotInCollectionPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 分页查询车牌号不在下拉选项集合中的记录
    @Select("""
            SELECT r.id, r.plate_no AS value, r.record_date, r.order_no
            FROM records r
            WHERE r.plate_no IS NOT NULL AND r.plate_no != ''
              AND NOT EXISTS (SELECT 1 FROM collections c WHERE c.category = 'plate_no' AND c.value = r.plate_no)
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> plateNoNotInCollectionPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 分页查询重复订单号的记录
    @Select("""
            SELECT r.id, r.order_no, r.company
            FROM records r
            WHERE r.order_no IS NOT NULL AND r.order_no != ''
              AND EXISTS (
                SELECT 1 FROM records r2
                WHERE r2.order_no = r.order_no AND r2.company = r.company AND r2.id < r.id
              )
            ORDER BY r.order_no, r.id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> duplicateOrdersPaginated(@Param("offset") int offset, @Param("limit") int limit);

    // 查询未来日期的记录
    @Select("""
            SELECT r.id, r.record_date, r.order_no, r.company
            FROM records r
            WHERE r.record_date > CURRENT_DATE
            ORDER BY r.record_date DESC, r.id DESC
            """)
    List<Map<String, Object>> futureDates();

    // 查询缺少关联图片的记录
    @Select("""
            SELECT r.id, r.record_date, r.order_no, r.company, r.ocr_status
            FROM records r
            LEFT JOIN record_images ri ON ri.record_id = r.id
            WHERE ri.id IS NULL
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> missingImages();

    // 查询缺少净重的记录
    @Select("""
            SELECT r.id, r.record_date, r.order_no, r.company, r.receiver, r.ocr_status, r.net_weight
            FROM records r
            WHERE r.net_weight IS NULL OR r.net_weight = 0
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> missingWeights();

    // 查询缺少运价的记录
    @Select("""
            SELECT r.id, r.record_date, r.order_no, r.company, r.receiver, r.freight_rate
            FROM records r
            WHERE r.freight_rate IS NULL OR r.freight_rate = 0
            ORDER BY r.record_date DESC NULLS LAST, r.id DESC
            """)
    List<Map<String, Object>> missingRates();

    // 查询卡住的 OCR 任务
    @Select("""
            SELECT t.id AS image_id, t.record_id, t.file_name, t.retry_count, t.started_at
            FROM ocr_tasks t
            WHERE t.status = 'processing'
              AND t.started_at < NOW() - INTERVAL '30 minutes'
            ORDER BY t.started_at ASC
            """)
    List<Map<String, Object>> staleOcrTasks();

    // 查询重复单号（按 order_no 聚合）
    @Select("""
            SELECT r.order_no, COUNT(*) AS dup_count, MIN(r.id) AS first_id
            FROM records r
            WHERE r.order_no IS NOT NULL AND r.order_no != ''
            GROUP BY r.order_no
            HAVING COUNT(*) > 1
            ORDER BY dup_count DESC
            """)
    List<Map<String, Object>> duplicateOrdersGrouped();

    // 查询基础资料异常（collections 表中无效数据）
    @Select("""
            SELECT c.category AS field, c.value, COUNT(*) AS cnt
            FROM collections c
            WHERE c.value IS NULL OR c.value = ''
            GROUP BY c.category, c.value
            """)
    List<Map<String, Object>> collectionAnomalies();
}
