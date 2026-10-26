// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.openapi.linku.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * LinkU 开放接口 <b>查询</b> Mapper（F-2 / F-7）。替代原 BCP 直连 FSSC 库的 10 个只读 Mapper。
 *
 * <h3>设计要点</h3>
 * <ol>
 *   <li>一律返回 {@code List<Map<String,Object>>}：列别名即报文键，不暴露表结构、不引入实体类，
 *       schema 代际差异在服务端收敛（BCP 侧只消费稳定 JSON）。</li>
 *   <li>日期列一律 {@code DATE_FORMAT} 成 {@code yyyy-MM-dd} 字符串，避免客户端/驱动对
 *       DATE/DATETIME 的序列化歧义。</li>
 *   <li>租户过滤放在<b>主表</b> WHERE 与<b>关联表</b> ON 子句（而非 WHERE），
 *       避免把 LEFT JOIN 退化成 INNER JOIN 而丢行。</li>
 *   <li>本 Mapper 在 {@code TenantContext.runIgnoreTenant} 下执行，故不会叠加租户插件条件，
 *       tenantId 由入参显式决定（与回调实现同范式）。</li>
 * </ol>
 *
 * <p>查询范围与原 BCP Mapper 逐条对齐（见 {@code com.unierp.bcp.adapter.fssc.mapper}），
 * 含各类「不成立的最终状态」排除（VOID 采购订单 / REJECTED 应收单等）。</p>
 */
@Mapper
public interface LinkuOpenQueryMapper {

    /**
     * 应付关联点环节（AP）—— {@code po_order}（现役库 OTC 代）。
     * 返回 {@code amount} 与 {@code receivedAmount}，未清规则交由 BCP 侧按自身配置推导
     * （{@code bcp.adapter.fssc.ap-outstanding-mode}），保持与原直连行为一致。
     *
     * <p><b>G2R-10（2026-10-19 真跑实证，规则统一）</b>：{@code po_order.amount} 是<b>不含税</b>数
     * （该表根本没有税额列，无从换算），而付款环节 {@code exp_bill_payment.amount} 是<b>价税合计</b>。
     * 两把尺子放在同一张对账单上 ⇒ 应付 96,000 vs 实付 108,480，核销永远核不平，
     * 对账单被判 SETTLED 时其实只结掉了部分。
     *
     * <p>此处把应付关联点环节统一到<b>价税合计</b>规则：已开票的 PO 取该 PO 名下发票的
     * {@code inv_invoice.amount}（价税合计）之和；未开票的 PO 仍退回 {@code po_order.amount}
     * （此时它本身就是含税规则，无税可加）。{@code receivedAmount} 按同一比例缩放，
     * 保证 {@code MINUS_RECEIVED} 模式下两列同尺，不会算出一个"税额差"当未执行额。</p>
     */
    @Select("SELECT o.id AS id, o.po_no AS sourceBillNo, o.company_id AS companyId, "
            + "s.supplier_code AS partnerId, o.supplier_name AS partnerName, "
            // G2R-10：有发票 ⇒ 价税合计；无发票 ⇒ 原值（未开票的订单额本身就是含税规则）
            + "CASE WHEN inv.gross IS NOT NULL AND inv.gross > 0 THEN inv.gross ELSE o.amount END AS amount, "
            + "CASE WHEN inv.gross IS NOT NULL AND inv.gross > 0 AND o.amount IS NOT NULL AND o.amount > 0 "
            + "     THEN ROUND(COALESCE(o.received_amount, 0) * inv.gross / o.amount, 2) "
            + "     ELSE COALESCE(o.received_amount, 0) END AS receivedAmount, "
            + "o.currency AS currency, "
            + "o.status AS status, DATE_FORMAT(o.order_date, '%Y-%m-%d') AS billDate, "
            + "DATE_FORMAT(o.order_date, '%Y-%m') AS period "
            + "FROM po_order o "
            + "LEFT JOIN md_supplier s ON s.id = o.supplier_id AND s.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId}) "
            // G2R-10：按 PO 聚合的发票价税合计（inv_bill_invoice → inv_invoice → exp_bill.po_id）
            + "LEFT JOIN (SELECT b.po_id AS po_id, SUM(i.amount) AS gross "
            + "           FROM inv_bill_invoice l "
            + "           JOIN inv_invoice i ON i.id = l.invoice_id AND i.del_flag = 0 "
            + "           JOIN exp_bill b ON b.id = l.bill_id AND b.del_flag = 0 "
            + "           WHERE l.del_flag = 0 AND (#{tenantId} IS NULL OR l.tenant_id = #{tenantId}) "
            + "           GROUP BY b.po_id) inv ON inv.po_id = o.id "
            + "WHERE o.del_flag = 0 AND o.status <> 'VOID' "
            + "AND (#{tenantId} IS NULL OR o.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR o.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR s.supplier_code = #{partnerCode}) "
            + "AND (#{period} IS NULL OR REPLACE(DATE_FORMAT(o.order_date, '%Y-%m'), '-', '') "
            + "     = REPLACE(#{period}, '-', '')) "
            + "ORDER BY o.order_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPayables(@Param("tenantId") Long tenantId,
                                             @Param("companyId") Long companyId,
                                             @Param("partnerCode") String partnerCode,
                                             @Param("period") String period,
                                             @Param("limit") int limit);

