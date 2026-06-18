package com.guobang.transport.image;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.common.DateRange;
import com.guobang.transport.common.DateSupport;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.mapper.ImageMapper;
import com.guobang.transport.mapper.RecordMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图片服务，负责图片的存储、缩略图生成和查询
 */
@Service
@RequiredArgsConstructor
public class ImageService {
    /** 图片列表导出列定义 */
    public static final List<String[]> IMAGE_COLUMNS = List.of(
            new String[]{"id", "图片ID"}, new String[]{"file_name", "文件名"}, new String[]{"mime_type", "类型"},
            new String[]{"size", "大小"}, new String[]{"created_at", "上传时间"}, new String[]{"record_count", "关联记录数"},
            new String[]{"record_id", "记录ID"}, new String[]{"record_date", "记录日期"}, new String[]{"order_no", "单号"},
            new String[]{"ocr_status", "OCR状态"}, new String[]{"ocr_text", "OCR原文"}
    );

    private final ImageMapper imageMapper;
    private final RecordMapper recordMapper;
    private final SqlSession sqlSession;

    /**
     * 插入图片
     *
     * @param fileName 文件名
     * @param data     图片数据
     * @param mimeType MIME 类型
     * @return 新创建的图片 ID
     */
    @Transactional
    public int insert(String fileName, byte[] data, String mimeType) {
        if (fileName == null || fileName.isBlank()) { // 文件名为空时使用默认名
            fileName = "upload.jpg";
        }
        if (mimeType == null || mimeType.isBlank()) { // MIME类型为空时默认jpeg
            mimeType = "image/jpeg";
        }
        Image img = new Image(); // 构建图片实体
        img.setFileName(fileName);
        img.setData(data);
        img.setMimeType(mimeType);
        img.setSize(data.length); // 记录图片字节大小
        imageMapper.insertImage(img); // 插入数据库获取自增ID
        int id = img.getId().intValue();
        try {
            updateThumbnail(id, buildThumbnail(data), "image/jpeg"); // 异步生成缩略图，失败不影响主流程
        } catch (Exception ignored) {
        }
        return id;
    }

