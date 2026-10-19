package com.fssc.workflow.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 流程实例归档作业（G4 第 6 条「流程实例归档」，2026-09-24）。
 *
 * <p><b>为什么需要它</b>：单据侧早有冷热分离（{@code exp_bill*_cold}，见 {@code ColdArchiveService}），
 * 流程侧一直<b>没有</b>对应机制——{@code wf_instance / wf_task / wf_task_history} 只增不减，
 *
 * <p><b>搬移口径</b>：{@code wf_instance.status} 已终态（{@link #TERMINAL_STATUSES}）<b>且</b>
 * {@code end_time < now - N 个月}。N 取 {@code sys_config.wf.archive.months}，缺省 3、<b>硬钳 &le; 3</b>
 *
 * <p><b>两道闸（缺省即关，零行为变化）</b>：
 * <ol>
 *   <li>{@code sys_config.wf.archive.enabled}，<b>缺省 0（fail-closed，默认关）</b>：未配置时本作业
 *       一条都不搬，与上线前行为完全一致；</li>
 *   <li>{@code sys_config.wf.archive.batch.limit}（缺省 {@value #DEFAULT_BATCH_LIMIT}）：单次归档条数上限，
 *       避免一次性把在线表搬空导致下游查询/报表骤降。</li>
 * </ol>
 *
 * <p><b>搬移的原子性</b>：每个实例一个独立事务（{@link TransactionTemplate}），事务内
 * "插冷表 → 删在线表" 成对完成；任一步失败整实例回滚，在线数据不受影响，继续下一个实例。
 *
 * <p><b>为什么不搬 {@code ACT_*}</b>：{@code ACT_*} 是 Flowable 的<b>引擎账</b>，它的保留与清理
 * 是引擎自己的生命周期——本仓已有 {@code WfHistoryArchiveService} 按周清理超期历史。把 {@code ACT_*}
 * 纳入本作业的事务边界会造成两套口径打架（例如引擎账先被引擎清掉、业务账再删时对不上），
 * 且引擎表的分区/外键关系不受我们控制。故本作业<b>只搬 wf_* 镜像表，绝不增删任何 ACT_* 行</b>。
 *
 * <p><b>跨租户扫描</b>：本作业在调度线程运行（无租户上下文），所有 wf_* 查询一律包在
 * 冷表 {@code wf_*_cold} 未挂实体、不在 MyBatis-Plus 租户拦截器管辖内，其 SQL 走
 * {@link JdbcTemplate} 并手工带 {@code tenant_id}（与 {@code ColdArchiveService} 同一前例）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfInstanceArchiveJob {

    /** 归档开关：0=关（缺省）/ 1=开 */
    private static final String CFG_ENABLED = "wf.archive.enabled";
    /** 归档月数：完成早于 N 个月才归档，缺省 3，硬钳 [1,3] */
    private static final String CFG_MONTHS = "wf.archive.months";
    /** 单轮归档条数上限（每租户每轮），缺省 200 */
    private static final String CFG_BATCH_LIMIT = "wf.archive.batch.limit";

    private static final int DEFAULT_MONTHS = 3;
    private static final int MAX_MONTHS = 3;
    private static final int DEFAULT_BATCH_LIMIT = 200;

    /**
     * 终态集合（取值来源 {@link WfInstance#getStatus()} 的真实口径：
     * RUNNING 审批中 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销 / TERMINATED 已终止）。
     * {@code RUNNING} 是在途态，永不归档。
     */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("APPROVED", "REJECTED", "CANCELLED", "TERMINATED");

    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfTaskHistoryMapper wfTaskHistoryMapper;
    private final SysConfigMapper sysConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager txManager;
    private final JobHeartbeatService jobHeartbeatService;
    /** 每实例一个独立事务：@RequiredArgsConstructor 不注入 TransactionTemplate，改由 @PostConstruct 现搭 */
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void initTx() {
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    /** 每日一轮（首次延迟 5 分钟），走既有心跳包装，异常吞掉不外抛。 */
    @Scheduled(fixedDelay = 60_000 * 60 * 24, initialDelay = 60_000 * 5)
    public void archive() {
        jobHeartbeatService.run("WfInstanceArchiveJob", () -> {
            try {
                Map<String, Object> r = doArchive();
                log.info("[流程归档] 完成：{}", r);
            } catch (Exception e) {
                log.error("[流程归档] 执行异常", e);
            }
        });
    }

    /**
     * 归档主流程（供定时任务与运维手动触发）：逐租户，enabled=1 才搬。
     *
     * <p>搬移前把待归档实例列表<b>只读</b>打到日志留证。返回结构：
     * {@code {archived, failed, scannedTenants, perTenant:{tid:{months,count,moved}}}}。
     */
    public Map<String, Object> doArchive() {
        Map<String, Object> summary = new LinkedHashMap<>();
        int archived = 0;
        int failed = 0;
        Map<String, Object> perTenant = new LinkedHashMap<>();
        for (Long tenantId : listInstanceTenants()) {
            int enabled = readConfigInt(tenantId, CFG_ENABLED, 0);
            int months = clampMonths(readConfigInt(tenantId, CFG_MONTHS, DEFAULT_MONTHS));
            int limit = Math.max(1, readConfigInt(tenantId, CFG_BATCH_LIMIT, DEFAULT_BATCH_LIMIT));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("enabled", enabled);
            row.put("months", months);
            row.put("limit", limit);
            if (enabled != 1) {
                perTenant.put(String.valueOf(tenantId), row);
                continue;
            }
            List<WfInstance> candidates = listCandidates(tenantId, months, limit);
            row.put("candidates", candidates.size());
            perTenant.put(String.valueOf(tenantId), row);
            if (candidates.isEmpty()) {
                continue;
            }
            // 归档前只读留证：把将被搬移的实例列出来（运维可据此复核口径是否过宽）
            log.info("[流程归档] 租户 {} 本批待归档 {} 条（months={}, limit={}）：{}",
                    tenantId, candidates.size(), months, limit, describe(candidates));
            if (candidates.size() >= limit) {
                log.warn("[流程归档] 租户 {} 触及单轮上限 {} 条，剩余将在下一轮继续", tenantId, limit);
            }
            int moved = 0;
            for (WfInstance inst : candidates) {
                try {
                    archiveOne(tenantId, inst);
                    moved++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[流程归档] 实例搬移失败 tenant={} procInstId={}：{}",
                            tenantId, inst.getProcInstId(), e.getMessage(), e);
                }
            }
            archived += moved;
            row.put("moved", moved);
        }
        summary.put("archived", archived);
        summary.put("failed", failed);
        summary.put("perTenant", perTenant);
        return summary;
    }

    /**
     * 只读预览：按当前配置回答"当前会归档多少条、N 是多少"（不触发任何搬移）。
     *
     * <p>返回 {@code {maxMonths, defaultMonths, terminalStatuses, total, tenants:[{tenantId,enabled,months,limit,count}]}}。
     */
    public Map<String, Object> preview() {
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0L;
        for (Long tenantId : listInstanceTenants()) {
            int enabled = readConfigInt(tenantId, CFG_ENABLED, 0);
            int months = clampMonths(readConfigInt(tenantId, CFG_MONTHS, DEFAULT_MONTHS));
            int limit = Math.max(1, readConfigInt(tenantId, CFG_BATCH_LIMIT, DEFAULT_BATCH_LIMIT));
            long count = countCandidates(tenantId, months);
            total += count;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantId", tenantId);
            row.put("enabled", enabled);
            row.put("months", months);
            row.put("limit", limit);
            row.put("count", count);
            rows.add(row);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("maxMonths", MAX_MONTHS);
        resp.put("defaultMonths", DEFAULT_MONTHS);
        resp.put("terminalStatuses", TERMINAL_STATUSES);
        resp.put("enabledKey", CFG_ENABLED);
        resp.put("monthsKey", CFG_MONTHS);
        resp.put("batchLimitKey", CFG_BATCH_LIMIT);
        resp.put("total", total);
        resp.put("tenants", rows);
        return resp;
    }

    // ---------------- 内部实现 ----------------

    /** 单实例搬移事务：冷表插入（子→头）后删在线（子→头）。任一 SQL 异常整实例回滚。 */
    private void archiveOne(long tenantId, WfInstance inst) {
        final Long id = inst.getId();
        final String procInstId = inst.getProcInstId();
        // 回到该实例自己的租户上下文：在线表的 SELECT/DELETE 由租户拦截器自动加 tenant_id 条件
        TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            // ① 冷表插入：列序 = 在线表全列 + 末尾 archived_at（见 V2.2.0_g4_archive_20260924.sql）
            //    冷表未挂实体、不在租户拦截器管辖内，故手工带 tenant_id（同 ColdArchiveService 前例）
            jdbcTemplate.update("INSERT INTO wf_task_history_cold SELECT *, NOW() FROM wf_task_history"
                    + " WHERE tenant_id=? AND proc_inst_id=?", tenantId, procInstId);
            jdbcTemplate.update("INSERT INTO wf_task_cold SELECT *, NOW() FROM wf_task"
                    + " WHERE tenant_id=? AND proc_inst_id=?", tenantId, procInstId);
            jdbcTemplate.update("INSERT INTO wf_instance_cold SELECT *, NOW() FROM wf_instance"
                    + " WHERE tenant_id=? AND id=?", tenantId, id);

            // ② 删在线（子→头）；tenant_id 由租户拦截器注入，此处不手写
            wfTaskHistoryMapper.delete(new LambdaQueryWrapper<WfTaskHistory>()
                    .eq(WfTaskHistory::getProcInstId, procInstId));
            wfTaskMapper.delete(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getProcInstId, procInstId));
            wfInstanceMapper.deleteById(id);
            return null;
        }));
    }

    /** 候选实例（终态 + end_time 早于 cutoff）；回到该租户上下文，tenant_id 由拦截器注入，不手写 */
    private List<WfInstance> listCandidates(long tenantId, int months, int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        List<WfInstance> all = asTenant(tenantId, () -> wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .in(WfInstance::getStatus, TERMINAL_STATUSES)
                        .isNotNull(WfInstance::getEndTime)
                        .lt(WfInstance::getEndTime, cutoff)
                        .orderByAsc(WfInstance::getEndTime)
                        .last("LIMIT " + limit)));
        return all == null ? List.of() : all;
    }

    /** 候选实例计数（只读预览用；与 {@link #listCandidates} 同口径，不带 LIMIT） */
    private long countCandidates(long tenantId, int months) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        Long c = asTenant(tenantId, () -> wfInstanceMapper.selectCount(
                new LambdaQueryWrapper<WfInstance>()
                        .in(WfInstance::getStatus, TERMINAL_STATUSES)
                        .isNotNull(WfInstance::getEndTime)
                        .lt(WfInstance::getEndTime, cutoff)));
        return c == null ? 0L : c;
    }

    /** 存在流程实例的租户列表（跨租户扫描入口） */
    private List<Long> listInstanceTenants() {
        List<Long> ids = ignoreTenant(() -> jdbcTemplate.queryForList(
                "SELECT DISTINCT tenant_id FROM wf_instance ORDER BY tenant_id", Long.class));
        return ids == null ? List.of() : ids;
    }

    /** 待归档实例摘要（只读留证用，控制单条日志长度） */
    private String describe(List<WfInstance> list) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (WfInstance i : list) {
            if (shown++ >= 50) {
                sb.append("...(共 ").append(list.size()).append(" 条)");
                break;
            }
            if (shown > 1) {
                sb.append(", ");
            }
            sb.append(i.getProcInstId()).append('(').append(i.getBizType()).append('/').append(i.getStatus()).append(')');
        }
        return sb.toString();
    }

    private int clampMonths(int raw) {
        if (raw < 1) {
            return DEFAULT_MONTHS;
        }
        return Math.min(raw, MAX_MONTHS);
    }

    /**
     * 跨租户读取：{@code runIgnoreTenant} 只收 Runnable，用单元素数组把结果带出来
     */
    private <T> T ignoreTenant(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }

    /**
     * 回到指定租户上下文执行并取回结果（{@code callAs} 同样只收 Runnable，故用单元素数组带出）。
     * 这样在线表的 tenant_id 条件一律由租户拦截器注入，本类不手写租户过滤。
     */
    private <T> T asTenant(Long tenantId, Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.callAs(tenantId, () -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }

    /** 租户级 int 配置读取（SysConfigMapper + callAs 恢复上下文，参照 ColdArchiveService） */
    private int readConfigInt(long tenantId, String key, int def) {
        final int[] holder = {def};
        try {
            TenantContext.callAs(tenantId, () -> {
                SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key).last("limit 1"));
                if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                    try {
                        holder[0] = Integer.parseInt(c.getConfigValue().trim());
                    } catch (NumberFormatException ignored) {
                        // 保持默认
                    }
                }
            });
        } catch (Exception e) {
            log.warn("[流程归档] 读取配置失败 tenant={} key={}", tenantId, key, e);
        }
        return holder[0];
    }
}