    /**
     * AP 付款环节 —— {@code exp_bill_payment}（实付登记台账，D-V4R-97 权威查询面）。
     * 仅 {@code pay_status IN ('PAID','PAYING')}。伙伴编码三重规则与原 Mapper 一致。
     */
    @Select("SELECT bp.id AS id, b.bill_no AS sourceBillNo, b.company_id AS companyId, "
            + "COALESCE(c.customer_code, s.supplier_code, bp.payee_name) AS partnerId, "
            + "bp.amount AS amount, bp.pay_method AS paymentMethod, bp.pay_status AS status, "
            + "DATE_FORMAT(bp.pay_time, '%Y-%m-%d') AS payDate "
            + "FROM exp_bill_payment bp "
            + "JOIN exp_bill b ON b.id = bp.bill_id AND b.del_flag = 0 "
            + "LEFT JOIN crm_customer c ON c.id = bp.customer_id AND c.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR c.tenant_id = #{tenantId}) "
            + "LEFT JOIN md_supplier s ON s.supplier_name = bp.payee_name AND s.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId}) "
            + "WHERE bp.del_flag = 0 AND bp.pay_status IN ('PAID','PAYING') "
            + "AND (#{tenantId} IS NULL OR bp.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR b.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR c.customer_code = #{partnerCode} "
            + "     OR c.customer_name = #{partnerCode} OR s.supplier_code = #{partnerCode} "
            + "     OR bp.payee_name = #{partnerCode}) "
            + "ORDER BY bp.pay_time DESC, bp.id DESC LIMIT #{limit}")
    List<Map<String, Object>> selectPayments(@Param("tenantId") Long tenantId,
                                             @Param("companyId") Long companyId,
                                             @Param("partnerCode") String partnerCode,
                                             @Param("limit") int limit);

    /** AP 付款环节默认处理 —— {@code pay_plan}（付款计划；仅当实付台账取不到时使用）。 */
    @Select("SELECT p.id AS id, p.plan_no AS sourceBillNo, p.company_id AS companyId, "
            + "COALESCE(s.supplier_code, p.supplier) AS partnerId, p.amount AS amount, p.status AS status, "
            + "DATE_FORMAT(STR_TO_DATE(CONCAT(LEFT(REPLACE(IFNULL(p.period,''),'-',''),6),'01'), '%Y%m%d'), "
            + "           '%Y-%m-%d') AS payDate "
            + "FROM pay_plan p "
            + "LEFT JOIN md_supplier s ON s.supplier_name = p.supplier AND s.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId}) "
            + "WHERE (#{tenantId} IS NULL OR p.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR p.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR s.supplier_code = #{partnerCode} OR p.supplier = #{partnerCode}) "
            + "ORDER BY p.period ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPayPlanFallback(@Param("tenantId") Long tenantId,
                                                    @Param("companyId") Long companyId,
                                                    @Param("partnerCode") String partnerCode,
                                                    @Param("limit") int limit);

