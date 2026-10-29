// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.MdComplianceRule;
import com.fssc.compliance.mapper.MdComplianceRuleMapper;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 合规规则配置服务。
 *
 * <p>统一维护四类合规规则（重复发票/费用项目不一致/超标/黑名单），
 * 供智能审单与合规预检引擎 {@code PrecheckEngineService} 引用。
 * 命中判定沿用预检脚本语法，规则可 online 启停而不改代码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdComplianceRuleService {

    private final MdComplianceRuleMapper ruleMapper;

    private static final String[] SUPPORTED_TYPES = {
            "DUPLICATE_INVOICE", "EXPENSE_MISMATCH", "OVER_STANDARD", "BLACKLIST"
    };

    // 内置规则以 tenant_id=NULL（平台级）播种，而 md_compliance_rule 不在租户拦截器
    // 忽略名单内，拦截器一律追加 tenant_id=当前租户 ⇒ 平台级种子恒被过滤、列表恒空。
    // 读侧改为「平台级 ∪ 本租户自定义」，写侧仍限本租户自有行（不跨租户改写共享定义）。
    public List<MdComplianceRule> list(String ruleType, String status) {
        Long tenantId = readableTenantId();
        List<MdComplianceRule> rows = new ArrayList<>();
        TenantContext.runIgnoreTenant(() -> rows.addAll(ruleMapper.selectList(visibleWrapper(tenantId)
                .eq(ruleType != null && !ruleType.isBlank(), MdComplianceRule::getRuleType, ruleType)
                .eq(status != null && !status.isBlank(), MdComplianceRule::getStatus, status)
                .orderByAsc(MdComplianceRule::getSortNo).orderByAsc(MdComplianceRule::getId))));
        return rows;
    }

    public MdComplianceRule getById(Long id) {
        MdComplianceRule rule = selectVisible(id);
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合规规则不存在: " + id);
        }
        return rule;
    }

    public MdComplianceRule findByCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return null;
        }
        Long tenantId = readableTenantId();
        MdComplianceRule[] hit = new MdComplianceRule[1];
        TenantContext.runIgnoreTenant(() -> hit[0] = ruleMapper.selectOne(visibleWrapper(tenantId)
                .eq(MdComplianceRule::getRuleCode, ruleCode).last("LIMIT 1")));
        return hit[0];
    }

    /** 当前身份可读规则对应的租户值：平台身份只读平台内置行，租户身份读 内置 ∪ 本租户 */
    private Long readableTenantId() {
        return TenantContext.isPlatform() ? null : TenantContext.getTenantId();
    }

    /** 当前租户可见 = 平台内置（tenant_id IS NULL）∪ 本租户自定义 */
    private LambdaQueryWrapper<MdComplianceRule> visibleWrapper(Long tenantId) {
        return new LambdaQueryWrapper<MdComplianceRule>()
                .and(w -> {
                    w.isNull(MdComplianceRule::getTenantId);
                    if (tenantId != null) {
                        w.or().eq(MdComplianceRule::getTenantId, tenantId);
                    }
                });
    }

    private MdComplianceRule selectVisible(Long id) {
        if (id == null) {
            return null;
        }
        Long tenantId = readableTenantId();
        MdComplianceRule[] hit = new MdComplianceRule[1];
        TenantContext.runIgnoreTenant(() -> hit[0] = ruleMapper.selectOne(visibleWrapper(tenantId)
                .eq(MdComplianceRule::getId, id).last("LIMIT 1")));
        return hit[0];
    }

    // 写侧只允许改本租户自有行；平台内置行可见但不可改，避免一处启停影响全部租户
    private MdComplianceRule requireOwnRow(Long id) {
        MdComplianceRule own = ruleMapper.selectById(id);
        if (own != null) {
            return own;
        }
        if (selectVisible(id) != null) {
            throw new BizException(ErrorCode.FORBIDDEN, "平台内置规则仅供查看，如需调整请新增本企业的规则");
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "合规规则不存在: " + id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MdComplianceRule rule) {
        validate(rule);
        if (findByCode(rule.getRuleCode()) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合规规则编码已存在: " + rule.getRuleCode());
        }
        if (rule.getStatus() == null || rule.getStatus().isBlank()) {
            rule.setStatus("ENABLED");
        }
        if (rule.getSortNo() == null) {
            rule.setSortNo(0);
        }
        if (rule.getTenantId() == null) {
            rule.setTenantId(TenantContext.getTenantId());
        }
        ruleMapper.insert(rule);
        log.info("创建合规规则: ruleCode={}, ruleType={}", rule.getRuleCode(), rule.getRuleType());
        return rule.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MdComplianceRule rule) {
        requireOwnRow(rule.getId());
        validate(rule);
        ruleMapper.updateById(rule);
        log.info("更新合规规则: id={}, ruleCode={}", rule.getId(), rule.getRuleCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, boolean enable) {
        requireOwnRow(id);
        MdComplianceRule upd = new MdComplianceRule();
        upd.setId(id);
        upd.setStatus(enable ? "ENABLED" : "DISABLED");
        ruleMapper.updateById(upd);
        log.info("{}合规规则: id={}", enable ? "启用" : "停用", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MdComplianceRule exist = requireOwnRow(id);
        ruleMapper.deleteById(id);
        log.info("删除合规规则: id={}, ruleCode={}", id, exist.getRuleCode());
    }

    /** 启用中的全部可见规则（平台内置 ∪ 本租户），供预检/审单引擎加载 */
    public List<MdComplianceRule> listEnabled() {
        Long tenantId = readableTenantId();
        List<MdComplianceRule> rows = new ArrayList<>();
        TenantContext.runIgnoreTenant(() -> rows.addAll(ruleMapper.selectList(visibleWrapper(tenantId)
                .eq(MdComplianceRule::getStatus, "ENABLED")
                .orderByAsc(MdComplianceRule::getSortNo))));
        return rows;
    }

    private void validate(MdComplianceRule rule) {
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则编码不能为空");
        }
        if (rule.getRuleName() == null || rule.getRuleName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则名称不能为空");
        }
        if (rule.getRuleType() == null || rule.getRuleType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则类别不能为空");
        }
        boolean supported = false;
        for (String t : SUPPORTED_TYPES) {
            if (t.equals(rule.getRuleType())) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的规则类别: " + rule.getRuleType());
        }
    }
}