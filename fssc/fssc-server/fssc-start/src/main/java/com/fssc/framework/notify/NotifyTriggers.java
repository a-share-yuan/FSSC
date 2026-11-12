// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.notify;

/**
 * 通知触发点编码。
 *
 * <p>这里是「何时通知」的唯一真值：调用方只用常量挑触发点，发什么文案由
 * {@code sys_notify_template} 里对应的模板行决定。因此新增一个通知点＝
 * <b>本类加一个常量＋模板表插一行</b>，渲染（{@link NotifyTemplateService}）
 * 与发送（{@link NotifyService}）都不需要改。
 *
 * <p>编码是对外契约：模板行的 {@code trigger_code} 必须与本类常量逐字一致，
 * 上线后不要改常量值（改了等于把已配好的模板失联）。命名统一 {@code 业务域_动作} 大写。
 */
public final class NotifyTriggers {

    /** 报销单被驳回（逐级驳回与漏单驳回 THROW_BACK 共用一条，差异由 ${rejectType} 呈现） */
    public static final String EXP_BILL_REJECTED = "EXP_BILL_REJECTED";

    /** 报销单质检退回整改（{@code op_quality.result = 'BACK'}） */
    public static final String EXP_QUALITY_BACK = "EXP_QUALITY_BACK";

    private NotifyTriggers() {
    }
}
