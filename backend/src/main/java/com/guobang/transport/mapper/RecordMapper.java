package com.guobang.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guobang.transport.record.Record;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.jdbc.SQL;

public interface RecordMapper extends BaseMapper<Record> {

    // 执行自定义SQL查询单条记录（Map形式返回）
    @SelectProvider(type = SqlProvider.class, method = "selectMapWithSql")
    Map<String, Object> selectMapWithSql(@Param("sql") String sql, @Param("params") List<Object> params);

    // 执行自定义SQL查询多条记录列表
    @SelectProvider(type = SqlProvider.class, method = "selectListWithSql")
    List<Map<String, Object>> selectListWithSql(@Param("sql") String sql, @Param("params") List<Object> params);

    // 执行自定义SQL统计记录数量
    @SelectProvider(type = SqlProvider.class, method = "countWithSql")
    Integer countWithSql(@Param("sql") String sql, @Param("params") List<Object> params);

    // 执行自定义SQL更新操作
    @UpdateProvider(type = SqlProvider.class, method = "updateWithSql")
    int updateWithSql(@Param("sql") String sql, @Param("params") List<Object> params);

    class SqlProvider {
        // 直接返回传入的SQL语句
        public static String selectMapWithSql(Map<String, Object> params) {
            return (String) params.get("sql");
        }
        // 直接返回传入的SQL语句
        public static String selectListWithSql(Map<String, Object> params) {
            return (String) params.get("sql");
        }
        // 直接返回传入的SQL语句
        public static String countWithSql(Map<String, Object> params) {
            return (String) params.get("sql");
        }
        // 直接返回传入的SQL语句
        public static String updateWithSql(Map<String, Object> params) {
            return (String) params.get("sql");
        }
    }

