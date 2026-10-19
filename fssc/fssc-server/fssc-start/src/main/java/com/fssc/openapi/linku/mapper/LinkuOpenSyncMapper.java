package com.fssc.openapi.linku.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LinkU 开放接口 <b>写入</b> Mapper（F-3 / F-7）。替代原 BCP 直连 FSSC 库的写语句。
 *
 * <p>全部语句逐条移植自 {@code FsscArApAdapter}（现役库 OTC 代口径），语义与守卫完全一致：</p>
 * <ul>
 *   <li>AP 对账 → {@code supplier_recon}(+detail/diff)，幂等键 {@code (tenant_id, supplier_id, period)}，
 *       并回填 {@code linku_recon_id}/{@code linku_partner_id}/{@code linku_sync_status='SYNCED'}；</li>
 *   <li>{@code status} 归属守卫：仅当现值属 {@code DRAFT}/{@code DIFF} 时由 BCP 改写（对方已推进则保留）；</li>
 *   <li>AR 对账 → {@code ar_recon_record}（{@code record_type='RECON'}）按 (company, customer, period) 幂等 UPSERT；</li>
 *   <li>AR 收款核销 → {@code rc_receipt}+{@code rc_receipt_line}，并回写 {@code rc_receivable} 余额（含余额守卫）；</li>
 *   <li>AR/AP 对冲留痕 → {@code gl_inter_offset}（无资金流入，故不生成收款单）。</li>
 * </ul>
 *
 * <p>调用方须保证整批写入在同一事务内（{@code @Transactional}），保证「头表 + 明细 + 差异」全成或全败。</p>
 */
@Mapper
public interface LinkuOpenSyncMapper {

    // ==================== 主数据解析 ====================

