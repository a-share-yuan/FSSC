// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.returns.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.entity.PoOrderLine;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.mapper.PoOrderLineMapper;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.purchase.mapper.PoReceiptMapper;
import com.fssc.returns.entity.PurReturn;
import com.fssc.returns.entity.PurReturnLine;
import com.fssc.returns.mapper.PurReturnLineMapper;
import com.fssc.returns.mapper.PurReturnMapper;
import com.fssc.returns.support.ReturnLegMapping;
import com.fssc.sharing.entity.SupplierRecon;
import com.fssc.sharing.entity.SupplierReconDetail;
import com.fssc.sharing.entity.SupplierReconDiff;
import com.fssc.sharing.mapper.SupplierReconDetailMapper;
import com.fssc.sharing.mapper.SupplierReconDiffMapper;
import com.fssc.sharing.mapper.SupplierReconMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.NumberRuleService;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 采购退货单服务（AP 退货退款冲减应付）。
 * 状态机：DRAFT → PENDING → APPROVED → VOIDED；REJECTED 可改后重新提交。
 * 金额表内存正数；退货方式 REFUND 进对账、EXCHANGE 换货补货不进对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurReturnService {

    private static final String RULE_CODE = "PUR_RETURN";
    private static final BigDecimal TOL = new BigDecimal("0.01");

    private final PurReturnMapper mapper;
    private final PurReturnLineMapper lineMapper;
    private final NumberRuleService numberRuleService;
    private final PoReceiptMapper poReceiptMapper;
    /** 租户参数（sys_config）：审核/驳回/作废读 approve.deny_self 自审禁止开关，与 Flowable 路径共用同一开关 */
    private final SysConfigMapper sysConfigMapper;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 下游环节：REFUND 退货退款 → 供应商对账域（负向明细行 + balance 重算），EXCHANGE 不写 */
    private final SupplierReconMapper supplierReconMapper;
    private final SupplierReconDetailMapper supplierReconDetailMapper;
    private final SupplierReconDiffMapper supplierReconDiffMapper;
    /** 供应商解析：vendor_code → md_supplier（对账单以 supplier_id 挂账） */
    private final MdSupplierMapper mdSupplierMapper;
    /** company_code/company_name 按 company_id 补充（原两列恒 NULL） */
    private final SysCompanyMapper sysCompanyMapper;
    /** 明细 uom/source_line_id 回填，来源＝采购订单行（源单是收货单时经 po_receipt.po_id 定位） */
    private final PoOrderLineMapper poOrderLineMapper;
    private final PoOrderMapper poOrderMapper;
    /** 历史行：读时把 approver 工号换成真名 */
    private final SysUserMapper sysUserMapper;

    // 创建

    @Transactional(rollbackFor = Exception.class)
    public PurReturn create(PurReturn r) {
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货金额必须大于0");
        }
        if (r.getVendorCode() == null || r.getVendorCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商代码必填");
        }
        if (r.getBillDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货日期必填");
        }
        if (r.getReturnMode() == null || r.getReturnMode().isBlank()) {
            r.setReturnMode(PurReturn.MODE_REFUND);
        }
        assertVendorInMaster(r);
        fillCompanyCoords(r);
        fillSourceDocId(r);
        List<PurReturnLine> lines = normalizeLines(r);
        assertLineRule(lines);
        validateLineSum(r.getAmount(), lines);
        // 表头税额按明细派生回写（不信任前端传值，含税规则）
        r.setTaxAmount(deriveTaxAmount(lines));

        r.setId(null);
        r.setBillNo(numberRuleService.next(RULE_CODE));
        r.setStatus(PurReturn.ST_DRAFT);
        r.setLineCount(lines.size());
        mapper.insert(r);

        saveLines(r.getId(), lines);
        r.setLines(lines);
        log.info("[returns] 采购退货单 {} 创建", r.getBillNo());
        return r;
    }

    /** 修改（仅草稿/驳回态） */
    @Transactional(rollbackFor = Exception.class)
    public PurReturn update(PurReturn r) {
        PurReturn old = require(r.getId());
        if (!List.of(PurReturn.ST_DRAFT, PurReturn.ST_REJECTED).contains(old.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿/驳回态可修改：" + old.getBillNo());
        }
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货金额必须大于0");
        }
        // 编辑是整单覆盖（下面直接 set 到 old），不校验就会把 NOT NULL 的 vendor_code/bill_date 置空，
        // 变成 500 而不是友好提示——与 create 同一规则补齐。
        if (r.getVendorCode() == null || r.getVendorCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商代码必填");
        }
        if (r.getBillDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货日期必填");
        }
        assertVendorInMaster(r);   // （与 create 同校验：编辑改代码同样要现有）
        fillCompanyCoords(r);
        fillSourceDocId(r);
        List<PurReturnLine> lines = normalizeLines(r);
        assertLineRule(lines);
        validateLineSum(r.getAmount(), lines);
        // 表头税额按明细派生回写（不信任前端传值，含税规则）
        r.setTaxAmount(deriveTaxAmount(lines));

        old.setCompanyId(r.getCompanyId());
        old.setCompanyCode(r.getCompanyCode());
        old.setCompanyName(r.getCompanyName());
        old.setVendorCode(r.getVendorCode());
        old.setVendorName(r.getVendorName());
        old.setCcy(r.getCcy());
        old.setBillDate(r.getBillDate());
        old.setAmount(r.getAmount());
        old.setTaxAmount(deriveTaxAmount(lines));
        old.setReturnMode(r.getReturnMode() == null || r.getReturnMode().isBlank() ? PurReturn.MODE_REFUND : r.getReturnMode());
        old.setSourceDocType(r.getSourceDocType());
        old.setSourceDocId(r.getSourceDocId());
        old.setSourceBillNo(r.getSourceBillNo());
        old.setReturnReason(r.getReturnReason());
        old.setRemark(r.getRemark());
        old.setLineCount(lines.size());
        mapper.updateById(old);

        // 重写明细行（先逻辑删旧行，再插新行）
        lineMapper.delete(new LambdaQueryWrapper<PurReturnLine>().eq(PurReturnLine::getReturnId, old.getId()));
        saveLines(old.getId(), lines);
        old.setLines(lines);
        return old;
    }

    // 状态机

    @Transactional(rollbackFor = Exception.class)
    public PurReturn submit(Long id) {
        PurReturn r = require(id);
        if (!List.of(PurReturn.ST_DRAFT, PurReturn.ST_REJECTED).contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可提交");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        r.setStatus(PurReturn.ST_PENDING);
        mapper.updateById(r);
        auditTrailService.recordUpdate("PUR_RETURN", id, r.getBillNo(), "pur_return",
                String.valueOf(id),
                auditBefore, Collections.singletonMap("status", PurReturn.ST_PENDING), "提交");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurReturn approve(Long id) {
        PurReturn r = require(id);
        if (!PurReturn.ST_PENDING.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 仅待审核态可审核");
        }
        // 第二道防线：单据级「审批人 ≠ 制单人」（与角色隔离互补，齐平 Flowable 的 approve.deny_self）
        assertNotSelf(r, "审核");
        // 有源单（采购入库）时软校验：累计已生效退货金额 + 本次 ≤ 源单金额
        // 界面建单只带源单号，source_doc_id 曾恒 NULL ⇒ 本校验对界面单永不生效。
        // 这里对历史行再补一次关联（随下方 updateById 写入数据库），新单在 create/update 已写入。
        if ("PO_RECEIPT".equals(r.getSourceDocType())) {
            fillSourceDocId(r);
            PoReceipt src = r.getSourceDocId() == null ? null : poReceiptMapper.selectById(r.getSourceDocId());
            if (src != null && src.getAmount() != null) {
                BigDecimal returned = alreadyReturnedAmount(r);
                if (returned.add(r.getAmount()).compareTo(src.getAmount()) > 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "退货金额超限：源单 " + r.getSourceBillNo() + " 金额 " + src.getAmount()
                                    + "，已退 " + returned + "，本次 " + r.getAmount());
                }
            }
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("approver", r.getApprover());
        auditBefore.put("approveTime", r.getApproveTime());
        r.setStatus(PurReturn.ST_APPROVED);
        r.setApprover(currentUserName());
        r.setApproveTime(LocalDateTime.now());
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("approver", r.getApprover());
        auditAfter.put("approveTime", r.getApproveTime());
        auditTrailService.recordUpdate("PUR_RETURN", id, r.getBillNo(), "pur_return",
                String.valueOf(id), auditBefore, auditAfter, "审核通过");
        // 边界：APPROVED 收尾下游环节——按 return_mode 分流（ReturnLegMapping 为
        // 采购/销售两域共用映射表）：REFUND 退货退款 ⇒ 生成 supplier_recon_detail 负向行
        // （item_type='PUR_RETURN'、amount 取负、关联源入库单 GRN 的 source_bill_no）并按明细重算
        // 对账单余额；EXCHANGE 退货补货 ⇒ 不进对账（不冲减应付）。与审核同事务收尾
        // 复用 收尾事件规则，不另建定时任务。
        if (ReturnLegMapping.entersRecon(r.getReturnMode())) {
            postRefundToSupplierRecon(r);
        }
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurReturn reject(Long id, String reason) {
        PurReturn r = require(id);
        if (!PurReturn.ST_PENDING.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 仅待审核态可驳回");
        }
        // 驳回与审核同属「审批决定」：自己驳回自己等于绕过撤回，一并拦截
        assertNotSelf(r, "驳回");
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(PurReturn.ST_REJECTED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voidReason", r.getVoidReason());
        auditTrailService.recordUpdate("PUR_RETURN", id, r.getBillNo(), "pur_return",
                String.valueOf(id), auditBefore, auditAfter, "驳回");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurReturn voidBill(Long id, String reason) {
        PurReturn r = require(id);
        if (!List.of(PurReturn.ST_DRAFT, PurReturn.ST_PENDING, PurReturn.ST_REJECTED, PurReturn.ST_APPROVED)
                .contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因（退出对账需记录）");
        }
        // 作废「已审核」单 = 反审核（抹掉一条已生效的冲减记录），禁止本人操作；
        // 草稿/在途/驳回态本人作废属流程内清理，无控制被绕过，故不拦。
        if (PurReturn.ST_APPROVED.equals(r.getStatus())) {
            assertNotSelf(r, "作废");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(PurReturn.ST_VOIDED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voidReason", r.getVoidReason());
        auditTrailService.recordUpdate("PUR_RETURN", id, r.getBillNo(), "pur_return",
                String.valueOf(id), auditBefore, auditAfter, "作废");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurReturn r = require(id);
        if (!PurReturn.ST_DRAFT.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可删除：" + r.getBillNo());
        }
        lineMapper.delete(new LambdaQueryWrapper<PurReturnLine>().eq(PurReturnLine::getReturnId, id));
        mapper.deleteById(id);
    }

    // 查询

    public PurReturn get(Long id) {
        PurReturn r = require(id);
        r.setLines(lineMapper.selectList(new LambdaQueryWrapper<PurReturnLine>()
                .eq(PurReturnLine::getReturnId, id).orderByAsc(PurReturnLine::getLineNo)));
        fillApproverNames(Collections.singletonList(r));
        return r;
    }

    /**
     * 退货单分页查询（列表页与 {@code GET /export} 共用同一查询）。
     *
     * <p>只按入参过滤的话，既无数据范围也不封顶；{@code /export} 传
     * {@code Integer.MAX_VALUE} 调用本方法，等于"凭一个 companyId 就能整表拉走"。
     * 现按 data_scope 五级办结（退货单无部门/经办人列，故只到公司级，其余级别降级本公司）。</p>
     */
    @DataScope("采购退货列表数据范围")
    public PageResult<PurReturn> page(long pageNum, long pageSize, Long companyId, String vendorCode,
                                      String status, String billDateStart, String billDateEnd, String keyword) {
        LambdaQueryWrapper<PurReturn> q = new LambdaQueryWrapper<>();
        if (companyId != null) q.eq(PurReturn::getCompanyId, companyId);
        if (vendorCode != null && !vendorCode.isBlank()) q.eq(PurReturn::getVendorCode, vendorCode);
        if (status != null && !status.isBlank()) q.eq(PurReturn::getStatus, status);
        if (billDateStart != null && !billDateStart.isBlank()) q.ge(PurReturn::getBillDate, billDateStart);
        if (billDateEnd != null && !billDateEnd.isBlank()) q.le(PurReturn::getBillDate, billDateEnd);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(PurReturn::getBillNo, keyword).or().like(PurReturn::getVendorName, keyword));
        }
        DataScopeKit.apply(q, PurReturn::getCompanyId, null, null);
        q.orderByDesc(PurReturn::getId);
        Page<PurReturn> p =
                mapper.selectPage(new Page<>(pageNum, pageSize), q);
        fillApproverNames(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // 内部工具

    /**
     * 历史行补齐：读取时把 approver 的工号批量换成真名。
     * <p>写入侧 {@link #currentUserName()} 只对"本次审批"生效；审批发生在升级前建的老单据
     * 仍存工号，列表「审核人」列因此直接显示数字。
     * 与应付域 {@code ApRequestService.fillDisplayNames} 同一读时解析规则，写入数据库值不动。</p>
     */
    private void fillApproverNames(List<PurReturn> rows) {
        if (rows == null || rows.isEmpty()) return;
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (PurReturn r : rows) {
            if (r.getApprover() != null && !r.getApprover().isBlank()) codes.add(r.getApprover().trim());
        }
        if (codes.isEmpty()) return;
        Map<String, String> display = new HashMap<>();
        for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserName, codes))) {
            if (u.getNickName() != null && !u.getNickName().isBlank()) {
                display.put(u.getUserName(), u.getNickName());
            }
        }
        if (display.isEmpty()) return;
        for (PurReturn r : rows) {
            if (r.getApprover() != null) {
                r.setApprover(display.getOrDefault(r.getApprover().trim(), r.getApprover()));
            }
        }
    }

    private List<PurReturnLine> normalizeLines(PurReturn r) {
        List<PurReturnLine> lines = r.getLines();
        if (lines == null) lines = new ArrayList<>();
        List<PurReturnLine> out = new ArrayList<>();
        int no = 1;
        for (PurReturnLine ln : lines) {
            if (ln == null) continue;
            // 界面「加一行」留出的空行（数量 1、其余全空）不落库，也不参与合计与校验；
            // 否则会给退货单塞进一行无名称无金额的脏数据，行数与金额口径都对不上。
            if (isBlankLine(ln)) continue;
            ln.setId(null);
            ln.setReturnId(r.getId());
            if (ln.getLineNo() == null) ln.setLineNo(no);
            if (ln.getAmount() == null) ln.setAmount(BigDecimal.ZERO);
            out.add(ln);
            no++;
        }
        fillLineTrace(r, out);
        return out;
    }

    /** 未动过的默认空行：数量停在 1，其余字段全空 */
    private boolean isBlankLine(PurReturnLine ln) {
        boolean noCode = ln.getItemCode() == null || ln.getItemCode().isBlank();
        boolean noName = ln.getItemName() == null || ln.getItemName().isBlank();
        boolean noUom = ln.getUom() == null || ln.getUom().isBlank();
        boolean noPrice = ln.getPrice() == null || ln.getPrice().signum() == 0;
        boolean noAmount = ln.getAmount() == null || ln.getAmount().signum() == 0;
        boolean defaultQty = ln.getQty() == null || ln.getQty().compareTo(BigDecimal.ONE) == 0;
        return noCode && noName && noUom && noPrice && noAmount && defaultQty;
    }

    /**
     * 明细行逐行校验（与前端 PurchaseReturn.vue 的行内红星同一规则，后端为最终校验）。
     * 原规则只要求「有一行填了名称且金额 > 0」，数量为 0、单价为空、单位为空都能入库，
     * 退货数量与收货/对账口径因此对不上；接口、导入等非界面入口也绕不过这道断言。
     */
    private void assertLineRule(List<PurReturnLine> lines) {
        int no = 1;
        for (PurReturnLine ln : lines) {
            String pos = "第 " + no + " 行";
            if (ln.getItemName() == null || ln.getItemName().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, pos + "物料名称必填");
            }
            if (ln.getQty() == null || ln.getQty().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, pos + "数量必须大于 0");
            }
            if (ln.getPrice() == null || ln.getPrice().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, pos + "单价必须大于 0");
            }
            if (ln.getUom() == null || ln.getUom().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, pos + "单位必填（门户发货与对账要按单位核算数量）");
            }
            no++;
        }
    }

    /**
     * 供应商代码必须在供应商主数据（{@code md_supplier}）现有，名称一律以主数据为准回填。
     * <p>原实现是「代码纯输入框＋名称手填」：任意字符串都能入库（如 XX），且名称留空时
     * {@code vendor_name} 写入数据库为空串 ⇒ 列表「供应商」列空白，
     * 下游对账/函证/税务规则缺关键字段，须进【编辑】手输名称才有值。
     * <p>名称以主数据覆盖而非"空才补"：手工名称与现有名称分叉会让同一供应商在单据上出现两种写法。
     */
    private void assertVendorInMaster(PurReturn r) {
        MdSupplier sup = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                .eq(MdSupplier::getSupplierCode, r.getVendorCode())
                .last("limit 1"));
        if (sup == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "供应商代码 " + r.getVendorCode() + " 不在供应商主数据中：请先在【供应商管理】建档，或改用在册代码");
        }
        r.setVendorName(sup.getSupplierName());
    }

    /**
     * 按 company_id 补充 company_code/company_name。
     * 界面只提交 company_id（公司下拉），两列不补就恒 NULL ⇒ 台账/导出里法人主体只有 id，
     * 读的人无从对号。公司 id 不现有时如实报错，不静默留空。
     */
    private void fillCompanyCoords(PurReturn r) {
        if (r.getCompanyId() == null) return;
        SysCompany c = sysCompanyMapper.selectById(r.getCompanyId());
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属公司不存在: " + r.getCompanyId());
        }
        r.setCompanyCode(c.getCompanyCode());
        r.setCompanyName(c.getCompanyName());
    }

    /**
     * 明细行补「单位」与「来源行 id」，保证行级追溯（原两列恒 NULL）。
     * <p>来源＝采购订单行 {@code po_order_line}（本系统收货单 {@code po_receipt} 无行表，
     * 行级坐标只能落在订单行上）：源单是收货单时经 {@code po_receipt.po_id} 定位到订单再取行。
     * <p>只在能唯一确定时才写 source_line_id；单位在多条候选不一致时留空（宁缺勿错关联），
     * 界面仍允许人工填写单位。
     */
    private void fillLineTrace(PurReturn r, List<PurReturnLine> lines) {
        Long poId = resolveSourcePoId(r);
        for (PurReturnLine ln : lines) {
            if (ln.getItemCode() == null || ln.getItemCode().isBlank()) continue;
            List<PoOrderLine> hits = poOrderLineMapper.selectList(
                    new LambdaQueryWrapper<PoOrderLine>()
                            .eq(PoOrderLine::getMaterialCode, ln.getItemCode())
                            .eq(poId != null, PoOrderLine::getPoId, poId));
            if (hits.isEmpty()) continue;
            if (ln.getUom() == null || ln.getUom().isBlank()) {
                String unit = hits.get(0).getUnit();
                boolean agree = hits.stream().allMatch(h -> Objects.equals(h.getUnit(), unit));
                if (agree && unit != null && !unit.isBlank()) ln.setUom(unit);
            }
            if (ln.getSourceLineId() == null && hits.size() == 1) {
                ln.setSourceLineId(hits.get(0).getId());
            }
        }
    }

    /**
     * 按源单类型＋单号补写 {@code source_doc_id}。
     * <p>界面只提交源单号（如 GRN374866），该列历史上恒 NULL；而审核期「累计已退 + 本次 ≤ 源单金额」
     * 与 {@link #alreadyReturnedAmount} 都以 id 为键 ⇒ 校验对界面单形同不存在。
     * 找不到源单时留空（宁缺勿错关联），不阻断保存：源单号本身仍是可追溯的唯一凭据。
     */
    private void fillSourceDocId(PurReturn r) {
        if (r.getSourceDocId() != null) return;
        String t = r.getSourceDocType();
        String no = r.getSourceBillNo();
        if (t == null || t.isBlank() || no == null || no.isBlank()) return;
        if ("PO_RECEIPT".equalsIgnoreCase(t)) {
            PoReceipt rec = poReceiptMapper.selectOne(new LambdaQueryWrapper<PoReceipt>()
                    .eq(PoReceipt::getReceiptNo, no).last("limit 1"));
            if (rec != null) r.setSourceDocId(rec.getId());
        } else if ("PO_ORDER".equalsIgnoreCase(t)) {
            PoOrder po = poOrderMapper.selectOne(
                    new LambdaQueryWrapper<PoOrder>()
                            .eq(PoOrder::getPoNo, no).last("limit 1"));
            if (po != null) r.setSourceDocId(po.getId());
        }
    }

    /** 源单 → 采购订单 id（PO_RECEIPT 经 po_receipt.po_id；PO_ORDER 直接取；无法定位返回 null） */
    private Long resolveSourcePoId(PurReturn r) {
        String t = r.getSourceDocType();
        if (t == null || t.isBlank()) return null;
        String no = r.getSourceBillNo();
        if ("PO_RECEIPT".equalsIgnoreCase(t)) {
            PoReceipt rec = r.getSourceDocId() != null ? poReceiptMapper.selectById(r.getSourceDocId())
                    : (no == null || no.isBlank() ? null : poReceiptMapper.selectOne(
                            new LambdaQueryWrapper<PoReceipt>().eq(PoReceipt::getReceiptNo, no).last("limit 1")));
            return rec == null ? null : rec.getPoId();
        }
        if ("PO_ORDER".equalsIgnoreCase(t)) {
            if (r.getSourceDocId() != null) return r.getSourceDocId();
            if (no == null || no.isBlank()) return null;
            PoOrder po = poOrderMapper.selectOne(
                    new LambdaQueryWrapper<PoOrder>()
                            .eq(PoOrder::getPoNo, no).last("limit 1"));
            return po == null ? null : po.getId();
        }
        return null;
    }

    private void saveLines(Long returnId, List<PurReturnLine> lines) {
        for (PurReturnLine ln : lines) {
            ln.setReturnId(returnId);
            lineMapper.insert(ln);
        }
    }

    private void validateLineSum(BigDecimal headerAmount, List<PurReturnLine> lines) {
        BigDecimal sum = lines.stream().map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(headerAmount).abs().compareTo(TOL) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "明细行金额合计(" + sum + ")与表头金额(" + headerAmount + ")不一致（允许尾差≤0.01）");
        }
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货单至少包含一行明细");
        }
    }

    /**
     * 表头税额按明细派生（含税规则，与前端 derivedTax/recalc 同式、与发票侧价税分离规则同源）：
     * taxAmount = Σ line.amount × taxRate / (100 + taxRate)；行无税率按 0 计。
     * 保存即整算回写（create/update 同一入口），历史 tax_amount=0 的历史行出只读清单交人工，本方法只影响新写。
     */
    private BigDecimal deriveTaxAmount(List<PurReturnLine> lines) {
        BigDecimal tax = BigDecimal.ZERO;
        for (PurReturnLine ln : lines) {
            if (ln == null || ln.getAmount() == null || ln.getTaxRate() == null || ln.getTaxRate().signum() <= 0) {
                continue;
            }
            tax = tax.add(ln.getAmount().multiply(ln.getTaxRate())
                    .divide(new BigDecimal("100").add(ln.getTaxRate()), 2, RoundingMode.HALF_UP));
        }
        return tax;
    }

    /**
     * 同源单已生效退货金额合计。升级前建的单 {@code source_doc_id} 为 NULL，
     * 只按 id 统计会把它们漏算 ⇒ 同时按「源单类型＋单号」并入（同一 id 下的历史行，两种条件只命中一次）。
     */
    private BigDecimal alreadyReturnedAmount(PurReturn r) {
        LambdaQueryWrapper<PurReturn> q = new LambdaQueryWrapper<PurReturn>()
                .eq(PurReturn::getStatus, PurReturn.ST_APPROVED);
        if (r.getSourceDocId() != null) {
            q.and(w -> w.eq(PurReturn::getSourceDocId, r.getSourceDocId())
                    .or(x -> x.isNull(PurReturn::getSourceDocId)
                            .eq(PurReturn::getSourceDocType, r.getSourceDocType())
                            .eq(PurReturn::getSourceBillNo, r.getSourceBillNo())));
        } else {
            q.eq(PurReturn::getSourceDocType, r.getSourceDocType())
                    .eq(PurReturn::getSourceBillNo, r.getSourceBillNo());
        }
        return mapper.selectList(q)
                .stream().filter(x -> !x.getId().equals(r.getId()))
                .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 下游环节（APPROVED 收尾按 return_mode 分流）

    /**
     * REFUND 退货退款 → 供应商对账域：生成 supplier_recon_detail 负向明细行
     * （item_type=〔PUR_RETURN〕、amount 取负、itemNo 关联源入库单 GRN 的 source_bill_no），
     * 并按明细重算对账单期间四列与累计余额。EXCHANGE 不进入本方法（不冲减应付）。
     * 去重：同一退货单号在同一对账单已挂行则跳过（防重复审核/事件重放）。
     */
    private void postRefundToSupplierRecon(PurReturn r) {
        MdSupplier sup = resolveSupplier(r);
        if (sup == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "退货单 " + r.getBillNo() + " 供应商主数据不存在：" + r.getVendorCode() + "（无法挂账进对账）");
        }
        String period = YearMonth.from(r.getBillDate()).toString().replace("-", "");
        SupplierRecon recon = supplierReconMapper.selectOne(new LambdaQueryWrapper<SupplierRecon>()
                .eq(SupplierRecon::getSupplierId, sup.getId())
                .eq(SupplierRecon::getPeriod, period)
                .last("limit 1"));
        if (recon == null) {
            recon = newSupplierRecon(r, sup, period);
        }
        Long dup = supplierReconDetailMapper.selectCount(new LambdaQueryWrapper<SupplierReconDetail>()
                .eq(SupplierReconDetail::getReconId, recon.getId())
                .eq(SupplierReconDetail::getItemType, ReturnLegMapping.PUR_RETURN_ITEM_TYPE)
                .like(SupplierReconDetail::getRemark, r.getBillNo()));
        if (dup != null && dup > 0) {
            log.info("[returns] 退货单 {} 已在对账单 {} 挂过负向行，跳过", r.getBillNo(), recon.getReconNo());
            return;
        }
        SupplierReconDetail d = new SupplierReconDetail();
        d.setTenantId(TenantContext.getTenantId());
        d.setReconId(recon.getId());
        d.setItemType(ReturnLegMapping.PUR_RETURN_ITEM_TYPE);
        d.setItemId(r.getSourceDocId() == null ? r.getId() : r.getSourceDocId());
        d.setItemNo(r.getSourceBillNo() == null || r.getSourceBillNo().isBlank() ? r.getBillNo() : r.getSourceBillNo());
        d.setItemDate(r.getBillDate());
        d.setAmount(ReturnLegMapping.negate(r.getAmount()));
        d.setMatchStatus("UNMATCHED");
        d.setRemark("采购退货单 " + r.getBillNo() + "（REFUND 退货退款进对账"
                + (r.getSourceBillNo() == null || r.getSourceBillNo().isBlank() ? "" : "，源入库单 " + r.getSourceBillNo())
                + "）");
        d.setCreateTime(LocalDateTime.now());
        d.setUpdateTime(LocalDateTime.now());
        supplierReconDetailMapper.insert(d);
        recalcReconFromDetails(recon);
        invalidateReconConfirmation(r, recon, d.getAmount());
        log.info("[returns] 采购退货单 {} REFUND 分流：对账单 {} 写入负向行 {}，余额已重算",
                r.getBillNo(), recon.getReconNo(), d.getAmount());
    }

    /**
     * 已发函/已确认的对账单被退货冲减后，原供应商确认结论已不代表新余额：
     * 置回待确认并清空确认时间，同时补一条差异记录，避免“余额变了、确认状态仍是已确认”。
     * 未发函的草稿对账单不打断（本就未确认）。
     */
    private void invalidateReconConfirmation(PurReturn r, SupplierRecon recon, BigDecimal amount) {
        String oldStatus = recon.getStatus();
        String oldConfirm = recon.getSupplierConfirmStatus();
        boolean letterLive = "SENT".equals(recon.getLetterStatus()) || "RECEIVED".equals(recon.getLetterStatus());
        boolean wasConfirmed = "CONFIRMED".equals(oldStatus) || "DIFF".equals(oldStatus)
                || "RESOLVED".equals(oldStatus)
                || "CONFIRMED".equals(oldConfirm) || "REJECTED".equals(oldConfirm);
        if (!wasConfirmed && !letterLive) {
            return;
        }
        String newStatus = letterLive ? "SENT" : "DRAFT";
        String newConfirm = letterLive ? "PENDING" : "NONE";
        String note = "退货单 " + r.getBillNo() + " REFUND 冲减 " + amount
                + "，原确认状态 " + oldStatus + "/" + oldConfirm + " 已失效，需重新确认";

        supplierReconMapper.update(null, new LambdaUpdateWrapper<SupplierRecon>()
                .eq(SupplierRecon::getId, recon.getId())
                .set(SupplierRecon::getStatus, newStatus)
                .set(SupplierRecon::getSupplierConfirmStatus, newConfirm)
                .set(SupplierRecon::getSupplierConfirmTime, null)
                .set(SupplierRecon::getSupplierConfirmRemark, null)
                .set(SupplierRecon::getLetterReceivedTime, null)
                .set(SupplierRecon::getConfirmTime, null)
                .set(SupplierRecon::getConfirmBy, null)
                .set(SupplierRecon::getDiffRemark, note)
                .set(SupplierRecon::getUpdateBy, currentUserId())
                .set(SupplierRecon::getUpdateTime, LocalDateTime.now()));

        SupplierReconDiff diff = new SupplierReconDiff();
        diff.setTenantId(TenantContext.getTenantId());
        diff.setReconId(recon.getId());
        diff.setDiffType("AMOUNT_DIFF");
        diff.setOurAmount(amount);
        diff.setSupplierAmount(BigDecimal.ZERO);
        diff.setDiffAmount(amount);
        diff.setReason("REFUND 退货冲减后需供应商重新确认：" + r.getBillNo());
        diff.setAdjustmentType(ReturnLegMapping.PUR_RETURN_ITEM_TYPE);
        diff.setStatus("PENDING");
        diff.setRemark("采购退货单 " + r.getBillNo() + " 自动登记");
        diff.setCreateBy(currentUserId());
        diff.setCreateTime(LocalDateTime.now());
        diff.setUpdateTime(LocalDateTime.now());
        supplierReconDiffMapper.insert(diff);

        auditTrailService.recordUpdate("SUPPLIER_RECON", recon.getId(), recon.getReconNo(), "supplier_recon",
                String.valueOf(recon.getId()),
                Map.of("status", oldStatus == null ? "" : oldStatus,
                        "supplierConfirmStatus", oldConfirm == null ? "" : oldConfirm),
                Map.of("status", newStatus, "supplierConfirmStatus", newConfirm),
                note);
        log.info("[returns] 退货单 {} 冲减对账单 {}：供应商确认状态 {}→{}，已补差异记录",
                r.getBillNo(), recon.getReconNo(), oldConfirm, newConfirm);
    }

    /** vendor_code → md_supplier（对账单以 supplier_id 挂账；编码优先、名称兜底） */
    private MdSupplier resolveSupplier(PurReturn r) {
        MdSupplier sup = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                .eq(MdSupplier::getSupplierCode, r.getVendorCode())
                .last("limit 1"));
        if (sup == null && r.getVendorName() != null && !r.getVendorName().isBlank()) {
            sup = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                    .eq(MdSupplier::getSupplierName, r.getVendorName())
                    .last("limit 1"));
        }
        return sup;
    }

    private SupplierRecon newSupplierRecon(PurReturn r, MdSupplier sup, String period) {
        SupplierRecon recon = new SupplierRecon();
        recon.setTenantId(TenantContext.getTenantId());
        recon.setSupplierId(sup.getId());
        recon.setSupplierName(sup.getSupplierName());
        recon.setReconNo(nextReconNo());
        recon.setPeriod(period);
        recon.setPayableAmount(BigDecimal.ZERO);
        recon.setPaidAmount(BigDecimal.ZERO);
        recon.setBalance(BigDecimal.ZERO);
        recon.setOpeningBalance(BigDecimal.ZERO);
        recon.setPeriodPayable(BigDecimal.ZERO);
        recon.setPeriodPaid(BigDecimal.ZERO);
        recon.setClosingBalance(BigDecimal.ZERO);
        recon.setReconType("AUTO");
        recon.setLetterStatus("NONE");
        recon.setSupplierConfirmStatus("PENDING");
        recon.setLinkuSyncStatus("NONE");
        recon.setStatus("DRAFT");
        recon.setCompanyId(r.getCompanyId() != null ? r.getCompanyId()
                : (UserContext.get() == null ? null : UserContext.get().getCompanyId()));
        recon.setCreateBy(currentUserId());
        recon.setCreateTime(LocalDateTime.now());
        recon.setUpdateBy(currentUserId());
        recon.setUpdateTime(LocalDateTime.now());
        recon.setDelFlag(0);
        supplierReconMapper.insert(recon);
        return recon;
    }

    private String nextReconNo() {
        try {
            return numberRuleService.next("SUPPLIER_RECON");
        } catch (Exception e) {
            return "RC" + System.currentTimeMillis();
        }
    }

    /**
     * 按明细重算对账单余额（去重，可从任意时点重放）：
     * periodPayable = ΣINVOICE + ΣPUR_RETURN（退货负向行直接冲减本期应付）；
     * periodPaid = ΣPAYMENT；closing = 期初 + 本期应付 − 本期已付；
     * 累计列按 beforePaid = 累计已付 − 本期已付 还原后重算（与生成规则一致，双列兼容）。
     */
    private void recalcReconFromDetails(SupplierRecon recon) {
        List<SupplierReconDetail> details = supplierReconDetailMapper.selectList(
                new LambdaQueryWrapper<SupplierReconDetail>().eq(SupplierReconDetail::getReconId, recon.getId()));
        BigDecimal invoiceSum = BigDecimal.ZERO;
        BigDecimal returnSum = BigDecimal.ZERO;
        BigDecimal paymentSum = BigDecimal.ZERO;
        for (SupplierReconDetail d : details) {
            BigDecimal amt = d.getAmount() == null ? BigDecimal.ZERO : d.getAmount();
            if (ReturnLegMapping.PUR_RETURN_ITEM_TYPE.equals(d.getItemType())) {
                returnSum = returnSum.add(amt);
            } else if ("PAYMENT".equals(d.getItemType())) {
                paymentSum = paymentSum.add(amt);
            } else {
                invoiceSum = invoiceSum.add(amt);
            }
        }
        BigDecimal opening = nz(recon.getOpeningBalance());
        BigDecimal paidCum = nz(recon.getPaidAmount());
        BigDecimal periodPayable = invoiceSum.add(returnSum);
        BigDecimal periodPaid = paymentSum;
        BigDecimal closing = opening.add(periodPayable).subtract(periodPaid);
        BigDecimal payable = opening.add(paidCum).subtract(periodPaid).add(periodPayable);
        SupplierRecon upd = new SupplierRecon();
        upd.setId(recon.getId());
        upd.setPeriodPayable(periodPayable);
        upd.setPeriodPaid(periodPaid);
        upd.setClosingBalance(closing);
        upd.setPayableAmount(payable);
        upd.setBalance(payable.subtract(paidCum));
        upd.setUpdateBy(currentUserId());
        upd.setUpdateTime(LocalDateTime.now());
        supplierReconMapper.updateById(upd);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private PurReturn require(Long id) {
        PurReturn r = mapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购退货单不存在 id=" + id);
        }
        return r;
    }

    private String currentUserName() {
        try {
            LoginUser u = UserContext.get();
            if (u == null) return null;
            // 审核人/经办人列直接显示工号——
            // getUserName() 是登录账号（工号），真名在 nickName；与应付域 ApRequestService 同一规则：
            // 有真名取真名，无真名（历史账号未维护）才回落工号，不留空。
            String real = u.getNickName();
            return real != null && !real.isBlank() ? real : u.getUserName();
        } catch (Exception e) {
            return null;
        }
    }

    private Long currentUserId() {
        try {
            LoginUser u = UserContext.get();
            return u == null ? null : u.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 自审禁止开关（与 {@code WfEngineFacade#isDenySelfEnabled} 同一开关、同一语义，fail-closed）：
     * <ul>
     *  <li>{@code sys_config.approve.deny_self = '1'} → 开启；</li>
     *  <li>配置缺失 / 读取异常 → <b>默认开启</b>（宁可禁止，不静默放行）；</li>
     *  <li>显式 {@code '0'} → 关闭（兼容演示数据）。</li>
     * </ul>
     * 内控依据：审批人必须与制单人分离（方案 §5.5「审核权限授予共享审核岗」）。
     */
    private boolean isDenySelfEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, "approve.deny_self")
                    .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return true;
            }
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.error("[returns] 读取 approve.deny_self 异常，fail-closed 默认禁止自审: {}", ex.getMessage(), ex);
            return true;
        }
    }

    /**
     * 单据级「操作人 ≠ 制单人」拦截 —— 第二道防线，与「角色审批/制单分离」互补。
     * <p>角色隔离挡的是「授权时把两侧码塞给同一角色/同一人」；本方法挡的是「运行期单据归属」，
     * 两者缺一不可：超级权限（{@code *:*:*}）与未来授权回退都会绕过前者。
     * <p>{@code create_by} 为空（历史/外部导入数据）时无法判定归属，放行以免误伤历史数据。
     */
    private void assertNotSelf(PurReturn r, String action) {
        if (!isDenySelfEnabled()) {
            return;
        }
        Long me = currentUserId();
        if (me == null || r.getCreateBy() == null) {
            return;
        }
        if (me.equals(r.getCreateBy())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "禁止" + action + "自己制单的单据（" + r.getBillNo() + "），请由他人办理");
        }
    }
}
