// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.notify.NotifyService;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.entity.InvInvoiceGlobal;
import com.fssc.invoice.mapper.InvBillInvoiceMapper;
import com.fssc.invoice.mapper.InvInvoiceGlobalMapper;
import com.fssc.invoice.mapper.InvInvoiceMapper;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.service.PurchaseService;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发票服务：录入（价税分离校验 amount = net + tax）、单据关联发票、发票查询、销售开票（OTC02）。
 * OCR 识别（img_file.ocr_text → inv_invoice）留 P9 接入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("invoice")
public class InvoiceService {

    private final InvInvoiceMapper invInvoiceMapper;
    private final InvBillInvoiceMapper invBillInvoiceMapper;
    private final InvInvoiceGlobalMapper invInvoiceGlobalMapper;
    private final GlTaxProvisionMapper glTaxProvisionMapper;
    private final MdSupplierMapper mdSupplierMapper;
    private final WfEngineFacade wfEngineFacade;
    private final NotifyService notifyService;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final OrgGuard orgGuard;
    private final ExpBillMapper expBillMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final PurchaseService purchaseService;
    /** 发票与收货单匹配时自动生成暂估冲回 + 正式入账凭证（内部 REQUIRES_NEW + 全量兜底） */
    private final VoucherService voucherService;

    // 进项发票全生命周期：认证勾选 / 进项转出 / 票账比对·申报