    /**
     * 获取图片数据
     *
     * @param imageId 图片 ID
     * @return 图片数据
     */
    public ImageData data(int imageId) {
        Map<String, Object> row = imageMapper.selectDataById(imageId); // 按ID查询图片原始数据
        if (row == null) {
            return null;
        }
        String mime = DbSupport.trim(row.get("mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(row.get("mime_type")); // MIME类型缺省时降级为jpeg
        byte[] bytes = (byte[]) row.get("data");
        if (bytes == null || bytes.length == 0) { // 数据为空则返回null
            return null;
        }
        return new ImageData(bytes, mime);
    }

    /**
     * 获取图片的 Data URL
     *
     * @param imageId 图片 ID
     * @return Data URL
     */
    public String dataUrl(int imageId) {
        ImageData data = data(imageId); // 获取图片原始数据
        if (data == null || data.bytes() == null || data.bytes().length == 0) {
            return null;
        }
        return "data:" + data.mimeType() + ";base64," + Base64.getEncoder().encodeToString(data.bytes()); // 拼接Data URL供前端直接显示
    }

    /**
     * 获取缩略图的 Data URL
     *
     * @param imageId 图片 ID
     * @return 缩略图 Data URL
     */
    public String thumbnailDataUrl(int imageId) {
        Map<String, Object> row = imageMapper.selectThumbnailById(imageId); // 查询缩略图数据
        if (row == null) {
            return null;
        }
        byte[] bytes = null;
        String mime = DbSupport.trim(row.get("thumbnail_mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(row.get("thumbnail_mime_type")); // 缩略图MIME类型默认jpeg
        bytes = (byte[]) row.get("thumbnail_data");
        if (bytes == null || bytes.length == 0) { // 缩略图不存在时，从原图懒生成
            ImageData original = data(imageId);
            if (original == null || original.bytes() == null) {
                return null;
            }
            bytes = buildThumbnail(original.bytes()); // 基于原图构建缩略图
            if (bytes == null) {
                return null;
            }
            updateThumbnail(imageId, bytes, "image/jpeg"); // 持久化缩略图以便后续复用
            mime = "image/jpeg";
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes); // 返回Data URL格式
    }

    /**
     * 更新图片数据
     *
     * @param imageId  图片 ID
     * @param fileName 文件名
     * @param data     图片数据
     * @param mimeType MIME 类型
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateData(int imageId, String fileName, byte[] data, String mimeType) {
        Map<String, Object> old = imageMapper.selectFileNameById(imageId); // 先查询原记录确认存在
        if (old == null) {
            return false;
        }
        String finalName = fileName == null || fileName.isBlank() ? DbSupport.trim(old.get("file_name")) : fileName; // 文件名为空则保留原名
        imageMapper.updateImageData(finalName, data, mimeType, data.length, imageId); // 更新图片数据和元信息
        try {
            updateThumbnail(imageId, buildThumbnail(data), "image/jpeg"); // 重新生成缩略图
        } catch (Exception ignored) {
        }
        return true;
    }

    /**
     * 分页查询图片列表
     *
     * @param filters 筛选条件
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 包含 images、total、offset、limit 的 Map
     */
    public Map<String, Object> list(Map<String, String> filters, int offset, int limit) {
        offset = Math.max(offset, 0); // 偏移量不允许负数
        limit = Math.min(Math.max(limit, 1), 200); // 每页限制1-200条防止过大查询
        FilterClause clause = filterClause(filters); // 构建WHERE子句和参数
        String countSql = "WITH " + imageListCte() + " SELECT COUNT(*) FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id " + clause.where(); // 统计总数用于分页
        Integer total = sqlSession.selectOne("com.guobang.transport.mapper.RecordMapper.countWithSql",
                Map.of("sql", countSql, "params", new ArrayList<>(clause.params())));
        List<Object> params = new ArrayList<>(clause.params());
        int pi = params.size();
        params.add(limit); // 追加分页参数
        params.add(offset);
        String listSql = "WITH " + imageListCte() + """
                SELECT i.id, i.file_name, i.mime_type, i.size, i.created_at,
                       i.thumbnail_data IS NOT NULL AS has_thumbnail,
                       COALESCE(agg.record_count, 0) AS record_count,
                       agg.record_id, agg.record_date, agg.order_no, agg.ocr_status, agg.ocr_text
                FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id
                """ + clause.where() + " ORDER BY i.id DESC LIMIT #{params[" + pi + "]} OFFSET #{params[" + (pi+1) + "]}"; // 按ID倒序分页查询
        List<Map<String, Object>> images = DbSupport.normalizeRows(
                sqlSession.selectList("com.guobang.transport.mapper.RecordMapper.selectListWithSql",
                        Map.of("sql", listSql, "params", params)));
        for (Map<String, Object> image : images) {
            image.put("thumbnail_base64", thumbnailDataUrl((Integer) image.get("id"))); // 为每条记录附带缩略图Base64
        }
        return Map.of("images", images, "total", total == null ? 0 : total, "offset", offset, "limit", limit); // 返回分页结果
    }

    /**
     * 获取导出行数据
     *
     * @param filters     筛选条件
     * @param includeData 是否包含图片数据
     * @return 导出行列表
     */
    public List<Map<String, Object>> exportRows(Map<String, String> filters, boolean includeData) {
        FilterClause clause = filterClause(filters); // 构建筛选条件
        String dataCol = includeData ? ", i.data" : ""; // 仅zip格式导出时包含原始图片数据列
        String sql = "WITH " + imageListCte() + """
                SELECT i.id, i.file_name, i.mime_type, i.size, i.created_at,
                       COALESCE(agg.record_count, 0) AS record_count,
                       agg.record_id, agg.record_date, agg.order_no, agg.ocr_status, agg.ocr_text
                """ + dataCol + " FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id "
                + clause.where() + " ORDER BY i.created_at DESC, i.id DESC"; // 按上传时间倒序排列
        List<Map<String, Object>> rows = sqlSession.selectList("com.guobang.transport.mapper.RecordMapper.selectListWithSql",
                Map.of("sql", sql, "params", new ArrayList<>(clause.params())));
        return DbSupport.normalizeRows(rows); // 标准化行数据类型
    }

    /**
     * 删除图片及其关联记录
     *
     * @param imageId 图片 ID
     * @return 包含更新记录数和删除记录数的 Map
     */
    @Transactional
    public Map<String, Object> deleteImageAndRecords(int imageId) {
        List<Map<String, Object>> rows = sqlSession.selectList("com.guobang.transport.mapper.RecordMapper.selectListWithSql",
                Map.of("sql", """
                        SELECT r.id, r.image_id FROM records r
                        JOIN record_images ri ON ri.record_id=r.id WHERE ri.image_id=#{params[0]}
                        """,
                        "params", List.of(imageId))); // 查询所有关联此图片的记录
        int updatedRecords = 0;
        for (Map<String, Object> row : rows) {
            String remaining = String.join(",", splitImageIds(row.get("image_id")).stream().filter(id -> !String.valueOf(imageId).equals(id)).toList()); // 从image_id列表中移除被删除的图片
            recordMapper.updateImageFileName(remaining, (Integer) row.get("id")); // 更新记录的image_id字段
            recordMapper.deleteRecordImages((Integer) row.get("id")); // 清除关联表记录
            int sort = 0;
            for (String id : splitImageIds(remaining)) {
                if (id.matches("\\d+")) {
                    recordMapper.insertRecordImage((Integer) row.get("id"), Integer.parseInt(id), sort++); // 重新插入剩余的图片关联并保持顺序
                }
            }
            updatedRecords++;
        }
        List<Map<String, Object>> taskRows = sqlSession.selectList("com.guobang.transport.mapper.RecordMapper.selectListWithSql",
                Map.of("sql", """
                        SELECT r.id, r.ocr_status
                        FROM records r JOIN ocr_tasks t ON t.record_id=r.id
                        WHERE t.image_id=#{params[0]} AND r.id NOT IN (SELECT record_id FROM record_images)
                        """,
                        "params", List.of(imageId))); // 查找仅关联此图片且已无其他图片的记录
        int deletedRecords = 0;
        for (Map<String, Object> row : taskRows) {
            String status = DbSupport.trim(row.get("ocr_status"));
            if ("duplicate".equals(status) || "error".equals(status)) { // 仅删除重复或错误状态的孤立记录
                sqlSession.delete("com.guobang.transport.mapper.OcrTaskMapper.deleteByRecordId", row.get("id")); // 先删OCR任务
                sqlSession.delete("com.guobang.transport.mapper.RecordMapper.deleteById", row.get("id")); // 再删记录
                deletedRecords++;
            }
        }
        imageMapper.deleteById((long) imageId); // 最后删除图片本身
        return Map.of("updated_records", updatedRecords, "deleted_records", deletedRecords);
    }

    /**
     * 获取图片基本信息
     *
     * @param imageId 图片 ID
     * @return 图片信息 Map
     */
    public Map<String, Object> imageRow(int imageId) {
        Image img = imageMapper.selectById((long) imageId);
        if (img == null) {
            return null;
        }
        Map<String, Object> row = Map.of(
                "id", img.getId(),
                "file_name", img.getFileName(),
                "mime_type", img.getMimeType());
        return DbSupport.normalizeRow(row);
    }

    private void updateThumbnail(int imageId, byte[] data, String mimeType) {
        if (data == null || data.length == 0) { // 缩略图数据为空则跳过
            return;
        }
        imageMapper.updateThumbnail(data, mimeType, imageId); // 写入缩略图数据到数据库
    }

    private byte[] buildThumbnail(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data)); // 解码原始图片
            if (src == null) {
                return null;
            }
            int maxW = 120; // 缩略图最大宽度120px
            int maxH = 90;  // 缩略图最大高度90px
            double scale = Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()); // 等比缩放取最小比例
            if (scale > 1) {
                scale = 1; // 原图小于缩略图尺寸时不放大
            }
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale)); // 计算缩放后宽度
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale)); // 计算缩放后高度
            BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); // 创建RGB缩略图画布
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); // 使用双线性插值提高缩放质量
            g.drawImage(src, 0, 0, w, h, null); // 绘制缩放后的图片
            g.dispose(); // 释放图形资源
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out); // 编码为JPEG格式
            return out.toByteArray();
        } catch (Exception ex) {
            return null; // 缩略图生成失败不影响主流程
        }
    }

    private FilterClause filterClause(Map<String, String> filters) {
        List<String> where = new ArrayList<>(); // WHERE条件列表
        List<Object> params = new ArrayList<>(); // 对应参数列表
        if (has(filters, "created_at_start")) { // 按上传时间起始筛选
            where.add("i.created_at >= #{params[" + params.size() + "]}");
            params.add(filters.get("created_at_start"));
        }
        if (has(filters, "created_at_end")) { // 按上传时间截止筛选
            where.add("i.created_at < #{params[" + params.size() + "]}");
            params.add(filters.get("created_at_end"));
        }
        if (has(filters, "record_date_start")) { // 按记录日期起始筛选
            where.add("agg.record_date >= #{params[" + params.size() + "]}");
            params.add(filters.get("record_date_start"));
        }
        if (has(filters, "record_date_end")) { // 按记录日期截止筛选
            where.add("agg.record_date < #{params[" + params.size() + "]}");
            params.add(filters.get("record_date_end"));
        }
        if (has(filters, "month")) { // 按月份筛选，转换为日期范围
            String month = filters.get("month");
            if (month.length() == 7) { // 格式必须为yyyy-MM
                where.add("agg.record_date >= #{params[" + params.size() + "]}::date");
                params.add(month + "-01"); // 月初
                String nextMonth = month.substring(0, 5) + String.format("%02d", Integer.parseInt(month.substring(5, 7)) + 1); // 计算下个月
                if (Integer.parseInt(month.substring(5, 7)) == 12) { // 12月特殊处理，跨年
                    nextMonth = (Integer.parseInt(month.substring(0, 4)) + 1) + "-01";
                }
                where.add("agg.record_date < #{params[" + params.size() + "]}::date");
                params.add(nextMonth + "-01"); // 下月月初作为截止
            }
        }
        if (has(filters, "ocr_status")) { // 按OCR状态筛选
            where.add("agg.ocr_status = #{params[" + params.size() + "]}");
            params.add(filters.get("ocr_status"));
        }
        if (has(filters, "file_name")) { // 按文件名模糊搜索
            where.add("i.file_name ILIKE #{params[" + params.size() + "]}");
            params.add("%" + filters.get("file_name").trim() + "%");
        }
        if (has(filters, "order_no")) { // 按单号模糊搜索
            where.add("agg.order_no ILIKE #{params[" + params.size() + "]}");
            params.add("%" + filters.get("order_no").trim() + "%");
        }
        if (has(filters, "keyword")) { // 全局关键词搜索，匹配文件名、单号或OCR文本
            String q = "%" + filters.get("keyword").trim() + "%";
            int i = params.size();
            where.add("(i.file_name ILIKE #{params[" + i + "]} OR agg.order_no ILIKE #{params[" + (i+1) + "]} OR agg.ocr_text ILIKE #{params[" + (i+2) + "]})");
            params.add(q);
            params.add(q);
            params.add(q);
        }
        return new FilterClause(where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where), params); // 拼接WHERE子句
    }

    private static boolean has(Map<String, String> filters, String key) {
        return filters != null && filters.get(key) != null && !filters.get(key).isBlank(); // 判断筛选条件是否存在且非空
    }

    private static String imageListCte() {
        return """
                record_agg AS (
                    SELECT ri.image_id,
                           COUNT(*) AS record_count,
                           MIN(r.id) AS record_id,
                           MIN(r.record_date) AS record_date,
                           MIN(r.order_no) AS order_no,
                           MIN(r.ocr_status) AS ocr_status,
                           MIN(r.ocr_text) AS ocr_text
                    FROM record_images ri
                    JOIN records r ON r.id=ri.record_id
                    GROUP BY ri.image_id
                )
                """; // CTE子查询：按图片聚合关联记录的统计信息
    }

    private record FilterClause(String where, List<Object> params) {
    }

    private static List<String> splitImageIds(Object value) {
        String text = DbSupport.str(value); // 将值转为字符串
        if (text.isBlank()) {
            return List.of(); // 空值返回空列表
        }
        return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList(); // 按逗号分割并清理空白
    }

    public record ImageData(byte[] bytes, String mimeType) {
    }
}
