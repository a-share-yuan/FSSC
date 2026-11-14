package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户端菜单（sys_menu，【全局表-无 tenant_id】）。
 * 所有租户共用一套菜单定义；租户的授权通过 sys_role_menu（带 tenant_id）落地。
 * 注意：不能继承 BaseEntity（否则会错误映射不存在的 tenant_id 列）。
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String menuName;
    /** M目录 / C菜单 / F按钮 */
    private String menuType;
    private String path;
    private String component;
    /** 权限标识，如 exp:claim:submit */
    private String perms;
    private String icon;
    /** 所属功能点（套餐开关过滤用） */
    private String featureCode;
    /** PC / H5 / ALL */
    private String terminal;
    private Integer sort;
    /** 1 显示 0 隐藏 */
    private Integer visible;
    /** 1 启用 0 停用 */
    private Integer status;

    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
