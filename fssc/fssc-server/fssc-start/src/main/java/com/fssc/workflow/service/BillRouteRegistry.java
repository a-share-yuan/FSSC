// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * bizType 单据路由<b>唯一真值表</b>。
 *
 * <p>同一个 bizType 的查询范围若分散在多处各写一遍，彼此就会静默漂移。本表收敛的三处是：
 * <ul>
 *   <li>{@code WfEngineFacade.BIZ_NO_TABLES}——起流时写 {@code wf_instance.biz_no} 的表/列；</li>
 *   <li>{@code WfTaskController.fillBillInfoBatch()}——待办列表回填 单号/金额/标题 的表/列；</li>
 *   <li>{@code NodeOrgResolver.companyTable()}——COMPANY 范围角色取人时的公司归属表。</li>
 * </ul>
 * 漂移的典型形态：{@code RECEIVABLE_COLLECT} 前两处已改对 {@code rc_receivable}，
 * 取人侧仍写 {@code rc_collect_todo}；{@code FA_DEPRECIATION} 取人侧已改 {@code asset_depr_batch}，
 * 查询侧仍按 {@code FA} 前缀借 {@code md_asset} 的卡号（同一类"错值比空值危险"）。
 * 收敛到本类之后，改一个 bizType 只需改一处。
 *
 * 本表不改变任何一单的落点、待办或候选面。刻意更正只有一处：
 * <ol>
 *   <li>{@code FA_DEPRECIATION} 查询侧 {@code md_asset}→{@code asset_depr_batch}（与取人侧对齐）。</li>
 * </ol>
 * 其余差异原样保留并标注为"待定"：启用它们的后果是<b>换授权面</b>——
 * {@code companyTable()} 返回 null 时 {@code canOperate()} 会放宽放行，改成有值即收窄候选面，
 * 属业务/权限判定，不在查询合一这一范围内顺手做。
 */
public final class BillRouteRegistry {

    /**
     * @param fillTable 待办回填目标表；null＝该 bizType 不参与待办回填（不新增覆盖面）
     * @param noCol 单号列；null＝该表无单号列，单号置空属预期
     * @param amtCol 金额列；null＝无金额列
     * @param titleCol 标题列；null＝无标题列
     * @param bizNoTable 起流单号表；null＝回落 {@code fillTable}
     * @param bizNoCol 起流单号列；null＝回落 {@code noCol}
     * @param companyTable COMPANY 范围角色的公司归属表；null＝不参与公司解析（多数是"待启用"而非"不可用"）
     * @param ownerCol 「我的单据」按人过滤的归属人列；构造时按 {@link #homeTable()} 自动查
     *                    {@link #TABLE_OWNER}，无需逐行登记；null＝该表没有登记人列 ⇒ 前端明示不支持
     */
    public record Route(String fillTable, String noCol, String amtCol, String titleCol,
                        String bizNoTable, String bizNoCol, String companyTable,
                        String ownerCol) {

        public String bizNoTableOrDefault() { return bizNoTable != null ? bizNoTable : fillTable; }

        public String bizNoColOrDefault() { return bizNoCol != null ? bizNoCol : noCol; }

        /** 该 bizType 的业务主表：待办回填表优先，其次取人表（两者不一致的行见 {@link #EXACT} 注释）。 */
        public String homeTable() { return fillTable != null ? fillTable : companyTable; }

        public Route {
            if (ownerCol == null) {
                String home = fillTable != null ? fillTable : companyTable;
                ownerCol = home == null ? null : TABLE_OWNER.get(home);
            }
        }
    }

