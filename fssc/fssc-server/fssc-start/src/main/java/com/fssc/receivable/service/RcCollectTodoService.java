// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcCollectTodo;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcCollectTodoMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 催收待办服务：扫描逾期应收自动生成催收待办，处理后可标记已解决。
 * 状态：DOING 催收中 / RESOLVED 已解决。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RcCollectTodoService {

    private final RcCollectTodoMapper mapper;
    private final RcReceivableMapper receivableMapper;
    private final WfEngineFacade wfEngineFacade;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 公司维度读取守卫（催收待办列表接 OrgGuard，与应收单同一规则） */
    private final OrgGuard orgGuard;
    /**
     * 乙：只读取流程侧记录（发起人／任务承办人）来数清同一人在一条催收任务上占了几个岗，
     * 不改候选取人、不动引擎守卫、不做拦截。仅 SELECT。
     */
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;

    public List<RcCollectTodo> list() {
        LambdaQueryWrapper<RcCollectTodo> qw = new LambdaQueryWrapper<RcCollectTodo>()
                .orderByDesc(RcCollectTodo::getCreateTime);
        // 公司维度读取过滤，与 RcReceivableService.page 同款守卫同一规则（rc_collect_todo.company_id 为直接列）
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(RcCollectTodo::getCompanyId, scopeC);
        }
        List<RcCollectTodo> rows = mapper.selectList(qw);
        // 乙：读侧提示位（零额外查询，只看既有审计列）。
        // 建单时 MP 会把 update_by 一并填成 create_by，故只有"已办结"行才可比对，否则会全员误报。
        for (RcCollectTodo t : rows) {
            if ("RESOLVED".equals(t.getStatus()) && isSamePerson(t.getCreateBy(), t.getUpdateBy())) {
                t.setMultiRoleWarning("该催收待办由同一人发起并本人办结，建议由他人复核办结结论");
            }
        }
        return rows;
    }

    /** 审计列判同一人：任一为 null／0（无登录上下文时的填充值，判不出人）都不算 */
    private static boolean isSamePerson(Long a, Long b) {
        return a != null && a != 0L && a.equals(b);
    }

    /**
     * 扫描逾期应收自动生成催收待办：逾期天数 &gt;= days 且尚未存在 DOING 状态的待办时生成。
     * <p>基准日＝系统当前日；补跑历史／未来账期请用
     * {@link #createOverdueTodos(int, LocalDate)}。
     */
    @Transactional
    public int createOverdueTodos(int days) {
        return createOverdueTodos(days, null);
    }

    /**
     * 催收待办生成（带账期基准日）。
     * <p>原实现把 {@code LocalDate.now()} 写死在逾期天数算法里（{@code between(dueDate, now)}），
     * 与 {@code accrueOverdueBadDebt} 同族：业务期间晚于系统日时，天数算不出、阈值校验（days）也拦不住，
     * 待办恒 0 条。本重载只把"与哪一天比"参数化，阈值语义（{@code overdueDays >= days}）一字未改。
     *
     * @param days 逾期天数阈值（不是基准日，已自纠：阈值不是空集成因）
     * @param asOfDate 账期／跑批基准日；null 回退系统当前日（与旧行为逐字一致）
     */
    @Transactional
    public int createOverdueTodos(int days, LocalDate asOfDate) {
        LocalDate baseline = asOfDate == null ? LocalDate.now() : asOfDate;
        List<RcReceivable> overdue = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getStatus, "OVERDUE")
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .isNotNull(RcReceivable::getDueDate));
        int created = 0;
        Long firstId = null;
        // 本次催收清单的逾期余额合计——起流时作为流程金额（一流程覆盖多条待办）
        BigDecimal collectAmount = BigDecimal.ZERO;
        for (RcReceivable r : overdue) {
            long overdueDays = ChronoUnit.DAYS.between(r.getDueDate(), baseline);
            if (overdueDays < days) continue;
            Long exist = mapper.selectCount(new LambdaQueryWrapper<RcCollectTodo>()
                    .eq(RcCollectTodo::getReceivableId, r.getId())
                    .eq(RcCollectTodo::getStatus, "DOING"));
            if (exist != null && exist > 0) continue;
            RcCollectTodo todo = new RcCollectTodo();
            todo.setTenantId(TenantContext.getTenantId());
            todo.setReceivableId(r.getId());
            todo.setCustomerId(r.getCustomerId());
            todo.setBillNo(r.getBillNo());
            todo.setDueDate(r.getDueDate());
            todo.setOverdueDays((int) overdueDays);
            todo.setStatus("DOING");
            // 多分子公司审批路由：优先取来源应收单的公司，取不到回退登录用户
            if (todo.getCompanyId() == null) {
                Long cid = r.getCompanyId();
                if (cid == null) {
                    LoginUser lu = UserContext.get();
                    if (lu != null) cid = lu.getCompanyId();
                }
                todo.setCompanyId(cid);
            }
            mapper.insert(todo);
            // 起流 bizId 必须传应收单 id（CollectBizAction 按 RcReceivable 收尾核销），
            // 原传催收待办 id 导致审批通过后"不存在的应收单"空转
            if (firstId == null) firstId = r.getId();
            collectAmount = collectAmount.add(r.getRemainAmount() == null ? BigDecimal.ZERO : r.getRemainAmount());
            created++;
        }
        // 生成催收待办后发起 OTC04 应收账款催收管理审批流（bizType=RECEIVABLE_COLLECT）
        if (created > 0 && firstId != null) {
            try {
                // 传逾期余额合计（RECEIVABLE_COLLECT 当前无金额网关 ⇒ 不改路由）
                wfEngineFacade.start("RECEIVABLE_COLLECT", firstId, userId(),
                        "催收清单-" + baseline, collectAmount);
            } catch (Exception ex) {
                log.error("催收待办 {} 发起审批失败: {}", firstId, ex.getMessage(), ex);
                throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
            }
        }
        return created;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 解决催收待办。
     * <p>乙档：只做提示校验——办结时数一遍同一人在这一条催收任务上占了几岗
     * （发起＝待办 create_by，起单＝RECEIVABLE_COLLECT 流程发起人，领取承办＝流程任务处理人，
     * 办结＝本次操作人），两岗及以上回非阻断复核提示位；不拦办结、不改候选取人、不动引擎守卫。
     * <p>返回体由 Void 改结构化 Map 是增量：该端点原 data 恒为 null，现调用方（应收看板）忽略返回值。
     */
    public Map<String, Object> solve(Long todoId) {
        RcCollectTodo todo = require(todoId);
        if ("RESOLVED".equals(todo.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该待办已解决");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", todo.getStatus());
        RcCollectTodo u = new RcCollectTodo();
        u.setId(todoId);
        u.setStatus("RESOLVED");
        mapper.updateById(u);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", "RESOLVED");
        auditTrailService.recordUpdate("RC_COLLECT_TODO", todoId, todo.getBillNo(), "rc_collect_todo",
                String.valueOf(todoId), auditBefore, auditAfter, "催收待办已解决");

        Long operator = userId();
        List<String> roles = new ArrayList<>();
        if (operator != null) {
            if (isSamePerson(todo.getCreateBy(), operator)) roles.add("发起（扫描生成待办）");
            if (isSamePerson(instanceStarter(todo.getReceivableId()), operator)) roles.add("起单（发起催收审批）");
            if (hasHandledTask(todo.getReceivableId(), operator)) roles.add("领取承办（催收流程任务）");
            roles.add("办结（标记已解决）");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("todoId", todoId);
        res.put("billNo", todo.getBillNo());
        res.put("receivableId", todo.getReceivableId());
        res.put("status", "RESOLVED");
        res.put("roles", roles);
        boolean multiRole = roles.size() >= 2;
        res.put("multiRole", multiRole);
        String warning = multiRole
                ? "本条催收任务由同一人承担 " + roles.size() + " 岗（" + String.join("、", roles)
                        + "），催收结论未经他人复核，建议由他人复核后归档"
                : null;
        res.put("warning", warning);
        if (multiRole) {
            log.warn("催收待办 {} 复核提示：同一人承担 {} 岗（{}）",
                    todo.getBillNo(), roles.size(), String.join("、", roles));
        }
        return res;
    }

    /** 催收审批流发起人（wf_instance.start_user_id；无实例或发起人为 0＝判不出人，返回 null） */
    private Long instanceStarter(Long receivableId) {
        if (receivableId == null) return null;
        List<WfInstance> ins = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBizType, "RECEIVABLE_COLLECT")
                        .eq(WfInstance::getBizId, receivableId)
                        .orderByAsc(WfInstance::getId));
        for (WfInstance wi : ins) {
            Long su = wi.getStartUserId();
            if (su != null && su != 0L) return su;
        }
        return null;
    }

    /** 本次操作人是否领取承办过本条催收的流程任务（wf_task.assignee_id 记录，只读） */
    private boolean hasHandledTask(Long receivableId, Long operator) {
        if (receivableId == null || operator == null) return false;
        Long n = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getBizType, "RECEIVABLE_COLLECT")
                .eq(WfTask::getBizId, receivableId)
                .eq(WfTask::getAssigneeId, operator));
        return n != null && n > 0;
    }

    private RcCollectTodo require(Long id) {
        RcCollectTodo t = mapper.selectById(id);
        if (t == null) throw new BizException(ErrorCode.PARAM_INVALID, "催收待办不存在: " + id);
        return t;
    }
}