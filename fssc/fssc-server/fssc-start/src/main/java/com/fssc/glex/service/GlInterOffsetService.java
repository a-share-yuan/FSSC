// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.mapper.GlInterOffsetMapper;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.service.ConfigService;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 内部往来抵消服务（gl_inter_offset，公司间应收/应付往来抵消分录）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlInterOffsetService {

    private final GlInterOffsetMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final RcReceivableMapper rcReceivableMapper;
    private final InvoiceDomainFacade invoiceFacade;
    private final ConfigService configService;
    private final VoucherService voucherService;
    private final OpVoucherMapper opVoucherMapper;

    public PageResult<GlInterOffset> page(long pageNum, long pageSize, String period, String interFlag, String keyword) {
        LambdaQueryWrapper<GlInterOffset> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlInterOffset::getPeriod, period);
        }
        if (StringUtils.hasText(interFlag)) {
            qw.eq(GlInterOffset::getInterFlag, interFlag);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlInterOffset::getOffsetNo, keyword).or().like(GlInterOffset::getVoucherNo, keyword));
        }
        qw.orderByDesc(GlInterOffset::getId);
        Page<GlInterOffset> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 按期间生成内部往来抵消分录：对若干公司对生成应收/应付抵消记录并自动 post（置 OFFSET、生成凭证号）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<GlInterOffset> generateOffset(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        Long tenantId = TenantContext.getTenantId();
        long seq = countByPeriod(period) + 1;
        List<GlInterOffset> created = new ArrayList<>();
        // 缺陷D4(20260919)：公司对不再硬编码 (1,2)(2,3)(3,1)——演示库公司为 1/31~37（2、3 不存在），
        // 固定公司对的余额恒为 0，抵消永远静默跳过、流程永不发起（GL-10 阻塞根因）。
        // 现按「sys_config gl.offset.companyPairs（fromId:toId,..）→ 未配置则按有余额公司自适应配对」解析。
        List<long[]> pairs = resolveCompanyPairs();
        for (long[] p : pairs) {
            BigDecimal amount = pairOffsetAmount(p[0], p[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("公司对 {}→{} 无可抵消余额，跳过", p[0], p[1]);
                continue;
            }
            // 成对生成镜像分录：往账(PAYABLE) from→to，来账(RECEIVABLE) to→from
            GlInterOffset out = newEntry(tenantId, period, p[0], p[1], "PAYABLE", amount, seq);
            seq++;
            GlInterOffset in = newEntry(tenantId, period, p[1], p[0], "RECEIVABLE", amount, seq);
            seq++;
            // 往来抵消金额公式强校验：金额 > 0、方向合法
            assertOffset(out.getAmount(), out.getInterFlag());
            assertOffset(in.getAmount(), in.getInterFlag());
            // 显式断言：同一公司对的来账(应收)与往账(应付)金额必须成对相等，否则内部勾不平
            assertPairEqual(in.getAmount(), out.getAmount());
            mapper.insert(out);
            mapper.insert(in);
            created.add(out);
            created.add(in);
        }
        // P6：内部往来抵消即发起 GL_OFFSET 内部往来抵消审批流（总账会计编制→共享主管复核→财务经理审批）
        if (!created.isEmpty()) {
            BigDecimal total = created.stream().map(GlInterOffset::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            try {
                wfEngineFacade.start("GL_OFFSET", created.get(0).getId(), userId(),
                        "内部往来抵消-" + period, total);
            } catch (Exception ex) {
                log.error("内部往来抵消 {} 发起抵消审批失败: {}", period, ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
        return created;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 取消/置 OFFSET 单独操作：post（置 OFFSET、生成凭证号） */
    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        GlInterOffset e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抵消记录不存在: " + id);
        }
        if ("OFFSET".equals(e.getOffsetStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已抵消");
        }
        // 抵消记账即出证，凭证号取凭证对象上的真号；出证不成功不置 OFFSET、不写自造号
        Long voucherId;
        try {
            voucherId = voucherService.generateInterOffsetVoucher(e);
        } catch (Exception ex) {
            log.error("内部往来抵消 {} 记账出证失败（期间 {}，金额 {}）: {}",
                    e.getOffsetNo(), e.getPeriod(), e.getAmount(), ex.getMessage(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "抵消记账未完成：抵消凭证未能生成（" + ex.getMessage() + "），该分录保持未抵消");
        }
        if (voucherId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "抵消记账未完成：抵消凭证未生成，该分录保持未抵消");
        }
        OpVoucher v = opVoucherMapper.selectById(voucherId);
        String realNo = v == null ? null : v.getVoucherNo();
        if (!StringUtils.hasText(realNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "抵消记账未完成：凭证已生成但凭证号取不到（凭证 id=" + voucherId + "），该分录保持未抵消");
        }
        e.setVoucherNo(realNo);
        e.setOffsetStatus("OFFSET");
        // post 也是状态写入，记账人须写入数据库
        e.setUpdateBy(userId());
        mapper.updateById(e);
        log.info("内部往来抵消 {} 记账完成，凭证 {}（凭证状态 {}）",
                e.getOffsetNo(), realNo, v.getStatus());
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlInterOffset>().eq(GlInterOffset::getPeriod, period));
    }

    /** 往来抵消强校验：抵消金额必须 > 0，方向必须为 RECEIVABLE/PAYABLE。
     *  内部往来抵消要求同一公司对的应收/应付金额相等（镜像勾销），此处做数据合法性默认处理。 */
    private static void assertOffset(BigDecimal amount, String interFlag) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抵消金额必须 > 0: " + amount);
        }
        if (!Set.of("RECEIVABLE", "PAYABLE").contains(interFlag)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内部抵消方向非法: " + interFlag);
        }
    }

    /** 内部抵消铁律：同一公司对的来账(应收)与往账(应付)金额必须成对相等，否则内部勾不平。 */
    public static void assertPairEqual(BigDecimal inAmount, BigDecimal outAmount) {
        if (inAmount == null || outAmount == null || inAmount.compareTo(outAmount) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内部抵消来账与往账金额不一致");
        }
    }

    /** 内部抵消公司对必须跨主体——同一公司自我抵消无业务意义（历史 3 条脏数据含 0.00 元行），前后端双拦。 */
    public static void assertDistinctCompanies(Long fromCompanyId, Long toCompanyId) {
        if (fromCompanyId != null && fromCompanyId.equals(toCompanyId)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "内部往来抵消的公司对不能为同一公司（自我抵消无意义）: " + fromCompanyId);
        }
    }

    /** 手工录入镜像对（来账应收 in + 往账应付 out）：录入即强制来账=往账成对相等，置 DRAFT 待 post。 */
    @Transactional(rollbackFor = Exception.class)
    public List<GlInterOffset> create(GlInterOffset in, GlInterOffset out) {
        if (in == null || out == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "需成对录入来账(应收)与往账(应付)");
        }
        String period = StringUtils.hasText(in.getPeriod()) ? in.getPeriod() : out.getPeriod();
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        assertOffset(out.getAmount(), out.getInterFlag());
        assertOffset(in.getAmount(), in.getInterFlag());
        // 手工录入路径同样调用成对相等断言（复用 assertPairEqual）
        assertPairEqual(in.getAmount(), out.getAmount());
        // 拒绝 from==to 自我抵消（来账/往账两环节各自校验，同主体直拦）
        assertDistinctCompanies(in.getFromCompanyId(), in.getToCompanyId());
        assertDistinctCompanies(out.getFromCompanyId(), out.getToCompanyId());
        Long tenantId = TenantContext.getTenantId();
        long seq = countByPeriod(period) + 1;
        // 手工录入路径的审计人同样由服务端覆盖，不要采信请求体里的 createBy/updateBy
        Long uid = userId();
        in.setId(null);
        in.setTenantId(tenantId);
        in.setCreateBy(uid);
        in.setUpdateBy(uid);
        in.setPeriod(period);
        in.setOffsetNo("IO" + period + String.format("%03d", seq));
        if (in.getOffsetStatus() == null) {
            in.setOffsetStatus("DRAFT");
        }
        out.setId(null);
        out.setTenantId(tenantId);
        out.setCreateBy(uid);
        out.setUpdateBy(uid);
        out.setPeriod(period);
        out.setOffsetNo("IO" + period + String.format("%03d", seq + 1));
        if (out.getOffsetStatus() == null) {
            out.setOffsetStatus("DRAFT");
        }
        mapper.insert(in);
        mapper.insert(out);
        // BUG-10 修复：手工成对录入与自动生成同一规则，发起 GL_OFFSET 内部往来抵消审批流
        // 总额取单侧金额（镜像对来账=往账，双计会把审批额放大一倍）
        try {
            BigDecimal total = in.getAmount() != null ? in.getAmount()
                    : (out.getAmount() == null ? BigDecimal.ZERO : out.getAmount());
            wfEngineFacade.start("GL_OFFSET", in.getId(), userId(),
                    "内部往来抵消-" + period, total);
        } catch (Exception ex) {
            log.warn("手工录入抵消发起 GL_OFFSET 审批失败: {}", ex.getMessage());
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return Arrays.asList(in, out);
    }

    /** 构造一条抵消分录（生成期间 + IO 顺序单号，直接置已抵消）。 */
    private GlInterOffset newEntry(Long tenantId, String period, long fromId, long toId,
                                   String interFlag, BigDecimal amount, long seq) {
        GlInterOffset e = new GlInterOffset();
        e.setTenantId(tenantId);
        // 审计人取服务端登录态，与同族 gl_tax_provision/gl_month_close 同一规则；
        // 缺这一步时 create_by/update_by 全 NULL，事后无法回答「谁做的抵消」。
        Long uid = userId();
        e.setCreateBy(uid);
        e.setUpdateBy(uid);
        e.setPeriod(period);
        e.setFromCompanyId(fromId);
        e.setToCompanyId(toId);
        e.setInterFlag(interFlag);
        e.setAmount(amount);
        e.setOffsetNo("IO" + period + String.format("%03d", seq));
        e.setOffsetStatus("OFFSET");
        // 不预造 GLIO 号——凭证号只由真实凭证回写（GL_OFFSET 办结时 GlBizAction.handleOffset 回写）
        return e;
    }

    /**
     * 缺陷D4(20260919)：抵消公司对解析。
     * <ol>
     *   <li>优先读 sys_config {@code gl.offset.companyPairs}，格式 {@code fromId:toId,fromId:toId,...}；</li>
     *   <li>未配置则自适应：取「有未作废发票（应付余额>0）」的公司 × 「有未结清应收（remain>0）」的公司
     *       做跨公司配对（排除自身），再由 {@link #pairOffsetAmount} 按 min(应付,应收) 逐对裁量——
     *       与原硬编码三公司循环的业务意图一致，但不再依赖特定公司 ID 存在。</li>
     * </ol>
     */
    private List<long[]> resolveCompanyPairs() {
        try {
            // sys_config 在 master 库，经 ConfigService 路由读取（直连 mapper 有跨库连接风险）
            String configured = configService.getValue("gl.offset.companyPairs");
            if (configured != null && !configured.isBlank()) {
                List<long[]> pairs = new ArrayList<>();
                for (String pair : configured.split(",")) {
                    String[] ab = pair.trim().split(":");
                    if (ab.length == 2) {
                        pairs.add(new long[]{Long.parseLong(ab[0].trim()), Long.parseLong(ab[1].trim())});
                    }
                }
                if (!pairs.isEmpty()) {
                    return pairs;
                }
            }
        } catch (Exception ex) {
            log.warn("读取 gl.offset.companyPairs 失败，退回自适应配对: {}", ex.getMessage());
        }
        // 自适应：有应付余额的公司 × 有应收余额的公司（跨公司）
        Set<Long> froms = invoiceFacade.companyIdsByStatus("NORMAL");
        Set<Long> tos = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .in(RcReceivable::getStatus, "NORMAL", "OVERDUE", "PARTIAL")
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .isNotNull(RcReceivable::getCompanyId)).stream()
                .map(RcReceivable::getCompanyId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        List<long[]> pairs = new ArrayList<>();
        for (Long f : froms) {
            for (Long t : tos) {
                if (!f.equals(t)) {
                    pairs.add(new long[]{f, t});
                }
            }
        }
        log.info("内部往来抵消自适应公司对: {}（应付方{} × 应收方{}）",
                pairs.size(), froms, tos);
        return pairs;
    }

    /**
     * 一对公司的抵消金额：取 min(一方应收余额, 另一方应付余额)。
     * 应收余额 = rc_receivable.remain_amount 汇总（按 companyId）
     * 应付余额 = inv_invoice.amount 汇总（按 companyId，未作废的发票）
     * 若任一方为 0 或查不到数据，返回 0（无可抵消余额）。
     */
    private BigDecimal pairOffsetAmount(long fromId, long toId) {
        // fromId 方的应付余额（发票未作废合计）
        List<InvInvoice> invoices = invoiceFacade.findByCompanyIdAndStatus(fromId, "NORMAL");
        BigDecimal payable = invoices.stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // toId 方的应收余额（未结清的应收单余额合计；状态规则与资金头寸一致 NORMAL/OVERDUE/PARTIAL，
        // 原值 'NONE' 在状态枚举中不存在 → 应收恒 0 → 抵消永远静默跳过）
        List<RcReceivable> receivables = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getCompanyId, toId)
                .in(RcReceivable::getStatus, "NORMAL", "OVERDUE", "PARTIAL")
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO));
        BigDecimal receivable = receivables.stream()
                .map(r -> r.getRemainAmount() == null ? BigDecimal.ZERO : r.getRemainAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal offset = payable.min(receivable);
        return offset.compareTo(BigDecimal.ZERO) > 0 ? offset : BigDecimal.ZERO;
    }
}