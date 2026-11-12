// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.service;

import com.fssc.system.service.ConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 预算口径开关（租户级，落 {@code sys_config}，由预算管理员自助配置）。
 *
 * <p>为什么做成开关而不是写死：这两条都是<b>口径选择</b>而非对错——</p>
 * <ul>
 *   <li><b>预提/待摊是否占预算</b>：占＝「申请占用」（预算影响类型三值之一），
 *       客户在册手册也是「选择预提期间即占用预算的期间」。但有的企业预提只是会计估计、
 *       不进预算控制（预提与报销两条线各自独立）。两种做法都成立，取决于"预提是不是花钱的承诺"。</li>
 *   <li><b>合同是否占预算</b>：占＝强控（签了合同就锁额度，防止同一笔预算被重复承诺）；
 *       不占＝弱控（只看余额不锁）。我方现行是占用，属强控方向。</li>
 * </ul>
 *
 * <p><b>缺省＝开</b>：配置项不存在或为空时一律按"启用"处理，保证升级后行为与改造前一致，
 * 不会因为忘了配参数而静默改变预算口径。只有显式写 {@code 0/false/off/no} 才关。</p>
 *
 * <p><b>关闭不追溯</b>：开关只影响<b>新发生</b>的占用；已经占上的额度不会因为把开关关掉而消失，
 * 仍按原路径在冲销/驳回/关单时释放。否则"关开关"会变成一次静默的额度重算，账实立刻对不上。</p>
 *
 * <p>键名与语义一并固化在这里，业务侧只调 {@link #accrualOccupyEnabled()} /
 * {@link #contractOccupyEnabled()}，不各自拼 key 字符串。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetSwitchService {

    /** 预提/待摊是否进预算占用（申请占用）。缺省开。 */
    public static final String KEY_ACCRUAL_OCCUPY = "budget.accrual.occupy.enabled";
    /** 合同签订是否占预算。缺省开。 */
    public static final String KEY_CONTRACT_OCCUPY = "budget.contract.occupy.enabled";

    private static final String NAME_ACCRUAL = "预提/待摊占用预算";
    private static final String NAME_CONTRACT = "合同占用预算";

    private final ConfigService configService;

    /** 预提/待摊（ACCRUAL/DEFER 记账单）是否在提交时占用预算 */
    public boolean accrualOccupyEnabled() {
        return enabled(KEY_ACCRUAL_OCCUPY);
    }

    /** 合同（EXP_CONTRACT）签订时是否占用预算 */
    public boolean contractOccupyEnabled() {
        return enabled(KEY_CONTRACT_OCCUPY);
    }

    /** 当前生效值快照（供配置页回显；缺省项按启用返回，前端不显示"未配置"空态） */
    public Map<String, Boolean> snapshot() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("accrualOccupy", accrualOccupyEnabled());
        m.put("contractOccupy", contractOccupyEnabled());
        return m;
    }

    /**
     * 保存开关。只写显式传入的项（null＝不改），避免前端表单少传一个字段把另一项误关。
     *
     * <p>走 {@link ConfigService#setValue}：存在则更新、不存在则新建，且带缓存失效，
     * 改完当场生效（{@code getValue} 走 TenantCache，不清缓存会读到旧值）。</p>
     */
    public void save(Boolean accrualOccupy, Boolean contractOccupy) {
        if (accrualOccupy != null) {
            configService.setValue(KEY_ACCRUAL_OCCUPY, accrualOccupy ? "1" : "0", NAME_ACCRUAL);
        }
        if (contractOccupy != null) {
            configService.setValue(KEY_CONTRACT_OCCUPY, contractOccupy ? "1" : "0", NAME_CONTRACT);
        }
        log.info("预算口径开关已更新：预提/待摊占用={}，合同占用={}",
                accrualOccupy == null ? "(未改)" : accrualOccupy,
                contractOccupy == null ? "(未改)" : contractOccupy);
    }

    private boolean enabled(String key) {
        String v = configService.getValue(key);
        if (v == null || v.isBlank()) {
            return true; // 缺省开：升级后行为不变
        }
        String s = v.trim().toLowerCase();
        boolean on = !("0".equals(s) || "false".equals(s) || "off".equals(s) || "no".equals(s));
        return on;
    }
}
