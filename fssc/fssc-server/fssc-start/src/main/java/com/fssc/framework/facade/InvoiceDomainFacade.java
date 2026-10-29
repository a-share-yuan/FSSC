package com.fssc.framework.facade;

import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 发票域跨域读写契约：费用域、共享域、合规域、财务域、流程收尾通过此接口访问发票池，
 * 不直接依赖 InvInvoiceMapper / InvBillInvoiceMapper / InvoiceService。
 *
 * <p><b>分库同前</b>：发票域登记了独立路由键 {@code invoice}（{@code fssc.datasource.invoice}），
 * 独立库就绪前该路由别名到 master，实现类（{@code InvoiceFacadeImpl}）同时标注类级
 * {@code @FsscDataSource("invoice")}——今天走的是主库，独立库一开即自动改道，调用方零改动。
 * 跨域共享的是<b>数据形状</b>（实体字段，前端序列化不变），不是仓储：查询语义全部留在实现类里。
 *
 * <p><b>不挂起外层事务</b>：本接口的实现只切路由，不改变事务传播。调用方已在事务中时仍用同一连接、
 * 看得到本事务未提交的数据（与改前直连 Mapper 完全一致）。反过来说<b>事务内跨库切不动</b>——
 * 真正的跨库写要在调用侧开 {@code REQUIRES_NEW}，这是「先分库＋门面、跑一阵再谈进程」已知的边界。
 *
 * <p>只读报表/联查服务（:8094）不装配本实现，其跨域汇总仍读副本 —— 见
 * {@code docker/README-document-invoice-split.md} 的例外清单。
 */
public interface InvoiceDomainFacade {

    // 发票主表读

    /** 按主键取一张发票；不存在／已逻辑删返回 null。 */
    InvInvoice findById(Long invoiceId);

    /** 按主键批量取（空入参返回空列表；顺序不保证）。 */
    List<InvInvoice> findByIds(Collection<Long> invoiceIds);

    /** 按发票号码取全部同号票（租户内；按 id 正序）。 */
    List<InvInvoice> findByInvoiceNo(String invoiceNo);

    /** 按发票号码取一张（并发同号时的兜底重查；无记录返回 null）。 */
    InvInvoice findOneByInvoiceNo(String invoiceNo);

    /** 发票池内同「代码 + 号码」的计数（全电票代码为空串，与写入口径一致）。 */
    long countByCodeAndNo(String invoiceCode, String invoiceNo);

    /** 指定公司 + 票面状态的全部发票。 */
    List<InvInvoice> findByCompanyIdAndStatus(Long companyId, String status);

    /** 有指定票面状态发票的公司 ID 集合（去空、去重）。 */
    Set<Long> companyIdsByStatus(String status);

    /** 指定认证状态（certify_status）的全部发票。 */
    List<InvInvoice> findByCertifyStatus(String certifyStatus);

    /** 按销售方名称 + 开票日期闭区间取发票（供应商对账单期间段）。 */
    List<InvInvoice> findBySellerNameAndDateRange(String sellerName, LocalDate fromInclusive, LocalDate toInclusive);

    /** 按销售方名称 + 开票日期早于指定日取发票（对账单期初段）。 */
    List<InvInvoice> findBySellerNameBefore(String sellerName, LocalDate beforeExclusive);

    /** 按来源单据反查发票（按创建时间倒序）。 */
    List<InvInvoice> findBySourceDoc(String sourceDocType, Long sourceDocId);

    // 发票主表写

    /**
     * 原始入池（<b>不走</b> {@code InvoiceService#create} 的全校验链，仅登记），
     * 供报销补录发票使用——改前该处就是裸 {@code insert}，语义按原样保留。
     *
     * @return 新发票 ID
     */
    Long insertInvoice(InvInvoice invoice);

    /** 只改票面状态；发票不存在返回 false。 */
    boolean updateStatus(Long invoiceId, String status);

    // 单据-发票勾稽（inv_bill_invoice）

    /** 按单据取勾稽行（租户内）。 */
    List<InvBillInvoice> findLinksByBillId(Long billId);

    /** 按多张单据取勾稽行（三单匹配按收货单批量取）。 */
    List<InvBillInvoice> findLinksByBillIds(Collection<Long> billIds);

    /** 按发票取勾稽行（跨单占用判定）。 */
    List<InvBillInvoice> findLinksByInvoiceId(Long invoiceId);

    /** 按发票取「除该单据以外」的勾稽行（跨单判重：同号票被别的单据占用）。 */
    List<InvBillInvoice> findLinksByInvoiceIdExcludingBill(Long invoiceId, Long billId);

    /**
     * 一组发票被「非本单」占用的勾稽行数（预检跨单占用计数）。
     *
     * @param invoiceIds 同号票的 ID 集合（空集合直接返回 0）
     * @param billId 当前单据 ID
     */
    long countLinksByInvoiceIdsExcludingBill(Collection<Long> invoiceIds, Long billId);

    /** 单据的有效勾稽笔数（{@code del_flag=0}，反审核前置校验）。 */
    long countActiveLinksByBillId(Long billId);

    /** 该单据与该发票是否已勾稽。 */
    boolean existsLink(Long billId, Long invoiceId);

    /**
     * 建立单据-发票勾稽。
     *
     * @param amount 本次占用额（缺省应传发票面额；占用累计是三单匹配／超付判定的输入）
     * @return 新勾稽行 ID
     */
    Long createLink(Long billId, Long invoiceId, BigDecimal amount);
}
