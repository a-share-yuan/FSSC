package com.fssc.apflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fssc.apflow.entity.ApMonthClose;
import com.fssc.apflow.mapper.ApMonthCloseMapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.bank.entity.BankReconItem;
import com.fssc.bank.mapper.BankReconItemMapper;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.service.CollabSyncService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysCompanyMapper;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AP月末关账服务（蓝图 SCC_AP_20）：发起（协同冻结打通点7）→组长派工→
 * 检查项签认（支持通过/退回/挂起+说明+前置自动检查）→ERP关账（恢复协同）→反关账（重开检查）。
 * 冻结语义：scpFrozen=1 期间，本服务提供 isFrozen(companyId) 供付款/协同通道拦截发送。
 *
 * <p>整改（非费用域完成度审查）：companyId 必填并校验组织存在；检查项不再只能"通过"；
 * 全程动作写 traceJson 记录；提供前置自动检查
 * 未审凭证数/未匹配三单数/未达账项数，人工仅处理异常项。
 *
 * <p>本服务不再复写 md_period。会计期间状态机的唯一归属是总账月结审批末节点
 * （见 GlMonthCloseService#applyApprovalOutcome 与），子账月结抢先封期会让
 * AP→AR→GL 的同主体月结链在后两环被"期间已结账"拦死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApMonthCloseService {

    /** 有自动检查规则的检查项：关账前必须跑过自动检查，异常通过必须留处理说明。 */
    private static final List<String> AUTO_ITEM_CODES =
            List.of("BIZ_CHECK", "OFFSET_CHECK", "RELATED_CHECK");

    private final ApMonthCloseMapper mapper;
    private final CollabSyncService collabSyncService;
    private final SysCompanyMapper companyMapper;
    private final OpVoucherMapper voucherMapper;
    private final ExpBillMapper expBillMapper;
    private final BankReconItemMapper bankReconItemMapper;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /** 发起关账：body {companyId,period,groupLeader,remark}；组织必填、期间冲突拦截。 */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose init(Long companyId, String period, String groupLeader, String remark) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账期间格式必须为 yyyy-MM");
        }
        if (companyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "必须选择关账组织（公司）");
        }
        SysCompany company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账组织不存在 id=" + companyId);
        }
        Long exists = mapper.selectCount(new LambdaQueryWrapper<ApMonthClose>()
                .eq(ApMonthClose::getCompanyId, companyId)
                .eq(ApMonthClose::getPeriod, period)
                // 已关账/已作废的旧单不占用本期间（作废后可重新发起）
                .notIn(ApMonthClose::getStatus, ApMonthClose.ST_CLOSED, ApMonthClose.ST_VOID));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间 " + period + " 已存在未完成的关账单");
        }
        ApMonthClose c = new ApMonthClose();
        c.setCloseNo("MC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        c.setCompanyId(companyId);
        c.setPeriod(period);
        c.setGroupLeader(groupLeader);
        c.setRemark(remark);
        c.setCheckItemsJson(defaultItems());
        c.setScpFrozen(1); // 打通点7：关账触发→协同冻结
        c.setStatus(ApMonthClose.ST_INIT);
        mapper.insert(c);
        trace(c, "INIT", "发起关账并冻结协同（组织 " + company.getCompanyName() + "）");
        mapper.updateById(c);
        // 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
        // 打通点7：关账触发 → SCP 状态通知（禁止发送数据至ERP）
        collabSyncService.log(CollabSyncLog.IF_SCP_FREEZE, "AP_TO_SCP", c.getCloseNo(),
                CollabSyncLog.ST_SUCCESS, "关账触发：公司 " + companyId + " 期间 " + period + " 协同冻结", null);
        return c;
    }

    /** 组长派工：body members 逗号分隔（前端人员选择器多选）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose dispatch(Long id, String members) {
        ApMonthClose c = require(id);
        requireStatus(c, ApMonthClose.ST_INIT, "组长派工");
        if (members == null || members.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组长派工必须指定组员清单");
        }
        c.setMembers(members);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        c.setStatus(ApMonthClose.ST_DISPATCHED);
        trace(c, "DISPATCH", "组长派工：组员 " + members);
        mapper.updateById(c);
        // 审计：状态跃迁
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditTrailService.recordUpdate("AP_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ap_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "组长派工");
        return c;
    }

    /**
     * 维护检查项：code=BIZ_CHECK/OFFSET_CHECK/RELATED_CHECK/IT_CHECK；
     * itemStatus=OK 通过 / REJECTED 退回 / SUSPENDED 挂起 / PENDING 重开处理；
     * 全部通过转 CHECKING；每步记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose completeItem(Long id, String code, String itemStatus, String note, String handler) {
        ApMonthClose c = require(id);
        if (ApMonthClose.ST_INIT.equals(c.getStatus()) || ApMonthClose.ST_CLOSED.equals(c.getStatus())
                || ApMonthClose.ST_VOID.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单 " + c.getCloseNo() + " 当前状态不可维护检查项");
        }
        String st = normalizeItemStatus(itemStatus);
        List<Map<String, Object>> items = parseItems(c.getCheckItemsJson());
        String name = null;
        boolean hit = false;
        for (Map<String, Object> item : items) {
            if (code.equals(String.valueOf(item.get("code")))) {
                name = String.valueOf(item.get("name"));
                if ("OK".equals(st)) {
                    requireOverrideNote(item, note);
                }
                item.put("status", st);
                item.put("note", note);
                item.put("handler", handler);
                item.put("time", LocalDateTime.now().toString());
                hit = true;
            }
        }
        if (!hit) {
            throw new BizException(ErrorCode.PARAM_INVALID, "检查项不存在：" + code);
        }
        c.setCheckItemsJson(toJson(items));
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        boolean allOk = items.stream().allMatch(i -> "OK".equals(String.valueOf(i.get("status"))));
        if (allOk && !ApMonthClose.ST_CHECKING.equals(c.getStatus())) {
            c.setStatus(ApMonthClose.ST_CHECKING);
        } else if (!allOk && ApMonthClose.ST_CHECKING.equals(c.getStatus())) {
            // 有检查项被退回/挂起 → 回到检查处理中，关账入口随之关闭
            c.setStatus(ApMonthClose.ST_DISPATCHED);
        }
        trace(c, "ITEM_" + st, "检查项「" + name + "」置 " + itemStatusCn(st)
                + (handler == null || handler.isBlank() ? "" : "（处理人 " + handler + "）")
                + (note == null || note.isBlank() ? "" : "：" + note));
        mapper.updateById(c);
        // 审计：仅当检查项签认实际推动状态跃迁时记录
        if (!Objects.equals(auditBefore.get("status"), c.getStatus())) {
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", c.getStatus());
            auditTrailService.recordUpdate("AP_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ap_month_close",
                    String.valueOf(c.getId()), auditBefore, auditAfter, "维护检查项签认");
        }
        return c;
    }

    /** 前置自动检查：自动跑数，人工仅确认。返回更新后的检查项列表。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoCheck(Long id) {
        ApMonthClose c = require(id);
        if (ApMonthClose.ST_CLOSED.equals(c.getStatus()) || ApMonthClose.ST_VOID.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单已关闭或已作废，无需自动检查");
        }
        YearMonth ym = YearMonth.parse(c.getPeriod());
        int year = ym.getYear();
        int month = ym.getMonthValue();

        // 1) 未审凭证数：当期该公司 DRAFT 状态凭证
        Long draftVouchers = voucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getCompanyId, c.getCompanyId())
                .eq(OpVoucher::getPeriodYear, year)
                .eq(OpVoucher::getPeriodMonth, month)
                .eq(OpVoucher::getStatus, "DRAFT"));
        // 2) 未匹配三单数：当期对公付款单缺 PO 或收货单关联（三单匹配前置条件缺失）
        Long unmatchedThreeWay = expBillMapper.selectCount(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getBillType, "PAY")
                .eq(ExpBill::getPeriodYear, year)
                .eq(ExpBill::getPeriodMonth, month)
                .eq(ExpBill::getStatus, "APPROVED")
                .and(w -> w.isNull(ExpBill::getPoId).or().isNull(ExpBill::getPoReceiptId)));
        // 3) 未达账项数：当月银行对账未勾稽（OPEN）项
        Long unreconItems = bankReconItemMapper.selectCount(new LambdaQueryWrapper<BankReconItem>()
                .eq(BankReconItem::getMonth, c.getPeriod())
                .eq(BankReconItem::getStatus, "OPEN"));

        // 自动检查结果回写检查项 auto 字段：仅提示，状态仍由人工签认
        List<Map<String, Object>> items = parseItems(c.getCheckItemsJson());
        Map<String, Map<String, Object>> autoByCode = new LinkedHashMap<>();
        autoByCode.put("BIZ_CHECK", auto("未审凭证数", draftVouchers));
        autoByCode.put("OFFSET_CHECK", auto("未达账项数", unreconItems));
        autoByCode.put("RELATED_CHECK", auto("未匹配三单数", unmatchedThreeWay));
        for (Map<String, Object> item : items) {
            String code = String.valueOf(item.get("code"));
            if (autoByCode.containsKey(code)) {
                item.put("auto", autoByCode.get(code));
            } else {
                item.remove("auto");
            }
        }
        c.setCheckItemsJson(toJson(items));
        String detail = "未审凭证 " + draftVouchers + " 张 / 未匹配三单 " + unmatchedThreeWay
                + " 单 / 未达账项 " + unreconItems + " 笔";
        trace(c, "AUTO_CHECK", "前置自动检查：" + detail);
        mapper.updateById(c);

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("closeNo", c.getCloseNo());
        ret.put("detail", detail);
        ret.put("draftVouchers", draftVouchers);
        ret.put("unmatchedThreeWay", unmatchedThreeWay);
        ret.put("unreconItems", unreconItems);
        ret.put("items", items);
        return ret;
    }

    /** ERP关账：全部检查项通过方可关账；完成后恢复协同数据交互并联动会计期间结账。 */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose closePeriod(Long id) {
        ApMonthClose c = require(id);
        requireStatus(c, ApMonthClose.ST_CHECKING, "关账");
        List<Map<String, Object>> items = parseItems(c.getCheckItemsJson());
        boolean allOk = items.stream().allMatch(i -> "OK".equals(String.valueOf(i.get("status"))));
        if (!allOk) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在未完成的关账检查项，不可关账");
        }
        assertAutoCheckCleared(items);
        c.setStatus(ApMonthClose.ST_CLOSED);
        c.setScpFrozen(0);
        c.setClosedTime(LocalDateTime.now());
        c.setClosedBy(currentUser());
        mapper.updateById(c);
        // 审计：状态跃迁（检查处理中 → 已关账）
        auditTrailService.recordUpdate("AP_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ap_month_close",
                String.valueOf(c.getId()),
                Collections.singletonMap("status", ApMonthClose.ST_CHECKING),
                Collections.singletonMap("status", c.getStatus()), "总账关账完成");
        // 期间联动（改规则）：AP 月结是子账清单，不再替总账关闭会计期间。
        // 原实现此处调 periodService.closeByPeriod()，会把 md_period 直接置 CLOSED，
        // 于是随后的应收月结「过总账」与总账月结「发起」都被"期间已结账"拦死（GlMonthCloseService:276），
        // 手册既定的 AP→AR→GL 顺序在同主体上根本走不通；期间状态机的唯一归属是总账月结审批末节点。
        trace(c, "CLOSE", "总账关账完成，协同恢复；会计期间 " + c.getPeriod() + " 的结账由总账月结（GL）审批末节点联动");
        mapper.updateById(c);
        // 打通点7：关账完成 → SCP 状态通知（恢复数据交互）
        collabSyncService.log(CollabSyncLog.IF_SCP_UNFREEZE, "AP_TO_SCP", c.getCloseNo(),
                CollabSyncLog.ST_SUCCESS, "关账完成：期间 " + c.getPeriod() + " 协同恢复", null);
        log.info("[apflow] 月末关账 {} 完成，期间 {} 协同恢复（会计期间由总账月结末节点结账）", c.getCloseNo(), c.getPeriod());
        return c;
    }

    /**
     * 反关账：关账单 CLOSED → 回到检查处理中，期间重开（md_period 回 NORMAL），
     * 协同重新冻结；原因必填，全程记录（reopenTime/reopenReason + trace）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose unClose(Long id, String reason) {
        ApMonthClose c = require(id);
        if (!ApMonthClose.ST_CLOSED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单 " + c.getCloseNo() + " 未关账，无需反关账");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "反关账必须填写原因（审计记录要求）");
        }
        c.setStatus(ApMonthClose.ST_CHECKING);
        c.setScpFrozen(1); // 期间重开 → 协同重新冻结，防重开期间产生凭证
        c.setReopenTime(LocalDateTime.now());
        c.setReopenReason(reason);
        mapper.updateById(c);
        // 反关账须清位关账人/关账时间——updateById 对 null 值一律跳过，
        // 残留的 closed_time/closed_by 会让读面把"已重开"的单继续显示成"某时刻已关账"；
        // 清列走 UpdateWrapper.set(null)（历史值由 reopen_time/reopen_reason/trace_json 记录，不删行）。
        mapper.update(null, new LambdaUpdateWrapper<ApMonthClose>()
                .eq(ApMonthClose::getId, c.getId())
                .set(ApMonthClose::getClosedTime, null)
                .set(ApMonthClose::getClosedBy, null));
        // 审计：状态跃迁（已关账 → 检查处理中）
        auditTrailService.recordUpdate("AP_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ap_month_close",
                String.valueOf(c.getId()),
                Collections.singletonMap("status", ApMonthClose.ST_CLOSED),
                Collections.singletonMap("status", c.getStatus()), "反关账重开期间");
        // 期间联动（改规则）：既然本口不再关闭期间，反关账也就不再重开期间
        // （否则 AP 反关账会把总账月结已封的期间撬开，比原缺陷更危险）。
        trace(c, "REOPEN", "反关账重开检查：" + reason + "（协同重新冻结；会计期间状态不由 AP 侧改动）");
        mapper.updateById(c);
        collabSyncService.log(CollabSyncLog.IF_SCP_FREEZE, "AP_TO_SCP", c.getCloseNo(),
                CollabSyncLog.ST_SUCCESS, "反关账：期间 " + c.getPeriod() + " 重开，协同重新冻结", null);
        log.info("[apflow] 关账单 {} 反关账，期间 {} 重开：{}", c.getCloseNo(), c.getPeriod(), reason);
        return c;
    }

    /**
     * 作废（最终状态 VOID）：未关账的关账单整体废弃——置 VOID、解冻协同
     * （scpFrozen=0，不再参与 isFrozen 拦截）、全程记录不删行；已关账须先反关账，
     * 不能直接作废（期间锁只在关账完成时建立，故作废路径只需解冻协同）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApMonthClose voidBill(Long id, String reason) {
        ApMonthClose c = require(id);
        if (ApMonthClose.ST_VOID.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单 " + c.getCloseNo() + " 已作废");
        }
        if (ApMonthClose.ST_CLOSED.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单 " + c.getCloseNo() + " 已关账，须先反关账，不能直接作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因（审计记录要求）");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", c.getStatus());
        c.setStatus(ApMonthClose.ST_VOID);
        c.setScpFrozen(0); // 最终状态：解冻协同，本单不再参与 isFrozen 拦截
        trace(c, "VOID", "作废关账单：" + reason);
        mapper.updateById(c);
        // 审计：状态跃迁（未完成 → 已作废）
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditTrailService.recordUpdate("AP_MONTH_CLOSE", c.getId(), c.getCloseNo(), "ap_month_close",
                String.valueOf(c.getId()), auditBefore, auditAfter, "作废关账单");
        collabSyncService.log(CollabSyncLog.IF_SCP_UNFREEZE, "AP_TO_SCP", c.getCloseNo(),
                CollabSyncLog.ST_SUCCESS, "关账单作废：期间 " + c.getPeriod() + " 协同解冻", null);
        log.info("[apflow] 关账单 {} 作废：{}", c.getCloseNo(), reason);
        return c;
    }

    /** 协同冻结判定（供付款/对账通道拦截）：该公司当期存在未完成关账单且 scpFrozen=1。 */
    public boolean isFrozen(Long companyId, String period) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<ApMonthClose>()
                .eq(ApMonthClose::getCompanyId, companyId == null ? 1L : companyId)
                .eq(period != null, ApMonthClose::getPeriod, period)
                .eq(ApMonthClose::getScpFrozen, 1)
                .notIn(ApMonthClose::getStatus, ApMonthClose.ST_CLOSED, ApMonthClose.ST_VOID));
        return n != null && n > 0;
    }

    public List<ApMonthClose> list(Long companyId, String status) {
        LambdaQueryWrapper<ApMonthClose> q = new LambdaQueryWrapper<>();
        if (companyId != null) {
            q.eq(ApMonthClose::getCompanyId, companyId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(ApMonthClose::getStatus, status);
        }
        return mapper.selectList(q.orderByDesc(ApMonthClose::getId));
    }

    public ApMonthClose get(Long id) {
        return require(id);
    }

    // 内部

    /** 蓝图 9.5 检查项清单默认四项。 */
    static String defaultItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("BIZ_CHECK", "账务处理正确性（例外报表）"));
        items.add(item("OFFSET_CHECK", "应收应付对冲科目期末平检"));
        items.add(item("RELATED_CHECK", "关联方开票核对"));
        items.add(item("IT_CHECK", "IT处理异常"));
        return toJson(items);
    }

    private static Map<String, Object> item(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("status", "PENDING");
        return m;
    }

    private static Map<String, Object> auto(String metric, Long count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metric", metric);
        m.put("count", count == null ? 0 : count);
        m.put("ok", count == null || count == 0);
        return m;
    }

    /** 追加一条操作记录（traceJson：[{action,oper,time,detail}]，最多保留 200 条防膨胀）。 */
    private void trace(ApMonthClose c, String action, String detail) {
        List<Map<String, Object>> list;
        try {
            list = c.getTraceJson() == null || c.getTraceJson().isBlank()
                    ? new ArrayList<>()
                    : parseList(c.getTraceJson());
        } catch (Exception e) {
            // 历史记录 JSON 损坏则重建空列表，不阻断本次关账操作
            list = new ArrayList<>();
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("action", action);
        t.put("oper", currentUser());
        t.put("time", LocalDateTime.now().toString());
        t.put("detail", detail);
        list.add(t);
        if (list.size() > 200) {
            list = new ArrayList<>(list.subList(list.size() - 200, list.size()));
        }
        c.setTraceJson(toJson(list));
    }

    /** 自动检查异常项人工通过时必须写处理说明，并随检查项快照记录。 */
    private void requireOverrideNote(Map<String, Object> item, String note) {
        Object autoObj = item.get("auto");
        if (!(autoObj instanceof Map)) {
            return;
        }
        Map<?, ?> auto = (Map<?, ?>) autoObj;
        if (Boolean.TRUE.equals(auto.get("ok"))) {
            return;
        }
        if (note == null || note.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "检查项「" + item.get("name") + "」自动检查异常（" + auto.get("metric")
                            + "=" + auto.get("count") + "），通过必须填写处理说明");
        }
        item.put("overrideNote", note.trim());
        item.put("overrideTime", LocalDateTime.now().toString());
    }

    /** 关账前二次校验：自动检查必须已执行；异常项必须有处理说明（防绕过检查项直接改状态）。 */
    private void assertAutoCheckCleared(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String code = String.valueOf(item.get("code"));
            Object autoObj = item.get("auto");
            if (AUTO_ITEM_CODES.contains(code) && autoObj == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "检查项「" + item.get("name") + "」尚未执行前置自动检查，不可关账");
            }
            if (!(autoObj instanceof Map)) {
                continue;
            }
            Map<?, ?> auto = (Map<?, ?>) autoObj;
            if (Boolean.TRUE.equals(auto.get("ok"))) {
                continue;
            }
            Object overrideNote = item.get("overrideNote");
            if (overrideNote == null || String.valueOf(overrideNote).isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "检查项「" + item.get("name") + "」自动检查异常（" + auto.get("metric")
                                + "=" + auto.get("count") + "），未填写处理说明，不可关账");
            }
        }
    }

    private static String normalizeItemStatus(String s) {
        if (s == null || s.isBlank()) {
            return "OK";
        }
        switch (s.trim().toUpperCase()) {
            case "OK":
            case "REJECTED":
            case "SUSPENDED":
            case "PENDING":
                return s.trim().toUpperCase();
            case "TRUE":
            case "1":
                return "OK";
            default:
                throw new BizException(ErrorCode.PARAM_INVALID, "检查项状态非法：" + s + "（允许：通过/驳回/挂起/待办）");
        }
    }

    private static String itemStatusCn(String s) {
        switch (s) {
            case "OK": return "已通过";
            case "REJECTED": return "已退回";
            case "SUSPENDED": return "已挂起";
            default: return "待处理";
        }
    }

    private String currentUser() {
        LoginUser u = UserContext.get();
        return u == null ? null : (u.getUsername() == null ? null : u.getUsername());
    }

    static String toJson(Object o) {
        try {
            return JsonMapper.builder().build().writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON序列化失败:" + e.getMessage());
        }
    }

    private List<Map<String, Object>> parseItems(String json) {
        try {
            return JsonMapper.builder().build()
                    .readValue(json, new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "检查项数据损坏，请联系管理员");
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        try {
            return JsonMapper.builder().build()
                    .readValue(json, new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "操作记录数据损坏，请联系管理员");
        }
    }

    private ApMonthClose require(Long id) {
        ApMonthClose c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单不存在 id=" + id);
        }
        return c;
    }

    private void requireStatus(ApMonthClose c, String expected, String action) {
        if (!expected.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关账单 " + c.getCloseNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + c.getStatus());
        }
    }
}
