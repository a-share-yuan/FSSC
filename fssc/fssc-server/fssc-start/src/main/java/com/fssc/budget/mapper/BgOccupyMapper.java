package com.fssc.budget.mapper;

import com.fssc.budget.entity.BgOccupy;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预算占用流水（bg_occupy）。
 * 【铁律：只增不改】：只提供 insert/查询，无 update/delete —— 占用流水不可篡改。
 * tenant_id 由 MetaObjectHandler 从 TenantContext 填充（实体已标 fill=INSERT）。
 */
@Mapper
public interface BgOccupyMapper {

    @Insert("insert into bg_occupy(tenant_id, budget_item_id, oper_type, amount, biz_type, biz_id, biz_no, " +
            "biz_line_id, oper_user_id, remark, create_time) " +
            "values(#{tenantId}, #{budgetItemId}, #{operType}, #{amount}, #{bizType}, #{bizId}, #{bizNo}, " +
            "#{bizLineId}, #{operUserId}, #{remark}, #{createTime})")
    int insert(BgOccupy occupy);

    /** 占用流水真分页查询（IPage 触发 MP 分页插件；租户拦截器自动注入 tenant_id）。
     *  2026-09-17：增加 budgetItemId 过滤，供执行页「穿透查看该明细的占用/核销流水」。 */
    @Select("<script>select * from bg_occupy " +
            "<where>" +
            "<if test='budgetItemId != null'>and budget_item_id = #{budgetItemId}</if> " +
            "<if test='operType != null and operType != \"\"'>and oper_type = #{operType}</if> " +
            "<if test='bizType != null and bizType != \"\"'>and biz_type = #{bizType}</if> " +
            "<if test='keyword != null and keyword != \"\"'>and biz_no like concat('%',#{keyword},'%')</if> " +
            "</where> order by id desc</script>")
    com.baomidou.mybatisplus.core.metadata.IPage<BgOccupy> searchPage(
            com.baomidou.mybatisplus.core.metadata.IPage<BgOccupy> page,
            @Param("budgetItemId") Long budgetItemId,
            @Param("operType") String operType,
            @Param("bizType") String bizType,
            @Param("keyword") String keyword);

    /** 按业务单据汇总净占用额（OCCUPY 为正、RELEASE 为负），用于 CER 占用幂等与驳回释放 */
    @Select("select coalesce(sum(amount), 0) from bg_occupy where biz_type = #{bizType} and biz_id = #{bizId}")
    java.math.BigDecimal sumAmountByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * R5-D-265：某预算行的全行流水净占用（bg_occupy.amount 代数和，OCCUPY 正 / RELEASE、WRITE_OFF 负）。
     * 供提交链路「表头 occupy_amount vs 流水净额」一致性闸逐行对点（bg_occupy 只增不改，是本行的真相账本）。
     */
    @Select("select coalesce(sum(amount), 0) from bg_occupy where budget_item_id = #{budgetItemId}")
    java.math.BigDecimal sumNetOccupyByItem(@Param("budgetItemId") Long budgetItemId);

    /**
     * （2026-09-14）：单据是否已发生过预算执行核销（WRITE_OFF）。
     * 仅统计费用/付款域三种 biz_type（EXP_CLAIM 报销审批核销 / EXP_PAY 对公付款审批核销 /
     * TR_PAY 资金付款批次支付核销），其 biz_id 均为 exp_bill 主键；
     * 排除 FA_CER 等其他域（biz_id 为各自表主键，数值可能与 exp_bill 撞号）。
     */
    @Select("select count(*) from bg_occupy where oper_type = 'WRITE_OFF' and biz_id = #{bizId} " +
            "and biz_type in ('EXP_CLAIM','EXP_PAY','TR_PAY')")
    int countWriteOffForBill(@Param("bizId") Long bizId);

    /**
     * 单据在某一预算明细上的净占用额（OCCUPY 为正，WRITE_OFF/RELEASE 为负）。
     * <p>P0-C2：核销时以此为准，不再用 bg_item.occupy_amount（全行汇总）截断，
     * 避免 A 单的占用被 B 单核销吃掉。
     * <p>按 biz_id 归户但跨 biz_type：事前申请占用记 EXP_CLAIM、其核销记 EXP_APPLY
     * （{@code ExpenseWfEventListener.writeOffApplyOccupy}），故不能按单一 biz_type 过滤。
     * 跨类型仅在费用/付款域内发生（这些类型的 biz_id 均为 exp_bill 主键）；
     * FA_CER / EXP_CONTRACT 等其他域的 biz_id 与 exp_bill 主键会撞号，故 {@code crossExpense=false}
     * 时只按调用方自身 biz_type 归户。
     */
    @Select("<script>select coalesce(sum(amount), 0) from bg_occupy " +
            "where budget_item_id = #{budgetItemId} and biz_id = #{bizId} " +
            "and (biz_type = #{bizType}" +
            "<if test='crossExpense'> or biz_type in ('EXP_CLAIM','EXP_PAY','EXP_APPLY','EXP_LOAN','EXP_REPAY','TR_PAY')</if>" +
            ")</script>")
    java.math.BigDecimal sumNetOccupyByItemAndBiz(@Param("budgetItemId") Long budgetItemId,
                                                  @Param("bizId") Long bizId,
                                                  @Param("bizType") String bizType,
                                                  @Param("crossExpense") boolean crossExpense);

