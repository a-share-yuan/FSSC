// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.mapper.ExpensePolicyStandardMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePolicyStandardService {

    private final ExpensePolicyStandardMapper expensePolicyStandardMapper;

    public List<ExpensePolicyStandard> listByPolicy(Long policyId) {
        LambdaQueryWrapper<ExpensePolicyStandard> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpensePolicyStandard::getPolicyId, policyId);
        return expensePolicyStandardMapper.selectList(qw);
    }

    public ExpensePolicyStandard getById(Long id) {
        ExpensePolicyStandard standard = expensePolicyStandardMapper.selectById(id);
        if (standard == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策标准不存在: " + id);
        }
        return standard;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExpensePolicyStandard standard) {
        // 原实现是裸 insert，零校验。两个后果：
        // ① standard_unit 是 NOT NULL 列，缺它 → SQL 异常 → HTTP 500「系统异常」，调用方无从判断是参数问题；
        // ② 标准下限 > 上限也能写入数据库（手册 G16.13 要求提示「该标准将永远无法命中」），
        //    写入数据库后规则匹配永远不命中，属静默失效——比直接报错更难排查。
        if (standard.getPolicyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择所属费用政策");
        }
        if (standard.getStandardType() == null || standard.getStandardType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择标准类型（金额/限额/比例）");
        }
        if (standard.getStandardKey() == null || standard.getStandardKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写标准项标识");
        }
        if (standard.getStandardUnit() == null || standard.getStandardUnit().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写单位（如 元/天/次/人）");
        }
        BigDecimal min = standard.getStandardValueMin();
        BigDecimal max = standard.getStandardValueMax();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "下限大于上限，该标准将永远无法命中：下限 " + min.stripTrailingZeros().toPlainString()
                            + " ＞ 上限 " + max.stripTrailingZeros().toPlainString() + "，请调整后重填");
        }
        expensePolicyStandardMapper.insert(standard);
        log.info("创建政策标准: policyId={}, standardKey={}", standard.getPolicyId(), standard.getStandardKey());
        return standard.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ExpensePolicyStandard standard) {
        ExpensePolicyStandard exist = expensePolicyStandardMapper.selectById(standard.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策标准不存在: " + standard.getId());
        }
        // update 与 create 同一套校验，否则「先建后改」能绕过下限≤上限的校验。
        if (standard.getStandardUnit() == null || standard.getStandardUnit().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写单位（如 元/天/次/人）");
        }
        BigDecimal min = standard.getStandardValueMin();
        BigDecimal max = standard.getStandardValueMax();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "下限大于上限，该标准将永远无法命中：下限 " + min.stripTrailingZeros().toPlainString()
                            + " ＞ 上限 " + max.stripTrailingZeros().toPlainString() + "，请调整后重填");
        }
        expensePolicyStandardMapper.updateById(standard);
        log.info("更新政策标准: id={}", standard.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExpensePolicyStandard standard = expensePolicyStandardMapper.selectById(id);
        if (standard == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策标准不存在: " + id);
        }
        expensePolicyStandardMapper.deleteById(id);
        log.info("删除政策标准: id={}", id);
    }
}
