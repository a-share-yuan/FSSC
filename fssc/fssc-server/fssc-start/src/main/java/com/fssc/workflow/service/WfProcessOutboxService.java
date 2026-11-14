package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.workflow.dto.WfProjectionEvent;
import com.fssc.workflow.entity.WfProcessOutbox;
import com.fssc.workflow.mapper.WfProcessOutboxMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程投影事件发件箱（{@code wf_process_outbox}）——流程配置「流程处理模式」参数的
 * 「消息队列（推荐）」一档：<b>事务性发件箱 + 调度消费</b>（不引入外部中间件，方案 §四 4.2）。
 *
 * <p><b>三段职责</b>：
 * <ol>
 *   <li>{@link #queueModeEnabled()}：模式判定（{@code sys_config.wf.process.mode}，缺省 {@code SYNC}，fail-closed）；</li>
 *   <li>{@link #enqueue}：监听器在事件当场落一行（与业务同事务 ⇒ 不丢事件）；</li>
 *   <li>{@link #claimBatch}/{@link #markConsumed}/{@link #markRetry}/{@link #markDead}/{@link #stats}：
 *       消费侧的领取、成功、退避重试、死信与观测。</li>
 * </ol>
 *
 * <p><b>模式判定为什么要缓存 5 秒</b>：{@code queueModeEnabled()} 会被<b>每个流程事件</b>调用
 * 监听器用它决定"内联投影还是入队"，也要被门面 {@code mirrorByFacade()} 按动作调用。
 * 直读库会把这些热路径变成逐事件一次 {@code sys_config} 查询；而完全内存化又会把"参数"退化成
 * "需要重启的编译期开关"（该参数的语义是勾选即生效）。折中：<b>5 秒 TTL 缓存</b>——
 * 勾选后 5 秒内生效、免重启，且查询量摊薄到"每 5 秒最多一次"。fail-closed：读不到/异常一律 SYNC。
 *
 * <p><b>零影响</b>：{@code SYNC} 时 {@link #enqueue} 在入口即返回（先判模式，不碰库），
 * 消费作业在 {@code WfOutboxConsumerJob} 里也是先判模式再干活——本表一行不写、线程不启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessOutboxService {

    /** 流程处理模式开关（sys_config.config_key）：{@code SYNC} / {@code QUEUE} */
    public static final String CFG_KEY_MODE = "wf.process.mode";
    public static final String MODE_SYNC = "SYNC";
    public static final String MODE_QUEUE = "QUEUE";

    public static final String ST_PENDING = "PENDING";
    public static final String ST_CONSUMED = "CONSUMED";
    public static final String ST_DEAD = "DEAD";

    /** 消费失败退避重试上限：达到即转 DEAD 并记缺陷，等人工处置（不静默丢弃） */
    public static final int MAX_ATTEMPTS = 5;

    /** 模式缓存 TTL（毫秒）：勾选后 5 秒内生效，免重启 */
    private static final long MODE_TTL_MILLIS = 5000L;

    /** {@code last_error} 列宽 varchar(1000)，留出余量 */
    private static final int MAX_ERR_LEN = 990;

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final WfProcessOutboxMapper outboxMapper;
    private final SysConfigMapper sysConfigMapper;
    private final WfMirrorDefectRecorder mirrorDefectRecorder;

    /** 模式缓存（volatile 双字段：读线程多、写线程少，不加锁足够） */
    private volatile boolean queueModeCache;
    private volatile long queueModeReadAt;

    // 模式判定

    /**
     * 是否「消息队列」模式（fail-closed：缺行/非 QUEUE/读异常一律视为 SYNC）。
     */
    public boolean queueModeEnabled() {
        long now = System.currentTimeMillis();
        if (now - queueModeReadAt < MODE_TTL_MILLIS) {
            return queueModeCache;
        }
        boolean queue = MODE_QUEUE.equalsIgnoreCase(rawMode());
        queueModeCache = queue;
        queueModeReadAt = now;
        return queue;
    }

    /** 当前模式原值（供运维页/日志展示，不做缓存判定用） */
    public String currentMode() {
        return queueModeEnabled() ? MODE_QUEUE : MODE_SYNC;
    }

    /**
     * 只读缓存、<b>不触发刷新</b>（供 {@code @Scheduled} 消费作业的每轮判定）。
     *
     * <p><b>为什么要与 {@link #queueModeEnabled()} 分开——这是"默认行为零变化"的关键取舍</b>：
     * 方案 §三 的零变化验收门槛写的是"SYNC 时任何查询多一次也没有"。但模式又要"勾选即生效、免重启"，
     * 两者不可能同时做到字面成立（不查库就不知道参数变了）。这里的解法是把"刷新"只放在<b>业务路径</b>上：
     * <ul>
     *   <li>业务路径（监听器/门面/入队）用 {@link #queueModeEnabled()}——本来就在处理请求、库连接是热的，
     *       每 5 秒最多多一次 {@code sys_config} 单行点查；</li>
     *   <li>调度作业（每 5 秒一轮）用本方法——纯内存读，零查询。系统空闲时（无任何流程操作）
     *       整条链路一次库都不碰，与原先逐字一致。</li>
     * </ul>
     * 且不会漏判：模式一旦被切到 QUEUE，必然伴随流程操作（有人点审批/发起），业务路径随即刷新缓存，
     * 下一轮 tick 就能看到并开始消费；切换期间入队的事件一条不丢（它们已在表里）。
     *
     * <p>代价（不粉饰）：若系统在"切到 QUEUE 之后、且长时间无任何流程操作"的状态下，
     * 缓存会停留在旧值直到下一次业务路径刷新——但此时表里也不会有待消费事件，无实际影响。
     */
    public boolean peekQueueMode() {
        return queueModeCache;
    }

    private String rawMode() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, CFG_KEY_MODE)
                    .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return MODE_SYNC;
            }
            return c.getConfigValue().trim();
        } catch (Exception ex) {
            log.warn("读取 {} 异常，fail-closed 视为 {}: {}", CFG_KEY_MODE, MODE_SYNC, ex.getMessage());
            return MODE_SYNC;
        }
    }

    // 入队

    /**
     * 投递一条投影事件（在事件当场、与业务同事务调用）。
     *
     * <p><b>去重</b>：唯一键 {@code uk_wpo_idem} 拒绝重复投递——重复到达是"同一事件被投两次"，
     * 不是错误（引擎在重试/补偿时会重复发事件），故捕获 {@link DuplicateKeyException} 后静默返回。
     *
     * <p><b>失败语义</b>：唯一键冲突之外写失败＝事件丢失＝镜像缺行，必须可见——
     * 记缺陷（WF-MIRRO1）后<b>冒泡</b>，与 G0/G1 同一规则（让引擎命令失败、界面可见、可重试）。
     */
    public void enqueue(WfProjectionEvent e) {
        if (e == null || e.getEventType() == null) {
            return;
        }
        if (!queueModeEnabled()) {
            return; // SYNC 模式：一行不写（零影响）
        }
        WfProcessOutbox row = new WfProcessOutbox();
        row.setTenantId(e.getTenantId());
        row.setEventType(e.getEventType());
        row.setProcInstId(e.getProcInstId());
        row.setTaskId(e.getTaskId());
        row.setBizType(e.getBizType());
        row.setBizId(e.getBizId());
        row.setBizNo(e.getBizNo());
        row.setIdempotencyKey(idempotencyKey(e));
        row.setStatus(ST_PENDING);
        row.setAttempts(0);
        row.setPayload(toJson(e));
        TenantContext.callAs(e.getTenantId(), () -> {
            try {
                outboxMapper.insert(row);
            } catch (DuplicateKeyException dup) {
                // 同一事件重复投递：去重，不是错误
                log.debug("投影事件重复投递（幂等忽略）: key={}", row.getIdempotencyKey());
            } catch (Exception ex) {
                mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED,
                        "WfProcessOutboxService.enqueue", e.getTenantId(),
                        "type=" + e.getEventType() + " procInstId=" + e.getProcInstId()
                                + " taskId=" + e.getTaskId() + " key=" + row.getIdempotencyKey(), ex);
                throw ex instanceof RuntimeException re ? re
                        : new BizException(
                                ErrorCode.SYSTEM_ERROR, "投影事件入队失败: " + ex.getMessage());
            }
        });
    }

    /**
     * 唯一键：任务级事件按 taskId（改派再加办理人，使"改派到不同人"不被判重），
     * 流程级事件按 procInstId。
     */
    static String idempotencyKey(WfProjectionEvent e) {
        boolean taskLevel = e.getTaskId() != null && !e.getTaskId().isBlank();
        String key = e.getEventType() + ":" + (taskLevel ? e.getTaskId() : e.getProcInstId());
        if (taskLevel && WfProjectionService.EV_TASK_ASSIGNED.equals(e.getEventType())) {
            key = key + ":" + (e.getAssigneeId() == null ? "-" : e.getAssigneeId());
        }
        // idempotency_key 列宽 varchar(160)：Flowable 的 id 是 UUID（36 字符），理论最长远小于此，
        // 但仍按列宽硬截断，避免超长导致 INSERT 失败（唯一键截断后仍有意义：前缀已含事件类型与 taskId）
        return key.length() <= 160 ? key : key.substring(0, 160);
    }

    // 消费侧

    /**
     * 领取一批待消费事件（按 {@code id ASC}：id 为自增主键，同一事务内严格递增，
     * 即"同一实例的事件天然排在正确顺序"）。
     *
     * <p>跨租户扫描走 {@link TenantContext#runIgnoreTenant}（与 {@code WfTimeoutJob} 同一做法）：
     * 调度线程没有租户上下文，且必须看到所有租户的积压。单条消费时再 {@code callAs} 回它自己的租户。
     *
     * @param limit 单轮上限（防单轮把一整天的积压全捞出来，让观测与日志有机会跟上）
     */
    public List<WfProcessOutbox> claimBatch(int limit) {
        LocalDateTime now = LocalDateTime.now();
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = outboxMapper.selectList(
                new LambdaQueryWrapper<WfProcessOutbox>()
                        .eq(WfProcessOutbox::getStatus, ST_PENDING)
                        .and(w -> w.isNull(WfProcessOutbox::getNextRetryAt)
                                .or().le(WfProcessOutbox::getNextRetryAt, now))
                        .orderByAsc(WfProcessOutbox::getId)
                        .last("limit " + Math.max(limit, 1))));
        @SuppressWarnings("unchecked")
        List<WfProcessOutbox> rows = (List<WfProcessOutbox>) holder[0];
        return rows == null ? List.of() : rows;
    }

    /** 反序列化 {@code payload}；失败返回 null（调用方按"不可消费"记缺陷转 DEAD，不静默跳过）。 */
    public WfProjectionEvent parse(WfProcessOutbox row) {
        if (row == null || row.getPayload() == null || row.getPayload().isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(row.getPayload(), WfProjectionEvent.class);
        } catch (Exception ex) {
            log.warn("投影事件反序列化失败 id={} key={}: {}", row.getId(), row.getIdempotencyKey(), ex.getMessage());
            return null;
        }
    }

    /** 投影成功：置 CONSUMED 并记消费时刻与已尝试次数。 */
    public void markConsumed(WfProcessOutbox row) {
        int attempts = row.getAttempts() == null ? 0 : row.getAttempts() + 1;
        update(row.getId(), w -> w
                .set(WfProcessOutbox::getStatus, ST_CONSUMED)
                .set(WfProcessOutbox::getConsumedTime, LocalDateTime.now())
                .set(WfProcessOutbox::getNextRetryAt, null)
                .set(WfProcessOutbox::getLastError, null)
                .set(WfProcessOutbox::getAttempts, attempts));
    }

    /**
     * 消费失败但未到上限：留在 PENDING，记录退避时间与原因。
     *
     * <p>退避＝{@code 2^attempts} 秒（2/4/8/16s，上限 {@value #MAX_ATTEMPTS} 次），
     * 避免"引擎正好在忙/建索引"这类瞬时故障被连续重试放大。
     */
    public void markRetry(WfProcessOutbox row, String error) {
        int attempts = row.getAttempts() == null ? 0 : row.getAttempts() + 1;
        long backoffSeconds = (long) Math.pow(2, Math.min(attempts, 4));
        update(row.getId(), w -> w
                .set(WfProcessOutbox::getStatus, ST_PENDING)
                .set(WfProcessOutbox::getAttempts, attempts)
                .set(WfProcessOutbox::getNextRetryAt, LocalDateTime.now().plusSeconds(backoffSeconds))
                .set(WfProcessOutbox::getLastError, truncate(error)));
    }

    /**
     * 消费失败且已达上限：转 DEAD 并记缺陷（人工可见、可处置）。
     *
     * <p><b>为什么不静默丢弃</b>：死信＝该事件对应的镜像行永久缺失（幽灵待办/实例滞留的成因），
     * 必须落到 {@code sys_oper_log} 让 G0 对账与巡检脚本看得见。
     */
    public void markDead(WfProcessOutbox row, String error) {
        int attempts = row.getAttempts() == null ? 0 : row.getAttempts() + 1;
        update(row.getId(), w -> w
                .set(WfProcessOutbox::getStatus, ST_DEAD)
                .set(WfProcessOutbox::getNextRetryAt, null)
                .set(WfProcessOutbox::getAttempts, attempts)
                .set(WfProcessOutbox::getLastError, truncate(error)));
        mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED,
                "WfProcessOutboxService.consume", row.getTenantId(),
                "死信 id=" + row.getId() + " type=" + row.getEventType()
                        + " procInstId=" + row.getProcInstId() + " taskId=" + row.getTaskId()
                        + " attempts=" + attempts + " err=" + truncate(error), null);
    }

    private void update(Long id, Consumer<LambdaUpdateWrapper<WfProcessOutbox>> setter) {
        TenantContext.runIgnoreTenant(() -> {
            LambdaUpdateWrapper<WfProcessOutbox> w = new LambdaUpdateWrapper<>();
            w.eq(WfProcessOutbox::getId, id);
            setter.accept(w);
            outboxMapper.update(null, w);
        });
    }

    // 观测

    /**
     * 投递观测（方案 §六 G5「六列台账」规则）：积压深度 / 最老未消费年龄 / 失败率。
     *
     * <p>三项都是<b>从本表直接算出来的</b>（这正是选发件箱而非外部 MQ 的理由之一：
     * 队列状态就是一张能 SQL 查的表，不需要额外的 MQ 监控面板）。
     */
    public Map<String, Object> stats() {
        Object[] holder = new Object[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = queryStats());
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) holder[0];
        return r;
    }

    private Map<String, Object> queryStats() {
        long pending = countByStatus(ST_PENDING);
        long consumed = countByStatus(ST_CONSUMED);
        long dead = countByStatus(ST_DEAD);
        long oldestAgeSeconds = 0;
        List<WfProcessOutbox> oldest = outboxMapper.selectList(new LambdaQueryWrapper<WfProcessOutbox>()
                .eq(WfProcessOutbox::getStatus, ST_PENDING)
                .orderByAsc(WfProcessOutbox::getId)
                .last("limit 1"));
        if (!oldest.isEmpty() && oldest.get(0).getCreateTime() != null) {
            oldestAgeSeconds = Duration.between(oldest.get(0).getCreateTime(), LocalDateTime.now()).getSeconds();
        }
        long total = pending + consumed + dead;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mode", currentMode());
        r.put("pending", pending);
        r.put("consumed", consumed);
        r.put("dead", dead);
        r.put("total", total);
        r.put("oldestPendingAgeSeconds", Math.max(oldestAgeSeconds, 0));
        // 失败率＝死信/总量（死信是最终状态失败；PENDING 里含未开始的，不算失败）
        r.put("failureRate", total == 0 ? 0.0d
                : BigDecimal.valueOf(dead * 1.0d / total)
                        .setScale(4, RoundingMode.HALF_UP).doubleValue());
        return r;
    }

    private long countByStatus(String status) {
        Long n = outboxMapper.selectCount(new LambdaQueryWrapper<WfProcessOutbox>()
                .eq(WfProcessOutbox::getStatus, status));
        return n == null ? 0L : n;
    }

    // 工具

    private static String toJson(WfProjectionEvent e) {
        try {
            return JSON.writeValueAsString(e);
        } catch (Exception ex) {
            // 序列化失败＝投递物不可用，等同入队失败（由调用方记缺陷并冒泡），此处返回 null 会被下游拦下
            throw new BizException(
                    ErrorCode.SYSTEM_ERROR, "投影事件序列化失败: " + ex.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERR_LEN ? s : s.substring(0, MAX_ERR_LEN);
    }
}