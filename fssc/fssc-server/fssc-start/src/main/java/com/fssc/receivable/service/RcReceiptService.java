package com.fssc.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceiptLineMapper;
import com.fssc.receivable.mapper.RcReceiptMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 公司维度读取守卫（rc_receipt 无 company_id 列，经源应收单派生过滤） */
    private final OrgGuard orgGuard;

    /**
     * 收款核销列表（公司维度读取过滤）。
     * rc_receipt 无 company_id 列（本批不补列，补列走产品确认登记件
     * _w20_097_rc_receipt_company_id_ddl.sql），公司范围经源应收单 rc_receivable.company_id 派生，
     * 与 RcReceivableService.page 同守卫（OrgGuard.enforceCompany()）同一规则。
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
     * 收款单→公司归属派生 SQL（rc_receipt 无 company_id 列）：
     * 主通路＝核销明细 rc_receipt_line.receivable_id（create 两分支均落明细，机器路径 ArWriteoffService 同）；
     * 遗留通路＝rc_receipt.receivable_ids 逗号串（#12 明细核销上线前的历史行）。
     * 无从派生的行＝无公司归属，公司账号不可见（与 RcReceivableService.page 的 eq 规则一致）；
     * 集团账号 scopeC=null 不限制，由调用方保证不走本 SQL。
     *
     * <p><b>2026-11-09 性能修复（P0）</b>：原写法是一个 OR 连接的两个相关 EXISTS，其中遗留通路
     * {@code find_in_set(r2.id, rc.receivable_ids)} 完全用不上索引——优化器只能拿每张收款单去
     * 扫一遍该公司名下全部应收（压测 7 万收款 × 9 万应收 ≈ 六亿次比较，实测 16 分钟不返回）。
     * 现改为两条分支 {@code UNION}：
     * <ol>
     *   <li>明细通路照旧走 {@code rc_receipt_line.receipt_id} / {@code rc_receivable.company_id}；</li>
     *   <li>遗留通路先用 {@code NOT EXISTS(明细)} 把外层收窄到"真的没有明细的历史行"
     *       （#12 之后新单必有明细，这一支通常只剩个位数），再逐行 find_in_set。</li>
     * </ol>
     * 口径：一张单既有明细、又在 receivable_ids 里挂着别家公司的旧串时，**以明细为准**
     * （明细是核销事实，逗号串只是历史残留）。UNION 自带去重，与原 OR 的语义等价。
     */
    private static String derivedCompanyScopeSql(Long scopeC) {
        return "select rc.id from rc_receipt rc where exists ("
                + "select 1 from rc_receipt_line l join rc_receivable r on r.id = l.receivable_id"
                + " where l.receipt_id = rc.id and l.del_flag = 0 and r.del_flag = 0 and r.company_id = " + scopeC
                + ") union "
                + "select rc.id from rc_receipt rc where not exists ("
                + "select 1 from rc_receipt_line l2 where l2.receipt_id = rc.id and l2.del_flag = 0"
                + ") and exists ("
                + "select 1 from rc_receivable r2 where r2.del_flag = 0 and r2.company_id = " + scopeC
                + " and find_in_set(r2.id, rc.receivable_ids))";
    }

    /**
     * 收款单登记：头（rc_receipt）＋明细（rc_receipt_line）两表成对写，且头状态直接置 WRITTEN_OFF。
     * 无事务边界时明细写失败会留下"有收款单、无核销明细、已称核销"的半截记录（跨表写成对的方法补边界；
     * 本条属自定规则）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(RcReceipt e) {
        e.setId(null);
        Long tenantId = TenantContext.getTenantId();
        e.setTenantId(tenantId);
        if (!StringUtils.hasText(e.getBillNo())) e.setBillNo(genBillNo(tenantId));
        // 必填兜底：收款日期原先为空时静默补「今天」，用户选的日期从未生效
        // （前端曾把字段绑成实体不存在的 payDate）。机器路径（AR 资金核销 ArWriteoffService）始终显式赋值，故硬校验安全。
        if (e.getReceiptDate() == null) throw new BizException(ErrorCode.PARAM_INVALID, "请选择收款日期");
        if (e.getAmount() == null || e.getAmount().signum() <= 0) throw new BizException(ErrorCode.PARAM_INVALID, "请输入收款金额（须大于 0）");
        if (e.getCustomerId() == null) throw new BizException(ErrorCode.PARAM_INVALID, "请选择往来单位");
        LoginUser user = UserContext.getRequired();
        e.setOperatorId(user.getUserId());
        e.setStatus("WRITTEN_OFF");
        // 合规无明细时按往来单位 OPEN 应收单先进先出自动核销——
        // 登记即核销与 WRITTEN_OFF 状态一致，修复"登记不冲减余额、事后 FIFO 无单可核"断层
        Long scopeC = orgGuard.enforceCompany();
        if (e.getLines() == null || e.getLines().isEmpty()) {
            List<RcReceivable> open = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                    .eq(RcReceivable::getCustomerId, e.getCustomerId())
                    // FIFO 候选必须限定在建单人所属法人主体内：客户主数据全租户共享，同一往来单位
                    // 在别的法人主体下也有应收，不限主体就会拿 1 号主体的收款去冲 35 号主体的应收——
                    // 收款进 1 号主体的银行账、应收却减 35 号主体，账务上不成立，账龄与坏账也跟着失真。
                    // 规则与读取侧同守卫（RcReceivableService.page 的 eq(companyId, scopeC)、本类 list()
                    // 的 derivedCompanyScopeSql）：看不见的单也不该被自动核销；集团级账号 scopeC=null 不限制。
                    .eq(scopeC != null, RcReceivable::getCompanyId, scopeC)
                    .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                    // 候选只筛"余额>0"不够：一张从未确认（status=NONE，含流程卡死件）的应收会被收款
                    // 喂满转 DONE，而同额、已走完审批的 CONFIRMED 孪生单分文未收。
                    // 规则与门户侧"已确认才进分母"对齐：未确认／被驳回的单不构成可收款债权。
                    // 用排除式而非枚举式——NORMAL（开票自动生成环节）与 OVERDUE（到期转逾期）都是合法可核销态。
                    .notIn(RcReceivable::getStatus, "NONE", "REJECTED")
                    .orderByAsc(RcReceivable::getDueDate)
                    .orderByAsc(RcReceivable::getId));
            BigDecimal rest0 = e.getAmount();
            if (e.getLines() == null) e.setLines(new ArrayList<>());
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
                // 此处 rest0 是「自动核销后仍未被任何应收单吃下的余量」，
                // 真实未核销余额 = 本次收款额 − rest0（等价于 open 各单 remain 合计）。
                // 旧文案误写成 rest0 + e.getAmount()＝2×收款额 − 余额，把可用额虚增（N2 实证：
                // 收款 85,000、真实可核销余额 0，却回显 170,000），会误导操作人以为还有额度。
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "收款金额(" + e.getAmount() + ")超出该往来单位未核销应收余额("
                                + e.getAmount().subtract(rest0) + ")"
                                // 候选加了法人主体校验后，"余额"是本主体规则的余额；
                                // 不点破这一层，操作人会拿全租户余额对不上号（他公司同客户的应收不计入）。
                                + (scopeC != null ? "（仅统计公司 " + scopeC + " 本法人主体的应收；"
                                        + "他公司同往来单位的应收不参与核销）" : ""));
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
                // 指定应收单核销同样不得跨法人主体——FIFO 候选加了公司校验，
                // 手工指定通道若无同一道校验就成旁路（换一条路照样串账）。
                if (scopeC != null && rec.getCompanyId() != null && !scopeC.equals(rec.getCompanyId())) {
                    throw new BizException(ErrorCode.FORBIDDEN,
                            "第 " + (i + 1) + " 行应收单(" + rec.getBillNo() + ")属于公司 " + rec.getCompanyId()
                                    + "，与本收款单的法人主体（公司 " + scopeC + "）不一致，不能跨法人核销；"
                                    + "请在该公司下登记收款，或改用集团级账号");
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
            // 登记即核销必须同步回写应收环节——received += amt / remain -= amt（归零置 DONE）。
            // 只落 rc_receipt_line 不回写主表会让①应收余额虚高；②下一笔收款的 FIFO 按未回写的
            // remain 再从已核销单起配，同一张应收被核销两遍。
            for (RcReceiptLine line : lines) {
                RcReceivable rec = receivableMapper.selectById(line.getReceivableId());
                BigDecimal amt = line.getOffsetAmount();
                BigDecimal received = (rec.getReceivedAmount() == null ? BigDecimal.ZERO : rec.getReceivedAmount()).add(amt);
                // 余额必须按当前 remain 冲减：坏账核销会直接下调 remain，若按 amount-received 重算会把它加回来
                BigDecimal balance = rec.getRemainAmount() == null ? BigDecimal.ZERO : rec.getRemainAmount();
                BigDecimal remain = balance.subtract(amt).max(BigDecimal.ZERO);
                RcReceivable upd = new RcReceivable();
                upd.setId(rec.getId());
                upd.setReceivedAmount(received);
                upd.setRemainAmount(remain);
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    upd.setStatus("DONE");
                }
                receivableMapper.updateById(upd);
            }
            // 单头冗余列 receivable_ids 必须与明细同源回填。
            // 该列不是历史包袱——银企对账（BankReconcileService 的银行流水↔应收勾稽）与 LinkU 开放查询
            // （LinkuOpenQueryMapper 用 FIND_IN_SET(rr.id, r.receivable_ids) 反推公司）都还在读它，
            // 而新建的收款单该列恒 NULL ⇒ 这两条链只看得到老单。所以规则取"回填"而非"删列"。
            // 顺序＝明细行顺序（自动 FIFO 即到期日+id 升序），去重后逗号拼接，与 resolveReceivables 的解析规则一致。
            String ids = lines.stream()
                    .map(l -> String.valueOf(l.getReceivableId()))
                    .distinct()
                    .collect(Collectors.joining(","));
            RcReceipt head = new RcReceipt();
            head.setId(e.getId());
            head.setReceivableIds(ids);
            receiptMapper.updateById(head);
            e.setReceivableIds(ids);
        }
        return e.getId();
    }

    /**
     * 已核销（最终状态）名称表：这些状态＝本笔收款的冲减<b>已经发生</b>，{@link #applyReceipt} 只去重跳过。
     * <p>登记环节 {@code create()} 在同一事务内在<b>同一事务</b>内既建明细又冲减应收环节并置
     * {@code WRITTEN_OFF}，故 WRITTEN_OFF 就是"已核销"最终状态（原案拟新增的 APPLIED 态已被
     * 的"登记即冲减"取代——若再把 WRITTEN_OFF 当"待冲减"再核销一次，就会二次冲减）。
     * 同义词 WRITE_OFF／APPLIED 一并认，避免旁路写点（{@code ExpenseWfEventListener} 还款收款环节）漏网。
     */
    private static final Set<String> APPLIED_RECEIPT_STATUSES =
            Set.of("WRITTEN_OFF", "WRITE_OFF", "APPLIED");

    /**
     * 核销执行：
     * <p>去重守卫的方向：登记时状态<b>已经</b>是 WRITTEN_OFF，所以判据不能写成
     * {@code if (!"WRITTEN_OFF".equals(status)) throw}——条件恒 false，throw 永不触发，成了死码，
     * "重复核销"只剩 {@link #applyByLines} 的余额算术兜着；余额一旦被别的通道推高，
     * 同一笔收款就能<b>再次核销成功</b>＝二次冲减。
     * <p>规则：已核销最终状态＝去重跳过（不再二次冲减，也不靠余额碰运气）；
     * 其余状态＝非登记态，显式拒绝（措辞按真实语义）。
     * <p>有明细 → 逐行按各明细金额冲减（强校验 ≤ 余额）；无明细（历史）→ 按到期日+id 先进先出冲减，
     * 超出余额回滚拦截；remainAmount 减为 0 的应收单自动置 DONE。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyReceipt(Long receiptId) {
        RcReceipt receipt = require(receiptId);
        String status = receipt.getStatus();
        if (status != null && APPLIED_RECEIPT_STATUSES.contains(status.trim().toUpperCase())) {
            log.info("收款单 {}（{}）状态 {}＝登记时已核销，本次核销请求幂等跳过",
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
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("receivedAmount", r.getReceivedAmount());
        auditBefore.put("remainAmount", r.getRemainAmount());
        Map<String, Object> auditAfter = new LinkedHashMap<>();
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

    /** 返回收款单的核销明细（供前端回显）；详情按 list 同派生规则守卫，防按 id 直查绕过数据范围 */
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

    /** 收款单在公司维度是否可见（复用 list 同款派生 SQL 的单据版） */
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