// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfProcessConfig;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfProcessConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** 单据类型流程绑定配置（wf_process_config，带 tenant_id）。 */
@Service
@RequiredArgsConstructor
public class WfProcessConfigService {

    private final WfProcessConfigMapper configMapper;
    private final WfModelMapper modelMapper;

    public PageResult<WfProcessConfig> page(long pageNum, long pageSize, String bizType, String keyword) {
        LambdaQueryWrapper<WfProcessConfig> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(WfProcessConfig::getBizType, bizType);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(WfProcessConfig::getConfigName, keyword);
        }
        qw.orderByDesc(WfProcessConfig::getPriority);
        Page<WfProcessConfig> p = configMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(WfProcessConfig cfg) {
        validate(cfg);
        cfg.setId(null);
        if (cfg.getPriority() == null) {
            cfg.setPriority(0);
        }
        if (cfg.getStatus() == null) {
            cfg.setStatus("NORMAL");
        }
        configMapper.insert(cfg);
        return cfg.getId();
    }

    public void update(WfProcessConfig cfg) {
        if (configMapper.selectById(cfg.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "配置不存在: " + cfg.getId());
        }
        validate(cfg);
        configMapper.updateById(cfg);
    }

    /**
     * 必填收口（批次 2C，与前端 FlowConfig「流程绑定」/ GenericList 单据类型配置弹窗同口径）：
     * bizType 是路由入口、modelKey 是命中的流程，留空即「有规则但永远解析不出流程」。
     */
    private void validate(WfProcessConfig cfg) {
        AssertUtil.notBlank(cfg.getBizType(), ErrorCode.PARAM_INVALID, "请选择单据类型");
        AssertUtil.notBlank(cfg.getConfigName(), ErrorCode.PARAM_INVALID, "请输入配置名称");
        AssertUtil.notBlank(cfg.getModelKey(), ErrorCode.PARAM_INVALID, "请选择绑定流程模型");
    }

    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    /**
     * 按单据类型路由流程定义 key（禁止硬编码 processKey）。
     * 同 biz_type 多条规则：子类型精确命中 > 通配（sub_type 为空），同级按 priority 从大到小，取第一条金额区间命中的。
     */
    public String resolveModelKey(String bizType, BigDecimal amount, Long companyId, Long deptId) {
        return resolveModelKey(bizType, null, amount, companyId, deptId);
    }

    /** V1.3.19 子类型感知路由：subType 非空时优先命中 sub_type 精确规则，其次通配规则 */
    public String resolveModelKey(String bizType, String subType, BigDecimal amount, Long companyId, Long deptId) {
        List<WfProcessConfig> rules = configMapper.selectList(new LambdaQueryWrapper<WfProcessConfig>()
                .eq(WfProcessConfig::getBizType, bizType)
                .eq(WfProcessConfig::getStatus, "NORMAL")
                .orderByDesc(WfProcessConfig::getPriority));
        if (rules.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未配置流程规则: " + bizType);
        }
        // 排序：子类型精确命中优先于通配（sub_type 为空的规则兜底），同级保持 priority 降序
        List<WfProcessConfig> ordered = new java.util.ArrayList<>(rules);
        ordered.sort(java.util.Comparator.comparingInt(
                r -> (subType != null && !subType.isBlank() && subType.equalsIgnoreCase(r.getSubType())) ? 0 : 1));
        for (WfProcessConfig r : ordered) {
            // 子类型不匹配的规则直接排除（规则声明了 sub_type 就必须等于单据子类型；空=通配）
            if (r.getSubType() != null && !r.getSubType().isBlank()
                    && !r.getSubType().equalsIgnoreCase(subType == null ? "" : subType)) {
                continue;
            }
            if (r.getCompanyId() != null && !r.getCompanyId().equals(companyId)) {
                continue;
            }
            if (r.getDeptId() != null && !r.getDeptId().equals(deptId)) {
                continue;
            }
            if (amount != null) {
                if (r.getAmountMin() != null && amount.compareTo(r.getAmountMin()) < 0) {
                    continue;
                }
                if (r.getAmountMax() != null && amount.compareTo(r.getAmountMax()) >= 0) {
                    continue;
                }
            }
            // UC01 组织范围过滤：模型限定指定公司(ORG)时，当前公司必须命中 org_ids
            if (!orgScopeMatches(r.getModelKey(), companyId)) {
                continue;
            }
            return r.getModelKey();
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "无命中的流程规则: " + bizType);
    }

    /** 校验流程模型组织范围：排除组织命中→不适用；ORG→公司须在 org_ids 内（companyId 为空视为不适用，fail-closed）；ALL/未配置放行 */
    private boolean orgScopeMatches(String modelKey, Long companyId) {
        if (modelKey == null || modelKey.isBlank()) return true;
        WfModel m = modelMapper.selectOne(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, modelKey).last("limit 1"));
        if (m == null) return true;
        // UC07 排除组织：命中排除列表 → 不适用（即使 ORG 限定范围内）
        if (companyId != null && containsId(m.getExcludeOrgIds(), companyId)) {
            return false;
        }
        if (m.getOrgScope() == null || !"ORG".equals(m.getOrgScope())) {
            return true; // 默认全集团
        }
        String ids = m.getOrgIds();
        if (ids == null || ids.isBlank()) return true; // ORG 但未选公司 → 视同全集团
        if (companyId == null) return false; // 无法判断归属 → 不适用
        return containsId(ids, companyId);
    }

    /** 逗号分隔 ID 列表中是否包含指定 ID */
    private boolean containsId(String csv, Long id) {
        if (csv == null || csv.isBlank()) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(s -> Long.toString(id).equals(s));
    }
}
