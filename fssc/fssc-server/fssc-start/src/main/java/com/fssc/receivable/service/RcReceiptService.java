package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceiptLineMapper;
import com.fssc.receivable.mapper.RcReceiptMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 收款核销服务：
 * 1. 明细核销（推荐 #12）：收款单登记时落 rc_receipt_line 明细，
 *    每一行指定【应收单 + 本次核销金额】，applyReceipt 逐行校验 核销金额 ≤ 应收余额 后冲减。
 * 2. 历史兼容：无明细时沿用 receivableIds 逗号串按先进先出(FIFO)核销。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RcReceiptService {

    private final RcReceiptMapper receiptMapper;
    private final RcReceiptLineMapper receiptLineMapper;
    private final RcReceivableMapper receivableMapper;
    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** R5-D-097：公司维度读取守卫（rc_receipt 无 company_id 列，经源应收单派生过滤） */
    private final com.fssc.framework.org.OrgGuard orgGuard;

    /**
     * 收款核销列表（R5-D-097：公司维度读取过滤）。
     * rc_receipt 无 company_id 列（本批不补列，补列走人工裁决登记件
     * _w20_097_rc_receipt_company_id_ddl.sql），公司口径经源应收单 rc_receivable.company_id 派生，
     * 与 RcReceivableService.page 同守卫（OrgGuard.enforceCompany()）同口径。
     */
    public List<RcReceipt> list() {
        LambdaQueryWrapper<RcReceipt> qw = new LambdaQueryWrapper<RcReceipt>()
                .orderByDesc(RcReceipt::getId);
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.inSql(RcReceipt::getId, derivedCompanyScopeSql(scopeC));
        }
        return receiptMapper.selectList(qw);
    }

    /**
     * R5-D-097 收款单→公司归属派生 SQL（rc_receipt 无 company_id 列）：
     * 主通路＝核销明细 rc_receipt_line.receivable_id（create 两分支均落明细，机器路径 ArWriteoffService 同）；
     * 遗留通路＝rc_receipt.receivable_ids 逗号串（#12 明细核销上线前的历史行）。
     * 无从派生的行＝无公司归属，公司账号不可见（与 RcReceivableService.page 的 eq 口径一致）；
     * 集团账号 scopeC=null 不限制，由调用方保证不走本 SQL。
     */
    private static String derivedCompanyScopeSql(Long scopeC) {
        return "select rc.id from rc_receipt rc where exists ("
                + "select 1 from rc_receipt_line l join rc_receivable r on r.id = l.receivable_id"
                + " where l.receipt_id = rc.id and l.del_flag = 0 and r.del_flag = 0 and r.company_id = " + scopeC
                + ") or exists ("
                + "select 1 from rc_receivable r2 where r2.del_flag = 0 and r2.company_id = " + scopeC
                + " and find_in_set(r2.id, rc.receivable_ids))";
    }

    /**
     * 收款单登记：头（rc_receipt）＋明细（rc_receipt_line）两表成对写，且头状态直接置 WRITTEN_OFF。
     * 无事务边界时明细写失败会留下"有收款单、无核销明细、已称核销"的半截记录（ROOT-11 已裁口径：
     * 跨表写成对的方法补边界；本条属自定，无厂商条款可依）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(RcReceipt e) {
        e.setId(null);
        Long tenantId = TenantContext.getTenantId();
        e.setTenantId(tenantId);
        if (!StringUtils.hasText(e.getBillNo())) e.setBillNo(genBillNo(tenantId));
        // 必填兜底（治理批次 2B）：收款日期原先为空时静默补「今天」，用户选的日期从未生效
        // （前端曾把字段绑成实体不存在的 payDate）。机器路径（AR 资金核销 ArWriteoffService）始终显式赋值，故硬校验安全。
        if (e.getReceiptDate() == null) throw new BizException(ErrorCode.PARAM_INVALID, "请选择收款日期");
        if (e.getAmount() == null || e.getAmount().signum() <= 0) throw new BizException(ErrorCode.PARAM_INVALID, "请输入收款金额（须大于 0）");
        if (e.getCustomerId() == null) throw new BizException(ErrorCode.PARAM_INVALID, "请选择往来单位");
        LoginUser user = UserContext.getRequired();
        e.setOperatorId(user.getUserId());
        e.setStatus("WRITTEN_OFF");
        // 合规整改( )：无明细时按往来单位 OPEN 应收单先进先出自动核销——
        // 登记即核销与 WRITTEN_OFF 状态一致，修复"登记不冲减余额、事后 FIFO 无单可核"断层
        if (e.getLines() == null || e.getLines().isEmpty()) {
            List<RcReceivable> open = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                    .eq(RcReceivable::getCustomerId, e.getCustomerId())
                    .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                    // D-V4R-71（V5 册任务3 重跑 2026-10-15 实测）：候选只筛"余额>0"⇒ 一张从未确认
                    // （status=NONE，含流程卡死件）的应收会被收款喂满转 DONE，而同额、已走完审批的
                    // CONFIRMED 孪生单分文未收（实案：158,200 两笔收款把 RC…2259131/2259521/2300311
                    // 三枚 NONE 结清，RC…2314441/2316491/2317271 三枚 CONFIRMED 仍挂全额）。
                    // 口径与门户侧"已确认才进分母"对齐：未确认／被驳回的单不构成可收款债权。
                    // 用排除式而非枚举式——NORMAL（开票自动生成腿）与 OVERDUE（到期转逾期）都是合法可核销态。
                    .notIn(RcReceivable::getStatus, "NONE", "REJECTED")
                    .orderByAsc(RcReceivable::getDueDate)
                    .orderByAsc(RcReceivable::getId));
            BigDecimal rest0 = e.getAmount();
            if (e.getLines() == null) e.setLines(new java.util.ArrayList<>());
            for (RcReceivable r : open) {
                if (rest0.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal pay = r.getRemainAmount().compareTo(rest0) <= 0 ? r.getRemainAmount() : rest0;
                RcReceiptLine line = new RcReceiptLine();
                line.setReceivableId(r.getId());
                line.setOffsetAmount(pay);
                e.getLines().add(line);
                rest0 = rest0.subtract(pay);
            }
            if (rest0.compareTo(BigDecimal.ZERO) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "收款金额(" + e.getAmount() + ")超出该往来单位未核销应收余额(" + rest0.add(e.getAmount()) + ")");
            }
        }
        receiptMapper.insert(e);
        // #12 明细核销：如有子项则逐行落 rc_receipt_line（聚合校验金额，防超收/超余额）
        if (e.getLines() != null && !e.getLines().isEmpty()) {
            List<RcReceiptLine> lines = e.getLines();
            BigDecimal lineSum = BigDecimal.ZERO;
            for (int i = 0; i < lines.size(); i++) {
                RcReceiptLine line = lines.get(i);
                if (line.getReceivableId() == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行应收单不能为空");
                }
                BigDecimal amt = line.getOffsetAmount() == null ? BigDecimal.ZERO : line.getOffsetAmount();
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行核销金额必须大于 0");
                }
                lineSum = lineSum.add(amt);
                // 核销金额 ≤ 对应应收单余额（强勾稽，防超额核销）
                RcReceivable rec = receivableMapper.selectById(line.getReceivableId());
                if (rec == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "应收单不存在: " + line.getReceivableId());
                }
                BigDecimal balance = rec.getRemainAmount() == null ? BigDecimal.ZERO : rec.getRemainAmount();
                if (balance.compareTo(amt) < 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "第 " + (i + 1) + " 行核销金额(" + amt + ")超过应收单余额(" + balance + ")");
                }
                if (customerMismatch(rec, e.getCustomerId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "第 " + (i + 1) + " 行应收单(" + rec.getBillNo() + ")往来单位与此收款单不一致");
                }
                line.setTenantId(tenantId);
                line.setReceiptId(e.getId());
                line.setReceivableNo(rec.getBillNo());
                line.setStatus("WRITTEN_OFF");
            }
            if (lineSum.compareTo(e.getAmount()) != 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "明细核销合计(" + lineSum + ")与收款金额(" + e.getAmount() + ")不一致");
            }
            receiptLineMapper.insert(lines);
            // D-V3-143：登记即核销必须同步回写应收腿——received += amt / remain -= amt（归零置 DONE）。
            // 原缺陷只落 rc_receipt_line 不回写应收主表：①应收余额虚高；②下一笔收款的 FIFO 按
            // 未回写的 remain 再从已核销单起配（实测 M1 被双计核销 45,200）。
            for (RcReceiptLine line : lines) {
                RcReceivable rec = receivableMapper.selectById(line.getReceivableId());
                BigDecimal amt = line.getOffsetAmount();
                BigDecimal received = (rec.getReceivedAmount() == null ? BigDecimal.ZERO : rec.getReceivedAmount()).add(amt);
                BigDecimal remain = rec.getAmount().subtract(received);
                RcReceivable upd = new RcReceivable();
                upd.setId(rec.getId());
                upd.setReceivedAmount(received);
                upd.setRemainAmount(remain.max(BigDecimal.ZERO));
                if (upd.getRemainAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    upd.setStatus("DONE");
                }
                receivableMapper.updateById(upd);
            }
        }
        return e.getId();
    }

    /**
     * 已核销（终态）词表：这些状态＝本笔收款的冲减<b>已经发生</b>，{@link #applyReceipt} 只幂等跳过。
     * <p>D-DEC-423：登记腿 {@code create()} 自 D-V3-143 起在<b>同一事务</b>内既建明细又冲减应收腿并置
     * {@code WRITTEN_OFF}，故 WRITTEN_OFF 就是"已核销"终态（台账 甲案拟新增的 APPLIED 态已被
     * D-V3-143 的"登记即冲减"取代——若照原案把 WRITTEN_OFF 当"待冲减"再核销一次，就会二次冲减）。
     * 同义词 WRITE_OFF／APPLIED 一并认，避免旁路写点（{@code ExpenseWfEventListener} 还款收款腿）漏网。
     */
    private static final java.util.Set<String> APPLIED_RECEIPT_STATUSES =
            java.util.Set.of("WRITTEN_OFF", "WRITE_OFF", "APPLIED");

    /**
     * 核销执行（D-DEC-423①②：幂等守卫判据方向纠正）：
     * <p>原判据 {@code if (!"WRITTEN_OFF".equals(status)) throw "收款单已核销，不能重复核销"}
     * <b>方向写反</b>——登记时状态<b>已经</b>是 WRITTEN_OFF，条件恒 false ⇒ 该 throw 永不触发＝死码，
     * "重复核销"只由 {@link #applyByLines} 的余额算术校验兜住（实测 17:05:07 二次点击即如此）；
     * 一旦余额被别的通道推高（D-DEC-409 的 {@code received_amount} 双写通道），
     * 同一笔收款就能<b>再次核销成功</b>＝二次冲减。
     * <p>现口径：已核销终态＝幂等跳过（不再二次冲减、也不再靠余额碰运气）；
     * 其余状态＝非登记态，显式拒绝（措辞按真实语义改正）。
     * <p>有明细 → 逐行按各明细金额冲减（强校验 ≤ 余额）；无明细（历史）→ 按到期日+id 先进先出冲减，
     * 超出余额回滚拦截；remainAmount 减为 0 的应收单自动置 DONE。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyReceipt(Long receiptId) {
        RcReceipt receipt = require(receiptId);
        String status = receipt.getStatus();
        if (status != null && APPLIED_RECEIPT_STATUSES.contains(status.trim().toUpperCase())) {
            log.info("收款单 {}（{}）状态 {}＝登记时已核销（D-V3-143 同事务冲减应收腿），本次核销请求幂等跳过（D-DEC-423① 防二次冲减）",
                    receiptId, receipt.getBillNo(), status);
            return;
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "收款单 " + (receipt.getBillNo() == null ? receiptId : receipt.getBillNo())
                        + " 状态为 " + (status == null || status.isBlank() ? "空" : status)
                        + "，不是「已登记核销」态，不能执行核销（请先在「收款核销」tab 登记收款）");
    }

    /** 明细核销：逐行校验核销金额 ≤ 应收余额（防余额被并发核销超额），并冲减余额 */
    private void applyByLines(List<RcReceiptLine> lines) {
        for (RcReceiptLine line : lines) {
            RcReceivable rec = receivableMapper.selectById(line.getReceivableId());
            if (rec == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "应收单不存在: " + line.getReceivableId());
            }
            BigDecimal balance = nvl(rec.getRemainAmount());
            if (balance.compareTo(line.getOffsetAmount()) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "明细核销金额(" + line.getOffsetAmount() + ")超过应收单余额(" + balance + ")，不可全额核销");
            }
            updateReceivable(rec, line.getOffsetAmount());
        }
    }

    /** 历史刷新（无明细）：按 receivableIds 先进先出冲减，超出应收余额回滚拦截 */
    private void applyByFifo(RcReceipt receipt) {
        List<RcReceivable> receivables = resolveReceivables(receipt.getReceivableIds());
        if (receivables.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未指定可核销的应收单");
        }
        BigDecimal rest = receipt.getAmount();
        for (RcReceivable r : receivables) {
            if (rest.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal balance = nvl(r.getRemainAmount());
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal pay = balance.compareTo(rest) <= 0 ? balance : rest;
            updateReceivable(r, pay);
            rest = rest.subtract(pay);
        }
        if (rest.compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款金额超出应收余额，不可全部核销");
        }
    }

    /** 冲减单个应收单余额；减为 0 置 DONE 结清 */
    private void updateReceivable(RcReceivable r, BigDecimal pay) {
        BigDecimal balance = nvl(r.getRemainAmount());
        BigDecimal newRemain = balance.subtract(pay);
        BigDecimal newReceived = nvl(r.getReceivedAmount()).add(pay);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("receivedAmount", r.getReceivedAmount());
        auditBefore.put("remainAmount", r.getRemainAmount());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("receivedAmount", newReceived);
        auditAfter.put("remainAmount", newRemain);
        RcReceivable u = new RcReceivable();
        u.setId(r.getId());
        u.setReceivedAmount(newReceived);
        u.setRemainAmount(newRemain);
        if (newRemain.compareTo(BigDecimal.ZERO) == 0) {
            auditBefore.put("status", r.getStatus());
            auditAfter.put("status", "DONE");
            u.setStatus("DONE");
        }
        receivableMapper.updateById(u);
        auditTrailService.recordUpdate("RC_RECEIVABLE", r.getId(), r.getBillNo(), "rc_receivable",
                String.valueOf(r.getId()), auditBefore, auditAfter, "收款核销冲减应收余额");
    }

    private boolean customerMismatch(RcReceivable rec, Long customerId) {
        return customerId != null && !customerId.equals(rec.getCustomerId());
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 返回收款单的核销明细（供前端回显）；R5-D-097：详情按 list 同派生口径守卫，防按 id 直查绕过数据范围 */
    public List<RcReceiptLine> lines(Long receiptId) {
        require(receiptId);
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null && !visibleInCompany(receiptId, scopeC)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看他公司收款单（id " + receiptId + "）");
        }
        return receiptLineMapper.selectList(new LambdaQueryWrapper<RcReceiptLine>()
                .eq(RcReceiptLine::getReceiptId, receiptId)
                .orderByAsc(RcReceiptLine::getId));
    }

    /** R5-D-097：收款单在公司维度是否可见（复用 list 同款派生 SQL 的单据版） */
    private boolean visibleInCompany(Long receiptId, Long scopeC) {
        Long hit = receiptMapper.selectCount(new LambdaQueryWrapper<RcReceipt>()
                .eq(RcReceipt::getId, receiptId)
                .inSql(RcReceipt::getId, derivedCompanyScopeSql(scopeC)));
        return hit != null && hit > 0;
    }

    /** 解析逗号分隔的应收单id，并按借款到期的先进先出排序 */
    private List<RcReceivable> resolveReceivables(String receivableIds) {
        List<RcReceivable> result = new ArrayList<>();
        if (!StringUtils.hasText(receivableIds)) return result;
        for (String s : receivableIds.split(",")) {
            String id = s.trim();
            if (id.isEmpty()) continue;
            try {
                Long rid = Long.parseLong(id);
                RcReceivable r = receivableMapper.selectById(rid);
                if (r != null) result.add(r);
            } catch (NumberFormatException ignored) {
                // 忽略非法id
            }
        }
        result.sort((a, b) -> {
            int c = compareTo(a.getDueDate(), b.getDueDate());
            if (c != 0) return c;
            return Long.compare(a.getId(), b.getId());
        });
        return result;
    }

    private RcReceipt require(Long id) {
        RcReceipt r = receiptMapper.selectById(id);
        if (r == null) throw new BizException(ErrorCode.PARAM_INVALID, "收款单不存在: " + id);
        return r;
    }

    private static int compareTo(LocalDate a, LocalDate b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private String genBillNo(Long tenantId) {
        return "SK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + (tenantId == null ? "" : String.valueOf(tenantId % 1000));
    }
}