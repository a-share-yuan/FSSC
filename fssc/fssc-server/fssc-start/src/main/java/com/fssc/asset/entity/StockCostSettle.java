package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 存货成本结转批次（stock_cost_settle，带 tenant_id）。
 * 月度成本结转：领用成本/处置成本/跌价准备。
 */
@Data
@TableName("stock_cost_settle")
public class StockCostSettle implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String settleNo;
    private String period;
    /** ISSUE_COST领用成本/DISPOSAL_COST处置成本/DEVAL_PROV跌价准备 */
    private String settleType;
    private BigDecimal totalAmount;
    private Integer itemCount;
    /** DRAFT/PENDING/APPROVED/DONE/REJECTED */
    private String status;
    private String resultJson;
    private Long sourceVoucherId;
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
