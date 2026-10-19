// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票验真日志（inv_verify_log，带 tenant_id）。
 * 【审计表-不可删】无 del_flag/create_by，按真实列独立声明；Mapper 不提供 delete。
 */
@Data
@TableName("inv_verify_log")
public class InvVerifyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long invoiceId;
    /** TAX税局/THIRD第三方/MANUAL人工 */
    private String channel;
    /** 请求报文 */
    private String reqParam;
    /** 返回报文 */
    private String respContent;
    /** PASS通过/FAIL失败 */
    private String result;
    private String msg;
    private Long verifyUserId;

    /**
     * 验真时间。<b>不用 {@code FieldFill.INSERT}</b>（D-DEC-174）：
     * {@code FsscMetaObjectHandler.insertFill} 只认 createBy/updateBy/createTime/updateTime/delFlag/tenantId
     * 六个名字，本列名 {@code verifyTime} 不在其列 ⇒ 带填充标记的列会被 MyBatis-Plus 无条件写成
     * {@code #{verifyTime}}（即 NULL），连带跳过表上的 {@code DEFAULT CURRENT_TIMESTAMP}——
     * 现网 inv_verify_log 全部行 verify_time 为 NULL、页面「验真时间」恒空即此因。
     * 现由写侧显式赋值（{@code InvoiceEnhanceService.recordVerify}）；万一漏赋值，
     * 无填充标记的 NULL 字段会被 INSERT 跳过，仍由库默认值 CURRENT_TIMESTAMP 兜住。
     */
    private LocalDateTime verifyTime;

    /**
     * 发票号码：非表列（inv_verify_log 按真实列声明，无 invoice_no 列，加列属 DDL 裁决，R5-D-122）。
     * 「验真记录」列表按票号稽核用——查询侧按 invoiceId 批查 inv_invoice 回填，不落库。
     */
    @TableField(exist = false)
    private String invoiceNo;
}
