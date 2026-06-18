package com.guobang.transport.collection;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guobang.transport.common.BusinessException;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.mapper.CollectionMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 基础资料服务，负责开单公司、发货单位、收货单位、车牌号等基础数据的管理
 */
@Service
@RequiredArgsConstructor
public class CollectionService {
    private final CollectionMapper collectionMapper;

    /**
     * 获取指定分类的基础资料列表
     *
     * @param category 分类名称
     * @return 基础资料列表
     */
    public List<Collection> list(String category) {
        // 分类名称不能为空
        if (DbSupport.trim(category).isBlank()) {
            throw new BusinessException("category 不能为空", HttpStatus.BAD_REQUEST);
        }
        // 按分类过滤并按值升序返回
        return collectionMapper.selectList(new QueryWrapper<Collection>()
                .eq("category", category.trim()).orderByAsc("value"));
    }

    /**
     * 分页获取指定分类的基础资料列表
     *
     * @param category 分类名称
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 包含 items 和 total 的 Map
     */
    public Map<String, Object> list(String category, int offset, int limit) {
        // 分类名称不能为空
        if (DbSupport.trim(category).isBlank()) {
            throw new BusinessException("category 不能为空", HttpStatus.BAD_REQUEST);
        }
        // 校正分页参数
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), 200);

        // 构建按分类过滤、按值升序的查询条件
        QueryWrapper<Collection> qw = new QueryWrapper<Collection>()
                .eq("category", category.trim()).orderByAsc("value");

        // 查询该分类下的总数
        Long total = collectionMapper.selectCount(new QueryWrapper<Collection>()
                .eq("category", category.trim()));

        // 追加分页子句并执行查询
        qw.last("LIMIT " + limit + " OFFSET " + offset);
        List<Collection> items = collectionMapper.selectList(qw);

        // 组装分页结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    /**
     * 获取所有基础资料，按分类分组
     *
     * @return 按分类分组的基础资料 Map
     */
    public Map<String, List<Collection>> all() {
        // 查询全部基础资料，按分类和值排序
        List<Collection> list = collectionMapper.selectList(new QueryWrapper<Collection>().orderByAsc("category", "value"));
        // 按 category 字段分组返回
        return list.stream().collect(Collectors.groupingBy(Collection::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * 创建基础资料
     *
     * @param body 包含 category 和 value 的数据
     * @return 新创建的记录 ID
     */
    public int create(Map<String, Object> body) {
        // 提取并去空格
        String category = DbSupport.trim(body.get("category"));
        String value = DbSupport.trim(body.get("value"));
        // 必填校验
        if (category.isBlank() || value.isBlank()) {
            throw new BusinessException("category 和 value 不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 值长度限制
        if (value.length() > 64) {
            throw new BusinessException("value 长度不能超过 64 个字符", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 检查同分类下是否已存在相同值（防重复）
        if (collectionMapper.countByCategoryAndValue(category, value) > 0) {
            throw new BusinessException("该分类下已存在相同的值", HttpStatus.CONFLICT);
        }
        // 构建实体并插入
        Collection c = new Collection();
        c.setCategory(category);
        c.setValue(value);
        collectionMapper.insert(c);
        return c.getId().intValue();
    }

    /**
     * 删除基础资料
     *
     * @param id 记录 ID
     * @return 是否删除成功
     */
    public boolean delete(int id) {
        // 按 ID 删除基础资料记录
        return collectionMapper.deleteById((long) id) > 0;
    }

    /**
     * 更新基础资料
     *
     * @param id   记录 ID
     * @param body 更新数据
     * @return 是否更新成功
     */
    public boolean update(int id, Map<String, Object> body) {
        // 查询已有记录
        Collection c = collectionMapper.selectById((long) id);
        if (c == null) return false;
        // 提取并校验新值
        String value = DbSupport.trim(body.get("value"));
        if (value.isBlank()) {
            throw new BusinessException("value 不能为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (value.length() > 64) {
            throw new BusinessException("value 长度不能超过 64 个字符", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        // 设置新值并更新
        c.setValue(value);
        return collectionMapper.updateById(c) > 0;
    }

    public void validateRecordCollections(Map<String, Object> data) {
        // 预留方法：用于校验运输记录中的基础资料引用是否合法
    }
}
