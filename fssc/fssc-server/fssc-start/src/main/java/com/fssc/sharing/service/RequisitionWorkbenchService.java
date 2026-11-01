// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillAttachMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.dto.ArchiveInfoDTO;
import com.fssc.framework.facade.dto.ImageFileDTO;
import com.fssc.framework.security.UserContext;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.service.MenuService;
import com.fssc.system.service.MenuService.MenuNode;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.BillRouteRegistry;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一收单工作台 Service
 * 聚合多数据源为统一视图：场景卡片 + 概览看板 + 我的单据/待办/已办 + 单据全链路详情
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequisitionWorkbenchService {

    private final ExpBillMapper expBillMapper;
    private final ExpBillAttachMapper expBillAttachMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final ImageFacade imageFacade;
    private final ArchiveFacade archiveFacade;
    private final WfTaskMapper wfTaskMapper;
    private final WfTaskHistoryMapper wfTaskHistoryMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final MenuService menuService;
    private final MyDocumentService myDocumentService;

    // 场景卡片

    /**
     * 返回当前用户可发起单据的场景卡片（按菜单权限过滤）
     * 统一收单大厅：一个页面按场景卡片发起所有单据
     */
    public List<Map<String, Object>> getScenes() {
        // 提取当前用户的菜单路径集合
        Set<String> menuPaths = extractMenuPaths();

        return List.of(
            group("费用报销", List.of(
                scene("CLAIM", "员工费用报销", "ExpenseClaim", "/expense/claim", "daily-reimbursement"),
                scene("CLAIM_TRAVEL", "差旅培训报销", "TravelClaim", "/expense/claim-travel", "travel-reimbursement")
            )),
            group("事前申请", List.of(
                scene("APPLY_MEETING", "会议申请", "MeetingApply", "/expense/apply/meeting", "meeting"),
                scene("APPLY_TRAVEL", "出差申请", "TravelApply", "/expense/apply/travel", "business-trip"),
                scene("APPLY_TRAIN", "培训申请", "TrainApply", "/expense/apply/train", "training")
            )),
            group("资金收付", List.of(
                scene("LOAN", "员工借款", "LoanApply", "/expense/loan", "loan"),
                scene("REPAY", "还款", "RepayApply", "/expense/repay", "repayment"),
                scene("PAY", "对公付款", "CorpPay", "/corp-pay", "payment"),
                scene("FUND_PLAN", "资金计划", "FundPlan", "/treasury", "fund-plan"),
                scene("TRANSFER", "资金调拨", "Transfer", "/treasury", "transfer")
            )),
            group("采购应付", List.of(
                scene("PO_ORDER", "采购订单", "PoOrder", "/purchase/order", "purchase-order"),
                scene("PO_RECEIPT", "收货单", "PoReceipt", "/purchase/receipt", "receipt"),
                scene("CONTRACT", "合同录入", "Contract", "/contract", "contract")
            )),
            group("应收收入", List.of(
                scene("INVOICE_ISSUE", "发票开具", "InvoiceIssue", "/invoice", "invoice"),
                scene("RECEIVABLE", "应收确认", "Receivable", "/receivable", "receivable")
            )),
            group("资产存货", List.of(
                scene("ASSET", "资产卡片", "AssetCard", "/asset", "asset"),
                scene("DEPR_BATCH", "折旧计提", "DeprBatch", "/asset", "depreciation")
            ))
        ).stream()
         .map(g -> {
             @SuppressWarnings("unchecked")
             List<Map<String, Object>> items = (List<Map<String, Object>>) g.get("items");
             List<Map<String, Object>> filtered = items.stream()
                 .filter(item -> {
                     String route = (String) item.get("route");
                     return menuPaths.contains(route) || isChildOfMenuPath(route, menuPaths);
                 })
                 .collect(Collectors.toList());
             Map<String, Object> fg = new LinkedHashMap<>(g);
             fg.put("items", filtered);
             return fg;
         })
         .filter(g -> !((List<?>) g.get("items")).isEmpty())
         .collect(Collectors.toList());
    }

    /** 从 MenuService 提取当前用户的菜单路径集合 */
    private Set<String> extractMenuPaths() {
        var user = UserContext.get();
        if (user == null) return Set.of();
        Set<String> paths = new HashSet<>();
        try {
            var tree = menuService.myTree(user);
            walkPaths(tree, paths);
        } catch (Exception e) {
            log.warn("getScenes: 菜单树查询失败，返回空路径集", e);
        }
        return paths;
    }

    /** 递归提取菜单树中的所有 path */
    private void walkPaths(List<?> nodes, Set<String> paths) {
        if (nodes == null) return;
        for (Object node : nodes) {
            if (!(node instanceof MenuNode)) continue;
            MenuNode n = (MenuNode) node;
            String path = n.getPath();
            if (path != null && !path.isBlank()) {
                paths.add(path.startsWith("/") ? path : "/" + path);
            }
            walkPaths(n.getChildren(), paths);
        }
    }

    /** 判断 route 是否是某个菜单路径的子路径（处理 /expense/apply/meeting 这类深链） */
    private boolean isChildOfMenuPath(String route, Set<String> menuPaths) {
        for (String p : menuPaths) {
            if (p.equals(route) || p.startsWith(route + "/")) return true;
        }
        return false;
    }

    private Map<String, Object> group(String label, List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", label);
        m.put("items", items);
        return m;
    }

    private Map<String, Object> scene(String type, String label, String component, String route, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docType", type);
        m.put("label", label);
        m.put("component", component);
        m.put("route", route);
        m.put("icon", icon);
        return m;
    }

    // 概览看板

    public Map<String, Object> getDashboard() {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();

        // 我的待办数
        long todoCount = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .and(w -> w.eq(WfTask::getAssigneeId, userId)
                        .or().like(WfTask::getCandidateIds, "," + userId + ",")));

        // 我发起的单据总数（与「我的单据」列表同一规则：MyDocumentService.myDocs().total）
        // 原先按 wf_instance RUNNING 计数，导致卡片数字 0 而列表有 N 条的矛盾（用户感知"权限对不上"）。
        // 全类型 ownerCol 计数，数字与列表 total 一致。
        long mineCount;
        if (userId != null) {
            try {
                var pageQuery = new PageQuery();
                pageQuery.setPageNum(1);
                pageQuery.setPageSize(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> docsResult = myDocumentService.myDocs(pageQuery, null, null);
                mineCount = ((Number) docsResult.getOrDefault("total", 0L)).longValue();
            } catch (Exception e) {
                log.warn("getDashboard: myDocs 查询失败，回退到 wf_instance 计数", e);
                mineCount = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getStartUserId, userId)
                        .eq(WfInstance::getStatus, "RUNNING"));
            }
        } else {
            mineCount = 0;
        }

        // 我的已办——与「我的已办」tab 同一规则（wf_task_history 由我办理），原先按
        // wf_instance.status=COMPLETED 过滤，而该取值全库 0 行（最终状态实际写 APPROVED/REJECTED/CANCELLED）
        // ⇒ 卡片恒为 0，和 tab 里的列表自相矛盾。
        // 最终状态收尾行也带 oper_user_id（"谁的最后一票定论"），但它不是"我办了这一节"，
        // 计入就是把同一节数两遍（作废行的处理同理），故与 tab 一并排除。
        long doneCount = wfTaskHistoryMapper.selectCount(new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getOperUserId, userId)
                .notIn(WfTaskHistory::getAction, WfQueryService.TERMINAL_ACTIONS));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todo", todoCount);
        result.put("mine", mineCount);
        result.put("done", doneCount);
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    // 我的单据（费用域 exp_bill 一表）

    /**
     * 旧规则：只查 exp_bill（10+ 种 bill_type 都在这一张表，但合同/采购/预算/总账都不在）。
     * 全类型见 {@code MyDocumentService.myDocs()}；本方法保留一个版本给旧入口。
     */
    public PageResult<Map<String, Object>> myBills(PageQuery query, String billType, String status) {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();

        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getApplyUserId, userId)
                .orderByDesc(ExpBill::getId);

        if (billType != null && !billType.isBlank()) {
            qw.eq(ExpBill::getBillType, billType);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(ExpBill::getStatus, status);
        }

        Page<ExpBill> p =
                expBillMapper.selectPage(Page.of(
                        query.getPageNum(), query.getPageSize()), qw);

        List<Map<String, Object>> list = new ArrayList<>();
        for (ExpBill bill : p.getRecords()) {
            list.add(billToMap(bill));
        }
        return PageResult.of(list, p.getTotal(), query.getPageNum(), query.getPageSize());
    }

    private Map<String, Object> billToMap(ExpBill bill) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", bill.getId());
        m.put("billNo", bill.getBillNo());
        m.put("billType", bill.getBillType());
        m.put("billSubType", bill.getBillSubType());
        m.put("title", bill.getTitle());
        m.put("status", bill.getStatus());
        m.put("totalAmount", bill.getTotalAmount());
        m.put("applyDate", bill.getApplyDate());
        m.put("applyUserName", bill.getApplyUserName());
        m.put("companyId", bill.getCompanyId());
        m.put("deptId", bill.getDeptId());
        return m;
    }

    // 我的待办

    public PageResult<Map<String, Object>> myTodo(PageQuery query) {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();

        LambdaQueryWrapper<WfTask> qw = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .and(w -> w.eq(WfTask::getAssigneeId, userId)
                        .or().like(WfTask::getCandidateIds, "," + userId + ","))
                .orderByDesc(WfTask::getId);

        Page<WfTask> p =
                wfTaskMapper.selectPage(Page.of(
                        query.getPageNum(), query.getPageSize()), qw);

        List<Map<String, Object>> list = new ArrayList<>();
        for (WfTask task : p.getRecords()) {
            list.add(taskToMap(task));
        }
        return PageResult.of(list, p.getTotal(), query.getPageNum(), query.getPageSize());
    }

    private Map<String, Object> taskToMap(WfTask task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getTaskId());
        m.put("id", task.getId());
        m.put("procInstId", task.getProcInstId());
        m.put("bizType", task.getBizType());
        m.put("bizNo", task.getBizNo());
        m.put("bizId", task.getBizId());
        m.put("title", task.getTitle());
        m.put("taskName", task.getNodeName());
        m.put("status", task.getStatus());
        m.put("createTime", task.getCreateTime());
        return m;
    }

    // 我的已办

    public PageResult<Map<String, Object>> myDone(PageQuery query) {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();

        LambdaQueryWrapper<WfTaskHistory> qw = new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getOperUserId, userId)
                // 与上方卡片同一规则——最终状态收尾行不算"我办理的某一节"
                .notIn(WfTaskHistory::getAction, WfQueryService.TERMINAL_ACTIONS)
                .orderByDesc(WfTaskHistory::getId);

        Page<WfTaskHistory> p =
                wfTaskHistoryMapper.selectPage(Page.of(
                        query.getPageNum(), query.getPageSize()), qw);

        List<Map<String, Object>> list = new ArrayList<>();
        for (WfTaskHistory h : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("procInstId", h.getProcInstId());
            m.put("bizType", h.getBizType());
            m.put("bizId", h.getBizId());
            m.put("taskName", h.getNodeName());
            m.put("action", h.getAction());
            m.put("comment", h.getComment());
            m.put("operUserName", h.getOperUserName());
            m.put("handleTime", h.getOperTime());
            list.add(m);
        }
        return PageResult.of(list, p.getTotal(), query.getPageNum(), query.getPageSize());
    }

    // 单据全链路详情

    /**
     * 聚合单据全链路：单据基础 + 流程轨迹 + 凭证 + 付款 + 影像 + 档案
     * 参考方案 "单据详情内联展示流程轨迹 + 凭证 + 付款 + 影像 + 档案"
     *
     * <p>本方法按 <b>exp_bill 主键</b>查询。"我的单据"扩到全类型后，各域 id 空间互不相干
     * （合同 id=7 与报销单 id=7 同时存在），故只有<b>明确是费用域</b>的类型才放行——
     * 类型缺失或非费用域一律拒绝：回一份同主键的报销单详情，比回空更危险。
     * 影像同样按 bizId 查（{@code img_file} 无类型维度），跨域时一并给空而不是给别人的影像。
     */
    public Map<String, Object> getBillDetail(Long billId, String bizType) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean known = bizType != null && !bizType.isBlank();
        boolean expenseFamily = known && BillRouteRegistry.isInlinedExpense(BillRouteRegistry.of(bizType));
        if (!expenseFamily) {
            result.put("bill", null);
            result.put("images", Collections.emptyList());
            result.put("imageCount", 0);
            result.put("error", (known ? "「" + bizType + "」不是费用域单据" : "未指明单据类型")
                    + "，本详情接口按报销单(exp_bill)主键查询（各域 id 空间互不相干，跨域同 id 会撞号）"
                    + "⇒ 请到该域的台账页查看");
            return result;
        }

        // 1. 单据基础信息
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            // 单据主档缺失（测试脏数据/业务行被清理等）：不阻断影像/档案查看，
            // 仍按 bizId 尽力返回已关联的影像与档案，并给可读提示而非硬错误。
            result.put("bill", null);
            result.put("billDetail", null);
            result.put("wfInstance", null);
            result.put("voucher", null);
            result.put("voucherLines", Collections.emptyList());
            result.put("payments", Collections.emptyList());
            List<ImageFileDTO> orphanImages = imageFacade.findImageFilesByBizId(billId);
            result.put("images", orphanImages);
            result.put("imageCount", orphanImages.size());
            result.put("archive", archiveFacade.findLatestByRefId(billId));
            result.put("error", "单据主档不存在或已被清理（billId=" + billId
                    + "），仅展示已关联的影像/档案，业务详情不可用");
            return result;
        }
        result.put("bill", billToMap(bill));
        result.put("billDetail", bill);

        // 2. 流程轨迹（WfInstance by bizNo = billNo）
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizNo, bill.getBillNo())
                .orderByDesc(WfInstance::getId)
                .last("LIMIT 1"));
        result.put("wfInstance", instance);

        // 3. 凭证（OpVoucher by bill_id）
        //    bill_id 是跨域裸主键：批次出纳凭证（TREASURY_PAY/CIP_PAY_BATCH）与各域凭证都写自己的主键，
        //    只按 bill_id 取会把同号他域凭证挂到费用单上，必须限定费用族来源。
        OpVoucher voucher = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, billId)
                .in(OpVoucher::getSourceType, VoucherService.EXPENSE_BILL_SOURCES)
                .orderByDesc(OpVoucher::getId)
                .last("LIMIT 1"));
        if (voucher != null) {
            List<OpVoucherLine> lines = opVoucherLineMapper.selectByVoucherId(voucher.getId());
            result.put("voucher", voucher);
            result.put("voucherLines", lines);
        } else {
            result.put("voucher", null);
            result.put("voucherLines", Collections.emptyList());
        }

        // 4. 付款记录（ExpBillPayment by bill_id）
        List<ExpBillPayment> payments = expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, billId)
                .orderByAsc(ExpBillPayment::getLineNo));
        result.put("payments", payments);

        // 5. 影像/附件：影像工作台批次（img_file）+ 报销页上传（exp_bill_attach）两个来源合并，
        //    按 fileId 去重。单据头 image_status 由 exp_bill_attach 计数得出，抽屉若只读 img_file
        //    会出现「image_status=COMPLETE 但附件数 0」的矛盾。
        List<ImageFileDTO> images = new ArrayList<>(imageFacade.findImageFilesByBizId(billId));
        Set<Long> seenFileIds = images.stream()
                .map(ImageFileDTO::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        List<ExpBillAttach> attaches = expBillAttachMapper.selectList(new LambdaQueryWrapper<ExpBillAttach>()
                .eq(ExpBillAttach::getBillId, billId)
                .orderByAsc(ExpBillAttach::getSort)
                .orderByAsc(ExpBillAttach::getId));
        for (ExpBillAttach attach : attaches) {
            if (attach.getFileId() == null || !seenFileIds.add(attach.getFileId())) continue;
            ImageFileDTO dto = new ImageFileDTO();
            dto.setFileId(attach.getFileId());
            dto.setBizId(billId);
            dto.setBizType("EXPENSE_ATTACH");
            dto.setPageType(attach.getAttachType());
            dto.setPageNo(attach.getSort());
            dto.setCreateTime(attach.getCreateTime());
            images.add(dto);
        }
        result.put("images", images);
        result.put("imageCount", images.size());

        // 6. 档案（断点5：通过 ArchiveFacade 查询，不再直接依赖 OpArchiveMapper）
        ArchiveInfoDTO archive = archiveFacade.findLatestByRefId(billId);
        result.put("archive", archive);

        return result;
    }
}
