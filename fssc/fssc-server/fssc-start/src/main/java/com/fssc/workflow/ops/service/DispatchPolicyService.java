package com.fssc.workflow.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.ops.dto.AssignmentDecision;
import com.fssc.workflow.ops.dto.CandidateLoad;
import com.fssc.workflow.ops.entity.OpDispatchPolicy;
import com.fssc.workflow.ops.mapper.OpDispatchPolicyMapper;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * G3共享作业分配策略：配置读取 ＋ 四模式策略解析器 ＋ 普通/加急双时限。
 *
 * <p><b>分配规则</b>（方案 §三 ③ 共享作业层，{@code 分配策略表}）：
 * <ul>
 *   <li><b>抢单 PICK</b>：任务进池，池内谁先打开谁锁定他人不可处理 —— 本类不选人，只出"进池"决策；</li>
 *   <li><b>均衡·按时 BALANCE_TIME</b>：默认每 1h 一分，选当前<b>在办任务预计耗时</b>最少者；</li>
 *   <li><b>均衡·按量 BALANCE_VOLUME</b>：累积 N 单一分配，选<b>在手任务数</b>最少者（3 人得 4/3/3）；</li>
 *   <li><b>智能 SMART</b>：在均衡基础上按「在手量 + 时限紧迫度 + 加急惩罚」加权，优先派给手上任务少的人。</li>
 * </ul>
 *
 * <p><b>为什么预计耗时按节点历史平均停留估算</b>：{@code wf_task_history.duration} 是既有真值
 * 每个节点办理的真实停留毫秒，比"给所有人一个固定系数"更贴近"按时均衡"的本意；
 * 该节点无历史样本时退回"时限的 1/3"作兜底，并在决策理由里如实标注估算来源。
 *
 * <p><b>每个配置列都要有读取点</b>：本类的每一个配置列都落在具体代码上——
 * {@code strategy} 进 {@link #decide}，{@code normalHours/urgentHours} 进
 * {@code TaskTimeLimitService} 的 due_time 计算，{@code balanceIntervalHours/balanceBatchSize}
 * 进 {@link #planBalanceVolume} 与决策理由，{@code smartWeight*} 进 SMART 打分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPolicyService {

    private final OpDispatchPolicyMapper policyMapper;
    private final WfTaskMapper taskMapper;
    private final WfTaskHistoryMapper historyMapper;
    private final SysUserMapper sysUserMapper;

    /** 全局默认行标识（model_key='*' AND node_key='*'） */
    public static final String GLOBAL_KEY = "*";

    /** 内置兜底时限：通行规则 普通 24h / 加急 4h（库里无配置行时的最后一道防线） */
    private static final BigDecimal BUILTIN_NORMAL_HOURS = new BigDecimal("24");
    private static final BigDecimal BUILTIN_URGENT_HOURS = new BigDecimal("4");

    /** 节点历史平均停留的取样窗口（天）与最小样本数 */
    private static final int DWELL_WINDOW_DAYS = 90;
    private static final int DWELL_MIN_SAMPLES = 3;

    private static final Set<String> VALID_STRATEGIES =
            Set.of("PICK", "BALANCE_TIME", "BALANCE_VOLUME", "SMART");

    // 1. 配置 CRUD（列表/详情/新增/修改/删除）

    public PageResult<OpDispatchPolicy> page(PageQuery q, String keyword, String strategy) {
        LambdaQueryWrapper<OpDispatchPolicy> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(OpDispatchPolicy::getPolicyName, keyword)
                    .or().like(OpDispatchPolicy::getModelKey, keyword)
                    .or().like(OpDispatchPolicy::getNodeKey, keyword));
        }
        if (strategy != null && !strategy.isBlank()) {
            w.eq(OpDispatchPolicy::getStrategy, strategy.trim().toUpperCase());
        }
        w.orderByAsc(OpDispatchPolicy::getModelKey).orderByAsc(OpDispatchPolicy::getNodeKey);
        Page<OpDispatchPolicy> p = policyMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    public OpDispatchPolicy get(Long id) {
        OpDispatchPolicy p = policyMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分配策略配置不存在: " + id);
        }
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(OpDispatchPolicy policy) {
        normalizeAndValidate(policy);
        Long dup = policyMapper.selectCount(new LambdaQueryWrapper<OpDispatchPolicy>()
                .eq(OpDispatchPolicy::getModelKey, policy.getModelKey())
                .eq(OpDispatchPolicy::getNodeKey, policy.getNodeKey()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该节点已有策略配置（modelKey=" + policy.getModelKey() + ", nodeKey=" + policy.getNodeKey()
                            + "），请直接修改，避免出现两条互相矛盾的策略");
        }
        policyMapper.insert(policy);
        log.info("G3 新增分配策略: id={}, node={}:{}, strategy={}", policy.getId(),
                policy.getModelKey(), policy.getNodeKey(), policy.getStrategy());
        return policy.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OpDispatchPolicy policy) {
        if (policy.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "策略配置 ID 必填");
        }
        normalizeAndValidate(policy);
        policyMapper.updateById(policy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        policyMapper.deleteById(id);
    }

    private void normalizeAndValidate(OpDispatchPolicy p) {
        p.setModelKey(p.getModelKey() == null || p.getModelKey().isBlank() ? GLOBAL_KEY : p.getModelKey().trim());
        p.setNodeKey(p.getNodeKey() == null || p.getNodeKey().isBlank() ? GLOBAL_KEY : p.getNodeKey().trim());
        String s = p.getStrategy() == null ? "" : p.getStrategy().trim().toUpperCase();
        if (!VALID_STRATEGIES.contains(s)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "分配策略只能是 PICK/BALANCE_TIME/BALANCE_VOLUME/SMART 之一，收到「" + p.getStrategy() + "」");
        }
        p.setStrategy(s);
        if (p.getNormalHours() == null || p.getNormalHours().signum() <= 0) {
            p.setNormalHours(BUILTIN_NORMAL_HOURS);
        }
        if (p.getUrgentHours() == null || p.getUrgentHours().signum() <= 0) {
            p.setUrgentHours(BUILTIN_URGENT_HOURS);
        }
        if (p.getEnabled() == null) {
            p.setEnabled(1);
        }
    }

    // 2. 配置解析：节点级 → 全局默认行 → 内置兜底

    /**
     * 解析某节点的生效策略：先按 {@code modelKey:nodeKey} 精确匹配（与
     * {@code WfNodeConfigResolver} 同一"精确键、不位置兜底"的契约），未命中再取全局默认行，
     * 仍无则返回内置兜底对象（抢单 + 24h/4h）。<b>不返回 null</b>——调用方无需到处判空。
     */
    public OpDispatchPolicy resolvePolicy(String modelKey, String nodeKey) {
        if (modelKey != null && !modelKey.isBlank() && nodeKey != null && !nodeKey.isBlank()) {
            OpDispatchPolicy hit = policyMapper.selectOne(new LambdaQueryWrapper<OpDispatchPolicy>()
                    .eq(OpDispatchPolicy::getModelKey, modelKey)
                    .eq(OpDispatchPolicy::getNodeKey, nodeKey)
                    .eq(OpDispatchPolicy::getEnabled, 1)
                    .last("limit 1"));
            if (hit != null) {
                return hit;
            }
        }
        OpDispatchPolicy global = policyMapper.selectOne(new LambdaQueryWrapper<OpDispatchPolicy>()
                .eq(OpDispatchPolicy::getModelKey, GLOBAL_KEY)
                .eq(OpDispatchPolicy::getNodeKey, GLOBAL_KEY)
                .eq(OpDispatchPolicy::getEnabled, 1)
                .last("limit 1"));
        if (global != null) {
            return global;
        }
        OpDispatchPolicy builtin = new OpDispatchPolicy();
        builtin.setModelKey(GLOBAL_KEY);
        builtin.setNodeKey(GLOBAL_KEY);
        builtin.setPolicyName("内置默认策略");
        builtin.setStrategy("PICK");
        builtin.setNormalHours(BUILTIN_NORMAL_HOURS);
        builtin.setUrgentHours(BUILTIN_URGENT_HOURS);
        builtin.setEnabled(1);
        return builtin;
    }

    /** 按优先级取时限（小时）：加急取 urgentHours，其余取 normalHours */
    public BigDecimal limitHours(OpDispatchPolicy policy, String priority) {
        BigDecimal h = "URGENT".equalsIgnoreCase(priority == null ? "" : priority.trim())
                ? policy.getUrgentHours() : policy.getNormalHours();
        if (h == null || h.signum() <= 0) {
            h = "URGENT".equalsIgnoreCase(priority == null ? "" : priority.trim())
                    ? BUILTIN_URGENT_HOURS : BUILTIN_NORMAL_HOURS;
        }
        return h;
    }

    // 3. 四模式策略解析器（可解释）

    /**
     * 按策略从候选池里挑出目标办理人，并给出可解释的决策说明。
     *
     * <p>抢单模式（PICK）<b>不选人</b>：通行规则是"打开即锁定他人不可处理"，
     * 由池内候选人主动认领（{@code WfEngineFacade.claim}），系统抢单式指派反而破坏该语义。
     */
    public AssignmentDecision decide(OpDispatchPolicy policy, String priority,
                                    List<CandidateLoad> candidates, LocalDateTime now) {
        String strategy = policy.normalizedStrategy();
        Long policyId = policy.getId();
        String snapshot = snapshot(candidates);

        if (candidates == null || candidates.isEmpty()) {
            return new AssignmentDecision(strategy, policyId, null, null, "",
                    "候选池为空，无法自动分配（策略=" + strategy + "）；请检查节点角色成员或分组配置");
        }
        if ("PICK".equals(strategy)) {
            return new AssignmentDecision(strategy, policyId, null, null, snapshot,
                    "抢单模式：任务进池待领取，不做系统指派（池内谁先打开谁锁定，"
                            + "配置的分配间隔 " + fmt(policy.getBalanceIntervalHours()) + "h 仅作池内轮转提示）");
        }
        if (candidates.size() == 1) {
            CandidateLoad only = candidates.get(0);
            return new AssignmentDecision(strategy, policyId, only.userId(), only.userName(), snapshot,
                    "候选仅 1 人（" + only.userName() + "，在手 " + only.pendingCount() + " 单），直接指派");
        }

        boolean urgent = "URGENT".equalsIgnoreCase(priority == null ? "" : priority.trim());
        CandidateLoad chosen = switch (strategy) {
            case "BALANCE_TIME" -> candidates.stream()
                    .min(Comparator.comparingLong(CandidateLoad::expectedMinutes)
                            .thenComparingInt(CandidateLoad::pendingCount))
                    .orElse(candidates.get(0));
            case "BALANCE_VOLUME" -> candidates.stream()
                    .min(Comparator.comparingInt(CandidateLoad::pendingCount)
                            .thenComparingLong(CandidateLoad::expectedMinutes))
                    .orElse(candidates.get(0));
            default -> smartPick(candidates, policy, urgent);
        };
        return new AssignmentDecision(strategy, policyId, chosen.userId(), chosen.userName(), snapshot,
                reason(strategy, chosen, candidates, policy, urgent, now));
    }

    /**
     * 智能分配：在均衡基础上加权。
     *
     * <pre>
     *   score = wVol * 在手量归一 // 手上任务少的人优先
     *         + wUrg * 超时风险（已超时/在手）
     *         + (本单加急 ? wPri * 在手量归一 : 0) // 加急单再压一次"手上多的"
     * </pre>
     * 分数低者胜；权重来自 {@code op_dispatch_policy.smart_weight_*}，运营可调。
     */
    private CandidateLoad smartPick(List<CandidateLoad> candidates, OpDispatchPolicy policy, boolean urgent) {
        int maxPending = candidates.stream().mapToInt(CandidateLoad::pendingCount).max().orElse(1);
        double wVol = weight(policy.getSmartWeightVolume(), 0.5);
        double wUrg = weight(policy.getSmartWeightUrgency(), 0.3);
        double wPri = weight(policy.getSmartWeightPriority(), 0.2);

        Map<Long, Double> scores = new HashMap<>();
        for (CandidateLoad c : candidates) {
            double volNorm = maxPending <= 0 ? 0d : (double) c.pendingCount() / maxPending;
            double score = wVol * volNorm + wUrg * c.urgencyRatio() + (urgent ? wPri * volNorm : 0d);
            scores.put(c.userId(), score);
        }
        return candidates.stream()
                .min(Comparator.comparingDouble((CandidateLoad c) -> scores.getOrDefault(c.userId(), 0d))
                        .thenComparingInt(CandidateLoad::pendingCount))
                .orElse(candidates.get(0));
    }

    private double weight(BigDecimal v, double dft) {
        return v == null ? dft : v.doubleValue();
    }

    private String reason(String strategy, CandidateLoad chosen, List<CandidateLoad> all,
                          OpDispatchPolicy policy, boolean urgent, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();
        switch (strategy) {
            case "BALANCE_TIME" -> sb.append("均衡·按时：比较各候选在办任务预计耗时合计，")
                    .append(chosen.userName()).append(" 最少（").append(chosen.expectedMinutes())
                    .append(" 分钟 / ").append(chosen.pendingCount()).append(" 单）")
                    .append("；节点分配间隔配置 ").append(fmt(policy.getBalanceIntervalHours())).append("h");
            case "BALANCE_VOLUME" -> sb.append("均衡·按量：比较各候选在手任务数，")
                    .append(chosen.userName()).append(" 最少（").append(chosen.pendingCount()).append(" 单）")
                    .append("；批量批大小配置 ").append(policy.getBalanceBatchSize() == null ? 10 : policy.getBalanceBatchSize()).append(" 单");
            default -> {
                int maxPending = all.stream().mapToInt(CandidateLoad::pendingCount).max().orElse(1);
                sb.append("智能加权：在手量归一(").append(chosen.pendingCount()).append("/").append(maxPending)
                        .append(") × 权重 ").append(fmt(policy.getSmartWeightVolume()))
                        .append(" + 超时风险(").append(String.format("%.2f", chosen.urgencyRatio()))
                        .append(") × 权重 ").append(fmt(policy.getSmartWeightUrgency()));
                if (urgent) {
                    sb.append(" + 加急惩罚 × 权重 ").append(fmt(policy.getSmartWeightPriority()));
                }
                sb.append(" ⇒ ").append(chosen.userName()).append(" 得分最低");
            }
        }
        if (urgent) {
            sb.append("；本单为加急（时限 ").append(fmt(policy.getUrgentHours())).append("h）");
        }
        sb.append("；决策时刻 ").append(now);
        return sb.toString();
    }

    /** 候选快照：用户ID:姓名:在手量:预计耗时分钟:超时数，一行一人（日志里可直接追） */
    private String snapshot(List<CandidateLoad> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CandidateLoad c : candidates) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(c.userId()).append(':').append(c.userName() == null ? "-" : c.userName())
                    .append(':').append(c.pendingCount())
                    .append(':').append(c.expectedMinutes())
                    .append(':').append(c.overdueCount());
        }
        return sb.length() > 4000 ? sb.substring(0, 4000) : sb.toString();
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "-" : v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // 4. 均衡·按量：累积 N 单一分配（3 人得 4/3/3）

    /**
     * 均衡·按量的批量分配计划：把 {@code taskIds} 逐单派给"当前在手量 + 本轮已派量"最少的人，
     * 等价于"累积 10 单一分配，3 人得 4/3/3"的结果。
     *
     * <p>只出计划不写入数据库（写入数据库由调用方决定），因此可作为"分配试算"直接给运营看。
     *
     * @return taskId → 用户ID（保持入参顺序，同序输出便于前端逐行展示）
     */
    public Map<String, Long> planBalanceVolume(List<String> taskIds, List<CandidateLoad> candidates, int batchSize) {
        Map<String, Long> plan = new LinkedHashMap<>();
        if (taskIds == null || taskIds.isEmpty() || candidates == null || candidates.isEmpty()) {
            return plan;
        }
        int limit = batchSize > 0 ? Math.min(batchSize, taskIds.size()) : taskIds.size();
        Map<Long, Integer> assigned = new HashMap<>();
        for (int i = 0; i < limit; i++) {
            CandidateLoad best = candidates.stream()
                    .min(Comparator.comparingInt((CandidateLoad c) -> c.pendingCount() + assigned.getOrDefault(c.userId(), 0))
                            .thenComparingLong(CandidateLoad::expectedMinutes))
                    .orElse(candidates.get(0));
            assigned.merge(best.userId(), 1, Integer::sum);
            plan.put(taskIds.get(i), best.userId());
        }
        return plan;
    }

    // 5. 候选人负载画像（三种策略共用的唯一规则）

    /**
     * 构建候选人的负载画像。数据源：{@code wf_task} 的 PENDING 行（在手量 + due_time 超时）
     * ＋ {@code wf_task_history.duration} 的节点历史平均停留（预计耗时）。
     *
     * @param userIds 候选用户 ID
     */
    public List<CandidateLoad> loadCandidates(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .and(w -> w.in(WfTask::getAssigneeId, ids)
                        .or().isNull(WfTask::getAssigneeId)));

        Map<Long, String> names = userNames(ids);
        Map<String, Long> dwell = nodeAvgDwellMinutes();
        LocalDateTime now = LocalDateTime.now();

        Map<Long, int[]> counter = new HashMap<>();      // userId -> [在手量, 超时数]
        Map<Long, Long> expected = new HashMap<>();      // userId -> 预计耗时合计
        for (Long id : ids) {
            counter.put(id, new int[]{0, 0});
            expected.put(id, 0L);
        }
        for (WfTask t : pending) {
            Set<Long> owners = new LinkedHashSet<>();
            if (t.getAssigneeId() != null && ids.contains(t.getAssigneeId())) {
                owners.add(t.getAssigneeId());
            }
            // 未指派（任务池候选组态）：候选人也算"潜在承接人"，否则池内节点永远算不出负载
            if (t.getAssigneeId() == null && t.getCandidateIds() != null) {
                for (Long cid : WfQueryService.parseCandidateIds(t.getCandidateIds())) {
                    if (ids.contains(cid)) {
                        owners.add(cid);
                    }
                }
            }
            long minutes = dwellMinutesOf(t, dwell);
            boolean overdue = t.getDueTime() != null && t.getDueTime().isBefore(now);
            for (Long owner : owners) {
                int[] c = counter.get(owner);
                c[0]++;
                if (overdue) {
                    c[1]++;
                }
                expected.merge(owner, minutes, Long::sum);
            }
        }

        List<CandidateLoad> out = new ArrayList<>();
        for (Long id : ids) {
            int[] c = counter.getOrDefault(id, new int[]{0, 0});
            out.add(new CandidateLoad(id, names.get(id), c[0], expected.getOrDefault(id, 0L), c[1]));
        }
        return out;
    }

    /** 单条任务的预计处理耗时：节点历史平均停留，无样本则退回该节点时限的 1/3 */
    private long dwellMinutesOf(WfTask t, Map<String, Long> dwell) {
        Long avg = dwell.get(t.getNodeKey() == null ? "" : t.getNodeKey());
        if (avg != null && avg > 0) {
            return avg;
        }
        OpDispatchPolicy p = resolvePolicy(t.getModelKey(), t.getNodeKey());
        BigDecimal hours = limitHours(p, "NORMAL");
        return Math.max(1L, hours.multiply(new BigDecimal("60")).divide(new BigDecimal("3"), 0, RoundingMode.HALF_UP).longValue());
    }

    /**
     * 节点历史平均停留（分钟）：{@code wf_task_history.duration} 按 node_key 求平均。
     * 只取最近 {@value #DWELL_WINDOW_DAYS} 天且样本数 ≥ {@value #DWELL_MIN_SAMPLES} 的节点，
     * 样本太少时宁可不给数（退回时限估算），也不让一两单的偶然值左右均衡结果。
     */
    private Map<String, Long> nodeAvgDwellMinutes() {
        LocalDateTime from = LocalDateTime.now().minusDays(DWELL_WINDOW_DAYS);
        List<WfTaskHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .select(WfTaskHistory::getNodeKey, WfTaskHistory::getDuration)
                .ge(WfTaskHistory::getOperTime, from)
                .isNotNull(WfTaskHistory::getDuration)
                .isNotNull(WfTaskHistory::getNodeKey));
        Map<String, long[]> agg = new HashMap<>();
        for (WfTaskHistory h : rows) {
            if (h.getDuration() == null || h.getDuration() <= 0) {
                continue;
            }
            long[] a = agg.computeIfAbsent(h.getNodeKey(), k -> new long[2]);
            a[0] += h.getDuration();
            a[1]++;
        }
        Map<String, Long> out = new HashMap<>();
        agg.forEach((k, a) -> {
            if (a[1] >= DWELL_MIN_SAMPLES) {
                out.put(k, a[0] / a[1] / 60000L);
            }
        });
        return out;
    }

    /** 批量取用户名（一次查完，避免 N+1） */
    public Map<Long, String> userNames(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        Set<Long> ids = new LinkedHashSet<>();
        userIds.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return out;
        }
        for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
            out.put(u.getId(), u.getNickName());
        }
        return out;
    }
}