    /**
     * 表 → 归属人（"这单是谁登记的"）列。<b>未登记的表即没有按人检索的列</b>，
     * 由 {@link #myDocUnsupported()} 显式报给前端，而不是静默少数据。
     * 列名与列注释按 information_schema 逐列核对：
     * <ul>
     *   <li>{@code apply_user_id}＝报销/预算调整/资产申请的报账人；</li>
     *   <li>{@code applicant_id}＝付款计划经办人、档案销毁申请人；{@code requester_id}＝借阅申请人；
     *       {@code filing_user_id}＝归档人；</li>
     *   <li>{@code create_by}＝建单人（主数据/总账/合同/采购/应收应付/发票/对账等）。</li>
     * </ul>
     * 刻意<em>不</em>登记：{@code tr_fund_plan.owner_id}（列注释＝资金负责人，是业务责任人不是登记人，
     * 拿它当"我的单据"会把别人的计划算进来）、{@code ar_claim} 只有处理人列。
     */
    private static final Map<String, String> TABLE_OWNER = Map.ofEntries(
            Map.entry("exp_bill", "apply_user_id"),
            Map.entry("bg_adjust", "apply_user_id"),
            Map.entry("fa_cer_request", "apply_user_id"),
            Map.entry("pay_plan", "applicant_id"),
            Map.entry("op_archive", "filing_user_id"),
            Map.entry("archive_access_log", "requester_id"),
            Map.entry("archive_destroy_approval", "applicant_id"),
            Map.entry("cont_contract", "create_by"),
            Map.entry("po_order", "create_by"),
            Map.entry("po_receipt", "create_by"),
            Map.entry("op_pay_batch", "create_by"),
            Map.entry("bg_scheme", "create_by"),
            Map.entry("rc_receivable", "create_by"),
            Map.entry("rc_bad_debt", "create_by"),
            Map.entry("inv_invoice", "create_by"),
            Map.entry("supplier_recon", "create_by"),
            Map.entry("ar_recon_record", "create_by"),
            Map.entry("asset_depr_batch", "create_by"),
            Map.entry("tax_refund", "create_by"),
            Map.entry("md_bank_account", "create_by"),
            Map.entry("md_supplier", "create_by"),
            Map.entry("crm_customer", "create_by"),
            Map.entry("gl_inter_offset", "create_by"),
            Map.entry("gl_month_close", "create_by"),
            Map.entry("gl_consolidate", "create_by"),
            Map.entry("gl_shared_master", "create_by"),
            Map.entry("gl_journal_entry", "create_by"),
            Map.entry("gl_tax_provision", "create_by"),
            Map.entry("gl_salary_run", "create_by")
    );

    /**
     * 业务主表 → 该表的台账页。<b>按表而不是按 bizType 登记</b>：一张表可能承载多种 bizType
     * （{@code gl_journal_entry}＝计提/待摊/其他收入/手工凭证四合一、{@code po_order}＝PO/PURCHASE/
     * PURCHASE_ORDER_REVIEW 三写），行内没有类型列时无法判定某一行到底属于哪一种 ⇒ 跳转规则只能落在表上，
     * 拿首行 bizType 去查路由＝给整表贴一个可能错的标签（错值比空值危险）。
     *
     * <p>费用报销族（exp_bill）不登记：那一族行内有 {@code bill_type + bill_sub_type}，可细到 6 个填单页，
     * 前端 {@code constants/billStatus.js#editBillPath} 已持有该规则，这里再写一遍＝第二处真值。
     */
    private static final Map<String, String> TABLE_LEDGER = Map.ofEntries(
            Map.entry("cont_contract", "/contract"),
            Map.entry("po_order", "/purchase/order"),
            Map.entry("op_pay_batch", "/pay"),
            Map.entry("pay_plan", "/purchase/pay-plan"),
            Map.entry("bg_adjust", "/budget/adjust"),
            Map.entry("bg_scheme", "/budget/scheme"),
            Map.entry("rc_receivable", "/receivable/receivable"),
            Map.entry("rc_bad_debt", "/arflow/badDebt"),
            Map.entry("inv_invoice", "/invoice/issue"),
            Map.entry("supplier_recon", "/pay/recon"),
            Map.entry("bank_reconcile", "/bank/reconcile"),
            Map.entry("gl_inter_offset", "/glex/special/inter"),
            Map.entry("gl_month_close", "/glex/special/monthclose"),
            Map.entry("gl_consolidate", "/glex/special/consolidate"),
            Map.entry("gl_shared_master", "/glex/special/master"),
            Map.entry("gl_journal_entry", "/glex/special/journal"),
            Map.entry("gl_tax_provision", "/glex/special/tax"),
            Map.entry("gl_salary_run", "/glex/special/salary"),
            Map.entry("tax_refund", "/glex/tax-refund"),
            Map.entry("fa_cer_request", "/asset/asset"),
            Map.entry("asset_depr_batch", "/asset/depr-batch"),
            Map.entry("op_archive", "/archive/archive"),
            Map.entry("archive_access_log", "/archive/access"),
            Map.entry("archive_destroy_approval", "/archive/destroy"),
            Map.entry("md_bank_account", "/master/bank")
    );

