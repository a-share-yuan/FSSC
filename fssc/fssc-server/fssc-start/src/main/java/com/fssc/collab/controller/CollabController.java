package com.fssc.collab.controller;

import com.fssc.apflow.service.ApMonthCloseService;
import com.fssc.collab.entity.CcpShipping;
import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.entity.ScpBill;
import com.fssc.collab.mapper.CollabSyncLogMapper;
import com.fssc.collab.entity.ScpRecon;
import com.fssc.collab.service.CcpFlowService;
import com.fssc.collab.service.ScpReconService;
import com.fssc.collab.service.ScpFlowService;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCP/CCP 协同平台打通 OpenAPI（/api/tenant/collab/**）：
 * SCP 供应商发票全流程（匹配/寄送/签收/扫描票核/记账/认证/付款回写，打通点1~7）、
 * CCP 客户对账与发运数据（接口1/2/3/对账状态同步）、接口日志、关账冻结状态（R-14）。
 */
@RestController
@RequestMapping("/api/tenant/collab")
@RequiredArgsConstructor
public class CollabController {

    private final ScpFlowService scpFlowService;
    private final ScpReconService scpReconService;
    private final CcpFlowService ccpFlowService;
    private final ApMonthCloseService apMonthCloseService;
    private final CollabSyncLogMapper syncLogMapper;
    private final com.fssc.system.service.ConfigService configService;

    // ---------------- SCP 供应商端 ----------------

    @RequiresPerm("collab:scp:list")
    @GetMapping("/scp/bills")
    public Result<List<ScpBill>> bills(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
        return Result.success(scpFlowService.list(status, keyword));
    }

    /** 供应商确认对账单+申请开票数据落地（打通点1/2） */
    @OperLog(module = "COLLAB", operType = "ADD", desc = "SCP对账开票数据落地")
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/bills")
    public Result<ScpBill> create(@RequestBody ScpBill bill) {
        return Result.success(scpFlowService.create(bill));
    }

    /** 自动匹配（R-16 容差比对） */
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/bills/{id}/match")
    public Result<ScpBill> match(@PathVariable Long id) {
        return Result.success(scpFlowService.match(id));
    }

    /** 取消匹配（R-19，仅待寄送） */
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/bills/{id}/cancel-match")
    public Result<ScpBill> cancelMatch(@PathVariable Long id) {
        return Result.success(scpFlowService.cancelMatch(id));
    }

    /** 创建邮包寄送：body {courierCompany, courierNo} */
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/bills/{id}/send")
    public Result<ScpBill> send(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(scpFlowService.send(id,
                (String) body.get("courierCompany"), (String) body.get("courierNo")));
    }

    /** 内网快递签收 */
    @RequiresPerm("collab:scp:sign")
    @PostMapping("/scp/bills/{id}/sign")
    public Result<ScpBill> sign(@PathVariable Long id) {
        return Result.success(scpFlowService.sign(id));
    }

    /** 扫描识别+票核（R-20 六要素）：body 为票面数据（可部分提供） */
    @RequiresPerm("collab:scp:sign")
    @PostMapping("/scp/bills/{id}/scan")
    public Result<ScpBill> scan(@PathVariable Long id, @RequestBody(required = false) ScpBill scanned) {
        return Result.success(scpFlowService.scan(id, scanned));
    }

    /** 票核失败复核后重新匹配 */
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/bills/{id}/rematch")
    public Result<ScpBill> reMatch(@PathVariable Long id) {
        return Result.success(scpFlowService.reMatch(id));
    }

    /** 记账（打通点3，受 R-14 关账冻结拦截） */
    @OperLog(module = "COLLAB", operType = "UPDATE", desc = "SCP三单匹配记账推送EBS")
    @RequiresPerm("collab:scp:post")
    @PostMapping("/scp/bills/{id}/post")
    public Result<ScpBill> post(@PathVariable Long id) {
        return Result.success(scpFlowService.post(id));
    }

