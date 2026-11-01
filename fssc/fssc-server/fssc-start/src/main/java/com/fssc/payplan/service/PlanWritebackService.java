// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.payplan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.payplan.entity.OpPlanPaidLog;
import com.fssc.payplan.entity.PayPlan;
import com.fssc.payplan.mapper.OpPlanPaidLogMapper;
import com.fssc.payplan.mapper.PayPlanMapper;
import com.fssc.sharing.entity.OpPayBatchDetail;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 付款批次→采购付款计划 核销回写（D6：付款完成回写计划已付款）。
 *
 * <h3>背景（缺口）</h3>
 * OTC 代下采购付款计划（pay_plan）与付款批次（op_pay_batch）是两个独立域：
 * 计划审批通过止于 APPROVED，批次执行打款成功后无任何回写——协同端（BCP 门户"付款结算"）
 * 因此永远看不到"已付款"。本服务在批次执行成功的同事务内按收款方归集核销计划。
 *
 * <h3>规则（Phase 1 最小闭环）</h3>
 * <ul>
 *   <li><b>匹配</b>：批次明细 {@code supplier_id} = 计划 {@code supplier_id} 优先；
 *       <b>计划行 supplier_id 为 NULL</b> 时回退 {@code pay_plan.supplier = 批次明细 payee_name}；
 *       明细侧无 id 时整条按名称归集（与原先同一规则）。status=APPROVED 且未全额核销；
 *       同主体多笔时本公司计划优先、期间升序 FIFO 分配。</li>
 *   <li><b>核销</b>：paid_amount 累加本次分配额；paid_amount ≥ amount → status=DONE（全额），
 *       否则保持 APPROVED（部分执行）。</li>
 *   <li><b>去重</b>：op_plan_paid_log(batch_no, plan_id) 唯一键，批次重试/重执行不重复核销。</li>
 *   <li><b>开关</b>：fssc.plan-writeback.enabled（默认 true）。</li>
 * </ul>
 *
 * <h3>勾稽键以档案 id 优先，名称文本只作回退</h3>
 * 只看 {@link #writeBackOnBatchPaid} 的匹配键 {@code pay_plan.supplier = 批次明细 payee_name}
 * 的<b>字符串相等</b>，名称近似即错配（{@code md_supplier} 按 supplier_name 分组目前零重名，
 * 尚未爆雷；但「东昇贸易有限公司」与「东昇实业有限公司」这类同前缀异主体，
 * 恰是三单匹配认定的「收款方≠供应商」真实场景，两域都用名称做键就会互相印证错）。
 *
 * <p><b>现规则</b>：{@code pay_plan.supplier_id} 与 {@code op_pay_batch_detail.supplier_id} 已加列（库=fssc）。
 * 收款主体先按档案 id 归集，计划候选先按 id 相等取；<b>只有 supplier_id 为 NULL 的计划行</b>才回退名称等值匹配，
 * 挂了档案的计划行永不以名称参与比对——近似名至多命中"两侧都还没挂档案"的历史行，不会把 A 家的付款
 * 核销到已挂档案的 B 家计划上。明细侧没有 id 时整条退回名称归集，与不带 id 的旧行为一致。
 *
 * id 只取上下文已有的真值，绝不在后端按名称反查档案补 id（那等于把已知不可靠的判据搬进代码）：
 * <ul>
 *   <li>计划侧：{@code POST /api/tenant/pay-plan/create} 的入参 {@code supplierId}
 *       （{@code PayPlan.vue} 的档案下拉，值＝md_supplier.id），随实体透传写入数据库；</li>
 *   <li>批次侧：{@code op_pay_batch_detail.supplier_id ← exp_bill_payment.payee_id}
 *       （仅 {@code payee_type='SUPPLIER'} 时；该列由上游按 采购订单 supplier_id → 合同往来单位
 *       两级解析，两条都取不到即为 NULL）。</li>
 * </ul>
 *
 * <p><b>历史不回填</b>（以名称文本回灌就是用同一套判据给自己背书，回填规则待产品确认），
 * 所以老行的 supplier_id 恒为 NULL 并走名称回退分支——回退分支的存在是历史规则，不是遗留缺陷。
 * 新录的计划与付款一旦带上档案 id，勾稽即不再依赖名称。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanWritebackService {

    private final PayPlanMapper payPlanMapper;
    private final OpPlanPaidLogMapper planPaidLogMapper;

    /** 总开关：false 时批次执行不回写计划（灰度/回退用） */
    @Value("${fssc.plan-writeback.enabled:true}")
    private boolean enabled;

    /**
     * 批次打款成功后按收款方归集回写付款计划。
     * 由 PayBatchService.execute 在批次置 PAID 后调用；REQUIRES_NEW 独立事务——
     * 核销失败不拖垮已完成的付款（调用方有 catch + 日志），可人工/重试补核销。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void writeBackOnBatchPaid(Long batchCompanyId, String batchNo, List<OpPayBatchDetail> details) {
        if (!enabled || details == null || details.isEmpty()) {
            return;
        }
        // 去重：该批次已回写过则跳过（重试/重执行场景）
        Long logged = planPaidLogMapper.selectCount(
                new LambdaQueryWrapper<OpPlanPaidLog>().eq(OpPlanPaidLog::getBatchNo, batchNo));
        if (logged != null && logged > 0) {
            log.info("[D6] 批次 {} 计划核销已回写过，跳过", batchNo);
            return;
        }
        // 按收款主体归集本次打款金额：有档案 id 的按 id 归集，无 id 的（历史行／非供应商收款方）按收款方名称归集
        Map<PayeeGroup, BigDecimal> byPayee = new LinkedHashMap<>();
        for (OpPayBatchDetail d : details) {
            if (d.getAmount() == null) {
                continue;
            }
            String payeeName = blankToNull(d.getPayeeName());
            if (d.getSupplierId() == null && payeeName == null) {
                continue; // 既无档案 id 又无名称：无从归集（与改造前一致）
            }
            byPayee.merge(new PayeeGroup(d.getSupplierId(), payeeName), d.getAmount(), BigDecimal::add);
        }
        LocalDateTime now = LocalDateTime.now();
        // 同批同计划只落一条核销台账（uk_batch_plan），故 id 命中组与名称回退组命中同一条计划时按批次汇总
        Map<Long, BigDecimal> allocByPlan = new LinkedHashMap<>();
        Map<Long, String> planNoById = new LinkedHashMap<>();
        // 本轮累计后的计划已付额：同一条计划可被多个归集组命中，以本表为准，避免同批次重复占用
        Map<Long, BigDecimal> paidByPlan = new LinkedHashMap<>();
        for (Map.Entry<PayeeGroup, BigDecimal> e : byPayee.entrySet()) {
            BigDecimal remaining = e.getValue();
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<PayPlan> plans = candidatePlans(e.getKey());
            // 同主体多公司：本公司计划优先核销
            plans.sort((a, b) -> Long.compare(
                    b.getCompanyId() != null && b.getCompanyId().equals(batchCompanyId) ? 1 : 0,
                    a.getCompanyId() != null && a.getCompanyId().equals(batchCompanyId) ? 1 : 0));
            for (PayPlan plan : plans) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal paid = paidByPlan.containsKey(plan.getId())
                        ? paidByPlan.get(plan.getId())
                        : (plan.getPaidAmount() == null ? BigDecimal.ZERO : plan.getPaidAmount());
                BigDecimal unpaid = plan.getAmount().subtract(paid);
                if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal alloc = unpaid.min(remaining);
                BigDecimal newPaid = paid.add(alloc);
                PayPlan up = new PayPlan();
                up.setId(plan.getId());
                up.setPaidAmount(newPaid);
                boolean full = newPaid.compareTo(plan.getAmount()) >= 0;
                if (full) {
                    up.setStatus("DONE");
                }
                payPlanMapper.updateById(up);
                paidByPlan.put(plan.getId(), newPaid);
                allocByPlan.merge(plan.getId(), alloc, BigDecimal::add);
                planNoById.putIfAbsent(plan.getId(), plan.getPlanNo());
                remaining = remaining.subtract(alloc);
                log.info("[D6] 付款批次 {} 核销付款计划 {} {} 元（paid={}/{}，status={}，收款主体={}）",
                        batchNo, plan.getPlanNo(), alloc, newPaid, plan.getAmount(),
                        full ? "DONE" : "APPROVED", e.getKey());
            }
        }
        for (Map.Entry<Long, BigDecimal> e : allocByPlan.entrySet()) {
            OpPlanPaidLog logRow = new OpPlanPaidLog();
            logRow.setPlanId(e.getKey());
            logRow.setPlanNo(planNoById.get(e.getKey()));
            logRow.setBatchNo(batchNo);
            logRow.setAmount(e.getValue());
            logRow.setPaidAt(now);
            planPaidLogMapper.insert(logRow);
        }
    }

    /**
     * 计划候选查询（双路）：
     * <ul>
     *   <li>明细带档案 id → {@code supplier_id = id} 为主路，另并一条 {@code supplier_id IS NULL 且
     *       supplier = 收款方名称} 的回退路，专供没有档案 id 的历史计划（不回填，回退即它们的唯一出口）；
     *       已挂档案的计划行只能按 id 命中，名称再近似也不会被核销走；</li>
     *   <li>明细无档案 id（历史批次／员工等非标品收款方）→ 维持原名称等值匹配，规则不变。</li>
     * </ul>
     */
    private List<PayPlan> candidatePlans(PayeeGroup group) {
        LambdaQueryWrapper<PayPlan> qw = new LambdaQueryWrapper<PayPlan>()
                .eq(PayPlan::getStatus, "APPROVED")
                .apply("paid_amount < amount");
        if (group.supplierId() == null) {
            qw.eq(PayPlan::getSupplier, group.payeeName());
        } else {
            Long supplierId = group.supplierId();
            String payeeName = group.payeeName();
            qw.and(w -> {
                w.eq(PayPlan::getSupplierId, supplierId);
                if (payeeName != null) {
                    w.or(o -> o.isNull(PayPlan::getSupplierId).eq(PayPlan::getSupplier, payeeName));
                }
            });
        }
        return payPlanMapper.selectList(qw.orderByAsc(PayPlan::getPeriod).orderByAsc(PayPlan::getId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 收款主体归集键：供应商档案 id 优先，无 id 时用收款方名称 */
    private record PayeeGroup(Long supplierId, String payeeName) {
    }
}
