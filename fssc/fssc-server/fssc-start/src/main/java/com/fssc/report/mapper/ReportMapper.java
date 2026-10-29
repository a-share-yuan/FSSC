// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 报表聚合查询（join 多表，租户拦截器自动为每张租户表注入 tenant_id）。
 * 只读聚合，不提供写方法。
 *
 * <p><b>数据权限</b>：注解 SQL 无法像 QueryWrapper 那样动态拼装，故本接口的报表方法统一
 * 追加三个可空过滤参数 {@code scopeCompanyId / scopeDeptIds / scopeUserId}，由
 * {@code ReportQueryController} 依据 {@code DataScopeHolder}（@DataScope 切面写入）解析后传入；
 * SQL 内以 {@code <if>} 追加条件。三参数语义与 {@code DataScopeKit.apply} 逐级对应：
 * 传 null＝该维度不追加条件；由调用方保证 scopeDeptIds 非空集（需要收紧时传哨兵值而非空集/ null）。
 * 表内没有对应组织列时该维度不出现（调用方按 DataScopeKit 规则降级到公司维度）。</p>
 */
@Mapper
public interface ReportMapper {

    /**
     * 费用类报表的统一生效状态白名单（F-06）：审批通过／已付／已结清／已办结／已过账。
     * 原 expenseSummary／monthlyTrend／expenseComposition／expenseAnalysis 四处只写
     * {@code APPROVED,POSTED}，已付款（PAID）的单会整张从费用规则里漏掉——与 approvalEfficiency
     * 的五最终状态规则对齐，四处共用一份，避免再次漂移。
     */
    String EXPENSE_EFFECTIVE_STATUS = "'APPROVED','PAID','REPAID','COMPLETED','POSTED'";

    /** 部门报销排行（金额降序）。companyId 非 null 时仅统计该公司（① 导出规则）
     *  camelCase 别名为报表工作台的列绑定，snake_case 别名保留给导出与看板的既有读取，两者共存。
     *  补数据范围参数。原规则只按公司办结（companyId），而本接口挂 {@code dashboard:view}
     *  ——普通员工与共享会计返回完全相同的全公司排名（10-15 实测两个账号结果逐行一致）。
     *  scopeDeptIds/scopeUserId 与同文件 {@code loanBalance} 同一套语义：null 即不追加该维条件。
     *  ⑮（2026-10-16 晚间场实测）：补生效状态过滤——原规则把 DRAFT/VOID/WITHDRAWN/REJECTED
     *  一并计入，赵勇（本部门只有一张 0 元草稿申请）的部门排名渲染成「费用审核组 1 单 0.00」。 */
    @Select("<script>select b.dept_id as dept_id, d.dept_name as dept_name, count(*) as cnt, " +
            "sum(b.total_amount) as amount, " +
            "d.dept_name as deptName, count(*) as billCount, sum(b.total_amount) as totalAmount " + // R5-D-021
            "from exp_bill b left join sys_dept d on b.dept_id = d.id " +
            "where b.del_flag = 0 and b.dept_id is not null " +
            "and b.status not in ('DRAFT','VOID','WITHDRAWN','REJECTED') " + // ⑮ D-V4R-50
            "<if test='companyId != null'>and b.company_id = #{companyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.dept_id, d.dept_name order by amount desc</script>")
    List<Map<String, Object>> expenseByDept(@Param("companyId") Long companyId,
                                            @Param("scopeDeptIds") Collection<Long> scopeDeptIds,
                                            @Param("scopeUserId") Long scopeUserId);