    /**
     * 批量进项认证勾选：仅增值税专用发票（invoiceKind=SPECIAL 或 invoiceType=VAT）可抵扣。
     * 勾选后 deduct_amount = taxAmount，certify_status=CERTIFIED，所属期=period。
     * 已进项转出(REVERSED)的发票不可重复勾选。
     */
    @Transactional(rollbackFor = Exception.class)
    public int certify(List<Long> ids, String period) {
        requireIds(ids, "请选择待勾选认证的发票");
        if (period == null || period.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定勾选认证所属期(如 202608)");
        }
        // period 原样落 certify_period 列（varchar(6)），
        // 传 "2026-10" 这类带横杠格式会因列宽不足抛 DataTooLong → 整单 500「系统异常」，
        // 调用方无法从错误信息判断是格式问题（易被当成系统故障排查）。
        // 此处显式归一为 yyyyMM 并给出可执行提示，不让格式问题走到 DB 层炸 500。
        String certifyPeriod = normalizeCertifyPeriod(period);
        int n = 0;
        for (Long id : ids) {
            InvInvoice inv = requireInvoice(id);
            if ("REVERSED".equals(inv.getCertifyStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票 " + noOf(inv) + " 已进项转出，不可认证勾选");
            }
            if ("CERTIFIED".equals(inv.getCertifyStatus())) {
                continue;
            }
            if (!isVatDeductible(inv)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票 " + noOf(inv) + " 非增值税专用发票，不可认证抵扣");
            }
            InvInvoice u = new InvInvoice();
            u.setId(id);
            u.setCertifyStatus("CERTIFIED");
            u.setCertifyPeriod(certifyPeriod);
            u.setCertifyDate(LocalDateTime.now());
            u.setDeductAmount(inv.getTaxAmount());
            invInvoiceMapper.updateById(u);
            n++;
        }
        return n;
    }

    /**
     * 认证所属期归一化为 {@code yyyyMM}。
     * <p>接受 {@code yyyy-MM}（界面/手册规则）与 {@code yyyyMM}（列宽规则），非法格式显式 400 拒绝——
     * 若直接落 varchar(6) 列会触发 DataTooLong，整单 500，调用方无从判断是格式问题。
     */
    private String normalizeCertifyPeriod(String period) {
        String s = period.trim();
        if (s.matches("\\d{6}")) {
            return checkMonth(s.substring(0, 4), s.substring(4, 6), period);
        }
        if (s.matches("\\d{4}-\\d{2}")) {
            return checkMonth(s.substring(0, 4), s.substring(5, 7), period);
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "认证所属期格式不正确：" + period + "（应为 yyyy-MM 如 2026-10，或 yyyyMM 如 202610）");
    }

    /** 补正：仅做格式归一会放行 {@code 2026-13} 这类不存在月份，归一后必须再校验月份范围。 */
    private String checkMonth(String year, String month, String raw) {
        int m = Integer.parseInt(month);
        if (m < 1 || m > 12) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "认证所属期月份非法：" + raw + "（月份须为 01~12）");
        }
        return year + month;
    }

    /** 取消勾选：CERTIFIED → NOT_CERTIFIED，可抵扣税额清零（仅限尚未进项转出者）。 */
    @Transactional(rollbackFor = Exception.class)
    public int cancelCertify(List<Long> ids) {
        requireIds(ids, "请选择待取消勾选的发票");
        int n = 0;
        for (Long id : ids) {
            InvInvoice inv = requireInvoice(id);
            if (!"CERTIFIED".equals(inv.getCertifyStatus())) {
                continue;
            }
            // 认证期间/认证时间必须显式置 NULL——MyBatis-Plus 默认 NOT_NULL 更新策略会
            // 跳过实体里的 null 字段，旧写法 updateById 置 null 实际把残留期间留在行上
            // （取消勾选后 certify_status 回 NOT_CERTIFIED 而 certify_period/certify_date 残留，界面自相矛盾）。
            invInvoiceMapper.update(null, new LambdaUpdateWrapper<InvInvoice>()
                    .set(InvInvoice::getCertifyStatus, "NOT_CERTIFIED")
                    .set(InvInvoice::getCertifyPeriod, null)
                    .set(InvInvoice::getCertifyDate, null)
                    .set(InvInvoice::getDeductAmount, BigDecimal.ZERO)
                    .eq(InvInvoice::getId, id));
            n++;
        }
        return n;
    }

    /**
     * 进项转出：已勾选的进项税额不得抵扣（用于免税项目/简易计税/集体福利等），
     * 置 CERTIFIED → REVERSED，deduct_amount 清零，并记录转出时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public int transferOut(List<Long> ids) {
        requireIds(ids, "请选择待进项转出的发票");
        int n = 0;
        for (Long id : ids) {
            InvInvoice inv = requireInvoice(id);
            if ("REVERSED".equals(inv.getCertifyStatus())) {
                continue;
            }
            InvInvoice u = new InvInvoice();
            u.setId(id);
            u.setCertifyStatus("REVERSED");
            u.setDeductAmount(BigDecimal.ZERO);
            u.setReverseDate(LocalDateTime.now());
            invInvoiceMapper.updateById(u);
            n++;
        }
        return n;
    }

    /**
     * 票账比对 · 增值税申报汇总：应纳增值税 = 销项税额(gl_tax_provision VAT) − 认证进项税额(Σ inv_invoice.deduct_amount)。
     * 用于与 gl_tax_provision 对齐申报数据，发现问题票额差异。
     */
    public VatFiling filingSummary(String period) {
        if (period == null || period.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定申报期间(如 202608)");
        }
        // 认证进项税额：本期 CERTIFIED 发票可抵扣税额合计
        List<InvInvoice> certified = invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getCertifyStatus, "CERTIFIED")
                .eq(InvInvoice::getCertifyPeriod, period));
        BigDecimal inputVat = certified.stream()
                .map(x -> x.getDeductAmount() == null ? BigDecimal.ZERO : x.getDeductAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int inputCount = certified.size();
        // 销项税额：本期 VAT 税金计提
        List<GlTaxProvision> vat = glTaxProvisionMapper.selectList(new LambdaQueryWrapper<GlTaxProvision>()
                .eq(GlTaxProvision::getTaxType, "VAT")
                .eq(GlTaxProvision::getPeriod, period));
        BigDecimal outputVat = vat.stream()
                .map(x -> x.getTaxAmount() == null ? BigDecimal.ZERO : x.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 票账比对：发票税额总额 vs 计提销项（供发现差异；此处为抵扣规则展示）
        BigDecimal invoiceTax = certified.stream()
                .map(x -> x.getTaxAmount() == null ? BigDecimal.ZERO : x.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        VatFiling v = new VatFiling();
        v.setPeriod(period);
        v.setOutputVat(outputVat);
        v.setInputVat(inputVat);
        v.setNetVatPayable(outputVat.subtract(inputVat));
        v.setCertifiedCount(inputCount);
        v.setCertifiedTax(invoiceTax);
        return v;
    }

    private boolean isVatDeductible(InvInvoice inv) {
        return "SPECIAL".equalsIgnoreCase(inv.getInvoiceKind())
                || "VAT".equalsIgnoreCase(inv.getInvoiceType());
    }

    private InvInvoice requireInvoice(Long id) {
        InvInvoice inv = invInvoiceMapper.selectById(id);
        if (inv == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票不存在: " + id);
        }
        return inv;
    }

    private void requireIds(List<Long> ids, String tip) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, tip);
        }
    }

    private String noOf(InvInvoice inv) {
        String no = inv.getInvoiceNo();
        return (no == null || no.isBlank()) ? String.valueOf(inv.getId()) : no;
    }

    @Data
    public static class VatFiling {
        private String period;
        /** 销项税额（gl_tax_provision VAT 合计） */
        private BigDecimal outputVat;
        /** 认证进项税额（本期 CERTIFIED deduct_amount 合计） */
        private BigDecimal inputVat;
        /** 应纳增值税 = 销项 − 进项 */
        private BigDecimal netVatPayable;
        /** 本期认证发票张数 */
        private int certifiedCount;
        /** 本期认证发票税额合计（票账比对维度） */
        private BigDecimal certifiedTax;
    }

    /** 销售发票开具（OTC02 INVOICE_ISSUE）：登记待开发票并发起开票审批流（应收会计→财务经理→财务总监）。 */
    @Transactional(rollbackFor = Exception.class)
    public Long issue(InvInvoice invoice) {
        orgGuard.assertCompanyAllowed(invoice.getCompanyId());
        requireManualHeader(invoice);
        if (invoice.getAmount() == null || invoice.getNetAmount() == null || invoice.getTaxAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额/不含税/税额必填");
        }
        BigDecimal sum = invoice.getNetAmount().add(invoice.getTaxAmount());
        if (invoice.getAmount().compareTo(sum) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "价税不符：价税合计 " + invoice.getAmount() + " ≠ 不含税 " + invoice.getNetAmount()
                            + " + 税额 " + invoice.getTaxAmount());
        }
        // 票号级去重（超时重试防重）：外部网关慢而前端 20s 超时重试，
        // 同租户同票号已开票成功即直接返回既有票（最早一张）id +「已开票成功」语义，不再重复写入、
        // 不再调外部网关；tenant_id 由 FsscTenantLineHandler 自动追加（与 existsInPool 同一规则），
        // 竞态兜底仍靠 uk_tenant_inv + 4090（GlobalExceptionHandler）。
        InvInvoice existed = invInvoiceMapper.selectOne(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceNo, invoice.getInvoiceNo())
                .orderByAsc(InvInvoice::getId)
                .last("LIMIT 1"));
        if (existed != null) {
            log.info("该发票已开票成功（单号 {}），本次未重复开具，直接返回既有票 id={}",
                    invoice.getInvoiceNo(), existed.getId());
            return existed.getId();
        }
        invoice.setId(null);
        invoice.setTenantId(TenantContext.getTenantId());
        if (invoice.getStatus() == null) {
            invoice.setStatus("ISSUING");
        }
        // 多分子公司审批路由：前端已传不覆盖，否则取当前登录用户所属公司
        if (invoice.getCompanyId() == null) {
            LoginUser lu = UserContext.get();
            if (lu != null) invoice.setCompanyId(lu.getCompanyId());
        }
        invoice.setSourceDocType("SALES_INVOICE");
        // 经开票链产生的票一律销项，进项动作（关联/报销/认证）后端硬拒
        invoice.setDirection("OUTPUT");
        invInvoiceMapper.insert(invoice);
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        try {
            wfEngineFacade.start("INVOICE_ISSUE", invoice.getId(), userId,
                    "发票开具-" + invoice.getInvoiceNo(), invoice.getAmount());
        } catch (Exception ex) {
            log.error("开票单 {} 发起开票审批失败: {}", invoice.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return invoice.getId();
    }

    /**
     * 人工录入发票的票头必填守卫（必填项治理批次 2B，规则 Q2「前端 + 后端兜底」）。
     * <p>只给手工入口调用（{@code POST /api/tenant/invoice/create}、{@code /issue}）：
     * {@link #create} 同时是 OCR 识别入池的写入数据库通道（{@code InvoiceFacadeImpl#poolFromOcr}），
     * 机器识别的票面要素天然可能残缺，把校验写进 create 会把影像路径整单打回，
     * 故本方法守在人工边界而非 create 内部。文案与前端 INVOICE 规则逐字对齐。
     */
    public void requireManualHeader(InvInvoice invoice) {
        // 发票号码＝外部票据号（规则 Q1 人工必输例外）；发票代码在数电票下不存在，不设为必填
        if (isBlank(invoice.getInvoiceNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入发票号码");
        }
        if (invoice.getInvoiceDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择开票日期");
        }
        // 开票日期不得晚于当前系统日期——未来票入池后会被定时作业当场验真，
        // 落出「验真时间早于开票日期」的怪证据（历史 25000001/25000002 两行即此）。
        if (invoice.getInvoiceDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开票日期不得晚于当前日期，请核实后重新录入");
        }
        if (isBlank(invoice.getSellerName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入销售方名称");
        }
        if (isBlank(invoice.getInvoiceTitle())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入购买方名称（发票抬头）");
        }
        if (isBlank(invoice.getCurrencyCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择币种");
        }
        if (invoice.getAmount() == null || invoice.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入价税合计（须大于 0）");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 录入发票（价税分离校验）。红冲/销方黑名单的自动标记见 {@link #detectAnomaly}，
     *  购方抬头/税号的补写在方法与下方 D3 注释里。
     * 分库改造：inv_invoice 在 master 库；被 OcrService（image 库独立事务）调用时
     * REQUIRES_NEW 挂起外层 image 事务、绑定 master 连接。不显式声明事务的话，
     * 多步写入只能靠调用方事务或逐条自动提交，中途失败会留下半张票。 */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long create(InvInvoice invoice) {
        orgGuard.assertCompanyAllowed(invoice.getCompanyId());
        invoice.setId(null);
        // 全电票无发票代码：NULL 归一为空串（列已 NOT NULL DEFAULT ''），
        // 使 uk_tenant_inv 租户内查重对全电票生效，且与 inv_invoice_global 空串规则一致
        if (invoice.getInvoiceCode() == null) {
            invoice.setInvoiceCode("");
        }
        if (invoice.getAmount() == null || invoice.getNetAmount() == null || invoice.getTaxAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额/不含税/税额必填");
        }
        BigDecimal sum = invoice.getNetAmount().add(invoice.getTaxAmount());
        if (invoice.getAmount().compareTo(sum) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "价税不符：价税合计 " + invoice.getAmount() + " ≠ 不含税 " + invoice.getNetAmount()
                            + " + 税额 " + invoice.getTaxAmount());
        }
        if (invoice.getStatus() == null) {
            invoice.setStatus("NORMAL");
        }
        if (invoice.getLockStatus() == null) {
            invoice.setLockStatus("NONE");
        }
        invoice.setAnomalyFlag(detectAnomaly(invoice));
        Long tenantId = invoice.getTenantId() == null ? TenantContext.getTenantId() : invoice.getTenantId();
        // 手工补录防重：同租户同票号发票已被非 VOID 单据占用 → 拦截；
        // 同 code+no 精确重复（原依赖 uk_tenant_inv 抛裸 500）→ 友好报错
        assertNotDuplicateEntry(invoice);
        // 电子发票全局查重（跨租户预警）：同一"发票代码+号码"已在【其他企业】出现即预警，
        // 但不阻断创建（避免误伤换开发票）；同租户重复仍由 uk_tenant_inv 硬拦。
        boolean crossTenantDup = isGlobalDuplicated(invoice, tenantId);
        if (crossTenantDup && "NORMAL".equals(invoice.getAnomalyFlag())) {
            invoice.setAnomalyFlag("DUP_GLOBAL");
        }
        // 多分子公司审批路由：入库前补所属公司，前端已传不覆盖
        if (invoice.getCompanyId() == null) {
            LoginUser lu = UserContext.get();
            if (lu != null) invoice.setCompanyId(lu.getCompanyId());
        }
        invoice.setSourceDocType("MANUAL");
        // 购方信息在本表有两组语义重复的列，一组空则按该规则查询的报表必然漏行。
        //   invoice_title 注释「购方名称」 ← 手工补录表单填这一组
        //   buyer_name 注释「购方抬头」 ← OCR 链（OcrService#toPoolDto → InvoiceFacadeImpl）填这一组
        // 两条链路各填一半时，收票在池子里看得见、按购买方过滤的报表却一条都查不到。
        // 此处按互补规则双向补齐：任一组有值就回填另一组，单条链路与未来新调用方都自动受益；
        // 两组都空则保持 NULL（不臆造数据，缺失仍是缺失）。
        // 注意必须放在 insert 之前，且不能覆盖对方已有的非空值。
        if (isBlank(invoice.getBuyerName()) && !isBlank(invoice.getInvoiceTitle())) {
            invoice.setBuyerName(invoice.getInvoiceTitle());
        } else if (isBlank(invoice.getInvoiceTitle()) && !isBlank(invoice.getBuyerName())) {
            invoice.setInvoiceTitle(invoice.getBuyerName());
        }
        if (isBlank(invoice.getBuyerTaxNo()) && !isBlank(invoice.getTaxNo())) {
            invoice.setBuyerTaxNo(invoice.getTaxNo());
        } else if (isBlank(invoice.getTaxNo()) && !isBlank(invoice.getBuyerTaxNo())) {
            invoice.setTaxNo(invoice.getBuyerTaxNo());
        }
        invInvoiceMapper.insert(invoice);
        // 入库后 upsert 全局索引（记录 first_tenant_id / last_invoice_id / 出现次数）
        upsertGlobalIndex(invoice, tenantId);
        if (crossTenantDup) {
            alertGlobalDuplicate(invoice, tenantId);
        }
        return invoice.getId();
    }

    /**
     * 去重环节②·主域实现：这张「发票代码＋号码」是否已在当前租户的发票池里。
     *
     * <p>给 OCR 链的入池前置判定用（见 {@code OcrService#recognizeAndRecord}）。命中即跳过入池，
     * 不再靠 {@link #create} 里的 {@code assertNotDuplicateEntry} 抛异常后被调用方误记成"入池失败"。
     *
     * <p>过滤规则与 {@code assertNotDuplicateEntry} 的②（精确重复）一致：
     * {@code tenant_id} 由 {@code FsscTenantLineHandler} 自动追加、{@code del_flag=0} 由实体的
     * {@code @TableLogic} 自动追加，故此处不要再手工拼 tenant/del_flag（会变成重复条件、且无上下文时反被拦）。
     * 全电票的 {@code invoice_code} 在库里是空串（列 NOT NULL DEFAULT ''），入参须已由调用方归一。
     *
     * @param invoiceCode 发票代码（已归一，null 由调用方转空串）
     * @param invoiceNo 发票号码；为空＝无法判定 ⇒ 返回 false，让调用方按原路走查重
     * @return true＝池内有未作废的同码同号发票
     */
    @FsscDataSource("master")
    public boolean existsInPool(String invoiceCode, String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return false;
        }
        String code = invoiceCode == null ? "" : invoiceCode.trim();
        Long hits = invInvoiceMapper.selectCount(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceCode, code)
                .eq(InvInvoice::getInvoiceNo, invoiceNo.trim()));
        return hits != null && hits > 0;
    }

    /**
     * 发票手工补录防重（补录入口统一校验）：
     * <ol>
     *  <li>同租户同票号发票已被其他单据占用（inv_bill_invoice 勾稽，排除已作废 VOID 单据的关联）
     *      → 报「发票已存在于单据X，不得重复补录」；</li>
     *  <li>同 发票代码+号码 在发票池已存在（未占用）→ 友好报错，不让 uk_tenant_inv 撞唯一键变成裸 500。</li>
     * </ol>
     * 规则与提交侧判重统一：VOID 单据的勾稽不算占用，其余（在途/已批/已付）均算。
     */
    private void assertNotDuplicateEntry(InvInvoice invoice) {
        String no = invoice.getInvoiceNo();
        if (no == null || no.isBlank()) {
            return;
        }
        String code = invoice.getInvoiceCode() == null ? "" : invoice.getInvoiceCode().trim();
        List<InvInvoice> sameNo = invInvoiceMapper.selectList(new LambdaQueryWrapper<InvInvoice>()
                .eq(InvInvoice::getInvoiceNo, no.trim()));
        for (InvInvoice exist : sameNo) {
            // 已作废(INPUT 补录录错额后走 /invoice/void 记录作废)的池内记录不再占号：
            // 不放开这条，操作人作废自己那张错票后就再也录不回来（"作废→重录"这条纠错通道会断在第二步）。
            // 原记录仍留在池里（status=VOID、del_flag=0）可追溯，占用判定仍由下面①兜着。
            if ("VOID".equals(exist.getStatus())) {
                continue;
            }
            // ① 占用校验：同号发票被非 VOID 单据勾稽 → 拦截补录
            List<InvBillInvoice> links = invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                    .eq(InvBillInvoice::getInvoiceId, exist.getId()));
            for (InvBillInvoice link : links) {
                if (link.getBillId() == null) {
                    continue;
                }
                ExpBill linked = expBillMapper.selectById(link.getBillId());
                if (linked == null || "VOID".equals(linked.getStatus())) {
                    continue; // 已作废(VOID)单据的关联不算占用
                }
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票已存在于单据" + (linked.getBillNo() == null || linked.getBillNo().isBlank()
                                ? String.valueOf(linked.getId()) : linked.getBillNo())
                                + "，不得重复补录");
            }
            // ② 精确重复（code+no 已在池）→ 友好报错，不靠 uk_tenant_inv 抛 DuplicateKey 变成 500
            if (code.equals(exist.getInvoiceCode() == null ? "" : exist.getInvoiceCode().trim())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票 " + (code.isBlank() ? "全电票 " : code + " ") + no.trim()
                                + " 已存在于发票池（记录id=" + exist.getId() + "），不得重复录入");
            }
        }
    }

    /** 全局查重命中判定（跨租户只读）：该"代码+号码"是否已由【其他租户】首次录入。 */
    private boolean isGlobalDuplicated(InvInvoice invoice, Long tenantId) {
        String no = invoice.getInvoiceNo();
        if (no == null || no.isBlank()) {
            return false;
        }
        String code = normalizeCode(invoice.getInvoiceCode());
        boolean[] dup = {false};
        TenantContext.runIgnoreTenant(() -> {
            InvInvoiceGlobal g = invInvoiceGlobalMapper.selectOne(new LambdaQueryWrapper<InvInvoiceGlobal>()
                    .eq(InvInvoiceGlobal::getInvoiceCode, code)
                    .eq(InvInvoiceGlobal::getInvoiceNo, no)
                    .last("limit 1"));
            dup[0] = g != null && g.getFirstTenantId() != null && !g.getFirstTenantId().equals(tenantId);
        });
        return dup[0];
    }

    /** 全局索引 upsert（跨租户写入，runIgnoreTenant）：不存在则以当前租户为首录插入，存在则累计出现次数并回写最近发票ID。 */
    private void upsertGlobalIndex(InvInvoice invoice, Long tenantId) {
        String no = invoice.getInvoiceNo();
        if (no == null || no.isBlank()) {
            return;
        }
        String code = normalizeCode(invoice.getInvoiceCode());
        Long invId = invoice.getId();
        TenantContext.runIgnoreTenant(() -> {
            InvInvoiceGlobal g = invInvoiceGlobalMapper.selectOne(new LambdaQueryWrapper<InvInvoiceGlobal>()
                    .eq(InvInvoiceGlobal::getInvoiceCode, code)
                    .eq(InvInvoiceGlobal::getInvoiceNo, no)
                    .last("limit 1"));
            if (g == null) {
                InvInvoiceGlobal n = new InvInvoiceGlobal();
                n.setInvoiceCode(code);
                n.setInvoiceNo(no);
                n.setFirstTenantId(tenantId);
                n.setTenantCount(1);
                n.setInvoiceKind(invoice.getInvoiceKind());
                n.setLastInvoiceId(invId);
                invInvoiceGlobalMapper.insert(n);
            } else {
                g.setLastInvoiceId(invId);
                g.setTenantCount((g.getTenantCount() == null ? 1 : g.getTenantCount()) + 1);
                if (g.getInvoiceKind() == null) {
                    g.setInvoiceKind(invoice.getInvoiceKind());
                }
                invInvoiceGlobalMapper.updateById(g);
            }
        });
    }

    /** 跨企业重复预警：向当前租户税务经理（TAX_MANAGER，无则回退全活跃用户）推站内信告警。 */
    private void alertGlobalDuplicate(InvInvoice invoice, Long tenantId) {
        String label = (invoice.getInvoiceCode() == null || invoice.getInvoiceCode().isBlank()
                ? "全电票" : invoice.getInvoiceCode() + " ") + invoice.getInvoiceNo();
        String title = "发票跨企业重复预警";
        String content = "发票 " + label + " 已在其他企业使用，疑似重复报销，请人工核查。";
        try {
            for (Long uid : taxManagerUsers(tenantId)) {
                notifyService.notifyUser(uid, "DUP_GLOBAL", title, content, invoice.getId(), null);
            }
        } catch (Exception e) {
            log.warn("[发票全局查重] 预警推送失败 invoiceNo={}", invoice.getInvoiceNo(), e);
        }
    }

    /** 解析税务经理角色用户；该角色无用户时回退租户全部激活用户（与 InvoiceAbnormalAlertJob 同一规则）。 */
    private List<Long> taxManagerUsers(Long tenantId) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "TAX_MANAGER")
                .eq(SysRole::getStatus, "NORMAL")
                .last("limit 1"));
        List<Long> uids = new ArrayList<>();
        if (role != null) {
            uids = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()))
                    .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
        }
        if (!uids.isEmpty()) {
            return uids;
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "NORMAL"))
                .stream().map(SysUser::getId).collect(Collectors.toList());
    }

    /** 全电票发票代码归一：NULL → 空串，使全局唯一键 uk_global_code_no 正常生效。 */
    private String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    /** 判定发票异常标记——红冲/作废/销方黑名单命中即异常，进异常清单供资金/税务岗监控。 */
    private String detectAnomaly(InvInvoice invoice) {
        if ("RED".equalsIgnoreCase(invoice.getInvoiceNature())
                || "RED_FLUSH".equalsIgnoreCase(invoice.getStatus())) {
            return "RED_FLUSH";
        }
        if ("INVALID".equalsIgnoreCase(invoice.getStatus()) || "DISABLED".equalsIgnoreCase(invoice.getStatus())) {
            return "INVALID";
        }
        if (isSellerBlacklisted(invoice.getSellerTaxNo())) {
            return "SELLER_BLACK";
        }
        return "NORMAL";
    }

    /** 销方黑名单命中：按发票销方税号匹配主数据供应商黑名单（md_supplier.blacklist=1）。 */
    private boolean isSellerBlacklisted(String sellerTaxNo) {
        if (sellerTaxNo == null || sellerTaxNo.isBlank()) {
            return false;
        }
        Long count = mdSupplierMapper.selectCount(new LambdaQueryWrapper<MdSupplier>()
                .eq(MdSupplier::getTaxNo, sellerTaxNo.trim())
                .eq(MdSupplier::getBlacklist, 1));
        return count != null && count > 0;
    }

    /**
     * 发票异常清单（红冲/作废/销方黑名单），供 7×24 监控与税务岗复核。
     */
    public PageResult<InvInvoice> anomalyList(long pageNum, long pageSize, String anomalyFlag) {
        LambdaQueryWrapper<InvInvoice> qw = new LambdaQueryWrapper<>();
        if (anomalyFlag != null && !anomalyFlag.isBlank() && !"ALL".equalsIgnoreCase(anomalyFlag)) {
            qw.eq(InvInvoice::getAnomalyFlag, anomalyFlag);
        } else {
            qw.ne(InvInvoice::getAnomalyFlag, "NORMAL");
        }
        qw.orderByDesc(InvInvoice::getId);
        Page<InvInvoice> p = invInvoiceMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 发票累计占用金额（Σ inv_bill_invoice.amount）：供「发票→报销草稿」自动填单判定剩余面额。
     */
    public BigDecimal occupiedAmount(Long invoiceId) {
        List<InvBillInvoice> recs = invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        return recs.stream()
                .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 发票关联到单据（校验发票存在 + 占用累计金额 ≤ 发票金额 #7）。
     * 占用累计 = Σ(inv_bill_invoice.amount)，超出发票金额即拦截，防发票被重复超额报销。
     */
    /**
     * 发票剩余可用面额 = 票面额 − 其他单据已占用合计（不小于 0）。
     * 供批量绑票通道逐票自动占用时使用。
     */
    public BigDecimal remainingAmount(Long invoiceId) {
        InvInvoice invoice = invInvoiceMapper.selectById(invoiceId);
        if (invoice == null || invoice.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        List<InvBillInvoice> others = invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        BigDecimal used = others.stream()
                .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remain = invoice.getAmount().subtract(used);
        return remain.compareTo(BigDecimal.ZERO) > 0 ? remain : BigDecimal.ZERO;
    }

    /**
     * 销项票判定＝direction 列 或 开票链推导
     * （sourceDocType=SALES_INVOICE/RED_FLUSH、状态 ISSUING/ISSUED/RED_FLUSHED/RED_FLUSH）。
     * 推导项是为覆盖 direction 列 DDL 之前/旧 jar 代次差窗内产生的历史票。
     */
    public static boolean isOutputInvoice(InvInvoice inv) {
        if (inv == null) {
            return false;
        }
        if ("OUTPUT".equalsIgnoreCase(inv.getDirection())) {
            return true;
        }
        String sdt = inv.getSourceDocType();
        String st = inv.getStatus();
        return "SALES_INVOICE".equals(sdt) || "RED_FLUSH".equals(sdt)
                || "ISSUING".equals(st) || "ISSUED".equals(st)
                || "RED_FLUSHED".equals(st) || "RED_FLUSH".equals(st);
    }

    /**
     * 同一（单据, 单据行）是否已勾稽过这张发票。bindToBill 每次调用都新插一行，重复提交即为重复占用，
     * 所以关联前先按这一对判重；lineId 为空表示整单关联，只与同样为空的既有行相撞。
     */
    static boolean alreadyBound(Collection<InvBillInvoice> links, Long billId, Long lineId) {
        if (links == null || billId == null) {
            return false;
        }
        for (InvBillInvoice link : links) {
            if (link == null || !billId.equals(link.getBillId())) {
                continue;
            }
            Long existing = link.getLineId();
            if (lineId == null ? existing == null : lineId.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long bindToBill(Long billId, Long lineId, Long invoiceId, BigDecimal amount) {
        // 必填兜底：billId 为空历史上会在下面的占用累计计算处 billId.equals(...) 抛 NPE（裸 500），
        // 并会插入 bill_id 为空的孤儿勾稽记录，使发票被计为「已占用」却无任何单据可追溯。
        if (invoiceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择发票");
        }
        if (billId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要关联的业务单据");
        }
        InvInvoice invoice = invInvoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票不存在: " + invoiceId);
        }
        BigDecimal occupyAmount = amount == null ? BigDecimal.ZERO : amount;
        if (occupyAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票占用金额不能为负数");
        }
        // 红冲/已作废票禁止再被占用（防二次报销/红冲联动）
        if ("RED_FLUSH".equalsIgnoreCase(invoice.getStatus())
                || "RED".equalsIgnoreCase(invoice.getInvoiceNature())
                || "INVALID".equalsIgnoreCase(invoice.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + invoice.getInvoiceCode() + " " + invoice.getInvoiceNo()
                            + " 为" + ("RED".equalsIgnoreCase(invoice.getInvoiceNature()) ? "红字" : "作废")
                            + "发票，禁止绑定占用/报销");
        }
        // 销项票＝本公司开出去的票，不得被采购/报销单据关联占用，
        // 否则三单匹配金额虚增、虚报路径打通。
        // direction 列是后加的，历史行在代次差窗内可能恒为默认 INPUT ⇒ 叠加开票链推导再判。
        if (isOutputInvoice(invoice)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + invoice.getInvoiceNo() + " 是销项发票（本公司开具），不可关联业务单据占用；"
                            + "可抵扣对象应为收到的进项票");
        }
        if (invoice.getAmount() != null && occupyAmount.compareTo(BigDecimal.ZERO) > 0) {
            List<InvBillInvoice> links = invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                    .eq(InvBillInvoice::getInvoiceId, invoiceId));
            if (alreadyBound(links, billId, lineId)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票 " + invoice.getInvoiceCode() + " " + invoice.getInvoiceNo()
                                + " 已关联本单据，请勿重复关联；需调整占用金额请先【反核销】解除原关联，再关联一次");
            }
            // 本单已勾稽的行同样计入占用：本方法只 INSERT 不改行，放过本单就会让同一张票被一张单吃穿
            BigDecimal used = links.stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalOccupy = used.add(occupyAmount);
            if (totalOccupy.compareTo(invoice.getAmount()) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "发票占用累计超限：发票 " + invoice.getInvoiceCode() + " " + invoice.getInvoiceNo()
                                + " 面额 " + invoice.getAmount() + "，已占用 " + used + "，本次 " + occupyAmount
                                + "，合计 " + totalOccupy + " 超出面额");
            }
        }
        InvBillInvoice bind = new InvBillInvoice();
        bind.setTenantId(TenantContext.getTenantId());
        bind.setBillId(billId);
        bind.setLineId(lineId);
        bind.setInvoiceId(invoiceId);
        bind.setAmount(occupyAmount);
        invBillInvoiceMapper.insert(bind);
        // 占用后置入账锁定（LOCKED_IN_USE），防再次占用/期间红冲二次入账
        if (!"LOCKED_IN_USE".equals(invoice.getLockStatus())) {
            InvInvoice lock = new InvInvoice();
            lock.setId(invoiceId);
            lock.setLockStatus("LOCKED_IN_USE");
            lock.setSourceDocType("BILL");
            lock.setSourceDocId(billId);
            invInvoiceMapper.updateById(lock);
        }
        // PTP接通：发票绑定对公付款单时回写收货单开票金额（激活原零调用方 markInvoiced）
        // PO → 收货 → 发票(markInvoiced) → 三单匹配 → 付款，采购循环第一环闭合
        try {
            ExpBill bill = expBillMapper.selectById(billId);
            if (bill != null && "PAY".equals(bill.getBillType()) && bill.getPoReceiptId() != null) {
                purchaseService.markInvoiced(bill.getPoReceiptId(), occupyAmount);
                log.info("[PTP] 发票 {} 绑定付款单 {} → 回写收货单 {} 开票金额 {}", invoiceId, billId, bill.getPoReceiptId(), occupyAmount);
                // 发票与收货单匹配登记点——红字冲回暂估（借 暂估应付/贷 存货）+ 正式入账（借 存货/贷 应付），
                // 去重（冲回按收货单、入账按发票），科目缺失/异常仅 warn 不阻断发票绑定
                PoReceipt matchedReceipt = null;
                try {
                    matchedReceipt = purchaseService.receiptById(bill.getPoReceiptId());
                } catch (Exception ignore) {
                    // 收货单缺失时冲回仍按 ID 去重生成
                }
                voucherService.reverseEstimateOnInvoiceMatch(
                        bill.getPoReceiptId(),
                        matchedReceipt == null ? null : matchedReceipt.getReceiptNo(),
                        invoiceId, invoice.getInvoiceNo(), occupyAmount);
            }
        } catch (Exception ex) {
            log.error("[PTP] 发票绑定回写收货单开票金额失败（不影响发票绑定本身）: billId={}, {}", billId, ex.getMessage(), ex);
        }
        // #12 占用计数与明细表同步（used_count = Σinv_bill_invoice 记录数，is_used 随之置位）
        recalcUsedCount(invoiceId);
        return bind.getId();
    }

    /** 按单据号取单（反核销按号解析入口）。 */
    public ExpBill billByNo(String billNo) {
        return expBillMapper.selectOne(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getBillNo, billNo)
                .last("limit 1"));
    }

    /** 不再接受发票勾稽占用的单据最终状态（三单匹配候选窗口排除项） */
    private static final List<String> BIND_DEAD_BILL_STATUS = List.of("VOID", "TERMINATED", "REJECTED", "WITHDRAWN");

    /**
     * 发票「关联单据」候选单据页。
     *
     * <p><b>为什么不能按提单人查</b>：弹窗若取 {@code /expense/bill/myPage}，服务端硬过滤
     * {@code apply_user_id = 当前登录人}，集中作业模式下的共享会计（代位制单/代位录票）
     * 下拉里就只有<b>自己提的单</b>，他人单据的发票既录不上也绑不上；而三单匹配的刚性拦截
     * （{@code PAY_EXCEED_INVOICE}＝关联发票 0 < 本次付款）唯一的解法就是「补录发票 → 关联单据」，
     * 出口一旦不可达，批次判超付后就永久停在执行环节（{@code PayBatchService.execute} 每次现算现拦）。</p>
     *
     * <p><b>规则</b>：候选单据按<b>操作权限</b>查询（能绑票＝能选单，端点权限码 {@code inv:bind}），
     * 不按提单人查询。组织维度仍受 {@link OrgGuard} 约束——
     * 集团级账号（无公司）跨公司可见，普通账号只见本公司单据 + 无公司维度的历史单据 + 本人提的单，
     * 与 {@code OrgGuard.assertVisible} 同一套 fail-open 规则，不放宽越权面。</p>
     *
     * <p>每行附带发票环节差额（{@code invoiceBound}＝本单已勾稽发票占用合计，
     * {@code invoiceGap}＝应付额 − 已覆盖额），使经办在绑票前就能看清"还差多少票"，
     * 绑完再跑一次三单匹配即知是否解开。</p>
     *
     * @param keyword 单号/标题/提单人/收款方 模糊匹配（弹窗按单号回车检索）
     * @param billType 单据类型（PAY/CLAIM/SETTLE…），空＝不限
     * @param status 单据状态，空＝不限；作废/终止/驳回/撤回的死单一律排除
     */
    public PageResult<Map<String, Object>> bindableBills(long pageNum, long pageSize, String keyword,
                                                         String billType, String status) {
        final Long company = orgGuard.enforceCompany();
        final Long self = UserContext.get() == null ? null : UserContext.get().getUserId();
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<>();
        if (company != null) {
            qw.and(w -> {
                w.eq(ExpBill::getCompanyId, company).or().isNull(ExpBill::getCompanyId);
                if (self != null) {
                    w.or().eq(ExpBill::getApplyUserId, self);
                }
            });
        }
        if (billType != null && !billType.isBlank()) {
            qw.eq(ExpBill::getBillType, billType.trim());
        }
        if (status != null && !status.isBlank()) {
            qw.eq(ExpBill::getStatus, status.trim());
        }
        qw.notIn(ExpBill::getStatus, BIND_DEAD_BILL_STATUS);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ExpBill::getBillNo, kw).or().like(ExpBill::getTitle, kw)
                    .or().like(ExpBill::getApplyUserName, kw).or().like(ExpBill::getPayeeName, kw));
        }
        qw.orderByDesc(ExpBill::getId);
        Page<ExpBill> p = expBillMapper.selectPage(Page.of(pageNum, pageSize), qw);
        List<ExpBill> records = p.getRecords();

        // 发票环节现算：一页 ≤ 若干行，一次 in 查询取回勾稽占用合计（与三单匹配同一规则 inv_bill_invoice）
        Map<Long, BigDecimal> boundByBill = new HashMap<>();
        if (!records.isEmpty()) {
            List<Long> ids = records.stream().map(ExpBill::getId).filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!ids.isEmpty()) {
                for (InvBillInvoice link : invBillInvoiceMapper.selectList(
                        new LambdaQueryWrapper<InvBillInvoice>().in(InvBillInvoice::getBillId, ids))) {
                    if (link.getBillId() == null) {
                        continue;
                    }
                    boundByBill.merge(link.getBillId(), nzAmt(link.getAmount()), BigDecimal::add);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpBill b : records) {
            BigDecimal bound = boundByBill.getOrDefault(b.getId(), BigDecimal.ZERO);
            // 与三单匹配的付款环节同一规则：核定付款额优先，退回单头合计额
            BigDecimal pay = b.getApprovedPayAmount() != null ? b.getApprovedPayAmount() : nzAmt(b.getTotalAmount());
            BigDecimal gap = pay.subtract(bound);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("billNo", b.getBillNo());
            m.put("title", b.getTitle());
            m.put("billType", b.getBillType());
            m.put("status", b.getStatus());
            m.put("companyId", b.getCompanyId());
            m.put("applyUserId", b.getApplyUserId());
            m.put("applyUserName", b.getApplyUserName());
            m.put("payeeName", b.getPayeeName());
            m.put("totalAmount", b.getTotalAmount());
            m.put("paidAmount", b.getPaidAmount());
            m.put("payAmount", pay);
            m.put("invoiceBound", bound);
            m.put("invoiceGap", gap.signum() < 0 ? BigDecimal.ZERO : gap);
            rows.add(m);
        }
        return PageResult.of(rows, p.getTotal(), pageNum, pageSize);
    }

    private static BigDecimal nzAmt(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 反核销（配套 · 单据反操作链第一环）：解除发票-单据勾稽并解锁发票。
     *
     * <p>前置：本单据不存在 PAID 付款记录——付款落地后勾稽是既成资金事实的凭证线索，不得反；
     * 资金差错走付款退款单（同一规则）。</p>
     *
     * <p>语义：勾稽记录软删（del_flag=1，记录不物理删）；发票 lock_status 解锁
     * （LOCKED_IN_USE → NONE，清占用溯源）；占用额度随之释放（remaining/used_count 均按
     * del_flag=0 重算）。反核销后单据可正常重提或做差额重审。</p>
     *
     * @return 解除的勾稽记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int unbindFromBill(Long billId, Long invoiceId) {
        if (billId == null || invoiceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定单据与发票");
        }
        List<InvBillInvoice> links = invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getBillId, billId)
                .eq(InvBillInvoice::getInvoiceId, invoiceId)
                .eq(InvBillInvoice::getDelFlag, 0));
        if (links.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未找到该发票与本单据的有效勾稽记录");
        }
        Long paidCnt = expBillPaymentMapper.selectCount(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, billId)
                .eq(ExpBillPayment::getPayStatus, "PAID"));
        if (paidCnt != null && paidCnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该单据付款已执行，勾稽不可反核销（资金差错请走付款退款单）");
        }
        int n = 0;
        for (InvBillInvoice link : links) {
            // del_flag 是 @TableLogic 字段，updateById 会把它从 SET 里剔掉 ⇒ 生成 "UPDATE t WHERE id=?" 空 SET，
            // MyBatis-Plus 直接抛 MybatisPlusException（解绑端点补了权限门仍 500 的根因）。逻辑删除要走 deleteById。
            n += invBillInvoiceMapper.deleteById(link.getId());
        }
        // 解锁发票（清占用溯源需置 NULL，updateById 跳过空值 → 用 UpdateWrapper）
        LambdaUpdateWrapper<InvInvoice> unlock =
                new LambdaUpdateWrapper<InvInvoice>()
                        .eq(InvInvoice::getId, invoiceId)
                        .set(InvInvoice::getLockStatus, "NONE")
                        .set(InvInvoice::getSourceDocType, null)
                        .set(InvInvoice::getSourceDocId, null);
        invInvoiceMapper.update(null, unlock);
        recalcUsedCount(invoiceId);
        // 反核销同步释放收货单开票额（勾稽列两向都要跟着明细走，否则 po_receipt.invoice_amount 只增不减）
        try {
            ExpBill b = expBillMapper.selectById(billId);
            if (b != null && b.getPoReceiptId() != null) {
                purchaseService.markInvoiced(b.getPoReceiptId(), BigDecimal.ZERO);
            }
        } catch (Exception ex) {
            log.warn("[PTP] 反核销后回写收货单开票额失败: billId={}, {}", billId, ex.getMessage());
        }
        return n;
    }

    /**
     * 重算发票占用计数（#12）：used_count = 关联明细记录数，is_used = 是否被占用。
     * 计数以明细表为唯一真相源，避免累加式维护导致的漂移。
     */
    private void recalcUsedCount(Long invoiceId) {
        Long cnt = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        InvInvoice u = new InvInvoice();
        u.setId(invoiceId);
        u.setUsedCount(cnt == null ? 0 : cnt.intValue());
        u.setIsUsed(cnt != null && cnt > 0 ? 1 : 0);
        invInvoiceMapper.updateById(u);
    }

    /**
     * 占用计数一致性对账（#12）：扫描租户内发票，比对 used_count 与 Σinv_bill_invoice 记录数。
     * 返回不一致清单（含实算值），供运维定位漂移数据；fix=true 时顺带自愈。
     *
     * <p>漂移集合由 {@link com.fssc.invoice.mapper.InvInvoiceMapper#selectUsedCountDiff} 一次 JOIN 出，
     * 不再「取全租户发票→逐张计数」（11.2 万次往返，实测 57 秒）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> auditUsedCount(boolean fix) {
        Long tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> diff = new ArrayList<>(invInvoiceMapper.selectUsedCountDiff(tenantId));
        if (fix) {
            for (Map<String, Object> m : diff) {
                recalcUsedCount(((Number) m.get("invoiceId")).longValue());
                m.put("fixed", true);
            }
        }
        log.info("[发票占用计数对账] 租户 {} 不一致 {} 张，fix={}", tenantId, diff.size(), fix);
        return diff;
    }

    /** 单据已关联发票列表 */
    public List<InvBillInvoice> billInvoices(Long billId) {
        return invBillInvoiceMapper.selectList(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getBillId, billId));
    }

    // 销项发票作废与红冲

    /**
     * 销项发票作废（当月作废）：仅当月（开票日期与当前同月）且未被收款核销/单据占用引用时
     * 可作废，status 置 VOID、remark 记录（原因+操作人+时间）、异常标记置 INVALID。
     * 不满足条件抛明确业务异常。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidIssued(Long invoiceId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废原因必填");
        }
        InvInvoice inv = requireInvoice(invoiceId);
        String status = inv.getStatus() == null ? "" : inv.getStatus();
        if ("VOID".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票 " + noOf(inv) + " 已作废，请勿重复操作");
        }
        // 进项「发票补录」录错额（典型＝税额填 0，被三单匹配按不含税规则误判超收货）也要有正规出口：
        // 这类票 direction=INPUT、status=NORMAL，从未走开具流程 ⇒ 与销项 ISSUED 同列可作废集。
        // 下面「当月」与「未被勾稽引用」两道前置照旧拦，所以放宽只放开纠错通道、不放开已核销资金。
        boolean apEntryCorrectable = "INPUT".equals(inv.getDirection()) && "NORMAL".equals(status);
        if (!"ISSUED".equals(status) && !apEntryCorrectable) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已开具(ISSUED)的销项发票、或未核销的进项补录票(INPUT/NORMAL)可作废，当前状态: " + status
                            + " 方向: " + inv.getDirection());
        }
        // 仅当月可作废：开票日期与当前同月（未填开票日期按录入当月处理）
        LocalDate invDate = inv.getInvoiceDate() != null
                ? inv.getInvoiceDate() : (inv.getCreateTime() != null ? inv.getCreateTime().toLocalDate() : LocalDate.now());
        YearMonth invMonth = YearMonth.from(invDate);
        if (!invMonth.equals(YearMonth.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + noOf(inv) + " 开票日期 " + invDate + " 非当月，不可作废，请走跨月红冲（/invoice/red-flush）");
        }
        // 未被收款核销/单据占用引用：inv_bill_invoice 存在勾稽即拦截
        Long bound = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        if (bound != null && bound > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + noOf(inv) + " 已被 " + bound + " 条单据/收款核销记录引用，不可作废");
        }
        InvInvoice upd = new InvInvoice();
        upd.setId(invoiceId);
        upd.setStatus("VOID");
        upd.setAnomalyFlag("INVALID");
        upd.setRemark(appendRemark(inv.getRemark(), "【作废】原因：" + reason.trim()));
        invInvoiceMapper.updateById(upd);
        log.info(" 销项发票 {} 已作废（原因: {}，操作人: {}）",
                noOf(inv), reason.trim(), UserContext.get() == null ? "system" : UserContext.get().getUsername());
    }

    /**
     * 进项「发票补录」金额更正（的出口）：补录时把税额填成 0，会让三单匹配按
     * 「价税 − 税额」折算后判成「发票超收货超容差」，而票号上有 uk_tenant_inv 唯一键 ⇒ 作废后同号码录不回来，
     * 光补 inv:void/inv:unbind 两道门并不能纠错。这里给一张<b>未被占用</b>的进项票一个改额通道：
     * 只改三个金额字段并把 VOID/NORMAL 归位成 NORMAL，票号、销方、日期、影像一律不动，记录追加进 remark。
     */
    public void correctEntry(Long invoiceId, BigDecimal netAmount, BigDecimal taxAmount,
                             BigDecimal amount, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "更正原因必填");
        }
        if (netAmount == null || taxAmount == null || amount == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不含税、税额、价税合计三项均必填");
        }
        InvInvoice inv = requireInvoice(invoiceId);
        if (!"INPUT".equals(inv.getDirection())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅进项补录票可更正金额，当前方向: " + inv.getDirection() + "（销项请走红冲或作废正规口）");
        }
        String status = inv.getStatus() == null ? "" : inv.getStatus();
        if (!"NORMAL".equals(status) && !"VOID".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅未入池流转的进项票（NORMAL/VOID）可更正，当前状态: " + status);
        }
        if (netAmount.signum() < 0 || taxAmount.signum() < 0 || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额不能为负且价税合计必须大于 0");
        }
        if (netAmount.add(taxAmount).subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "价税不符：价税合计 " + amount + " ≠ 不含税 " + netAmount + " + 税额 " + taxAmount);
        }
        Long bound = invBillInvoiceMapper.selectCount(new LambdaQueryWrapper<InvBillInvoice>()
                .eq(InvBillInvoice::getInvoiceId, invoiceId));
        if (bound != null && bound > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + noOf(inv) + " 已被 " + bound + " 条单据勾稽引用，请先解除关联再更正");
        }
        InvInvoice upd = new InvInvoice();
        upd.setId(invoiceId);
        upd.setNetAmount(netAmount);
        upd.setTaxAmount(taxAmount);
        upd.setAmount(amount);
        upd.setStatus("NORMAL");
        upd.setAnomalyFlag("NORMAL");
        upd.setRemark(appendRemark(inv.getRemark(), "【更正】不含税 " + netAmount + "／税额 " + taxAmount
                + "／价税 " + amount + "；原因：" + reason.trim()
                + "；操作人：" + (UserContext.get() == null ? "system" : UserContext.get().getUsername())));
        invInvoiceMapper.updateById(upd);
        log.info("进项补录票 {} 金额已更正为 不含税 {}/税额 {}/价税 {}（原因: {}）",
                noOf(inv), netAmount, taxAmount, amount, reason.trim());
    }

    /**
     * 销项发票跨月红冲：生成负数金额的红字发票记录（新 id，invoice_no=原号+R 后缀，
     * amount/net/tax 取负，original_id 关联原票，status=RED_FLUSH、invoice_nature=RED），
     * 原票状态置 RED_FLUSHED、remark 记录。仅 ISSUED 原票可红冲；已红冲的拒绝重复操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long redFlush(Long invoiceId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "红冲原因必填");
        }
        InvInvoice origin = requireInvoice(invoiceId);
        String status = origin.getStatus() == null ? "" : origin.getStatus();
        if ("RED_FLUSHED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票 " + noOf(origin) + " 已红冲，请勿重复操作");
        }
        if (!"ISSUED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已开具(ISSUED)的销项发票可红冲，当前状态: " + status);
        }
        if (origin.getAmount() == null || origin.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票 " + noOf(origin) + " 金额无效，不可红冲");
        }
        // 红字发票记录：invoice_no=原号+R（32 位长度保护），金额取负
        String redNo = (origin.getInvoiceNo() == null ? String.valueOf(origin.getId()) : origin.getInvoiceNo()) + "R";
        if (redNo.length() > 32) {
            redNo = redNo.substring(redNo.length() - 32);
        }
        InvInvoice red = new InvInvoice();
        red.setInvoiceCode(origin.getInvoiceCode() == null ? "" : origin.getInvoiceCode());
        red.setInvoiceNo(redNo);
        red.setInvoiceType(origin.getInvoiceType());
        red.setInvoiceKind(origin.getInvoiceKind());
        red.setInvoiceNature("RED");
        red.setInvoiceTitle(origin.getInvoiceTitle());
        red.setTaxNo(origin.getTaxNo());
        red.setSellerName(origin.getSellerName());
        red.setSellerTaxNo(origin.getSellerTaxNo());
        red.setBuyerName(origin.getBuyerName());
        red.setBuyerTaxNo(origin.getBuyerTaxNo());
        red.setInvoiceDate(LocalDate.now());
        // 负数金额（红冲）：amount = -(net + tax)，保持价税分离勾稽
        BigDecimal net = origin.getNetAmount() == null ? BigDecimal.ZERO : origin.getNetAmount();
        BigDecimal tax = origin.getTaxAmount() == null ? BigDecimal.ZERO : origin.getTaxAmount();
        red.setNetAmount(net.negate());
        red.setTaxAmount(tax.negate());
        red.setAmount(origin.getAmount().negate());
        red.setCurrencyCode(origin.getCurrencyCode());
        red.setStatus("RED_FLUSH");
        red.setLockStatus("NONE");
        red.setVerifyStatus("NONE");
        red.setAnomalyFlag("RED_FLUSH");
        red.setCompanyId(origin.getCompanyId());
        red.setSourceDocType("RED_FLUSH");
        red.setSourceDocId(origin.getId());
        // 红字票继承原票方向（红冲只发生在销项链）
        red.setDirection("OUTPUT");
        red.setOriginalId(origin.getId());
        red.setTenantId(TenantContext.getTenantId());
        red.setRemark(appendRemark(null, "【红冲】原票 " + noOf(origin) + "；原因：" + reason.trim()));
        invInvoiceMapper.insert(red);
        // 原票置 RED_FLUSHED + 记录
        InvInvoice upd = new InvInvoice();
        upd.setId(origin.getId());
        upd.setStatus("RED_FLUSHED");
        upd.setAnomalyFlag("RED_FLUSH");
        upd.setRemark(appendRemark(origin.getRemark(),
                "【红冲】已生成红字发票 " + redNo + "（id=" + red.getId() + "）；原因：" + reason.trim()));
        invInvoiceMapper.updateById(upd);
        log.info(" 销项发票 {} 跨月红冲完成，红字发票 id={} {} 金额={}（操作人: {}）",
                noOf(origin), red.getId(), redNo, red.getAmount(),
                UserContext.get() == null ? "system" : UserContext.get().getUsername());
        return red.getId();
    }

    /** remark 记录追加（500 长度保护，超长保留尾部） */
    private String appendRemark(String base, String addition) {
        String merged = (base == null || base.isBlank() ? "" : base + "；") + addition;
        return merged.length() > 500 ? merged.substring(merged.length() - 500) : merged;
    }

    /**
     * 发票查询（需求 7.3）：按 业务编号/发票日期范围/扫描(录入)日期范围/供应商/发票代码·号码 等条件获取发票数据。
     */
    public PageResult<InvInvoice> page(long pageNum, long pageSize, String keyword,
                                       String invoiceNo, String invoiceCode, String sellerName,
                                       String invoiceDateFrom, String invoiceDateTo,
                                       String scanDateFrom, String scanDateTo, String billNo,
                                       String verifyStatus, String certifyStatus, String direction) {
        LambdaQueryWrapper<InvInvoice> qw = new LambdaQueryWrapper<>();
        // 方向筛选。INPUT 需含 direction 列为 NULL 的历史行（DDL 前旧链路），OUTPUT 精确等值。
        if ("INPUT".equalsIgnoreCase(direction)) {
            qw.ne(InvInvoice::getDirection, "OUTPUT");
        } else if ("OUTPUT".equalsIgnoreCase(direction)) {
            qw.eq(InvInvoice::getDirection, "OUTPUT");
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(InvInvoice::getInvoiceNo, keyword)
                    .or().like(InvInvoice::getSellerName, keyword)
                    .or().like(InvInvoice::getInvoiceCode, keyword));
        }
        if (invoiceNo != null && !invoiceNo.isBlank()) {
            qw.eq(InvInvoice::getInvoiceNo, invoiceNo);
        }
        if (invoiceCode != null && !invoiceCode.isBlank()) {
            qw.like(InvInvoice::getInvoiceCode, invoiceCode);
        }
        if (sellerName != null && !sellerName.isBlank()) {
            qw.like(InvInvoice::getSellerName, sellerName);
        }
        if (invoiceDateFrom != null && !invoiceDateFrom.isBlank()) {
            qw.ge(InvInvoice::getInvoiceDate, invoiceDateFrom);
        }
        if (invoiceDateTo != null && !invoiceDateTo.isBlank()) {
            qw.le(InvInvoice::getInvoiceDate, invoiceDateTo);
        }
        if (scanDateFrom != null && !scanDateFrom.isBlank()) {
            qw.ge(InvInvoice::getCreateTime, scanDateFrom + " 00:00:00");
        }
        if (scanDateTo != null && !scanDateTo.isBlank()) {
            qw.le(InvInvoice::getCreateTime, scanDateTo + " 23:59:59");
        }
        if (verifyStatus != null && !verifyStatus.isBlank()) {
            qw.eq(InvInvoice::getVerifyStatus, verifyStatus);
        }
        if (certifyStatus != null && !certifyStatus.isBlank()) {
            qw.eq(InvInvoice::getCertifyStatus, certifyStatus);
        }
        if (billNo != null && !billNo.isBlank()) {
            // 业务编号：经 inv_bill_invoice 关联 exp_bill 反查发票（多租户拦截器自动注入 tenant_id）
            qw.apply("exists (select 1 from inv_bill_invoice bi join exp_bill b on bi.bill_id = b.id " +
                    "where bi.invoice_id = inv_invoice.id and b.bill_no like concat('%', {0}, '%'))", billNo);
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(InvInvoice::getCompanyId, scopeC);
        }
        qw.orderByDesc(InvInvoice::getId);
        Page<InvInvoice> p = invInvoiceMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }
}
