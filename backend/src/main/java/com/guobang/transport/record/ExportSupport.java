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
            new String[]{"plate_no", "车牌号"}, new String[]{"net_weight", "净重"},
            new String[]{"freight_rate", "运费单价"}, new String[]{"detour_surcharge", "绕路加价"},
            new String[]{"total_cost", "总费用"}, new String[]{"source", "来源"}, new String[]{"reviewed", "是否核对"},
            new String[]{"reviewed_at", "核对时间"}, new String[]{"review_note", "审核备注"}, new String[]{"note", "备注"},
            new String[]{"ocr_status", "OCR状态"}, new String[]{"file_name", "文件名"}, new String[]{"image_id", "图片ID"},
            new String[]{"created_at", "创建时间"}, new String[]{"updated_at", "更新时间"}
    );

    public static byte[] csv(List<Map<String, Object>> rows, List<String[]> columns) {
        StringBuilder out = new StringBuilder("\uFEFF"); // 写入 BOM 头，确保 Excel 正确识别 UTF-8 编码
        writeCsvRow(out, columns.stream().map(col -> col[1]).toList()); // 写入表头行（中文列名）
        for (Map<String, Object> row : rows) {
            writeCsvRow(out, columns.stream().map(col -> value(row.get(col[0]))).toList()); // 逐行写入数据，按列定义顺序取值
        }
        return out.toString().getBytes(StandardCharsets.UTF_8); // 转为 UTF-8 字节数组返回
    }

    public static byte[] xls(List<Map<String, Object>> rows, List<String[]> columns, String title) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html><head><meta charset="utf-8">
                <style>table{border-collapse:collapse}th,td{border:1px solid #999;padding:4px 6px;mso-number-format:'\\@'}th{background:#f2f4f7;font-weight:bold}</style>
                </head><body>
                """); // 构建 HTML 骨架，mso-number-format 强制 Excel 以文本格式显示所有单元格，避免数字被自动转换
        html.append("<h3>").append(escape(title)).append("</h3><table><thead><tr>"); // 写入标题和表头开始标签
        for (String[] column : columns) {
            html.append("<th>").append(escape(column[1])).append("</th>"); // 逐列写入表头（中文列名），并转义 HTML 特殊字符
        }
        html.append("</tr></thead><tbody>"); // 表头结束，数据体开始
        for (Map<String, Object> row : rows) {
            html.append("<tr>"); // 开始一行数据
            for (String[] column : columns) {
                html.append("<td>").append(escape(value(row.get(column[0])))).append("</td>"); // 逐列写入单元格值
            }
            html.append("</tr>"); // 结束当前行
        }
        html.append("</tbody></table></body></html>"); // 关闭所有 HTML 标签
        return html.toString().getBytes(StandardCharsets.UTF_8); // 转为 UTF-8 字节数组，Excel 可直接打开此格式的 HTML 表格
    }

    public static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void writeCsvRow(StringBuilder out, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(','); // 非首列前加逗号分隔
            }
            out.append('"').append(values.get(i).replace("\"", "\"\"")).append('"'); // 用双引号包裹值，内部双引号转义为两个双引号（RFC 4180 标准）
        }
        out.append('\n'); // 行尾换行
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); // 转义 HTML 特殊字符，防止 XSS 和表格渲染异常
    }
}
