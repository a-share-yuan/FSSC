// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.budget.service.BudgetService;
import com.fssc.expense.dto.LoanCandidateDTO;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.entity.ExpTravel;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillRelationMapper;
import com.fssc.expense.mapper.ExpLoanOffsetMapper;
import com.fssc.expense.mapper.ExpTravelMapper;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdExpenseStandard;
import com.fssc.masterdata.mapper.MdExpenseStandardMapper;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 单据细分服务：差旅明细（exp_travel）、借款冲抵（exp_loan_offset）、单据关联（exp_bill_relation）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseDetailService {

    private final ExpTravelMapper expTravelMapper;
    private final ExpLoanOffsetMapper expLoanOffsetMapper;
    private final ExpBillRelationMapper expBillRelationMapper;
    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final MdExpenseStandardMapper mdExpenseStandardMapper;
    private final SysConfigMapper sysConfigMapper;
    private final BudgetService budgetService;
    private final OrgGuard orgGuard;

    // 差旅明细

    /** 保存差旅行（整表替换，仅 DRAFT） */
    @Transactional(rollbackFor = Exception.class)
    public void saveTravels(Long billId, List<ExpTravel> travels) {
        ExpBill bill = requireBill(billId);
        if (!"DRAFT".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可维护差旅行");
        }
        assertTravelRows(travels);
        expTravelMapper.delete(new LambdaQueryWrapper<ExpTravel>().eq(ExpTravel::getBillId, billId));
        // 境外凭证合规⑤：开关关闭时补贴超标并入个税标记跳过（回退旧行为）
        boolean overseasCompliance = isOverseasComplianceEnabled();
        if (travels != null) {
            int no = 1;
            int allowanceAutoCount = 0;
            for (ExpTravel t : travels) {
                t.setId(null);
                t.setBillId(billId);
                t.setLineNo(no++);
                // 海外报账③：days 缺失按 起止日期含首末日全算 自动补
                // （end − begin + 1；半天/比例折算法留待配置化扩展）
                t.setDays(AbroadStandardRule.resolveDays(t.getDays(), t.getBeginDate(), t.getEndDate()));
                // 补贴按天自动核算：days 有值且补贴未填 → allowance = days × 境外补贴标准 allowance_daily；
                // 查不到补贴标准则跳过不报错（保持人工填报规则）
                BigDecimal allowanceDaily = null;
                if (t.getDays() != null && t.getDays().compareTo(BigDecimal.ZERO) > 0
                        && nvl(t.getAllowanceAmount()).compareTo(BigDecimal.ZERO) == 0) {
                    allowanceDaily = findAllowanceDaily(billId);
                    BigDecimal auto = AbroadStandardRule.calcAllowance(t.getDays(), allowanceDaily);
                    if (auto != null) {
                        t.setAllowanceAmount(auto);
                        allowanceAutoCount++;
                    }
                }
                // 境外凭证合规⑤：境外差旅行（destCountry 非空且非国内）补贴超过 标准/日×天数 时，
                // 超标部分并入工资薪金计税 → allowance_taxable=1（19号公告+个税规则）
                if (overseasCompliance && t.getDestCountry() != null && !t.getDestCountry().isBlank()
                        && !AbroadStandardRule.isDomestic(t.getDestCountry())) {
                    if (allowanceDaily == null) {
                        allowanceDaily = findAllowanceDaily(billId);
                    }
                    BigDecimal over = OverseasVoucherRule.allowanceTaxable(
                            t.getAllowanceAmount(), allowanceDaily, t.getDays());
                    if (over.compareTo(BigDecimal.ZERO) > 0) {
                        t.setAllowanceTaxable(1);
                        log.info("差旅行补贴超标 billId={} 行程行={}（{}）超标 {} 元，将并入工资薪金计税",
                                billId, t.getLineNo(), t.getDestCountry(), over);
                    } else {
                        t.setAllowanceTaxable(0);
                    }
                }
                if (t.getTotalAmount() == null) {
                    // 兜底合计原先只加 住宿+长途交通+补贴+其他，漏了市内交通费
                    // ——前端「小计」一直把这栏算进去，两边不一致时草稿重开就会出现"分量 5,400 / 合计 6,000"。
                    BigDecimal total = nvl(t.getHotelAmount()).add(nvl(t.getTrafficAmount()))
                            .add(nvl(t.getAllowanceAmount())).add(nvl(t.getLocalTrafficAmount()))
                            .add(nvl(t.getOtherAmount()));
                    t.setTotalAmount(total);
                }
                expTravelMapper.insert(t);
            }
            if (allowanceAutoCount > 0) {
                log.info("差旅行补贴按天自动核算 billId={} 共 {} 行按 天数×补贴/日 生成出差补贴", billId, allowanceAutoCount);
            }
        }
    }

    /**
     * 服务端兜底：前端已禁用倒置日期，但接口直调仍可写入。
     * 重复行程按「姓名优先、无姓名用 travelerId」判定，与 PreApply.travelDup 同一规则。
     */
    private void assertTravelRows(List<ExpTravel> travels) {
        if (travels == null || travels.isEmpty()) return;
        for (int i = 0; i < travels.size(); i++) {
            ExpTravel t = travels.get(i);
            if (t.getBeginDate() != null && t.getEndDate() != null
                    && t.getEndDate().isBefore(t.getBeginDate())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 行行程结束日期早于开始日期");
            }
        }
        for (int i = 0; i < travels.size(); i++) {
            ExpTravel a = travels.get(i);
            String aKey = travelIdentity(a);
            if (aKey == null || a.getBeginDate() == null) continue;
            for (int j = i + 1; j < travels.size(); j++) {
                ExpTravel b = travels.get(j);
                String bKey = travelIdentity(b);
                if (bKey == null || b.getBeginDate() == null) continue;
                if (aKey.equals(bKey) && a.getBeginDate().equals(b.getBeginDate())) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "同一出差人同一天存在重复行程：" + travelDisplayName(a)
                                    + "（" + a.getBeginDate() + "）");
                }
            }
        }
    }

    private String travelIdentity(ExpTravel t) {
        String name = t.getTravelerName();
        if (name != null && !name.isBlank()) return "NAME:" + name.trim();
        return t.getTravelerId() == null ? null : "ID:" + t.getTravelerId();
    }

    private String travelDisplayName(ExpTravel t) {
        String name = t.getTravelerName();
        return name == null || name.isBlank() ? String.valueOf(t.getTravelerId()) : name;
    }

    /**
     * 取补贴/日标准（allowance_daily）：优先匹配本单费用明细行 itemId 的 abroad=1 补贴标准，
     * 无则退取本租户任一 abroad=1 且 allowance_daily 非空的标准（优先级高者）；均无返回 null。
     */
    private BigDecimal findAllowanceDaily(Long billId) {
        List<Long> itemIds = expBillLineMapper.selectList(
                        new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, billId))
                .stream().map(ExpBillLine::getItemId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        MdExpenseStandard std = selectAllowanceStandard(itemIds.isEmpty() ? null : itemIds);
        if (std == null && !itemIds.isEmpty()) {
            std = selectAllowanceStandard(null); // itemId 无匹配 → 本租户任一 abroad=1 补贴标准
        }
        return std == null ? null : std.getAllowanceDaily();
    }

    private MdExpenseStandard selectAllowanceStandard(List<Long> itemIds) {
        LambdaQueryWrapper<MdExpenseStandard> w = new LambdaQueryWrapper<>();
        w.eq(MdExpenseStandard::getAbroad, 1).isNotNull(MdExpenseStandard::getAllowanceDaily);
        if (itemIds != null) {
            w.in(MdExpenseStandard::getItemId, itemIds);
        }
        w.orderByDesc(MdExpenseStandard::getPriority).last("limit 1");
        return mdExpenseStandardMapper.selectOne(w);
    }

    /**
     * 境外凭证合规⑤总开关：sys_config overseas.compliance_enabled（种子=1，租户拦截器自动过滤）。
     * 无记录/读取异常一律视为开启（参照 ExpenseBillService），值=0 时回退旧行为。
     */
    private boolean isOverseasComplianceEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, "overseas.compliance_enabled")
                    .last("limit 1"));
            return c == null || c.getConfigValue() == null || !"0".equals(c.getConfigValue().trim());
        } catch (Exception e) {
            log.warn("读取 overseas.compliance_enabled 失败，默认开启境外凭证合规", e);
            return true;
        }
    }

    public List<ExpTravel> travels(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill != null) {
            orgGuard.assertVisible(bill.getCompanyId(), bill.getApplyUserId());
        }
        return expTravelMapper.selectList(new LambdaQueryWrapper<ExpTravel>()
                .eq(ExpTravel::getBillId, billId).orderByAsc(ExpTravel::getLineNo));
    }

    // 借款冲抵

    /**
     * 添加借款冲抵：记录冲抵 + 更新单据 offset_amount/payable_amount。
     * 强制冲销（#2 借款余额台账）：当指定 loanBillId（借款单）时，校验冲抵金额 ≤ 该借款单可用余额。
     *
     * <p><b>提交环节</b>：报销冲抵（CLAIM）与还款冲抵（REPAY）<b>两环节同一规则</b>，
     * 提交期一律<b>只登记占用量</b>——载体是现列现表的
     * {@code exp_bill_relation(LOAN_CLAIM).use_amount}，不写 {@code exp_loan_offset} 流水、
     * 不减 {@code loan_balance}、不迁借款单状态；冲抵流水与余额减记改由审批通过环节落
     * （{@code ExpenseWfEventListener.offsetLoansByClaim} / {@code repayLoan}），余额由那两环节按
     * 「借款额 − Σ现存冲抵流水」<b>绝对量重算</b>（甲②），报销/还款单驳回、撤回、反审核时
     * 在途占用自然释放或流水作废重算（甲③）。三段同波，缺一即台账失真。治的三条病灶：
     * <ol>
     *  <li>提单期就把台账真值扣掉 ⇒ 审批链对「是否已结清」不具控制力（同病灶的残留半条，
     *      REPAY 环节曾用此形，现在与 CLAIM 环节并到同一个环节上）；</li>
     *  <li>草稿改额只加不减 ⇒ {@code offset_amount} 翻倍、余额越改越少（本方法原「累加写表头」）；</li>
     *  <li>报销单被驳回/撤回无人回补（{@code restoreLoanBalanceOnReject} 只管 REPAY 环节）
     *      ⇒ 借款余额永久停在扣后值。</li>
     * </ol>
     *
     * <p><b>登记人（③）</b>：本方法不再 INSERT {@code exp_loan_offset}，故 {@code create_by}
     * 的显式补写随之下移到两处通过环节——{@code repayLoan}（一直是 {@code currentUserOr(申请人)}）与
     * {@code offsetLoansByClaim}（已按「恒写申请人」对齐到同一规则）。该列<b>没有</b>
     * {@code @TableField(fill=...)}，本仓库的 {@code FsscMetaObjectHandler} 是注解驱动填充，
     * 不显式 set 就是 NULL（{@code exp_loan} 表不存在，借款余额列在
     * {@code exp_bill.loan_balance}，其审计列由 {@code TenantAuditBase} 标了
     * {@code fill=INSERT_UPDATE}，走 {@code updateById} 才自动填）。
     *
     * <p>已知现有残口（此处不修，勿据此认为已修）：①前端「冲销借款」候选面默认额仍取
     * {@code row.loanBalance} 账面余额、未扣在途（{@code LoanApply.vue:341}/{@code useBill.js:759}），
     * 第二张草稿勾满额会走到本方法被 {@link #registerLoanOccupancy} 的服务端校验拒掉；
     * ②前端删行只 splice 本地数组、服务端无删除端点，旧登记不撤销 ⇒ 在途占用会锁在关系行上，
     * 直到该单被驳回/撤回/作废才释放。
     *
     * @return 关系行 id（在途登记的载体；不再是 exp_loan_offset id）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addLoanOffset(Long billId, ExpLoanOffset offset) {
        ExpBill bill = requireBill(billId);
        if (!"DRAFT".equals(bill.getStatus()) && !"APPROVING".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前状态不可冲抵借款: " + bill.getStatus());
        }
        // 冲抵金额必填且为正
        if (offset.getOffsetAmount() == null || offset.getOffsetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲抵金额必须大于 0");
        }
        offset.setOffsetType(offset.getOffsetType() == null ? "CLAIM" : offset.getOffsetType());
        // CLAIM／REPAY 同走占用登记：入参上的 offsetType 只是界面语义，提交期不再落流水，
        // 真正的流水类型由审批通过环节按单据 bill_type 判定（REPAY 单→repayLoan、CLAIM 单→offsetLoansByClaim）。
        return registerLoanOccupancy(bill, offset);
    }

    /**
     * 借款冲抵的在途登记（/391 甲①，CLAIM 与 REPAY 共用）：占用量写
     * {@code exp_bill_relation(LOAN_CLAIM).use_amount}，去重 upsert；
     * 借款单余额与冲抵流水一律留给审批通过环节，提单期不动台账。
     *
     * <p>可冲余额 = 借款单账面余额 − 其他<b>在途</b>（DRAFT/APPROVING）报销/还款单已占用量。
     * 余额不再于提单期扣减，故必须在服务端自己扣在途，否则同一笔在贷余额可被任意多张草稿重复全额勾走
     * ——这一句就是 R-18「还款/冲抵超额」的实际拦校验点（本方法末段）。
     *
     * <p>{@code relation_type} 沿用 {@code LOAN_CLAIM}：还款冲销也挂这个类型是 已现有的
     * 语义复用缺陷（枚举里没有还款型关系），不扩名称表、只按同一载体查询。
     *
     * @return 关系行 id（在途登记的载体；不再是 exp_loan_offset id）
     */
    private Long registerLoanOccupancy(ExpBill bill, ExpLoanOffset offset) {
        if (offset.getLoanBillId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲抵借款必须指定借款单");
        }
        ExpBill loan = expBillMapper.selectById(offset.getLoanBillId());
        if (loan == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "借款单不存在: " + offset.getLoanBillId());
        }
        if (!"LOAN".equals(loan.getBillType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲抵对象不是借款单: " + offset.getLoanBillId());
        }
        if (bill.getApplyUserId() != null && !bill.getApplyUserId().equals(loan.getApplyUserId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能冲销本人名下的借款单");
        }
        offset.setLoanBillNo(loan.getBillNo());
        BigDecimal bookBal = nvl(loan.getLoanBalance());
        BigDecimal transit = transitLoanOccupancy(loan.getId(), bill.getId());
        BigDecimal avail = bookBal.subtract(transit);
        if (offset.getOffsetAmount().compareTo(avail) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲抵金额(" + offset.getOffsetAmount()
                    + ")超过借款单可冲抵余额(" + avail + ")：账面余额 " + bookBal
                    + "，其中在途报销/还款单已占用 " + transit);
        }
        ExpBillRelation exist = expBillRelationMapper.selectOne(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, loan.getId())
                .eq(ExpBillRelation::getTargetBillId, bill.getId())
                .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM")
                .last("limit 1"));
        BigDecimal booked = exist == null ? BigDecimal.ZERO : nvl(exist.getUseAmount());
        Long relId;
        if (exist == null) {
            ExpBillRelation r = new ExpBillRelation();
            r.setTenantId(TenantContext.getTenantId());
            r.setSourceBillId(loan.getId());
            r.setTargetBillId(bill.getId());
            r.setRelationType("LOAN_CLAIM");
            r.setUseAmount(offset.getOffsetAmount());
            r.setCreateTime(LocalDateTime.now());
            expBillRelationMapper.insert(r);
            relId = r.getId();
        } else {
            ExpBillRelation upd = new ExpBillRelation();
            upd.setId(exist.getId());
            upd.setUseAmount(offset.getOffsetAmount());
            expBillRelationMapper.updateById(upd);
            relId = exist.getId();
        }
        // 表头按「本次 − 已登记」差额校正（甲①·病灶②）：同一单重复保存/改额不再无条件累加。
        applyOffsetToHeader(bill, offset.getOffsetAmount().subtract(booked));
        return relId;
    }

    /**
     * 该借款单被其他在途（未生效）报销／还款单占用的金额（报销与还款两条链同走关系行登记，故同一条规则查询）；
     * 已通过的单其冲抵额已落成流水并扣进 loan_balance，不再重复扣。
     */
    private BigDecimal transitLoanOccupancy(Long loanBillId, Long selfBillId) {
        List<ExpBillRelation> rels = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, loanBillId)
                .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM")
                .ne(selfBillId != null, ExpBillRelation::getTargetBillId, selfBillId));
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpBillRelation r : rels) {
            if (r.getTargetBillId() == null) {
                continue;
            }
            ExpBill t = expBillMapper.selectById(r.getTargetBillId());
            String st = t == null ? null : t.getStatus();
            if ("DRAFT".equals(st) || "APPROVING".equals(st)) {
                sum = sum.add(nvl(r.getUseAmount()));
            }
        }
        return sum;
    }

    /**
     * 借款冲抵候选面（配套）：列出<b>本人名下</b>仍有可冲抵余额的借款单，
     * 并吐出与放行校验同源的 {@code availableBalance = 账面余额 − 在途占用}。
     *
     * <p><b>为什么要扣在途</b>：候选面直接给前端 {@code loanBalance}（账面余额，<b>不含在途占用</b>），
     * 前端拿它当「可冲抵额」默认勾全额 ⇒ 第二张草稿必然撞本类 {@link #registerLoanOccupancy}
     * 的超额校验。所以把在途这层扣减<b>提前到候选面</b>，
     * 前端的默认值/上限与服务端放行规则同源，用户不必白填一遍表再吃一次拒。</p>
     *
     * <p>规则：① 只列本人（{@code apply_user_id = 当前用户}）——服务端本就只允许冲销本人借款；
     * ② 状态含 {@code APPROVED/PARTIAL_REPAID/REPAID}（借款被在途冲抵会迁 PARTIAL_REPAID，
     * 单查 APPROVED 会立刻失明）；③ {@code availableBalance ≤ 0} 的不返回（无可冲抵）；
     * ④ 公司维度按入参过滤（可空＝不过滤）。</p>
     *
     * @param companyId 公司ID（可空＝不过滤）
     * @param limit 返回上限（空或 ≤0 取 50，上限 200）
     */
    public List<LoanCandidateDTO> loanCandidates(Long companyId, Integer limit) {
        LoginUser u = UserContext.get();
        Long me = u == null ? null : u.getUserId();
        int cap = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        if (me == null) {
            return Collections.emptyList();
        }
        List<ExpBill> loans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getBillType, "LOAN")
                .eq(ExpBill::getApplyUserId, me)
                .in(ExpBill::getStatus, "APPROVED", "PARTIAL_REPAID", "REPAID")
                .gt(ExpBill::getLoanBalance, BigDecimal.ZERO)
                .eq(companyId != null, ExpBill::getCompanyId, companyId)
                .orderByAsc(ExpBill::getId)
                .last("limit " + cap));
        List<LoanCandidateDTO> out = new ArrayList<>(loans.size());
        for (ExpBill loan : loans) {
            BigDecimal book = nvl(loan.getLoanBalance());
            BigDecimal transit = transitLoanOccupancy(loan.getId(), null);
            BigDecimal available = book.subtract(transit);
            if (available.signum() <= 0) {
                continue;   // 已被在途占满：列出来也只能是死路
            }
            LoanCandidateDTO dto = new LoanCandidateDTO();
            dto.setId(loan.getId());
            dto.setBillNo(loan.getBillNo());
            dto.setTitle(loan.getTitle());
            dto.setTotalAmount(nvl(loan.getTotalAmount()));
            dto.setLoanBalance(book);
            dto.setAvailableBalance(available);
            dto.setApplyDate(loan.getApplyDate());
            out.add(dto);
        }
        return out;
    }

    /** 冲抵额记入报销单表头：offset_amount 按差额累加、payable_amount 随之重算（借款冲抵与预付冲销共用规则）。 */
    private void applyOffsetToHeader(ExpBill bill, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        BigDecimal totalOffset = nvl(bill.getOffsetAmount()).add(delta);
        ExpBill update = new ExpBill();
        update.setId(bill.getId());
        update.setOffsetAmount(totalOffset.signum() < 0 ? BigDecimal.ZERO : totalOffset);
        // 应付 = 核定规则 − 冲抵。规则优先级与 ExpenseBillService.approveDeduct 保持一致：
        // 核定付款 > 核定报销 > 申请金额（审批核减后按核定付款控制，防超付基于核定规则）
        BigDecimal base = bill.getApprovedPayAmount() != null ? bill.getApprovedPayAmount()
                : (bill.getApprovedAmount() != null ? bill.getApprovedAmount() : bill.getTotalAmount());
        BigDecimal payable = nvl(base).subtract(update.getOffsetAmount());
        update.setPayableAmount(payable.signum() < 0 ? BigDecimal.ZERO : payable);
        expBillMapper.updateById(update);
    }

    public List<ExpLoanOffset> loanOffsets(Long billId) {
        List<ExpLoanOffset> rows = new ArrayList<>(expLoanOffsetMapper.selectList(
                new LambdaQueryWrapper<ExpLoanOffset>().eq(ExpLoanOffset::getBillId, billId)));
        // 报销/还款冲抵的在途量不在提单期落流水，
        // 草稿「编辑重提」必须从 LOAN_CLAIM 关系行把它补回来，否则冲销借款清单整块看不见。
        // 已生效环节（审批后落的流水）优先，同一借款单不再重复补在途行。
        // 补出来的行 offsetType 统一按 CLAIM 呈现（关系表不存类型）——两环节的提交期登记已并为同一动作，
        // 该标签只影响回显、不影响写入数据库，真正的流水类型由审批通过环节按单据 bill_type 判定。
        List<ExpBillRelation> rels = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getTargetBillId, billId)
                .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM"));
        for (ExpBillRelation r : rels) {
            if (r.getSourceBillId() == null) {
                continue;
            }
            // 同一 (本单, 借款单) 已有流水行（CLAIM 或 REPAY 都算）＝该笔冲抵已在台账上，
            // 关系行只是同一笔的另一面视图，再补在途行就是双份（还款单的 LOAN_CLAIM 关系即此形）。
            boolean booked = rows.stream().anyMatch(o -> Objects.equals(o.getLoanBillId(), r.getSourceBillId()));
            if (booked) {
                continue;
            }
            ExpLoanOffset transit = new ExpLoanOffset();
            transit.setBillId(billId);
            transit.setLoanBillId(r.getSourceBillId());
            transit.setOffsetAmount(nvl(r.getUseAmount()));
            transit.setOffsetType("CLAIM");
            transit.setRemark("在途占用（未经审批，不占台账）");
            rows.add(transit);
        }
        fillTransitLoanBillNo(rows);
        return rows;
    }

    /** 在途合成行只带借款单 id，单据号回查补齐（关系表只存 id，界面按单号展示）。 */
    private void fillTransitLoanBillNo(List<ExpLoanOffset> rows) {
        Set<Long> missing = rows.stream()
                .filter(o -> o.getLoanBillNo() == null && o.getLoanBillId() != null)
                .map(ExpLoanOffset::getLoanBillId).collect(Collectors.toSet());
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, String> noById = new HashMap<>();
        for (ExpBill b : expBillMapper.selectBatchIds(missing)) {
            noById.put(b.getId(), b.getBillNo());
        }
        for (ExpLoanOffset o : rows) {
            if (o.getLoanBillNo() == null) {
                o.setLoanBillNo(noById.get(o.getLoanBillId()));
            }
        }
    }

    // 单据关联

    @Transactional(rollbackFor = Exception.class)
    public Long addRelation(Long sourceBillId, Long targetBillId, String relationType, BigDecimal useAmount) {
        // 勾稽校验（补齐 §1.4）：单据存在性 + 占用金额合法 + 累计占用 ≤ 源单据金额
        assertRelationUseAmount(sourceBillId, targetBillId, relationType, useAmount);

        // E2E-001 修复：同源同目标同类型已存在时不重复插入（草稿重复保存场景），
        // 仅更新占用金额，避免 LOAN_CLAIM 关系重复导致差额计算累加出错。
        ExpBillRelation exist = expBillRelationMapper.selectOne(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, sourceBillId)
                .eq(ExpBillRelation::getTargetBillId, targetBillId)
                .eq(relationType != null, ExpBillRelation::getRelationType, relationType)
                .last("limit 1"));
        if (exist != null) {
            ExpBillRelation upd = new ExpBillRelation();
            upd.setId(exist.getId());
            if (useAmount != null) {
                upd.setUseAmount(useAmount);
            }
            expBillRelationMapper.updateById(upd);
            return exist.getId();
        }

        ExpBillRelation r = new ExpBillRelation();
        r.setTenantId(TenantContext.getTenantId());
        r.setSourceBillId(sourceBillId);
        r.setTargetBillId(targetBillId);
        r.setRelationType(relationType);
        r.setUseAmount(useAmount);
        r.setCreateTime(LocalDateTime.now());
        expBillRelationMapper.insert(r);
        return r.getId();
    }

    /**
     * 单据关联占用校验（补齐 §1.4「累计报销占用 ≤ 申请金额」）。
     *
     * <p>source 为被占用方：APPLY_CLAIM=事前申请单、LOAN_CLAIM=借款单；
     * 同一 source 允许多次被报销关联（多次报销共用一张申请单），
     * 但 Σuse_amount 不得超过其单据金额——直接 insert 而没有任何校验的话，
     * 就能无限关联、导致超申请/超借款总额报销。
     */
    private void assertRelationUseAmount(Long sourceBillId, Long targetBillId,
                                         String relationType, BigDecimal useAmount) {
        if (sourceBillId == null || targetBillId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关联单据ID不能为空");
        }
        if (sourceBillId.equals(targetBillId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能关联单据自身");
        }
        ExpBill source = expBillMapper.selectById(sourceBillId);
        if (source == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源单据不存在: " + sourceBillId);
        }
        if (expBillMapper.selectById(targetBillId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标单据不存在: " + targetBillId);
        }
        if (useAmount == null) {
            return; // 历史调用可不携带金额，跳过占用校验
        }
        if (useAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "占用金额必须大于 0");
        }
        // 累计占用（含本次）不得超出源单据金额
        // 合规整改( ):排除本单自身在草稿保存阶段已写入的占用关联,否则提交时本单自占被重复计数,
        // 事前申请报销永远无法提交(已占用=本次来源均为同一 target)
        List<ExpBillRelation> existed = expBillRelationMapper.selectList(
                new LambdaQueryWrapper<ExpBillRelation>()
                        .eq(ExpBillRelation::getSourceBillId, sourceBillId)
                        .eq(relationType != null, ExpBillRelation::getRelationType, relationType)
                        .ne(targetBillId != null, ExpBillRelation::getTargetBillId, targetBillId));
        // 只统计在途/已批目标单的占用——草稿/已驳回/已作废单不占额度
        // WITHDRAWN（撤回）也在排除名单里——撤回后单据不可复活，
        // 但关系行按追溯规则保留，若仍计占用就会把借款/申请额度锁死在一张已终止的单上。
        BigDecimal used = BigDecimal.ZERO;
        for (ExpBillRelation e : existed) {
            ExpBill t = expBillMapper.selectById(e.getTargetBillId());
            String st = t == null || t.getStatus() == null ? "DRAFT" : t.getStatus();
            if ("DRAFT".equals(st) || "REJECTED".equals(st) || "WITHDRAWN".equals(st)
                    || "CANCELLED".equals(st) || "VOID".equals(st) || "CLOSED".equals(st)) {
                continue;
            }
            used = used.add(nvl(e.getUseAmount()));
        }
        // 原实现用 total_amount（借款总额）做上限，而报销冲抵时 useAmount 由前端硬取全额
        // （见 pickLoan 里 offsetAmount = row.loanBalance），
        // 于是「已占用」被记成整笔借款额、把尚未冲抵的差额一并锁死：
        //   借款 5000 → 报销 4600（占用记 5000）→ 还款 400 时 used(5000)+400 > limit(5000) 被拒，
        //   差额 400 永远还不了，借款单卡死在 PARTIAL_REPAID。
        // 修正后的统一规则：LOAN_CLAIM（借款被占用/被冲销）一律以借款未还余额为上限，
        // 且 used 只统计「非还款单」的占用——还款单本身是核销动作，不是对借款额度的再占用，
        // 若把它算进 used 会出现 used(4600报销 + 400还款)=5000、再加本次 400 ⇒ 5400 > 400 恒被拒。
        BigDecimal limit = nvl(source.getTotalAmount());
        if ("LOAN_CLAIM".equals(relationType)) {
            // 借款额度的权威规则是「未还余额」：total_amount − 已实际冲抵（exp_loan_offset/CLAIM）− 在途还款占用。
            // 这里不能叠加 used：它按关系行记占用（报销冲抵时按全额记），
            //   与 repaid 指向同一笔钱，两者相加会把同一笔占用算两遍 ⇒ 上限恒被击穿。
            //   所以 LOAN_CLAIM 分支只用余额规则，used 不参与。
            BigDecimal repaid = repaidAmountOfLoan(sourceBillId);
            // 排除本单自身：本次动作所在的还款单在草稿阶段已写过关系行，计入会变成「自己占用自己」
            BigDecimal pendingRepay = repaidAmountOfRepay(sourceBillId, targetBillId);
            limit = nvl(source.getTotalAmount()).subtract(repaid).subtract(pendingRepay);
            if (limit.signum() < 0) {
                limit = BigDecimal.ZERO;
            }
            if (useAmount.compareTo(limit) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "本次冲销(" + useAmount + ")超出借款可还余额(" + limit
                                + ")，借款总额 " + nvl(source.getTotalAmount())
                                + " 已冲抵 " + repaid + "，在途还款占用 " + pendingRepay
                                + "，可还 " + limit);
            }
            return;
        }
        if (used.add(useAmount).compareTo(limit) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "累计占用(" + used.add(useAmount) + ")超出" + typeName(relationType) + "金额(" + limit
                            + ")，已占用 " + used + "，本次 " + useAmount);
        }
    }

    /** 借款已被报销实际冲抵的金额（exp_loan_offset 里 offset_type=CLAIM 的生效合计）。 */
    private BigDecimal repaidAmountOfLoan(Long loanBillId) {
        return nvl(expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                        .eq(ExpLoanOffset::getLoanBillId, loanBillId)
                        .eq(ExpLoanOffset::getOffsetType, "CLAIM"))
                .stream().map(ExpLoanOffset::getOffsetAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 该借款单上尚未终结的还款单占用合计（还款＝核销动作，单独统计不与冲抵混算）。 */
    private BigDecimal repaidAmountOfRepay(Long loanBillId, Long excludeTargetBillId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpBillRelation e : expBillRelationMapper.selectList(
                new LambdaQueryWrapper<ExpBillRelation>()
                        .eq(ExpBillRelation::getSourceBillId, loanBillId)
                        .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM"))) {
            if (excludeTargetBillId != null && excludeTargetBillId.equals(e.getTargetBillId())) {
                continue; // 本单自身
            }
            ExpBill t = expBillMapper.selectById(e.getTargetBillId());
            if (t == null || !"REPAY".equals(t.getBillType())) {
                continue;
            }
            String st = t.getStatus() == null ? "DRAFT" : t.getStatus();
            if ("REJECTED".equals(st) || "WITHDRAWN".equals(st)
                    || "CANCELLED".equals(st) || "VOID".equals(st) || "CLOSED".equals(st)) {
                continue;
            }
            sum = sum.add(nvl(e.getUseAmount()));
        }
        return sum;
    }

    private static String typeName(String relationType) {
        if ("APPLY_CLAIM".equals(relationType)) return "事前申请";
        if ("LOAN_CLAIM".equals(relationType)) return "借款单";
        if ("PREPAY_CLAIM".equals(relationType)) return "预付款单";
        return "源单据";
    }

    // 预付冲销（#13）

    /**
     * 预付冲销（#13）：报销单冲销预付款单（bill_type=PAY 且 bill_sub_type=PREPAY）。
     *
     * <p>勾稽：冲销额 &gt; 0 且 累计冲销 ≤ 预付金额（防超额冲销）；
     * 冲销后重算报销单 offset_amount / payable_amount（与借款冲抵同一规则）；
     * 并释放预付款占用的预算，避免"预付占用预算 + 报销再占一次"的双计。
     *
     * <p>系统原先只有借款冲抵（addLoanOffset），预付款没有冲销出口，
     * 预付资金只能靠人工在报销单上备注，预算也被重复占用。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long writeOffPrepay(Long billId, Long prepayBillId, BigDecimal amount) {
        ExpBill bill = requireBill(billId);
        if (!"DRAFT".equals(bill.getStatus()) && !"APPROVING".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前状态不可冲销预付: " + bill.getStatus());
        }
        ExpBill prepay = expBillMapper.selectById(prepayBillId);
        if (prepay == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预付款单不存在: " + prepayBillId);
        }
        if (!"PAY".equals(prepay.getBillType()) || !"PREPAY".equals(prepay.getBillSubType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "冲销对象不是预付款单(PAY/PREPAY): " + prepayBillId);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲销金额必须大于 0");
        }
        // 累计冲销（含本次）不得超预付金额
        List<ExpBillRelation> existed = expBillRelationMapper.selectList(
                new LambdaQueryWrapper<ExpBillRelation>()
                        .eq(ExpBillRelation::getSourceBillId, prepayBillId)
                        .eq(ExpBillRelation::getRelationType, "PREPAY_CLAIM"));
        BigDecimal used = BigDecimal.ZERO;
        for (ExpBillRelation e : existed) {
            used = used.add(nvl(e.getUseAmount()));
        }
        BigDecimal limit = nvl(prepay.getTotalAmount());
        if (used.add(amount).compareTo(limit) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "冲销金额(" + amount + ")超出预付款可冲销余额(" + limit.subtract(used) + ")");
        }

        ExpBillRelation r = new ExpBillRelation();
        r.setTenantId(TenantContext.getTenantId());
        r.setSourceBillId(prepayBillId);
        r.setTargetBillId(billId);
        r.setRelationType("PREPAY_CLAIM");
        r.setUseAmount(amount);
        r.setCreateTime(LocalDateTime.now());
        expBillRelationMapper.insert(r);

        // 重算报销单冲抵/应付（规则与借款冲抵一致：核定付款 > 核定报销 > 申请金额）
        BigDecimal totalOffset = nvl(bill.getOffsetAmount()).add(amount);
        BigDecimal base = bill.getApprovedPayAmount() != null ? bill.getApprovedPayAmount()
                : (bill.getApprovedAmount() != null ? bill.getApprovedAmount() : bill.getTotalAmount());
        BigDecimal payable = nvl(base).subtract(totalOffset);
        ExpBill upd = new ExpBill();
        upd.setId(billId);
        upd.setOffsetAmount(totalOffset);
        upd.setPayableAmount(payable.signum() < 0 ? BigDecimal.ZERO : payable);
        expBillMapper.updateById(upd);

        // 预算：释放预付款明细行对应占用（防预付与报销双计预算）
        List<ExpBillLine> pLines = expBillLineMapper.selectList(
                new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, prepayBillId));
        for (ExpBillLine pl : pLines) {
            if (pl.getBudgetItemId() != null && pl.getAmount() != null && pl.getAmount().signum() > 0) {
                BigDecimal release = amount.min(pl.getAmount());
                if (release.signum() > 0) {
                    budgetService.release(pl.getBudgetItemId(), release, "EXP_PREPAY_OFFSET",
                            billId, bill.getBillNo());
                }
            }
        }
        return r.getId();
    }

    public List<ExpBillRelation> relations(Long billId) {
        List<ExpBillRelation> list = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, billId).or()
                .eq(ExpBillRelation::getTargetBillId, billId));
        fillRelationBillNos(list);
        return list;
    }

    /** 关系表只存 id：批量补源/目标单据号，草稿重开时「关联事前申请」才认得出是哪张单 */
    private void fillRelationBillNos(List<ExpBillRelation> list) {
        Set<Long> ids = new HashSet<>();
        for (ExpBillRelation r : list) {
            if (r.getSourceBillId() != null) ids.add(r.getSourceBillId());
            if (r.getTargetBillId() != null) ids.add(r.getTargetBillId());
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> noById = new HashMap<>();
        for (ExpBill b : expBillMapper.selectBatchIds(ids)) {
            noById.put(b.getId(), b.getBillNo());
        }
        for (ExpBillRelation r : list) {
            r.setSourceBillNo(noById.get(r.getSourceBillId()));
            r.setTargetBillNo(noById.get(r.getTargetBillId()));
        }
    }

    // 工具

    private ExpBill requireBill(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        return bill;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
