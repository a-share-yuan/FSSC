package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户字典类型（sys_dict_type，带 tenant_id） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends TenantAuditBase {

    /** 字典类型编码，如 expense_type */
    private String dictType;
    private String dictName;
    /** NORMAL/DISABLED */
    private String status;
}
