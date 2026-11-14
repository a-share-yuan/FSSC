package com.fssc.sharing.service;

import com.fssc.framework.facade.event.BillTerminalEvent;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * 付款条目在<strong>审批通过的收尾</strong>里自动组批，而不是等出纳打开付款页。
 *
 * <p><b>为什么不能挂在出纳页</b>
 * <ul>
 *  <li>组批若由出纳页加载触发（{@code Pay.vue} 的 {@code load()} 里调 autoGroup），
 *      该接口要求 {@code pay:batch:create} 权限——出纳（3259/3264）按治理口径<strong>不授</strong>该权限，
 *      于是这条链在真实权限模型下根本不成立，付款条目长期无人产出批次。</li>
 *  <li>审批通过后付款行不进任何批次，出纳没有入口；查询范围本身是通的，
 *      卡点在触发点与权限门槛。</li>
 * </ul>
 *
 * <p><b>本类做的</b>：单据审批通过（{@link BillTerminalEvent}，status=APPROVED）后按本单名下
 * 未入批的 WAITING 付款记录组批，服务端触发、与登录人权限无关。付款页只剩读操作，
 * 组批不再依赖 {@code pay:batch:create}。
 *
 * <p><b>本类不做的（需产品确认）</b>：权限补授（CASHIER 不给 {@code pay:batch:create} 是治理口径）、
 * 以及"由谁在系统里对未组批兜底"的运营规则。
 *
 * <p><b>时序</b>：AFTER_COMMIT 且 {@code fallbackExecution = true}——付款记录由审批收尾事务写入，
 * 必须等它提交后再组批，否则本事务看不见条目（且收尾事务不能因组批异常被标 rollback-only）。
 * 引擎线程无登录上下文，按事件携带的 tenantId 重建租户上下文（与 {@code EventOrchestrationBus} 同一规则），
 * 审计人由自动填充回落 system。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayBatchAutoGroupListener {

    private final PayBatchService payBatchService;

    /** 只有会产生付款记录的单据类型需要组批：报销(CLAIM)、对公付款/结算(PAY)、借款放款(LOAN)。 */
    private static final Set<String> PAYABLE_BILL_TYPES = Set.of("CLAIM", "PAY", "LOAN");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBillApproved(BillTerminalEvent event) {
        if (event == null || event.getBillId() == null) {
            return;
        }
        if (!"APPROVED".equals(event.getStatus())) {
            return;
        }
        String billType = event.getBillType() == null ? "" : event.getBillType().trim().toUpperCase();
        if (!PAYABLE_BILL_TYPES.contains(billType)) {
            return;
        }
        Runnable task = () -> {
            try {
                PayBatchService.AutoGroupResult r = payBatchService.autoGroupForBill(event.getBillId());
                if (r.getBatchCount() > 0 || !r.getSkippedReasons().isEmpty()) {
                    log.info("单据 {}({}) 审批通过自动组批：新建 {} 个批次，跳过 {} 组（原因见付款页提示口径）",
                            event.getBillNo(), billType, r.getBatchCount(), r.getSkippedReasons().size());
                }
            } catch (Exception ex) {
                // 组批失败不得影响审批收尾（单据已 APPROVED、付款记录已写入数据库）：
                // 出纳仍可在付款工作台「选待付记录成批」手工补组，属可恢复动作，只记录不抛出。
                log.error("单据 {}({}) 审批通过自动组批失败（可在付款工作台手工成批）: {}",
                        event.getBillNo(), billType, ex.getMessage(), ex);
            }
        };
        Long tid = event.getTenantId();
        if (tid == null) {
            log.warn("单据 {} 终态事件未携带 tenantId，自动组批按忽略租户兜底执行", event.getBillNo());
            TenantContext.runIgnoreTenant(task);
        } else {
            TenantContext.callAs(tid, task);
        }
    }
}
