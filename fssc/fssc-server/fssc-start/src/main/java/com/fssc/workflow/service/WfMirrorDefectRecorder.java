// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.fssc.framework.log.OperLogWriter;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysOperLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 流程镜像写失败缺陷记录器（G0 止血，2026-09-24）。
 *
 * <p><b>背景（方案 §二 F1~F4）</b>：{@code wf_instance}/{@code wf_task}/{@code wf_task_history}
 * 是 Flowable 引擎之外的"第二本账"，写点分散在 {@code WfEngineFacade} 各处，且失败一律
 * {@code catch(Exception)} 吞掉、只留一行 log。结果是双账漂移长期不可见
 * （F13：{@code wf_task_history} 294 行 vs {@code act_hi_taskinst} 211 行）。
 *
 * <p>本组件是"失败必须冒泡"的可见物载体：镜像写失败时统一
 * <ol>
 *   <li>以固定错误码 + 场景 + 主键写 ERROR 日志；</li>
 *   <li>异步落一条 {@code sys_oper_log}（{@code status=1} 失败、{@code business_type=OTHER}、
 *       {@code error_msg} 带错误码与上下文），使缺陷在「系统日志」页可查、可被巡检脚本统计。</li>
 * </ol>
 *
 * <p>写库走 {@link OperLogWriter#writeSys}（{@code @Async} 且自带 try/catch）——审计写入绝不反过来
 * 阻断镜像失败的冒泡链路；即使业务事务回滚，这条缺陷记录也已落到独立线程的独立事务里，
 * 从而回答"当时到底哪一步没写进去"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfMirrorDefectRecorder {

    /** 镜像表写失败（wf_instance / wf_task / wf_task_history 之一） */
    public static final String CODE_MIRROR_WRITE_FAILED = "WF-MIRROR-001";
    /** 双账对账发现差异（引擎账 ACT_* vs 镜像账 wf_*），只报不修 */
    public static final String CODE_LEDGER_DRIFT = "WF-MIRROR-002";
    /** 引擎旁路推进（未过门面办结，F3） */
    public static final String CODE_ENGINE_BYPASS = "WF-MIRROR-003";

    /** {@code sys_oper_log.error_msg} 列宽 varchar(2000)，留出余量 */
    private static final int MAX_MSG_LEN = 1900;
    /** 缺陷记录固定落在该"虚拟模块"名下，便于日志页按标题过滤 */
    private static final String TITLE = "流程镜像写失败";

    private final OperLogWriter operLogWriter;

    /**
     * 记录一条镜像缺陷。
     *
     * @param code     错误码（见本类常量）
     * @param scene    场景/方法名，如 {@code markTaskDone}
     * @param tenantId 归属租户；为 null 时回退当前线程租户上下文（调度线程无上下文则只写日志）
     * @param detail   可定位的上下文（taskId / procInstId / 单据号等）
     * @param ex       原始异常，可为 null
     */
    public void record(String code, String scene, Long tenantId, String detail, Throwable ex) {
        String msg = buildMessage(code, scene, detail, ex);
        log.error("[{}] 流程镜像写失败 scene={} detail={}", code, scene, detail, ex);
        Long tid = resolveTenantId(tenantId);
        if (tid == null) {
            // 租户上下文缺失（调度线程 / 忽略租户聚合）：sys_oper_log.tenant_id NOT NULL，
            // 无法写入数据库；日志已记录，且对账作业会在下一轮扫描把它重新暴露出来。
            return;
        }
        SysOperLog row = new SysOperLog();
        row.setTenantId(tid);
        row.setTitle(TITLE);
        row.setBusinessType("OTHER");
        row.setMethod(scene);
        row.setStatus(1);
        row.setErrorMsg(truncate(msg));
        row.setCostTime(0L);
        row.setOperTime(LocalDateTime.now());
        try {
            operLogWriter.writeSys(row);
        } catch (Exception ignore) {
            // 审计写入失败不得影响缺陷冒泡
        }
    }

    private String buildMessage(String code, String scene, String detail, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(code).append(']').append(scene == null ? "" : " " + scene);
        if (detail != null && !detail.isBlank()) {
            sb.append(" | ").append(detail);
        }
        if (ex != null) {
            String m = ex.getMessage();
            sb.append(" | ").append(ex.getClass().getSimpleName());
            if (m != null && !m.isBlank()) {
                sb.append(": ").append(m);
            }
        }
        return sb.toString();
    }

    private Long resolveTenantId(Long explicit) {
        if (explicit != null) {
            return explicit;
        }
        if (!TenantContext.hasContext() || TenantContext.isIgnoreTenant()) {
            return null;
        }
        try {
            return TenantContext.getTenantId();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_MSG_LEN ? s : s.substring(0, MAX_MSG_LEN);
    }
}
