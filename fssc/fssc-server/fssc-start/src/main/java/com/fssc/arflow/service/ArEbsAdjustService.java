// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArEbsAdjust;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArEbsAdjustMapper;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.service.CollabSyncService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * AR 接口 2/4 服务（蓝图 7.1/7.2）：
 * 接口2 财务调整数据推送（ERP→协同，7.2接口6）：折扣/索赔/溢交调整后推送，
 *       写入数据库并自动重核关联对账单（对账异常→重核→通过自动 RECONCILED，应收调整单做法）；
 * 接口4 负数发票推送（ERP→协同，7.2接口7）：ERP 贷项凭证后推负数发票数据，
 *       生成红字前置（red_pending=1，贷项单→红字申请范式），人工补红字通知单号后走既有 /red。
 * 去重：adjust_no / ebs_credit_memo_no；日志：AR_IF06 / AR_IF07（不随业务事务回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArEbsAdjustService {

    private final ArEbsAdjustMapper adjustMapper;
    private final ArStatementMapper statementMapper;
    private final ArPreInvoiceMapper preInvoiceMapper;
    private final ArStatementService statementService;
    private final CollabSyncService syncService;

    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 接口2：ERP 财务调整推送。body 见 ar_ebs_adjust。 */
    @Transactional(rollbackFor = Exception.class)
    public ArEbsAdjust pushAdjust(Map<String, Object> body) {
        String adjustNo = str(body.get("adjustNo"));
        if (!StringUtils.hasText(adjustNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ERP调整单号必填（唯一标识）");
        }
        Long dup = adjustMapper.selectCount(new LambdaQueryWrapper<ArEbsAdjust>()
                .eq(ArEbsAdjust::getAdjustNo, adjustNo));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单 " + adjustNo + " 已推送，请勿重复");
        }
        ArEbsAdjust a = new ArEbsAdjust();
        a.setAdjustNo(adjustNo);
        a.setCustomerCode(str(body.get("customerCode")));
        a.setAdjustType(str(body.get("adjustType")));
        a.setEbsVoucherNo(str(body.get("ebsVoucherNo")));
        a.setStatementNo(str(body.get("statementNo")));
        a.setStatus(ArEbsAdjust.ST_RECEIVED);
        if (body.get("amount") != null) {
            a.setAmount(new BigDecimal(String.valueOf(body.get("amount"))));
        }
        if (body.get("taxAmount") != null) {
            a.setTaxAmount(new BigDecimal(String.valueOf(body.get("taxAmount"))));
        }
        adjustMapper.insert(a);
        syncService.log(CollabSyncLog.IF_AR_RECON_SYNC, "EBS_TO_SHARED", adjustNo,
                CollabSyncLog.ST_SUCCESS, "【AR接口2】财务调整推送：" + a.getAdjustType() + " " + a.getAmount(), null);

        // 自动重核：关联对账单处于对账异常时，按蓝图"调整后数据推送至协同平台重新核对"
        if (StringUtils.hasText(a.getStatementNo())) {
            ArStatement st = statementMapper.selectOne(new LambdaQueryWrapper<ArStatement>()
                    .eq(ArStatement::getBillNo, a.getStatementNo()).last("LIMIT 1"));
            if (st == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "关联对账单不存在: " + a.getStatementNo());
            }
            if (ArStatement.ST_ABNORMAL.equals(st.getStatus())) {
                statementService.check(st.getId());
                String auditOldStatus = a.getStatus();
                a.setStatus(ArEbsAdjust.ST_APPLIED);
                adjustMapper.updateById(a);
                auditTrailService.recordUpdate("AR_EBS_ADJUST", a.getId(), a.getAdjustNo(), "ar_ebs_adjust",
                        String.valueOf(a.getId()), Collections.singletonMap("status", auditOldStatus),
                        Collections.singletonMap("status", a.getStatus()), "关联对账单异常已自动重核");
            }
        }
        return a;
    }

    public List<ArEbsAdjust> adjusts(String customerCode) {
        LambdaQueryWrapper<ArEbsAdjust> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerCode)) {
            q.eq(ArEbsAdjust::getCustomerCode, customerCode);
        }
        return adjustMapper.selectList(q.orderByDesc(ArEbsAdjust::getId));
    }

    /**
     * 接口4：ERP 负数发票推送（红字来源）。body {ebsCreditMemoNo, preInvoiceNo|invoiceNo}。
     * 置 red_pending=1（待补红字通知单号），人工补录后走既有 /red 推金税。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice pushRedInvoice(Map<String, Object> body) {
        String memoNo = str(body.get("ebsCreditMemoNo"));
        if (!StringUtils.hasText(memoNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ERP贷项凭证号必填（唯一标识）");
        }
        String preInvoiceNo = str(body.get("preInvoiceNo"));
        String invoiceNo = str(body.get("invoiceNo"));
        ArPreInvoice inv = preInvoiceMapper.selectOne(new LambdaQueryWrapper<ArPreInvoice>()
                .eq(StringUtils.hasText(preInvoiceNo), ArPreInvoice::getPreInvoiceNo, preInvoiceNo)
                .eq(!StringUtils.hasText(preInvoiceNo) && StringUtils.hasText(invoiceNo), ArPreInvoice::getInvoiceNo, invoiceNo)
                .last("LIMIT 1"));
        if (inv == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票不存在（preInvoiceNo/invoiceNo 二选一）");
        }
        if (Integer.valueOf(1).equals(inv.getRedPending())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该发票已接收负数推送，请勿重复");
        }
        if (!ArPreInvoice.ST_INVOICED.equals(inv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已开票(INVOICED)发票可接收负数推送，当前 " + inv.getStatus());
        }
        ArPreInvoice u = new ArPreInvoice();
        u.setId(inv.getId());
        u.setRedPending(1);
        u.setRemark((inv.getRemark() == null ? "" : inv.getRemark() + " | ") + "ERP贷项凭证 " + memoNo);
        preInvoiceMapper.updateById(u);
        syncService.log(CollabSyncLog.IF_AR_INVOICE_WB, "EBS_TO_SHARED", inv.getPreInvoiceNo(),
                CollabSyncLog.ST_SUCCESS, "【AR接口4】负数发票推送（红字来源就绪，待补红字通知单号）：" + memoNo, null);
        return preInvoiceMapper.selectById(inv.getId());
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
