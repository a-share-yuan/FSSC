package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 发票邮包收件人地址簿：客户 + 收件人 + 电话 + 邮箱 + 地址，供开票链选收件人复用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ar_mail_recipient")
public class ArMailRecipient extends TenantAuditBase {

    private Long companyId;
    private String recipientName;
    private String customerCode;
    private String customerName;
    private String phone;
    private String email;
    private String address;
    /** 1=该客户默认收件人 */
    private Integer isDefault;
    private String remark;
}
