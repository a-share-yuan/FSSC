package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArInvoiceRule;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArInvoiceRuleMapper;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ConnectorFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预制发票服务（蓝图开票链路）：对账单完成→按客户规则拆分合并（附件一）→商务确认（参数开关，
 * 任务3.3）→推送金税（GOLDEN_TAX 接口配置，未配置走模拟，任务3.1）→回写→打印→邮寄；
 * 红字/换票/作废重开/提前开票核对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArPreInvoiceService {

    /** 商务确认参数（任务3.3：蓝图标注功能保留暂不开放，参数控制是否启用） */
    public static final String CFG_BIZ_CONFIRM = "ar.bizconfirm.enabled";

    private static final BigDecimal DEFAULT_LIMIT = new BigDecimal("1000000");
    private static final String GOLDEN_TAX_CATEGORY = "GOLDEN_TAX";

    private final ArPreInvoiceMapper mapper;
    private final ArStatementMapper statementMapper;
    private final ArInvoiceRuleMapper ruleMapper;
    private final ConnectorFacade connector;
    private final com.fssc.system.service.ConfigService configService;

    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    /** 换票重开时作废行仍占用预制发票号：从该对账单已用最大序号续编，避免撞 uk_pre_no。 */
    private int nextSeq(String reconBillNo) {
        List<ArPreInvoice> existing = mapper.selectList(new LambdaQueryWrapper<ArPreInvoice>()
                .select(ArPreInvoice::getPreInvoiceNo)
                .eq(ArPreInvoice::getReconBillNo, reconBillNo));
        int max = 0;
        for (ArPreInvoice p : existing) {
            String no = p.getPreInvoiceNo();
            if (no == null || no.length() <= reconBillNo.length()) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(no.substring(reconBillNo.length())));
            } catch (NumberFormatException ignored) {
                // 历史非本规则编号：不参与续号
            }
        }
        return max + 1;
    }

    /** 拆分合并并生成预制发票。 */
    @Transactional(rollbackFor = Exception.class)
    public List<ArPreInvoice> generate(String reconBillNo) {
        ArStatement bill = statementMapper.selectOne(new LambdaQueryWrapper<ArStatement>()
                .eq(ArStatement::getBillNo, reconBillNo).last("LIMIT 1"));
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单不存在：" + reconBillNo);
        }
        if (!ArStatement.ST_RECONCILED.equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单 " + reconBillNo + " 未对账完成，不可开票");
        }
        Long exists = mapper.selectCount(new LambdaQueryWrapper<ArPreInvoice>()
                .eq(ArPreInvoice::getReconBillNo, reconBillNo)
                .notIn(ArPreInvoice::getStatus, ArPreInvoice.ST_VOIDED));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单 " + reconBillNo + " 已存在有效预制发票（作废后可重新生成）");
        }
        ArInvoiceRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<ArInvoiceRule>()
                .eq(ArInvoiceRule::getCustomerCode, bill.getCustomerCode())
                .eq(ArInvoiceRule::getStatus, ArInvoiceRule.ST_ENABLED)
                .last("LIMIT 1"));
        BigDecimal limit = rule == null || rule.getLimitAmount() == null ? DEFAULT_LIMIT : rule.getLimitAmount();
        Integer maxLines = rule == null ? null : rule.getMaxLines();
        String splitDim = rule == null ? "PO" : rule.getSplitDim();

        List<Map<String, Object>> lines = parseLines(bill.getLinesJson());
        BigDecimal billTotal = bill.getRmbAmount() == null ? bill.getAmount() : bill.getRmbAmount();
        BigDecimal billTax = bill.getRmbTaxAmount() == null ? bill.getTaxAmount() : bill.getRmbTaxAmount();
        if (lines.isEmpty()) {
            lines = new ArrayList<>();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", "汇总");
            summary.put("qty", 1);
            summary.put("amount", billTotal);
            summary.put("taxAmount", billTax);
            summary.put("totalAmount", billTotal.add(billTax));
            lines.add(summary);
        }
        List<ArPreInvoiceSplitter.Group> groups = ArPreInvoiceSplitter.split(lines, limit, maxLines);
        BigDecimal groupTotalSum = groups.stream().map(ArPreInvoiceSplitter.Group::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean bizConfirmOn = rule != null && rule.getNeedBizConfirm() != null && rule.getNeedBizConfirm() == 1
                && "1".equals(configService.getValue(CFG_BIZ_CONFIRM));

        List<ArPreInvoice> result = new ArrayList<>();
        int seq = nextSeq(reconBillNo);
        for (ArPreInvoiceSplitter.Group g : groups) {
            ArPreInvoice inv = new ArPreInvoice();
            inv.setPreInvoiceNo(ArPreInvoiceSplitter.preInvoiceNo(reconBillNo, seq++));
            inv.setCompanyId(bill.getCompanyId());
            inv.setReconBillNo(reconBillNo);
            inv.setCustomerCode(bill.getCustomerCode());
            inv.setCustomerName(bill.getCustomerName());
            inv.setInvoiceType(rule == null || rule.getInvoiceType() == null ? "专票" : rule.getInvoiceType());
            inv.setCcy("CNY");
            // 尾差并单（附件一：修改金额尾差功能）——最后一组吸收整单差额
            BigDecimal amount = g.amount();
            BigDecimal tax = g.taxAmount();
            BigDecimal total = g.totalAmount();
            boolean last = g == groups.get(groups.size() - 1);
            if (last && groupTotalSum.compareTo(billTotal.add(billTax)) != 0) {
                total = billTotal.add(billTax);
                amount = billTotal;
                tax = total.subtract(amount);
                inv.setRemark("已调整金额尾差 " + groupTotalSum.subtract(total).abs());
            }
            inv.setAmount(amount);
            inv.setTaxAmount(tax);
            inv.setTotalAmount(total);
            inv.setLineCount(g.lineIndexes().size());
            inv.setLinesJson(toJson(subLines(lines, g.lineIndexes())));
            inv.setSplitDim(splitDim);
            inv.setStatus(bizConfirmOn ? ArPreInvoice.ST_BIZ_CONFIRMING : ArPreInvoice.ST_TO_INVOICE);
            inv.setPreCheckFlag(bill.getPreInvoiceFlag() != null && bill.getPreInvoiceFlag() == 1 ? "PENDING" : "NONE");
            mapper.insert(inv);
            result.add(inv);
        }
        log.info("[arflow] 对账单 {} 拆分生成 {} 张预制发票（限额 {} 行数上限 {} 商务确认 {}）",
                reconBillNo, result.size(), limit, maxLines, bizConfirmOn ? "启用" : "未启用");
        return result;
    }

    /** 商务确认通过（任务3.3）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice bizConfirm(Long id, String confirmer) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_BIZ_CONFIRMING, "商务确认");
        String auditOldStatus = inv.getStatus();
        String auditOldRemark = inv.getRemark();
        inv.setStatus(ArPreInvoice.ST_TO_INVOICE);
        inv.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ")
                + "商务确认通过[" + (confirmer == null ? "-" : confirmer) + "]");
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("remark", inv.getRemark());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "商务确认通过");
        return inv;
    }

    /** 商务确认驳回。 */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice bizReject(Long id, String reason) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_BIZ_CONFIRMING, "商务确认驳回");
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商务确认驳回必须填写原因");
        }
        String auditOldStatus = inv.getStatus();
        String auditOldRemark = inv.getRemark();
        inv.setStatus(ArPreInvoice.ST_VOIDED);
        inv.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ") + "商务确认驳回:" + reason);
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("remark", inv.getRemark());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "商务确认驳回");
        return inv;
    }

    /**
     * 推送金税开票：GOLDEN_TAX 接口配置存在走真实通道（IntegrationGateway），
     * 未配置走模拟（任务3.1：接口配置功能，未配置自动回退）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice pushTax(Long id) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_TO_INVOICE, "推送金税");
        ExtInterfaceConfig cfg = connector.getConfig(GOLDEN_TAX_CATEGORY);
        if (cfg != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("preInvoiceNo", inv.getPreInvoiceNo());
            payload.put("invoiceType", inv.getInvoiceType());
            payload.put("buyerName", inv.getCustomerName());
            payload.put("amount", inv.getAmount());
            payload.put("taxAmount", inv.getTaxAmount());
            payload.put("totalAmount", inv.getTotalAmount());
            // P0-2 幂等键：同一预制发票号重复推送（如超时重发）不重复开票，回放首次结果
            ChannelResponse resp = connector.invokeWrite(GOLDEN_TAX_CATEGORY, payload,
                    "GOLDEN_TAX:" + com.fssc.framework.tenant.TenantContext.getTenantId() + ":" + inv.getPreInvoiceNo());
            if (resp == null || !resp.isOk()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "金税开票推送失败: "
                        + (resp == null ? "无响应" : resp.getBody()));
            }
            log.info("[arflow] 预制发票 {} 金税真实通道推送完成", inv.getPreInvoiceNo());
        } else {
            log.info("[arflow] 预制发票 {} 金税未配置，模拟推送", inv.getPreInvoiceNo());
        }
        String auditOldStatus = inv.getStatus();
        String auditOldRemark = inv.getRemark();
        inv.setStatus(ArPreInvoice.ST_PUSHED);
        inv.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ")
                + (cfg == null ? "金税推送[模拟]" : "金税推送[真实]"));
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("remark", inv.getRemark());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "推送金税");
        return inv;
    }

    /** 金税回写（发票号/代码/日期回写并同步EBS）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice writeback(Long id, String invoiceNo, String invoiceCode, LocalDate invoiceDate) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_PUSHED, "金税回写");
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回写发票号码必填");
        }
        String auditOldStatus = inv.getStatus();
        String auditOldInvoiceNo = inv.getInvoiceNo();
        String auditOldInvoiceCode = inv.getInvoiceCode();
        inv.setInvoiceNo(invoiceNo);
        inv.setInvoiceCode(invoiceCode);
        inv.setInvoiceDate(invoiceDate == null ? LocalDate.now() : invoiceDate);
        inv.setStatus(ArPreInvoice.ST_INVOICED);
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("invoiceNo", auditOldInvoiceNo);
        auditBefore.put("invoiceCode", auditOldInvoiceCode);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("invoiceNo", inv.getInvoiceNo());
        auditAfter.put("invoiceCode", inv.getInvoiceCode());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "金税回写开票信息");
        return inv;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice print(Long id) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_INVOICED, "打印");
        inv.setPrintedFlag(1);
        mapper.updateById(inv);
        return inv;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice voidInvoice(Long id, String reason) {
        ArPreInvoice inv = require(id);
        if (List.of(ArPreInvoice.ST_VOIDED, ArPreInvoice.ST_RED_INVOICED).contains(inv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票 " + inv.getPreInvoiceNo() + " 已是终态");
        }
        String auditOldStatus = inv.getStatus();
        String auditOldRemark = inv.getRemark();
        inv.setStatus(ArPreInvoice.ST_VOIDED);
        inv.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ") + "作废:" + reason);
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("remark", inv.getRemark());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "预制发票作废");
        return inv;
    }

    /** 红字发票（蓝图 SCC_AR_05）：需红字通知单号。 */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice red(Long id, String redNoticeNo) {
        ArPreInvoice inv = require(id);
        requireStatus(inv, ArPreInvoice.ST_INVOICED, "红字冲销");
        if (redNoticeNo == null || redNoticeNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "红字开票必须填写红字通知单号");
        }
        String auditOldStatus = inv.getStatus();
        String auditOldRedNoticeNo = inv.getRedNoticeNo();
        inv.setRedNoticeNo(redNoticeNo);
        inv.setStatus(ArPreInvoice.ST_RED_INVOICED);
        mapper.updateById(inv);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("redNoticeNo", auditOldRedNoticeNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", inv.getStatus());
        auditAfter.put("redNoticeNo", inv.getRedNoticeNo());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", inv.getId(), inv.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(inv.getId()), auditBefore, auditAfter, "红字发票冲销");
        return inv;
    }

    /** 换票（蓝图 SCC_AR_06）：作废原票并按原对账单重新生成。 */
    @Transactional(rollbackFor = Exception.class)
    public List<ArPreInvoice> exchange(Long id, String reason) {
        // 换票要说明原因，原票作废记录与重开新票都要能对上（税务稽核看这条留痕）
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "换票原因必填");
        }
        ArPreInvoice origin = require(id);
        if (!List.of(ArPreInvoice.ST_INVOICED, ArPreInvoice.ST_TO_INVOICE, ArPreInvoice.ST_PUSHED)
                .contains(origin.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票 " + origin.getPreInvoiceNo() + " 当前状态不可换票");
        }
        String auditOldStatus = origin.getStatus();
        String auditOldRemark = origin.getRemark();
        origin.setStatus(ArPreInvoice.ST_VOIDED);
        origin.setRemark((origin.getRemark() == null ? "" : origin.getRemark() + " | ") + "换票作废:" + reason);
        mapper.updateById(origin);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", origin.getStatus());
        auditAfter.put("remark", origin.getRemark());
        auditTrailService.recordUpdate("AR_PRE_INVOICE", origin.getId(), origin.getPreInvoiceNo(), "ar_pre_invoice",
                String.valueOf(origin.getId()), auditBefore, auditAfter, "换票作废原票");
        List<ArPreInvoice> fresh = generate(origin.getReconBillNo());
        for (ArPreInvoice inv : fresh) {
            inv.setOriginNo(origin.getPreInvoiceNo());
            inv.setRemark("换票重开（原票 " + origin.getPreInvoiceNo() + "）");
            mapper.updateById(inv);
        }
        return fresh;
    }

    /** 提前开票数据核对（蓝图 SCC_AR_08）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice preCheck(Long id, boolean matched, String note) {
        ArPreInvoice inv = require(id);
        if (!"PENDING".equals(inv.getPreCheckFlag())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票 " + inv.getPreInvoiceNo() + " 非待核对状态");
        }
        inv.setPreCheckFlag("DONE");
        inv.setPreCheckNote((matched ? "核对一致" : "核对差异已调整") + "：" + note);
        mapper.updateById(inv);
        return inv;
    }

    public List<ArPreInvoice> list(String status, String customerName) {
        LambdaQueryWrapper<ArPreInvoice> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ArPreInvoice::getStatus, status);
        }
        if (customerName != null && !customerName.isBlank()) {
            q.like(ArPreInvoice::getCustomerName, customerName);
        }
        return mapper.selectList(q.orderByDesc(ArPreInvoice::getId));
    }

    public ArPreInvoice get(Long id) {
        return require(id);
    }

    // ---------------- 工具 ----------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseLines(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                return (List<Map<String, Object>>) (List<?>) list;
            }
        } catch (Exception ignore) {
            // 明细解析失败按空处理，走汇总行
        }
        return List.of();
    }

    private List<Map<String, Object>> subLines(List<Map<String, Object>> lines, List<Integer> indexes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer i : indexes) {
            out.add(lines.get(i));
        }
        return out;
    }

    static String toJson(Object o) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON序列化失败:" + e.getMessage());
        }
    }

    private ArPreInvoice require(Long id) {
        ArPreInvoice inv = mapper.selectById(id);
        if (inv == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票不存在 id=" + id);
        }
        return inv;
    }

    private void requireStatus(ArPreInvoice inv, String expected, String action) {
        if (!expected.equals(inv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票 " + inv.getPreInvoiceNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + inv.getStatus());
        }
    }
}
