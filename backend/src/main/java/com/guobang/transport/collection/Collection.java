package com.guobang.transport.collection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("collections")
public class Collection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
