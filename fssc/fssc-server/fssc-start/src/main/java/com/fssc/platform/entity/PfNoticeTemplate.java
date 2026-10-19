// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板（pf_notice_template）。
 * 【平台表-无 tenant_id】渠道模板全局维护，租户发送消息时按 template_code 引用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_notice_template")
public class PfNoticeTemplate extends PlatformBaseEntity {

    /** 模板编码（全局唯一） */
    private String templateCode;
    private String templateName;
    /** INNER站内信/SMS短信/EMAIL邮件/WECOM企微/DINGTALK钉钉 */
    private String channel;
    /** 标题模板 */
    private String title;
    /** 内容模板，占位符 ${xxx} */
    private String content;
    /** 1启用 0禁用 */
    private Integer status;
    private String remark;
}