    /** AP 入库环节 —— {@code po_receipt}（自带 supplier_id，公司维度经 po_order 关联）。 */
    @Select("SELECT r.id AS id, r.receipt_no AS sourceBillNo, o.company_id AS companyId, "
            + "s.supplier_code AS partnerId, r.supplier_name AS partnerName, r.amount AS amount, "
            + "r.status AS status, DATE_FORMAT(r.receipt_date, '%Y-%m-%d') AS receiptDate, "
            + "r.po_no AS poNo "
            + "FROM po_receipt r "
            + "LEFT JOIN md_supplier s ON s.id = r.supplier_id AND s.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId}) "
            + "LEFT JOIN po_order o ON o.id = r.po_id AND o.del_flag = 0 "
            + "WHERE r.del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR r.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR o.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR s.supplier_code = #{partnerCode}) "
            + "ORDER BY r.receipt_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPurchaseReceipts(@Param("tenantId") Long tenantId,
                                                     @Param("companyId") Long companyId,
                                                     @Param("partnerCode") String partnerCode,
                                                     @Param("limit") int limit);

    /** AP 退货环节 —— {@code pur_return}（仅 REFUND+APPROVED 冲减应付；原 Mapper 双码规则）。 */
    @Select("SELECT r.id AS id, r.bill_no AS sourceBillNo, r.company_id AS companyId, "
            + "r.vendor_code AS partnerId, r.vendor_name AS partnerName, r.amount AS amount, "
            + "r.status AS status, DATE_FORMAT(r.bill_date, '%Y-%m-%d') AS receiptDate "
            + "FROM pur_return r "
            + "WHERE r.del_flag = 0 AND r.return_mode = 'REFUND' AND r.status = 'APPROVED' "
            + "AND (#{tenantId} IS NULL OR r.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR r.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR r.vendor_code = #{partnerCode} "
            + "     OR (#{unifiedCode} IS NOT NULL AND r.vendor_code = #{unifiedCode})) "
            + "ORDER BY r.bill_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectPurchaseReturns(@Param("tenantId") Long tenantId,
                                                    @Param("companyId") Long companyId,
                                                    @Param("partnerCode") String partnerCode,
                                                    @Param("unifiedCode") String unifiedCode,
                                                    @Param("limit") int limit);

    /** AR 应收环节 —— {@code rc_receivable}（未清取原生 remain_amount；排除 REJECTED）。 */
    @Select("SELECT r.id AS id, r.bill_no AS sourceBillNo, r.company_id AS companyId, "
            + "c.customer_code AS partnerId, r.amount AS amount, "
            + "IFNULL(r.remain_amount, r.amount) AS outstanding, r.currency_code AS currency, "
            + "r.status AS status, r.period AS period, "
            + "DATE_FORMAT(r.bill_date, '%Y-%m-%d') AS billDate, DATE_FORMAT(r.due_date, '%Y-%m-%d') AS dueDate "
            + "FROM rc_receivable r "
            + "LEFT JOIN crm_customer c ON c.id = r.customer_id AND c.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR c.tenant_id = #{tenantId}) "
            + "WHERE r.del_flag = 0 AND IFNULL(r.status,'') <> 'REJECTED' "
            + "AND (#{tenantId} IS NULL OR r.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR r.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR c.customer_code = #{partnerCode}) "
            + "AND (#{period} IS NULL OR REPLACE(IFNULL(r.period,''), '-', '') = REPLACE(#{period}, '-', '')) "
            + "ORDER BY r.due_date ASC, r.bill_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectReceivables(@Param("tenantId") Long tenantId,
                                                @Param("companyId") Long companyId,
                                                @Param("partnerCode") String partnerCode,
                                                @Param("period") String period,
                                                @Param("limit") int limit);

