package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 资产类别（md_asset_category，带 tenant_id）。
 * parentId 支持父级类别；depreciationMonths 默认折旧月数；residualRate 默认残值率（0.05=5%）。
 */
@Data
@TableName("md_asset_category")
public class MdAssetCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 类别编码 */
    private String categoryCode;
    /** 类别名称 */
    private String categoryName;
    /** 父类别ID，顶级为 0 */
    private Long parentId;
    /** 默认折旧月数 */
    private Integer depreciationMonths;
    /** 默认残值率（0.05=5%） */
    private BigDecimal residualRate;
}