    /** 审批时效：提交(submit_time) → 审批通过(派工 create_time) 的平均分钟，按单据类型
     *  报表工作台按「单据类型 + 小时」绑定，故补 camelCase 别名（avg/max 换算为小时）；
     *  snake_case 的分钟别名保留给看板的既有读取，两者共存。
     *  补公司维办结——scopeCompanyId 由 DashboardService 按 orgGuard.enforceCompany()
     *  传入（同文件 expenseByDept 现例）；exp_bill 有 company_id 列；null（集团级/无公司登录）不追加。
     *  再补部门/本人两维。原规则同样只按公司办结，而接口挂 {@code dashboard:view}
     *  ⇒ 普通员工能看到全公司各部门审批时效，与共享会计无差异（10-15 实测）。 */
    @Select("<script>select b.bill_type as bill_type, count(*) as cnt, " +
            "round(avg(timestampdiff(MINUTE, b.submit_time, a.create_time)), 1) as avg_minutes, " +
            "b.bill_type as nodeName, " +
            "round(avg(timestampdiff(MINUTE, b.submit_time, a.create_time)) / 60, 1) as avgHours, " + // R5-D-115
            "round(max(timestampdiff(MINUTE, b.submit_time, a.create_time)) / 60, 1) as maxHours " + // R5-D-115
            // ⑮：审批通过是最终状态迁移而非唯一最终状态——已付(PAID)/结清(REPAID)/办结(COMPLETED)
            // 的单同样走完了 submit→AUDIT 全程，硬编码 status='APPROVED' 会把付款后的单从时效里剔除
            //（晚间场实测：bill 7 已 PAID、bill 3 已 REPAID 均有 AUDIT 任务却不计入）
            "from exp_bill b join op_audit_task a on b.id = a.bill_id " +
            "where a.task_type = 'AUDIT' and a.del_flag = 0 and b.del_flag = 0 " +
            "and b.status in ('APPROVED','PAID','REPAID','COMPLETED','POSTED') and b.submit_time is not null " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.bill_type</script>")
    List<Map<String, Object>> approvalEfficiency(@Param("scopeCompanyId") Long scopeCompanyId,
                                                 @Param("scopeDeptIds") Collection<Long> scopeDeptIds,
                                                 @Param("scopeUserId") Long scopeUserId);

    // 需求 11.4 借款余额汇总：员工借款金额/未冲销余额
    @Select("<script>select b.apply_user_id as applyUserId, max(ifnull(u.nick_name, b.apply_user_name)) as applyUserName, count(*) as billCnt, " +
            "sum(b.total_amount) as loanTotal, sum(ifnull(b.loan_balance, 0)) as balance " +
            "from exp_bill b left join sys_user u on b.apply_user_id = u.id " +
            "where b.del_flag = 0 and b.bill_type = 'LOAN' and b.status != 'VOID' " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.apply_user_id order by balance desc</script>")
    List<Map<String, Object>> loanBalance(@Param("scopeCompanyId") Long scopeCompanyId,
                                          @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                          @Param("scopeUserId") Long scopeUserId);

    // 需求 11.6 合同汇总：合同总额 / 已付款金额（PAY 已审批单据累计）
    @Select("<script>select c.contract_no as contractNo, c.contract_name as contractName, c.contract_type as contractType, c.party_b_name as partyBName, " +
            "c.amount as contractAmount, c.currency_code as currencyCode, c.status, " +
            "ifnull(sum(p.total_amount), 0) as paidAmount, c.begin_date as beginDate, c.end_date as endDate " +
            "from cont_contract c " +
            "left join exp_bill p on p.contract_id = c.id and p.del_flag = 0 and p.bill_type = 'PAY' " +
            "and p.status in ('APPROVED','POSTED') " +
            "where c.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and c.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and c.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "group by c.id, c.contract_no, c.contract_name, c.contract_type, c.party_b_name, " +
            "c.amount, c.currency_code, c.status, c.begin_date, c.end_date " +
            "order by c.id desc</script>")
    List<Map<String, Object>> contractSummary(@Param("scopeCompanyId") Long scopeCompanyId,
                                              @Param("scopeDeptIds") List<Long> scopeDeptIds);

    /** 合同汇总分页（含关键字过滤；原 contractSummary 保留给单行查找/多tab报表用） */
    @Select("<script>select * from (" +
            "select c.id as contractId, c.contract_no as contractNo, c.contract_name as contractName, c.contract_type as contractType, c.party_b_name as partyBName, " +
            "c.amount as contractAmount, c.currency_code as currencyCode, c.status, " +
            "ifnull(sum(p.total_amount), 0) as paidAmount, c.begin_date as beginDate, c.end_date as endDate " +
            "from cont_contract c " +
            "left join exp_bill p on p.contract_id = c.id and p.del_flag = 0 and p.bill_type = 'PAY' " +
            "and p.status in ('APPROVED','POSTED') " +
            "where c.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and c.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and c.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "group by c.id, c.contract_no, c.contract_name, c.contract_type, c.party_b_name, " +
            "c.amount, c.currency_code, c.status, c.begin_date, c.end_date" +
            ") t " +
            "<if test='kw != null and kw != \"\"'>where t.contractNo like concat('%',#{kw},'%') or t.contractName like concat('%',#{kw},'%')</if> " +
            "order by t.contractId desc</script>")
    IPage<Map<String, Object>> contractSummaryPage(Page<Map<String, Object>> page, @Param("kw") String kw,
                                                   @Param("scopeCompanyId") Long scopeCompanyId,
                                                   @Param("scopeDeptIds") List<Long> scopeDeptIds);

    // 需求 11.7 费用明细表：按单据+明细行查询
    // F-03：原 `order by b.id desc limit 300` 是行级截断——边界单只出一半明细，且同单各行在并列排序下
    // 保留哪几行不确定（BX382025100006 曾只剩劳动保护费、差旅费被截掉）。改为单据粒度：
    // 先取最近 100 单（关键字/数据范围全部下推到单据），再取这些单的全部明细，单内按行 id 定序。
    @Select("<script>select b.bill_no as billNo, b.bill_type as billType, b.bill_sub_type as billSubType, " +
            "b.apply_user_name as applyUserName, b.apply_date as applyDate, b.dept_id as deptId, " +
            "l.item_name as itemName, l.amount, b.status, b.voucher_no as voucherNo " +
            "from exp_bill b join exp_bill_line l on l.bill_id = b.id where l.item_id is not null " +
            "and b.del_flag = 0 and l.del_flag = 0 " + // R5-D-031
            "and b.id in (select t.id from (select b2.id from exp_bill b2 where b2.del_flag = 0 " +
            "<if test='kw != null and kw != \"\"'>and (b2.bill_no like concat('%',#{kw},'%') " +
            "or b2.apply_user_name like concat('%',#{kw},'%') or exists (select 1 from exp_bill_line l2 " +
            "where l2.bill_id = b2.id and l2.del_flag = 0 and l2.item_id is not null " +
            "and l2.item_name like concat('%',#{kw},'%')))</if> " +
            "<if test='scopeCompanyId != null'>and b2.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b2.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b2.apply_user_id = #{scopeUserId}</if> " +
            "order by b2.id desc limit 100) t) " +
            "order by b.id desc, l.id</script>")
    List<Map<String, Object>> expenseDetail(@Param("kw") String kw,
                                            @Param("scopeCompanyId") Long scopeCompanyId,
                                            @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                            @Param("scopeUserId") Long scopeUserId);

    // 需求 11.8 运营数据统计：共享作业情况
    // 分库改造：img_batch 已迁 fssc_image 库，"影像批次"计数改由 ReportQueryController
    // 经 ImageFacade.countBatches 应用层拼接，本 SQL 只保留 master 库 5 个 UNION 分支。
    // 注：op_audit_task / op_quality 无 company_id（共享中心跨公司集中办理规则），
    // 本方法不做数据范围办结，未挂 @DataScope——见 ReportQueryController 同处说明。
    // 残留：三个 op_audit_task 分支原样统计，会把已作废任务计进 KPI，逐支补软删过滤。
    @Select("select '派工任务总数' as kpi, count(*) as val from op_audit_task where del_flag = 0 " + // R5-D-299
            "union all select '待处理任务', count(*) from op_audit_task where pool_status = 'WAITING' and del_flag = 0 " + // R5-D-299
            "union all select '已办结任务', count(*) from op_audit_task where pool_status = 'DONE' and del_flag = 0 " + // R5-D-299
            "union all select '生成凭证数', count(*) from op_voucher where del_flag = 0 " +
            "union all select '付款批次', count(*) from op_pay_batch where del_flag = 0 " +
            "union all select '质检记录', count(*) from op_quality where del_flag = 0")
    List<Map<String, Object>> opsStatistics();

    // 需求 11.1 费用发生汇总表：组织在期间段内各类费用的发生情况
    @Select("<script>select l.item_name as itemName, l.item_id as itemId, count(distinct b.id) as billCnt, " +
            "count(*) as lineCnt, sum(l.amount) as totalAmount " +
            "from exp_bill b join exp_bill_line l on l.bill_id = b.id " +
            "where b.status in (" + EXPENSE_EFFECTIVE_STATUS + ") and b.period_year is not null " +
            "and b.del_flag = 0 and l.del_flag = 0 " + // R5-D-031
            "<if test='year != null'>and b.period_year = #{year}</if> " +
            "<if test='month != null'>and b.period_month = #{month}</if> " +
            "<if test='itemName != null and itemName != \"\"'>and l.item_name like concat('%',#{itemName},'%')</if> " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by l.item_id, l.item_name order by totalAmount desc</script>")
    List<Map<String, Object>> expenseSummary(@Param("year") Integer year, @Param("month") Integer month,
                                             @Param("itemName") String itemName,
                                             @Param("scopeCompanyId") Long scopeCompanyId,
                                             @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                             @Param("scopeUserId") Long scopeUserId);

    // 需求 11.2 费用月度变动表：特定组织各类费用的月度变动
    @Select("<script>select b.period_year as year, b.period_month as month, l.item_name as itemName, " +
            "sum(l.amount) as totalAmount " +
            "from exp_bill b join exp_bill_line l on l.bill_id = b.id " +
            "where b.status in (" + EXPENSE_EFFECTIVE_STATUS + ") and b.period_year is not null " +
            "and b.del_flag = 0 and l.del_flag = 0 " + // R5-D-031
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.period_year, b.period_month, l.item_name " +
            "order by b.period_year, b.period_month, totalAmount desc</script>")
    List<Map<String, Object>> monthlyTrend(@Param("scopeCompanyId") Long scopeCompanyId,
                                           @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                           @Param("scopeUserId") Long scopeUserId);

    // 需求 11.3 费用构成情况表：某类型费用在各组织的月度变动
    @Select("<script>select b.company_id as companyId, b.period_year as year, b.period_month as month, " +
            "sum(l.amount) as totalAmount " +
            "from exp_bill b join exp_bill_line l on l.bill_id = b.id " +
            "where b.status in (" + EXPENSE_EFFECTIVE_STATUS + ") and b.period_year is not null " +
            "and b.del_flag = 0 and l.del_flag = 0 " + // R5-D-031
            "<if test='itemName != null and itemName != \"\"'>and l.item_name like concat('%',#{itemName},'%')</if> " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.company_id, b.period_year, b.period_month " +
            "order by b.period_year, b.period_month</script>")
    List<Map<String, Object>> expenseComposition(@Param("itemName") String itemName,
                                                 @Param("scopeCompanyId") Long scopeCompanyId,
                                                 @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                                 @Param("scopeUserId") Long scopeUserId);

    // 扩展报表

    /** 1. 应付账龄分析：按供应商统计应付余额及账龄分布 */
    @Select("<script>select b.payee_name as payeeName, count(*) as billCnt, " +
            "sum(b.payable_amount - ifnull(b.paid_amount, 0)) as balance, " +
            "sum(case when datediff(curdate(), b.apply_date) &lt;= 30 then b.payable_amount - ifnull(b.paid_amount, 0) else 0 end) as d0_30, " +
            "sum(case when datediff(curdate(), b.apply_date) between 31 and 60 then b.payable_amount - ifnull(b.paid_amount, 0) else 0 end) as d31_60, " +
            "sum(case when datediff(curdate(), b.apply_date) between 61 and 90 then b.payable_amount - ifnull(b.paid_amount, 0) else 0 end) as d61_90, " +
            "sum(case when datediff(curdate(), b.apply_date) > 90 then b.payable_amount - ifnull(b.paid_amount, 0) else 0 end) as d90_plus " +
            "from exp_bill b where b.bill_type = 'PAY' and b.status in ('APPROVED','POSTED') " +
            "and b.payable_amount > ifnull(b.paid_amount, 0) and b.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.payee_name order by balance desc</script>")
    List<Map<String, Object>> apAging(@Param("scopeCompanyId") Long scopeCompanyId,
                                      @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                      @Param("scopeUserId") Long scopeUserId);

    /** 2. 应收账龄分析：按客户统计应收余额及账龄分布 */
    @Select("<script>select r.customer_id as customerId, ifnull(c.customer_name, concat('客户#', r.customer_id)) as customerName, count(*) as billCnt, " +
            "sum(r.remain_amount) as balance, " +
            "sum(case when datediff(curdate(), r.bill_date) &lt;= 30 then r.remain_amount else 0 end) as d0_30, " +
            "sum(case when datediff(curdate(), r.bill_date) between 31 and 60 then r.remain_amount else 0 end) as d31_60, " +
            "sum(case when datediff(curdate(), r.bill_date) between 61 and 90 then r.remain_amount else 0 end) as d61_90, " +
            "sum(case when datediff(curdate(), r.bill_date) > 90 then r.remain_amount else 0 end) as d90_plus " +
            "from rc_receivable r left join crm_customer c on r.customer_id = c.id " +
            "where r.del_flag = 0 and r.remain_amount > 0 " +
            "<if test='scopeCompanyId != null'>and r.company_id = #{scopeCompanyId}</if> " +
            "group by r.customer_id order by balance desc</script>")
    List<Map<String, Object>> arAging(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 3. 资金收支汇总：按月度统计收支金额 */
    @Select("<script>select date_format(trans_date, '%Y-%m') as period, " +
            "sum(case when amount > 0 then amount else 0 end) as income, " +
            "sum(case when amount &lt; 0 then abs(amount) else 0 end) as expense, " +
            "sum(amount) as netAmount, count(*) as transCnt " +
            "from op_bank_statement " +
            "where 1=1 " +
            "<if test='scopeCompanyId != null'>and company_id = #{scopeCompanyId}</if> " +
            "group by date_format(trans_date, '%Y-%m') order by period desc limit 12</script>")
    List<Map<String, Object>> cashFlowSummary(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 4. 资金余额表：各银行账户当前余额 */
    @Select("<script>select ba.account_name as accountName, ba.bank_name as bankName, " +
            "ba.bank_account as bankAccount, ba.currency_code as currencyCode, " +
            "ba.balance, ba.status " +
            "from md_bank_account ba where ba.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and ba.company_id = #{scopeCompanyId}</if> " +
            "order by ba.bank_name</script>")
    List<Map<String, Object>> bankBalanceReport(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 5. 供应商付款汇总：按供应商统计付款总额及笔数 */
    @Select("<script>select b.payee_name as payeeName, count(*) as billCnt, " +
            "sum(b.total_amount) as totalAmount, sum(ifnull(b.paid_amount, 0)) as paidAmount, " +
            "sum(b.payable_amount - ifnull(b.paid_amount, 0)) as unpaidAmount " +
            "from exp_bill b where b.bill_type = 'PAY' and b.status in ('APPROVED','POSTED','PAID') " +
            "and b.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.payee_name order by totalAmount desc</script>")
    List<Map<String, Object>> supplierPayment(@Param("scopeCompanyId") Long scopeCompanyId,
                                              @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                              @Param("scopeUserId") Long scopeUserId);

    /** 6. 进项发票统计：按月度统计进项发票金额、税额
     *  列语义对齐建表规则——amount=价税合计、net_amount=不含税，故 totalAmount 取 amount、netAmount 取 net_amount。 */
    @Select("<script>select date_format(invoice_date, '%Y-%m') as period, " +
            "count(*) as invoiceCnt, sum(amount) as totalAmount, sum(tax_amount) as taxAmount, " + // R5-D-022
            "sum(net_amount) as netAmount " + // R5-D-022
            "from inv_invoice where (direction = 'INPUT' or (direction is null and invoice_kind = 'SPECIAL')) and del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and company_id = #{scopeCompanyId}</if> " +
            "group by date_format(invoice_date, '%Y-%m') order by period desc limit 12</script>")
    List<Map<String, Object>> inputInvoiceStats(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 7. 销项发票统计：按月度统计销项发票金额、税额
     *  列语义对齐建表规则——amount=价税合计、net_amount=不含税，故 totalAmount 取 amount、netAmount 取 net_amount。 */
    @Select("<script>select date_format(invoice_date, '%Y-%m') as period, " +
            "count(*) as invoiceCnt, sum(amount) as totalAmount, sum(tax_amount) as taxAmount, " + // R5-D-022
            "sum(net_amount) as netAmount " + // R5-D-022
            "from inv_invoice where (direction = 'OUTPUT' or (direction is null and invoice_kind = 'NORMAL')) and del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and company_id = #{scopeCompanyId}</if> " +
            "group by date_format(invoice_date, '%Y-%m') order by period desc limit 12</script>")
    List<Map<String, Object>> outputInvoiceStats(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 8. 税负分析：按期间统计进项税额、销项税额、应纳税额
     *  补 taxBase（销项不含税金额）作为税负率分母——税负率 = 应纳税额 / 不含税销售收入，
     *  原查询只回三个税额，前端无从计算该比率（手册「表头缺列＝缺陷」）。 */
    @Select("<script>select date_format(invoice_date, '%Y-%m') as period, " +
            "sum(case when (direction = 'INPUT' or (direction is null and invoice_kind = 'SPECIAL')) then tax_amount else 0 end) as inputTax, " +
            "sum(case when (direction = 'OUTPUT' or (direction is null and invoice_kind = 'NORMAL')) then tax_amount else 0 end) as outputTax, " +
            "sum(case when (direction = 'OUTPUT' or (direction is null and invoice_kind = 'NORMAL')) then net_amount else 0 end) as taxBase, " + // D-H10-01
            "sum(case when (direction = 'OUTPUT' or (direction is null and invoice_kind = 'NORMAL')) then tax_amount else 0 end) - " +
            "sum(case when (direction = 'INPUT' or (direction is null and invoice_kind = 'SPECIAL')) then tax_amount else 0 end) as netTax " +
            "from inv_invoice where del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and company_id = #{scopeCompanyId}</if> " +
            "group by date_format(invoice_date, '%Y-%m') order by period desc limit 12</script>")
    List<Map<String, Object>> taxBurdenAnalysis(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 9. 运营效率分析：按任务类型统计平均处理时长 */
    // 注：op_audit_task 无 company_id（实体仅 dept_id/apply_user_id），
    // 共享作业任务池属跨公司集中办理规则，本方法不做数据范围办结，未挂 @DataScope。
    @Select("select t.task_type as taskType, count(*) as taskCnt, " +
            "round(avg(timestampdiff(MINUTE, t.receive_time, t.finish_time)), 1) as avgMinutes, " +
            "max(timestampdiff(MINUTE, t.receive_time, t.finish_time)) as maxMinutes, " +
            "sum(case when t.finish_time > t.sla_time then 1 else 0 end) as slaBreachCnt " +
            "from op_audit_task t where t.pool_status = 'DONE' " +
            "and t.del_flag = 0 " + // R5-D-299
            "and t.receive_time is not null and t.finish_time is not null " +
            "group by t.task_type")
    List<Map<String, Object>> opsEfficiency();

    /** 10. 质检合格率：按质检类型统计合格率 */
    // 注：op_quality 无任何组织列（实体仅 auditor_id/checker_id），
    // 质检合格率是全中心规则，本方法不做数据范围办结，未挂 @DataScope。
    @Select("select q.check_type as checkType, count(*) as totalCnt, " +
            "sum(case when q.result = 'PASS' then 1 else 0 end) as passCnt, " +
            "round(sum(case when q.result = 'PASS' then 1 else 0 end) * 100.0 / count(*), 1) as passRate, " +
            "round(avg(q.score), 1) as avgScore " +
            "from op_quality q where q.del_flag = 0 " +
            "group by q.check_type")
    List<Map<String, Object>> qualityPassRate();

    /** 11. 影像统计：按业务类型统计影像批次及文档数。
     *  分库改造：img_batch 已迁 fssc_image 库，本查询改由 ReportQueryController
     *  经 ImageFacade.statBatchByBizType（image 库路由）提供，方法已移除。 */

    /** 12. 合同执行明细：合同付款进度及执行率 */
    @Select("<script>select c.contract_no as contractNo, c.contract_name as contractName, c.party_b_name as partyBName, " +
            "c.amount as contractAmount, ifnull(sum(p.total_amount), 0) as paidAmount, " +
            "round(ifnull(sum(p.total_amount), 0) * 100.0 / nullif(c.amount, 0), 1) as executionRate, " +
            "c.status, c.end_date as endDate " +
            "from cont_contract c " +
            // F-05：补 p.del_flag = 0——原规则把已软删的付款环节计入已付额，与本文件 contractSummary 不一致
            "left join exp_bill p on p.contract_id = c.id and p.del_flag = 0 and p.bill_type = 'PAY' " +
            "and p.status in ('APPROVED','POSTED') " +
            "where c.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and c.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and c.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "group by c.id, c.contract_no, c.contract_name, c.party_b_name, " +
            "c.amount, c.status, c.end_date order by c.id desc</script>")
    List<Map<String, Object>> contractExecution(@Param("scopeCompanyId") Long scopeCompanyId,
                                                @Param("scopeDeptIds") List<Long> scopeDeptIds);

    /** 13. 预算超支预警：预算使用率超80%的项目 */
    // 办结只按公司维；不按部门维（bg_item 公司级预算 dept_id 常为 NULL，
    // 追加 dept 条件会把「有预算却查不到」的既有缺陷在新端点上复刻）。
    @Select("<script>select bi.dim_key as dimKey, d.dept_name as deptName, i.item_name as itemName, bi.budget_year as budgetYear, bi.budget_period as budgetPeriod, " +
            "bi.total_amount as totalAmount, bi.used_amount as usedAmount, bi.occupy_amount as occupyAmount, " +
            "round(bi.used_amount * 100.0 / nullif(bi.total_amount, 0), 1) as usageRate, " +
            "case when bi.used_amount > bi.total_amount then 'OVER' else 'WARNING' end as alertLevel " +
            "from bg_item bi left join sys_dept d on bi.dept_id = d.id " +
            "left join md_expense_item i on bi.item_id = i.id where bi.del_flag = 0 " +
            "and bi.total_amount > 0 and bi.used_amount / bi.total_amount > 0.8 " +
            "<if test='scopeCompanyId != null'>and bi.company_id = #{scopeCompanyId}</if> " +
            "order by usageRate desc</script>")
    List<Map<String, Object>> budgetOverrunAlert(@Param("scopeCompanyId") Long scopeCompanyId);

    /** 14. 借款逾期分析：未还款且超过预计还款日的借款 */
    @Select("<script>select b.bill_no as billNo, b.apply_user_name as applyUserName, " +
            "b.total_amount as loanAmount, b.loan_balance as balance, " +
            "b.expect_repay_date as expectRepayDate, " +
            "datediff(curdate(), b.expect_repay_date) as overdueDays, " +
            "b.status " +
            "from exp_bill b where b.bill_type = 'LOAN' and b.status != 'VOID' " +
            "and b.loan_balance > 0 and b.expect_repay_date &lt; curdate() " +
            "and b.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "order by overdueDays desc</script>")
    List<Map<String, Object>> loanOverdue(@Param("scopeCompanyId") Long scopeCompanyId,
                                          @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                          @Param("scopeUserId") Long scopeUserId);

    /** 15. 凭证生成统计：按来源类型和月度统计凭证生成情况 */
    @Select("<script>select v.source_type as sourceType, " +
            "concat(v.period_year, '-', lpad(v.period_month, 2, '0')) as period, " +
            "count(*) as voucherCnt, sum(v.debit_total) as debitTotal, sum(v.credit_total) as creditTotal " +
            "from op_voucher v where v.del_flag = 0 " +
            "<if test='scopeCompanyId != null'>and v.company_id = #{scopeCompanyId}</if> " +
            "group by v.source_type, v.period_year, v.period_month " +
            "order by v.period_year desc, v.period_month desc, v.source_type</script>")
    List<Map<String, Object>> voucherStats(@Param("scopeCompanyId") Long scopeCompanyId);

    // 预置分析报表体系（新增5个预置报表）

    /** 16. 资产台账报表：资产卡片+累计折旧+净值 */
    @Select("<script>select a.asset_no as assetNo, a.asset_name as assetName, " +
            "c.category_name as categoryName, d.dept_name as deptName, " +
            "a.original_amount as originalAmount, a.accum_depr as accumDepr, " +
            "a.net_amount as netAmount, a.residual_value as residualValue, " +
            "a.depreciation_method as depreciationMethod, a.status, " +
            "a.purchase_date as purchaseDate, a.last_depr_date as lastDeprDate " +
            "from md_asset a " +
            "left join md_asset_category c on a.category_id = c.id " +
            "left join sys_dept d on a.dept_id = d.id " +
            "where 1=1 " +
            "<if test='scopeCompanyId != null'>and a.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and a.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "order by a.id desc</script>")
    List<Map<String, Object>> assetLedger(@Param("scopeCompanyId") Long scopeCompanyId,
                                          @Param("scopeDeptIds") List<Long> scopeDeptIds);

    /** 17. 折旧明细报表：逐资产逐月折旧明细 */
    @Select("<script>select a.asset_no as assetNo, a.asset_name as assetName, " +
            "d.period as period, d.monthly_depr as deprAmount, d.accum_depr_after as accumDepr, " +
            "d.net_amount_after as netAmount, d.depr_date as deprDate " +
            "from md_asset_depr_detail d " +
            "join md_asset a on d.asset_id = a.id " +
            "where 1=1 " +
            "<if test='period != null and period != \"\"'>and d.period = #{period}</if> " +
            "<if test='assetNo != null and assetNo != \"\"'>and a.asset_no like concat('%',#{assetNo},'%')</if> " +
            "<if test='scopeCompanyId != null'>and a.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and a.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "order by d.period desc, a.id desc limit 500</script>")
    List<Map<String, Object>> assetDeprDetail(@Param("period") String period,
                                               @Param("assetNo") String assetNo,
                                               @Param("scopeCompanyId") Long scopeCompanyId,
                                               @Param("scopeDeptIds") List<Long> scopeDeptIds);

    /** 18. 预算执行明细表：组织×部门 预算vs实际+差异率 */
    // 办结只按公司维，理由同 budgetOverrunAlert（dept_id NULL 陷阱）。
    @Select("<script>select bi.dim_key as dimKey, bi.budget_year as budgetYear, " +
            "bi.total_amount as totalAmount, bi.adjust_amount as adjustAmount, " +
            "bi.occupy_amount as occupyAmount, bi.used_amount as usedAmount, " +
            "(bi.total_amount + bi.adjust_amount - bi.used_amount) as remainAmount, " +
            "round(bi.used_amount * 100.0 / nullif(bi.total_amount + bi.adjust_amount, 0), 1) as executionRate " +
            "from bg_item bi where bi.del_flag = 0 and bi.total_amount > 0 " +
            "<if test='budgetYear != null'>and bi.budget_year = #{budgetYear}</if> " +
            "<if test='scopeCompanyId != null'>and bi.company_id = #{scopeCompanyId}</if> " +
            "order by executionRate desc</script>")
    List<Map<String, Object>> budgetExecutionDetail(@Param("budgetYear") Integer budgetYear,
                                                    @Param("scopeCompanyId") Long scopeCompanyId);

    /** 19. 费用多维分析：公司×部门×费用类型×期间交叉 */
    @Select("<script>select b.company_id as companyId, sc.company_name as companyName, " +
            "b.dept_id as deptId, d.dept_name as deptName, " +
            "l.item_name as itemName, " +
            "concat(b.period_year, '-', lpad(b.period_month, 2, '0')) as period, " +
            "sum(l.amount) as totalAmount, count(*) as billCnt " +
            "from exp_bill b " +
            "join exp_bill_line l on l.bill_id = b.id " +
            "left join sys_company sc on b.company_id = sc.id " +
            "left join sys_dept d on b.dept_id = d.id " +
            "where b.status in (" + EXPENSE_EFFECTIVE_STATUS + ") and b.period_year is not null " +
            "and b.del_flag = 0 and l.del_flag = 0 " + // R5-D-031
            "<if test='year != null'>and b.period_year = #{year}</if> " +
            "<if test='companyId != null'>and b.company_id = #{companyId}</if> " +
            "<if test='scopeCompanyId != null'>and b.company_id = #{scopeCompanyId}</if> " +
            "<if test='scopeDeptIds != null'>and b.dept_id in <foreach collection='scopeDeptIds' item='did' open='(' separator=',' close=')'>#{did}</foreach></if> " +
            "<if test='scopeUserId != null'>and b.apply_user_id = #{scopeUserId}</if> " +
            "group by b.company_id, sc.company_name, b.dept_id, d.dept_name, l.item_name, " +
            "b.period_year, b.period_month " +
            "order by b.period_year, b.period_month, totalAmount desc " +
            "limit 500</script>")
    List<Map<String, Object>> expenseAnalysis(@Param("year") Integer year,
                                               @Param("companyId") Long companyId,
                                               @Param("scopeCompanyId") Long scopeCompanyId,
                                               @Param("scopeDeptIds") List<Long> scopeDeptIds,
                                               @Param("scopeUserId") Long scopeUserId);

    /** 20. 资金流水明细表：账户×期间收支明细 */
    @Select("<script>select ba.bank_account as bankAccount, ba.account_name as accountName, " +
            "bs.trans_date as transDate, bs.trans_type as transType, " +
            "bs.amount as amount, bs.balance as balance, " +
            "bs.summary as summary, bs.opposite_name as counterparty " +
            "from op_bank_statement bs " +
            "left join md_bank_account ba on bs.bank_account_id = ba.id " +
            "where 1=1 " +
            "<if test='bankAccount != null and bankAccount != \"\"'>and ba.bank_account = #{bankAccount}</if> " +
            // 期间过滤改索引可用区间条件（禁 date_format 包裹日期列；&gt;/&lt; 为 XML 实体）
            "<if test='period != null and period != \"\"'>and bs.trans_date &gt;= str_to_date(concat(#{period}, '-01'), '%Y-%m-%d') and bs.trans_date &lt; date_add(str_to_date(concat(#{period}, '-01'), '%Y-%m-%d'), interval 1 month)</if> " +
            "<if test='scopeCompanyId != null'>and bs.company_id = #{scopeCompanyId}</if> " +
            // F-01：同刻多笔（9 行同 timestamp）时仅按 trans_date 排序无法定序，limit 500 的截断边界
            // 每次取到的行集合不同 ⇒ 补主键作唯一键默认处理
            "order by bs.trans_date desc, bs.id desc limit 500</script>")
    List<Map<String, Object>> treasuryFlow(@Param("bankAccount") String bankAccount,
                                            @Param("period") String period,
                                            @Param("scopeCompanyId") Long scopeCompanyId);
}
