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

    /**
     * 是否平台内置字典（1=内置，0=租户自定义）。
     *
     * 该列在库中**早已存在**（默认 0），但实体若**没有映射**，is_builtin 就只能是列默认值、
     * 无法被读出也无法在 Java 侧判定 —— 「内置字典免权限只读通道」需要这个映射。
     *
     * 语义（与 sys_dict_*_def 那套"平台级全局字典"表区分）：
     *   本表仍是**租户级**表（走 TenantLineHandler 加 tenant_id 条件），is_builtin 只标记
     *   「这一类是平台下发的内置枚举、业务只读、不参与租户自定义」，
     *   **不是**"跨租户共享"。跨租户共享要走 sys_dict_*_def（当前 0 行，见字典多租户隔离核实文档）。
     */
    private Integer isBuiltin;
}
