package com.guobang.transport.quality;

import com.guobang.transport.db.DbSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataQualityService {
    private final JdbcTemplate jdbc;

    public DataQualityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> report() {
        List<Map<String, Object>> collectionChecks = new ArrayList<>();
        for (String[] field : List.of(
                new String[]{"sender", "sender", "发货单位"},
                new String[]{"receiver", "receiver", "收货单位"},
                new String[]{"company", "company", "开单公司"},
                new String[]{"plate_no", "plate", "车牌号"}
        )) {
            collectionChecks.add(Map.of(
                    "field", field[0],
                    "category", field[1],
                    "label", field[2],
                    "items", DbSupport.normalizeRows(jdbc.queryForList(
                            """
                            SELECT r.%s AS value, COUNT(*) AS cnt
                            FROM records r
                            WHERE NULLIF(BTRIM(r.%s), '') IS NOT NULL
                              AND NOT EXISTS (
                                  SELECT 1 FROM collections c
                                  WHERE c.category=? AND BTRIM(c.value)=BTRIM(r.%s)
                              )
                            GROUP BY r.%s
                            ORDER BY cnt DESC, value
                            LIMIT 100
                            """.formatted(field[0], field[0], field[0], field[0]),
                            field[1]
                    ))
            ));
        }
        return Map.of(
                "collection_checks", collectionChecks,
                "future_dates", rows("""
                        SELECT id, record_date, order_no, company
                        FROM records
                        WHERE record_date > CURRENT_DATE
                        ORDER BY record_date DESC, id DESC
                        LIMIT 100
                        """),
                "missing_images", rows("""
                        SELECT r.id, r.record_date, r.order_no, r.company, r.ocr_status
                        FROM records r
                        LEFT JOIN record_images ri ON ri.record_id = r.id
                        WHERE r.source='ocr'
                          AND ri.record_id IS NULL
                          AND COALESCE(r.image_id, '') = ''
                        ORDER BY r.id DESC
                        LIMIT 100
                        """),
                "missing_weights", rows("""
                        SELECT id, record_date, order_no, company, receiver, ocr_status, net_weight
                        FROM records
                        WHERE reviewed=0
                          AND COALESCE(ocr_status, '') NOT IN ('pending', 'processing')
                          AND (net_weight IS NULL OR net_weight <= 0)
                        ORDER BY id DESC
                        LIMIT 100
                        """),
                "missing_rates", rows("""
                        SELECT id, record_date, order_no, company, receiver, freight_rate
                        FROM records
                        WHERE reviewed=0
                          AND (freight_rate IS NULL OR freight_rate <= 0)
                          AND COALESCE(ocr_status, '') NOT IN ('pending', 'processing', 'error')
                        ORDER BY id DESC
                        LIMIT 100
                        """),
                "stale_ocr_tasks", rows("""
                        SELECT id, record_id, image_id, file_name, retry_count, started_at
                        FROM ocr_tasks
                        WHERE status='processing'
                          AND started_at < NOW() - INTERVAL '30 minutes'
                        ORDER BY started_at ASC, id ASC
                        LIMIT 100
                        """),
                "duplicate_order_company", rows("""
                        SELECT order_no, company, COUNT(*) AS cnt, MIN(id) AS first_id
                        FROM records
                        WHERE NULLIF(BTRIM(order_no), '') IS NOT NULL
                          AND NULLIF(BTRIM(company), '') IS NOT NULL
                        GROUP BY order_no, company
                        HAVING COUNT(*) > 1
                        ORDER BY cnt DESC, order_no, company
                        LIMIT 100
                        """)
        );
    }

    private List<Map<String, Object>> rows(String sql) {
        return DbSupport.normalizeRows(jdbc.queryForList(sql));
    }
}
