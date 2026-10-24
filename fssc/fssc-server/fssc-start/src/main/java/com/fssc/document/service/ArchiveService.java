package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("complianceArchiveService")
@RequiredArgsConstructor
public class ArchiveService {

    private final OpArchiveMapper archiveCatalogMapper;
    /**
     * D-DEC-180（P1，12-31:158）：完整性哈希的<b>唯一算式</b>已收拢到 {@link ArchiveIntegrityService}。
     * 本类原有的两个私有算法（{@code computeIntegrityHash}＝档案号|ref|类型|文件，
     * {@code calculateIntegrityHash}＝档案编码|类型|单据|凭证|附件）连同
     * {@code BillArchiveService.doArchive} 里"直接写主文件裸 MD5"的第三种写法一并废止——
     * 三套算式并存正是"同单异值＝误报、异卷同值＝漏报"的根因。
     */
    private final ArchiveIntegrityService archiveIntegrityService;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    private final com.fssc.framework.facade.BillFacade billFacade;
    /**
     * D-DEC-028：读档案时把「归档审批流自身」的实例号富化出来（只读 wf_instance，不写任何流程表）。
     * 用视图而非引擎门面——档案页只需要"这条卷是被哪次审批定论的"，不需要驱动能力。
     */
    private final WfInstanceMapper wfInstanceMapper;

    /**
     * D-DEC-005（X-19）：非单据载体的「归档对象类型」解析器（按域各自提供 bean，无实现则不解析）。
     * 存在性校验与目录要素（关联单号/标题/所属公司）都由它给，登记簿因此不需要任何业务表加列。
     */
    private final java.util.List<ArchiveCarrierResolver> carrierResolvers;

    /**
     * V1.4.92（AMC-05）：档案非集中管理判定复用 {@code DeliveryService.isNonCentralArchive}
     * （模式的唯一读点），此处只为给「原件保管单位」落默认值，不另读 {@code sys_company.archive_mode}。
     */
    private final com.fssc.sharing.service.DeliveryService deliveryService;

    /**
     * 档案类型权威词表（RA-D255a，2026-09-27 REF-C 判类＝<b>闭集枚举</b>）：
     * 前 8 项与前端 {@code ArchiveBoard.vue} 的 {@code archiveTypeLabel} 同一套；D-DEC-005 加的
     * {@code BANK_SLIP}/{@code SUPPLIER} 两类是「非单据载体」登记用，前端标签暂缺（列表回落显示原码），
     * 补标签属前端文件改动、不在本轮范围。
     *
     * <p><b>为什么不建"可配置档案"表位</b>：库里 {@code sys_dict_type} 仅 1 行、{@code sys_dict_data} 仅 3 行
     * （全是 expense_type），档案域两列从未接字典 ⇒ 厂商那套"集团维护的可配置档案"在我方只有骨架没有实现，
     * 走它属扩工程且要写库（升人工裁决 ④ 组）。普查见
     * {@code 人工测试/已裁RA35条_厂商做法参考与落码对齐册_20260927.md} §七·二。
     *
     * <p> 已知不一致（<b>只登记、不改 DDL</b>，改列注释属 DDL 需另裁）：{@code op_archive.archive_type}
     * 的列注释只写 5 项（BILL/VOUCHER/CONTRACT/REPORT/OTHER），库里却在用 EXPENSE 7 行——列注释不是权威词表。
     * 历史行一律不回写（演练数据保留铁律），本白名单只挡新增。
     */
    static final java.util.Set<String> ARCHIVE_TYPES = java.util.Set.of(
            "BILL", "VOUCHER", "EXPENSE", "PAYMENT", "CONTRACT", "INVOICE", "REPORT", "OTHER",
            // D-DEC-005：非单据载体靠类型进登记簿（业务表零加列）——BANK_SLIP 是银行回单登记已在写的取值，
            // SUPPLIER 是供应商载体；BILL/EXPENSE/PAYMENT 的既有语义与回写白名单一字未动。
            "BANK_SLIP", "SUPPLIER");

    /** 词表守卫：不在集内的取值直接拒（不静默清洗成默认值——清洗会把错值变成"看不见的错值"）。D-DEC-005：三条立卷入口＋回单登记入口共用 */
    public static String assertArchiveType(String archiveType) {
        String v = archiveType == null ? null : archiveType.trim().toUpperCase();
        if (v == null || !ARCHIVE_TYPES.contains(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案类型取值不在词表内: " + archiveType + "，可选 " + new java.util.TreeSet<>(ARCHIVE_TYPES));
        }
        return v;
    }

    /**
     * D-DEC-095（12-22:113）／D-DEC-021（12-08:69）：<b>回写白名单的唯一权威</b>——
     * 词表里「{@code bill_id}/{@code ref_id} 语义＝{@code exp_bill} 主键」的那一类。
     *
     * <p>逐类判据（按建卡入口实测，不按猜）：
     * <ul>
     *   <li>{@code BILL}＝{@code BillArchiveService.doArchive/eArchiveBill} 写入，{@code ref_id}＝单据主键 ✔</li>
     *   <li>{@code EXPENSE}＝UI「新建档案目录→报销凭证」默认类型（{@code ArchiveController} 立卷入参），
     *   <li>{@code PAYMENT}＝付款/结算类档案。本系统付款单/对公结算单<b>本身就在 {@code exp_bill}</b>
     *       （{@code bill_type} APPLY/CLAIM/LOAN/REPAY/PAY，FK/HK 单号），与 {@code BILL}/{@code EXPENSE} 同类目 ✔</li>
     *   <li>{@code VOUCHER}＝凭证主键、{@code CONTRACT}＝{@code cont_contract} 主键、{@code INVOICE}＝发票主键、
     *       {@code REPORT}＝报表主键——裸主键跨域回写必串号（{@code id} 段完全重叠，D-DEC-244／261 同族）✘</li>
     *   <li>{@code BANK_SLIP}（{@code BankReconcileService:574} 登记，ref_id/bill_id 均为
     *       {@code op_bank_statement.id}）与 {@code OTHER} ✘</li>
     * </ul>
     *
     * <p>白名单只是<b>第一道</b>；第二道仍是调用方的 {@code billFacade.findBillForArchive(id) != null} 反查，
     * 两道都过才回写。历史行不回写、不清洗（演练数据保留铁律）。
     */
    public static final java.util.Set<String> BILL_SCOPED_ARCHIVE_TYPES =
            java.util.Set.of("BILL", "EXPENSE", "PAYMENT");

    /** 档案类型的 ref 是否指向费用单据（回写/收尾两条链路共用，防再次各写一套谓词——D-DEC-095 的成因就是两处分叉） */
    public static boolean isBillScopedType(String archiveType) {
        return archiveType != null && BILL_SCOPED_ARCHIVE_TYPES.contains(archiveType.trim().toUpperCase());
    }

    /**
     * D-DEC-005（X-19）：立卷前按「归档对象类型」反查载体本身——<b>非单据载体绝不经 exp_bill</b>
     * （合同主键拿去查 {@code exp_bill} 会命中同号的单据＝跨域裸主键串号，D-DEC-244/261 同族）。
     * 有解析器的类型查不到载体即拒绝立卷（不写悬空卷）；无解析器的类型只登记、要素留空、不造数。
     */
    private ArchiveCarrierResolver.Carrier resolveCarrier(String archiveType, Long refId) {
        if (refId == null || refId <= 0L || carrierResolvers == null) {
            return null;
        }
        for (ArchiveCarrierResolver r : carrierResolvers) {
            if (r.archiveTypes() != null && r.archiveTypes().contains(archiveType)) {
                ArchiveCarrierResolver.Carrier c = r.resolve(refId);
                if (c == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "立卷失败：档案类型 " + archiveType + " 下不存在主键 " + refId + " 的归档对象，"
                                    + "请从该对象的列表中选择后立卷");
                }
                return c;
            }
        }
        return null;
    }

