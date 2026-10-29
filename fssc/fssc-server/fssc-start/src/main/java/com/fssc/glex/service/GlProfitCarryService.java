// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.gl.service.GlService;
import com.fssc.glex.entity.GlMonthClose;
import com.fssc.glex.mapper.GlMonthCloseMapper;
import com.fssc.masterdata.entity.MdPeriod;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdPeriodMapper;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.framework.org.OrgGuard;
import com.fssc.system.service.NumberRuleService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 损益结转服务：把原内嵌于月结的损益结转逻辑抽为可独立执行的 service 动作。
 * <p>
 * 规则：对指定公司+期间的<b>损益类科目</b>（科目编码以 6 开头，md_subject.subject_code 前缀判定，
 * 按当期总账发生额 net = 借 − 贷）生成结转凭证，余额结转至 本年利润(4103)：
 * <ul>
 *  <li>费用类（净借方余额 net&gt;0）：借 本年利润(4103) / 贷 该损益科目；</li>
 *  <li>收入类（净贷方余额 net&lt;0）：借 该损益科目 / 贷 本年利润(4103)；</li>
 *  <li>净额为 0 的科目不生成行；</li>
 *  <li>对方科目 4103 取净额一行：费用合计&gt;收入合计（亏损）→ 借 4103；反之（盈利）→ 贷 4103。</li>
 * </ul>
 * 数据源为 op_gl_entry（POSTED 凭证实时聚合，GlService.balance 规则），因此<b>仅结转已过账发生额</b>。
 * <p>
 * 去重：同公司+同期间已存在 source_type='PROFIT_CARRY' 凭证（VOID 除外）则拒绝并提示先冲销。
 * 与月结的关系：GlMonthCloseService.close 目前仅做试算平衡与期间关账、<b>未内嵌</b>损益结转；
 * 后续如需在月结内联动结转，直接复用本 service 的 {@link #carry(String, Long)}，避免重复实现。
 * 凭证生成后立即过账（DRAFT→POSTED 并落总账），保证随后月结试算/关账包含结转影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlProfitCarryService {

    /** 本年利润科目编码（缺失时抛明确异常，见 sql/impl-cl-20260914.sql DML 补科目） */
    private static final String SUBJ_PROFIT = "4103";
    /** 结转凭证 source_type（唯一键） */
    public static final String SOURCE_TYPE = "PROFIT_CARRY";

    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final MdSubjectMapper mdSubjectMapper;
    private final GlService glService;
    private final VoucherService voucherService;
    private final OrgGuard orgGuard;
    private final NumberRuleService numberRuleService;
    private final GlMonthCloseMapper glMonthCloseMapper;
    /** 损益结转的期间态前置校验（已关账期间快拒，不再跑全量损益计算后才被拒） */
    private final MdPeriodMapper mdPeriodMapper;

    /**
     * 执行损益结转：对 公司+期间 的损益类科目生成并过账结转凭证，回写最近一条月结记录的 voucher_no（如有）。
     *
     * @param period 会计期间 yyyyMM
     * @param companyId 结转公司（空则 1，与月结 close 同一规则）
     * @return 结转结果摘要（voucherId/voucherNo/期间/借方合计/贷方合计/结转科目数）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> carry(String period, Long companyId) {
        if (!StringUtils.hasText(period) || period.length() != 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间必须为 yyyyMM");
        }
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        Long tenantId = TenantContext.getTenantId();
        Long comp = companyId != null ? companyId : 1L;
        orgGuard.assertVisible(comp, null);

        // 期间态前置校验——已关账(CLOSED)期间直接早退。原实现把 glService.balance 全量损益
        // 计算跑完才在关账侧被拒（记录实测拒绝耗时 20,003ms），用户等 20s 才拿到一句拒绝。
        MdPeriod periodRow = mdPeriodMapper.selectOne(
                new LambdaQueryWrapper<MdPeriod>()
                        .eq(MdPeriod::getCompanyId, comp)
                        .eq(MdPeriod::getPeriodYear, year)
                        .eq(MdPeriod::getPeriodMonth, month)
                        .last("limit 1"));
        if (periodRow != null && "CLOSED".equals(periodRow.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 已关账（CLOSED），不可执行损益结转；如需调整请先反结账");
        }

        // 去重：同公司+期间已有结转凭证（VOID 除外）则拒绝
        Long existed = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getCompanyId, comp)
                .eq(OpVoucher::getPeriodYear, year)
                .eq(OpVoucher::getPeriodMonth, month)
                .eq(OpVoucher::getSourceType, SOURCE_TYPE)
                .ne(OpVoucher::getStatus, "VOID"));
        if (existed != null && existed > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 已存在 " + existed + " 张损益结转凭证，不可重复结转；如需重做请先冲销原凭证");
        }

        // 本年利润科目必须存在（结转目标）
        MdSubject profit = mdSubjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, SUBJ_PROFIT)
                .last("limit 1"));
        if (profit == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "科目 " + SUBJ_PROFIT + " 本年利润 不存在，无法结转；请先在科目维护中新增（或执行 sql/impl-cl-20260914.sql 的科目补录 DML）");
        }

        // 损益类科目当期发生额（op_gl_entry 聚合；含全部已过账凭证）
        List<Map<String, Object>> balances = glService.balance(comp, year, month);
        record Carry(String code, String name, Long subjectId, BigDecimal net) {}
        List<Carry> carries = new ArrayList<>();
        for (Map<String, Object> b : balances) {
            String code = b.get("subjectCode") == null ? "" : String.valueOf(b.get("subjectCode"));
            if (!code.startsWith("6")) {
                continue; // 损益类：科目编码以 6 开头（6001 收入 / 64 成本 / 66 费用 / 67 减值 / 681 所得税等）
            }
            BigDecimal debit = dec(b.get("periodDebit"));
            BigDecimal credit = dec(b.get("periodCredit"));
            BigDecimal net = debit.subtract(credit);
            if (net.signum() == 0) {
                continue;
            }
            carries.add(new Carry(code,
                    b.get("subjectName") == null ? "" : String.valueOf(b.get("subjectName")),
                    b.get("subjectId") == null ? null : Long.valueOf(String.valueOf(b.get("subjectId"))),
                    net));
        }
        if (carries.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 无损益类科目发生额（或均为 0），无需结转；请先完成凭证过账");
        }

        // 生成结转凭证（凭证日期取结转期间末日，保证落在本期间）
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(comp);
        v.setPeriodYear(year);
        v.setPeriodMonth(month);
        v.setVoucherDate(YearMonth.of(year, month).atEndOfMonth());
        v.setSourceType(SOURCE_TYPE);
        v.setSourceDocType(SOURCE_TYPE);
        v.setSummary("损益结转-" + period + "-公司" + comp);
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        int no = 10;
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        // 费用类（net>0 借方余额）：贷 损益科目；收入类（net<0 贷方余额）：借 损益科目
        for (Carry c : carries) {
            BigDecimal amt = c.net().abs();
            if (c.net().signum() > 0) {
                opVoucherLineMapper.insert(line(tenantId, v.getId(), no, "结转 " + c.code() + " " + c.name(),
                        c.subjectId(), c.code(), c.name(), "CREDIT", amt));
                creditTotal = creditTotal.add(amt);
            } else {
                opVoucherLineMapper.insert(line(tenantId, v.getId(), no, "结转 " + c.code() + " " + c.name(),
                        c.subjectId(), c.code(), c.name(), "DEBIT", amt));
                debitTotal = debitTotal.add(amt);
            }
            no += 10;
        }
        // 对方科目 本年利润(4103) 净额行：费用合计>收入合计（亏损）→ 借 4103；盈利 → 贷 4103
        BigDecimal gap = creditTotal.subtract(debitTotal); // >0：贷方尚缺，借 4103 补（亏损结转）
        if (gap.signum() > 0) {
            opVoucherLineMapper.insert(line(tenantId, v.getId(), no, "结转 本年利润（亏损）",
                    profit.getId(), SUBJ_PROFIT, profit.getSubjectName(), "DEBIT", gap));
            debitTotal = debitTotal.add(gap);
        } else if (gap.signum() < 0) {
            opVoucherLineMapper.insert(line(tenantId, v.getId(), no, "结转 本年利润（盈利）",
                    profit.getId(), SUBJ_PROFIT, profit.getSubjectName(), "CREDIT", gap.negate()));
            creditTotal = creditTotal.add(gap.negate());
        }
        // 头行合计与平衡标记
        OpVoucher upd = new OpVoucher();
        upd.setId(v.getId());
        upd.setDebitTotal(debitTotal);
        upd.setCreditTotal(creditTotal);
        upd.setIsBalanced(debitTotal.compareTo(creditTotal) == 0 ? 1 : 0);
        opVoucherMapper.updateById(upd);

        // 立即过账（含末级科目/辅助/借贷平衡校验 + 落总账），保证月结试算/关账包含结转影响
        Long operUser = UserContext.get() == null
                ? null : UserContext.get().getUserId();
        // 结转凭证由本次操作的会计当场生成、当场入账（create_by＝过账人），
        // 属无人工审核环节的系统机制凭证，走系统过账口，否则损益结转被自家制审分离校验自锁。
        voucherService.postSystem(v.getId(), operUser);

        // 回写最近一条月结记录的结转凭证号（便于追溯；UNCLOSED 日志行不回写）
        // 连带：月结发起态由 CLOSED 改为 RUNNING（末节点通过才 CLOSED），若仍只认 CLOSED，
        // 在月结审批在途期间做的损益结转就找不到落点、凭证号不回写。改为「已结/在途/审批通过未关账」
        // 三态取最新一条，查询范围与月结单本身一致。
        GlMonthClose latest = glMonthCloseMapper.selectOne(new LambdaQueryWrapper<GlMonthClose>()
                .eq(GlMonthClose::getPeriod, period)
                .eq(GlMonthClose::getCompanyId, comp)
                .in(GlMonthClose::getStatus, "CLOSED",
                        GlMonthCloseService.STATUS_RUNNING, GlMonthCloseService.STATUS_APPROVED_NOT_CLOSED)
                .orderByDesc(GlMonthClose::getId)
                .last("limit 1"));
        if (latest != null && (latest.getVoucherNo() == null || latest.getVoucherNo().isBlank())) {
            GlMonthClose mark = new GlMonthClose();
            mark.setId(latest.getId());
            mark.setVoucherNo(v.getVoucherNo());
            glMonthCloseMapper.updateById(mark);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("voucherId", v.getId());
        result.put("voucherNo", v.getVoucherNo());
        result.put("period", period);
        result.put("companyId", comp);
        result.put("debitTotal", debitTotal);
        result.put("creditTotal", creditTotal);
        result.put("subjectLines", carries.size());
        log.info(" 期间 {} 损益结转完成：凭证 {}，结转科目 {} 个，借 {} / 贷 {}",
                period, v.getVoucherNo(), carries.size(), debitTotal, creditTotal);
        return result;
    }

    private OpVoucherLine line(Long tenantId, Long voucherId, int lineNo, String summary,
                               Long subjectId, String code, String name, String direction, BigDecimal amount) {
        OpVoucherLine l = new OpVoucherLine();
        l.setTenantId(tenantId);
        l.setVoucherId(voucherId);
        l.setLineNo(lineNo);
        l.setSummary(summary);
        l.setSubjectId(subjectId);
        l.setSubjectCode(code);
        l.setSubjectName(name);
        l.setDirection(direction);
        l.setAmount(amount);
        l.setCurrencyCode("CNY");
        l.setCreateTime(LocalDateTime.now());
        return l;
    }

    private String nextVoucherNo() {
        try {
            return numberRuleService.next("VOUCHER");
        } catch (Exception e) {
            return "JZ" + System.currentTimeMillis();
        }
    }

    private BigDecimal dec(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }
}