    /**
     * 业务主表 → 单据族中文名（同一张表多种 bizType 时的兜底类型标签，见 {@link #TABLE_LEDGER} 注释）。
     * 前端 {@code constants/enums} 的 BIZ_TYPE 名称表按编码取值，这里给的是"表级"规则，两者不重叠。
     */
    private static final Map<String, String> TABLE_DOMAIN = Map.ofEntries(
            Map.entry("cont_contract", "合同"),
            Map.entry("po_order", "采购订单"),
            Map.entry("op_pay_batch", "付款批次"),
            Map.entry("pay_plan", "付款计划"),
            Map.entry("bg_adjust", "预算调整"),
            Map.entry("bg_scheme", "预算方案"),
            Map.entry("rc_receivable", "应收单"),
            Map.entry("rc_bad_debt", "坏账计提"),
            Map.entry("inv_invoice", "发票"),
            Map.entry("supplier_recon", "供应商对账单"),
            Map.entry("ar_recon_record", "应收对账记录"),
            Map.entry("bank_reconcile", "银企对账单"),
            Map.entry("gl_inter_offset", "内部冲销"),
            Map.entry("gl_month_close", "月末结账"),
            Map.entry("gl_consolidate", "合并报表"),
            Map.entry("gl_shared_master", "共享主数据"),
            Map.entry("gl_journal_entry", "总账凭证"),
            Map.entry("gl_tax_provision", "税金计提"),
            Map.entry("gl_salary_run", "薪酬计提"),
            Map.entry("tax_refund", "退税"),
            Map.entry("fa_cer_request", "资产卡片申请"),
            Map.entry("asset_depr_batch", "折旧计提批"),
            Map.entry("op_archive", "会计档案"),
            Map.entry("archive_access_log", "档案借阅"),
            Map.entry("archive_destroy_approval", "档案销毁"),
            Map.entry("md_bank_account", "银行账户")
    );

    /** 该表的台账页；null＝没有可跳转的台账页（前端只给"详情/无操作"，不给死链）。 */
    public static String ledgerRouteOfTable(String table) {
        return table == null ? null : TABLE_LEDGER.get(table);
    }

    /** 该表的单据族中文名；null＝行内自带更细的类型（exp_bill 族）。 */
    public static String domainLabelOfTable(String table) {
        return table == null ? null : TABLE_DOMAIN.get(table);
    }

    /** exp_bill 族（含带 EXP_ 前缀的运行期真值）＝工作台内联表单族，跳转规则在前端。 */
    public static boolean isInlinedExpense(Route r) {
        return r != null && "exp_bill".equals(r.homeTable());
    }

    private BillRouteRegistry() {
    }

    /** 待办回填＋取人（起流单号回落 fillTable/noCol） */
    private static Route f(String fillTable, String noCol, String amtCol, String titleCol, String companyTable) {
        return new Route(fillTable, noCol, amtCol, titleCol, null, null, companyTable, null);
    }

    /** 只一起流单号（不参与待办回填） */
    private static Route bizNoOnly(String table, String col) {
        return new Route(null, null, null, null, table, col, null, null);
    }

    /** 只取人（既不回填也不起流取单号） */
    private static Route companyOnly(String table) {
        return new Route(null, null, null, null, null, null, table, null);
    }