    /** AR 收款环节 —— {@code rc_receipt}（公司维度经核销关系 EXISTS 关联，两代载体并集）。 */
    @Select("SELECT r.id AS id, r.bill_no AS sourceBillNo, c.customer_code AS partnerId, "
            + "c.customer_name AS partnerName, r.amount AS amount, r.status AS status, "
            + "DATE_FORMAT(r.receipt_date, '%Y-%m-%d') AS receiptDate, "
            + "(SELECT rr.company_id FROM rc_receivable rr WHERE rr.del_flag = 0 "
            + "   AND FIND_IN_SET(rr.id, r.receivable_ids) > 0 LIMIT 1) AS companyId "
            + "FROM rc_receipt r "
            + "LEFT JOIN crm_customer c ON c.id = r.customer_id AND c.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR c.tenant_id = #{tenantId}) "
            + "WHERE r.del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR r.tenant_id = #{tenantId}) "
            + "AND (#{partnerCode} IS NULL OR c.customer_code = #{partnerCode}) "
            + "AND (#{companyId} IS NULL OR EXISTS ("
            + "     SELECT 1 FROM rc_receivable rr WHERE rr.del_flag = 0 AND rr.company_id = #{companyId} "
            + "       AND FIND_IN_SET(rr.id, r.receivable_ids) > 0) "
            + "  OR EXISTS ("
            + "     SELECT 1 FROM rc_receipt_line rl JOIN rc_receivable rr2 ON rr2.id = rl.receivable_id "
            + "       AND rr2.del_flag = 0 "
            + "     WHERE rl.del_flag = 0 AND rl.receipt_id = r.id AND rr2.company_id = #{companyId})) "
            + "ORDER BY r.receipt_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectReceipts(@Param("tenantId") Long tenantId,
                                             @Param("companyId") Long companyId,
                                             @Param("partnerCode") String partnerCode,
                                             @Param("limit") int limit);

