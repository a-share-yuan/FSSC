package com.fssc.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同（cont_contract，带 tenant_id，逻辑删除 del_flag）。状态机：DRAFT→APPROVING→EFFECTIVE，可 CHANGING 变更 / 补充协议。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cont_contract")
public class ContContract extends TenantAuditBase {

    private String contractNo;
    private String contractName;
    /** 合同类型：PURCHASE 采购 / SALE 销售 / SERVICE 服务 / FRAMEWORK 框架 */
    private String contractType;
    private String partyAName;
    private String partyBName;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate signDate;
    private LocalDate beginDate;
    private LocalDate endDate;
    /** 补充协议关联的原合同 id */
    private Long parentId;
    /** DRAFT/APPROVING/EFFECTIVE/CHANGING/REJECTED/VOID/CLOSED */
    private String status;
    private String remark;
    // ---------- 合同台账深化字段（中集财务共享需求说明书-合同台账 v1.0） ----------
    /** 关联客商（crm_customer） */
    private Long customerId;
    /** 签订公司 */
    private Long companyId;
    /** 费用归属部门 */
    private Long deptId;
    /** 费用类型ID */
    private Long itemId;
    private String itemName;
    /** 无固定金额（长期协议/以量定额）1=是，不控制预算 */
    private Integer noFixedAmount;
    /** 合同大类（费用类/资产类/成本类/外包类等） */
    private String contractClass;
    /** 是否同比价格递减 */
    private Integer priceDecrease;
    /** 是否招标比价 */
    private Integer bidCompare;
    /** 招标比价过程 */
    private String bidProcess;
    /** 中标原因 */
    private String bidReason;
    /** 中标说明 */
    private String bidNote;
    /** 来源：MANUAL 手动 / IFS 同步 / IMPORT 导入 */
    private String source;
}
