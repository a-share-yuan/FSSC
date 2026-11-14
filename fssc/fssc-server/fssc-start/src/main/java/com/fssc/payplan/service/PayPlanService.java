package com.fssc.payplan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.spi.DistributedState;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.payplan.entity.PayPlan;
import com.fssc.payplan.mapper.PayPlanMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 采购付款计划服务：分页查询 + 新建并发起审批。
 * <p>基地付款计划(PayPlan.planType=BASE)发起 {@code PAY_PLAN_BASE}；
 * 板块付款计划(planType=PLATE)发起 {@code PAY_PLAN_PLATE}。
 * 均调 WfEngineFacade.start() 走版本化 BPMN 审批（基地:基地采购部直接上级→项目经理→基地财务经理→基地总经理；
 * 板块:项目助理总监/板块资金主任/板块财务经理/采购助理总监并行会签→采购总监→副总裁→板块财务总监→总裁）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayPlanService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Duration SUBMIT_DEDUP_WINDOW = Duration.ofSeconds(60);
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofMinutes(5);

    private final PayPlanMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    /** 审计：驳回重提状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 去重校验载体（与 TrFundPlanService B6 同用 DistributedState） */
    private final DistributedState distributedState;

    public PageResult<PayPlan> page(long pn, long ps, String keyword, String planType) {
        LambdaQueryWrapper<PayPlan> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(PayPlan::getPlanNo, keyword)
                    .or().like(PayPlan::getSupplier, keyword)
                    .or().like(PayPlan::getPurpose, keyword));
        }
        if (StringUtils.hasText(planType)) {
            qw.eq(PayPlan::getPlanType, planType);
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(PayPlan::getCompanyId, scopeC);
        }
        qw.orderByDesc(PayPlan::getId);
        Page<PayPlan> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /** 新建付款计划并发起对应审批流，返回单据ID。 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(PayPlan plan) {
        validate(plan);
        LoginUser u = UserContext.get();
        Long uid = u == null ? null : u.getUserId();
        Long companyId = plan.getCompanyId() == null ? (u == null ? null : u.getCompanyId()) : plan.getCompanyId();
        if (companyId != null) {
            orgGuard.assertCompanyAllowed(companyId);
        }
        plan.setId(null);
        plan.setPlanNo(genNo(plan.getPlanType()));
        plan.setCompanyId(companyId);
        plan.setApplicantId(uid);
        plan.setStatus("APPROVING");
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());

        // （F1 后端环节，与 B6/TrFundPlanService 同型）：一次点击被重试机制原样重发的第二笔，
        // 在这里撞防重窗口或锁。签名不能用 planNo——genNo 带秒级时间戳＋随机数，重发两笔必然不同号，拿它去重等于不设校验。
        String submitKey = submitSignature(plan, uid);
        if (distributedState.marked(submitKey)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "重复提交：相同内容的付款计划在 " + SUBMIT_DEDUP_WINDOW.toSeconds() + " 秒内已提交成功，请刷新列表查看，勿再次提交");
        }
        if (!distributedState.tryLock(submitKey, SUBMIT_LOCK_TTL)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "相同内容的付款计划正在提交中，请稍后刷新列表查看结果");
        }
        try {
            mapper.insert(plan);

            String flowKey = "PLATE".equals(plan.getPlanType()) ? "PAY_PLAN_PLATE" : "PAY_PLAN_BASE";
            String title = ("PLATE".equals(plan.getPlanType()) ? "板块" : "基地") + "采购付款计划-"
                    + (StringUtils.hasText(plan.getSupplier()) ? plan.getSupplier() : plan.getPlanNo());
            String procInstId = wfEngineFacade.start(flowKey, plan.getId(), uid, title, plan.getAmount());
            PayPlan upd = new PayPlan();
            upd.setId(plan.getId());
            upd.setProcInstId(procInstId);
            upd.setStatus("APPROVING");
            mapper.updateById(upd);
            // 只有成功落单才立窗口；失败不立，用户可立即重试
            distributedState.mark(submitKey, SUBMIT_DEDUP_WINDOW);
        } catch (Exception ex) {
            log.error("付款计划 {} 发起审批失败: {}", plan.getPlanNo(), ex.getMessage(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID, "发起付款计划审批失败: " + ex.getMessage());
        } finally {
            distributedState.unlock(submitKey);
        }
        return plan.getId();
    }

    /**
     * 提交去重签名（租户＋公司＋期间＋供应商＋金额＋用途＋经办人）。
     * 末段 MD5：本地实现取 MySQL GET_LOCK，锁名上限 64 字符（超长会报 4163 把接口打成 500）。
     * 供应商优先取档案 id，档案缺失时退供应商名（允许 supplier_id 为 NULL）。
     */
    private static String submitSignature(PayPlan plan, Long applicantId) {
        String supplier = plan.getSupplierId() != null ? String.valueOf(plan.getSupplierId()) : plan.getSupplier();
        String raw = TenantContext.getTenantId() + ':' + plan.getCompanyId() + ':'
                + plan.getPeriod() + ':' + supplier + ':' + plan.getAmount().toPlainString() + ':'
                + plan.getPurpose() + ':' + applicantId;
        return "fssc:pay:plan:create:" + DigestUtils
                .md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 提单人作废在途付款计划 APPROVING → VOID。
     * 不给这条出口的话 APPROVING 单只能任其挂在审批池，或由引擎开发端点裸作废实例（那条环节不写业务状态、不记录）。
     * 前置＝提单人本人＋APPROVING＋未发生核销；审批链由 cancelInstance 同步终止，失败冒泡回滚。
     * 若审批链已被引擎开发端点"裸作废"（该端点按设计只终结流程、不回写业务态），
     * cancelInstance 会抛「实例不存在或已结束」＝这条单永远废不掉＝死单。现只对该种异常放行、照常落 VOID。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidPlan(Long planId, String reason) {
        PayPlan plan = mapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款计划不存在: " + planId);
        }
        LoginUser u = UserContext.get();
        Long uid = u == null ? null : u.getUserId();
        if (uid == null || !uid.equals(plan.getApplicantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅提单人本人可作废该付款计划");
        }
        if (!"APPROVING".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中的付款计划可作废，当前状态: " + plan.getStatus());
        }
        if (plan.getPaidAmount() != null && plan.getPaidAmount().compareTo(ZERO) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该计划已核销 " + plan.getPaidAmount().toPlainString() + " 元，不可作废");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因");
        }
        boolean engineAlreadyEnded = false;
        if (StringUtils.hasText(plan.getProcInstId())) {
            // 审批面可能已被"裸作废"终结（引擎开发端点 cancelInstance 只删流程实例＋回写
            // wf_instance，按设计不触发业务收尾），于是业务单仍挂 APPROVING、待办却一条不剩＝既办不了也废不掉。
            // 这里把"引擎终止"做成去重臂：只吞「实例不存在或已结束」这一种 PARAM_INVALID，其余异常照旧冒泡
            // 流程失败不得静默的规则不变：业务侧 VOID＋audit_trail 照常办结。
            try {
                wfEngineFacade.cancelInstance(plan.getProcInstId(), "提单人作废 " + plan.getPlanNo() + "：" + reason);
            } catch (BizException ex) {
                boolean alreadyEnded = ex.getCode() == ErrorCode.PARAM_INVALID.getCode()
                        && ex.getMessage() != null && ex.getMessage().contains("实例不存在或已结束");
                if (!alreadyEnded) {
                    throw ex;
                }
                engineAlreadyEnded = true;
                log.warn("付款计划 {} 的审批面此前已终结（{}），本次作废只改业务态 APPROVING→VOID",
                        plan.getPlanNo(), ex.getMessage());
            }
        }
        PayPlan upd = new PayPlan();
        upd.setId(planId);
        upd.setStatus("VOID");
        upd.setUpdateTime(LocalDateTime.now());
        mapper.updateById(upd);
        auditTrailService.recordUpdate("PAY_PLAN", planId, plan.getPlanNo(), "pay_plan",
                String.valueOf(planId),
                Collections.singletonMap("status", "APPROVING"),
                Collections.singletonMap("status", "VOID"),
                "提单人作废：" + reason + (engineAlreadyEnded ? "（审批面此前已由运维终结，本次仅办结业务状态）" : ""));
        log.info("付款计划 {} 作废: APPROVING→VOID（原因：{}{}）", plan.getPlanNo(), reason,
                engineAlreadyEnded ? "，审批面此前已终结" : "");
    }

    /**
     * 驳回付款计划重报 REJECTED → 重新审批：驳回置 REJECTED 后
     * 单据在 REJECTED 上没有出口，只能新建一条计划单绕过。重发起对应审批流（新实例），失败冒泡回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long planId) {
        PayPlan plan = mapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款计划不存在: " + planId);
        }
        if (!"REJECTED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已驳回的付款计划可重报，当前状态: " + plan.getStatus());
        }
        if (wfEngineFacade.hasActiveTask("PLATE".equals(plan.getPlanType()) ? "PAY_PLAN_PLATE" : "PAY_PLAN_BASE", planId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请勿重复提交：该计划已有在办审批流程");
        }
        PayPlan upd = new PayPlan();
        upd.setId(planId);
        upd.setStatus("APPROVING");
        upd.setUpdateTime(LocalDateTime.now());
        mapper.updateById(upd);
        String flowKey = "PLATE".equals(plan.getPlanType()) ? "PAY_PLAN_PLATE" : "PAY_PLAN_BASE";
        String title = ("PLATE".equals(plan.getPlanType()) ? "板块" : "基地") + "采购付款计划-"
                + (StringUtils.hasText(plan.getSupplier()) ? plan.getSupplier() : plan.getPlanNo()) + "(重报)";
        try {
            String procInstId = wfEngineFacade.start(flowKey, planId, plan.getApplicantId(), title, plan.getAmount());
            PayPlan flowUpd = new PayPlan();
            flowUpd.setId(planId);
            flowUpd.setProcInstId(procInstId);
            mapper.updateById(flowUpd);
        } catch (Exception ex) {
            log.error("付款计划 {} 重报发起审批失败: {}", plan.getPlanNo(), ex.getMessage(), ex);
            throw ex; // 流程发起失败必须冒泡，否则单据停在旧状态而流程已起
        }
        auditTrailService.recordUpdate("PAY_PLAN", planId, plan.getPlanNo(), "pay_plan",
                String.valueOf(planId),
                Collections.singletonMap("status", "REJECTED"),
                Collections.singletonMap("status", "APPROVING"), "驳回后重报审批");
        log.info("付款计划 {} 驳回重报: REJECTED→APPROVING", plan.getPlanNo());
    }

    private void validate(PayPlan p) {
        if (p == null || !StringUtils.hasText(p.getPlanType())
                || (!"BASE".equals(p.getPlanType()) && !"PLATE".equals(p.getPlanType()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "计划类型必填（BASE=基地 / PLATE=板块）");
        }
        if (!StringUtils.hasText(p.getSupplier())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商必填");
        }
        if (p.getAmount() == null || p.getAmount().compareTo(ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款金额必须大于0");
        }
        if (!StringUtils.hasText(p.getPurpose())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用途说明必填");
        }
        // 计划期间决定付款挂到哪个月度资金计划，留空会落一条无法归集的计划（period 在 DDL 可空，DB 不拦）
        if (!StringUtils.hasText(p.getPeriod())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择计划期间");
        }
    }

    private String genNo(String planType) {
        String prefix = "PLATE".equals(planType) ? "PP" : "PB";
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return prefix + LocalDateTime.now().format(NO_FMT) + rand;
    }
}