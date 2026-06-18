package com.guobang.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guobang.transport.collection.Collection;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CollectionMapper extends BaseMapper<Collection> {

    // 按类别和值统计下拉选项数量（用于判断是否已存在）
    @Select("SELECT COUNT(*) FROM collections WHERE category=#{category} AND BTRIM(value)=#{value}")
    int countByCategoryAndValue(@Param("category") String category, @Param("value") String value);
}
