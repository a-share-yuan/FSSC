package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交通工具（需求 2.23：交通工具类型及对应舱位等级、适用员工等级范围）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_vehicle")
public class MdVehicle extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 交通工具编码 */
    private String vehicleCode;

    /** 交通工具名称 */
    private String vehicleName;

    /** 座位/舱位等级 */
    private String seatClass;

    /** 适用员工等级范围 */
    private String levelRange;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

}
