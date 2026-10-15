package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租户使用量日统计（pf_tenant_stat）。
 * 【平台表-仅聚合】ref_tenant_id 为业务外键；无 create_by/update_by/del_flag，按真实列独立声明。
 * 跨租户统计必须走 TenantContext.runIgnoreTenant() 且只允许聚合。
 */
@Data
@TableName("pf_tenant_stat")
public class PfTenantStat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID【业务外键,非隔离字段】 */
    private Long refTenantId;
    private LocalDate statDate;
    private Integer userCount;
    private Integer activeUser;
    private Long billCount;
    private Long storageMb;
    private Long apiCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
