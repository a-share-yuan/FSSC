package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组（批量授权单元，承接业务权限集群）。
 * tenant_id 由 FsscTenantLineHandler 自动过滤（非忽略表），INSERT 时由调用方显式填充。
 */
@Data
@TableName("sys_user_group")
public class SysUserGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String groupCode;
    private String groupName;
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
