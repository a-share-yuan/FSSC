package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.mapper.GlInterOffsetMapper;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>本服务只判<b>系统能判的 4 项</b>（其余保留人工）：
 * <ol>
 *   <li>全部凭证已过账（无 DRAFT）—— 读 op_voucher（查"生成了没过账"）</li>
 *   <li>税金计提已完成 —— 读 gl_tax_provision</li>
 *   <li>内部往来已抵消 —— 读 gl_inter_offset</li>
 *   <li>应生成未生成凭证检查 —— 读 exp_bill × op_voucher（查"根本应生成而未生成"，账证核对）</li>
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
    public static final String ITEM_VOUCHER_EXPECTED = "应生成未生成凭证检查";

    /** 界面显示名：存储键沿用历史值（存量行与系统校验都依赖逐字匹配），读面只出中文 */
    public static final String LABEL_VOUCHER_POSTED = "全部凭证已过账（无未过账凭证）";

    /** 存储键 → 界面显示名（非本项原样返回） */
    public static String displayName(String itemName) {
        return ITEM_VOUCHER_POSTED.equals(itemName) ? LABEL_VOUCHER_POSTED : itemName;
    }

    private final OpVoucherMapper opVoucherMapper;
    private final ExpBillMapper expBillMapper;
    private final GlTaxProvisionMapper taxProvisionMapper;
    private final GlInterOffsetMapper interOffsetMapper;

    /** 该项是否由系统判定（是则人工勾选必须过校验） */
    public static boolean isSystemChecked(String itemName) {
        return ITEM_VOUCHER_POSTED.equals(itemName)
                || ITEM_TAX_PROVISION.equals(itemName)
                || ITEM_INTER_OFFSET.equals(itemName)
                || ITEM_VOUCHER_EXPECTED.equals(itemName);
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
        if (ITEM_VOUCHER_EXPECTED.equals(itemName)) {
            return verifyExpectedVoucher(period, companyId);
        }
        return null;
    }

    /** 清单级校验：一次返回每项的系统判定结果（前端在清单上显示"系统判"标签与原因） */
    public List<Map<String, Object>> verifyAll(String period, Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : List.of(ITEM_VOUCHER_POSTED, ITEM_TAX_PROVISION, ITEM_INTER_OFFSET, ITEM_VOUCHER_EXPECTED)) {
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
        return n == 0 ? null : "本期仍有 " + n + " 张凭证未过账（草稿状态），过账后才能结账";
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

    /**
     * 应生成未生成凭证检查（账证核对）。
     * <p>与「无 DRAFT」互补：DRAFT 查"生成了没过账"，本项查"该生成的根本没生成"——
     * 单据审批通过、钱可能已付，凭证却一张没有，靠旧清单永远发现不了。
     * 按单据类型定应生成的凭证来源：报销→EXPENSE、对公结算→EXPENSE_SETTLE、对公付款→EXPENSE_PAY、
     * 借款→EXP_LOAN、还款→EXP_REPAY；事前申请不出证，不判。
     */
    private String verifyExpectedVoucher(String period, Long companyId) {
        int[] ym = parsePeriod(period);
        LocalDate from = LocalDate.of(ym[0], ym[1], 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<ExpBill> bills = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getDelFlag, 0)
                .in(ExpBill::getStatus, "APPROVED", "PAID", "CLOSED")
                .eq(companyId != null, ExpBill::getCompanyId, companyId)
                // 已出证的按凭证日期归期；尚未出证的按业务日期归期（否则永远不进入任何一期）
                .and(w -> w.between(ExpBill::getVoucherDate, from, to)
                        .or(x -> x.isNull(ExpBill::getVoucherDate)
                                .between(ExpBill::getApplyDate, from, to))));
        if (bills.isEmpty()) {
            return null;
        }
        List<Long> billIds = new ArrayList<>();
        for (ExpBill b : bills) {
            billIds.add(b.getId());
        }
        List<OpVoucher> vouchers = opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getDelFlag, 0)
                .ne(OpVoucher::getStatus, "VOID")
                .in(OpVoucher::getBillId, billIds));
        Map<Long, Set<String>> sourcesByBill = new HashMap<>();
        for (OpVoucher v : vouchers) {
            sourcesByBill.computeIfAbsent(v.getBillId(), k -> new LinkedHashSet<>()).add(v.getSourceType());
        }
        List<String> missing = new ArrayList<>();
        for (ExpBill b : bills) {
            String expected = expectedVoucherSource(b);
            if (expected == null) {
                continue;
            }
            Set<String> got = sourcesByBill.getOrDefault(b.getId(), Collections.emptySet());
            if (!got.contains(expected)) {
                missing.add(b.getBillNo());
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        String sample = String.join("、", missing.subList(0, Math.min(5, missing.size())))
                + (missing.size() > 5 ? " 等" : "");
        return "本期有 " + missing.size() + " 张已办结单据缺少对应凭证（" + sample
                + "）；请到凭证台账或单据详情补生成后再结账";
    }

    /** 单据类型 → 应生成的凭证来源类型；不出证（事前申请等）返回 null */
    private static String expectedVoucherSource(ExpBill b) {
        String type = b.getBillType();
        String sub = b.getBillSubType();
        if ("CLAIM".equals(type)) {
            return "EXPENSE";
        }
        if ("PAY".equals(type)) {
            return "SETTLE".equalsIgnoreCase(sub) ? "EXPENSE_SETTLE" : "EXPENSE_PAY";
        }
        if ("LOAN".equals(type)) {
            return "EXP_LOAN";
        }
        if ("REPAY".equals(type)) {
            return "EXP_REPAY";
        }
        return null;
    }

    private static int[] parsePeriod(String period) {
        if (period == null || period.length() != 6) {
            throw new IllegalArgumentException("会计期间格式应为 yyyyMM: " + period);
        }
        return new int[]{Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4, 6))};
    }
}
