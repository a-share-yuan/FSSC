// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.service;
import com.fssc.framework.security.UserContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.common.util.StrUtil;
import com.fssc.contract.entity.ContContract;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 合同服务：CRUD + 审批状态机 + 台账深化（中集财务共享需求说明书-合同台账 v1.0）。
 * <ul>
 *   <li>固定金额合同审批通过 → 按费用类型映射的预算科目冻结（占用）合同总额</li>
 *   <li>手工关闭 / 变更完成 → 释放冻结预算</li>
 *   <li>无固定金额（长期协议/以量定额）合同不控制预算</li>
 * </ul>
 *
 * <p>D-DEC-329（X-09，乙）：合同审批存在两条链并存——本类的<b>手工直批腿</b>（approve/reject，落 EFFECTIVE/DRAFT）
 * 与 Flowable 的<b>流程腿</b>（submit 起流程，收尾在 ContractBizAction，落 APPROVED/REJECTED）。裁定<b>不归一</b>，
 * 改为各补状态互斥闸：直批腿执行前必须"已提交且不在流程在途"（{@link #assertNotInFlow}，服务端可阻断）；
 * 反向那一半（流程收尾不得覆写已被直批处理的单）落在 workflow/action/{@code ContractBizAction}，本波禁改，已回主窗。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContractService {

    private final ContContractMapper mapper;
    private final BudgetService budgetService;
    private final com.fssc.sharing.service.VoucherService voucherService;
    private final com.fssc.workflow.service.WfEngineFacade wfEngineFacade;
    /** 组织合法性守卫（V1.3.20 安全审计①）：读路径公司隔离 + 写路径公司归属校验 */
    private final OrgGuard orgGuard;
    /** 编号规则取号（合同号等主数据编号的唯一正经来源） */
    private final NumberRuleService numberRuleService;
    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** 合同审批流业务类型：提交起流程与直批腿的在途互斥判据共用同一字面量（D-DEC-329） */
    private static final String CONTRACT_BIZ_TYPE = "CONTRACT_APPROVE";

    @DataScope("合同列表数据范围")
    public PageResult<ContContract> page(long pn, long ps, String kw) {
        LambdaQueryWrapper<ContContract> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(ContContract::getContractNo, kw)
                    .or().like(ContContract::getContractName, kw)
                    .or().like(ContContract::getPartyBName, kw));
        }
        // ③ data_scope 五级：依据登录人 data_scope 注入 公司/部门/本人 过滤（TENANT 不加条件）
        DataScopeKit.apply(qw, ContContract::getCompanyId, null, null);
        qw.orderByDesc(ContContract::getId);
        Page<ContContract> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    /** 写路径公司归属校验（①）：建单时单据公司必须等于登录人公司 */
    /** 写路径公司归属校验（①）：建单时单据公司必须等于登录人公司 */
    public Long create(ContContract e) {
        // 必填收口（Q1 口径：合同号系统取号、人工只读；其余为业务必输项）
        AssertUtil.notBlank(e.getContractName(), ErrorCode.PARAM_INVALID, "合同名称必填");
        AssertUtil.notBlank(e.getContractType(), ErrorCode.PARAM_INVALID, "合同类型必填");
        AssertUtil.notBlank(e.getPartyBName(), ErrorCode.PARAM_INVALID, "乙方（客商名称）必填");
        AssertUtil.notNull(e.getItemId(), ErrorCode.PARAM_INVALID, "费用类型必填（合同占用预算的科目由它决定）");
        if (e.getAmount() == null && !Integer.valueOf(1).equals(e.getNoFixedAmount())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合同总额必填（勾选「无固定金额」时可留空）");
        }
        // D-CON-1：前端「签订公司」下拉常只回传名称而 companyId 缺省 → cont_contract.company_id=NULL，
        // 列表按 DataScope 过滤公司，新建合同随即"隐身"。写守卫本就要求单据公司==登录公司，
        // 故缺省按当前登录人公司回填，杜绝 NULL 归属。
        if (e.getCompanyId() == null) {
            e.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(e.getCompanyId());
        // 合同号＝编号规则取号（HT+yyyyMMdd+流水），杜绝无号合同流入审批/台账
        if (StrUtil.isBlank(e.getContractNo())) {
            e.setContractNo(numberRuleService.next("CONT_CONTRACT"));
        } else {
            assertNoUnique(e.getContractNo(), null);
        }
        e.setId(null);
        if (e.getStatus() == null) e.setStatus("DRAFT");
        if (e.getSource() == null) e.setSource("MANUAL");
        e.setTenantId(TenantContext.getTenantId());
        mapper.insert(e);
        return e.getId();
    }

    public void update(ContContract e) {
        AssertUtil.notBlank(e.getContractName(), ErrorCode.PARAM_INVALID, "合同名称必填");
        if (StrUtil.isNotBlank(e.getContractNo())) {
            assertNoUnique(e.getContractNo(), e.getId());
        }
        mapper.updateById(e);
    }

    /** 合同号查重（前端可绕过，故服务端必须自己判一次唯一） */
    private void assertNoUnique(String no, Long selfId) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<ContContract>()
                .eq(ContContract::getContractNo, no)
                .ne(selfId != null, ContContract::getId, selfId));
        AssertUtil.isTrue(n == null || n == 0, ErrorCode.DATA_ALREADY_EXISTS, "合同号已存在: " + no);
    }

    /**
     * D-DEC-329（X-09，乙）：废弃/终止类动作走配置状态，不留按钮旁路。
     * 改前此处直接 deleteById ⇒ 审批中的单可被删掉（流程任务变悬空）、已生效/已审批的单可绕过
     * 「手工关闭」把已冻结的预算与已生成的凭证留在原地。现只允许未生效的草稿/已驳回作废。
     */
    public void delete(Long id) {
        ContContract c = require(id);
        String s = c.getStatus();
        if ("APPROVING".equals(s)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合同审批中，请先在流程中驳回（或本页驳回）后再作废");
        }
        if ("APPROVED".equals(s) || "EFFECTIVE".equals(s)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "合同已审批通过/已生效，请先「手工关闭」（释放预算冻结）后再作废");
        }
        mapper.deleteById(id);
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    public void submit(Long id) {
        ContContract c = require(id);
        if (!"DRAFT".equals(c.getStatus())) throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可提交");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        setStatus(id, "APPROVING");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "APPROVING");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "提交审批");
        // CL 合同审批纳入 Flowable 流程引擎统一管理
        // 批次3 R3-D：传入合同金额，使流程实例金额与单据一致（CONTRACT 流程当前无金额网关 ⇒ 只影响展示/统计，不改路由）
        String title = "合同审批-" + (c.getContractNo() != null ? c.getContractNo() : String.valueOf(id));
        wfEngineFacade.start(CONTRACT_BIZ_TYPE, id, userId(), title, c.getAmount());
    }

    public void approve(Long id) {
        ContContract c = require(id);
        if (!"APPROVING".equals(c.getStatus())) throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中可审批通过");
        assertNotInFlow(id, "审批通过");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        setStatus(id, "EFFECTIVE");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "EFFECTIVE");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "审批通过");
        // 固定金额合同 → 按费用类型映射的预算科目冻结合同总额（台账需求：固定金额合同通过合同总额冻结预算）
        freezeBudget(c);
        // P1 修复：合同为直批模式（无流程），ContractBizAction(CONTRACT_APPROVE) 经事件总线永远不触发，
        // 生效即在此生成合同凭证（激活 generateByContract 零调用方）；失败不影响生效
        try {
            Long vid = voucherService.generateByContract(id);
            log.info("合同 {} 审批通过自动生成合同凭证: {}", c.getContractNo(), vid);
        } catch (Exception ex) {
            log.error("合同 {} 合同凭证生成失败（已生效，可后续补生成）: {}", c.getContractNo(), ex.getMessage());
        }
    }

    public void reject(Long id) {
        ContContract c = require(id);
        if (!"APPROVING".equals(c.getStatus())) throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中可驳回");
        assertNotInFlow(id, "驳回");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        setStatus(id, "DRAFT");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "DRAFT");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "审批驳回");
    }

    /**
     * D-CON-01：驳回合同编辑重提 REJECTED → DRAFT（对齐 exp_bill returnToEdit 的乙方案——
     * REJECTED 保留作历史，重提走显式端点回草稿后修改，再经 submit 起【新】流程实例）。
     */
    public void returnToEdit(Long id) {
        ContContract c = require(id);
        if (!"REJECTED".equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已驳回合同可编辑重提，当前状态: " + c.getStatus());
        }
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        setStatus(id, "DRAFT");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "DRAFT");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "驳回后编辑重提");
    }

    /** 手工关闭合同（台账需求：合同可以手工关闭，已关闭合同不能被引用） */
    public void close(Long id) {
        ContContract c = require(id);
        if (!"EFFECTIVE".equals(c.getStatus())) throw new BizException(ErrorCode.PARAM_INVALID, "仅生效合同可关闭");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        releaseBudget(c);
        setStatus(id, "CLOSED");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "CLOSED");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "手工关闭");
    }

    /** 发起变更：复制原合同为新的 DRAFT 变更版本（parentId 关联），原合同关闭并释放预算 */
    public Long change(Long id) {
        ContContract c = require(id);
        if (!"EFFECTIVE".equals(c.getStatus())) throw new BizException(ErrorCode.PARAM_INVALID, "仅生效合同可发起变更");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        releaseBudget(c);
        setStatus(id, "CLOSED");
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "CLOSED");
        auditTrailService.recordUpdate("CONTRACT", id, c.getContractNo(), "cont_contract",
                String.valueOf(id), auditBefore, auditAfter, "合同变更（原合同关闭）");
        ContContract copy = copyOf(c);
        copy.setContractNo((c.getContractNo() == null ? "HT" : c.getContractNo()) + "-V" + (c.getParentId() == null ? 2 : 3));
        copy.setStatus("DRAFT");
        copy.setRemark("合同变更（原合同 " + c.getContractNo() + "）");
        mapper.insert(copy);
        return copy.getId();
    }

    /** 补充协议：基于原合同新建 DRAFT 补充协议（parentId 关联，原合同与补充协议可分别执行） */
    public Long supplement(Long parentId) {
        ContContract p = require(parentId);
        ContContract c = copyOf(p);
        c.setContractNo(p.getContractNo() + "-S");
        c.setContractName((p.getContractName() == null ? "" : p.getContractName()) + "（补充协议）");
        c.setParentId(parentId);
        c.setStatus("DRAFT");
        mapper.insert(c);
        return c.getId();
    }

    // ---------------- 预算冻结 / 释放 ----------------

    /** 预算行年度就近匹配用；冻结与释放取同一入参，保证占用/释放落在同一预算行 */
    private static Integer budgetYearOf(ContContract c) {
        return c.getSignDate() != null ? c.getSignDate().getYear()
                : (c.getBeginDate() != null ? c.getBeginDate().getYear() : null);
    }

    private void freezeBudget(ContContract c) {
        if (c.getNoFixedAmount() != null && c.getNoFixedAmount() == 1) {
            return; // 无固定金额合同不控制预算
        }
        BigDecimal amt = c.getAmount();
        if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) return;
        Long budgetItemId = budgetService.resolveOccupyItem(null, c.getItemId(), c.getCompanyId(), c.getDeptId(),
                budgetYearOf(c), false);
        if (budgetItemId == null) {
            log.warn("[预算冻结] 合同 {} 未配置费用类型({})→预算明细映射，跳过冻结", c.getContractNo(), c.getItemId());
            return;
        }
        try {
            budgetService.occupy(budgetItemId, amt, "EXP_CONTRACT", c.getId(), c.getContractNo());
        } catch (Exception e) {
            // 预算冻结属辅助控制：映射缺失/额度不足/脏数据均不得阻断合同审批，仅告警留痕
            log.warn("[预算冻结] 合同 {} 冻结预算明细 {} 失败（不阻断审批）: {}",
                    c.getContractNo(), budgetItemId, e.getMessage());
        }
    }

    private void releaseBudget(ContContract c) {
        if (c.getNoFixedAmount() != null && c.getNoFixedAmount() == 1) {
            return;
        }
        BigDecimal amt = c.getAmount();
        if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) return;
        Long budgetItemId = budgetService.resolveOccupyItem(null, c.getItemId(), c.getCompanyId(), c.getDeptId(),
                budgetYearOf(c), false);
        if (budgetItemId == null) {
            log.warn("[预算释放] 合同 {} 未配置费用类型({})→预算明细映射，跳过释放", c.getContractNo(), c.getItemId());
            return;
        }
        try {
            budgetService.release(budgetItemId, amt, "EXP_CONTRACT", c.getId(), c.getContractNo());
        } catch (Exception e) {
            // 释放失败同理不阻断关单/变更，仅告警（否则已生效合同无法关闭）
            log.warn("[预算释放] 合同 {} 释放预算明细 {} 失败（不阻断关闭）: {}",
                    c.getContractNo(), budgetItemId, e.getMessage());
        }
    }

    private ContContract copyOf(ContContract src) {
        ContContract c = new ContContract();
        c.setTenantId(src.getTenantId());
        c.setContractType(src.getContractType());
        c.setPartyAName(src.getPartyAName());
        c.setPartyBName(src.getPartyBName());
        c.setAmount(src.getAmount());
        c.setCurrencyCode(src.getCurrencyCode());
        c.setSignDate(src.getSignDate());
        c.setBeginDate(src.getBeginDate());
        c.setEndDate(src.getEndDate());
        c.setCustomerId(src.getCustomerId());
        c.setCompanyId(src.getCompanyId());
        c.setDeptId(src.getDeptId());
        c.setItemId(src.getItemId());
        c.setItemName(src.getItemName());
        c.setNoFixedAmount(src.getNoFixedAmount());
        c.setContractClass(src.getContractClass());
        c.setPriceDecrease(src.getPriceDecrease());
        c.setBidCompare(src.getBidCompare());
        c.setBidProcess(src.getBidProcess());
        c.setBidReason(src.getBidReason());
        c.setBidNote(src.getBidNote());
        c.setSource(src.getSource());
        return c;
    }

    /**
     * D-DEC-329（X-09，乙）：两条审批链并存<b>不归一</b>，改为各补一道服务端状态互斥闸。
     * 本方法是<b>手工直批腿</b>的闸：单据除"已提交（APPROVING）"外还必须<b>不在审批流在途</b>才可执行——
     * 有在办流程任务说明这张单正由待办人签核，直批会把流程结论架空（同一张单两个结论、凭证重复生成）。
     * 可阻断的前置校验，不是文案提示；引擎查询异常时按"在途"处置（fail-closed，拿不准就不动）。
     */
    private void assertNotInFlow(Long id, String action) {
        boolean inFlow;
        try {
            inFlow = wfEngineFacade.hasActiveTask(CONTRACT_BIZ_TYPE, id);
        } catch (Exception ex) {
            log.warn("合同 {} 审批流在途查询失败，按在途处置（fail-closed）: {}", id, ex.getMessage());
            inFlow = true;
        }
        if (inFlow) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该合同有审批流任务在途，请从「待办中心」办理，不能在本页直接" + action);
        }
    }

    private ContContract require(Long id) {
        ContContract c = mapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.PARAM_INVALID, "合同不存在: " + id);
        orgGuard.assertVisible(c.getCompanyId(), null); // ① 写路径公司归属校验（update/approve/close/... 全经此）
        return c;
    }

    private void setStatus(Long id, String s) {
        ContContract u = new ContContract();
        u.setId(id);
        u.setStatus(s);
        mapper.updateById(u);
    }
}
