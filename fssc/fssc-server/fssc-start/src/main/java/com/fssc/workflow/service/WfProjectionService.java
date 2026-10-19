package com.fssc.workflow.service;

import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.dto.WfProjectionEvent;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 流程镜像投影的<b>唯一实现</b>（G1 单一真值 + ④层消息队列，2026-09-24）。
 *
 * <p><b>为什么必须抽出来</b>：改造前投影逻辑长在 {@code WfMirrorProjectionListener} 里，
 * 一旦引入"消息队列模式"，同一套投影就出现了第二条执行路径（同步内联 / 异步消费）——
 * 两条路径各写一份子逻辑＝双账漂移迟早重现。故投影动作<b>只此一份</b>：
 * <ul>
 *   <li>同步模式：{@code WfMirrorProjectionListener} 构造 {@link WfProjectionEvent} 后<b>内联调用</b>本类；</li>
 *   <li>消息队列模式：监听器把同一信封投进 {@code wf_process_outbox}，由 {@code WfOutboxConsumerJob}
 *       取出后调用本类<b>同一方法</b>。</li>
 * </ul>
 *
 * <p><b>入参是数据信封而非 Flowable 实体</b>：候选组、到期时间、流程变量回退这些"只有事件当场才解析得准"
 * 的东西，已由监听器解好装进 {@link WfProjectionEvent}。本类<b>只做纯写库</b>，因此异步消费时
 * 无需再触碰引擎（引擎变量在实例结束后会被清理，那时再解析就晚了）。
 *
 * <p><b>无 {@code @Transactional}</b>：与改造前一致（{@code WfEngineFacade} 全类 {@code @Transactional}
 * 计数为 0，镜像写与引擎推进本就不在同一事务）。本类内置事务会改变"投影失败是否回滚引擎命令"的语义，
 * 属夹带变更，故不做。
 *
 * <p><b>失败语义</b>：写失败一律"记缺陷 + 冒泡"（G0 口径）——镜像缺行会造成幽灵待办等真实业务后果。
 * 唯一例外是"上下文不可恢复"（{@link WfProjectionEvent#getTenantId()} 为 null）：记缺陷后 {@code return}，
 * 绝不抛，否则该实例永远无法收尾（死循环）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProjectionService {

    private final WfQueryService wfQueryService;
    private final WfMirrorDefectRecorder mirrorDefectRecorder;

    /** 事件类型常量（与 {@code WfProjectionEvent.eventType} 同值，也与监听器订阅的引擎事件名对齐） */
    public static final String EV_PROCESS_STARTED = "PROCESS_STARTED";
    public static final String EV_TASK_CREATED = "TASK_CREATED";
    public static final String EV_TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String EV_TASK_COMPLETED = "TASK_COMPLETED";
    public static final String EV_TASK_DELETED = "TASK_DELETED";
    public static final String EV_PROCESS_COMPLETED = "PROCESS_COMPLETED";

    /**
     * 按事件信封投影镜像行（同步内联与异步消费<b>共用本入口</b>）。
     *
     * <p>调用前提：调用方已保证本方法是该事件<b>唯一</b>的投影执行者（不会同一事件被投影两次）——
     * 同步模式下由监听器的模式判定保证，队列模式下由 outbox 的 {@code status=PENDING→CONSUMED} 保证。
     */
    public void apply(WfProjectionEvent e) {
        if (e == null || e.getEventType() == null) {
            return;
        }
        Long tenantId = e.getTenantId();
        if (tenantId == null) {
            recordContextMissing(e.getEventType(), e.getProcInstId());
            return;
        }
        TenantContext.callAs(tenantId, () -> {
            switch (e.getEventType()) {
                case EV_PROCESS_STARTED -> onProcessStarted(e, tenantId);
                case EV_TASK_CREATED -> onTaskCreated(e, tenantId);
                case EV_TASK_ASSIGNED -> onTaskAssigned(e, tenantId);
                case EV_TASK_COMPLETED -> onTaskCompleted(e, tenantId);
                case EV_TASK_DELETED -> onTaskDeleted(e);
                case EV_PROCESS_COMPLETED -> onProcessCompleted(e);
                default -> log.debug("投影事件不在契约内，忽略: type={} proc={}", e.getEventType(), e.getProcInstId());
            }
        });
    }

    // ---------------- 六个投影动作（与改造前监听器逐个 handler 一一对应） ----------------

    /** PROCESS_STARTED → 建 wf_instance（RUNNING）。 */
    private void onProcessStarted(WfProjectionEvent e, Long tenantId) {
        if (e.getProcInstId() == null) {
            return;
        }
        // 幂等：开关切换窗口内门面可能已写过同一实例（instanceByProcInstId 判重）
        if (wfQueryService.instanceByProcInstId(e.getProcInstId()) != null) {
            return;
        }
        WfInstance inst = new WfInstance();
        inst.setTenantId(tenantId);
        inst.setProcInstId(e.getProcInstId());
        inst.setProcDefId(e.getProcDefId());
        inst.setModelKey(e.getModelKey());
        inst.setBizType(e.getBizType());
        inst.setBizId(e.getBizId());
        inst.setBizNo(e.getBizNo());
        inst.setTitle(e.getTitle());
        inst.setAmount(e.getAmount());
        inst.setStartUserId(e.getStartUserId());
        inst.setStartTime(LocalDateTime.now());
        inst.setStatus("RUNNING");
        wfQueryService.recordInstance(inst);
    }

    /** TASK_CREATED → 建 wf_task（PENDING）。 */
    private void onTaskCreated(WfProjectionEvent e, Long tenantId) {
        if (e.getTaskId() == null) {
            return;
        }
        // 幂等：uk_tenant_task_id 唯一键会拒绝重复插入，先判 PENDING 行是否存在
        if (wfQueryService.pendingTaskExists(e.getTaskId())) {
            return;
        }
        WfTask row = buildTaskRow(e, tenantId);
        if (row == null) {
            return; // 归属信息缺失（biz_type NOT NULL 无法回填）：buildTaskRow 内已记缺陷
        }
        wfQueryService.recordTask(row);
        refreshInstanceCurrent(e.getProcInstId(), e.getNodeName(),
                e.getAssigneeRaw() == null ? "" : e.getAssigneeRaw());
    }

    /** TASK_ASSIGNED → 更新 wf_task.assignee_id（含释放置空）。 */
    private void onTaskAssigned(WfProjectionEvent e, Long tenantId) {
        if (e.getTaskId() == null) {
            return;
        }
        Long assignee = e.getAssigneeId();
        if (assignee == null) {
            // MP 的 update(entity, wrapper) 忽略 null 字段，置空必须走显式 set null 的专用方法
            wfQueryService.clearTaskAssignee(e.getTaskId());
        } else {
            wfQueryService.updateTaskAssignee(e.getTaskId(), assignee);
        }
        refreshInstanceCurrent(e.getProcInstId(), null, assignee == null ? "" : String.valueOf(assignee));
    }

    /** TASK_COMPLETED → wf_task 置 DONE + 写 wf_task_history（APPROVE/REJECT）。 */
    private void onTaskCompleted(WfProjectionEvent e, Long tenantId) {
        if (e.getTaskId() == null) {
            return;
        }
        wfQueryService.markTaskDone(e.getTaskId(), e.getOperUserId());
        WfInstance inst = wfQueryService.instanceByProcInstId(e.getProcInstId());
        WfTaskHistory h = new WfTaskHistory();
        h.setTenantId(tenantId);
        h.setProcInstId(e.getProcInstId());
        h.setTaskId(e.getTaskId());
        h.setNodeKey(e.getNodeKey());
        h.setNodeName(e.getNodeName());
        h.setBizType(inst == null || inst.getBizType() == null ? "UNKNOWN" : inst.getBizType());
        h.setBizId(inst == null || inst.getBizId() == null ? 0L : inst.getBizId());
        h.setAction(Boolean.TRUE.equals(e.getApproved()) ? "APPROVE" : "REJECT");
        h.setOperUserId(e.getOperUserId());
        h.setOperUserName(e.getOperUserName());
        h.setComment(e.getComment());
        h.setOperTime(LocalDateTime.now());
        wfQueryService.recordHistory(h);
    }

    /** TASK_DELETED（实体删除）→ wf_task 置 CANCELLED + 节点级作废留痕（只动 PENDING 行，已 DONE 的不受影响）。 */
    private void onTaskDeleted(WfProjectionEvent e) {
        if (e.getTaskId() == null) {
            return;
        }
        // D-DEC-275：只翻状态不写留痕 ⇒ 追溯链上这一节凭空消失（判「尚未到达」）。
        // 引擎删除的因由不在投影契约里（信封无 deleteReason），故按"未办理即作废"如实记，
        // 具体死因由同实例的实例级行（CANCEL / WITHDRAW / REJECTED / APPROVED）对读。
        wfQueryService.markTaskCancelled(e.getTaskId(),
                "引擎任务已删除（实例终止、会签同节点兄弟清理或改道搬执行），本节任务未办理即作废");
    }

    /** PROCESS_COMPLETED → 更新 wf_instance 状态 + 关闭该实例 PENDING 任务并落作废留痕。 */
    private void onProcessCompleted(WfProjectionEvent e) {
        if (e.getProcInstId() == null) {
            return;
        }
        wfQueryService.updateInstanceStatus(e.getProcInstId(),
                Boolean.TRUE.equals(e.getApproved()) ? "APPROVED" : "REJECTED", LocalDateTime.now());
        // 幽灵待办收口：实例结束后会签/票签同节点兄弟任务被引擎删除，wf_task 的 PENDING 行须同步关闭
        // D-DEC-275：关闭动作配一行作废留痕，否则该节在审批链/追溯链上判「尚未到达」
        wfQueryService.closePendingTasksByInstance(e.getProcInstId(),
                Boolean.TRUE.equals(e.getApproved())
                        ? "流程已办结，同节点未办理的会签/票签任务自动作废"
                        : "流程已按驳回结束，未办理的在办任务作废");
    }

    // ---------------- 投影构造与工具 ----------------

    /**
     * 由事件信封构造 wf_task 投影行（字段口径与改造前监听器的 {@code buildTaskRow} 逐字一致，
     * 差异只在"变量/实例行回退已在监听器构造信封时完成"）。
     *
     * @return 构造好的行；实例归属（biz_type NOT NULL）无法回填时返回 {@code null}（已记缺陷）
     */
    private WfTask buildTaskRow(WfProjectionEvent e, Long tenantId) {
        if (e.getBizType() == null) {
            // biz_type 为 NOT NULL：既无变量又无实例行时无法归属，记缺陷跳过（与门面 recordTaskMirror 同判据）
            mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED,
                    "WfProjectionService.TASK_CREATED", tenantId,
                    "procInstId=" + e.getProcInstId() + " taskId=" + e.getTaskId(), null);
            return null;
        }
        WfTask row = new WfTask();
        row.setTenantId(tenantId);
        row.setTaskId(e.getTaskId());
        row.setProcInstId(e.getProcInstId());
        row.setModelKey(e.getModelKey());
        row.setNodeKey(e.getNodeKey());
        row.setNodeName(e.getNodeName());
        row.setBizType(e.getBizType());
        row.setBizId(e.getBizId());
        row.setBizNo(e.getBizNo());
        row.setTitle(e.getTitle());
        row.setAmount(e.getAmount());
        row.setStartUserId(e.getStartUserId());
        row.setCreateTime(toLocalDateTime(e.getTaskCreateTimeMillis()));
        row.setStatus("PENDING");
        row.setAssigneeId(e.getAssigneeId());
        if (e.getAssigneeId() == null) {
            row.setCandidateIds(e.getCandidateIds());
        }
        row.setDueTime(toLocalDateTime(e.getDueTimeMillis()));
        return row;
    }

    /**
     * 刷新实例视图的"当前节点/当前办理人"。
     *
     * <p>为什么由投影维护：门面对应的 {@code wfQueryService.updateInstance(...)} 写点在开关打开后被守卫跳过，
     * 若投影不接管，{@code current_node}/{@code current_assignee} 会永远为空（大屏"在谁手上"整列为空）。
     * 并联扇出时以最后一条任务为准（近似值，已知取舍）。
     */
    private void refreshInstanceCurrent(String procInstId, String nodeName, String assignee) {
        try {
            WfInstance cur = wfQueryService.instanceByProcInstId(procInstId);
            if (cur == null || !"RUNNING".equals(cur.getStatus())) {
                return;
            }
            WfInstance upd = new WfInstance();
            if (nodeName != null) {
                upd.setCurrentNode(nodeName);
            }
            // 置空串而非 null：MP 的 update(entity,wrapper) 忽略 null，用 null 清不掉旧办理人
            upd.setCurrentAssignee(assignee == null ? "" : assignee);
            wfQueryService.updateInstance(upd, procInstId);
        } catch (Exception ex) {
            log.warn("投影实例当前节点/办理人失败 procInstId={}: {}", procInstId, ex.getMessage());
        }
    }

    /** 不可恢复分支：记缺陷（WF-MIRROR-003）后返回，绝不冒泡成死循环。 */
    private void recordContextMissing(String eventType, String procInstId) {
        mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_ENGINE_BYPASS,
                "WfProjectionService." + eventType, null,
                "procInstId=" + procInstId + "（引擎已清理上下文，投影跳过）", null);
    }

    /** epoch 毫秒 → {@code LocalDateTime}；null 视为"未解析到"，交由列默认值/NULL 处理。 */
    private static LocalDateTime toLocalDateTime(Long millis) {
        return millis == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}