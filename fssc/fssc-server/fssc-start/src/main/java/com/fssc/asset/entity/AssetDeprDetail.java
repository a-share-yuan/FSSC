package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产折旧明细（md_asset_depr_detail，带 tenant_id）。
 * 逐资产逐月记录折旧明细，支撑资产台账、折旧查询、审计追溯。
 */
@Data
@TableName("md_asset_depr_detail")
public class AssetDeprDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 资产卡片ID */
    private Long assetId;
    /** 折旧批单ID */
    private Long batchId;
    /** 计提期间 YYYY-MM */
    private String period;
    /** 本月折旧额 */
    private BigDecimal monthlyDepr;
    /** 计提后累计折旧 */
    private BigDecimal accumDeprAfter;
    /** 计提后账面净值 */
    private BigDecimal netAmountAfter;
    /** 计提日期 */
    private LocalDate deprDate;
    /** 关联凭证ID */
    private Long sourceVoucherId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
