// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月结（gl_month_close，带 tenant_id）。对指定期间试算平衡后发起 GL_MONTH_CLOSE 审批。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_month_close")
public class GlMonthClose extends TenantAuditBase {

    /** 月结单号 */
    private String closeNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** 结账公司 sys_company.id */
    private Long companyId;
    /** 借方发生额合计 */
    private BigDecimal debitTotal;
    /** 贷方发生额合计 */
    private BigDecimal creditTotal;
    /** 借贷差异 */
    private BigDecimal diff;
    /** 是否试算平衡 0否/1是 */
    private Boolean balanced;
    /** DRAFT/CLOSED（起发起态为 RUNNING，另有 APPROVED/REJECTED/UNCLOSED，见 GlMonthCloseService 常量） */
    private String status;
    /** 结转凭证号 */
    private String voucherNo;
    /** 执行结果说明 */
    private String resultMsg;

    // 月结记录回不到审批流：{@code gl_month_close} 表上<b>没有</b> proc_inst_id / approve_time / audit_time
    // 三列（按 information_schema.columns 只读复核仍然缺），于是月结单与 GL_MONTH_CLOSE 审批流之间
    // 没有任何键——月结记录点不进审批链、也答不出"谁在哪一刻批的"。
    // 处理办法沿用既有两处读侧补充（{@code OpPayBatch.payProcInstId}、
    // {@code OpArchive.archiveProcInstId}）：零 DDL、零写库副作用，由 {@code GlMonthCloseService.page()} 按
    // {@code wf_instance(biz_type='GL_MONTH_CLOSE', biz_id=月结单ID)} 与 {@code wf_task_history}
    // 末节点 APPROVE/AUTO_PASS 记录填出下面几列。持久化见类尾注释态 SQL（未执行）。

    /** 本单 GL_MONTH_CLOSE 审批流的 Flowable 实例号（读侧补充，非表列） */
    @TableField(exist = false)
    private String wfProcInstId;

    /** 审批流状态 RUNNING/APPROVED/REJECTED/CANCELLED/TERMINATED（读侧补充） */
    @TableField(exist = false)
    private String wfFlowStatus;

    /** 审批<b>办结</b>时刻＝{@code wf_instance.end_time}（在途为空；读侧补充） */
    @TableField(exist = false)
    private LocalDateTime approveTime;

    /** <b>末节点复核</b>时刻＝该实例最后一条 APPROVE/AUTO_PASS 记录的 oper_time（读侧补充） */
    @TableField(exist = false)
    private LocalDateTime auditTime;

    /** 末节点复核人 id（与 md_period.close_by 查询同一规则；读侧补充） */
    @TableField(exist = false)
    private Long auditUserId;

    /** 末节点复核人姓名（{@code wf_task_history.oper_user_name}；读侧补充） */
    @TableField(exist = false)
    private String auditUserName;

    /*
     * 需产品确认：以下是把上面三枚键<b>持久化</b>的注释态 DDL，没有执行过，
     * 也不得借修复之名代为执行。
     * 写入数据库后把实体上的 @TableField(exist = false) 改成真实列名，并在
     * GlMonthCloseService.close() 起流处用 wfEngineFacade.start(...) 的返回值写 proc_inst_id、
     * 在 applyApprovalOutcome() 定稿处写 approve_time/audit_time/audit_user_id（读侧补充保留，兼容历史行）。
     * -- ALTER TABLE gl_month_close
     * ADD COLUMN proc_inst_id VARCHAR(64) NULL COMMENT 'GL_MONTH_CLOSE 审批流实例ID',
     * ADD COLUMN approve_time DATETIME NULL COMMENT '审批办结时刻＝流程结束时刻',
     * ADD COLUMN audit_time DATETIME NULL COMMENT '末节点复核时刻＝最后一条 APPROVE/AUTO_PASS 记录',
     * ADD COLUMN audit_user_id BIGINT NULL COMMENT '末节点复核人',
     * ADD KEY idx_gl_mc_proc_inst_id (proc_inst_id);
     * -- 历史回灌（数据轨，与上面的判定一起做；历史行默认不回写＝演练数据保留铁律）：
     * -- UPDATE gl_month_close g JOIN wf_instance w ON w.biz_type='GL_MONTH_CLOSE' AND w.biz_id=g.id
     * SET g.proc_inst_id=w.proc_inst_id, g.approve_time=w.end_time WHERE g.proc_inst_id IS NULL;
     * -- UPDATE gl_month_close g JOIN wf_task_history h ON h.id=(SELECT MAX(h2.id) FROM wf_task_history h2
     * WHERE h2.proc_inst_id=g.proc_inst_id AND h2.action IN ('APPROVE','AUTO_PASS'))
     * SET g.audit_time=h.oper_time, g.audit_user_id=h.oper_user_id WHERE g.audit_time IS NULL;
     */
}