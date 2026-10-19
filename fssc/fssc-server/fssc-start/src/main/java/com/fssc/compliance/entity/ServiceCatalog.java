package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** 服务目录（共享中心对外提供的服务项，含 SLA 时效目标）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_catalog")
public class ServiceCatalog extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务编码 */
    private String serviceCode;

    /** 服务名称 */
    private String serviceName;

    /** 服务分类：REIMBURSE/PAYMENT/LOAN/INVOICE/ARCHIVE/ALL */
    private String category;

    /** 关联单据类型（EXP_CLAIM/EXP_PAY/EXP_LOAN...），ALL 表示全部 */
    private String processType;

    /** 处理时效（小时） */
    private Integer handleSlaHours;

    /** 响应时效（小时） */
    private Integer responseSlaHours;

    /** 负责角色 */
    private String ownerRole;

    /** 服务描述 */
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
