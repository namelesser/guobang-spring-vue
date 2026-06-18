package com.guobang.transport.quality;

import com.guobang.transport.db.DbSupport;
import com.guobang.transport.mapper.DataQualityMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据质量服务，负责检查运输记录中的异常数据
 */
@Service
@RequiredArgsConstructor
public class DataQualityService {
    private final DataQualityMapper dqMapper;

    /**
     * 获取数据质量检查报告
     *
     * @return 包含各类检查结果的报告
     */
    public Map<String, Object> get() {
        // 组装最终报告：包含各类检查结果
        Map<String, Object> result = new LinkedHashMap<>();

        // 未来日期检查
        result.put("future_dates", DbSupport.normalizeRows(dqMapper.futureDates()));

        // 缺图片记录
        result.put("missing_images", DbSupport.normalizeRows(dqMapper.missingImages()));

        // 缺净重记录
        result.put("missing_weights", DbSupport.normalizeRows(dqMapper.missingWeights()));

        // 缺运价记录
        result.put("missing_rates", DbSupport.normalizeRows(dqMapper.missingRates()));

        // OCR 卡住任务
        result.put("stale_ocr_tasks", DbSupport.normalizeRows(dqMapper.staleOcrTasks()));

        // 重复单号（按 order_no 聚合）
        result.put("duplicate_orders", DbSupport.normalizeRows(dqMapper.duplicateOrdersGrouped()));

        // 开单公司与基础资料不一致
        List<Map<String, Object>> senderMismatch = DbSupport.normalizeRows(dqMapper.senderCompanyMismatch());
        result.put("sender_company_mismatch", senderMismatch);

        // 收货单位不在基础资料
        result.put("receiver_not_in_collection", DbSupport.normalizeRows(dqMapper.receiverNotInCollection()));

        // 车牌号不在基础资料
        result.put("plate_no_not_in_collection", DbSupport.normalizeRows(dqMapper.plateNoNotInCollection()));

        // 基础资料异常
        List<Map<String, Object>> collectionAnomalies = DbSupport.normalizeRows(dqMapper.collectionAnomalies());
        Map<String, Object> collectionChecks = new LinkedHashMap<>();
        for (Map<String, Object> row : collectionAnomalies) {
            String field = (String) row.get("field");
            collectionChecks.computeIfAbsent(field, k -> {
                Map<String, Object> check = new LinkedHashMap<>();
                check.put("field", field);
                check.put("label", getFieldLabel(field));
                check.put("items", new ArrayList<>());
                return check;
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> check = (Map<String, Object>) collectionChecks.get(field);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) check.get("items");
            items.add(row);
        }
        result.put("collection_checks", new ArrayList<>(collectionChecks.values()));

        return result;
    }

    /**
     * 获取字段中文标签
     *
     * @param field 字段名
     * @return 中文标签
     */
    private String getFieldLabel(String field) {
        return switch (field) {
            case "company" -> "开单公司";
            case "sender" -> "发货单位";
            case "receiver" -> "收货单位";
            case "plate_no" -> "车牌号";
            case "material" -> "物料";
            case "site" -> "站点";
            default -> field;
        };
    }

    /**
     * 创建基础资料检查结果
     *
     * @param field 字段名
     * @param label 中文标签
     * @param items 异常项列表
     * @return 检查结果 Map
     */
    private Map<String, Object> createCollectionCheck(String field, String label, List<Map<String, Object>> items) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("field", field);
        check.put("label", label);
        check.put("items", items);
        return check;
    }

    /**
     * 获取指定检查项的详情（不分页）
     *
     * @param checkId 检查项 ID
     * @return 检查详情
     */
    public Map<String, Object> detail(String checkId) {
        // 根据检查项 ID 调用对应的查询方法
        List<Map<String, Object>> items = switch (checkId) {
            case "senderCompanyMismatch" -> DbSupport.normalizeRows(dqMapper.senderCompanyMismatch());
            case "transportTypeGuess" -> DbSupport.normalizeRows(dqMapper.transportTypeStats());
            case "duplicateOrders" -> DbSupport.normalizeRows(dqMapper.duplicateOrders());
            case "companyNotInCollection" -> DbSupport.normalizeRows(dqMapper.senderNotInCollection());
            case "receiverNotInCollection" -> DbSupport.normalizeRows(dqMapper.receiverNotInCollection());
            case "plate_noNotInCollection" -> DbSupport.normalizeRows(dqMapper.plateNoNotInCollection());
            case "unreviewedRecords" -> DbSupport.normalizeRows(dqMapper.unreviewedRecords());
            default -> null;
        };
        // 未知检查类型返回 null
        if (items == null) {
            return null;
        }
        // 组装详情结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("check_id", checkId);
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    /**
     * 获取指定检查项的详情（分页）
     *
     * @param checkId 检查项 ID
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 包含 items 和 total 的检查详情
     */
    public Map<String, Object> detail(String checkId, int offset, int limit) {
        // 校正分页参数
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), 200);

        List<Map<String, Object>> items;
        int total;
        // 根据检查项 ID 分别查询总数和分页数据
        switch (checkId) {
            case "senderCompanyMismatch":
                total = dqMapper.countSenderCompanyMismatch();
                items = DbSupport.normalizeRows(dqMapper.senderCompanyMismatchPaginated(offset, limit));
                break;
            case "transportTypeGuess":
                // 运输类型统计无需分页，直接查全量
                items = DbSupport.normalizeRows(dqMapper.transportTypeStats());
                total = items.size();
                break;
            case "duplicateOrders":
                total = dqMapper.duplicateOrders().size();
                items = DbSupport.normalizeRows(dqMapper.duplicateOrdersPaginated(offset, limit));
                break;
            case "companyNotInCollection":
                total = dqMapper.countSenderNotInCollection();
                items = DbSupport.normalizeRows(dqMapper.senderNotInCollectionPaginated(offset, limit));
                break;
            case "receiverNotInCollection":
                total = dqMapper.countReceiverNotInCollection();
                items = DbSupport.normalizeRows(dqMapper.receiverNotInCollectionPaginated(offset, limit));
                break;
            case "plate_noNotInCollection":
                total = dqMapper.countPlateNoNotInCollection();
                items = DbSupport.normalizeRows(dqMapper.plateNoNotInCollectionPaginated(offset, limit));
                break;
            case "unreviewedRecords":
                total = dqMapper.countUnreviewed();
                items = DbSupport.normalizeRows(dqMapper.unreviewedRecordsPaginated(offset, limit));
                break;
            default:
                // 未知检查类型返回 null
                return null;
        }

        // 组装分页详情结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("check_id", checkId);
        result.put("items", items);
        result.put("total", total);
        return result;
    }
}
