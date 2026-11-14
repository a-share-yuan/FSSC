package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能填单助手：OCR 识别结果 → 自动填充报销单草稿 + 智能分类 + 关联建议。
 * 依赖已存在的发票（inv_invoice）和费用项（md_expense_item）数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartFillService {

    private final InvoiceDomainFacade invoiceFacade;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;

    /**
     * 根据发票 ID 生成智能填单建议。
     *
     * @param invoiceId 发票ID
     * @return 填单建议
     */
    public SmartFillResult smartFill(Long invoiceId) {
        InvInvoice inv = invoiceFacade.findById(invoiceId);
        if (inv == null) {
            return new SmartFillResult(false, "发票不存在: " + invoiceId, null, null, null, null);
        }

        // 1. 智能分类费用项
        MdExpenseItem suggestedItem = classifyExpenseItem(inv);

        // 2. 建议金额
        SmartFillAmount amount = new SmartFillAmount(inv.getAmount(), inv.getNetAmount(), inv.getTaxAmount());

        // 3. 构建建议的报销单行
        SmartFillLine line = new SmartFillLine();
        line.setItemId(suggestedItem != null ? suggestedItem.getId() : null);
        line.setItemName(suggestedItem != null ? suggestedItem.getItemName() : "其他费用");
        line.setAmount(inv.getAmount());
        line.setNetAmount(inv.getNetAmount());
        line.setTaxAmount(inv.getTaxAmount());
        line.setInvoiceId(inv.getId());
        line.setInvoiceNo(inv.getInvoiceNo());
        line.setInvoiceCode(inv.getInvoiceCode());
        line.setSellerName(inv.getSellerName());
        line.setInvoiceDate(inv.getInvoiceDate());
        line.setRemark("OCR自动识别: " + inv.getSellerName() + " " + inv.getInvoiceNo());

        // 4. 建议关联的事前申请
        List<SmartFillReference> preApplications = new ArrayList<>();

        // 5. 建议关联的项目（按供应商名称匹配）
        List<SmartFillReference> suggestedProjects = new ArrayList<>();

        String summary = String.format("OCR识别: %s %s %.2f元",
                inv.getInvoiceType() != null ? inv.getInvoiceType() : "发票",
                inv.getSellerName() != null ? inv.getSellerName() : "",
                inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO);

        return new SmartFillResult(true, null, summary, suggestedItem != null ? suggestedItem.getItemName() : "其他费用",
                Collections.singletonList(line), amount);
    }

    /**
     * 批量智能填单：根据多张发票生成建议。
     *
     * @param invoiceIds 发票ID列表
     * @return 合并后的填单建议
     */
    public SmartFillResult batchSmartFill(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return new SmartFillResult(false, "发票列表为空", null, null, null, null);
        }

        List<InvInvoice> invoices = invoiceFacade.findByIds(invoiceIds);
        if (invoices.isEmpty()) {
            return new SmartFillResult(false, "未找到有效发票", null, null, null, null);
        }

        List<SmartFillLine> lines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String firstSeller = null;

        for (InvInvoice inv : invoices) {
            MdExpenseItem item = classifyExpenseItem(inv);
            SmartFillLine line = new SmartFillLine();
            line.setItemId(item != null ? item.getId() : null);
            line.setItemName(item != null ? item.getItemName() : "其他费用");
            line.setAmount(inv.getAmount());
            line.setNetAmount(inv.getNetAmount());
            line.setTaxAmount(inv.getTaxAmount());
            line.setInvoiceId(inv.getId());
            line.setInvoiceNo(inv.getInvoiceNo());
            line.setInvoiceCode(inv.getInvoiceCode());
            line.setSellerName(inv.getSellerName());
            line.setInvoiceDate(inv.getInvoiceDate());
            line.setRemark("OCR识别: " + inv.getSellerName() + " " + inv.getInvoiceNo());
            lines.add(line);

            if (inv.getAmount() != null) totalAmount = totalAmount.add(inv.getAmount());
            if (firstSeller == null) firstSeller = inv.getSellerName();
        }

        String summary = String.format("OCR批量识别: %d张发票, 合计%.2f元", invoices.size(), totalAmount);
        SmartFillAmount amount = new SmartFillAmount(totalAmount, null, null);

        return new SmartFillResult(true, null, summary, "多笔费用", lines, amount);
    }

    /**
     * 智能分类费用项：根据发票类型和供应商名称匹配费用项。
     * <p>
     * 规则：增值税专票/火车票→差旅费；普通发票→办公费/服务费；其他→其他费用。
     */
    private MdExpenseItem classifyExpenseItem(InvInvoice inv) {
        if (inv == null) return null;

        String keyword = null;
        if ("VAT".equals(inv.getInvoiceType()) || "VATN".equals(inv.getInvoiceType())) {
            // 增值税发票：根据供应商名称判断
            if (inv.getSellerName() != null) {
                String name = inv.getSellerName().toLowerCase();
                if (name.contains("酒店") || name.contains("住宿") || name.contains("宾馆")) {
                    keyword = "住宿";
                } else if (name.contains("餐饮") || name.contains("饭店") || name.contains("餐厅")) {
                    keyword = "餐饮";
                } else if (name.contains("交通") || name.contains("航空") || name.contains("铁路") || name.contains("票务")) {
                    keyword = "交通";
                } else if (name.contains("办公") || name.contains("文具") || name.contains("电脑")) {
                    keyword = "办公";
                } else if (name.contains("咨询") || name.contains("顾问") || name.contains("服务")) {
                    keyword = "服务";
                }
            }
        } else if ("TRAIN".equals(inv.getInvoiceType())) {
            keyword = "交通";
        } else if (inv.getSellerName() != null) {
            String name = inv.getSellerName().toLowerCase();
            if (name.contains("加油") || name.contains("石油") || name.contains("石化")) {
                keyword = "交通";
            }
        }

        if (keyword != null) {
            List<MdExpenseItem> items = mdExpenseItemMapper.selectList(
                    new LambdaQueryWrapper<MdExpenseItem>()
                            .eq(MdExpenseItem::getStatus, "NORMAL")
                            .like(MdExpenseItem::getItemName, keyword)
                            .last("LIMIT 1"));
            if (!items.isEmpty()) {
                return items.get(0);
            }
        }

        // 兜底：取第一个可用的费用项
        List<MdExpenseItem> defaultItems = mdExpenseItemMapper.selectList(
                new LambdaQueryWrapper<MdExpenseItem>()
                        .eq(MdExpenseItem::getStatus, "NORMAL")
                        .eq(MdExpenseItem::getIsLeaf, 1)
                        .last("LIMIT 1"));
        return defaultItems.isEmpty() ? null : defaultItems.get(0);
    }

    /**
     * 获取员工最近的事前申请，用于关联建议。
     *
     * @param applyUserId 申请人ID
     * @return 事前申请列表
     */
    public List<ExpBill> listPreApplications(Long applyUserId) {
        if (applyUserId == null) return Collections.emptyList();
        return expBillMapper.selectList(
                new LambdaQueryWrapper<ExpBill>()
                        .eq(ExpBill::getBillType, "APPLY")
                        .eq(ExpBill::getApplyUserId, applyUserId)
                        .in(ExpBill::getStatus, "APPROVED", "APPROVING")
                        .orderByDesc(ExpBill::getId)
                        .last("LIMIT 10"));
    }

    // 内部类

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SmartFillResult {
        private boolean success;
        private String error;
        private String summary;          // 摘要
        private String expenseType;      // 建议费用类型
        private List<SmartFillLine> lines; // 费用明细行
        private SmartFillAmount amount;  // 金额信息
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SmartFillLine {
        private Long itemId;
        private String itemName;
        private BigDecimal amount;
        private BigDecimal netAmount;
        private BigDecimal taxAmount;
        private Long invoiceId;
        private String invoiceNo;
        private String invoiceCode;
        private String sellerName;
        private LocalDate invoiceDate;
        private String remark;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SmartFillAmount {
        private BigDecimal totalAmount;
        private BigDecimal totalNetAmount;
        private BigDecimal totalTaxAmount;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SmartFillReference {
        private Long id;
        private String no;
        private String name;
        private BigDecimal amount;
    }
}