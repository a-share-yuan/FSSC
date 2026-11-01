// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.StrUtil;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.entity.MdSupplierBank;
import com.fssc.masterdata.mapper.MdSupplierBankMapper;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 往来单位候选聚合服务（之邻缺陷 修复）。
 *
 * <h3>缺陷背景</h3>
 * 对公结算（SETTLE）的「往来单位」下拉只读 {@code crm_customer}（客户档案），
 * 而供应商落在 {@code md_supplier}，两者<b>零同步</b>。现役库 {@code crm_customer} 近乎空表
 * ⇒ 下拉候选里一个供应商都没有 ⇒ 结算单挂不上 {@code customer_id}/{@code po_id}/{@code po_receipt_id}
 * ⇒ 三单匹配环节断裂，SETTLE 单据根本提交不了。
 *
 * <h3>为什么不能直接把 supplierId 当 customerId 用</h3>
 * {@code exp_bill.customer_id} 的列注释是「关联往来单位ID(crm_customer)」，
 * 下游（应收核销、坏账、对账、合同）<b>都按 crm_customer.id 解析</b>。
 * 两个表的 id 空间完全不同（例：弘毅贸易在 crm_customer 是 id=1，在 md_supplier 是 id=18），
 * 直接混用会把结算单静默挂到<b>另一个主体</b>上——比"选不出来"危险得多。
 *
 * <h3>本服务的做法：按需建档，产出语义正确的 customer_id</h3>
 * ① 已有 crm_customer 档案（按 customer_code 或名称对上）⇒ 直接用它的 id；
 * ② 没有 ⇒ 用供应商信息<b>建一份</b>档案（customer_code 沿用 supplier_code，
 *    customer_type=SUPPLIER），返回新建的 id。
 * 这样下拉里每一条供应商候选背后都有一个真实的 crm_customer.id，
 * 与既有下游解析规则一致。建档是<b>去重</b>的：重复调用不会产生重复档案。
 *
 * <h3>顺带修的第二个缺口：收款账号带不出</h3>
 * 原 {@code crm_customer} 表<b>根本没有银行账号字段</b>，而
 * {@code CorpPay#onCounterChange} 在取 {@code c.accountNo}/{@code c.bankName}，
 * 这两个值必然为 undefined ⇒ 收款账号必填项永远填不上 ⇒ 提交被拦。
 * 本服务从 {@code md_supplier_bank} 取默认收款账户一并带出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CounterpartyCandidateService {

    private final CrmCustomerMapper customerMapper;
    private final MdSupplierMapper supplierMapper;
    private final MdSupplierBankMapper supplierBankMapper;

    /** 供应商在 crm_customer 里的类型标识（与 CustomerService.create 的校验规则一致） */
    private static final String TYPE_SUPPLIER = "SUPPLIER";

    /** 建档后的初始状态：ACTIVE=已核准（供应商主数据本身已过审，无需再走客户建档审批流） */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 加载往来单位候选：客户档案 ∪ 供应商主数据（供应商按需建档为 crm_customer）。
     *
     * @param keyword 模糊搜索（编码/名称），可空
     * @param onlySupplier true=只要供应商类型；false=全部类型
     * @return 候选列表，字段与 CrmCustomer 对齐，并补 accountNo/bankName
     */
    public List<Map<String, Object>> listCandidates(String keyword, boolean onlySupplier, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("[往来单位候选] 无租户上下文，返回空");
            return out;
        }

        // ① 已有客户档案（del_flag 由 @TableLogic 自动过滤，不手写条件）
        LambdaQueryWrapper<CrmCustomer> cw = new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getTenantId, tenantId)
                .orderByAsc(CrmCustomer::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (onlySupplier) {
            cw.like(CrmCustomer::getCustomerType, TYPE_SUPPLIER);
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            cw.and(w -> w.like(CrmCustomer::getCustomerCode, kw)
                    .or().like(CrmCustomer::getCustomerName, kw));
        }
        List<CrmCustomer> customers = customerMapper.selectList(cw);
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (CrmCustomer c : customers) {
            Map<String, Object> m = toCandidate(c);
            out.add(m);
            if (StrUtil.isNotBlank(c.getCustomerCode())) {
                byCode.put(c.getCustomerCode().trim().toUpperCase(), m);
            }
        }

        // ② 供应商主数据：crm_customer 里没有的才建档
        LambdaQueryWrapper<MdSupplier> sw = new LambdaQueryWrapper<MdSupplier>()
                .eq(MdSupplier::getTenantId, tenantId)
                .eq(MdSupplier::getStatus, "NORMAL")
                .orderByAsc(MdSupplier::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            sw.and(w -> w.like(MdSupplier::getSupplierCode, kw)
                    .or().like(MdSupplier::getSupplierName, kw));
        }
        List<MdSupplier> suppliers = supplierMapper.selectList(sw);
        for (MdSupplier s : suppliers) {
            if (StrUtil.isBlank(s.getSupplierCode())) continue;
            String key = s.getSupplierCode().trim().toUpperCase();
            Map<String, Object> hit = byCode.get(key);
            if (hit != null) {
                // 已在档案里：只需把供应商侧信息补全（账号/银行），由末尾的批量补齐统一做
                hit.put("source", "BOTH");
                continue;
            }
            // 不在档案里 ⇒ 建档，产出语义正确的 customer_id
            Long newId = ensureCustomerArchive(s);
            if (newId == null) continue;
            CrmCustomer created = customerMapper.selectById(newId);
            if (created == null) continue;
            Map<String, Object> m = toCandidate(created);
            m.put("source", "SUPPLIER_AUTO");
            m.put("supplierId", s.getId());
            out.add(m);
        }
        // 供应商侧字段（supplierId/supplierName/blacklist/accountNo/bankName）最后统一批量补齐：
        // 原先逐行回查供应商 + 默认账户，300 行候选要打 300×2 = 600 条 SQL（实测 121+116 条、~74ms 纯 SQL）。
        enrichSupplierBatch(out);
        return out;
    }

    /**
     * 按 supplier_code 去重建档。
     *
     * @return 新建档案的 crm_customer.id；已存在时返回既有 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long ensureCustomerArchive(MdSupplier s) {
        if (s == null || StrUtil.isBlank(s.getSupplierCode())) return null;
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return null;

        Long exist = customerMapper.selectCount(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getTenantId, tenantId)
                .eq(CrmCustomer::getCustomerCode, s.getSupplierCode().trim()));
        if (exist != null && exist > 0) {
            CrmCustomer c = customerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                    .eq(CrmCustomer::getTenantId, tenantId)
                    .eq(CrmCustomer::getCustomerCode, s.getSupplierCode().trim())
                    .last("LIMIT 1"));
            return c != null ? c.getId() : null;
        }

        CrmCustomer c = new CrmCustomer();
        c.setTenantId(tenantId);
        c.setCustomerCode(s.getSupplierCode().trim());
        c.setCustomerName(StrUtil.isBlank(s.getSupplierName()) ? s.getSupplierCode() : s.getSupplierName());
        c.setCustomerType(TYPE_SUPPLIER);
        // 不再逐列拷 contact/phone/address：全仓普查（10-23 16:3x）＝这三列除本服务的自动建档写入位置与 CustomerService 的关键字 LIKE 外零读点，纯影子数据。
        // 供应商侧的联系方式改由 CustomerService.page 出参时按编码回查，界面「带出联系人」规则不变。
        // 关键字侧的兜底＝CustomerService.SUPPLIER_CONTACT_EXISTS 联查供应商三列，影子行清空后按 联系人/电话/地址 仍搜得到。
        c.setStatus(STATUS_ACTIVE);
        c.setRemark("由供应商主数据自动建档（联系方式取自供应商档案）");
        customerMapper.insert(c);
        log.info("[往来单位候选] 供应商 {} 自动建档为往来单位 customer_id={}",
                c.getCustomerCode(), c.getId());
        return c.getId();
    }

    /** CrmCustomer → 候选 Map（字段名与既有前端用法对齐：id/customerName/customerType）；联系人在此留空，由 {@code CustomerService#page} 的回查规则统一（不要两处各拷一份）。 */
    private Map<String, Object> toCandidate(CrmCustomer c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("customerCode", c.getCustomerCode());
        m.put("customerName", c.getCustomerName());
        m.put("customerType", c.getCustomerType());
        m.put("creditLevel", c.getCreditLevel());
        m.put("status", c.getStatus());
        m.put("source", "CRM_CUSTOMER");
        return m;
    }

    /**
     * 用 md_supplier_bank 的默认收款账户批量补齐 supplierId/supplierName/blacklist/accountNo/bankName。
     *
     * <p>候选的 id 是 crm_customer.id，需先按 customer_code 对上 supplier 再取账户；
     * 批量化后每次调用固定两条 SQL（供应商一条、账户一条），不再随候选行数线性增长。</p>
     */
    private void enrichSupplierBatch(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;

        // customer_code → supplier：一次 IN 查完；大小写不敏感（档案侧编码与供应商侧别名可能大小写不一）
        Set<String> codes = new LinkedHashSet<>();
        for (Map<String, Object> m : rows) {
            if (m.get("supplierId") != null) continue;   // 供应商侧自动建档的行已带着 supplierId
            Object code = m.get("customerCode");
            if (code == null || StrUtil.isBlank(String.valueOf(code))) continue;
            codes.add(String.valueOf(code).trim());
        }
        Map<String, MdSupplier> byCode = new HashMap<>();
        if (!codes.isEmpty()) {
            for (MdSupplier s : supplierMapper.selectList(new LambdaQueryWrapper<MdSupplier>()
                    .eq(MdSupplier::getTenantId, tenantId)
                    .in(MdSupplier::getSupplierCode, codes))) {
                if (s.getSupplierCode() != null) {
                    byCode.putIfAbsent(s.getSupplierCode().trim().toUpperCase(), s);
                }
            }
        }
        Map<Long, MdSupplierBank> bankOf = new HashMap<>();
        List<Long> needBank = new ArrayList<>();
        for (MdSupplier s : byCode.values()) {
            needBank.add(s.getId());
        }
        // 自动建档行也要银行账户 ⇒ 把已带 supplierId 的行一并纳入
        for (Map<String, Object> m : rows) {
            Object sid = m.get("supplierId");
            if (sid instanceof Number n) needBank.add(n.longValue());
        }
        if (!needBank.isEmpty()) {
            // 默认账户优先：同一供应商取 ORDER BY is_default DESC, id ASC 的首条
            for (MdSupplierBank b : supplierBankMapper.selectList(new LambdaQueryWrapper<MdSupplierBank>()
                    .eq(MdSupplierBank::getTenantId, tenantId)
                    .in(MdSupplierBank::getSupplierId, needBank)
                    .orderByDesc(MdSupplierBank::getIsDefault)
                    .orderByAsc(MdSupplierBank::getId))) {
                bankOf.putIfAbsent(b.getSupplierId(), b);
            }
        }
        for (Map<String, Object> m : rows) {
            Object code = m.get("customerCode");
            MdSupplier s = null;
            if (m.get("supplierId") == null && code != null && StrUtil.isNotBlank(String.valueOf(code))) {
                s = byCode.get(String.valueOf(code).trim().toUpperCase());
            }
            if (s != null) {
                // crm_customer 无 supplierId 字段，这里补挂来源供应商 id
                m.put("supplierId", s.getId());
                m.put("supplierName", s.getSupplierName());
                m.put("blacklist", s.getBlacklist());
            }
            Object sid = m.get("supplierId");
            if (!(sid instanceof Number)) continue;
            MdSupplierBank bank = bankOf.get(((Number) sid).longValue());
            if (bank != null) {
                m.put("accountNo", bank.getBankAccount());
                m.put("bankName", StrUtil.isBlank(bank.getBankBranch()) ? bank.getBankName() : bank.getBankBranch());
            }
        }
    }
}
