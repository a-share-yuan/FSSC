package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_supplier_contact 供应商联系人(N)(41号审查 供应商专属页子表) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_supplier_contact")
public class MdSupplierContact extends TenantAuditBase {

    private Long supplierId;
    @NotBlank(message = "联系人姓名(contactName)不能为空")
    private String contactName;
    private String position;
    /** 电话（选填，填了就必须合规：手机号或含区号固话） */
    @Pattern(regexp = "^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{6,8}(-\\d{1,5})?)?$", message = "电话应为手机号或含区号固话（如 0731-88888888）")
    private String phone;
    /** 邮箱（选填，填了就必须合规） */
    @Pattern(regexp = "^([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?$", message = "邮箱格式不正确")
    private String email;
    private Integer isPrimary;
    private String remark;
}
