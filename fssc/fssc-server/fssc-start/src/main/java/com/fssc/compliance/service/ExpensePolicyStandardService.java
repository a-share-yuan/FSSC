package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.mapper.ExpensePolicyStandardMapper;
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
