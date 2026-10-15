package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 功能点定义（pf_feature），套餐开关的最小单位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_feature")
public class PfFeature extends PlatformBaseEntity {

    /** 功能编码，如 F_EXPENSE_CLAIM */
    private String featureCode;
    private String featureName;
    /** 所属模块：SYSTEM/MASTERDATA/EXPENSE/BUDGET/WORKFLOW/SHARING/IMAGE/INVOICE/REPORT */
    private String module;
    private Integer sort;
    private String remark;
}
