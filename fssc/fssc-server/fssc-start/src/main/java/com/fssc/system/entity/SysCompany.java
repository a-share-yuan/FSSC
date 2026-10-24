package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 纳税主体/公司（sys_company，带 tenant_id）。集团型租户有多个法人公司。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_company")
public class SysCompany extends TenantAuditBase {

    private String companyCode;
    private String companyName;
    /** 上级公司，0 为顶级 */
    private Long parentId;
    /** 祖级路径，逗号分隔，如 0,1,3 */
    private String ancestors;
    /** 统一社会信用代码 */
    private String creditCode;
    /** 纳税人识别号 */
    private String taxNo;
    private Integer sort;
    /** NORMAL正常 / DISABLED停用 */
    private String status;
    /**
     * 档案管理模式（V1.4.92）：CENTRAL 集中（纸质原件寄送共享中心）／NON_CENTRAL 非集中（原件留原单位，
     * 不产生实物流）。默认 CENTRAL＝改造前行为；判定入口见 {@code DeliveryService}。
     */
    private String archiveMode;
}
