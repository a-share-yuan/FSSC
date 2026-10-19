package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.mapper.WfModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN 仓库化（G3-4，问题全集 A-5）：模型 XML 全量导出入仓 + 发布动作产出 diff 记录。
 *
 * <p>体检背景：{@code wf_model.bpmn_xml} 是流程设计的事实唯一权威，但它只活在数据库里——
 * 没有 git 历史、没有 review、被覆盖即不可回滚，出事只能靠 {@code ACT_RE_MODEL} 里的引擎副本猜。
 * 本服务把 XML 以文件形式落到 {@code fssc.wf.bpmn-repo-dir}（建议指向代码仓库子目录后一起提交），
 * 每次真实部署（发布）额外写出统一 diff 并追加一行发布日志，使流程变更可对比、可追溯。
 *
 * <p>目录布局：
 * <pre>
 *   INDEX.md                        全量模型清单（key / 名称 / 版本 / 摘要 / 部署 ID）
 *   models/&lt;key&gt;/&lt;id&gt;_v&lt;ver&gt;.bpmn20.xml   每次导出的版本快照
 *   models/&lt;key&gt;/latest.bpmn20.xml  该 key 当前生效 XML
 *   publish/&lt;key&gt;/v&lt;旧&gt;_to_v&lt;新&gt;_&lt;时间戳&gt;.diff  发布 diff
 *   publish/&lt;key&gt;/LOG.md            发布日志（含在途旧实例数）
 * </pre>
 *
 * <p>所有写文件失败都不允许影响发布主流程：调用方 {@link WfModelService} 以 try/catch 包裹，
 * 这里自身也只记 warn，导出/发布记录属可追溯性增强而非事务参与者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfBpmnRepoService {

    private final WfModelMapper modelMapper;

    /** 仓库化根目录：默认进程工作目录下的 bpmn-repo，部署时指向代码仓库子目录即可随 git 纳管 */
    @Value("${fssc.wf.bpmn-repo-dir:./bpmn-repo}")
    private String repoDir;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int CONTEXT = 3;
    /** LCS DP 单元格上限：超限时退化为摘要行，避免超大 BPMN 把内存吃掉（约 16MB 整型矩阵） */
    private static final long DP_CELL_LIMIT = 4_000_000L;
    private static final int MAX_DIFF_READ = 200_000;

    // ---------- 全量导出 ----------

    /** 全量导出 wf_model 的 BPMN 到仓库目录（跨租户，设计态资产统一归集） */
    public Map<String, Object> exportAll() {
        List<WfModel> all = new ArrayList<>();
        TenantContext.runIgnoreTenant(() ->
                all.addAll(modelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                        .orderByAsc(WfModel::getModelKey).orderByAsc(WfModel::getId))));
        List<String> indexRows = new ArrayList<>();
        int written = 0;
        int blank = 0;
        List<String> failures = new ArrayList<>();
        for (WfModel m : all) {
            String key = safe(m.getModelKey());
            int ver = m.getVersion() == null ? 1 : m.getVersion();
            String xml = m.getBpmnXml();
            if (xml == null || xml.isBlank()) {
                blank++;
                indexRows.add("| " + key + " | " + nz(m.getModelName()) + " | " + ver
                        + " | （无 BPMN） | " + nz(m.getDeployId()) + " |");
                continue;
            }
            try {
                write(modelDir(key).resolve(m.getId() + "_v" + ver + ".bpmn20.xml"), xml);
                write(modelDir(key).resolve("latest.bpmn20.xml"), xml);
                written++;
                indexRows.add("| " + key + " | " + nz(m.getModelName()) + " | " + ver
                        + " | " + sha12(xml) + " | " + nz(m.getDeployId()) + " |");
            } catch (IOException e) {
                failures.add(key + "#" + m.getId() + ": " + e.getMessage());
                log.warn("BPMN 导出失败 modelKey={} id={}", key, m.getId(), e);
            }
        }
        StringBuilder md = new StringBuilder();
        md.append("# BPMN 模型仓库化导出清单\n\n")
                .append("> 生成时间：").append(LocalDateTime.now().format(TS)).append("\n>\n")
                .append("> 事实来源：`wf_model.bpmn_xml`（设计态唯一权威）。本目录是它的文件化快照，")
                .append("请连同代码一起提交，用于 diff、review 与误覆盖回滚。\n")
                .append("> 手工全量导出：`POST /api/tenant/wf/designer/export-repo`；发布动作会自动补写版本快照与 diff。\n\n")
                .append("| modelKey | 名称 | 版本 | XML 摘要(sha256/12) | 最近部署 ID |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (String r : indexRows) {
            md.append(r).append('\n');
        }
        try {
            write(root().resolve("INDEX.md"), md.toString());
        } catch (IOException e) {
            log.warn("BPMN 索引写入失败", e);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dir", root().toAbsolutePath().normalize().toString().replace('\\', '/'));
        r.put("models", all.size());
        r.put("exported", written);
        r.put("withoutBpmn", blank);
        r.put("failures", failures);
        return r;
    }

    // ---------- 发布 diff ----------

    /**
     * 发布/重新部署成功后落盘：版本快照 + 统一 diff + 发布日志一行。
     *
     * @param oldXml     部署前 XML（首次发布可为 null）
     * @param newVersion 部署后回写的版本号
     * @param inFlight   在途旧实例数（发布不迁移老实例，数量决定要不要人工跟进），可为 null
     */
    public Map<String, Object> recordPublish(WfModel oldModel, String newXml, int newVersion,
                                             String deployId, String procDefId, Integer inFlight) {
        String key = safe(oldModel.getModelKey());
        int oldVersion = oldModel.getVersion() == null ? 1 : oldModel.getVersion();
        String oldXml = oldModel.getBpmnXml();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dir", root().toAbsolutePath().normalize().toString().replace('\\', '/'));
        r.put("modelKey", key);
        r.put("fromVersion", oldVersion);
        r.put("toVersion", newVersion);
        try {
            write(modelDir(key).resolve(oldModel.getId() + "_v" + newVersion + ".bpmn20.xml"), newXml);
            write(modelDir(key).resolve("latest.bpmn20.xml"), newXml);
            String diff = diffHeader(key, oldVersion, newVersion)
                    + (newXml.equals(oldXml) ? "（BPMN 内容未变化，仅重新部署）\n" : unifiedDiff(oldXml, newXml));
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String diffName = "v" + oldVersion + "_to_v" + newVersion + "_" + stamp + ".diff";
            write(publishDir(key).resolve(diffName), diff);
            r.put("diffFile", "publish/" + key + "/" + diffName);
            r.put("changed", !newXml.equals(oldXml));
            String row = "| " + LocalDateTime.now().format(TS) + " | v" + oldVersion + " → v" + newVersion
                    + " | " + nz(deployId) + " | " + nz(procDefId) + " | "
                    + (inFlight == null ? "—" : inFlight) + " | [" + diffName + "](./" + diffName + ") |\n";
            appendPublishLog(key, row, newVersion);
            r.put("logFile", "publish/" + key + "/LOG.md");
        } catch (IOException e) {
            log.warn("BPMN 发布记录写入失败 modelKey={}", key, e);
            r.put("error", e.getMessage());
        }
        return r;
    }

    /** 某模型的发布日志（结构化行 + diff 文件名），供设计器「发布记录」抽屉展示 */
    public List<Map<String, Object>> publishHistory(String modelKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        Path dir = publishDir(safe(modelKey));
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var s = Files.list(dir)) {
            List<Path> files = new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".diff")).toList());
            files.sort((x, y) -> y.getFileName().toString().compareTo(x.getFileName().toString()));
            for (Path p : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("path", "publish/" + safe(modelKey) + "/" + p.getFileName());
                m.put("summary", firstChangeSummary(p));
                try {
                    m.put("time", LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(),
                            java.time.ZoneId.systemDefault()).format(TS));
                } catch (IOException ignore) {
                    // 文件 mtime 不可读时置 null（展示性字段，不影响流程定义加载）
                    m.put("time", null);
                }
                out.add(m);
            }
        } catch (IOException e) {
            log.warn("发布记录目录读取失败 modelKey={}", modelKey, e);
        }
        return out;
    }

    /** 读取单个 diff 文件内容（仅允许 publish 目录下的 .diff，拒绝路径穿越） */
    public String diffContent(String modelKey, String fileName) {
        String key = safe(modelKey);
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")
                || !name.endsWith(".diff")) {
            return null;
        }
        Path p = publishDir(key).resolve(name);
        Path base = publishDir(key).toAbsolutePath().normalize();
        if (!p.toAbsolutePath().normalize().startsWith(base) || !Files.isRegularFile(p)) {
            return null;
        }
        try {
            String txt = Files.readString(p, StandardCharsets.UTF_8);
            return txt.length() > MAX_DIFF_READ ? txt.substring(0, MAX_DIFF_READ) + "\n…（内容过长已截断）" : txt;
        } catch (IOException e) {
            log.warn("diff 读取失败 {}", p, e);
            return null;
        }
    }

    // ---------- 目录与文件 ----------

    private Path root() {
        return Paths.get(repoDir == null || repoDir.isBlank() ? "./bpmn-repo" : repoDir);
    }

    private Path modelDir(String key) {
        return root().resolve("models").resolve(key);
    }

    private Path publishDir(String key) {
        return root().resolve("publish").resolve(key);
    }

    /** 模型 key 落盘前收敛为安全文件名片段，避免任何路径穿越或平台非法字符 */
    static String safe(String key) {
        if (key == null || key.isBlank()) {
            return "_unnamed";
        }
        return key.trim().replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }

    private void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    private void appendPublishLog(String key, String row, int newVersion) throws IOException {
        Path log = publishDir(key).resolve("LOG.md");
        String body;
        if (Files.isRegularFile(log)) {
            body = Files.readString(log, StandardCharsets.UTF_8);
        } else {
            Files.createDirectories(log.getParent());
            body = "# 发布记录 " + key + "\n\n"
                    + "> 每次真实部署到 Flowable 都追加一行：新版本只作用于之后发起的实例，"
                    + "「在途旧实例」列即发布时仍在按旧版本执行的活动流程数。\n\n"
                    + "| 发布时间 | 版本 | 部署 ID | 流程定义 ID | 在途旧实例 | diff |\n"
                    + "| --- | --- | --- | --- | --- | --- |\n";
        }
        if (!body.endsWith("\n")) {
            body = body + "\n";
        }
        Files.write(log, (body + row).getBytes(StandardCharsets.UTF_8));
    }

    private static String diffHeader(String key, int oldVersion, int newVersion) {
        return "--- bpmn:" + key + " v" + oldVersion + "\n+++ bpmn:" + key + " v" + newVersion + "\n";
    }

    private static String nz(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private static String sha12(String xml) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 12);
        } catch (Exception e) {
            return "—";
        }
    }

    /** diff 文件首行摘要：+N / -M 行数，供列表展示 */
    private static String firstChangeSummary(Path diff) {
        try {
            int add = 0;
            int del = 0;
            for (String line : Files.readAllLines(diff, StandardCharsets.UTF_8)) {
                if (line.startsWith("+") && !line.startsWith("+++")) add++;
                else if (line.startsWith("-") && !line.startsWith("---")) del++;
            }
            return "+" + add + " / -" + del;
        } catch (IOException e) {
            return "—";
        }
    }

    // ---------- 统一 diff ----------

    /** 行级 LCS 统一 diff（3 行上下文）。oldXml 为 null 视为新增全文。 */
    static String unifiedDiff(String oldXml, String newXml) {
        String[] a = splitLines(oldXml);
        String[] b = splitLines(newXml);
        if ((long) Math.max(1, a.length) * Math.max(1, b.length) > DP_CELL_LIMIT) {
            return "@@ 差异过大，仅给出行数 @@\n全文 " + a.length + " 行 → " + b.length + " 行\n";
        }
        List<DiffLine> script = editScript(a, b);
        boolean[] keep = new boolean[script.size()];
        for (int i = 0; i < script.size(); i++) {
            if (script.get(i).tag == ' ') {
                continue;
            }
            for (int t = Math.max(0, i - CONTEXT); t <= Math.min(script.size() - 1, i + CONTEXT); t++) {
                keep[t] = true;
            }
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < keep.length) {
            if (!keep[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < keep.length && keep[i]) {
                i++;
            }
            int end = i;
            int oldStart = 0;
            int oldCount = 0;
            int newStart = 0;
            int newCount = 0;
            for (int t = start; t < end; t++) {
                DiffLine d = script.get(t);
                if (d.tag != '+') {
                    if (oldStart == 0) oldStart = d.oldNo;
                    oldCount++;
                }
                if (d.tag != '-') {
                    if (newStart == 0) newStart = d.newNo;
                    newCount++;
                }
            }
            if (oldCount == 0) {
                oldStart = start > 0 ? script.get(start - 1).oldNo : 0;
            }
            if (newCount == 0) {
                newStart = start > 0 ? script.get(start - 1).newNo : 0;
            }
            sb.append("@@ -").append(oldStart).append(',').append(oldCount)
                    .append(" +").append(newStart).append(',').append(newCount).append(" @@\n");
            for (int t = start; t < end; t++) {
                DiffLine d = script.get(t);
                sb.append(d.tag).append(d.text).append('\n');
            }
        }
        if (sb.length() == 0) {
            return "@@ 无行级差异 @@\n";
        }
        return sb.toString();
    }

    private static List<DiffLine> editScript(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int x = n - 1; x >= 0; x--) {
            for (int y = m - 1; y >= 0; y--) {
                dp[x][y] = a[x].equals(b[y]) ? dp[x + 1][y + 1] + 1 : Math.max(dp[x + 1][y], dp[x][y + 1]);
            }
        }
        List<DiffLine> out = new ArrayList<>();
        int x = 0;
        int y = 0;
        while (x < n && y < m) {
            if (a[x].equals(b[y])) {
                out.add(new DiffLine(' ', x + 1, y + 1, a[x]));
                x++;
                y++;
            } else if (dp[x + 1][y] >= dp[x][y + 1]) {
                out.add(new DiffLine('-', x + 1, 0, a[x]));
                x++;
            } else {
                out.add(new DiffLine('+', 0, y + 1, b[y]));
                y++;
            }
        }
        while (x < n) {
            out.add(new DiffLine('-', x + 1, 0, a[x]));
            x++;
        }
        while (y < m) {
            out.add(new DiffLine('+', 0, y + 1, b[y]));
            y++;
        }
        return out;
    }

    private static String[] splitLines(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        return s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    private static final class DiffLine {
        private final char tag;
        private final int oldNo;
        private final int newNo;
        private final String text;

        private DiffLine(char tag, int oldNo, int newNo, String text) {
            this.tag = tag;
            this.oldNo = oldNo;
            this.newNo = newNo;
            this.text = text;
        }
    }
}
