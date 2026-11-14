package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-用户组关联。一个用户可属于多个组，权限为"直接角色 ∪ 各组成员角色"的并集。
 */
@Data
@TableName("sys_user_group_rel")
public class SysUserGroupRel implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long groupId;
    private LocalDateTime createTime;
}
