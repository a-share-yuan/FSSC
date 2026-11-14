package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资本性支出申请（FA_CER，FA01）简化申请记录。
 *
 * <p>原 FA_CER 发起时 bizId=null（无对应业务单据表），导致：
 * 流程结束事件缺 bizId 不发布 → 资产域监听器收不到回写（DA-01），也无法做预算占用核销。
 * 现引入本简化申请表：发起即落一条 APPLYING 记录并以 {@code bizId=fa_cer_request.id} 启动审批流，
 * 审批通过 → 预算占用 + 状态 APPROVED；驳回 → 状态 REJECTED（预算如有占用则释放）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fa_cer_request")
public class FaCerRequest extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 申请编号（规则 FA_CER，缺省 CER+时间戳） */
    private String requestNo;

    /** 资产/项目名称 */
    private String assetName;

    /** 申请金额 */
    private BigDecimal amount;

    /** 预算科目（bg_item.id） */
    private Long budgetItemId;

    /** 申请部门 */
    private Long deptId;

    /** 申请公司 */
    private Long companyId;

    /** 备注 */
    private String remark;

    /** 状态：APPLYING 审批中 / APPROVED 已通过（预算已占用） / REJECTED 已驳回 */
    private String status;

    /** 流程实例ID */
    private String procInstId;

    /** 申请人 */
    private Long applyUserId;

    /** 申请人姓名 */
    private String applyUserName;
}
