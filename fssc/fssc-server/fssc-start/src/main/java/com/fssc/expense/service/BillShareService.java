// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.expense.entity.ExpBillShare;
import com.fssc.expense.mapper.ExpBillShareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用分摊（exp_bill_share，带 tenant_id）。
 * 跨部门/公司/项目分摊：同一单据多行分摊，金额合计需与单据金额一致（saveDraft 时校验由调用方决定）。
 */
@Service
@RequiredArgsConstructor
public class BillShareService {

    private final ExpBillShareMapper shareMapper;

    public PageResult<ExpBillShare> page(long pageNum, long pageSize, Long billId) {
        LambdaQueryWrapper<ExpBillShare> qw = new LambdaQueryWrapper<>();
        if (billId != null) {
            qw.eq(ExpBillShare::getBillId, billId);
        }
        qw.orderByAsc(ExpBillShare::getId);
        Page<ExpBillShare> p = shareMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 某单据全部分摊行 */
    public List<ExpBillShare> listByBill(Long billId) {
        return shareMapper.selectList(new LambdaQueryWrapper<ExpBillShare>()
                .eq(ExpBillShare::getBillId, billId).orderByAsc(ExpBillShare::getId));
    }

    public Long create(ExpBillShare share) {
        if (share.getBillId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据ID必填");
        }
        if (share.getShareAmount() == null || share.getShareAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分摊金额必须大于 0");
        }
        share.setId(null);
        shareMapper.insert(share);
        return share.getId();
    }

    /** 整单分摊覆盖保存：删旧插新 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceForBill(Long billId, List<ExpBillShare> shares) {
        shareMapper.delete(new LambdaQueryWrapper<ExpBillShare>().eq(ExpBillShare::getBillId, billId));
        if (shares != null) {
            for (ExpBillShare s : shares) {
                s.setId(null);
                s.setBillId(billId);
                shareMapper.insert(s);
            }
        }
    }

    public void delete(Long id) {
        shareMapper.deleteById(id);
    }
}
