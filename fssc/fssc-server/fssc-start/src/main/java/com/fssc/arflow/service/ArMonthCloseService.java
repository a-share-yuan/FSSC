// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.arflow.entity.ArMonthClose;
import com.fssc.arflow.mapper.ArMonthCloseMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.system.service.PeriodLockService;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * AR月末结账服务（蓝图月结管理 9.6，状态机4.5）：
 * 应收月结：发起→清接口行→扛数据→差异调整→过总账→数据核对→关闭账期；
 * 成本月结：发起→扛数据→差异调整→过总账→数据核对→关闭账期（无清接口行步骤）。
 * 扛数快照：应收=当月应收单合计；成本=当月费用/成本类凭证借方合计。
 * 数据核对超容差（ar.recon.tolerance-amt-pct）置差异未消除，须重新调整后再核对；
 * 关闭账期后 isClosed(period)=true，当月不允许再录入业务（蓝图规则6.5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArMonthCloseService {

    /** 成本月结扛数的凭证来源类型（费用/成本/折旧） */
    private static final String[] COST_SOURCE_TYPES = {"EXPENSE", "EXPENSE_PAY", "STOCK_COST_SETTLE", "ASSET_DEPRECIATION"};

    private final ArMonthCloseMapper mapper;
    private final RcReceivableMapper receivableMapper;
    private final OpVoucherMapper voucherMapper;
    private final ArFlowVoucherService voucherService;
    private final ConfigService configService;
    private final PeriodLockService periodLockService;

    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 发起月结：同一公司同一期间同一类型不允许存在未完成月结单。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose init(Long companyId, String period, String closeType, String remark) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结期间格式必须为 yyyy-MM");
        }
        if (!ArMonthClose.T_COST.equals(closeType)) {
            closeType = ArMonthClose.T_RECEIVABLE;
        }
        Long cid = companyId == null ? 1L : companyId;
        Long exists = mapper.selectCount(new LambdaQueryWrapper<ArMonthClose>()
                .eq(ArMonthClose::getCompanyId, cid)
                .eq(ArMonthClose::getPeriod, period)
                .eq(ArMonthClose::getCloseType, closeType)
                // 已关闭/已作废的旧单不占用本期间（作废后可重新发起）
                .notIn(ArMonthClose::getStatus, ArMonthClose.ST_CLOSED, ArMonthClose.ST_VOID));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 已存在未完成的" + typeName(closeType) + "单");
        }
        ArMonthClose c = new ArMonthClose();
        c.setCloseNo("ARC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        c.setCompanyId(cid);
        c.setPeriod(period);
        c.setCloseType(closeType);
        c.setOperator(currentUser());
        c.setRemark(remark);
        c.setStatus(ArMonthClose.ST_INIT);
        c.setDiffFlag(0);
        mapper.insert(c);
        return c;
    }

    /** 清接口行（仅应收月结）：清理 ERP 接口表临时数据——模拟清点当期未推送成功的旧接口行。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose clearInterface(Long id) {
        ArMonthClose c = require(id);
        if (ArMonthClose.T_COST.equals(c.getCloseType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "成本月结无「清接口行」步骤（蓝图流程28/31）");
        }
        requireStatus(c, ArMonthClose.ST_INIT, "清接口行");
        String auditOldStatus = c.getStatus();
        c.setStatus(ArMonthClose.ST_INTERFACE_CLEARED);
        c.setOperator(currentUser());
        mapper.updateById(c);
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), Collections.singletonMap("status", auditOldStatus),
                Collections.singletonMap("status", c.getStatus()), "清接口行");
        return c;
    }

    /** 扛数据：从业务表抓取当期数据快照（应收=应收单合计；成本=成本类凭证借方合计）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose pullData(Long id) {
        ArMonthClose c = require(id);
        String expect = ArMonthClose.T_COST.equals(c.getCloseType())
                ? ArMonthClose.ST_INIT : ArMonthClose.ST_INTERFACE_CLEARED;
        requireStatus(c, expect, "扛数据");
        String auditOldStatus = c.getStatus();
        BigDecimal auditOldPulledAmount = c.getPulledAmount();
        Integer auditOldPulledCount = c.getPulledCount();
        YearMonth ym = YearMonth.parse(c.getPeriod());
        LocalDate begin = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        if (ArMonthClose.T_COST.equals(c.getCloseType())) {
            List<OpVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getSourceType, (Object[]) COST_SOURCE_TYPES)
                    .eq(OpVoucher::getCompanyId, c.getCompanyId())
                    .eq(OpVoucher::getDelFlag, 0)
                    .ge(OpVoucher::getVoucherDate, begin)
                    .le(OpVoucher::getVoucherDate, end)
                    .ne(OpVoucher::getStatus, "VOID"));
            BigDecimal sum = vouchers.stream()
                    .map(v -> v.getDebitTotal() == null ? BigDecimal.ZERO : v.getDebitTotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            c.setPulledAmount(sum);
            c.setPulledCount(vouchers.size());
        } else {
            List<RcReceivable> rows = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                    .eq(RcReceivable::getCompanyId, c.getCompanyId())
                    .ge(RcReceivable::getBillDate, begin)
                    .le(RcReceivable::getBillDate, end));
            BigDecimal sum = rows.stream()
                    .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            c.setPulledAmount(sum);
            c.setPulledCount(rows.size());
        }
        c.setStatus(ArMonthClose.ST_DATA_PULLED);
        c.setOperator(currentUser());
        mapper.updateById(c);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("pulledAmount", auditOldPulledAmount);
        auditBefore.put("pulledCount", auditOldPulledCount);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("pulledAmount", c.getPulledAmount());
        auditAfter.put("pulledCount", c.getPulledCount());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "扛数据");
        return c;
    }

    /** 差异调整：记录调整说明，可反复执行；差异未消除标记清除待重新核对。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose adjust(Long id, String note) {
        ArMonthClose c = require(id);
        if (!ArMonthClose.ST_DATA_PULLED.equals(c.getStatus()) && !ArMonthClose.ST_GL_POSTED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo() + " 当前状态不可差异调整");
        }
        String auditOldStatus = c.getStatus();
        String auditOldAdjustNote = c.getAdjustNote();
        Integer auditOldDiffFlag = c.getDiffFlag();
        c.setAdjustNote(note);
        c.setDiffFlag(0);
        if (ArMonthClose.ST_DATA_PULLED.equals(c.getStatus())) {
            c.setStatus(ArMonthClose.ST_ADJUSTED);
        }
        c.setOperator(currentUser());
        mapper.updateById(c);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("adjustNote", auditOldAdjustNote);
        auditBefore.put("diffFlag", auditOldDiffFlag);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("adjustNote", c.getAdjustNote());
        auditAfter.put("diffFlag", c.getDiffFlag());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "差异调整");
        return c;
    }

    /** 过总账：生成账期汇总凭证（借1122/贷6001，模拟推送 ERP 总账）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose postGl(Long id) {
        ArMonthClose c = require(id);
        if (!ArMonthClose.ST_ADJUSTED.equals(c.getStatus()) && !ArMonthClose.ST_GL_POSTED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo() + " 须先完成扛数与差异调整");
        }
        BigDecimal amount = c.getPulledAmount() == null ? BigDecimal.ZERO : c.getPulledAmount();
        if (amount.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本期扛数为负（" + amount.toPlainString() + "），无可过总账数据；请核对红字应收单后再处理");
        }
        if (amount.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "扛数金额为0，无可过总账数据");
        }
        String voucherNo = voucherService.generateMonthCloseVoucher(c.getTenantId(), c.getCompanyId(), amount,
                typeName(c.getCloseType()) + "过总账-" + c.getPeriod(), c.getId());
        String auditOldStatus = c.getStatus();
        String auditOldVoucherNo = c.getVoucherNo();
        BigDecimal auditOldGlAmount = c.getGlAmount();
        c.setVoucherNo(voucherNo);
        c.setGlAmount(amount);
        c.setStatus(ArMonthClose.ST_GL_POSTED);
        c.setOperator(currentUser());
        mapper.updateById(c);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("voucherNo", auditOldVoucherNo);
        auditBefore.put("glAmount", auditOldGlAmount);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("voucherNo", c.getVoucherNo());
        auditAfter.put("glAmount", c.getGlAmount());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "过总账");
        return c;
    }

    /**
     * 数据核对：重新抓取当期数据与扛数快照比对，容差内通过（RECONCILED），
     * 超容差置差异未消除（留在 GL_POSTED，需调整后重新核对）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose reconcile(Long id) {
        ArMonthClose c = require(id);
        requireStatus(c, ArMonthClose.ST_GL_POSTED, "数据核对");
        YearMonth ym = YearMonth.parse(c.getPeriod());
        LocalDate begin = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        BigDecimal current;
        if (ArMonthClose.T_COST.equals(c.getCloseType())) {
            List<OpVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getSourceType, (Object[]) COST_SOURCE_TYPES)
                    .eq(OpVoucher::getCompanyId, c.getCompanyId())
                    .eq(OpVoucher::getDelFlag, 0)
                    .ge(OpVoucher::getVoucherDate, begin)
                    .le(OpVoucher::getVoucherDate, end)
                    .ne(OpVoucher::getStatus, "VOID"));
            current = vouchers.stream()
                    .map(v -> v.getDebitTotal() == null ? BigDecimal.ZERO : v.getDebitTotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            List<RcReceivable> rows = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                    .eq(RcReceivable::getCompanyId, c.getCompanyId())
                    .ge(RcReceivable::getBillDate, begin)
                    .le(RcReceivable::getBillDate, end));
            current = rows.stream()
                    .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String auditOldStatus = c.getStatus();
        BigDecimal auditOldDiffAmount = c.getDiffAmount();
        Integer auditOldDiffFlag = c.getDiffFlag();
        BigDecimal pulled = c.getPulledAmount() == null ? BigDecimal.ZERO : c.getPulledAmount();
        BigDecimal diff = current.subtract(pulled).abs();
        c.setDiffAmount(diff);
        BigDecimal tolerancePct = tolerancePct();
        BigDecimal allowed = pulled.abs().max(BigDecimal.ONE).multiply(tolerancePct);
        if (diff.compareTo(allowed) > 0) {
            c.setDiffFlag(1);
            c.setOperator(currentUser());
            mapper.updateById(c);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "数据核对未通过：快照 " + pulled + " vs 当前 " + current + "（差异 " + diff
                            + " 超容差 " + tolerancePct.multiply(BigDecimal.valueOf(100)) + "%），请差异调整后重新核对");
        }
        c.setDiffFlag(0);
        c.setStatus(ArMonthClose.ST_RECONCILED);
        c.setOperator(currentUser());
        mapper.updateById(c);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("diffAmount", auditOldDiffAmount);
        auditBefore.put("diffFlag", auditOldDiffFlag);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("diffAmount", c.getDiffAmount());
        auditAfter.put("diffFlag", c.getDiffFlag());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "数据核对通过");
        return c;
    }

    /** 关闭账期：核对通过方可关账；关闭后当月不允许再录入业务（isClosed 拦截）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose closePeriod(Long id) {
        ArMonthClose c = require(id);
        requireStatus(c, ArMonthClose.ST_RECONCILED, "关闭账期");
        String auditOldStatus = c.getStatus();
        c.setStatus(ArMonthClose.ST_CLOSED);
        c.setClosedTime(LocalDateTime.now());
        c.setOperator(currentUser());
        mapper.updateById(c);
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), Collections.singletonMap("status", auditOldStatus),
                Collections.singletonMap("status", c.getStatus()), "关闭账期");
        // 蓝图规则6.5：关闭账期后当月不允许再录入业务（sys_config 锁标记，各业务入口判定）
        periodLockService.lock(c.getCompanyId(), c.getPeriod());
        // 应收月结不再复写 md_period——期间状态机唯一归属总账月结审批末节点。
        // 本子账的「当月不再录入」由上面 periodLockService.lock 承担，无需撬动会计期间表。
        log.info("[arflow] {} 月结 {} 完成，期间 {} 已关闭（会计期间由总账月结末节点结账）", typeName(c.getCloseType()), c.getCloseNo(), c.getPeriod());
        return c;
    }

    /**
     * 反月结（41号审查报告 同源整改）：CLOSED → RECONCILED，
     * 账期解锁（ar.period.lock），原因必填并追加进 remark 记录（不再动 md_period）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose unClose(Long id, String reason) {
        ArMonthClose c = require(id);
        if (!ArMonthClose.ST_CLOSED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo() + " 未关闭账期，无需反月结");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "反月结必须填写原因（审计记录要求）");
        }
        String auditOldStatus = c.getStatus();
        String auditOldRemark = c.getRemark();
        c.setStatus(ArMonthClose.ST_RECONCILED);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String mark = "[反月结 " + stamp + " " + currentUser() + "] " + reason;
        c.setRemark(c.getRemark() == null || c.getRemark().isBlank() ? mark : c.getRemark() + "；" + mark);
        mapper.updateById(c);
        // 反月结须清位关账人/关账时间（operator/closed_time）——updateById 对 null 值一律跳过，
        // 残留会让读面把"已重开"的单项继续显示成"某时刻已关闭账期"；清列走 UpdateWrapper.set(null)，
        // 操作与原因已并入 remark 记录（不删行，可回溯反月结历史）。
        mapper.update(null, new LambdaUpdateWrapper<ArMonthClose>()
                .eq(ArMonthClose::getId, c.getId())
                .set(ArMonthClose::getClosedTime, null)
                .set(ArMonthClose::getOperator, null));
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("remark", c.getRemark());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "反月结");
        periodLockService.unlock(c.getCompanyId(), c.getPeriod());
        // 与 closePeriod 对称——反月结只解本子账的账期锁，不撬 md_period
        log.info("[arflow] {} 月结 {} 反月结，期间 {} 解锁：{}", typeName(c.getCloseType()), c.getCloseNo(), c.getPeriod(), reason);
        return c;
    }

    /**
     * 作废（最终状态 VOID）：未关闭账期的月结单整体废弃——置 VOID、原因并入 remark
     * 记录（不删行）；已关闭账期须先反月结，不能直接作废（账期锁/期间联动只在关闭账期
     * 时建立，故作废路径无需解锁，也避免误放同期间他单的锁）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArMonthClose voidBill(Long id, String reason) {
        ArMonthClose c = require(id);
        if (ArMonthClose.ST_VOID.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo() + " 已作废");
        }
        if (ArMonthClose.ST_CLOSED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo() + " 已关闭账期，须先反月结，不能直接作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因（审计记录要求）");
        }
        String auditOldStatus = c.getStatus();
        String auditOldRemark = c.getRemark();
        c.setStatus(ArMonthClose.ST_VOID);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String mark = "[作废 " + stamp + " " + currentUser() + "] " + reason;
        c.setRemark(c.getRemark() == null || c.getRemark().isBlank() ? mark : c.getRemark() + "；" + mark);
        mapper.updateById(c);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("remark", c.getRemark());
        auditTrailService.recordUpdate("AR_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ar_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "作废月结单");
        log.info("[arflow] {} 月结 {} 作废：{}", typeName(c.getCloseType()), c.getCloseNo(), reason);
        return c;
    }

    /** 账期关闭判定（蓝图规则6.5：关闭账期不允许再录入当月业务）。 */
    public boolean isClosed(Long companyId, String period) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<ArMonthClose>()
                .eq(ArMonthClose::getCompanyId, companyId == null ? 1L : companyId)
                .eq(ArMonthClose::getPeriod, period)
                .eq(ArMonthClose::getStatus, ArMonthClose.ST_CLOSED));
        return n != null && n > 0;
    }

    public List<ArMonthClose> list(String closeType, String status, String period) {
        LambdaQueryWrapper<ArMonthClose> q = new LambdaQueryWrapper<>();
        if (closeType != null && !closeType.isBlank()) {
            q.eq(ArMonthClose::getCloseType, closeType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(ArMonthClose::getStatus, status);
        }
        if (period != null && !period.isBlank()) {
            q.eq(ArMonthClose::getPeriod, period);
        }
        return mapper.selectList(q.orderByDesc(ArMonthClose::getId));
    }

    // 内部

    private BigDecimal tolerancePct() {
        String v = configService.getValue("ar.recon.tolerance-amt-pct");
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(v.trim()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private static String typeName(String closeType) {
        return ArMonthClose.T_COST.equals(closeType) ? "成本月结" : "应收月结";
    }

    private ArMonthClose require(Long id) {
        ArMonthClose c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单不存在 id=" + id);
        }
        return c;
    }

    private void requireStatus(ArMonthClose c, String expected, String action) {
        if (!expected.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月结单 " + c.getCloseNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + c.getStatus());
        }
    }

    private String currentUser() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUsername();
    }
}
