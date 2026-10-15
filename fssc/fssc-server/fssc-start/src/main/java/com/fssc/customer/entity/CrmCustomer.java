package com.fssc.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户/往来单位（crm_customer，带 tenant_id，逻辑删除）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CrmCustomer extends TenantAuditBase {

    private String customerCode;
    private String customerName;
    /** 往来类型：CUSTOMER 客户 / SUPPLIER 供应商 / VENDOR 服务商 */
    private String customerType;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    /** 信用等级 A/B/C */
    private String creditLevel;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
