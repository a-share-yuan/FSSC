// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.security.LoginUser;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识检索：字段加权 BM25 ＋ IDF 加权覆盖闸，零外部依赖。
 *
 * <p>回答原则：抽取式优先——先给文档原文片段，再给出处；无证据即拒答，不让模型自由发挥。
 * 检索结果永远被视为数据：剥离"忽略以上指令"类文本，文档内容不能触发任何动作。
 *
 * <p>两道闸缺一不可：
 * <ol>
 *   <li><b>主题闸</b>——文档标题/小节标题必须提到问题的主语（首个核心词组）。
 *       没有这道闸，"借款多久必须还"会命中《交付已知限制与规避》里的"借款逾期"三个字。</li>
 *   <li><b>覆盖闸</b>——分块必须按 IDF 加权覆盖问题核心词组的足够比例。
 *       没有这道闸，BM25 会被"是什么"这类高频二字词带偏，命中毫不相干的段落。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndex {

    public static final String RESOURCE = "ai/knowledge-fssc.json";

    private final ObjectMapper objectMapper;

    @Value("${fssc.ai.knowledge.enabled:true}")
    private boolean enabled = true;

    @Value("${fssc.ai.knowledge.top-k:6}")
    private int defaultTopK = 6;

    @Value("${fssc.ai.knowledge.min-score:1.6}")
    private double minScore = 1.6;

    /** 覆盖闸阈值：问题核心词组的 IDF 加权覆盖率低于此值即视为"没有对应原文"。 */
    @Value("${fssc.ai.knowledge.min-coverage:0.35}")
    private double minCoverage = 0.35;

    /** 标题/小节标题相对正文的权重（短字段，命中即说明主题对得上）。 */
    @Value("${fssc.ai.knowledge.head-weight:3.0}")
    private double headWeight = 3.0;

    /** 导航型小节的降权系数（只说"去哪儿看"的小节不该当答案）。 */
    @Value("${fssc.ai.knowledge.nav-penalty:0.45}")
    private double navPenalty = 0.45;

    private List<Chunk> chunks = List.of();
    private List<Map<String, Integer>> tfText = List.of();
    private List<Map<String, Integer>> tfHead = List.of();
    private Map<String, Integer> dfText = Map.of();
    private Map<String, Integer> dfHead = Map.of();
    private double avgText = 1;
    private double avgHead = 1;
    private String corpusHash;

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern ASCII = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]+");
    private static final Pattern INJECTION = Pattern.compile(
            "忽略(以上|之前|上述).{0,8}(指令|规则)|ignore\\s+(all\\s+)?previous|system\\s*prompt|系统提示词|你现在是.{0,10}模型",
            Pattern.CASE_INSENSITIVE);

    /**
     * 导航型小节名：只交代"去哪儿看"，本身不回答问题（阅读提示/索引/速览/演示路径…）。
     * 标记词全部取自语料里实际存在的小节名（85/1406 段），不是凭空设想的关键词表。
     */
    private static final Pattern NAV_HEADING = Pattern.compile(
            "阅读提示|索引|速览|口径说明|演示操作路径|演示路径|导入演示数据库|排障速查|术语表");

    /** 册首引言块："本篇覆盖 …" 只说本册收录什么，不回答任何具体问题。 */
    private static final Pattern NAV_LEAD = Pattern.compile("^>\\s*本篇覆盖");

    /** 疑问词与虚词：切出问题的"核心词组"时先剥掉它们。 */
    private static final List<String> SEPARATORS = List.of(
            "在哪里", "在哪", "哪里", "是什么", "有什么", "什么", "哪些", "多少", "多久", "几天", "多长时间",
            "怎么样", "什么样", "什么样子", "怎样", "怎么", "如何", "是否", "有没有", "能不能", "可不可以",
            "不能", "不得", "不可以", "禁止", "请问", "帮我", "给我", "我想", "我要", "想知道", "一下", "情况",
            "的", "了", "吗", "呢", "啊", "呀", "和", "与", "或", "是", "在", "有");

    @PostConstruct
    public void load() {
        if (!enabled) {
            log.info("AI 知识索引未启用（fssc.ai.knowledge.enabled=false）");
            return;
        }
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Bundle b = objectMapper.readValue(in, Bundle.class);
            List<Map<String, Integer>> texts = new ArrayList<>(b.getChunks().size());
            List<Map<String, Integer>> heads = new ArrayList<>(b.getChunks().size());
            Map<String, Integer> dt = new HashMap<>();
            Map<String, Integer> dh = new HashMap<>();
            long totalText = 0;
            long totalHead = 0;
            for (Chunk c : b.getChunks()) {
                Map<String, Integer> tt = termFreq(c.getText());
                Map<String, Integer> th = termFreq(c.getTitle() + " " + (c.getHeading() == null ? "" : c.getHeading()));
                texts.add(tt);
                heads.add(th);
                for (String t : tt.keySet()) {
                    dt.merge(t, 1, Integer::sum);
                }
                for (String t : th.keySet()) {
                    dh.merge(t, 1, Integer::sum);
                }
                totalText += Math.max(1, c.getText() == null ? 0 : c.getText().length());
                totalHead += Math.max(1, len(c.getTitle()) + len(c.getHeading()));
            }
            this.chunks = List.copyOf(b.getChunks());
            this.tfText = List.copyOf(texts);
            this.tfHead = List.copyOf(heads);
            this.dfText = Map.copyOf(dt);
            this.dfHead = Map.copyOf(dh);
            this.avgText = b.getChunks().isEmpty() ? 1 : (double) totalText / b.getChunks().size();
            this.avgHead = b.getChunks().isEmpty() ? 1 : (double) totalHead / b.getChunks().size();
            this.corpusHash = b.getCorpusHash();
            log.info("AI 知识索引加载完成：docs={} chunks={} terms={} corpusHash={} generatedAt={}",
                    b.getDocCount(), chunks.size(), dt.size(), corpusHash, b.getGeneratedAt());
        } catch (Exception e) {
            throw new IllegalStateException("AI 知识索引加载失败（" + RESOURCE + "）：" + e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return enabled && !chunks.isEmpty();
    }

    public String corpusHash() {
        return corpusHash;
    }

    public record Hit(String title, String docPath, String heading, String text, double score) {
    }

    public record Answer(String text, List<Hit> hits) {
    }

    public Answer answer(String question, LoginUser user) {
        List<Hit> hits = search(question, user, defaultTopK);
        if (hits.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("根据知识库：\n");
        List<Hit> used = hits.subList(0, Math.min(2, hits.size()));
        for (Hit h : used) {
            sb.append("《").append(h.title()).append("》")
                    .append(h.heading() == null || h.heading().isBlank() ? "" : " · " + h.heading())
                    .append('\n').append(excerpt(h.text(), question)).append("\n\n");
        }
        sb.append("出处：");
        for (int i = 0; i < used.size(); i++) {
            if (i > 0) sb.append("；");
            Hit h = used.get(i);
            sb.append('[').append(i + 1).append("] ").append(h.docPath());
            if (h.heading() != null && !h.heading().isBlank()) {
                sb.append(" · ").append(h.heading());
            }
        }
        return new Answer(sb.toString(), used);
    }

    public List<Hit> search(String question, LoginUser user, int topK) {
        if (!isEnabled() || question == null || question.isBlank()) {
            return List.of();
        }
        List<String> qt = tokens(question);
        if (qt.isEmpty()) {
            return List.of();
        }
        boolean admin = user != null && user.isAdmin();
        List<String> runs = coreRuns(question);
        Set<String> core = new LinkedHashSet<>();
        for (String r : runs) {
            core.addAll(bigrams(r));
        }
        List<String> head = runs.isEmpty() ? List.of() : bigrams(runs.get(0));
        double coreIdf = coreIdfSum(core);
        int n = chunks.size();
        List<Hit> scored = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Chunk c = chunks.get(i);
            if (c.isRestricted() && !admin) {
                continue;
            }
            Map<String, Integer> tt = tfText.get(i);
            Map<String, Integer> th = tfHead.get(i);
            // 主题闸：文档标题/小节标题必须提到问题主语
            if (!head.isEmpty() && head.stream().noneMatch(th::containsKey)) {
                continue;
            }
            double score = (bm25(tt, dfText, avgText, n, qt, c.getText() == null ? 1 : c.getText().length())
                    + headWeight * bm25(th, dfHead, avgHead, n, qt, len(c.getTitle()) + len(c.getHeading())))
                    * navFactor(c);
            if (score < minScore) {
                continue;
            }
            // 覆盖闸：核心词组的 IDF 加权覆盖率
            if (coreIdf > 0) {
                double hit = 0;
                for (String t : core) {
                    if (tt.containsKey(t) || th.containsKey(t)) {
                        hit += idf(dfText, n, t);
                    }
                }
                if (hit / coreIdf < minCoverage) {
                    continue;
                }
            }
            scored.add(new Hit(c.getTitle(), c.getDocPath(), c.getHeading(), c.getText(), score));
        }
        scored.sort((a, b2) -> Double.compare(b2.score(), a.score()));
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    private double coreIdfSum(Set<String> core) {
        double sum = 0;
        int n = chunks.size();
        for (String t : core) {
            sum += idf(dfText, n, t);
        }
        return sum;
    }

    /**
     * 导航型小节的降权系数。
     *
     * <p>小节名只说"去哪儿看"（阅读提示/索引/速览/演示路径…）或册首交代"本篇覆盖什么"，
     * 它们会因为又短又含主题词而在 BM25 里压过真正的内容段：曾出现过"报销流程是什么"命中
     *《费用报销 · 流程说明（域册）》的"4. 阅读提示"，答案里只有一句"环节叙述见上层文档"。
     * 这里给这类分块乘一个降权系数而不是直接剔除——语料里没有别段可依时它仍可兜底。
     */
    private double navFactor(Chunk c) {
        String heading = c.getHeading() == null ? "" : c.getHeading();
        int slash = heading.lastIndexOf('/');
        String seg = (slash >= 0 ? heading.substring(slash + 1) : heading).trim();
        if (NAV_HEADING.matcher(seg).find()) {
            return navPenalty;
        }
        String text = c.getText() == null ? "" : c.getText();
        if (NAV_LEAD.matcher(text).find()) {
            return navPenalty;
        }
        return 1.0;
    }

    private static double bm25(Map<String, Integer> tf, Map<String, Integer> df, double avg, int n,
                                List<String> queryTokens, int length) {
        double k1 = 1.2;
        double b = 0.75;
        double score = 0;
        for (String t : queryTokens) {
            Integer f = tf.get(t);
            if (f == null) {
                continue;
            }
            int d = df.getOrDefault(t, 0);
            double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
            double denom = f + k1 * (1 - b + b * length / avg);
            score += idf * f * (k1 + 1) / denom;
        }
        return score;
    }

    private static double idf(Map<String, Integer> df, int n, String t) {
        int d = df.getOrDefault(t, 0);
        return Math.log(1 + (n - d + 0.5) / (d + 0.5));
    }

    private String excerpt(String text, String question) {
        String clean = sanitize(text);
        String q = question == null ? "" : question;
        int best = -1;
        for (String t : tokens(q)) {
            if (t.length() < 2) continue;
            int idx = clean.indexOf(t);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        int start = best < 0 ? 0 : Math.max(0, clean.lastIndexOf('\n', best));
        String out = clean.substring(Math.min(start, clean.length())).trim();
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out.length() <= 520 ? out : out.substring(0, 520) + "…";
    }

    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            if (INJECTION.matcher(line).find()) {
                continue;
            }
            String l = line.replaceAll("^\\s*[-*+>]\\s*", "")
                    .replace("**", "").replace("`", "")
                    .replaceAll("^\\|?\\s*|---.*$", "")
                    .trim();
            if (l.isEmpty()) {
                sb.append('\n');
            } else {
                sb.append(l).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** CJK 连续片段取 bigram；英文取小写词。查询与索引同法，避免两侧口径漂移。 */
    static List<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        Matcher cm = CJK.matcher(text == null ? "" : text);
        while (cm.find()) {
            String run = cm.group();
            if (run.length() == 1) {
                out.add(run);
            }
            for (int i = 0; i + 1 < run.length(); i++) {
                out.add(run.substring(i, i + 2));
            }
        }
        Matcher am = ASCII.matcher(text == null ? "" : text.toLowerCase());
        while (am.find()) {
            String w = am.group();
            if (w.length() >= 2) {
                out.add(w);
            }
        }
        return new ArrayList<>(out);
    }

    private static Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens(text)) {
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }

    /** 剥掉疑问词与虚词，剩下的连续汉字段就是问题的"核心词组"。 */
    static List<String> coreRuns(String question) {
        List<String> runs = new ArrayList<>();
        Matcher cm = CJK.matcher(question == null ? "" : question);
        while (cm.find()) {
            String s = cm.group();
            for (String sep : SEPARATORS) {
                s = s.replace(sep, " ");
            }
            for (String seg : s.trim().split("\\s+")) {
                if (seg.length() >= 2) {
                    runs.add(seg);
                }
            }
        }
        return runs;
    }

    private static List<String> bigrams(String run) {
        if (run.length() == 2) {
            return List.of(run);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 1 < run.length(); i++) {
            out.add(run.substring(i, i + 2));
        }
        return out;
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bundle {
        private int version;
        private String generatedAt;
        private String corpusHash;
        private int docCount;
        private int chunkCount;
        private int restrictedCount;
        private List<Chunk> chunks = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chunk {
        private String id;
        private String docPath;
        private String title;
        private String heading;
        private String text;
        private boolean restricted;
        private int length;
    }
}
