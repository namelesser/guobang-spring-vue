package com.guobang.transport.record;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class ExportSupport {
    private ExportSupport() {
    }

    public static final List<String[]> RECORD_COLUMNS = List.of(
            new String[]{"id", "ID"}, new String[]{"record_date", "日期"}, new String[]{"order_no", "单号"},
            new String[]{"sender", "发货单位"}, new String[]{"receiver", "收货单位"}, new String[]{"company", "开单公司"},
            new String[]{"plate_no", "车牌号"}, new String[]{"net_weight", "净重"}, new String[]{"driver", "司机"},
            new String[]{"freight_rate", "运费单价"}, new String[]{"detour_surcharge", "绕路加价"},
            new String[]{"total_cost", "总费用"}, new String[]{"source", "来源"}, new String[]{"reviewed", "是否核对"},
            new String[]{"reviewed_at", "核对时间"}, new String[]{"review_note", "审核备注"}, new String[]{"note", "备注"},
            new String[]{"ocr_status", "OCR状态"}, new String[]{"file_name", "文件名"}, new String[]{"image_id", "图片ID"},
            new String[]{"created_at", "创建时间"}, new String[]{"updated_at", "更新时间"}
    );

    public static byte[] csv(List<Map<String, Object>> rows, List<String[]> columns) {
        StringBuilder out = new StringBuilder("\uFEFF");
        writeCsvRow(out, columns.stream().map(col -> col[1]).toList());
        for (Map<String, Object> row : rows) {
            writeCsvRow(out, columns.stream().map(col -> value(row.get(col[0]))).toList());
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] xls(List<Map<String, Object>> rows, List<String[]> columns, String title) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html><head><meta charset="utf-8">
                <style>table{border-collapse:collapse}th,td{border:1px solid #999;padding:4px 6px;mso-number-format:'\\@'}th{background:#f2f4f7;font-weight:bold}</style>
                </head><body>
                """);
        html.append("<h3>").append(escape(title)).append("</h3><table><thead><tr>");
        for (String[] column : columns) {
            html.append("<th>").append(escape(column[1])).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr>");
            for (String[] column : columns) {
                html.append("<td>").append(escape(value(row.get(column[0])))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void writeCsvRow(StringBuilder out, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(values.get(i).replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
