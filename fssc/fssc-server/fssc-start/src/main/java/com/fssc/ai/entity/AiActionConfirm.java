package com.fssc.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 高风险动作确认票据（ai_action_confirm）。
 *
 * <p>语义＝作者案例里"飞书确认卡片"的内网等价物：一次性、绑定参数摘要
 * 参数哪怕改一个字段，摘要即不符，票据作废、短 TTL、确认人必须是有权限的真人。
 * 申请人（AI 或代问的人）与确认人不得为同一账号。
 */
@Data
@TableName("ai_action_confirm")
public class AiActionConfirm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 关联的 AI 指令/会话标识 */
    private String instructionId;

    /** 目录动作码 */
    private String actionCode;
    private String actionUrl;
    private String actionMethod;

    /** SHA-256(规范化 JSON 参数)，执行时复核：参数变＝票据废 */
    private String paramsDigest;

    /**
     * 参数文本快照：{@code params_digest} 只防篡改，人手读不出哈希背后的参数，
     * 确认人等于"闭眼背书"。本列仅供人读（待确认队列回显），执行复核仍以 digest 为准；
     * 口令/令牌类字段落盘前打码。
     */
    private String argsText;

    /** 发起（请求）确认的人 */
    private Long applicantUserId;
    /** 实际点确认的真人；与申请人相同时按自审禁止处理 */
    private Long confirmerUserId;
    private String confirmerUsername;

    /** PENDING / CONFIRMED / REJECTED / USED / EXPIRED */
    private String status;

    private String reason;
    private LocalDateTime expireAt;
    private LocalDateTime confirmTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
