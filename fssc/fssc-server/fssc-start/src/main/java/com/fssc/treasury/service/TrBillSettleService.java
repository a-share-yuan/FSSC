// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.treasury.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.treasury.entity.TrBillSettle;
import com.fssc.treasury.mapper.TrBillSettleMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 票据结算服务。
 * 在库(STOCK)票据可背书转让(endorse)、贴现(discount)、到期托收收妥(collection)。
 * 票据登记即发起 TR_BILL 票据结算审批流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrBillSettleService {

    private final TrBillSettleMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;

    /** 票据登记入库（STOCK） */
    public Long create(TrBillSettle settle) {
        if (!StringUtils.hasText(settle.getTicketNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据号不能为空");
        }
        if (settle.getStatus() == null) settle.setStatus("STOCK");
        if (settle.getEndorseFlag() == null) settle.setEndorseFlag(Boolean.FALSE);
        // 整改(20260917, DEV-008/VER-016)：创建即生成票据单号（口径：单据创建即有业务主键）。
        if (!StringUtils.hasText(settle.getBillNo())) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long seq = mapper.selectCount(new LambdaQueryWrapper<TrBillSettle>()
                    .likeRight(TrBillSettle::getBillNo, "PJ" + date)) + 1;
            settle.setBillNo(String.format("PJ%s%04d", date, seq));
        }
        settle.setId(null);
        settle.setTenantId(TenantContext.getTenantId());
        if (settle.getCompanyId() == null || settle.getCompanyId() <= 0) {
            settle.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(settle.getCompanyId());
        mapper.insert(settle);
        // 票据登记入库即发起 TR_BILL 票据结算审批流（出纳登记→资金岗复核→财务经理审批）
        // 传入票面金额（TR_BILL 当前无金额网关 ⇒ 只影响实例金额展示/统计，不改路由）
        try {
            wfEngineFacade.start("TR_BILL", settle.getId(), userId(),
                    "票据结算-" + settle.getTicketNo(), settle.getAmount());
        } catch (Exception ex) {
            log.error("票据 {} 发起结算审批失败: {}", settle.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
        }
        return settle.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 背书转让：在库票据转让给下家，状态 -> NEGOSIATED */
    @Transactional
    public void endorse(Long id) {
        TrBillSettle settle = requireExist(id);
        requireInStock(settle, "背书转让");
        settle.setStatus("NEGOSIATED");
        settle.setEndorseFlag(Boolean.TRUE);
        mapper.updateById(settle);
    }

    /** 贴现：在库票据到期前贴现，状态 -> DISCOUNTED */
    @Transactional
    public void discount(Long id) {
        TrBillSettle settle = requireExist(id);
        requireInStock(settle, "贴现");
        settle.setStatus("DISCOUNTED");
        mapper.updateById(settle);
    }

    /** 托收到期收妥：在库票据托收并收妥款，状态 -> COLLECTION */
    @Transactional
    public void collection(Long id) {
        TrBillSettle settle = requireExist(id);
        requireInStock(settle, "托收到期收妥");
        settle.setStatus("COLLECTION");
        mapper.updateById(settle);
    }

    public TrBillSettle detail(Long id) {
        return requireExist(id);
    }

    public PageResult<TrBillSettle> page(long pageNum, long pageSize, String status) {
        LambdaQueryWrapper<TrBillSettle> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) qw.eq(TrBillSettle::getStatus, status);
        qw.orderByDesc(TrBillSettle::getId);
        Page<TrBillSettle> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    private void requireInStock(TrBillSettle settle, String action) {
        if (!"STOCK".equals(settle.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅" + action + ",票据需处于在库状态");
        }
    }

    private TrBillSettle requireExist(Long id) {
        TrBillSettle settle = mapper.selectById(id);
        if (settle == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据不存在：" + id);
        }
        return settle;
    }
}