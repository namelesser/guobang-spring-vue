package com.guobang.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guobang.transport.image.Image;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ImageMapper extends BaseMapper<Image> {

    // 插入新图片记录，返回自增主键ID
    @Insert("""
            INSERT INTO images(file_name, data, mime_type, size)
            VALUES (#{fileName}, #{data}, #{mimeType}, #{size})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertImage(Image image);

    // 更新图片数据（文件名、二进制数据、MIME类型、大小），同时清除缩略图
    @Update("""
            UPDATE images
            SET file_name=#{fileName}, data=#{data}, mime_type=#{mimeType}, size=#{size},
                thumbnail_data=NULL, thumbnail_mime_type='image/jpeg', thumbnail_updated_at=NULL
            WHERE id=#{id}
            """)
    int updateImageData(@Param("fileName") String fileName, @Param("data") byte[] data,
                        @Param("mimeType") String mimeType, @Param("size") int size,
                        @Param("id") int id);

    // 更新图片缩略图数据和MIME类型
    @Update("""
            UPDATE images SET thumbnail_data=#{data},
                thumbnail_mime_type=#{mimeType}, thumbnail_updated_at=LOCALTIMESTAMP
            WHERE id=#{id}
            """)
    int updateThumbnail(@Param("data") byte[] data,
                        @Param("mimeType") String mimeType, @Param("id") int id);

    // 根据ID查询图片二进制数据和MIME类型
    @Select("SELECT data, mime_type FROM images WHERE id=#{id}")
    Map<String, Object> selectDataById(@Param("id") int id);

    // 根据ID查询图片缩略图数据和MIME类型
    @Select("SELECT thumbnail_data, thumbnail_mime_type FROM images WHERE id=#{id}")
    Map<String, Object> selectThumbnailById(@Param("id") int id);

    // 根据ID查询图片文件名
    @Select("SELECT file_name FROM images WHERE id=#{id}")
    Map<String, Object> selectFileNameById(@Param("id") int id);
}
