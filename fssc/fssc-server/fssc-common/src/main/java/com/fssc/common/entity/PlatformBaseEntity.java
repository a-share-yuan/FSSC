package com.fssc.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台表（pf_*）实体基类。与 {@link BaseEntity} 的差异：
 * <ul>
 *   <li>【没有 tenant_id】—— 平台表不参与租户改写（架构铁律）。</li>
 *   <li>create_by / update_by 为 bigint，映射 Long（记录平台用户ID）。</li>
 *   <li>带 delFlag 逻辑删除。审计日志表（pf_oper_log/pf_login_log）【不用本基类】—— 它们不可删。</li>
 * </ul>
 */
public class PlatformBaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 必须标 fill=INSERT，否则 MP 生成的 INSERT 列清单不含 create_by，MetaObjectHandler 填值无效 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 必须标 fill=INSERT_UPDATE，否则审计人无法落库 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
}