    @Select("SELECT id FROM md_supplier WHERE tenant_id = #{tenantId} AND supplier_code = #{code} "
            + "AND del_flag = 0 LIMIT 1")
    Long selectSupplierIdByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT id FROM crm_customer WHERE tenant_id = #{tenantId} AND customer_code = #{code} "
            + "AND del_flag = 0 LIMIT 1")
    Long selectCustomerIdByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT id FROM rc_receivable WHERE tenant_id = #{tenantId} AND bill_no = #{billNo} "
            + "AND del_flag = 0 LIMIT 1")
    Long selectReceivableIdByBillNo(@Param("tenantId") Long tenantId, @Param("billNo") String billNo);

    @Select("SELECT IFNULL(remain_amount, amount) FROM rc_receivable "
            + "WHERE tenant_id = #{tenantId} AND bill_no = #{billNo} AND del_flag = 0 LIMIT 1")
    BigDecimal selectRemainAmount(@Param("tenantId") Long tenantId, @Param("billNo") String billNo);

    // ==================== AP 对账（supplier_recon 三表） ====================

    @Select("SELECT id FROM supplier_recon WHERE tenant_id = #{tenantId} AND supplier_id = #{supplierId} "
            + "AND period = #{period} AND del_flag = 0 LIMIT 1")
    Long selectReconRowId(@Param("tenantId") Long tenantId,
                          @Param("supplierId") Long supplierId,
                          @Param("period") String period);

    @Insert("INSERT INTO supplier_recon (tenant_id, supplier_id, supplier_name, recon_no, period, "
            + "payable_amount, paid_amount, balance, opening_balance, period_payable, period_paid, closing_balance, "
            + "recon_type, status, diff_remark, linku_recon_id, linku_partner_id, linku_sync_status, "
            + "linku_sync_time, company_id, create_time, update_time, del_flag) "
            + "VALUES (#{tenantId}, #{supplierId}, NULL, #{reconNo}, #{period}, "
            + "#{payableAmount}, #{paidAmount}, #{balance}, #{openingBalance}, #{payableAmount}, #{paidAmount}, "
            + "#{balance}, 'AUTO', #{status}, #{diffRemark}, #{linkuReconId}, #{linkuPartnerId}, 'SYNCED', "
            + "#{now}, #{companyId}, #{now}, #{now}, 0)")
    int insertSupplierRecon(@Param("tenantId") Long tenantId,
                            @Param("supplierId") Long supplierId,
                            @Param("reconNo") String reconNo,
                            @Param("period") String period,
                            @Param("payableAmount") BigDecimal payableAmount,
                            @Param("paidAmount") BigDecimal paidAmount,
                            @Param("balance") BigDecimal balance,
                            @Param("openingBalance") BigDecimal openingBalance,
                            @Param("status") String status,
                            @Param("diffRemark") String diffRemark,
                            @Param("linkuReconId") Long linkuReconId,
                            @Param("linkuPartnerId") Long linkuPartnerId,
                            @Param("companyId") Long companyId,
                            @Param("now") LocalDateTime now);

    /** 状态归属守卫：仅当现值属 DRAFT/DIFF 时由 BCP 改写；linku 字段无条件刷新。 */
    @Update("UPDATE supplier_recon SET payable_amount = #{payableAmount}, paid_amount = #{paidAmount}, "
            + "balance = #{balance}, opening_balance = #{openingBalance}, period_payable = #{payableAmount}, "
            + "period_paid = #{paidAmount}, closing_balance = #{balance}, "
            + "status = CASE WHEN status IN ('DRAFT','DIFF') THEN #{status} ELSE status END, "
            + "diff_remark = #{diffRemark}, linku_recon_id = #{linkuReconId}, linku_sync_status = 'SYNCED', "
            + "linku_sync_time = #{now}, linku_partner_id = IFNULL(#{linkuPartnerId}, linku_partner_id), "
            + "update_time = #{now} WHERE id = #{rowId}")
    int updateSupplierRecon(@Param("rowId") Long rowId,
                            @Param("payableAmount") BigDecimal payableAmount,
                            @Param("paidAmount") BigDecimal paidAmount,
                            @Param("balance") BigDecimal balance,
                            @Param("openingBalance") BigDecimal openingBalance,
                            @Param("status") String status,
                            @Param("diffRemark") String diffRemark,
                            @Param("linkuReconId") Long linkuReconId,
                            @Param("linkuPartnerId") Long linkuPartnerId,
                            @Param("now") LocalDateTime now);

    @Select("SELECT item_no FROM supplier_recon_detail WHERE tenant_id = #{tenantId} AND recon_id = #{rowId}")
    List<String> selectDetailItemNos(@Param("tenantId") Long tenantId, @Param("rowId") Long rowId);

    @Insert("INSERT INTO supplier_recon_detail (tenant_id, recon_id, item_type, item_id, item_no, item_date, "
            + "amount, match_status, match_type, match_rule, create_time, update_time) "
            + "VALUES (#{tenantId}, #{reconId}, 'INVOICE', NULL, #{itemNo}, #{itemDate}, #{amount}, "
            + "'MATCHED', 'AUTO', 'INVOICE_NO', #{now}, #{now})")
    int insertReconDetail(@Param("tenantId") Long tenantId,
                          @Param("reconId") Long reconId,
                          @Param("itemNo") String itemNo,
                          @Param("itemDate") LocalDate itemDate,
                          @Param("amount") BigDecimal amount,
                          @Param("now") LocalDateTime now);

    @Select("SELECT reason FROM supplier_recon_diff WHERE tenant_id = #{tenantId} AND recon_id = #{rowId}")
    List<String> selectDiffReasons(@Param("tenantId") Long tenantId, @Param("rowId") Long rowId);

    @Insert("INSERT INTO supplier_recon_diff (tenant_id, recon_id, diff_type, our_amount, supplier_amount, "
            + "diff_amount, reason, adjustment_type, status, create_time, update_time) "
            + "VALUES (#{tenantId}, #{reconId}, #{diffType}, #{ourAmount}, #{supplierAmount}, #{diffAmount}, "
            + "#{reason}, 'NONE', 'PENDING', #{now}, #{now})")
    int insertReconDiff(@Param("tenantId") Long tenantId,
                        @Param("reconId") Long reconId,
                        @Param("diffType") String diffType,
                        @Param("ourAmount") BigDecimal ourAmount,
                        @Param("supplierAmount") BigDecimal supplierAmount,
                        @Param("diffAmount") BigDecimal diffAmount,
                        @Param("reason") String reason,
                        @Param("now") LocalDateTime now);

    // ==================== AR 对账（ar_recon_record） ====================

    @Select("SELECT id FROM ar_recon_record WHERE tenant_id = #{tenantId} AND company_id = #{companyId} "
            + "AND customer_id = #{customerId} "
            + "AND REPLACE(IFNULL(period,''), '-', '') = REPLACE(#{period}, '-', '') "
            + "AND record_type = 'RECON' AND del_flag = 0 LIMIT 1")
    Long selectArReconRowId(@Param("tenantId") Long tenantId,
                            @Param("companyId") Long companyId,
                            @Param("customerId") Long customerId,
                            @Param("period") String period);

    @Insert("INSERT INTO ar_recon_record (tenant_id, record_type, period, customer_id, company_id, "
            + "status, remark, create_time, update_time, del_flag) "
            + "VALUES (#{tenantId}, 'RECON', #{period}, #{customerId}, #{companyId}, #{status}, #{remark}, "
            + "#{now}, #{now}, 0)")
    int insertArReconRecord(@Param("tenantId") Long tenantId,
                            @Param("period") String period,
                            @Param("customerId") Long customerId,
                            @Param("companyId") Long companyId,
                            @Param("status") String status,
                            @Param("remark") String remark,
                            @Param("now") LocalDateTime now);

    @Update("UPDATE ar_recon_record SET status = #{status}, remark = #{remark}, update_time = #{now} "
            + "WHERE id = #{rowId}")
    int updateArReconRecord(@Param("rowId") Long rowId,
                            @Param("status") String status,
                            @Param("remark") String remark,
                            @Param("now") LocalDateTime now);

    // ==================== AR 收款核销（rc_receipt + rc_receipt_line + 余额） ====================

    @Insert("INSERT INTO rc_receipt (tenant_id, bill_no, receivable_ids, customer_id, receipt_date, pay_method, "
            + "amount, status, create_time, update_time, del_flag) "
            + "VALUES (#{tenantId}, #{billNo}, #{receivableIds}, #{customerId}, #{receiptDate}, #{payMethod}, "
            + "#{amount}, 'WRITTEN_OFF', #{now}, #{now}, 0)")
    int insertRcReceipt(@Param("tenantId") Long tenantId,
                        @Param("billNo") String billNo,
                        @Param("receivableIds") String receivableIds,
                        @Param("customerId") Long customerId,
                        @Param("receiptDate") LocalDate receiptDate,
                        @Param("payMethod") String payMethod,
                        @Param("amount") BigDecimal amount,
                        @Param("now") LocalDateTime now);

    @Select("SELECT id FROM rc_receipt WHERE tenant_id = #{tenantId} AND bill_no = #{billNo} LIMIT 1")
    Long selectRcReceiptId(@Param("tenantId") Long tenantId, @Param("billNo") String billNo);

    @Insert("INSERT INTO rc_receipt_line (tenant_id, receipt_id, receivable_id, receivable_no, offset_amount, "
            + "status, create_time, update_time, del_flag) "
            + "VALUES (#{tenantId}, #{receiptId}, #{receivableId}, #{receivableNo}, #{offsetAmount}, "
            + "'WRITTEN_OFF', #{now}, #{now}, 0)")
    int insertRcReceiptLine(@Param("tenantId") Long tenantId,
                            @Param("receiptId") Long receiptId,
                            @Param("receivableId") Long receivableId,
                            @Param("receivableNo") String receivableNo,
                            @Param("offsetAmount") BigDecimal offsetAmount,
                            @Param("now") LocalDateTime now);

    /**
     * 应收余额回写（第13轮口径）：{@code status} 必须是第一个赋值（读原始 remain_amount），
     * 且带余额守卫，允许「正好结清」（差额 ≤ 0.01），禁止超额。
     */
    @Update("UPDATE rc_receivable "
            + "SET status = CASE WHEN IFNULL(remain_amount, amount) - #{amount} <= 0.01 THEN 'DONE' ELSE 'NONE' END, "
            + "    received_amount = IFNULL(received_amount, 0) + #{amount}, "
            + "    remain_amount   = IFNULL(remain_amount, amount) - #{amount}, "
            + "    update_time     = #{now} "
            + "WHERE tenant_id = #{tenantId} AND bill_no = #{billNo} AND del_flag = 0 "
            + "  AND IFNULL(remain_amount, amount) >= #{amount} - 0.01")
    int applyReceivableBalance(@Param("tenantId") Long tenantId,
                               @Param("billNo") String billNo,
                               @Param("amount") BigDecimal amount,
                               @Param("now") LocalDateTime now);

    // ==================== 对冲留痕（gl_inter_offset） ====================

    @Insert("INSERT INTO gl_inter_offset (tenant_id, offset_no, period, from_company_id, to_company_id, "
            + "inter_flag, amount, offset_status, create_time, update_time, del_flag) "
            + "VALUES (#{tenantId}, #{offsetNo}, #{period}, #{companyId}, #{companyId}, #{interFlag}, "
            + "#{amount}, 'OFFSET', #{now}, #{now}, 0)")
    int insertGlInterOffset(@Param("tenantId") Long tenantId,
                            @Param("offsetNo") String offsetNo,
                            @Param("period") String period,
                            @Param("companyId") Long companyId,
                            @Param("interFlag") String interFlag,
                            @Param("amount") BigDecimal amount,
                            @Param("now") LocalDateTime now);

    // ==================== 状态回写 ====================

    @Update("UPDATE rc_receipt SET status = #{status}, update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND bill_no = #{billNo} AND del_flag = 0")
    int updateRcReceiptStatus(@Param("tenantId") Long tenantId,
                              @Param("billNo") String billNo,
                              @Param("status") String status,
                              @Param("now") LocalDateTime now);

    @Update("UPDATE ar_pre_invoice SET status = #{status} WHERE tenant_id = #{tenantId} "
            + "AND (invoice_no = #{billNo} OR pre_invoice_no = #{billNo})")
    int updateArPreInvoiceStatus(@Param("tenantId") Long tenantId,
                                 @Param("billNo") String billNo,
                                 @Param("status") String status);

    @Select("SELECT COUNT(1) FROM ap_service_request WHERE tenant_id = #{tenantId} "
            + "AND company_id = #{companyId} AND bill_no = #{billNo}")
    int countApServiceRequest(@Param("tenantId") Long tenantId,
                              @Param("companyId") Long companyId,
                              @Param("billNo") String billNo);

    @Update("UPDATE ap_service_request SET status = #{status}, pay_time = #{now} "
            + "WHERE bill_no = #{billNo} AND company_id = #{companyId} AND tenant_id = #{tenantId}")
    int updateApServiceRequestStatus(@Param("tenantId") Long tenantId,
                                     @Param("companyId") Long companyId,
                                     @Param("billNo") String billNo,
                                     @Param("status") String status,
                                     @Param("now") LocalDateTime now);

    /** 通用探针：表是否存在（用于 {@code ar_pre_invoice} 等代际相关表的优雅降级）。 */
    @Select("SELECT COUNT(1) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(#{tableName})")
    int tableExists(@Param("tableName") String tableName);
}
