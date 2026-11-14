package com.fssc.apflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** AP派工规则（蓝图）：按付款方法派工；多负责人=选单池；SLA/组长字段支撑转审升级。 */
@Data
@TableName("ap_dispatch_rule")
public class ApDispatchRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String ruleName;
    /** 单据类型（空=全部） */
    private String reqType;
    /** 付款方法（派工维度） */
    private String paymentWay;
    /** 负责人清单（逗号分隔，多人=选单池） */
    private String handlers;
    /** AP组长（修改付款方式转审） */
    private String groupLeader;
    private String companyCode;
    /** SLA时限（小时） */
    private Integer slaHours;
    private Integer enabled;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 只读显示名（与 handlers/groupLeader 逐位对齐，逗号分隔）：派工跨法人而人员分页按登录人公司办结，故读取时按 user_name 回查补齐；只覆盖已写进规则的工号，不借此放开人员目录，也不写入数据库；查不到时回显工号。 */
    @TableField(exist = false)
    private String handlerNames;

    /** 只读显示名（对应 groupLeader），规则同 handlerNames。 */
    @TableField(exist = false)
    private String groupLeaderName;

    /** 编辑凭据（只读下发、保存原样回传）＝六个可编辑字段的 SHA-256，用于并发覆盖检测；不要写入数据库。 */
    @TableField(exist = false)
    private String editFingerprint;
}
