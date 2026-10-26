// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlConsolidate;
import com.fssc.glex.mapper.GlConsolidateMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 合并报表底稿服务（gl_consolidate，多公司报表底稿创建/汇总/调整）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlConsolidateService {

    private final GlConsolidateMapper mapper;
    private final WfEngineFacade wfEngineFacade;

    public PageResult<GlConsolidate> page(long pageNum, long pageSize, String period, String status, String keyword) {
        LambdaQueryWrapper<GlConsolidate> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlConsolidate::getPeriod, period);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(GlConsolidate::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlConsolidate::getReportNo, keyword).or().like(GlConsolidate::getCompanyIds, keyword));
        }
        qw.orderByDesc(GlConsolidate::getId);
        Page<GlConsolidate> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * D-GL-04：会计期间归一化为 {@code yyyyMM}（底表列宽只有 6 位），容忍 {@code yyyy-MM} 输入。
     * <p>月份越界/格式非法一律 400，绝不放行到 INSERT 变成 500。
     */
    private String normalizePeriod(String period) {
        String s = period.trim();
        if (s.matches("\\d{4}-\\d{2}")) {
            s = s.replace("-", "");
        }
        if (!s.matches("\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "会计期间格式不正确：" + period + "（应为 yyyyMM 如 202610，或 yyyy-MM 如 2026-10）");
        }
        int mm = Integer.parseInt(s.substring(4));
        if (mm < 1 || mm > 12) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间月份非法：" + s.substring(4) + "（应在 01~12）");
        }
        return s;
    }

    /** 创建合并底稿（DRAFT） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(GlConsolidate e) {
        if (!StringUtils.hasText(e.getPeriod())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        // D-GL-04：gl_consolidate.period 是 varchar(6)=yyyyMM，传 yyyy-MM 会在 INSERT 时
        // 触发 "Data too long" 的 SQL 异常，被全局处理器包成 HTTP 500「系统异常」——
        // 调用方只会当系统故障排查。先归一化，非法格式显式 400。
        e.setPeriod(normalizePeriod(e.getPeriod()));
        e.setId(null);
        e.setTenantId(TenantContext.getTenantId());
        e.setReportNo(StringUtils.hasText(e.getReportNo())
                ? e.getReportNo()
                : "CR" + e.getPeriod() + String.format("%03d", countByPeriod(e.getPeriod()) + 1));
        e.setStatus("DRAFT");
        mapper.insert(e);
        return e.getId();
    }

    /** 按期间汇总合并：合并公司的底稿行，置 CONSOLIDATED。 */
    @Transactional(rollbackFor = Exception.class)
    public void consolidate(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        List<GlConsolidate> list = mapper.selectList(
                new LambdaQueryWrapper<GlConsolidate>().eq(GlConsolidate::getPeriod, period));
        boolean merged = false;
        for (GlConsolidate c : list) {
            if ("CONSOLIDATED".equals(c.getStatus())) {
                continue;
            }
            c.setStatus("CONSOLIDATED");
            mapper.updateById(c);
            merged = true;
        }
        if (!merged && list.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该期间无可合并的底稿: " + period);
        }
        // 接线 GL9 GL_MERGE 合并报表审批：汇总后有底稿置 CONSOLIDATED，即发起合并报表审批流
        if (merged) {
            Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
            try {
                wfEngineFacade.start("GL_MERGE", list.get(0).getId(), userId,
                        "合并报表-" + period, null);
            } catch (Exception ex) {
                log.error("合并报表 {} 发起审批失败: {}", period, ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
    }

    /** 调整分录：更新底稿 rawRows（合并抵消调整） */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long id, String rawRows) {
        GlConsolidate c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "底稿不存在: " + id);
        }
        c.setRawRows(rawRows);
        mapper.updateById(c);
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlConsolidate>().eq(GlConsolidate::getPeriod, period));
    }
}