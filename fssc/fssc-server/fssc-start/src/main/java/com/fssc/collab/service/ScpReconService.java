package com.fssc.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.entity.ScpBill;
import com.fssc.collab.entity.ScpRecon;
import com.fssc.collab.mapper.ScpBillMapper;
import com.fssc.collab.mapper.ScpReconMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * SCP 供应商对账管理（AP 蓝图 SCC_AP_01/02，外网对账单管理，页面 9.1 序号 1~3，E1~E6）：
 * 收货信息导入（E1，EBS→协同平台 mock）→ 创建预制对账单（E4）→ 异议反馈/处理（E5）→
 * 确认对账单（E6，R-30 增专不调整/增普重算税额）→ 申请开票（打通点2 → ScpFlowService 落地 ScpBill）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScpReconService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScpReconMapper mapper;
    private final ScpBillMapper scpBillMapper;
    private final ScpFlowService scpFlowService;
    private final ArPreInvoiceMapper preInvoiceMapper;
    private final CollabSyncService syncService;

    /** E1 收货/折扣/索赔信息导入 + E4 创建预制对账单：lines=[{desc,qty,price,amount,taxAmount,poNo}]。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpRecon importGoods(ScpRecon r) {
        if (!StringUtils.hasText(r.getSupplierCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商代码必填");
        }
        if (r.getLinesJson() == null || r.getLinesJson().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收货/折扣/索赔明细行不能为空（EBS收货信息导入）");
        }
        computeTotals(r, null);
        r.setId(null);
        r.setTenantId(TenantContext.getTenantId());
        if (!StringUtils.hasText(r.getReconNo())) {
            r.setReconNo("RB" + LocalDateTime.now().format(TS) + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        }
        if (!StringUtils.hasText(r.getCcy())) {
            r.setCcy("CNY");
        }
        r.setObjectionStatus(ScpRecon.OBJ_NONE);
        r.setStatus(ScpRecon.ST_PRELIM);
        mapper.insert(r);
        return r;
    }

    /** E5 异议反馈（供应商）：修改后销方物料描述 + 申请原因。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpRecon objection(Long id, String note) {
        ScpRecon r = require(id);
        if (!ScpRecon.ST_PRELIM.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅预制待确认对账单可提异议反馈");
        }
        if (!StringUtils.hasText(note)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "异议原因必填");
        }
        r.setObjectionNote(note);
        r.setObjectionStatus(ScpRecon.OBJ_SUBMITTED);
        mapper.updateById(r);
        return r;
    }

    /** E5 异议处理（内网对账员）：判责后关闭异议，可暂挂（备注留痕）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpRecon resolveObjection(Long id, String note) {
        ScpRecon r = require(id);
        if (!ScpRecon.OBJ_SUBMITTED.equals(r.getObjectionStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该对账单无待处理异议");
        }
        r.setObjectionNote((r.getObjectionNote() == null ? "" : r.getObjectionNote() + " | 处理：" + (note == null ? "" : note)));
        r.setObjectionStatus(ScpRecon.OBJ_RESOLVED);
        mapper.updateById(r);
        return r;
    }

    /**
     * E6 确认对账单：选增专/增普（R-30）——增专数据不调整；增普按税率重算金额/税额/价税合计。
     * 确认完成即【打通点1】AP 三单匹配数据源就绪。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpRecon confirm(Long id, String invoiceType, BigDecimal taxRatePct) {
        ScpRecon r = require(id);
        if (!ScpRecon.ST_PRELIM.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单 " + r.getReconNo() + " 当前状态不可确认");
        }
        // R5-D-101：入参兼容历史中文值与字典码，统一归一化为字典码入库（VAT_SPECIAL/VAT_NORMAL）
        invoiceType = "增专".equals(invoiceType) ? "VAT_SPECIAL" : ("增普".equals(invoiceType) ? "VAT_NORMAL" : invoiceType);
        if (!"VAT_SPECIAL".equals(invoiceType) && !"VAT_NORMAL".equals(invoiceType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票类型必须为增专或增普（R-30）");
        }
        r.setInvoiceType(invoiceType);
        if ("VAT_NORMAL".equals(invoiceType) && taxRatePct != null && taxRatePct.signum() >= 0) {
            // R-30：增普按税率重算金额/税额/价税合计（不含税金额不变）
            BigDecimal tax = nvl(r.getAmount()).multiply(taxRatePct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            r.setTaxAmount(tax);
            r.setTotalAmount(nvl(r.getAmount()).add(tax));
        }
        r.setStatus(ScpRecon.ST_CONFIRMED);
        mapper.updateById(r);
        syncService.log(CollabSyncLog.IF_AR_RECON_SYNC, "SCP_TO_AP", r.getReconNo(),
                CollabSyncLog.ST_SUCCESS, "【打通点1】对账单确认完成，三单匹配数据源就绪（" + invoiceType + "）", null);
        return r;
    }

    /** 作废（预制/已确认且未开票）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpRecon voidRecon(Long id, String reason) {
        ScpRecon r = require(id);
        if (ScpRecon.ST_VOIDED.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单已作废");
        }
        Long used = scpBillMapper.selectCount(new LambdaQueryWrapper<ScpBill>()
                .eq(ScpBill::getReconBillNo, r.getReconNo()));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单已申请开票，不可作废");
        }
        r.setStatus(ScpRecon.ST_VOIDED);
        r.setRemark(append(r.getRemark(), "作废原因：" + (reason == null ? "" : reason)));
        mapper.updateById(r);
        return r;
    }

    /** 申请开票（E7）：由已确认对账单生成 ScpBill（打通点2，R-31 预制发票号=对账单号+三位流水）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill applyInvoice(Long id, String poNo) {
        ScpRecon r = require(id);
        if (!ScpRecon.ST_CONFIRMED.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已确认对账单可申请开票");
        }
        ScpBill b = new ScpBill();
        b.setReconBillNo(r.getReconNo());
        b.setSupplierCode(r.getSupplierCode());
        b.setSupplierName(r.getSupplierName());
        b.setBuyerCompanyCode(r.getBuyerCompanyCode());
        b.setInvoiceType(r.getInvoiceType());
        b.setCcy(r.getCcy());
        b.setAmount(r.getAmount());
        b.setTaxAmount(r.getTaxAmount());
        b.setTotalAmount(r.getTotalAmount());
        b.setReconAmount(r.getAmount());
        b.setReconTaxAmount(r.getTaxAmount());
        b.setReconTotal(r.getTotalAmount());
        b.setBuyerTaxNo(null);
        b.setSellerTaxNo(null);
        b.setPoNo(poNo);
        b.setRemark("由对账单 " + r.getReconNo() + " 申请开票（打通点2）");
        ScpBill created = scpFlowService.create(b);
        syncService.log(CollabSyncLog.IF_SCP_PAY, "SCP_TO_AP", created.getPreInvoiceNo(),
                CollabSyncLog.ST_MOCK, "【打通点2】预制发票申请落地：对账单 " + r.getReconNo(), null);
        return created;
    }

    public List<ScpRecon> list(String status, String keyword) {
        LambdaQueryWrapper<ScpRecon> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ScpRecon::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ScpRecon::getReconNo, keyword)
                    .or().like(ScpRecon::getSupplierName, keyword));
        }
        return mapper.selectList(q.orderByDesc(ScpRecon::getId));
    }

    // ---------------- 内部 ----------------

    private void computeTotals(ScpRecon r, BigDecimal taxRatePct) {
        // 明细金额已在 linesJson；表头金额以导入传入为准，缺省按明细聚合由前端/调用方保证
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账金额（不含税合计）必须大于0");
        }
        if (r.getTaxAmount() == null) {
            r.setTaxAmount(BigDecimal.ZERO);
        }
        if (r.getTotalAmount() == null) {
            r.setTotalAmount(r.getAmount().add(r.getTaxAmount()));
        }
    }

    /**
     * 核销回写发票状态（AR 蓝图状态机4.1 已开票→已核销）：应收单号 AR-INV-{发票号} 全额核销后，
     * 将同发票号的预制发票 INVOICED → WRITTEN_OFF。供 ArWriteoffService 调用。
     */
    public void markInvoiceWrittenOff(String receivableBillNo) {
        if (!StringUtils.hasText(receivableBillNo) || !receivableBillNo.startsWith("AR-INV-")) {
            return;
        }
        String invoiceNo = receivableBillNo.substring("AR-INV-".length());
        ArPreInvoice inv = preInvoiceMapper.selectOne(new LambdaQueryWrapper<ArPreInvoice>()
                .eq(ArPreInvoice::getInvoiceNo, invoiceNo)
                .eq(ArPreInvoice::getStatus, ArPreInvoice.ST_INVOICED)
                .last("LIMIT 1"));
        if (inv == null) {
            return;
        }
        ArPreInvoice u = new ArPreInvoice();
        u.setId(inv.getId());
        u.setStatus("WRITTEN_OFF");
        preInvoiceMapper.updateById(u);
        log.info("[collab] 预制发票 {} 已全额核销，状态回写 WRITTEN_OFF", invoiceNo);
    }

    private ScpRecon require(Long id) {
        ScpRecon r = mapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单不存在 id=" + id);
        }
        return r;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String append(String origin, String add) {
        String base = origin == null ? "" : origin;
        return base.isBlank() ? add : base + "；" + add;
    }
}
