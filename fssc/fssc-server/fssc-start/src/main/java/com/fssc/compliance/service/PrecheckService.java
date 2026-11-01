// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.mapper.ExpBillAttachMapper;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.service.ExpenseBillService;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.mapper.ExpensePolicyMapper;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 合规预检引擎（风险前置）：提交时一次性校验所有合规规则，聚合反馈全部不合规项。
 * 规则维度：费用项合法性 / 金额 / 发票要求 / 政策金额上限 / 提交时限 / 重复发票 / 费用标准超标(WARN)。
 * FAIL 拦截提交；WARN 提示不拦截（如标准超标，审批人据实裁断）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrecheckService {

    // 拆单检测（SPLIT_ORDER_DETECT）参数：与 md_compliance_rule SPLIT_ORDER_DETECT 的 threshold_config 保持一致
    /** 审批阈值：报销金额达到该值需升级更高层审批（默认 5000 元；调整时同步规则表 threshold_config） */
    private static final BigDecimal SPLIT_APPROVAL_THRESHOLD = new BigDecimal("5000");
    /** 命中区间：阈值下方 10%（[阈值×(1-10%), 阈值)——拆单规避审批的典型落点） */
    private static final BigDecimal SPLIT_BAND_RATIO = new BigDecimal("0.10");
    /** 聚合窗口：提交日回溯 7 天 */
    private static final int SPLIT_WINDOW_DAYS = 7;
    /** 窗口内（含本单）同人同收款方临界区间的报销单达到该张数即疑似拆单 */
    private static final int SPLIT_MIN_COUNT = 3;

    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper lineMapper;
    private final ExpBillAttachMapper attachMapper;
    private final MdExpenseItemMapper itemMapper;
    private final ExpensePolicyMapper policyMapper;
    /** 发票池读/勾稽读经发票域 Facade（分库断点）：本类不再直连 inv_invoice/inv_bill_invoice */
    private final InvoiceDomainFacade invoiceFacade;
    /** 票面键/票号查重经影像域 Facade（服务化断点）：本类不再直连 fssc_image 库 */
    private final ImageFacade imageFacade;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 对草稿单据执行合规预检（一次性返回全部不合规项） */
    public List<PrecheckItem> precheck(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        List<ExpBillLine> lines = lineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                .eq(ExpBillLine::getBillId, billId));
        List<PrecheckItem> items = new ArrayList<>();

        // 1) 完整性：至少一条明细
        if (lines.isEmpty()) {
            items.add(PrecheckItem.fail("COMPLETE_LINE", "报销单至少需要一条费用明细"));
        }

        // 费用项级聚合
        BigDecimal total = BigDecimal.ZERO;
        boolean needInvoice = false;
        Map<Long, List<ExpBillLine>> byItem = lines.stream().collect(Collectors.groupingBy(ExpBillLine::getItemId));
        List<Long> itemIds = lines.stream().map(ExpBillLine::getItemId).distinct().toList();
        Map<Long, MdExpenseItem> itemMap = itemIds.isEmpty() ? Map.of() : itemMapper.selectBatchIds(itemIds)
                .stream().collect(Collectors.toMap(MdExpenseItem::getId, x -> x));

        for (ExpBillLine line : lines) {
            // 2) 费用项合法性 + 金额
            MdExpenseItem item = itemMap.get(line.getItemId());
            if (item == null) {
                items.add(PrecheckItem.fail("ITEM_INVALID", "费用项不存在: " + line.getItemId()));
                continue;
            }
            if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                items.add(PrecheckItem.fail("AMOUNT_INVALID", "明细金额必须大于 0: " + item.getItemName()));
            }
            if (item.getNeedInvoice() != null && item.getNeedInvoice() == 1) {
                needInvoice = true;
            }
            total = total.add(line.getAmount() == null ? BigDecimal.ZERO : line.getAmount());
        }

        // 3) 发票要求（费用项要求 or 政策要求）
        boolean isClaim = "CLAIM".equals(bill.getBillType());
        List<ExpensePolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<ExpensePolicy>()
                .eq(ExpensePolicy::getStatus, 1)
                .in(!byItem.isEmpty(), ExpensePolicy::getExpenseItemId, byItem.keySet()));
        for (ExpensePolicy p : policies) {
            if (p.getNeedInvoice() != null && p.getNeedInvoice() == 1) {
                needInvoice = true;
            }
        }
        if (isClaim && needInvoice) {
            long invoiceCnt = attachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, billId).eq(ExpBillAttach::getAttachType, "INVOICE"));
            if (invoiceCnt == 0) {
                items.add(PrecheckItem.fail("INVOICE_REQUIRED", "该单据费用项/政策要求必须附发票，当前未上传"));
            }
        }

        // 3.1) 事前申请（出差/培训/会议）附件强制（合规整改 /03）
        if ("APPLY".equals(bill.getBillType())) {
            long anyAttach = attachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, billId));
            if (anyAttach == 0) {
                items.add(PrecheckItem.fail("APPLY_ATTACH_REQUIRED",
                        "事前申请必须上传附件（行程单、出差/会议/培训通知、审批件等），当前未上传任何附件"));
            }
        }

        // 3.2) 跨单重复发票（合规整改）：明细行解析出的发票号已被其他单据关联即拦截
        Set<String> parsedNos = new HashSet<>();
        for (ExpBillLine line : lines) {
            String ext = line.getExtJson();
            if (ext == null || !ext.contains("invoiceNo")) continue;
            try {
                String no = new ObjectMapper().readTree(ext)
                        .path("invoice").path("invoiceNo").asText("");
                if (!no.isBlank()) parsedNos.add(no);
            } catch (Exception ignore) { /* 元信息异常不作为预检失败 */ }
        }
        for (String no : parsedNos) {
            List<InvInvoice> sameNo = invoiceFacade.findByInvoiceNo(no);
            if (sameNo.isEmpty()) continue;
            List<Long> ids = sameNo.stream().map(InvInvoice::getId).toList();
            long usedByOthers = invoiceFacade.countLinksByInvoiceIdsExcludingBill(ids, billId);
            if (usedByOthers > 0) {
                items.add(PrecheckItem.fail("INVOICE_DUP",
                        "发票 " + no + " 已被其他报销单使用，禁止重复报销"));
            }
        }

        // 3.3) OCR 票号查重（票号没勾进明细也拦）——
        //      本单附件影像经 OCR 识别出的票号，若已被其他单据的在用附件使用，即判重复发票
        if (isClaim) {
            try {
                Set<Long> myFileIds = attachMapper.selectList(new LambdaQueryWrapper<ExpBillAttach>()
                                .eq(ExpBillAttach::getBillId, billId))
                        .stream().map(ExpBillAttach::getFileId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> ocrNos = new HashSet<>(parsedNos);
                if (!myFileIds.isEmpty()) {
                    ocrNos.addAll(imageFacade.invoiceNosByFileIds(TenantContext.getTenantId(), new ArrayList<>(myFileIds)));
                }
                for (String no : ocrNos) {
                    List<Long> otherFileIds = imageFacade
                            .fileIdsByInvoiceNo(TenantContext.getTenantId(), no).stream()
                            .filter(f -> !myFileIds.contains(f)).distinct().toList();
                    if (otherFileIds.isEmpty()) continue;
                    long usedByOthers = attachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                            .in(ExpBillAttach::getFileId, otherFileIds));
                    if (usedByOthers > 0) {
                        items.add(PrecheckItem.fail("INVOICE_DUP",
                                "发票号码 " + no + " 的影像已被其他报销单使用，禁止重复报销"));
                    }
                }
            } catch (Exception ex) {
                // 跨库查询故障不作为预检失败（与票面键组装同一规则降级）
                log.warn("OCR 票号查重执行失败(跳过): {}", ex.getMessage());
            }
        }

        // 4) 政策金额上限 + 提交时限（按单据费用项匹配政策）
        if (!byItem.isEmpty()) {
            for (ExpensePolicy p : policies) {
                // 金额上限（该费用项明细金额合计）
                if (p.getMaxAmount() != null) {
                    BigDecimal itemTotal = byItem.get(p.getExpenseItemId()).stream()
                            .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (itemTotal.compareTo(p.getMaxAmount()) > 0) {
                        items.add(PrecheckItem.fail("POLICY_AMOUNT",
                                "超政策金额上限：[" + p.getPolicyName() + "] 上限 " + p.getMaxAmount()
                                        + "，该费用项合计 " + itemTotal));
                    }
                }
                // 提交时限：费用日期距今天数
                if (p.getSubmitDays() != null && bill.getApplyDate() != null) {
                    long days = ChronoUnit.DAYS.between(bill.getApplyDate(), LocalDate.now());
                    if (days > p.getSubmitDays()) {
                        items.add(PrecheckItem.warn("SUBMIT_DEADLINE",
                                "超提交时限：[" + p.getPolicyName() + "] 要求 " + p.getSubmitDays()
                                        + " 天内提交，已逾期 " + (days - p.getSubmitDays()) + " 天"));
                    }
                }
            }
        }

        // 5) 重复发票：关联发票已被其他单据使用
        List<InvBillInvoice> links = invoiceFacade.findLinksByBillId(billId);
        if (!links.isEmpty()) {
            List<Long> invIds = links.stream().map(InvBillInvoice::getInvoiceId).distinct().toList();
            List<InvInvoice> invs = invoiceFacade.findByIds(invIds);
            for (InvInvoice inv : invs) {
                if (inv.getInvoiceNo() == null) continue;
                long used = invoiceFacade.countByCodeAndNo(inv.getInvoiceCode(), inv.getInvoiceNo());
                if (used > 1) {
                    items.add(PrecheckItem.fail("INVOICE_DUP",
                            "发票重复报销：" + inv.getInvoiceCode() + " " + inv.getInvoiceNo()));
                }
            }
        }

        // 7) 借款逾期拦截（提交新 LOAN 时，检查申请人名下是否有逾期未还的 LOAN）
        if ("LOAN".equals(bill.getBillType()) && bill.getApplyUserId() != null
                && ExpenseBillService.LOAN_OVERDUE_BLOCK_NEW_LOAN) {
            LocalDate today = LocalDate.now();
            List<ExpBill> openLoans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                    .eq(ExpBill::getApplyUserId, bill.getApplyUserId())
                    .eq(ExpBill::getBillType, "LOAN")
                    .in(ExpBill::getStatus, "APPROVED", "APPROVING", "PARTIAL_REPAID")
                    .ne(bill.getId() != null, ExpBill::getId, bill.getId())); // 排除自己（草稿编辑重提交时）
            for (ExpBill loan : openLoans) {
                if (loan.getLoanBalance() == null || loan.getLoanBalance().signum() <= 0) continue;
                if (loan.getExpectRepayDate() != null && loan.getExpectRepayDate().isBefore(today)) {
                    long overdueDays = ChronoUnit.DAYS.between(loan.getExpectRepayDate(), today);
                    items.add(PrecheckItem.fail("LOAN_OVERDUE",
                            "名下有逾期未还借款（单号：" + (loan.getBillNo() == null ? loan.getId() : loan.getBillNo())
                                    + " 应还日 " + loan.getExpectRepayDate() + " 已逾期 " + overdueDays
                                    + " 天，余额 " + loan.getLoanBalance()
                                    + " 元）。借款结清前禁止再次申请（会计年度内累计 X 次逾期将升级管控）。"));
                    break; // 一条就足够拦截，避免重复堆积
                }
            }
        }

        // 6) 标准超标提示（WARN）：沿用提交时 overStd 计算逻辑的只读版
        for (ExpBillLine line : lines) {
            if (line.getAmount() != null && line.getStdAmount() != null
                    && line.getAmount().compareTo(line.getStdAmount()) > 0) {
                items.add(PrecheckItem.warn("STANDARD_OVER",
                        "超过报销标准：明细 " + (line.getItemName() == null ? line.getItemId() : line.getItemName())
                                + " 金额 " + line.getAmount() + " > 标准 " + line.getStdAmount()));
            }
        }

        // 8) 拆单检测（SPLIT_ORDER_DETECT，WARN 提示不拦截）：跨单据聚合超出预检表达式引擎的能力边界
        //    （引擎仅对注入 Map 中的标量变量求值，无窗口/聚合/他单数据访问），故落在 Java 侧审核链；
        //    md_compliance_rule 已登记同名规则保持规则清单可见性。
        if (isClaim) {
            PrecheckItem split = detectSplitOrder(bill, total);
            if (split != null) {
                items.add(split);
            }
        }

        return items;
    }

    /**
     * 拆单检测：同一申请人 + 同一收款方/商户，7 天窗口内（含本单）≥{@value #SPLIT_MIN_COUNT} 张
     * CLAIM 报销单，且单张金额均落在审批阈值下方 10% 区间 [阈值×0.9, 阈值)——疑似拆单规避高阶审批。
     * 仅统计已提交有效单（SUBMITTED/APPROVING/APPROVED），排除草稿/作废/驳回与本单；
     * 多租户拦截器自动限定当前租户。命中返回 REVIEW/WARN 风险项，未命中返回 null。
     */
    private PrecheckItem detectSplitOrder(ExpBill bill, BigDecimal total) {
        if (bill.getApplyUserId() == null || bill.getPayeeName() == null || bill.getPayeeName().isBlank()
                || total == null || total.signum() <= 0) {
            return null;
        }
        BigDecimal lower = SPLIT_APPROVAL_THRESHOLD.multiply(BigDecimal.ONE.subtract(SPLIT_BAND_RATIO));
        // 本单金额须先落进临界区间，才有拆单嫌疑（大额正常单不触发额外查询）
        if (total.compareTo(lower) < 0 || total.compareTo(SPLIT_APPROVAL_THRESHOLD) >= 0) {
            return null;
        }
        List<ExpBill> recent = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getApplyUserId, bill.getApplyUserId())
                .eq(ExpBill::getPayeeName, bill.getPayeeName())
                .eq(ExpBill::getBillType, "CLAIM")
                .in(ExpBill::getStatus, "SUBMITTED", "APPROVING", "APPROVED")
                .ge(ExpBill::getCreateTime, LocalDateTime.now().minusDays(SPLIT_WINDOW_DAYS))
                .ne(bill.getId() != null, ExpBill::getId, bill.getId()));
        long inBand = recent.stream()
                .map(b -> b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount())
                .filter(a -> a.compareTo(lower) >= 0 && a.compareTo(SPLIT_APPROVAL_THRESHOLD) < 0)
                .count();
        if (inBand + 1 >= SPLIT_MIN_COUNT) {
            log.info("[拆单检测] billNo={} 申请人={} 收款方={} 窗口{}天内临界区间共{}单（含本单）",
                    bill.getBillNo(), bill.getApplyUserId(), bill.getPayeeName(),
                    SPLIT_WINDOW_DAYS, inBand + 1);
            return PrecheckItem.warn("SPLIT_ORDER_DETECT",
                    "疑似拆单报销：申请人向同一收款方[" + bill.getPayeeName() + "]近 " + SPLIT_WINDOW_DAYS
                            + " 天内已有 " + inBand + " 单，连同本单（" + total + " 元）共 " + (inBand + 1)
                            + " 单金额均落在审批阈值区间 " + lower + " ~ " + SPLIT_APPROVAL_THRESHOLD
                            + " 元下方临界带，转人工复核是否存在拆单规避审批");
        }
        return null;
    }

    /** 仅返回 FAIL 项（供提交拦截） */
    public List<PrecheckItem> failures(Long billId) {
        return precheck(billId).stream().filter(i -> "FAIL".equals(i.getLevel())).toList();
    }

    /** 费用政策 CRUD */
    public PageResult<ExpensePolicy> page(long pageNum, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<ExpensePolicy> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ExpensePolicy::getPolicyName, keyword).or().like(ExpensePolicy::getPolicyCode, keyword));
        }
        if (status != null && !status.isBlank()) qw.eq(ExpensePolicy::getStatus, status);
        qw.orderByDesc(ExpensePolicy::getId);
        Page<ExpensePolicy> p = policyMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(ExpensePolicy p) {
        p.setId(null);
        p.setTenantId(TenantContext.getTenantId());
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getNeedInvoice() == null) p.setNeedInvoice(0);
        p.setDelFlag(0);
        policyMapper.insert(p);
        return p.getId();
    }

    public void update(ExpensePolicy p) {
        policyMapper.updateById(p);
    }

    @Data
    public static class PrecheckItem {
        private String code;
        private String level; // FAIL 拦截 / WARN 提示
        private String msg;

        public static PrecheckItem fail(String code, String msg) {
            PrecheckItem i = new PrecheckItem();
            i.setCode(code);
            i.setLevel("FAIL");
            i.setMsg(msg);
            return i;
        }

        public static PrecheckItem warn(String code, String msg) {
            PrecheckItem i = new PrecheckItem();
            i.setCode(code);
            i.setLevel("WARN");
            i.setMsg(msg);
            return i;
        }
    }
}
