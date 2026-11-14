package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板（sys_notify_template）：触发点 → 标题/正文/深链。
 *
 * <p>存在的理由：驳回一度是唯一「单据死了没人知道」的终态——档案到期、申报到期、发票异常、
 * 应收逾期都会通知，只有「你的报销被退了」不会。补通知时若把文案写死进 {@code handleRejected}，
 * 每加一个通知点（退件、质检退回、超期）都要改代码重新打包；改为一行模板一条触发点，
 * 业务可自行改文案与开关（即「消息模板 + 消息配置」的思路，文案不进代码）。
 *
 * <p>模板只做 {@code ${var}} 字符串替换，不引入表达式引擎：模板由管理员维护，
 * 支持表达式等于把 OGNL 入口开给配置面，收益远小于风险。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notify_template")
public class SysNotifyTemplate extends TenantAuditBase {

    /** 触发点编码，如 EXP_BILL_REJECTED；与 sys_notify_template.uk_tenant_trigger 对应 */
    private String triggerCode;
    /** 触发点名称（人读，便于配置页展示） */
    private String triggerName;
    /** 消息类型：NOTICE/APPROVAL/ALERT（落到 sys_message.msg_type） */
    private String msgType;
    /** 标题模板，支持 ${billNo} 等占位符 */
    private String titleTpl;
    /** 正文模板 */
    private String contentTpl;
    /** 跳转链接模板，空＝消息不可点击 */
    private String linkTpl;
    /** 1=启用 0=停用；停用等同「不通知」，不报错 */
    private Integer enabled;
    /** 1=内置模板（升级不覆盖业务改过的文案） */
    private Integer isBuiltin;
    private String remark;
}