    /** 全量显式行（前缀族见 {@link #of(String)} 与 {@link #companyOf(String)}） */
    private static final Map<String, Route> EXACT = new ConcurrentHashMap<>(Map.ofEntries(
            // —— 资金域 ——
            // 死映射：treasury_transfer / asset_card 两张表在 information_schema 查无，
            // 两 bizType 在 wf_instance/wf_task 均无行 ⇒ 暂时潜伏。刻意不猜目标表：
            // 猜表正是前文"错值比空值危险"的成因。
            Map.entry("TREASURY", f("treasury_transfer", "transfer_no", "amount", "title", null)),
            Map.entry("ASSET", f("asset_card", "card_no", "original_value", "asset_name", null)),
            Map.entry("TR_TRANSFER", f("tr_transfer", "transfer_no", "amount", "purpose", "tr_transfer")),
            // TR_UPLOAD 与 TR_TRANSFER 同表同列，但 companyTable() 里没有它这一行 ⇒ 公司解析为 null。
            // 暂不补：启用＝收窄候选面，属授权口径待定（下文同理，标注"取人未启用"）。
            Map.entry("TR_UPLOAD", f("tr_transfer", "transfer_no", "amount", "purpose", null)),
            Map.entry("TR_PAY", f("op_pay_batch", "batch_no", "total_amount", "batch_no", "op_pay_batch")),
            Map.entry("TR_PAY_FAIL", f("op_pay_batch", "batch_no", "total_amount", "batch_no", "op_pay_batch")),
            // tr_credit_bill / tr_bill_settle 都有 company_id（后者由 TrBillSettleService.orgGuard
            // .currentCompany() 写入），create_by 也早已补出；没有公司列的是 bank_reconcile。
            // TR_BILL 若不登记 tr_bill_settle，COMPANY 范围角色的候选名单会为空
            // （NodeOrgResolver 侧取人 null）⇒ TB_1／TB_2 双 ESCALATE 到同一个人，再撞连办校验被迫转办。
            // TR_RECEIPT 无在役实例，先留 null（同前提成立但未核实）。
            Map.entry("TR_RECEIPT", f("tr_credit_bill", "bill_no", "amount", "bill_no", null)),
            Map.entry("TR_BILL", f("tr_bill_settle", "bill_no", "amount", "bill_no", "tr_bill_settle")),
            Map.entry("TR_PLAN", f("tr_fund_plan", "plan_no", "outcome_amount", "plan_no", "tr_fund_plan")),
            Map.entry("TR_PLAN_ADJUST", f("tr_fund_plan", "plan_no", "outcome_amount", "plan_no", "tr_fund_plan")),
            Map.entry("TR_BANK_RECON", f("bank_reconcile", "serial_no", null, "serial_no", null)),
            // 银行账户主数据/印鉴变更：md_bank_account 无单号列 ⇒ 单号空属预期；公司按 create_by 归属。
            Map.entry("TR_BANK_MASTER", f("md_bank_account", null, null, "account_name", "md_bank_account")),
            Map.entry("TR_SEAL_CHANGE", f("md_bank_account", null, null, "account_name", "md_bank_account")),

            // —— 采购 / 合同 / 预算 ——
            // po_order 有 company_id 列，但 companyTable() 只认 PURCHASE_ORDER_REVIEW ⇒
            // PO/PURCHASE 两个 bizType 取人 null（＝canOperate 放宽）。补登属授权口径待定，暂不启用。
            Map.entry("PO", f("po_order", "po_no", "amount", "supplier_name", null)),
            Map.entry("PURCHASE", f("po_order", "po_no", "amount", "supplier_name", null)),
            Map.entry("PURCHASE_ORDER_REVIEW", f("po_order", "po_no", "amount", "supplier_name", "po_order")),
            Map.entry("CONTRACT", f("cont_contract", "contract_no", "amount", "contract_name", "cont_contract")),
            Map.entry("CONTRACT_APPROVE", f("cont_contract", "contract_no", "amount", "contract_name", "cont_contract")),
            // BUDGET→bg_adjust：整表无金额列（金额在 bg_adjust_line 明细），无 company_id 但有 create_by。
            Map.entry("BUDGET", f("bg_adjust", "adjust_no", null, "reason", null)),
            Map.entry("BG_SCHEME", f("bg_scheme", "scheme_code", null, "scheme_name", "bg_scheme")),
            Map.entry("BG_APPROVE", f("bg_scheme", "scheme_code", null, "scheme_name", "bg_scheme")),
            // 预算调整单进流程后的镜像取数路由（与 wf_process_config 的 BG_ADJUST 行同名）
            Map.entry("BG_ADJUST", f("bg_adjust", "adjust_no", null, "reason", null)),

            // —— 应收 / 发票 / 对账 ——
            Map.entry("RECEIVABLE", f("rc_receivable", "bill_no", "amount", "bill_no", "rc_receivable")),
            // bizId 是<b>应收单</b> id 而非催收待办 id —— 按催收待办取数会查空，
            // 把应收单的落点错记成"没有这张单"。
            Map.entry("RECEIVABLE_COLLECT", f("rc_receivable", "bill_no", "amount", "bill_no", "rc_receivable")),
            Map.entry("INVOICE_ISSUE", f("inv_invoice", "invoice_no", "amount", "invoice_title", "inv_invoice")),
            Map.entry("RECON_ACTIVE", f("supplier_recon", "recon_no", "payable_amount", "supplier_name", "supplier_recon")),
            // RECON_PASSIVE 的查询路由已随旧门摘除：三张记录表零行、无历史实例可追溯。
            // ar_recon_record 补 amount 列后两行同值——同表的「我的单据」查询按表去重取首行，
            // 只改 AR_RECON 会让 amount 位的取值随 Map.ofEntries 迭代次序漂移。
            Map.entry("AR_RECON", f("ar_recon_record", "period", "amount", "record_type", "ar_recon_record")),
            Map.entry("AR_REPORT", f("ar_recon_record", "period", "amount", "record_type", "ar_recon_record")),
            Map.entry("AR_CLAIM", f("ar_claim", "bill_no", "amount", "reason", "ar_claim")),
            // EER31-01 只在起流单号映射里出现，既不回填也不取人。
            Map.entry("EER31-01", bizNoOnly("ar_claim", "bill_no")),
            // BAD_DEBT（不带 _PROV/_CONFIRM 的那条老 bizType）三处各说各话：起流单号查 rc_receivable、
            // 待办走 rc_bad_debt LEFT JOIN rc_receivable 的特例方法、取人查 rc_bad_debt。
            // 这里如实登记三值；待办回填仍由 WfTaskController.fillBadDebt() 承担 ⇒ fillTable 置 null 防重复。
            Map.entry("BAD_DEBT", new Route(null, null, null, null, "rc_receivable", "bill_no", "rc_bad_debt", null)),
            Map.entry("BAD_DEBT_PROV", f("rc_bad_debt", "period", "amount", "period", "rc_bad_debt")),
            Map.entry("BAD_DEBT_CONFIRM", f("rc_bad_debt", "period", "amount", "period", "rc_bad_debt")),

            // —— 资产 ——
            Map.entry("FA_CER", f("fa_cer_request", "request_no", "amount", "asset_name", "fa_cer_request")),
            // 查询侧按 FA 前缀借 md_asset 的资产卡号是错的：折旧批次 id 若与资产卡 id 撞号，
            // 待办会显示<b>另一张资产</b>的卡号。取人侧早已是 asset_depr_batch，此处与它对齐。
            // 该表有 batch_no/total_amount，标题列取 period（与 rc_bad_debt 同一取值规则）。
            Map.entry("FA_DEPRECIATION", f("asset_depr_batch", "batch_no", "total_amount", "period", "asset_depr_batch")),

            // —— 存货（前缀族见 of()：md_inventory/item_code/out_amount/item_name）——

            // —— 总账域 ——
            Map.entry("GL_OFFSET", f("gl_inter_offset", "offset_no", "amount", "offset_no", "gl_inter_offset")),
            Map.entry("GL_MONTH_CLOSE", f("gl_month_close", "close_no", "diff", "result_msg", "gl_month_close")),
            Map.entry("GL_MERGE", f("gl_consolidate", "report_no", null, "report_no", "gl_consolidate")),
            Map.entry("GL_SHARED_MASTER", f("gl_shared_master", "req_no", null, "req_no", "gl_shared_master")),
            Map.entry("GL_ACCRUAL", f("gl_journal_entry", "entry_no", "amount", "summary", "gl_journal_entry")),
            Map.entry("GL_DEFER", f("gl_journal_entry", "entry_no", "amount", "summary", "gl_journal_entry")),
            Map.entry("GL_OTHER_RECEIPT", f("gl_journal_entry", "entry_no", "amount", "summary", "gl_journal_entry")),
            Map.entry("GL_MANUAL_VOUCHER", f("gl_journal_entry", "entry_no", "amount", "summary", "gl_journal_entry")),
            // 起 gl_journal_entry 的是五合一（计提/待摊/手工/其他收款/盘点差异结转），盘点差异那一型
            // 不入本表会有三处后果：待办「去处理」在 bizEntry.js 查不到落点；待办的单号/金额/标题三列空；
            // sameTableDomains 按 fillTable 分组缺行，联查「声明域落空·同表纠偏」认不出它。
            // 五格与同表另四型逐格一致（entry_no/amount/summary + companyTable=gl_journal_entry 取人，
            // 该表有 company_id/create_by）⇒ 不给第五型另立规则。
            Map.entry("GL_CHECK_DIFF", f("gl_journal_entry", "entry_no", "amount", "summary", "gl_journal_entry")),
            Map.entry("GL_TAX_PROV", f("gl_tax_provision", "provision_no", "tax_amount", "provision_no", "gl_tax_provision")),
            // GL_TAX_PAY 不建新单据、复用当期计提行 ⇒ 单号列与计提同。
            Map.entry("GL_TAX_PAY", f("gl_tax_provision", "provision_no", "tax_amount", "provision_no", "gl_tax_provision")),
            Map.entry("GL_SALARY_PROV", f("gl_salary_run", "run_no", "total_amount", "run_no", "gl_salary_run")),
            Map.entry("GL_SALARY_PAY", f("gl_salary_run", "run_no", "total_amount", "run_no", "gl_salary_run")),
            Map.entry("GL_TAX_REFUND", f("tax_refund", "refund_no", "base_amount", "refund_type", "tax_refund")),

            // —— 付款计划 ——
            Map.entry("PAY_PLAN_BASE", f("pay_plan", "plan_no", "amount", "plan_no", "pay_plan")),
            Map.entry("PAY_PLAN_PLATE", f("pay_plan", "plan_no", "amount", "plan_no", "pay_plan")),

            // —— 档案四链（这四张表既无 company_id 也无 create_by ⇒ COMPANY 范围角色不可用）——
            Map.entry("FM_ARCHIVE", f("op_archive", "archive_no", null, "title", "op_archive")),
            Map.entry("FM_BORROW", f("archive_access_log", null, null, "access_reason", "archive_access_log")),
            Map.entry("FM_DOC_FLOW", f("dispatch_task", "task_code", "amount", "task_code", "dispatch_task")),
            Map.entry("FM_DESTROY", f("archive_destroy_approval", "destroy_code", null, "destroy_reason", "archive_destroy_approval")),

            // —— 主数据（无 company_id，公司按 create_by 建单人归属；不参与待办回填）——
            Map.entry("CUSTOMER_MAINT", companyOnly("crm_customer")),
            Map.entry("SUPPLIER_ADD", companyOnly("md_supplier")),

            // —— 发票匹配 ——
            // 起流单号映射（facade）里原本就有这两型，待办回填却缺行 ⇒ fillTable 曾是 null。
            // 补行依据：`#/approval` 列表按 bill_type 显示（bizResolved 判据），exp_bill 行必然存在且
            // id＝bizId 一对一（bizId 就是那张付款单主键，配对查询无跨域撞号面）⇒ "显示的是本行自己的字段"。
            // 补行只消灭「无关联单据／金额 -」的假阴性，不引入错值。INVOICE_MATCH_MANUAL 同型。
            // 取人环节维持 null——启用＝收窄候选面，属授权口径待定（见类注释）。
            Map.entry("INVOICE_MATCH", f("exp_bill", "bill_no", "total_amount", "title", null)),
            Map.entry("INVOICE_MATCH_MANUAL", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("PTP03", f("exp_bill", "bill_no", "total_amount", "title", null)),

            // —— 费用报销族（exp_bill；带 EXP_ 前缀的运行期真值同样落这张表）——
            Map.entry("CLAIM", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("CLAIM_TRAVEL", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("LOAN", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("REPAY", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("TRAVEL", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("TRAIN", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("PREPAY", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("SETTLE", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            // APPLY/MEETING/PAY 三格：待办回填走 exp_bill（controller 的 EXP_BILL_TYPES 含它们），
            // 但 companyTable() 的 EXPENSE_BIZ <b>不含</b> ⇒ 取人 null。两套集合的差集原样保留（待定）。
            // MEETING 是例外：它只在 facade 的 11 项类型集合里、<b>不在</b> controller 的 10 项里
            // ⇒ 起流单号取 exp_bill.bill_no、待办不回填，两值分开登记（合并成一行就会顺手扩覆盖面）。
            Map.entry("APPLY", f("exp_bill", "bill_no", "total_amount", "title", null)),
            Map.entry("MEETING", bizNoOnly("exp_bill", "bill_no")),
            Map.entry("PAY", f("exp_bill", "bill_no", "total_amount", "title", null)),
            Map.entry("EXP_CLAIM", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("EXP_LOAN", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            // EXP_REPAY 是运行期真 bizType（ExpenseBillService 提交时按 bill_type 映射
            // REPAY→EXP_REPAY）。若本表只有不带前缀的 REPAY，of("EXP_REPAY") 会走完
            // 显式映射→FA/INV 前缀→BIZNO_EXP_TYPES 三档全部让路（那 11 项是<b>不带前缀</b>的老名），
            // 返回 null ⇒ 起流时 wf_instance.biz_no 取不到单号、待办的单号/金额/标题三列也回填不上。
            // 症状是只有这一型空着：同一批 EXP_LOAN、EXP_CLAIM、EXP_PAY 的实例都有号。
            // 取人环节没坏（companyOf 认 EXP_ 前缀），坏的只有查询环节，正是本表要消灭的"三处各写一遍"漂移。
            Map.entry("EXP_REPAY", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("EXP_APPLY", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill")),
            Map.entry("EXP_PAY", f("exp_bill", "bill_no", "total_amount", "title", "exp_bill"))
    ));

    /**
     * 为可配置表单登记台账行。
     *
     * <p>客户在自己的配置里新增一种单据时，不需要改本类：通用运行库的表名与列名是固定的
     * （{@code biz_form_bill.bill_no / title / company_id}），把 bizType 注册进来就能
     * 参与起流取单号与待办回填。
     *
     * <p>幂等：已存在同名 bizType 时保留原行，不覆盖既有财务单据的映射。
     *
     * @param bizType 流程路由键（md_form_config.biz_type 或 form_code）
     */
    public static void registerGenericBill(String bizType) {
        if (bizType == null || bizType.isBlank() || EXACT.containsKey(bizType)) {
            return;
        }
        EXACT.put(bizType, f("biz_form_bill", "bill_no", null, "title", "biz_form_bill"));
    }

    /** 待办回填走 mapper（selectBatchIds 双向匹配 EXP_ 前缀）的类型，见 WfTaskController。 */
    public static final Set<String> EXP_BILL_FILLED = Set.of(
            "CLAIM", "CLAIM_TRAVEL", "LOAN", "REPAY", "PAY", "TRAVEL", "TRAIN",
            "PREPAY", "SETTLE", "APPLY");

    /**
     * <b>派单／子流程类</b> bizType：以 {@code exp_bill} 为单据载体（bizId 就是 exp_bill.id），
     * 跑的却是<b>围绕这张单的另一种动作</b>，不是这张单自身的审批主流程：
     * <ul>
     *   <li>{@code INVOICE_MATCH} / {@code INVOICE_MATCH_MANUAL}——三单匹配（含手工匹配）异常派单链，
     *       由 {@code ThreeWayMatchService.route()} 在单据已有主流程之后再起一枚实例；</li>
     *   <li>{@code PTP03}——发票校验异常人工复审链（{@code ThreeWayMatchController} 注释即"PTP03 INVOICE_MATCH"）。</li>
     * </ul>
     * 用途＝联查/追溯判「这单批完了没有」时必须排除它们：同一张单据名下既有主流程实例
     * （SETTLE 停在扫描岗）又有派单实例（INVOICE_MATCH 已 APPROVED），
     * 按"最后完成的那枚"查询就把「派单批完」显示成「已批」，且主流程真实卡点整条丢失。
     *
     * <p>登记在<b>本表</b>而不是散进 {@code BillTraceChainController}：这张表就是 bizType 的唯一真值表，
     * "某类型是不是单据自身的主流程"属于同一类事实，写在别处会长出第二处真值。
     */
    public static final Set<String> SUB_CHAIN_ON_BILL = Set.of(
            "INVOICE_MATCH", "INVOICE_MATCH_MANUAL", "PTP03");

    /** 该 bizType 是否派单/子流程类（null 安全）。 */
    public static boolean isSubChainOnBill(String bizType) {
        return bizType != null && SUB_CHAIN_ON_BILL.contains(bizType.trim().toUpperCase());
    }

    /**
     * 流程 bizType → 凭证 {@code source_doc_type} 的<b>写侧域名别名</b>（读侧补臂）。
     * <p>同一张单据在两侧可能用不同的名字：CONTRACT_APPROVE（流程域）vs CONTRACT（凭证写侧）、
     * FA_DEPRECIATION（流程域）vs ASSET_DEPRECIATION（{@code AssetDeprBatchService→generateAssetVoucher} 写侧）。
     * 追溯/联查按 bizType 成对匹配凭证就<b>恒 0 命中</b>——数据在、页面说没有＝假灰断链。
     * <p>这里只登记<b>现库实证过</b>的两对，不做前缀推断（错值比空值危险）。写侧统一命名
     * 会动既有凭证规则，属待定范围；本别名只做读侧补臂，不改任何写入值。
     */
    private static final Map<String, Set<String>> VOUCHER_DOMAIN_ALIASES = Map.of(
            "CONTRACT_APPROVE", Set.of("CONTRACT"),
            "FA_DEPRECIATION", Set.of("ASSET_DEPRECIATION")
    );

    /** 按 bizType 查凭证时并入写侧别名域（返回含自身的集合，null 安全、去空白）。 */
    public static Set<String> voucherSourceDomainsOf(String bizType) {
        if (bizType == null || bizType.isBlank()) return Set.of();
        String t = bizType.trim();
        Set<String> alias = VOUCHER_DOMAIN_ALIASES.get(t);
        return alias == null || alias.isEmpty() ? Set.of(t)
                : Stream.concat(Stream.of(t), alias.stream())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * <b>无审批流、只以凭证配对存在</b>的单据域（后端关联点半）：
     * 现库 {@code wf_instance} biz_type 普查（36 型）没有 PO_RECEIPT——收货不是一条审批链，
     * 收货动作由采购订单审批通过后的下推产生，但它有正经凭证
     * （现库 id=9/12/19/26/42，{@code source_doc_type='PO_RECEIPT'＋source_doc_id＝收货行 id}，借 1405／贷 2203）。
     * 追溯链的通用关联点以 {@code wf_instance(biz_type,biz_id)} 为入口⇒ 对这种域必然「本域没有流程实例」，
     * 于是数据在、入口开不了（＝334 里 S4 队把「追溯」钮押后等的那根关联点）。
     * 本集合＝允许按（source_doc_type, 本域主键）<b>配对</b>直读凭证段的域；仍是成对匹配，不是裸主键跨域。
     */
    private static final Set<String> NO_FLOW_VOUCHER_DOMAINS = Set.of("PO_RECEIPT");

    /** 该域是否「无审批流、只以凭证配对存在」（null 安全）。 */
    public static boolean isNoFlowVoucherDomain(String bizType) {
        return bizType != null && NO_FLOW_VOUCHER_DOMAINS.contains(bizType.trim().toUpperCase());
    }

    /** 单据表 → 该表上登记过的流程域（＝"同一张单据可能挂的几种流程"分组）。 */
    private static final Map<String, Set<String>> DOMAINS_BY_FILL_TABLE = groupDomainsByTable();

    private static Map<String, Set<String>> groupDomainsByTable() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (Map.Entry<String, Route> e : EXACT.entrySet()) {
            String table = e.getValue().fillTable();
            // 无回填表＝谈不上"同一张单据"（如 BAD_DEBT 老类型只挂 rc_receivable 的单号）
            if (table == null) continue;
            // 派单/子流程链不并入主流程业务域：把它们当"同一张单的另一种主流程"正是 的成因
            if (SUB_CHAIN_ON_BILL.contains(e.getKey())) continue;
            m.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(e.getKey());
        }
        return m;
    }

    /**
     * 与 {@code bizType} 共用同一张单据表的<b>兄弟流程域</b>（含自身）；未登记/无回填表时只回自身。
     *
     * <p>用途＝联查/追溯入口的「声明域落空」纠正（的发放半、同主键双轮）。
     * 业务页的列表行只有"这一张单"的视角，而一张表可能轮流向它发起多条流程：
     * <ul>
     *   <li>{@code gl_salary_run}＝计提（GL_SALARY_PROV）与发放（GL_SALARY_PAY）共用同一行；</li>
     *   <li>{@code tr_transfer}＝调拨（TR_TRANSFER）与上存（TR_UPLOAD）共用同一张表；</li>
     *   <li>{@code gl_journal_entry}＝预提/待摊/手工/其他收款四合一；{@code md_bank_account}＝开户与印鉴变更。</li>
     * </ul>
     * 入口在这种页面上只能声明其中一个域，声明错了按字面执行就会返回"本域无流程实例"，
     * 办理人依然看不到另一条链。纠正<b>只在这张表的业务域内</b>发生——跨表撞号（的
     * {@code bank_reconcile.id} 撞上 {@code exp_bill.id}）不在这个族内，因此本方法不构成串号通道。
     */
    public static Set<String> sameTableDomains(String bizType) {
        if (bizType == null || bizType.isBlank()) return Set.of();
        String t = bizType.trim();
        Route r = EXACT.get(t);
        if (r == null || r.fillTable() == null) return Set.of(t);
        Set<String> family = DOMAINS_BY_FILL_TABLE.get(r.fillTable());
        return family == null ? Set.of(t) : family;
    }

    private static final Set<String> BIZNO_EXP_TYPES = Set.of(
            "CLAIM", "CLAIM_TRAVEL", "APPLY", "TRAVEL", "TRAIN", "MEETING",
            "LOAN", "REPAY", "PAY", "PREPAY", "SETTLE");

    /** 原先 NodeOrgResolver.EXPENSE_BIZ 的 8 项（与上面 11 项的差集＝APPLY/MEETING/PAY，见类注释）。 */
    private static final Set<String> COMPANY_EXPENSE_TYPES = Set.of(
            "CLAIM", "CLAIM_TRAVEL", "LOAN", "REPAY", "TRAVEL", "TRAIN", "PREPAY", "SETTLE");

    private static final Route FA_PREFIX_FILL = f("md_asset", "asset_no", "original_amount", "asset_name", "md_asset");
    private static final Route INV_PREFIX_FILL = f("md_inventory", "item_code", "out_amount", "item_name", "md_inventory");

    /**
     * 查询路由（待办回填＋起流单号）。查不到返回 null＝该 bizType 既不回填也不取单号。
     * 注意原先门面用的是 {@code startsWith("FA")}（无下划线），这里保持一致以免多覆盖。
     */
    public static Route of(String bizType) {
        if (bizType == null || bizType.isBlank()) return null;
        Route r = EXACT.get(bizType);
        if (r != null) return r;
        if (bizType.startsWith("FA")) return FA_PREFIX_FILL;
        if (bizType.startsWith("INV")) return INV_PREFIX_FILL;
        if (BIZNO_EXP_TYPES.contains(bizType)) return EXACT.get("CLAIM");
        return null;
    }

    /**
     * 取人路由（COMPANY 范围角色的公司归属表）。返回 null＝不参与公司解析，
     * 后果由 {@code canOperate()} 对 null 公司"放宽放行"承担（＝候选面扩到全租户），办结它属授权口径待定。
     */
    public static String companyOf(String bizType) {
        if (bizType == null || bizType.isBlank()) return null;
        if (bizType.startsWith("EXP_") || COMPANY_EXPENSE_TYPES.contains(bizType)) return "exp_bill";
        Route r = EXACT.get(bizType);
        if (r != null) return r.companyTable();
        if (bizType.startsWith("INV_")) return "md_inventory";
        if (bizType.startsWith("FA_")) return "md_asset";
        return null;
    }

    /** 全库显式行（供待办回填逐项遍历；前缀族由调用方按前缀合并成一次查询） */
    public static Map<String, Route> exactRoutes() {
        return EXACT;
    }

    /** 该 bizType 是否有显式行——前缀族回填据此让路（原先 {@code INVOICE_*} 被 "INV" 前缀二次覆盖就是缺这条判断） */
    public static boolean hasExact(String bizType) {
        return bizType != null && EXACT.containsKey(bizType);
    }
}
