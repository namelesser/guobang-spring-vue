package com.guobang.transport.rate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guobang.transport.common.BusinessException;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.mapper.FreightRateMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 运价服务，负责线路运价的增删改查和查询
 */
@Service
@RequiredArgsConstructor
public class RateService {
    private final FreightRateMapper rateMapper;

    /**
     * 获取所有运价列表
     *
     * @return 运价列表
     */
    public List<Map<String, Object>> all() {
        // 按发货地、目的地升序，生效日期降序查询全部运价
        List<FreightRate> rates = rateMapper.selectList(new QueryWrapper<FreightRate>()
                .orderByAsc("origin", "destination")
                .orderByDesc("effective_from"));
        // 将实体列表转换为 Map 列表返回
        return rates.stream().map(this::toMap).toList();
    }

    /**
     * 分页获取运价列表
     *
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 包含 items 和 total 的 Map
     */
    public Map<String, Object> list(int offset, int limit) {
        // 校正分页参数，确保 offset 非负、limit 在 1~200 范围内
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), 200);

        // 查询运价总数用于前端分页计算
        Long total = rateMapper.selectCount(new QueryWrapper<>());

        // 按 ID 降序分页查询运价记录
        List<FreightRate> rates = rateMapper.selectList(new QueryWrapper<FreightRate>()
                .orderByDesc("id")
                .last("LIMIT " + limit + " OFFSET " + offset));

        // 转换为 Map 列表
        List<Map<String, Object>> items = rates.stream().map(this::toMap).toList();

        // 组装分页结果：包含数据项和总数
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    private Map<String, Object> toMap(FreightRate rate) {
        // 将运价实体转换为 Map，日期字段为空时返回 null，否则转为字符串
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rate.getId());
        out.put("origin", rate.getOrigin());
        out.put("destination", rate.getDestination());
        out.put("sender", rate.getSender());
        out.put("price_per_ton", rate.getPricePerTon());
        out.put("effective_from", rate.getEffectiveFrom() == null ? null : rate.getEffectiveFrom().toString());
        out.put("effective_to", rate.getEffectiveTo() == null ? null : rate.getEffectiveTo().toString());
        out.put("note", rate.getNote());
        out.put("created_at", rate.getCreatedAt());
        return out;
    }

    /**
     * 创建运价
     *
     * @param body 运价数据
     * @return 新创建的运价 ID
     */
    public int create(Map<String, Object> body) {
        // 标准化输入数据（类型转换、去空格等）
        Map<String, Object> data = normalize(body);
        // 校验生效日期区间是否与已有运价重叠
        validatePeriod(data, null);
        // 构建运价实体并设置各字段
        FreightRate rate = new FreightRate();
        rate.setOrigin((String) data.get("origin"));
        rate.setDestination((String) data.get("destination"));
        rate.setSender((String) data.get("sender"));
        rate.setPricePerTon((BigDecimal) data.get("price_per_ton"));
        rate.setEffectiveFrom((LocalDate) data.get("effective_from"));
        rate.setEffectiveTo((LocalDate) data.get("effective_to"));
        rate.setNote((String) data.get("note"));
        // 插入数据库并返回自增主键
        rateMapper.insert(rate);
        return rate.getId().intValue();
    }

    /**
     * 更新运价
     *
     * @param id   运价 ID
     * @param body 更新数据
     * @return 是否更新成功
     */
    public boolean update(int id, Map<String, Object> body) {
        // 根据 ID 查询已有运价记录
        FreightRate existing = rateMapper.selectById((long) id);
        if (existing == null) {
            return false;
        }
        // 将已有数据复制到 merged 中作为基础值
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("origin", existing.getOrigin());
        merged.put("destination", existing.getDestination());
        merged.put("sender", existing.getSender());
        merged.put("price_per_ton", existing.getPricePerTon());
        merged.put("effective_from", existing.getEffectiveFrom());
        merged.put("effective_to", existing.getEffectiveTo());
        merged.put("note", existing.getNote());
        // 用请求体中的非空字段覆盖已有值（部分更新）
        body.forEach((k, v) -> {
            if (v != null) {
                merged.put(k, v);
            }
        });
        // 标准化合并后的数据
        Map<String, Object> normalized = normalize(merged);
        // 校验日期区间重叠（排除自身 ID）
        validatePeriod(normalized, id);

        // 仅更新请求体中明确传入的字段
        FreightRate rate = new FreightRate();
        rate.setId((long) id);
        if (body.containsKey("origin")) rate.setOrigin((String) normalized.get("origin"));
        if (body.containsKey("destination")) rate.setDestination((String) normalized.get("destination"));
        if (body.containsKey("sender")) rate.setSender((String) normalized.get("sender"));
        if (body.containsKey("price_per_ton")) rate.setPricePerTon((BigDecimal) normalized.get("price_per_ton"));
        if (body.containsKey("effective_from")) rate.setEffectiveFrom((LocalDate) normalized.get("effective_from"));
        if (body.containsKey("effective_to")) rate.setEffectiveTo((LocalDate) normalized.get("effective_to"));
        if (body.containsKey("note")) rate.setNote((String) normalized.get("note"));
        // 执行按 ID 更新，返回是否成功
        return rateMapper.updateById(rate) > 0;
    }

    /**
     * 删除运价
     *
     * @param id 运价 ID
     * @return 是否删除成功
     */
    public boolean delete(int id) {
        // 按 ID 删除运价记录，返回删除是否成功
        return rateMapper.deleteById((long) id) > 0;
    }

    /**
     * 查询指定线路和日期的运价
     *
     * @param origin      发货地
     * @param destination 目的地
     * @param onDate      日期
     * @return 运价信息
     */
    public Map<String, Object> lookup(String origin, String destination, String onDate) {
        // 任一参数为空则直接返回 null
        if (DbSupport.trim(origin).isBlank() || DbSupport.trim(destination).isBlank() || DbSupport.trim(onDate).isBlank()) {
            return null;
        }
        // 解析日期字符串，截取前 10 位（yyyy-MM-dd）
        LocalDate date = LocalDate.parse(onDate.substring(0, 10));
        // 调用自定义 SQL 查询匹配的运价（同一发货地/目的地可能有多条记录）
        Map<String, Object> row = rateMapper.lookup(origin, origin, destination, destination, date, date);
        // 对结果行做标准化处理（如数值精度统一）
        return row == null ? null : DbSupport.normalizeRow(row);
    }

    /**
     * 根据 ID 获取运价
     *
     * @param id 运价 ID
     * @return 运价信息
     */
    public Map<String, Object> find(int id) {
        // 按 ID 查询运价记录
        FreightRate rate = rateMapper.selectById((long) id);
        if (rate == null) {
            return null;
        }
        // 构建返回 Map，日期字段为空则返回 null
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rate.getId());
        out.put("origin", rate.getOrigin());
        out.put("destination", rate.getDestination());
        out.put("sender", rate.getSender());
        out.put("price_per_ton", rate.getPricePerTon());
        out.put("effective_from", rate.getEffectiveFrom() == null ? null : rate.getEffectiveFrom().toString());
        out.put("effective_to", rate.getEffectiveTo() == null ? null : rate.getEffectiveTo().toString());
        out.put("note", rate.getNote());
        out.put("created_at", rate.getCreatedAt());
        return out;
    }

    private Map<String, Object> normalize(Map<String, Object> input) {
        // 对输入字段进行去空格和类型转换
        String origin = DbSupport.trim(input.get("origin"));
        String destination = DbSupport.trim(input.get("destination"));
        String sender = DbSupport.trim(input.get("sender"));
        LocalDate from = DbSupport.date(input.get("effective_from"));
        LocalDate to = DbSupport.date(input.get("effective_to"));
        BigDecimal price = DbSupport.decimal(input.get("price_per_ton"));
        // 必填字段校验：开单公司、收货单位、单价和生效日期不能为空
        if (origin.isBlank() || destination.isBlank() || from == null || price == null) {
            throw new BusinessException("开单公司、收货单位、单价和生效日期不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 单价不能为负数
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("单价不能为负数", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 返回标准化后的数据 Map
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("origin", origin);
        out.put("destination", destination);
        out.put("sender", sender);
        out.put("price_per_ton", price);
        out.put("effective_from", from);
        out.put("effective_to", to);
        out.put("note", DbSupport.trim(input.get("note")));
        return out;
    }

    private void validatePeriod(Map<String, Object> data, Integer excludeId) {
        LocalDate from = (LocalDate) data.get("effective_from");
        LocalDate to = (LocalDate) data.get("effective_to");
        // 失效日期不能早于生效日期
        if (to != null && to.isBefore(from)) {
            throw new BusinessException("失效日期不能早于生效日期", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 若未设置失效日期，使用 9999-12-31 作为永久有效标记
        LocalDate effectiveTo = to == null ? LocalDate.of(9999, 12, 31) : to;
        Map<String, Object> row;
        // 更新时排除自身 ID，创建时不排除
        if (excludeId != null) {
            row = rateMapper.findOverlapping(
                    (String) data.get("origin"), (String) data.get("destination"), (String) data.get("sender"),
                    from, effectiveTo, excludeId);
        } else {
            row = rateMapper.findOverlappingNoExclude(
                    (String) data.get("origin"), (String) data.get("destination"), (String) data.get("sender"),
                    from, effectiveTo);
        }
        // 若存在重叠记录，抛出冲突异常并附带重叠运价信息
        if (row != null) {
            Map<String, Object> normalized = DbSupport.normalizeRow(row);
            throw new BusinessException(
                    "该线路日期区间与运价 ID " + normalized.get("id") + " 重叠（" + normalized.get("effective_from") + " 至 "
                            + (normalized.get("effective_to") == null ? "永久" : normalized.get("effective_to")) + "）",
                    HttpStatus.CONFLICT
            );
        }
    }
}
