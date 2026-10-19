// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.framework.facade.dto.ArchiveInfoDTO;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.BillRouteRegistry;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 单据全链路联查（P2-1：单据→审批→凭证→付款→批次→档案 一站式追溯）。
 * <p>
 * 一次请求聚合六段链路，供前端 BillTraceDrawer 渲染业务链时间线：
 * <ul>
 *   <li>bill：单据摘要（费用域＝exp_bill 整行；其它域＝wf_instance 头信息）</li>
 *   <li>payments：exp_bill_payment 付款记录（bill_id，仅费用域）</li>
 *   <li>batches：op_pay_batch 付款批次（费用域经 payment.pay_batch_no；他域经 D-TRC-05 回溯列）</li>
 *   <li>vouchers：op_voucher 记账凭证 + 每张凭证分录行</li>
 *   <li>archives：op_archive 电子档案</li>
 *   <li>traces：wf_task_history 审批轨迹（含提单/驳回/转办/加签/催办/撤销，多轮次全含）</li>
 * </ul>
 *
 * <p><b>【跨域串号防护（关键）】</b>本接口的 billId 语义<b>随 bizType 而变</b>：
 * 调用方 BillTraceDrawer 来自 50+ 个业务页，各页传的是<b>本域业务表自增主键</b>
 * （发票传 inv_invoice.id、资本性支出传 fa_cer_request.id…），这些 id 空间互不相干。
 * 若不加校验直接按主键关联，会产生两类事故：
 * <ol>
 *   <li>查到<b>另一张无关单据</b>（同 id 不同单）→ 头部编号/金额/状态全是别人的；</li>
 *   <li>下游 voucher/archive 按裸 id 关联，撞出<b>完全无关的单据</b>
 *       （实证：资本性支出单 CER1789602519664 → id=22 → 显示 GL 手工凭证 JZ2026091700022）；
 *       叠加抽屉另一路详情接口被「仅限本人单据」守卫拦截导致 billInfo 为空，
 *       就出现「提单灰、审批通过灰、生成凭证绿」这种看似跳过环节的错乱时间线。</li>
 * </ol>
 * 因此分派口径为（D-TRC-06 乙：按单据域分派锚点，复用 {@link BillRouteRegistry} 而不是继续特判费用域）：
 * <ul>
 *   <li>声明了 {@code bizType} 且<b>不是</b>费用域 ⇒ 只走该域锚点（{@code wf_instance(bizType,bizId)}），
 *       <b>绝不回落 exp_bill</b>；</li>
 *   <li><b>未声明</b>（D-DEC-152 收口）⇒ 先用 {@code wf_instance.biz_type}＋{@code wf_task_history.biz_type}
 *       解析这张主键<b>实际所属的域</b>，再按域取数：唯一域且非费用 ⇒ 走该域锚点；
 *       同号多域且跨域 ⇒ {@code NOT_SUPPORTED} 并列出候选域（宁缺勿错）；
 *       解析不出域（尚未起流的草稿）才按 {@code exp_bill} 主键取数——<b>任何情况下都不拿裸 biz_id
 *       去 exp_bill 撞号再说成"这单批完了"</b>；</li>
 *   <li>两路都查不到本域锚点 ⇒ {@code scope=NOT_SUPPORTED} + 全空集合，<b>宁缺勿错</b>，
 *       绝不用其它单据的数据填补，也不按数字大小/前缀猜域。</li>
 * </ul>
 *
 * <p><b>【"已批"以单据主流程实例为准（D-DEC-060）】</b>同一 biz_id 可挂多枚实例（报销主流程＋三单匹配派单链），
 * 此前摘要取 {@code id} 最大的那一枚 ⇒「派单链跑完」被显示成「这单已批」，而主流程真实卡点从响应里丢失。
 * 现在摘要一律锚在<b>主流程实例</b>（{@code exp_bill.proc_inst_id} 回写列 → 单据自身类型 → 排除派单类），
 * 派单/子流程实例另行列在 {@code summary.subInstances}（每枚带 {@code subChain} 标其是否派单类），
 * 两条事实并存而不是互相顶替。
 *
 * <p><b>【审批通过环节权威判定】</b>时间线 6 个环节必须同源。本接口此前只给出
 * BILL/PAYMENT/BATCH/VOUCHER/ARCHIVE，唯独缺 APPROVED，前端只能拿另一个接口
 * （/expense/bill/detail，且被 requireMine 守卫拦截）的状态去补第 2 个环节，
 * 两条数据源一断就错位。这里补齐 APPROVED，三档证据取或（负面状态一票否决），
 * 前端只认 summary.reached，实现单一数据源。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/expense/bill-trace-chain")
public class BillTraceChainController {

    // ---------------- 审批通过判定常量 ----------------
    /** 单据状态：走到这一步即认定「审批通过」已达（含费用/借款/还款各域末态） */
    private static final Set<String> STATUS_APPROVED = Set.of(
            "APPROVED", "PAID", "POSTED", "ARCHIVED", "COMPLETED", "REPAID", "PARTIAL_REPAID", "CLOSED");
    /** 负面末态：显式驳回/撤回/作废，优先级高于「下游已发生」的推断 */
    private static final Set<String> STATUS_NEGATIVE = Set.of(
            "REJECTED", "WITHDRAWN", "VOID", "TERMINATED", "CANCELLED");
    /** 流程实例终态：已审批/已完成 */
    private static final Set<String> INST_APPROVED = Set.of("APPROVED", "COMPLETED");
    /**
     * D-DEC-446 甲-2：费用域内「付款/组批」段的载体适用性。
     * 报销(CLAIM)与付款(PAY)有 exp_bill_payment 腿；借款/还款的拨付与收款在真跑中实测零载体
     * （余额于提交期扣减），费用申请单(APPLY)后续才付款 ⇒ 这三型不画该段，
     * 否则时间线把「本域无此环节」显示成「这一步没做」。未知类型按适用处理（不因显示层收窄数据）。
     */
    private static final Set<String> PAY_SEGMENT_TYPES = Set.of("CLAIM", "PAY");
    /**
     * 费用域凭证来源白名单：防止把其它域（GL 手工凭证 GL_ACCRUAL / 资产 FA_* / 应付 AP / 应收 AR 等）
     * 按裸 bill_id、source_doc_id 撞进本单链路。
     */
    private static final Set<String> VOUCHER_SOURCE_EXPENSE = Set.of(
            "EXPENSE", "EXPENSE_PAY", "EXP_LOAN", "EXP_REPAY", "EXPENSE_REIM", "EXPENSE_LOAN", "EXPENSE_REPAY");
    /** 批次「单据即批次」域：TR_PAY 的 bizId 就是 op_pay_batch.id，不是某条流程的回溯列 */
    private static final Set<String> BATCH_IS_BILL = Set.of("TR_PAY", "TR_PAY_FAIL");
    /** 单次返回的轨迹行数上限（多轮次全含，防御性截断） */
    private static final int TRACE_LIMIT = 500;

