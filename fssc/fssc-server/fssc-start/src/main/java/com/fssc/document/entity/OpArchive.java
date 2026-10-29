// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电子会计档案（op_archive，带 tenant_id）。
 * 无 create_by/update_by，按真实列独立声明。
 */
@Data
@TableName("op_archive")
public class OpArchive implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 档案编号（租户内唯一） */
    private String archiveNo;

    // 合并自 archive_catalog（档案目录）

    /** 档案编码(冗余) */
    private String archiveCode;
    /** BILL单据/VOUCHER凭证/CONTRACT合同/REPORT报表/OTHER */
    private String archiveType;

    /**
     * 载体类型：PAPER 纸质（原件保管，归档前必须逐件编页）/ ELECTRONIC 电子（按件计数，不强制编页）。
     *
     * <p>新增列。此前 {@code op_archive} 没有任何载体字段，{@code preArchiveFailures}
     * 只能做「编了就必须编全、从第 1 页连续」的一致性校验，分不清纸质与电子 ⇒ DA/T 22 的
     * 「纸质卷必须编页」无法只对纸质卷强制。列默认 {@code PAPER}（严格侧）：历史 5 卷原件均为纸质，
     * 若默认电子等于把存量纸质卷静默放过，闸门先自废一半；实为电子的卷逐卷订正。
     */
    private String carrierType;

    /** 档案年度 */
    private Integer archiveYear;

    /** 档案期间 */
    private String archivePeriod;
    /** 关联主键（单据ID/凭证ID） */
    private Long refId;

    /** 单据ID */
    private Long billId;

    /**
     * UX-OUT-11（读侧补充，与 {@link #archiveProcInstId} 同法＝零 DDL）：来源单据编号。
     *
     * <p>本列此前不存在，而档案列表/选择器出参是裸实体 ⇒ 前端「关联单据」列没有可绑的编号，
     * 只能回落到主键（实测渲染成 {@code 单据#null}／{@code 单据#700000562}）。单号其实一直在
     * {@code ref_no} 里（人工立卷走 {@code ArchiveService.fillCatalogFromSourceBill}、
     * 自动腿走 {@code BillArchiveService}，三条腿同源），只是列读错了字段。
     * 现由 {@code ArchiveService.fillSourceBillNo} 按单据载体类型回填，非单据载体保持 NULL
     * （合同/供应商编号冒充单据编号＝第二个语义陷阱）。
     */
    @TableField(exist = false)
    private String billNo;

    /** 来源单据的审批流程实例ID，使档案可反查"是哪次审批产生的" */
    private String procInstId;

    /**
     * 证据 12-09A:57：<b>档案自身</b>那条归档审批流（FM_ARCHIVE，bizId＝op_archive.id）
     * 的流程实例号。上一列存的是<b>被归档那张单</b>的实例号（现有规则，
     * {@code BillTraceChainController} 的「单据流 → 档案」正向联查就靠它，不能改），
     * 于是「由档案反查归档审批链」在表上没有任何键可用——档案 11 的 proc_inst_id 是报销单
     * CLAIM 实例，真实归档实例是另一条（同族 已在联查侧打过补丁）。
     *
     * <p>本列＝读侧补充（与 {@code OpPayBatch.payProcInstId}／① 完全同法）：
     * 由 {@code ArchiveService.fillArchiveFlowKey} 按 {@code wf_instance(biz_type='FM_ARCHIVE',
     * biz_id=档案ID)} 最新一条填出，<b>零 DDL、零运行时副作用</b>，前端/接口拿到的档案行上
     * 从此有两个不同语义的实例号。
     *
     * <pre>
     * -- 需人工裁决：以下是把该键<b>持久化</b>的注释态 DDL，本轮未执行、也不得由缺陷修复批次执行。
     * -- 写入数据库后本字段去掉 @TableField(exist = false) 改 @TableField("archive_proc_inst_id")，
     * -- 并在 ArchiveService.archive() 起流处用 wfEngineFacade.start(...) 的返回值（就是新实例号）写入。
     * -- ALTER TABLE op_archive
     * ADD COLUMN archive_proc_inst_id VARCHAR(64) NULL COMMENT '归档审批流(FM_ARCHIVE)自身实例ID' AFTER proc_inst_id,
     * ADD COLUMN archive_flow_status VARCHAR(20) NULL COMMENT '归档审批流状态 RUNNING/APPROVED/REJECTED/CANCELLED' AFTER archive_proc_inst_id;
     * -- 历史回灌（数据轨，与上面的裁决一起做；不回灌则老档案行仍只能靠读侧补充）：
     * -- UPDATE op_archive a JOIN wf_instance w ON w.biz_type = 'FM_ARCHIVE' AND w.biz_id = a.id
     * SET a.archive_proc_inst_id = w.proc_inst_id, a.archive_flow_status = w.status
     * WHERE a.archive_proc_inst_id IS NULL; -- 同一档案多轮归档实例时取 id 最大的一条（读侧同一规则）
     * </pre>
     */
    @TableField(exist = false)
    private String archiveProcInstId;

    /** 归档审批流自身状态（读侧补充，同上；未发起归档流则为空） */
    @TableField(exist = false)
    private String archiveFlowStatus;

    /** 凭证ID */
    private Long voucherId;

    /** 关联ID列表JSON */
    private String relatedIds;
    private String refNo;
    private String title;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    /** 归档文件ID（sys_file） */
    private Long fileId;
    private Long filingUserId;
    private LocalDateTime filingTime;
    /** 保管年限 */
    private Integer retentionYears;

    /**
     * 原件保管单位（AMC-05）：档案非集中管理时纸质原件的保管法人公司 id；
     * 集中管理（原件已寄送共享中心）为空。
     */
    private Long paperKeeperCompanyId;

    /** 原件存放位置（AMC-05）：非集中模式下的具体存放地点，集中模式为空 */
    private String paperLocation;

    /** 到期销毁日期 */
    private LocalDate destroyDate;

    /** 完整性哈希（唯一算式＝{@code ArchiveIntegrityService.snapshot}，
     *  输入＝档案身份(id/编号/编码/类型)＋业务键(ref/bill/voucher/ref_no/年度/期间)＋载体与附件清单；
     *  时间列、状态列、归属列一律不入算。历史行为旧算式产物，重算前判 MISMATCH 属预期，历史回灌属数据轨。） */
    private String integrityHash;

    /** 完整性状态：VERIFIED/MISMATCH/UNKNOWN */
    private String integrityStatus;

    /**
     * 归档状态（流程轴）：PENDING/ARCHIVING/ARCHIVED/DISPOSED。
     *
     * <p><b>（160）定列：</b>本列＝<b>归档结论的唯一真相源</b>。
     * 参与业务判定的读点全部按它——档案列表/检索筛选（{@code ArchiveService.page/search}）、
     * 统计（{@code getArchiveStats}）、到期预警（{@code ArchiveExpireJob}）、
     * 单据全链路联查（{@code BillTraceChainController}）、执行归档的状态机强制校验（{@code ArchiveService.archive}）。
     */
    private String archiveStatus;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 销毁时间 */
    private LocalDateTime destroyedAt;
    /**
     * 保管处置状态：FILED已归档在架/EXPIRED到期/DESTROYED已销毁。
     *
     * <p><b>本列是 {@code archiveStatus} 的冗余镜像，不承载业务判定</b>，
     * 取值一律由 {@code ArchiveService.dispositionOf(archiveStatus)} 派生后同源回写：
     * <pre>
     *   PENDING / ARCHIVING / ARCHIVED → FILED （案卷已登记在架）
     *   DISPOSED → DESTROYED （已销毁）
     *   EXPIRED → 预留，现无写入方（到期预警只发站内信不改列）
     * </pre>
     *
     * <p><b>缺陷原状（为什么会"两列互斥"）</b>：写入位置各写一列——
     * {@code BillArchiveService.doArchive} 只写 {@code status='ARCHIVED'} 而从不写本域默认 PENDING 的
     * {@code archive_status}（实测 id=21/22/23 与现库 id=4/10 都是「archive_status=PENDING ＋ status=ARCHIVED」）；
     * {@code ArchiveFacadeImpl.handleArchiveApproval} 又把两列都写成 'ARCHIVED'，
     * 而 'ARCHIVED' 根本不在本列名称表里。现两处均改走 {@code dispositionOf}，
     * 唯一现有例外＝FM_DESTROY 审批通过时先置本列 DESTROYED（归档轴待「执行销毁」才 DISPOSED），
     * 判"已销毁"须两列取或（{@code ArchiveDestroyService.assertAllExpired}）。
     *
     * <p>列不删、名称表不改（DDL 需人工裁决）；历史行的旧值不回写（演练数据保留铁律），
     * 只在后续正常写入时按映射自然收敛。
     */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /**
     * 卷内件数（读侧补充，零 DDL，与 {@code OpArchive#billNo}／{@code archiveProcInstId} 同法）：
     * 由 {@code ArchiveService.fillItemCount} 在分页/列表端点上<b>一次批量</b>按卷聚合出来。
     * 界面据此把"0 件的卷"标出来并禁用【执行归档】——后端入口闸也会拦，但不要让人撞了才知道。
     */
    @TableField(exist = false)
    private Integer itemCount;
}