    /** D-DEC-005：非单据载体的目录要素补齐——只填登记簿已有的列（关联单号/标题/归属公司），载体表零加列、零回写 */
    private void fillCatalogFromCarrier(OpArchive archive, ArchiveCarrierResolver.Carrier c) {
        if (archive == null || c == null) {
            return;
        }
        if (isBlank(archive.getRefNo())) {
            archive.setRefNo(c.refNo());
        }
        if (isBlank(archive.getTitle())) {
            archive.setTitle(firstNonBlank(c.title(), c.refNo()));
        }
        if (archive.getCompanyId() == null) {
            archive.setCompanyId(c.companyId());
        }
    }

    /**
     * 解析档案行的回写目标：{@code bill_id} 优先，缺则取 {@code ref_id}（{@code doArchive} 只写 ref_id 不写 bill_id，
     * 见 {@code ArchiveFacade} L30 D-TRC-06 注释）；类型不在白名单时返回 {@code null}＝不回写。
     */
    public static Long resolveSourceBillId(OpArchive archive) {
        if (archive == null || !isBillScopedType(archive.getArchiveType())) {
            return null;
        }
        return archive.getBillId() != null ? archive.getBillId() : archive.getRefId();
    }

    /**
     * D-DEC-048（12-16:60）：判重视为<b>在册/在途</b>的归档状态（真相源 {@code archive_status} 轴，
     * 定列依据见 {@code OpArchive.archiveStatus} 的 D-DEC-182 注释）。
     *
     * <p><b>DISPOSED 不在集内＝销毁后允许重新立卷</b>：原卷已销毁、载体已下架，同一单据再立一卷
     * 是共享中心的常规补录动作；若产品裁定"销毁过的单据永不再立卷"，只需把 DISPOSED 并进本集
     * （单点改动，三条立卷入口共用）。
     */
    static final java.util.Set<String> LIVE_VOLUME_STATUSES =
            java.util.Set.of("PENDING", "ARCHIVING", "ARCHIVED");

    /**
     * D-DEC-048：按<b>来源单据键</b>查出该单据仍存在的在册/在途卷（空列表＝可立卷）。
     *
     * <p><b>键的取法</b>：{@code ref_id} 与 {@code bill_id} 二者命中任一即算同一单据——
     * {@code BillArchiveService.doArchive}（影像归档链路）只写 ref_id，
     * {@code createArchive}/{@code eArchiveBill} 两列都写，只用一列判会漏掉整条影像链路。
     *
     * <p><b>类型维度</b>：{@code BILL}/{@code EXPENSE}/{@code PAYMENT} 三类的 ref 都指 {@code exp_bill}
     * 主键（判据见 {@link #BILL_SCOPED_ARCHIVE_TYPES} 注释）⇒ 必须<b>跨这三类</b>判重，
     * 否则新建档案目录时把类型从「报销凭证」改成「单据档案」就绕开了闸（原 D-DEC-276b 的实现正是
     * 按 (archive_type, ref_id) 精确判，所以「同一单据可无限次重复立卷」在换类型下依旧成立）；
     * 其余类型（VOUCHER/CONTRACT/INVOICE/REPORT/OTHER）的 ref 指各域主键，裸主键跨域必串号
     * （D-DEC-244／261 同族），只能在<b>同 archive_type</b> 内判重。
     *
     * <p><b>租户维度</b>：{@code op_archive} 非忽略表，{@code FsscTenantLineHandler} 自动追加
     * {@code tenant_id} 条件，{@code @TableLogic} 自动过滤 del_flag ⇒ 本查询天然按租户＋未作废行判重，
     * 无需手工条件（手工加会与拦截器各写一套、留下漏网口径）。
     *
     * <p><b>无来源键＝不判重</b>：控制器在「关联单据」留空时兜的是 {@code 0L}（H-04 的解析口径），
     * 这类卷没有来源单据，不存在"同一单据重复立卷"可言；改前按 {@code ref_id=0} 判重，
     * 会把第二张<b>无来源</b>的卷也拦下——那是误伤，不是本条要的闸。
     */
    public List<OpArchive> findLiveVolumes(Long sourceId, String archiveType) {
        if (sourceId == null || sourceId <= 0L) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        qw.in(OpArchive::getArchiveStatus, LIVE_VOLUME_STATUSES)
                .and(w -> w.eq(OpArchive::getRefId, sourceId).or().eq(OpArchive::getBillId, sourceId));
        if (isBillScopedType(archiveType)) {
            qw.in(OpArchive::getArchiveType, BILL_SCOPED_ARCHIVE_TYPES);
        } else if (archiveType != null && !archiveType.isBlank()) {
            qw.eq(OpArchive::getArchiveType, archiveType.trim().toUpperCase());
        }
        qw.orderByAsc(OpArchive::getId);
        return archiveCatalogMapper.selectList(qw);
    }

    /**
     * D-DEC-048：立卷前的重复硬闸——已有在册/在途卷即拒绝，并把<b>原卷号</b>如实报出来
     * （改前的文案只说"已立卷 N 次"，经办人拿着报错既找不到原卷、也做不了后续处置）。
     *
     * <p>存量重复行不回写、不删除（演练数据保留铁律），本闸只挡新增。
     */
    private void assertNoLiveVolumeForSource(Long sourceId, String archiveType) {
        List<OpArchive> live = findLiveVolumes(sourceId, archiveType);
        if (live.isEmpty()) {
            return;
        }
        StringBuilder nos = new StringBuilder();
        for (OpArchive a : live) {
            if (nos.length() > 0) {
                nos.append("、");
            }
            nos.append(firstNonBlank(a.getArchiveNo(), a.getArchiveCode(), "档案#" + a.getId()))
                    .append('（').append(archiveStatusLabel(a.getArchiveStatus())).append('）');
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "该单据已立卷 " + live.size() + " 次，同一单据不得重复立卷；原档案卷号：" + nos
                        + "。如需重新立卷，请先处置原卷（删除登记或执行销毁）——已销毁的卷不再占用键位");
    }

