// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.compliance.entity.MdViolationReason;
import com.fssc.compliance.mapper.MdViolationReasonMapper;
import com.fssc.compliance.mapper.PrecheckResultMapper;
import com.fssc.sharing.entity.OpQuality;
import com.fssc.sharing.mapper.OpQualityMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 预检拦截统计与「漏拦率」看板。
 *
 * <p>回答两个问题：
 * <ol>
 *   <li><b>哪条预检规则在拦人</b>——按规则码聚合拦截次数与涉及单据数，顺带暴露"从不命中"的规则；</li>
 *   <li><b>有多少问题是预检没拦住、被人工质检事后发现的</b>——质检退回(BACK)的单据里，
 *       其预检历史中没有 FAIL 记录的那部分，即「疑似漏拦」。这才是系统缺陷的度量，
 *       比扣员工的分有用。</li>
 * </ol>
 *
 * <p>全部走 MyBatis-Plus mapper 而不是裸 JDBC，确保 tenant_id 由框架自动重写，不跨租户串数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrecheckStatsService {

    private final PrecheckResultMapper precheckResultMapper;
    private final OpQualityMapper opQualityMapper;
    private final MdViolationReasonMapper violationReasonMapper;

    /** 近 N 天的拦截分布 + 漏拦率 */
    public Map<String, Object> stats(int days) {
        int d = days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime since = LocalDateTime.now().minusDays(d);

        // ① 拦截明细（FAIL 且落在窗口内）
        List<com.fssc.compliance.entity.PrecheckResult> blocks = precheckResultMapper.selectList(
                new LambdaQueryWrapper<com.fssc.compliance.entity.PrecheckResult>()
                        .eq(com.fssc.compliance.entity.PrecheckResult::getCheckResult, "FAIL")
                        .ge(com.fssc.compliance.entity.PrecheckResult::getCreateTime, since));
        Map<String, Map<String, Object>> ruleAgg = new LinkedHashMap<>();
        Set<Long> blockedBills = new HashSet<>();
        for (com.fssc.compliance.entity.PrecheckResult r : blocks) {
            String code = r.getRuleCode() == null ? "UNKNOWN" : r.getRuleCode();
            Map<String, Object> a = ruleAgg.computeIfAbsent(code, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("ruleCode", k);
                m.put("blocks", 0);
                m.put("bills", 0);
                m.put("_billIds", new HashSet<Long>());
                m.put("lastAt", null);
                return m;
            });
            a.put("blocks", (Integer) a.get("blocks") + 1);
            if (r.getBillId() != null) {
                ((Set<Long>) a.get("_billIds")).add(r.getBillId());
                blockedBills.add(r.getBillId());
            }
            LocalDateTime ct = r.getCreateTime();
            if (ct != null && (a.get("lastAt") == null || ct.isAfter((LocalDateTime) a.get("lastAt")))) {
                a.put("lastAt", ct);
            }
        }
        List<Map<String, Object>> byRule = new ArrayList<>();
        for (Map<String, Object> a : ruleAgg.values()) {
            a.put("bills", ((Set<?>) a.remove("_billIds")).size());
            byRule.add(a);
        }
        byRule.sort(Comparator.comparingInt(m -> -(Integer) m.get("blocks")));

        // ② 质检退回（人工发现问题）
        List<OpQuality> backs = opQualityMapper.selectList(new LambdaQueryWrapper<OpQuality>()
                .eq(OpQuality::getResult, "BACK")
                .ge(OpQuality::getCheckTime, since));

        // ③ 这批单据里，预检当期有没有 FAIL 记录
        Set<Long> backBillIds = new HashSet<>();
        for (OpQuality q : backs) {
            if (q.getBillId() != null) {
                backBillIds.add(q.getBillId());
            }
        }
        Set<Long> precheckFailedBillIds = new HashSet<>();
        if (!backBillIds.isEmpty()) {
            List<com.fssc.compliance.entity.PrecheckResult> hits = precheckResultMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.compliance.entity.PrecheckResult>()
                            .eq(com.fssc.compliance.entity.PrecheckResult::getCheckResult, "FAIL")
                            .in(com.fssc.compliance.entity.PrecheckResult::getBillId, backBillIds));
            for (com.fssc.compliance.entity.PrecheckResult r : hits) {
                if (r.getBillId() != null) {
                    precheckFailedBillIds.add(r.getBillId());
                }
            }
        }

        // ④ 漏拦样本（退回但预检没拦过），带违规原因名
        Map<String, String> reasonNames = new HashMap<>();
        for (MdViolationReason r : violationReasonMapper.selectList(null)) {
            reasonNames.put(r.getReasonCode(), r.getReasonName());
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        int missed = 0;
        for (OpQuality q : backs) {
            boolean precheckCaught = q.getBillId() != null && precheckFailedBillIds.contains(q.getBillId());
            if (precheckCaught) {
                continue;
            }
            missed++;
            if (samples.size() >= 50) {
                continue;   // 只留前 50 条样本，计数照常累加
            }
            Map<String, Object> s = new HashMap<>();
            s.put("billNo", q.getBillNo());
            s.put("checkTime", q.getCheckTime());
            s.put("score", q.getScore());
            s.put("violationReasonCode", q.getViolationReasonCode());
            s.put("violationReasonName", q.getViolationReasonCode() == null
                    ? null : reasonNames.get(q.getViolationReasonCode()));
            s.put("deductReason", q.getDeductReason());
            samples.add(s);
        }

        Map<String, Object> missRate = new HashMap<>();
        missRate.put("backTotal", backs.size());
        missRate.put("missed", missed);
        missRate.put("rate", backs.isEmpty() ? 0.0 : Math.round(missed * 1000.0 / backs.size()) / 1000.0);
        missRate.put("samples", samples);

        Map<String, Object> out = new HashMap<>();
        out.put("days", d);
        out.put("byRule", byRule);
        out.put("blockedBills", blockedBills.size());
        out.put("blockedTimes", blocks.size());
        out.put("missRate", missRate);
        return out;
    }
}
