// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.match.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.match.entity.MatchTolerance;
import com.fssc.match.mapper.MatchToleranceMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 三单匹配容差参数服务：收货金额 vs 发票金额的容差（百分比 + 绝对值）参数化。
 * <ul>
 *  <li>{@link #getTolerance(Long)}：按公司查 match_tolerance；无配置 / 停用 / 读取异常 →
 *      返回默认参数（0.5% / 100 元），保证匹配引擎永远拿到可用容差；</li>
 *  <li>{@link #allowedOver(MatchTolerance, BigDecimal)}：容差金额 =
 *      max(基准金额 × amount_pct / 100, amount_abs)（百分比与绝对值双轨取大，满足其一即在容差内）；</li>
 *  <li>{@link #upsert(MatchTolerance)}：按 租户+公司 upsert（供 POST /api/tenant/match-tolerance）。</li>
 * </ul>
 * tenant_id 过滤由框架 FsscTenantLineHandler 自动追加，查询无需手工条件；INSERT 显式填充租户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchToleranceService {

    private final MatchToleranceMapper toleranceMapper;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 默认金额容差百分比：0.5 = 0.5% */
    public static final BigDecimal DEFAULT_AMOUNT_PCT = new BigDecimal("0.5");
    /** 默认金额容差绝对值：100 元 */
    public static final BigDecimal DEFAULT_AMOUNT_ABS = new BigDecimal("100");

    /** 状态：启用 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 状态：停用（停用后按默认参数处理） */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 按公司查容差参数。companyId 为空时回退当前登录用户所属公司；仍为空、无配置、
     * 配置停用或读取异常时，返回默认参数（amountPct=0.5、amountAbs=100，id 为空表示默认值未写入数据库）。
     */
    public MatchTolerance getTolerance(Long companyId) {
        Long cid = companyId;
        if (cid == null) {
            LoginUser u = UserContext.get();
            cid = u == null ? null : u.getCompanyId();
        }
        if (cid == null) {
            return defaultTolerance(null);
        }
        try {
            MatchTolerance t = toleranceMapper.selectOne(new LambdaQueryWrapper<MatchTolerance>()
                    .eq(MatchTolerance::getCompanyId, cid)
                    .orderByDesc(MatchTolerance::getUpdateTime)
                    .orderByDesc(MatchTolerance::getId)
                    .last("limit 1"));
            if (t == null || !STATUS_NORMAL.equals(t.getStatus())) {
                // 无配置或停用 → 默认 0.5% / 100 元
                return defaultTolerance(cid);
            }
            fillDefaultIfBlank(t);
            return t;
        } catch (Exception ex) {
            log.error(" 读取匹配容差参数失败 companyId={}，按默认(0.5%/100元)处理: {}", cid, ex.getMessage(), ex);
            return defaultTolerance(cid);
        }
    }

    /**
     * 容差金额：max(基准金额 × amount_pct / 100, amount_abs)。
     * t 为空时按默认参数计算；基准金额为空按 0。
     */
    public BigDecimal allowedOver(MatchTolerance t, BigDecimal base) {
        BigDecimal pct = nz(t == null ? null : t.getAmountPct(), DEFAULT_AMOUNT_PCT);
        BigDecimal abs = nz(t == null ? null : t.getAmountAbs(), DEFAULT_AMOUNT_ABS);
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal pctAmt = b.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return pctAmt.max(abs);
    }

    /**
     * 容差内判定：差异(over) ≤ 容差金额（max(百分比, 绝对值)）即容差内。
     */
    public boolean withinTolerance(MatchTolerance t, BigDecimal base, BigDecimal over) {
        BigDecimal o = over == null ? BigDecimal.ZERO : over;
        return allowedOver(t, base).subtract(o).compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 按租户+公司 upsert 容差参数（POST /api/tenant/match-tolerance，唯一调用方是页面保存，无机器写入路径）。
     * 必填守卫（批次 2B，与前端 MATCH_TOLERANCE 登记表同一规则）：公司/百分比/绝对值三项都是参数本身，
     * 原实现把空值静默补成默认 0.5%/100 元——用户没填却写入数据库一条"看起来填过"的参数，故改为直接拒绝。
     * 读取侧 {@link #getTolerance(Long)} 的默认兜底保留（匹配引擎必须拿到可用容差）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MatchTolerance upsert(MatchTolerance in) {
        if (in == null || in.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择公司");
        }
        if (in.getAmountPct() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写金额容差百分比");
        }
        if (in.getAmountAbs() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写绝对值容差");
        }
        if (in.getAmountPct().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额容差百分比不能为负数");
        }
        if (in.getAmountAbs().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "绝对值容差不能为负数");
        }
        if (in.getStatus() != null && !STATUS_NORMAL.equals(in.getStatus()) && !STATUS_DISABLED.equals(in.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 仅支持 NORMAL/DISABLED");
        }
        MatchTolerance exist = toleranceMapper.selectOne(new LambdaQueryWrapper<MatchTolerance>()
                .eq(MatchTolerance::getCompanyId, in.getCompanyId())
                .orderByDesc(MatchTolerance::getUpdateTime)
                .orderByDesc(MatchTolerance::getId)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            in.setId(null);
            if (in.getTenantId() == null) {
                in.setTenantId(TenantContext.getTenantId());
            }
            if (in.getStatus() == null || in.getStatus().isBlank()) {
                in.setStatus(STATUS_NORMAL);
            }
            in.setCreateTime(now);
            in.setUpdateTime(now);
            toleranceMapper.insert(in);
            log.info(" 新增三单匹配容差参数 companyId={} pct={} abs={} status={}",
                    in.getCompanyId(), in.getAmountPct(), in.getAmountAbs(), in.getStatus());
            return in;
        }
        // exist 即将被就地覆盖，更新前先采集旧值（状态/百分比/绝对值）
        String prevStatus = exist.getStatus();
        BigDecimal prevAmountPct = exist.getAmountPct();
        BigDecimal prevAmountAbs = exist.getAmountAbs();
        exist.setAmountPct(in.getAmountPct());
        exist.setAmountAbs(in.getAmountAbs());
        if (in.getStatus() != null && !in.getStatus().isBlank()) {
            exist.setStatus(in.getStatus());
        }
        exist.setUpdateTime(now);
        toleranceMapper.updateById(exist);
        log.info(" 更新三单匹配容差参数 companyId={} pct={} abs={} status={}",
                exist.getCompanyId(), exist.getAmountPct(), exist.getAmountAbs(), exist.getStatus());
        // 审计记录：容差参数变更（状态/百分比/绝对值）
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", prevStatus);
        auditBefore.put("amountPct", prevAmountPct);
        auditBefore.put("amountAbs", prevAmountAbs);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", exist.getStatus());
        auditAfter.put("amountPct", exist.getAmountPct());
        auditAfter.put("amountAbs", exist.getAmountAbs());
        auditTrailService.recordUpdate("MATCH", exist.getId(), null, "match_tolerance",
                String.valueOf(exist.getId()), auditBefore, auditAfter, "更新三单匹配容差参数");
        return exist;
    }

    /** 默认容差参数（未写入数据库，仅内存表示）：0.5% / 100 元 */
    private MatchTolerance defaultTolerance(Long companyId) {
        MatchTolerance t = new MatchTolerance();
        t.setCompanyId(companyId);
        t.setAmountPct(DEFAULT_AMOUNT_PCT);
        t.setAmountAbs(DEFAULT_AMOUNT_ABS);
        t.setStatus(STATUS_NORMAL);
        return t;
    }

    /** 空值填默认：amountPct→0.5，amountAbs→100 */
    private void fillDefaultIfBlank(MatchTolerance t) {
        if (t.getAmountPct() == null) t.setAmountPct(DEFAULT_AMOUNT_PCT);
        if (t.getAmountAbs() == null) t.setAmountAbs(DEFAULT_AMOUNT_ABS);
    }

    private BigDecimal nz(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }
}
