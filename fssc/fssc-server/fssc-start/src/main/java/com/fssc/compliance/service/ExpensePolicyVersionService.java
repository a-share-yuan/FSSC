package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ExpensePolicyVersion;
import com.fssc.compliance.mapper.ExpensePolicyVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePolicyVersionService {

    private final ExpensePolicyVersionMapper expensePolicyVersionMapper;

    public List<ExpensePolicyVersion> listByPolicy(Long policyId) {
        LambdaQueryWrapper<ExpensePolicyVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpensePolicyVersion::getPolicyId, policyId)
                .orderByDesc(ExpensePolicyVersion::getCreateTime);
        return expensePolicyVersionMapper.selectList(qw);
    }

    public ExpensePolicyVersion getById(Long id) {
        ExpensePolicyVersion version = expensePolicyVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策版本不存在: " + id);
        }
        return version;
    }

    public ExpensePolicyVersion getLatest(Long policyId) {
        LambdaQueryWrapper<ExpensePolicyVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpensePolicyVersion::getPolicyId, policyId)
                .orderByDesc(ExpensePolicyVersion::getCreateTime)
                .last("limit 1");
        return expensePolicyVersionMapper.selectOne(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExpensePolicyVersion version) {
        if (version.getStatus() == null) {
            version.setStatus("DRAFT");
        }
        if (version.getVersion() == null || version.getVersion().isEmpty()) {
            String maxVersion = getMaxVersion(version.getPolicyId());
            version.setVersion(maxVersion);
        }
        if (version.getPolicyId() == null) {
            version.setPolicyId(0L);
        }
        if (version.getSnapshotData() == null || version.getSnapshotData().isEmpty()) {
            version.setSnapshotData("{}");
        }
        expensePolicyVersionMapper.insert(version);
        log.info("创建政策版本: policyId={}, version={}", version.getPolicyId(), version.getVersion());
        return version.getId();
    }

    private String getMaxVersion(Long policyId) {
        LambdaQueryWrapper<ExpensePolicyVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpensePolicyVersion::getPolicyId, policyId)
                .orderByDesc(ExpensePolicyVersion::getCreateTime)
                .last("limit 1");
        ExpensePolicyVersion latest = expensePolicyVersionMapper.selectOne(qw);
        if (latest != null && latest.getVersion() != null) {
            return latest.getVersion();
        }
        return "V1.0";
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long approverId) {
        ExpensePolicyVersion version = expensePolicyVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策版本不存在: " + id);
        }
        version.setStatus("APPROVED");
        version.setApprovedBy(approverId);
        version.setApprovedAt(java.time.LocalDateTime.now());
        expensePolicyVersionMapper.updateById(version);
        log.info("审批政策版本: id={}, approverId={}", id, approverId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        ExpensePolicyVersion version = expensePolicyVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策版本不存在: " + id);
        }
        version.setStatus("ACTIVATED");
        expensePolicyVersionMapper.updateById(version);
        log.info("激活政策版本: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        ExpensePolicyVersion version = expensePolicyVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策版本不存在: " + id);
        }
        version.setStatus("ARCHIVED");
        expensePolicyVersionMapper.updateById(version);
        log.info("归档政策版本: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExpensePolicyVersion version = expensePolicyVersionMapper.selectById(id);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "政策版本不存在: " + id);
        }
        expensePolicyVersionMapper.deleteById(id);
        log.info("删除政策版本: id={}", id);
    }
}
