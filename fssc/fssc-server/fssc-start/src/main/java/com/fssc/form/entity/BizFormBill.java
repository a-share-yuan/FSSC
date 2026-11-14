package com.fssc.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 可配置表单的通用单据。
 *
 * <p>字段集合由 {@code md_form_config} + {@code md_form_field} 决定，值统一落在
 * {@link #formData} 的 JSON 里，因此新增一种单据不需要改表结构、也不需要新实体。
 *
 * <p>与费用单据（{@code exp_bill}）的分工：费用单据有金额、预算占用、凭证与发票联动，
 * 继续走宽表；本表只承载「无财务联动」的通用审批单据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_form_bill")
public class BizFormBill extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 表单编码 md_form_config.form_code */
    private String formCode;

    /** 单据编号，由 sys_number_rule 按 form_code 取号 */
    private String billNo;

    /** 流程标题 */
    private String title;

    /** 起流时实际使用的 bizType 快照，便于事后追溯当时命中的是哪条流程路由 */
    private String bizType;

    /** 申请人 */
    private Long applyUserId;

    /** 申请部门 */
    private Long applyDeptId;

    /** 所属公司 */
    private Long companyId;

    /** DRAFT/APPROVING/APPROVED/REJECTED/CANCELED */
    private String status;

    /** 表单字段键值 JSON，键取自 md_form_field.field_key */
    private String formData;

    /** 流程实例 ID */
    private String procInstId;

    /** 当前节点（冗余，列表展示用） */
    private String currentNode;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;
}
