package com.fssc.treasury.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.treasury.entity.TrFundPlan;
import com.fssc.treasury.mapper.TrFundPlanMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 资金计划服务。
 * create（编制草稿）-> selectPlan（选中关联资金计划）-> combineActual（按流水更新实际收/支并完成）。
 * P5：计划编制即发起 TR_PLAN 资金计划编制审批流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrFundPlanService {

    private final TrFundPlanMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    private final com.fssc.framework.spi.DistributedState distributedState;

    /** 同一笔提交的防重窗口：超时重发的第二笔（F1 场景，两次请求相隔约 20s）落在窗口内被拒 */
    private static final java.time.Duration SUBMIT_DEDUP_WINDOW = java.time.Duration.ofSeconds(60);
    /** 在途互斥锁的兜底存活时间：远大于单次编制耗时，防止异常退出后锁泄漏 */
    private static final java.time.Duration SUBMIT_LOCK_TTL = java.time.Duration.ofMinutes(5);

    /** 编制资金计划（草稿） */
    public Long create(TrFundPlan plan) {
        if (plan.getIncomeAmount() == null) plan.setIncomeAmount(BigDecimal.ZERO);
        if (plan.getOutcomeAmount() == null) plan.setOutcomeAmount(BigDecimal.ZERO);
        if (plan.getIncomeActual() == null) plan.setIncomeActual(BigDecimal.ZERO);
        if (plan.getOutcomeActual() == null) plan.setOutcomeActual(BigDecimal.ZERO);
        if (plan.getStatus() == null) plan.setStatus("DRAFT");
        plan.setId(null);
        plan.setTenantId(TenantContext.getTenantId());
        // 缺陷D3(20260919)：计划必须归属真实公司——company_id 缺省时数据库默认落 0（不存在的外部公司），
        // 引擎 roleHit 按「角色范围×单据公司」过滤恒失配，节点永久无人可办（孤儿 FP202609180001 的根因）。
        // 此处缺省回填当前用户公司，并做组织合法性校验。
        if (plan.getCompanyId() == null || plan.getCompanyId() <= 0) {
            plan.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(plan.getCompanyId());
        // 缺陷修复（H-06 / DF-03）：计划编号在创建时即取号（sys_number_rule TR_PLAN，FP 前缀），
        // 原实现不落 plan_no 且审批结束事件不回写，导致 tr_fund_plan 历史行 plan_no=NULL、
        // 审批待办/列表单据号列恒空。
        if (plan.getPlanNo() == null || plan.getPlanNo().isBlank()) {
            plan.setPlanNo(nextPlanNo());
        }
        // F1 后端幂等闸（与前端重试白名单/防连点互补）：一次点击因请求超时被重发的第二笔，
        // 会在同一签名上撞锁或被防重窗口拦下。签名取「租户+公司+期间+编制人+收付金额」＝
        // 同一次点击的两笔必然同值，而同一期间的不同方案（选中流程要求可以多案）不受影响。
        String submitKey = submitSignature(plan, userId());
        if (distributedState.marked(submitKey)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "重复提交：相同内容的资金计划在 " + SUBMIT_DEDUP_WINDOW.toSeconds() + " 秒内已提交成功，请刷新列表查看，勿再次提交");
        }
        if (!distributedState.tryLock(submitKey, SUBMIT_LOCK_TTL)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "相同内容的资金计划正在提交中，请稍后刷新列表查看结果");
        }
        try {
            mapper.insert(plan);
            // P5：计划编制即发起 TR_PLAN 资金计划编制审批流（资金岗编制→共享主管复核→财务经理阈值审批）
            try {
                wfEngineFacade.start("TR_PLAN", plan.getId(), userId(),
                        "资金计划-" + plan.getId(), plan.getIncomeAmount() == null ? BigDecimal.ZERO : plan.getIncomeAmount());
            } catch (Exception ex) {
                log.error("资金计划 {} 发起计划审批失败: {}", plan.getId(), ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
            // 只有成功落单才立窗口；失败不立，用户可立即重试
            distributedState.mark(submitKey, SUBMIT_DEDUP_WINDOW);
        } finally {
            distributedState.unlock(submitKey);
        }
        return plan.getId();
    }

    /**
     * 编制提交的幂等签名（key 自带命名空间，约定见 DistributedState）。
     * 末段取 MD5：本地实现的锁走 MySQL GET_LOCK，锁名上限 64 字符（实测超长直接报 4163），
     * 明文拼接「租户+公司+期间+编制人+两个金额」会越过该上限并把接口打成 500。
     */
    private static String submitSignature(TrFundPlan plan, Long creatorId) {
        String raw = plan.getTenantId() + ':' + plan.getCompanyId() + ':' + plan.getPeriod() + ':' + creatorId
                + ':' + plan.getIncomeAmount().toPlainString() + ':' + plan.getOutcomeAmount().toPlainString();
        return "fssc:tr:fp:create:" + org.springframework.util.DigestUtils
                .md5DigestAsHex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 选中（入选）关联资金计划：DRAFT/SELECTED 均可进入 SELECTED */
    @Transactional
    public void selectPlan(Long id) {
        TrFundPlan plan = requireExist(id);
        if ("COMPLETED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已完成资金计划不可重新选中");
        }
        assertNotRejected(plan, "选中");
        plan.setStatus("SELECTED");
        mapper.updateById(plan);
    }

    /**
     * 批次1：驳回计划编辑重提 REJECTED → DRAFT 并重新发起 TR_PLAN 审批流（起【新】实例）。
     * 此前 TreasuryPlanBizAction 驳回置 REJECTED 后无任何出口，且 selectPlan/adjust 只挡
     * COMPLETED，被驳回计划可被"选中/调整"侧门带病推进——两头一并收口。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long planId) {
        TrFundPlan plan = requireExist(planId);
        if (!"REJECTED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已驳回资金计划可编辑重提，当前状态: " + plan.getStatus());
        }
        if (wfEngineFacade.hasActiveTask("TR_PLAN", planId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请勿重复提交：该资金计划已有在办审批流程");
        }
        TrFundPlan upd = new TrFundPlan();
        upd.setId(planId);
        upd.setStatus("DRAFT");
        mapper.updateById(upd);
        try {
            wfEngineFacade.start("TR_PLAN", planId, userId(),
                    "资金计划-" + planId, plan.getIncomeAmount() == null ? BigDecimal.ZERO : plan.getIncomeAmount());
        } catch (Exception ex) {
            log.error("资金计划 {} 重新发起审批失败: {}", planId, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡（事务回滚，状态还原 REJECTED）
        }
    }

    private void assertNotRejected(TrFundPlan plan, String action) {
        if ("REJECTED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "已驳回的资金计划不可" + action + "，请先编辑重提（驳回后重走审批）");
        }
    }

    /**
     * D-G5-05：作废资金计划（<b>不是物理删除</b>）。
     *
     * <p><b>为什么需要这个出口</b>：此前资金计划<b>没有删除/作废端点</b>，
     * 跑测者想清理一张重复的草稿只能直接删表 ⇒ 产生两个后果：
     * <ol>
     *   <li>删表<b>不联动</b>终止流程实例 ⇒ {@code wf_instance} 仍 RUNNING、
     *       {@code act_ru_task} 留一个<b>无 assignee</b> 的僵尸待办（实测 FP202610200001）；
     *       手工清理引擎表还会撞外键（{@code act_ru_identitylink.TASK_ID_}→{@code act_ru_task.ID_}，报 1451）。</li>
     *   <li>物理删除不留痕，事后无法追溯「这张单为什么没了」。</li>
     * </ol>
     *
     * <p><b>本方法的口径</b>：单据置 {@code VOIDED}（留痕，不删行）＋
     * <b>联动终止</b>该单名下所有 RUNNING 流程实例。
     * 流程终止<b>不自己实现</b>，直接复用已验证可用的
     * {@link WfEngineFacade#cancelRunningInstancesByBiz}（D-DEC-068 确立的标准收尾入口：
     * 引擎实例删除＋wf_instance 置 CANCELLED＋wf_task 的 PENDING 置 CANCELLED＋双留痕）。
     *
     * <p><b>可作废的状态</b>：DRAFT / SELECTED / REJECTED（尚未进入实质执行）。
     * 已 COMPLETED/PAID/CLOSED/ARCHIVED 的单据有后续凭证与账务影响，不允许作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidPlan(Long planId, String reason) {
        TrFundPlan plan = requireExist(planId);
        String status = plan.getStatus();
        if (!"DRAFT".equals(status) && !"SELECTED".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅草稿/已选中/已驳回的资金计划可作废，当前状态: " + status);
        }
        // 流程收尾：作废必须同时终止在办流程，否则待办镜像滞留 PENDING、催办扫描持续空转
        // （D-DEC-068 口径）。该方法幂等：无 RUNNING 实例返回 0，不抛错。
        int cancelled = wfEngineFacade.cancelRunningInstancesByBiz("TR_PLAN", planId,
                "资金计划作废（操作人 id=" + userId() + "）：" + (reason == null || reason.isBlank() ? "未填原因" : reason));
        TrFundPlan upd = new TrFundPlan();
        upd.setId(planId);
        upd.setStatus("VOIDED");
        upd.setUpdateBy(userId());
        upd.setUpdateTime(java.time.LocalDateTime.now());
        mapper.updateById(upd);
        log.info("资金计划已作废：id={} 单号={} 原状态={} 终止流程实例数={} 原因={}",
                planId, plan.getPlanNo(), status, cancelled, reason);
    }

    /**
     * 资金计划调整（T1）：在计划收/支基础上追加调整额，并发起 TR_PLAN_ADJUST 计划调整审批流。
     *
     * <p>缺陷修复（H-09 / DF-01）：原实现对调整额无任何校验，任意负数/超额数值都会直接并入计划。
     * 现增加三重校验（不通过抛友好业务异常，前端无需改造）：</p>
     * <ul>
     *   <li>调整额不可全为 0（至少一个方向有实际调整）；</li>
     *   <li>调整后期间计划金额（收入/支出）均不能为负；</li>
     *   <li>单方向单次调整额绝对值 ≤ 当期计划总额（收入+支出，即最多翻一倍），超限拦截。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long id, BigDecimal incomeAdjust, BigDecimal outcomeAdjust) {
        TrFundPlan plan = requireExist(id);
        if ("COMPLETED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已完成资金计划不可调整");
        }
        assertNotRejected(plan, "调整");
        BigDecimal incAdj = nz(incomeAdjust);
        BigDecimal outcAdj = nz(outcomeAdjust);
        if (incAdj.compareTo(BigDecimal.ZERO) == 0 && outcAdj.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整失败：收入/支出调整额不能同时为空或 0");
        }
        BigDecimal curInc = nz(plan.getIncomeAmount());
        BigDecimal curOutc = nz(plan.getOutcomeAmount());
        BigDecimal inc = curInc.add(incAdj);
        BigDecimal outc = curOutc.add(outcAdj);
        if (inc.compareTo(BigDecimal.ZERO) < 0 || outc.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("调整失败：调整后期间计划金额不能为负（当前 收入 %s / 支出 %s，调整 收入 %s / 支出 %s）",
                            curInc, curOutc, incAdj, outcAdj));
        }
        BigDecimal cap = curInc.add(curOutc);
        if (incAdj.abs().compareTo(cap) > 0 || outcAdj.abs().compareTo(cap) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("调整失败：单次调整额不能超过当期计划总额 %s（本次调整 收入 %s / 支出 %s），如有需要请分次调整或先编制新计划",
                            cap, incAdj, outcAdj));
        }
        plan.setIncomeAmount(inc);
        plan.setOutcomeAmount(outc);
        mapper.updateById(plan);
        try {
            wfEngineFacade.start("TR_PLAN_ADJUST", plan.getId(), userId(),
                    "资金计划调整-" + plan.getId(), inc.max(outc));
        } catch (Exception ex) {
            log.error("资金计划 {} 发起调整审批失败: {}", plan.getId(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 资金计划取号：sys_number_rule TR_PLAN（FP 前缀）；规则缺失时降级时间戳号，不阻断编制 */
    private String nextPlanNo() {
        try {
            return numberRuleService.next("TR_PLAN");
        } catch (Exception ex) {
            log.warn("资金计划取号失败（规则 TR_PLAN 缺失/停用?），降级时间戳编号: {}", ex.getMessage());
            return "FP" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
    }

    /** 按流水数据更新实际收/支，命中计划目标后置为 COMPLETED */
    @Transactional
    public void combineActual(Long id, BigDecimal incomeActual, BigDecimal outcomeActual) {
        TrFundPlan plan = requireExist(id);
        incomeActual = incomeActual == null ? plan.getIncomeActual() : incomeActual;
        outcomeActual = outcomeActual == null ? plan.getOutcomeActual() : outcomeActual;
        plan.setIncomeActual(incomeActual == null ? BigDecimal.ZERO : incomeActual);
        plan.setOutcomeActual(outcomeActual == null ? BigDecimal.ZERO : outcomeActual);
        if (plan.getStatus() == null) plan.setStatus("DRAFT");
        if (plan.getIncomeAmount() != null && plan.getOutcomeAmount() != null
                && plan.getIncomeActual().compareTo(plan.getIncomeAmount()) >= 0
                && plan.getOutcomeActual().compareTo(plan.getOutcomeAmount()) >= 0) {
            plan.setStatus("COMPLETED");
        }
        mapper.updateById(plan);
    }

    public TrFundPlan detail(Long id) {
        TrFundPlan plan = requireExist(id);
        orgGuard.assertVisible(plan.getCompanyId(), plan.getOwnerId());
        return plan;
    }

    public PageResult<TrFundPlan> page(long pageNum, long pageSize, String period, String status) {
        LambdaQueryWrapper<TrFundPlan> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) qw.eq(TrFundPlan::getPeriod, period);
        if (StringUtils.hasText(status)) qw.eq(TrFundPlan::getStatus, status);
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(TrFundPlan::getCompanyId, scopeC);
        }
        qw.orderByDesc(TrFundPlan::getId);
        Page<TrFundPlan> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // ---------------- P0-5 状态机：APPROVED→PAID→CLOSED→ARCHIVED ----------------

    /** P0-5：资金计划 APPROVED/COMPLETED→PAID（资金拨付完成时触发） */
    @Transactional
    public void markPaid(Long planId) {
        TrFundPlan plan = requireExist(planId);
        if (!"APPROVED".equals(plan.getStatus()) && !"COMPLETED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已审批或已完成的资金计划可标记已付款，当前状态: " + plan.getStatus());
        }
        TrFundPlan update = new TrFundPlan();
        update.setId(planId);
        update.setStatus("PAID");
        mapper.updateById(update);
        log.info("资金计划 {} 状态 →PAID", planId);
    }

    /** P0-5：资金计划 PAID→CLOSED（关账） */
    @Transactional
    public void closePlan(Long planId) {
        TrFundPlan plan = requireExist(planId);
        if (!"PAID".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已付款资金计划可关账，当前状态: " + plan.getStatus());
        }
        TrFundPlan update = new TrFundPlan();
        update.setId(planId);
        update.setStatus("CLOSED");
        mapper.updateById(update);
        log.info("资金计划 {} 状态 PAID→CLOSED", planId);
    }

    /** P0-5：资金计划 CLOSED→ARCHIVED（归档，终态） */
    @Transactional
    public void archivePlan(Long planId) {
        TrFundPlan plan = requireExist(planId);
        if (!"CLOSED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已关账资金计划可归档，当前状态: " + plan.getStatus());
        }
        TrFundPlan update = new TrFundPlan();
        update.setId(planId);
        update.setStatus("ARCHIVED");
        mapper.updateById(update);
        log.info("资金计划 {} 状态 CLOSED→ARCHIVED", planId);
    }

    private TrFundPlan requireExist(Long id) {
        TrFundPlan plan = mapper.selectById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金计划不存在：" + id);
        }
        return plan;
    }
}