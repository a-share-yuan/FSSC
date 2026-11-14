package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 员工等级（差旅标准控制）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_employee_level")
public class MdEmployeeLevel extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 等级编码 */
    private String levelCode;
    /** 等级名称 */
    private String levelName;
    /** 排序 */
    private Integer sort;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