    /** AR 预制发票环节（三单匹配中间环节）—— {@code ar_pre_invoice}（蓝图代表，缺失时由服务层降级为空）。 */
    @Select("SELECT id AS id, pre_invoice_no AS sourceBillNo, company_id AS companyId, "
            + "customer_code AS partnerId, amount AS amount, status AS status, "
            + "DATE_FORMAT(invoice_date, '%Y-%m-%d') AS receiptDate "
            + "FROM ar_pre_invoice "
            + "WHERE (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR customer_code = #{partnerCode}) "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectPreInvoices(@Param("tenantId") Long tenantId,
                                                @Param("companyId") Long companyId,
                                                @Param("partnerCode") String partnerCode,
                                                @Param("limit") int limit);

    /**
     * AR 销售订单环节（P0-C《AR 三单匹配改造》）—— 候选表 {@code so_order} + {@code so_order_line}。
     *
     * <p>ERP／销售模块物化表。现役 FSSC 库<b>不存在</b>该表（全仓 {@code so_order}/{@code sales_order}
     * 零命中），由服务层 {@link LinkuOpenQueryService#salesOrders} 先做表存在性探测，
     * 缺失时直接返回空列表并告警 —— <b>不伪造数据</b>（方案 §五降级保护）。</p>
     *
     * <p>返回<b>行级扁平</b>记录（头字段随行重复），由 BCP 侧按 {@code soNo} 分组落头/行。
     * 列别名即报文键，与 BCP {@code SalesOrderSyncService} 的消费契约对齐。</p>
     */
    @Select("SELECT o.so_no AS soNo, o.id AS soId, o.company_id AS companyId, "
            + "c.customer_code AS partnerId, o.customer_name AS customerName, "
            + "DATE_FORMAT(o.order_date, '%Y-%m-%d') AS soDate, o.status AS soStatus, "
            + "o.total_amount AS totalAmount, o.tax_amount AS taxAmount, o.remark AS remark, "
            + "l.line_no AS lineNo, l.material_code AS materialCode, l.material_name AS materialName, "
            + "l.quantity AS quantity, l.unit AS unit, l.unit_price AS unitPrice, "
            + "l.amount AS amount, l.tax_rate AS taxRate, l.tax_amount AS lineTaxAmount, "
            + "l.shipped_qty AS shippedQty, l.status AS lineStatus "
            + "FROM so_order o "
            + "LEFT JOIN so_order_line l ON l.so_id = o.id AND l.del_flag = 0 "
            + "LEFT JOIN crm_customer c ON c.id = o.customer_id AND c.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR c.tenant_id = #{tenantId}) "
            + "WHERE o.del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR o.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR o.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR c.customer_code = #{partnerCode}) "
            + "AND (#{period} IS NULL OR REPLACE(DATE_FORMAT(o.order_date, '%Y-%m'), '-', '') "
            + "     = REPLACE(#{period}, '-', '')) "
            + "ORDER BY o.order_date ASC, o.id ASC, l.line_no ASC LIMIT #{limit}")
    List<Map<String, Object>> selectSalesOrderLines(@Param("tenantId") Long tenantId,
                                                    @Param("companyId") Long companyId,
                                                    @Param("partnerCode") String partnerCode,
                                                    @Param("period") String period,
                                                    @Param("limit") int limit);

    /**
     * AR 销售出库发货环节（P0-C）—— 候选表 {@code so_shipment}。
     *
     * <p>发货环节→订单环节的桥为 {@code so_no}（列别名 {@code soNo}）。现役库缺失，服务层探测后降级为空。</p>
     */
    @Select("SELECT s.id AS id, s.ship_no AS sourceBillNo, s.company_id AS companyId, "
            + "c.customer_code AS partnerId, s.customer_name AS partnerName, s.amount AS amount, "
            + "s.status AS status, DATE_FORMAT(s.ship_date, '%Y-%m-%d') AS receiptDate, "
            + "s.so_no AS soNo, s.ship_line_no AS shipLineNo, s.material_code AS materialCode, "
            + "s.quantity AS qty, s.unit_price AS unitPrice "
            + "FROM so_shipment s "
            + "LEFT JOIN crm_customer c ON c.id = s.customer_id AND c.del_flag = 0 "
            + "  AND (#{tenantId} IS NULL OR c.tenant_id = #{tenantId}) "
            + "WHERE s.del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR s.tenant_id = #{tenantId}) "
            + "AND (#{companyId} IS NULL OR s.company_id = #{companyId}) "
            + "AND (#{partnerCode} IS NULL OR c.customer_code = #{partnerCode}) "
            + "ORDER BY s.ship_date ASC LIMIT #{limit}")
    List<Map<String, Object>> selectSaleShipments(@Param("tenantId") Long tenantId,
                                                  @Param("companyId") Long companyId,
                                                  @Param("partnerCode") String partnerCode,
                                                  @Param("limit") int limit);

    /** 往来单位主数据 —— 供应商（{@code md_supplier}）。 */
    @Select("SELECT id AS id, supplier_code AS partnerCode, supplier_name AS partnerName "
            + "FROM md_supplier WHERE del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "AND (#{partnerCode} IS NULL OR supplier_code = #{partnerCode}) "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectSuppliers(@Param("tenantId") Long tenantId,
                                              @Param("partnerCode") String partnerCode,
                                              @Param("limit") int limit);

    /** 往来单位主数据 —— 客户（{@code crm_customer}）。 */
    @Select("SELECT id AS id, customer_code AS partnerCode, customer_name AS partnerName "
            + "FROM crm_customer WHERE del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "AND (#{partnerCode} IS NULL OR customer_code = #{partnerCode}) "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectCustomers(@Param("tenantId") Long tenantId,
                                              @Param("partnerCode") String partnerCode,
                                              @Param("limit") int limit);

    /** 期间冻结状态 —— {@code gl_month_close} 该期间最新一条记录的状态。 */
    @Select("SELECT status AS status, DATE_FORMAT(MAX(update_time), '%Y-%m-%d %H:%i:%s') AS updateTime "
            + "FROM gl_month_close WHERE del_flag = 0 "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "AND company_id = #{companyId} "
            + "AND REPLACE(IFNULL(period, ''), '-', '') = REPLACE(#{period}, '-', '') "
            + "GROUP BY status ORDER BY MAX(update_time) DESC LIMIT 1")
    List<Map<String, Object>> selectPeriodStatus(@Param("tenantId") Long tenantId,
                                                 @Param("companyId") Long companyId,
                                                 @Param("period") String period);
}
