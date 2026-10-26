// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.apflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.apflow.entity.ApDispatchRule;
import com.fssc.apflow.entity.ApServiceRequest;
import com.fssc.apflow.mapper.ApDispatchRuleMapper;
import com.fssc.apflow.mapper.ApServiceRequestMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.bank.gateway.BankGateway;
import com.fssc.bank.gateway.BankResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AP付款服务申请单服务（蓝图 SCC_AP_15~19 状态机 + 派工规则 R-01~R-05）。
 *
 * <p>状态机：待派工 → 审单中 →（确认修改付款方式转组长 GROUP_REVIEW）→ 已过账 → 已付款；
 * 旁路：已作废（终止付款，作废后可重拉覆盖单号不变 R-10）/ 已完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApRequestService {

    private static final List<String> TYPES = Arrays.asList(
            ApServiceRequest.TYPE_PREPAY, ApServiceRequest.TYPE_PREPAY_WRITEOFF,
            ApServiceRequest.TYPE_PLAN_PAYMENT, ApServiceRequest.TYPE_RELATED_PAYMENT,
            ApServiceRequest.TYPE_URGENT_PAYMENT);

    private final ApServiceRequestMapper requestMapper;
    private final ApDispatchRuleMapper ruleMapper;
    private final ApMonthCloseService monthCloseService;
    private final com.fssc.arflow.service.ArFlowVoucherService voucherService;
    private final com.fssc.connector.api.ConnectorFacade connector;
    private final com.fssc.collab.service.CollabSyncService collabSyncService;
    /** 付款通道：与出纳付款批次共用 BankGateway；dev 默认模拟，生产 fssc.bank.gateway=http 切真实银企直联。 */
    private final BankGateway bankGateway;
    private final SysUserMapper sysUserMapper;
    /** P4-3 审计扩面：单据级状态跃迁记录（接口级另有 @OperLog 双写） */
    private final AuditTrailService auditTrailService;

    // ---------------- 创建 ----------------

    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest create(ApServiceRequest r) {
        if (!TYPES.contains(r.getReqType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据类型非法:" + r.getReqType());
        }
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额必须大于0");
        }
        validateForms(r);
        r.setId(null);
        r.setBillNo(ApServiceRequest.prefixOf(r.getReqType()) + LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        if (r.getCcy() == null || r.getCcy().isBlank()) {
            r.setCcy("CNY");
        }
        if (r.getPaymentType() == null || r.getPaymentType().isBlank()) {
            r.setPaymentType("快速");
        }
        if (r.getPaymentConfig() == null || r.getPaymentConfig().isBlank()) {
            r.setPaymentConfig("payment process profile");
        }
        if (ApServiceRequest.TYPE_PREPAY.equals(r.getReqType())) {
            r.setTaxAmount(java.math.BigDecimal.ZERO); // 预付款税额强制0
            r09OrderCheck(r);
        }
        if (r.getRebateAmount() == null) {
            r.setRebateAmount(java.math.BigDecimal.ZERO);
        }
        r.setStatus(ApServiceRequest.ST_PENDING_DISPATCH);
        requestMapper.insert(r);
        log.info("[apflow] 服务申请单 {} 创建（{}）", r.getBillNo(), r.getReqType());
        return r;
    }

    /**
     * R-09 订单信息获取（蓝图 ERP 接口12，SCC_AP_15）：预付款创建时按 PO 编号到 ERP 校验
     * 供应商代码/币种/付款方法；通道 = {PROVIDER}_ORDER_GET（多 ERP 解析，见 ErpChannelResolver），
     * 未配置则 MOCK 跳过（离线可跑）。
     */
    private void r09OrderCheck(ApServiceRequest r) {
        String category = connector.resolveErpChannel("ORDER_GET");
        if (!connector.isConfigured(category)) {
            collabSyncService.log(com.fssc.collab.entity.CollabSyncLog.IF_AR_SHIPPING, "SHARED_TO_EBS",
                    r.getBillNo(), com.fssc.collab.entity.CollabSyncLog.ST_MOCK,
                    "R-09 " + category + " 未配置，跳过 PO 校验（PO " + r.getPoNo() + "）", null);
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("poNo", r.getPoNo());
        com.fssc.connector.api.ChannelResponse resp = connector.invokeRead(category, payload);
        if (resp == null || !resp.isOk()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "R-09 订单信息获取失败（PO " + r.getPoNo() + "）");
        }
        collabSyncService.log(com.fssc.collab.entity.CollabSyncLog.IF_AR_SHIPPING, "SHARED_TO_EBS",
                r.getBillNo(), com.fssc.collab.entity.CollabSyncLog.ST_SUCCESS,
                "R-09 PO 校验通过（PO " + r.getPoNo() + "）", null);
    }

    /** OA作废后重拉覆盖：单号不变（R-10）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest repull(Long id, ApServiceRequest fresh) {
        ApServiceRequest r = require(id);
        if (!ApServiceRequest.ST_VOIDED.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已作废单据可重拉覆盖：" + r.getBillNo());
        }
        Long srcId = r.getId();
        String billNo = r.getBillNo();
        fresh.setId(srcId);
        fresh.setBillNo(billNo);
        fresh.setStatus(ApServiceRequest.ST_PENDING_DISPATCH);
        fresh.setHandler(null);
        fresh.setHandlerPool(null);
        fresh.setVoidReason(null);
        fresh.setModifiedFlag(null);
        validateForms(fresh);
        requestMapper.updateById(fresh);
        return fresh;
    }

    // ---------------- 派工（R-01~R-05） ----------------

    /** 自动派工：按单据类型+付款方法匹配规则；多负责人进入选单池（R-02）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest dispatch(Long id) {
        ApServiceRequest r = require(id);
        requireStatus(r, ApServiceRequest.ST_PENDING_DISPATCH, "派工");
        // P4-3 审计：改前快照（只记本次实际变化的字段）
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("handler", r.getHandler());
        auditBefore.put("handlerPool", r.getHandlerPool());
        ApDispatchRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<ApDispatchRule>()
                .eq(ApDispatchRule::getEnabled, 1)
                .eq(ApDispatchRule::getPaymentWay, r.getPaymentWay())
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置「" + r.getPaymentWay() + "」付款方法的派工规则，请由共享运营岗手工派工");
        }
        List<String> handlers = split(rule.getHandlers());
        assertRuleUsers(rule, handlers);
        r.setGroupLeader(rule.getGroupLeader());
        if (handlers.size() == 1) {
            r.setHandler(handlers.get(0));
            r.setHandlerPool(null);
        } else {
            r.setHandler(null);
            r.setHandlerPool(String.join(",", handlers));
        }
        r.setStatus(ApServiceRequest.ST_DISPATCHED);
        r.setDispatchTime(LocalDateTime.now());
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("handler", r.getHandler());
        auditAfter.put("handlerPool", r.getHandlerPool());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter, "自动派工");
        return r;
    }

    /** 选单：多负责人池中认领（R-02）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest claim(Long id, String user) {
        ApServiceRequest r = require(id);
        requireStatus(r, ApServiceRequest.ST_DISPATCHED, "选单");
        if (r.getHandlerPool() == null || Arrays.stream(r.getHandlerPool().split("[,，]"))
                .noneMatch(s -> s.trim().equals(user))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户 " + user + " 不在本单选单池内");
        }
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("handler", r.getHandler());
        auditBefore.put("handlerPool", r.getHandlerPool());
        r.setHandler(user);
        r.setHandlerPool(null);
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("handler", r.getHandler());
        auditAfter.put("handlerPool", r.getHandlerPool());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter, "选单认领");
        return r;
    }

    /** 运营岗手工派工/转单（R-05）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest assign(Long id, String user) {
        ApServiceRequest r = require(id);
        if (!List.of(ApServiceRequest.ST_PENDING_DISPATCH, ApServiceRequest.ST_DISPATCHED)
                .contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可派工/转单");
        }
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("handler", r.getHandler());
        auditBefore.put("handlerPool", r.getHandlerPool());
        r.setHandler(user);
        r.setHandlerPool(null);
        if (ApServiceRequest.ST_PENDING_DISPATCH.equals(r.getStatus())) {
            r.setStatus(ApServiceRequest.ST_DISPATCHED);
            r.setDispatchTime(LocalDateTime.now());
        }
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("handler", r.getHandler());
        auditAfter.put("handlerPool", r.getHandlerPool());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter, "手工派工/转单");
        return r;
    }

    // ---------------- 确认（付款计划表解析确认） ----------------

    /**
     * 确认服务请求：可修改字段=付款类型/银行账户/付款方法/折扣额/到期日/付款额；
     * 修改付款方法自动转AP组长审核（R-03）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest confirm(Long id, ApServiceRequest patch) {
        ApServiceRequest r = require(id);
        if (!List.of(ApServiceRequest.ST_DISPATCHED, ApServiceRequest.ST_GROUP_REVIEW)
                .contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可确认服务请求");
        }
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("paymentType", r.getPaymentType());
        auditBefore.put("bankNo", r.getBankNo());
        auditBefore.put("paymentWay", r.getPaymentWay());
        auditBefore.put("rebateAmount", r.getRebateAmount());
        auditBefore.put("expireDate", r.getExpireDate());
        auditBefore.put("paymentAmount", r.getPaymentAmount());
        auditBefore.put("modifiedFlag", r.getModifiedFlag());
        String originWay = r.getPaymentWay();
        if (notBlank(patch.getPaymentType())) {
            r.setPaymentType(patch.getPaymentType());
        }
        if (notBlank(patch.getBankNo())) {
            r.setBankNo(patch.getBankNo());
        }
        if (notBlank(patch.getPaymentWay())) {
            r.setPaymentWay(patch.getPaymentWay());
        }
        if (patch.getRebateAmount() != null) {
            r.setRebateAmount(patch.getRebateAmount());
        }
        if (patch.getExpireDate() != null) {
            r.setExpireDate(patch.getExpireDate());
        }
        if (patch.getPaymentAmount() != null) {
            r.setPaymentAmount(patch.getPaymentAmount());
        }
        if (notBlank(patch.getRemark())) {
            r.setRemark(patch.getRemark());
        }
        validateForms(r);
        r.setConfirmTime(LocalDateTime.now());
        boolean wayChanged = originWay != null && !originWay.equals(r.getPaymentWay());
        if (wayChanged || ApServiceRequest.ST_GROUP_REVIEW.equals(r.getStatus())) {
            r.setModifiedFlag(1);
            r.setModifyNote((r.getModifyNote() == null ? "" : r.getModifyNote() + " | ")
                    + "确认时修改付款方法：" + originWay + " → " + r.getPaymentWay());
            r.setStatus(ApServiceRequest.ST_GROUP_REVIEW);
        }
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("paymentType", r.getPaymentType());
        auditAfter.put("bankNo", r.getBankNo());
        auditAfter.put("paymentWay", r.getPaymentWay());
        auditAfter.put("rebateAmount", r.getRebateAmount());
        auditAfter.put("expireDate", r.getExpireDate());
        auditAfter.put("paymentAmount", r.getPaymentAmount());
        auditAfter.put("modifiedFlag", r.getModifiedFlag());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter,
                wayChanged ? "确认服务请求（付款方法变更，转组长审核 R-03）" : "确认服务请求");
        return r;
    }

    /** 组长审核（R-04）：通过/驳回均回上一责任人，通过后可过账，不通过必须填原因。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest groupReview(Long id, boolean pass, String note) {
        ApServiceRequest r = require(id);
        requireStatus(r, ApServiceRequest.ST_GROUP_REVIEW, "组长审核");
        if (!pass && (note == null || note.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核不通过必须填写原因");
        }
        String reviewer = currentUserName();
        String reason = note == null ? "" : note.trim();
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("modifiedFlag", r.getModifiedFlag());
        r.setModifyNote((r.getModifyNote() == null ? "" : r.getModifyNote() + " | ")
                + (pass ? "组长通过[" : "组长驳回[") + reviewer + "]：" + reason);
        r.setStatus(ApServiceRequest.ST_DISPATCHED);
        if (pass) {
            r.setModifiedFlag(0);
        }
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("modifiedFlag", r.getModifiedFlag());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter,
                (pass ? "组长审核通过：" : "组长审核驳回：") + (note == null ? "" : note));
        return r;
    }

    // ---------------- 过账/付款/作废/完成 ----------------

    /** 过账：ERP 生成付款凭证（多 ERP 适配；未配置外部通道时本地方案生成）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest post(Long id) {
        ApServiceRequest r = require(id);
        requireStatus(r, ApServiceRequest.ST_DISPATCHED, "过账");
        if (r.getModifiedFlag() != null && r.getModifiedFlag() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据已修改，须待组长审核通过后方可过账");
        }
        // 协同冻结门控（打通点7）：关账期间禁止发送数据产生凭证
        String period = r.getPaymentDate() == null ? null
                : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").format(r.getPaymentDate());
        if (monthCloseService.isFrozen(r.getCompanyId(), period)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    (period == null ? "付款日期为空，无法确定所属会计期间；" : "期间 " + period + " ")
                            + "存在未完成的月末关账（协同冻结），禁止过账；请先填写付款日期或待关账完成");
        }
        // 真实分录凭证（补齐评估缺口2）：借2202应付账款/贷1002银行存款，失败阻断过账（fail-closed）
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voucherNo", r.getVoucherNo());
        String voucherNo = voucherService.generatePaymentVoucher(
                r.getTenantId() == null ? 1L : r.getTenantId(),
                r.getCompanyId(), r.getAmount(), r.getVendorName(), r.getBillNo(), r.getId());
        r.setVoucherNo(voucherNo);
        r.setStatus(ApServiceRequest.ST_POSTED);
        r.setPostTime(LocalDateTime.now());
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voucherNo", r.getVoucherNo());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter, "过账（生成付款凭证 " + voucherNo + "）");
        return r;
    }

    /**
     * 资金付款：经 {@link BankGateway} 提交银行（dev 模拟 / 生产银企直联同一接口）。
     * 银行失败只抛错、单据保持 POSTED 供重试，绝不先置 PAID；成功才回写银行流水号。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest pay(Long id) {
        ApServiceRequest r = require(id);
        requireStatus(r, ApServiceRequest.ST_POSTED, "付款");
        if (r.getVoucherNo() == null || r.getVoucherNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据尚无付款凭证，禁止发起资金付款");
        }
        if (r.getBankNo() == null || r.getBankNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款账号为空，禁止发起资金付款");
        }
        String batchNo = r.getBillNo();
        BankResult bank = bankGateway.submitBatch(batchNo, List.of(new BankGateway.PayItem(
                r.getVendorName(), null, r.getBankNo(), r.getAmount(),
                "付款服务申请 " + r.getPaymentWay()
                        + (r.getPaymentDate() == null ? "" : "，付款日 " + r.getPaymentDate()))));
        if (bank == null || !bank.isSuccess()) {
            String reason = bank == null ? "银行通道无响应"
                    : (notBlank(bank.getFailReason()) ? bank.getFailReason() : "银行返回失败");
            log.warn("[AP付款] 单据 {} 银行付款失败：{}", r.getBillNo(), reason);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "银行付款失败：" + reason + "；单据保持已过账，可修正后重试");
        }
        String serial = (bank.getSerialNos() == null || bank.getSerialNos().isEmpty())
                ? null : bank.getSerialNos().get(0);
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("payVoucherNo", r.getPayVoucherNo());
        r.setPayVoucherNo(serial);
        r.setStatus(ApServiceRequest.ST_PAID);
        r.setPayTime(LocalDateTime.now());
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("payVoucherNo", r.getPayVoucherNo());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter,
                "资金付款（银行批次 " + batchNo + "，"
                        + (serial == null ? "银行未返回流水号" : "流水号 " + serial) + "）");
        return r;
    }

    /** 作废（终止付款；预付款作废后可在OA调整重拉覆盖 R-10）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest voidRequest(Long id, String reason) {
        ApServiceRequest r = require(id);
        if (!List.of(ApServiceRequest.ST_PENDING_DISPATCH, ApServiceRequest.ST_DISPATCHED,
                ApServiceRequest.ST_GROUP_REVIEW).contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 已过账，不可作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因");
        }
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setVoidReason(reason);
        r.setStatus(ApServiceRequest.ST_VOIDED);
        requestMapper.updateById(r);
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voidReason", r.getVoidReason());
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore, auditAfter, "作废（终止付款）：" + reason);
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApServiceRequest close(Long id) {
        ApServiceRequest r = require(id);
        if (!List.of(ApServiceRequest.ST_PAID, ApServiceRequest.ST_POSTED,
                ApServiceRequest.ST_DISPATCHED).contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可完成");
        }
        Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        r.setStatus(ApServiceRequest.ST_COMPLETED);
        requestMapper.updateById(r);
        auditTrailService.recordUpdate("AP_REQUEST", id, r.getBillNo(), "ap_service_request",
                String.valueOf(id), auditBefore,
                java.util.Collections.singletonMap("status", r.getStatus()), "完成");
        return r;
    }

    public com.fssc.common.core.PageResult<ApServiceRequest> page(long pageNum, long pageSize,
                                                                   String reqType, String status, String vendorName) {
        LambdaQueryWrapper<ApServiceRequest> q = new LambdaQueryWrapper<>();
        if (notBlank(reqType)) {
            q.eq(ApServiceRequest::getReqType, reqType);
        }
        if (notBlank(status)) {
            q.eq(ApServiceRequest::getStatus, status);
        }
        if (notBlank(vendorName)) {
            q.like(ApServiceRequest::getVendorName, vendorName);
        }
        q.orderByDesc(ApServiceRequest::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ApServiceRequest> p =
                requestMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), q);
        return com.fssc.common.core.PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public ApServiceRequest get(Long id) {
        return require(id);
    }

    // ---------------- 派工规则 ----------------

    public java.util.List<ApDispatchRule> listRules() {
        java.util.List<ApDispatchRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ApDispatchRule>()
                .orderByAsc(ApDispatchRule::getId));
        fillDisplayNames(rules);
        rules.forEach(r -> r.setEditFingerprint(editFingerprint(r)));
        return rules;
    }

    /**
     * 派工是跨法人的共享岗配置，而人员分页接口按登录人公司强制办结 ⇒ 界面取不到别公司同事的姓名。
     * 这里在读取时按 user_name 回查补齐只读显示名：只覆盖**已写进规则**的工号，借此放开人员目录、写入数据库。
     * 查不到（账号已删）时留空位，界面按纯工号显示。
     */
    private void fillDisplayNames(java.util.List<ApDispatchRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ApDispatchRule r : rules) {
            names.addAll(split(r.getHandlers()));
            if (notBlank(r.getGroupLeader())) {
                names.add(r.getGroupLeader().trim());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        Map<String, String> display = new HashMap<>();
        for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserName, names))) {
            display.put(u.getUserName(), notBlank(u.getNickName()) ? u.getNickName() : u.getUserName());
        }
        for (ApDispatchRule r : rules) {
            r.setHandlerNames(r.getHandlers() == null ? null : split(r.getHandlers()).stream()
                    .map(n -> display.getOrDefault(n, n)).collect(java.util.stream.Collectors.joining(",")));
            r.setGroupLeaderName(notBlank(r.getGroupLeader())
                    ? display.getOrDefault(r.getGroupLeader().trim(), r.getGroupLeader().trim()) : null);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ApDispatchRule saveRule(ApDispatchRule rule) {
        if (rule.getHandlers() == null || rule.getHandlers().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "负责人清单必填（多人即选单池）");
        }
        assertRuleUsers(rule, split(rule.getHandlers()));
        if (rule.getId() == null) {
            rule.setId(null);
            ruleMapper.insert(rule);
        } else {
            // 并发校验：规则是配置面，两人同开弹窗后各自保存会互覆盖且界面毫无察觉。
            // 编辑分支先锁住当前行、用 listRules 下发的指纹重算比对，不符即拒＝后写的人必须刷新重来。
            ApDispatchRule current = ruleMapper.selectOne(new LambdaQueryWrapper<ApDispatchRule>()
                    .eq(ApDispatchRule::getId, rule.getId())
                    .last("FOR UPDATE"));
            if (current == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "派工规则不存在或已被删除，请刷新后重试");
            }
            if (rule.getEditFingerprint() == null || rule.getEditFingerprint().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少编辑凭据，请关闭弹窗刷新后重试");
            }
            if (!rule.getEditFingerprint().equals(editFingerprint(current))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该派工规则已被他人修改，请关闭弹窗刷新后重试");
            }
            ruleMapper.updateById(rule);
        }
        rule.setEditFingerprint(null);
        rule.setHandlerNames(null);
        rule.setGroupLeaderName(null);
        return rule;
    }

    /**
     * 编辑凭据＝ruleName/paymentWay/handlers/groupLeader/enabled/remark 六字段拼接后的 SHA-256（只读回显，不写入数据库）。
     * 选它而不选 version 列或全局乐观锁拦截器＝零 DDL、不外溢到其它 updateById 语义。
     */
    private String editFingerprint(ApDispatchRule r) {
        String canonical = String.join("\u0001",
                blankToEmpty(r.getRuleName()), blankToEmpty(r.getPaymentWay()), blankToEmpty(r.getHandlers()),
                blankToEmpty(r.getGroupLeader()), String.valueOf(r.getEnabled()), blankToEmpty(r.getRemark()));
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String blankToEmpty(String v) {
        return v == null ? "" : v;
    }

    // ---------------- 校验（蓝图附录三） ----------------

    private void validateForms(ApServiceRequest r) {
        List<String> errors = new java.util.ArrayList<>();
        if (r.getPaymentWay() == null || r.getPaymentWay().isBlank()) {
            errors.add("付款方法必填（支票/银行转账/应付票据）");
        }
        if (r.getInvoiceNo() == null || r.getInvoiceNo().isBlank()) {
            errors.add("发票编号必填");
        } else if (!validInvoiceNoRule(r.getInvoiceNo())) {
            errors.add("发票编号不符合规则：连续XXXXXX01-10或非连续XXXXXX01,XXXXXX05,XXXXXX09");
        }
        if (ApServiceRequest.TYPE_PREPAY.equals(r.getReqType())) {
            if (r.getPoNo() == null || r.getPoNo().isBlank()) {
                errors.add("预付款必须录入PO编号");
            }
            if (r.getTaxAmount() != null && r.getTaxAmount().compareTo(java.math.BigDecimal.ZERO) != 0) {
                errors.add("预付款税额强制为0.0");
            }
        }
        if ("应付票据".equals(r.getPaymentWay()) && r.getExpireDate() == null) {
            errors.add("付款方法=应付票据时必须录入汇票到期日期");
        }
        if (r.getPaymentAmount() != null) {
            // 背书付款额与发票合计一致性由确认岗提供发票合计后校验（此处校验为正数）
            if (r.getPaymentAmount().signum() <= 0) {
                errors.add("背书付款额必须大于0");
            }
        }
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务申请单校验未通过：" + String.join("；", errors));
        }
    }

    /** 发票号规则（蓝图 R-24）：后缀区间 XXXXXX01-10 或逗号分隔单号。 */
    static boolean validInvoiceNoRule(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return false;
        }
        for (String seg : invoiceNo.split("[,，]")) {
            String s = seg.trim();
            if (s.isEmpty()) {
                return false;
            }
            if (s.contains("-")) {
                String[] parts = s.split("-");
                if (parts.length != 2 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+")) {
                    return false;
                }
                String left = parts[0];
                String right = parts[1];
                if (right.length() < left.length()) {
                    String leftSuffix = left.substring(left.length() - right.length());
                    if (Integer.parseInt(right) < Integer.parseInt(leftSuffix)) {
                        return false;
                    }
                } else if (Long.parseLong(right) < Long.parseLong(left)) {
                    return false;
                }
            } else if (!s.matches("\\d+")) {
                return false;
            }
        }
        return true;
    }

    private List<String> split(String handlers) {
        if (handlers == null || handlers.isBlank()) {
            return List.of();
        }
        return Arrays.stream(handlers.split("[,，]")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
    }

    /** 派工规则落地前校验账号真实存在且启用，避免生成无法认领的幽灵单池（F4）。 */
    private void assertRuleUsers(ApDispatchRule rule, List<String> handlers) {
        if (handlers.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "派工规则「" + (rule.getRuleName() == null ? rule.getPaymentWay() : rule.getRuleName())
                            + "」未配置负责人，请先修正规则或改用人工派工");
        }
        List<String> candidates = new ArrayList<>(handlers);
        if (notBlank(rule.getGroupLeader())) {
            candidates.add(rule.getGroupLeader());
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserName, candidates));
        List<String> missing = new ArrayList<>();
        for (String c : candidates) {
            boolean ok = users.stream().anyMatch(u -> c.equals(u.getUserName())
                    && "NORMAL".equalsIgnoreCase(u.getStatus()));
            if (!ok) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "派工规则「" + (rule.getRuleName() == null ? rule.getPaymentWay() : rule.getRuleName())
                            + "」配置的负责人不存在或已停用：" + String.join("、", missing)
                            + "；请先修正规则或改用人工派工");
        }
    }

    private String currentUserName() {
        LoginUser u = UserContext.get();
        if (u == null) {
            return "系统";
        }
        if (notBlank(u.getRealName())) {
            return u.getRealName();
        }
        return notBlank(u.getUsername()) ? u.getUsername() : "系统";
    }

    private ApServiceRequest require(Long id) {
        ApServiceRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务申请单不存在 id=" + id);
        }
        return r;
    }

    private void requireStatus(ApServiceRequest r, String expected, String action) {
        if (!expected.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务申请单 " + r.getBillNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + r.getStatus());
        }
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    /** 背书付款额一致性（发票合计由确认岗录入时联动校验） */
    @SuppressWarnings("unused")
    private void checkEndorseAmount(BigDecimal paymentAmount, BigDecimal invoiceTotal) {
        if (paymentAmount != null && invoiceTotal != null && paymentAmount.compareTo(invoiceTotal) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "背书票据付款金额必须与发票合计金额一致（付款额 "
                    + paymentAmount + " ≠ 发票合计 " + invoiceTotal + "）");
        }
    }

    static LocalDate dateOf(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return LocalDate.parse(String.valueOf(v).substring(0, 10));
    }
}
