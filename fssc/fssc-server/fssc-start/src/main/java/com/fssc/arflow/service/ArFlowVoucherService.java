// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fssc.arflow.entity.ArClaim;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AR服务申请真实分录凭证生成（补齐评估缺口2）。
 *
 * <p>范式对齐 VoucherService（借/贷两行平衡分录 + md_subject 科目解析 + 同源判重去重），
 * 但独立成类避免改动 2300+ 行的共享大文件。科目取自单据上的附件五默认值；
 * 科目缺码时分录仍落 subjectCode（subjectId 允许空），不阻断。</p>
 *
 * <ul>
 *   <li>索赔/暂估/对冲/其它业务：借 debitAccount / 贷 creditAccount；</li>
 *   <li>暂估冲回：红字镜像（借 creditAccount / 贷 debitAccount）；</li>
 *   <li>付款服务申请过账：借 2202 应付账款 / 贷 1002 银行存款。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArFlowVoucherService {

    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final MdSubjectMapper mdSubjectMapper;
    private final NumberRuleService numberRuleService;
    /**
     * 月结链自锁：月结过总账凭证「制单即审」复用共享凭证正规过账通道
     * （{@link VoucherService#postSystem}，与 GlProfitCarryService 损益结转同一范式），
     * 不自实现过账、不裸 UPDATE status。
     */
    private final VoucherService voucherPostService;

    /** AR服务申请凭证（审单过账调用）。金额：对冲用借贷金额，其余用申请金额。 */
    public String generateClaimVoucher(ArClaim c) {
        BigDecimal debitAmount = ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())
                ? nvl(c.getDebitAmount()) : claimAmount(c);
        BigDecimal creditAmount = ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())
                ? nvl(c.getCreditAmount()) : claimAmount(c);
        String debitCode = requireCode(c.getDebitAccount(), "借方科目");
        String creditCode = requireCode(c.getCreditAccount(), "贷方科目");
        if (debitAmount.signum() == 0 || creditAmount.signum() == 0
                || debitAmount.compareTo(creditAmount) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证借贷金额必须一致且大于0（借 "
                    + debitAmount + " / 贷 " + creditAmount + "）");
        }
        return createVoucher(c.getTenantId(), c.getCompanyId(), debitCode, creditCode,
                debitAmount, "AR服务申请-" + nullToBlank(c.getBillNo()), "AR_CLAIM", c.getId(),
                c.getBillNo(), c.getCustomerName());
    }

    /**
     * 其它业务对冲多行分录凭证（蓝图附件11.4.3，"多借多贷"做法）：
     * 解析单据 lines_json（[{debitCode,debitAmount,creditCode,creditAmount,summary,partnerCode}]），
     * 逐行生成借/贷分录；科目缺失 fail-closed；合计借贷强校验；同源判重去重。
     */
    public String generateClaimMultiLineVoucher(ArClaim c) {
        OpVoucher existed = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, "AR_CLAIM")
                .eq(OpVoucher::getSourceDocId, c.getId())
                .ne(OpVoucher::getStatus, "VOID")
                .last("LIMIT 1"));
        if (existed != null) {
            return existed.getVoucherNo();
        }
        List<Map<String, Object>> rows;
        try {
            rows = JsonMapper.builder().build().readValue(
                    c.getLinesJson(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对冲分录解析失败：" + e.getMessage());
        }
        LocalDate today = LocalDate.now();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        OpVoucher v = new OpVoucher();
        v.setTenantId(c.getTenantId());
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(c.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(c.getId());
        v.setSourceType("AR_CLAIM");
        v.setSourceDocType("AR_CLAIM");
        v.setSourceDocId(c.getId());
        v.setSummary("其它业务对冲-" + c.getBillNo());
        v.setStatus("DRAFT");
        v.setCreateTime(LocalDateTime.now());
        opVoucherMapper.insert(v);
        int lineNo = 10;
        for (Map<String, Object> r : rows) {
            String dCode = String.valueOf(r.get("debitCode"));
            String cCode = String.valueOf(r.get("creditCode"));
            BigDecimal dAmt = new BigDecimal(String.valueOf(r.get("debitAmount")));
            BigDecimal cAmt = new BigDecimal(String.valueOf(r.get("creditAmount")));
            String summary = r.get("summary") == null ? "对冲分录" : String.valueOf(r.get("summary"));
            Long dId = findSubjectIdByCode(dCode);
            Long cId = findSubjectIdByCode(cCode);
            if (dId == null || cId == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "分录科目不存在（" + dCode + "/" + cCode + "），请先维护科目表");
            }
            opVoucherLineMapper.insert(line(c.getTenantId(), v.getId(), lineNo, summary, dId, dCode, null, "DEBIT", dAmt));
            lineNo += 10;
            opVoucherLineMapper.insert(line(c.getTenantId(), v.getId(), lineNo, summary, cId, cCode, c.getCustomerName(), "CREDIT", cAmt));
            lineNo += 10;
            debitTotal = debitTotal.add(dAmt);
            creditTotal = creditTotal.add(cAmt);
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分录合计借贷不平（借 " + debitTotal + " ≠ 贷 " + creditTotal + "）");
        }
        OpVoucher upd = new OpVoucher();
        upd.setId(v.getId());
        upd.setDebitTotal(debitTotal);
        upd.setCreditTotal(creditTotal);
        upd.setIsBalanced(1);
        opVoucherMapper.updateById(upd);
        log.info("[arflow] 对冲多行分录凭证 {} 生成（{} 行，借/贷合计 {}）", v.getVoucherNo(), rows.size(), debitTotal);
        return v.getVoucherNo();
    }

    /** 暂估冲回红字凭证（方向镜像）。 */
    public String generateClaimReversalVoucher(ArClaim c) {
        String debitCode = requireCode(c.getCreditAccount(), "冲回贷方科目");
        String creditCode = requireCode(c.getDebitAccount(), "冲回借方科目");
        BigDecimal amount = claimAmount(c);
        return createVoucher(c.getTenantId(), c.getCompanyId(), debitCode, creditCode,
                amount, "收入暂估冲回-" + nullToBlank(c.getBillNo()), "AR_CLAIM_REVERSE", c.getId(),
                c.getBillNo(), c.getCustomerName());
    }

    /** AP付款服务申请过账凭证：借 2202 应付账款 / 贷 1002 银行存款。 */
    public String generatePaymentVoucher(Long tenantId, Long companyId, BigDecimal amount,
                                         String vendorName, String billNo, Long docId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款凭证金额必须大于0");
        }
        return createVoucher(tenantId, companyId, "2202", "1002", amount,
                "付款服务申请过账-" + nullToBlank(billNo), "AP_REQUEST", docId, billNo, vendorName);
    }

    /** 票据审单入账凭证：借 1121 应收票据 / 贷 1122 应收账款（票据结算应收）。 */
    public String generateNoteVoucher(Long tenantId, Long companyId, BigDecimal amount,
                                      String customerName, String billNo, Long docId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据入账凭证金额必须大于0");
        }
        return createVoucher(tenantId, companyId, "1121", "1122", amount,
                "票据入账-" + nullToBlank(billNo), "AR_NOTE", docId, billNo, customerName);
    }

    /** 票据托收到账凭证：借 1002 银行存款 / 贷 1121 应收票据。 */
    public String generateNoteCollectVoucher(Long tenantId, Long companyId, BigDecimal amount,
                                             String customerName, String billNo, Long docId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "托收入账凭证金额必须大于0");
        }
        return createVoucher(tenantId, companyId, "1002", "1121", amount,
                "票据托收入账-" + nullToBlank(billNo), "AR_NOTE_COLLECT", docId, billNo, customerName);
    }

    /**
     * 汇票贴现凭证（贴现款与贴现利息单独记账，附录11.3.2）：
     * 借 1002 银行存款（贴现净额）+ 借 660302 贴现利息 / 贷 1121 应收票据（票面额）。
     */
    public String generateNoteDiscountVoucher(Long tenantId, Long companyId, BigDecimal faceAmount,
                                              BigDecimal netAmount, BigDecimal interest,
                                              String customerName, String billNo, Long docId) {
        if (faceAmount == null || faceAmount.signum() <= 0
                || netAmount == null || netAmount.signum() <= 0
                || interest == null || interest.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "贴现凭证金额不合法");
        }
        String existed = findExistingVoucher("AR_NOTE_DISCOUNT", docId);
        if (existed != null) {
            return existed;
        }
        LocalDate today = LocalDate.now();
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(companyId);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(docId);
        v.setSourceType("AR_NOTE_DISCOUNT");
        v.setSourceDocType("AR_NOTE_DISCOUNT");
        v.setSourceDocId(docId);
        v.setSummary("汇票贴现-" + nullToBlank(billNo));
        v.setStatus("DRAFT");
        v.setCreateTime(LocalDateTime.now());
        opVoucherMapper.insert(v);

        Long bankId = findSubjectIdByCode("1002");
        Long interestId = findSubjectIdByCode("660302");
        Long noteId = findSubjectIdByCode("1121");
        opVoucherLineMapper.insert(line(tenantId, v.getId(), 10, "汇票贴现-贴现净额", bankId, "1002", customerName, "DEBIT", netAmount));
        if (interest.signum() > 0) {
            opVoucherLineMapper.insert(line(tenantId, v.getId(), 20, "汇票贴现-贴现利息", interestId, "660302", null, "DEBIT", interest));
        }
        opVoucherLineMapper.insert(line(tenantId, v.getId(), 30, "汇票贴现-票面注销", noteId, "1121", customerName, "CREDIT", faceAmount));

        OpVoucher upd = new OpVoucher();
        upd.setId(v.getId());
        upd.setDebitTotal(netAmount.add(interest));
        upd.setCreditTotal(faceAmount);
        upd.setIsBalanced(netAmount.add(interest).compareTo(faceAmount) == 0 ? 1 : 0);
        opVoucherMapper.updateById(upd);
        log.info("[arflow] 贴现凭证 {} 生成（净额 {} + 利息 {} / 票面 {}）", v.getVoucherNo(), netAmount, interest, faceAmount);
        return v.getVoucherNo();
    }

    /** 应收应付对冲凭证：借 2202 应付账款 / 贷 1122 应收账款（同时核销应收与应付）。 */
    public String generateOffsetVoucher(Long tenantId, Long companyId, BigDecimal amount,
                                        String customerName, String billNo, Long docId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对冲凭证金额必须大于0");
        }
        return createVoucher(tenantId, companyId, "2202", "1122", amount,
                "应收应付对冲-" + nullToBlank(billNo), "AR_AP_OFFSET", docId, billNo, customerName);
    }

    /** 月结过总账汇总凭证：借 1122 应收账款 / 贷 6001 主营业务收入（账期汇总模拟推送 ERP 总账）。 */
    public String generateMonthCloseVoucher(Long tenantId, Long companyId, BigDecimal amount,
                                            String summary, Long docId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结过总账凭证金额必须大于0");
        }
        String voucherNo = createVoucher(tenantId, companyId, "1122", "6001", amount,
                nullToBlank(summary), "AR_MONTH_CLOSE", docId, summary, null);
        // 月结链自锁：月结链结转凭证「制单即审」——生成后立即走共享凭证正规过账通道
        // （GlProfitCarryService:189 损益结转同一范式），不再以 DRAFT 停留；否则本链制造的 DRAFT
        // 会撞 GlMonthCloseService.close()「期间无 DRAFT 方可月结」守卫，月结链自己过不去。
        autoPostMonthCloseVoucher(docId);
        return voucherNo;
    }

    /**
     * 月结过总账凭证制单即审（DRAFT → POSTED，过账人＝当前月结操作人并记录）。
     * <p>复用共享凭证正规过账通道 {@code VoucherService.postSystem}（含公司可见性、期间开账、
     * 分录非空、末级科目、辅助核算、借贷平衡整套校验，写 post_user_id/post_time 并落总账）；
     * 月结结转属月结作业的系统动作、无人工审核环节，与 GlProfitCarryService 损益结转同一
     * 制审分离豁免规则（通道内留 warn 日志供事后核查），不自实现过账、不裸 UPDATE status。
     * <p>去重：仅 DRAFT 才过账（重跑 postGl 时同源凭证已 POSTED 则跳过）；VOID 不复活。</p>
     */
    private void autoPostMonthCloseVoucher(Long docId) {
        OpVoucher v = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, "AR_MONTH_CLOSE")
                .eq(OpVoucher::getSourceDocId, docId)
                .ne(OpVoucher::getStatus, "VOID")
                .orderByDesc(OpVoucher::getId)
                .last("LIMIT 1"));
        if (v == null || !"DRAFT".equals(v.getStatus())) {
            return;
        }
        LoginUser lu = UserContext.get();
        Long operUser = lu == null ? null : lu.getUserId();
        voucherPostService.postSystem(v.getId(), operUser);
        log.info("[arflow] 月结过总账凭证 {} 制单即审（POSTED，过账人 {}，月结单 {}）",
                v.getVoucherNo(), operUser, docId);
    }

    private String findExistingVoucher(String sourceType, Long sourceDocId) {
        OpVoucher existed = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, sourceType)
                .eq(OpVoucher::getSourceDocId, sourceDocId)
                .ne(OpVoucher::getStatus, "VOID")
                .last("LIMIT 1"));
        return existed == null ? null : existed.getVoucherNo();
    }

    // 内部

    private BigDecimal claimAmount(ArClaim c) {
        BigDecimal amt = c.getHeadquartersAmount() != null ? c.getHeadquartersAmount() : c.getAmount();
        return nvl(amt);
    }

    private String createVoucher(Long tenantId, Long companyId, String debitCode, String creditCode,
                                 BigDecimal amount, String summary, String sourceType, Long sourceDocId,
                                 String billNo, String partnerName) {
        // 同源判重（去重范式）：同 sourceType + sourceDocId 已有凭证（非VOID）直接返回其凭证号
        OpVoucher existed = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, sourceType)
                .eq(OpVoucher::getSourceDocId, sourceDocId)
                .ne(OpVoucher::getStatus, "VOID")
                .last("LIMIT 1"));
        if (existed != null) {
            log.warn("[arflow] 单据 {} 已存在凭证 {}，跳过重复生成（幂等）", billNo, existed.getVoucherNo());
            return existed.getVoucherNo();
        }
        LocalDate today = LocalDate.now();
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(companyId);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(sourceDocId);
        v.setSourceType(sourceType);
        v.setSourceDocType(sourceType);
        v.setSourceDocId(sourceDocId);
        v.setSummary(summary);
        v.setStatus("DRAFT");
        v.setCreateTime(LocalDateTime.now());
        opVoucherMapper.insert(v);

        Long debitSubjectId = findSubjectIdByCode(debitCode);
        Long creditSubjectId = findSubjectIdByCode(creditCode);
        OpVoucherLine dr = line(tenantId, v.getId(), 10, summary + "-借",
                debitSubjectId, debitCode, null, "DEBIT", amount);
        OpVoucherLine cr = line(tenantId, v.getId(), 20, summary + "-贷",
                creditSubjectId, creditCode, partnerName, "CREDIT", amount);
        opVoucherLineMapper.insert(dr);
        opVoucherLineMapper.insert(cr);

        OpVoucher upd = new OpVoucher();
        upd.setId(v.getId());
        upd.setDebitTotal(amount);
        upd.setCreditTotal(amount);
        upd.setIsBalanced(1);
        opVoucherMapper.updateById(upd);
        log.info("[arflow] 凭证 {} 生成（借 {}/贷 {} 金额 {}）", v.getVoucherNo(), debitCode, creditCode, amount);
        return v.getVoucherNo();
    }

    private OpVoucherLine line(Long tenantId, Long vId, int no, String summary,
                               Long subjectId, String code, String partnerName, String dir, BigDecimal amount) {
        OpVoucherLine l = new OpVoucherLine();
        l.setTenantId(tenantId);
        l.setVoucherId(vId);
        l.setLineNo(no);
        l.setSummary(summary);
        l.setSubjectId(subjectId);
        l.setSubjectCode(code);
        l.setSubjectName(subjectNameOf(code, subjectId));
        l.setDirection(dir);
        l.setAmount(amount);
        l.setCurrencyCode("CNY");
        l.setCreateTime(LocalDateTime.now());
        return l;
    }

    private String subjectNameOf(String code, Long subjectId) {
        if (subjectId == null) {
            return code;
        }
        MdSubject s = mdSubjectMapper.selectById(subjectId);
        return s == null || s.getSubjectName() == null ? code : s.getSubjectName();
    }

    private Long findSubjectIdByCode(String code) {
        List<MdSubject> list = mdSubjectMapper.selectList(
                new LambdaQueryWrapper<MdSubject>().eq(MdSubject::getSubjectCode, code).last("limit 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private String nextVoucherNo() {
        try {
            return numberRuleService.next("VOUCHER");
        } catch (Exception e) {
            return "JZ" + System.currentTimeMillis();
        }
    }

    private String requireCode(String code, String label) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "未配置，无法生成凭证（请先在单据上补录科目）");
        }
        return code.trim();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nullToBlank(String v) {
        return v == null ? "" : v;
    }
}
