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
import com.fssc.gl.service.GlService;
import com.fssc.glex.entity.GlMonthClose;
import com.fssc.glex.mapper.GlMonthCloseMapper;
import com.fssc.masterdata.service.PeriodService;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 月结服务（gl_month_close）。对指定期间执行试算平衡校验，balance 则发起 GL_MONTH_CLOSE 月结审批流。
 *
 * <p><b>关账时点（D-DEC-189，2026-10-01 源码级整改）</b>：发起月结<b>只建在途单</b>
 * （gl_month_close=RUNNING、md_period 保持 NORMAL），真正关账（md_period=CLOSED + 月结单 CLOSED）
 * 后置到末节点审批通过的收尾回写 {@link #applyApprovalOutcome}（由 GlBizAction 在流程结束事件里调用）。
 * 整改前是「点按钮那一秒即关账、7 节审批零回写、驳回不重开」——审批只做事后追认，
 * 且被驳回的月结单会把期间永久锁死，须人工反结账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlMonthCloseService {

    /** 月结审批在途态（发起即置此态；末节点通过才回写 CLOSED） */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 审批通过但因期间状态变化未关账（可见、不静默） */
    public static final String STATUS_APPROVED_NOT_CLOSED = "APPROVED";
    /** 审批驳回/撤回 */
    public static final String STATUS_REJECTED = "REJECTED";

    private final com.fssc.glex.mapper.GlCloseCheckItemMapper closeCheckItemMapper;
    private final GlMonthCloseMapper mapper;
    private final GlService glService;
    private final WfEngineFacade wfEngineFacade;
    private final PeriodService periodService;
    private final OpVoucherMapper opVoucherMapper;
    /** D-V4R-68：账证一致核对要直读总账分录表（试算的取数源），故与 GlService 同用一张 Mapper */
    private final com.fssc.gl.mapper.GlEntryMapper glEntryMapper;
    /** D-DEC-189：回写关账人时只读取该实例末节点审批留痕（wf_task_history，审计表只读） */
    private final com.fssc.workflow.mapper.WfTaskHistoryMapper wfTaskHistoryMapper;
    /**
     * D-DEC-191（12-31:169）：月结列表读侧富化用——只读 {@code wf_instance}（biz_type=GL_MONTH_CLOSE,
     * biz_id=月结单ID）把「这张月结单是被哪条审批流定的、什么时候办结的」补上。
     * 表上没有 proc_inst_id/approve_time/audit_time 三列（加列属 DDL 裁决，注释态 SQL 见实体类尾），
     * 故与 D-DEC-114①／D-DEC-028 同法走 transient 富化，零写库副作用。
     */
    private final com.fssc.workflow.mapper.WfInstanceMapper wfInstanceMapper;
    /**
     * D-DEC-291（GL 族终态零留痕）：月结单的终态跃迁（RUNNING → CLOSED／REJECTED／APPROVED）
     * 原先只有 log 与 wf_task_history，audit_trail 里查不到任何 GL_MONTH_CLOSE 行——
     * 而"这期间到底是被哪一次审批定成关账/没关账"正是事后核查要问的事。
     * 本字段只补审计行，不改任何回写判据（{@code AuditTrailService.record} 自身吞异常、不阻断业务）。
     */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    public PageResult<GlMonthClose> page(long pageNum, long pageSize, String period, String keyword) {
        LambdaQueryWrapper<GlMonthClose> qw = new LambdaQueryWrapper<>();
        // D-DEC-195：库内 gl_month_close.period 是 6 位 yyyyMM（实测行 id=3 period='202609'，close() 亦按
        // length()==6 入库），而月结看板传 '2026-12' —— 原实现直接 eq 精确匹配，格式必不等 ⇒ 恒 0 行（看板全空）。
        // 归一化收在服务端入口：yyyyMM / yyyy-MM / yyyy/MM / yy-MM 一律折成库口径，所有调用方一并治愈；
        // 空白＝不按期间过滤（月结列表页取全量的正常语义），非空而非法则显式报错，不再静默空表。
        String normPeriod = normalizePeriod(period);
        if (normPeriod != null) {
            qw.eq(GlMonthClose::getPeriod, normPeriod);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlMonthClose::getCloseNo, keyword).or().like(GlMonthClose::getVoucherNo, keyword));
        }
        qw.orderByDesc(GlMonthClose::getId);
        Page<GlMonthClose> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillWfFlowKey(p.getRecords());   // D-DEC-191：月结行带出审批流实例号/办结时刻/末节点复核人与时刻
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * D-DEC-191：给月结行富化「GL_MONTH_CLOSE 审批流」的可追责信息。
     *
     * <p>取数两条批量查询（不按行循环打库）：
     * <ol>
     *   <li>{@code wf_instance}：{@code biz_type='GL_MONTH_CLOSE' AND biz_id IN (月结单ID)}，
     *       按 id 升序覆盖 ⇒ 同一月结单被驳回后重新发起时留下<b>最新一轮</b>实例
     *       （与 {@code ArchiveService.fillArchiveFlowKey}、{@code PayBatchService.fillDisplayFields} 同口径）；
     *       实例的 {@code end_time} 即审批办结时刻＝approve_time；</li>
     *   <li>{@code wf_task_history}：上述实例里最后一条 APPROVE/AUTO_PASS 留痕 ⇒
     *       audit_time / audit_user_id / audit_user_name（与 D-DEC-189 关账人 {@link #lastApproverOf} 同一把尺）。</li>
     * </ol>
     *
     * <p>富化失败只记日志：月结看板不能因为附加列取不到而打不开。
     */
    private void fillWfFlowKey(java.util.List<GlMonthClose> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (GlMonthClose r : rows) {
            if (r != null && r.getId() != null) {
                ids.add(r.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            java.util.List<com.fssc.workflow.entity.WfInstance> flows = wfInstanceMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.workflow.entity.WfInstance>()
                            .eq(com.fssc.workflow.entity.WfInstance::getBizType, "GL_MONTH_CLOSE")
                            .in(com.fssc.workflow.entity.WfInstance::getBizId, ids)
                            .orderByAsc(com.fssc.workflow.entity.WfInstance::getId));
            Map<Long, com.fssc.workflow.entity.WfInstance> latestByCloseId = new java.util.HashMap<>();
            for (com.fssc.workflow.entity.WfInstance w : flows) {
                if (w.getBizId() != null) {
                    latestByCloseId.put(w.getBizId(), w);   // 升序覆盖 ⇒ 留下最新一条
                }
            }
            java.util.List<String> procIds = new java.util.ArrayList<>();
            for (com.fssc.workflow.entity.WfInstance w : latestByCloseId.values()) {
                if (w.getProcInstId() != null && !w.getProcInstId().isBlank()) {
                    procIds.add(w.getProcInstId());
                }
            }
            Map<String, com.fssc.workflow.entity.WfTaskHistory> lastAudit = new java.util.HashMap<>();
            if (!procIds.isEmpty()) {
                java.util.List<com.fssc.workflow.entity.WfTaskHistory> audits = wfTaskHistoryMapper.selectList(
                        new LambdaQueryWrapper<com.fssc.workflow.entity.WfTaskHistory>()
                                .select(com.fssc.workflow.entity.WfTaskHistory::getProcInstId,
                                        com.fssc.workflow.entity.WfTaskHistory::getOperTime,
                                        com.fssc.workflow.entity.WfTaskHistory::getOperUserId,
                                        com.fssc.workflow.entity.WfTaskHistory::getOperUserName)
                                .in(com.fssc.workflow.entity.WfTaskHistory::getProcInstId, procIds)
                                .in(com.fssc.workflow.entity.WfTaskHistory::getAction, "APPROVE", "AUTO_PASS")
                                .orderByAsc(com.fssc.workflow.entity.WfTaskHistory::getId));
                for (com.fssc.workflow.entity.WfTaskHistory h : audits) {
                    if (h.getProcInstId() != null) {
                        lastAudit.put(h.getProcInstId(), h);
                    }
                }
            }
            for (GlMonthClose r : rows) {
                if (r == null || r.getId() == null) {
                    continue;
                }
                com.fssc.workflow.entity.WfInstance w = latestByCloseId.get(r.getId());
                if (w == null) {
                    continue;
                }
                r.setWfProcInstId(w.getProcInstId());
                r.setWfFlowStatus(w.getStatus());
                r.setApproveTime(w.getEndTime());
                com.fssc.workflow.entity.WfTaskHistory h = w.getProcInstId() == null
                        ? null : lastAudit.get(w.getProcInstId());
                if (h != null) {
                    r.setAuditTime(h.getOperTime());
                    r.setAuditUserId(h.getOperUserId());
                    r.setAuditUserName(h.getOperUserName());
                }
            }
        } catch (Exception ex) {
            log.warn("月结行富化审批流信息失败（不影响月结列表取数）: {}", ex.getMessage());
        }
    }

    // ---------------- D-DEC-195：期间格式归一化（看板恒空的根因） ----------------

    /**
     * 把外部传入的会计期间统一成库内 6 位 {@code yyyyMM}。
     *
     * <p>接受 {@code 202612} / {@code 2026-12} / {@code 2026/12} / {@code 20261}（yyyy-M）/ {@code 26-12}（yy-MM）；
     * 其余一律 {@link ErrorCode#PARAM_INVALID} 显式报错——静默匹配不到行是本号的病根，不能靠放宽查询治。
     *
     * @param raw 原始期间串
     * @return 6 位 yyyyMM；{@code raw} 为 null/空白时返回 {@code null}（＝不按期间过滤）
     */
    private static String normalizePeriod(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        String digits = s.replaceAll("\\D", "");
        int year;
        int month;
        if (digits.length() == 6) {
            year = Integer.parseInt(digits.substring(0, 4));
            month = Integer.parseInt(digits.substring(4, 6));
        } else if (digits.length() == 5) {
            year = Integer.parseInt(digits.substring(0, 4));
            month = Integer.parseInt(digits.substring(4, 5));
        } else if (digits.length() == 4) {
            year = 2000 + Integer.parseInt(digits.substring(0, 2));
            month = Integer.parseInt(digits.substring(2, 4));
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, badPeriod(s));
        }
        if (year < 1900 || year > 2999 || month < 1 || month > 12) {
            throw new BizException(ErrorCode.PARAM_INVALID, badPeriod(s));
        }
        return String.format("%04d%02d", year, month);
    }

    /** 必输期间的归一化（月结/反结账入口）：空白与非法格式都显式报错，不落到查询里。 */
    private static String requirePeriod(String raw) {
        String norm = normalizePeriod(raw);
        if (norm == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "会计期间不能为空（yyyyMM 或 yyyy-MM，如 202612 / 2026-12）");
        }
        return norm;
    }

    private static String badPeriod(String s) {
        return "会计期间格式不正确：" + s + "（应为 yyyyMM 或 yyyy-MM，如 202612 / 2026-12）";
    }

    /**
     * 月结执行：trialBalance 校验借贷平衡，<b>写「在途」月结记录并发起 GL_MONTH_CLOSE 审批</b>；
     * 关账（md_period=CLOSED）不在本方法发生，见 {@link #applyApprovalOutcome}（D-DEC-189）。
     * D-GL-MC2 整改（2026-09-21）：①期间建档校验<b>前置</b>到写库之前——旧实现 try/catch 吞掉
     * closeByPeriod 异常但内层共享事务已被标记 rollback-only，提交时 UnexpectedRollbackException
     * 连月结记录+审批流整体回滚并抛 500；②失败即显式抛业务异常整体回滚（原子），不再留半成品行；
     * ③同公司+期间已有 CLOSED 月结记录时幂等拒绝（原 close() 无幂等保护，双击即多行）。
     * D-GL-MC1 整改：空期间（无任何凭证、试算 0=0）拒绝月结。
     * D-DEC-189 整改：①发起态由 CLOSED 改 RUNNING（审批在途），md_period 不再于提单同秒置 CLOSED；
     * ②幂等/重发守卫改按「同公司+期间<b>最新一条</b>月结记录」判定——CLOSED 拦重发、
     * RUNNING 拦重复发起（发起态不再是 CLOSED 后，原守卫会失去对双击的拦截），
     * 反结账日志行（UNCLOSED）为最新行时放行重新月结。
     */
    @Transactional(rollbackFor = Exception.class)
    public GlMonthClose close(String period, Long companyId) {
        // D-DEC-195：入口归一化（2026-12 / 2026/12 → 202612）——原校验只认 6 位，
        // 传 YYYY-MM 的入口（看板「标记完成」即月结发起）一律被拒；空白/非法仍显式报错。
        period = requirePeriod(period);
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        Long tenantId = TenantContext.getTenantId();
        Long comp = companyId != null ? companyId : 1L;

        // 幂等/在途守卫（D-DEC-189）：按同公司+期间最新一条月结记录判定
        //  - CLOSED：已月结且未反结账，拒绝重复月结（防双击/重跑多行）
        //  - RUNNING：月结审批在途，拒绝二次发起（发起态改 RUNNING 后，旧 CLOSED 守卫拦不住双击，
        //    会并存两张在途单、起两条审批流，末节通过时互相回写）
        //  - UNCLOSED/REJECTED/DRAFT：上一次已反结账/被驳回/不平未结，允许重新月结
        GlMonthClose last = mapper.selectOne(new LambdaQueryWrapper<GlMonthClose>()
                .eq(GlMonthClose::getPeriod, period)
                .eq(GlMonthClose::getCompanyId, comp)
                .orderByDesc(GlMonthClose::getId).last("LIMIT 1"));
        if (last != null && "CLOSED".equals(last.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 已月结（" + last.getCloseNo() + "），如需重新月结请先反结账");
        }
        if (last != null && STATUS_RUNNING.equals(last.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 的月结单 " + last.getCloseNo()
                            + " 审批尚在途，不能重复发起；如需变更期间数据，请先撤回或驳回该月结单");
        }

        // 前置（D-VC-2/D-GL-MC2）：期间须已建档且未结账——建档缺失在这里显式拦截, 不再发生
        // 「插入成功→联动结账失败→事务毒化」的路径
        var periodRec = periodService.periodOf(comp, year, month);
        if (periodRec == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("会计期间 %d-%02d 未建档，无法月结。请先在「会计期间维护」为该期间建档", year, month));
        }
        if ("CLOSED".equals(periodRec.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间 " + period + " 已处于结账状态，如需重月结请先反结账");
        }

        // 合规整改( )：月结前置校验——该期间+公司存在未过账(DRAFT)凭证时禁止月结，
        // 须先完成凭证审核过账（DRAFT→POSTED）再发起月结，防止未过账凭证漏出本期账。
        // 该公司当期必须已建清单且全部 DONE，缺行/未勾齐一律拦截（清单由「月结反结账」同接口维护）。
        com.fssc.glex.entity.GlCloseCheckItem chkProbe = closeCheckItemMapper.selectOne(
                new LambdaQueryWrapper<com.fssc.glex.entity.GlCloseCheckItem>()
                        .eq(com.fssc.glex.entity.GlCloseCheckItem::getCompanyId, comp)
                        .eq(com.fssc.glex.entity.GlCloseCheckItem::getPeriod, period)
                        .last("LIMIT 1"));
        if (chkProbe == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 尚未建立月结检查清单，请先在「检查闸清单」生成并逐项完成");
        }
        Long pendingChk = closeCheckItemMapper.selectCount(new LambdaQueryWrapper<com.fssc.glex.entity.GlCloseCheckItem>()
                .eq(com.fssc.glex.entity.GlCloseCheckItem::getCompanyId, comp)
                .eq(com.fssc.glex.entity.GlCloseCheckItem::getPeriod, period)
                .ne(com.fssc.glex.entity.GlCloseCheckItem::getStatus, "DONE"));
        if (pendingChk != null && pendingChk > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "月结检查闸未通过：期间 " + period + " 还有 " + pendingChk + " 项检查未完成，请逐项办结后再结账");
        }

        Long draftCount = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getCompanyId, comp)
                .eq(OpVoucher::getPeriodYear, year)
                .eq(OpVoucher::getPeriodMonth, month)
                .eq(OpVoucher::getStatus, "DRAFT"));
        if (draftCount != null && draftCount > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "月结前须完成凭证审核过账:期间 " + period + " 有 " + draftCount + " 张 DRAFT 凭证");
        }

        // D-V4R-68：账证一致闸——试算只 SUM op_gl_entry 自身借贷，凭证侧消失的分录会两侧同额虚增，
        // 于是"借贷必相等"照样成立而账证已不平（实测差 9,000＝5 张已不存在凭证的 12 行分录）。
        assertVoucherLedgerConsistent(comp, year, month, period);

        // D-GL-MC1：空期间拦截——该期间无任何凭证(VOID 除外)时试算恒为 0=0, 属假平衡
        Long voucherCount = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getCompanyId, comp)
                .eq(OpVoucher::getPeriodYear, year)
                .eq(OpVoucher::getPeriodMonth, month)
                .ne(OpVoucher::getStatus, "VOID"));
        if (voucherCount == null || voucherCount == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + period + " 无任何凭证(空期间)，不允许月结");
        }

        Map<String, Object> t = glService.trialBalance(comp, year, month);
        BigDecimal debit = dec(t.get("debitTotal"));
        BigDecimal credit = dec(t.get("creditTotal"));
        BigDecimal diff = dec(t.get("diff"));
        boolean balanced = diff.compareTo(BigDecimal.ZERO) == 0;

        long seq = countByPeriod(period) + 1;
        GlMonthClose e = new GlMonthClose();
        e.setTenantId(tenantId);
        e.setPeriod(period);
        e.setCompanyId(comp);
        e.setDebitTotal(debit);
        e.setCreditTotal(credit);
        e.setDiff(diff);
        e.setBalanced(balanced);
        e.setCloseNo("MC" + period + String.format("%03d", seq));
        // D-DEC-190：不预造 GLMC 号——本域发起不出证，预造号是空头凭证号且永久占位，
        // 使损益结转真号（JZ…）回不到月结单（GlProfitCarryService 只认 voucher_no 为空时回写）。
        e.setVoucherNo(null);
        // D-DEC-189：发起只建在途单——试算平衡＝RUNNING（月结审批中，期间<b>尚未</b>关账），
        // 不平＝DRAFT（保持原口径，不关账）。旧实现此处直接置 CLOSED，与 md_period 同秒关账，
        // 使 7 节审批退化为事后追认（实测早末节点 59 分钟）。
        e.setStatus(balanced ? STATUS_RUNNING : "DRAFT");
        e.setResultMsg(balanced
                ? "试算平衡，期间 " + period + " 借方=贷方=¥" + debit
                        + "；已发起月结审批（" + e.getCloseNo() + "），期间尚未关账——末节点通过后自动关账"
                : "借贷不平，差异 ¥" + diff.abs());
        mapper.insert(e);

        // D-DEC-189：此处不再调用 periodService.closeByPeriod()。关账动作后置到
        // applyApprovalOutcome()（末节点审批通过收尾），驳回/撤回则整个不关账。
        // 期间在审批在途期间保持开账（NORMAL），符合「先审后关」的账务控制点。

        // G1：月结发起 GL_MONTH_CLOSE 月结审批流（总账会计月结准备→财务经理复核→财务总监→总裁阈值）
        try {
            // D-DEC-191：起流返回值就是本单的审批实例号。表上暂无列可存（加列属 DDL 裁决，注释态 SQL 见实体类尾），
            // 先挂在响应的 transient 字段上——发起后前端立刻就能拿到"这张月结单挂在哪条审批流上"，
            // 后续列表由 fillWfFlowKey 从 wf_instance 富化出同一个值。
            String procInstId = wfEngineFacade.start("GL_MONTH_CLOSE", e.getId(), userId(),
                    "月结-" + period + "-公司" + comp, balanced ? debit : diff.abs());
            e.setWfProcInstId(procInstId);
            e.setWfFlowStatus(STATUS_RUNNING);
        } catch (Exception ex) {
            log.error("月结 {} 发起月结审批失败: {}", period, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return e;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    // ---------------- D-DEC-189：末节点审批结论回写（关账时点） ----------------

    /**
     * 月结审批收尾回写（由 {@code GlBizAction} 在 GL_MONTH_CLOSE 流程结束事件里调用）。
     *
     * <p><b>通过</b>：先按<b>审批结束这一刻</b>的账面重算试算与未过账闸（在途期间可能又落了新凭证），
     * 仍平衡且无 DRAFT 凭证 → gl_month_close RUNNING→CLOSED、md_period 联动 CLOSED
     * （close_time＝审批通过时刻、close_by＝末节点 APPROVE 留痕人）；
     * 若已不平衡或出现新的未过账凭证 → 置 {@value #STATUS_APPROVED_NOT_CLOSED}（审批通过·未关账），
     * 把原因写进 result_msg 并告警——<b>不冒充关账，也不静默</b>。
     *
     * <p><b>驳回/撤回</b>：RUNNING→REJECTED，期间<b>保持开账</b>（整改前是"提单即关账、驳回不重开"，
     * 被驳的月结会把期间永久锁死，只能人工反结账）。
     *
     * <p>幂等：仅当当前状态为 {@value #STATUS_RUNNING}／DRAFT（不平单被驳回）时才流转；
     * 已是 CLOSED/REJECTED/UNCLOSED 的行只记日志不改写（收尾事件重发/双发安全）。
     *
     * @param closeId   gl_month_close 主键（流程 bizId）
     * @param approved  审批结论
     * @param comment   末节点意见（可空）
     * @param endType   结束类型（WITHDRAWN＝申请人撤回，仅用于把留痕写准）
     * @param procInstId Flowable 流程实例号（用于取末节点审批人，可空）
     * @param endOperUserId 末节点操作人（由流程结束事件直接携带）。D-V4R-67：留痕行是在本方法
     *                    之后才落库的，只靠 {@link #lastApproverOf} 取数会拿到<b>上一个</b>节点的
     *                    审批人 ⇒ md_period.close_by 记错人。事件带人时以事件为准，取不到再退回留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyApprovalOutcome(Long closeId, boolean approved, String comment,
                                     String endType, String procInstId, Long endOperUserId) {
        if (closeId == null) {
            log.warn("月结收尾回写：事件未携带 bizId，跳过（无法定位月结单）");
            return;
        }
        GlMonthClose e = mapper.selectById(closeId);
        if (e == null) {
            log.warn("月结收尾回写：不存在的月结单 id={}", closeId);
            return;
        }
        String cur = e.getStatus();
        if (!STATUS_RUNNING.equals(cur) && !"DRAFT".equals(cur)) {
            log.info("月结单 {} 当前状态 {} 非在途态，收尾回写幂等跳过", e.getCloseNo(), cur);
            return;
        }
        String period = e.getPeriod();
        if (period == null || period.length() != 6) {
            log.error("月结单 {} 期间字段异常（{}），无法回写关账", e.getCloseNo(), period);
            return;
        }
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        Long comp = e.getCompanyId() != null ? e.getCompanyId() : 1L;
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (!approved) {
            boolean withdrawn = com.fssc.workflow.event.WfProcessEndedEvent.END_WITHDRAWN.equals(endType);
            GlMonthClose rej = new GlMonthClose();
            rej.setId(closeId);
            rej.setStatus(STATUS_REJECTED);
            rej.setResultMsg(msg((withdrawn ? "月结审批被撤回" : "月结审批被驳回")
                    + "（" + stamp + "）：期间 " + period + " 未关账，仍为开账状态，可修正账务后重新月结"));
            mapper.updateById(rej);
            recordCloseAudit(closeId, e, STATUS_REJECTED, (withdrawn ? "月结审批被申请人撤回，期间未关账"
                    : "月结审批被驳回，期间未关账"), comment);
            log.warn("月结单 {}（期间 {}，公司 {}）审批{} → REJECTED，期间保持开账；末节点意见：{}",
                    e.getCloseNo(), period, comp, withdrawn ? "被撤回" : "被驳回", trimComment(comment));
            return;
        }

        // 审批通过：按办结这一刻的账面复核（在途期新落的凭证可能改变平衡/留下未过账凭证）
        Long draftCount = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getCompanyId, comp)
                .eq(OpVoucher::getPeriodYear, year)
                .eq(OpVoucher::getPeriodMonth, month)
                .eq(OpVoucher::getStatus, "DRAFT"));
        Map<String, Object> t = glService.trialBalance(comp, year, month);
        BigDecimal debit = dec(t.get("debitTotal"));
        BigDecimal credit = dec(t.get("creditTotal"));
        BigDecimal diff = dec(t.get("diff"));
        boolean balanced = diff.compareTo(BigDecimal.ZERO) == 0;

        GlMonthClose upd = new GlMonthClose();
        upd.setId(closeId);
        upd.setDebitTotal(debit);
        upd.setCreditTotal(credit);
        upd.setDiff(diff);
        upd.setBalanced(balanced);
        if (draftCount != null && draftCount > 0) {
            upd.setStatus(STATUS_APPROVED_NOT_CLOSED);
            upd.setResultMsg(msg("月结审批已通过（" + stamp + "），但期间 " + period + " 新出现 " + draftCount
                    + " 张未过账凭证，本次未关账：请先过账再重新月结"));
            mapper.updateById(upd);
            recordCloseAudit(closeId, e, STATUS_APPROVED_NOT_CLOSED,
                    "月结审批通过，但期间 " + period + " 有 " + draftCount + " 张未过账凭证·本次未关账", comment);
            log.error("月结单 {} 审批通过但期间 {} 有 {} 张 DRAFT 凭证 → {}（未关账）",
                    e.getCloseNo(), period, draftCount, STATUS_APPROVED_NOT_CLOSED);
            return;
        }
        if (!balanced) {
            upd.setStatus(STATUS_APPROVED_NOT_CLOSED);
            upd.setResultMsg(msg("月结审批已通过（" + stamp + "），但办结时试算不平：借 " + debit + "/贷 " + credit
                    + " 差异 " + diff.abs() + "，本次未关账：请核实调整分录后重新月结"));
            mapper.updateById(upd);
            recordCloseAudit(closeId, e, STATUS_APPROVED_NOT_CLOSED,
                    "月结审批通过，但办结时试算不平（差异 " + diff.abs() + "）·本次未关账", comment);
            log.error("月结单 {} 审批通过但期间 {} 试算不平（差异 {}） → {}（未关账）",
                    e.getCloseNo(), period, diff, STATUS_APPROVED_NOT_CLOSED);
            return;
        }
        Long closer = endOperUserId != null ? endOperUserId : lastApproverOf(procInstId);
        upd.setStatus("CLOSED");
        upd.setResultMsg(msg("月结审批末节点通过，期间 " + period + " 于 " + stamp + " 关账；借方=贷方=¥" + debit
                + (closer == null ? "" : "；关账依据审批人 id=" + closer)));
        mapper.updateById(upd);
        // 联动关账：md_period CLOSED，close_time＝审批通过时刻，close_by＝末节点审批人
        periodService.closeByPeriod(comp, year, month, closer);
        recordCloseAudit(closeId, e, "CLOSED", "月结审批末节点通过，期间 " + period + " 于 " + stamp
                + " 关账（md_period 已联动 CLOSED" + (closer == null ? "" : "，关账人 id=" + closer) + "）", comment);
        // R5-D-170：月结 CLOSED 同步收尾在途流程实例——期间已关账而 GL_MONTH_CLOSE 实例仍 RUNNING，
        // 会长期挂着一条无人可办的清单任务污染三关前置清点（存量 MC202609001/wf_instance 74 即此）。
        // cancelRunningInstancesByBiz 仅收 RUNNING、幂等、双留痕；失败不回滚关账事实，告警留痕人工跟进。
        try {
            int cancelledWf = wfEngineFacade.cancelRunningInstancesByBiz("GL_MONTH_CLOSE", closeId,
                    "月结单已 CLOSED，收尾作废在途审批实例（R5-D-170）");
            if (cancelledWf > 0) {
                log.info("月结单 {} 关账后同步作废在途流程实例 {} 个", e.getCloseNo(), cancelledWf);
            }
        } catch (Exception ex) {
            log.warn("月结单 {} 关账后同步作废在途流程实例失败: {}", e.getCloseNo(), ex.getMessage());
        }
        log.info("月结单 {}（期间 {}，公司 {}）审批通过 → CLOSED，md_period 已关账（关账人 {}）",
                e.getCloseNo(), period, comp, closer);
    }

    /**
     * D-DEC-291：月结单终态跃迁留痕。口径与 {@code GlBizAction.recordStatusAudit} 逐字对齐
     * （bizType 固定 GL_MONTH_CLOSE、obj_type=gl_month_close、before/after 只带本次实际变化的 status 一列），
     * 不整实体 JSON、不新增审计类型。审计表只增不改，存量不回灌（178/342 既有裁决）。
     */
    private void recordCloseAudit(Long closeId, GlMonthClose before, String after, String note, String comment) {
        String c = trimComment(comment);
        java.util.Map<String, Object> b = java.util.Collections.singletonMap("status", before.getStatus());
        java.util.Map<String, Object> a = java.util.Collections.singletonMap("status", after);
        auditTrailService.recordUpdate("GL_MONTH_CLOSE", closeId, before.getCloseNo(), "gl_month_close",
                String.valueOf(closeId), b, a, note + (c == null || c.isBlank() ? "" : "：" + c));
    }

    /** result_msg 列宽口径（gl_month_close.result_msg varchar(200)）：超长截断，避免收尾回写把留痕写成报错 */
    private static final int RESULT_MSG_MAX = 200;

    private String msg(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= RESULT_MSG_MAX ? s : s.substring(0, RESULT_MSG_MAX - 1) + "…";
    }

    /** 审批意见只做告警可读性用（不落 result_msg，列宽 200 容不下）：去 HTML/空白、限长、null 安全 */
    private String trimComment(String c) {
        if (c == null || c.isBlank()) {
            return "";
        }
        String s = c.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /**
     * D-V4R-68 账证一致核对：期间内每一行总账分录都必须找得到未删除的凭证。
     * 幽灵分录（凭证已不在、分录还在）会让借贷两侧同额虚增，试算恒"平衡"而账证已不平，
     * 所以这道核对必须独立于"借=贷"那条判据存在。发现即拦，并列出票号让人去冲销/补回。
     */
    private void assertVoucherLedgerConsistent(Long comp, int year, int month, String period) {
        java.util.List<com.fssc.gl.entity.GlEntry> entries = glEntryMapper.selectList(
                new LambdaQueryWrapper<com.fssc.gl.entity.GlEntry>()
                        .eq(com.fssc.gl.entity.GlEntry::getCompanyId, comp)
                        .eq(com.fssc.gl.entity.GlEntry::getPeriodYear, year)
                        .eq(com.fssc.gl.entity.GlEntry::getPeriodMonth, month));
        if (entries.isEmpty()) {
            return;
        }
        java.util.Set<Long> vids = new java.util.LinkedHashSet<>();
        for (com.fssc.gl.entity.GlEntry e : entries) {
            if (e.getVoucherId() != null) {
                vids.add(e.getVoucherId());
            }
        }
        java.util.Set<Long> live = new java.util.HashSet<>();
        if (!vids.isEmpty()) {
            for (OpVoucher v : opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getId, vids).eq(OpVoucher::getDelFlag, 0))) {
                live.add(v.getId());
            }
        }
        java.util.Map<String, BigDecimal> ghost = new java.util.LinkedHashMap<>();
        for (com.fssc.gl.entity.GlEntry e : entries) {
            if (e.getVoucherId() != null && live.contains(e.getVoucherId())) {
                continue;
            }
            ghost.merge(e.getVoucherNo() == null ? "(无票号)" : e.getVoucherNo(),
                    e.getAmount() == null ? BigDecimal.ZERO : e.getAmount(), BigDecimal::add);
        }
        if (!ghost.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "账证不一致：期间 " + period + " 的总账里有 " + ghost.size() + " 张凭证只剩分录而无凭证（"
                            + String.join("、", ghost.keySet()) + "），试算会同额虚增仍判「平衡」。"
                            + "请先冲销或补回这些分录（凭证侧消失多为绕过界面的直接删除）再月结。");
        }
    }

    /**
     * 取末节点审批人（wf_task_history 上该实例最后一条 APPROVE/AUTO_PASS 行的操作人），只读。
     * 收尾线程无登录上下文（UserContext 为空），关账人必须来自审批留痕，否则 close_by 整列 NULL
     * 无法追责。取数失败返回 null（不影响关账本身）。
     */
    private Long lastApproverOf(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            return null;
        }
        try {
            java.util.List<com.fssc.workflow.entity.WfTaskHistory> rows = wfTaskHistoryMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.workflow.entity.WfTaskHistory>()
                            .eq(com.fssc.workflow.entity.WfTaskHistory::getProcInstId, procInstId)
                            .in(com.fssc.workflow.entity.WfTaskHistory::getAction, "APPROVE", "AUTO_PASS")
                            .isNotNull(com.fssc.workflow.entity.WfTaskHistory::getOperUserId)
                            .orderByDesc(com.fssc.workflow.entity.WfTaskHistory::getId)
                            .last("LIMIT 1"));
            return rows.isEmpty() ? null : rows.get(0).getOperUserId();
        } catch (Exception ex) {
            log.warn("月结关账人取数失败 proc={}: {}", procInstId, ex.getMessage());
            return null;
        }
    }

    // ---------------- 反结账（2026-09-14） ----------------

    private final com.fssc.system.mapper.SysUserRoleMapper sysUserRoleMapper;
    private final com.fssc.system.mapper.SysRoleMapper sysRoleMapper;

    /**
     * 反结账：将期间状态由 CLOSED 回退为开账（md_period NORMAL），并落一条 UNCLOSED 反结账日志
     * （gl_month_close，含操作人/时间）。已生成的月结凭证<b>不自动删除</b>（提示语说明，需人工冲销）。
     * <p>校验（不满足抛明确业务异常）：
     * <ol>
     *  <li>期间格式 yyyyMM；该期间确已月结（md_period 存在且 CLOSED）；</li>
     *  <li>该期间的<b>下一期间</b>尚未结账（md_period 非 CLOSED）——期间连续关闭、逐期回退（厂商口径，方案A）；</li>
     *  <li>调用者为财务总监（CFO）、总账会计（FIN_GL）或租户管理员（perms 含 *:*:*）。</li>
     * </ol>
     *
     * @param period    会计期间 yyyyMM
     * @param companyId 结账公司（空则 1，与 close 同口径）
     * @return 反结账日志行（gl_month_close，status=UNCLOSED）
     */
    @Transactional(rollbackFor = Exception.class)
    public GlMonthClose unclose(String period, Long companyId) {
        // D-DEC-195：与 close() 同口径归一化，反结账不再要求调用方先手动改成 6 位。
        period = requirePeriod(period);
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        Long tenantId = TenantContext.getTenantId();
        Long comp = companyId != null ? companyId : 1L;

        // ① 角色校验：财务总监(CFO) 或 租户管理员
        Long operUser = userId();
        if (!isCfoOrTenantAdmin(operUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "反结账仅限财务总监(CFO)或租户管理员操作");
        }

        // ② 期间须已结账（md_period CLOSED）
        var periodRec = periodService.periodOf(comp, year, month);
        if (periodRec == null || !"CLOSED".equals(periodRec.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间 " + period + " 未处于结账状态，无需反结账");
        }

        // ③ 反结账顺序守卫（方案A·对齐厂商口径 2026-09-25）：仅要求「下一期间尚未结账」，
        //   须从最后已结账期间逐期回退；下期有凭证不再拦截（反结账后按期继续核算为常态，
        int nextYear = month == 12 ? year + 1 : year;
        int nextMonth = month == 12 ? 1 : month + 1;
        var nextRec = periodService.periodOf(comp, nextYear, nextMonth);
        if (nextRec != null && "CLOSED".equals(nextRec.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "反结账拦截：下一期间 " + nextYear + String.format("%02d", nextMonth)
                            + " 仍处于结账状态，须先反结账下一期间、逐期回退（期间连续关闭口径）");
        }

        // ④ 期间回退开账（CLOSED → NORMAL）
        periodService.reopenByPeriod(comp, year, month);

        // ⑤ 反结账日志：gl_month_close 落一条 UNCLOSED 记录（操作人/时间留痕；历史 CLOSED 行保留不改）
        String operator = "unknown";
        com.fssc.framework.security.LoginUser lu = UserContext.get();
        if (lu != null) {
            operator = lu.getRealName() == null || lu.getRealName().isBlank() ? lu.getUsername() : lu.getRealName();
        }
        long seq = countByPeriod(period) + 1;
        GlMonthClose closeLog = new GlMonthClose();
        closeLog.setTenantId(tenantId);
        closeLog.setPeriod(period);
        closeLog.setCompanyId(comp);
        closeLog.setDebitTotal(BigDecimal.ZERO);
        closeLog.setCreditTotal(BigDecimal.ZERO);
        closeLog.setDiff(BigDecimal.ZERO);
        closeLog.setBalanced(true);
        closeLog.setCloseNo("MCU" + period + String.format("%03d", seq));
        closeLog.setStatus("UNCLOSED");
        closeLog.setResultMsg("反结账：期间 " + period + " 由 CLOSED 回退 OPEN；操作人：" + operator
                + "（" + operUser + "）；时间：" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "；注意：已生成的月结凭证不自动删除，需人工冲销");
        mapper.insert(closeLog);
        log.info(" 期间 {} 反结账完成（公司 {}，操作人 {}）", period, comp, operator);
        return closeLog;
    }

    /** 财务总监(CFO)角色 或 租户管理员（perms 含 *:*:*，平台超管同权）判定 */
    private boolean isCfoOrTenantAdmin(Long operUser) {
        com.fssc.framework.security.LoginUser lu = UserContext.get();
        if (lu != null && lu.isAdmin()) {
            return true;
        }
        if (operUser == null) {
            return false;
        }
        try {
            java.util.List<com.fssc.system.entity.SysUserRole> urs = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.system.entity.SysUserRole>()
                            .eq(com.fssc.system.entity.SysUserRole::getUserId, operUser));
            if (urs.isEmpty()) {
                return false;
            }
            java.util.List<Long> roleIds = urs.stream()
                    .map(com.fssc.system.entity.SysUserRole::getRoleId).toList();
            // 方案A：期间关闭属总账域日常作业，FIN_GL（持 gl:monthclose:close 的总账会计）与 CFO 同等放行
            long cfoCount = sysRoleMapper.selectBatchIds(roleIds).stream()
                    .filter(r -> "CFO".equals(r.getRoleCode()) || "FIN_GL".equals(r.getRoleCode()))
                    .count();
            return cfoCount > 0;
        } catch (Exception ex) {
            log.error("反结账角色校验异常（fail-closed 拒绝）: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlMonthClose>().eq(GlMonthClose::getPeriod, period));
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