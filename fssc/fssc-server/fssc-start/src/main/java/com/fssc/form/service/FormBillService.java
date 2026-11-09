// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.form.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.form.entity.BizFormBill;
import com.fssc.form.mapper.BizFormBillMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.masterdata.entity.MdFormConfig;
import com.fssc.masterdata.mapper.MdFormConfigMapper;
import com.fssc.masterdata.service.FormMetaService;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.BillRouteRegistry;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可配置表单的通用单据运行时。
 *
 * <p>一张单据的全部行为只由配置决定，本类不含任何具体单据类型的判断：
 * <ul>
 *   <li>字段与校验规则 → {@code md_form_field} / {@code md_form_validation}（经 {@link FormMetaService}）</li>
 *   <li>起哪些流程 → {@code md_form_config.model_key} / {@code biz_type}，再由 {@code wf_process_config} 分档命中</li>
 *   <li>单据编号 → {@code sys_number_rule}（按 form_code 取号，未配规则时降级）</li>
 *   <li>界面 → 前端按元数据渲染，不感知具体表单</li>
 * </ul>
 *
 * <p>与费用单据的分工：{@code store_mode='EXPENSE_WIDE'} 的表单继续走 {@code exp_bill}，
 * 本类只接管 {@code store_mode='JSON'} 的通用审批单据，不触碰既有费用链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormBillService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BizFormBillMapper billMapper;
    private final MdFormConfigMapper formConfigMapper;
    private final FormMetaService formMetaService;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;

    // ===== 配置 =====

    /** 取启用中的表单配置；不存在或已停用直接拒绝，避免"配了没发"的半成品对外可见 */
    public MdFormConfig requireConfig(String formCode) {
        if (!StringUtils.hasText(formCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单编码不能为空");
        }
        MdFormConfig cfg = formConfigMapper.selectOne(new LambdaQueryWrapper<MdFormConfig>()
                .eq(MdFormConfig::getFormCode, formCode)
                .last("limit 1"));
        if (cfg == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单不存在：" + formCode);
        }
        if (!"NORMAL".equalsIgnoreCase(cfg.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单已停用：" + formCode);
        }
        return cfg;
    }

    /** 供前端取运行时元数据：表单头 + 字段 + 规则 + 默认值，一次返回 */
    public Map<String, Object> runtimeMeta(String formCode, String roles) {
        MdFormConfig cfg = requireConfig(formCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formCode", cfg.getFormCode());
        out.put("formName", cfg.getFormName());
        out.put("modelKey", cfg.getModelKey());
        out.put("storeMode", cfg.getStoreMode());
        out.put("allowDraft", cfg.getAllowDraft());
        out.put("status", cfg.getStatus());
        out.put("fields", formMetaService.fieldsOf(formCode, splitRoles(roles)));
        out.put("rules", formMetaService.rulesOf(formCode, null));
        return out;
    }

    // ===== 单据 =====

    /**
     * 保存单据（新建或更新）。
     *
     * <p>必录/范围/正则/跨字段全部由服务端元数据校验拦下，前端 rules 只是提示。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(String formCode, Long id, Map<String, Object> formData) {
        MdFormConfig cfg = requireConfig(formCode);
        assertJsonStore(cfg);
        Map<String, Object> data = formData == null ? new LinkedHashMap<>() : formData;
        formMetaService.validateOrThrow(formCode, data, "SAVE");

        Long uid = currentUserId();
        String json = toJson(data);

        if (id == null) {
            BizFormBill bill = new BizFormBill();
            bill.setFormCode(formCode);
            bill.setBillNo(nextBillNo(formCode));
            bill.setBizType(resolveBizType(cfg));
            bill.setApplyUserId(uid);
            bill.setApplyDeptId(asLong(data.get("applyDeptId")));
            bill.setCompanyId(asLong(data.get("companyId")));
            bill.setStatus("DRAFT");
            bill.setFormData(json);
            bill.setTitle(buildTitle(cfg, data, bill.getBillNo()));
            billMapper.insert(bill);
            return bill.getId();
        }

        BizFormBill bill = requireOwned(id, uid);
        assertEditable(bill);
        bill.setFormData(json);
        bill.setApplyDeptId(asLong(data.get("applyDeptId")));
        bill.setCompanyId(asLong(data.get("companyId")));
        bill.setTitle(buildTitle(cfg, data, bill.getBillNo()));
        billMapper.updateById(bill);
        return id;
    }

    /**
     * 提交：按 APPROVE 时机再校验一次，然后以配置里的流程 key 起流。
     *
     * <p>驳回件（REJECTED）重新提交时覆盖 proc_inst_id —— 旧实例已结束，
     * 新实例从首节点重新走，不做实例复活。
     */
    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        Long uid = currentUserId();
        BizFormBill bill = requireOwned(id, uid);
        assertEditable(bill);
        MdFormConfig cfg = requireConfig(bill.getFormCode());
        assertJsonStore(cfg);

        Map<String, Object> data = fromJson(bill.getFormData());
        formMetaService.validateOrThrow(bill.getFormCode(), data, "APPROVE");

        if (!StringUtils.hasText(bill.getBillNo())) {
            bill.setBillNo(nextBillNo(bill.getFormCode()));
        }
        if (!StringUtils.hasText(bill.getTitle())) {
            bill.setTitle(buildTitle(cfg, data, bill.getBillNo()));
        }
        String bizType = resolveBizType(cfg);
        // 注册台账后起流：resolveBizNo 才能从 biz_form_bill 取到单号写进 wf_instance.biz_no，
        // 待办列表的单号列不会空。未注册时起流仍成功，只是单号列留白。
        BillRouteRegistry.registerGenericBill(bizType);
        String procInstId = wfEngineFacade.start(bizType, bill.getId(), null, bill.getTitle(), null, null);

        bill.setBizType(bizType);
        bill.setProcInstId(procInstId);
        bill.setStatus("APPROVING");
        bill.setSubmitTime(LocalDateTime.now());
        billMapper.updateById(bill);
        return procInstId;
    }

    /**
     * 详情。
     *
     * <p>数据面取舍：同一租户的登录用户都可读。审批人打开待办时并不知道表单归属校验规则，
     * 若按申请人过滤会把正常审批挡在门外；写操作（保存/提交）仍严格限本人。
     */
    public Map<String, Object> detail(Long id) {
        BizFormBill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在：" + id);
        }
        MdFormConfig cfg = formConfigMapper.selectOne(new LambdaQueryWrapper<MdFormConfig>()
                .eq(MdFormConfig::getFormCode, bill.getFormCode())
                .last("limit 1"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", bill.getId());
        out.put("formCode", bill.getFormCode());
        out.put("formName", cfg == null ? bill.getFormCode() : cfg.getFormName());
        out.put("billNo", bill.getBillNo());
        out.put("title", bill.getTitle());
        out.put("bizType", bill.getBizType());
        out.put("status", bill.getStatus());
        out.put("procInstId", bill.getProcInstId());
        out.put("applyUserId", bill.getApplyUserId());
        out.put("applyDeptId", bill.getApplyDeptId());
        out.put("companyId", bill.getCompanyId());
        out.put("createTime", bill.getCreateTime());
        out.put("submitTime", bill.getSubmitTime());
        out.put("finishTime", bill.getFinishTime());
        out.put("editable", canEdit(bill) && currentUserId().equals(bill.getApplyUserId()));
        out.put("data", fromJson(bill.getFormData()));
        return out;
    }

    /** 我发起的单据 */
    public PageResult<BizFormBill> myPage(String formCode, String status, long pageNum, long pageSize) {
        Long uid = currentUserId();
        LambdaQueryWrapper<BizFormBill> w = new LambdaQueryWrapper<BizFormBill>()
                .eq(BizFormBill::getApplyUserId, uid)
                .eq(StringUtils.hasText(formCode), BizFormBill::getFormCode, formCode)
                .eq(StringUtils.hasText(status), BizFormBill::getStatus, status)
                .orderByDesc(BizFormBill::getId);
        Page<BizFormBill> p = billMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // ===== 内部 =====

    /** 通用运行库只管 JSON 存储；宽表表单请走各自域的入口，避免出现两套写入真值 */
    private void assertJsonStore(MdFormConfig cfg) {
        if ("EXPENSE_WIDE".equalsIgnoreCase(cfg.getStoreMode())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "表单「" + cfg.getFormName() + "」由业务单据链路承载，请从对应业务菜单发起");
        }
    }

    private String resolveBizType(MdFormConfig cfg) {
        return StringUtils.hasText(cfg.getBizType()) ? cfg.getBizType() : cfg.getFormCode();
    }

    /** 优先取配置的标题字段，取不到则用「表单名-单号」保证待办列表可区分 */
    private String buildTitle(MdFormConfig cfg, Map<String, Object> data, String billNo) {
        if (StringUtils.hasText(cfg.getTitleExpr())) {
            Object v = data.get(cfg.getTitleExpr());
            if (v != null && StringUtils.hasText(String.valueOf(v))) {
                return String.valueOf(v);
            }
        }
        return cfg.getFormName() + (StringUtils.hasText(billNo) ? "-" + billNo : "");
    }

    private String nextBillNo(String formCode) {
        try {
            String no = numberRuleService.next(formCode);
            if (StringUtils.hasText(no)) {
                return no;
            }
        } catch (Exception e) {
            log.debug("编号规则未配置(formCode={})，使用降级编号: {}", formCode, e.getMessage());
        }
        String prefix = formCode.length() > 6 ? formCode.substring(0, 6) : formCode;
        return prefix + LocalDateTime.now().format(TS) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private BizFormBill requireOwned(Long id, Long uid) {
        BizFormBill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在：" + id);
        }
        if (!uid.equals(bill.getApplyUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作本人发起的单据");
        }
        return bill;
    }

    private boolean canEdit(BizFormBill bill) {
        return "DRAFT".equals(bill.getStatus()) || "REJECTED".equals(bill.getStatus());
    }

    private void assertEditable(BizFormBill bill) {
        if (!canEdit(bill)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "当前状态（" + bill.getStatus() + "）不可编辑或提交");
        }
    }

    private Long currentUserId() {
        if (UserContext.get() == null || UserContext.get().getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return UserContext.get().getUserId();
    }

    private java.util.Set<String> splitRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return java.util.Set.of();
        }
        return java.util.Set.of(roles.split(","));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("表单数据解析失败，按空对象处理: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return JSON.writeValueAsString(data == null ? new LinkedHashMap<>() : data);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单数据序列化失败");
        }
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 供审批回调把单据状态推进到终态 */
    @Transactional(rollbackFor = Exception.class)
    public void finishByProcInst(String procInstId, String status, String currentNode) {
        if (!StringUtils.hasText(procInstId)) {
            return;
        }
        List<BizFormBill> bills = billMapper.selectList(new LambdaQueryWrapper<BizFormBill>()
                .eq(BizFormBill::getProcInstId, procInstId));
        for (BizFormBill bill : bills) {
            bill.setStatus(status);
            bill.setCurrentNode(currentNode);
            if ("APPROVED".equals(status) || "REJECTED".equals(status) || "CANCELED".equals(status)) {
                bill.setFinishTime(LocalDateTime.now());
            }
            billMapper.updateById(bill);
        }
    }
}
