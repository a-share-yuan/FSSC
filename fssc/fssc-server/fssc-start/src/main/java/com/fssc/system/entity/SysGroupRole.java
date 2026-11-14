package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组-角色关联（组 → 角色 → 菜单/权限）。
 * 权限最终仍经角色落地，用户组只是"把一组角色批量授予一批用户"的聚合单元。
 */
@Data
@TableName("sys_group_role")
public class SysGroupRole implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long groupId;
    private Long roleId;
    private LocalDateTime createTime;
}
