// SPDX-License-Identifier: AGPL-3.0-or-later
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 案卷卷内件清单（op_archive_item，带 tenant_id）。
 *
 * <p><b>为什么要有这张表</b>：归档整理的层级是「全宗 → 年度/机构/保管期限 → 案卷 → 件 → 页」，
 * "件"才是最小整理单位（DA/T 22—2015《归档文件整理规则》），而本系统原先把"卷"退化成了"件"——
 * {@code op_archive} 只有 {@code bill_id}/{@code ref_id}/{@code related_ids}/{@code file_id}
 * 四个单值列，立卷入口一次只接一张单、判重键又按来源单据，结果是一卷一件、卷内目录无从谈起。
 * 本表承载"一卷多件"，{@code op_archive} 那几列降级为<b>首件镜像</b>（不要删列：历史卷与旧读数都在引用）。
 *
 * <p><b>两条不变式</b>（由 {@code ArchiveService} 单点把守，不在界面层重复实现）：
 * <ul>
 *   <li>{@code (tenant_id, archive_id, seq_no)} 唯一 ⇒ 件号连续、不重号；</li>
 *   <li>同一 {@code bill_id} 不得同时出现在两卷的在册/在途件清单里 ⇒ 防"每点一次多落一卷"
 *       的原意，但允许"先建卷→分批加件→再执行归档"。</li>
 * </ul>
 */
@Data
@TableName("op_archive_item")
public class OpArchiveItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 所属案卷 op_archive.id */
    private Long archiveId;

    /** 卷内件号，自 1 连续 */
    private Integer seqNo;

    /** 来源单据类型（费用域固定 EXP_BILL；他域按 bizType 解释） */
    private String billType;

    /** 来源单据主键 */
    private Long billId;

    /** 来源单据编号（冗余，卷内目录直显，不要再拿主键冒充单号） */
    private String billNo;

    /** 来源凭证号（冗余） */
    private String voucherNo;

    /** 件日期 */
    private LocalDate itemDate;

    /** 件题名（默认取单据标题） */
    private String title;

    /** 起页（纸质卷必填） */
    private Integer pageFrom;

    /** 页数（电子卷以文件数替代） */
    private Integer pageCount;

    /** 该件的文件ID清单，逗号串，顺序即卷内文件顺序 */
    private String fileIds;

    /** 件级快照哈希 */
    private String contentHash;

    /** 件金额（卷合计与审批核对用） */
    private BigDecimal amount;

    /** 件所属公司；不要跨公司混卷 */
    private Long companyId;

    private String remark;

    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer delFlag;

    /** 只读补充：本件的文件个数（卷内目录列「文件数」，不落库） */
    @TableField(exist = false)
    private Integer fileCount;

    /** 只读补充：本卷的归档状态（卷内目录展开时直显，不落库） */
    @TableField(exist = false)
    private String archiveStatus;
}
