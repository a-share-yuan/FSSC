// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 BankAccount 服务（带 tenant_id，租户拦截器自动隔离） */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final MdBankAccountMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;
    /** 审计扩面（D 类）：银行账户变更记录 */
    private final AuditTrailService auditTrailService;

    /**
     * 银行账户是 company_id 级主数据，原实现只按关键字过滤 ⇒ 同租户内跨公司全量可见。
     * 现接 data_scope 五级办结；本表无部门／经办人列，更窄的级别按公司范围收（与采购退货列表同法）。
     */
    @DataScope("银行账户列表数据范围")
    public PageResult<MdBankAccount> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdBankAccount> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdBankAccount::getAccountName, keyword).or().like(MdBankAccount::getAccountCode, keyword));
        }
        DataScopeKit.apply(qw, MdBankAccount::getCompanyId, null, null);
        qw.orderByDesc(MdBankAccount::getId);
        Page<MdBankAccount> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 账户性质名称表（人民银行银行结算账户分类） */
    private static final Set<String> ACCOUNT_TYPES =
            Set.of("BASIC", "GENERAL", "SPECIAL", "TEMPORARY");

    /**
     * 账户性质校验——新增必填、取值受控，且「一司一基本户」在服务端成立。
     * 只统计启用中（status=NORMAL、未删除）的账户；历史 account_type 为 NULL 的账户不参与判重。
     */
    private String assertAccountType(MdBankAccount e, Long companyId, boolean require) {
        String t = e.getAccountType() == null ? "" : e.getAccountType().trim().toUpperCase();
        if (t.isEmpty()) {
            if (require) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "请选择账户性质（基本/一般/专用/临时存款账户）");
            }
            return null;
        }
        if (!ACCOUNT_TYPES.contains(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账户性质取值不合法: " + e.getAccountType()
                    + "（可选 " + String.join("/", ACCOUNT_TYPES) + "）");
        }
        e.setAccountType(t);
        if (!"BASIC".equals(t) || companyId == null) {
            return t;
        }
        long basic = mapper.selectCount(new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getCompanyId, companyId)
                .eq(MdBankAccount::getAccountType, "BASIC")
                .eq(MdBankAccount::getStatus, "NORMAL")
                .ne(e.getId() != null, MdBankAccount::getId, e.getId()));
        if (basic > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该公司已存在启用中的基本存款账户（一司一基本户），请改选一般/专用存款账户或先销户");
        }
        return t;
    }

    /**
     * 默认账户唯一——同一公司只允许一个 is_default=1，置默认时把其余账户降为非默认。
     */
    private void enforceSingleDefault(MdBankAccount e, Long companyId) {
        if (e.getIsDefault() == null || e.getIsDefault() != 1 || companyId == null) {
            return;
        }
        MdBankAccount demote = new MdBankAccount();
        demote.setIsDefault(0);
        mapper.update(demote, new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getCompanyId, companyId)
                .eq(MdBankAccount::getIsDefault, 1)
                .ne(e.getId() != null, MdBankAccount::getId, e.getId()));
    }

    /**
     * P0 资金路由：银行账号同租户唯一——Bean Validation 只能校验单字段形态，
     * 「同号重复登记」必须查库，放这里默认处理（与前后端格式校验构成唯一性校验）。
     */
    private void assertBankAccountUnique(MdBankAccount e) {
        if (!StringUtils.hasText(e.getBankAccount())) {
            return;
        }
        String acct = e.getBankAccount().trim();
        long dup = mapper.selectCount(new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getBankAccount, acct)
                .ne(e.getId() != null, MdBankAccount::getId, e.getId()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS,
                    "银行账号 [" + acct + "] 已存在，同一账号不能重复登记（如确属换户请先停用原账户）");
        }
    }

    public Long create(MdBankAccount e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        // TR-O1：companyId 缺省按当前登录人公司回填；NOT NULL 字段缺失给明确业务提示（不再写入数据库报 ERROR 1364）
        if (e.getCompanyId() == null) {
            LoginUser u = UserContext.get();
            if (u != null) {
                e.setCompanyId(u.getCompanyId());
            }
        }
        if (e.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属公司不能为空：当前登录人未配置公司归属，请显式传入 companyId");
        }
        if (!StringUtils.hasText(e.getAccountName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账户名称(accountName)不能为空");
        }
        if (!StringUtils.hasText(e.getBankName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开户银行(bankName)不能为空");
        }
        if (!StringUtils.hasText(e.getBankAccount())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行账号(bankAccount)不能为空");
        }
        assertBankAccountUnique(e);
        // 账户性质必填 + 一司一基本户 + 默认账户唯一
        assertAccountType(e, e.getCompanyId(), true);
        enforceSingleDefault(e, e.getCompanyId());
        // TR-O1 补充：account_code 同为 NOT NULL 无默认值列，缺省按编号规则自动生成（默认处理时间戳，风格同 createBatch）
        if (!StringUtils.hasText(e.getAccountCode())) {
            String code;
            try { code = numberRuleService.next("BANK_ACCOUNT"); }
            catch (Exception ex) {
                // 编号规则未配置/异常：默认处理用时间戳生成 account_code，保证 NOT NULL 列可写入数据库
                log.warn("银行账号编号规则生成失败，回退时间戳: {}", ex.getMessage());
                code = "BA" + System.currentTimeMillis();
            }
            e.setAccountCode(code);
        }
        mapper.insert(e);
        // T1：新增银行主数据即发起 TR_BANK_MASTER 银行主数据审批流（资金岗经办→财务经理复核）
        try {
            wfEngineFacade.start("TR_BANK_MASTER", e.getId(), userId(), "银行主数据-" + e.getAccountName());
        } catch (Exception ex) {
            log.error("银行主数据 {} 发起主数据审批失败: {}", e.getId(), ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return e.getId();
    }

    /**
     * 审计扩面（D 类·银行账户变更）：改前采集旧值，**仅当账号/开户行实际变化时**记录
     * （规则：before/after 只含本次实际变化字段；取不到旧实体则不写、不伪造）。
     */
    public void update(MdBankAccount e) {
        MdBankAccount old = e.getId() == null ? null : mapper.selectById(e.getId());
        // 改性质同样要过「一司一基本户」校验；公司归属取本次提交值，缺省沿用现有值
        Long companyId = e.getCompanyId() != null ? e.getCompanyId() : (old == null ? null : old.getCompanyId());
        assertAccountType(e, companyId, false);
        enforceSingleDefault(e, companyId);
        assertBankAccountUnique(e);
        mapper.updateById(e);
        if (old == null) {
            return;
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (e.getBankAccount() != null && !Objects.equals(old.getBankAccount(), e.getBankAccount())) {
            before.put("bankAccount", old.getBankAccount());
            after.put("bankAccount", e.getBankAccount());
        }
        if (e.getBankName() != null && !Objects.equals(old.getBankName(), e.getBankName())) {
            before.put("bankName", old.getBankName());
            after.put("bankName", e.getBankName());
        }
        if (before.isEmpty()) {
            return; // 本次未改银行账户字段，不写无意义审计
        }
        auditTrailService.recordUpdate("BANK_ACCOUNT", e.getId(),
                old.getAccountCode() == null ? old.getAccountName() : old.getAccountCode(), "md_bank_account",
                String.valueOf(e.getId()), before, after, "银行账户变更");
    }

    /**
     * 发起银行印鉴变更审批（TR01-02 → TR_SEAL_CHANGE，总裁终审）。
     * 与 create() 的「开户 → TR_BANK_MASTER（分管领导终审）」区分：依据源设计
     * 六.6 账户管理，开户走分管领导终审、印鉴变更走总裁终审，故拆分为两条流程。
     *
     * @param bankAccountId 银行账户主数据ID
     * @return 流程实例ID
     */
    public String startSealChange(Long bankAccountId) {
        MdBankAccount e = mapper.selectById(bankAccountId);
        if (e == null) {
            throw new IllegalArgumentException("银行账户不存在: id=" + bankAccountId);
        }
        try {
            return wfEngineFacade.start("TR_SEAL_CHANGE", bankAccountId, userId(),
                    "银行印鉴变更-" + e.getAccountName());
        } catch (Exception ex) {
            log.error("银行账户 {} 发起印鉴变更审批失败: {}", bankAccountId, ex.getMessage(), ex);
            throw new RuntimeException("发起印鉴变更审批失败", ex);
        }
    }

    public void enable(Long id, boolean enable) {
        // 重新启用也是"成为启用中账户"的入口——基本户判重必须一并覆盖，否则停用再启用即绕过校验
        if (enable) {
            MdBankAccount cur = id == null ? null : mapper.selectById(id);
            if (cur != null && "BASIC".equalsIgnoreCase(cur.getAccountType())) {
                MdBankAccount probe = new MdBankAccount();
                probe.setId(id);
                probe.setAccountType("BASIC");
                assertAccountType(probe, cur.getCompanyId(), false);
            }
        }
        MdBankAccount u = new MdBankAccount();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /** 当前登录用户归属公司（未登录或无公司归属返回 null，由调用方决定是否默认处理） */
    private Long companyId() {
        return UserContext.get() == null ? null : UserContext.get().getCompanyId();
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
