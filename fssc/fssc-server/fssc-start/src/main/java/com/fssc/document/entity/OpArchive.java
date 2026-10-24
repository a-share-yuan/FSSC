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

    // ========== 合并自 archive_catalog（档案目录） ==========

    /** 档案编码(冗余) */
    private String archiveCode;
    /** BILL单据/VOUCHER凭证/CONTRACT合同/REPORT报表/OTHER */
    private String archiveType;

    /** 档案年度 */
    private Integer archiveYear;

    /** 档案期间 */
    private String archivePeriod;
    /** 关联主键（单据ID/凭证ID） */
    private Long refId;

    /** 单据ID */
    private Long billId;

    /** D-TRC-05：来源单据的审批流程实例ID，使档案可反查"是哪次审批产生的" */
    private String procInstId;

    /**
     * D-DEC-028（证据 12-09A:57）：<b>档案自身</b>那条归档审批流（FM_ARCHIVE，bizId＝op_archive.id）
     * 的流程实例号。上一列存的是<b>被归档那张单</b>的实例号（D-TRC-05 在册口径，
     * {@code BillTraceChainController} 的「单据流 → 档案」正向联查就靠它，不能改），
     * 于是「由档案反查归档审批链」在表上没有任何键可用——档案 11 的 proc_inst_id 是报销单
     * CLAIM 实例，真实归档实例是另一条（同族 D-DEC-240 已在联查侧打过补丁）。
     *
     * <p>本列＝读侧富化（与 {@code OpPayBatch.payProcInstId}／D-DEC-114① 完全同法）：
     * 由 {@code ArchiveService.fillArchiveFlowKey} 按 {@code wf_instance(biz_type='FM_ARCHIVE',
     * biz_id=档案ID)} 最新一条填出，<b>零 DDL、零运行时副作用</b>，前端/接口拿到的档案行上
     * 从此有两个不同语义的实例号。
     *
     * <pre>
     * --  需人工裁决：以下是把该键<b>持久化</b>的注释态 DDL，本轮未执行、也不得由缺陷修复批次执行。
     * -- 落库后本字段去掉 @TableField(exist = false) 改 @TableField("archive_proc_inst_id")，
     * -- 并在 ArchiveService.archive() 起流处用 wfEngineFacade.start(...) 的返回值（就是新实例号）写入。
     * -- ALTER TABLE op_archive
     * --   ADD COLUMN archive_proc_inst_id VARCHAR(64) NULL COMMENT '归档审批流(FM_ARCHIVE)自身实例ID（D-DEC-028）' AFTER proc_inst_id,
     * --   ADD COLUMN archive_flow_status   VARCHAR(20) NULL COMMENT '归档审批流状态 RUNNING/APPROVED/REJECTED/CANCELLED' AFTER archive_proc_inst_id;
     * -- 存量回灌（数据轨，与上面的裁决一起做；不回灌则老档案行仍只能靠读侧富化）：
     * -- UPDATE op_archive a JOIN wf_instance w ON w.biz_type = 'FM_ARCHIVE' AND w.biz_id = a.id
     * --    SET a.archive_proc_inst_id = w.proc_inst_id, a.archive_flow_status = w.status
     * --  WHERE a.archive_proc_inst_id IS NULL;   -- 同一档案多轮归档实例时取 id 最大的一条（读侧同口径）
     * </pre>
     */
    @TableField(exist = false)
    private String archiveProcInstId;

    /** D-DEC-028：归档审批流自身状态（读侧富化，同上；未发起归档流则为空） */
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
     * 原件保管单位（V1.4.92，AMC-05）：档案非集中管理时纸质原件的保管法人公司 id；
     * 集中管理（原件已寄送共享中心）为空。
     */
    private Long paperKeeperCompanyId;

    /** 原件存放位置（V1.4.92，AMC-05）：非集中模式下的具体存放地点，集中模式为空 */
    private String paperLocation;

    /** 到期销毁日期 */
    private LocalDate destroyDate;

    /** 完整性哈希（D-DEC-180：唯一算式＝{@code ArchiveIntegrityService.snapshot}，
     *  输入＝档案身份(id/编号/编码/类型)＋业务键(ref/bill/voucher/ref_no/年度/期间)＋载体与附件清单；
     *  时间列、状态列、归属列一律不入算。历史行为旧算式产物，重算前判 MISMATCH 属预期，存量回灌属数据轨。） */
    private String integrityHash;

    /** 完整性状态：VERIFIED/MISMATCH/UNKNOWN */
    private String integrityStatus;

    /**
     * 归档状态（流程轴）：PENDING/ARCHIVING/ARCHIVED/DISPOSED。
     *
     * <p><b>D-DEC-182（12-31:160）定列：</b>本列＝<b>归档结论的唯一真相源</b>。
     * 参与业务判定的读点全部按它——档案列表/检索筛选（{@code ArchiveService.page/search}）、
     * 统计（{@code getArchiveStats}）、到期预警（{@code ArchiveExpireJob}）、
     * 单据全链路联查（{@code BillTraceChainController}）、执行归档的状态机硬闸（{@code ArchiveService.archive}）。
     */
    private String archiveStatus;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 销毁时间 */
    private LocalDateTime destroyedAt;
    /**
     * 保管处置状态：FILED已归档在架/EXPIRED到期/DESTROYED已销毁。
     *
     * <p><b>D-DEC-182：本列是 {@code archiveStatus} 的冗余镜像，不承载业务判定</b>，
     * 取值一律由 {@code ArchiveService.dispositionOf(archiveStatus)} 派生后同源回写：
     * <pre>
     *   PENDING / ARCHIVING / ARCHIVED  →  FILED     （案卷已登记在架）
     *   DISPOSED                        →  DESTROYED （已销毁）
     *   EXPIRED                         →  预留，现无写入方（到期预警只发站内信不改列）
     * </pre>
     *
     * <p><b>缺陷原状（为什么会"两列互斥"）</b>：写入点各写一列——
     * {@code BillArchiveService.doArchive} 只写 {@code status='ARCHIVED'} 而从不写本域默认 PENDING 的
     * {@code archive_status}（实测 id=21/22/23 与现库 id=4/10 都是「archive_status=PENDING ＋ status=ARCHIVED」）；
     * {@code ArchiveFacadeImpl.handleArchiveApproval} 又把两列都写成 'ARCHIVED'，
     * 而 'ARCHIVED' 根本不在本列词表里。现两处均改走 {@code dispositionOf}，
     * 唯一在册例外＝FM_DESTROY 审批通过时先置本列 DESTROYED（归档轴待「执行销毁」才 DISPOSED），
     * 判"已销毁"须两列取或（{@code ArchiveDestroyService.assertAllExpired}）。
     *
     * <p>列不删、词表不改（DDL 需人工裁决）；历史行的旧值不回写（演练数据保留铁律），
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
}
