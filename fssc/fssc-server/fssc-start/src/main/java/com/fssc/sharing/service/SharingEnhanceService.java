// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.service.ArchiveDestroyService;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.sharing.entity.OpBankStatement;
import com.fssc.sharing.entity.OpQuality;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpBankStatementMapper;
import com.fssc.sharing.mapper.OpQualityMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享运营增强：质检（op_quality）+ 档案归档（op_archive）+ 银行流水（op_bank_statement）。
 */
@Service
@RequiredArgsConstructor
public class SharingEnhanceService {

    private final OpQualityMapper qualityMapper;
    private final ArchiveFacade archiveFacade;
    private final OpBankStatementMapper bankStatementMapper;
    private final MdBankAccountMapper bankAccountMapper;
    private final ExpBillMapper expBillMapper;
    /** #56 甲档 W6：档案裸绑入口的 voucherId 存在性＋同租户校验 */
    private final OpVoucherMapper voucherMapper;
    /**
     * #56 甲档 W6：增强页的档案登记/更新直接把 OpArchive 绑进请求体，
     * voucherId 今天就能从 JSON 塞任意值 ⇒ 写入数据库前必须验凭证存在且同租户，否则扩面等于开了脏写入口。
     */
    private void assertVoucherBinding(Long voucherId) {
        if (voucherId == null) {
            return;
        }
        OpVoucher v = voucherMapper.selectById(voucherId);
        if (v == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        }
        Long mine = TenantContext.getTenantId();
        if (mine != null && !mine.equals(v.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不属于当前租户: " + voucherId);
        }
    }

    // 质检

    public PageResult<OpQuality> qualityPage(long pageNum, long pageSize, Long billId, String result) {
        LambdaQueryWrapper<OpQuality> qw = new LambdaQueryWrapper<>();
        if (billId != null) {
            qw.eq(OpQuality::getBillId, billId);
        }
        if (result != null && !result.isBlank()) {
            qw.eq(OpQuality::getResult, result);
        }
        qw.orderByDesc(OpQuality::getId);
        Page<OpQuality> p = qualityMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long addQuality(OpQuality q) {
        // 必填办结（批次 2C，与前端 SharingQuality 弹窗同一规则）：抽检方式/结果原先留空即静默记 RANDOM/PASS
        AssertUtil.notBlank(q.getBillNo(), ErrorCode.PARAM_INVALID, "请输入单据编号");
        AssertUtil.notBlank(q.getCheckType(), ErrorCode.PARAM_INVALID, "请选择抽检方式");
        AssertUtil.notBlank(q.getResult(), ErrorCode.PARAM_INVALID, "请选择质检结果");
        AssertUtil.notNull(q.getScore(), ErrorCode.PARAM_INVALID, "请输入得分（0-100）");
        if (q.getScore() < 0 || q.getScore() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "得分应为 0-100");
        }
        // 弹窗只录单据编号，bill_id 由编号反查（历史上直接拦「被质检单据必填」⇒ 新增质检永远提交不了）
        if (q.getBillId() == null) {
            ExpBill bill = expBillMapper.selectOne(new LambdaQueryWrapper<ExpBill>()
                    .eq(ExpBill::getBillNo, q.getBillNo()).last("limit 1"));
            if (bill == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "单据编号不存在: " + q.getBillNo());
            }
            q.setBillId(bill.getId());
        }
        q.setId(null);
        // checker_id NOT NULL：缺省用当前操作人
        if (q.getCheckerId() == null) {
            LoginUser u = UserContext.get();
            q.setCheckerId(u == null ? 0L : u.getUserId());
        }
        if (q.getAuditorId() == null) {
            q.setAuditorId(q.getCheckerId());
        }
        qualityMapper.insert(q);
        return q.getId();
    }