    private final ExpBillMapper expBillMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    /** D-TRC-06 / RA-TRC-03 乙：状态轨迹直读 wf_task_history；exp_bill_trace 全库无写入点、已标注废弃 */
    private final WfTaskHistoryMapper wfTaskHistoryMapper;
    private final WfTaskMapper wfTaskMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final ArchiveFacade archiveFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    /** D-TRC-06：非费用域锚点的公司归属解析（与取人侧同源，见 NodeOrgResolver.companyIdOfBill） */
    private final NodeOrgResolver nodeOrgResolver;

    @RequiresPerm("exp:bill:list")
    @OperLog(module = "EXPENSE", operType = "QUERY", desc = "单据全链路联查")
    @GetMapping("/{billId}")
    public Result<Map<String, Object>> chain(@PathVariable Long billId,
                                             @RequestParam(value = "bizType", required = false) String bizType) {
        String declared = bizType == null || bizType.isBlank() ? null : bizType.trim();

        // ============ 0) 锚点分派：声明了他域类型就只走他域，绝不回落 exp_bill ============
        if (declared != null && !isExpenseAnchor(declared)) {
            return Result.success(genericChain(declared, billId));
        }

        // ============ 0′) 未声明域：先解析这张主键真实所属的域，再决定锚点（D-DEC-152） ============
        // 判据与 BillRouteRegistry / 凭证来源列（D-DEC-244）同族：域只能来自库里的事实
        // （wf_instance.biz_type、wf_task_history.biz_type），不能来自"这个数字在 exp_bill 里查得到"。
        if (declared == null) {
            Set<String> resolved = resolvedDomainsOf(billId);
            if (resolved.size() == 1) {
                String only = resolved.iterator().next();
                if (!isExpenseAnchor(only)) {
                    return Result.success(genericChain(only, billId));
                }
            } else if (resolved.size() > 1 && resolved.stream().anyMatch(t -> !isExpenseAnchor(t))) {
                return Result.success(unsupported(null,
                        "该主键同时属于多个单据域（" + String.join("、", resolved)
                        + "），无法从数字判断要看哪一张。为避免跨域串号不回落任何域取数——请从业务页的「追溯」入口进入"
                        + "（入口会声明 bizType），或为本页的追溯入口补传单据域。"));
            }
        }

        // ============ 1) 费用域锚点（未起流的草稿单据只能按 exp_bill 主键解释＝历史行为） ============
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            return Result.success(unsupported(declared,
                    "exp_bill 中不存在该主键，且未声明可路由的单据域（bizType）。为避免跨域串号，本接口不按数字猜域。"));
        }
        // D-TRC-04：本接口把凭证分录、付款账号、档案一次性吐出，此前只有 exp:bill:list 权限闸、
        // 无归属闸 → 同租户内任何人换 billId 即可读他公司/他人单据全链。改用与详情接口同源的可见性守卫。
        orgGuard.assertVisible(bill.getCompanyId(), bill.getApplyUserId());

        // 流程实例＝本单的多轮审批。未声明时只认费用域 biz_type，避免同 id 的他域流程混进轨迹与判定。
        Predicate<String> domain = t -> isExpenseAnchor(t) || (declared != null && declared.equals(t));
        List<WfInstance> instances = instancesOf(billId, domain);

        Map<String, Object> out = new LinkedHashMap<>();

        // 1) 单据摘要
        out.put("bill", bill);

        // 2) 付款记录
        List<ExpBillPayment> payments = expBillPaymentMapper.selectList(
                new LambdaQueryWrapper<ExpBillPayment>().eq(ExpBillPayment::getBillId, billId)
                        .orderByAsc(ExpBillPayment::getLineNo));
        out.put("payments", payments);

        // 3) 付款批次（按 pay_batch_no 去重）
        List<String> batchNos = payments.stream()
                .map(ExpBillPayment::getPayBatchNo)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();
        List<OpPayBatch> batches = batchNos.isEmpty() ? List.of()
                : opPayBatchMapper.selectList(new LambdaQueryWrapper<OpPayBatch>()
                        .in(OpPayBatch::getBatchNo, batchNos));
        out.put("batches", batches);

        // 4) 凭证（bill_id 老字段 + source_doc_id 标准化字段 + D-TRC-05 回溯列 三口取并）
        //    限定费用域来源类型：同 id 的 GL/FA/AP/AR 凭证属其它业务域，不得混入本单链路
        List<String> pis = procInstIds(instances);
        List<OpVoucher> vouchers = opVoucherMapper.selectList(
                new LambdaQueryWrapper<OpVoucher>()
                        .in(OpVoucher::getSourceType, VOUCHER_SOURCE_EXPENSE)
                        .and(w -> {
                            w.eq(OpVoucher::getBillId, billId).or().eq(OpVoucher::getSourceDocId, billId);
                            if (!pis.isEmpty()) {
                                w.or().in(OpVoucher::getProcInstId, pis);
                            }
                        })
                        .orderByAsc(OpVoucher::getId));
        Map<Long, List<OpVoucherLine>> voucherLines = new LinkedHashMap<>();
        for (OpVoucher v : vouchers) {
            voucherLines.put(v.getId(), opVoucherLineMapper.selectByVoucherId(v.getId()));
        }
        out.put("vouchers", vouchers);
        out.put("voucherLines", voucherLines);

        // 5) 电子档案（bill_id 直挂 + D-TRC-05 回溯列；经 ArchiveFacade，不直接依赖 OpArchiveMapper）
        List<ArchiveInfoDTO> archives = mergeArchives(archiveFacade.findByBillId(billId),
                archiveFacade.findByProcInstIds(pis));
        out.put("archives", archives);

