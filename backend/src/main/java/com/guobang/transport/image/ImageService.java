package com.guobang.transport.image;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.common.DateRange;
import com.guobang.transport.common.DateSupport;
import com.guobang.transport.db.DbSupport;
import com.guobang.transport.storage.ObjectStorageService;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageService {
    public static final List<String[]> IMAGE_COLUMNS = List.of(
            new String[]{"id", "图片ID"}, new String[]{"file_name", "文件名"}, new String[]{"mime_type", "类型"},
            new String[]{"size", "大小"}, new String[]{"created_at", "上传时间"}, new String[]{"record_count", "关联记录数"},
            new String[]{"record_id", "记录ID"}, new String[]{"record_date", "记录日期"}, new String[]{"order_no", "单号"},
            new String[]{"ocr_status", "OCR状态"}, new String[]{"ocr_text", "OCR原文"}
    );

    private final JdbcTemplate jdbc;
    private final ObjectStorageService objectStorage;

    public ImageService(JdbcTemplate jdbc, ObjectStorageService objectStorage) {
        this.jdbc = jdbc;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public int insert(String fileName, byte[] data, String mimeType) {
        if (fileName == null || fileName.isBlank()) {
            fileName = "upload.jpg";
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/jpeg";
        }
        try {
            if (objectStorage.enabled()) {
                Integer id = jdbc.queryForObject(
                        "INSERT INTO images(file_name, data, mime_type, size, storage_backend) VALUES (?, ''::bytea, ?, ?, 's3') RETURNING id",
                        Integer.class,
                        fileName, mimeType, data.length
                );
                String key = objectStorage.objectKeyForImage(id, fileName);
                objectStorage.put(key, data, mimeType);
                jdbc.update("UPDATE images SET object_key=?, storage_backend='s3' WHERE id=?", key, id);
                updateThumbnail(id, buildThumbnail(data), "image/jpeg");
                return id;
            }
        } catch (Exception ignored) {
        }
        Integer id = jdbc.queryForObject(
                "INSERT INTO images(file_name, data, mime_type, size, storage_backend) VALUES (?, ?, ?, ?, 'db') RETURNING id",
                Integer.class,
                fileName, data, mimeType, data.length
        );
        try {
            updateThumbnail(id, buildThumbnail(data), "image/jpeg");
        } catch (Exception ignored) {
        }
        return id;
    }

    public ImageData data(int imageId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT data, mime_type, object_key FROM images WHERE id=?", imageId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String mime = DbSupport.trim(row.get("mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(row.get("mime_type"));
        String objectKey = DbSupport.trim(row.get("object_key"));
        if (!objectKey.isBlank()) {
            try {
                return new ImageData(objectStorage.get(objectKey), mime);
            } catch (Exception ignored) {
            }
        }
        byte[] bytes = (byte[]) row.get("data");
        return bytes == null || bytes.length == 0 ? new ImageData(null, mime) : new ImageData(bytes, mime);
    }

    public String dataUrl(int imageId) {
        ImageData data = data(imageId);
        if (data == null || data.bytes() == null || data.bytes().length == 0) {
            return null;
        }
        return "data:" + data.mimeType() + ";base64," + Base64.getEncoder().encodeToString(data.bytes());
    }

    public String thumbnailDataUrl(int imageId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT thumbnail_data, thumbnail_mime_type, thumbnail_object_key FROM images WHERE id=?",
                imageId
        );
        if (rows.isEmpty()) {
            return null;
        }
        byte[] bytes = null;
        String mime = DbSupport.trim(rows.get(0).get("thumbnail_mime_type")).isBlank() ? "image/jpeg" : DbSupport.trim(rows.get(0).get("thumbnail_mime_type"));
        String key = DbSupport.trim(rows.get(0).get("thumbnail_object_key"));
        if (!key.isBlank()) {
            try {
                bytes = objectStorage.get(key);
            } catch (Exception ignored) {
            }
        }
        if (bytes == null) {
            bytes = (byte[]) rows.get(0).get("thumbnail_data");
        }
        if (bytes == null || bytes.length == 0) {
            ImageData original = data(imageId);
            if (original == null || original.bytes() == null) {
                return null;
            }
            bytes = buildThumbnail(original.bytes());
            updateThumbnail(imageId, bytes, "image/jpeg");
            mime = "image/jpeg";
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Transactional
    public boolean updateData(int imageId, String fileName, byte[] data, String mimeType) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT file_name, object_key, thumbnail_object_key FROM images WHERE id=?", imageId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> old = rows.get(0);
        String finalName = fileName == null || fileName.isBlank() ? DbSupport.trim(old.get("file_name")) : fileName;
        String objectKey = null;
        String storageBackend = "db";
        byte[] dbData = data;
        try {
            if (objectStorage.enabled()) {
                objectKey = objectStorage.objectKeyForImage(imageId, finalName);
                objectStorage.put(objectKey, data, mimeType);
                storageBackend = "s3";
                dbData = new byte[0];
            }
        } catch (Exception ignored) {
            objectKey = null;
            storageBackend = "db";
            dbData = data;
        }
        jdbc.update(
                """
                UPDATE images
                SET file_name=?, data=?, mime_type=?, size=?, storage_backend=?, object_key=?,
                    thumbnail_data=NULL, thumbnail_object_key=NULL, thumbnail_mime_type='image/jpeg', thumbnail_updated_at=NULL
                WHERE id=?
                """,
                finalName, dbData, mimeType, data.length, storageBackend, objectKey, imageId
        );
        deleteObjectQuietly(DbSupport.trim(old.get("object_key")));
        deleteObjectQuietly(DbSupport.trim(old.get("thumbnail_object_key")));
        try {
            updateThumbnail(imageId, buildThumbnail(data), "image/jpeg");
        } catch (Exception ignored) {
        }
        return true;
    }

    public Map<String, Object> list(Map<String, String> filters, int offset, int limit) {
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        FilterClause clause = filterClause(filters);
        Integer total = jdbc.queryForObject(
                "WITH " + imageListCte() + " SELECT COUNT(*) FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id " + clause.where(),
                Integer.class,
                clause.params().toArray()
        );
        List<Object> params = new ArrayList<>(clause.params());
        params.add(limit);
        params.add(offset);
        List<Map<String, Object>> images = DbSupport.normalizeRows(jdbc.queryForList(
                "WITH " + imageListCte() + """
                SELECT i.id, i.file_name, i.mime_type, i.size, i.created_at,
                       (i.thumbnail_data IS NOT NULL OR i.thumbnail_object_key IS NOT NULL) AS has_thumbnail,
                       i.storage_backend, i.object_key,
                       COALESCE(agg.record_count, 0) AS record_count,
                       agg.record_id, agg.record_date, agg.order_no, agg.ocr_status, agg.ocr_text
                FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id
                """ + clause.where() + " ORDER BY i.id DESC LIMIT ? OFFSET ?",
                params.toArray()
        ));
        for (Map<String, Object> image : images) {
            image.put("thumbnail_base64", thumbnailDataUrl((Integer) image.get("id")));
        }
        return Map.of("images", images, "total", total == null ? 0 : total, "offset", offset, "limit", limit);
    }

    public List<Map<String, Object>> exportRows(Map<String, String> filters, boolean includeData) {
        FilterClause clause = filterClause(filters);
        String dataCol = includeData ? ", i.data, i.object_key" : ", i.object_key";
        return DbSupport.normalizeRows(jdbc.queryForList(
                "WITH " + imageListCte() + """
                SELECT i.id, i.file_name, i.mime_type, i.size, i.created_at, i.storage_backend,
                       COALESCE(agg.record_count, 0) AS record_count,
                       agg.record_id, agg.record_date, agg.order_no, agg.ocr_status, agg.ocr_text
                """ + dataCol + " FROM images i LEFT JOIN record_agg agg ON agg.image_id=i.id "
                        + clause.where() + " ORDER BY i.created_at DESC, i.id DESC",
                clause.params().toArray()
        ));
    }

    @Transactional
    public Map<String, Object> deleteImageAndRecords(int imageId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT r.id, r.image_id FROM records r
                JOIN record_images ri ON ri.record_id=r.id
                WHERE ri.image_id=?
                """,
                imageId
        );
        int updatedRecords = 0;
        for (Map<String, Object> row : rows) {
            String remaining = String.join(",", splitImageIds(row.get("image_id")).stream().filter(id -> !String.valueOf(imageId).equals(id)).toList());
            jdbc.update("UPDATE records SET image_id=?, updated_at=LOCALTIMESTAMP WHERE id=?", remaining, row.get("id"));
            jdbc.update("DELETE FROM record_images WHERE record_id=?", row.get("id"));
            int sort = 0;
            for (String id : splitImageIds(remaining)) {
                if (id.matches("\\d+")) {
                    jdbc.update("INSERT INTO record_images(record_id, image_id, sort_order) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", row.get("id"), Integer.parseInt(id), sort++);
                }
            }
            updatedRecords++;
        }
        List<Map<String, Object>> taskRows = jdbc.queryForList(
                """
                SELECT r.id, r.ocr_status
                FROM records r JOIN ocr_tasks t ON t.record_id=r.id
                WHERE t.image_id=? AND r.id NOT IN (SELECT record_id FROM record_images)
                """,
                imageId
        );
        int deletedRecords = 0;
        for (Map<String, Object> row : taskRows) {
            String status = DbSupport.trim(row.get("ocr_status"));
            if ("duplicate".equals(status) || "error".equals(status)) {
                jdbc.update("DELETE FROM ocr_tasks WHERE record_id=?", row.get("id"));
                jdbc.update("DELETE FROM records WHERE id=?", row.get("id"));
                deletedRecords++;
            }
        }
        List<Map<String, Object>> keys = jdbc.queryForList("SELECT object_key, thumbnail_object_key FROM images WHERE id=?", imageId);
        jdbc.update("DELETE FROM images WHERE id=?", imageId);
        if (!keys.isEmpty()) {
            deleteObjectQuietly(DbSupport.trim(keys.get(0).get("object_key")));
            deleteObjectQuietly(DbSupport.trim(keys.get(0).get("thumbnail_object_key")));
        }
        return Map.of("updated_records", updatedRecords, "deleted_records", deletedRecords);
    }

    public Map<String, Object> imageRow(int imageId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, file_name, mime_type FROM images WHERE id=?", imageId);
        return rows.isEmpty() ? null : DbSupport.normalizeRow(rows.get(0));
    }

    private void updateThumbnail(int imageId, byte[] data, String mimeType) {
        if (data == null || data.length == 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT object_key, storage_backend FROM images WHERE id=?", imageId);
        if (rows.isEmpty()) {
            return;
        }
        String thumbKey = null;
        byte[] dbData = data;
        try {
            if (objectStorage.enabled() && ("s3".equals(DbSupport.trim(rows.get(0).get("storage_backend"))) || !DbSupport.trim(rows.get(0).get("object_key")).isBlank())) {
                thumbKey = objectStorage.objectKeyForThumbnail(imageId, DbSupport.trim(rows.get(0).get("object_key")));
                objectStorage.put(thumbKey, data, mimeType);
                dbData = null;
            }
        } catch (Exception ignored) {
            thumbKey = null;
            dbData = data;
        }
        jdbc.update(
                """
                UPDATE images SET thumbnail_data=?, thumbnail_object_key=?, thumbnail_mime_type=?, thumbnail_updated_at=LOCALTIMESTAMP
                WHERE id=?
                """,
                dbData, thumbKey, mimeType, imageId
        );
    }

    private byte[] buildThumbnail(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                return null;
            }
            int maxW = 120;
            int maxH = 90;
            double scale = Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight());
            if (scale > 1) {
                scale = 1;
            }
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private FilterClause filterClause(Map<String, String> filters) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (filters != null && filters.get("month") != null && !filters.get("month").isBlank()) {
            DateRange range = DateSupport.monthBounds(filters.get("month"));
            where.add("i.created_at >= ? AND i.created_at < ?");
            params.add(range.start());
            params.add(range.end());
        }
        if (filters != null && filters.get("ocr_status") != null && !filters.get("ocr_status").isBlank()) {
            where.add("COALESCE(agg.ocr_status, '') = ?");
            params.add(filters.get("ocr_status"));
        }
        if (filters != null && filters.get("file_name") != null && !filters.get("file_name").isBlank()) {
            where.add("i.file_name ILIKE ?");
            params.add("%" + filters.get("file_name").trim() + "%");
        }
        if (filters != null && filters.get("order_no") != null && !filters.get("order_no").isBlank()) {
            where.add("COALESCE(agg.order_no, '') ILIKE ?");
            params.add("%" + filters.get("order_no").trim() + "%");
        }
        return new FilterClause(where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where), params);
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
                """;
    }

    private void deleteObjectQuietly(String key) {
        try {
            if (key != null && !key.isBlank()) {
                objectStorage.delete(key);
            }
        } catch (Exception ignored) {
        }
    }

    private static List<String> splitImageIds(Object value) {
        String text = DbSupport.str(value);
        if (text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private record FilterClause(String where, List<Object> params) {
    }

    public record ImageData(byte[] bytes, String mimeType) {
    }
}
