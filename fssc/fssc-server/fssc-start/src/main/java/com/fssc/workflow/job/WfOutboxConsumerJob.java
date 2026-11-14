package com.fssc.workflow.job;

import com.fssc.framework.config.AsyncConfig;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.workflow.dto.WfProjectionEvent;
import com.fssc.workflow.entity.WfProcessOutbox;
import com.fssc.workflow.service.WfProcessOutboxService;
import com.fssc.workflow.service.WfProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程投影事件发件箱消费者（④层「流程处理模式＝消息队列」）。
 *
 * <p><b>它做什么</b>：把 {@code wf_process_outbox} 里 {@code PENDING} 的投影事件取出来，
 * 逐条交给 {@link WfProjectionService}（<b>与同步模式同一份投影实现</b>），成功后置 {@code CONSUMED}；
 * 失败按 2/4/8/16 秒退避重试，{@code MAX_ATTEMPTS}(5) 次仍失败转 {@code DEAD}
 * 并记缺陷（WF-MIRRO1）——死信绝不静默丢弃，它意味着某个镜像行永久缺失。
 *
 * <p><b>执行顺序（方案 §四 4.3）</b>：按 {@code id ASC} 领取、<b>单线程串行消费</b>
 * （{@link AsyncConfig#wfOutboxExecutor} 为 core=max=1），因此"同一实例的事件"
 * 天然按投递顺序投影，不会出现 {@code TASK_ASSIGNED} 先于 {@code TASK_CREATED} 写入数据库的倒挂。
 *
 * <p><b>为什么不用 {@code applicationTaskExecutor}</b>：那是 Flowable 引擎异步作业的执行器，
 * 共用会让"投影消费"与"引擎推进"互相抢线程（见 {@code AsyncConfig.wfOutboxExecutor} 注释）。
 *
 * <p><b>失败不崩</b>：整轮包在 {@link JobHeartbeatService#run} 里（与 {@code WfTimeoutJob} 同一范式），
 * 心跳表可查最近执行时间/耗时/连续失败；单条事件失败不影响同批其余事件。
 *
 * <p><b>零影响（SYNC 模式）</b>：每轮第一件事是 {@link WfProcessOutboxService#peekQueueMode()}——
 * <b>纯内存读、零查询</b>，非 QUEUE 立即返回：不领表、不起线程、不写任何行，与原先逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfOutboxConsumerJob {

    /** 心跳名（op_job_heartbeat.job_name） */
    public static final String JOB_NAME = "WfOutboxConsumerJob";

    /** 单轮领取上限：一次不要吞掉整天的积压，让日志/观测能跟上 */
    private static final int BATCH_LIMIT = 200;

    private final WfProcessOutboxService outboxService;
    private final WfProjectionService projectionService;
    private final JobHeartbeatService jobHeartbeatService;

    /**
     * 消费专用执行器（core=max=1）。
     *
     * <p>用字段注入而非 Lombok 构造器注入：容器里有 3 个 {@code ThreadPoolTaskExecutor}，
     * 必须按名字限定；而 {@code @Qualifier} 在 Lombok 生成的构造器参数上不生效（项目未开
     * {@code lombok.copyableAnnotations}），故沿用本项目既有做法（参见 {@code WfEngineFacade.budgetService}）。
     */
    @Autowired
    @Qualifier(AsyncConfig.WF_OUTBOX_EXECUTOR)
    private ThreadPoolTaskExecutor wfOutboxExecutor;

    /** 在途互斥：同一时刻只允许一批在跑，避免调度抖动导致多批并发（会破坏"串行投影"前提） */
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    /**
     * 每 5 秒一趟：先判模式（零查询），再在专用线程池上跑一批。
     *
     * <p>为什么提交到线程池而不是直接在调度线程跑：{@code @Scheduled} 的调度线程是全应用共用的，
     * 一批事件涉及多次库写与可能的退避重试，堵住调度线程会连累其它定时任务。
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 20000)
    public void tick() {
        if (!outboxService.peekQueueMode()) {
            return; // SYNC（缺省）：纯内存判定，零查询、零写入、不起线程
        }
        if (!inFlight.compareAndSet(false, true)) {
            return; // 上一批还在跑：本轮跳过（下一轮 5 秒后再来）
        }
        try {
            wfOutboxExecutor.execute(() -> {
                try {
                    jobHeartbeatService.run(JOB_NAME, this::consumeOnce);
                } catch (Exception ex) {
                    // 心跳包装已吞异常，这里只兜"提交后线程内意外"，不得让线程池静默丢任务
                    log.error("{} 消费线程异常", JOB_NAME, ex);
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (Exception ex) {
            // 线程池拒绝（容量 4 已被占满）：本轮放弃，下一轮自然重来
            inFlight.set(false);
            log.warn("{} 提交消费任务被拒（线程池繁忙），本轮跳过: {}", JOB_NAME, ex.getMessage());
        }
    }

    /**
     * 消费一批（可单独调用，便于验证与手工补偿）。
     *
     * @return 本批成功投影条数
     */
    public int consumeOnce() {
        List<WfProcessOutbox> rows = outboxService.claimBatch(BATCH_LIMIT);
        if (rows.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (WfProcessOutbox row : rows) {
            WfProjectionEvent event = outboxService.parse(row);
            if (event == null) {
                // payload 反序列化失败＝投递物损坏，重试也不会好，直接死信（已记缺陷）
                outboxService.markDead(row, "payload 反序列化失败（事件信封损坏）");
                continue;
            }
            try {
                // 租户上下文由 WfProjectionService.apply 依信封里的 tenantId 重建
                projectionService.apply(event);
                outboxService.markConsumed(row);
                ok++;
            } catch (Exception ex) {
                String msg = errMsg(ex);
                int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
                if (attempts >= WfProcessOutboxService.MAX_ATTEMPTS) {
                    outboxService.markDead(row, msg);
                    log.error("{} 投影事件消费失败并转死信 id={} type={} proc={} attempts={}",
                            JOB_NAME, row.getId(), row.getEventType(), row.getProcInstId(), attempts, ex);
                } else {
                    outboxService.markRetry(row, msg);
                    log.warn("{} 投影事件消费失败，退避重试 id={} type={} proc={} attempts={}: {}",
                            JOB_NAME, row.getId(), row.getEventType(), row.getProcInstId(), attempts, msg);
                }
            }
        }
        // 观测（方案 §六 G5「六列台账」）：投递了几条、消费了几条、失败几条、最老积压多久
        Map<String, Object> st = outboxService.stats();
        log.info("{} 本批 领取={} 成功={} | 积压={} 已消费={} 死信={} 最老积压={}s 失败率={}",
                JOB_NAME, rows.size(), ok, st.get("pending"), st.get("consumed"), st.get("dead"),
                st.get("oldestPendingAgeSeconds"), st.get("failureRate"));
        return ok;
    }

    private static String errMsg(Throwable ex) {
        String m = ex.getMessage();
        return (m == null || m.isBlank()) ? ex.getClass().getName() : m;
    }
}