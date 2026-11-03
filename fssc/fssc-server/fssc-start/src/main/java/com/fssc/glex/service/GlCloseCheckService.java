// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.mapper.GlInterOffsetMapper;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 月结检查清单的系统可判项。
 *
 * <p>改造前 8 项全是人工勾选：勾一下只是把 status 置 DONE，不读任何业务数据。
 * 「税金计提已完成」与「全部凭证已过账」这两项恰恰是系统完全判得了的，
 * 人工勾选等于把「我以为做完了」写进结账依据。
 *
 * <p>本服务只判<b>系统能判的 3 项</b>（其余 5 项保留人工）：
 * <ol>
 *   <li>全部凭证已过账（无 DRAFT）—— 读 op_voucher</li>
 *   <li>税金计提已完成 —— 读 gl_tax_provision</li>
 *   <li>内部往来已抵消 —— 读 gl_inter_offset</li>
 * </ol>
 * 判据一律只读，不代勾、不改单据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlCloseCheckService {

    /** 系统可判项名称（与 seed 的默认清单逐字对应；改名即失去系统校验，属配置风险） */
    public static final String ITEM_VOUCHER_POSTED = "全部凭证已过账（无 DRAFT）";
    public static final String ITEM_TAX_PROVISION = "税金计提已完成";
    public static final String ITEM_INTER_OFFSET = "内部往来已抵消";

    private final OpVoucherMapper opVoucherMapper;
    private final GlTaxProvisionMapper taxProvisionMapper;
    private final GlInterOffsetMapper interOffsetMapper;

    /** 该项是否由系统判定（是则人工勾选必须过校验） */
    public static boolean isSystemChecked(String itemName) {
        return ITEM_VOUCHER_POSTED.equals(itemName)
                || ITEM_TAX_PROVISION.equals(itemName)
                || ITEM_INTER_OFFSET.equals(itemName);
    }

    /**
     * 校验一项。
     *
     * @return 通过返回 null；不通过返回给用户看的原因（含具体条数，不写"校验失败"了事）
     */
    public String verifyOne(String itemName, String period, Long companyId) {
        if (ITEM_VOUCHER_POSTED.equals(itemName)) {
            return verifyVoucherPosted(period, companyId);
        }
        if (ITEM_TAX_PROVISION.equals(itemName)) {
            return verifyTaxProvision(period, companyId);
        }
        if (ITEM_INTER_OFFSET.equals(itemName)) {
            return verifyInterOffset(period, companyId);
        }
        return null;
    }

    /** 清单级校验：一次返回每项的系统判定结果（前端在清单上显示"系统判"标签与原因） */
    public List<Map<String, Object>> verifyAll(String period, Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : List.of(ITEM_VOUCHER_POSTED, ITEM_TAX_PROVISION, ITEM_INTER_OFFSET)) {
            String err = verifyOne(name, period, companyId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemName", name);
            m.put("passed", err == null);
            m.put("message", err == null ? "系统校验通过" : err);
            out.add(m);
        }
        return out;
    }

    private String verifyVoucherPosted(String period, Long companyId) {
        int[] ym = parsePeriod(period);
        Long draft = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getPeriodYear, ym[0])
                .eq(OpVoucher::getPeriodMonth, ym[1])
                .eq(companyId != null, OpVoucher::getCompanyId, companyId)
                .eq(OpVoucher::getStatus, "DRAFT"));
        long n = draft == null ? 0 : draft;
        return n == 0 ? null : "本期仍有 " + n + " 张凭证处于 DRAFT（未过账），过账后才能结账";
    }

    private String verifyTaxProvision(String period, Long companyId) {
        List<GlTaxProvision> rows = taxProvisionMapper.selectList(new LambdaQueryWrapper<GlTaxProvision>()
                .eq(GlTaxProvision::getPeriod, period)
                .eq(companyId != null, GlTaxProvision::getCompanyId, companyId));
        if (rows.isEmpty()) {
            return "本期没有任何税金计提记录；如确无应纳税额，请先在「税务管理→税金计提」做零申报留痕";
        }
        long pending = rows.stream()
                .filter(r -> !"POSTED".equalsIgnoreCase(String.valueOf(r.getStatus()))
                        && !"APPROVED".equalsIgnoreCase(String.valueOf(r.getStatus())))
                .count();
        if (pending > 0) {
            return "本期有 " + pending + " 条税金计提仍处审批中/已驳回，计提未完成";
        }
        long noVoucher = rows.stream()
                .filter(r -> !"POSTED".equalsIgnoreCase(String.valueOf(r.getStatus())))
                .count();
        if (noVoucher > 0) {
            return "本期有 " + noVoucher + " 条税金计提已审批但未出凭证（应落未落），请到计提页查看挂接核查";
        }
        return null;
    }

    private String verifyInterOffset(String period, Long companyId) {
        LambdaQueryWrapper<GlInterOffset> qw = new LambdaQueryWrapper<GlInterOffset>()
                .eq(GlInterOffset::getPeriod, period);
        if (companyId != null) {
            qw.and(w -> w.eq(GlInterOffset::getFromCompanyId, companyId)
                    .or().eq(GlInterOffset::getToCompanyId, companyId));
        }
        List<GlInterOffset> rows = interOffsetMapper.selectList(qw);
        if (rows.isEmpty()) {
            // 无内部往来也是合法状态：不要求每期必须有抵消记录
            return null;
        }
        long unposted = rows.stream()
                .filter(r -> !"POSTED".equalsIgnoreCase(String.valueOf(r.getOffsetStatus())))
                .count();
        return unposted == 0 ? null : "本期有 " + unposted + " 条内部往来抵消未过账";
    }

    private static int[] parsePeriod(String period) {
        if (period == null || period.length() != 6) {
            throw new IllegalArgumentException("会计期间格式应为 yyyyMM: " + period);
        }
        return new int[]{Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4, 6))};
    }
}
