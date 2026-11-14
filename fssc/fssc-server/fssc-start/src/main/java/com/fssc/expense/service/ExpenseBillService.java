package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fssc.audit.util.ChecksumUtil;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AmountCnUtil;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.service.FraudDetectionService;
import com.fssc.compliance.service.PrecheckService;
import com.fssc.contract.entity.ContPayRecord;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.contract.mapper.ContPayRecordMapper;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillAttach;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.entity.ExpTravel;
import com.fssc.expense.mapper.ExpBillAttachMapper;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.expense.mapper.ExpBillRelationMapper;
import com.fssc.expense.mapper.ExpTravelMapper;
import com.fssc.framework.cache.TenantCache;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.invoice.service.InvoiceService;
import com.fssc.masterdata.entity.MdCity;
import com.fssc.masterdata.service.PeriodService;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdExpenseStandard;
import com.fssc.masterdata.mapper.MdCityMapper;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdExpenseStandardMapper;
import com.fssc.masterdata.service.ExchangeRateService;
import com.fssc.masterdata.service.FormMetaService;
import com.fssc.masterdata.service.PostableSubjectResolver;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.mapper.OpPayBatchDetailMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysFile;
import com.fssc.system.entity.SysPost;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysFileMapper;
import com.fssc.system.mapper.SysPostMapper;
import com.fssc.system.service.FileService;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.workflow.service.WfFormPermGuard;
import com.fssc.system.service.NumberRuleService;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报销单据服务（一期 CLAIM 为主，APPLY/LOAN/REPAY/PAY 共用主表）。
 * 状态机：DRAFT → SUBMITTED/APPROVING → APPROVED / REJECTED / WITHDRAWN；DRAFT → VOID。
 * 提交链路：校验(费用项/金额/发票/附件) → 预算占用(逐行) → 发起 Flowable 流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseBillService {

    /** 借款默认归还天数（取值 45 天；可后续迁移为 md_sys_config 配置项） */
    public static final int DEFAULT_LOAN_REPAY_DAYS = 45;

    /** 借款逾期允许再次借款的开关（会计年度内逾期 true=禁止再借，默认 true；可后续迁移字典） */
    public static final boolean LOAN_OVERDUE_BLOCK_NEW_LOAN = true;

    /** 费用日期是否允许晚于今天（默认 0=不允许；与前端 expense.claim.futureDateAllowed 同键） */
    public static final String FUTURE_EXPENSE_DATE_KEY = "expense.claim.futureDateAllowed";

    /** 缓存域：报销标准（与 ExpenseStandardService 写入口失效共用同一 key 前缀） */
    private static final String EXP_STD_DOMAIN = "expenseStandard";

    private final ExpBillMapper expBillMapper;
    /** 会计期间守卫：关账/冻结期间禁止新建或保存业务单据（G14.6 关账后 409 实证） */
    private final PeriodService periodService;
    private final ExpBillLineMapper expBillLineMapper;
    /** 分摊行（exp_bill_share）驱动预算占用——多部门分摊时按分摊行分别占用 */
    private final com.fssc.expense.mapper.ExpBillShareMapper expBillShareMapper;
    /** 表单元数据引擎：必录/长度/数值规则从代码搬到配置，此处做权威校验 */
    private final FormMetaService formMetaService;
    private final ExpBillAttachMapper expBillAttachMapper;
    private final ExpBillRelationMapper expBillRelationMapper;
    private final ExpTravelMapper expTravelMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    /** 制证科目预检：提交时就确认每条费用行能落到末级科目 */
    private final PostableSubjectResolver postableSubjectResolver;
    private final MdExpenseStandardMapper mdExpenseStandardMapper;
    private final MdCityMapper mdCityMapper;
    private final SysUserMapper sysUserMapper;
    private final SysPostMapper sysPostMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysFileMapper sysFileMapper;
    /** 1b：附件覆盖后回收孤儿文件登记，走与【删除附件】同一个出口（含令牌清理） */
    private final FileService sysFileService;
    private final JdbcTemplate jdbc;
    private final WfEngineFacade wfEngineFacade;
    private final WfInstanceMapper wfInstanceMapper;
    private final WfTaskMapper wfTaskMapper;
    /** B1：form_perm 服务端字段级校验（审批端字段写入面硬校验，fail-open） */
    private final WfFormPermGuard wfFormPermGuard;
    private final WfModelMapper wfModelMapper;
    private final BudgetService budgetService;
    private final NumberRuleService numberRuleService;
    private final ContContractMapper contContractMapper;
    private final ContPayRecordMapper contPayRecordMapper;
    private final PrecheckService precheckService;
    private final FraudDetectionService fraudDetectionService;
    private final InvoiceService invoiceService;
    /** 发票池/勾稽读写经发票域 Facade（分库断点）：本类不再直连 inv_invoice/inv_bill_invoice */
    private final InvoiceDomainFacade invoiceFacade;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final OpPayBatchDetailMapper opPayBatchDetailMapper;
    private final OpPayBatchMapper opPayBatchMapper;
    /** 往来单位（crm_customer）——payee_name 的服务端权威来源之一，页面选定往来单位后户名以此为准 */
    private final CrmCustomerMapper crmCustomerMapper;
    /** 采购订单（po_order）——payee_name 的服务端权威来源之二，与往来单位互为交叉校验 */
    private final PoOrderMapper poOrderMapper;
    /** 作废即级联——本单作废时同批办结名下非 VOID 凭证（复用既有作废/红冲机制，不另造） */
    private final VoucherService voucherService;
    private final ExchangeRateService exchangeRateService;
    private final OrgGuard orgGuard;
    /**
     * 借款冲抵的减记收敛在审批通过环节（{@code ExpenseWfEventListener.repayLoan / offsetLoansByClaim}），
     * 反审核是这一环的逆操作，必须把已扣的额度反冲回去。算式复用同一条
     * （{@code ExpenseWfEventListener.rollbackLoanLedgerOnUnaudit}），不在本类另写一套余额加减——
     * 两处各写一套，迟早加减对不上。
     * 同包注入，无环（本类只被 ExpenseBillController 依赖）。
     */
    private final ExpenseWfEventListener expenseBizAction;

    // 草稿

    /**
     * payee 环节：申请人显示名统一取<b>姓名</b>（{@code sys_user.nick_name}），姓名为空才回落工号。
     *
     * <p>不能用工号：{@code exp_bill.apply_user_name} 一旦落 {@code LoginUser.getUsername()}＝<b>工号</b>，
     * 下游 {@code ExpenseWfEventListener.createClaimPayment} 会把该列<b>原样</b>拷进
     * {@code exp_bill_payment.payee_name}，组批时 {@code PayBatchService.addDetail} 再拷进
     * {@code op_pay_batch_detail.payee_name} ⇒ 出纳付款批次明细的「收款方」就显示成工号，
     * 银企报文与对账单同样带工号出门。
     *
     * <p>改写只发生在写入位置，存量行不回填：显示层取名规则与共享审核环节
     * （{@code fillAttachUploadNames} 的 nick_name 优先、回落 user_name）逐字一致，
     * 两套规则并存时以本方法为准。收款对象判定不受影响——
     * {@code resolvePayToEmployee} 的回退比对同时认工号/登录账号/姓名经 sys_user 反查，
     * 本列写姓名后仍按 {@code apply_user_id} 与反查结果命中「付给本人」。
     * 关键字搜索（{@code myPage}/{@code InvoiceService} 的 LIKE apply_user_name）随新单改为按姓名命中。
     */
    private static String applicantDisplayName(LoginUser u) {
        if (u == null) {
            return null;
        }
        String realName = u.getRealName();
        return realName == null || realName.isBlank() ? u.getUsername() : realName.trim();
    }

    /**
     * 甲（代报销正式写入）：把「报销人／代理人」两个身份显式落到 exp_bill。
     *
     * <p>列语义（沿用实体既有规则，不新建代理关系表/列）：
     * <ul>
     *  <li>{@code apply_user_id}＝报销人＝被代理人，款项归属人；{@code agent_user_id}＝代理人＝替他人填单的人；</li>
     *  <li><b>代理人只能是被代理那个单的实际填单人</b>：显式带上 agent_user_id 时必须等于当前登录人，
     *      指他人为代理人一律拒绝（否则代报销成了任意挂名的后门）；</li>
     *  <li><b>二者不可同人</b>：报销人＝登录人即视为未代理，agent_user_id 不写（留空）；</li>
     *  <li>两个身份都不带 ⇒ 与原先逐字相同（报销人＝登录人、agent_user_id 空），线上默认行为零变更；</li>
     *  <li>登记人 {@code create_by/update_by} 与代理身份是两回事，各写各列，不混用。</li>
     * </ul>
     *
     * <p>编辑环节（{@code exist != null}）：报销人以历史为准，客户端改写即拒绝；代理只可<b>补登</b>不可撤登
     * （JSON 无法区分「未带」与「置空」，且 MP updateById 不写 NULL 列），要撤代理请重填草稿。
     *
     * @param bill 请求携带的单据头（身份列在此归一后再写入数据库）
     * @param exist 历史行；null＝新建
     */
    private void registerApplicantIdentity(ExpBill bill, ExpBill exist, LoginUser u) {
        Long me = u.getUserId();
        Long reqApplicant = bill.getApplyUserId();
        Long reqAgent = bill.getAgentUserId();
        if (exist == null) {
            if (reqApplicant == null || Objects.equals(reqApplicant, me)) {
                // 未指定他人报销人（或指定的就是本人）⇒ 未代理，与原先一致
                if (reqAgent != null && !reqAgent.equals(me)) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "代理人须为填单操作人本人（当前登录人 " + me + "），且不得与报销人同人");
                }
                bill.setApplyUserId(me);
                bill.setApplyUserName(applicantDisplayName(u));
                bill.setAgentUserId(null);
                return;
            }
            if (reqAgent != null && !reqAgent.equals(me)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "代理人须为填单操作人本人（当前登录人 " + me + "），不能指定他人为代理人");
            }
            SysUser principal = assertNormalUser(reqApplicant);
            assertAgentCompanyBoundary(u, principal);
            bill.setApplyUserId(reqApplicant);
            bill.setApplyUserName(userDisplayName(principal));
            bill.setAgentUserId(me);
            log.info("代报销登记（新建）：报销人 {}（{}）、代理人 {}（{}）",
                    reqApplicant, bill.getApplyUserName(), me, applicantDisplayName(u));
            return;
        }
        Long storedApplicant = exist.getApplyUserId();
        if (reqApplicant != null && !Objects.equals(reqApplicant, storedApplicant)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "报销人不可改（代报销单请重新填单），当前报销人: " + storedApplicant);
        }
        bill.setApplyUserId(storedApplicant);
        bill.setApplyUserName(exist.getApplyUserName());
        if (reqAgent == null || Objects.equals(storedApplicant, me)) {
            // 未带代理身份，或报销人就是登录人本人 ⇒ 维持历史（同人不算代理）
            bill.setAgentUserId(exist.getAgentUserId());
            return;
        }
        if (!reqAgent.equals(me)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "代理人须为填单操作人本人（当前登录人 " + me + "），不能指定他人为代理人");
        }
        if (storedApplicant != null) {
            assertAgentCompanyBoundary(u, assertNormalUser(storedApplicant));
        }
        bill.setAgentUserId(me);
        log.info("代报销补登（编辑）：报销人 {}（{}）、代理人 {}", storedApplicant,
                exist.getApplyUserName(), me);
    }

    /** 被代理人合法性：sys_user 存在且 status=NORMAL（停用/锁定账号不得被挂名为报销人） */
    private SysUser assertNormalUser(Long userId) {
        SysUser su = sysUserMapper.selectById(userId);
        if (su == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报销人（被代理人）不存在: " + userId);
        }
        if (su.getStatus() != null && !"NORMAL".equals(su.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "报销人（被代理人）已停用/锁定，不能代其报销: " + userId);
        }
        return su;
    }

    /**
     * 代报销的公司边界（甲的另一半）：代理人只能在自己的公司内替他人报销。
     * 代理报销「代理人只能在报销人主职位所在公司代理报销」的边界语义；
     * 我方没有"显式代理授权档案"这张表（授权载体尚未建立），先以组织边界收敛越权面。
     * 任一方的 company_id 为空（集团岗/脏数据）不拦——缺维度时拒绝会把集团岗挡在门外。
     */
    private void assertAgentCompanyBoundary(LoginUser agent, SysUser principal) {
        Long mine = agent.getCompanyId();
        Long theirs = principal.getCompanyId();
        if (mine != null && theirs != null && !mine.equals(theirs)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "只能在本公司内代他人报销（报销人所属公司与操作人不同）：报销人 "
                            + principal.getId() + "，两边公司 " + theirs + " / " + mine);
        }
    }

    /** 取名规则与 /一致：nick_name 优先、回落 user_name */
    private static String userDisplayName(SysUser su) {
        if (su == null) {
            return null;
        }
        return StringUtils.hasText(su.getNickName()) ? su.getNickName().trim() : su.getUserName();
    }

    /** 保存草稿（新建或更新，行明细整表替换） */
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(ExpBill bill, List<ExpBillLine> lines, List<ExpBillAttach> attaches) {
        // AI 网关/直连调用可能给出 {} 或只有 bill 的空体 —— 缺一句话说明比 NPE→500 好定位得多
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体缺少 bill（报销单主体），示例：{\"bill\":{\"billType\":\"CLAIM\",\"title\":\"…\",\"totalAmount\":600}}");
        }
        LoginUser u = UserContext.getRequired();
        orgGuard.assertCompanyAllowed(bill.getCompanyId());
        assertPersonsUnique(bill.getExtJson());
        if (bill.getId() == null) {
            bill.setId(null);
            bill.setTenantId(TenantContext.getTenantId());
            bill.setBillNo(genBillNo(bill.getBillType()));
            bill.setStatus("DRAFT");
            // 甲：报销人／代理人两个身份做成正式写入（未显式携带身份时行为与原先逐字相同）
            registerApplicantIdentity(bill, null, u);
            // 登记人显式赋值＝实际操作人（MP 自动填充只在无登录上下文时补 0L）；与 agent_user_id 各写各列，不混用
            bill.setCreateBy(u.getUserId());
            if (bill.getCompanyId() == null) {
                bill.setCompanyId(u.getCompanyId());
            }
            if (bill.getApplyDate() == null) {
                bill.setApplyDate(LocalDate.now());
            }
            if (bill.getCurrencyCode() == null) {
                bill.setCurrencyCode("CNY");
            }
            if (bill.getImageStatus() == null) {
                bill.setImageStatus("NONE");
            }
            // 按申请日期自动填会计期间（看板月度趋势依赖）；年/月分别兜底，防只传其一时 R4 勾稽失真
            if (bill.getApplyDate() != null) {
                if (bill.getPeriodYear() == null) {
                    bill.setPeriodYear(bill.getApplyDate().getYear());
                }
                if (bill.getPeriodMonth() == null) {
                    bill.setPeriodMonth(bill.getApplyDate().getMonthValue());
                }
            }
            // 期间守卫：单据期间由申请日期推导（或由前端显式指定）；已关账期间一律拒绝落库。
            // 原来 assertOpen 只接在凭证生成/过账侧，导致关账后仍能「草稿已保存」并写入 exp_bill，
            // 与手册 §四 4「关账后 409 拦截、查数据库无任何新单」相悖。
            // 必须在 insert 之前、companyId 与 periodYear/Month 都已兜底之后调用。
            if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
                periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
            }
            // 借款草稿默认为借款日 + DEFAULT_LOAN_REPAY_DAYS 天到期归还；前端已显式填写则不覆盖
            if ("LOAN".equals(bill.getBillType()) && bill.getExpectRepayDate() == null && bill.getApplyDate() != null) {
                bill.setExpectRepayDate(bill.getApplyDate().plusDays(DEFAULT_LOAN_REPAY_DAYS));
            }
            // 预计还款日不得早于借款申请日（前端已禁选，服务端兜底 API 直写）
            assertExpectRepayDate(bill.getBillType(), bill.getApplyDate(), bill.getExpectRepayDate());
            // 金额大写随总金额写入数据库（打印/OA 用）
            bill.setAmountCn(AmountCnUtil.toCn(bill.getTotalAmount()));
            // 渠道记录：前端显式传值，后端按白名单办结；未传时补 PC，与走列默认值的结果同值
            normalizeBillSource(bill, false);
            applyPayeeAuthority(bill, null);
            expBillMapper.insert(bill);
        } else {
            ExpBill exist = requireMine(bill.getId(), u);
            if (!"DRAFT".equals(exist.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可编辑，当前状态: " + exist.getStatus());
            }
            // 编辑同样受期间守卫：updateById 忽略 null 字段，期间/公司须按「传值优先、缺省取原值」合并后再校验，
            // 否则只改标题就能绕过关账闸。
            Integer editYear = bill.getPeriodYear() != null ? bill.getPeriodYear() : exist.getPeriodYear();
            Integer editMonth = bill.getPeriodMonth() != null ? bill.getPeriodMonth() : exist.getPeriodMonth();
            LocalDate editDate = bill.getApplyDate() != null ? bill.getApplyDate() : exist.getApplyDate();
            if (editYear == null && editDate != null) editYear = editDate.getYear();
            if (editMonth == null && editDate != null) editMonth = editDate.getMonthValue();
            Long editCompany = bill.getCompanyId() != null ? bill.getCompanyId() : exist.getCompanyId();
            if (editCompany != null && editYear != null && editMonth != null) {
                periodService.assertOpen(editCompany, editYear, editMonth);
            }
            // 甲：编辑环节同样归一身份列（只可补登代理，不接受把单改到第三人名下）
            registerApplicantIdentity(bill, exist, u);
            // 登记人显式赋值（改动人），不靠 MP 无上下文时的 0L 兜底
            bill.setUpdateBy(u.getUserId());
            bill.setAmountCn(AmountCnUtil.toCn(bill.getTotalAmount()));
            // 渠道记录：编辑环节同样只认白名单；不传 source 时原样保留，避免把已登记的 H5 改写成 PC
            normalizeBillSource(bill, true);
            applyPayeeAuthority(bill, exist);
            // 编辑环节按「传值优先、缺省保留原值」合并后复校，避免只改日期其一绕过
            if ("LOAN".equals(bill.getBillType())) {
                LocalDate apply = bill.getApplyDate() != null ? bill.getApplyDate() : exist.getApplyDate();
                LocalDate repay = bill.getExpectRepayDate() != null ? bill.getExpectRepayDate() : exist.getExpectRepayDate();
                assertExpectRepayDate(bill.getBillType(), apply, repay);
            }
            expBillMapper.updateById(bill);
        }
        // 本币行原币金额/汇率兜底——历史前端只写 amount，orig_amount 缺失/为 0
        // 会让原币规则汇总少计；与前端本币分支回写形成双侧保证
        if (lines != null) {
            for (ExpBillLine l : lines) {
                if (!isForeign(l.getCurrencyCode()) && (l.getOrigAmount() == null || l.getOrigAmount().signum() == 0)
                        && l.getAmount() != null) {
                    l.setOrigAmount(l.getAmount());
                    if (l.getExchangeRate() == null) {
                        l.setExchangeRate(BigDecimal.ONE);
                    }
                }
            }
        }
        applyExchangePolicy(bill, lines);
        // 草稿路径也要有轻量行断言。【提交】走 validateAmountReconcile 全套强校验，
        // 【保存草稿】却直落 replaceLines——零校验就能批量造出「金额 ≤ 0 / 费用项为空」的脏草稿，
        // 它们在列表里显示正常，却永远提交不了，只能人工清理。
        // 草稿允许「不完整」（可暂缺附件/事由），但不允许数值自相矛盾。
        assertDraftLinesSane(lines);
        assertExpenseDatesNotFuture(lines);
        replaceLines(bill.getId(), lines);
        replaceAttaches(bill.getId(), attaches);
        // 前端「先传附件再保存/提交」时附件已随单据写库，但影像状态仍是初始 NONE。
        // 保存后按附件存在性同步 image_status（NONE → COMPLETE）。
        syncImageStatus(bill.getId());
        return bill.getId();
    }

    /**
     * 单据渠道（exp_bill.source）白名单办结：只认 PC / H5 两个字面值，其余一律回落列默认 PC。
     * 取值一律由前端显式传入、后端校验——不做 UA 嗅探、不加拦截器、不改自动填充。
     * keepBlank=true（编辑环节）时空值原样不动，交给 MP 的空值忽略策略保留既有渠道；
     * false（新建环节）时空值补 PC，与不写该列、由 DB 默认值得到的结果同值。
     */
    private static void normalizeBillSource(ExpBill bill, boolean keepBlank) {
        String src = bill.getSource();
        if (src == null || src.isBlank()) {
            if (!keepBlank) {
                bill.setSource("PC");
            }
            return;
        }
        if (!"PC".equals(src) && !"H5".equals(src)) {
            bill.setSource("PC");
        }
    }

    /**
     * 收款方名称与标题的服务端权威规则（仅 billType=PAY，即对公支付/对公结算）。
     *
     * <p>为什么要在服务端定源：payee_name 原样落前端回传的户名串并不成立——服务端不按
     * 「往来单位 / 采购订单」反查主数据，页面换了往来单位、单上户名仍会是上一张单那家
     * （exp_bill.customer_id 与 po_id 各自都对，只有户名不跟着变）。它是三处的唯一读源——凭证摘要（VoucherService）、出纳付款户名
     * （ExpenseWfEventListener#createPaymentRecord）、三单匹配拦截文案（ThreeWayMatchService），
     * 户名错指会让同族付款的支付校验被不相干主体的对账状态掩盖，所以名称必须在服务端定源。
     *
     * <p>规则：往来单位取 crm_customer.customer_name，采购订单取 po_order.supplier_name，
     * 两者都有值即互为交叉校验——指向不同主体时当场拒绝并点名两个来源，不静默取其一；
     * 只有一个有值时按该来源写入数据库。两个来源都为空的子类型（代收代付/机票结算无往来单位选择）
     * 保留原值，行为与本规则无关。
     *
     * <p>title 只认用户输入原文；为空时按「对公付款-单据编号」生成（沿用既有「业务名-标识」自动标题式），
     * 编号每单唯一，故不会沿用上一张单的标题。
     *
     * @param bill 本次要写入数据库的单据头
     * @param legacy 编辑环节的库中现单；新建环节传 null。前端未回传的字段按 updateById 的空值忽略语义取库中现值
     */
    private void applyPayeeAuthority(ExpBill bill, ExpBill legacy) {
        if (!"PAY".equals(bill.getBillType())) {
            return;
        }
        Long customerId = bill.getCustomerId() != null ? bill.getCustomerId()
                : (legacy == null ? null : legacy.getCustomerId());
        Long poId = bill.getPoId() != null ? bill.getPoId()
                : (legacy == null ? null : legacy.getPoId());
        // 前端「采购订单」下拉把选中项主键写进 ext_json.poId，而 exp_bill.po_id 列的取值源
        // 是上面的 bill.getPoId()（顶层字段）。保存草稿环节不经 onBeforeSubmit 的顶层镜像（CorpPay.vue 仅
        // 在「提交审批」时把 ext_json.poId 拷进 bill.poId），于是列位为空 ⇒ 本校验看不到 PO 可比、
        // 两个来源不同家也能放行。故这里兜底取请求里的 ext_json.poId：取到即用，
        // 既让交叉校验看到「真正要写入数据库的那条 PO」，又把该主键回写列位，
        // 使「校验比较的 po_id」与「insert/update 写入数据库的 po_id」恒等于用户在下拉里选中那条的主键。
        if (poId == null) {
            Long fromExt = poIdFromExtJson(bill.getExtJson());
            if (fromExt != null) {
                poId = fromExt;
                bill.setPoId(fromExt);
            }
        }
        String fromCustomer = null;
        if (customerId != null) {
            CrmCustomer c = crmCustomerMapper.selectById(customerId);
            if (c == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "往来单位不存在（customer_id=" + customerId + "），请重新选择往来单位");
            }
            fromCustomer = c.getCustomerName() == null ? null : c.getCustomerName().trim();
        }
        String fromPo = null;
        if (poId != null) {
            PoOrder po = poOrderMapper.selectById(poId);
            if (po == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "采购订单不存在（po_id=" + poId + "），请重新选择采购订单");
            }
            fromPo = po.getSupplierName() == null ? null : po.getSupplierName().trim();
        }
        boolean hasCustomer = fromCustomer != null && !fromCustomer.isEmpty();
        boolean hasPo = fromPo != null && !fromPo.isEmpty();
        if (hasCustomer && hasPo && !fromCustomer.equals(fromPo)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "往来单位与采购订单不是同一家供应商，已拒绝保存：往来单位「" + fromCustomer + "」(customer_id="
                            + customerId + ")，采购订单「" + fromPo + "」(po_id=" + poId
                            + ")。请改选同一家供应商，或同时清空其中一个来源");
        }
        if (hasCustomer || hasPo) {
            bill.setPayeeName(hasCustomer ? fromCustomer : fromPo);
        }
        if (bill.getTitle() == null || bill.getTitle().isBlank()) {
            bill.setTitle("对公付款-" + (bill.getBillNo() == null || bill.getBillNo().isBlank()
                    ? bill.getId() : bill.getBillNo()));
        }
    }

    /**
     * 从单据扩展字段 {@code ext_json.poId} 里取「用户在下拉里选中那条采购订单的主键」。
     * 兼容数字与字符串两种写法；ext_json 非法/无该键一律返回 null（交由既有校验兜底，绝不猜测）。
     */
    private static Long poIdFromExtJson(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n =
                    new ObjectMapper().readTree(extJson).get("poId");
            if (n == null || n.isNull()) {
                return null;
            }
            if (n.canConvertToLong()) {
                return n.asLong();
            }
            String s = n.asText().trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (Exception ignore) {
            // ext_json 非 JSON 或 poId 非数字：视为未携带 PO，与原先「列位为空、本校验不看 PO」同结果
            return null;
        }
    }

    /**
     * 影像状态同步：单据存在任一附件/发票时，image_status 由 NONE 升级为 COMPLETE
     * （枚举沿用 ExpBill 注释规则 NONE/PARTIAL/COMPLETE）；只升级不降级，失败不阻断保存。
     */
    private void syncImageStatus(Long billId) {
        try {
            long cnt = expBillAttachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, billId));
            if (cnt > 0) {
                ExpBill cur = expBillMapper.selectById(billId);
                if (cur != null && "NONE".equals(cur.getImageStatus())) {
                    ExpBill upd = new ExpBill();
                    upd.setId(billId);
                    upd.setImageStatus("COMPLETE");
                    expBillMapper.updateById(upd);
                    log.info("单据 {} 已随附件回写 image_status: NONE → COMPLETE", billId);
                }
            }
        } catch (Exception ex) {
            log.warn("单据 {} 影像状态同步失败（不阻断保存）: {}", billId, ex.getMessage());
        }
    }

    // 事前申请转报销单（APPLY → CLAIM）

    /**
     * 事前申请转报销单：把已审批通过的 APPLY 单复制为 CLAIM 草稿，
     * 并落 exp_bill_relation（APPLY_CLAIM，use_amount=申请金额）形成冲抵闭环。
     * 同一申请单只允许转一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long convertApply(Long applyId) {
        LoginUser u = UserContext.getRequired();
        ExpBill apply = expBillMapper.selectById(applyId);
        if (apply == null || !"APPLY".equals(apply.getBillType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "事前申请单不存在: " + applyId);
        }
        if (!"APPROVED".equals(apply.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已审批通过的事前申请可转报销单，当前状态: " + apply.getStatus());
        }
        long cnt = expBillRelationMapper.selectCount(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, applyId)
                .eq(ExpBillRelation::getRelationType, "APPLY_CLAIM"));
        if (cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该事前申请已转报销单，不能重复转换");
        }
        // 复制单据头为 CLAIM 草稿
        ExpBill claim = new ExpBill();
        claim.setTenantId(TenantContext.getTenantId());
        claim.setBillType("CLAIM");
        claim.setBillNo(genBillNo("CLAIM"));
        claim.setTitle(apply.getTitle() == null ? "事前申请转报销" : apply.getTitle());
        claim.setApplyDate(apply.getApplyDate() == null ? LocalDate.now() : apply.getApplyDate());
        claim.setTotalAmount(apply.getTotalAmount());
        claim.setCurrencyCode(apply.getCurrencyCode() == null ? "CNY" : apply.getCurrencyCode());
        claim.setCompanyId(apply.getCompanyId());
        claim.setDeptId(apply.getDeptId());
        claim.setStatus("DRAFT");
        claim.setApplyUserId(u.getUserId());
        claim.setApplyUserName(applicantDisplayName(u));
        claim.setImageStatus("NONE");
        claim.setPeriodYear(claim.getApplyDate().getYear());
        claim.setPeriodMonth(claim.getApplyDate().getMonthValue());
        expBillMapper.insert(claim);
        // 复制明细行
        List<ExpBillLine> srcLines = expBillLineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                .eq(ExpBillLine::getBillId, applyId));
        fillLineItemNames(srcLines);
        int lineNo = 1;
        for (ExpBillLine l : srcLines) {
            ExpBillLine nl = new ExpBillLine();
            nl.setBillId(claim.getId());
            nl.setLineNo(lineNo++);
            nl.setItemId(l.getItemId());
            nl.setItemName(l.getItemName());
            nl.setExpenseDate(l.getExpenseDate() == null ? claim.getApplyDate() : l.getExpenseDate());
            nl.setDeptId(l.getDeptId());
            nl.setCostCenterId(l.getCostCenterId());
            nl.setProjectId(l.getProjectId());
            nl.setSubjectId(l.getSubjectId());
            nl.setCurrencyCode(l.getCurrencyCode() == null ? "CNY" : l.getCurrencyCode());
            nl.setOrigAmount(l.getOrigAmount() == null ? l.getAmount() : l.getOrigAmount());
            nl.setAmount(l.getAmount());
            nl.setRemark(l.getRemark());
            expBillLineMapper.insert(nl);
        }
        // 落关联（冲抵闭环：报销可用申请金额）
        ExpBillRelation rel = new ExpBillRelation();
        rel.setTenantId(TenantContext.getTenantId());
        rel.setSourceBillId(applyId);
        rel.setTargetBillId(claim.getId());
        rel.setRelationType("APPLY_CLAIM");
        rel.setUseAmount(apply.getTotalAmount() == null ? BigDecimal.ZERO : apply.getTotalAmount());
        rel.setCreateTime(LocalDateTime.now());
        expBillRelationMapper.insert(rel);
        log.info("事前申请 {} 转报销单 {} 成功", apply.getBillNo(), claim.getBillNo());
        return claim.getId();
    }

    // 解析发票入池 / 跨单重复报销拦截（合规整改）

    /**
     * 提交时把明细行 extJson.invoice（OCR/版式解析结果）登记进发票池并建立单票关联，
     * 随后做跨单判重：同一发票号若已关联其他在途/已批单据，直接拦截提交。
     * 去重：池中已有同号发票则复用；本单已有关联则跳过。
     */
    private void registerAndCheckDuplicateInvoices(ExpBill bill, List<ExpBillLine> lines) {
        ObjectMapper om = new ObjectMapper();
        for (ExpBillLine line : lines) {
            String ext = line.getExtJson();
            if (ext == null || !ext.contains("invoiceNo")) {
                continue;
            }
            String no;
            LocalDate invDate = null;
            BigDecimal amt = null;
            BigDecimal net = null;
            BigDecimal tax = null;
            String seller = null;
            try {
                JsonNode inv = om.readTree(ext).path("invoice");
                no = inv.path("invoiceNo").asText("");
                if (no.isBlank()) {
                    continue;
                }
                String d = inv.path("invoiceDate").asText("");
                if (!d.isBlank()) {
                    try { invDate = LocalDate.parse(d); } catch (Exception ignore) { /* 日期格式异常不阻断 */ }
                }
                if (inv.hasNonNull("amount")) amt = BigDecimal.valueOf(inv.path("amount").asDouble());
                if (inv.hasNonNull("netAmount")) net = BigDecimal.valueOf(inv.path("netAmount").asDouble());
                if (inv.hasNonNull("taxAmount")) tax = BigDecimal.valueOf(inv.path("taxAmount").asDouble());
                seller = inv.path("sellerName").asText(null);
            } catch (Exception e) {
                log.warn("解析行发票元信息失败 lineId={}", line.getId(), e);
                continue;
            }
            // 跨单判重：发票号已被其他单据关联（在途/已批）→ 拦截。
            // 已作废（VOID）单据的勾稽不算占用——把 VOID 也算进去，作废后重报就会被误拦；
            // 其余状态（在途/已批/已付）均算占用，与手工补录入口同一规则。
            List<InvInvoice> same = invoiceFacade.findByInvoiceNo(no);
            for (InvInvoice exist : same) {
                List<InvBillInvoice> otherLinks =
                        invoiceFacade.findLinksByInvoiceIdExcludingBill(exist.getId(), bill.getId());
                String occupiedBy = null;
                for (InvBillInvoice link : otherLinks) {
                    if (link.getBillId() == null) {
                        continue;
                    }
                    ExpBill linked = expBillMapper.selectById(link.getBillId());
                    if (linked == null || "VOID".equals(linked.getStatus())) {
                        continue; // VOID 不算占用
                    }
                    occupiedBy = linked.getBillNo() == null || linked.getBillNo().isBlank()
                            ? String.valueOf(linked.getId()) : linked.getBillNo();
                    break;
                }
                if (occupiedBy != null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "发票 " + no + " 已存在于单据" + occupiedBy
                                    + "，不得重复补录/报销；如需并单请先撤回原单据");
                }
            }
            // 入池（不存在才建）
            Long invId;
            if (!same.isEmpty()) {
                invId = same.get(0).getId();
            } else {
                InvInvoice inv = new InvInvoice();
                inv.setInvoiceCode("");
                inv.setInvoiceNo(no);
                inv.setInvoiceDate(invDate);
                inv.setAmount(amt == null ? BigDecimal.ZERO : amt);
                inv.setNetAmount(net == null ? BigDecimal.ZERO : amt.subtract(tax == null ? BigDecimal.ZERO : tax));
                inv.setTaxAmount(tax == null ? BigDecimal.ZERO : tax);
                inv.setSellerName(seller);
                inv.setSourceDocType("CLAIM_BILL");
                inv.setSourceDocId(bill.getId());
                inv.setStatus("NORMAL");
                inv.setLockStatus("LOCKED");
                try {
                    invId = invoiceFacade.insertInvoice(inv);
                    log.info("报销发票 {} 已登记发票池 id={} billId={}", no, invId, bill.getId());
                } catch (Exception e) {
                    log.warn("发票池登记失败（可能并发同号）invoiceNo={} billId={}", no, bill.getId(), e);
                    InvInvoice again = invoiceFacade.findOneByInvoiceNo(no);
                    if (again == null) continue;
                    invId = again.getId();
                }
            }
            // 本单关联（去重）
            if (invId != null && !invoiceFacade.existsLink(bill.getId(), invId)) {
                // 报销勾稽登记占用额（缺省=发票面额），否则占用累计恒为 0，
                // 三单匹配/超付判定与 bindToBill 的跨单占用校验都会失真。
                invoiceFacade.createLink(bill.getId(), invId, amt == null ? BigDecimal.ZERO : amt);
            }
        }
    }

    // 发票→报销草稿自动填单（OCR 后置）

    /**
     * 发票池一键生成报销草稿并自动占用：校验发票归属（租户拦截器）+ 剩余面额 &gt; 0 →
     * saveDraft 生成 CLAIM 草稿（表头 priceType=TAX_IN、总金额=票面价税合计；单行
     * amount=含税 / netAmount=不含税 / taxAmount=税额 / taxRate 按票推算，expenseDate=今天）→
     * bindToBill 按价税合计占用（红冲/作废禁占、占用累计≤面额等复用现有强校验，
     * 发票已 LOCKED 占用满则整体回滚报错）→ 返回 {billId, billNo}。
     * 勾稽由 submit 时统一校验，草稿阶段不设障。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createClaimFromInvoice(Long invoiceId, Long itemId, String title) {
        if (itemId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择费用项");
        }
        MdExpenseItem item = mdExpenseItemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用项不存在: " + itemId);
        }
        // 归属校验：selectById 走租户拦截器，跨租户发票查不到即不存在
        InvInvoice inv = invoiceFacade.findById(invoiceId);
        if (inv == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票不存在: " + invoiceId);
        }
        // 销项票不可生成报销草稿。必须在 saveDraft 之前拦——
        // 后置到 bindToBill 会先落一张脏 CLAIM 草稿（ 同型坑）。
        if (InvoiceService.isOutputInvoice(inv)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + inv.getInvoiceNo() + " 是销项发票（本公司开具），不能用于生成报销草稿");
        }
        if (inv.getAmount() == null || inv.getNetAmount() == null || inv.getTaxAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票价税三项不全，无法自动填单");
        }
        // 剩余面额预检（锁定/占用满给明确提示；bindToBill 的 ≤面额 强校验仍是最终防线）
        BigDecimal occupied = invoiceService.occupiedAmount(invoiceId);
        if (inv.getAmount().subtract(occupied).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发票 " + inv.getInvoiceNo() + " 已锁定或占用满（面额 " + inv.getAmount()
                            + "，已占用 " + occupied + "），无法生成报销草稿");
        }
        // 表头：CLAIM + 含税规则，总金额=票面价税合计（applyDate/期间/币种由 saveDraft 补齐）
        ExpBill bill = new ExpBill();
        bill.setBillType("CLAIM");
        bill.setTitle(title == null || title.isBlank()
                ? "发票报销-" + (inv.getInvoiceNo() == null ? inv.getId() : inv.getInvoiceNo()) : title);
        bill.setPriceType("TAX_IN");
        bill.setTotalAmount(inv.getAmount());
        bill.setNetAmount(inv.getNetAmount());
        bill.setTaxAmount(inv.getTaxAmount());
        bill.setRemark("由发票 " + inv.getInvoiceNo() + " 自动生成");
        // 明细行：含税金额 + 价税三项按票面回填；税率按票推算 = 税额/不含税（4 位小数，如 0.13）
        ExpBillLine line = new ExpBillLine();
        line.setItemId(itemId);
        line.setItemName(item.getItemName());
        line.setExpenseDate(LocalDate.now());
        line.setPriceType("TAX_IN");
        line.setCurrencyCode("CNY");
        line.setAmount(inv.getAmount());
        line.setOrigAmount(inv.getAmount());
        line.setNetAmount(inv.getNetAmount());
        line.setTaxAmount(inv.getTaxAmount());
        if (inv.getNetAmount().compareTo(BigDecimal.ZERO) > 0) {
            line.setTaxRate(inv.getTaxAmount().divide(inv.getNetAmount(), 4, RoundingMode.HALF_UP));
        }
        Long billId = saveDraft(bill, List.of(line), null);
        // 自动占用：bindToBill 复用现有校验（红冲/作废拦截、占用累计≤面额、置 LOCKED_IN_USE、占用计数）
        List<ExpBillLine> saved = lines(billId);
        invoiceService.bindToBill(billId, saved.isEmpty() ? null : saved.get(0).getId(), invoiceId, inv.getAmount());
        ExpBill savedBill = expBillMapper.selectById(billId);
        log.info("发票 {} 自动生成报销草稿 {}（占用 {}），行价税={}/{}/税率{}",
                inv.getInvoiceNo(), savedBill.getBillNo(), inv.getAmount(), inv.getNetAmount(), inv.getTaxAmount(),
                line.getTaxRate());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("billId", billId);
        resp.put("billNo", savedBill.getBillNo());
        return resp;
    }

    // 提交（校验链 + 占用 + 流程）

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long billId, Long approverUserId) {
        submit(billId, approverUserId, null);
    }

    /** 会按费用行出借方科目的单据：报销（EXPENSE 凭证）与对公结算（EXPENSE_SETTLE 计提凭证） */
    private static boolean needsExpenseSubject(ExpBill bill) {
        if (bill == null) {
            return false;
        }
        return "CLAIM".equals(bill.getBillType())
                || ("PAY".equals(bill.getBillType()) && "SETTLE".equalsIgnoreCase(bill.getBillSubType()));
    }

    /**
     * @param firstAssigneeUserId 提单人为首审环节点名的办理人（方案）；仅当该流程首审节点配成
     *                            PICK 档且灰度放行时生效，由 {@code WfEngineFacade} 统一校验候选集，
     *                            不合法直接抛错回滚（不静默改派）。null=按节点配置自动取人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long billId, Long approverUserId, Long firstAssigneeUserId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"DRAFT".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可提交，当前状态: " + bill.getStatus());
        }
        // 提交再闸一次：守卫上线前落库的历史草稿也不能在关账期间进入审批链。
        if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        }
        // 提交环节复核代理身份：报销人与代理人同人即视为未代理，撤掉代理列后再进审批链
        // （历史脏行／绕过保存环节的写入在此办结；报销人与代理人的其余组合已由保存环节校验）。
        if (bill.getAgentUserId() != null && bill.getAgentUserId().equals(bill.getApplyUserId())) {
            expBillMapper.update(null, new LambdaUpdateWrapper<ExpBill>()
                    .eq(ExpBill::getId, billId)
                    .set(ExpBill::getAgentUserId, null));
            bill.setAgentUserId(null);
            log.info("单据 {} 报销人与代理人同人，提交前撤销代理登记（agent_user_id 置空）", bill.getBillNo());
        }
        // 驳回意见/驳回类型只由驳回腿写入，重提侧不主动清就会残留：同一张单屏幕上
        // 一边显示「已审批/审批中」，一边显示上一次的「智能审单自动驳回：…」，读文案的人会判错状态。
        // updateById 忽略 null，清列要走 LambdaUpdateWrapper。
        if ((bill.getAuditRemark() != null && !bill.getAuditRemark().isBlank()) || bill.getRejectType() != null) {
            expBillMapper.update(null, new LambdaUpdateWrapper<ExpBill>()
                    .eq(ExpBill::getId, billId)
                    .set(ExpBill::getAuditRemark, null)
                    .set(ExpBill::getRejectType, null));
            bill.setAuditRemark(null);
            bill.setRejectType(null);
            log.info("单据 {} 重新提交，清上一轮驳回意见与驳回类型", bill.getBillNo());
        }
        assertPersonsUnique(bill.getExtJson());
        // 提交权去重：以「DRAFT→APPROVING」条件更新原子认领。
        // 并发双击时后到请求更新 0 行即被拒，同单不会起出两个流程实例；后续任一校验失败整事务回滚，状态自动还原 DRAFT。
        int claimed = expBillMapper.update(null, new LambdaUpdateWrapper<ExpBill>()
                .eq(ExpBill::getId, billId)
                .eq(ExpBill::getStatus, "DRAFT")
                .set(ExpBill::getStatus, "APPROVING"));
        if (claimed == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请勿重复提交：该单据正在或已经提交");
        }
        List<ExpBillLine> lines = expBillLineMapper.selectList(
                new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, billId));
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报销单至少需要一条费用明细");
        }
        // 提交环节再校日期边界（草稿可能由旧版本或 API 直写写入数据库）
        assertExpenseDatesNotFuture(lines);
        assertExpectRepayDate(bill.getBillType(), bill.getApplyDate(), bill.getExpectRepayDate());

        // 单据强校验链（财务共享口径：界面字段↔字段、字段↔表勾稽，提交即拦）
        assertHeaderRequired(bill);
        validateAmountReconcile(bill, lines);
        // 元数据权威校验：
        //     必录/长度/数值范围规则落 md_form_field + md_form_validation，不再只靠代码 if-else。
        //     放在硬编码校验<b>之后</b>：元数据是「加严」不是「放宽」——
        //     历史单据若某字段不满足新配的规则，先被硬编码校验拦下并给出原有文案（行为不变）；
        //     硬编码放行后才由元数据补第二道校验。这样上线不会让历史单据突然提交不了。
        validateByFormMeta(bill, lines);

        // 收款账户校验（防付错）：pay_target 驱动收款账号必填
        assertPayeeAccount(bill);

        // 合规预检（风险前置）：一次性反馈全部不合规项，FAIL 聚合拦截
        List<PrecheckService.PrecheckItem> fails = precheckService.failures(billId);
        if (!fails.isEmpty()) {
            // 拦截留痕（供「预检漏拦率」看板统计）：不留痕就永远算不出哪些问题是被人工事后发现的
            precheckService.recordBlocks(billId, bill.getBillType(), fails);
            String detail = fails.stream()
                    .map(PrecheckService.PrecheckItem::getMsg)
                    .collect(Collectors.joining("；"));
            throw new BizException(ErrorCode.PARAM_INVALID, "提交被拦截（" + fails.size() + " 项不合规）：" + detail);
        }

        // ①-1 反欺诈拦截/预警（HIGH_RISK 阻断提交，SUSPICIOUS 预警放行）
        try {
            FraudDetectionService.FraudCheckResult fraudResult =
                    fraudDetectionService.checkAndBlock(bill.getApplyUserId(), billId);
            if (fraudResult.isShouldBlock()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "提交被反欺诈拦截（风险等级 HIGH）：" + fraudResult.getSummary());
            }
            if (fraudResult.isShouldWarn()) {
                log.warn("反欺诈预警（放行）billId={}: {}", billId, fraudResult.getSummary());
            }
        } catch (BizException be) {
            throw be; // 拦截异常向上传播
        } catch (Exception fe) {
            log.warn("反欺诈扫描异常（不影响提交）billId={}: {}", billId, fe.getMessage());
        }

        // ② 校验链：费用项合法 + 金额 + 发票/附件要求（发票要求由预检统一判定，此处不再重复拦截）
        BigDecimal total = BigDecimal.ZERO;
        boolean needInvoice = false;
        // 海外报账②出国判定：任一差旅行 dest_country 非空且不含"中国" → 境外标准档。
        // countryTier 暂固定 DEV 兜底；国家→档位(DEV/EMG/OTH)映射规则留待 md_sys_config 配置化后替换。
        boolean abroad = AbroadStandardRule.resolveAbroad(travelDestCountries(billId));
        String countryTier = "DEV";
        // 境外凭证合规⑤（2016年19号公告）：abroad 且开关开启时执行收据中文说明必填、
        // 发票必填放宽、境外行可抵扣税额归零；sys_config overseas.compliance_enabled=0 全部跳过（回退旧行为）。
        // 国内（abroad=false）行为零变化。
        boolean overseasCompliance = abroad && isOverseasComplianceEnabled();
        // 费用标准维度：申请人职级 + 差旅目的地城市等级 + 出差天数（均来自关联 APPLY 的差旅明细）
        Integer stdPostLevel = resolvePostLevel(bill.getApplyUserId());
        String stdCityLevel = resolveStdCityLevel(billId, bill);
        BigDecimal stdTravelDays = resolveTravelDays(billId, bill);
        for (ExpBillLine line : lines) {
            MdExpenseItem item = mdExpenseItemMapper.selectById(line.getItemId());
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "费用项不存在: " + line.getItemId());
            }
            if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "明细金额必须大于 0: " + item.getItemName());
            }
            // 制证可行性预检（报销 CLAIM / 对公结算 PAY+SETTLE）：科目缺配或非末级在此拦下，
            // 不让它走到「钱付了、凭证生成失败」那一步
            if (needsExpenseSubject(bill)) {
                postableSubjectResolver.requireExpenseSubject(item, line.getSubjectId(), bill.getCompanyId());
            }
            if (item.getNeedInvoice() != null && item.getNeedInvoice() == 1) {
                needInvoice = true;
            }
            total = total.add(line.getAmount());
            // 境外凭证合规⑤：收据/行程单须填中文说明（19号公告税前扣除要件）；
            // 境外票据无增值税进项可抵扣，可抵扣税额归零（价税勾稽不动，仅在 deductAmount 层面）。
            if (overseasCompliance) {
                String cnErr = OverseasVoucherRule.requireCnDesc(true, line);
                if (cnErr != null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "第" + line.getLineNo() + "行[" + item.getItemName() + "]" + cnErr);
                }
                if (OverseasVoucherRule.forceNoDeduct(true, line)
                        && line.getDeductAmount() != null
                        && line.getDeductAmount().compareTo(BigDecimal.ZERO) != 0) {
                    log.info("境外凭证合规⑤：第{}行[{}]可抵扣税额 {} 归零（境外票据无进项抵扣）",
                            line.getLineNo(), item.getItemName(), line.getDeductAmount());
                    line.setDeductAmount(BigDecimal.ZERO);
                    expBillLineMapper.updateById(line);
                }
            }
            // ④ 差旅/费用标准校验：超标标记（不拦截，仅提示，审批人据实裁断）；
            //   abroad=true 时住宿/交通/餐饮行换源境外标准（abroad=1 且档位匹配），超标机制沿用；
            //   国内按「申请人职级 × 差旅城市等级」取档，DAY 类按日均金额比较。
            MdExpenseStandard std = findExpenseStandard(line.getItemId(), bill.getCompanyId(), abroad, countryTier,
                    stdPostLevel, stdCityLevel);
            if (std != null && std.getAmount() != null) {
                line.setStdAmount(std.getAmount());
                BigDecimal compare = line.getAmount();
                boolean perDay = "DAY".equalsIgnoreCase(std.getStdType() == null ? "" : std.getStdType().trim());
                if (perDay && stdTravelDays != null && stdTravelDays.signum() > 0) {
                    compare = compare.divide(stdTravelDays, 2, RoundingMode.HALF_UP);
                }
                if (compare.compareTo(std.getAmount()) > 0) {
                    line.setOverStd(1);
                    line.setOverReason(perDay
                            ? "日均 " + compare + " 元超过标准 " + std.getAmount() + " 元/日"
                            : "超过报销标准 " + compare.subtract(std.getAmount()) + " 元");
                } else {
                    line.setOverStd(0);
                    line.setOverReason(null);
                }
                expBillLineMapper.updateById(line);
            }
        }
        // 发票必填（仅报销 CLAIM 在费用项要求发票时强制；借款/还款/付款/申请不需要）
        boolean isClaim = "CLAIM".equals(bill.getBillType());
        if (isClaim && needInvoice) {
            long invoiceCnt = expBillAttachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, billId).eq(ExpBillAttach::getAttachType, "INVOICE"));
            // 境外凭证合规⑤：abroad 时发票必填放宽为「附 INVOICE 或 任一行为
            // RECEIPT/ITINERARY 且中文说明非空（19号公告税前扣除要件，逐行必填已在前置循环拦截）」
            boolean overseasVoucherOk = overseasCompliance && lines.stream().anyMatch(l ->
                    OverseasVoucherRule.isReceiptForm(l.getVoucherForm())
                            && l.getCnDesc() != null && !l.getCnDesc().isBlank());
            if (invoiceCnt == 0 && !overseasVoucherOk) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该单据费用项要求必须附发票");
            }
        }
        // 事前申请（出差/培训/会议）附件强制（Visio EER04-01-01 及会议申请需求：无附件不得提交）
        if ("APPLY".equals(bill.getBillType())) {
            long anyAttach = expBillAttachMapper.selectCount(new LambdaQueryWrapper<ExpBillAttach>()
                    .eq(ExpBillAttach::getBillId, billId));
            if (anyAttach == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "事前申请必须上传附件（行程单、出差/会议/培训通知、审批件等），当前未上传任何附件");
            }
        }
        // 解析发票入池 + 单票关联 + 跨单重复报销硬拦截（合规整改：）
        registerAndCheckDuplicateInvoices(bill, lines);
        bill.setTotalAmount(total);
        bill.setStatus("SUBMITTED");

        // 金额防篡改：提交时对关键字段生成校验和（审批/付款执行时校验）
        try {
            bill.setAmountChecksum(ChecksumUtil.sha256(
                    total, bill.getPayeeName(), bill.getBillNo(), bill.getTitle()));
        } catch (Exception ignored) {
            // 校验和生成失败不阻断提交（审批时校验会因缺失而提示）
        }

        // 对公付款引用合同 → 不允许超合同金额付款（台账需求：超合同金额付款需使用合同调整）
        if ("PAY".equals(bill.getBillType()) && bill.getContractId() != null) {
            checkContractLimit(bill);
        }

        // 预算占用（逐行；控制方式由 bg_scheme.control_type 决定：HARD 拦截 / SOFT 需超支理由 / WARN 放行）
        //   超支理由取自单据扩展字段 extJson.overrunReason（软控方案超预算时必填）
        String overrunReason = overrunReasonOf(bill.getExtJson());
        // 对公付款（billType=PAY，PREPAY/SETTLE）同样走预算占用：行上无显式预算科目时按
        // md_item_budget_map（费用类型→预算科目映射，与合同域同一规则）解析，
        // 解析到即与报销单同链路 occupy（HARD/SOFT/WARN 由方案配置决定），biz_type 取 EXP_PAY。
        // 还款单是例外：还款在业务上是资金回流，预算侧应当「冲减借款单既有占用」而不是再占一次，
        // 否则同一笔借款被双重占用。故 REPAY 单完全不进 occupy()（也就不触发 HARD/SOFT/WARN 判定
        // 与零下达额度放行），改为按当时的在贷余额做与 repayLoan 同一规则的 FIFO 预分配，
        // 逐借款单写 RELEASE 负数流水。
        boolean isRepayBill = "REPAY".equals(bill.getBillType());
        // 无预算办结：解析不到预算行不再静默跳过。静默跳过会让报销金额游离于预算控制之外而无人知晓
        // （费用项配了预算行的是少数，绝大多数报销行其实没有对应预算行）。
        // 现统一记录，并按服务端策略（sys_config: budget.no_budget_policy）决定是否拒绝提交。
        // 还款单不参与该办结：它本就不占用预算，无预算科目不构成拒提理由。
        List<String> noBudgetLines = new ArrayList<>();
        BigDecimal noBudgetAmount = BigDecimal.ZERO;
        LinkedHashSet<Long> repayBudgetItems = new LinkedHashSet<>();
        // 分摊行驱动占用。改造前整单只落一条预算线（exp_bill_share 全仓仅 CRUD 四个引用点、
        // 无任何消费方），多部门分摊时要么被 conflict 拒提、要么全额占在制单人部门上。
        // 现按分摊行（line_id 维度）逐行解析预算并分别占用；无分摊行的行保持原单线逻辑。
        java.util.Map<Long, List<com.fssc.expense.entity.ExpBillShare>> sharesByLine = new java.util.HashMap<>();
        if (!isRepayBill) {
            List<com.fssc.expense.entity.ExpBillShare> shares = expBillShareMapper.selectList(
                    new LambdaQueryWrapper<com.fssc.expense.entity.ExpBillShare>()
                            .eq(com.fssc.expense.entity.ExpBillShare::getBillId, billId));
            for (com.fssc.expense.entity.ExpBillShare sh : shares) {
                if (sh.getLineId() != null && sh.getShareAmount() != null && sh.getShareAmount().signum() > 0) {
                    sharesByLine.computeIfAbsent(sh.getLineId(), k -> new ArrayList<>()).add(sh);
                }
            }
        }
        for (ExpBillLine line : lines) {
            if (line.getAmount() == null) {
                continue;
            }
            // 分摊行优先：一条明细被拆到多个承担部门时，按分摊金额分别占用各自部门预算
            List<com.fssc.expense.entity.ExpBillShare> lineShares = sharesByLine.get(line.getId());
            if (lineShares != null && !lineShares.isEmpty()) {
                for (com.fssc.expense.entity.ExpBillShare sh : lineShares) {
                    BigDecimal shAmt = sh.getShareAmount();
                    Long shItem = budgetService.resolveOccupyItem(
                            sh.getBudgetItemId(), line.getItemId(),
                            sh.getCompanyId() != null ? sh.getCompanyId() : bill.getCompanyId(),
                            sh.getDeptId() != null ? sh.getDeptId() : bill.getDeptId(),
                            bill.getPeriodYear(),
                            sh.getProjectId() != null ? sh.getProjectId() : bill.getProjectId());
                    if (shItem == null) {
                        noBudgetLines.add("行" + line.getLineNo() + "分摊至部门 " + sh.getDeptId()
                                + "（" + shAmt + "）未命中预算明细");
                        noBudgetAmount = noBudgetAmount.add(shAmt);
                        continue;
                    }
                    budgetService.occupy(shItem, shAmt,
                            BudgetService.occupyBizTypeOf(bill.getBillType()),
                            billId, bill.getBillNo(), overrunReason);
                }
                continue;
            }
            Long budgetItemId = budgetService.resolveOccupyItem(line.getBudgetItemId(), line.getItemId(),
                    bill.getCompanyId(), bill.getDeptId(), bill.getPeriodYear(), bill.getProjectId());
            if (budgetItemId == null) {
                if (!isRepayBill) {
                    noBudgetLines.add("行" + line.getLineNo() + "（费用项 " + line.getItemId() + "，" + line.getAmount() + "）");
                    noBudgetAmount = noBudgetAmount.add(line.getAmount());
                }
                continue;
            }
            if (!budgetItemId.equals(line.getBudgetItemId())) {
                log.info("单据 {} 行 {} 预算科目按主体解析为 {}（前端值 {}）",
                        bill.getBillNo(), line.getId(), budgetItemId, line.getBudgetItemId());
                // 解析结果必须落回明细行：审批通过时的核销读的是 exp_bill_line.budget_item_id，
                // 不回写则占用记在 A 行、核销打到 B 行，原占用永久悬挂（预算执行数失真）。
                ExpBillLine bind = new ExpBillLine();
                bind.setId(line.getId());
                bind.setBudgetItemId(budgetItemId);
                expBillLineMapper.updateById(bind);
                line.setBudgetItemId(budgetItemId);
            }
            if (isRepayBill) {
                // 只留科目做冲减候选集（真正要冲的行由 BudgetService 按借款单台账并集确定）
                repayBudgetItems.add(budgetItemId);
                continue;
            }
            // 业务类型统一取 BudgetService.occupyBizTypeOf —— 与驳回释放、反审批回冲同一规则。
            // 若对 LOAN/APPLY/REPAY 一律记 EXP_CLAIM，bg_occupy 里 EXP_LOAN／EXP_REPAY 就永远是 0 行，
            // 按业务类型读占用台账会全部失真。
            budgetService.occupy(budgetItemId, line.getAmount(),
                    BudgetService.occupyBizTypeOf(bill.getBillType()),
                    billId, bill.getBillNo(), overrunReason);
        }
        if (isRepayBill) {
            offsetLoanBudgetOnRepaySubmit(bill, billId, total, repayBudgetItems);
        }
        if (!noBudgetLines.isEmpty()) {
            String detail = bill.getBillNo() + " 有 " + noBudgetLines.size() + " 行 / " + noBudgetAmount
                    + " 元未命中预算明细：" + String.join("；", noBudgetLines);
            if (NO_BUDGET_POLICY_BLOCK.equalsIgnoreCase(noBudgetPolicy())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "无预算科目可占用（费用类型未配预算科目或该费用项无预算行），当前无预算策略为 BLOCK，拒绝提交。" + detail);
            }
            log.warn("无预算占用放行（策略 {}）：{}", noBudgetPolicy(), detail);
        }

        // 发起 Flowable 流程（approver 默认该单据的申请人，保证 "提交XX单" 首节点
        //   assignee=${approver} 指派给真实发起人而不是租户管理员）
        Long approver = approverUserId != null ? approverUserId : bill.getApplyUserId();
        // bizType 用单据真实类型（EXP_CLAIM/EXP_APPLY/EXP_LOAN/EXP_REPAY/EXP_PAY），
        // 流程事件收尾按类型分流（核销/释放/借款余额更新）
        String bizType = switch (bill.getBillType() == null ? "CLAIM" : bill.getBillType()) {
            case "APPLY" -> "EXP_APPLY";
            case "LOAN" -> "EXP_LOAN";
            case "REPAY" -> "EXP_REPAY";
            case "PAY" -> "EXP_PAY";
            default -> "EXP_CLAIM";
        };
        String procInstId = wfEngineFacade.start(bizType, billId, approver, bill.getTitle(), total,
                bill.getBillSubType(), null, firstAssigneeUserId);
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("APPROVING");
        // 表头合计/价税在提交时按 Σ明细 重算（R1/#9 拆分），必须随收尾写入数据库，
        // 否则校验和(按重算值生成)与库值不符，审批环节会误报"已被篡改"
        update.setTotalAmount(bill.getTotalAmount());
        update.setNetAmount(bill.getNetAmount());
        update.setTaxAmount(bill.getTaxAmount());
        update.setAmountCn(AmountCnUtil.toCn(bill.getTotalAmount()));
        // 金额防篡改校验和随提交写入数据库（否则审批环节无法校验）
        update.setAmountChecksum(bill.getAmountChecksum());
        update.setProcInstId(procInstId);
        // 信用高的提单人提交的单据可由系统自动审核通过、按比例抽检保合规：
        // 小额报销（开关+阈值）系统自动审核通过并按比例进抽检池；记录 oper=系统自动审核(sysauto)。
        autoAuditIfEligible(bill, billId, bizType, procInstId, total);
        update.setSubmitTime(LocalDateTime.now());
        expBillMapper.updateById(update);
        // 驳回方式（reject_type）描述的是"这一单上次被怎么退的"。重提后它已不是本单的当前状态，
        // 不清就会残留：任何按它统计"在途驳回单"的报表都会把已付/已批单据算成刚被驳回。
        // updateById 忽略 null 字段（MyBatis-Plus 默认非空更新），清列必须走条件更新显式 set null。
        if (bill.getRejectType() != null) {
            expBillMapper.update(null,
                    new LambdaUpdateWrapper<ExpBill>()
                            .eq(ExpBill::getId, billId)
                            .set(ExpBill::getRejectType, null)
                            // 驳回意见随 reject_type 同一个环节清（两者只在驳回路径成对写入），
                            // 否则重提后移动端卡片仍显示上一轮的「审批意见」。
                            // 只由核减写入的 remark（reject_type 为 NULL）不进这个分支，不会被误清。
                            .set(ExpBill::getAuditRemark, null));
        }
    }

    /**
     * 驳回/撤回单编辑重提（场景演示缺口修复）：REJECTED|WITHDRAWN → DRAFT 并解除旧流程实例关联，
     * 申请人可在发起页修改后重新提交；重新提交经 submit 走【新】流程实例（旧实例保留历史痕迹）。
     *
     * <p>原规则只认 REJECTED/DRAFT，而 WITHDRAWN（撤回）在前端无任何出口、后端
     * {@link #voidBill} 也拒收 ⇒ 撤回的单既不能改、不能重提、也不能作废，成为永久脏数据，
     * 还白占单号与预算。此处把「撤回」并入「退回草稿」同轨，与「撤回=取消单据」规则一致。</p>
     */
    public void returnToEdit(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"REJECTED".equals(bill.getStatus()) && !"DRAFT".equals(bill.getStatus())
                && !"WITHDRAWN".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿/已驳回/已撤回单据可编辑重提，当前状态: " + bill.getStatus());
        }
        if ("REJECTED".equals(bill.getStatus()) || "WITHDRAWN".equals(bill.getStatus())) {
            LambdaUpdateWrapper<ExpBill> uw =
                    new LambdaUpdateWrapper<>();
            uw.eq(ExpBill::getId, billId)
              .set(ExpBill::getStatus, "DRAFT")
              .set(ExpBill::getProcInstId, null)
              .set(ExpBill::getUpdateBy, u.getUserId());
            // 超支理由随单据落库（ext_json.overrunReason），重提时服务端读到它就不再要求补填，
            // 前端预检也按"已有理由"跳过弹窗——于是驳回→改大金额→重提这条路可以完全不复核超支理由。
            // 理由描述的是<b>上一轮</b>的金额与调剂方式，金额一改它就不成立了，故退回草稿时清掉，
            // 让下一次提交必须重新给一次（服务端仍有"需填写超支理由"兜底，前端会重新弹窗）。
            String cleared = clearOverrunReason(bill.getExtJson());
            if (cleared != null) {
                uw.set(ExpBill::getExtJson, cleared);
            }
            expBillMapper.update(uw);
        }
    }

    /**
     * 从 ext_json 里摘掉 {@code overrunReason} 键，返回新 JSON 串。
     *
     * <p>只摘这一个键，其余扩展字段原样保留；原串为空/解析失败/本来就没有该键时返回 {@code null}
     * （表示无需更新，调用方跳过 set）。</p>
     */
    private static String clearOverrunReason(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode n = om.readTree(extJson);
            if (n == null || !n.isObject() || !n.has("overrunReason")) {
                return null;
            }
            ((ObjectNode) n).remove("overrunReason");
            return om.writeValueAsString(n);
        } catch (Exception ex) {
            // 解析失败不能顺手把整串覆盖掉——那会连带丢掉别的扩展字段
            return null;
        }
    }

    /**
     * 还款单提交时的预算<b>冲减</b>（替代原「新增占用」）。
     *
     * <p><b>分配规则</b>：还款单与借款单之间没有显式关联（{@code exp_bill_relation} 只有
     * APPLY_CLAIM／LOAN_CLAIM／PREPAY_CLAIM 三类），还款核销一直是「按申请人在贷借款单 id 升序 FIFO」。
     * 本方法在提单期做<b>同一规则</b>的预分配，只多一处：状态判据含 {@code PARTIAL_REPAID}
     * （否则第二次还款看不到第一批未结清的借款，的在贷规则）。
     *
     * <p>预分配额只是提单期的净额判据；审批收尾（结清）时按 {@code exp_loan_offset} 的
     * 实际冲抵额经 {@code BudgetService.alignLoanOccupy} 校准，两环节对不上时以实际额为准。
     * 冲减本身由 {@code BudgetService.offsetLoanOccupy} 逐借款单、逐预算行封顶在
     * 「该借款单在该行的净占用」内，天然不会冲掉别人的占用，也不会超额释放。
     */
    private void offsetLoanBudgetOnRepaySubmit(ExpBill bill, Long billId, BigDecimal total,
                                               Collection<Long> repayBudgetItems) {
        BigDecimal remain = total == null ? BigDecimal.ZERO : total;
        if (remain.signum() <= 0 || bill.getApplyUserId() == null) {
            return;
        }
        List<ExpBill> loans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getApplyUserId, bill.getApplyUserId())
                .eq(ExpBill::getBillType, "LOAN")
                .in(ExpBill::getStatus, "APPROVED", "PARTIAL_REPAID")
                .orderByAsc(ExpBill::getId));
        BigDecimal done = BigDecimal.ZERO;
        for (ExpBill loan : loans) {
            if (remain.signum() <= 0) {
                break;
            }
            BigDecimal bal = loan.getLoanBalance() == null ? BigDecimal.ZERO : loan.getLoanBalance();
            if (bal.signum() <= 0) {
                continue;
            }
            BigDecimal take = remain.min(bal);
            done = done.add(budgetService.offsetLoanOccupy(loan.getId(), take, bill.getBillNo(),
                    repayBudgetItems, "还款单提交冲减借款占用"));
            remain = remain.subtract(take);
        }
        if (done.signum() == 0) {
            // 在贷余额为 0（或该借款单从未占用预算）时不写任何流水；留一条 warn 便于
            // 与「还款单不产生预算变化」的正常态区分——原缺陷现场正是这里静默新增了 80,000 占用。
            log.warn("还款单 {}（billId={}）提交时无可冲减的借款预算占用，未写 bg_occupy 流水",
                    bill.getBillNo(), billId);
        }
    }

    /**
     * 取费用项对应的生效报销标准（按 公司 + 费用项 + 优先级最高）。
     * 标准表 md_expense_standard：住宿/交通/招待等按职级/城市等级/费用项配置额度。
     * 提交链路逐行高频只读，按 租户+费用项+公司+境外档 维度进程内 TTL 缓存，标准维护时主动失效。
     * 海外报账②：abroad=true 时换源境外标准档（abroad=1，档位精确匹配→境外通用档），
     * 无境外档可配时回退国内标准；abroad=false 行为与旧版一致（仅显式排除 abroad=1 行）。
     */
    /** 从单据扩展字段 extJson 中取超支理由（软控方案超预算时必填）；解析失败不阻断，返回 null */
    private static String overrunReasonOf(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = new ObjectMapper().readTree(extJson);
            JsonNode v = n == null ? null : n.get("overrunReason");
            return v == null || v.isNull() ? null : v.asText();
        } catch (Exception ignored) {
            // extJson 非 JSON 或解析异常：视为未填写超支理由，由预算软控逻辑决定是否拦截
            return null;
        }
    }

    private MdExpenseStandard findExpenseStandard(Long itemId, Long companyId, boolean abroad, String countryTier,
                                                  Integer postLevel, String cityLevel) {
        if (itemId == null) return null;
        String cacheKey = TenantCache.mdKey(EXP_STD_DOMAIN + ":" + itemId + ":" + companyId
                + (abroad ? ":AB:" + countryTier : "")
                + ":PL" + postLevel + ":CL" + cityLevel);
        return TenantCache.get(cacheKey, () -> {
            if (abroad) {
                LambdaQueryWrapper<MdExpenseStandard> aw = new LambdaQueryWrapper<>();
                aw.eq(MdExpenseStandard::getItemId, itemId)
                        .eq(MdExpenseStandard::getAbroad, 1);
                if (companyId != null) {
                    // 公司专属优先，NULL=全局标准兜底（历史种子均为全局规则）
                    aw.and(w -> w.eq(MdExpenseStandard::getCompanyId, companyId)
                            .or().isNull(MdExpenseStandard::getCompanyId));
                }
                MdExpenseStandard picked = AbroadStandardRule.pickStandard(
                        mdExpenseStandardMapper.selectList(aw), countryTier);
                if (picked != null) {
                    return picked;
                }
                // 无境外档可配 → 回退国内标准（不改变旧规则）
            }
            // 国内：先按 职级档 + 城市等级 取精确档（缺维度视为通配行）
            MdExpenseStandard dimmed = pickDomesticStandard(itemId, companyId, postLevel, cityLevel);
            if (dimmed != null) {
                return dimmed;
            }
            // 维度无命中 → 回落旧口径（不看职级/城市），保证历史种子标准仍生效
            LambdaQueryWrapper<MdExpenseStandard> w = new LambdaQueryWrapper<>();
            w.eq(MdExpenseStandard::getItemId, itemId);
            w.ne(MdExpenseStandard::getAbroad, 1);
            if (companyId != null) {
                w.and(x -> x.eq(MdExpenseStandard::getCompanyId, companyId)
                        .or().isNull(MdExpenseStandard::getCompanyId));
            }
            w.last("ORDER BY company_id IS NULL, priority DESC LIMIT 1");
            return mdExpenseStandardMapper.selectOne(w);
        });
    }

    /** 按职级档（post_level_min/max）与城市等级（CITY_1/2/3）取档；两者均未知时返回 null 走旧口径 */
    private MdExpenseStandard pickDomesticStandard(Long itemId, Long companyId, Integer postLevel, String cityLevel) {
        if (postLevel == null && cityLevel == null) return null;
        LambdaQueryWrapper<MdExpenseStandard> w = new LambdaQueryWrapper<>();
        w.eq(MdExpenseStandard::getItemId, itemId);
        w.ne(MdExpenseStandard::getAbroad, 1);
        if (companyId != null) {
            w.and(x -> x.eq(MdExpenseStandard::getCompanyId, companyId)
                    .or().isNull(MdExpenseStandard::getCompanyId));
        }
        if (postLevel != null) {
            w.and(x -> x.isNull(MdExpenseStandard::getPostLevelMin)
                    .or().le(MdExpenseStandard::getPostLevelMin, postLevel));
            w.and(x -> x.isNull(MdExpenseStandard::getPostLevelMax)
                    .or().ge(MdExpenseStandard::getPostLevelMax, postLevel));
        }
        if (cityLevel != null) {
            w.and(x -> x.isNull(MdExpenseStandard::getCityLevel)
                    .or().eq(MdExpenseStandard::getCityLevel, cityLevel));
        }
        w.orderByDesc(MdExpenseStandard::getPriority)
                .orderByAsc(MdExpenseStandard::getId)
                .last("LIMIT 1");
        return mdExpenseStandardMapper.selectOne(w);
    }

    /** 申请人职级：sys_user.post_id → sys_post.post_level；无岗位/未设职级返回 null */
    private Integer resolvePostLevel(Long userId) {
        if (userId == null) return null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getPostId() == null) return null;
        SysPost post = sysPostMapper.selectById(user.getPostId());
        return post == null ? null : post.getPostLevel();
    }

    /** 差旅明细来源单：本单是 APPLY 取自身，否则按 APPLY_CLAIM 关系取源申请单 */
    private Long travelSourceBillId(Long billId, ExpBill bill) {
        if (bill != null && "APPLY".equals(bill.getBillType())) return billId;
        ExpBillRelation rel = expBillRelationMapper.selectOne(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getTargetBillId, billId)
                .eq(ExpBillRelation::getRelationType, "APPLY_CLAIM")
                .orderByDesc(ExpBillRelation::getId)
                .last("LIMIT 1"));
        return rel == null ? null : rel.getSourceBillId();
    }

    private List<ExpTravel> travelLinesOf(Long billId, ExpBill bill) {
        Long src = travelSourceBillId(billId, bill);
        if (src == null) return new ArrayList<>();
        return expTravelMapper.selectList(new LambdaQueryWrapper<ExpTravel>()
                .eq(ExpTravel::getBillId, src).orderByAsc(ExpTravel::getLineNo));
    }

    /** 差旅目的地城市等级 → 标准表口径 CITY_1/2/3；无城市/非 A/B/C 返回 null */
    private String resolveStdCityLevel(Long billId, ExpBill bill) {
        for (ExpTravel t : travelLinesOf(billId, bill)) {
            if (t.getToCityId() == null) continue;
            MdCity city = mdCityMapper.selectById(t.getToCityId());
            String mapped = toStdCityLevel(city == null ? null : city.getCityLevel());
            if (mapped != null) return mapped;
        }
        return null;
    }

    /** 出差天数：取关联差旅明细的最大 days；无明细返回 null（DAY 类标准退化为整单金额比较） */
    private BigDecimal resolveTravelDays(Long billId, ExpBill bill) {
        BigDecimal max = null;
        for (ExpTravel t : travelLinesOf(billId, bill)) {
            if (t.getDays() == null) continue;
            if (max == null || t.getDays().compareTo(max) > 0) max = t.getDays();
        }
        return max;
    }

    private String toStdCityLevel(String cityLevel) {
        if (cityLevel == null || cityLevel.isBlank()) return null;
        switch (cityLevel.trim().toUpperCase(Locale.ROOT)) {
            case "A": case "CITY_1": return "CITY_1";
            case "B": case "CITY_2": return "CITY_2";
            case "C": case "CITY_3": return "CITY_3";
            default: return null;
        }
    }

    /** 出差目的国集合（exp_travel.dest_country），供出国判定 */
    private List<String> travelDestCountries(Long billId) {
        return expTravelMapper.selectList(new LambdaQueryWrapper<ExpTravel>()
                        .eq(ExpTravel::getBillId, billId))
                .stream().map(ExpTravel::getDestCountry).collect(Collectors.toList());
    }

    /**
     * 境外凭证合规⑤总开关：sys_config overseas.compliance_enabled（种子=1）。
     * 无记录/读取异常一律视为开启（参照 OcrService.isLocalParseEnabled），值=0 时回退旧行为。
     */
    private boolean isOverseasComplianceEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, OVERSEAS_COMPLIANCE_KEY)
                    .last("limit 1"));
            return c == null || c.getConfigValue() == null || !"0".equals(c.getConfigValue().trim());
        } catch (Exception e) {
            log.warn("读取 {} 失败，默认开启境外凭证合规", OVERSEAS_COMPLIANCE_KEY, e);
            return true;
        }
    }

    /** 境外凭证合规开关配置键（sys_config，租户级） */
    private static final String OVERSEAS_COMPLIANCE_KEY = "overseas.compliance_enabled";

    /** 无预算策略配置键（sys_config）：WARN=放行并记录（默认，灰度中），BLOCK=拒绝提交 */
    private static final String NO_BUDGET_POLICY_KEY = "budget.no_budget_policy";
    /** BLOCK 取值 */
    private static final String NO_BUDGET_POLICY_BLOCK = "BLOCK";

    /**
     * 无预算策略：读 sys_config {@code budget.no_budget_policy}，缺省/异常一律 WARN（放行并留痕）。
     * <p>取值要按主体灰度切 BLOCK，前提是先补预算行、配预算科目——费用项里配了预算行的只是少数，
     * 直接 BLOCK 会拦住绝大多数报销/付款提交。
     */
    private String noBudgetPolicy() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, NO_BUDGET_POLICY_KEY)
                    .last("limit 1"));
            return c == null || c.getConfigValue() == null || c.getConfigValue().isBlank()
                    ? "WARN" : c.getConfigValue().trim().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            log.warn("读取 {} 失败，按 WARN（放行留痕）处理", NO_BUDGET_POLICY_KEY, e);
            return "WARN";
        }
    }

    /**
     * 表单元数据校验：把「新增一种单据的字段校验」从改代码变成改配置。
     *
     * <p><b>为什么是第二道校验而不是第一道</b>：
     * <ul>
     *   <li>第一道 {@link #assertHeaderRequired} 是历史硬编码规则，承载已上线的业务规则；</li>
     *   <li>本方法是元数据规则，承载「后台可配」的新增要求；</li>
     *   <li>顺序固定为「硬编码 → 元数据」：历史单据不会被新配规则突然拦住，
     *       新增单据则自动获得元数据校验能力（无需改代码）。</li>
     * </ul>
     *
     * <p>找不到对应表单配置时静默跳过（不因为缺配置就阻断生产单据）。
     */
    private void validateByFormMeta(ExpBill bill, List<ExpBillLine> lines) {
        String formCode = formCodeOf(bill.getBillType(), bill.getBillSubType());
        if (formCode == null) {
            log.debug("元数据校验跳过：单据类型 {} 无对应表单配置", bill.getBillType());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billNo", bill.getBillNo());
        data.put("title", bill.getTitle());
        data.put("applyUserId", bill.getApplyUserId());
        data.put("applyUserName", bill.getApplyUserName());
        data.put("applyDate", bill.getApplyDate());
        data.put("currencyCode", bill.getCurrencyCode());
        data.put("totalAmount", bill.getTotalAmount());
        data.put("deptId", bill.getDeptId());
        data.put("companyId", bill.getCompanyId());
        data.put("payeeName", bill.getPayeeName());
        data.put("paymentAmount", bill.getPayableAmount());
        data.put("repayAmount", bill.getPayableAmount());
        if (lines != null && !lines.isEmpty()) {
            BigDecimal sum = lines.stream()
                    .map(ExpBillLine::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            data.put("lineCount", lines.size());
            data.put("lineAmountSum", sum);
        }
        formMetaService.validateOrThrow(formCode, data, "SAVE");
    }

    /**
     * 单据类型 → 元数据表单编码。
     *
     * <p><b>规则以 md_form_config 实查为准</b>：
     * 现有 12 个表单里 CLAIM=EXP_CLAIM、LOAN=EXP_LOAN、REPAY=EXP_REPAY、
     * APPLY=EXP_MEETING/EXP_TRAVEL/EXP_TRAIN、PAY=EXP_SETTLE/EXP_PREPAY/EXP_AIR 等。
     * 一个单据类型可能对应多个表单（按子类型分流），这里取<b>该类型的第一张表</b>作为默认表单，
     * 子类型细分由 {@link #formCodeOf(String, String)} 承接。
     */
    private String formCodeOf(String billType) {
        return formCodeOf(billType, null);
    }

    /** 单据类型 + 子类型 → 元数据表单编码；无对应表单返回 null（跳过校验） */
    private String formCodeOf(String billType, String billSubType) {
        if (billType == null) {
            return null;
        }
        // 子类型优先（与 md_form_config.bill_sub_type 对齐）
        if (billSubType != null && !billSubType.isBlank()) {
            String exact = switch (billType + "_" + billSubType) {
                case "APPLY_TRAVEL", "CLAIM_TRAVEL" -> "EXP_TRAVEL";
                case "APPLY_MEETING" -> "EXP_MEETING";
                case "APPLY_TRAIN" -> "EXP_TRAIN";
                case "CLAIM_AGENCY" -> "EXP_AGENCY";
                case "PAY_AIR" -> "EXP_AIR";
                case "PAY_PREPAY", "PAY_PREPAY_WRITEOFF" -> "EXP_PREPAY";
                case "PAY_SETTLE" -> "EXP_SETTLE";
                default -> null;
            };
            if (exact != null) {
                return exact;
            }
        }
        return switch (billType) {
            case "CLAIM" -> "EXP_CLAIM";
            case "LOAN" -> "EXP_LOAN";
            case "REPAY" -> "EXP_REPAY";
            case "APPLY" -> "EXP_TRAVEL";
            case "PAY" -> "EXP_SETTLE";
            default -> null;
        };
    }

    /**
     * 表头必填守卫：与前端 constants/billRequired.js 的 COMMON 同一规则。
     *
     * <p>前端校验挡在请求之前，这里挡的是绕过页面的调用（导入、开放接口、脚本）。
     * 单据编号由 {@code genBillNo} 写入数据库时取号，人工只读，故这里只验证它确实存在，不要求用户输入。
     */
    private void assertHeaderRequired(ExpBill bill) {
        if (bill.getBillNo() == null || bill.getBillNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据编号缺失（未正常保存），请重新保存草稿后再提交");
        }
        if (bill.getTitle() == null || bill.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题必填：请填写能说明本笔业务事项的单据标题");
        }
        if (bill.getApplyUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请人必填：单据须归属到具体经办人，否则审批与额度归集无据");
        }
        if (bill.getApplyDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请日期必填：预算占用与期间归属按申请日期计");
        }
        if (bill.getCurrencyCode() == null || bill.getCurrencyCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "币种必填");
        }
    }

    /**
     * 收款账户校验（防付错）：收款对象驱动收款账号必填。
     *
     * <p>收款对象=员工 → 个人银行卡必填；供应商/客户 → 其银行账户必填，
     * 且须能定位到往来单位（customer_id 或 payee_name）以便核对其对公账户，防止付错对象。
     *
     * <p>未显式指定 pay_target 的单据跳过校验——历史单据与不涉及付款的类型（APPLY）
     * 因此不受影响，这条校验只对新单生效。
     */
    private void assertPayeeAccount(ExpBill bill) {
        // 付款去向是资金安全底线字段：报销单/对公付款单（金额>0）收款对象必填，
        // 收款对象驱动收款账号必填（防付错）。借款/还款/事前申请不涉及对外付款，不受影响。
        boolean needPay = ("CLAIM".equals(bill.getBillType()) || "PAY".equals(bill.getBillType()))
                && bill.getTotalAmount() != null && bill.getTotalAmount().signum() > 0;
        String target = bill.getPayTarget();
        if (needPay && (target == null || target.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "收款对象（员工/供应商/客户）必填：付款去向为资金安全底线字段，请选择收款对象后提交");
        }
        if (target == null || target.isBlank()) {
            return;
        }
        String acc = bill.getPayeeAccountNo();
        if (acc == null || acc.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "收款对象为" + payTargetName(target) + "，收款账号必填（防付错）");
        }
        if (("SUPPLIER".equals(target) || "CUSTOMER".equals(target))
                && bill.getCustomerId() == null
                && (bill.getPayeeName() == null || bill.getPayeeName().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "收款对象为" + payTargetName(target) + "，须指定往来单位或收款方名称以便核对其银行账户");
        }
    }

    private static String payTargetName(String target) {
        if ("EMPLOYEE".equals(target)) return "员工";
        if ("SUPPLIER".equals(target)) return "供应商";
        if ("CUSTOMER".equals(target)) return "客户";
        return target;
    }

    // 审批核减（核定金额三层）

    /**
     * 审批核减：审批人核减报销金额，并分别核定付款额与退款额。
     *
     * <p>三层勾稽（通行规则）：
     * <ul>
     *  <li>核定报销 ≤ 申请金额——审批只能核减，不能核增</li>
     *  <li>核定付款 ≤ 核定报销——付款不得突破核定规则</li>
     *  <li>核定退款 = 核定报销 − 核定付款</li>
     * </ul>
     * 核减后应付金额 payable_amount 按「核定付款 − 已冲抵」重算，使防超付基于核定规则而非申请规则。
     *
     * <p>核定的入口只有这里：approved_amount 若只在流程监听里被动累加，就没有任何审批人主动核减的位置，
     * 「核定三层」也就无从成立。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveDeduct(Long billId, BigDecimal approvedAmount, BigDecimal approvedPayAmount,
                              BigDecimal approvedRefundAmount, String remark) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        if (!"APPROVING".equals(bill.getStatus()) && !"SUBMITTED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅待审批/审批中单据可核减，当前状态: " + bill.getStatus());
        }
        assertApproveTrilogy(bill, approvedAmount, approvedPayAmount, approvedRefundAmount);

        // form_perm 服务端字段级校验：本审批环节被配为 H/R 的字段不得提交/变更。
        // fail-open：无在办任务 / 未配 form_perm 时放行（全库 form_perm 当前为空，接入后零行为差异）。
        // 本端点的全部表头写入字段都要登记——漏掉 auditRemark（审批意见），
        // 节点把它配成 H/R 时绕过界面直调仍能改意见，属同一越权面。
        wfFormPermGuard.assertFieldsWritable(bill.getProcInstId(), WfFormPermGuard.fields(
                "approvedAmount", approvedAmount,
                "approvedPayAmount", approvedPayAmount,
                "approvedRefundAmount", approvedRefundAmount,
                "auditRemark", remark));

        ExpBill upd = new ExpBill();
        upd.setId(billId);
        upd.setApprovedAmount(approvedAmount);
        upd.setApprovedPayAmount(approvedPayAmount);
        upd.setApprovedRefundAmount(approvedRefundAmount);
        if (remark != null && !remark.isBlank()) {
            upd.setAuditRemark(remark);
        }
        // 应付随「核定付款」重算（未核定付款时回退核定报销/申请金额）
        BigDecimal base = approvedPayAmount != null ? approvedPayAmount
                : (approvedAmount != null ? approvedAmount : bill.getTotalAmount());
        upd.setPayableAmount(calcPayable(base, bill.getOffsetAmount()));
        expBillMapper.updateById(upd);
        log.info("审批核减 billId={} 核定报销={} 核定付款={} 核定退款={} 操作人={}",
                billId, approvedAmount, approvedPayAmount, approvedRefundAmount, u.getUserId());
    }

    /** 核定三层勾稽校验 */
    private void assertApproveTrilogy(ExpBill bill, BigDecimal approved, BigDecimal pay, BigDecimal refund) {
        if (approved == null && pay == null && refund == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "核定金额不能全为空");
        }
        BigDecimal applyAmt = bill.getTotalAmount() == null ? BigDecimal.ZERO : bill.getTotalAmount();
        if (approved != null) {
            if (approved.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "核定报销金额不能为负: " + approved);
            }
            if (approved.compareTo(applyAmt) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "核定报销(" + approved + ")超出申请金额(" + applyAmt + ")，审批只能核减不能核增");
            }
        }
        BigDecimal base = approved != null ? approved : applyAmt;
        BigDecimal payAmt = pay != null ? pay : base;
        if (pay != null) {
            if (pay.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "核定付款金额不能为负: " + pay);
            }
            if (pay.compareTo(base) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "核定付款(" + pay + ")超出核定报销(" + base + ")，付款不得超出核定金额");
            }
        }
        if (refund != null) {
            if (refund.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "核定退款金额不能为负: " + refund);
            }
            BigDecimal expect = base.subtract(payAmt);
            if (refund.subtract(expect).abs().compareTo(CNY_AMT_TOLERANCE) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "核定退款(" + refund + ") ≠ 核定报销(" + base + ") − 核定付款(" + payAmt + ")=" + expect);
            }
        }
    }

    // 员工借款台账（#18）

    /**
     * 员工借款余额台账（#18）：按员工聚合「Σ借款 − Σ已冲销/已还」，供资金岗催收。
     *
     * <p>规则：借款单的 loan_balance 是 exp_loan_offset 台账的净额投影
     * （/391 甲②：还款/报销冲抵一律在流程最终状态环节按「借款额 − Σ现存冲抵流水」绝对量重算，
     * 报销冲抵不再于提单期扣减；归零置 REPAID 同样只在最终状态环节落），
     * 故未还余额直接取 Σloan_balance，已冲销额 = Σ借款金额 − Σ未还余额，
     * 无需再关联 exp_loan_offset，避免与台账规则分叉。
     * <b>在途（未生效）的报销冲抵不在此规则内</b>：它只登记在 exp_bill_relation(LOAN_CLAIM).use_amount，
     * 台账页显示的仍是"账面未还余额"，不是"可冲抵余额"（后者还要再扣在途占用）。
     *
     * <p>数据范围：本方法不能做成全租户台账，跨公司/跨部门可见即等于"人人可看全集团欠款人"。
     * 按 data_scope 五级办结（公司→部门→本人，逐级降级），规则与单据列表完全同源；
     * 因聚合维度是 apply_user_id，owner 列取 {@code ExpBill::getApplyUserId}。</p>
     *
     * @param onlyOverdue true=仅返回已逾期（预计还款日已过且仍有余额）的员工
     */
    @DataScope("员工借款台账数据范围")
    public List<Map<String, Object>> loanLedger(Boolean onlyOverdue) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<ExpBill>()
                .and(w -> w.eq(ExpBill::getBillType, "LOAN")
                        .or().gt(ExpBill::getLoanBalance, BigDecimal.ZERO))
                .notIn(ExpBill::getStatus, "VOID", "DRAFT", "WITHDRAWN", "REJECTED");
        DataScopeKit.apply(qw, ExpBill::getCompanyId, ExpBill::getDeptId, ExpBill::getApplyUserId);
        if (Boolean.TRUE.equals(onlyOverdue)) {
            qw.isNotNull(ExpBill::getExpectRepayDate)
                    .lt(ExpBill::getExpectRepayDate, today)
                    .gt(ExpBill::getLoanBalance, BigDecimal.ZERO);
        }
        List<ExpBill> loans = expBillMapper.selectList(qw);
        Map<Long, List<ExpBill>> byUser = loans.stream()
                .filter(b -> b.getApplyUserId() != null)
                .collect(Collectors.groupingBy(ExpBill::getApplyUserId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ExpBill>> e : byUser.entrySet()) {
            BigDecimal loanTotal = BigDecimal.ZERO;
            BigDecimal balance = BigDecimal.ZERO;
            LocalDate earliestDue = null;
            int unsettled = 0;
            for (ExpBill b : e.getValue()) {
                loanTotal = loanTotal.add(b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount());
                BigDecimal bal = b.getLoanBalance() == null ? BigDecimal.ZERO : b.getLoanBalance();
                balance = balance.add(bal);
                if (bal.signum() > 0) {
                    unsettled++;
                    if (b.getExpectRepayDate() != null
                            && (earliestDue == null || b.getExpectRepayDate().isBefore(earliestDue))) {
                        earliestDue = b.getExpectRepayDate();
                    }
                }
            }
            long overdueDays = (earliestDue == null || !earliestDue.isBefore(today))
                    ? 0L : ChronoUnit.DAYS.between(earliestDue, today);
            ExpBill sample = e.getValue().get(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applyUserId", e.getKey());
            row.put("applyUserName", sample.getApplyUserName());
            row.put("deptId", sample.getDeptId());
            row.put("companyId", sample.getCompanyId());
            row.put("loanTotal", loanTotal);
            row.put("repaidAmount", loanTotal.subtract(balance));
            row.put("balance", balance);
            row.put("unsettledCount", unsettled);
            row.put("earliestRepayDate", earliestDue);
            row.put("overdueDays", overdueDays);
            row.put("overdue", overdueDays > 0);
            rows.add(row);
        }
        // 余额大的排前面，便于资金岗优先催收
        rows.sort((a, b) -> ((BigDecimal) b.get("balance")).compareTo((BigDecimal) a.get("balance")));
        return rows;
    }

    /** 应付 = 核定规则 − 已冲抵（下限 0）。核定付款优先，其次核定报销，最后申请金额。 */
    public static BigDecimal calcPayable(BigDecimal base, BigDecimal offset) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal o = offset == null ? BigDecimal.ZERO : offset;
        BigDecimal r = b.subtract(o);
        return r.signum() < 0 ? BigDecimal.ZERO : r;
    }

    /**
     * #9 含税/不含税录入切换：按录入规则自动拆分价税，返回（可能变化的）含税金额。
     *
     * <p>行 price_type 缺省时继承表头；两者皆无则原样返回，保持现有行为不变。
     * 税率以小数形式存储（0.06 表示 6%）。
     */
    private BigDecimal applyPriceType(ExpBill bill, ExpBillLine line, BigDecimal amount) {
        String pt = line.getPriceType() != null ? line.getPriceType() : bill.getPriceType();
        if (pt == null || pt.isBlank() || amount == null || amount.signum() <= 0) {
            return amount;
        }
        BigDecimal rate = line.getTaxRate();
        if (rate == null || rate.signum() <= 0) {
            return amount; // 无税率无法拆价税
        }
        boolean taxEx = "TAX_EX".equalsIgnoreCase(pt);
        if (!taxEx && !"TAX_IN".equalsIgnoreCase(pt)) {
            return amount; // 未知规则不处理
        }
        BigDecimal net, tax, gross;
        if (taxEx) {
            net = amount;
            tax = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            gross = net.add(tax);
        } else {
            gross = amount;
            net = gross.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            tax = gross.subtract(net);
        }
        line.setNetAmount(net);
        line.setTaxAmount(tax);
        line.setAmount(gross);
        expBillLineMapper.updateById(line);
        return gross;
    }

    /**
     * 草稿路径的轻量行断言。
     *
     * 背景：【提交】走 validateAmountReconcile 全套强校验（行金额非空、数量非负、量价勾稽、
     * 费用项存在性、表头=Σ明细），【保存草稿】却直落 replaceLines，不校验就能落库。
     * 后果：脚本/误操作可批量造出「行金额 ≤ 0」「有金额无费用项」的脏草稿，
     * 这些草稿在列表与草稿箱里显示完全正常，却永远提交不了（提交时才被拦），
     * 只能人工逐张清理；附件/发票关联/分摊都已挂在它们身上，
     * 删除时若级联不全还会留孤儿发票占用记录。
     *
     * 规则：草稿允许「不完整」（可暂缺附件、事由、承担对象），但不允许数值自相矛盾——
     * 一旦填了金额，就必须 > 0；一旦填了金额，费用项就必须选。
     * 这与"草稿可以不填完"的直觉不冲突：不填就留空走 null 过滤，
     * 填了就得填对。
     */
    private void assertDraftLinesSane(List<ExpBillLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ExpBillLine l = lines.get(i);
            if (l == null) {
                continue;
            }
            BigDecimal amt = l.getAmount();
            boolean hasAmt = amt != null && amt.signum() != 0;
            boolean hasItem = l.getItemId() != null;
            // 过滤规则与前端 saveDraft 的 filter(l => l.itemId || Number(l.amount) > 0) 同一规则
            if (!hasAmt && !hasItem) {
                continue;
            }
            int pos = i + 1;
            if (hasAmt && amt.signum() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + pos + " 行明细金额不能为负数（当前 " + amt.toPlainString() + "）");
            }
            if (!hasItem) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + pos + " 行填了金额 " + amt.toPlainString()
                                + " 却没选费用项；如只想留草稿占位，请把金额清空");
            }
        }
    }

    /** 费用日期不得晚于今天；参数 expense.claim.futureDateAllowed=1/true 时放行 */
    private void assertExpenseDatesNotFuture(List<ExpBillLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String allowed = cfgOr(FUTURE_EXPENSE_DATE_KEY, "0");
        if ("1".equals(allowed) || "true".equalsIgnoreCase(allowed)) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (int i = 0; i < lines.size(); i++) {
            ExpBillLine l = lines.get(i);
            if (l != null && l.getExpenseDate() != null && l.getExpenseDate().isAfter(today)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 行费用日期 " + l.getExpenseDate() + " 晚于今天；如业务允许未来费用，请开启参数 "
                                + FUTURE_EXPENSE_DATE_KEY);
            }
        }
    }

    /** 借款预计还款日不得早于借款申请日（仅 LOAN 生效） */
    private void assertExpectRepayDate(String billType, LocalDate applyDate, LocalDate repayDate) {
        if (!"LOAN".equals(billType) || applyDate == null || repayDate == null) {
            return;
        }
        if (repayDate.isBefore(applyDate)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "预计还款日 " + repayDate + " 不得早于借款申请日 " + applyDate);
        }
    }

    /**
     * 单据强校验链（财务共享口径：表头↔明细、价税、汇率、期间勾稽）。
     * 仅在相关字段已填时校验，避免影响未启用价税/外币/跨期场景的单据：
     * R1 表头总金额 = Σ明细金额（原值不符即拦，不静默覆盖）；
     * R2 价税勾稽　不含税 + 税额 = 金额（头、行级一致）；
     * R3 本位币金额 = 原币金额 × 汇率（四舍五入两位，0.01 容差）；
     * R4 会计期间与申请日期一致，防止跨期错配。
     *
     * @param bill 单据头（可能携带前端回传的合计/价税/期间）
     * @param lines 明细行（数据库实落行）
     */
    private void validateAmountReconcile(ExpBill bill, List<ExpBillLine> lines) {
        BigDecimal sumLine = BigDecimal.ZERO;
        boolean priceTypeApplied = false; // #9 是否发生过价税拆分（表头按派生值重算，不做 R1 拦截）
        for (ExpBillLine line : lines) {
            BigDecimal amt = line.getAmount();
            if (amt == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "明细金额为空，无法合计: 行 " + line.getLineNo());
            }
            // #9 含税/不含税录入切换：按录入规则自动拆分价税（2019 年价税分离规则）
            //    TAX_IN 含税录入：录入值=含税 → 不含税=含税/(1+税率)，税额=含税−不含税
            //    TAX_EX 不含税录入：录入值=不含税 → 税额=不含税×税率，含税=不含税+税额
            //    仅当显式指定 price_type 且税率>0 时生效（纯增量，不影响历史单据）
            BigDecimal before = amt;
            amt = applyPriceType(bill, line, amt);
            if (amt != null && before != null && amt.compareTo(before) != 0) {
                priceTypeApplied = true;
            }
            sumLine = sumLine.add(amt);
            // R3 本位币 = 原币 × 汇率（仅非本位币且原币/汇率齐备）
            if (isForeign(line.getCurrencyCode()) && line.getOrigAmount() != null && line.getExchangeRate() != null) {
                BigDecimal calc = line.getOrigAmount().multiply(line.getExchangeRate())
                        .setScale(2, RoundingMode.HALF_UP);
                if (calc.subtract(amt).abs().compareTo(CNY_AMT_TOLERANCE) > 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "明细行 " + line.getLineNo() + " 本位币金额(" + amt + ") ≠ 原币×汇率("
                                    + line.getOrigAmount() + "×" + line.getExchangeRate() + "=" + calc + ")");
                }
            }
            // 量价勾稽（#18）：数量 × 单价 = 金额。金额优先反算单价（有数量有金额无单价时），
            // 否则校验差率：|数量×单价 − 金额| ≤ 0.01（容差）
            if (line.getQuantity() != null && line.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                if (line.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "明细行 " + line.getLineNo() + " 数量不能为负: " + line.getQuantity());
                }
                if (line.getUnitPrice() != null && line.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal calc = line.getQuantity().multiply(line.getUnitPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    if (calc.subtract(amt).abs().compareTo(CNY_AMT_TOLERANCE) > 0) {
                        throw new BizException(ErrorCode.PARAM_INVALID,
                                "明细行 " + line.getLineNo() + " 量价勾稽不符：数量(" + line.getQuantity()
                                        + ")×单价(" + line.getUnitPrice() + ")=" + calc + " ≠ 金额(" + amt + ")");
                    }
                } else if (line.getUnitPrice() == null) {
                    // 金额优先反算单价：单价 = 金额 / 数量（保留 4 位防精度流失），金额为权威规则
                    BigDecimal unit = amt.divide(line.getQuantity(), 4, RoundingMode.HALF_UP);
                    line.setUnitPrice(unit);
                    expBillLineMapper.updateById(line);
                }
            }
            // R2 行级价税：不含税 + 税额 = 金额（仅当价税确实录入，避免误拦未填行级价税的单据）
            if (line.getNetAmount() != null && line.getTaxAmount() != null
                    && (line.getNetAmount().signum() != 0 || line.getTaxAmount().signum() != 0)) {
                BigDecimal nt = line.getNetAmount().add(line.getTaxAmount());
                if (nt.subtract(amt).abs().compareTo(CNY_AMT_TOLERANCE) > 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "明细行 " + line.getLineNo() + " 价税不符：不含税(" + line.getNetAmount()
                                    + ") + 税额(" + line.getTaxAmount() + ") ≠ 金额(" + amt + ")");
                }
            }
        }
        // 明细是否普遍做了价税分离（前端录入 net/tax 或后端拆分均算）：
        // 决定表头不含税/税额是否按明细汇总对齐（修复 D1-03：原逻辑 priceTypeApplied=false 时
        // 表头 net/tax 从不与明细汇总对齐，导致 BX/LOAN 等单据表头税额与明细长期不一致）
        boolean lineHasTaxSplit = lines.stream().anyMatch(l ->
                (l.getNetAmount() != null && l.getNetAmount().signum() != 0)
                        || (l.getTaxAmount() != null && l.getTaxAmount().signum() != 0));
        // R1 表头总金额 = Σ明细（不匹配即拦，从前端回传值判定勾稽是否失真）
        // #9 例外：发生过 TAX_EX/TAX_IN 拆分时，表头录入值为不含税规则，属派生值 → 自动重算不拦截
        //    表头为 0/null 视为客户端未提供合计（派生场景），以 Σ明细 重算，不拦截
        if (bill.getTotalAmount() != null && bill.getTotalAmount().signum() > 0
                && bill.getTotalAmount().subtract(sumLine).abs().compareTo(CNY_AMT_TOLERANCE) > 0
                && !priceTypeApplied) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "表头总金额(" + bill.getTotalAmount() + ") ≠ 明细合计(" + sumLine + ")");
        }
        bill.setTotalAmount(sumLine);
        if (lineHasTaxSplit) {
            // 表头不含税/税额按明细汇总对齐（修复 D1-03：原仅 priceTypeApplied 才对齐，
            // 导致前端直接录入 net/tax 的单据表头价税与明细汇总长期不一致）
            BigDecimal sumNet = BigDecimal.ZERO, sumTax = BigDecimal.ZERO;
            for (ExpBillLine ln : lines) {
                if (ln.getNetAmount() != null) sumNet = sumNet.add(ln.getNetAmount());
                if (ln.getTaxAmount() != null) sumTax = sumTax.add(ln.getTaxAmount());
            }
            bill.setNetAmount(sumNet);
            bill.setTaxAmount(sumTax);
        }
        // R2 头部价税：不含税 + 税额 = 总金额（仅当价税确实录入，避免误拦未做价税分离的单据）
        if (bill.getTotalAmount() != null && bill.getNetAmount() != null && bill.getTaxAmount() != null
                && (bill.getNetAmount().signum() != 0 || bill.getTaxAmount().signum() != 0)) {
            BigDecimal nt = bill.getNetAmount().add(bill.getTaxAmount());
            if (nt.subtract(bill.getTotalAmount()).abs().compareTo(CNY_AMT_TOLERANCE) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "表头价税不符：不含税(" + bill.getNetAmount() + ") + 税额(" + bill.getTaxAmount()
                                + ") ≠ 总金额(" + bill.getTotalAmount() + ")");
            }
        }
        // R4 会计期间与申请日期一致，防跨期错配
        if (bill.getApplyDate() != null) {
            if (bill.getPeriodYear() == null || bill.getPeriodMonth() == null
                    || bill.getPeriodYear() != bill.getApplyDate().getYear()
                    || bill.getPeriodMonth() != bill.getApplyDate().getMonthValue()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "会计期间(" + bill.getPeriodYear() + "-" + bill.getPeriodMonth()
                                + ") 与申请日期(" + bill.getApplyDate() + ")不一致");
            }
        }
    }

    /** 非本位币（人民币）视为外币，需做原币×汇率勾稽 */
    private boolean isForeign(String currencyCode) {
        return currencyCode != null && !"CNY".equalsIgnoreCase(currencyCode);
    }

    /** 金额勾稽容差（分）：0.01 */
    private static final BigDecimal CNY_AMT_TOLERANCE = new BigDecimal("0.01");

    // 撤单 / 作废

    /** 撤回：仅审批中且未处理可撤（WfEngineFacade 内校验），释放预算由流程事件完成 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"APPROVING".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中可撤回，当前状态: " + bill.getStatus());
        }
        wfEngineFacade.withdraw(bill.getProcInstId(), u.getUserId());
    }

    /** 作废：仅草稿。（作废即级联）：同批把该单名下非 VOID 凭证办结——
     *  DRAFT→作废（voidVoucher）、POSTED→红字冲销（reverseVoucher），复用既有机制不另造；
     *  整条链一个事务，任何一张办结失败整单回滚，杜绝「VOID 单＋活凭证」残留。
     *  记录：控制器 @OperLog ＋ voidVoucher 审计记录 ＋ 红字凭证 remark。 */
    @Transactional(rollbackFor = Exception.class)
    public void voidBill(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        // WITHDRAWN（撤回）与 DRAFT 同为「未走完审批链、无凭证」的态，允许一并作废，
        // 否则撤回单无任何办结出口，只能烂在库里占单号与预算。
        if (!"DRAFT".equals(bill.getStatus()) && !"WITHDRAWN".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿/已撤回单据可作废，当前状态: " + bill.getStatus());
        }
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("VOID");
        expBillMapper.updateById(update);
        // 作废即级联——同批办结该单名下非 VOID 凭证（规则与方法注释见 VoucherService）
        int cascaded = voucherService.cascadeVoidBySourceBill(bill, u.getUserId());
        log.info("单据 {} 作废完成（VOID），级联作废凭证 {} 张", bill.getBillNo(), cascaded);
    }

    /**
     * 反审核（配套 · 单据反操作链第二环）：APPROVED → DRAFT，退回申请人修改后重提。
     *
     * <p>前置（通行规则 fail-loud，逐条给可执行文案）：</p>
     * <ol>
     *   <li>仅 APPROVED 可反审；</li>
     *   <li>已制证（voucher_no 非空）不得反审——记账态先行，凭证须先作废；</li>
     *   <li>付款已执行（同单存在 PAID 付款记录）不得反审——既成资金事实走付款退款；</li>
     *   <li>付款记录仍在批次（非 VOID）须先由出纳作废批次；</li>
     *   <li>存在有效发票勾稽须先反核销（发票池→解除关联）。</li>
     * </ol>
     *
     * <p>动作：作废 WAITING 付款记录（pay_status=VOID，记录不物理删）；按行回冲预算——
     * 该行发生过 writeOff 走 {@link BudgetService#reverseWriteOff}（回冲已用），否则走
     * {@link BudgetService#release}（释放占用；提交占用、执行转核销的双态规则）；状态置 DRAFT，
     * 申请人重新编辑提交（重提重新占用预算并新建流程实例，与驳回/撤回重提同轨）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long billId) {
        LoginUser u = UserContext.getRequired();
        // 权限模型：仅制单人可反审核本人单据（撤回同族；SoD 上财务不代制单人反审，
        // 财务对在途单的纠错走流程驳回，对已审单的纠错走付款退款/红字）。
        ExpBill bill = requireMine(billId, u);
        if (!"APPROVED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已审批的单据可反审核，当前状态不允许该操作");
        }
        if (bill.getVoucherNo() != null && !bill.getVoucherNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据已制证（" + bill.getVoucherNo() + "），请先作废凭证再反审核");
        }
        List<ExpBillPayment> pays = expBillPaymentMapper.selectList(
                new LambdaQueryWrapper<ExpBillPayment>()
                        .eq(ExpBillPayment::getBillId, billId));
        if (pays.stream().anyMatch(p -> "PAID".equals(p.getPayStatus()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该单据付款已执行，不得反审核（资金差错请走付款退款单）");
        }
        List<Long> payIds = pays.stream().map(ExpBillPayment::getId).toList();
        if (!payIds.isEmpty()) {
            List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                    new LambdaQueryWrapper<OpPayBatchDetail>()
                            .in(OpPayBatchDetail::getBillPaymentId, payIds));
            for (OpPayBatchDetail d : details) {
                OpPayBatch b = d.getBatchId() == null ? null : opPayBatchMapper.selectById(d.getBatchId());
                if (b != null && !"VOID".equals(b.getStatus())) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "付款记录仍在批次 " + b.getBatchNo() + "（" + b.getStatus() + "），请先由出纳作废批次再反审核");
                }
            }
        }
        Long bindCnt = invoiceFacade.countActiveLinksByBillId(billId);
        if (bindCnt != null && bindCnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该单据存在有效发票勾稽 " + bindCnt + " 笔，请先在发票池解除关联（反核销）再反审核");
        }
        // WAITING 付款记录作废记录（重提后由审批事件重新生成）
        for (ExpBillPayment p : pays) {
            if ("WAITING".equals(p.getPayStatus())) {
                ExpBillPayment upd = new ExpBillPayment();
                upd.setId(p.getId());
                upd.setPayStatus("VOID");
                upd.setRemark("反审核作废：原付款记录随反审核失效，重提后重新生成");
                upd.setUpdateTime(LocalDateTime.now());
                expBillPaymentMapper.updateById(upd);
            }
        }
        // 预算回冲（双态规则：单据级判据——发生过 writeOff 全部走 reverseWriteOff 回冲已用，否则 release 释放占用）
        // 附带点：占用类型按 billType 还原（原 LOAN/APPLY 也被记成 EXP_CLAIM，
        // 释放规则与占用规则必须同名，否则 bg_occupy 台账按类型读数继续失真）。
        String occupyBizType = BudgetService.occupyBizTypeOf(bill.getBillType());
        if ("EXP_REPAY".equals(occupyBizType)) {
            // 还款单在预算侧没有「本单 biz_id 的占用」（修复后它只冲减借款单的占用），
            // 按行 release 会给从未占用的 biz_id 再记一笔负数 → 第二次失真。
            // 预算侧维持"只记录"：借款单占用由 BudgetService.offsetLoanOccupy 的净额判据自校验
            //驳回/撤回后重提能再冲、不双冲，本方法不动它。
            log.warn("还款单 {} 反审核：预算侧无本单占用可释放，bg_occupy 未做变更"
                    + "（借款台账的反冲见下方 rollbackLoanLedgerOnUnaudit）", bill.getBillNo());
        } else {
            boolean executedWriteOff = budgetService.hasWriteOff(billId);
            List<ExpBillLine> lines = expBillLineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                    .eq(ExpBillLine::getBillId, billId));
            for (ExpBillLine line : lines) {
                if (line.getBudgetItemId() == null || line.getAmount() == null) continue;
                if (executedWriteOff) {
                    budgetService.reverseWriteOff(line.getBudgetItemId(), line.getAmount(),
                            occupyBizType, bill.getId(), bill.getBillNo());
                } else {
                    budgetService.release(line.getBudgetItemId(), line.getAmount(),
                            occupyBizType, bill.getId(), bill.getBillNo());
                }
            }
        }
        // 借款冲抵/还款的减记已收敛到审批通过环节
        // （exp_loan_offset 流水 + loan_balance 绝对量重算 + REPAID/PARTIAL_REPAID 翻档，
        //  落点 ExpenseWfEventListener.repayLoan / offsetLoansByClaim），
        // 反审核把单据退回未生效态 DRAFT，就必须在同一次调用里把已扣的额反冲回去，
        // 否则「单未生效、账已结清」——这一形态——在反审核环节上原样重现。
        // 回退算式复用驳回环节那一条（作废流水 + 按台账重算），不另写加减：
        // exp_bill_relation(LOAN_CLAIM) 关系行保留不删，单据回 DRAFT 后其登记额自动重新计入
        // "在途占用"规则（ExpenseDetailService.transitLoanOccupancy 只计 DRAFT/APPROVING），
        // 重提走「本次 − 已登记」差额算式，既不双扣也不双冲。
        // 明确不动的两个环节如实写：①凭证环节——方法开头的 voucher_no 校验已把已制证的单挡在门外；
        //   ②收款单环节（rc_receipt，还款到账登记）——它是已发生的资金事实，
        //   反审核是否连带冲销收款属产品规则，仍留原状待主窗裁。
        BigDecimal restoredLoan = expenseBizAction.rollbackLoanLedgerOnUnaudit(bill);
        if (restoredLoan.signum() > 0) {
            log.info("单据 {} 反审核：借款台账已反冲 {}（冲抵流水作废＋余额/翻档按台账重算）",
                    bill.getBillNo(), restoredLoan);
        }
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("DRAFT");
        expBillMapper.updateById(update);
    }

    // 事前申请关闭 / 终止报销（页面规则）

    /** 申请单关闭：已完成且未被任何单据报销引用时关闭，释放已占用预算，状态→CLOSED */
    @Transactional(rollbackFor = Exception.class)
    public void closeApply(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"APPLY".equals(bill.getBillType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅事前申请单可关闭");
        }
        if (!"APPROVED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批完成且未报销的申请单可关闭，当前状态: " + bill.getStatus());
        }
        long refCnt = expBillRelationMapper.selectCount(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getSourceBillId, billId));
        if (refCnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该申请单已被报销单引用，不能关闭，可执行“终止报销”");
        }
        releaseOccupy(bill);
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("CLOSED");
        expBillMapper.updateById(update);
    }

    /** 申请单终止报销：确定后续不再报销时终止，释放未释放完毕的预算，状态→TERMINATED */
    @Transactional(rollbackFor = Exception.class)
    public void terminateApply(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"APPLY".equals(bill.getBillType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅事前申请单可终止报销");
        }
        if (!"APPROVED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批完成的申请单可终止报销，当前状态: " + bill.getStatus());
        }
        releaseOccupy(bill);
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("TERMINATED");
        expBillMapper.updateById(update);
    }

    // 状态机：APPROVED→PAID→CLOSED

    /** 报销单/对公付款单 APPROVED→PAID（付款完成时触发，可由 PayBatchService 级联调用） */
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        if (!"APPROVED".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅审批通过的单据可标记已付款，当前状态: " + bill.getStatus());
        }
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("PAID");
        // TRC-E：手工/级联付讫同样回填 pay_time（去重：已有值不覆盖）
        update.setPayTime(bill.getPayTime() == null ? LocalDateTime.now() : bill.getPayTime());
        expBillMapper.updateById(update);
        log.info("单据 {} 状态 APPROVED→PAID", bill.getBillNo());
    }

    /** 报销单/对公付款单 PAID→CLOSED（关账，最终状态前的最后一步） */
    @Transactional(rollbackFor = Exception.class)
    public void closeClaim(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireMine(billId, u);
        if (!"PAID".equals(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已付款单据可关账，当前状态: " + bill.getStatus());
        }
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus("CLOSED");
        expBillMapper.updateById(update);
        log.info("单据 {} 状态 PAID→CLOSED", bill.getBillNo());
    }

    /** 释放单据所有明细行占用的预算 */
    private void releaseOccupy(ExpBill bill) {
        List<ExpBillLine> lines = expBillLineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                .eq(ExpBillLine::getBillId, bill.getId()));
        for (ExpBillLine line : lines) {
            if (line.getBudgetItemId() != null && line.getAmount() != null) {
                budgetService.release(line.getBudgetItemId(), line.getAmount(),
                        "EXP_APPLY", bill.getId(), bill.getBillNo());
            }
        }
    }

    /** 合同金额上限校验：累计已付 + 本次 ≤ 合同总额（台账需求：不允许超合同金额付款，超金额需合同变更） */
    private void checkContractLimit(ExpBill bill) {
        var c = contContractMapper.selectById(bill.getContractId());
        if (c == null) return;
        if (c.getNoFixedAmount() != null && c.getNoFixedAmount() == 1) return; // 无固定金额合同不控制
        BigDecimal limit = c.getAmount();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) return;
        List<ContPayRecord> recs = contPayRecordMapper.selectList(
                new LambdaQueryWrapper<ContPayRecord>()
                        .eq(ContPayRecord::getContractId, bill.getContractId()));
        BigDecimal paid = BigDecimal.ZERO;
        for (ContPayRecord r : recs) {
            if (r.getPayAmount() != null) paid = paid.add(r.getPayAmount());
        }
        BigDecimal total = paid.add(bill.getTotalAmount() == null ? BigDecimal.ZERO : bill.getTotalAmount());
        if (total.compareTo(limit) > 0) {
            // 文案腿：原提示只说「请先进行合同变更」，而入口与 change() 校验都要求合同状态＝EFFECTIVE，
            // 流程腿办结的合同停在 APPROVED ⇒ 这句指引对本仓库所有流程审批合同都走不通。改点名可达出口（补充协议／重签／调减本单）。
            throw new BizException(ErrorCode.PARAM_INVALID, "超合同金额付款：合同 " + c.getContractNo() + " 总额 "
                    + limit + "，已付 " + paid + "，本次 " + total.subtract(paid)
                    + "（状态「" + c.getStatus() + "」的合同：已生效可点【变更】调增合同总额；未生效请走【补充协议】或重新签订合同，或调减本单金额）");
        }
    }

    // 查询

    public ExpBill detail(Long billId) {
        LoginUser u = UserContext.getRequired();
        ExpBill bill = requireViewable(billId, u);
        enrichFlow(bill);
        return bill;
    }

    public List<ExpBillLine> lines(Long billId) {
        return expBillLineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                .eq(ExpBillLine::getBillId, billId).orderByAsc(ExpBillLine::getLineNo));
    }

    public List<ExpBillAttach> attaches(Long billId) {
        List<ExpBillAttach> list = expBillAttachMapper.selectList(new LambdaQueryWrapper<ExpBillAttach>()
                .eq(ExpBillAttach::getBillId, billId).orderByAsc(ExpBillAttach::getSort));
        fillAttachFileNames(list);
        fillAttachUploadNames(list);
        return list;
    }


    /**
     * 附件回显补齐原始文件名：exp_bill_attach 只有 file_id，前端缩略图/扩展名/OCR 按钮都按文件名判定，
     * 缺名时草稿重开就只剩一个 FILE 图标（「修改重提后附件看不见」）。
     */
    private void fillAttachFileNames(List<ExpBillAttach> list) {
        Set<Long> fileIds = list.stream().map(ExpBillAttach::getFileId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (fileIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = sysFileMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(SysFile::getId,
                        f -> f.getFileName() == null ? "" : f.getFileName(), (x, y) -> x));
        list.forEach(a -> a.setFileName(nameById.get(a.getFileId())));
    }

    /**
     * 显示面：exp_bill_attach.upload_by 存 sys_user.id，直接给前端只显数字（写入侧补痕见
     * replaceAttaches）；回显批量反查 sys_user 填 uploadByName（nick_name 优先、回落 user_name，
     * 与审批轨迹取名同一规则），一次 IN 查询禁逐行。历史 upload_by=NULL 的行保持空，前端显「—」。
     */
    private void fillAttachUploadNames(List<ExpBillAttach> list) {
        Set<Long> userIds = list.stream().map(ExpBillAttach::getUploadBy)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getNickName()) ? u.getNickName()
                                : (u.getUserName() == null ? "" : u.getUserName()), (x, y) -> x));
        list.forEach(a -> {
            if (a.getUploadBy() != null) {
                a.setUploadByName(nameById.get(a.getUploadBy()));
            }
        });
    }

    /**
     * 我的单据（minBalance 非 null 时只返回 loan_balance > minBalance 的单据，供未冲销借款列表用）。
     * keyword：单号/标题服务端模糊过滤。前端在已加载的几页里做过滤，翻到后面的单据就搜不到，
     * 故两个列表页的搜索都走这里。
     * startDate/endDate：申请日期（apply_date）闭区间，两端各自可缺；source：渠道（PC/H5）等值筛选。
     * 三者均沿用本仓库既有规则——String 直传、空值不加条件，前端不传时不缩小结果集。
     */
    public PageResult<ExpBill> myPage(long pageNum, long pageSize, String billType, String status,
                                      BigDecimal minBalance, String keyword,
                                      String startDate, String endDate, String source) {
        return myPage(pageNum, pageSize, billType, status, minBalance, keyword, startDate, endDate, source,
                null, null);
    }

    /**
     * 同 {@link #myPage(long, long, String, String, BigDecimal, String, String, String, String)}，
     * 追加两个候选面过滤维度。
     *
     * <p>为什么要加：预付冲销的候选清单传了 {@code billSubType:'PREPAY'}，但这个端点从来没有
     * 接这个参数——服务端静默忽略，弹窗标题写着"该供应商未冲销的预付款"，
     * 列出来的却是对公结算单（SETTLE），甚至可能是别家供应商的单。
     * 过滤条件不落到 SQL 上就等于没有，故在服务端补齐。</p>
     *
     * @param billSubType 单据子类型（PREPAY/SETTLE/CLAIM…），空＝不限
     * @param customerId  往来单位（供应商/客户主数据 id），空＝不限
     */
    public PageResult<ExpBill> myPage(long pageNum, long pageSize, String billType, String status,
                                      BigDecimal minBalance, String keyword,
                                      String startDate, String endDate, String source,
                                      String billSubType, Long customerId) {
        LoginUser u = UserContext.getRequired();
        // 甲（读侧补齐，与 requireMine/requireViewable 同一规则）：所有人＝报销人 ∪ 该单登记的代理人。
        // 只按 apply_user_id 过滤时，代理人看不到自己代填的单——代填单在他自己的「我的单据」里整片消失，
        // 且「冲销借款 / 关联事前申请」两个候选面（同用本方法）取不到被代理人名下的借款单与事前申请单，
        // 代填的报销单无法冲抵那笔借款。外溢风险由写侧兜住：addLoanOffset 仍要求
        // 报销人 == 借款单报销人，代 A 填的单冲不了 B 的借款。
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<ExpBill>()
                .inSql(ExpBill::getId, mineBillIdsSql(u.getUserId()));
        if (billType != null && !billType.isBlank()) {
            qw.eq(ExpBill::getBillType, billType);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(ExpBill::getStatus, status);
        }
        // 子类型（PREPAY/SETTLE/…）：预付冲销候选、预付冲销放开额度等候选面都靠它把范围收窄
        if (billSubType != null && !billSubType.isBlank()) {
            qw.eq(ExpBill::getBillSubType, billSubType.trim());
        }
        // 往来单位：冲销只能在同一供应商名下进行，跨供应商冲销会把别人的预付冲掉
        if (customerId != null) {
            qw.eq(ExpBill::getCustomerId, customerId);
        }
        if (minBalance != null) {
            qw.gt(ExpBill::getLoanBalance, minBalance);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ExpBill::getBillNo, kw).or().like(ExpBill::getTitle, kw));
        }
        // 渠道筛选（PC/H5）：空值不加条件
        if (source != null && !source.isBlank()) {
            qw.eq(ExpBill::getSource, source.trim());
        }
        // 申请日期闭区间，两端各自可缺（只传 startDate 即只限下界）；apply_date 为 date 列，
        // 与 InvoiceService 的日期区间过滤同一规则：String 直传、由 ge/le 拼进条件，缺失即整段不加
        if (startDate != null && !startDate.isBlank()) {
            qw.ge(ExpBill::getApplyDate, startDate.trim());
        }
        if (endDate != null && !endDate.isBlank()) {
            qw.le(ExpBill::getApplyDate, endDate.trim());
        }
        qw.orderByDesc(ExpBill::getId);
        Page<ExpBill> p = expBillMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // 单据流程感知：回填当前流程名 + 当前节点（整页一批，不按行往返）
        enrichFlowBatch(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 机票查询分页：当前用户的 APPROVED 事前申请单，
     * 支持 关键字（单号/申请人/标题，服务端 LIKE）与 核销状态（服务端过滤），
     * 并回填 relationUsed（exp_bill_relation 中出现即"已核销"），消除前端 N+1 逐单查关系。
     */
    public PageResult<ExpBill> airTicketPage(long pageNum, long pageSize, String keyword, Integer used) {
        LoginUser u = UserContext.getRequired();
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getApplyUserId, u.getUserId())
                .eq(ExpBill::getBillType, "APPLY")
                .eq(ExpBill::getStatus, "APPROVED");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ExpBill::getBillNo, kw)
                    .or().like(ExpBill::getApplyUserName, kw)
                    .or().like(ExpBill::getTitle, kw));
        }
        if (used != null) {
            // 已核销/未核销过滤下推到库：关联关系表 8.5 万行，原先每页都全量拉回内存再拿它拼
            // 8.5 万个 id 的 IN/NOT IN 列表——端点约 184ms，其中 106ms 花在这次整表拉取，
            // 且拼出的 SQL 文本本身也随关系数线性膨胀。
            if (used == 1) {
                qw.inSql(ExpBill::getId, relationBillIdsSql());
            } else if (used == 0) {
                qw.notInSql(ExpBill::getId, relationBillIdsSql());
            }
        }
        qw.orderByDesc(ExpBill::getId);
        Page<ExpBill> p = expBillMapper.selectPage(Page.of(pageNum, pageSize), qw);
        if (used != null && (used == 1 || used == 0)) {
            // 过滤后本页每行同态，不需要再问关联表
            boolean allUsed = used == 1;
            for (ExpBill b : p.getRecords()) {
                b.setRelationUsed(allUsed);
            }
        } else {
            markRelationUsed(p.getRecords());
        }
        enrichFlowBatch(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 关联关系表里出现过的单据 id 集合（源端 ∪ 目标端）。
     *
     * <p>子查询显式写 {@code tenant_id}——租户拦截器只补主表，不补原生 SQL 片段；
     * 外层套 {@code SELECT id FROM (…UNION…) d} 防止优化器把 UNION 退化成逐行求值。
     */
    private String relationBillIdsSql() {
        long t = TenantContext.getTenantId();
        return "/* tenant-ok: 按 file_id 全局唯一键计数附件引用，无跨租户撞号 */ SELECT id FROM ("
                + "SELECT source_bill_id AS id FROM exp_bill_relation WHERE tenant_id = " + t
                + " UNION SELECT target_bill_id FROM exp_bill_relation WHERE tenant_id = " + t
                + ") d";
    }

    /** 只对当前页这几行回填「已被关联」标志：两枚走索引的小查询，替代整表拉取。 */
    private void markRelationUsed(List<ExpBill> bills) {
        if (bills == null || bills.isEmpty()) {
            return;
        }
        List<Long> ids = bills.stream().map(ExpBill::getId).collect(Collectors.toList());
        Set<Long> usedIds = new HashSet<>();
        for (ExpBillRelation r : expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .select(ExpBillRelation::getSourceBillId, ExpBillRelation::getTargetBillId)
                .in(ExpBillRelation::getSourceBillId, ids))) {
            usedIds.add(r.getSourceBillId());
            if (r.getTargetBillId() != null) {
                usedIds.add(r.getTargetBillId());
            }
        }
        for (ExpBillRelation r : expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .select(ExpBillRelation::getSourceBillId, ExpBillRelation::getTargetBillId)
                .in(ExpBillRelation::getTargetBillId, ids))) {
            usedIds.add(r.getTargetBillId());
            if (r.getSourceBillId() != null) {
                usedIds.add(r.getSourceBillId());
            }
        }
        for (ExpBill b : bills) {
            b.setRelationUsed(usedIds.contains(b.getId()));
        }
    }

    /** 单据流程感知：按 procInstId 查 wf_instance 回填 flowName/currentNode */
    private void enrichFlow(ExpBill bill) {
        try {
            if (bill.getProcInstId() == null || bill.getProcInstId().isBlank()) {
                return;
            }
            WfInstance inst = wfInstanceMapper.selectOne(
                    new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getProcInstId, bill.getProcInstId())
                            .last("limit 1"));
            String key;
            if (inst != null) {
                bill.setCurrentNode(inst.getCurrentNode());
                key = inst.getModelKey();
            } else {
                // 视图行缺失（历史单据只写了引擎表）：退回 wf_task 取节点与流程 key，
                // 否则列表的「当前流程/当前节点」两列对已提交过的单据整片空白。
                WfTask task = wfTaskMapper.selectOne(
                        new LambdaQueryWrapper<WfTask>()
                                .eq(WfTask::getProcInstId, bill.getProcInstId())
                                .orderByDesc(WfTask::getId)
                                .last("limit 1"));
                if (task == null) {
                    return;
                }
                bill.setCurrentNode(task.getNodeName());
                key = task.getModelKey();
            }
            if (key != null) {
                WfModel m = wfModelMapper.selectOne(
                        new LambdaQueryWrapper<WfModel>()
                                .eq(WfModel::getModelKey, key)
                                .last("limit 1"));
                bill.setFlowName(m != null && m.getModelName() != null ? m.getModelName() : key);
            }
        } catch (Exception ex) {
            // 流程信息回填失败不影响列表
        }
    }

    /**
     * 「我的单据」单据 id 集合：报销人名下 ∪ 该单登记的代理人名下。
     *
     * <p><b>为什么不再写成 {@code apply_user_id=? OR agent_user_id=?}</b>：两列分属不同索引，
     * 优化器不做索引合并，会退化为按 {@code uk_tenant_bill_no} 前缀扫全租户 34 万行再逐行回表判
     * （带 status 过滤的 COUNT 约 571ms、端点约 766ms；带关键字 LIKE 时端点约 630ms）。
     * 改成两臂各自走索引物化出 id 集合后，外层按主键过滤：同一查询降到 2.8~3.8ms。
     *
     * <p>子查询里显式写 {@code tenant_id}——租户拦截器只补主表，不补原生 SQL 片段；
     * 外层必须套 {@code SELECT id FROM (…UNION…) d}，否则优化器会把 UNION 退化成逐行求值。
     */
    private String mineBillIdsSql(Long userId) {
        long t = TenantContext.getTenantId();
        return "/* tenant-ok: 按 file_id 全局唯一键计数附件引用，无跨租户撞号 */ SELECT id FROM ("
                + "SELECT id FROM exp_bill WHERE tenant_id = " + t + " AND apply_user_id = " + userId
                + " UNION SELECT id FROM exp_bill WHERE tenant_id = " + t + " AND agent_user_id = " + userId
                + ") d";
    }

    /**
     * 列表用批量回填：与 {@link #enrichFlow} 取值规则完全一致（实例优先、缺失退回最新任务、模型名兜底 key），
     * 只是把「每行 2 次查询」收成「每页最多 3 次」——实例表一批、缺失行退回 wf_task 一批、模型名一批。
     * 20 行的「我的单据」原来打 40 条 SQL，现在 3 条。
     */
    private void enrichFlowBatch(List<ExpBill> bills) {
        if (bills == null || bills.isEmpty()) {
            return;
        }
        try {
            List<String> procInstIds = bills.stream()
                    .map(ExpBill::getProcInstId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            if (procInstIds.isEmpty()) {
                return;
            }
            Map<String, WfInstance> instByProcInst = new LinkedHashMap<>();
            for (WfInstance inst : wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .in(WfInstance::getProcInstId, procInstIds))) {
                instByProcInst.putIfAbsent(inst.getProcInstId(), inst);
            }
            List<String> missing = procInstIds.stream()
                    .filter(id -> !instByProcInst.containsKey(id))
                    .collect(Collectors.toList());
            Map<String, WfTask> taskByProcInst = new LinkedHashMap<>();
            if (!missing.isEmpty()) {
                for (WfTask t : wfTaskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                        .in(WfTask::getProcInstId, missing)
                        .orderByDesc(WfTask::getId))) {
                    taskByProcInst.putIfAbsent(t.getProcInstId(), t);
                }
            }
            Map<String, String> keyByProcInst = new LinkedHashMap<>();
            for (String id : procInstIds) {
                WfInstance inst = instByProcInst.get(id);
                if (inst != null) {
                    keyByProcInst.put(id, inst.getModelKey());
                    continue;
                }
                WfTask t = taskByProcInst.get(id);
                if (t != null) {
                    keyByProcInst.put(id, t.getModelKey());
                }
            }
            Set<String> modelKeys = keyByProcInst.values().stream()
                    .filter(k -> k != null && !k.isBlank())
                    .collect(Collectors.toSet());
            Map<String, String> flowNameByKey = new LinkedHashMap<>();
            if (!modelKeys.isEmpty()) {
                for (WfModel m : wfModelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                        .in(WfModel::getModelKey, modelKeys))) {
                    flowNameByKey.putIfAbsent(m.getModelKey(),
                            m.getModelName() != null ? m.getModelName() : m.getModelKey());
                }
            }
            for (ExpBill bill : bills) {
                String id = bill.getProcInstId();
                if (id == null || id.isBlank()) {
                    continue;
                }
                WfInstance inst = instByProcInst.get(id);
                if (inst != null) {
                    bill.setCurrentNode(inst.getCurrentNode());
                } else {
                    WfTask t = taskByProcInst.get(id);
                    if (t == null) {
                        continue;
                    }
                    bill.setCurrentNode(t.getNodeName());
                }
                String key = keyByProcInst.get(id);
                if (key != null) {
                    String name = flowNameByKey.get(key);
                    bill.setFlowName(name != null ? name : key);
                }
            }
        } catch (Exception ex) {
            // 同 enrichFlow：流程信息回填失败不影响列表
        }
    }

    // 内部工具

    private ExpBill requireMine(Long billId, LoginUser u) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        // 代报销单的写权＝报销人（被代理人）∪ 该单登记的代理人，其余他人单据照旧拒绝。
        // 代理列由保存环节校验「代理人＝填单登录人本人、且与报销人不同人」，故本谓词不会把写权外溢到第三人。
        // 用 Objects.equals 而非 equals：apply_user_id 为 NULL 的历史行直接解引用会 NPE。
        if (!Objects.equals(bill.getApplyUserId(), u.getUserId())
                && !Objects.equals(bill.getAgentUserId(), u.getUserId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权操作他人单据");
        }
        return bill;
    }

    /**
     * 详情可见性归属谓词 = 单据所有人 ∪ 该单流程实例上的办理人/候选人。
     *
     * <p>原 detail() 直接走 {@link #requireMine}，只认 exp_bill.apply_user_id，于是审批链末节点办理人
     * 如总账会计 在审批工作台点「去处理」被拒「无权操作他人单据」，看不到任何单据信息只能盲签。
     * 归属改为按 wf_task 的办理人规则判定（与 WfQueryService.myTodo 同款谓词：
     * assignee_id = 我 或 candidate_ids 含 ,我），即「谁有待办/办过，谁能看」。
     *
     * <p>写路径（草稿/提交/撤回/删除/作废等）继续用 requireMine，本方法只放开只读详情，
     * 不给非所有人任何改单权限。
     */
    private ExpBill requireViewable(Long billId, LoginUser u) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        if (Objects.equals(bill.getApplyUserId(), u.getUserId())
                || Objects.equals(bill.getAgentUserId(), u.getUserId())) {
            // 甲：代报销单的填单人（登记的代理人）同样能看自己代填的单
            return bill;
        }
        if (isFlowHandler(bill, u)) {
            return bill;
        }
        throw new BizException(ErrorCode.PERMISSION_DENIED, "无权查看他人单据");
    }

    /**
     * 流程办理人归属：该单流程实例上存在指派给我、或把我列为候选的待办/已办任务。
     * 查询异常时退回「仅所有人可见」，不因流程库抖动而放大权限。
     */
    private boolean isFlowHandler(ExpBill bill, LoginUser u) {
        if (bill.getProcInstId() == null || bill.getProcInstId().isBlank() || u.getUserId() == null) {
            return false;
        }
        try {
            Long me = u.getUserId();
            Long cnt = wfTaskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getProcInstId, bill.getProcInstId())
                    .in(WfTask::getStatus, "PENDING", "DONE")
                    .and(w -> w.eq(WfTask::getAssigneeId, me)
                            .or().like(WfTask::getCandidateIds, "," + me + ",")));
            return cnt != null && cnt > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 海外报账第一步①：外币行自动带价 + 锁定（仅动外币分支，CNY 行零影响）。
     *
     * <p>行 isForeign 且原币金额非空时按 汇率表(expenseDate，缺省 applyDate) 取系统价：
     * 未填汇率 → 自动填入系统价，amount 为空或仍等于原币金额（用户未改）时按 R3 反算本位币；
     * 已填汇率且与系统价偏差 &gt;2% → 拦截，强制使用系统汇率；
     * 表内无该币种价 → 放行（保持手填自由，兼容历史场景）。
     */
    private void applyExchangePolicy(ExpBill bill, List<ExpBillLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (ExpBillLine line : lines) {
            if (!isForeign(line.getCurrencyCode()) || line.getOrigAmount() == null) {
                continue;
            }
            LocalDate date = line.getExpenseDate() != null ? line.getExpenseDate() : bill.getApplyDate();
            if (date == null) {
                date = LocalDate.now();
            }
            BigDecimal sys = exchangeRateService.lookup(line.getCurrencyCode(), "CNY", date);
            if (sys == null || sys.signum() <= 0) {
                // 查不到价一律拒单：按 1:1 入账会把没折算的本位币金额静默记低。
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "明细行 " + line.getLineNo() + "(" + line.getCurrencyCode() + "→CNY) 在 " + date
                                + " 无可用汇率，本位币无法折算；请在「主数据 → 汇率表」维护覆盖该日期的汇率后重新提交");
            }
            BigDecimal rate = line.getExchangeRate();
            if (rate == null || rate.signum() <= 0) {
                line.setExchangeRate(sys);
                if (line.getAmount() == null || line.getAmount().compareTo(line.getOrigAmount()) == 0) {
                    line.setAmount(line.getOrigAmount().multiply(sys)
                            .setScale(2, RoundingMode.HALF_UP));
                }
            } else if (rate.subtract(sys).abs().compareTo(sys.multiply(RATE_MAX_DEVIATION)) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "明细行 " + line.getLineNo() + "(" + line.getCurrencyCode() + ") 汇率(" + rate
                                + ")与当月汇率表偏差过大，请使用系统汇率 " + sys);
            }
        }
    }

    /** 手工汇率与系统价允许的最大偏差（2%），超出即锁定强制用表价 */
    private static final BigDecimal RATE_MAX_DEVIATION = new BigDecimal("0.02");

    private void replaceLines(Long billId, List<ExpBillLine> lines) {
        expBillLineMapper.delete(new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, billId));
        if (lines != null) {
            fillLineItemNames(lines);
            int no = 1;
            for (ExpBillLine line : lines) {
                line.setId(null);
                line.setBillId(billId);
                line.setLineNo(no++);
                expBillLineMapper.insert(line);
            }
        }
    }

    /**
     * 明细只带 itemId 时 itemName 写入数据库为空，详情/台账整列空白（借款单尤甚，
     * 前端借款行由 syncLoanLine 只写 itemId）。写入数据库前按 itemId 批量反查主数据补齐；
     * 查不到不阻断保存，读取侧还有 formatter 兜底。
     */
    private void fillLineItemNames(List<ExpBillLine> lines) {
        List<Long> missing = lines.stream()
                .filter(l -> l.getItemId() != null && !StringUtils.hasText(l.getItemName()))
                .map(ExpBillLine::getItemId).distinct().toList();
        if (missing.isEmpty()) return;
        Map<Long, String> nameById = mdExpenseItemMapper.selectList(
                        new LambdaQueryWrapper<MdExpenseItem>()
                                .in(MdExpenseItem::getId, missing))
                .stream().filter(it -> StringUtils.hasText(it.getItemName()))
                .collect(Collectors.toMap(
                        MdExpenseItem::getId,
                        MdExpenseItem::getItemName, (a, b) -> a));
        for (ExpBillLine l : lines) {
            if (l.getItemId() != null && !StringUtils.hasText(l.getItemName())) {
                String name = nameById.get(l.getItemId());
                if (name != null) l.setItemName(name);
            }
        }
    }

    /**
     * 服务端复检：培训人员（extJson.persons）同名一人只允许一行——同名重复会把
     * trainTotal 翻倍直接进单据金额。前端可绕过（AI 网关/直连），故服务端必须自己判一次。
     */
    private void assertPersonsUnique(String extJson) {
        if (!StringUtils.hasText(extJson)) return;
        try {
            JsonNode persons =
                    new ObjectMapper().readTree(extJson).get("persons");
            if (persons == null || !persons.isArray()) return;
            Set<String> seen = new HashSet<>();
            for (JsonNode p : persons) {
                String name = p.path("name").asText("").trim();
                if (name.isEmpty()) continue;
                if (!seen.add(name)) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "培训人员「" + name + "」重复登记，同名人员只允许一行");
                }
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception ignore) { /* extJson 非法交由其他校验兜底 */ }
    }

    private void replaceAttaches(Long billId, List<ExpBillAttach> attaches) {
        // 本方法按"整组覆盖"落附件链路：前端附件行的【删除】只把行从表单里摘掉，
        // 若只按差集删 exp_bill_attach，被移除的文件会永久留在 sys_file 成为孤儿——而唯一能删文件的界面
        // 在配置面「文件管理」，员工进不去。
        // 故先记下将被移除的文件，写库后回收其中"本人上传＋已无任何引用"的那部分（不顺手删他人数据／不留悬空引用）。
        Set<Long> keep = new HashSet<>();
        if (attaches != null) {
            for (ExpBillAttach a : attaches) if (a.getFileId() != null) keep.add(a.getFileId());
        }
        List<ExpBillAttach> before = expBillAttachMapper.selectList(
                new LambdaQueryWrapper<ExpBillAttach>().eq(ExpBillAttach::getBillId, billId));
        expBillAttachMapper.delete(new LambdaQueryWrapper<ExpBillAttach>().eq(ExpBillAttach::getBillId, billId));
        if (attaches != null) {
            // 附件上传人：exp_bill_attach.upload_by 是「谁上传谁负责」的唯一凭据，留空即断链。
            // 入参带值（如影像系统/批量迁移已登记归属）时不覆盖；无登录人（引擎线程）时留空不假填。
            Long me = UserContext.get() == null ? null : UserContext.get().getUserId();
            // 同名文件服务端去重——前端查重被绕过（脚本/并发双击）时，
            // 同一文件名重复写入会在票覆盖/影像核验里虚增张数。同名首行保留、后续行丢弃。
            Set<String> seenNames = new HashSet<>();
            for (ExpBillAttach a : attaches) {
                String nameKey = a.getFileName() == null ? "" : a.getFileName().trim();
                if (!nameKey.isEmpty() && !seenNames.add(nameKey)) {
                    continue;
                }
                a.setId(null);
                a.setBillId(billId);
                if (a.getUploadBy() == null) {
                    a.setUploadBy(me);
                }
                expBillAttachMapper.insert(a);
            }
        }
        reclaimDetachedFiles(before, keep);
    }

    /**
     * 回收"本次被移除＋本人上传＋不再被任何单据/冷表/电子档案引用"的文件登记。
     * 三个不放过：他人上传的 不要删；仍被任何在库单据引用的 不要删；已进冷表或电子档案的 不要删（档案完整性优先）。
     * 无登录上下文（引擎线程/批量迁移）时整体跳过，不要把系统行为当成人在删文件。
     */
    private void reclaimDetachedFiles(List<ExpBillAttach> before, Set<Long> keep) {
        Long me = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (me == null || before == null || before.isEmpty()) return;
        for (ExpBillAttach old : before) {
            Long fid = old.getFileId();
            if (fid == null || keep.contains(fid)) continue;
            SysFile f = sysFileMapper.selectById(fid);
            if (f == null || !me.equals(f.getUploadBy())) continue;
            Long liveRefs = expBillAttachMapper.selectCount(
                    new LambdaQueryWrapper<ExpBillAttach>().eq(ExpBillAttach::getFileId, fid));
            Long coldRefs = jdbc.queryForObject(
                    "/* tenant-ok: 按 file_id 全局唯一键计数附件引用，无跨租户撞号 */ SELECT COUNT(*) FROM exp_bill_attach_cold WHERE file_id = ? AND del_flag = 0", Long.class, fid);
            Long archRefs = jdbc.queryForObject(
                    "/* tenant-ok: 按 file_id 全局唯一键计数附件引用，无跨租户撞号 */ SELECT COUNT(*) FROM op_archive WHERE file_id = ? AND del_flag = 0", Long.class, fid);
            if ((liveRefs != null && liveRefs > 0) || (coldRefs != null && coldRefs > 0) || (archRefs != null && archRefs > 0)) {
                continue;
            }
            sysFileService.delete(fid);
            log.info("[1b] 附件回收：bill 覆盖后移除的孤儿文件已注销 sys_file#{}（上传人 {}，本次操作人 {}）", fid, f.getUploadBy(), me);
        }
    }

    /** 本租户管理员（is_admin=1），作为默认审批人 */
    private Long findTenantAdmin() {
        List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsAdmin, 1).eq(SysUser::getStatus, "NORMAL").last("limit 1"));
        return admins.isEmpty() ? null : admins.get(0).getId();
    }

    private String genBillNo(String billType) {
        // 优先走编号规则（sys_number_rule 行锁取号）；未配置规则时降级旧逻辑
        String ruleCode = switch (billType == null ? "CLAIM" : billType) {
            case "APPLY" -> "EXP_APPLY";
            case "LOAN" -> "EXP_LOAN";
            case "REPAY" -> "EXP_REPAY";
            case "PAY" -> "EXP_PAY";
            default -> "EXP_CLAIM";
        };
        try {
            return numberRuleService.next(ruleCode);
        } catch (Exception e) {
            log.warn("编号规则取号失败(ruleCode={})，降级旧编号: {}", ruleCode, e.getMessage());
        }
        String prefix = switch (billType == null ? "CLM" : billType) {
            case "APPLY" -> "SQ";
            case "LOAN" -> "JK";
            case "REPAY" -> "HK";
            case "PAY" -> "FK";
            default -> "BX";
        };
        return prefix + System.currentTimeMillis();
    }
    /**
     * 小额自动审核＋抽检：
     * 开关 expense.autoaudit.enabled=1 且 total ≤ expense.autoaudit.maxamount 且 billType=CLAIM 时，
     * 以系统身份（sysauto）依次通过链上全部节点至最终状态；并按 expense.autoaudit.samplerate 概率打
     * 「进抽检池」标记（ext_json.autoAuditSample=true），抽检列表按此查询。任何异常只降级为不自动审。
     */
    private void autoAuditIfEligible(ExpBill bill, Long billId, String bizType, String procInstId, BigDecimal total) {
        try {
            if (!"EXP_CLAIM".equals(bizType)) return;
            if (!"1".equals(cfgOr("expense.autoaudit.enabled", "1"))) return;
            BigDecimal max = new BigDecimal(cfgOr("expense.autoaudit.maxamount", "2000"));
            if (total == null || total.compareTo(max) > 0) return;
            SysUser sys = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUserName, "sysauto").last("LIMIT 1"));
            if (sys == null) { log.warn("小额自动审核跳过：sysauto 系统账号未建"); return; }
            int guard = 0;
            while (guard++ < 40) {
                List<WfTask> open = wfTaskMapper.selectList(
                        new LambdaQueryWrapper<WfTask>()
                                .eq(WfTask::getProcInstId, procInstId)
                                .eq(WfTask::getStatus, "PENDING"));
                if (open == null || open.isEmpty()) break;
                for (WfTask t : open) {
                    wfEngineFacade.complete(t.getTaskId(), true, "系统自动审核（小额免审）", sys.getId(), null, null, null, null);
                }
                List<WfTask> still = wfTaskMapper.selectList(
                        new LambdaQueryWrapper<WfTask>()
                                .eq(WfTask::getProcInstId, procInstId)
                                .eq(WfTask::getStatus, "PENDING"));
                if (still != null && still.size() >= open.size()) break; // 防呆：无推进即退出
            }
            boolean sample = ThreadLocalRandom.current().nextInt(100)
                    < Integer.parseInt(cfgOr("expense.autoaudit.samplerate", "50"));
            ExpBill flag = new ExpBill();
            flag.setId(billId);
            String ext = bill.getExtJson() == null || bill.getExtJson().isBlank() ? "{}" : bill.getExtJson();
            String extNoBrace = ext.endsWith("}") ? ext.substring(0, ext.length() - 1) : ext;
            flag.setExtJson(extNoBrace + (extNoBrace.isEmpty() ? "" : ",") + "\"autoAudit\":true,\"autoAuditSample\":" + sample + "}");
            expBillMapper.updateById(flag);
            log.info("小额自动审核完成 billId={} amount={} 抽检标记={}", billId, total, sample);
        } catch (Exception ex) {
            log.warn("自动审核失败（降级为人工审批）billId={}: {}", billId, ex.getMessage(), ex);
        }
    }

    private String cfgOr(String key, String def) {
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
            return c == null || c.getConfigValue() == null || c.getConfigValue().isBlank() ? def : c.getConfigValue().trim();
        } catch (Exception ex) {
            return def;
        }
    }

}