    // 插入新的运输记录，返回自增主键ID
    @Insert("""
            INSERT INTO records (source, file_name, image_id, record_date, order_no, sender, receiver, company,
                plate_no, net_weight, freight_rate, detour_surcharge, total_cost, reviewed,
                reviewed_at, review_note, note, ocr_status, ocr_text)
            VALUES (#{source}, #{fileName}, #{imageId}, #{recordDate}, #{orderNo}, #{sender}, #{receiver}, #{company},
                #{plateNo}, #{netWeight}, #{freightRate}, #{detourSurcharge}, #{totalCost}, #{reviewed},
                #{reviewedAt}, #{reviewNote}, #{note}, #{ocrStatus}, #{ocrText})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRecord(Record record);

    // 根据ID查询单条记录（Map形式返回）
    @Select("SELECT * FROM records WHERE id=#{id}")
    Map<String, Object> selectMapById(@Param("id") int id);

    // 按动态WHERE条件统计记录数量
    @Select("<script>SELECT COUNT(*) FROM records ${whereSql}</script>")
    int countWithWhere(@Param("whereSql") String whereSql);

    // 按动态WHERE条件统计汇总信息（趟次、重量、运费、已审/未审数量）
    @Select("<script>SELECT COUNT(*) as total_trips, COALESCE(SUM(net_weight), 0) as total_weight, "
            + "COALESCE(SUM(total_cost), 0) as total_freight, "
            + "COALESCE(SUM(CASE WHEN reviewed=1 THEN 1 ELSE 0 END), 0) as reviewed_count, "
            + "COALESCE(SUM(CASE WHEN reviewed=0 THEN 1 ELSE 0 END), 0) as unreviewed_count "
            + "FROM records ${whereSql}</script>")
    Map<String, Object> summaryWithWhere(@Param("whereSql") String whereSql);

    // 按动态WHERE条件查询记录列表（支持分页和排序）
    @Select("<script>SELECT * FROM records ${whereSql} ORDER BY record_date DESC NULLS LAST, id DESC ${limitOffset}</script>")
    List<Map<String, Object>> listWithWhere(@Param("whereSql") String whereSql,
                                            @Param("limitOffset") String limitOffset);

    // 按动态WHERE条件查询导出数据（不带分页，按日期降序）
    @Select("<script>SELECT id, record_date, order_no, sender, receiver, company, plate_no, net_weight, "
            + "freight_rate, detour_surcharge, total_cost, source, reviewed, reviewed_at, "
            + "review_note, note, ocr_status, file_name, image_id, created_at, updated_at "
            + "FROM records ${whereSql} ORDER BY record_date DESC NULLS LAST, id DESC</script>")
    List<Map<String, Object>> exportWithWhere(@Param("whereSql") String whereSql);

    // 查询指定ID之后的下一条未审记录（跳过OCR处理中的记录）
    @Select("SELECT * FROM records WHERE id!=#{id} AND reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '') "
            + "AND id>#{id} ORDER BY id ASC LIMIT 1")
    Map<String, Object> nextUnreviewedAfter(@Param("id") int id);

    // 查询第一条未审记录（跳过OCR处理中和无图片的记录）
    @Select("SELECT * FROM records WHERE reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '') "
            + "ORDER BY id ASC LIMIT 1")
    Map<String, Object> firstUnreviewedRecord();

    // 统计未审记录数量（跳过OCR处理中和无图片的记录）
    @Select("SELECT COUNT(*) FROM records WHERE reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '')")
    int countUnreviewed();

    // 查询记录关联的所有图片ID（按排序顺序）
    @Select("SELECT id, image_id FROM record_images WHERE record_id=#{recordId} ORDER BY sort_order ASC, id ASC")
    List<Map<String, Object>> recordImages(@Param("recordId") int recordId);

    // 查询记录关联的图片ID列表
    @Select("SELECT image_id FROM record_images WHERE record_id=#{recordId} ORDER BY sort_order ASC, id ASC")
    List<Long> imageIdsForRecord(@Param("recordId") int recordId);

    // 批量查询多条记录各自的第一张图片ID（DISTINCT ON去重）
    @Select("<script>SELECT DISTINCT ON (record_id) record_id, image_id FROM record_images "
            + "WHERE record_id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY record_id, sort_order ASC, id ASC</script>")
    List<Map<String, Object>> batchFirstImageIds(@Param("ids") List<Long> ids);

    // 根据图片ID查询关联的第一条记录
    @Select("""
            SELECT r.* FROM record_images ri
            JOIN records r ON r.id = ri.record_id
            WHERE ri.image_id=#{imageId}
            ORDER BY r.id ASC LIMIT 1
            """)
    Map<String, Object> firstRecordByImageId(@Param("imageId") int imageId);

    // 插入记录与图片的关联关系（重复关联时忽略）
    @Insert("INSERT INTO record_images(record_id, image_id, sort_order) VALUES (#{recordId}, #{imageId}, #{sortOrder}) ON CONFLICT (record_id, image_id) DO NOTHING")
    int insertRecordImage(@Param("recordId") int recordId, @Param("imageId") int imageId, @Param("sortOrder") int sortOrder);

    // 删除记录关联的所有图片关系
    @Delete("DELETE FROM record_images WHERE record_id=#{recordId}")
    int deleteRecordImages(@Param("recordId") int recordId);

    // 根据图片ID重置关联记录的审核状态为未审
    @Update("""
            UPDATE records SET reviewed=0, reviewed_at=NULL, updated_at=LOCALTIMESTAMP
            WHERE id IN (SELECT record_id FROM record_images WHERE image_id=#{imageId})
            """)
    int rereviewByImageId(@Param("imageId") int imageId);

    // 查询是否存在相同订单号和公司的重复记录
    @Select("<script>SELECT id, order_no, company FROM records WHERE order_no=#{orderNo} AND company=#{company} "
            + "<if test='excludeId != null'> AND id != #{excludeId}</if> ORDER BY id ASC LIMIT 1</script>")
    Map<String, Object> findDuplicate(@Param("orderNo") String orderNo, @Param("company") String company,
                                      @Param("excludeId") Integer excludeId);

    // 查询当前ID之后的相邻未审记录（用于审核页面导航）
    @Select("SELECT * FROM records WHERE reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '') "
            + "AND id>#{currentId} ORDER BY id ASC LIMIT 1")
    Map<String, Object> adjacentUnreviewed(@Param("currentId") int currentId);

    // 查询任意一条未审记录（用于审核入口跳转）
    @Select("SELECT * FROM records WHERE reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '') "
            + "ORDER BY id ASC LIMIT 1")
    Map<String, Object> anyUnreviewed();

    // 批量查询未审记录（跳过OCR处理中和无图片的记录）
    @Select("SELECT * FROM records WHERE reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '') "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<Map<String, Object>> unreviewedRecords(@Param("limit") int limit);

    // 查询记录的OCR状态信息
    @Select("SELECT id, ocr_status, file_name FROM records WHERE id=#{id}")
    Map<String, Object> ocrStatus(@Param("id") int id);

    // 根据图片ID查询文件名
    @Select("SELECT file_name FROM images WHERE id=#{imageId}")
    Map<String, Object> imageFileName(@Param("imageId") int imageId);

    // 更新图片的文件名
    @Update("UPDATE images SET file_name=#{fileName} WHERE id=#{imageId}")
    int updateImageFileName(@Param("fileName") String fileName, @Param("imageId") int imageId);

    // 查询可审核的记录（未审核、OCR已完成、有图片关联）
    @Select("SELECT id, reviewed FROM records WHERE id=#{id} AND reviewed=0 "
            + "AND COALESCE(ocr_status, 'done') NOT IN ('pending', 'processing') "
            + "AND (EXISTS (SELECT 1 FROM record_images WHERE record_id = records.id) "
            + "OR COALESCE(records.image_id, '') != '')")
    Map<String, Object> reviewableRecord(@Param("id") int id);
}