    /** 归档状态→人话（与前端 ArchiveBoard.vue archStatusLabel、ArchivePage.vue ARCH_STATUS_LABEL 同一套词表） */
    private static String archiveStatusLabel(String archiveStatus) {
        if (archiveStatus == null || archiveStatus.isBlank()) {
            return "未归档";
        }
        return switch (archiveStatus.trim().toUpperCase()) {
            case "ARCHIVING" -> "归档审批中";
            case "ARCHIVED" -> "已归档";
            case "DISPOSED" -> "已销毁";
            case "PENDING" -> "待归档";
            default -> archiveStatus;
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createArchive(Long billId, String archiveType, Integer archiveYear,
                              String archivePeriod, Integer retentionPeriod) {
        // 必填守卫（前端 ARCHIVE_CREATE 登记表同口径）：年度/期间/保管期限决定到期销毁日期，
        // 缺失时原逻辑按「当年 + 10 年」推算，等于凭默认值立卷。
        if (archiveType == null || archiveType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择档案类型");
        }
        archiveType = assertArchiveType(archiveType);
        if (archiveYear == null || archiveYear < 2000 || archiveYear > 2100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入年度");
        }
        if (archivePeriod == null || archivePeriod.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入期间");
        }
        if (!archivePeriod.matches("\\d{4}-\\d{2}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间格式应为 yyyy-MM");
        }
        if (retentionPeriod == null || retentionPeriod <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择保管期限");
        }
        // D-DEC-276b（B2，2026-09-28 已裁）＋ D-DEC-048（12-16:60）：同一来源单据已有在册/在途卷时拒绝再次立卷。
        // 判重口径已收拢到 assertNoLiveVolumeForSource() 单点（三条立卷入口共用，见该方法注释）。
        assertNoLiveVolumeForSource(billId, archiveType);
        // D-DEC-005：bill_id 只对「ref 就是 exp_bill 主键」的那几类落值；非单据载体只落 ref_id＋类型，
        // 否则合同/供应商主键会挂进单据联查（getByBillId／findByBillId）里冒充某张单据的档案。
        boolean billCarrier = isBillScopedType(archiveType);
        OpArchive archive = new OpArchive();
        archive.setBillId(billCarrier ? billId : null);
        archive.setArchiveNo(nextArchiveNo(archiveType));
        // D-DEC-184（12-31:162）：archive_code 列注释即「档案编码(冗余)」，此前人工立卷路径从不写它
        // （只有 autoArchive 写，且写的是 100 秒内必重的时间戳后 5 位）⇒ 实测该列大面积 NULL。
        // 现取「编号镜像」口径：唯一性由 uk_tenant_archive_no 保证，编码不再另起一套号源。
        archive.setArchiveCode(archive.getArchiveNo());
        archive.setRefId(billId);
        archive.setArchiveType(archiveType);
        archive.setArchiveYear(archiveYear);
        archive.setArchivePeriod(archivePeriod);
        // 缺陷修复（V1.3.17）：自动计算到期销毁日期。
        // 依据《会计档案管理办法》：保管期限从会计年度终了后的第一天算起 →
        // destroyDate = (archiveYear + retentionYears + 1) 年 1 月 1 日。
        // 此前不设 destroy_date 导致销毁申请强校验"未设置到期销毁日期，禁止销毁"。
        // 年度/保管年限的"未填即兜当年/10 年"已在上面的必填守卫处收口，不再凭默认值立卷。
        int retention = retentionPeriod;
        archive.setRetentionYears(retention);
        archive.setDestroyDate(destroyDateOf(archiveYear, null, retention));
        archive.setArchiveStatus("PENDING");
        // D-DEC-182：status 列不再由各写入点随手填，一律由真相源 archive_status 映射（同库默认 FILED 一致）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        archive.setIntegrityStatus("UNKNOWN");
        // D-DEC-276a（A1，2026-09-28 已裁）：立卷动作本身必须留下"谁立的卷"。行业方案〔FSSC·1034/1033/1338〕
        // 的归档记录把「归档人/归档时间/创建人」做成固定栏位，而非事后备注；此前本方法从不写这两列，
        // 实测 archive_type='EXPENSE' 的 8 行 filing_user_id 全空（BILL 路径由 BillArchiveService 写）。
        // 无登录上下文时保持 NULL，不造假人。
        archive.setFilingUserId(userId());
        archive.setFilingTime(LocalDateTime.now());
        // 缺陷12(20260918)：创建档案目录未落 company_id，而档案列表按登录人公司过滤(OrgGuard.enforceCompany)，
        // 导致创建者本人也看不到刚建的档案（本公司用户均不可见）。此处按当前登录人公司落值，与列表过滤口径一致。
        com.fssc.framework.security.LoginUser _cu = com.fssc.framework.security.UserContext.get();
        if (_cu != null && _cu.getCompanyId() != null) {
            archive.setCompanyId(_cu.getCompanyId());
        }
        // D-TRC-05：立卷时回溯来源单据的审批流程实例。手工立卷的 billId 是自由文本、不带域标识，
        // 口径与 TRC-C 回填一致＝按 exp_bill 主键反查；查不到（非费用单据或输错）留 NULL。
        if (billCarrier) {
            com.fssc.framework.facade.dto.BillArchiveDTO srcBill =
                    billId == null ? null : billFacade.findBillForArchive(billId);
            if (srcBill != null) {
                archive.setProcInstId(srcBill.getProcInstId());
            }
            // D-DEC-184／D-DEC-185：目录要素（关联单号／标题／会计年度月份／归属公司）按来源单据真值补齐，
            // 与 autoArchive 同一把尺；无载体不造 file_id 引用。
            fillCatalogFromSourceBill(archive, srcBill);
        } else {
            // D-DEC-005：非单据载体一律不反查 exp_bill（跨域裸主键串号），要素由本域解析器给
            fillCatalogFromCarrier(archive, resolveCarrier(archiveType, billId));
            fillPeriodFromArchivePeriod(archive);
        }
        // V1.4.92（AMC-05）：公司已解析完成，再按模式补「原件保管单位」
        applyPaperKeeperDefault(archive);
        archiveCatalogMapper.insert(archive);
        log.info("创建档案: id={}, billId={}, archiveType={}, 保管至 {}", archive.getId(), billId, archiveType,
                archive.getDestroyDate());
        return archive.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        String cur = archive.getArchiveStatus();
        // D-DEC-027／D-DEC-181 状态机硬闸：FM_ARCHIVE 审批在途不得重复发起（每次点都会新起一条实例）。
        // 判据用引擎实况而非只看列值——流程管理员 cancelInstance 作废实例不发布收尾事件（WfEngineFacade:3023），
        // 只看 ARCHIVING 会把档案永久锁死在途态（D-DEC-20 那类"永久不可办"）；无在办任务即放行重驱动。
        // 引擎查询异常时 fail-open（宁可重驱动，不可卡死）。已销毁档案一律不得再归档。
        if ("ARCHIVING".equals(cur) && hasRunningArchiveFlow(archiveId)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案 " + archive.getArchiveNo() + " 归档审批进行中（FM_ARCHIVE 未闭环），不得重复执行归档");
        }
        if ("DISPOSED".equals(cur)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案 " + archive.getArchiveNo() + " 已销毁，不得再执行归档");
        }
        // ARCHIVED 行不拦：反归档（unarchive）全仓未实现，放开重执行是历史终态卷唯一的整改入口；
        // 要不要收紧属产品裁决（D-DEC-048 的立卷判重闸 assertNoLiveVolumeForSource 与此无关——
        // 它拦的是"同一单据再建一张卷"，这里放行的是"同一张卷重跑归档审批"，两码事）。
        // D-DEC-027（12-09A:56）／D-DEC-181（12-31:159）：执行归档不再落终态。
        // 本方法随后就发 FM_ARCHIVE 审批流（下方 wfEngineFacade.start），此刻三节点一个没办，
        // 原先却已把 archive_status=ARCHIVED、integrity_status=VERIFIED、archived_at 全写完，
        // 审批闭环只剩"把同样的值再写一遍"（实测 3 节审批仅 update_time 变化），驳回也没有回退。
        // 现改为只进中间态 ARCHIVING（op_archive.archive_status 既有枚举 PENDING/ARCHIVING/ARCHIVED/DISPOSED，
        // 见 fssc_op_archive.sql L51，未新增取值；追溯链 archivedCount 已按"PENDING/ARCHIVING 不计"口径实现），
        // 终态 ARCHIVED／archived_at／VERIFIED 与源单据回写一律由审批闭环 ArchiveFacadeImpl.handleArchiveApproval 落。
        archive.setArchiveStatus("ARCHIVING");
        // D-DEC-182：真相源每次跃迁都同源回写镜像列（顺带把历史行里写进 status 的错轴取值纠正回本轴词表）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        // D-DEC-184／D-DEC-185：执行归档前按来源单据把仍缺的目录要素补齐（只补空、不覆盖真值）。
        // 276a/184 上线前立的历史卷 archive_code/ref_no/title/period_year/period_month 多是半套，
        // 而本方法紧接的哈希与 FM_ARCHIVE 标题都要用它们——先补再封，快照与目录才一致。
        if (isBillScopedType(archive.getArchiveType())) {
            Long srcBillId = resolveSourceBillId(archive);
            fillCatalogFromSourceBill(archive, srcBillId == null ? null : billFacade.findBillForArchive(srcBillId));
        }
        if (archive.getArchiveCode() == null && archive.getArchiveNo() != null) {
            archive.setArchiveCode(archive.getArchiveNo());   // 列注释＝"档案编码(冗余)"⇒ 编号镜像
        }
        // #20 防篡改：归档快照哈希仍在执行归档时固化（文件此刻才移入归档区），
        // 但"校验通过"是审批闭环的结论——哈希已算≠已校验，此处只留 UNKNOWN，闭环重算比对后才置 VERIFIED。
        archive.setIntegrityHash(archiveIntegrityService.snapshot(archive));
        archive.setIntegrityStatus("UNKNOWN");
        archiveCatalogMapper.updateById(archive);
        // D-DEC-276a 的"归档生效即回写源单据"半边不删、只是后移：
        // "生效"＝审批闭环通过，而非点击执行。原前置写 markSourceBillArchived() 已从此处摘除，
        // 由 handleArchiveApproval 在同一语义下回写（同一白名单 ArchiveService.isBillScopedType）。
        // exp_bill.archive_status 只有 UNARCHIVED/ARCHIVED 两值（fssc_exp_bill.sql L76），无中间态可用，
        // 且不得新造取值 ⇒ 审批在途期间单据侧保持 UNARCHIVED（不置位），终态才 ARCHIVED。
        // 接线 M1 FM_ARCHIVE 凭证归档审批流：执行归档即发起归档审批
        try {
            // D-DEC-028：起流的返回值就是「档案自身的审批实例号」。表上暂无列可存（加列属 DDL 裁决，
            // 注释态 SQL 见 OpArchive 类注释），先如实记进日志；读侧由 fillArchiveFlowKey 从
            // wf_instance 富化出同一个值，档案反查归档审批链由此拿到明确可点的键。
            // D-DEC-185（12-31:163）后半：流程标题不再拿 biz_id 凑名——实测 wf_instance.title=「凭证归档-24」
            // 这种串既看不出是哪张单、也对不上任何业务号，审批人在待办里无从核对。
            // 取真名的优先序：档案标题（立卷时已按来源单据真名补齐，见 fillCatalogFromSourceBill）
            // → 关联单号 ref_no → 档案编号 archive_no → 档案编码 archive_code；
            // 全部缺失才退回「档案#ID」（仍带 ID，但前面已尽力取真名，且记 warn 便于回补）。
            String flowTitle = archiveFlowTitle(archive);
            String archiveProcInstId = wfEngineFacade.start("FM_ARCHIVE", archiveId, userId(), flowTitle, null);
            log.info("档案 {} 已发起归档审批：FM_ARCHIVE procInstId={}（档案自身实例号，"
                            + "与 op_archive.proc_inst_id 里的源单据实例号不是一回事，D-DEC-028）",
                    archive.getArchiveNo(), archiveProcInstId);
        } catch (Exception ex) {
            log.error("档案 {} 发起归档审批失败: {}", archiveId, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        log.info("执行归档: archiveId={} → ARCHIVING（待审批闭环定稿）", archiveId);
    }

    /**
     * 该档案是否仍有在办的 FM_ARCHIVE 审批任务（{@code WfEngineFacade.hasActiveTask}，按流程变量
     * bizType/bizId 查 Flowable 运行时任务）。查询异常按"无在办"处理＝放行，
     * 档案员最多重复发起一次，绝不能因引擎不可查而被永久锁死在途态。
     */
    private boolean hasRunningArchiveFlow(Long archiveId) {
        try {
            return wfEngineFacade.hasActiveTask("FM_ARCHIVE", archiveId);
        } catch (Exception ex) {
            log.warn("档案 {} 在途审批任务查询失败，按无在办放行: {}", archiveId, ex.getMessage());
            return false;
        }
    }

    /**
     * R5-D-197（内控红线，销毁不可逆）：原 {@code destroy(Long archiveId)} 是「一键销毁」旁路——直置
     * DISPOSED，无到期校验、不查销毁审批、不起 FM_DESTROY 流程（实测 id=25 destroy_date=2030-01-01
     * 未届满却已 DISPOSED，同刻销毁审批/流程三表零行）。本方法已<b>整体删除</b>（删掉而非留抛错桩，
     * 任何残留调用点将在编译期即暴露）；所有人工销毁入口一律转 {@link ArchiveDestroyService#createDestroyRequest}
     * 发起「销毁申请」，交由合规链定论（assertAllExpired 到期校验＋双人复核双签＋FM_DESTROY 起流程），
     * DISPOSED 的唯一落地点是 {@link ArchiveDestroyService#executeDestroy}（审批闭环后执行）。
     */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        archiveCatalogMapper.deleteById(archiveId);
        log.info("删除档案: archiveId={}", archiveId);
    }

    public OpArchive getById(Long id) {
        OpArchive archive = archiveCatalogMapper.selectById(id);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + id);
        }
        orgGuard.assertVisible(archive.getCompanyId(), archive.getFilingUserId());
        fillArchiveFlowKey(List.of(archive));   // D-DEC-028
        return archive;
    }

    /**
     * D-DEC-028（证据 12-09A:57）：给档案行补上<b>归档审批流自身</b>的实例号与状态。
     *
     * <p><b>为什么不改 {@code op_archive.proc_inst_id} 的取值</b>：那一列按 D-TRC-05 存的是
     * <b>被归档那张单</b>的审批实例，「单据全链路 → 档案」段（{@code BillTraceChainController}
     * 按 proc_inst_id 回溯）与 {@code ArchiveFacadeImpl.findByProcInstIds} 都靠它；
     * 就地翻成归档实例＝正向联查当场断（D-DEC-240 那批补丁也会失效）。表上又只有这一列实例号，
     * 加列属 DDL 裁决（注释态 SQL 已写在 {@code OpArchive} 类注释里，未执行）。
     * 故取 {@code OpPayBatch.payProcInstId}（D-DEC-114①）同法：<b>读侧按 biz_type＋biz_id 富化</b>，
     * 归档流自己的实例号从此可查、可点、可反查审批链，而既有正向链路一字未动。
     *
     * <p>一次批量查询（不按行循环打库），同一档案多轮归档实例时取 id 最大的一条＝最新一轮，
     * 与 {@code PayBatchService.fillDisplayFields} 同口径。富化失败只记日志——档案列表不能因为
     * 一个附加列取不到而打不开。
     */
    private void fillArchiveFlowKey(List<OpArchive> archives) {
        if (archives == null || archives.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (OpArchive a : archives) {
            if (a != null && a.getId() != null) {
                ids.add(a.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<WfInstance> flows = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getBizType, "FM_ARCHIVE")
                    .in(WfInstance::getBizId, ids)
                    .orderByAsc(WfInstance::getId));
            Map<Long, WfInstance> latestByArchiveId = new HashMap<>();
            for (WfInstance w : flows) {
                if (w.getBizId() != null) {
                    latestByArchiveId.put(w.getBizId(), w);   // 升序覆盖 ⇒ 留下的是最新一条
                }
            }
            for (OpArchive a : archives) {
                if (a == null || a.getId() == null) {
                    continue;
                }
                WfInstance w = latestByArchiveId.get(a.getId());
                if (w != null) {
                    a.setArchiveProcInstId(w.getProcInstId());
                    a.setArchiveFlowStatus(w.getStatus());
                }
            }
        } catch (Exception ex) {
            log.warn("档案行富化归档审批流实例号失败（不影响档案取数）: {}", ex.getMessage());
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * D-DEC-185（12-31:163）后半：FM_ARCHIVE 的流程标题（真名优先，不拿 biz_id 凑）。
     * 标题落在 {@code wf_instance.title}，是待办列表／流程监控上唯一的人眼标识，
     * 改前是 {@code "凭证归档-" + archiveCode或档案ID}，而 archive_code 又大面积 NULL ⇒ 实际输出「凭证归档-24」。
     */
    private String archiveFlowTitle(OpArchive archive) {
        String name = firstNonBlank(archive.getTitle(),
                firstNonBlank(archive.getRefNo(), firstNonBlank(archive.getArchiveNo(), archive.getArchiveCode())));
        if (name == null) {
            log.warn("档案 {} 无标题／关联单号／编号可用作流程标题，FM_ARCHIVE 标题退回 ID 形态（D-DEC-185 待回补真名）",
                    archive.getId());
            return "凭证归档-档案#" + archive.getId();
        }
        return "凭证归档-" + name;
    }

    /**
     * D-DEC-185（读侧挂点对外入口）：把「归档审批流自身」的实例号与状态富化进档案行。
     * 供档案域之外的档案列表端点（如共享作业 {@code SharingEnhanceService.archivePage}，
     * 即「档案归档」页的已归档档案 Tab）复用，避免各端点各自决定要不要挂 028 的富化。
     * <b>只读 wf_instance，不写任何流程表，也不翻转 {@code proc_inst_id} 的源单据语义。</b>
     */
    public void enrichFlowKey(List<OpArchive> archives) {
        fillArchiveFlowKey(archives);
    }

    /*
     * D-DEC-276a 的源单据回写（archive_status=ARCHIVED + archive_time + archive_by）
     * 原为本类 private markSourceBillArchived()，由"执行归档"前置调用。
     * D-DEC-027／D-DEC-181 修后该前置写已摘除——回写改在审批闭环
     * ArchiveFacadeImpl.handleArchiveApproval 单点执行，类型白名单与目标解析
     * 上移为本类的 isBillScopedType()／resolveSourceBillId()，
     * 两条链路共用同一把尺（D-DEC-095 的成因正是"管控"与"回写"各写一套谓词而互为反向）。
     */

    public PageResult<OpArchive> page(long pageNum, long pageSize, String archiveType,
                                            Integer archiveYear, String archiveStatus) {
        return page(pageNum, pageSize, archiveType, archiveYear, archiveStatus, null);
    }

    /**
     * D-DEC-256（核实册:325，与 D-DEC-254 同根）：档案列表端点<b>支持按关键词取档案</b>。
     *
     * <p>改前本方法只认类型/年度/状态三个筛选，检索框（档案选择器／档案板列表）输入<b>真实档案号</b>
     * 时前端只能在"已经取回的第一页"里本地过滤——第一页取不满的部分永远搜不到，
     * 于是"输入真实档案号返 0 条"。现把关键词下推到服务端，检索臂与 {@link #search} <b>共用同一把尺</b>
     * （{@link #applyKeywordArms}，D-DEC-184 定下的五列面），不新增也不删减任何一列。
     *
     * <p><b>与 {@link #search} 的唯一差别（也正是本方法的用途所在）</b>：{@code search} 是
     * "档案检索"口径，硬性只出 {@code ARCHIVED}（184 检索面现状，不动）；本方法服务列表/选择器，
     * 状态按入参决定（传空＝不限）⇒ 待归档（PENDING）、归档审批中（ARCHIVING）的卷号同样可被检索到，
     * 否则档案板上刚立的卷在选择器里输入自己的编号反而"不存在"。
     *
     * @param keyword 档案编号／档案编码／关联单号／标题／载体清单，可空
     */
    public PageResult<OpArchive> page(long pageNum, long pageSize, String archiveType,
                                            Integer archiveYear, String archiveStatus, String keyword) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            applyKeywordArms(qw, keyword);
        }
        if (archiveType != null && !archiveType.isEmpty()) {
            qw.eq(OpArchive::getArchiveType, archiveType);
        }
        if (archiveYear != null) {
            qw.eq(OpArchive::getArchiveYear, archiveYear);
        }
        if (archiveStatus != null && !archiveStatus.isEmpty()) {
            qw.eq(OpArchive::getArchiveStatus, archiveStatus);
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(OpArchive::getCompanyId, scopeC);
        }
        qw.orderByDesc(OpArchive::getCreateTime);

        long total = archiveCatalogMapper.selectCount(qw);
        qw.last("LIMIT " + (pageNum - 1) * pageSize + "," + pageSize);
        List<OpArchive> list = archiveCatalogMapper.selectList(qw);
        fillArchiveFlowKey(list);   // D-DEC-028：档案列表行带出归档审批流自身实例号
        return PageResult.of(list, total, pageNum, pageSize);
    }

    /**
     * 档案完整性校验（#20 防篡改）。
     *
     * <p>原实现仅回读 integrity_status 字段，等于"自己说自己没被改"，无实际校验能力。
     * 现改为重新计算哈希并与归档时固化的快照比对，返回是否一致，
     * 可发现归档文件被替换或关键元数据被篡改。
     *
     * <p>R5-D-199（读端点写库）：本方法由 GET {@code /{id}/verify} 进入，是<b>只读语义</b>——
     * 改前却在此 {@code updateById} 落 {@code integrity_status} 与 {@code update_time}
     * （反复点「校验」即反复写状态、审计时间列被污染，且 GET 不幂等）。
     * 现有意<b>只算不写</b>：只重算比对并返回布尔，方法体内不含任何写库调用；
     * 状态落库与校验人留痕一律走显式 POST {@code /{id}/verify}（{@link #verifyIntegrityFull}）。
     *
     * <p>D-DEC-180：重算一律走 {@link ArchiveIntegrityService#snapshot}——与写入侧<b>同一套算式</b>，
     * 不再出现"写入用甲式、校验用乙式"的必然误报。
     * 历史行（旧算式产物，含被写成裸文件 MD5 的）在新算式下会判 MISMATCH，属预期，
     * 待发布窗口一次性重算回灌（数据轨，本轮不执行）。
     */
    public boolean verifyIntegrity(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        if (archive.getIntegrityHash() == null) {
            log.warn("档案 {} 未固化完整性哈希，无法校验（历史档案，建议重新归档）", archiveId);
            return false;
        }
        boolean ok = archive.getIntegrityHash().equals(archiveIntegrityService.snapshot(archive));
        log.info("档案完整性校验（只算不写） id={} 结果={}", archiveId, ok ? "VERIFIED" : "MISMATCH");
        return ok;
    }

    /**
     * W1 判据（D-DEC-312）：归档定稿前的逐项检查，返回**未通过清单**（空＝全通过）。
     * <p>零新阈值——只用归档时已固化的完整性哈希与其重算值；原 {@link #verifyIntegrity} 只回布尔、
     * 结果"已算不判"，撤掉 FM_ARCHIVE Task_3 人工复核后由本方法把判据接进定稿分支。
     */
    public java.util.List<String> preArchiveFailures(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            return java.util.List.of("档案不存在: " + archiveId);
        }
        java.util.List<String> failures = new java.util.ArrayList<>();
        if (archive.getIntegrityHash() == null) {
            failures.add("未固化完整性哈希，归档是否被改动无法校验（历史档案，需重新归档）");
        } else if (!archive.getIntegrityHash().equals(archiveIntegrityService.snapshot(archive))) {
            failures.add("完整性哈希与归档快照不一致（归档文件或关键元数据已被改动）");
        }
        return failures;
    }

    public List<OpArchive> listExpiringSoon(int days) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        qw.eq(OpArchive::getArchiveStatus, "ARCHIVED")
                .isNotNull(OpArchive::getDestroyDate)
                .le(OpArchive::getDestroyDate, threshold)
                .ge(OpArchive::getDestroyDate, LocalDate.now())
                .orderByAsc(OpArchive::getDestroyDate);
        List<OpArchive> list = archiveCatalogMapper.selectList(qw);
        // D-DEC-185（读侧挂点补齐）：到期预警清单也是档案列表端点，同样带出归档审批流实例号，
        // 否则只有 /page 与 /list 能反查归档链、这一份不能（028 段的富化挂点漏了一处）。
        fillArchiveFlowKey(list);
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long autoArchive(Long billId, String archiveType, Integer archiveYear,
                             String archivePeriod) {
        // 与 createArchive 同一把尺（RA-D255a）：自动归档也不得写入词表外的类型
        archiveType = assertArchiveType(archiveType);
        // D-DEC-048（12-16:60）：本入口改前<b>零判重</b>——同一单据每点一次「自动归档」就多落一卷，
        // batchArchive 逐单循环又是按同一入口连发，"无限次重复立卷"的字面成因就在这一条上。
        // 与人工立卷共用 assertNoLiveVolumeForSource（同一把尺，不再各写一套谓词——D-DEC-095 的成因）。
        // 批量入口 batchArchive 逐单 catch 异常并计数，个别单据已立卷不会拖垮整批。
        assertNoLiveVolumeForSource(billId, archiveType);
        // D-DEC-005：与 createArchive 同一把尺——非单据载体不落 bill_id、不反查 exp_bill
        boolean billCarrier = isBillScopedType(archiveType);
        OpArchive archive = new OpArchive();
        // D-DEC-184（12-31:162）＋ D-DEC-180：档案编号取号（租户内唯一），档案编码＝其冗余镜像。
        // 改前此处是 generateArchiveCode()＝"ARC-前缀-年度-" + System.currentTimeMillis() 的后 5 位，
        // ① 100 秒窗口内必重码（batchArchive 逐单循环就在同一秒里连落多卷），
        // ② 老卷的 archive_code 大面积 NULL ⇒ 归档目录"档案编码"要素整列空（本条证据），
        // ③ 重码直接进完整性哈希输入 ⇒ 不同两卷同哈希（D-DEC-180 的漏报半边）。
        // 现按 DDL 列注释「archive_code＝档案编码(冗余)」的既有语义做成编号镜像，唯一性由 uk_tenant_archive_no 兜底。
        archive.setArchiveNo(nextArchiveNo(archiveType));
        archive.setArchiveCode(archive.getArchiveNo());
        archive.setBillId(billCarrier ? billId : null);
        // ref_id 为 NOT NULL 无默认值：与 createArchive 口径一致，来源单据即 ref
        archive.setRefId(billId);
        archive.setArchiveType(archiveType);
        archive.setArchiveYear(archiveYear);
        archive.setArchivePeriod(archivePeriod);
        archive.setRetentionYears(getDefaultRetentionPeriod(archiveType));
        archive.setArchiveStatus("ARCHIVING");
        // D-DEC-182：status 是保管处置轴的冗余镜像列，取值一律由真相源 archive_status 映射（见 dispositionOf）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        archive.setIntegrityStatus("UNKNOWN");
        // D-DEC-021 第二半（上架登记字段回填）：本路径此前与 createArchive 不同——立卷人／立卷时间两列从不写，
        // 实测该类型行的 filing_user_id/filing_time 全空（D-DEC-276a 只覆盖了 createArchive 一条入口）。
        // 同口径补齐：有登录上下文即落真人，无上下文保持 NULL，不造假人。
        archive.setFilingUserId(userId());
        archive.setFilingTime(LocalDateTime.now());
        // D-TRC-05：同 createArchive 口径，按 exp_bill 主键反查来源审批流程实例，查不到留 NULL
        if (billCarrier) {
            com.fssc.framework.facade.dto.BillArchiveDTO src = billFacade.findBillForArchive(billId);
            if (src != null) {
                archive.setProcInstId(src.getProcInstId());
            }
            // D-DEC-184：目录要素补齐（关联单号／档案标题／会计年度月份／所属公司），
            // 与 createArchive 共用 fillCatalogFromSourceBill，两条立卷入口不再一份填半套。
            fillCatalogFromSourceBill(archive, src);
        } else {
            // D-DEC-005：非单据载体经本域解析器取要素（类型不在 exp_bill 语义内，一律不反查单据）
            fillCatalogFromCarrier(archive, resolveCarrier(archiveType, billId));
            fillPeriodFromArchivePeriod(archive);
        }
        // 与人工立卷共用一把尺（destroyDateOf）。改前此处是 now()+保管年限，与年度式对同一卷可差一年以上；
        // 落在目录要素补齐<b>之后</b>，因为 archive_year/period_year 到这一步才取得到来源真值。
        archive.setDestroyDate(destroyDateOf(archive.getArchiveYear(), archive.getPeriodYear(),
                archive.getRetentionYears()));
        // V1.4.92（AMC-05）：同人工立卷口径，按公司模式补「原件保管单位」
        applyPaperKeeperDefault(archive);
        archiveCatalogMapper.insert(archive);

        // D-DEC-180：主键入库之后才算快照（id 是可区分要素），随后回写 integrity_hash
        archive.setIntegrityHash(archiveIntegrityService.snapshot(archive));
        archive.setIntegrityStatus("VERIFIED");
        archive.setArchiveStatus("ARCHIVED");
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));   // D-DEC-182 同源镜像
        archive.setArchivedAt(LocalDateTime.now());
        archiveCatalogMapper.updateById(archive);