        // 6) 审批轨迹（wf_task_history，时间正序，含全部轮次）
        List<WfTaskHistory> traces = tracesOf(billId, domain);
        out.put("traces", traces);

        out.put("summary", expenseSummary(bill, instances, payments, batches, vouchers, archives, traces));
        return Result.success(out);
    }

    // ---------------- 非费用域锚点（D-TRC-06） ----------------

    /**
     * 他域单据全链路：锚点＝{@code wf_instance(biz_type,biz_id)}（租户拦截器天然过滤 tenant_id），
     * 下游凭证/批次/档案一律经 D-TRC-05 的 {@code proc_inst_id} 回溯列或
     * {@code source_doc_type + source_doc_id} 成对精确匹配关联——<b>任何一段都不按裸主键跨域关联</b>。
     * 归属校验与费用域同源（TRC-B 口径），否则扩面即放大 D-TRC-04。
     */
    private Map<String, Object> genericChain(String declared, Long bizId) {
        List<WfInstance> instances = instancesOf(bizId, declared::equals);
        String domain = declared;
        if (instances.isEmpty()) {
            // 声明域落空 ⇒ 只在「同一张单据表的兄弟域」里再认一次（D-DEC-148 的发放半、D-DEC-239 同主键双轮）：
            // 薪酬计提/发放共用 gl_salary_run 同一行、调拨/上存共用 tr_transfer 同一张表，业务页的列表行
            // 没有"本轮流程是哪条链"的视角，只能声明其中一个。纠正面由 BillRouteRegistry 按 fillTable 分组，
            // 跨表撞号（152 的 bank_reconcile.id ↔ exp_bill.id）不在这个族内 ⇒ 不构成串号通道。
            for (String sibling : BillRouteRegistry.sameTableDomains(declared)) {
                if (sibling.equals(declared)) continue;
                List<WfInstance> hit = instancesOf(bizId, sibling::equals);
                if (!hit.isEmpty()) {
                    instances = hit;
                    domain = sibling;
                    break;
                }
            }
        }
        if (instances.isEmpty()) {
            // D-DEC-334 后端锚点半：无审批流、只以（source_doc_type, 本域主键）配对存在的域
            // （PO_RECEIPT——wf_instance biz_type 普查 36 型没有它，而 op_voucher 5 行在册）⇒
            // 通用链"实例锚点必空"≠"此单无凭证"，按配对直读凭证/档案段，不再回 NOT_SUPPORTED。
            if (BillRouteRegistry.isNoFlowVoucherDomain(declared)) {
                return pairOnlyChain(declared, bizId);
            }
            return unsupported(declared, "该单据在本域没有流程实例（未发起，或传入主键不属于 " + declared + " 域）。"
                    + "为避免跨域串号，不回落其它域取数。");
        }
        assertGenericVisible(domain, bizId, instances);
        // 实际命中域（＝声明域，或纠正后的同表兄弟域）；下游各段一律按它取数，声明值只在摘要里如实留痕。
        final String bizType = domain;

        WfInstance latest = instances.get(instances.size() - 1);
        List<String> pis = procInstIds(instances);
        Map<String, Object> out = new LinkedHashMap<>();

        // 1) 单据头：他域没有统一实体，用流程实例视图里已有的单号/标题/金额/状态，不猜业务表
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", bizId);
        head.put("billType", domain);
        head.put("declaredBizType", declared);
        head.put("billNo", latest.getBizNo());
        head.put("title", latest.getTitle());
        head.put("totalAmount", latest.getAmount());
        head.put("status", latest.getStatus());
        head.put("procInstId", latest.getProcInstId());
        head.put("submitTime", latest.getStartTime());
        out.put("bill", head);

        // 2) 付款记录：exp_bill_payment 是费用域专属表，他域不按 bill_id 借用 ⇒ 空段
        out.put("payments", List.of());

        // 3) 付款批次：① 本域流程回溯到的批次；② TR_PAY 系「单据即批次」，bizId 就是批次主键
        Map<Long, OpPayBatch> batchById = new LinkedHashMap<>();
        if (!pis.isEmpty()) {
            for (OpPayBatch b : opPayBatchMapper.selectList(
                    new LambdaQueryWrapper<OpPayBatch>().in(OpPayBatch::getProcInstId, pis))) {
                batchById.put(b.getId(), b);
            }
        }
        if (BATCH_IS_BILL.contains(bizType)) {
            for (OpPayBatch b : opPayBatchMapper.selectBatchIds(List.of(bizId))) {
                batchById.putIfAbsent(b.getId(), b);
            }
        }
        List<OpPayBatch> batches = new ArrayList<>(batchById.values());
        out.put("batches", batches);

        // 4) 凭证：source_doc_type + source_doc_id 成对精确匹配，或 D-TRC-05 回溯列命中本单流程。
        //    D-DEC-326/D-DEC-430 甲案读侧半：bizType 与凭证写侧域名可能不同名
        //    （CONTRACT_APPROVE vs CONTRACT／FA_DEPRECIATION vs ASSET_DEPRECIATION，现库凭证 7/34 实证）⇒
        //    成对匹配并入 BillRouteRegistry 登记的写侧别名，消灭"凭证在册、页面 0 张"的假灰。
        //    仍按（域, 本域主键）配对＋回溯臂，不引入裸主键跨域关联。
        java.util.Set<String> voucherDomains = com.fssc.workflow.service.BillRouteRegistry.voucherSourceDomainsOf(bizType);
        List<OpVoucher> vouchers = opVoucherMapper.selectList(
                new LambdaQueryWrapper<OpVoucher>()
                        .and(w -> {
                            w.in(OpVoucher::getSourceDocType, voucherDomains).eq(OpVoucher::getSourceDocId, bizId);
                            if (!pis.isEmpty()) {
                                w.or().in(OpVoucher::getProcInstId, pis);
                            }
                        })
                        .orderByAsc(OpVoucher::getId));
        Map<Long, List<OpVoucherLine>> voucherLines = new LinkedHashMap<>();
        for (OpVoucher v : vouchers) {
            voucherLines.put(v.getId(), opVoucherLineMapper.selectByVoucherId(v.getId()));
        }
        out.put("vouchers", vouchers);
        out.put("voucherLines", voucherLines);

        // 5) 档案：只认 proc_inst_id（op_archive.bill_id / ref_id 是跨域裸主键，他域借用即串号）。
        //    例外＝「单据即档案」域（FM_ARCHIVE 的 bizId 就是 op_archive.id）：op_archive.proc_inst_id 存的是
        //    被归档那张单的审批流程，不是归档审批自己的流程实例 ⇒ 单靠回溯必然 0 命中（D-DEC-240）。
        //    按本域主键补上这一行，与 BATCH_IS_BILL 同口径，不引入跨域关联。
        List<ArchiveInfoDTO> archives = new ArrayList<>(archiveFacade.findByProcInstIds(pis));
        if (isArchiveAnchor(bizType)) {
            ArchiveInfoDTO self = archiveFacade.findById(bizId);
            if (self != null && self.getId() != null
                    && archives.stream().noneMatch(x -> self.getId().equals(x.getId()))) {
                archives.add(self);
                archives.sort(java.util.Comparator.comparing(ArchiveInfoDTO::getId));
            }
        }
        out.put("archives", archives);

        // 6) 审批轨迹
        List<WfTaskHistory> traces = tracesOf(bizId, bizType::equals);
        out.put("traces", traces);

        Map<String, Object> summary =
                genericSummary(bizType, instances, latest, batches, vouchers, archives, traces);
        // 声明域 vs 实际命中域如实透出：入口页（如薪酬列表）只能声明其中一个时，
        // 前端可据此提示"本单实际走的是 XX 链"，而不是让人以为另一条链不存在。
        summary.put("declaredBizType", declared);
        summary.put("bizTypeCorrected", !declared.equals(bizType));
        out.put("summary", summary);
        return out;
    }

    /**
     * D-DEC-334：无审批流域的配对链（PO_RECEIPT 首用）。
     * 段集如实收窄＝单据头（按凭证回显）＋凭证＋分录＋档案；审批轨迹/付款/批次结构性无腿，
     * 给空数组而不是编造。归属闸不 fail-open：能解析公司就走 OrgGuard，解析不出收紧为集团岗。
     */
    private Map<String, Object> pairOnlyChain(String domain, Long bizId) {
        List<OpVoucher> vouchers = opVoucherMapper.selectList(
                new LambdaQueryWrapper<OpVoucher>()
                        .eq(OpVoucher::getSourceDocType, domain)
                        .eq(OpVoucher::getSourceDocId, bizId)
                        .orderByAsc(OpVoucher::getId));
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", bizId);
        head.put("billType", domain);
        OpVoucher first = vouchers.isEmpty() ? null : vouchers.get(0);
        head.put("billNo", first != null && first.getSourceBillNo() != null ? first.getSourceBillNo() : ("#" + bizId));
        head.put("title", first == null ? null : first.getSummary());
        head.put("totalAmount", first == null ? null : first.getDebitTotal());
        head.put("status", first == null ? null : first.getStatus());
        out.put("bill", head);
        out.put("payments", List.of());
        out.put("batches", List.of());
        Map<Long, List<OpVoucherLine>> voucherLines = new LinkedHashMap<>();
        for (OpVoucher v : vouchers) {
            voucherLines.put(v.getId(), opVoucherLineMapper.selectByVoucherId(v.getId()));
        }
        out.put("vouchers", vouchers);
        out.put("voucherLines", voucherLines);
        out.put("archives", List.of());
        out.put("traces", List.of());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", domain);
        summary.put("declaredBizType", domain);
        summary.put("bizTypeCorrected", false);
        summary.put("billMatched", !vouchers.isEmpty());
        summary.put("approved", false);
        summary.put("noApprovalFlow", true);
        summary.put("note", "本域没有审批流（收货由采购订单审批通过后下推产生），联查只给凭证/档案段；审批轨迹结构性无腿。");
        out.put("summary", summary);
        Long company = vouchers.stream().map(OpVoucher::getCompanyId).filter(Objects::nonNull).findFirst().orElse(null);
        if (company != null) {
            orgGuard.assertVisible(company, null);
        } else {
            LoginUser me = UserContext.get();
            boolean groupLevel = me != null && (me.getCompanyId() == null
                    || (me.getDataScope() != null && me.getDataScope() == 1));
            if (!groupLevel) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "无权查看该单据的全链路：" + domain + " 域没有公司归属维度，仅集团岗可见");
            }
        }
        return out;
    }

    /**
     * 他域归属校验（TRC-B 同口径）：能解析出公司 ⇒ 走 OrgGuard（本公司/本人单据/集团岗放行）；
     * 解析不出公司（该域表既无 company_id 也无 create_by 可回溯，见 D-B3-06）⇒ 收紧为
     * 「集团级或本单流程参与人可见」，而不是 fail-open 把 58 个视图全放开。
     */
    private void assertGenericVisible(String bizType, Long bizId, List<WfInstance> instances) {
        Long company = nodeOrgResolver.companyIdOfBill(bizType, bizId);
        Long starter = instances.stream().map(WfInstance::getStartUserId)
                .filter(Objects::nonNull).findFirst().orElse(null);
        LoginUser me = UserContext.get();
        if (company != null) {
            if (me != null && hasActualTrace(me.getUserId(), instances)) {
                return;
            }
            orgGuard.assertVisible(company, starter);
            return;
        }
        if (me == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "未登录，无法确定本单据的可见范围");
        }
        boolean groupLevel = me.getCompanyId() == null
                || (me.getDataScope() != null && me.getDataScope() == 1);
        if (groupLevel || Objects.equals(me.getUserId(), starter) || participated(me.getUserId(), instances)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "无权查看该单据的全链路：" + bizType + " 域没有公司归属维度，仅提单人、流程参与人与集团岗可见");
    }

    /**
     * 本单实际留痕人（只认已发生的操作/被转办记录，不含仅挂名的候选人）—— D-DEC-327
     * 跨公司协办时，公司可解析分支原先只问 OrgGuard，会把真办过这单的人挡在门外。
     */
    private boolean hasActualTrace(Long userId, List<WfInstance> instances) {
        List<String> pis = procInstIds(instances);
        if (pis.isEmpty()) {
            return false;
        }
        Long hits = wfTaskHistoryMapper.selectCount(new LambdaQueryWrapper<WfTaskHistory>()
                .in(WfTaskHistory::getProcInstId, pis)
                .and(w -> w.eq(WfTaskHistory::getOperUserId, userId).or().eq(WfTaskHistory::getTargetUserId, userId)));
        return hits != null && hits > 0;
    }

    /** 本单流程参与人：任一轮次的历史操作人/被转办人，或当前在办任务的办理人/候选人 */
    private boolean participated(Long userId, List<WfInstance> instances) {
        List<String> pis = procInstIds(instances);
        if (pis.isEmpty()) {
            return false;
        }
        Long hits = wfTaskHistoryMapper.selectCount(new LambdaQueryWrapper<WfTaskHistory>()
                .in(WfTaskHistory::getProcInstId, pis)
                .and(w -> w.eq(WfTaskHistory::getOperUserId, userId).or().eq(WfTaskHistory::getTargetUserId, userId)));
        if (hits != null && hits > 0) {
            return true;
        }
        Long open = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .in(WfTask::getProcInstId, pis)
                .and(w -> w.eq(WfTask::getAssigneeId, userId)
                        .or().apply("find_in_set({0}, candidate_ids)", userId)));
        return open != null && open > 0;
    }

    // ---------------- 内部逻辑 ----------------

    /**
     * 该 bizType 是否以 exp_bill 为单据表：{@code EXP_} 前缀、注册表 fillTable/起流单号表/公司表为 exp_bill。
     * INVOICE_MATCH、PTP03、MEETING 这类"借 exp_bill 存单"的类型也算——它们的 bizId 就是 exp_bill.id，
     * 走费用锚点才是对的。
     */
    private static boolean isExpenseAnchor(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return false;
        }
        if (bizType.startsWith("EXP_")) {
            return true;
        }
        if ("exp_bill".equals(BillRouteRegistry.companyOf(bizType))) {
            return true;
        }
        BillRouteRegistry.Route r = BillRouteRegistry.of(bizType);
        if (r == null) {
            return false;
        }
        return "exp_bill".equals(r.fillTable()) || "exp_bill".equals(r.bizNoTableOrDefault());
    }

    /**
     * 该 bizType 是否「单据即档案」：注册表业务主表就是 op_archive ⇒ 传入的 bizId 本身就是档案主键。
     * 按注册表判定而不是再硬编码一个类型集合，避免第二处真值漂移（同 BATCH_IS_BILL 的语义，但这里可推导）。
     */
    private static boolean isArchiveAnchor(String bizType) {
        BillRouteRegistry.Route r = BillRouteRegistry.of(bizType);
        return r != null && "op_archive".equals(r.homeTable());
    }

    /** 本单据的流程实例（多轮次全部返回，按发起先后正序；bizTypeFilter 用于剔除同主键的他域流程） */
    private List<WfInstance> instancesOf(Long bizId, Predicate<String> bizTypeFilter) {
        List<WfInstance> all = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizId, bizId).orderByAsc(WfInstance::getId));
        return all.stream().filter(i -> bizTypeFilter.test(i.getBizType())).collect(Collectors.toList());
    }

    /**
     * 未声明域时的<b>所属域解析</b>（D-DEC-152）：这张数字主键在流程账上到底属于哪些域。
     *
     * <p>两张留痕表都带 {@code biz_type}，取并集而不是只读一张——已起流但镜像行被清理、
     * 或反过来只落了实例没有历史的边界情况都能覆盖；两张都空＝从未起流（草稿），
     * 调用方只能按费用单据主键解释，保持历史行为。
     *
     * <p>返回集合<b>可能跨域</b>，调用方据此决定分派/拒绝，这里不做任何"哪个更像"的猜测。
     */
    private Set<String> resolvedDomainsOf(Long bizId) {
        Set<String> domains = new java.util.LinkedHashSet<>();
        for (WfInstance i : wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizId, bizId).orderByAsc(WfInstance::getId))) {
            if (i.getBizType() != null && !i.getBizType().isBlank()) {
                domains.add(i.getBizType().trim());
            }
        }
        for (WfTaskHistory h : wfTaskHistoryMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .select(WfTaskHistory::getBizType)
                .eq(WfTaskHistory::getBizId, bizId)
                .orderByAsc(WfTaskHistory::getId)
                .last("limit " + TRACE_LIMIT))) {
            if (h.getBizType() != null && !h.getBizType().isBlank() && !"UNKNOWN".equals(h.getBizType())) {
                domains.add(h.getBizType().trim());
            }
        }
        return domains;
    }

    /**
     * 单据主流程实例（D-DEC-060）：摘要里的 instStatus / currentNode / "已批"证据锚定在这一枚，
     * 而不是 {@code id} 最大的那一枚。12-18:74 的实测形态就是被后者坑的——
     * 单据 26 名下 SETTLE(98, {@code bill.proc_inst_id}, RUNNING, 停在扫描岗) 与
     * INVOICE_MATCH(99, APPROVED)，取 99 就把「派单批完」显示成「这单已批」。
     *
     * <p>判据按可靠性递减，用了哪一档随 {@code summary.mainInstBy} 一起透出（不假装永远锚得准）：
     * <ol>
     *   <li>{@code BILL_PROC_INST_ID}——命中 {@code exp_bill.proc_inst_id} 回写列：提交主流程时由
     *       {@code ExpenseBillService} 写入，是单据自己的权威锚点；</li>
     *   <li>{@code BILL_TYPE}——实例 bizType 与单据自身类型一致（含 {@code EXP_} 前缀归一）；</li>
     *   <li>{@code EXCLUDE_SUB_CHAIN}——排除 {@link BillRouteRegistry#isSubChainOnBill 派单/子流程类}后取最新一轮；</li>
     *   <li>{@code FALLBACK_LATEST}——前三档都落空（名下全是派单类型）才退回最新一轮，
     *       该档会在摘要里附带 {@code mainInstUncertain=true}，前端与人工复核据此识别。</li>
     * </ol>
     * 同类型多轮次（驳回重提）在该档内取 {@code id} 最大＝最新一轮，与原口径一致。
     */
    private static MainInst mainInstanceOf(ExpBill bill, List<WfInstance> instances) {
        if (instances.isEmpty()) {
            return new MainInst(null, "NONE", false);
        }
        if (bill != null && bill.getProcInstId() != null && !bill.getProcInstId().isBlank()) {
            for (WfInstance i : instances) {
                if (bill.getProcInstId().equals(i.getProcInstId())) {
                    return new MainInst(i, "BILL_PROC_INST_ID", false);
                }
            }
        }
        String ownType = normalizeBillType(bill == null ? null : bill.getBillType());
        if (ownType != null) {
            WfInstance hit = null;
            for (WfInstance i : instances) {
                if (ownType.equals(normalizeBillType(i.getBizType()))) {
                    hit = i; // 同类型多轮次（驳回重提）取最后一轮
                }
            }
            if (hit != null) {
                return new MainInst(hit, "BILL_TYPE", false);
            }
        }
        WfInstance nonSub = null;
        for (WfInstance i : instances) {
            if (!BillRouteRegistry.isSubChainOnBill(i.getBizType())) {
                nonSub = i;
            }
        }
        if (nonSub != null) {
            return new MainInst(nonSub, "EXCLUDE_SUB_CHAIN", false);
        }
        return new MainInst(instances.get(instances.size() - 1), "FALLBACK_LATEST", true);
    }

    /** 主流程实例判据结果：实例 + 用了哪一档 + 该档是否为兜底 */
    private record MainInst(WfInstance inst, String by, boolean uncertain) {
    }

    /** 单据类型 / 实例 bizType 归一：转大写并去 {@code EXP_} 前缀，使 CLAIM 与 EXP_CLAIM 同判 */
    private static String normalizeBillType(String t) {
        if (t == null || t.isBlank()) {
            return null;
        }
        String s = t.trim().toUpperCase();
        return s.startsWith("EXP_") ? s.substring(4) : s;
    }

    /**
     * 派单/子流程实例视图：主流程锚点收窄后，这些实例的事实不能丢，单独列进摘要给前端展示。
     * {@code subChain} 标出该枚是否属 {@link BillRouteRegistry#isSubChainOnBill 派单/子流程类}
     * ⇒ 前端可把「派单已办结」与「同 biz_id 的重提旧轮次」分开计数（12-18:74 要求两条事实并存而不混算）。
     */
    private static List<Map<String, Object>> subInstancesOf(WfInstance main, List<WfInstance> instances) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WfInstance i : instances) {
            if (main == null || !Objects.equals(main.getProcInstId(), i.getProcInstId())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", i.getId());
                m.put("procInstId", i.getProcInstId());
                m.put("bizType", i.getBizType());
                m.put("subChain", BillRouteRegistry.isSubChainOnBill(i.getBizType()));
                m.put("status", i.getStatus());
                m.put("currentNode", suppressWhenTerminal(i, WfInstance::getCurrentNode));
                m.put("endTime", i.getEndTime());
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 终态抑制取值器（D-DEC-072）：实例已进终态（非 RUNNING，判据与 D-DEC-121 清列同一把尺子
     * {@link WfQueryService#isTerminalInstanceStatus}）时，「当前节/当前办理人」这类"在办指针"
     * 一律返回 {@code null}，联查不再把已结流程显示成还挂在某一节等人办。
     *
     * <p>只作用于<b>展示</b>：库内列值不动（存量回刷 SQL 已在裁决册，本批不重复出）。
     */
    private static String suppressWhenTerminal(WfInstance inst,
                                               java.util.function.Function<WfInstance, String> getter) {
        if (inst == null || WfQueryService.isTerminalInstanceStatus(inst.getStatus())) {
            return null;
        }
        return getter.apply(inst);
    }

    /**
     * 审批轨迹：直读 wf_task_history（RA-TRC-03 乙）。按 biz_id + 域过滤取全部轮次，
     * 时间正序，供前端「状态轨迹」与「审批流转轨迹」共用同一数据源。
     *
     * <p>D-DEC-054/089：正序按 {@link WfQueryService#causalOrder} 的<b>因果序</b>给，不是单纯落账序——
     * 实例级定论行（APPROVED/REJECTED，D-DEC-109 起也带 node_key）由 PROCESS_COMPLETED 监听器在
     * {@code complete()} 内先写、末节办理行在其后写（12-17 #635/#636、12-22 #679/#680 同秒逆序），
     * 按 id 或按 oper_time 排都会让时间线读出"流程先结束、末节才审批"。定论行统一沉到末尾。
     *
     * <p>D-DEC-067/080：每行随带 {@code instanceLevel} 附加位（非表列），前端据此把定论行放
     * "整单结论"栏而不是"第 N 节办理"栏，不再需要自己按 action 猜。
     */
    private List<WfTaskHistory> tracesOf(Long bizId, Predicate<String> bizTypeFilter) {
        List<WfTaskHistory> rows = wfTaskHistoryMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getBizId, bizId)
                .orderByAsc(WfTaskHistory::getId)
                .last("limit " + TRACE_LIMIT));
        List<WfTaskHistory> kept = rows.stream().filter(h -> bizTypeFilter.test(h.getBizType()))
                .collect(Collectors.toList());
        for (WfTaskHistory h : kept) {
            h.setInstanceLevel(WfQueryService.isInstanceLevelTrail(h));
        }
        return WfQueryService.causalOrder(kept, false);
    }

    private static List<String> procInstIds(List<WfInstance> instances) {
        return instances.stream().map(WfInstance::getProcInstId)
                .filter(p -> p != null && !p.isBlank()).distinct().collect(Collectors.toList());
    }

    private static List<ArchiveInfoDTO> mergeArchives(List<ArchiveInfoDTO> a, List<ArchiveInfoDTO> b) {
        Map<Long, ArchiveInfoDTO> byId = new LinkedHashMap<>();
        for (List<ArchiveInfoDTO> src : List.of(a, b)) {
            for (ArchiveInfoDTO x : src) {
                if (x != null && x.getId() != null) {
                    byId.putIfAbsent(x.getId(), x);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 费用域链路完整度摘要（前端时间线唯一数据源）。
     * reached 六段：BILL → APPROVED → VOUCHER → PAYMENT → BATCH → ARCHIVE，
     * 与前端业务链时间线节点 key 一一对应。
     */
    private Map<String, Object> expenseSummary(ExpBill bill, List<WfInstance> instances,
                                              List<ExpBillPayment> payments, List<OpPayBatch> batches,
                                              List<OpVoucher> vouchers, List<ArchiveInfoDTO> archives,
                                              List<WfTaskHistory> traces) {
        String billStatus = bill.getStatus();
        // D-DEC-060：状态/卡点/"已批"证据锚在单据主流程实例上，而不是 id 最大（＝最后完成）的那一枚。
        MainInst main = mainInstanceOf(bill, instances);
        WfInstance inst = main.inst();

        // —— 审批通过三档证据，取或；负面状态一票否决 ——
        boolean approved;
        String evidence;
        if (billStatus != null && STATUS_APPROVED.contains(billStatus)) {
            approved = true;
            evidence = "BILL_STATUS:" + billStatus;
        } else if (inst != null && inst.getStatus() != null && INST_APPROVED.contains(inst.getStatus())) {
            approved = true;
            evidence = "WF_INSTANCE:" + inst.getStatus();
        } else if (!payments.isEmpty() || !batches.isEmpty() || !vouchers.isEmpty() || !archives.isEmpty()) {
            // 已有下游凭证/付款/批次/档案 —— 未审批不可能产出下游单据，据此反推审批已通过
            approved = true;
            evidence = "DOWNSTREAM";
        } else {
            approved = false;
            evidence = "NONE";
        }
        if (billStatus != null && STATUS_NEGATIVE.contains(billStatus)) {
            approved = false;
            evidence = "STATUS_NEGATIVE:" + billStatus;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", "EXPENSE");
        summary.put("billMatched", true);
        summary.put("billStatus", billStatus);
        summary.put("approved", approved);
        summary.put("approvalEvidence", evidence);
        summary.put("instStatus", inst == null ? null : inst.getStatus());
        // D-DEC-072（＝D-DEC-056 的联查透出腿）：实例已终态 ⇒ 抑制「当前节/当前办理人」。
        // D-DEC-121 已把清列收口在 updateInstanceStatus，但①存量行未回刷（回刷 SQL 已入裁决册）、
        // ②终态判定与展示之间还隔着一次读数——联查是「已办结却显示仍在某节待办」的直接出口
        // （12-18:86 实测 summary.currentNode='FSSC稽核核算岗终审【财务审核岗】'）。
        // 展示层再抑制一次，用的是同一个 isTerminalInstanceStatus 判据（不另写一把尺子）。
        summary.put("currentNode", suppressWhenTerminal(inst, WfInstance::getCurrentNode));
        summary.put("currentAssignee", suppressWhenTerminal(inst, WfInstance::getCurrentAssignee));
        if (inst != null && WfQueryService.isTerminalInstanceStatus(inst.getStatus())) {
            summary.put("currentNodeSuppressedBy", "INSTANCE_TERMINAL:" + inst.getStatus());
        }
        summary.put("rounds", instances.size());
        // D-DEC-060：主流程实例锚点口径透出，并把派单/子流程实例单列——
        // 「匹配链已批完」与「本单主流程还在扫描岗」是两条并存的事实，不是一条顶替另一条。
        summary.put("mainInstBy", main.by());
        summary.put("mainInstUncertain", main.uncertain());
        summary.put("mainProcInstId", inst == null ? null : inst.getProcInstId());
        summary.put("mainBizType", inst == null ? null : inst.getBizType());
        summary.put("subInstances", subInstancesOf(inst, instances));
        summary.put("paymentCount", payments.size());
        summary.put("batchCount", batches.size());
        summary.put("voucherCount", vouchers.size());
        summary.put("archiveCount", archives.size());
        summary.put("traceCount", traces.size());
        // D-DEC-446 乙-2/乙-3：各段终态计数（判据用）＋存在计数（副标题用），两套并存不互相顶替
        int posted = postedVoucherCount(vouchers);
        int payPaid = paidPaymentCount(payments);
        int batchSettled = settledBatchCount(batches);
        int archived = archivedCount(archives);
        summary.put("postedVoucherCount", posted);
        summary.put("paidPaymentCount", payPaid);
        summary.put("settledBatchCount", batchSettled);
        summary.put("archivedCount", archived);
        boolean paySegment = bill.getBillType() == null || PAY_SEGMENT_TYPES.contains(bill.getBillType());
        summary.put("segmentSupport", segmentSupport(paySegment));
        summary.put("paymentSupported", paySegment);
        summary.put("reached", reached(approved, posted, payPaid, batchSettled, archived));
        // P3：凭证自动生成失败标记（exp_bill.ext_json.voucherPending）。使时间线能回答
        // "VOUCHER 段为什么没到"，而不是只显示一个灰节点——补生成入口也随标记一并给出。
        Map<String, Object> voucherPending = voucherPendingOf(bill);
        if (voucherPending != null) {
            summary.put("voucherPending", voucherPending);
        }
        return summary;
    }

    /**
     * P3：单据侧「凭证待补生成」标记（{@code exp_bill.ext_json.voucherPending}，写入侧见
     * {@code ExpenseWfEventListener#markVoucherPending}）。
     *
     * <p>判定口径＝{@code voucher_no 为空 且 标记存在}：补生成成功后 {@code voucher_no} 有值，
     * 标记即自然失效、无需回写清除（避免多写点同步）。解析失败按"无标记"处理，绝不影响主链路。
     */
    private static Map<String, Object> voucherPendingOf(ExpBill bill) {
        if (bill.getVoucherNo() != null && !bill.getVoucherNo().isBlank()) {
            return null;
        }
        String raw = bill.getExtJson();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(raw).get("voucherPending");
            if (node == null || !node.isObject()) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("code", node.path("code").asText(null));
            out.put("sourceType", node.path("sourceType").asText(null));
            out.put("reason", node.path("reason").asText(null));
            out.put("time", node.path("time").asText(null));
            out.put("retryEntry", "POST /api/tenant/sharing/voucher/generate/" + bill.getId());
            return out;
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 他域链路摘要：没有本域单据实体可判状态，审批结论只看流程实例终态 + 下游反推 */
    private Map<String, Object> genericSummary(String bizType, List<WfInstance> instances, WfInstance latest,
                                               List<OpPayBatch> batches, List<OpVoucher> vouchers,
                                               List<ArchiveInfoDTO> archives, List<WfTaskHistory> traces) {
        boolean approved;
        String evidence;
        if (latest.getStatus() != null && INST_APPROVED.contains(latest.getStatus())) {
            approved = true;
            evidence = "WF_INSTANCE:" + latest.getStatus();
        } else if (!batches.isEmpty() || !vouchers.isEmpty() || !archives.isEmpty()) {
            approved = true;
            evidence = "DOWNSTREAM";
        } else {
            approved = false;
            evidence = "NONE";
        }
        if (latest.getStatus() != null && STATUS_NEGATIVE.contains(latest.getStatus())) {
            approved = false;
            evidence = "WF_STATUS_NEGATIVE:" + latest.getStatus();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", bizType);
        summary.put("billMatched", true);
        summary.put("billStatus", latest.getStatus());
        summary.put("approved", approved);
        summary.put("approvalEvidence", evidence);
        summary.put("instStatus", latest.getStatus());
        summary.put("currentNode", suppressWhenTerminal(latest, WfInstance::getCurrentNode));
        summary.put("rounds", instances.size());
        // payments 段对他域不适用（exp_bill_payment 属费用域专属表），前端据此不画该环节
        summary.put("paymentSupported", false);
        summary.put("paymentCount", 0);
        summary.put("paidPaymentCount", 0);
        int posted = postedVoucherCount(vouchers);
        int batchSettled = settledBatchCount(batches);
        int archived = archivedCount(archives);
        summary.put("postedVoucherCount", posted);
        summary.put("settledBatchCount", batchSettled);
        summary.put("archivedCount", archived);
        summary.put("segmentSupport", segmentSupport(false));
        summary.put("batchCount", batches.size());
        summary.put("voucherCount", vouchers.size());
        summary.put("archiveCount", archives.size());
        summary.put("traceCount", traces.size());
        summary.put("reached", reached(approved, posted, 0, batchSettled, archived));
        return summary;
    }

    /**
     * 六段链路到达位（费用域与他域共用，保证前端时间线两路口径一致）。
     *
     * <p>D-DEC-446 乙-1：形参一律是「终态计数」而非「存在计数」。此前拿 {@code voucherCount>0} 判达，
     * 于是 DRAFT 凭证、PENDING 档案也亮绿——假绿。真正的到达判据是：凭证存在 {@code status='POSTED'} 行、
     * 付款存在 {@code pay_status='PAID'} 行、批次已出账、档案 {@code archive_status='ARCHIVED'}。
     * 「存在但未终态」由前端副标题按两套计数差额明示，不在这里收窄。
     */
    private static List<String> reached(boolean approved, int postedVoucherCount, int paidPaymentCount,
                                        int settledBatchCount, int archivedCount) {
        List<String> reached = new ArrayList<>();
        reached.add("BILL");
        if (approved) {
            reached.add("APPROVED");
        }
        if (postedVoucherCount > 0) {
            reached.add("VOUCHER");
        }
        if (paidPaymentCount > 0) {
            reached.add("PAYMENT");
        }
        if (settledBatchCount > 0) {
            reached.add("BATCH");
        }
        if (archivedCount > 0) {
            reached.add("ARCHIVE");
        }
        return reached;
    }

    /** 已过账凭证数（{@code op_voucher.status='POSTED'}）；DRAFT 不计 */
    private static int postedVoucherCount(List<OpVoucher> vouchers) {
        int n = 0;
        for (OpVoucher v : vouchers) {
            if ("POSTED".equals(v.getStatus())) {
                n++;
            }
        }
        return n;
    }

    /** 已付款记录数（{@code exp_bill_payment.pay_status='PAID'}）；待付/失败不计 */
    private static int paidPaymentCount(List<ExpBillPayment> payments) {
        int n = 0;
        for (ExpBillPayment p : payments) {
            if ("PAID".equals(p.getPayStatus())) {
                n++;
            }
        }
        return n;
    }

    /** 已出账批次数：PAID / CLOSED / ARCHIVED 均视为该段已完成，付款在批次里跑完就不会再回退 */
    private static int settledBatchCount(List<OpPayBatch> batches) {
        int n = 0;
        for (OpPayBatch b : batches) {
            String s = b.getStatus();
            if ("PAID".equals(s) || "CLOSED".equals(s) || "ARCHIVED".equals(s)) {
                n++;
            }
        }
        return n;
    }

    /** 已归档卷数（{@code op_archive.archive_status='ARCHIVED'}）；PENDING/ARCHIVING 不计 */
    private static int archivedCount(List<ArchiveInfoDTO> archives) {
        int n = 0;
        for (ArchiveInfoDTO a : archives) {
            if ("ARCHIVED".equals(a.getArchiveStatus())) {
                n++;
            }
        }
        return n;
    }

    /**
     * D-DEC-446 甲-2：各段对本单据类型的适用性。前端据此裁剪格子，
     * 把「本域无此环节」和「有此环节但尚未办理」区分开。
     */
    private static Map<String, Object> segmentSupport(boolean paySegment) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("BILL", true);
        out.put("APPROVED", true);
        out.put("VOUCHER", true);
        out.put("PAYMENT", paySegment);
        out.put("BATCH", paySegment);
        out.put("ARCHIVE", true);
        return out;
    }

    /** 锚点未命中：明确返回「未接入」，全空集合，绝不用其它单据数据填补 */
    private Map<String, Object> unsupported(String bizType, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bill", null);
        out.put("payments", List.of());
        out.put("batches", List.of());
        out.put("vouchers", List.of());
        out.put("voucherLines", new LinkedHashMap<>());
        out.put("archives", List.of());
        out.put("traces", List.of());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", "NOT_SUPPORTED");
        summary.put("billMatched", false);
        summary.put("bizType", bizType);
        summary.put("reason", reason == null
                ? "该单据域尚未接入全链路追溯（当前：费用/借款/还款单据，以及已声明 bizType 且发起过流程的单据域）" : reason);
        summary.put("paymentCount", 0);
        summary.put("batchCount", 0);
        summary.put("voucherCount", 0);
        summary.put("archiveCount", 0);
        summary.put("traceCount", 0);
        summary.put("reached", List.of());
        out.put("summary", summary);
        return out;
    }
}