    /** 更新质检评分 */
    public void updateQuality(OpQuality q) {
        if (qualityMapper.selectById(q.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "质检记录不存在: " + q.getId());
        }
        // 编辑弹窗提交完整对象，故与新增同一规则拦空（否则清空一项反而被 updateById 跳过、界面与库不一致）
        AssertUtil.notBlank(q.getBillNo(), ErrorCode.PARAM_INVALID, "请输入单据编号");
        AssertUtil.notBlank(q.getCheckType(), ErrorCode.PARAM_INVALID, "请选择抽检方式");
        AssertUtil.notBlank(q.getResult(), ErrorCode.PARAM_INVALID, "请选择质检结果");
        AssertUtil.notNull(q.getScore(), ErrorCode.PARAM_INVALID, "请输入得分（0-100）");
        if (q.getScore() < 0 || q.getScore() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "得分应为 0-100");
        }
        qualityMapper.updateById(q);
    }

    /** 删除质检记录（逻辑删） */
    public void deleteQuality(Long id) {
        qualityMapper.deleteById(id);
    }

    // 档案归档

    /** 档案列表（档案域单点：筛选口径＋状态轴折算＋归档流补充都在 Facade 内）。 */
    public PageResult<OpArchive> archivePage(long pageNum, long pageSize, String archiveType, String status) {
        return archiveFacade.pageArchives(pageNum, pageSize, archiveType, status);
    }

    public Long createArchive(OpArchive a) {
        // 跨域前置留在调用方：凭证存在性/同租户（glex 域）＋按来源单据回填 procInstId（费用域）
        assertVoucherBinding(a.getVoucherId());
        // 登记时按 billId 回溯来源审批流程实例（规则同 TRC-C 回填）；
        // 只给 refId/billId 为空的档案不猜归属，留 NULL
        if (a.getProcInstId() == null && a.getBillId() != null) {
            ExpBill src = expBillMapper.selectById(a.getBillId());
            if (src != null) {
                a.setProcInstId(src.getProcInstId());
            }
        }
        // 档案域内规则（编号查重/释放、status 镜像兜底、filingTime 兜底、插入）收在 Facade
        return archiveFacade.createArchive(a);
    }

    /** 更新档案信息 */
    public void updateArchive(OpArchive a) {
        assertVoucherBinding(a.getVoucherId());
        archiveFacade.updateArchive(a);
    }

    /**
     * 内控红线，销毁不可逆：原先本方法直置 archive_status=DISPOSED＋destroyed_at——
     * 无到期校验、无销毁审批、不起 FM_DESTROY，是与 ArchiveDestroyService 合规链互不相容的第二条旁路。
     * 现整体改道：一律转 {@link ArchiveDestroyService#createDestroyRequest}
     * 发起销毁申请（assertAllExpired 到期校验＋双人复核双签＋FM_DESTROY 起流程），本方法不再直改任何销毁列。
     */
    public Long destroyArchive(Long id, String reason) {
        LoginUser cu = UserContext.get();
        return archiveFacade.requestDestroy(List.of(id), reason, cu == null ? null : cu.getUserId());
    }

    // 银行流水（对账底稿）

    public PageResult<OpBankStatement> statementPage(long pageNum, long pageSize,
                                                     Long bankAccountId, String reconStatus,
                                                     String bankSerial, String transType) {
        LambdaQueryWrapper<OpBankStatement> qw = new LambdaQueryWrapper<>();
        if (bankAccountId != null) {
            qw.eq(OpBankStatement::getBankAccountId, bankAccountId);
        }
        if (reconStatus != null && !reconStatus.isBlank()) {
            qw.eq(OpBankStatement::getReconStatus, reconStatus);
        }
        // 161b（流水号环节）：SharingStatement.vue 的「流水号」输入框一直在发 bankSerial，
        // 端点却没声明该入参 ⇒ 参数被丢、筛选静默哑火（列表恒等全量，用户只觉"输了没反应"）。
        // 按 like 处理：流水号常带前后缀/年份分段，整串精确匹配会让"只记得后几位"直接查空。
        if (bankSerial != null && !bankSerial.isBlank()) {
            qw.like(OpBankStatement::getBankSerial, bankSerial.trim());
        }
        // 161b（交易方向环节）：页面上下拉发 DEBIT/CREDIT，而写入数据库值取决于导入通道——
        // CSV 批量导入是原样落 trans_type（本类 {@code importStatementCsv} 里的
        // `s.setTransType(emptyToNull(cell(col, 2)))`），手工态与本域读数侧
        // （BankReconcileService.isCreditStatement、TrCreditBillService.IN_TYPES、
        //  BankSlip.vue 的 transText 名称表）都认 CREDIT/RECEIPT/IN 为收、DEBIT/OUT/PAY/PAYMENT 为付。
        // 故按同义集合 in，避免"选了贷方、库里写 RECEIPT ⇒ 恒空"的第二次哑火。
        List<String> directions = transTypeAliases(transType);
        if (!directions.isEmpty()) {
            qw.in(OpBankStatement::getTransType, directions);
        }
        qw.orderByDesc(OpBankStatement::getTransDate);
        Page<OpBankStatement> p = bankStatementMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 161b：交易方向入参 → 同义写入数据库值集合（规则与本域三处读数点对齐，见 {@code statementPage} 注释）。
     * 非 DEBIT/CREDIT 的原样值（如库里存的中文"收"）按单值精确匹配，不做臆测扩展；空白返回空集＝不加条件。
     */
    private static List<String> transTypeAliases(String transType) {
        if (transType == null || transType.isBlank()) {
            return List.of();
        }
        String t = transType.trim();
        if ("DEBIT".equalsIgnoreCase(t)) {
            return List.of("DEBIT", "OUT", "PAY", "PAYMENT");
        }
        if ("CREDIT".equalsIgnoreCase(t)) {
            return List.of("CREDIT", "RECEIPT", "IN");
        }
        return List.of(t);
    }

    /** 导入一条银行流水（uk_tenant_serial 防重；重复导入去重忽略） */
    @Transactional(rollbackFor = Exception.class)
    public void importStatement(OpBankStatement s) {
        // 必填办结（批次 2C，与前端 GenericList「导入银行流水」弹窗同一规则，字段要求对齐 CSV 批量导入）：
        // 流水号是唯一键、账户与公司决定这笔流水落在谁的账上、金额 0 与无日期都会让后续对账失效
        AssertUtil.notNull(s.getCompanyId(), ErrorCode.PARAM_INVALID, "请选择公司");
        AssertUtil.notNull(s.getBankAccountId(), ErrorCode.PARAM_INVALID, "请选择银行账户");
        AssertUtil.notBlank(s.getBankSerial(), ErrorCode.PARAM_INVALID, "请输入银行流水号");
        AssertUtil.notNull(s.getTransDate(), ErrorCode.PARAM_INVALID, "请选择交易日期");
        AssertUtil.isTrue(s.getAmount() != null && s.getAmount().signum() > 0,
                ErrorCode.PARAM_INVALID, "请输入金额（须大于 0）");
        long dup = bankStatementMapper.selectCount(new LambdaQueryWrapper<OpBankStatement>()
                .eq(OpBankStatement::getBankAccountId, s.getBankAccountId())
                .eq(OpBankStatement::getBankSerial, s.getBankSerial()));
        if (dup > 0) {
            return; // 去重
        }
        s.setId(null);
        if (s.getReconStatus() == null) {
            s.setReconStatus("UNRECON");
        }
        // 导入记录——MetaObjectHandler 取不到登录人会落 0，这里显式回填真实操作人
        Long operator = currentOperator();
        if (operator != null) {
            s.setCreateBy(operator);
            s.setUpdateBy(operator);
        }
        bankStatementMapper.insert(s);
    }

    /** 当前登录人（无上下文返回 null，交由 MetaObjectHandler 兜底） */
    private static Long currentOperator() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }

    /** 银行流水 CSV 导入模板（列顺序与「导出 CSV」一致；# 与表头行自动跳过）。 */
    public static final String STATEMENT_CSV_TEMPLATE = String.join("\n",
            "# 银行流水导入模板（列）：银行流水号,交易时间,方向(DEBIT借/CREDIT贷),金额,余额,对方户名,对方账号,摘要,对账状态,回单状态",
            "# 交易时间格式 yyyy-MM-dd HH:mm:ss；金额必填；重复流水号按所选银行账户自动跳过；# 开头与表头行自动跳过",
            "bankSerial,transDate,transType,amount,balance,oppositeName,oppositeAccount,summary,reconStatus,slipStatus",
            "BS202610001,2026-10-05 09:30:00,CREDIT,12000.00,,客户回款,6222001,货款,UNRECON,NONE");

    /**
     * 批量导入银行流水（multipart CSV）。company_id / bank_account_id 按所选银行账户回填；
     * uk_tenant_serial 去重去重；逐行容错，返回 {total,inserted,skipped,errors[]}。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importStatementCsv(MultipartFile file, Long bankAccountId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择 CSV 文件");
        }
        if (bankAccountId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择目标银行账户");
        }
        MdBankAccount account = bankAccountMapper.selectById(bankAccountId);
        if (account == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行账户不存在: " + bankAccountId);
        }
        String csvText;
        try {
            csvText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "读取上传文件失败: " + ex.getMessage());
        }
        int total = 0;
        int inserted = 0;
        int skipped = 0;
        Long operator = currentOperator();
        List<String> errors = new ArrayList<>();
        for (String raw : csvText.replace("\uFEFF", "").split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> col = splitCsvLine(line);
            String bankSerial = cell(col, 0);
            if ("bankSerial".equalsIgnoreCase(bankSerial) || "银行流水号".equals(bankSerial)) {
                continue; // 表头行
            }
            total++;
            if (bankSerial.isEmpty()) {
                errors.add("银行流水号必填");
                continue;
            }
            LocalDateTime transDate;
            try {
                transDate = parseDateTime(cell(col, 1));
            } catch (Exception ex) {
                // 行级解析失败累积进导入错误清单，不中断整表处理
                errors.add("[" + bankSerial + "] 交易时间格式错误(" + cell(col, 1) + ")，应为 yyyy-MM-dd HH:mm:ss");
                continue;
            }
            if (transDate == null) {
                errors.add("[" + bankSerial + "] 交易时间必填");
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(cell(col, 3));
            } catch (Exception ex) {
                // 同上：单行金额非法只记行级错误，不中断整表导入
                errors.add("[" + bankSerial + "] 金额无效(" + cell(col, 3) + ")");
                continue;
            }
            long dup = bankStatementMapper.selectCount(new LambdaQueryWrapper<OpBankStatement>()
                    .eq(OpBankStatement::getBankAccountId, bankAccountId)
                    .eq(OpBankStatement::getBankSerial, bankSerial));
            if (dup > 0) {
                skipped++;
                continue;
            }
            OpBankStatement s = new OpBankStatement();
            s.setId(null);
            s.setBankAccountId(bankAccountId);
            s.setCompanyId(account.getCompanyId());
            s.setBankSerial(bankSerial);
            s.setTransDate(transDate);
            s.setTransType(emptyToNull(cell(col, 2)));
            s.setAmount(amount);
            s.setBalance(parseDecimalOrNull(cell(col, 4)));
            s.setOppositeName(emptyToNull(cell(col, 5)));
            s.setOppositeAccount(emptyToNull(cell(col, 6)));
            s.setSummary(emptyToNull(cell(col, 7)));
            String recon = emptyToNull(cell(col, 8));
            s.setReconStatus(recon == null ? "UNRECON" : recon);
            String slip = emptyToNull(cell(col, 9));
            if (slip != null) {
                s.setSlipStatus(slip);
            }
            // CSV 批量导入逐行记录导入人（同一次上传同一人，循环外取一次）
            if (operator != null) {
                s.setCreateBy(operator);
                s.setUpdateBy(operator);
            }
            bankStatementMapper.insert(s);
            inserted++;
        }
        if (total == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "CSV 无有效数据行（列：银行流水号,交易时间,方向,金额,余额,对方户名,对方账号,摘要,对账状态,回单状态）");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        // 记录规则：op_bank_statement 列面无 create_by/update_by（DDL 转人工），
        // 批量导入的逐批归属改由本次导入的审计记录承载——文件名＋行数＋操作人随响应落
        // sys_oper_log.json_result（@OperLog 批量导入银行流水），供「这批流水是哪次上传、
        // 由谁传的」回溯；行数含 total/inserted/skipped 三规则。
        result.put("fileName", file.getOriginalFilename());
        result.put("operator", operator);
        result.put("total", total);
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    /** 极简 CSV 行切分：支持双引号包裹与 "" 转义（容忍户名/摘要含逗号）。 */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out;
    }

    private static String cell(List<String> col, int idx) {
        return idx < col.size() ? col.get(idx).trim() : "";
    }

    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    private static BigDecimal parseDecimalOrNull(String v) {
        if (v == null || v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 宽松日期时间解析：接受 yyyy-MM-dd / yyyy/M/d，带或不带 HH:mm:ss，ISO 的 T 分隔，毫秒可缺省。 */
    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        int dot = t.indexOf('.');
        if (dot > 0) {
            t = t.substring(0, dot); // 去毫秒
        }
        t = t.replace('T', ' ').replace('/', '-');
        String[] dt = t.split(" ");
        String[] dparts = dt[0].split("-");
        String[] tparts = dt.length > 1 ? dt[1].split(":") : new String[]{"0", "0", "0"};
        if (dparts.length < 3) {
            throw new IllegalArgumentException("bad date: " + text);
        }
        return LocalDateTime.of(
                Integer.parseInt(dparts[0]), Integer.parseInt(dparts[1]), Integer.parseInt(dparts[2]),
                Integer.parseInt(tparts[0]),
                tparts.length > 1 ? Integer.parseInt(tparts[1]) : 0,
                tparts.length > 2 ? Integer.parseInt(tparts[2]) : 0);
    }

    /**
     * 对账标记（手工环节）。
     * <p>入参只有一枚 reconBillId，接口没带它属于哪张表 ⇒ 判别列无处可取，只能显式写 NULL：
     * 用 updateById 会跳过空值、把上一次自动勾对留下的 type 残留给一个新指针（等于配错表），
     * 而 NULL 正是"读取侧退回原有按上下文推断"的既定语义。
     */
    public void markRecon(Long id, Long reconBillId) {
        OpBankStatement exist = bankStatementMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流水不存在: " + id);
        }
        bankStatementMapper.update(null, new LambdaUpdateWrapper<OpBankStatement>()
                .eq(OpBankStatement::getId, id)
                .set(OpBankStatement::getReconStatus, "RECONED")
                .set(OpBankStatement::getReconBillId, reconBillId)
                .set(OpBankStatement::getReconTime, LocalDateTime.now())
                .set(OpBankStatement::getReconBillType, null));
    }

    /** 删除流水（物理删；去重导入的误录数据可清理） */
    public void deleteStatement(Long id) {
        if (bankStatementMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流水不存在: " + id);
        }
        bankStatementMapper.deleteById(id);
    }
}
