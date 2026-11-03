// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.common.util.StrUtil;
import com.fssc.framework.security.UserContext;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.entity.MdSupplierBank;
import com.fssc.masterdata.entity.MdSupplierContact;
import com.fssc.masterdata.entity.MdSupplierQualification;
import com.fssc.masterdata.mapper.MdSupplierBankMapper;
import com.fssc.masterdata.mapper.MdSupplierContactMapper;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.masterdata.mapper.MdSupplierQualificationMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 主数据 Supplier 服务（带 tenant_id，租户拦截器自动隔离）。
 * 供应商新增走准入审批（PTP01 SUPPLIER_ADD），创建后自动发起流程。
 * 41号审查 专属页子表 —— 银行账户(N)/联系人(N)/资质证照(N)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final MdSupplierMapper mapper;
    private final MdSupplierBankMapper bankMapper;
    private final MdSupplierContactMapper contactMapper;
    private final MdSupplierQualificationMapper qualMapper;
    private final WfEngineFacade wfEngineFacade;
    private final AuditTrailService auditTrailService;

    public PageResult<MdSupplier> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdSupplier> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdSupplier::getSupplierName, keyword).or().like(MdSupplier::getSupplierCode, keyword));
        }
        qw.orderByDesc(MdSupplier::getId);
        Page<MdSupplier> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdSupplier e) {
        // 必填办结（与前端红星同一规则）：供应商编码＝人工录入的唯一编码，名称必输
        AssertUtil.notBlank(e.getSupplierCode(), ErrorCode.PARAM_INVALID, "供应商编码必填");
        AssertUtil.notBlank(e.getSupplierName(), ErrorCode.PARAM_INVALID, "供应商名称必填");
        assertCodeUnique(e.getSupplierCode(), null);
        assertTaxIdentityUnique(e, null);
        e.setId(null);
        // 主数据零审批、状态位核准：
        // 建档落「未核准(PENDING)」状态位，不再发起 MD_MASTER 审批流；由持核准权限的岗位
        // 经 POST /{id}/approve 把状态位翻到 ACTIVE（页面「核准」按钮）。
        if (e.getStatus() == null || "NORMAL".equals(e.getStatus())) {
            e.setStatus("PENDING");
        }
        mapper.insert(e);
        // 审批流已停用（MD_MASTER 已删），建档即 PENDING 状态位，核准走 approve() 翻转
        log.info("供应商 {} 建档完成（PENDING，待核准）", e.getSupplierCode());
        return e.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    public void update(MdSupplier e) {
        AssertUtil.notBlank(e.getSupplierName(), ErrorCode.PARAM_INVALID, "供应商名称必填");
        if (StrUtil.isNotBlank(e.getSupplierCode())) {
            assertCodeUnique(e.getSupplierCode(), e.getId());
        }
        assertTaxIdentityUnique(e, e.getId());
        mapper.updateById(e);
    }

    /** 编码唯一（不含自身）：杜绝同编码双档案导致的付款/对账串档 */
    private void assertCodeUnique(String code, Long selfId) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<MdSupplier>()
                .eq(MdSupplier::getSupplierCode, code)
                .ne(selfId != null, MdSupplier::getId, selfId));
        AssertUtil.isTrue(n == null || n == 0, ErrorCode.PARAM_INVALID, "供应商编码已存在: " + code);
    }

    /**
     * 税号/统一社会信用代码唯一（不含自身）——同一法人重复建档会导致
     * 付款对象重复、对账串档、发票查验重复预警。选填字段只在非空时查重。
     */
    private void assertTaxIdentityUnique(MdSupplier e, Long selfId) {
        assertOneFieldUnique(e.getTaxNo(), MdSupplier::getTaxNo, selfId, "税号");
        assertOneFieldUnique(e.getCreditCode(), MdSupplier::getCreditCode, selfId, "统一社会信用代码");
    }

    private void assertOneFieldUnique(String value, SFunction<MdSupplier, ?> col, Long selfId, String label) {
        if (!StringUtils.hasText(value)) return; // 选填：留空不查重
        Long n = mapper.selectCount(new LambdaQueryWrapper<MdSupplier>()
                .eq(col, value.trim())
                .ne(selfId != null, MdSupplier::getId, selfId));
        AssertUtil.isTrue(n == null || n == 0, ErrorCode.PARAM_INVALID, label + "已被其他供应商档案占用: " + value.trim());
    }

    public void enable(Long id, boolean enable) {
        MdSupplier u = new MdSupplier();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    /** 删除供应商（逻辑删） */
    public void delete(Long id) {
        mapper.hardDelete(id);
    }

    // 41号审查 专属页子表(银行账户/联系人/资质证照)

    public List<MdSupplierBank> banks(Long supplierId) {
        require(supplierId);
        return bankMapper.selectList(new LambdaQueryWrapper<MdSupplierBank>()
                .eq(MdSupplierBank::getSupplierId, supplierId)
                .orderByDesc(MdSupplierBank::getIsDefault).orderByAsc(MdSupplierBank::getId));
    }

    public Long addBank(MdSupplierBank b) {
        require(b.getSupplierId());
        if (!StringUtils.hasText(b.getBankName()) || !StringUtils.hasText(b.getBankAccount())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开户行与银行账号必填");
        }
        if (b.getStatus() == null) {
            b.setStatus("NORMAL");
        }
        if (b.getIsDefault() == null) {
            b.setIsDefault(0);
        }
        if (b.getIsDefault() == 1) {
            // 默认账户唯一：先清掉其它默认标记
            for (MdSupplierBank o : banks(b.getSupplierId())) {
                if (o.getIsDefault() == 1) {
                    MdSupplierBank u = new MdSupplierBank();
                    u.setId(o.getId());
                    u.setIsDefault(0);
                    bankMapper.updateById(u);
                }
            }
        }
        bankMapper.insert(b);
        return b.getId();
    }

    public void deleteBank(Long id) {
        bankMapper.deleteById(id);
    }

    public List<MdSupplierContact> contacts(Long supplierId) {
        require(supplierId);
        return contactMapper.selectList(new LambdaQueryWrapper<MdSupplierContact>()
                .eq(MdSupplierContact::getSupplierId, supplierId)
                .orderByDesc(MdSupplierContact::getIsPrimary).orderByAsc(MdSupplierContact::getId));
    }

    public Long addContact(MdSupplierContact c) {
        require(c.getSupplierId());
        if (!StringUtils.hasText(c.getContactName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "联系人姓名必填");
        }
        if (c.getIsPrimary() == null) {
            c.setIsPrimary(0);
        }
        contactMapper.insert(c);
        return c.getId();
    }

    public void deleteContact(Long id) {
        contactMapper.deleteById(id);
    }

    public List<MdSupplierQualification> quals(Long supplierId) {
        require(supplierId);
        return qualMapper.selectList(new LambdaQueryWrapper<MdSupplierQualification>()
                .eq(MdSupplierQualification::getSupplierId, supplierId)
                .orderByAsc(MdSupplierQualification::getValidTo).orderByAsc(MdSupplierQualification::getId));
    }

    public Long addQual(MdSupplierQualification q) {
        require(q.getSupplierId());
        if (!StringUtils.hasText(q.getQualName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "证照名称必填");
        }
        qualMapper.insert(q);
        return q.getId();
    }

    public void deleteQual(Long id) {
        qualMapper.deleteById(id);
    }

    private void require(Long supplierId) {
        if (supplierId == null || mapper.selectById(supplierId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商不存在 id=" + supplierId);
        }
    }
    /**
     * 状态位核准（未核准→核准）：PENDING→ACTIVE。
     * 权限 md:supplier:approve；去重：已是 ACTIVE 跳过。审计记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        MdSupplier sup = mapper.selectById(id);
        if (sup == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商不存在: " + id);
        }
        if ("ACTIVE".equals(sup.getStatus())) {
            return; // 去重
        }
        String old = sup.getStatus();
        MdSupplier upd = new MdSupplier();
        upd.setId(id);
        upd.setStatus("ACTIVE");
        mapper.updateById(upd);
        if (auditTrailService != null) {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", old);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", "ACTIVE");
            auditTrailService.recordUpdate("SUPPLIER_ADD", id, sup.getSupplierCode(), "md_supplier",
                    String.valueOf(id), before, after, "状态位核准（页面直接核准）");
        }
    }

}
