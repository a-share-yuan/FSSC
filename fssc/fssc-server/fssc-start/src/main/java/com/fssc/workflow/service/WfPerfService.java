package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程绩效与积压分析（改造方案 <b>把拼造数据换成真规则</b>）。
 *
 * <p>数据源全部是业务台账，不读引擎表，也不再按一套写死的 7 节点词汇画假图：
 * <ul>
 *  <li><b>流转路径（桑基）</b>：{@code wf_task_history} 同一实例按 oper_time 排序后的相邻节点转移，
 *      即"这批单据真实走过的路"，节点名取真实 nodeName；</li>
 *  <li><b>节点停留（热力）</b>：同实例相邻两次审批事件的时间差＝该节点实际停留，
 *      分桶后按真实节点名铺开；首节点停留以 {@code wf_instance.start_time} 为起点；</li>
 *  <li><b>SLA 达成率</b>：节点停留 × {@code wf_node_config.timeout_hours}（modelKey:nodeKey 精确匹配，
 *      未配 SLA 的节点不参与分母，避免"没要求＝都达标"把达成率刷高）；</li>
 *  <li><b>积压账龄</b>：{@code wf_task} PENDING 的 create_time 账龄 + 超时天数；</li>
 *  <li><b>按人时效 / 超时未办 Top 榜</b>：{@code wf_task} 按 assignee_id（未指派时按候选人）聚合。</li>
 * </ul>
 *
 * <p><b>规则边界</b>：审批历史（路径/停留）自单据进入第二环节起才有完整事件链；待办镜像
 * {@code wf_task} 的历史在 双账补齐脚本执行前只覆盖首节点，因此"办结数/按人时效"类
 * 指标在补齐前会偏低，路径与停留不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfPerfService {

    private final WfTaskMapper taskMapper;
    private final WfTaskHistoryMapper historyMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfNodeConfigMapper nodeConfigMapper;
    private final SysUserMapper sysUserMapper;
    /**
     * 卡单判定：阈值规则与超时聚合告警同源（{@code wf.timeout.stuck.alert.after_urges}），
     * 看板只把 {@code urged_count} 变成红牌，<b>不新增列、不写库</b>。
     */
    private final WfQueryService wfQueryService;

    /** 节点停留分桶上界（分钟）与标签，一一对应，前端热力图 x 轴直接用 */
    private static final long[] BUCKET_MAX_MINUTES = {60, 240, 480, 1440};
    private static final String[] BUCKET_LABELS = {"<1小时", "1-4小时", "4-8小时", "8-24小时", ">24小时"};

    /** 待办账龄分桶（小时上界） */
    private static final long[] AGING_MAX_HOURS = {24, 72, 168};
    private static final String[] AGING_LABELS = {"<1天", "1-3天", "3-7天", ">7天"};

    /** 热力图/节点表最多铺多少个节点（按命中数降序），防止 40+ 节点把图压成一条线 */
    private static final int TOP_NODES = 12;
    /** 榜单条数 */
    private static final int TOP_HANDLERS = 15;
    /**
     * 积压档的机器动作回看天数。积压看板本身是"当前快照"，没有天数入参，
     * 台账里的"实际发生"事件需要一个窗口才能数——取与其余三档同默认值 30 天，保持四档规则一致。
     */
    private static final int BACKLOG_LEDGER_DAYS = 30;

    private static final String[] INSTANCE_STATUSES = {"RUNNING", "APPROVED", "REJECTED", "CANCELLED", "TERMINATED"};

    /*
     * 看板快照缓存。
     *
     * 四个自观测端点各自把同一份数据现算一遍：/overview 与 /backlog 各读一次在办
     * PENDING 快照 + 审批历史窗，/handler-top 再读一次 PENDING + 按人聚合，
     * /node-dwell 又读一次历史窗。并发时四端点轮着打库、互相抢 I/O，耗时随并发放大；
     * 单发则快得多，说明成本在"重复装载"而不是单个规则慢。
     *
     * 这里只收敛<b>装载</b>：TTL 内复用同一份 PENDING / 历史窗 / 按人聚合 / 实例状态计数。
     * 判定口径一律不动——每个数仍由下面各方法唯一给定，缓存不参与任何阈值、终态或归属判断；
     * 看板是派生统计，几秒新鲜度不改变业务含义（账务动作不读这里）。
     */
    private static final long SNAPSHOT_TTL_MS = 10_000;

    /** 快照：{@code at}＝装载时刻（毫秒），{@code value}＝数据 */
    private record Snap<T>(long at, T value) { }

    private final Object snapshotLock = new Object();
    private volatile Snap<List<WfTask>> pendingCache;
    private volatile Snap<Map<Integer, List<WfTaskHistory>>> historyCache;
    private volatile Snap<Map<Integer, List<Map<String, Object>>>> doneStatsCache;
    private volatile Snap<Map<String, Long>> statusCache;

    private static boolean fresh(Snap<?> s) {
        return s != null && System.currentTimeMillis() - s.at() < SNAPSHOT_TTL_MS;
    }

    // 1. 总览（大屏顶部卡片）

    public Map<String, Object> overview(int days) {
        int window = normalizeDays(days);
        List<WfTaskHistory> hist = historyWindow(window);
        Map<String, WfInstance> instances = instancesOf(hist);
        List<NodeStat> stats = nodeStats(hist, instances);

        long events = 0;
        long onTime = 0;
        long slaMeasured = 0;
        long slaOnTime = 0;
        int[] buckets = new int[BUCKET_LABELS.length];
        for (NodeStat ns : stats) {
            for (int i = 0; i < ns.buckets.length; i++) {
                buckets[i] += ns.buckets[i];
            }
            events += ns.events;
            onTime += ns.minutesSum;
            slaMeasured += ns.slaMeasured;
            slaOnTime += ns.slaOnTime;
        }

        List<WfTask> pending = pendingTasks();
        LocalDateTime now = LocalDateTime.now();
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        long overduePending = 0;
        long noDueTime = 0;
        long over7d = 0;
        long pendingAgeSumHours = 0;
        for (WfTask t : pending) {
            long age = hoursBetween(t.getCreateTime(), now);
            pendingAgeSumHours += age;
            if (age > 168) {
                over7d++;
            }
            // 超时未办走 overdueOf() 唯一谓词（due_time 实算），与时效榜两列同一份规则
            // 顶部 KPI 与「当前在办/在办超时」列自此由同一谓词、同一份 PENDING 快照出数
            Overdue od = overdueOf(t, cfgIndex, now);
            if (od.yes()) {
                overduePending++;
            }
            if (DUE_BASIS_FALLBACK.equals(od.basis) || DUE_BASIS_UNMEASURABLE.equals(od.basis)) {
                noDueTime++;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        Map<String, Long> instanceStatus = instanceStatusCount();
        out.put("instanceStatus", instanceStatus);
        out.put("runningInstances", instanceStatus.getOrDefault("RUNNING", 0L));
        out.put("approvedInstances", instanceStatus.getOrDefault("APPROVED", 0L));
        out.put("cancelledInstances", instanceStatus.getOrDefault("CANCELLED", 0L));
        out.put("rejectedInstances", instanceStatus.getOrDefault("REJECTED", 0L));
        out.put("pendingTasks", pending.size());
        out.put("approvalEvents", events);
        out.put("finishedToday", countFinishedToday());
        out.put("overdueInFlight", overduePending);
        // 兜底规则规模如实给出（这些行没有 wf_task.due_time，超时按 create_time＋节点 SLA 回算）
        out.put("noDueTimeTasks", noDueTime);
        out.put("backlogOver7d", over7d);
        out.put("avgPendingHours", pending.isEmpty() ? 0 : Math.round(pendingAgeSumHours / (double) pending.size()));
        // SLA 达成率：只统计"配了 timeout_hours 且有真实停留"的审批事件
        out.put("slaRate", slaMeasured == 0 ? null : round2(slaOnTime * 100.0 / slaMeasured));
        out.put("slaMeasured", slaMeasured);
        out.put("avgNodeMinutes", events == 0 ? 0 : (int) (onTime / events));
        List<Map<String, Object>> bucketList = new ArrayList<>();
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", BUCKET_LABELS[i]);
            m.put("count", buckets[i]);
            bucketList.add(m);
        }
        out.put("buckets", bucketList);
        // 概览卡补超时处置档计数（阶梯档／AUTO_PASS 的节点数、窗口内实际发生数、在办落档数）。
        // 手册要求"改档后可见"：三档表头只有超时次数/达成率时，改档结果在屏幕上无痕。
        out.put("timeoutActions", timeoutActionLedger(pending, hist, instances));
        out.put("note", "统计说明：审批事件取自审批记录，停留＝同一单据相邻两次审批的时间差；"
                + "达成率仅统计配了办理时限且停留可测的审批（" + slaMeasured + "/" + events + "）。"
                + "超时未办＝仍在办理且已过了到期时点（按到期时间实算，不用「停留−配置时限」推算）；"
                + "其中 " + noDueTime + " 条没有到期时间，按「发起时间＋节点办理时限」推算并已单列标注。");
        return out;
    }

    private Map<String, Long> instanceStatusCount() {
        Snap<Map<String, Long>> c = statusCache;
        if (fresh(c)) {
            return c.value();
        }
        synchronized (snapshotLock) {
            c = statusCache;
            if (fresh(c)) {
                return c.value();
            }
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String st : INSTANCE_STATUSES) {
                counts.put(st, instanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getStatus, st)));
            }
            statusCache = new Snap<>(System.currentTimeMillis(), counts);
            return counts;
        }
    }

    /**
     * {@code /wf/board} 顶部四卡的唯一规则源——按状态<b>全量</b> COUNT。
     *
     * <p>拿「当前页」的行自己数时，翻页/筛选后读数随页大小漂移，在途实例可能一直显 0，
     * 与 {@code /flow-center} 同一规则也会对不上。completed 取 APPROVED、rejected 取 REJECTED＋TERMINATED，
     * 与该页原有分桶规则一致，这枚只修<b>查询范围</b>，不改业务规则。CANCELLED 单列返回，前端如需并入再判。</p>
     */
    public Map<String, Object> instanceStatusSummary() {
        Map<String, Long> counts = instanceStatusCount();
        long total = 0;
        for (Long v : counts.values()) {
            total += v == null ? 0 : v;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("running", counts.getOrDefault("RUNNING", 0L));
        out.put("completed", counts.getOrDefault("APPROVED", 0L));
        out.put("rejected", counts.getOrDefault("REJECTED", 0L) + counts.getOrDefault("TERMINATED", 0L));
        out.put("cancelled", counts.getOrDefault("CANCELLED", 0L));
        out.put("byStatus", counts);
        return out;
    }

    // 2. 流转路径（桑基真规则：实例真实走过的路径）

    public Map<String, Object> paths(int days) {
        int window = normalizeDays(days);
        List<WfTaskHistory> hist = historyWindow(window);
        Map<String, List<String>> sequences = nodeSequences(hist);

        // 节点分层：按"节点在各实例中首次出现的步序"平均值排序，只保留层号递增的边——
        // 驳回/回退产生的回边按此规则自然丢弃（ECharts 桑基遇环直接报错）。
        Map<String, List<Integer>> firstSteps = new HashMap<>();
        Map<String, Integer> hits = new HashMap<>();
        Map<String, Long> links = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : sequences.entrySet()) {
            List<String> seq = e.getValue();
            Set<String> seen = new HashSet<>();
            Map<String, Integer> firstSeen = new HashMap<>();
            for (int i = 0; i < seq.size(); i++) {
                firstSeen.putIfAbsent(seq.get(i), i);
                if (seen.add(seq.get(i))) {
                    hits.merge(seq.get(i), 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> fs : firstSeen.entrySet()) {
                firstSteps.computeIfAbsent(fs.getKey(), k -> new ArrayList<>()).add(fs.getValue());
            }
            for (int i = 0; i + 1 < seq.size(); i++) {
                if (seq.get(i).equals(seq.get(i + 1))) {
                    continue;
                }
                links.merge(seq.get(i) + "\u0001" + seq.get(i + 1), 1L, Long::sum);
            }
        }
        Map<String, Integer> layer = new HashMap<>();
        List<String> ordered = new ArrayList<>(firstSteps.keySet());
        Comparator<String> byStep = Comparator
                .comparingDouble((String n) -> avg(firstSteps.get(n)))
                .thenComparing(n -> -hits.getOrDefault(n, 0));
        ordered.sort(byStep);
        for (int i = 0; i < ordered.size(); i++) {
            layer.put(ordered.get(i), i);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String name : ordered) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("hits", hits.getOrDefault(name, 0));
            nodes.add(m);
        }
        List<Map<String, Object>> outLinks = new ArrayList<>();
        for (Map.Entry<String, Long> en : links.entrySet()) {
            String[] pair = en.getKey().split("\u0001");
            Integer from = layer.get(pair[0]);
            Integer to = layer.get(pair[1]);
            if (from == null || to == null || to <= from || en.getValue() == 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", pair[0]);
            m.put("target", pair[1]);
            m.put("value", en.getValue());
            outLinks.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        out.put("instances", sequences.size());
        out.put("nodes", nodes);
        out.put("links", outLinks);
        // 全景档同样要能看见改档结果——同名节点可能挂在多个流程模型上，档位并列显示。
        List<WfTask> pending = pendingTasks();
        Map<String, WfInstance> instances = instancesOf(hist);
        Map<String, NodeAction> actions = nodeActions(hist, instances);
        for (Map<String, Object> m : nodes) {
            NodeAction na = actions.get((String) m.get("name"));
            m.put("timeoutAction", na == null ? TIER_UNSPECIFIED : na.tierLabel());
            m.put("urgeEvents", na == null ? 0 : na.ev(EV_URGE));
            m.put("autoPassEvents", na == null ? 0 : na.ev(EV_AUTO_PASS));
            m.put("escalateEvents", na == null ? 0 : na.ev(EV_ESCALATE));
        }
        out.put("timeoutActions", timeoutActionLedger(pending, hist, instances));
        out.put("note", "节点＝审批记录里的真实节点名，连线值＝窗口内该流转实际发生的次数，退回的流转已剔除。"
                + "节点行的「处置档／催办·自动通过·升级行数」＝该节点在窗口内命中的处置档与系统自动动作次数。"
                + "没有节点名的记录不画进本图（宁缺不造），规模见「节点 SLA 明细」页签的说明。");
        return out;
    }

    // 3. 节点停留热力（真规则：task_history 节点命中 + 相邻事件时间差）

    public Map<String, Object> nodeDwell(int days) {
        int window = normalizeDays(days);
        List<WfTaskHistory> hist = historyWindow(window);
        Map<String, WfInstance> instances = instancesOf(hist);
        List<NodeStat> stats = nodeStats(hist, instances);
        List<Map<String, Object>> rows = new ArrayList<>();
        // 节点 SLA 明细档补两列——该节点配置的处置档 + 窗口内机器动作行数
        // 催办/自动通过/升级，改档能不能当场看见就看这两列。
        Map<String, NodeAction> actions = nodeActions(hist, instances);
        for (NodeStat ns : stats.size() > TOP_NODES ? stats.subList(0, TOP_NODES) : stats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", ns.node);
            // 无名记录行如实标注（前端加灰色 tag），不再与真实节点混成一堆
            m.put("unnamedTrace", isUnnamedNodeLabel(ns.node));
            m.put("events", ns.events);
            m.put("avgMinutes", ns.events == 0 ? 0 : (int) (ns.minutesSum / ns.events));
            m.put("maxMinutes", ns.maxMinutes);
            m.put("timeoutHours", ns.timeoutHours);
            m.put("slaRate", ns.slaMeasured == 0 ? null : round2(ns.slaOnTime * 100.0 / ns.slaMeasured));
            m.put("overdue", ns.overdue);
            NodeAction na = actions.get(ns.node);
            m.put("timeoutAction", na == null ? TIER_UNSPECIFIED : na.tierLabel());
            m.put("urgeEvents", na == null ? 0 : na.ev(EV_URGE));
            m.put("autoPassEvents", na == null ? 0 : na.ev(EV_AUTO_PASS));
            m.put("escalateEvents", na == null ? 0 : na.ev(EV_ESCALATE));
            m.put("buckets", ns.buckets);
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        out.put("buckets", List.of(BUCKET_LABELS));
        out.put("rows", rows);
        out.put("droppedNodes", Math.max(0, stats.size() - TOP_NODES));
        // 无名记录的规模（行数＋事件数）如实给出——它不是一个节点，是历史行没写 node_name/node_key
        int unnamedRows = 0;
        long unnamedEvents = 0;
        for (NodeStat ns : stats) {
            if (isUnnamedNodeLabel(ns.node)) {
                unnamedRows++;
                unnamedEvents += ns.events;
            }
        }
        out.put("unnamedNodeRows", unnamedRows);
        out.put("unnamedNodeEvents", unnamedEvents);
        out.put("timeoutActions", timeoutActionLedger(pendingTasks(), hist, instances));
        out.put("note", "行＝窗口内命中最多的 " + TOP_NODES + " 个真实节点，格值＝落在该停留区间的审批次数；"
                + "「处置档／催办·自动通过·升级行数」读自节点超时处置配置与审批记录，只数不改。"
                + (unnamedEvents > 0
                ? "窗口内 " + unnamedEvents + " 条审批记录没有节点名（" + unnamedRows + " 行）：能认出节点的按节点显示、"
                  + "两者都认不出的标「记录缺少名称·无节点信息」，不替它们造名。"
                : ""));
        return out;
    }

    /** 按节点聚合：命中事件数、停留（均值/最大）、分桶、配了 SLA 的达成率与超时次数。 */
    private List<NodeStat> nodeStats(List<WfTaskHistory> hist, Map<String, WfInstance> instances) {
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        Map<String, NodeStat> byNode = new LinkedHashMap<>();
        String prevProc = null;
        LocalDateTime prevTime = null;
        for (WfTaskHistory h : hist) {
            String proc = h.getProcInstId();
            if (proc == null || !proc.equals(prevProc)) {
                prevProc = proc;
                WfInstance inst = proc == null ? null : instances.get(proc);
                prevTime = inst == null ? null : (inst.getStartTime() != null ? inst.getStartTime() : inst.getCreateTime());
            }
            // 无名记录按真实来源出label（有 key 显 key，全无则标「记录缺名」），不再合成伪节点
            String node = nodeLabel(h.getNodeName(), h.getNodeKey());
            NodeStat ns = byNode.computeIfAbsent(node, k -> new NodeStat(k));
            ns.events++;
            Integer sla = timeoutHoursOf(cfgIndex, modelKeyOf(instances, proc), h.getNodeKey());
            ns.timeoutHours = sla;
            if (h.getOperTime() != null && prevTime != null) {
                long minutes = Math.max(0, Duration.between(prevTime, h.getOperTime()).toMinutes());
                ns.minutesSum += minutes;
                ns.maxMinutes = Math.max(ns.maxMinutes, minutes);
                ns.buckets[bucketOf(minutes)]++;
                if (sla != null && sla > 0) {
                    ns.slaMeasured++;
                    if (minutes <= sla * 60L) {
                        ns.slaOnTime++;
                    } else {
                        ns.overdue++;
                    }
                }
            }
            if (h.getOperTime() != null) {
                prevTime = h.getOperTime();
            }
        }
        List<NodeStat> stats = new ArrayList<>(byNode.values());
        stats.sort(Comparator.comparingInt((NodeStat s) -> s.events).reversed().thenComparing(s -> s.node));
        return stats;
    }

    // 4. 积压账龄（在途单堆在哪、堆了多久）

    public Map<String, Object> backlog() {
        return backlog(BACKLOG_LEDGER_DAYS);
    }

    /**
     * 积压账龄（加档）：除账龄/超时外，逐节点给出该节点在办量落在哪个处置档、
     * 累计催办次数、已达卡单阈值的条数——阶梯档（ESCALATE_AFTER_URGES）改档后有没有真的
     * "催满 N 次"，就在 {@code onLadderTier}/{@code urgedTotal}/{@code stuck} 三列上。
     *
     * @param days 机器动作（催办/自动通过/升级）事件的回看天数，仅影响 {@code timeoutActions} 台账
     */
    public Map<String, Object> backlog(int days) {
        List<WfTask> pending = pendingTasks();
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        Map<Long, String> names = namesOf(pending);
        LocalDateTime now = LocalDateTime.now();

        Map<Long, Integer> agingBuckets = new LinkedHashMap<>();
        Map<String, BacklogNode> byNode = new LinkedHashMap<>();
        int noDueTasks = 0;
        for (WfTask t : pending) {
            long age = hoursBetween(t.getCreateTime(), now);
            agingBuckets.merge((long) agingBucketOf(age), 1, Integer::sum);
            // 已超时＝now − wf_task.due_time（到期真值）；「账龄 − 配置 SLA」在
            // wf_task.create_time 早于实例起流时会把起流前的时间算进去，长出几十小时的假超时。
            Overdue od = overdueOf(t, cfgIndex, now);
            boolean overdue = od.yes();
            if (t.getDueTime() == null) {
                noDueTasks++;
            }
            // 记录缺名不造名——有 nodeKey 显 key，两者皆空如实标「记录缺名」
            String node = nodeLabel(t.getNodeName(), t.getNodeKey());
            BacklogNode bn = byNode.computeIfAbsent(node, BacklogNode::new);
            bn.count++;
            if (overdue) {
                bn.overdue++;
            }
            bn.maxAgeHours = (int) Math.max(bn.maxAgeHours, age);
            bn.noDue += t.getDueTime() == null ? 1 : 0;
            bn.fallbackOverdue += DUE_BASIS_FALLBACK.equals(od.basis) && od.yes() ? 1 : 0;
            // 处置档：一个节点名可能挂多个模型（同名不同 modelKey），档位并列、按任务各计一次
            String tier = timeoutTierOf(cfgIndex, t.getModelKey(), t.getNodeKey());
            if (tier != null) {
                bn.tiers.add(tier);
                if (TIER_ESCALATE_LADDER.equals(tier)) {
                    bn.ladder++;
                } else if (TIER_AUTO_PASS.equals(tier)) {
                    bn.autoPass++;
                }
            }
            int urged = t.getUrgedCount() == null ? 0 : t.getUrgedCount();
            bn.urged += urged;
            if (wfQueryService.isStuck(urged)) {
                bn.stuck++;
            }
        }
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int i = 0; i < AGING_LABELS.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", AGING_LABELS[i]);
            m.put("count", agingBuckets.getOrDefault((long) i, 0));
            buckets.add(m);
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (BacklogNode bn : byNode.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", bn.name);
            m.put("unnamedTrace", isUnnamedNodeLabel(bn.name));   // 本行节点名是否来自「记录缺名」兜底
            m.put("count", bn.count);
            m.put("overdue", bn.overdue);
            // 这一行的超时里有多少条是"没有 due_time、按 create_time＋SLA 兜底"算出来的
            m.put("overdueFallback", bn.fallbackOverdue);
            m.put("noDueTime", bn.noDue);
            m.put("maxAgeHours", bn.maxAgeHours);
            m.put("timeoutAction", bn.tiers.isEmpty() ? TIER_UNSPECIFIED : String.join("/", bn.tiers));
            m.put("onLadderTier", bn.ladder);
            m.put("onAutoPassTier", bn.autoPass);
            m.put("urgedTotal", bn.urged);
            m.put("stuck", bn.stuck);
            nodes.add(m);
        }
        nodes.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("overdue")).reversed()
                .thenComparing(m -> -(Integer) m.get("count")));

        int window = normalizeDays(days);
        List<WfTaskHistory> hist = historyWindow(window);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        out.put("pending", pending.size());
        out.put("buckets", buckets);
        out.put("byNode", nodes);
        out.put("overdueTop", overdueRank(pending, names, true));
        // 规则可追溯——榜上每行都带 overdueBasis；这两项给出「兜底规则」的规模
        out.put("noDueTimeTasks", noDueTasks);
        out.put("timeoutActions", timeoutActionLedger(pending, hist, instancesOf(hist)));
        out.put("note", "账龄＝任务发起到现在的时长；已超时＝现在 − 到期时间（按到期时点实算），"
                + "没有到期时间的 " + noDueTasks + " 条按「发起时间＋节点办理时限」推算，榜上该行标 †；"
                + "两者都没有（未配办理时限且无到期时间）的不计超时。"
                + "榜以任务为行，未指派的多候选人合并显示、不重复计数。"
                + "「处置档／催满升级·自动通过档在办数／累计催办／卡单数」读自节点超时处置配置与催办次数，"
                + "不改状态、不触发升级或自动通过。");
        return out;
    }

    // 5. 按人时效 + 超时未办 Top 榜

    /**
     * 办理时效榜：按办理人聚合窗口内办结量与平均停留，并给出在办量与超时未办数。
     * 规则提示：未指派（POOL/候选组）的待办按候选人计入，一人多候选会重复计入在办量——
     * 这是"谁还能办"的运营规则，不是"谁该背"的考核规则，故不参与排序，只作参考列。
     */
    public Map<String, Object> handlerTop(int days) {
        int window = normalizeDays(days);
        // 把窗口内 DONE 任务整表读进堆逐行累加，窗口一大就会把看板拖到超时。
        // 改为按人下推聚合（超时谓词与 overdueOf() 同规则，
        // 见 WfTaskMapper.handlerDoneStats 的注释），本方法只保留在办侧的逐行规则。
        List<Map<String, Object>> doneStats = handlerDoneStats(window);
        List<WfTask> pending = pendingTasks();
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        LocalDateTime now = LocalDateTime.now();

        Map<Long, int[]> agg = new LinkedHashMap<>();   // userId -> [办结, 平均分钟分子, 最长分钟, 超时办结]
        for (Map<String, Object> row : doneStats) {
            if (row == null || row.get("userId") == null) {
                continue;
            }
            Long uid = ((Number) row.get("userId")).longValue();
            long handled = numOf(row.get("handled"));
            long minutes = Math.min(numOf(row.get("minutesSum")), Integer.MAX_VALUE / 4);
            int[] a = agg.computeIfAbsent(uid, k -> new int[4]);
            a[0] = (int) handled;
            a[1] = (int) minutes;
            a[2] = (int) Math.min(numOf(row.get("maxMinutes")), Integer.MAX_VALUE);
            a[3] = (int) Math.min(numOf(row.get("overdueHandled")), Integer.MAX_VALUE);
        }
        Set<Long> nameIds = new LinkedHashSet<>(agg.keySet());
        for (WfTask t : pending) {
            if (t.getAssigneeId() != null) {
                nameIds.add(t.getAssigneeId());
            }
            nameIds.addAll(WfQueryService.parseCandidateIds(t.getCandidateIds()));
        }
        Map<Long, String> names = namesOfIds(nameIds);
        Map<Long, int[]> onHand = new LinkedHashMap<>();  // userId -> [在办, 超时未办, 阶梯档在办, AUTO_PASS档在办]
        // 「当前在办」「在办超时」两列的直接真相源＝同一份 wf_task PENDING 快照 ＋
        // overdueOf() 唯一超时谓词（与顶部 KPI「待办任务/超时未办」逐字同一判定），不再借道大屏 Top 榜
        // 的投影行（那是一次"榜单规则"的间接取样，改榜单就会改坏这两列）。
        // 未指派（POOL/候选组）按候选人各计一次"谁还能办"；一个任务对同一个人只计一次。
        int overdueTasks = 0;
        int noDueTimeTasks = 0;
        int unownedTasks = 0;
        int ownedTasks = 0;
        for (WfTask t : pending) {
            Overdue od = overdueOf(t, cfgIndex, now);
            if (od.yes()) {
                overdueTasks++;
            }
            if (t.getDueTime() == null) {
                noDueTimeTasks++;
            }
            List<Long> owners = ownersOf(t);
            if (owners.isEmpty()) {
                unownedTasks++;
                continue;
            }
            ownedTasks++;
            String tier = timeoutTierOf(cfgIndex, t.getModelKey(), t.getNodeKey());
            for (Long id : owners) {
                int[] o = onHand.computeIfAbsent(id, k -> new int[4]);
                o[0]++;
                if (od.yes()) {
                    o[1]++;
                }
                // 改档后"这个人的在办里有多少条落在阶梯档/AUTO_PASS 档"
                if (TIER_ESCALATE_LADDER.equals(tier)) {
                    o[2]++;
                } else if (TIER_AUTO_PASS.equals(tier)) {
                    o[3]++;
                }
            }
        }
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.addAll(agg.keySet());
        userIds.addAll(onHand.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long uid : userIds) {
            int[] a = agg.getOrDefault(uid, new int[4]);
            int[] o = onHand.getOrDefault(uid, new int[4]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("name", names.getOrDefault(uid, String.valueOf(uid)));
            m.put("handled", a[0]);
            m.put("avgMinutes", a[0] == 0 ? null : a[1] / a[0]);
            m.put("maxMinutes", a[2]);
            m.put("overdueHandled", a[3]);
            m.put("onHand", o[0]);
            m.put("overdueOnHand", o[1]);
            // 在办量按处置档拆开（阶梯档／AUTO_PASS 档），改档后按人可见
            m.put("onLadderTier", o[2]);
            m.put("onAutoPassTier", o[3]);
            rows.add(m);
        }
        rows.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("handled")).reversed()
                .thenComparing(m -> -(Integer) m.get("overdueOnHand")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", window);
        List<Map<String, Object>> shown = rows.size() > TOP_HANDLERS
                ? new ArrayList<>(rows.subList(0, TOP_HANDLERS)) : new ArrayList<>(rows);
        // Top 15 按「窗口内办结」排序，名下只有在办、窗口内没办结的人整批落在榜外，
        // 于是"两列在屏上还是全 0"（12-31 看到的就是这个形状）。这里把有在办却未进榜者补进末段并打标，
        // 让两列非恒 0 由构造保证；排序与办结榜规则不动，补行只在表尾。
        int appended = 0;
        for (int i = TOP_HANDLERS; i < rows.size(); i++) {
            Map<String, Object> m = rows.get(i);
            if (((Integer) m.get("onHand")) > 0) {
                m.put("onHandOnly", true);
                shown.add(m);
                appended++;
            }
        }
        out.put("rows", shown);
        out.put("measured", rows.size());
        out.put("topRows", shown.size());
        out.put("appendedOnHandRows", appended);
        // 对账口：「当前在办」「在办超时」两列曾因取键取错而恒为 0（见上方注释）。
        // 光"有数"不够，还要能对上同页 KPI——KPI 按任务数、本表按人（候选人重复计入），
        // 两个规则不并排给出，读者只会再报一次"两列对不上"。这里把三段差值逐项出数：
        //   屏上 15 行列合计 ≤ 全部人名下合计 ＝ 有主任务数（多候选按人重复） ＝ 待办 − 无人认领
        int overdueTasksAllUsers = 0;
        int onHandAllUsers = 0;
        for (int[] o : onHand.values()) {
            onHandAllUsers += o[0];
            overdueTasksAllUsers += o[1];
        }
        int cellOnHand = 0;
        int cellOverdue = 0;
        for (Map<String, Object> m : shown) {
            cellOnHand += (Integer) m.get("onHand");
            cellOverdue += (Integer) m.get("overdueOnHand");
        }
        Map<String, Object> reconcile = new LinkedHashMap<>();
        reconcile.put("pendingTasks", pending.size());          // ＝概览卡 pendingTasks（同一份 PENDING 快照）
        reconcile.put("overdueTasks", overdueTasks);            // ＝概览卡 overdueInFlight（同一 overdueOf 谓词）
        reconcile.put("noDueTimeTasks", noDueTimeTasks);              // 没有 due_time、走兜底/不可测规则的任务数
        reconcile.put("onHandCellSum", cellOnHand);             // 屏上各行「当前在办」列合计（只含 Top 15 人）
        reconcile.put("overdueOnHandCellSum", cellOverdue);     // 屏上各行「在办超时」列合计
        reconcile.put("onHandAllHandlersSum", onHandAllUsers);  // 全部人名下合计（未截断，候选人重复计入）
        reconcile.put("overdueOnHandAllHandlersSum", overdueTasksAllUsers);
        reconcile.put("ownedTasks", ownedTasks);                // 至少有一个可办理人的任务数
        reconcile.put("unownedTasks", unownedTasks);            // 候选组为空、无人认领的任务数（任何人名下都不计）
        reconcile.put("appendedOnHandRows", appended);          // 名下有在办、窗口内无办结而补进表尾的行数
        out.put("reconcile", reconcile);
        out.put("note", "办结时效＝任务从派发到办结的时长；「当前在办／在办超时」＝此刻的待办快照，"
                + "超时判定与顶部卡片同一规则（现在 − 到期时间；无到期时间的 " + noDueTimeTasks
                + " 条按「发起时间＋节点办理时限」推算）。"
                + "「催满升级档／自动通过档在办」＝该人在办任务所在节点配置的处置档，只数不改。"
                + "对账：屏上两列合计 " + cellOnHand + "／" + cellOverdue
                + " ＝ 全部人名下合计 " + onHandAllUsers + "／" + overdueTasksAllUsers
                + "（名下有在办而未进前 " + TOP_HANDLERS + " 的 " + appended + " 人已补在表尾，两列不会整屏为 0）；"
                + "有人可办 " + ownedTasks + " ＋ 无人认领 " + unownedTasks + " ＝ 待办 " + pending.size()
                + "，屏上按人合计 ≥ 任务数的差额＝一任务多候选按人重复计入（「谁还能办」的运营统计，不作为考核依据）。");
        return out;
    }

    /**
     * 超时未办榜（大屏"超时未办 Top 榜"的读侧）：<b>一行＝一个任务</b>。
     *
     * <p>把候选人逐个摊成独立行时，榜上「N 条」不是待办数：一个任务有多个候选人就会被算成多条。
     * 报表的计数单位始终是<b>任务</b>：「超期任务数量：按周期合计超期已处理任务数量」〔星·个人效率统计表〕，
     * 处理人只是分组/展示维度〔E·FSSC·330「处理人｜默认选中不限，显示所有审核处理人」〕。
     * 因此这里按任务出行、处理人合并成一个名单（{@code name} 顿号连接），候选人只展开<b>不重复计数</b>；
     * "每个人手上还有几单"的 per-user 规则由 {@link #handlerTop} 直接在同一份 PENDING 快照上现算，
     * 两处共用 {@link #overdueOf} 这唯一一套超时口径，榜单列与 KPI 因此天然同源。
     *
     * @param onlyOverdue true＝只返回已超时项（大屏榜），false＝全部在办（历史签名，现为通用参考规则）
     */
    private List<Map<String, Object>> overdueRank(List<WfTask> pending, Map<Long, String> names, boolean onlyOverdue) {
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (WfTask t : pending) {
            long age = hoursBetween(t.getCreateTime(), now);
            // 已超时实算走 overdueOf()（due_time 真值优先），时长带 1 位小数、行上标规则
            Overdue od = overdueOf(t, cfgIndex, now);
            if (onlyOverdue && !od.yes()) {
                continue;
            }
            List<Long> owners = ownersOf(t);
            Map<String, Object> m = base(t, age, od, owners, names);
            // 这一单落的处置档（催办/阶梯/AUTO_PASS），榜上直接可见
            String tier = timeoutTierOf(cfgIndex, t.getModelKey(), t.getNodeKey());
            m.put("timeoutAction", tier == null ? TIER_UNSPECIFIED : tier);
            if (owners.isEmpty()) {
                m.put("poolStatus", "无人认领");
            }
            out.add(m);
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("overdueHours")).doubleValue()).reversed());
        // 截断只作用于大屏 Top 榜（它要的是"最超前的几单"）；时效榜要算全量在办量，截了就把人漏掉
        if (!onlyOverdue || out.size() <= TOP_HANDLERS * 2) {
            return out;
        }
        return out.subList(0, TOP_HANDLERS * 2);
    }

    private Map<String, Object> base(WfTask t, long age, Overdue od, List<Long> owners, Map<Long, String> names) {
        boolean assigned = t.getAssigneeId() != null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getTaskId());
        m.put("bizNo", t.getBizNo());
        m.put("title", t.getTitle());
        // 节点名记录缺失时如实标注，不再让界面自己造一个"（未记节点名）"的伪节点
        String node = nodeLabel(t.getNodeName(), t.getNodeKey());
        m.put("node", node);
        m.put("unnamedTrace", isUnnamedNodeLabel(node));
        // 责任人＝一个任务一行：单人指派给真人；候选/池档把名单并成一列显示，不再摊成多行
        m.put("assigneeId", assigned ? t.getAssigneeId() : null);
        m.put("name", owners.isEmpty() ? "候选组待领取"
                : owners.stream().map(uid -> names.getOrDefault(uid, String.valueOf(uid)))
                        .collect(Collectors.joining("、")));
        m.put("candidateCount", assigned ? 1 : owners.size());
        // per-user 参考列（时效榜"在办/超时未办"）与这里同一套 ownersOf/overdueOf 规则
        m.put("ownerIds", owners);
        m.put("ageHours", age);
        m.put("timeoutHours", od.slaHours);
        m.put("dueTime", od.dueTime == null ? null : od.dueTime.toString());
        m.put("overdueHours", od.hours());
        // 这一行的超时是按哪个规则算出来的（DUE_TIME／CREATE_PLUS_SLA／NO_DUE_NO_SLA）
        m.put("overdueBasis", od.basis);
        m.put("urgedCount", t.getUrgedCount() == null ? 0 : t.getUrgedCount());
        // 卡单红牌：催满阈值仍 PENDING ＝ 卡单。派生标记，不写入数据库、不改状态。
        m.put("stuck", wfQueryService.isStuck(t.getUrgedCount()));
        return m;
    }

    // 6. 超时处置档计数（改档后 ESCALATE_AFTER_URGES／AUTO_PASS 节点计数可见）

    /**
     * 超时处置档规范值。取值名称表与超时作业侧 {@code WfTimeoutJob} 的三档同名同义，
     * 但本方法是查询面自己复刻的一份别名归一（{@code WfTimeoutJob.normalizeAction} 是作业包内的
     * 包级方法，查询面调用会把大屏的发布节奏绑到作业包上）。
     * <p><b>只读</b>：这里只把 {@code wf_node_config.timeout_action} 与 {@code wf_task_history.action}
     * 数出来，不判是否放行、不改任何节点行为；引擎侧的升级/自动通过逻辑一律不动。
     * <p><b>为什么"配了"不等于"生效"</b>：AUTO_PASS 还受 {@code sys_config.wf.timeout.autopass.nodes}
     * 白名单与 {@code wf.timeout.autopass.max_amount} 金额敞口约束，阶梯档实际升级还受作业侧
     * {@code wf.timeout.escalate.after_urges} 阈值约束；三者都是 {@code WfTimeoutJob} 的读取点，
     * 本查询面<b>不解析、不复算</b>，只把"配了几处"与"真发生了几行"并排列出（规则详见
     * {@link #timeoutActionLedger}）。
     */
    static final String TIER_REMIND = "AUTO_REMIND";
    static final String TIER_AUTO_PASS = "AUTO_PASS";
    static final String TIER_ESCALATE = "ESCALATE";
    static final String TIER_ESCALATE_LADDER = "ESCALATE_AFTER_URGES";
    /** 未配置或拼写不可识别（作业侧兜底为催办档） */
    static final String TIER_UNSPECIFIED = "UNSPECIFIED";

    /** 机器动作在 {@code wf_task_history.action} 里的写入数据库值（与作业侧/门面插桩同源，用于「实际发生」计数） */
    private static final String EV_URGE = "URGE";
    private static final String EV_AUTO_PASS = "AUTO_PASS";
    private static final String EV_ESCALATE = "ESCALATE";
    private static final String EV_AUTO_SKIP = "AUTO_SKIP";
    private static final String EV_TIMEOUT_RELEASE = "TIMEOUT_RELEASE";

    /** 处置档归一（NOTIFY/REMIND/URGE/AUTO_REMIND→催办；ESCALATE_AFTER_URGES/URGE_THEN_ESCALATE/REMIND_THEN_ESCALATE→阶梯档） */
    private static String timeoutTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return TIER_UNSPECIFIED;
        }
        switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "NOTIFY":
            case "REMIND":
            case "URGE":
            case "AUTO_REMIND":
                return TIER_REMIND;
            case "AUTO_PASS":
            case "AUTOPASS":
                return TIER_AUTO_PASS;
            case "ESCALATE_AFTER_URGES":
            case "URGE_THEN_ESCALATE":
            case "REMIND_THEN_ESCALATE":
                return TIER_ESCALATE_LADDER;
            case "AUTO_TRANSFER":
            case "TRANSFER":
            case "ESCALATE":
            case "AUTO_ESCALATE":
            case "REDISTRIBUTE":
                return TIER_ESCALATE;
            default:
                return TIER_UNSPECIFIED;
        }
    }

    /** 某模型某节点的处置档；查不到配置返回 null（＝该节点没有配置行，不参与档位统计） */
    private String timeoutTierOf(Map<String, WfNodeConfig> index, String modelKey, String nodeKey) {
        if (modelKey == null || nodeKey == null) {
            return null;
        }
        WfNodeConfig nc = index.get(modelKey + ":" + nodeKey);
        return nc == null ? null : timeoutTier(nc.getTimeoutAction());
    }

    /** 节点名 → 窗口内机器动作计数 + 该节点名下命中过的处置档（大屏三档共用底座） */
    private Map<String, NodeAction> nodeActions(List<WfTaskHistory> hist, Map<String, WfInstance> instances) {
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();
        Map<String, NodeAction> out = new LinkedHashMap<>();
        for (WfTaskHistory h : hist) {
            // 无名记录按真实来源出label（有 key 显 key，全无则标「记录缺名」），不再合成伪节点
            String node = nodeLabel(h.getNodeName(), h.getNodeKey());
            NodeAction na = out.computeIfAbsent(node, k -> new NodeAction());
            if (h.getAction() != null && !h.getAction().isBlank()) {
                na.actions.merge(h.getAction().trim().toUpperCase(Locale.ROOT), 1, Integer::sum);
            }
            String tier = timeoutTierOf(cfgIndex, modelKeyOf(instances, h.getProcInstId()), h.getNodeKey());
            if (tier != null) {
                na.tiers.add(tier);
            }
        }
        return out;
    }

    /**
     * 处置档总账：三档（概览卡/节点 SLA 明细/积压·时效）共用的一段聚合。
     *
     * <ul>
     *  <li><b>configured</b>：全库 {@code wf_node_config} 按档计数（节点数与其中配了 SLA 的节点数），
     *      回答"改档到底改了几处"——阶梯档/AUTO_PASS 各多少节点；</li>
     *  <li><b>fired</b>：窗口内 {@code wf_task_history} 的 URGE／AUTO_PASS／ESCALATE／AUTO_SKIP／
     *      TIMEOUT_RELEASE 事件数，回答"这些档真跑过没有"（配了不跑＝假开关，两列并排即露馅）；</li>
     *  <li><b>inFlight</b>：当前在办待办按处置档逐档计数（落在该档节点上的任务数 + 其中催办已达
     *      卡单阈值的条数）。卡单阈值取 {@code sys_config.wf.timeout.stuck.alert.after_urges}
     *      （经 {@code WfQueryService} 与看板红牌同一读取点）。</li>
     * </ul>
     *
     * <p>两个阈值不是同一个键，别混读：阶梯档<em>实际升级</em>用的是作业侧
     * {@code sys_config.wf.timeout.escalate.after_urges}（{@code WfTimeoutJob} 包内常量，默认 3），
     * 本台账的「其中卡单」列用的是大屏/告警侧 {@code wf.timeout.stuck.alert.after_urges}（默认亦 3）。
     * 两键同默认值但可各自改配，故本列只回答"催满到该打红牌了"，<b>不等于</b>作业已经升级过；
     * 作业真升级过的痕迹看 {@code firedEvents.ESCALATE}（{@code wf_task_history} 的 ESCALATE 行数）。
     * 白名单（{@code wf.timeout.autopass.nodes}）与金额敞口（{@code wf.timeout.autopass.max_amount}）
     * 属作业侧配置，大屏只标注不解析。
     */
    private Map<String, Object> timeoutActionLedger(List<WfTask> pending, List<WfTaskHistory> hist,
                                                    Map<String, WfInstance> instances) {
        Map<String, WfNodeConfig> cfgIndex = nodeConfigIndex();

        Map<String, Object> configured = new LinkedHashMap<>();
        for (String tier : new String[]{TIER_REMIND, TIER_ESCALATE_LADDER, TIER_AUTO_PASS, TIER_ESCALATE, TIER_UNSPECIFIED}) {
            configured.put(tier, new int[2]);
        }
        for (WfNodeConfig nc : WfNodeConfigResolver.loadAllCachedWith(() -> nodeConfigMapper.selectList(new LambdaQueryWrapper<>()))) {
            if (nc.getModelKey() == null || nc.getNodeKey() == null) {
                continue;
            }
            int[] c = (int[]) configured.get(timeoutTier(nc.getTimeoutAction()));
            if (c == null) {
                continue;
            }
            c[0]++;
            Integer sla = nc.getTimeoutHours();
            if (sla != null && sla > 0) {
                c[1]++;
            }
        }
        Map<String, Object> configuredOut = new LinkedHashMap<>();
        Map<String, Object> withSla = new LinkedHashMap<>();
        for (Map.Entry<String, Object> en : configured.entrySet()) {
            int[] c = (int[]) en.getValue();
            configuredOut.put(en.getKey(), c[0]);
            withSla.put(en.getKey(), c[1]);
        }

        Map<String, Integer> fired = new LinkedHashMap<>();
        for (String ev : new String[]{EV_URGE, EV_AUTO_PASS, EV_ESCALATE, EV_AUTO_SKIP, EV_TIMEOUT_RELEASE}) {
            fired.put(ev, 0);
        }
        for (WfTaskHistory h : hist) {
            String a = h.getAction() == null ? null : h.getAction().trim().toUpperCase(Locale.ROOT);
            if (a != null && fired.containsKey(a)) {
                fired.merge(a, 1, Integer::sum);
            }
        }

        int threshold = wfQueryService.stuckAfterUrges();
        // 在办落档五档逐档计数。阶梯档／AUTO_PASS 两档之外的档位若不给数，
        // 催办档与未配置档在页面上只能显示「—」——同一张表里两套规则，读者无法判断「—」是"没有"还是"没算"。
        // 五档同一规则，且五档 tasks 合计 + unclassifiedTasks ＝ 同页 KPI「待办任务」（＝pendingTasks）。
        Map<String, int[]> byTier = new LinkedHashMap<>();
        for (String tier : new String[]{TIER_REMIND, TIER_ESCALATE_LADDER, TIER_AUTO_PASS, TIER_ESCALATE, TIER_UNSPECIFIED}) {
            byTier.put(tier, new int[2]);
        }
        int unclassified = 0;
        for (WfTask t : pending) {
            String tier = timeoutTierOf(cfgIndex, t.getModelKey(), t.getNodeKey());
            if (tier == null) {
                // 该任务在 wf_node_config 里查不到 modelKey:nodeKey 配置行——不属于任何档，单列不摊进 UNSPECIFIED
                unclassified++;
                continue;
            }
            int[] c = byTier.get(tier);
            if (c == null) {
                continue;
            }
            c[0]++;
            if (wfQueryService.isStuck(t.getUrgedCount())) {
                c[1]++;
            }
        }
        Map<String, Object> byTierOut = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> en : byTier.entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("tasks", en.getValue()[0]);
            one.put("stuck", en.getValue()[1]);
            byTierOut.put(en.getKey(), one);
        }

        Map<String, Object> inFlight = new LinkedHashMap<>();
        inFlight.put("byTier", byTierOut);
        // 旧键名等价保留（＝byTier 中对应档的 tasks/stuck），不改已接线的读取方
        inFlight.put("escalateAfterUrgesTasks", byTier.get(TIER_ESCALATE_LADDER)[0]);
        inFlight.put("escalateAfterUrgesStuck", byTier.get(TIER_ESCALATE_LADDER)[1]);
        inFlight.put("autoPassTasks", byTier.get(TIER_AUTO_PASS)[0]);
        inFlight.put("autoPassStuck", byTier.get(TIER_AUTO_PASS)[1]);
        inFlight.put("stuckAfterUrges", threshold);
        inFlight.put("pendingTasks", pending.size());
        inFlight.put("unclassifiedTasks", unclassified);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configuredNodes", configuredOut);
        out.put("configuredNodesWithSla", withSla);
        out.put("firedEvents", fired);
        out.put("inFlight", inFlight);
        out.put("note", "档位＝节点超时处置配置归类后的处置档（只数不改，不判定是否放行）；"
                + "实际发生＝窗口内审批记录里的系统自动动作次数；"
                + "在办落档五档逐档计数，五档合计＋归不进档的待办行数＝待办任务数（与顶部卡片同一份待办快照）；"
                + "「超时自动通过」还需白名单与金额敞口两个条件同时满足才真放行，配了自动通过而实际发生数为 0，即形同未开。");
        return out;
    }

    /** 某节点名下命中过的处置档 + 机器动作行数（节点明细行的两列数据来源） */
    private static final class NodeAction {
        private final Map<String, Integer> actions = new LinkedHashMap<>();
        private final Set<String> tiers = new LinkedHashSet<>();

        private int ev(String action) {
            return actions.getOrDefault(action, 0);
        }

        private String tierLabel() {
            return tiers.isEmpty() ? TIER_UNSPECIFIED : String.join("/", tiers);
        }
    }

    // 共用底座

    // 超时实算的唯一谓词

    /** 到期时刻取 {@code wf_task.due_time}（双时限真值列，与 {@code OpTaskDueTimeScanJob}／{@code TaskPoolQueryService} 同源） */
    private static final String DUE_BASIS_TASK = "DUE_TIME";
    /** 无 {@code due_time} 的历史行：按 {@code create_time ＋节点 timeout_hours} 兜底回算（规则必须在行上标注） */
    private static final String DUE_BASIS_FALLBACK = "CREATE_PLUS_SLA";
    /** 既无 {@code due_time} 又无节点 SLA：超时<b>不可测</b>，一律不计超时（不是"没超时"） */
    private static final String DUE_BASIS_UNMEASURABLE = "NO_DUE_NO_SLA";

    /**
     * 单条任务的超时实算结果：超时时长（分钟）＋到期时刻＋节点配置 SLA＋所用规则。
     *
     * <p><b>假超时的根因</b>：页面把"账龄 − 配置 SLA"当成已超时时长，而 {@code wf_task.create_time}
     * 可能早于实例起流（起流前的时间全被算成账龄），于是报出的超时远大于按 {@code due_time}
     * 实算的结果。这里以 {@code due_time} 为唯一真相源，取不到才回算，
     * 并在应答里带上 {@code overdueBasis} 让规则可见。
     */
    private static final class Overdue {
        private final long minutes;
        private final String basis;
        private final LocalDateTime dueTime;
        private final Integer slaHours;

        private Overdue(long minutes, String basis, LocalDateTime dueTime, Integer slaHours) {
            this.minutes = minutes;
            this.basis = basis;
            this.dueTime = dueTime;
            this.slaHours = slaHours;
        }

        private boolean yes() {
            return minutes > 0;
        }

        /** 小时、1 位小数（16.4h 要显示成 16.4h，不能再被整数化抹平） */
        private double hours() {
            return Math.round(minutes / 6.0) / 10.0;
        }
    }

    /**
     * 超时判定<b>唯一</b>谓词（顶部 KPI、积压账龄、超时未办榜、时效榜两列全部走这一处，
     * 从此不可能出现"榜上有数、KPI 另一套"的分裂规则）。
     *
     * @param ref 比较基准：在办任务传 {@code now}，办结任务传办结时刻
     */
    private Overdue overdueOf(WfTask t, Map<String, WfNodeConfig> cfgIndex, LocalDateTime ref) {
        Integer sla = timeoutHoursOf(cfgIndex, t.getModelKey(), t.getNodeKey());
        LocalDateTime due = t.getDueTime();
        String basis = DUE_BASIS_TASK;
        if (due == null) {
            if (sla == null || sla <= 0 || t.getCreateTime() == null) {
                return new Overdue(0, DUE_BASIS_UNMEASURABLE, null, sla);
            }
            // 兜底：与作业侧 WfTimeoutJob.dueOf 同方向（due_time 缺失才回算），此处按自然小时、不复算工作日历
            due = t.getCreateTime().plusHours(sla);
            basis = DUE_BASIS_FALLBACK;
        }
        if (ref == null) {
            return new Overdue(0, DUE_BASIS_UNMEASURABLE, due, sla);
        }
        return new Overdue(Math.max(0, Duration.between(due, ref).toMinutes()), basis, due, sla);
    }

    /** 一个任务的可办理人（的两列与大屏榜共用）：已签收＝指派人一人；候选/池档＝候选人各计一次"谁还能办"。 */
    private static List<Long> ownersOf(WfTask t) {
        return t.getAssigneeId() != null
                ? List.of(t.getAssigneeId())
                : WfQueryService.parseCandidateIds(t.getCandidateIds());
    }

    /** 只有 node_key、名字缺失时的后缀（显 key 而不造名） */
    private static final String UNNAMED_SUFFIX = "（记录未记节点名）";
    /** 名字与 key 都没有时的整行标签（如实标缺名，不并成伪节点） */
    private static final String UNNAMED_BOTH = "（记录缺少名称·无节点信息）";

    /**
     * 节点显示名：<b>不造名</b>。
     * 有名字用名字；只有 {@code node_key} 就用 key（读者可反查 {@code wf_node_config}）；
     * 两者皆空如实标「记录缺名·无节点信息」——不能把所有无名记录并成一个
     * "（未记节点名）"的伪节点，那看起来就像一个真实的高频节点。
     */
    private static String nodeLabel(String nodeName, String nodeKey) {
        if (nodeName != null && !nodeName.isBlank()) {
            return nodeName.trim();
        }
        if (nodeKey != null && !nodeKey.isBlank()) {
            return nodeKey.trim() + UNNAMED_SUFFIX;
        }
        return UNNAMED_BOTH;
    }

    /** 这行是不是"无名记录"行（前端据此加标注，不把它当一个真实节点读）。 */
    private static boolean isUnnamedNodeLabel(String label) {
        return label != null && (label.endsWith(UNNAMED_SUFFIX) || UNNAMED_BOTH.equals(label));
    }

    private List<WfTaskHistory> historyWindow(int days) {
        Snap<Map<Integer, List<WfTaskHistory>>> c = historyCache;
        if (fresh(c) && c.value().containsKey(days)) {
            return c.value().get(days);
        }
        synchronized (snapshotLock) {
            c = historyCache;
            if (fresh(c) && c.value().containsKey(days)) {
                return c.value().get(days);
            }
            LocalDateTime from = LocalDateTime.now().minusDays(days);
            List<WfTaskHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                    .ge(WfTaskHistory::getOperTime, from)
                    .orderByAsc(WfTaskHistory::getProcInstId)
                    .orderByAsc(WfTaskHistory::getOperTime)
                    .orderByAsc(WfTaskHistory::getId)
                    .last("limit 20000"));
            Map<Integer, List<WfTaskHistory>> m =
                    (c != null && fresh(c)) ? new HashMap<>(c.value()) : new HashMap<>();
            m.put(days, rows);
            historyCache = new Snap<>(System.currentTimeMillis(), m);
            return rows;
        }
    }

    private List<WfTask> pendingTasks() {
        Snap<List<WfTask>> c = pendingCache;
        if (fresh(c)) {
            return c.value();
        }
        synchronized (snapshotLock) {
            c = pendingCache;
            if (fresh(c)) {
                return c.value();
            }
            List<WfTask> rows = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getStatus, "PENDING")
                    .orderByAsc(WfTask::getCreateTime));
            pendingCache = new Snap<>(System.currentTimeMillis(), rows);
            return rows;
        }
    }

    /** 按人办结聚合（窗口天数 → 结果），TTL 内复用同一份，见 {@link #SNAPSHOT_TTL_MS} 注释。 */
    private List<Map<String, Object>> handlerDoneStats(int window) {
        Snap<Map<Integer, List<Map<String, Object>>>> c = doneStatsCache;
        if (fresh(c) && c.value().containsKey(window)) {
            return c.value().get(window);
        }
        synchronized (snapshotLock) {
            c = doneStatsCache;
            if (fresh(c) && c.value().containsKey(window)) {
                return c.value().get(window);
            }
            List<Map<String, Object>> rows = taskMapper.handlerDoneStats(LocalDateTime.now().minusDays(window));
            Map<Integer, List<Map<String, Object>>> m =
                    (c != null && fresh(c)) ? new HashMap<>(c.value()) : new HashMap<>();
            m.put(window, rows);
            doneStatsCache = new Snap<>(System.currentTimeMillis(), m);
            return rows;
        }
    }

    /** 按实例分组的真实节点序列（连续同节点事件折叠，如转办/催办不产生新节点）。 */
    private Map<String, List<String>> nodeSequences(List<WfTaskHistory> hist) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (WfTaskHistory h : hist) {
            if (h.getProcInstId() == null || h.getNodeName() == null) {
                continue;
            }
            List<String> seq = out.computeIfAbsent(h.getProcInstId(), k -> new ArrayList<>());
            if (seq.isEmpty() || !seq.get(seq.size() - 1).equals(h.getNodeName())) {
                seq.add(h.getNodeName());
            }
        }
        return out;
    }

    private Map<String, WfInstance> instancesOf(List<WfTaskHistory> hist) {
        Set<String> ids = new LinkedHashSet<>();
        for (WfTaskHistory h : hist) {
            if (h.getProcInstId() != null) {
                ids.add(h.getProcInstId());
            }
        }
        Map<String, WfInstance> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<String> idList = new ArrayList<>(ids);
        for (int i = 0; i < idList.size(); i += 500) {
            for (WfInstance inst : instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .in(WfInstance::getProcInstId, idList.subList(i, Math.min(i + 500, idList.size()))))) {
                out.put(inst.getProcInstId(), inst);
            }
        }
        return out;
    }

    private Map<String, WfNodeConfig> nodeConfigIndex() {
        Map<String, WfNodeConfig> out = new HashMap<>();
        for (WfNodeConfig nc : WfNodeConfigResolver.loadAllCachedWith(() -> nodeConfigMapper.selectList(new LambdaQueryWrapper<>()))) {
            if (nc.getModelKey() != null && nc.getNodeKey() != null) {
                out.put(nc.getModelKey() + ":" + nc.getNodeKey(), nc);
            }
        }
        return out;
    }

    private Integer timeoutHoursOf(Map<String, WfNodeConfig> index, String modelKey, String nodeKey) {
        if (modelKey == null || nodeKey == null) {
            return null;
        }
        WfNodeConfig nc = index.get(modelKey + ":" + nodeKey);
        return nc == null ? null : nc.getTimeoutHours();
    }

    private String modelKeyOf(Map<String, WfInstance> instances, String procInstId) {
        WfInstance inst = procInstId == null ? null : instances.get(procInstId);
        return inst == null ? null : inst.getModelKey();
    }

    private long countFinishedToday() {
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        return instanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .isNotNull(WfInstance::getEndTime)
                .ge(WfInstance::getEndTime, dayStart));
    }

    private Map<Long, String> namesOf(List<WfTask> tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (WfTask t : tasks) {
            if (t.getAssigneeId() != null) {
                ids.add(t.getAssigneeId());
            }
            ids.addAll(WfQueryService.parseCandidateIds(t.getCandidateIds()));
        }
        return namesOfIds(ids);
    }

    /** 用户 id → 显示名（500 一档批查） */
    private Map<Long, String> namesOfIds(Set<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<Long> idList = new ArrayList<>(ids);
        for (int i = 0; i < idList.size(); i += 500) {
            for (SysUser u : sysUserMapper.selectBatchIds(idList.subList(i, Math.min(i + 500, idList.size())))) {
                out.put(u.getId(), u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName());
            }
        }
        return out;
    }

    /** SQL 聚合列取值：count() 回 Long、sum() 回 BigDecimal，统一成 long */
    private static long numOf(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private int bucketOf(long minutes) {
        for (int i = 0; i < BUCKET_MAX_MINUTES.length; i++) {
            if (minutes < BUCKET_MAX_MINUTES[i]) {
                return i;
            }
        }
        return BUCKET_MAX_MINUTES.length;
    }

    private int agingBucketOf(long hours) {
        for (int i = 0; i < AGING_MAX_HOURS.length; i++) {
            if (hours < AGING_MAX_HOURS[i]) {
                return i;
            }
        }
        return AGING_MAX_HOURS.length;
    }

    private long hoursBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return Math.max(0, Duration.between(from, to).toHours());
    }

    private int normalizeDays(int days) {
        return Math.min(365, Math.max(1, days));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 首次出现步序的均值必须是实数：用 {@code (int)(sum / size)} 向下取整时，
     * 1.5 与 1.0 并列，节点分层排序会把真实流转边判成回边丢掉（桑基少一条
     * 「扫描单据→直属上级审核」）。改为 double 比较，只影响层号排序，不参与展示。
     */
    private double avg(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (int v : values) {
            sum += v;
        }
        return (double) sum / values.size();
    }

    /** 单节点聚合中间态。 */
    private static final class NodeStat {
        private final String node;
        private final int[] buckets = new int[BUCKET_LABELS.length];
        private int events;
        private long minutesSum;
        private long maxMinutes;
        private Integer timeoutHours;
        private int slaMeasured;
        private int slaOnTime;
        private int overdue;

        private NodeStat(String node) {
            this.node = node;
        }
    }

    /** 积压节点聚合中间态（账龄/超时之外再挂处置档与催办规则）。 */
    private static final class BacklogNode {
        private final String name;
        private final Set<String> tiers = new LinkedHashSet<>();
        private int count;
        private int overdue;
        private int maxAgeHours;
        /** 落在阶梯档（ESCALATE_AFTER_URGES）节点上的在办任务数 */
        private int ladder;
        /** 落在 AUTO_PASS 档节点上的在办任务数 */
        private int autoPass;
        /** 该节点在办任务的累计催办次数（wf_task.urged_count 求和） */
        private int urged;
        /** 催办已达卡单阈值仍 PENDING 的条数（与作业侧同一阈值读取点） */
        private int stuck;
        /** 本节点在办里没有 wf_task.due_time 的条数（＝超时走兜底规则的规模） */
        private int noDue;
        /** 本节点按兜底规则（create_time＋SLA）判出的超时条数 */
        private int fallbackOverdue;

        private BacklogNode(String name) {
            this.name = name;
        }
    }
}