    /** 集中认证（打通点6）：body {result?} */
    @RequiresPerm("collab:scp:certify")
    @PostMapping("/scp/bills/{id}/certify")
    public Result<ScpBill> certify(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(scpFlowService.certify(id,
                body == null ? null : (String) body.get("result")));
    }

    /** 付款信息回写（打通点5）：body {payVoucherNo, payDate, payAmount} */
    @RequiresPerm("collab:scp:pay")
    @PostMapping("/scp/bills/{id}/pay-writeback")
    public Result<ScpBill> payWriteback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("payAmount") == null ? null
                : new BigDecimal(String.valueOf(body.get("payAmount")));
        LocalDate date = body.get("payDate") == null ? null
                : LocalDate.parse(String.valueOf(body.get("payDate")).substring(0, 10));
        return Result.success(scpFlowService.payWriteback(id,
                (String) body.get("payVoucherNo"), date, amount));
    }

    // ---------------- SCP 供应商对账管理（E1~E6，蓝图 SCC_AP_01/02） ----------------

    @RequiresPerm("collab:scp:recon:list")
    @GetMapping("/scp/recons")
    public Result<List<ScpRecon>> recons(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(scpReconService.list(status, keyword));
    }

    /** E1 收货信息导入 + E4 创建预制对账单：body 含 lines 明细 JSON */
    @OperLog(module = "COLLAB", operType = "ADD", desc = "SCP收货导入创建预制对账单")
    @RequiresPerm("collab:scp:recon:supplier")
    @PostMapping("/scp/recons")
    public Result<ScpRecon> importGoods(@RequestBody ScpRecon recon) {
        return Result.success(scpReconService.importGoods(recon));
    }

    /** E5 异议反馈（供应商）：body {note} */
    @RequiresPerm("collab:scp:recon:supplier")
    @PostMapping("/scp/recons/{id}/objection")
    public Result<ScpRecon> objection(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(scpReconService.objection(id, (String) body.get("note")));
    }

    /** E5 异议处理（内网对账员）：body {note} */
    @RequiresPerm("collab:scp:recon")
    @PostMapping("/scp/recons/{id}/resolve-objection")
    public Result<ScpRecon> resolveObjection(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(scpReconService.resolveObjection(id, (String) body.get("note")));
    }

    /** E6 确认对账单（打通点1）：body {invoiceType:增专|增普, taxRatePct?}（R-30） */
    @OperLog(module = "COLLAB", operType = "UPDATE", desc = "SCP确认对账单(打通点1)")
    @RequiresPerm("collab:scp:recon:supplier")
    @PostMapping("/scp/recons/{id}/confirm")
    public Result<ScpRecon> confirmRecon(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal rate = body.get("taxRatePct") == null ? null
                : new java.math.BigDecimal(String.valueOf(body.get("taxRatePct")));
        return Result.success(scpReconService.confirm(id, (String) body.get("invoiceType"), rate));
    }

    /** 作废对账单（未开票）：body {reason} */
    @RequiresPerm("collab:scp:recon")
    @PostMapping("/scp/recons/{id}/void")
    public Result<ScpRecon> voidRecon(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(scpReconService.voidRecon(id, body == null ? null : (String) body.get("reason")));
    }

    /** E7 申请开票（打通点2）：body {poNo?}，生成 ScpBill（R-31 预制发票号） */
    @OperLog(module = "COLLAB", operType = "ADD", desc = "SCP申请开票(打通点2)")
    @RequiresPerm("collab:scp:supplier")
    @PostMapping("/scp/recons/{id}/apply-invoice")
    public Result<ScpBill> applyInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(scpReconService.applyInvoice(id, body == null ? null : (String) body.get("poNo")));
    }

    /** R-17 手工匹配：body {reconAmount, reconTaxAmount, reconTotal}（人工核定基准后重匹配） */
    @RequiresPerm("collab:scp:match:manual")
    @PostMapping("/scp/bills/{id}/manual-match")
    public Result<ScpBill> manualMatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(scpFlowService.manualMatch(id,
                body.get("reconAmount") == null ? null : new java.math.BigDecimal(String.valueOf(body.get("reconAmount"))),
                body.get("reconTaxAmount") == null ? null : new java.math.BigDecimal(String.valueOf(body.get("reconTaxAmount"))),
                body.get("reconTotal") == null ? null : new java.math.BigDecimal(String.valueOf(body.get("reconTotal")))));
    }

    /** R-18 申请强制匹配（四眼第一步，单据进 FORCE_PENDING 锁定）：body {reason} */
    @RequiresPerm("collab:scp:force-appr")
    @PostMapping("/scp/bills/{id}/force-apply")
    public Result<ScpBill> forceApply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String operator = com.fssc.framework.security.UserContext.get() == null ? null
                : com.fssc.framework.security.UserContext.get().getUsername();
        return Result.success(scpFlowService.forceApply(id, (String) body.get("reason"), operator));
    }

    /** R-18 审批通过（分级审批做法：差额≤阈值需一级 force-appr；超阈值由二级岗 force-appr-l2 审批即可；四眼：审批人≠申请人） */
    @PostMapping("/scp/bills/{id}/force-approve")
    public Result<ScpBill> forceApprove(@PathVariable Long id) {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.getRequired();
        ScpBill b = scpFlowService.get(id);
        boolean l2 = scpForceServiceL2Required(b);
        String needed = l2 ? "collab:scp:force-appr-l2" : "collab:scp:force-appr";
        if (!hasPerm(u, needed)) {
            return Result.fail(4030, l2
                    ? "匹配差额超阈值，需二级审批权限：collab:scp:force-appr-l2"
                    : "缺少强匹审批权限：collab:scp:force-appr");
        }
        return Result.success(scpFlowService.approveForce(id, u.getUsername()));
    }

    /** R-18 审批驳回：body {reason} */
    @RequiresPerm("collab:scp:force-appr")
    @PostMapping("/scp/bills/{id}/force-reject")
    public Result<ScpBill> forceReject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.getRequired();
        return Result.success(scpFlowService.rejectForce(id, u.getUsername(), (String) body.get("reason")));
    }

    private boolean scpForceServiceL2Required(ScpBill b) {
        try {
            java.math.BigDecimal recon = b.getReconAmount() == null ? java.math.BigDecimal.ONE : b.getReconAmount();
            java.math.BigDecimal diff = b.getAmount() == null ? java.math.BigDecimal.ZERO
                    : b.getAmount().subtract(recon).abs();
            java.math.BigDecimal thresholdPct = new java.math.BigDecimal(configService.getValue("scp.force.diff-pct") == null ? "5" : configService.getValue("scp.force.diff-pct"));
            java.math.BigDecimal diffPct = diff.divide(recon.abs().max(java.math.BigDecimal.ONE), 6, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100));
            return diffPct.compareTo(thresholdPct) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasPerm(com.fssc.framework.security.LoginUser u, String perm) {
        return u.getPerms() != null && (u.getPerms().contains("*:*:*") || u.getPerms().contains(perm));
    }

    /** 预认证（内网发票处理查询，认证前置） */
    @RequiresPerm("collab:scp:certify")
    @PostMapping("/scp/bills/{id}/pre-certify")
    public Result<ScpBill> preCertify(@PathVariable Long id) {
        return Result.success(scpFlowService.preCertify(id));
    }

    /** 发票替换（蓝图 EBS 接口6）：旧票作废，同对账单生成新票（R-31）；body {reason} */
    @OperLog(module = "COLLAB", operType = "UPDATE", desc = "SCP发票替换")
    @RequiresPerm("collab:scp:replace")
    @PostMapping("/scp/bills/{id}/replace")
    public Result<ScpBill> replaceBill(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(scpFlowService.replaceBill(id, body == null ? null : (String) body.get("reason")));
    }

    /** 冲销凭证（蓝图 EBS 接口7）：记账成功回待匹配；body {reason} */
    @RequiresPerm("collab:scp:post")
    @PostMapping("/scp/bills/{id}/reverse-voucher")
    public Result<ScpBill> reverseVoucher(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(scpFlowService.reverseVoucher(id, body == null ? null : (String) body.get("reason")));
    }

    /** 直接扣款入账（蓝图 EBS 接口8）：body {deductAmount, reason} */
    @RequiresPerm("collab:scp:pay")
    @PostMapping("/scp/bills/{id}/direct-deduct")
    public Result<ScpBill> directDeduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal amt = body.get("deductAmount") == null ? null
                : new java.math.BigDecimal(String.valueOf(body.get("deductAmount")));
        return Result.success(scpFlowService.directDeduct(id, amt, (String) body.get("reason")));
    }

    // ---------------- CCP 客户端 ----------------

    @RequiresPerm("collab:ccp:manage")
    @GetMapping("/ccp/shippings")
    public Result<List<CcpShipping>> shippings(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(ccpFlowService.shippings(status, keyword));
    }

    /** 接口1：EBS 发运数据推送（CCP/定时抓取入口） */
    @OperLog(module = "COLLAB", operType = "ADD", desc = "EBS发运数据推送接收")
    @RequiresPerm("collab:ccp:manage")
    @PostMapping("/ccp/shippings")
    public Result<CcpShipping> pushShipping(@RequestBody CcpShipping shipping) {
        return Result.success(ccpFlowService.pushShipping(shipping));
    }

    /** 对账状态同步（链路步骤⑩）：?statementNo=ARB... */
    @RequiresPerm("collab:ccp:manage")
    @PostMapping("/ccp/recon-sync")
    public Result<Object> reconSync(@RequestParam String statementNo) {
        return Result.success(ccpFlowService.reconSync(statementNo));
    }

    /** 接口2/3：预制发票信息回写 EBS（失败人工重推入口） */
    @RequiresPerm("collab:ccp:manage")
    @PostMapping("/ccp/invoice-writeback/{preInvoiceId}")
    public Result<Object> invoiceWriteback(@PathVariable Long preInvoiceId) {
        return Result.success(ccpFlowService.invoiceWriteback(preInvoiceId));
    }

    // ---------------- 通用 ----------------

    /** 关账冻结状态（R-14/打通点7查询面）：SCP 发送侧按公司+期间校验 */
    @RequiresPerm("collab:freeze:view")
    @GetMapping("/freeze-status")
    public Result<Map<String, Object>> freezeStatus(@RequestParam(required = false) Long companyId,
                                                    @RequestParam(required = false) String period) {
        boolean frozen = apMonthCloseService.isFrozen(companyId == null ? 1L : companyId, period);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", companyId == null ? 1L : companyId);
        m.put("period", period);
        m.put("frozen", frozen);
        m.put("rule", frozen ? "R-14 关账期间禁止发送数据至EBS产生凭证" : "数据交互正常");
        return Result.success(m);
    }

    @RequiresPerm("collab:log:view")
    @GetMapping("/sync-logs")
    public Result<List<CollabSyncLog>> syncLogs(@RequestParam(required = false) String ifaceCode,
                                                @RequestParam(required = false) String status) {
        LambdaQueryWrapper<CollabSyncLog> q = new LambdaQueryWrapper<>();
        if (ifaceCode != null && !ifaceCode.isBlank()) {
            q.eq(CollabSyncLog::getIfaceCode, ifaceCode);
        }
        if (status != null && !status.isBlank()) {
            q.eq(CollabSyncLog::getStatus, status);
        }
        return Result.success(syncLogMapper.selectList(q.orderByDesc(CollabSyncLog::getId).last("LIMIT 200")));
    }
}
