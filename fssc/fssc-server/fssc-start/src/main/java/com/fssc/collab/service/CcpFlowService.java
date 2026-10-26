// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArPreInvoice;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArPreInvoiceMapper;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.collab.entity.CcpShipping;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.mapper.CcpShippingMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CCP 客户协同打通服务（AR 蓝图）：
 * 接口1 ERP发运数据推送（DB视图定时抓取，本处提供推送入口）→ 应收明细数据写入数据库；
 * 对账状态同步（CCP 对账状态与 AR 开票状态实时同步，链路步骤⑩）；
 * 接口2/3 发票信息回写 ERP（金税开票完成后自动回写，失败记日志人工处理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CcpFlowService {

    private final CcpShippingMapper shippingMapper;
    private final ArStatementMapper statementMapper;
    private final ArPreInvoiceMapper preInvoiceMapper;
    private final CollabSyncService syncService;

    /** 接口1：ERP 发运/系统发票数据推送写入数据库（定时抓取的模拟入口），失败即抛由调用方重试。 */
    @Transactional(rollbackFor = Exception.class)
    public CcpShipping pushShipping(CcpShipping s) {
        if (!StringUtils.hasText(s.getShippingNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发运单号必填");
        }
        if (s.getAmount() == null || s.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发运金额必须大于0");
        }
        Long dup = shippingMapper.selectCount(new LambdaQueryWrapper<CcpShipping>()
                .eq(CcpShipping::getShippingNo, s.getShippingNo()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发运单 " + s.getShippingNo() + " 已接收，勿重复推送");
        }
        s.setId(null);
        s.setTenantId(TenantContext.getTenantId());
        if (s.getStatus() == null) {
            s.setStatus(CcpShipping.ST_RECEIVED);
        }
        shippingMapper.insert(s);
        syncService.log(CollabSyncLog.IF_AR_SHIPPING, "EBS_TO_SHARED", s.getShippingNo(),
                CollabSyncLog.ST_SUCCESS,
                "发运数据接收：客户 " + s.getCustomerName() + "，金额 " + s.getAmount(), null);
        return s;
    }

    public List<CcpShipping> shippings(String status, String keyword) {
        LambdaQueryWrapper<CcpShipping> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(CcpShipping::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CcpShipping::getShippingNo, keyword)
                    .or().like(CcpShipping::getSoInvoiceNo, keyword)
                    .or().like(CcpShipping::getCustomerName, keyword));
        }
        return shippingMapper.selectList(q.orderByDesc(CcpShipping::getId));
    }

    /**
     * 对账状态同步（链路步骤⑩）：CCP 侧按对账单号查询 AR 侧对账/开票状态，
     * 写同步日志返回给 CCP；对账单不存在报错（CCP 凭证号关联键）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArStatement reconSync(String statementNo) {
        ArStatement st = statementMapper.selectOne(new LambdaQueryWrapper<ArStatement>()
                .eq(ArStatement::getBillNo, statementNo).last("LIMIT 1"));
        if (st == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账单不存在: " + statementNo);
        }
        syncService.log(CollabSyncLog.IF_AR_RECON_SYNC, "SHARED_TO_CCP", statementNo,
                CollabSyncLog.ST_SUCCESS,
                "对账状态=" + st.getStatus() + "，核对=" + st.getCheckStatus(), null);
        return st;
    }

    /**
     * 接口2/3 发票信息回写 ERP：对已开票（INVOICED）的预制发票补发回写
     * （正常链路由开票完成自动触发；本入口用于失败后的人工重推）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArPreInvoice invoiceWriteback(Long preInvoiceId) {
        ArPreInvoice inv = preInvoiceMapper.selectById(preInvoiceId);
        if (inv == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预制发票不存在 id=" + preInvoiceId);
        }
        if (!ArPreInvoice.ST_INVOICED.equals(inv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已开票(INVOICED)的预制发票可回写ERP，当前 " + inv.getStatus());
        }
        syncService.log(CollabSyncLog.IF_AR_INVOICE_WB, "SHARED_TO_EBS", inv.getPreInvoiceNo(),
                CollabSyncLog.ST_SUCCESS,
                "发票号 " + inv.getInvoiceNo() + "，开票日期 " + inv.getInvoiceDate(), null);
        return inv;
    }
}
