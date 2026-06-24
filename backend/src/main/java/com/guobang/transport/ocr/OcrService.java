package com.guobang.transport.ocr;

import com.guobang.transport.common.BusinessException;
import com.guobang.transport.collection.CollectionService;
import com.guobang.transport.db.DbSupport;

import com.guobang.transport.image.ImageService;
import com.guobang.transport.mapper.OcrTaskMapper;
import com.guobang.transport.mapper.RecordMapper;
import com.guobang.transport.rate.RateService;
import com.guobang.transport.record.RecordService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 服务，负责调用 OCR 引口识别图片文字并解析为运输记录
 */
@Service
@RequiredArgsConstructor
public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    /** 车牌号正则 */
    private static final Pattern PLATE_PATTERN = Pattern.compile("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁][A-Z][A-Z0-9]{5,6}");
    /** 公司名称后缀正则 */
    private static final Pattern COMPANY_SUFFIX = Pattern.compile(".+?(运输|物流|货运|集团|公司|有限|股份|商贸|贸易|实业|煤炭|能源|矿业|建材)");
    /** 公司名称清理正则 */
    private static final Pattern COMPANY_CLEAN = Pattern.compile("\\s+|驾驶员.*|司机.*|运输证.*|资格证.*|经营许可.*");
    /** 公司名称分割正则 */
    private static final Pattern COMPANY_SPLIT = Pattern.compile("[,，;；\\s]+");

    private final OcrTaskMapper ocrTaskMapper;
    private final RecordMapper recordMapper;
    private final ImageService imageService;
    private final RecordService recordService;
    private final RateService rateService;
    private final CollectionService collectionService;
    private final com.guobang.transport.mapper.FreightRateMapper freightRateMapper;
    private final OcrRemoteClient ocrRemoteClient;

    /** OCR 异步工作线程池 */
    private ExecutorService ocrWorker;

    /**
     * 初始化 OCR 工作线程池
     */
    @PostConstruct
    private void init() {
        this.ocrWorker = Executors.newSingleThreadExecutor(r -> { // 单线程执行器，避免并发OCR冲突
            Thread t = new Thread(r, "ocr-worker");
            t.setDaemon(true); // 设为守护线程，不阻止JVM退出
            return t;
        });
    }

    @PreDestroy
    private void shutdown() {
        if (ocrWorker != null) {
            ocrWorker.shutdownNow();
        }
    }

    public int enqueueImage(int recordId, int imageId, String fileName, String mimeType) {
        Map<String, Object> existing = ocrTaskMapper.findActiveTask(recordId, imageId); // 检查是否已有活跃任务
        if (existing != null) {
            return DbSupport.intValue(existing.get("id")); // 已存在则直接返回任务ID，避免重复
        }
        OcrTask task = new OcrTask(); // 创建新的OCR任务
        task.setRecordId((long) recordId);
        task.setImageId((long) imageId);
        task.setFileName(fileName);
        task.setMimeType(mimeType);
        task.setPriority(10); // 默认优先级10
        task.setStatus("pending"); // 初始状态为待处理
        task.setRetryCount(0);
        ocrTaskMapper.insertTask(task); // 插入数据库
        return task.getId().intValue();
    }

    public void processOcrTask(int taskId) {
        ocrWorker.submit(() -> { // 提交到单线程池异步执行
            try {
                Map<String, Object> task = ocrTaskMapper.claimTask(taskId); // 抢占指定任务，避免与定时轮询重复处理
                if (task == null) return; // 任务不存在则跳过
                processOcrSync(task); // 同步执行OCR处理
            } catch (Exception e) {
                log.error("OCR task {} unexpected error", taskId, e);
            }
        });
    }

    @Scheduled(fixedDelay = 2000)
    public void pollOcrTasks() {
        try {
            Map<String, Object> task = ocrTaskMapper.claimNextTask(); // 竞争获取下一个待处理任务
            if (task == null) {
                return; // 无待处理任务则跳过
            }
            ocrWorker.submit(() -> { // 提交到工作线程池
                try {
                    processOcrSync(task); // 执行OCR处理
                } catch (Exception e) {
                    log.error("OCR scheduler worker failed", e);
                }
            });
        } catch (Exception ex) {
            log.error("OCR scheduler failed to claim task", ex);
        }
    }

    private void processOcrSync(Map<String, Object> task) {
        int taskId = DbSupport.intValue(task.get("id")); // 提取任务ID
        int recordId = DbSupport.intValue(task.get("record_id")); // 提取关联记录ID
        int imageId = DbSupport.intValue(task.get("image_id")); // 提取关联图片ID
        String fileName = DbSupport.str(task.get("file_name"));
        log.info("Processing OCR task {} record {} image {} file {}", taskId, recordId, imageId, fileName);
        try {
            ImageService.ImageData imageData = imageService.data(imageId); // 读取图片二进制数据
            if (imageData == null || imageData.bytes() == null || imageData.bytes().length == 0) {
                ocrTaskMapper.markError(taskId, "image data is empty"); // 图片数据为空标记错误
                recordService.updateOcrStatus(recordId, "error", Map.of("ocr_text", "图片数据为空"));
                return;
            }
            String rawText;
            String ocrSource;
            try {
                rawText = ocrRemoteClient.callPaddleOcr(imageData.bytes()); // 优先使用PaddleOCR识别
                ocrSource = "PaddleOCR API";
            } catch (Exception e) {
                log.warn("OCR task {} PaddleOCR failed, falling back to Baidu: {}", taskId, e.getMessage()); // PaddleOCR失败时降级到百度OCR
                rawText = ocrRemoteClient.callBaiduOcr(imageData.bytes());
                ocrSource = "百度 OCR";
            }
            rawText = stripHtmlTags(rawText); // 清理HTML标签
            log.info("OCR task {} extracted {} chars", taskId, rawText.length());
            Map<String, Object> fields = extractFields(rawText); // 从OCR文本中提取结构化字段
            String company = DbSupport.str(fields.get("company"));
            String parseEntry = getParserLabel(company); // 获取解析器标签用于审计
            fields.put("ocr_text", rawText);
            String currentSource = DbSupport.str(task.get("source"));
            String newSource = currentSource.isBlank() ? "ocr" : currentSource; // 记录数据来源
            Map<String, Object> ocrData = new LinkedHashMap<>();
            ocrData.put("ocr_text", rawText);
            ocrData.put("sender", fields.get("sender"));
            ocrData.put("receiver", fields.get("receiver"));
            ocrData.put("company", fields.get("company"));
            ocrData.put("plate_no", fields.get("plate_no"));
            ocrData.put("net_weight", fields.get("net_weight"));
            ocrData.put("record_date", fields.get("record_date"));
            ocrData.put("order_no", fields.get("order_no"));
            ocrData.put("source", newSource);
            ocrData.put("reviewed", 0); // OCR结果默认未审核
            ocrData.put("reviewed_at", null);
            ocrData.put("review_note", "OCR引擎：" + ocrSource + "；解析入口：" + parseEntry); // 记录使用的OCR引擎和解析器
            recordService.updateOcrStatus(recordId, "done", ocrData); // 更新记录状态为完成
            ocrTaskMapper.markDone(taskId); // 标记任务完成
            System.out.println("[OCR] task " + taskId + " done");
        } catch (Exception e) {
            ocrTaskMapper.markError(taskId, e.getMessage()); // 标记任务错误
            recordService.updateOcrStatus(recordId, "error", Map.of("ocr_text", e.getMessage()));
            System.out.println("[OCR] task " + taskId + " error: " + e.getMessage());
        }
    }

    private static final Map<String, String> PARSER_LABELS = Map.of(
            "望谟县里谷沟腾飞砂石有限公司", "腾飞砂石专用解析",
            "贵州森垚水泥有限公司", "森垚水泥专用解析",
            "贵州秀明建材有限公司", "秀明建材专用解析",
            "__weighing_slip__", "称重计量单解析"
    );

    private String getParserLabel(String company) {
        if (company == null || company.isEmpty()) return "通用解析"; // 无公司信息时使用通用解析
        String label = PARSER_LABELS.get(company); // 按公司名查找专用解析器标签
        return label != null ? label : "通用解析"; // 未匹配则使用通用解析
    }

    private static final Pattern WG_NO = Pattern.compile("WG\\d{10,16}");
    private static final Pattern M_NO = Pattern.compile("M\\d+");
    private static final Pattern DIGITS_6 = Pattern.compile("\\d{6,}");
    private static final Pattern DATE_SLASH = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");
    private static final Pattern DATE_CN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern DATE_COMPACT = Pattern.compile("(\\d{4})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])");
    private static final Pattern NET_WEIGHT = Pattern.compile("净[\\s\\n]*重[：:\\s]*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern NET_WEIGHT2 = Pattern.compile("净[\\s\\n]*重[^\\d]{0,8}([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PLATE_ANY = Pattern.compile("([贵豫京沪粤鲁浙苏][A-Z]{1,2}\\d{4,5})");
    private static final Pattern COMPANY_NAME_PAT = Pattern.compile("[\\u4e00-\\u9fff]{4,}(?:有限公司|有限责任公司|砂石场|沙石场|建材厂|砖厂)");
    private static final Pattern COMPANY_LABEL = Pattern.compile("(?:开单公司|开票公司|过磅单位|销售单位|供货单位)[：:\\s]*([^\\n]+)");
    private static final Pattern SENDER_LABEL = Pattern.compile("发货单位[：:\\s]*([^\\n]+)");
    private static final Pattern RECEIVER_LABEL = Pattern.compile("收货单位[：:\\s]*([^\\n]+)");
    private static final Pattern CUSTOMER_LABEL = Pattern.compile("客户名称[：:\\s]*([^\\n]+)");
    private static final Pattern NO_LABEL = Pattern.compile("(?:NO|N0|NO\\.?|单号)[：:.\\s]*([A-Z0-9]{4,32})", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALL_PLATES = Set.of("贵AE3197", "贵AB5923");
    private static final Set<String> GENERIC_VALUES = Set.of("公司", "有限公司", "有限责任公司", "unknown", "unknown有限公司");

    private Map<String, Map<String, Object>> COMPANY_PROFILES;

    private Map<String, Map<String, Object>> getCompanyProfiles() {
        if (COMPANY_PROFILES != null) return COMPANY_PROFILES; // 懒加载缓存
        Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        Map<String, Object> tengfei = new LinkedHashMap<>(); // 腾飞砂石公司配置
        tengfei.put("id_keywords", List.of("腾飞砂石", "里谷沟"));
        tengfei.put("valid_plates", Set.of("贵AE3197", "贵AB5923"));
        tengfei.put("time_strategy", "no_prefix"); // 从单号前缀提取日期
        tengfei.put("net_strategy", "blank"); // 净重字段为空
        tengfei.put("sender_strategy", "known_list"); // 从已知发货方列表匹配
        tengfei.put("no_pattern", "WG\\d{13}");
        tengfei.put("known_senders", List.of("贵州嘉慧铁路物资贸易有限公司"));
        tengfei.put("known_receivers", List.of(
                "中铁五局集团有限公司黄百铁路1号拌合站",
                "中铁五局集团有限公司黄百铁路2号拌合站",
                "黄百铁路大桥局",
                "黄百铁路二十三局4号站",
                "黄百铁路二十三局5号站",
                "中铁十八局集团有限公司黄百铁路4号搅拌站"));
        profiles.put("望谟县里谷沟腾飞砂石有限公司", tengfei);

        Map<String, Object> senyao = new LinkedHashMap<>(); // 森垚水泥公司配置
        senyao.put("id_keywords", List.of("森垚水泥", "销售发货单（骨料）", "销售发货单", "森", "水泥"));
        senyao.put("valid_plates", Set.of("贵AE3197"));
        senyao.put("time_strategy", "accurate"); // 精确日期提取
        senyao.put("net_strategy", "ocr"); // 从OCR文本提取净重
        senyao.put("sender_strategy", "self"); // 发货方即公司自身
        senyao.put("no_pattern", "M\\d+");
        profiles.put("贵州森垚水泥有限公司", senyao);

        Map<String, Object> xiuming = new LinkedHashMap<>(); // 秀明建材公司配置
        xiuming.put("id_keywords", List.of("秀明建材"));
        xiuming.put("valid_plates", Set.of("贵AE3197"));
        xiuming.put("time_strategy", "accurate");
        xiuming.put("net_strategy", "ocr");
        xiuming.put("sender_strategy", "self");
        xiuming.put("no_pattern", "\\d{6,}");
        xiuming.put("known_receivers", List.of("吉祥免烧砖厂"));
        profiles.put("贵州秀明建材有限公司", xiuming);

        COMPANY_PROFILES = profiles; // 缓存结果
        return profiles;
    }

    private List<String> dbCollectionValues(String category) {
        try {
            String normalizedCategory = "plate".equals(category) ? "plate_no" : category; // 兼容旧代码中的车牌分类别名
            return collectionService.list(normalizedCategory).stream()
                    .map(c -> c.getValue())
                    .filter(v -> v != null && !v.isBlank() && !"未知".equals(v)) // 过滤空值和"未知"
                    .toList();
        } catch (Exception e) {
            return List.of(); // 查询失败返回空列表
        }
    }

    private List<String> freightRateValues(String company, String field) {
        try {
            if ("sender".equals(field)) {
                return freightRateMapper.findSendersByOrigin(company); // 按公司查询已知发货方
            } else if ("receiver".equals(field) || "destination".equals(field)) {
                return freightRateMapper.findDestinationsByOrigin(company); // 按公司查询已知收货方
            }
            return List.of();
        } catch (Exception e) {
            return List.of(); // 查询失败返回空列表
        }
    }

    private String identifyCompany(String ocrText) {
        if (ocrText.contains("称重计量单") || ocrText.contains("称 重 计 量 单")) { // 检测是否为称重计量单
            return "称重计量单";
        }
        for (var entry : getCompanyProfiles().entrySet()) { // 遍历公司配置匹配关键词
            List<String> keywords = (List<String>) entry.getValue().get("id_keywords");
            for (String kw : keywords) {
                if (ocrText.contains(kw)) return entry.getKey(); // 关键词命中则返回公司名
            }
        }
        String flat = ocrText.replace("\n", ""); // 去除换行后匹配已知发货方
        for (var entry : getCompanyProfiles().entrySet()) {
            List<String> senders = (List<String>) entry.getValue().getOrDefault("known_senders", List.of());
            for (String s : senders) {
                String prefix = s.replace("有限公司", "").replace("有限责任公司", ""); // 提取公司名简称
                if (flat.contains(s) || (!prefix.isEmpty() && flat.contains(prefix))) {
                    return entry.getKey(); // 发货方匹配则关联到对应公司
                }
            }
        }
        String first = ocrText.strip().split("\n")[0].replaceAll("过磅单$", ""); // 取首行作为公司名候选
        return first.contains("公司") ? first : "unknown"; // 包含"公司"则使用，否则标记unknown
    }

    private String normText(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s:：,，.。;；|/\\\\\\-_*]+", ""); // 去除所有标点符号和空白用于模糊匹配
    }

    private static double chineseSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0; // 完全匹配
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshtein(a, b); // 计算编辑距离
        return 1.0 - (double) dist / maxLen; // 归一化为相似度0-1
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1]; // 动态规划矩阵
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i; // 初始化第一列
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j; // 初始化第一行
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1; // 字符相同则代价为0
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost); // 取删除、插入、替换最小代价
            }
        }
        return dp[a.length()][b.length()]; // 返回右下角即编辑距离
    }

    private static long simHash(String text) {
        int[] bits = new int[64]; // 64位SimHash向量
        for (int i = 0; i < text.length() - 1; i++) { // 以bigram为特征
            long hash = fnv1a(text.substring(i, i + 2)); // 计算bigram的FNV1a哈希
            for (int j = 0; j < 64; j++) {
                bits[j] += ((hash & (1L << j)) != 0) ? 1 : -1; // 位为1则加1，否则减1
            }
        }
        long result = 0;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) result |= (1L << i); // 正数位设为1，负数位设为0
        }
        return result;
    }

    private static long fnv1a(String s) {
        long hash = 0xcbf29ce484222325L; // FNV1a 64位初始值
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i); // 异或当前字符
            hash *= 0x100000001b3L; // 乘以FNV质数
        }
        return hash;
    }

    private static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b); // 异或后统计不同位数即汉明距离
    }

    private static final int SIMHASH_HAMMING_THRESHOLD = 10;

    static class MatchResult {
        final String value;
        final double similarity;
        final boolean matched;

        MatchResult(String value, double similarity, boolean matched) {
            this.value = value;
            this.similarity = similarity;
            this.matched = matched;
        }
    }

    private MatchResult matchWithSimHashLevenshtein(String rawValue, List<String> candidates, double minScore) {
        if (rawValue == null || rawValue.isEmpty()) return new MatchResult("", 0.0, false); // 空值直接返回未匹配
        String rawNorm = normText(rawValue);
        candidates = candidates.stream().filter(c -> c != null && !"未知".equals(c)).toList(); // 过滤无效候选
        if (candidates.isEmpty()) return new MatchResult(rawValue, 0.0, false);

        for (String cand : candidates) {
            if (rawNorm.equals(normText(cand))) return new MatchResult(cand, 1.0, true); // 精确匹配直接返回
        }

        long rawHash = simHash(rawNorm); // 计算原始值的SimHash
        List<String> recalled = new java.util.ArrayList<>();
        for (String cand : candidates) {
            if (hammingDistance(rawHash, simHash(normText(cand))) <= SIMHASH_HAMMING_THRESHOLD) recalled.add(cand); // SimHash召回相似候选
        }

        String best = "";
        double bestScore = 0.0;
        for (String cand : recalled) {
            double score = chineseSimilarity(rawNorm, normText(cand)); // 对召回集计算精确编辑距离相似度
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        if (bestScore >= minScore) return new MatchResult(best, bestScore, true); // 超过阈值则匹配成功
        return new MatchResult(rawValue, bestScore, false);
    }

    private String bestKnownValue(String rawValue, List<String> candidates, String fullText, double minScore) {
        String rawNorm = normText(rawValue);
        String fullNorm = normText(fullText); // 全文标准化用于兜底匹配
        candidates = candidates.stream().filter(c -> c != null && !"未知".equals(c)).toList(); // 过滤无效候选
        if (candidates.isEmpty()) return rawValue != null ? rawValue : "";
        for (String cand : candidates) {
            if (!rawNorm.isEmpty() && rawNorm.equals(normText(cand))) return cand; // 精确匹配直接返回标准值
        }
        if (!rawNorm.isEmpty()) {
            List<String> partialHits = new java.util.ArrayList<>();
            for (String cand : candidates) {
                String candNorm = normText(cand);
                if (!GENERIC_VALUES.contains(rawNorm) && rawNorm.length() >= 2 && candNorm.contains(rawNorm) && candNorm.length() > rawNorm.length()) {
                    partialHits.add(cand); // 原始值是候选的子串（如简称匹配全称）
                }
            }
            if (!partialHits.isEmpty()) {
                return partialHits.stream().min(java.util.Comparator.comparingInt(String::length)).orElse(rawValue); // 取最短的匹配（最精确）
            }
        }
        List<String> exactHits = new java.util.ArrayList<>();
        for (String cand : candidates) {
            String candNorm = normText(cand);
            if (!candNorm.isEmpty() && (fullNorm.contains(candNorm) || rawNorm.contains(candNorm))) {
                exactHits.add(cand); // 候选值出现在全文或原始值中
            }
        }
        if (!exactHits.isEmpty()) {
            return exactHits.stream().max(java.util.Comparator.comparingInt(String::length)).orElse(rawValue); // 取最长匹配（最完整）
        }
        if (rawNorm.isEmpty()) return rawValue != null ? rawValue : "";

        MatchResult r1 = matchWithSimHashLevenshtein(rawValue, candidates, minScore); // SimHash+编辑距离模糊匹配
        if (r1.matched) return r1.value;

        long fullHash = simHash(fullNorm); // 对全文做SimHash进行兜底召回
        List<String> fullRecalled = new java.util.ArrayList<>();
        for (String cand : candidates) {
            String candNorm = normText(cand);
            if (candNorm.length() >= 4 && hammingDistance(fullHash, simHash(candNorm)) <= SIMHASH_HAMMING_THRESHOLD) {
                fullRecalled.add(cand); // 全文SimHash召回
            }
        }
        String fullBest = "";
        double fullBestScore = 0.0;
        for (String cand : fullRecalled) {
            double score = chineseSimilarity(normText(cand), fullNorm);
            if (score > fullBestScore) {
                fullBest = cand;
                fullBestScore = score;
            }
        }
        if (fullBestScore >= 0.85) return fullBest; // 全文匹配相似度>=85%则采纳

        return rawValue != null ? rawValue : ""; // 所有匹配策略都失败，返回原始值
    }

    private double sequenceMatchScore(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        int matches = 0;
        int ai = 0, bi = 0;
        while (ai < a.length() && bi < b.length()) {
            if (a.charAt(ai) == b.charAt(bi)) {
                matches++; // 字符匹配成功
                ai++;
                bi++;
            } else {
                bi++; // 不匹配时只移动b指针，允许跳过
            }
        }
        return (2.0 * matches) / maxLen; // F1-like得分，考虑匹配比例
    }

    private String extractTime(String text) {
        for (Pattern pat : new Pattern[]{DATE_SLASH, DATE_CN, DATE_COMPACT}) { // 依次尝试斜杠、中文、紧凑格式
            Matcher m = pat.matcher(text);
            if (m.find()) {
                try {
                    int y = Integer.parseInt(m.group(1));
                    int mo = Integer.parseInt(m.group(2));
                    int d = Integer.parseInt(m.group(3));
                    if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) { // 基本日期合法性校验
                        return String.format("%04d-%02d-%02d", y, mo, d); // 格式化为yyyy-MM-dd
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ""; // 未匹配到有效日期
    }

    private String noDate(String no) {
        String digits = no.replaceAll("[^0-9]", ""); // 提取纯数字
        if (digits.length() >= 8) {
            return digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8); // 前8位解析为日期
        }
        return ""; // 数字不足8位则无法解析
    }

    private String normalizeNoText(String text) {
        if (text == null) return "";
        text = text.toUpperCase(); // 统一转大写
        text = text.replace('０', '0').replace('１', '1').replace('２', '2').replace('３', '3').replace('４', '4') // 全角数字转半角
                .replace('５', '5').replace('６', '6').replace('７', '7').replace('８', '8').replace('９', '9')
                .replace('Ｏ', 'O').replace('〇', '0').replace('○', '0').replace('Ｗ', 'W').replace('Ｇ', 'G'); // 全角字母和特殊符号转换
        text = text.replaceAll("[\\s:：,，.。;；|/\\\\\\-_*]+", ""); // 去除标点和空白
        return text;
    }

    private String extractNo(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) { // 优先匹配WG开头的单号
            String clean = normalizeNoText(line.trim());
            Matcher m = WG_NO.matcher(clean);
            if (m.find()) return m.group();
        }
        for (String line : lines) { // 其次匹配M开头的单号
            String clean = normalizeNoText(line.trim());
            Matcher m = M_NO.matcher(clean);
            if (m.find()) return m.group();
        }
        for (String line : lines) { // 再匹配6位以上纯数字（排除日期行）
            if (line.contains("/") || line.contains("年") || line.contains("-")) continue; // 跳过含日期的行
            String clean = normalizeNoText(line.trim());
            Matcher m = DIGITS_6.matcher(clean);
            if (m.find()) return m.group();
        }
        for (int i = 0; i < lines.length; i++) { // 最后尝试从NO/单号标签后提取
            Matcher m = NO_LABEL.matcher(lines[i]);
            if (m.find()) {
                String cand = normalizeNoText(m.group(1));
                for (Pattern pat : new Pattern[]{WG_NO, M_NO, DIGITS_6}) {
                    Matcher mm = pat.matcher(cand);
                    if (mm.find()) return mm.group(); // 标签值中匹配已知模式
                }
                if (!cand.isEmpty()) return cand;
            }
            if (Pattern.compile("(?:NO|N0|NO\\.?|单号)", Pattern.CASE_INSENSITIVE).matcher(lines[i]).find() && i + 1 < lines.length) {
                String nxt = normalizeNoText(lines[i + 1].trim()); // NO标签在上一行，单号在下一行
                for (Pattern pat : new Pattern[]{WG_NO, M_NO, DIGITS_6}) {
                    Matcher mm = pat.matcher(nxt);
                    if (mm.find()) return mm.group();
                }
            }
        }
        return ""; // 所有策略都未匹配到单号
    }

    private String extractNetWeight(String text) {
        for (Pattern pat : new Pattern[]{NET_WEIGHT, NET_WEIGHT2}) { // 先匹配精确格式，再匹配宽松格式
            Matcher m = pat.matcher(text);
            if (m.find()) return m.group(1); // 返回捕获组中的数值
        }
        return ""; // 未匹配到净重
    }

    private String extractReceiver(String text) {
        String[] lines = text.split("\n");
        Matcher m = RECEIVER_LABEL.matcher(text); // 先尝试"收货单位：xxx"标签匹配
        if (m.find()) {
            String s = m.group(1).strip();
            if (!s.isEmpty() && !s.equals("扣重") && !s.equals("司机")) return s; // 排除误匹配的"扣重"和"司机"
        }
        for (int i = 0; i < lines.length; i++) { // 标签匹配失败时，按行查找"收货单位"并取后续行
            if (lines[i].contains("收货单位")) {
                java.util.List<String> parts = new java.util.ArrayList<>();
                for (int j = i + 1; j < Math.min(i + 3, lines.length); j++) {
                    String nxt = lines[j].strip();
                    if (!nxt.isEmpty() && !nxt.contains("扣重") && !nxt.contains("司机") && !nxt.contains("司磅员") && !nxt.contains("销售电话")) {
                        parts.add(nxt); // 收集有效行
                    } else break;
                }
                if (!parts.isEmpty()) return String.join("", parts); // 拼接多行为收货方
            }
        }
        return "";
    }

    private String extractSenderFromText(String text) {
        String[] lines = text.split("\n");
        Matcher m = SENDER_LABEL.matcher(text); // 先尝试"发货单位：xxx"标签匹配
        if (m.find()) {
            String s = m.group(1).strip();
            if (!s.isEmpty() && !s.equals("扣重") && !s.equals("收货单位")) return s; // 排除误匹配
        }
        for (int i = 0; i < lines.length; i++) { // 标签匹配失败时，按行查找"发货单位"
            if (lines[i].contains("发货单位")) {
                for (int j = i + 1; j < Math.min(i + 3, lines.length); j++) {
                    String nxt = lines[j].strip();
                    if (!nxt.isEmpty() && !nxt.contains("收货单位") && !nxt.contains("扣重") && !nxt.contains("司机")) {
                        return nxt; // 取发货单位后续有效行
                    } else break;
                }
            }
        }
        Matcher m2 = CUSTOMER_LABEL.matcher(text); // 最后尝试"客户名称：xxx"作为发货方
        return m2.find() ? m2.group(1).strip() : "";
    }

    private String extractCompanyFromText(String text) {
        Matcher m = COMPANY_LABEL.matcher(text); // 先尝试"开单公司/过磅单位：xxx"标签匹配
        if (m.find()) {
            String value = m.group(1).strip();
            if (!value.isEmpty() && !value.equals("发货单位") && !value.equals("收货单位") && !value.equals("客户名称")) {
                return value; // 排除标签误匹配
            }
        }
        Matcher m2 = COMPANY_NAME_PAT.matcher(text); // 回退到正则匹配中文公司名
        return m2.find() ? m2.group() : "";
    }

    private String fixSender(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.strip().equalsIgnoreCase("unknown")) return ""; // "unknown"视为空
        s = s.replaceAll("[^\\u4e00-\\u9fff]+$", ""); // 去除末尾非中文字符
        s = s.replace("有有限公司", "有限公司"); // 修复OCR重复识别错误
        if (s.endsWith("有")) s = s.substring(0, s.length() - 1); // 去除末尾多余的"有"
        s = s.replace("有限公司公司", "有限公司"); // 修复重复"公司"
        if (!s.contains("公司")) s += "有限公司"; // 缺少公司后缀时补全
        if (s.endsWith("有限公司有限公司")) s = s.substring(0, s.length() - 4); // 去除重复后缀
        return s;
    }

    private String findSecondCompany(String text, String exclude) {
        Matcher m = Pattern.compile("[\\u4e00-\\u9fff]{4,}(?:有限公司|商贸有限公司|建材有限公司)").matcher(text); // 匹配中文公司名
        while (m.find()) {
            String c = m.group();
            if (!c.contains(exclude) && !exclude.contains(c)) return c; // 返回第一个不等于排除项的公司名
        }
        return "";
    }

    private String extractPlateAny(String text, Set<String> validPlates) {
        Matcher m = PLATE_ANY.matcher(text); // 匹配车牌号模式
        if (m.find() && validPlates.contains(m.group(1))) return m.group(1); // 仅返回在有效车牌集合中的结果
        return "";
    }

    private Map<String, Object> parseTengfei(String ocrText, Map<String, Object> cfg) {
        Map<String, Object> out = emptyFields(); // 初始化空字段
        String no = extractNo(ocrText); // 提取单号
        String noPat = (String) cfg.get("no_pattern");
        out.put("order_no", no.matches(noPat) ? no : ""); // 单号需匹配WG+13位数字模式
        out.put("record_date", noDate((String) out.get("order_no"))); // 从单号中提取日期
        if (((String) out.get("record_date")).isEmpty()) out.put("record_date", extractTime(ocrText)); // 单号无日期则从文本提取
        out.put("company", "望谟县里谷沟腾飞砂石有限公司"); // 固定公司名

        List<String> dbSenders = freightRateValues("望谟县里谷沟腾飞砂石有限公司", "sender"); // 从运价表查询发货方
        if (dbSenders.isEmpty()) dbSenders = (List<String>) cfg.getOrDefault("known_senders", List.of()); // 降级到配置的已知列表
        List<String> dbReceivers = freightRateValues("望谟县里谷沟腾飞砂石有限公司", "receiver");
        if (dbReceivers.isEmpty()) dbReceivers = (List<String>) cfg.getOrDefault("known_receivers", List.of());
        List<String> dbPlates = dbCollectionValues("plate"); // 从字典表查询有效车牌
        Set<String> validPlates = dbPlates.isEmpty() ? (Set<String>) cfg.getOrDefault("valid_plates", ALL_PLATES) : new java.util.HashSet<>(dbPlates);

        String flat = ocrText.replace("\n", "");
        String clean = flat.replaceAll("(发货单位|收货单位|扣重|备注|司机|司磅员)", ""); // 去除干扰标签
        for (String s : dbSenders) {
            String prefix = s.replace("有限公司", "").replace("有限责任公司", ""); // 提取公司简称
            if (clean.contains(s) || (!prefix.isEmpty() && clean.contains(prefix))) {
                out.put("sender", s); // 全称或简称匹配则确认发货方
                break;
            }
        }
        if (((String) out.get("sender")).isEmpty()) {
            out.put("sender", fixSender(extractSenderFromText(ocrText))); // 回退到文本提取
        }
        out.put("sender", bestKnownValue((String) out.get("sender"), dbSenders, ocrText, 0.68)); // 模糊匹配标准值
        for (String r : dbReceivers) {
            if (clean.contains(r)) {
                out.put("receiver", r); // 收货方精确匹配
                break;
            }
        }
        if (((String) out.get("receiver")).isEmpty()) {
            out.put("receiver", extractReceiver(ocrText)); // 回退到文本提取
        }
        out.put("receiver", bestKnownValue((String) out.get("receiver"), dbReceivers, ocrText, 0.82)); // 模糊匹配标准值
        out.put("plate_no", bestKnownValue(extractPlateAny(ocrText, validPlates), dbPlates.isEmpty() ? new java.util.ArrayList<>(validPlates) : dbPlates, ocrText, 0.68)); // 车牌匹配
        return out;
    }

    private Map<String, Object> parseSenyao(String ocrText, Map<String, Object> cfg) {
        Map<String, Object> out = emptyFields(); // 初始化空字段
        out.put("order_no", extractNo(ocrText)); // 提取单号
        out.put("record_date", extractTime(ocrText)); // 提取日期
        out.put("company", "贵州森垚水泥有限公司"); // 固定公司名
        out.put("sender", "贵州森垚水泥有限公司"); // 发货方即自身
        out.put("net_weight", extractNetWeight(ocrText)); // 提取净重
        List<String> dbReceivers = freightRateValues("贵州森垚水泥有限公司", "receiver"); // 从运价表查询收货方
        List<String> dbPlates = dbCollectionValues("plate"); // 查询有效车牌
        String receiver = findSecondCompany(ocrText, "贵州森垚水泥有限公司"); // 查找文本中第二个公司名作为收货方
        if (receiver.isEmpty()) receiver = extractReceiver(ocrText); // 回退到标签提取
        out.put("receiver", bestKnownValue(receiver, dbReceivers, ocrText, 0.82)); // 模糊匹配标准值
        Set<String> validPlates = dbPlates.isEmpty() ? (Set<String>) cfg.getOrDefault("valid_plates", ALL_PLATES) : new java.util.HashSet<>(dbPlates);
        out.put("plate_no", bestKnownValue(extractPlateAny(ocrText, validPlates), dbPlates.isEmpty() ? new java.util.ArrayList<>(validPlates) : dbPlates, ocrText, 0.68)); // 车牌匹配
        return out;
    }

    private Map<String, Object> parseXiuming(String ocrText, Map<String, Object> cfg) {
        Map<String, Object> out = emptyFields(); // 初始化空字段
        out.put("order_no", extractNo(ocrText)); // 提取单号
        out.put("record_date", extractTime(ocrText)); // 提取日期
        out.put("company", "贵州秀明建材有限公司"); // 固定公司名
        out.put("sender", "贵州秀明建材有限公司"); // 发货方即自身
        out.put("net_weight", extractNetWeight(ocrText)); // 提取净重
        String flat = ocrText.replace("\n", "");
        String clean = flat.replaceAll("(发货单位|收货单位|扣重|备注|司机|司磅员)", ""); // 去除干扰标签
        List<String> dbReceivers = freightRateValues("贵州秀明建材有限公司", "receiver"); // 从运价表查询收货方
        if (dbReceivers.isEmpty()) dbReceivers = (List<String>) cfg.getOrDefault("known_receivers", List.of()); // 降级到配置列表
        for (String r : dbReceivers) {
            if (clean.contains(r)) {
                out.put("receiver", r); // 收货方精确匹配
                break;
            }
        }
        if (((String) out.get("receiver")).isEmpty()) {
            out.put("receiver", extractReceiver(ocrText)); // 回退到标签提取
        }
        String rawReceiver = (String) out.get("receiver");
        out.put("receiver", bestKnownValue(rawReceiver, dbReceivers, ocrText, 0.82)); // 模糊匹配标准值
        List<String> dbPlates = dbCollectionValues("plate"); // 查询有效车牌
        Set<String> validPlates = dbPlates.isEmpty() ? (Set<String>) cfg.getOrDefault("valid_plates", ALL_PLATES) : new java.util.HashSet<>(dbPlates);
        out.put("plate_no", bestKnownValue(extractPlateAny(ocrText, validPlates), dbPlates.isEmpty() ? new java.util.ArrayList<>(validPlates) : dbPlates, ocrText, 0.68)); // 车牌匹配
        return out;
    }

    private Map<String, Object> parseWeighingSlip(String ocrText) {
        Map<String, Object> out = emptyFields(); // 初始化空字段
        List<String> dbCompanies = dbCollectionValues("company"); // 查询公司字典
        List<String> dbSenders = dbCollectionValues("sender"); // 查询发货方字典
        List<String> dbPlates = dbCollectionValues("plate"); // 查询车牌字典
        out.put("order_no", extractNo(ocrText)); // 提取单号
        out.put("record_date", extractTime(ocrText)); // 提取日期
        out.put("net_weight", extractNetWeight(ocrText)); // 提取净重
        out.put("plate_no", bestKnownValue(extractPlateAny(ocrText, dbPlates.isEmpty() ? ALL_PLATES : new java.util.HashSet<>(dbPlates)), dbPlates.isEmpty() ? new java.util.ArrayList<>(ALL_PLATES) : dbPlates, ocrText, 0.68)); // 车牌匹配
        Matcher m = SENDER_LABEL.matcher(ocrText);
        String sender = "";
        if (m.find()) {
            sender = m.group(1).strip(); // 从标签提取发货方
        }
        if (sender.isEmpty()) sender = extractSenderFromText(ocrText); // 回退到文本提取
        sender = bestKnownValue(sender, dbSenders, ocrText, 0.68); // 模糊匹配标准值
        out.put("sender", sender);
        out.put("company", bestKnownValue(sender, dbCompanies, ocrText, 0.68)); // 发货方即公司
        String company = (String) out.get("company");
        List<String> dbReceivers = freightRateValues(company, "receiver"); // 按公司查询收货方
        if (dbReceivers.isEmpty()) dbReceivers = dbCollectionValues("receiver"); // 降级到收货方字典
        Matcher m2 = RECEIVER_LABEL.matcher(ocrText);
        String receiver = "";
        if (m2.find()) {
            receiver = m2.group(1).strip(); // 从标签提取收货方
        }
        if (receiver.isEmpty()) receiver = extractReceiver(ocrText); // 回退到文本提取
        out.put("receiver", bestKnownValue(receiver, dbReceivers, ocrText, 0.82)); // 模糊匹配标准值
        return out;
    }

    private Map<String, Object> parseUnknown(String ocrText) {
        Map<String, Object> out = emptyFields(); // 初始化空字段
        List<String> dbCompanies = dbCollectionValues("company"); // 查询公司字典
        List<String> dbPlates = dbCollectionValues("plate"); // 查询车牌字典

        out.put("order_no", extractNo(ocrText)); // 提取单号
        out.put("record_date", extractTime(ocrText)); // 提取日期
        if (((String) out.get("record_date")).isEmpty()) out.put("record_date", noDate((String) out.get("order_no"))); // 从单号提取日期作为兜底
        String company = identifyCompany(ocrText); // 识别公司
        if (company.strip().equalsIgnoreCase("unknown")) company = extractCompanyFromText(ocrText); // unknown时从文本提取
        out.put("company", bestKnownValue(company, dbCompanies, ocrText, 0.68)); // 模糊匹配标准公司名
        String confirmedCompany = (String) out.get("company");
        List<String> dbSenders = freightRateValues(confirmedCompany, "sender"); // 按公司查询发货方
        if (dbSenders.isEmpty()) dbSenders = dbCollectionValues("sender"); // 降级到发货方字典
        List<String> dbReceivers = freightRateValues(confirmedCompany, "receiver"); // 按公司查询收货方
        if (dbReceivers.isEmpty()) dbReceivers = dbCollectionValues("receiver"); // 降级到收货方字典
        out.put("sender", bestKnownValue(fixSender(extractSenderFromText(ocrText).isEmpty() ? confirmedCompany : extractSenderFromText(ocrText)), dbSenders, ocrText, 0.68)); // 发货方匹配，无标签时用公司名
        if (confirmedCompany.isEmpty() && dbCompanies.contains((String) out.get("sender"))) {
            out.put("company", out.get("sender")); // 公司为空但发货方在公司字典中，则用发货方作为公司
        }
        out.put("net_weight", extractNetWeight(ocrText)); // 提取净重
        out.put("receiver", bestKnownValue(extractReceiver(ocrText), dbReceivers, ocrText, 0.82)); // 收货方匹配
        out.put("plate_no", bestKnownValue(extractPlateAny(ocrText, dbPlates.isEmpty() ? ALL_PLATES : new java.util.HashSet<>(dbPlates)), dbPlates.isEmpty() ? new java.util.ArrayList<>(ALL_PLATES) : dbPlates, ocrText, 0.68)); // 车牌匹配
        return out;
    }

    private Map<String, Object> emptyFields() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("sender", ""); // 发货方
        f.put("receiver", ""); // 收货方
        f.put("company", ""); // 公司
        f.put("plate_no", ""); // 车牌号
        f.put("net_weight", ""); // 净重
        f.put("record_date", ""); // 日期
        f.put("order_no", ""); // 单号
        return f;
    }

    private Map<String, Object> extractFields(String rawText) {
        if (rawText == null || rawText.isBlank()) return emptyFields(); // 空文本返回空字段
        String company = identifyCompany(rawText); // 识别公司类型
        Map<String, Object> cfg = getCompanyProfiles().getOrDefault(company, Map.of()); // 获取公司配置
        Map<String, Object> fields;
        if ("__weighing_slip__".equals(company) || "称重计量单".equals(company)) {
            fields = parseWeighingSlip(rawText); // 称重计量单专用解析
        } else if ("望谟县里谷沟腾飞砂石有限公司".equals(company)) {
            fields = parseTengfei(rawText, cfg); // 腾飞砂石专用解析
        } else if ("贵州森垚水泥有限公司".equals(company)) {
            fields = parseSenyao(rawText, cfg); // 森垚水泥专用解析
        } else if ("贵州秀明建材有限公司".equals(company)) {
            fields = parseXiuming(rawText, cfg); // 秀明建材专用解析
        } else {
            fields = parseUnknown(rawText); // 通用解析器
        }
        return fields;
    }

    private String stripHtmlTags(String text) {
        if (text == null || text.isEmpty()) return text;
        text = text.replaceAll("(?i)<td[^>]*>", "\n"); // td标签转为换行
        text = text.replaceAll("(?i)</td>", ""); // 移除td闭合标签
        text = text.replaceAll("(?i)</?tr[^>]*>", "\n"); // tr标签转为换行
        text = text.replaceAll("<[^>]+>", ""); // 移除所有剩余HTML标签
        text = text.replace("&amp;", "&"); // HTML实体解码
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        text = text.replace("&nbsp;", " ");
        text = text.replaceAll("[ \t]+", " "); // 合并连续空白为单空格
        text = text.replaceAll("\\n\\s*\\n", "\n"); // 合并连续空行为单换行
        return text.strip();
    }

    public Map<String, Object> taskStatus(int taskId) {
        Map<String, Object> row = ocrTaskMapper.taskStatus(taskId); // 查询任务原始数据
        if (row == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("status", row.get("status"));
        out.put("record_id", row.get("record_id"));
        out.put("image_id", row.get("image_id"));
        out.put("error_message", row.get("error_message"));
        out.put("retry_count", row.get("retry_count"));
        out.put("created_at", row.get("created_at"));
        return out; // 返回精简的任务状态信息
    }

    public Map<String, Object> taskByRecordAndImage(int recordId, int imageId) {
        Map<String, Object> row = imageId > 0
                ? ocrTaskMapper.findActiveTask(recordId, imageId) // 按记录ID和图片ID查找活跃任务
                : ocrTaskMapper.latestTask(recordId); // 未指定图片时返回该记录最新任务
        if (row == null) {
            return null;
        }
        return taskStatus(DbSupport.intValue(row.get("id"))); // 返回精简的任务状态
    }

    public Map<String, Object> stats() {
        List<Map<String, Object>> rows = ocrTaskMapper.statusCounts(); // 查询各状态的任务数量
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byStatus.put(DbSupport.str(row.get("status")), row.get("cnt")); // 按状态分组统计
        }
        return Map.of("total", byStatus.values().stream().mapToInt(v -> ((Number) v).intValue()).sum(), "by_status", byStatus); // 返回总数和分状态统计
    }

    public boolean deleteTask(int id) {
        return ocrTaskMapper.deleteById((long) id) > 0; // 删除任务，返回是否成功
    }

    public boolean resetTask(int id) {
        return ocrTaskMapper.markPending(id) > 0; // 重置任务为待处理状态
    }

    public List<Map<String, Object>> listTasks(String status, int limit, int offset) {
        return ocrTaskMapper.listTasks(status, limit, offset); // 按状态分页查询任务列表
    }

    public Map<String, Object> taskStatusCounts() {
        List<Map<String, Object>> rows = ocrTaskMapper.statusCounts(); // 查询各状态数量
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byStatus.put(DbSupport.str(row.get("status")), row.get("cnt")); // 按状态名分组
        }
        return byStatus;
    }

    public Map<String, Object> retry(int taskId) {
        Map<String, Object> task = ocrTaskMapper.taskStatus(taskId); // 查询任务是否存在
        if (task == null) {
            throw new BusinessException("任务不存在", HttpStatus.NOT_FOUND);
        }
        ocrTaskMapper.markPending(taskId); // 重置为待处理状态
        processOcrTask(taskId); // 立即提交处理
        return Map.of("task_id", taskId, "status", "pending");
    }

    public int reocrImage(int imageId) {
        Map<String, Object> record = recordMapper.firstRecordByImageId(imageId); // 查找图片关联的第一条记录
        if (record == null) {
            throw new BusinessException("该图片未关联任何过磅记录", HttpStatus.NOT_FOUND);
        }
        int recordId = DbSupport.intValue(record.get("id"));
        Map<String, Object> imgRow = recordMapper.imageFileName(imageId); // 获取图片文件名
        String fileName = imgRow == null ? "reocr.jpg" : DbSupport.str(imgRow.get("file_name"));
        recordService.updateOcrStatus(recordId, "processing", Map.of("ocr_text", "", "ocr_status", "processing")); // 重置记录OCR状态为处理中
        int taskId = enqueueImage(recordId, imageId, fileName, "image/jpeg"); // 入队OCR任务
        ocrTaskMapper.markPending(taskId); // 确保任务为待处理状态
        processOcrTask(taskId); // 立即提交处理
        return taskId;
    }

    @Transactional
    public Map<String, Object> rereviewImage(int imageId) {
        Map<String, Object> record = recordMapper.firstRecordByImageId(imageId); // 查找图片关联的第一条记录
        if (record == null) {
            throw new BusinessException("该图片未关联任何过磅记录", HttpStatus.NOT_FOUND);
        }
        int recordId = DbSupport.intValue(record.get("id"));
        recordService.rereviewByImageId(imageId); // 触发重新审核
        Map<String, Object> updatedRecord = recordMapper.selectMapById(recordId); // 查询更新后的记录
        return Map.of("record_id", recordId, "updated_record", updatedRecord == null ? Map.of() : DbSupport.normalizeRow(updatedRecord));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public Map<String, Object> scan(MultipartFile image) throws Exception {
        String fileName = image.getOriginalFilename(); // 获取原始文件名
        if (fileName == null || fileName.isBlank()) {
            fileName = "scan.jpg"; // 默认文件名
        }
        String mimeType = image.getContentType(); // 获取MIME类型
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/jpeg"; // 默认MIME类型
        }
        byte[] bytes = image.getBytes(); // 读取图片字节
        int imageId = imageService.insert(fileName, bytes, mimeType); // 存储图片到数据库
        Map<String, Object> recordData = new LinkedHashMap<>();
        recordData.put("source", "ocr"); // 标记数据来源为OCR
        recordData.put("file_name", fileName);
        recordData.put("image_id", String.valueOf(imageId));
        int recordId = recordService.insert(recordData); // 创建运输记录
        int taskId = enqueueImage(recordId, imageId, fileName, mimeType); // 入队OCR任务
        processOcrTask(taskId); // 立即提交处理
        Map<String, Object> record = recordMapper.selectMapById(recordId);
        return Map.of("id", recordId, "record_id", recordId, "image_id", imageId, "task_id", taskId, "status", "pending",
                "record", record == null ? Map.of() : DbSupport.normalizeRow(record)); // 返回记录、图片和任务信息
    }

    public Map<String, Object> status(int recordId) {
        Map<String, Object> task = taskByRecordAndImage(recordId, 0); // 查询关联的OCR任务
        if (task == null) {
            Map<String, Object> record = recordMapper.selectMapById(recordId); // 无任务时查询记录本身
            if (record == null) {
                throw new BusinessException("记录不存在", HttpStatus.NOT_FOUND);
            }
            return Map.of("status", "no_task", "ocr_status", "no_task", "record", DbSupport.normalizeRow(record)); // 无OCR任务返回no_task状态
        }
        String status = DbSupport.trim(task.get("status"));
        return Map.of("status", status, "ocr_status", status, "task", DbSupport.normalizeRow(task)); // 返回任务状态
    }
}