        log.info("自动归档完成: archiveId={}, billId={}, archiveType={}",
                archive.getId(), billId, archiveType);
        return archive.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchArchive(List<Long> billIds, String archiveType, Integer archiveYear,
                              String archivePeriod) {
        int successCount = 0;
        int failCount = 0;

        for (Long billId : billIds) {
            try {
                autoArchive(billId, archiveType, archiveYear, archivePeriod);
                successCount++;
            } catch (Exception e) {
                log.error("归档失败: billId={}", billId, e);
                failCount++;
            }
        }

        log.info("批量归档完成: success={}, fail={}", successCount, failCount);
    }

    /**
     * 完整性校验（D-DEC-180 修后与 {@link #verifyIntegrity} 同一套算式）。
     *
     * <p>改前本方法走的是 {@code calculateIntegrityHash(id)}＝档案编码+类型+单据+凭证+附件，
     * 而写入侧（执行归档）走的是另一套（档案号|ref|类型|文件），两式永不相等 ⇒ 点「校验」必报不一致，
     * 也就是 12-31:158 记的「同单异值＝误报」。现两式合一，本方法只是入参形态不同的另一个入口。
     *
     * <p>R5-D-199：本方法是<b>显式写端点</b>——由 POST {@code /{id}/verify} 进入，负责把校验结论
     * 持久化到 {@code integrity_status}（{@code update_time} 由框架 MetaObjectHandler 自动填，不手写）；
     * 校验人（操作人）由控制器侧 {@code @OperLog} 落审计留痕，本处日志同样带上校验人。
     *
     * <p>R5-D-208：三态结论随回执返回（status/verified/message）——历史档案未固化哈希属
     * 「无法校验」（UNVERIFIABLE），改前被写成 MISMATCH（"疑似篡改"），把"没保住快照"误报成"被动了"。
     * 现该情形落 UNVERIFIABLE 独立状态，提示重新归档，不再冒充篡改结论。
     */
    public java.util.Map<String, Object> verifyIntegrityFull(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }

