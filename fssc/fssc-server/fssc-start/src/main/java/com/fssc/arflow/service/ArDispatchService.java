// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArClaim;
import com.fssc.arflow.entity.ArDispatchRule;
import com.fssc.arflow.mapper.ArClaimMapper;
import com.fssc.arflow.mapper.ArDispatchRuleMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.framework.security.UserContext;

/**
 * AR 自动派工服务（蓝图 5.2，共享任务池/派工规则范式）：
 * 规则命中：customer_prefix 最长前缀 → claim_type 精确 → priority；
 * 命中后：单人直派（可按在办量最少负载均衡）／多人进选单池（组内抢单）；
 * SLA 超时未处理自动升级组长（定时兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArDispatchService {

    private final ArDispatchRuleMapper ruleMapper;
    private final ArClaimMapper claimMapper;

    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 规则列表（维护页）。 */
    public List<ArDispatchRule> rules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<ArDispatchRule>()
                .orderByAsc(ArDispatchRule::getPriority).orderByAsc(ArDispatchRule::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ArDispatchRule saveRule(ArDispatchRule rule) {
        if (!StringUtils.hasText(rule.getHandlers())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "负责人清单必填（多人逗号分隔=选单池）");
        }
        if (rule.getId() == null) {
            rule.setTenantId(1L);
            if (rule.getPriority() == null) {
                rule.setPriority(100);
            }
            if (rule.getEnabled() == null) {
                rule.setEnabled(1);
            }
            if (rule.getAutoBalance() == null) {
                rule.setAutoBalance(0);
            }
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    /**
     * 自动派工（进入 PENDING_DISPATCH 时调用）：
     * 命中规则→单人直派/多人选单池+负载均衡；未命中→留手工（现状兼容）。
     *
     * @return 是否已自动派工
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean autoAssignClaim(ArClaim c) {
        if (!ArClaim.ST_PENDING_DISPATCH.equals(c.getStatus())) {
            return false;
        }
        ArDispatchRule rule = matchRule(c.getClaimType(), c.getCustomerCode());
        if (rule == null) {
            log.info("[ar-dispatch] 单据 {} 未命中派工规则，留手工派工", c.getBillNo());
            return false;
        }
        List<String> handlers = Arrays.stream(rule.getHandlers().split(","))
                .map(String::trim).filter(StringUtils::hasText).toList();
        // 候选排除当前登录人——自动派工不得回派给单据制单人自己（自审自办）
        String me = currentOperator();
        if (me != null) {
            handlers = handlers.stream().filter(h -> !me.equals(h)).toList();
            if (handlers.isEmpty()) {
                log.info("[ar-dispatch] 单据 {} 规则候选仅含当前登录人 {}，留手工派工", c.getBillNo(), me);
                return false;
            }
        }
        String auditOldStatus = c.getStatus();
        String auditOldHandler = c.getHandler();
        ArClaim u = new ArClaim();
        u.setId(c.getId());
        u.setGroupLeader(rule.getGroupLeader());
        if (handlers.size() == 1 || Integer.valueOf(1).equals(rule.getAutoBalance())) {
            // 单人直派 / 负载均衡：在办量最少者优先
            String picked = handlers.size() == 1 ? handlers.get(0) : leastBusy(handlers);
            u.setHandler(picked);
            u.setHandlerPool(null);
            u.setStatus(ArClaim.ST_DISPATCHED);
            u.setDispatchTime(LocalDateTime.now());
            log.info("[ar-dispatch] 单据 {} 自动派工至 {}（规则 {}）", c.getBillNo(), picked, rule.getRuleName());
        } else {
            // 多人：进选单池（组内抢单）
            u.setHandler(null);
            u.setHandlerPool(String.join(",", handlers));
            log.info("[ar-dispatch] 单据 {} 进选单池 {}（规则 {}）", c.getBillNo(), handlers, rule.getRuleName());
        }
        claimMapper.updateById(u);
        // 仅单人直派/负载均衡分支改变了状态与处理人（多人进选单池分支不动状态，不记轨迹）
        if (u.getStatus() != null) {
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", auditOldStatus);
            auditBefore.put("handler", auditOldHandler);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", u.getStatus());
            auditAfter.put("handler", u.getHandler());
            auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                    String.valueOf(c.getId()), auditBefore, auditAfter, "自动派工");
        }
        // 回写内存对象，调用方后续逻辑可见
        if (u.getHandler() != null) {
            c.setHandler(u.getHandler());
            c.setStatus(ArClaim.ST_DISPATCHED);
            c.setDispatchTime(u.getDispatchTime());
        } else {
            c.setHandlerPool(u.getHandlerPool());
        }
        return true;
    }

    /** 当前登录人显示名（username→realName→userId 回退），未登录返回 null。 */
    private String currentOperator() {
        LoginUser u = UserContext.get();
        if (u == null) return null;
        if (StringUtils.hasText(u.getUsername())) return u.getUsername();
        if (StringUtils.hasText(u.getRealName())) return u.getRealName();
        return String.valueOf(u.getUserId());
    }

    /** SLA 超时升级（定时兜底）：DISPATCHED 且超 sla_hours 的单据备注升级并记组长。 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void escalateSla() {
        // 定时任务线程无租户上下文，需跨租户扫描。
        // 用 setPlatform()（tenantId=null 且 ignore=false）不行：TenantContext.getTenantId() 的
        // fail-closed 检查仍会抛「业务SQL缺少租户条件」，任务每 5 分钟刷一次 ERROR。
        // 走框架既定的 runIgnoreTenant（FsscTenantLineHandler.ignoreTable 对 ignore 模式放行）。
        TenantContext.runIgnoreTenant(this::doEscalateSla);
    }

    private void doEscalateSla() {
        List<ArClaim> processing = claimMapper.selectList(new LambdaQueryWrapper<ArClaim>()
                .eq(ArClaim::getStatus, ArClaim.ST_DISPATCHED)
                .isNotNull(ArClaim::getDispatchTime));
        // 规则表整表读一次，而不是逐单再读一次：在办单到万级时，逐单 matchRule 会把
        // 同一张 ar_dispatch_rule 反复查上千遍，库侧往返吃掉整轮时间。
        // 规则在一轮内不变，提到循环外语义等价。
        List<ArDispatchRule> enabledRules = ruleMapper.selectList(new LambdaQueryWrapper<ArDispatchRule>()
                .eq(ArDispatchRule::getEnabled, 1));
        for (ArClaim c : processing) {
            if (!StringUtils.hasText(c.getGroupLeader())) {
                continue;
            }
            Long ruleId = null;
            Integer slaHours = null;
            ArDispatchRule rule = matchRule(enabledRules, c.getClaimType(), c.getCustomerCode());
            if (rule != null) {
                slaHours = rule.getSlaHours();
            }
            if (slaHours == null || slaHours <= 0) {
                continue;
            }
            long hours = Duration.between(c.getDispatchTime(), LocalDateTime.now()).toHours();
            if (hours >= slaHours && (c.getModifyNote() == null || !c.getModifyNote().contains("SLA升级"))) {
                ArClaim u = new ArClaim();
                u.setId(c.getId());
                u.setModifyNote((c.getModifyNote() == null ? "" : c.getModifyNote() + " | ")
                        + "SLA超时升级组长[" + c.getGroupLeader() + "] " + hours + "h");
                claimMapper.updateById(u);
                log.warn("[ar-dispatch] 单据 {} SLA 超时 {}h，升级组长 {}", c.getBillNo(), hours, c.getGroupLeader());
            }
        }
    }

    // 内部

    /** 规则命中：最长前缀 → 类型精确 → priority。 */
    private ArDispatchRule matchRule(String claimType, String customerCode) {
        return matchRule(ruleMapper.selectList(new LambdaQueryWrapper<ArDispatchRule>()
                .eq(ArDispatchRule::getEnabled, 1)), claimType, customerCode);
    }

    /** 同上，规则集由调用方给（批量场景一轮只读一次）。 */
    private ArDispatchRule matchRule(List<ArDispatchRule> rules, String claimType, String customerCode) {
        return rules.stream()
                .filter(r -> r.getClaimType() == null || r.getClaimType().isBlank()
                        || r.getClaimType().equals(claimType))
                .filter(r -> StringUtils.hasText(r.getCustomerPrefix()) && StringUtils.hasText(customerCode)
                        ? customerCode.startsWith(r.getCustomerPrefix())
                        : !StringUtils.hasText(r.getCustomerPrefix()))
                .min(Comparator
                        .comparing((ArDispatchRule r) -> -nvlLen(r.getCustomerPrefix()))
                        .thenComparing(r -> r.getClaimType() == null || r.getClaimType().isBlank() ? 1 : 0)
                        .thenComparing(r -> r.getPriority() == null ? 100 : r.getPriority()))
                .orElse(null);
    }

    private static int nvlLen(String prefix) {
        return prefix == null ? 0 : prefix.length();
    }

    /** 负载均衡：候选中当前在办（DISPATCHED）量最少者优先。 */
    private String leastBusy(List<String> handlers) {
        String best = handlers.get(0);
        long bestCount = Long.MAX_VALUE;
        for (String h : handlers) {
            long busy = claimMapper.selectCount(new LambdaQueryWrapper<ArClaim>()
                    .eq(ArClaim::getHandler, h)
                    .eq(ArClaim::getStatus, ArClaim.ST_DISPATCHED));
            if (busy < bestCount) {
                bestCount = busy;
                best = h;
            }
        }
        return best;
    }
}
