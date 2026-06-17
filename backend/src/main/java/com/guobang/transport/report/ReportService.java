package com.guobang.transport.report;

import com.guobang.transport.common.DateRange;
import com.guobang.transport.common.DateSupport;
import com.guobang.transport.db.DbSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> monthly(String month, String sender, String receiver, String company, String plateNo) {
        DateRange range = DateSupport.monthBounds(month);
        List<String> where = new ArrayList<>(List.of("record_date >= ? AND record_date < ?"));
        List<Object> params = new ArrayList<>(List.of(range.start(), range.end()));
        addEquals(where, params, "sender", sender);
        addEquals(where, params, "receiver", receiver);
        addEquals(where, params, "company", company);
        addEquals(where, params, "plate_no", plateNo);
        String whereClause = String.join(" AND ", where);
        List<Map<String, Object>> groups = DbSupport.normalizeRows(jdbc.queryForList(
                """
                SELECT company, sender, receiver, plate_no,
                       COUNT(*) as trips,
                       COALESCE(SUM(net_weight), 0) as total_weight,
                       COALESCE(AVG(CASE WHEN freight_rate IS NOT NULL THEN freight_rate + COALESCE(detour_surcharge, 0) END), 0) as avg_rate,
                       COALESCE(SUM(total_cost), 0) as total_freight
                FROM records
                """
                + "WHERE " + whereClause + "\n"
                + """
                GROUP BY company, sender, receiver, plate_no
                ORDER BY total_freight DESC
                """,
                params.toArray()
        ));
        Map<String, Object> grand = DbSupport.normalizeRow(jdbc.queryForMap(
                """
                SELECT COUNT(*) as trips,
                       COALESCE(SUM(net_weight), 0) as total_weight,
                       COALESCE(SUM(total_cost), 0) as total_freight,
                       COALESCE(SUM(CASE WHEN reviewed=1 THEN 1 ELSE 0 END), 0) as reviewed_count,
                       COALESCE(SUM(CASE WHEN reviewed=0 THEN 1 ELSE 0 END), 0) as unreviewed_count
                FROM records
                """
                + "WHERE " + whereClause,
                params.toArray()
        ));
        return Map.of(
                "month", month,
                "sender", sender == null ? "" : sender,
                "receiver", receiver == null ? "" : receiver,
                "company", company == null ? "" : company,
                "plate_no", plateNo == null ? "" : plateNo,
                "groups", groups,
                "grand_total", grand
        );
    }

    private static void addEquals(List<String> where, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.add(column + "=?");
            params.add(value);
        }
    }
}
