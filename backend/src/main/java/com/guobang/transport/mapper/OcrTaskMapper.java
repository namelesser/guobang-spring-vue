package com.guobang.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guobang.transport.ocr.OcrTask;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OcrTaskMapper extends BaseMapper<OcrTask> {

    // 插入新的OCR任务，返回自增主键ID
    @Insert("""
            INSERT INTO ocr_tasks (record_id, image_id, file_name, mime_type, priority, status, retry_count)
            VALUES (#{recordId}, #{imageId}, #{fileName}, #{mimeType}, #{priority}, #{status}, #{retryCount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertTask(OcrTask task);

    // 查询指定记录和图片的活跃OCR任务（pending或processing状态）
    @Select("""
            SELECT id, status, record_id, image_id, error_message, retry_count, created_at, started_at, file_name, mime_type
            FROM ocr_tasks
            WHERE record_id=#{recordId}
              AND image_id=#{imageId}
              AND status IN ('pending', 'processing')
            ORDER BY id DESC
            LIMIT 1
            """)
    Map<String, Object> findActiveTask(@Param("recordId") int recordId, @Param("imageId") int imageId);

    // 查询指定记录的最新一条OCR任务
    @Select("""
            SELECT id, status, record_id, image_id, error_message, retry_count, created_at, started_at, file_name, mime_type
            FROM ocr_tasks
            WHERE record_id=#{recordId}
            ORDER BY id DESC
            LIMIT 1
            """)
    Map<String, Object> latestTask(@Param("recordId") int recordId);

    // 按状态统计OCR任务数量
    @Select("SELECT COUNT(*) FROM ocr_tasks WHERE status=#{status}")
    int countByStatus(@Param("status") String status);

    // 统计各状态的OCR任务数量分布
    @Select("SELECT status, COUNT(*) AS cnt FROM ocr_tasks GROUP BY status")
    List<Map<String, Object>> statusCounts();

    // 获取并锁定下一条待处理的OCR任务（使用SKIP LOCKED避免并发冲突）
    @Select("""
            UPDATE ocr_tasks SET status='processing', started_at=LOCALTIMESTAMP
            WHERE id = (
                WITH next_task AS (
                    SELECT id FROM ocr_tasks
                    WHERE status='pending'
                    ORDER BY priority DESC, created_at ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                SELECT id FROM next_task
            ) AND status='pending'
            RETURNING id, record_id, image_id, file_name, mime_type
            """)
    Map<String, Object> claimNextTask();

    // 获取并锁定指定OCR任务，避免手动触发和定时轮询重复处理同一任务
    @Select("""
            UPDATE ocr_tasks
            SET status='processing', started_at=LOCALTIMESTAMP
            WHERE id=#{id} AND status='pending'
            RETURNING id, record_id, image_id, file_name, mime_type
            """)
    Map<String, Object> claimTask(@Param("id") int id);

    // 标记OCR任务为完成状态
    @Update("UPDATE ocr_tasks SET status='done', finished_at=LOCALTIMESTAMP WHERE id=#{id}")
    int markDone(@Param("id") int id);

    // 标记OCR任务为错误状态，记录错误信息
    @Update("UPDATE ocr_tasks SET status='error', error_message=#{msg}, finished_at=LOCALTIMESTAMP WHERE id=#{id}")
    int markError(@Param("id") int id, @Param("msg") String msg);

    // 将OCR任务重置为待处理状态
    @Update("UPDATE ocr_tasks SET status='pending', started_at=NULL WHERE id=#{id}")
    int markPending(@Param("id") int id);

    // 增加OCR任务的重试次数并重置为待处理状态
    @Update("UPDATE ocr_tasks SET status='pending', retry_count=retry_count+1 WHERE id=#{id}")
    int incrementRetry(@Param("id") int id);

    // 删除指定记录关联的所有OCR任务
    @Delete("DELETE FROM ocr_tasks WHERE record_id=#{recordId}")
    int deleteByRecordId(@Param("recordId") int recordId);

    // 根据ID删除单个OCR任务
    @Delete("DELETE FROM ocr_tasks WHERE id=#{id}")
    int deleteById(@Param("id") int id);

    // 查询OCR任务的状态详情
    @Select("""
            SELECT id, status, record_id, image_id, error_message, retry_count, created_at, started_at, finished_at, file_name, mime_type
            FROM ocr_tasks WHERE id=#{id}
            """)
    Map<String, Object> taskStatus(@Param("id") int id);

    // 分页查询OCR任务列表（可按状态筛选）
    @Select("<script>SELECT id, record_id, image_id, file_name, mime_type, priority, status, retry_count, "
            + "error_message, created_at, started_at, finished_at FROM ocr_tasks "
            + "<if test='status != null and status != \"\"'>WHERE status=#{status}</if> "
            + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String, Object>> listTasks(@Param("status") String status,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);
}
