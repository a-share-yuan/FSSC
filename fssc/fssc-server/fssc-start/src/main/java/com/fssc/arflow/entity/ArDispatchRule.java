package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AR 派工规则（蓝图 5.2：公司/岗位/组别三维 + 客户十位码前缀路由 + 选单池）。
 * 命中优先级：customer_prefix 最长前缀 → claim_type 精确 → priority。
 */
@Data
@TableName("ar_dispatch_rule")
public class ArDispatchRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String ruleName;
    /** 服务申请类型（10类之一），空=全部 */
    private String claimType;
    /** 客户十位码前缀，空=兜底 */
    private String customerPrefix;
    /** 负责人清单（逗号分隔，多人=选单池） */
    private String handlers;
    private String groupLeader;
    /** 1=按在办量最少自动直派；0=进选单池 */
    private Integer autoBalance;
    private Integer slaHours;
    private Integer priority;
    private Integer enabled;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