        String status;
        boolean verified;
        String message;
        if (archive.getIntegrityHash() == null) {
            // R5-D-208：无法校验（未固化哈希）——不写 MISMATCH，不冒充篡改结论
            status = "UNVERIFIABLE";
            verified = false;
            message = "无法校验：该档案未固化完整性哈希（历史档案），本次不产生篡改结论；建议重新归档后再校验";
        } else {
            String currentHash = archiveIntegrityService.snapshot(archive);
            verified = archive.getIntegrityHash().equals(currentHash);
            status = verified ? "VERIFIED" : "MISMATCH";
            message = verified ? "完整性校验通过" : "完整性校验不一致：归档文件或关键元数据已被改动";
        }
        archive.setIntegrityStatus(status);
        archiveCatalogMapper.updateById(archive);

        log.info("完整性校验（持久化）: archiveId={}, status={}, 校验人={}", archiveId, status, userId());
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("status", status);
        out.put("verified", verified);
        out.put("message", message);
        return out;
    }

    public OpArchive getByBillId(Long billId) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        // D-DEC-005：bill_id 只在单据语义的类型集内才是单据主键——合同/供应商等非单据载体不得冒充某张单据的档案
        qw.eq(OpArchive::getBillId, billId)
                .in(OpArchive::getArchiveType, BILL_SCOPED_ARCHIVE_TYPES)
                .orderByDesc(OpArchive::getCreateTime)
                .last("limit 1");
        OpArchive archive = archiveCatalogMapper.selectOne(qw);
        if (archive != null) {
            fillArchiveFlowKey(List.of(archive));   // D-DEC-185：按单据取档案也是读侧挂点
        }
        return archive;
    }

    /**
     * D-DEC-184（12-31:162）／D-DEC-256（核实册:325）：关键词检索臂的<b>唯一口径</b>——
     * 档案编码、<b>档案编号（真实卷号，256 的那一臂）</b>、关联单号、档案标题、载体清单五列 LIKE。
     *
     * <p>改前只 LIKE {@code archive_code}/{@code related_ids} 两列，而 archive_code 大面积 NULL、
     * 真正能核对的"关联单号/标题/卷号"根本不在检索面里（输入框提示就是"搜索档案编码或类型"，
     * 用户拿单号或卷号搜必然 0 行）。列表端点（{@link #page} 带 keyword）与检索端点
     * （{@link #search}）共用本方法，两个入口不再一份填半套。
     */
    private static void applyKeywordArms(LambdaQueryWrapper<OpArchive> qw, String keyword) {
        String kw = keyword.trim();
        qw.and(w -> w.like(OpArchive::getArchiveCode, kw)
                .or().like(OpArchive::getArchiveNo, kw)
                .or().like(OpArchive::getRefNo, kw)
                .or().like(OpArchive::getTitle, kw)
                .or().like(OpArchive::getRelatedIds, kw));
    }

    public List<OpArchive> search(String keyword, String archiveType, Integer archiveYear) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // D-DEC-184／D-DEC-256：检索面已含 archive_no（真实档案号）精确 LIKE 臂，
            // "选择器/检索框输入真实档案号返 0 条"的读侧断点在此收口（口径见 applyKeywordArms）。
            applyKeywordArms(qw, keyword);
        }
        if (archiveType != null && !archiveType.isBlank()) {
            qw.eq(OpArchive::getArchiveType, archiveType);
        }
        if (archiveYear != null) {
            qw.eq(OpArchive::getArchiveYear, archiveYear);
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(OpArchive::getCompanyId, scopeC);
        }
        qw.eq(OpArchive::getArchiveStatus, "ARCHIVED")
                .orderByDesc(OpArchive::getCreateTime);
        List<OpArchive> hits = archiveCatalogMapper.selectList(qw);
        fillArchiveFlowKey(hits);   // D-DEC-028
        return hits;
    }

    public Map<String, Object> getArchiveStats(Integer year) {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<OpArchive> baseQw = new LambdaQueryWrapper<>();
        if (year != null) {
            baseQw.eq(OpArchive::getArchiveYear, year);
        }

        long totalCount = archiveCatalogMapper.selectCount(baseQw);
        stats.put("totalCount", totalCount);

        LambdaQueryWrapper<OpArchive> archivedQw = new LambdaQueryWrapper<>();
        if (year != null) {
            archivedQw.eq(OpArchive::getArchiveYear, year);
        }
        archivedQw.eq(OpArchive::getArchiveStatus, "ARCHIVED");
        stats.put("archivedCount", archiveCatalogMapper.selectCount(archivedQw));

        LambdaQueryWrapper<OpArchive> pendingQw = new LambdaQueryWrapper<>();
        if (year != null) {
            pendingQw.eq(OpArchive::getArchiveYear, year);
        }
        pendingQw.eq(OpArchive::getArchiveStatus, "PENDING");
        stats.put("pendingCount", archiveCatalogMapper.selectCount(pendingQw));

        return stats;
    }

    /**
     * D-DEC-182（12-31:160）：<b>两列二源</b>的唯一映射——{@code archive_status}（归档流程轴，真相源）
     * → {@code status}（保管处置轴，冗余镜像）。
     *
     * <p>库里两列的既有词表（{@code fssc_op_archive.sql} 列注释，本轮不动 DDL）：
     * <ul>
     *   <li>{@code archive_status}：PENDING/ARCHIVING/ARCHIVED/DISPOSED，默认 PENDING ——
     *           <b>参与业务判定的那一列</b>：档案列表筛选（{@link #page}／{@link #search}）、
     *           统计（{@link #getArchiveStats}）、到期预警（{@code ArchiveExpireJob:73}）、
     *           单据全链路联查（{@code BillTraceChainController:819}）、执行归档状态机硬闸（{@link #archive}）
     *           全部按它判 ⇒ 定为真相源。</li>
     *   <li>{@code status}：FILED已归档/EXPIRED到期/DESTROYED已销毁，NOT NULL DEFAULT 'FILED' ——
     *           案卷在架/到期/已销毁的<b>保管</b>语义，降级为镜像，取值一律由本方法派生。</li>
     * </ul>
     *
     * <p><b>缺陷是怎么形成的</b>：{@code BillArchiveService.doArchive} 只写 {@code status='ARCHIVED'}
     * 而从不写 {@code archive_status}（于是库里落到默认 PENDING）⇒ 实测 id=21/22/23
     * 「archive_status=PENDING 而 status=ARCHIVED」互斥；而「ARCHIVED」<b>根本不在 status 的词表里</b>
     * （它写的是另一轴的取值）⇒ 页面无从判断哪列是结论。修法＝doArchive 补写真相源 ARCHIVED
     * （与它同事务里 {@code billFacade.markArchived} 的单据终态同口径），并按本映射写镜像。
     *
     * <p>不删列、不改列注释（DDL 需另裁）；历史行的旧值不回写（演练数据保留铁律），
     * 存量互斥行只登记不清洗。
     *
     * @param archiveStatus 真相源取值
     * @return 镜像列取值；入参为 null 时返回 null＝交由库默认 {@code FILED} 兜底
     */
    public static String dispositionOf(String archiveStatus) {
        if (archiveStatus == null || archiveStatus.isBlank()) {
            return null;
        }
        return switch (archiveStatus.trim().toUpperCase()) {
            case "DISPOSED" -> "DESTROYED";
            // PENDING/ARCHIVING/ARCHIVED 三态案卷都"已登记在架"，保管轴统一 FILED；
            // EXPIRED 保留给"到期未销毁"（现无写入方，ArchiveExpireJob 只推站内信不改列），本映射不造该值。
            default -> "FILED";
        };
    }

    /**
     * 到期销毁日期的<b>唯一算式</b>（三条立卷写入点共用一把尺）。
     * 《会计档案管理办法》：保管期限自会计年度终了后的第一天起算 ⇒ {@code (年度 + 保管年限 + 1) 年 1 月 1 日}。
     * 改前人工立卷走年度式、自动归档走 {@code now()+保管年限}，两式对同一卷可差一年以上；
     * 单据归档链路则从不设，致销毁申请被「未设置到期销毁日期，禁止销毁」整批拦死。
     *
     * @param archiveYear    档案年度，取不到退 {@code periodYear}，再取不到退立卷当年
     * @param periodYear     会计年度（目录要素由 {@code fillCatalogFromSourceBill} 补齐，可能晚于本调用落值）
     * @param retentionYears 保管年限，非正数时退默认 10 年
     */
    public static LocalDate destroyDateOf(Integer archiveYear, Integer periodYear, Integer retentionYears) {
        int year = archiveYear != null ? archiveYear
                : (periodYear != null ? periodYear : LocalDate.now().getYear());
        int retention = retentionYears != null && retentionYears > 0 ? retentionYears : 10;
        return LocalDate.of(year + retention + 1, 1, 1);
    }

    /**
     * D-DEC-184（12-31:162）：立卷入口的<b>目录要素补齐</b>——两条建档路径（{@link #createArchive}
     * 人工立卷／{@link #autoArchive} 自动归档）共用，改前两处都只写 archive_no/type/year/period，
     * 于是 {@code ref_no}／{@code title}／{@code period_year}／{@code period_month}／{@code company_id}
     * 整列空（实测 id=24 除 archive_period='2026-12' 外目录要素全 NULL）。
     *
     * <p>取值一律来自<b>来源单据真值</b>（{@code BillArchiveDTO}），取不到即保持 NULL、不造数：
     * <ul>
     *   <li>{@code ref_no}＝单据编号（bill_no），是「档案 ↔ 单据」在人眼层面的联查键；</li>
     *   <li>{@code title}＝单据标题，缺标题退单据编号；
     *           <b>D-DEC-185</b>：绝不拿 {@code biz_id}/{@code 档案ID} 拼名（改前起流标题就是「凭证归档-24」这种猜不透的串）；</li>
     *   <li>{@code period_year}/{@code period_month}＝单据会计期间；单据取不到时按
     *           {@code archive_period}（{@code yyyy-MM}，入口已校验格式）折算，两列从此不可能互相打脸；</li>
     *   <li>{@code archive_year}/{@code archive_period} 缺省同样按单据会计期间回填；</li>
     *   <li>{@code company_id} 缺省取单据归属公司（登录人公司已由 createArchive 先行落值，不覆盖）。</li>
     * </ul>
     *
     * <p> 明确<b>不补</b>的一列：{@code file_id}——本域立卷动作没有文件载体（影像迁移在
     * {@code BillArchiveService.doArchive} 那条链路），无载体不造引用；该列恒 NULL 的根因是
     * D-DEC-183「齐套类节点零闸口」（待裁：Task_1 是否强制要求 file_id 非空才可定稿），
     * 属产品裁决＋闸口接线，不在本条范围内。
     */
    private void fillCatalogFromSourceBill(OpArchive archive, com.fssc.framework.facade.dto.BillArchiveDTO src) {
        if (archive == null) {
            return;
        }
        if (src != null) {
            if (isBlank(archive.getRefNo())) {
                archive.setRefNo(src.getBillNo());
            }
            // D-DEC-185：标题取单据真名，真名缺位退单据编号——两级都不再有"以 ID 拼名"的口子
            if (isBlank(archive.getTitle())) {
                archive.setTitle(firstNonBlank(src.getTitle(), src.getBillNo()));
            }
            if (archive.getPeriodYear() == null) {
                archive.setPeriodYear(src.getPeriodYear());
            }
            if (archive.getPeriodMonth() == null) {
                archive.setPeriodMonth(src.getPeriodMonth());
            }
            if (archive.getArchiveYear() == null) {
                archive.setArchiveYear(src.getPeriodYear());
            }
            if (isBlank(archive.getArchivePeriod()) && src.getPeriodYear() != null && src.getPeriodMonth() != null) {
                archive.setArchivePeriod(String.format("%04d-%02d", src.getPeriodYear(), src.getPeriodMonth()));
            }
            if (archive.getCompanyId() == null) {
                archive.setCompanyId(src.getCompanyId());
            }
        }
        // 无单据（非费用域立卷／单号输错）时，会计年度月份仍可从档案期间折出来，
        // 消掉「archive_period 有值而 period_year/month 全空」这组同页二源
        fillPeriodFromArchivePeriod(archive);
    }

    /**
     * V1.4.92（AMC-05）：非集中档案模式下纸质原件留原单位 ⇒ 原件保管单位默认落档案归属公司；
     * 集中模式原件已寄送共享中心，两列保持 NULL（不写"共享中心"占位，保管地点属人工登记口径）。
     * 只补空、不覆盖人工已填值；两条人工/自动立卷入口与影像归档链路共用。
     */
    void applyPaperKeeperDefault(OpArchive archive) {
        if (archive == null || archive.getCompanyId() == null || archive.getPaperKeeperCompanyId() != null) {
            return;
        }
        if (deliveryService.isNonCentralArchive(archive.getCompanyId())) {
            archive.setPaperKeeperCompanyId(archive.getCompanyId());
        }
    }

    /** D-DEC-005：单据/载体两条立卷路径共用的期间回落（archive_period yyyy-MM → period_year/period_month） */
    private void fillPeriodFromArchivePeriod(OpArchive archive) {
        if ((archive.getPeriodYear() == null || archive.getPeriodMonth() == null)
                && archive.getArchivePeriod() != null) {
            String[] ym = archive.getArchivePeriod().trim().split("[-/]");
            if (ym.length == 2 && ym[0].matches("\\d{4}") && ym[1].matches("\\d{1,2}")) {
                if (archive.getPeriodYear() == null) {
                    archive.setPeriodYear(Integer.valueOf(ym[0]));
                }
                if (archive.getPeriodMonth() == null) {
                    archive.setPeriodMonth(Integer.valueOf(ym[1]));
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? (isBlank(b) ? null : b) : a;
    }

    /** D-DEC-048：按优先序取第一个非空值（全空返回 null），供卷号/标题这类"多级回落"取用 */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private String nextArchiveNo(String archiveType) {
        try {
            return numberRuleService.next("ARCHIVE");
        } catch (Exception e) {
            return archiveType + "-" + System.currentTimeMillis();
        }
    }

    private int getDefaultRetentionPeriod(String archiveType) {
        return switch (archiveType != null ? archiveType : "EXPENSE") {
            case "CONTRACT" -> 30;
            case "PAYMENT" -> 10;
            default -> 10;
        };
    }
}