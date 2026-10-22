package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P1-3 实付入账：付款成功后自动过账关联凭证（DRAFT→POSTED）。
 * <p>
 * 独立事务（REQUIRES_NEW）：凭证过账失败只影响凭证状态，绝不回滚已成功的付款；
 * 失败后可在凭证工作台用批量记账手工补账（P1-2 提供）。
 * <p>
 * D-DEC-288「凭证与电子归档早于资金指令」逐段核对后的两条落码腿（本类是"实付入账"语义的唯一持有者）：
 * <ol>
 *  <li><b>串号封堵</b>（{@link #autoPostDraftByBill} / {@link #autoPostDraftByBatch}）：
 *      {@code op_voucher.bill_id} 存的粒度按来源分家——单据级凭证（EXPENSE_PAY／CIP_PAY）存
 *      {@code exp_bill.id}，批次级凭证（TREASURY_PAY／CIP_PAY_BATCH，见
 *      {@code VoucherService.generateTreasuryPayVoucher} 与 D-DEC-036 后半）存
 *      {@code op_pay_batch.id}。原方法对两族凭证用同一条 {@code bill_id = ? OR source_doc_id = ?}
 *      取数，而两张表的 id 各自全局自增、数值区间重叠（现库 op_pay_batch 1~7 与 exp_bill 1~12 同段），
 *      于是"执行批次 2"会把<b>单据 2</b> 名下 80,000 元的付款腿凭证一并过账并盖上本批次号
 *      （D-DEC-288 真错位：凭证在自身的资金指令未下发时就被记账）。现按来源族划界；</li>
 *  <li><b>人工过账口的实付闸</b>（{@link #assertPaymentLegSettled} /
 *      {@link #findUnsettledPaymentLegs}）：自动通道只在银行指令成功之后推
 *      （{@code PayBatchService.writeBackPayment} 与 {@code PayBatchBizAction.closeAccountingForLateApproval}
 *      的 {@code if (!paid) return} 两腿），但凭证工作台的人工记账口（
 *      {@code POST /sharing/voucher/{id}/post}、{@code /sharing/voucher/batch-post}）
 *      只闸制审分离（D-DEC-166），谁都能把"贷 银行存款"的付款腿凭证在款未付出时入账——
 *      现库实证 4 张（凭证 14/15/16/23，对应单据 1/2/3/8 的付款记录至今 {@code WAITING}、
 *      {@code paid_amount=0}、{@code pay_time} 空，却在 2026-10-01 02:37:57～02:39:05 被置 POSTED）。
 *      本闸把"款未付出不得入账"从代码注释里的口径变成可执行判据，只闸付款腿、逐张给原因，
 *      取不到资金证据时放行（fail-open，不挡历史单与其他域）。</li>
 *  <li><b>存量只注记、不动账</b>（{@link #fillPayLegUnsettledFlag}，X-49 乙）：上面的写侧闸只管增量，
 *      已 POSTED 而款未付的那几张（现库 14/15/16/23）本类不碰其状态——冲销／回退草稿是账务动作，须财务另签；
 *      改为在凭证台账读侧按<b>同一套</b>资金证据打 {@code payLegUnsettled}，界面明示"资金指令未下发"，
 *      避免看凭证的人把"账面已记账"读成"款已付出"。</li>
 * </ol>
 * 不在本类处置的半条（属裁决面，见回报"需主窗裁决清单"）：制证时点本身是否参数化
 * （审批后即出 DRAFT 凭证 vs 付款后才出）——已由 D-DEC-288（X-48 甲）在 {@code VoucherService}
 * 一侧落成出纳参数，默认＝否＝现网行为不变；"归档早于付款"半条已按 既有裁决 判不修（RA-D288b）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherAutoPostService {

    /**
     * 批次级付款腿凭证来源族：{@code bill_id}/{@code source_doc_id} 存的是 {@code op_pay_batch.id}。
     * D-DEC-288 串号封堵的划界依据。
     */
    private static final Set<String> BATCH_LEG_SOURCE_TYPES = Set.of("TREASURY_PAY", "CIP_PAY_BATCH");

    /**
     * 实付闸默认覆盖族（贷方为银行存款 1002，款未付出即入账＝虚减银行）：
     * 单据级 EXPENSE_PAY／CIP_PAY ＋ 批次级 TREASURY_PAY／CIP_PAY_BATCH。
     * <p>D-DEC-288（X-50 甲）：这四族＝加参数化之前的既有覆盖面，默认值逐字不变。
     * <p>不含 EXPENSE_SETTLE（D-DEC-083：结算审批通过出的是"借 6602／贷 2202"计提应付凭证，
     * 付款腿另由批次凭证承载，审批后即入账是正确的）。
     */
    private static final Set<String> DEFAULT_PAYMENT_LEG_SOURCE_TYPES =
            Set.of("EXPENSE_PAY", "CIP_PAY", "TREASURY_PAY", "CIP_PAY_BATCH");

    /**
     * D-DEC-288（X-50 甲）：可<b>逐族</b>加闸的候选族——默认<b>关</b>（不在默认集内），
     * 由 {@link #CFG_PAYMENT_LEG_SOURCE_TYPES} 点名后才纳入实付闸，避免新增拦口堵期末批量记账。
     * <p>FA_ADD 无资金腿证据通道（凭证按资产卡片落账，本环境没有对应的付款记录/批次），
     * 即便点名也按"无证据可判＝放行"，与改前一致。
     */
    private static final Set<String> OPTIONAL_PAYMENT_LEG_SOURCE_TYPES =
            Set.of("EXP_LOAN", "GL_SALARY_PAY", "FA_ADD", "TR_TRANSFER");

    /**
     * D-DEC-288（X-50 甲）：实付闸覆盖族的逐族配置键（sys_config 逗号串，如
     * {@code EXP_LOAN,GL_SALARY_PAY}＝在默认四族之外再加两族；{@code -CIP_PAY}＝把某默认族撤闸）。
     * <b>读不到＝用默认四族</b>（本轮不向库里插参数行）。
     */
    private static final String CFG_PAYMENT_LEG_SOURCE_TYPES = "voucher.post.payment-leg.source-types";

    /**
     * D-DEC-288（X-49 乙）：读侧注记可判定的付款腿族——只收录<b>本类已有资金证据通道</b>的族，
     * 且证据键按 D-DEC-288 的划界口径取（批次级族＝{@code op_pay_batch.id}，其余＝{@code exp_bill.id}）。
     * <p>不含 GL_SALARY_PAY／TR_TRANSFER／FA_ADD：前两族的实付证据在各自域的另一张表
     * （{@link #settledPayrollOrTransfer} 走 selectById，未做列表批量化），FA_ADD 本就无资金腿证据通道；
     * 把它们塞进这条批量通道＝拿别的域主键去查 {@code exp_bill_payment}，正是 D-DEC-244 那类跨域裸主键串号。
     */
    private static final Set<String> FLAG_LEG_SOURCE_TYPES =
            Set.of("EXPENSE_PAY", "CIP_PAY", "EXP_LOAN", "TREASURY_PAY", "CIP_PAY_BATCH");

    private final OpVoucherMapper opVoucherMapper;
    private final VoucherService voucherService;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final com.fssc.system.service.ConfigService configService;
    private final com.fssc.glex.mapper.GlSalaryRunMapper glSalaryRunMapper;
    private final com.fssc.treasury.mapper.TrTransferMapper trTransferMapper;

    /**
     * 付款完成后自动过账该<b>单据</b>的全部 DRAFT 凭证（单据级腿：bill_id / source_doc_id = 单据 id）。
     *
     * <p>D-DEC-288：取数排除批次级来源族，避免单据 id 与付款批次 id 数值相同时串到批次凭证。
     *
     * @param billId  来源单据 ID（bill_id / source_doc_id 双字段兼容）
     * @param batchNo 付款批次号（写入凭证备注，便于溯源）
     * @param userId  过账人（付款执行人）
     * @return 过账结果 Map（凭证ID → OK/失败原因）；无 DRAFT 凭证时返回空
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Map<Long, String> autoPostDraftByBill(Long billId, String batchNo, Long userId) {
        return postDrafts(billId, batchNo, userId, false);
    }

    /**
     * 付款完成后自动过账该<b>付款批次</b>名下的批次级 DRAFT 凭证（D-DEC-036 后半的批次腿）。
     *
     * <p>D-DEC-288：只取 {@code source_type ∈ {TREASURY_PAY, CIP_PAY_BATCH}} 的凭证——
     * 这三个入参在旧实现里与单据通道共用一条 {@code bill_id = ?} 谓词，而批次 id 与单据 id 同段自增，
     * 于是批次执行成功会连带过账"同号单据"的单据级付款凭证（那张凭证的钱并未在本批次付出）。
     *
     * @param batchId 付款批次 ID（op_pay_batch.id）
     * @param batchNo 付款批次号
     * @param userId  过账人（付款执行人）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Map<Long, String> autoPostDraftByBatch(Long batchId, String batchNo, Long userId) {
        return postDrafts(batchId, batchNo, userId, true);
    }

    /** 实付入账取数并过账；{@code batchScope=true} 只碰批次级凭证，false 只碰单据级凭证（D-DEC-288） */
    private Map<Long, String> postDrafts(Long docId, String batchNo, Long userId, boolean batchScope) {
        if (docId == null) {
            return Map.of();
        }
        LambdaQueryWrapper<OpVoucher> qw = new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getStatus, "DRAFT")
                .and(w -> w.eq(OpVoucher::getBillId, docId)
                        .or()
                        .eq(OpVoucher::getSourceDocId, docId));
        if (batchScope) {
            qw.in(OpVoucher::getSourceType, BATCH_LEG_SOURCE_TYPES);
        } else {
            // source_type 未知的凭证仍走单据通道：NULL NOT IN (…) 在三值逻辑下恒不成立，
            // 直接用 notIn 会让这类凭证凭空从过账队列消失（同 D-DEC-276b 的 ne＋isNull 口径）
            qw.and(w -> w.isNull(OpVoucher::getSourceType)
                    .or()
                    .notIn(OpVoucher::getSourceType, BATCH_LEG_SOURCE_TYPES));
        }
        List<OpVoucher> drafts = opVoucherMapper.selectList(qw);
        if (drafts.isEmpty()) {
            return Map.of();
        }
        // 付款批次号写入备注，保持"实付入账"溯源
        for (OpVoucher v : drafts) {
            OpVoucher u = new OpVoucher();
            u.setId(v.getId());
            String remark = v.getRemark() == null ? "" : v.getRemark().trim();
            String tag = "实付入账批次:" + batchNo;
            u.setRemark(remark.isEmpty() ? tag : remark + "；" + tag);
            opVoucherMapper.updateById(u);
        }
        List<Long> ids = drafts.stream().map(OpVoucher::getId).toList();
        // D-DEC-166：实付入账＝付款流程已通过后的系统动作，职责分离在流程侧（提单/审批/付款执行三岗）已完成，
        // 这里显式声明系统过账，避免"付款执行人恰是凭证制单人"时钱已付而账不落（人工过账口仍强制制审分离）
        Map<Long, String> result = voucherService.batchPost(ids, userId, true);
        long ok = result.values().stream().filter("OK"::equals).count();
        if (ok > 0) {
            log.info("[P1-3] {} {} 付款完成自动过账 {} 张凭证（批次 {}）",
                    batchScope ? "批次" : "单据", docId, ok, batchNo);
        }
        result.forEach((id, msg) -> {
            if (!"OK".equals(msg)) {
                log.warn("[P1-3] {} {} 凭证 {} 自动过账失败: {}", batchScope ? "批次" : "单据", docId, id, msg);
            }
        });
        return result;
    }

    /**
     * D-DEC-288：人工过账口（凭证工作台单张/批量记账）的实付闸——付款腿凭证在款项实际付出前不得置 POSTED。
     * <p>只在<b>拿得到资金证据且证据说"未付"</b>时拦截：单据一张付款记录都没有（历史单、
     * 借款/还款族在本环境无资金腿）或批次行查不到时一律放行，避免把正常记账路径堵死。
     * 部分付款（单据有多条付款记录且至少一条 PAID）放行——那是人工判断范围，本闸不越权。
     *
     * @param voucherIds 待过账凭证 ID 集合
     * @return 凭证ID → 拦截原因（面向财务的白话，不含任何外部厂商名称）；空 Map＝全部可过账
     */
    public Map<Long, String> findUnsettledPaymentLegs(Collection<Long> voucherIds) {
        Map<Long, String> blocked = new LinkedHashMap<>();
        if (voucherIds == null || voucherIds.isEmpty()) {
            return blocked;
        }
        List<Long> ids = voucherIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return blocked;
        }
        Set<String> gated = gatedPaymentLegSourceTypes();   // D-DEC-288：一次入口读一次参数，不逐张查库
        for (OpVoucher v : opVoucherMapper.selectBatchIds(ids)) {
            String reason = unsettledReason(v, gated);
            if (reason != null) {
                blocked.put(v.getId(), reason);
            }
        }
        return blocked;
    }

    /**
     * D-DEC-288：单张记账入口用的硬闸版本——被拦时抛业务异常，界面直接回显原因。
     * <p>红字冲销／作废不走本闸（纠错通道必须始终可用）。
     */
    public void assertPaymentLegSettled(Long voucherId) {
        String reason = unsettledReason(opVoucherMapper.selectById(voucherId), gatedPaymentLegSourceTypes());
        if (reason != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, reason);
        }
    }

    /**
     * D-DEC-288（X-49 乙）裁决落地：<b>只读富化</b>——给"账面已记账、资金指令未下发"的付款腿凭证
     * 打 {@code payLegUnsettled} 展示标记，凭证台账据此在行上注记"资金指令未下发"。
     *
     * <p><b>零账务动作</b>：本方法不 {@code update} 任何表、不改 {@code op_voucher.status}，
     * 存量 4 张 POSTED 的付款腿凭证（现库凭证 14/15/16/23，单据 1/2/3/8 的付款记录至今 WAITING、
     * {@code pay_time} 空）保持原样不动账；是否拦截记账仍只由 {@link #unsettledReason} 那条<b>写侧闸</b>决定
     * （该闸只拦 DRAFT，管不到已 POSTED 的存量，所以存量只能靠注记说明——冲销／回退须财务另签）。
     *
     * <p>资金证据与写侧闸<b>完全同源</b>：单据级族按本单 {@code exp_bill_payment.pay_status} 判、
     * 批次级族按 {@code op_pay_batch.status} 判，关联键沿用 {@code source_doc_id ?: bill_id} 加来源族划界，
     * 不新增跨域裸主键关联。取不到证据行（无付款记录的历史单、批次行不存在）⇒ 标记留 null、界面不注记，
     * 与 {@link #findUnsettledPaymentLegs} 的 fail-open 口径一致。
     *
     * <p>调用点在 {@code SharingController.voucherPage}（凭证列表出参富化），逐页两条批量 SQL，不逐张查库。
     *
     * @param vouchers 凭证列表行（原地打标；为 null／空／无命中行时直接返回）
     */
    public void fillPayLegUnsettledFlag(Collection<OpVoucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Set<String> gated = gatedPaymentLegSourceTypes();
        List<OpVoucher> targets = new java.util.ArrayList<>();
        for (OpVoucher v : vouchers) {
            if (v == null || v.getId() == null) {
                continue;
            }
            // 只注记已记账行：DRAFT 行界面本就显示"未过账"，不存在"误判已结清"的风险
            if (!"POSTED".equalsIgnoreCase(String.valueOf(v.getStatus()))) {
                continue;
            }
            String sourceType = upperSourceType(v);
            if (!gated.contains(sourceType) || !FLAG_LEG_SOURCE_TYPES.contains(sourceType)) {
                continue;   // 非在册付款腿／本通道无资金证据的族，一律不出标记
            }
            if (legDocId(v) == null) {
                continue;   // 溯源键缺失（D-DEC-114③ 族）不在此处加责
            }
            targets.add(v);
        }
        if (targets.isEmpty()) {
            return;
        }
        Set<Long> billIds = new java.util.HashSet<>();
        Set<Long> batchIds = new java.util.HashSet<>();
        for (OpVoucher v : targets) {
            (BATCH_LEG_SOURCE_TYPES.contains(upperSourceType(v)) ? batchIds : billIds).add(legDocId(v));
        }
        java.util.Map<Long, List<ExpBillPayment>> paysByBill = new java.util.HashMap<>();
        if (!billIds.isEmpty()) {
            for (ExpBillPayment p : expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                    .in(ExpBillPayment::getBillId, billIds))) {
                paysByBill.computeIfAbsent(p.getBillId(), k -> new java.util.ArrayList<>()).add(p);
            }
        }
        java.util.Map<Long, OpPayBatch> batchById = new java.util.HashMap<>();
        if (!batchIds.isEmpty()) {
            for (OpPayBatch b : opPayBatchMapper.selectBatchIds(batchIds)) {
                batchById.putIfAbsent(b.getId(), b);
            }
        }
        for (OpVoucher v : targets) {
            Long docId = legDocId(v);
            // 三态标注用 Boolean.valueOf(...)：与 null 分支同处一个条件表达式，避免基本类型／null 混排的口径歧义
            if (BATCH_LEG_SOURCE_TYPES.contains(upperSourceType(v))) {
                OpPayBatch batch = batchById.get(docId);
                v.setPayLegUnsettled(batch == null ? null
                        : Boolean.valueOf(!"PAID".equalsIgnoreCase(String.valueOf(batch.getStatus()))));
            } else {
                List<ExpBillPayment> pays = paysByBill.get(docId);
                if (pays == null || pays.isEmpty()) {
                    v.setPayLegUnsettled(null);   // 本单无付款记录 ⇒ 无证据可判，不注记
                } else {
                    v.setPayLegUnsettled(Boolean.valueOf(pays.stream()
                            .noneMatch(p -> "PAID".equalsIgnoreCase(String.valueOf(p.getPayStatus())))));
                }
            }
        }
    }

    private static String upperSourceType(OpVoucher v) {
        return v.getSourceType() == null ? "" : v.getSourceType().trim().toUpperCase();
    }

    /** 资金证据的关联键：{@code source_doc_id ?: bill_id}（与写侧闸逐字同口径，两处不得各改一半） */
    private static Long legDocId(OpVoucher v) {
        return v.getSourceDocId() != null ? v.getSourceDocId() : v.getBillId();
    }

    /**
     * D-DEC-288（X-50 甲）：实付闸当前的覆盖族＝默认四族 ＋ 配置点名的候选族（－ 配置撤闸的默认族）。
     * <p>参数读不到／读失败＝默认四族（＝加本参数前的硬编码覆盖面），现网行为不变。
     */
    private Set<String> gatedPaymentLegSourceTypes() {
        String raw;
        try {
            raw = configService.getValue(CFG_PAYMENT_LEG_SOURCE_TYPES);
        } catch (Exception e) {
            log.warn("实付闸覆盖族参数 {} 读取失败，按默认四族执行: {}", CFG_PAYMENT_LEG_SOURCE_TYPES, e.getMessage());
            return DEFAULT_PAYMENT_LEG_SOURCE_TYPES;
        }
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAYMENT_LEG_SOURCE_TYPES;
        }
        Set<String> gated = new java.util.LinkedHashSet<>(DEFAULT_PAYMENT_LEG_SOURCE_TYPES);
        for (String token : raw.split(",")) {
            String t = token.trim().toUpperCase();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("-")) {
                gated.remove(t.substring(1).trim());       // 撤闸：把默认族从覆盖面里摘掉
            } else if (OPTIONAL_PAYMENT_LEG_SOURCE_TYPES.contains(t)) {
                gated.add(t);                              // 加闸：只认候选族，未知族不放开
            } else {
                log.warn("实付闸覆盖族参数含未在册来源族 {}（已忽略）；可点名族＝{}",
                        t, OPTIONAL_PAYMENT_LEG_SOURCE_TYPES);
            }
        }
        return gated;
    }

    /** 付款腿凭证的"款未付出"判定；{@code null}＝不是付款腿／已有实付证据／无证据可判（放行） */
    private String unsettledReason(OpVoucher v, Set<String> gated) {
        if (v == null || v.getId() == null) {
            return null;
        }
        if (!"DRAFT".equalsIgnoreCase(String.valueOf(v.getStatus()))) {
            return null; // 已记账/作废/红字等态由 post() 自己的状态机管
        }
        String sourceType = v.getSourceType() == null ? "" : v.getSourceType().trim().toUpperCase();
        if (!gated.contains(sourceType)) {
            return null;
        }
        Long docId = v.getSourceDocId() != null ? v.getSourceDocId() : v.getBillId();
        if (docId == null) {
            return null; // 溯源键缺失属 D-DEC-114③ 族，不在此处加责
        }
        if (BATCH_LEG_SOURCE_TYPES.contains(sourceType)) {
            OpPayBatch batch = opPayBatchMapper.selectById(docId);
            if (batch == null) {
                return null;
            }
            if ("PAID".equalsIgnoreCase(String.valueOf(batch.getStatus()))) {
                return null;
            }
            return "该凭证是付款批次的出纳付款腿（贷 银行存款），批次 " + batch.getBatchNo()
                    + " 当前状态为 " + batch.getStatus() + "，尚未实际付款；请先在付款管理执行付款，"
                    + "款项付出后再记账（实付入账口径）。";
        }
        if ("GL_SALARY_PAY".equals(sourceType) || "TR_TRANSFER".equals(sourceType)) {
            return settledPayrollOrTransfer(sourceType, docId);
        }
        // 单据级付款腿（EXPENSE_PAY／CIP_PAY／加闸后的 EXP_LOAN）：按本单付款记录判
        List<ExpBillPayment> pays = expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, docId));
        if (pays.isEmpty()) {
            return null; // 该单据在本环境没有资金腿，无证据可判 ⇒ 放行
        }
        boolean anyPaid = pays.stream()
                .anyMatch(p -> "PAID".equalsIgnoreCase(String.valueOf(p.getPayStatus())));
        if (anyPaid) {
            return null; // 已有实际付出的腿
        }
        String statuses = pays.stream().map(p -> String.valueOf(p.getPayStatus()))
                .distinct().collect(Collectors.joining("、"));
        return "该凭证是本单的付款腿（贷 银行存款），本单付款记录状态为 " + statuses
                + "，款项尚未付出；请先完成付款执行，款项付出后再记账（实付入账口径）。";
    }

    /**
     * D-DEC-288（X-50 甲）：薪酬发放／资金调拨两族的实付证据——各自域自己的资金状态列，
     * 查不到行／状态不认识＝无证据可判 ⇒ 放行（与 fail-open 同口径，不新增拦口）。
     */
    private String settledPayrollOrTransfer(String sourceType, Long docId) {
        if ("GL_SALARY_PAY".equals(sourceType)) {
            com.fssc.glex.entity.GlSalaryRun run = glSalaryRunMapper.selectById(docId);
            if (run == null || run.getPayStatus() == null || run.getPayStatus().isBlank()) {
                return null;
            }
            String st = run.getPayStatus().trim().toUpperCase();
            if ("PAID".equals(st)) {
                return null;
            }
            return "该凭证是薪酬发放的付款腿（贷 银行存款），发放批次 " + run.getRunNo()
                    + " 当前付款状态为 " + st + "，款项尚未实际发放；请先完成发放执行后再记账（实付入账口径）。";
        }
        com.fssc.treasury.entity.TrTransfer transfer = trTransferMapper.selectById(docId);
        if (transfer == null || transfer.getStatus() == null || transfer.getStatus().isBlank()) {
            return null;
        }
        String st = transfer.getStatus().trim().toUpperCase();
        if ("PAID".equals(st)) {
            return null;
        }
        return "该凭证是资金调拨的银行腿（贷 银行存款 转出方），调拨单 "
                + (transfer.getTransferNo() == null ? docId : transfer.getTransferNo())
                + " 当前状态为 " + st + "，资金尚未实际划拨；请先执行调拨后再记账（实付入账口径）。";
    }
}
