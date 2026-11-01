// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AP月末关账（蓝图 SCC_AP_20）：发起（协同冻结）→组长派工→四检查项签认→关账完成恢复协同。
 * scpFrozen=1 期间禁止协同/付款通道发送数据产生凭证（打通点7）。
 */
@Data
@TableName("ap_month_close")
public class ApMonthClose implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String closeNo;
    private Long companyId;
    /** 关账期间 yyyy-MM */
    private String period;
    /** INIT/DISPATCHED/CHECKING/CLOSED */
    private String status;
    private String groupLeader;
    private String members;
    /** 检查项JSON（账务正确性/对冲平检/关联方核对/IT异常） */
    private String checkItemsJson;
    /** 1=关账期间协同冻结（SCP禁止发送数据至总账） */
    private Integer scpFrozen;
    private LocalDateTime closedTime;
    /** 关账操作人（关账完成/反关账记录） */
    private String closedBy;
    /** 反关账时间（非空=该单发生过反关账重开期间） */
    private LocalDateTime reopenTime;
    /** 反关账原因 */
    private String reopenReason;
    /** 全程操作记录JSON：[{action,oper,time,detail}] */
    private String traceJson;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_INIT = "INIT";
    public static final String ST_DISPATCHED = "DISPATCHED";
    public static final String ST_CHECKING = "CHECKING";
    public static final String ST_CLOSED = "CLOSED";
    /** 作废最终状态（记录不删行，解冻协同与期间锁的出口态） */
    public static final String ST_VOID = "VOID";
}
