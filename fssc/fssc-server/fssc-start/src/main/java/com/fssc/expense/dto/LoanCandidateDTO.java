package com.fssc.expense.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * D-UI-03 配套（2026-10-19）：借款冲抵候选行。
 *
 * <p><b>为什么要单独的 VO</b>：{@code ExpBill.loanBalance} 是<b>账面余额</b>（借款额 − 已生效冲抵流水），
 * <b>不含在途占用</b>（自己/他人未审批草稿已在 {@code exp_bill_relation(LOAN_CLAIM)} 上登记的量）。
 * 前端候选面此前直接拿账面余额当「可冲抵额」用，于是：
 * ① 默认勾全额必然超额，被服务端 {@code registerLoanOccupancy} 闸拒（提示清晰，但用户已白填一遍表）；
 * ② 用户看不出这张借款单其实已被在途占掉多少。
 * {@code availableBalance = 账面余额 − 在途占用}，与服务端放行口径<b>同源</b>，前端不再自己猜。</p>
 *
 * <p>口径与放行闸完全一致（见 {@code ExpenseDetailService#registerLoanOccupancy}）：
 * <ul>
 *   <li>只列本人名下（{@code apply_user_id = 当前用户}）——服务端本就只允许冲销本人借款；</li>
 *   <li>在途＝目标单状态为 {@code DRAFT}/{@code APPROVING} 的 {@code LOAN_CLAIM} 登记（已审批通过的
 *       其冲抵额已落流水并扣进账面余额，不重复扣）；</li>
 *   <li>{@code availableBalance ≤ 0} 的候选<b>不出现在列表里</b>（无可冲抵，选了也是死路）。</li>
 * </ul>
 */
@Data
public class LoanCandidateDTO {

    /** 借款单 id（exp_bill.id） */
    private Long id;

    /** 借款单号（如 JK202610190003） */
    private String billNo;

    /** 事由/标题 */
    private String title;

    /** 借款额 */
    private BigDecimal totalAmount;

    /** 账面余额（借款额 − 已生效冲抵） */
    private BigDecimal loanBalance;

    /** 可冲抵余额（账面余额 − 在途占用）——前端默认值与上限都用它 */
    private BigDecimal availableBalance;

    /** 申请日期 */
    private LocalDate applyDate;
}
