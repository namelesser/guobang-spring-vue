package com.guobang.transport.image;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("images")
public class Image {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private byte[] data;
    private String mimeType;
    private Integer size;
    private byte[] thumbnailData;
    private String thumbnailMimeType;
    private LocalDateTime thumbnailUpdatedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; } // 返回图片自增主键
    public void setId(Long id) { this.id = id; } // 设置图片主键
    public String getFileName() { return fileName; } // 获取原始文件名
    public void setFileName(String fileName) { this.fileName = fileName; } // 设置文件名
    public byte[] getData() { return data; } // 获取图片二进制数据
    public void setData(byte[] data) { this.data = data; } // 设置图片二进制数据
    public String getMimeType() { return mimeType; } // 获取MIME类型如image/jpeg
    public void setMimeType(String mimeType) { this.mimeType = mimeType; } // 设置MIME类型
    public Integer getSize() { return size; } // 获取图片字节大小
    public void setSize(Integer size) { this.size = size; } // 设置图片字节大小
    public byte[] getThumbnailData() { return thumbnailData; } // 获取缩略图二进制数据
    public void setThumbnailData(byte[] thumbnailData) { this.thumbnailData = thumbnailData; } // 设置缩略图数据
    public String getThumbnailMimeType() { return thumbnailMimeType; } // 获取缩略图MIME类型
    public void setThumbnailMimeType(String thumbnailMimeType) { this.thumbnailMimeType = thumbnailMimeType; } // 设置缩略图MIME类型
    public LocalDateTime getThumbnailUpdatedAt() { return thumbnailUpdatedAt; } // 获取缩略图最后更新时间
    public void setThumbnailUpdatedAt(LocalDateTime thumbnailUpdatedAt) { this.thumbnailUpdatedAt = thumbnailUpdatedAt; } // 设置缩略图更新时间
    public LocalDateTime getCreatedAt() { return createdAt; } // 获取图片上传时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; } // 设置上传时间
}