    /**
     * 单据在某一预算明细上的历史占用总额（仅统计 OCCUPY 正向流水）。
     * <p>P0-C2：与 {@link #sumNetOccupyByItemAndBiz} 配对，用于区分
     * 「本单据从未在该预算行占用过」（=线下直报，核销只加已用）与
     * 「占用已全部转实/释放，又被重放核销」（=重复核销，必须整体跳过，否则 used 虚增）。
     */
    @Select("<script>select coalesce(sum(amount), 0) from bg_occupy " +
            "where budget_item_id = #{budgetItemId} and biz_id = #{bizId} and oper_type = 'OCCUPY' " +
            "and (biz_type = #{bizType}" +
            "<if test='crossExpense'> or biz_type in ('EXP_CLAIM','EXP_PAY','EXP_APPLY','EXP_LOAN','EXP_REPAY','TR_PAY')</if>" +
            ")</script>")
    java.math.BigDecimal sumOccupiedByItemAndBiz(@Param("budgetItemId") Long budgetItemId,
                                                 @Param("bizId") Long bizId,
                                                 @Param("bizType") String bizType,
                                                 @Param("crossExpense") boolean crossExpense);

    /**
     * 单据曾经占用过哪些预算明细（D-DEC-105 还款冲减：冲减对象要按借款单的真实占用行找，
     * 不能只看还款行解析出的科目）。
     * <p>仅限费用/付款域 biz_type：其余域（FA_CER／EXP_CONTRACT）的 biz_id 与 exp_bill 主键会撞号。
     */
    @Select("select distinct budget_item_id from bg_occupy " +
            "where biz_id = #{bizId} and oper_type = 'OCCUPY' " +
            "and biz_type in ('EXP_CLAIM','EXP_PAY','EXP_APPLY','EXP_LOAN','EXP_REPAY','TR_PAY')")
    List<Long> selectOccupiedItemsByBill(@Param("bizId") Long bizId);

    /**
     * 某业务单据在指定 biz_type 下曾经占用过的预算明细行（D-DEC-145 薪酬域）。
     * <p>薪酬的占用/核销/释放三步都以「当初真实占在哪几行」为准回查账本，不在收尾时重解析科目：
     * 费用项→预算行的映射可能被人在两步之间改过，重解析会把消耗记到另一条线上（同
     * {@link #selectOccupiedItemsByBill} 的理由，但薪酬的 biz_id 是 gl_salary_run 主键，
     * 与 exp_bill 主键会撞号，故必须按 biz_type 精确归户，不能跨费用域）。
     */
    @Select("select distinct budget_item_id from bg_occupy " +
            "where biz_type = #{bizType} and biz_id = #{bizId} and oper_type = 'OCCUPY'")
    List<Long> selectOccupiedItemsByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 还款单对某借款单的冲减/回补台账（D-DEC-105，逐预算行一行）。
     * <p>冲减流水（RELEASE，负数）与回补流水（OCCUPY，正数）都归户在<b>借款单</b> biz_id 上、
     * biz_no 记<b>还款单号</b>，因此"本还款单在这行冲了多少 / 又补回多少"一次查得，
     * 结清重放与驳回回补都按它做幂等，绝不新增第二笔占用。
     *
     * @return 每行含 budgetItemId / offsetAmount（已冲减，正数）/ restoreAmount（已回补，正数）
     */
    @Select("select budget_item_id as budgetItemId, " +
            "coalesce(sum(case when oper_type = 'RELEASE' then -amount else 0 end), 0) as offsetAmount, " +
            "coalesce(sum(case when oper_type = 'OCCUPY' then amount else 0 end), 0) as restoreAmount " +
            "from bg_occupy " +
            "where biz_type = #{bizType} and biz_id = #{loanBillId} and biz_no = #{repayBillNo} " +
            "and oper_type in ('RELEASE','OCCUPY') group by budget_item_id")
    List<java.util.Map<String, Object>> selectRepayOffsetLedger(@Param("bizType") String bizType,
                                                                @Param("loanBillId") Long loanBillId,
                                                                @Param("repayBillNo") String repayBillNo);

    /**
     * 某还款单冲减过哪些借款单的预算占用（D-DEC-105 驳回/撤回回补的自寻址入口）。
     * <p>回补只需要「当初冲在哪些借款单上」，这一个事实已经完整记在 {@code bg_occupy}
     * （biz_type=EXP_REPAY + biz_no=还款单号，biz_id=借款单 id）里，
     * 无需再回查 {@code exp_loan_offset}／FIFO 分配，避免两处口径分叉。
     */
    @Select("select distinct biz_id from bg_occupy " +
            "where biz_type = 'EXP_REPAY' and biz_no = #{repayBillNo} and oper_type in ('RELEASE','OCCUPY')")
    List<Long> selectOffsetLoanBillsByRepayNo(@Param("repayBillNo") String repayBillNo);
}
