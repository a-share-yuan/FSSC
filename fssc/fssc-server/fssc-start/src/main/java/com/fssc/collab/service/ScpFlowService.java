// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.apflow.service.ApMonthCloseService;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.entity.ScpBill;
import com.fssc.collab.mapper.ScpBillMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ConnectorFacade;
import com.fssc.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SCP 供应商协同发票全流程服务（AP 蓝图 SCC_AP_01~10、场景一 AP_S1）：
 * 登记（打通点1/2 数据落地）→ 自动匹配（R-16 容差比对）→ 创建邮包寄送 → 内网签收 →
 * 扫描+票核（R-20 六要素自动校验）→ 记账（打通点3，受 R-14 关账冻结拦截）→
 * 集中认证（打通点6）→ 付款回写（打通点5）。EBS 通道未配置时按 MOCK 落日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScpFlowService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScpBillMapper mapper;
    private final ConfigService configService;
    private final ApMonthCloseService monthCloseService;
    private final CollabSyncService syncService;
    private final ConnectorFacade connector;

    /**
     * 供应商确认对账单+申请开票后的数据落地（打通点1/2）：
     * 预制发票号 = 对账单号 + 三位流水（R-31）；初始状态待匹配。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill create(ScpBill b) {
        if (!StringUtils.hasText(b.getReconBillNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单号必填（三单匹配最小单位）");
        }
        if (b.getAmount() == null || b.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票金额必须大于0");
        }
        if (b.getTaxAmount() == null) {
            b.setTaxAmount(BigDecimal.ZERO);
        }
        if (b.getTotalAmount() == null) {
            b.setTotalAmount(b.getAmount().add(b.getTaxAmount()));
        }
        b.setId(null);
        b.setTenantId(TenantContext.getTenantId());
        int seq = mapper.selectCount(new LambdaQueryWrapper<ScpBill>()
                .eq(ScpBill::getReconBillNo, b.getReconBillNo())).intValue() + 1;
        if (!StringUtils.hasText(b.getPreInvoiceNo())) {
            b.setPreInvoiceNo(b.getReconBillNo() + String.format("%03d", seq));
        }
        if (!StringUtils.hasText(b.getBillNo())) {
            b.setBillNo("SCP" + LocalDateTime.now().format(TS) + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        }
        b.setStatus(ScpBill.ST_WAIT_MATCH);
        b.setFailReason(null);
        mapper.insert(b);
        // R5-D-100：SCP 对账开票数据落地留痕（打通点1/2）——collab_sync_log 记方向/单号/结果，落地不再静默
        syncService.log("SCP_IF01_LAND", "SCP_TO_AP", b.getPreInvoiceNo(),
                CollabSyncLog.ST_SUCCESS, "对账开票数据落地：预制发票号 " + b.getPreInvoiceNo() + "，金额 " + b.getAmount(), null);
        return b;
    }

    /**
     * 自动匹配（R-16）：按预设容差（ar.recon.tolerance-amt-pct）比对发票与对账单的
     * 金额/税额/价税合计；购销方信息一致性由登记侧校验。通过→待寄送；不通过→匹配失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill match(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_MATCH.equals(b.getStatus()) && !ScpBill.ST_MATCH_FAILED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可匹配");
        }
        BigDecimal tolerance = tolerancePct();
        List<String> issues = new java.util.ArrayList<>();
        if (b.getReconAmount() == null) {
            issues.add("对账单基准金额未提供（打通点1数据缺失）");
        } else {
            compare("金额", b.getAmount(), b.getReconAmount(), tolerance, issues);
            compare("税额", nvl(b.getTaxAmount()), nvl(b.getReconTaxAmount()), tolerance, issues);
            compare("价税合计", nvl(b.getTotalAmount()), nvl(b.getReconTotal()), tolerance, issues);
        }
        if (issues.isEmpty()) {
            b.setStatus(ScpBill.ST_WAIT_SEND);
            b.setFailReason(null);
        } else {
            b.setStatus(ScpBill.ST_MATCH_FAILED);
            b.setFailReason("自动匹配未通过：" + String.join("；", issues));
        }
        mapper.updateById(b);
        return b;
    }

    /** 手工匹配（R-17）：自动未通过时由发票审核岗人工核定对账单基准金额后重新匹配。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill manualMatch(Long id, BigDecimal reconAmount, BigDecimal reconTaxAmount, BigDecimal reconTotal) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_MATCH.equals(b.getStatus()) && !ScpBill.ST_MATCH_FAILED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可手工匹配");
        }
        if (reconAmount != null) {
            b.setReconAmount(reconAmount);
        }
        if (reconTaxAmount != null) {
            b.setReconTaxAmount(reconTaxAmount);
        }
        if (reconTotal != null) {
            b.setReconTotal(reconTotal);
        }
        mapper.updateById(b);
        return match(b.getId());
    }

    /**
     * 强制匹配（R-18）：双方核对无误但自动匹配仍不通过时强制通过。
     * 蓝图要求一级审批；当前以强制留痕（操作人+原因入备注与接口日志）简化，接审批中心后可加签。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill forceMatch(Long id, String reason) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_MATCH.equals(b.getStatus()) && !ScpBill.ST_MATCH_FAILED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可强制匹配");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "强制匹配必须填写原因（R-18 需一级审批）");
        }
        b.setStatus(ScpBill.ST_WAIT_SEND);
        b.setFailReason(null);
        b.setRemark(appendNote(b.getRemark(), "强制匹配（R-18）："));
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_MATCH_POST, "SCP_TO_EBS", b.getBillNo(),
                CollabSyncLog.ST_MOCK, "R-18 强制匹配生效（需一级审批，简化留痕）：" + reason, null);
        return b;
    }

    /** 预认证（内网发票处理查询页，认证前置）：记账成功后可预认证，状态不变、留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill preCertify(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_POSTED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅记账成功单据可预认证");
        }
        b.setRemark(appendNote(b.getRemark(), "预认证 " + LocalDate.now()));
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_CERTIFY, "AP_TO_SCP", b.getBillNo(),
                CollabSyncLog.ST_MOCK, "预认证完成（待集中认证）", null);
        return b;
    }

    private String appendNote(String origin, String add) {
        String base = origin == null ? "" : origin;
        return base.isBlank() ? add : base + "；" + add;
    }

    /** 取消匹配（R-19）：仅"待寄送"状态可取消，回待匹配。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill cancelMatch(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_SEND.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅待寄送状态可取消匹配（R-19），当前 " + b.getStatus());
        }
        b.setStatus(ScpBill.ST_WAIT_MATCH);
        mapper.updateById(b);
        return b;
    }

    /** 创建邮包寄送（外网寄送管理）：待寄送 → 待签收。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill send(Long id, String courierCompany, String courierNo) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_SEND.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 须匹配成功后才能寄送");
        }
        if (!StringUtils.hasText(courierCompany) || !StringUtils.hasText(courierNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "快递公司与快递单号必填");
        }
        b.setCourierCompany(courierCompany);
        b.setCourierNo(courierNo);
        b.setStatus(ScpBill.ST_WAIT_SIGN);
        mapper.updateById(b);
        return b;
    }

    /** 内网快递签收：待签收 → 待扫描（移交扫描岗）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill sign(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_SIGN.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可签收");
        }
        b.setStatus(ScpBill.ST_WAIT_SCAN);
        mapper.updateById(b);
        return b;
    }

    /** 扫描识别 + 票核（R-20 六要素自动二次校验）：待扫描 → 票核成功 / 票核失败。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill scan(Long id, ScpBill scanned) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_SCAN.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可扫描");
        }
        b.setStatus(ScpBill.ST_CHECKING);
        List<String> issues = new java.util.ArrayList<>();
        if (scanned != null) {
            if (StringUtils.hasText(scanned.getBuyerTaxNo()) && !scanned.getBuyerTaxNo().equals(b.getBuyerTaxNo())) {
                issues.add("购方税号不一致");
            }
            if (StringUtils.hasText(scanned.getSellerTaxNo()) && !scanned.getSellerTaxNo().equals(b.getSellerTaxNo())) {
                issues.add("销方税号不一致");
            }
            if (scanned.getAmount() != null && scanned.getAmount().compareTo(b.getAmount()) != 0) {
                issues.add("发票金额不一致（票面 " + scanned.getAmount() + "）");
            }
            if (scanned.getTaxAmount() != null && scanned.getTaxAmount().compareTo(nvl(b.getTaxAmount())) != 0) {
                issues.add("发票税额不一致（票面 " + scanned.getTaxAmount() + "）");
            }
            if (scanned.getTotalAmount() != null && scanned.getTotalAmount().compareTo(nvl(b.getTotalAmount())) != 0) {
                issues.add("价税合计不一致（票面 " + scanned.getTotalAmount() + "）");
            }
            if (scanned.getInvoiceDate() != null && !scanned.getInvoiceDate().equals(b.getInvoiceDate())) {
                issues.add("发票日期不一致（票面 " + scanned.getInvoiceDate() + "）");
            }
            if (StringUtils.hasText(scanned.getInvoiceNo())) {
                b.setInvoiceNo(scanned.getInvoiceNo());
            }
            if (StringUtils.hasText(scanned.getInvoiceCode())) {
                b.setInvoiceCode(scanned.getInvoiceCode());
            }
            if (StringUtils.hasText(scanned.getImageRef())) {
                b.setImageRef(scanned.getImageRef());
            }
        }
        if (issues.isEmpty()) {
            b.setStatus(ScpBill.ST_CHECK_OK);
            b.setFailReason(null);
        } else {
            b.setStatus(ScpBill.ST_CHECK_FAILED);
            b.setFailReason("票核失败（R-20六要素）：" + String.join("；", issues));
        }
        mapper.updateById(b);
        return b;
    }

    /** 票核失败复核后重新匹配：票核失败 → 待匹配。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill reMatch(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_CHECK_FAILED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅票核失败单据可复核重新匹配");
        }
        b.setStatus(ScpBill.ST_WAIT_MATCH);
        mapper.updateById(b);
        return b;
    }

    /**
     * 记账（打通点3：三单匹配数据推送 EBS）：票核成功 → 记账成功。
     * R-14：月末关账期间禁止发送数据至 EBS 产生凭证，直接拦截。
     * EBS 通道（ext_interface_config 分类 EBS_AP）未配置时 MOCK 落日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill post(Long id) {
        ScpBill b = require(id);
        if (!ScpBill.ST_CHECK_OK.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 须票核成功后才能记账");
        }
        String period = YearMonth.now().toString();
        if (monthCloseService.isFrozen(1L, period)) {
            syncService.log(CollabSyncLog.IF_SCP_MATCH_POST, "SCP_TO_EBS", b.getBillNo(),
                    CollabSyncLog.ST_FAILED, "R-14 关账冻结：期间 " + period + " 禁止发送数据至ERP产生凭证", null);
            throw new BizException(ErrorCode.PARAM_INVALID, "R-14 关账冻结：期间 " + period + " 禁止发送数据至ERP产生凭证");
        }
        boolean real = false;
        String result = "ERP通道未配置，模拟记账";
        // 多 ERP 解析（V1.4）：通道 = {PROVIDER}_AP（蓝图默认 EBS_AP）；
        String channel = connector.resolveErpChannel("AP");
        String variantCategory = connector.getConfig(connector.resolveErpChannel("AP_BLUE")) != null
                ? connector.resolveErpChannel("AP_BLUE") : channel;
        if (connector.isConfigured(variantCategory)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reconBillNo", b.getReconBillNo());
            payload.put("preInvoiceNo", b.getPreInvoiceNo());
            payload.put("invoiceNo", b.getInvoiceNo());
            payload.put("supplierCode", b.getSupplierCode());
            payload.put("amount", b.getAmount());
            // P0-2 幂等键：同一 SCP 单据重复记账（如超时重发）不重复生成 ERP 凭证，回放首次结果
            ChannelResponse resp = connector.invokeWrite(channel, payload,
                    "SCP_AP:" + TenantContext.getTenantId() + ":" + b.getBillNo());
            real = resp != null && resp.isOk();
            result = resp == null ? "ERP无响应" : resp.bodyForLog();
        }
        syncService.log(CollabSyncLog.IF_SCP_MATCH_POST, "SCP_TO_EBS", b.getBillNo(),
                real ? CollabSyncLog.ST_SUCCESS : CollabSyncLog.ST_MOCK, result,
                "{\"reconBillNo\":\"" + b.getReconBillNo() + "\",\"preInvoiceNo\":\"" + b.getPreInvoiceNo() + "\"}");
        if (connector.isConfigured(channel) && !real) {
            b.setFailReason("ERP记账失败：" + result);
            mapper.updateById(b);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "ERP记账失败：" + result);
        }
        b.setStatus(ScpBill.ST_POSTED);
        b.setFailReason(null);
        mapper.updateById(b);
        return b;
    }

    /** 集中认证（打通点6）：记账成功 → 认证完成（终态）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill certify(Long id, String result) {
        ScpBill b = require(id);
        if (!ScpBill.ST_POSTED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 须记账成功后才能认证");
        }
        b.setCertifyResult(result == null || result.isBlank() ? "认证通过" : result);
        b.setCertifyDate(LocalDate.now());
        b.setStatus(ScpBill.ST_CERTIFIED);
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_CERTIFY, "AP_TO_SCP", b.getBillNo(),
                CollabSyncLog.ST_SUCCESS, b.getCertifyResult() + "，认证日期 " + b.getCertifyDate(), null);
        return b;
    }

    /** 付款信息回写（打通点5：EBS付款成功后推送）：body {payVoucherNo, payDate, payAmount}。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill payWriteback(Long id, String payVoucherNo, LocalDate payDate, BigDecimal payAmount) {
        ScpBill b = require(id);
        if (!ScpBill.ST_POSTED.equals(b.getStatus()) && !ScpBill.ST_CERTIFIED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅记账成功/认证完成单据可回写付款信息");
        }
        if (!StringUtils.hasText(payVoucherNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款凭证号必填");
        }
        b.setPayVoucherNo(payVoucherNo);
        b.setPayDate(payDate == null ? LocalDate.now() : payDate);
        b.setPayAmount(payAmount == null ? b.getAmount() : payAmount);
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_PAY, "AP_TO_SCP", b.getBillNo(),
                CollabSyncLog.ST_SUCCESS, "付款凭证 " + payVoucherNo + "，金额 " + b.getPayAmount(), null);
        return b;
    }

    /**
     * R-18 强制匹配申请（四眼原则第一步）：置 FORCE_PENDING 锁定（不可寄送/匹配/票核），
     * 发起 SCP_FORCE_MATCH 审批流（挂共享主管待办）；差额超阈值须二级财务经理审批（见 approveForce）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill forceApply(Long id, String reason, String operator) {
        ScpBill b = require(id);
        if (!ScpBill.ST_WAIT_MATCH.equals(b.getStatus()) && !ScpBill.ST_MATCH_FAILED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 当前状态不可申请强制匹配");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "强制匹配原因必填（R-18 需一级审批）");
        }
        b.setForceStatus("PENDING");
        b.setForceReason(reason);
        b.setForceApplyUser(operator);
        b.setForcePrevStatus(b.getStatus());
        b.setStatus(ScpBill.ST_FORCE_PENDING);
        mapper.updateById(b);
        try {
            // 挂共享主管待办（差额二级路由在审批时按 scp.force.diff-pct 校验）
        } catch (Exception ignore) {
            // 待办挂接失败不回滚强制匹配申请，主管可在列表页处理
        }
        log.info("[arflow] 单据 {} 申请强制匹配（申请人 {}，原因 {}）", b.getBillNo(), operator, reason);
        return b;
    }

    /** R-18 审批通过：四眼（审批人≠申请人）；差额超阈值须二级权限（控制器校验）；通过后放行待寄送。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill approveForce(Long id, String approver) {
        ScpBill b = require(id);
        if (!ScpBill.ST_FORCE_PENDING.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 非强匹审批中状态");
        }
        if (approver != null && approver.equals(b.getForceApplyUser())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "四眼原则：审批人不得与申请人相同");
        }
        b.setStatus(ScpBill.ST_WAIT_SEND);
        b.setFailReason(null);
        b.setForceStatus("APPROVED");
        b.setForceApprUser(approver);
        b.setRemark(appendNote(b.getRemark(), "强制匹配审批通过[" + approver + "]"));
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_MATCH_POST, "SCP_TO_EBS", b.getBillNo(),
                CollabSyncLog.ST_MOCK, "R-18 强制匹配审批通过（" + approver + "）：" + b.getForceReason(), null);
        return b;
    }

    /** R-18 审批驳回：回退原状态并记录原因。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill rejectForce(Long id, String approver, String reason) {
        ScpBill b = require(id);
        if (!ScpBill.ST_FORCE_PENDING.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + b.getBillNo() + " 非强匹审批中状态");
        }
        b.setStatus(b.getForcePrevStatus() == null ? ScpBill.ST_WAIT_MATCH : b.getForcePrevStatus());
        b.setForceStatus("REJECTED");
        b.setForceApprUser(approver);
        b.setFailReason("强匹驳回[" + approver + "]：" + (reason == null ? "" : reason));
        mapper.updateById(b);
        return b;
    }

    /** 发票替换（蓝图 EBS 接口6）：原票作废，按同对账单重新生成新票（R-31 新流水）。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill replaceBill(Long id, String reason) {
        ScpBill old = require(id);
        if (List.of(ScpBill.ST_POSTED, ScpBill.ST_CERTIFIED, ScpBill.ST_VOIDED).contains(old.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + old.getBillNo() + " 当前状态不可替换");
        }
        old.setStatus(ScpBill.ST_VOIDED);
        old.setRemark(appendNote(old.getRemark(), "发票替换：" + (reason == null ? "" : reason)));
        mapper.updateById(old);
        ScpBill fresh = new ScpBill();
        fresh.setReconBillNo(old.getReconBillNo());
        fresh.setSupplierCode(old.getSupplierCode());
        fresh.setSupplierName(old.getSupplierName());
        fresh.setBuyerCompanyCode(old.getBuyerCompanyCode());
        fresh.setInvoiceType(old.getInvoiceType());
        fresh.setCcy(old.getCcy());
        fresh.setAmount(old.getAmount());
        fresh.setTaxAmount(old.getTaxAmount());
        fresh.setTotalAmount(old.getTotalAmount());
        fresh.setReconAmount(old.getReconAmount());
        fresh.setReconTaxAmount(old.getReconTaxAmount());
        fresh.setReconTotal(old.getReconTotal());
        fresh.setBuyerTaxNo(old.getBuyerTaxNo());
        fresh.setSellerTaxNo(old.getSellerTaxNo());
        fresh.setRemark("替换原票 " + old.getBillNo());
        return create(fresh);
    }

    /** 冲销凭证（蓝图 EBS 接口7）：记账成功 → 回待匹配（可重新走链），MOCK 留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill reverseVoucher(Long id, String reason) {
        ScpBill b = require(id);
        if (!ScpBill.ST_POSTED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅记账成功单据可冲销");
        }
        b.setStatus(ScpBill.ST_WAIT_MATCH);
        b.setRemark(appendNote(b.getRemark(), "冲销凭证：" + (reason == null ? "" : reason)));
        mapper.updateById(b);
        syncService.log(CollabSyncLog.IF_SCP_MATCH_POST, "SCP_TO_EBS", b.getBillNo(),
                CollabSyncLog.ST_MOCK, "EBS_AP_REVERSE 冲销凭证（变体细分）：" + (reason == null ? "" : reason), null);
        return b;
    }

    /** 直接扣款入账（蓝图 EBS 接口8）：记账成功单上登记扣款，MOCK 留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public ScpBill directDeduct(Long id, BigDecimal deductAmount, String reason) {
        ScpBill b = require(id);
        if (!ScpBill.ST_POSTED.equals(b.getStatus()) && !ScpBill.ST_CERTIFIED.equals(b.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅记账/认证单据可登记直接扣款");
        }
        b.setRemark(appendNote(b.getRemark(), "直接扣款入账 " + deductAmount + "：" + (reason == null ? "" : reason)));
        mapper.updateById(b);
        syncService.log("SCP_IF03_DIRECT", "SCP_TO_EBS", b.getBillNo(),
                CollabSyncLog.ST_MOCK, "EBS_AP_DIRECT 直接扣款入账（变体细分）：" + deductAmount, null);
        return b;
    }

    /** 按 id 查询（控制器审批差额校验用）。 */
    public ScpBill get(Long id) {
        return require(id);
    }

    public List<ScpBill> list(String status, String keyword) {
        LambdaQueryWrapper<ScpBill> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ScpBill::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ScpBill::getBillNo, keyword)
                    .or().like(ScpBill::getReconBillNo, keyword)
                    .or().like(ScpBill::getSupplierName, keyword)
                    .or().like(ScpBill::getInvoiceNo, keyword));
        }
        return mapper.selectList(q.orderByDesc(ScpBill::getId));
    }

    // ---------------- 工具 ----------------

    private BigDecimal tolerancePct() {
        String v = configService.getValue("ar.recon.tolerance-amt-pct");
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(v.trim()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private void compare(String label, BigDecimal expect, BigDecimal actual, BigDecimal tolerance, List<String> issues) {
        BigDecimal diff = expect.subtract(actual).abs();
        BigDecimal allowed = tolerance.multiply(expect.abs().max(BigDecimal.ONE));
        if (diff.compareTo(allowed) > 0) {
            issues.add(label + "超容差（发票 " + expect + " vs 对账单 " + actual + "）");
        }
    }

    private ScpBill require(Long id) {
        ScpBill b = mapper.selectById(id);
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SCP单据不存在 id=" + id);
        }
        return b;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
