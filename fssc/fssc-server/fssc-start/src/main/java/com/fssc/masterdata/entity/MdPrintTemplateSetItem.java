package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打印模板套成员（V1.4.81）。
 *
 * <p>不继承 TenantAuditBase：本表只有 4 个业务列 + del_flag，
 * 沿用平台级最小审计口径（create_time）。
 */
@Data
@TableName("md_print_template_set_item")
public class MdPrintTemplateSetItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    /** 所属模板套 */
    private Long setId;

    /** 成员模板 */
    private Long templateId;

    /** 打印页序 */
    private Integer pageOrder;

    /** 打印份数 */
    private Integer copies;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer delFlag;
}
