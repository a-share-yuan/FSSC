package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 凭证分录（op_voucher_line，带 tenant_id）。无 create_by，独立实体。 */
@Data
@TableName("op_voucher_line")
public class OpVoucherLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long voucherId;
    private Integer lineNo;
    private String summary;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    /** DEBIT借方 / CREDIT贷方 */
    private String direction;
    private BigDecimal amount;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal origAmount;
    private Long deptId;
    private Long projectId;
    private Long supplierId;
    /** 辅助核算：客户 ID（应收账款核销凭证使用） */
    private Long customerId;
    /**
     * 库表本有 {@code employee_id} 列（第 18 列），实体一直没映射 ⇒ 任何写入位置都填不进去。
     * 现补映射但<b>暂无写入方</b>：库里没有员工主档（{@code md_employee} 不存在，只有
     * {@code md_employee_level}），"员工辅助"到底指向 {@code sys_user.id} 还是人事档案 ID 属规则待定，
     * 猜错就是把薪酬/借款辅助挂到错的人身上（同族 的借款挂员工，留待该判定一并落）。
     */
    private Long employeeId;
    /**
     * 库表本有 {@code aux_json} 列（第 19 列，注释"其他辅助核算JSON"），实体没映射、
     * 全仓零写入 ⇒ 该列自建表以来恒 NULL。现补映射，写入方把<b>已解析出的辅助维度连同名称</b>
     * 一次性落在这里（键名沿用 {@code md_subject.aux_items} 的 dept/project/supplier/customer/employee 名称表），
     * 让"辅助核算取的是哪个供应商/项目"在凭证行上自解释，不必再回查单据与主数据。
     * 写入位置＝{@code VoucherService.stampAuxJson}，当前覆盖付款/结算凭证族（EXPENSE_PAY／TREASURY_PAY／
     * EXPENSE_SETTLE）；其余凭证族待同族缺陷（111 员工辅助、084 客户辅助）一并接入，不在本条范围。
     */
    private String auxJson;
    private LocalDateTime createTime;
}
