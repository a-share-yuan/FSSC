// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.document.mapper.OpArchiveItemMapper;
import com.fssc.framework.facade.BillFacade;
import com.fssc.framework.facade.dto.BillArchiveDTO;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.entity.OpArchiveItem;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.sharing.service.DeliveryService;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.framework.datasource.FsscDataSource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
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
@FsscDataSource("document")
public class ArchiveService {

    private final OpArchiveMapper archiveCatalogMapper;
    /**
     * 卷内件清单的唯一写入点。归档整理的层级是「案卷 → 件 → 页」，
     * "件"才是最小整理单位（DA/T 22—2015），本表把这一层补出来；{@code op_archive} 的
     * {@code bill_id}/{@code ref_no}/{@code related_ids} 降级为<b>首件镜像</b>（不要删列）。
     * 写入集中在本类，{@code ArchiveVolumeService} 只做编排，不要两处各写一套件规则。
     */
    private final OpArchiveItemMapper archiveItemMapper;
    /**
     * 完整性哈希的<b>唯一算式</b>在 {@link ArchiveIntegrityService}。
     * 本类与 {@code BillArchiveService.doArchive} 都不许自己再算一遍：
     * 档案号|ref|类型|文件、档案编码|类型|单据|凭证|附件、主文件裸 MD5，三套算式并存
     * 正是「同单异值＝误报、异卷同值＝漏报」的根因。
     */
    private final ArchiveIntegrityService archiveIntegrityService;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    private final NumberRuleService numberRuleService;
    private final BillFacade billFacade;
    /**
     * 读档案时把「归档审批流自身」的实例号补充出来（只读 wf_instance，不写任何流程表）。
     * 用视图而非引擎门面——档案页只需要"这条卷是被哪次审批定论的"，不需要驱动能力。
     */
    private final WfInstanceMapper wfInstanceMapper;

    /**
     * 非单据载体的「归档对象类型」解析器（按域各自提供 bean，无实现则不解析）。
     * 存在性校验与目录要素（关联单号/标题/所属公司）都由它给，登记簿因此不需要任何业务表加列。
     */
    private final List<ArchiveCarrierResolver> carrierResolvers;

    /**
     * 档案非集中管理判定复用 {@code DeliveryService.isNonCentralArchive}
     * 模式的唯一读点，此处只为给「原件保管单位」落默认值，不另读 {@code sys_company.archive_mode}。
     */
    private final DeliveryService deliveryService;

    /**
     * 档案类型权威名称表（<b>闭集枚举</b>）：
     * 前 8 项与前端 {@code ArchiveBoard.vue} 的 {@code archiveTypeLabel} 同一套；
     * {@code BANK_SLIP}/{@code SUPPLIER} 两类是「非单据载体」登记用，前端标签暂缺，
     * 列表会回落显示原码。
     *
     * <p><b>为什么不走可配置档案类型</b>：档案域从没接过字典表，库里
     * {@code sys_dict_type}／{@code sys_dict_data} 里只有费用类型，所谓「集团维护的可配置档案」
     * 在本仓只有骨架没有实现。走它既扩建工程又要写库，不如把这套闭集写死在代码里。
     *
     * <p>已知不一致（<b>只登记、不改 DDL</b>）：{@code op_archive.archive_type}
     * 的列注释只写 5 项（BILL/VOUCHER/CONTRACT/REPORT/OTHER），库里却在用 EXPENSE——
     * 列注释不是权威名称表，以本集合为准。历史行一律不回写，本白名单只挡新增。
     */
    static final Set<String> ARCHIVE_TYPES = Set.of(
            "BILL", "VOUCHER", "EXPENSE", "PAYMENT", "CONTRACT", "INVOICE", "REPORT", "OTHER",
            // 非单据载体靠类型进登记簿（业务表零加列）——BANK_SLIP 是银行回单登记已在写的取值，
            // SUPPLIER 是供应商载体；BILL/EXPENSE/PAYMENT 的既有语义与回写白名单一字未动。
            "BANK_SLIP", "SUPPLIER");

    /** 名称表守卫：不在集内的取值直接拒（不静默清洗成默认值——清洗会把错值变成"看不见的错值"）。三条立卷入口＋回单登记入口共用 */
    public static String assertArchiveType(String archiveType) {
        String v = archiveType == null ? null : archiveType.trim().toUpperCase();
        if (v == null || !ARCHIVE_TYPES.contains(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案类型取值不在词表内: " + archiveType + "，可选 " + new TreeSet<>(ARCHIVE_TYPES));
        }
        return v;
    }

    /**
     * 载体类型归一。空/未知一律落 {@code PAPER}（严格侧）——
     * 认不出载体时按纸质要求编页，而不是默认"电子"把闸门放过；自由文本一律 400，
     * 不要让前端随手传个"纸"、"电子档案"就落成第三种值。
     */
    private String normalizeCarrier(String carrierType) {
        if (carrierType == null || carrierType.isBlank()) {
            return "PAPER";
        }
        String v = carrierType.trim().toUpperCase();
        if (!"PAPER".equals(v) && !"ELECTRONIC".equals(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "载体类型只能是 PAPER（纸质）或 ELECTRONIC（电子），收到 " + carrierType);
        }
        return v;
    }

    /**
     * <b>回写白名单的唯一权威</b>——
     * 名称表里「{@code bill_id}/{@code ref_id} 语义＝{@code exp_bill} 主键」的那一类。
     *
     * <p>逐类判据（按各建卡入口的实际写入路径判，不按类型名的字面意思猜）：
     * <ul>
     *   <li>{@code BILL}＝{@code BillArchiveService.doArchive/eArchiveBill} 写入，{@code ref_id}＝单据主键 ✔</li>
     *   <li>{@code EXPENSE}＝UI「新建档案目录→报销凭证」默认类型（{@code ArchiveController} 立卷入参），
     *       {@code bill_id}＝{@code ref_id}＝单据主键 ✔</li>
     *   <li>{@code PAYMENT}＝付款/结算类档案。本系统付款单/对公结算单<b>本身就在 {@code exp_bill}</b>
     *       （{@code bill_type} APPLY/CLAIM/LOAN/REPAY/PAY，FK/HK 单号），与 {@code BILL}/{@code EXPENSE} 同类目 ✔</li>
     *   <li>{@code VOUCHER}＝凭证主键、{@code CONTRACT}＝{@code cont_contract} 主键、{@code INVOICE}＝发票主键、
     *       {@code REPORT}＝报表主键——裸主键跨域回写必串号，各域的 id 段完全重叠 ✘</li>
     *   <li>{@code BANK_SLIP}（{@code BankReconcileService:574} 登记，ref_id/bill_id 均为
     *       {@code op_bank_statement.id}）与 {@code OTHER} ✘</li>
     * </ul>
     *
     * <p>白名单只是<b>第一道</b>；第二道是调用方的 {@code billFacade.findBillForArchive(id) != null} 反查，
     * 两道都过才回写。历史行不回写、不清洗。
     */
    public static final Set<String> BILL_SCOPED_ARCHIVE_TYPES =
            Set.of("BILL", "EXPENSE", "PAYMENT");

    /** 档案类型的 ref 是否指向费用单据（回写与收尾两条链路共用，避免各写一套谓词） */
    public static boolean isBillScopedType(String archiveType) {
        return archiveType != null && BILL_SCOPED_ARCHIVE_TYPES.contains(archiveType.trim().toUpperCase());
    }

    /**
     * 立卷前按「归档对象类型」反查载体本身——<b>非单据载体绝不经 exp_bill</b>
     * （合同主键拿去查 {@code exp_bill} 会命中同号的单据＝跨域裸主键串号）。
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

    /** 非单据载体的目录要素补齐——只填登记簿已有的列（关联单号/标题/归属公司），载体表零加列、零回写 */
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
     * 见 {@code ArchiveFacade} L30 注释）；类型不在白名单时返回 {@code null}＝不回写。
     */
    public static Long resolveSourceBillId(OpArchive archive) {
        if (archive == null || !isBillScopedType(archive.getArchiveType())) {
            return null;
        }
        return archive.getBillId() != null ? archive.getBillId() : archive.getRefId();
    }

    /**
     * 判重视为<b>现有/在途</b>的归档状态（真相源 {@code archive_status} 轴，
     * 定列依据见 {@code OpArchive.archiveStatus} 的 注释）。
     *
     * <p><b>DISPOSED 不在集内＝销毁后允许重新立卷</b>：原卷已销毁、载体已下架，同一单据再立一卷
     * 是共享中心的常规补录动作；若产品判定"销毁过的单据永不再立卷"，只需把 DISPOSED 并进本集
     * 单点改动，三条立卷入口共用。
     */
    static final Set<String> LIVE_VOLUME_STATUSES =
            Set.of("PENDING", "ARCHIVING", "ARCHIVED");

    /**
     * 无分页检索端点（{@code /compliance/archive/list}、{@code /compliance/archive/search}）的行数硬上限。
     * 这两个端点没有 pageNum/pageSize，全量 ARCHIVED 在 52 万卷语料上实测单响应 95 MB。
     */
    private static final int SEARCH_MAX_ROWS = 500;

    /**
     * 按<b>来源单据键</b>查出该单据仍存在的现有/在途卷（空列表＝可立卷）。
     *
     * <p><b>键的取法</b>：{@code ref_id} 与 {@code bill_id} 二者命中任一即算同一单据——
     * {@code BillArchiveService.doArchive}（影像归档链路）只写 ref_id，
     * {@code createArchive}/{@code eArchiveBill} 两列都写，只用一列判会漏掉整条影像链路。
     *
     * <p><b>类型维度</b>：{@code BILL}/{@code EXPENSE}/{@code PAYMENT} 三类的 ref 都指 {@code exp_bill}
     * 主键（判据见 {@link #BILL_SCOPED_ARCHIVE_TYPES} 注释）⇒ 必须<b>跨这三类</b>判重：
     * 按 (archive_type, ref_id) 精确判时，把类型从「报销凭证」改成「单据档案」就绕开了校验；
     * 其余类型（VOUCHER/CONTRACT/INVOICE/REPORT/OTHER）的 ref 指各域主键，裸主键跨域必串号，
     * 只能在<b>同 archive_type</b> 内判重。
     *
     * <p><b>租户维度</b>：{@code op_archive} 非忽略表，{@code FsscTenantLineHandler} 自动追加
     * {@code tenant_id} 条件，{@code @TableLogic} 自动过滤 del_flag ⇒ 本查询天然按租户＋未作废行判重，
     * 无需手工条件（手工加会与拦截器各写一套、留下漏网规则）。
     *
     * <p><b>无来源键＝不判重</b>：控制器在「关联单据」留空时兜的是 {@code 0L}（H-04 的解析规则），
     * 这类卷没有来源单据，不存在「同一单据重复立卷」可言。按 {@code ref_id=0} 判重
     * 会把第二张<b>无来源</b>的卷也拦下，那是误伤，不是本校验要的规则。
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
     * 立卷前的重复强制校验——已有现有/在途卷即拒绝，并把<b>原卷号</b>如实报出来。
     * 只说「已立卷 N 次」不够：经办人拿着这条报错既找不到原卷、也做不了后续处置。
     *
     * <p>历史重复行不回写、不删除，本校验只挡新增。
     */
    private void assertNoLiveVolumeForSource(Long sourceId, String archiveType) {
        List<OpArchive> live = findLiveVolumes(sourceId, archiveType);
        if (live.isEmpty()) {
            // 一卷多件后，「这单是否已入卷」的答案也可能只在卷内件清单里：
            // 它是某卷的第 2 件时，卷表 bill_id 存的是首件，只查卷表就会放它进第二卷。
            if (isBillScopedType(archiveType)) {
                assertBillNotInOtherVolume(sourceId, null);
            }
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

    /** 归档状态→人话（与前端 ArchiveBoard.vue archStatusLabel、ArchivePage.vue ARCH_STATUS_LABEL 同一套名称表） */
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
        return createArchive(billId, archiveType, archiveYear, archivePeriod, retentionPeriod, null);
    }

    /**
     * 建档（可指定载体类型）。：{@code carrierType} 只由人工建卷弹窗透传；
     * 自动腿（影像归档／电子立卷）仍走 5 参重载 ⇒ 落 PAPER（严格侧），
     * 是否按腿自动定载体属业务口径，未裁前不猜。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createArchive(Long billId, String archiveType, Integer archiveYear,
                              String archivePeriod, Integer retentionPeriod, String carrierType) {
        // 必填守卫（前端 ARCHIVE_CREATE 登记表同一规则）：年度/期间/保管期限决定到期销毁日期，
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
        // 同一来源单据已有现有/在途卷时拒绝再次立卷。
        // 判重规则已收拢到 assertNoLiveVolumeForSource() 单点（三条立卷入口共用，见该方法注释）。
        assertNoLiveVolumeForSource(billId, archiveType);
        // bill_id 只对「ref 就是 exp_bill 主键」的那几类落值；非单据载体只落 ref_id＋类型，
        // 否则合同/供应商主键会挂进单据联查（getByBillId／findByBillId）里冒充某张单据的档案。
        boolean billCarrier = isBillScopedType(archiveType);
        OpArchive archive = new OpArchive();
        archive.setBillId(billCarrier ? billId : null);
        archive.setArchiveNo(nextArchiveNo(archiveType));
        // archive_code 取「编号镜像」：唯一性由 uk_tenant_archive_no 保证，编码不再另起一套号源。
        // 人工立卷不走 autoArchive 那条时间戳路径，两边写出来的编码必须是同一个口径。
        archive.setArchiveCode(archive.getArchiveNo());
        archive.setRefId(billId);
        archive.setArchiveType(archiveType);
        // 载体类型决定「归档前是否强制逐件编页」，空/未知一律落 PAPER（严格侧）
        archive.setCarrierType(normalizeCarrier(carrierType));
        archive.setArchiveYear(archiveYear);
        archive.setArchivePeriod(archivePeriod);
        // 到期销毁日期按《会计档案管理办法》算：保管期限从会计年度终了后的第一天起算 →
        // destroyDate = (archiveYear + retentionYears + 1) 年 1 月 1 日。
        // 这一列不落值，销毁申请就会卡在「未设置到期销毁日期，禁止销毁」。
        // 年度/保管年限的必填已在上面的守卫处办结，不在这里兜默认值。
        int retention = retentionPeriod;
        archive.setRetentionYears(retention);
        archive.setDestroyDate(destroyDateOf(archiveYear, null, retention));
        archive.setArchiveStatus("PENDING");
        // status 列不再由各写入位置随手填，一律由真相源 archive_status 映射（同库默认 FILED 一致）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        archive.setIntegrityStatus("UNKNOWN");
        // 立卷必须留下「谁立的卷」：归档人/归档时间是固定栏位，不是事后备注。
        // 无登录上下文时保持 NULL，不造假人。
        archive.setFilingUserId(userId());
        archive.setFilingTime(LocalDateTime.now());
        // 必须落 company_id：档案列表按登录人公司过滤(OrgGuard.enforceCompany)，
        // 不落值连创建者本人都看不到刚建的档案。取值口径与列表过滤一致。
        LoginUser _cu = UserContext.get();
        if (_cu != null && _cu.getCompanyId() != null) {
            archive.setCompanyId(_cu.getCompanyId());
        }
        // 立卷时回溯来源单据的审批流程实例。手工立卷的 billId 是自由文本、不带域标识，
        // 规则与 TRC-C 回填一致＝按 exp_bill 主键反查；查不到（非费用单据或输错）留 NULL。
        if (billCarrier) {
            BillArchiveDTO srcBill =
                    billId == null ? null : billFacade.findBillForArchive(billId);
            if (srcBill != null) {
                archive.setProcInstId(srcBill.getProcInstId());
            }
            // 目录要素（关联单号／标题／会计年度月份／归属公司）按来源单据真值补齐，
            // 与 autoArchive 同一把尺；无载体不造 file_id 引用。
            fillCatalogFromSourceBill(archive, srcBill);
        } else {
            // 非单据载体一律不反查 exp_bill（跨域裸主键串号），要素由本域解析器给
            fillCatalogFromCarrier(archive, resolveCarrier(archiveType, billId));
            fillPeriodFromArchivePeriod(archive);
        }
        // 公司已解析完成，再按模式补「原件保管单位」
        applyPaperKeeperDefault(archive);
        archiveCatalogMapper.insert(archive);
        // 立卷即落首件行（件号 1）。不要让卷没有件——旧版"卷＝件"的读法全靠这一行镜像。
        if (billCarrier && billId != null && billId > 0L) {
            writeVolumeItem(archive, billId, 1);
        }
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
        // 状态机强制校验：FM_ARCHIVE 审批在途不得重复发起（每次点都会新起一条实例）。
        // 判据用引擎实况而非只看列值——流程管理员 cancelInstance 作废实例不发布收尾事件（WfEngineFacade:3023），
        // 只看 ARCHIVING 会把档案永久锁死在途态（那类"永久不可办"）；无在办任务即放行重驱动。
        // 引擎查询异常时 fail-open（宁可重驱动，不可卡死）。已销毁档案一律不得再归档。
        if ("ARCHIVING".equals(cur) && hasRunningArchiveFlow(archiveId)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案 " + archive.getArchiveNo() + " 归档审批进行中（FM_ARCHIVE 未闭环），不得重复执行归档");
        }
        if ("DISPOSED".equals(cur)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案 " + archive.getArchiveNo() + " 已销毁，不得再执行归档");
        }
        // 入口闸①：空卷禁归档。卷内件清单是归档审批的<b>内容物</b>，0 件的卷没有可核对象，
        // 放过去＝审批人对着空气点"通过"（立 FMA_1 那节就是为了有人复核）。
        long itemCount = volumeItemCount(archiveId);
        if (itemCount <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "案卷 " + archive.getArchiveNo() + " 卷内无件，不得执行归档；请先在卷内目录加入待归档单据。");
        }
        // 入口闸③：内容物判据（件号连续／纸质卷已编页）在执行归档入口就拦下。
        // 同一条规则在 FM_ARCHIVE 定稿分支（preArchiveFailures）也会拦，但那一步已是审批人点了「通过」之后——
        // 只挂定稿就会变成「审批人点通过、档案永远停在归档中」，把死结推给审批人。
        // 此处 400 fail-fast；判据与定稿共用 volumeContentFailures 一处实现，不要两处各写一版。
        List<String> contentFailures = volumeContentFailures(archive);
        if (!contentFailures.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "案卷 " + archive.getArchiveNo() + " 归档前置未满足：" + String.join("；", contentFailures));
        }
        // 入口闸②：实物签收前置在人工腿也要有。这条尺自动腿早就有
        // （BillArchiveService.doArchive / eArchiveBill，防「签收沦为事后补章」），
        // 人工「新建档案目录→执行归档」不能绕过，否则同一件事两条腿一有一无。非集中管理公司无实物流，豁免；
        // 卷内任一件未签收即拦，并报出单号（不整批静默跳过）。
        if (!deliveryService.isNonCentralArchive(archive.getCompanyId())) {
            List<String> unsigned = new ArrayList<>();
            for (OpArchiveItem i : listVolumeItems(archiveId)) {
                if (i.getBillId() == null) {
                    continue;
                }
                BillArchiveDTO b = billFacade.findBillForArchive(i.getBillId());
                if (b != null && "UNSIGNED".equals(b.getDeliveryStatus())) {
                    unsigned.add(firstNonBlank(b.getBillNo(), "单据#" + b.getId()));
                }
            }
            if (!unsigned.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "卷内 " + unsigned.size() + " 张单据实物未签收，归档前置未满足："
                                + String.join("、", unsigned) + "。请先在「共享运营 › 实物流签收」完成签收。");
            }
        }
        // ARCHIVED 行不拦：反归档（unarchive）全仓未实现，放开重执行是历史最终状态卷唯一的整改入口。
        // 这里的放行与立卷判重（assertNoLiveVolumeForSource）不冲突：那条拦的是「同一单据再建一张卷」，
        // 这里放行的是「同一张卷重跑归档审批」。
        // 执行归档只进中间态 ARCHIVING（op_archive.archive_status 的既有枚举 PENDING/ARCHIVING/ARCHIVED/DISPOSED，
        // 未新增取值；追溯链 archivedCount 按「PENDING/ARCHIVING 不计」统计）。
        // 本方法随后就发 FM_ARCHIVE 审批流，此刻三个节点一个没办；若这时就把
        // ARCHIVED／archived_at／VERIFIED 全写完，审批闭环只剩「把同样的值再写一遍」，驳回也没有回退。
        // 最终状态与源单据回写一律由审批闭环 ArchiveFacadeImpl.handleArchiveApproval 落。
        archive.setArchiveStatus("ARCHIVING");
        // 真相源每次跃迁都同源回写镜像列（顺带把历史行里写进 status 的错轴取值纠正回本轴名称表）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        // 执行归档前按来源单据把仍缺的目录要素补齐（只补空、不覆盖真值）。
        // 历史卷的 archive_code/ref_no/title/period_year/period_month 常只有半套，
        // 而紧接的哈希与 FM_ARCHIVE 标题都要用它们——先补再封，快照与目录才一致。
        if (isBillScopedType(archive.getArchiveType())) {
            Long srcBillId = resolveSourceBillId(archive);
            fillCatalogFromSourceBill(archive, srcBillId == null ? null : billFacade.findBillForArchive(srcBillId));
        }
        if (archive.getArchiveCode() == null && archive.getArchiveNo() != null) {
            archive.setArchiveCode(archive.getArchiveNo());   // 档案编码＝编号镜像（冗余列）
        }
        // 防篡改：归档快照哈希在执行归档时固化（文件此刻才移入归档区），
        // 但「校验通过」是审批闭环的结论——哈希已算≠已校验。此处只留 UNKNOWN，
        // 闭环重算比对后才置 VERIFIED。
        archive.setIntegrityHash(archiveIntegrityService.snapshot(archive));
        archive.setIntegrityStatus("UNKNOWN");
        archiveCatalogMapper.updateById(archive);
        // 回写源单据不出现在这里：「归档生效」＝审批闭环通过，而不是点击执行。
        // 回写由 handleArchiveApproval 在同一语义下完成（同一白名单 ArchiveService.isBillScopedType）。
        // exp_bill.archive_status 只有 UNARCHIVED/ARCHIVED 两值，无中间态可用，也不得新造取值
        // ⇒ 审批在途期间单据侧保持 UNARCHIVED（不置位），最终状态才 ARCHIVED。
        // 接线 M1 FM_ARCHIVE 凭证归档审批流：执行归档即发起归档审批
        try {
            // 起流的返回值就是「档案自身的审批实例号」。表上暂无列可存（加列属 DDL 待确认，
            // 注释态 SQL 见 OpArchive 类注释），先如实记进日志；读侧由 fillArchiveFlowKey 从
            // wf_instance 补充出同一个值，档案反查归档审批链由此拿到明确可点的键。
            // 流程标题必须取真名，不能拿 biz_id 凑：wf_instance.title 若是「凭证归档-24」这种串，
            // 既看不出是哪张单、也对不上任何业务号，审批人在待办里无从核对。
            // 取真名的优先序：档案标题（立卷时已按来源单据真名补齐，见 fillCatalogFromSourceBill）
            // → 关联单号 ref_no → 档案编号 archive_no → 档案编码 archive_code；
            // 全部缺失才退回「档案#ID」（仍带 ID，但前面已尽力取真名，且记 warn 便于回补）。
            String flowTitle = archiveFlowTitle(archive);
            String archiveProcInstId = wfEngineFacade.start("FM_ARCHIVE", archiveId, userId(), flowTitle, null);
            log.info("档案 {} 已发起归档审批：FM_ARCHIVE procInstId={}（档案自身实例号，"
                            + "与 op_archive.proc_inst_id 里的源单据实例号不是一回事）",
                    archive.getArchiveNo(), archiveProcInstId);
        } catch (Exception ex) {
            log.error("档案 {} 发起归档审批失败: {}", archiveId, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不能静默降级成没有流程的孤儿单
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
     * 内控红线，销毁不可逆：这里<b>没有</b>一键销毁方法，也<b>没有</b>「按 id 删档案」的通用删除。
     * 直置 DISPOSED 而不做到期校验、不查销毁审批、不起 FM_DESTROY 流程，就是绕过整条合规链。
     * 所有人工销毁入口一律转 {@link ArchiveDestroyService#createDestroyRequest} 发起「销毁申请」，
     * 由合规链定论（assertAllExpired 到期校验＋双人复核双签＋FM_DESTROY 起流程）；
     * DISPOSED 的唯一落地点是 {@link ArchiveDestroyService#executeDestroy}（审批闭环后执行）。
     * 登记删除的唯一出口是 {@link #deleteEmptyVolume(Long)}（仅「待归档＋卷内 0 件」）。
     */

    public OpArchive getById(Long id) {
        OpArchive archive = archiveCatalogMapper.selectById(id);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + id);
        }
        assertArchiveVisible(archive);
        fillArchiveFlowKey(List.of(archive));
        return archive;
    }

    /**
     * 给档案行补上<b>归档审批流自身</b>的实例号与状态。
     *
     * <p><b>为什么不改 {@code op_archive.proc_inst_id} 的取值</b>：那一列存的是
     * <b>被归档那张单</b>的审批实例，「单据全链路 → 档案」段（{@code BillTraceChainController}
     * 按 proc_inst_id 回溯）与 {@code ArchiveFacadeImpl.findByProcInstIds} 都靠它；
     * 就地翻成归档实例，正向联查当场就断了。表上又只有这一列实例号，加列属 DDL 待确认
     * （注释态 SQL 已写在 {@code OpArchive} 类注释里，未执行）。
     * 所以走读侧：按 biz_type＋biz_id 补充，归档流自己的实例号可查、可点、可反查审批链，
     * 既有正向链路一字未动。
     *
     * <p>一次批量查询（不按行循环打库），同一档案多轮归档实例时取 id 最大的一条＝最新一轮，
     * 与 {@code PayBatchService.fillDisplayFields} 同一规则。补充失败只记日志——档案列表不能因为
     * 一个附加列取不到而打不开。
     */
    private void fillArchiveFlowKey(List<OpArchive> archives) {
        fillItemCount(archives);   // 卷内件数与归档流号同属读侧补充，一次批量，不逐卷打库
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

    /**
     * 卷内件数批量补充：一次 {@code GROUP BY archive_id} 聚合，不要按卷循环打库。
     *
     * <p>界面要靠这个数把"0 件的卷"标出来并禁用【执行归档】——后端 {@code archive()} 入口闸也会拦，
     * 但不要让人撞了报错才知道（能前置说明的，就别留给一次失败请求）。
     * 取数失败只记日志并留 null，档案列表不能因为一个附加列取不到而打不开。
     */
    private void fillItemCount(List<OpArchive> archives) {
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
            Map<Long, Integer> cnt = new HashMap<>();
            for (Map<String, Object> row : archiveItemMapper.selectMaps(
                    new QueryWrapper<OpArchiveItem>()
                            .select("archive_id AS archiveId", "COUNT(*) AS cnt")
                            .in("archive_id", ids)
                            .groupBy("archive_id"))) {
                Object vid = row.get("archiveId");
                Object c = row.get("cnt");
                if (vid != null && c != null) {
                    cnt.put(Long.valueOf(vid.toString()), Integer.valueOf(c.toString()));
                }
            }
            for (OpArchive a : archives) {
                if (a != null && a.getId() != null) {
                    a.setItemCount(cnt.getOrDefault(a.getId(), 0));
                }
            }
        } catch (Exception ex) {
            log.warn("档案行补充卷内件数失败（不影响档案取数）: {}", ex.getMessage());
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * FM_ARCHIVE 的流程标题：真名优先，不拿 biz_id 凑。
     * 标题落在 {@code wf_instance.title}，是待办列表／流程监控上唯一的人眼标识；
     * 若拼成 {@code "凭证归档-" + archiveCode或档案ID}，archive_code 为空时就只剩「凭证归档-24」这种串。
     */
    private String archiveFlowTitle(OpArchive archive) {
        String name = firstNonBlank(archive.getTitle(),
                firstNonBlank(archive.getRefNo(), firstNonBlank(archive.getArchiveNo(), archive.getArchiveCode())));
        if (name == null) {
            log.warn("档案 {} 无标题／关联单号／编号可用作流程标题，FM_ARCHIVE 标题退回 ID 形态",
                    archive.getId());
            return "凭证归档-档案#" + archive.getId();
        }
        return "凭证归档-" + name;
    }

    /**
     * 读侧挂点对外入口：把「归档审批流自身」的实例号与状态补充进档案行。
     * 供档案域之外的档案列表端点（如共享作业 {@code SharingEnhanceService.archivePage}，
     * 即「档案归档」页的已归档档案 Tab）复用，避免各端点各自决定要不要挂这条补充。
     * 只读 wf_instance，不写任何流程表，也不翻转 {@code proc_inst_id} 的源单据语义。
     */
    @FsscDataSource("master")
    public void enrichFlowKey(List<OpArchive> archives) {
        fillArchiveFlowKey(archives);
    }

    /*
     * 源单据回写（archive_status=ARCHIVED + archive_time + archive_by）不在这里做。
     * 回写统一由审批闭环 ArchiveFacadeImpl.handleArchiveApproval 单点执行；
     * 类型白名单与目标解析上移为本类的 isBillScopedType()／resolveSourceBillId()，
     * 两条链路共用同一把尺——各写一套谓词就会出现「管控」与「回写」方向相反的情况。
     */

    /**
     * 读侧补充（零 DDL）：按单据载体类型把 {@code ref_no} 回填成 {@code billNo}。
     * 档案列表/选择器出参是裸实体且没有编号字段 ⇒ 前端「关联单据」列只能回落主键，显示成 {@code 单据#null}。
     * 非单据载体（合同/供应商…）保持 NULL——拿别域单号冒充单据编号＝第二个语义陷阱。
     */
    private void fillSourceBillNo(List<OpArchive> archives) {
        if (archives == null || archives.isEmpty()) {
            return;
        }
        for (OpArchive a : archives) {
            if (a != null && a.getBillNo() == null && isBillScopedType(a.getArchiveType())) {
                a.setBillNo(a.getRefNo());
            }
        }
    }

    public PageResult<OpArchive> page(long pageNum, long pageSize, String archiveType,
                                            Integer archiveYear, String archiveStatus) {
        return page(pageNum, pageSize, archiveType, archiveYear, archiveStatus, null);
    }

    /**
     * 档案列表端点<b>支持按关键词取档案</b>。
     *
     * <p>关键词必须下推到服务端。只认类型/年度/状态三个筛选、把检索框放在前端过滤时，
     * 只能在「已经取回的第一页」里本地匹配，第一页取不满的部分永远搜不到，
     * 输入真实档案号就返 0 条。检索臂与 {@link #search} <b>共用同一把尺</b>
     * （{@link #applyKeywordArms}，定下的五列面），不新增也不删减任何一列。
     *
     * <p><b>与 {@link #search} 的唯一差别（也正是本方法的用途所在）</b>：{@code search} 是
     * 档案检索规则，硬性只出 {@code ARCHIVED}；本方法服务列表/选择器，
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
        if (archiveStatus != null && !archiveStatus.isBlank()) {
            // 归档整理页要「在办」＝待归档＋归档中两态，故允许逗号分隔多值（in）；单值走 eq。
            List<String> states = Arrays.stream(archiveStatus.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (states.size() == 1) {
                qw.eq(OpArchive::getArchiveStatus, states.get(0));
            } else if (states.size() > 1) {
                qw.in(OpArchive::getArchiveStatus, states);
            }
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(OpArchive::getCompanyId, scopeC);
        }
        // 计数必须在挂 ORDER BY 之前发出：MyBatis-Plus 的 selectCount 原样继承 wrapper 的排序子句，
        // 而 MySQL 一旦看到 ORDER BY 就无法只走索引求和，必须逐行回表取 create_time 再排序
        // （实测 10.3 万行 ARCHIVED：覆盖索引 39ms → 带回表 324ms）。排序只服务下面这页取值。
        long total = archiveCatalogMapper.selectCount(qw);
        qw.orderByDesc(OpArchive::getCreateTime);
        qw.last("LIMIT " + (pageNum - 1) * pageSize + "," + pageSize);
        List<OpArchive> list = archiveCatalogMapper.selectList(qw);
        fillArchiveFlowKey(list);   // 档案列表行带出归档审批流自身实例号
        fillSourceBillNo(list);     // 关联单据列带出业务编号（前端不再回落主键）
        return PageResult.of(list, total, pageNum, pageSize);
    }

    /**
     * 档案完整性校验（防篡改）。
     *
     * <p>必须重算哈希并与归档时固化的快照比对，不能只回读 integrity_status 字段——
     * 那等于「自己说自己没被改」，发现不了归档文件被替换或关键元数据被篡改。
     *
     * <p>本方法由 GET {@code /{id}/verify} 进入，是<b>只读语义</b>，方法体内不含任何写库调用：
     * 单纯重算比对并返回布尔。在此写 {@code integrity_status} 与 {@code update_time}，
     * 反复点「校验」就会反复写状态、污染审计时间列，而 GET 本不去重。
     * 状态落库与校验人记录一律走显式 POST {@code /{id}/verify}（{@link #verifyIntegrityFull}）。
     *
     * <p>重算一律走 {@link ArchiveIntegrityService#snapshot}——与写入侧<b>同一套算式</b>，
     * 否则「写入用甲式、校验用乙式」必然误报。
     * 历史行（旧算式产物，含被写成裸文件 MD5 的）在新算式下会判 MISMATCH，属预期，
     * 需要一次性重算回灌（数据轨，另开窗口执行）。
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
     * 归档定稿前的逐项检查，返回**未通过清单**（空＝全通过）。
     * <p>不引入新阈值——只用归档时已固化的完整性哈希与其重算值。
     * {@link #verifyIntegrity} 只回布尔、不下结论；撤掉 FM_ARCHIVE 的人工复核后，
     * 由本方法把判据接进定稿分支。
     */
    public List<String> preArchiveFailures(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            return List.of("档案不存在: " + archiveId);
        }
        List<String> failures = new ArrayList<>();
        if (archive.getIntegrityHash() == null) {
            failures.add("未固化完整性哈希，归档是否被改动无法校验（历史档案，需重新归档）");
        } else if (!archive.getIntegrityHash().equals(archiveIntegrityService.snapshot(archive))) {
            failures.add("完整性哈希与归档快照不一致（归档文件或关键元数据已被改动）");
        }
        failures.addAll(volumeContentFailures(archive));
        return failures;
    }

    /**
     * 内容物判据（与完整性哈希无关）：卷内件齐套、件号连续、纸质卷已编页。
     *
     * <p>本方法被两处共用，不允许各处再写一版：
     * <ul>
     *   <li>{@link #preArchiveFailures}（FM_ARCHIVE 审批定稿分支）——定稿前的完整判据；</li>
     *   <li>{@link #archive}（执行归档入口）——<b>fail-fast</b>：内容物不满足时不该起审批流。
     *       只挂定稿会出现「审批人点通过、档案永远停在归档中」的死结（本节判据全在定稿侧拦），
     *       等于把无法办结的单子推给审批人。</li>
     * </ul>
     * 哈希判据不在此处：执行归档时哈希由 {@link #archive} 当场固化，入口阶段必然为空，比了必假红。
     */
    public List<String> volumeContentFailures(OpArchive archive) {
        List<String> failures = new ArrayList<>();
        if (archive == null) {
            return failures;
        }
        Long archiveId = archive.getId();
        // 定稿判据必须包含「卷内目录齐套」两条：只比哈希的话，复核人既看不到影像，
        // 系统也不校验件是否齐，定稿那一步就形同虚设。
        long items = archiveId == null ? 0L : volumeItemCount(archiveId);
        if (items <= 0L) {
            failures.add("卷内无件，归档结论无人可核（请在卷内目录加入待归档单据后重新执行归档）");
        } else if (!volumeSeqContinuous(archiveId)) {
            failures.add("卷内件号不连续（存在空洞或重号），卷内目录与实物不一致");
        }
        // 页号要么全卷都不编（纯电子件），要么按件号从第 1 页起连续编到卷末——
        // 只编一半或跳号，装订后「第几页」对不上目录，编页这个控制点等于没设。全空时判定为未编，不拦。
        List<OpArchiveItem> volItems = items <= 0L ? List.of() : listVolumeItems(archiveId);
        boolean anyPages = false;
        boolean allPages = true;
        for (OpArchiveItem it : volItems) {
            boolean has = it.getPageFrom() != null && it.getPageFrom() > 0
                    && it.getPageCount() != null && it.getPageCount() > 0;
            anyPages = anyPages || has;
            allPages = allPages && has;
        }
        // 「编页」只对纸质卷成立（装订后页码是实物位置标识）；电子档案以「件/文件」为最小单位，
        // 不要求页码。所以按 carrier_type 分档：纸质卷全卷未编 ⇒ 拦（提示逐件编页，或把卷改成电子）；
        // 电子卷未编 ⇒ 放行。
        boolean paperVolume = !"ELECTRONIC".equalsIgnoreCase(archive.getCarrierType());
        if (paperVolume && !volItems.isEmpty() && !anyPages) {
            failures.add("纸质卷未编页：请按件填写起页与页数（从第 1 页起连续），"
                    + "电子载体请把卷的载体类型改为「电子」");
        }
        if (anyPages && !allPages) {
            failures.add("卷内件页号只编了一部分（须全卷一起编页，或全不编页）");
        } else if (anyPages) {
            int expect = 1;
            for (OpArchiveItem it : volItems) {
                if (it.getPageFrom() != expect) {
                    failures.add("卷内页号不连续（第 " + it.getSeqNo() + " 件应从第 " + expect
                            + " 页起，实为第 " + it.getPageFrom() + " 页）");
                    break;
                }
                expect += it.getPageCount();
            }
        }
        return failures;
    }

    // 卷内件清单（一卷多件）——写入与校验的唯一 owner

    /** 档案行读侧可见性（本公司／本人立的卷／集团级或全数据范围账号豁免）；{@code getById} 与本节共用一把尺。 */
    public void assertArchiveVisible(OpArchive archive) {
        if (archive == null) {
            return;
        }
        orgGuard.assertVisible(archive.getCompanyId(), archive.getFilingUserId());
    }

    /** 卷内件清单（按件号正序，附带文件数）。 */
    public List<OpArchiveItem> listVolumeItems(Long archiveId) {
        List<OpArchiveItem> items = archiveItemMapper.selectList(new LambdaQueryWrapper<OpArchiveItem>()
                .eq(OpArchiveItem::getArchiveId, archiveId)
                .orderByAsc(OpArchiveItem::getSeqNo));
        for (OpArchiveItem i : items) {
            i.setFileCount(countFileIds(i.getFileIds()));
        }
        return items;
    }

    /** 卷内件数。 */
    public long volumeItemCount(Long archiveId) {
        Long cnt = archiveItemMapper.selectCount(new LambdaQueryWrapper<OpArchiveItem>()
                .eq(OpArchiveItem::getArchiveId, archiveId));
        return cnt == null ? 0L : cnt;
    }

    /** 件号是否自 1 连续（定稿判据；空洞只可能来自直接改库或迁移缺件）。 */
    public boolean volumeSeqContinuous(Long archiveId) {
        int expect = 1;
        for (OpArchiveItem i : listVolumeItems(archiveId)) {
            if (i.getSeqNo() == null || i.getSeqNo() != expect) {
                return false;
            }
            expect++;
        }
        return true;
    }

    /**
     * 向在册卷追加一件（幂等：同卷已有该单则跳过并返回既有行）。
     *
     * <p>可改窗口＝待归档；归档中（审批在途）与已定稿一律拒绝——否则"审批人核的卷"与"定稿后的卷"
     * 内容不一致，归档审批这个控制点就白设（的立点本意）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpArchiveItem addVolumeItem(Long archiveId, Long billId) {
        OpArchive volume = requireEditableVolume(archiveId);
        OpArchiveItem exists = archiveItemMapper.selectOne(new LambdaQueryWrapper<OpArchiveItem>()
                .eq(OpArchiveItem::getArchiveId, archiveId)
                .eq(OpArchiveItem::getBillId, billId)
                .last("LIMIT 1"));
        if (exists != null) {
            return exists;
        }
        assertBillNotInOtherVolume(billId, archiveId);
        return writeVolumeItem(volume, billId, nextVolumeSeq(archiveId));
    }

    /** 把一件移出本卷（只解绑，不要动单据、不要动卷本身）；移出后件号重排保持连续。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeVolumeItem(Long archiveId, Long itemId) {
        OpArchive volume = requireEditableVolume(archiveId);
        OpArchiveItem item = archiveItemMapper.selectById(itemId);
        if (item == null || !archiveId.equals(item.getArchiveId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该件不属于本案卷，未执行移出");
        }
        archiveItemMapper.hardDelete(itemId);
        resequenceItems(archiveId);
        log.info("案卷 {} 移出件 {}（原件号 {}）", volume.getArchiveNo(), itemId, item.getSeqNo());
        releaseSourceWhenEmpty(volume);
    }

    /**
     * 卷内件清空后解除来源单据绑定。
     *
     * <p>卷表的 {@code ref_id/bill_id/ref_no/related_ids} 是「首件镜像」，件表才是内容真相。
     * 件全部移走后镜像仍在，于是 {@link #findLiveVolumes} 仍认为这张单据挂在本卷上：
     * 移出卷的提示说「可再加入其他案卷」，实际再建卷必被 {@link #assertNoLiveVolumeForSource}
     * 以「同一单据不得重复立卷」拦下，并建议「删除登记」——而界面上没有这个入口，
     * 单据就被永久锁在一只空卷里。这里把镜像归零（{@code ref_id=0} 即系统约定的「无来源键」），
     * 单据回到待立卷候选；空卷本体保留，可继续【追加件】或停在那里不占单据。
     *
     * <p>{@code proc_inst_id} 一并清空：单据流转页的电子档案段按 bill_id＋审批实例号两臂取卷，
     * 保留实例号会让这张单据的溯源里多出一行早已空掉的「待归档」卷，看着像还挂着一只卷。
     */
    private void releaseSourceWhenEmpty(OpArchive volume) {
        if (volumeItemCount(volume.getId()) > 0L) {
            return;
        }
        archiveCatalogMapper.update(null, new LambdaUpdateWrapper<OpArchive>()
                .eq(OpArchive::getId, volume.getId())
                .set(OpArchive::getRefId, 0L)
                .set(OpArchive::getBillId, null)
                .set(OpArchive::getRefNo, null)
                .set(OpArchive::getRelatedIds, null)
                .set(OpArchive::getProcInstId, null)
                .set(OpArchive::getUpdateTime, LocalDateTime.now()));
        log.info("案卷 {} 卷内已无件，解除来源单据绑定", volume.getArchiveNo());
    }

    /**
     * 删除空案卷（仅「待归档」且卷内 0 件）。
     *
     * <p>建错卷、或件全部移出后留下的空壳，此前没有出口：既不能执行归档（入口闸拦空卷），
     * 界面上也没有删除入口，只能一直占着一行。这里补唯一删除点，且只放行这一种形态；
     * 归档中/已归档/已销毁的卷不得删——那些要留痕、要走鉴定销毁审批。
     * 逻辑删除，档号不回收（号码规则继续向后取号，不得造出重号）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmptyVolume(Long archiveId) {
        OpArchive volume = archiveCatalogMapper.selectById(archiveId);
        if (volume == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "案卷不存在或已删除");
        }
        if (!"PENDING".equals(volume.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "只有「待归档」的案卷可以删除；案卷 " + volume.getArchiveNo()
                            + " 当前为「" + volume.getArchiveStatus() + "」，已归档的卷请走鉴定销毁审批");
        }
        long n = volumeItemCount(archiveId);
        if (n > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "卷内还有 " + n + " 件，不能删除；请先在卷内目录把件移出（移出只解绑，不动单据）");
        }
        archiveCatalogMapper.deleteById(archiveId);
        log.info("删除空案卷 {}（卷内 0 件）", volume.getArchiveNo());
    }

    /**
     * 件级编页（DA/T 22—2015 归档文件整理规则之页号）。
     *
     * <p>纸质卷装订后"第几页"是核对实物与目录的唯一锚点，{@code op_archive_item.page_from/page_count}
     * 两列自建表起就存在却从无写入点（组卷入卷只落件号），等于编页无处著录。
     * 本方法补上唯一写入点：起页＋页数一起给，页号区间不与卷内其它件重叠（重叠＝同一页被两件认领，
     * 装订后无从核对）；两者同时留空＝清空该件页号（不要只清一半，起页在、页数空＝无法判区间）。
     *
     * <p>可改窗口与件清单同一把尺（{@link #requireEditableVolume}）：归档审批在途、已定稿的卷不得改页号。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateItemPages(Long archiveId, Long itemId, Integer pageFrom, Integer pageCount) {
        requireEditableVolume(archiveId);
        OpArchiveItem item = itemId == null ? null : archiveItemMapper.selectById(itemId);
        if (item == null || !archiveId.equals(item.getArchiveId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该件不属于本案卷，未执行编页");
        }
        boolean emptyFrom = pageFrom == null || pageFrom <= 0;
        boolean emptyCount = pageCount == null || pageCount <= 0;
        if (!emptyFrom && !emptyCount) {
            int from = pageFrom;
            int to = pageFrom + pageCount - 1;
            for (OpArchiveItem other : listVolumeItems(archiveId)) {
                if (other.getId().equals(itemId)
                        || other.getPageFrom() == null || other.getPageCount() == null || other.getPageCount() <= 0) {
                    continue;
                }
                int of = other.getPageFrom();
                int ot = other.getPageFrom() + other.getPageCount() - 1;
                if (from <= ot && of <= to) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "页号与第 " + other.getSeqNo()
                            + " 件（第 " + of + "-" + ot + " 页）重叠，请重新编页");
                }
            }
        } else if (!emptyFrom || !emptyCount) {
            throw new BizException(ErrorCode.PARAM_INVALID, "起页与页数须同时填写（均为正整数），或同时留空以清除");
        }
        archiveItemMapper.update(null, new LambdaUpdateWrapper<OpArchiveItem>()
                .eq(OpArchiveItem::getId, itemId)
                .set(OpArchiveItem::getPageFrom, emptyFrom ? null : pageFrom)
                .set(OpArchiveItem::getPageCount, emptyCount ? null : pageCount)
                .set(OpArchiveItem::getUpdateTime, LocalDateTime.now()));
        log.info("案卷 {} 件 {}（件号 {}）编页：起 {} 页 / 共 {} 页",
                archiveId, itemId, item.getSeqNo(), emptyFrom ? null : pageFrom, emptyCount ? null : pageCount);
    }

    /**
     * 该单据是否已挂在<b>别的</b>在册/在途卷里。判重键从"同单不得重复立卷"换成"同单不得同时在两卷"：
     * 既查 {@code op_archive}（历史卷只有首件镜像、可能没有件行），也查 {@code op_archive_item}，
     * 两表并集才是"在册"的完整定义（不要只看一张表，的成因就是两条链路各写一套谓词）。
     */
    public void assertBillNotInOtherVolume(Long billId, Long keepThisArchiveId) {
        if (billId == null || billId <= 0L) {
            return;
        }
        Set<Long> volumeIds = new LinkedHashSet<>();
        for (OpArchive a : findLiveVolumes(billId, "BILL")) {
            volumeIds.add(a.getId());
        }
        for (OpArchiveItem i : archiveItemMapper.selectList(new LambdaQueryWrapper<OpArchiveItem>()
                .eq(OpArchiveItem::getBillId, billId))) {
            volumeIds.add(i.getArchiveId());
        }
        volumeIds.remove(keepThisArchiveId);
        List<String> nos = new ArrayList<>();
        for (Long vid : volumeIds) {
            OpArchive v = vid == null ? null : archiveCatalogMapper.selectById(vid);
            if (v != null && LIVE_VOLUME_STATUSES.contains(v.getArchiveStatus())) {
                nos.add(firstNonBlank(v.getArchiveNo(), v.getArchiveCode(), "档案#" + v.getId())
                        + "（" + archiveStatusLabel(v.getArchiveStatus()) + "）");
            }
        }
        if (!nos.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该单据已在案卷内，不得同时挂在两卷；原案卷号：" + String.join("、", nos)
                            + "。如需改卷，请先在原卷中「移出」该件。");
        }
    }

    /** 落一条件行：件的事实全部从来源单据现取（不要凭默认值造件、不要拿主键冒充单号）。 */
    private OpArchiveItem writeVolumeItem(OpArchive volume, Long billId, int seqNo) {
        BillArchiveDTO bill = billFacade.findBillForArchive(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在，未加入案卷: " + billId);
        }
        orgGuard.assertCompanyAllowed(bill.getCompanyId());
        if (volume != null && volume.getCompanyId() != null && bill.getCompanyId() != null
                && !volume.getCompanyId().equals(bill.getCompanyId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "跨公司混卷：案卷归属公司 " + volume.getCompanyId() + "，所选单据 " + bill.getBillNo()
                            + " 属公司 " + bill.getCompanyId() + "。请按公司分别组卷。");
        }
        List<Long> files = billFacade.getAttachFileIdsByBillId(billId);
        String fileIds = files.stream().map(String::valueOf).collect(Collectors.joining(","));
        OpArchiveItem item = new OpArchiveItem();
        item.setArchiveId(volume.getId());
        item.setSeqNo(seqNo);
        item.setBillType("EXP_BILL");
        item.setBillId(billId);
        item.setBillNo(bill.getBillNo());
        item.setVoucherNo(blankToNull(bill.getVoucherNo()));
        item.setTitle(bill.getTitle() == null ? bill.getBillNo() : bill.getTitle());
        item.setItemDate(bill.getApproveTime() == null ? null : bill.getApproveTime().toLocalDate());
        item.setFileIds(fileIds.isEmpty() ? null : fileIds);
        item.setFileCount(files.size());
        item.setAmount(bill.getTotalAmount());
        item.setCompanyId(bill.getCompanyId());
        item.setRemark("组卷入卷（件号 " + seqNo + "）");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        archiveItemMapper.insert(item);
        return item;
    }

    /** 只有"待归档"的卷能改件清单。 */
    private OpArchive requireEditableVolume(Long archiveId) {
        OpArchive volume = archiveId == null ? null : archiveCatalogMapper.selectById(archiveId);
        if (volume == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        assertArchiveVisible(volume);
        if ("ARCHIVING".equals(volume.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "案卷 " + volume.getArchiveNo() + " 归档审批进行中，不得增删卷内件；请先驳回该审批流。");
        }
        if ("ARCHIVED".equals(volume.getArchiveStatus()) || "DISPOSED".equals(volume.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "案卷 " + volume.getArchiveNo() + " 已定稿，卷内件清单不可再变更。");
        }
        return volume;
    }

    private int nextVolumeSeq(Long archiveId) {
        int max = 0;
        for (OpArchiveItem i : listVolumeItems(archiveId)) {
            if (i.getSeqNo() != null && i.getSeqNo() > max) {
                max = i.getSeqNo();
            }
        }
        return max + 1;
    }

    private void resequenceItems(Long archiveId) {
        int expect = 1;
        for (OpArchiveItem i : listVolumeItems(archiveId)) {
            if (i.getSeqNo() == null || i.getSeqNo() != expect) {
                OpArchiveItem upd = new OpArchiveItem();
                upd.setId(i.getId());
                upd.setSeqNo(expect);
                upd.setUpdateTime(LocalDateTime.now());
                archiveItemMapper.updateById(upd);
            }
            expect++;
        }
    }

    /** 件级空白归一：空串一律落 NULL（不要把空串写进库冒充"有值"，与列语义相反） */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static int countFileIds(String fileIds) {
        if (fileIds == null || fileIds.isBlank()) {
            return 0;
        }
        int n = 0;
        for (String part : fileIds.split(",")) {
            if (!part.isBlank()) {
                n++;
            }
        }
        return n;
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
        // 到期预警清单也是档案列表端点，同样要带出归档审批流实例号，
        // 否则只有 /page 与 /list 能反查归档链、这一份不能。
        fillArchiveFlowKey(list);
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long autoArchive(Long billId, String archiveType, Integer archiveYear,
                             String archivePeriod) {
        // 与 createArchive 同一把尺：自动归档也不得写入名称表外的类型
        archiveType = assertArchiveType(archiveType);
        // 判重与人工立卷共用 assertNoLiveVolumeForSource（同一把尺，不各写一套谓词）：
        // 本入口不判重的话，同一单据每点一次「自动归档」就多落一卷，
        // 而 batchArchive 逐单循环又是按同一入口连发。
        // 批量入口逐单 catch 异常并计数，个别单据已立卷不会拖垮整批。
        assertNoLiveVolumeForSource(billId, archiveType);
        // 与 createArchive 同一把尺——非单据载体不落 bill_id、不反查 exp_bill
        boolean billCarrier = isBillScopedType(archiveType);
        OpArchive archive = new OpArchive();
        // 档案编号租户内唯一，档案编码是它的冗余镜像。
        // 编码不能取「ARC-前缀-年度-」＋ System.currentTimeMillis() 的后 5 位：
        // ① batchArchive 逐单循环在同一秒里连落多卷，100 秒窗口内必然重码；
        // ② 旧卷的 archive_code 大面积 NULL，归档目录的「档案编码」要素整列空；
        // ③ 重码会进完整性哈希输入，两卷不同却算出同一个哈希。
        // 唯一性由 uk_tenant_archive_no 保证。
        archive.setArchiveNo(nextArchiveNo(archiveType));
        archive.setArchiveCode(archive.getArchiveNo());
        archive.setBillId(billCarrier ? billId : null);
        // ref_id 为 NOT NULL 无默认值：与 createArchive 规则一致，来源单据即 ref
        archive.setRefId(billId);
        archive.setArchiveType(archiveType);
        archive.setArchiveYear(archiveYear);
        archive.setArchivePeriod(archivePeriod);
        archive.setRetentionYears(getDefaultRetentionPeriod(archiveType));
        archive.setArchiveStatus("ARCHIVING");
        // status 是保管处置轴的冗余镜像列，取值一律由真相源 archive_status 映射（见 dispositionOf）
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));
        archive.setIntegrityStatus("UNKNOWN");
        // 立卷人／立卷时间两列必须与 createArchive 同一规则落值：有登录上下文就落真人，
        // 无上下文保持 NULL，不造假人。
        archive.setFilingUserId(userId());
        archive.setFilingTime(LocalDateTime.now());
        // 同 createArchive 规则，按 exp_bill 主键反查来源审批流程实例，查不到留 NULL
        if (billCarrier) {
            BillArchiveDTO src = billFacade.findBillForArchive(billId);
            if (src != null) {
                archive.setProcInstId(src.getProcInstId());
            }
            // 目录要素补齐（关联单号／档案标题／会计年度月份／所属公司），
            // 与 createArchive 共用 fillCatalogFromSourceBill，两条立卷入口不再一份填半套。
            fillCatalogFromSourceBill(archive, src);
        } else {
            // 非单据载体经本域解析器取要素（类型不在 exp_bill 语义内，一律不反查单据）
            fillCatalogFromCarrier(archive, resolveCarrier(archiveType, billId));
            fillPeriodFromArchivePeriod(archive);
        }
        // 到期销毁日期与人工立卷共用一把尺（destroyDateOf）：按 now()+保管年限算，与年度式对同一卷
        // 能差一年以上。此行必须排在目录要素补齐<b>之后</b>，因为 archive_year/period_year
        // 到这一步才取得到来源真值。
        archive.setDestroyDate(destroyDateOf(archive.getArchiveYear(), archive.getPeriodYear(),
                archive.getRetentionYears()));
        // 同人工立卷规则，按公司模式补「原件保管单位」
        applyPaperKeeperDefault(archive);
        archiveCatalogMapper.insert(archive);

        // 本入口只<b>登记待归档案卷</b>（PENDING＋哈希 UNKNOWN），不在同一次调用里把
        // 归档中→已校验→已归档→归档时间一路写完——那样「自动归档」就成了「控制点恒自动通过」。
        // 终态一律由 FM_ARCHIVE 审批闭环（ArchiveFacadeImpl.handleArchiveApproval）落：
        // 哈希在归档时固化，校验结论在闭环给出。
        archive.setIntegrityHash(archiveIntegrityService.snapshot(archive));
        archive.setIntegrityStatus("UNKNOWN");
        archive.setArchiveStatus("PENDING");
        archive.setStatus(dispositionOf(archive.getArchiveStatus()));   // status 是 archive_status 的镜像
        archiveCatalogMapper.updateById(archive);
        // 自动腿同样要落件行，否则新卷 0 件 ⇒ 被 archive() 的"空卷禁归档"闸永久拦死
        if (billCarrier && billId != null && billId > 0L) {
            writeVolumeItem(archive, billId, 1);
        }

        log.info("自动立卷完成（待归档，终态待 FM_ARCHIVE 审批）: archiveId={}, billId={}, archiveType={}",
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
     * 完整性校验（与 {@link #verifyIntegrity} 同一套算式）。
     *
     * <p>算式必须与写入侧（执行归档）一致。校验侧用「档案编码+类型+单据+凭证+附件」、
     * 写入侧用「档案号|ref|类型|文件」时，两式永不相等，点「校验」必报不一致——
     * 这就是「同单异值＝误报」。本方法与 {@link #verifyIntegrity} 只是入参形态不同的两个入口。
     *
     * <p>本方法是<b>显式写端点</b>——由 POST {@code /{id}/verify} 进入，负责把校验结论
     * 持久化到 {@code integrity_status}（{@code update_time} 由框架 MetaObjectHandler 自动填，不手写）；
     * 校验人（操作人）由控制器侧 {@code @OperLog} 落审计记录，本处日志同样带上校验人。
     *
     * <p>三态结论随响应返回（status/verified/message）。历史档案未固化哈希属
     * 「无法校验」（UNVERIFIABLE），不能写成 MISMATCH——那会把「没保住快照」误报成「被动过」。
     * 该情形落 UNVERIFIABLE 独立状态并提示重新归档。
     */
    public Map<String, Object> verifyIntegrityFull(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }

        String status;
        boolean verified;
        String message;
        if (archive.getIntegrityHash() == null) {
            // 无法校验（未固化哈希）——不写 MISMATCH，不冒充篡改结论
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("verified", verified);
        out.put("message", message);
        return out;
    }

    /**
     * 改卷的载体类型（纸质 ↔ 电子）。
     *
     * <p><b>为什么必须留这个口子</b>：载体决定「归档前是否强制逐件编页」，建卷时选错了就得能改——
     * 否则只能拿 SQL 订正，那正是全仓在禁的旁路。
     *
     * <p><b>可改窗口与卷内件同尺</b>：只有 {@code PENDING}（待归档）可改。审批在途改载体，
     * 审批人看的就是另一份卷；已定稿改载体则与归档结论不符。要改先走「驳回→待归档」。
     * 载体类型不在完整性哈希的输入段内（{@link ArchiveIntegrityService#snapshot}），改它不打破快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCarrier(Long archiveId, String carrierType) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        assertArchiveVisible(archive);
        if (!"PENDING".equals(archive.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "案卷 " + archive.getArchiveNo() + " 当前为「" + archiveStatusLabel(archive.getArchiveStatus())
                            + "」，只有待归档的卷可以改载体类型（审批在途/已定稿请走驳回）");
        }
        String v = normalizeCarrier(carrierType);
        if (v.equals(archive.getCarrierType())) {
            return;   // 幂等：选回原值不算一次写入
        }
        String before = archive.getCarrierType();
        archive.setCarrierType(v);
        archiveCatalogMapper.updateById(archive);
        log.info("案卷载体类型修改: archiveId={}, no={}, {} → {}, 操作人={}",
                archiveId, archive.getArchiveNo(), before, v, userId());
    }

    /**
     * 维护端点：<b>按卷重算并回灌</b>完整性哈希。
     *
     * <p><b>为什么需要它</b>：归档哈希的算式把 {@code no=档案编号} 与 {@code code=档案编码}
     * 都串进输入（{@link ArchiveIntegrityService#snapshot}），档号结构一改（比如加全宗前缀，
     * {@code <旧号> → <新号>}）这两段就变，在册卷会集体判成 {@code MISMATCH}。
     * 哈希串里还含 {@code files=[fileId:MD5]}（{@code sys_file.file_md5}），<b>SQL 算不出来</b>，
     * 只能由 Java 侧按同一算式重算——本方法就是那个唯一入口。
     *
     * <p><b>为什么改号要连带重算而不算"掩盖篡改"</b>：重算的对象是"改号<b>之后</b>这一卷的真实快照"，
     * 而不是把状态直接写成 VERIFIED。若文件或内容确被改动，重算出的值与数据库里其他锚点（件行／文件 MD5）
     * 仍将对不上，后续 {@code /verify} 会继续报不一致——即"用正确算式重新封存"，不是"伪造通过"。
     * 仍禁止 {@code UPDATE integrity_status='VERIFIED'} 之类直接改状态的 SQL。
     *
     * <p><b>状态取值</b>：已归档（{@code ARCHIVED}）的卷重算后落 {@code VERIFIED}（重算值＝新快照，
     * 定稿结论仍成立）；其余（PENDING／ARCHIVING）落 {@code UNKNOWN}——哈希已算≠已校验，
     * 校验结论只能由 FM_ARCHIVE 审批闭环给。已销毁（DISPOSED）的卷拒绝重算：销毁结论不可逆，
     * 不在维护口重开。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resignIntegrity(Long archiveId) {
        OpArchive archive = archiveCatalogMapper.selectById(archiveId);
        if (archive == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        assertArchiveVisible(archive);
        if ("DISPOSED".equals(archive.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案 " + archive.getArchiveNo() + " 已销毁，不得重算完整性哈希（销毁结论不可逆）");
        }
        String before = archive.getIntegrityHash();
        String resealed = archiveIntegrityService.snapshot(archive);
        boolean archived = "ARCHIVED".equals(archive.getArchiveStatus());
        String status = archived ? "VERIFIED" : "UNKNOWN";
        archive.setIntegrityHash(resealed);
        archive.setIntegrityStatus(status);
        archiveCatalogMapper.updateById(archive);
        log.info("按卷重算完整性哈希: archiveId={}, no={}, {} → {}, 状态={}, 操作人={}",
                archiveId, archive.getArchiveNo(),
                before == null ? "NULL" : before.substring(0, Math.min(8, before.length())),
                resealed == null ? "NULL" : resealed.substring(0, Math.min(8, resealed.length())),
                status, userId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("archiveId", archiveId);
        out.put("archiveNo", archive.getArchiveNo());
        out.put("hashBefore", before);
        out.put("hashAfter", resealed);
        out.put("integrityStatus", status);
        return out;
    }

    public OpArchive getByBillId(Long billId) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        // bill_id 只在单据语义的类型集内才是单据主键——合同/供应商等非单据载体不得冒充某张单据的档案
        qw.eq(OpArchive::getBillId, billId)
                .in(OpArchive::getArchiveType, BILL_SCOPED_ARCHIVE_TYPES)
                .orderByDesc(OpArchive::getCreateTime)
                .last("limit 1");
        OpArchive archive = archiveCatalogMapper.selectOne(qw);
        if (archive != null) {
            fillArchiveFlowKey(List.of(archive));
        }
        return archive;
    }

    /**
     * 关键词检索臂的<b>唯一规则</b>——
     * 档案编码、<b>档案编号（真实卷号）</b>、关联单号、档案标题、载体清单五列 LIKE。
     *
     * <p>检索面必须覆盖「用户真正拿来核对的那几列」。只 LIKE {@code archive_code}/{@code related_ids}
     * 时，archive_code 常常为空，而关联单号、标题、卷号根本不在检索面里，
     * 用户拿单号或卷号搜必然 0 行。列表端点（{@link #page} 带 keyword）与检索端点
     * {@link #search} 共用本方法。
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
            // 检索面已含 archive_no（真实档案号）精确 LIKE 臂，
            // "选择器/检索框输入真实档案号返 0 条"的读侧断点在此办结（规则见 applyKeywordArms）。
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
                .orderByDesc(OpArchive::getCreateTime)
                // 本端点无分页入参，全量 ARCHIVED 在 52 万卷语料上实测回出 95 MB 单响应
                // （/compliance/archive/{list,search}），任何调用方都会被整份实体反序列化拖住。
                // 检索只用于"找卷"，硬上限 500 行足够定位，超出部分请走 /page 分页端点。
                .last("LIMIT " + SEARCH_MAX_ROWS);
        List<OpArchive> hits = archiveCatalogMapper.selectList(qw);
        fillArchiveFlowKey(hits);
        fillSourceBillNo(hits);     // 检索面与列表面同一把尺
        return hits;
    }

    /**
     * 档案统计三数（总数／已归档／待归档）。
     *
     * <p><b>一条 GROUP BY 出三数</b>：原实现跑三次独立的 {@code selectCount}，每次都把整租户的
     * 卷表扫一遍（52 万行语料实测各约 1.1s，端点 3.3s）；三次的 WHERE 只差 archive_status 一个等值。
     * 现在分桶交给库做一次，Java 侧只搬运；配合
     * {@code idx_t_del_comp_status(tenant_id, del_flag, company_id, archive_status, archived_at)}
     * 走覆盖索引，约为原耗时的九分之一。
     *
     * <p>口径保持不变：年度过滤同传则三条都带；未落 archive_status 的历史行只计入总数
     * （与原实现在 archived/pending 两侧都不计入一致）。
     */
    public Map<String, Object> getArchiveStats(Integer year) {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<OpArchive> qw = new QueryWrapper<>();
        qw.select("archive_status", "count(*) as cnt");
        if (year != null) {
            qw.eq("archive_year", year);
        }
        qw.groupBy("archive_status");

        long totalCount = 0L;
        long archivedCount = 0L;
        long pendingCount = 0L;
        for (Map<String, Object> row : archiveCatalogMapper.selectMaps(qw)) {
            if (row == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            long n = cnt == null ? 0L : ((Number) cnt).longValue();
            totalCount += n;
            String st = row.get("archive_status") == null ? "" : String.valueOf(row.get("archive_status"));
            if ("ARCHIVED".equals(st)) {
                archivedCount = n;
            } else if ("PENDING".equals(st)) {
                pendingCount = n;
            }
        }
        stats.put("totalCount", totalCount);
        stats.put("archivedCount", archivedCount);
        stats.put("pendingCount", pendingCount);
        return stats;
    }

    /**
     * <b>两列二源</b>的唯一映射——{@code archive_status}（归档流程轴，真相源）
     * → {@code status}（保管处置轴，冗余镜像）。
     *
     * <p>库里两列的既有名称表（以 {@code fssc_op_archive.sql} 的列注释为准，DDL 不动）：
     * <ul>
     *   <li>{@code archive_status}：PENDING/ARCHIVING/ARCHIVED/DISPOSED，默认 PENDING ——
     *           <b>参与业务判定的那一列</b>：档案列表筛选（{@link #page}／{@link #search}）、
     *           统计（{@link #getArchiveStats}）、到期预警（{@code ArchiveExpireJob:73}）、
     *           单据全链路联查（{@code BillTraceChainController:819}）、执行归档状态机强制校验（{@link #archive}）
     *           全部按它判 ⇒ 定为真相源。</li>
     *   <li>{@code status}：FILED已归档/EXPIRED到期/DESTROYED已销毁，NOT NULL DEFAULT 'FILED' ——
     *           案卷在架/到期/已销毁的<b>保管</b>语义，降级为镜像，取值一律由本方法派生。</li>
     * </ul>
     *
     * <p>两列必须先自洽，否则页面无从判断哪列是结论。{@code BillArchiveService.doArchive}
     * 要同时写真相源 {@code archive_status=ARCHIVED}（与它同事务里 {@code billFacade.markArchived}
     * 的单据最终状态同一规则）和本映射派生的镜像值。只写 {@code status='ARCHIVED'} 会得到
     * 「archive_status=PENDING 而 status=ARCHIVED」这种互斥行——而且 ARCHIVED 根本不在 status 的名称表里，
     * 它写的是另一轴的取值。
     *
     * <p>不删列、不改列注释（DDL 变动另行决策）；历史行的旧值不回写，互斥行只登记不清洗。
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
            // PENDING/ARCHIVING/ARCHIVED 三态案卷都「已登记在架」，保管轴统一 FILED；
            // EXPIRED 留给「到期未销毁」，目前没有写入方（ArchiveExpireJob 只推站内信不改列），本映射不造该值。
            default -> "FILED";
        };
    }

    /**
     * 到期销毁日期的<b>唯一算式</b>（三条立卷写入位置共用一把尺）。
     * 《会计档案管理办法》：保管期限自会计年度终了后的第一天起算 ⇒ {@code (年度 + 保管年限 + 1) 年 1 月 1 日}。
     * 三条写入位置必须共用这一式。人工立卷走年度式、自动归档走 {@code now()+保管年限} 时，
     * 两式对同一卷能差一年以上；单据归档链路若不设值，销毁申请会被
     * 「未设置到期销毁日期，禁止销毁」整批拦死。
     *
     * @param archiveYear 档案年度，取不到退 {@code periodYear}，再取不到退立卷当年
     * @param periodYear 会计年度（目录要素由 {@code fillCatalogFromSourceBill} 补齐，可能晚于本调用落值）
     * @param retentionYears 保管年限，非正数时退默认 10 年
     */
    public static LocalDate destroyDateOf(Integer archiveYear, Integer periodYear, Integer retentionYears) {
        int year = archiveYear != null ? archiveYear
                : (periodYear != null ? periodYear : LocalDate.now().getYear());
        int retention = retentionYears != null && retentionYears > 0 ? retentionYears : 10;
        return LocalDate.of(year + retention + 1, 1, 1);
    }

    /**
     * 立卷入口的<b>目录要素补齐</b>——两条建档路径（{@link #createArchive}
     * 人工立卷／{@link #autoArchive} 自动归档）共用。两处若各写一半（只写 archive_no/type/year/period），
     * {@code ref_no}／{@code title}／{@code period_year}／{@code period_month}／{@code company_id}
     * 就会整列空。
     *
     * <p>取值一律来自<b>来源单据真值</b>（{@code BillArchiveDTO}），取不到即保持 NULL、不造数：
     * <ul>
     *   <li>{@code ref_no}＝单据编号（bill_no），是「档案 ↔ 单据」在人眼层面的联查键；</li>
     *   <li>{@code title}＝单据标题，缺标题退单据编号；
     *           不拿 {@code biz_id}/{@code 档案ID} 拼名——那样出来的「凭证归档-24」谁也看不懂；</li>
     *   <li>{@code period_year}/{@code period_month}＝单据会计期间；单据取不到时按
     *           {@code archive_period}（{@code yyyy-MM}，入口已校验格式）折算，两列从此不可能互相打脸；</li>
     *   <li>{@code archive_year}/{@code archive_period} 缺省同样按单据会计期间回填；</li>
     *   <li>{@code company_id} 缺省取单据归属公司（登录人公司已由 createArchive 先行落值，不覆盖）。</li>
     * </ul>
     *
     * <p>明确<b>不补</b>的一列：{@code file_id}——本域立卷动作没有文件载体（影像迁移在
     * {@code BillArchiveService.doArchive} 那条链路），无载体就不造引用。
     * 该列是否必须非空才能定稿，属产品口径，不在本方法处理。
     */
    private void fillCatalogFromSourceBill(OpArchive archive, BillArchiveDTO src) {
        if (archive == null) {
            return;
        }
        if (src != null) {
            if (isBlank(archive.getRefNo())) {
                archive.setRefNo(src.getBillNo());
            }
            // 标题取单据真名，真名缺位退单据编号——不留「以 ID 拼名」的口子
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
     * 非集中档案模式下纸质原件留原单位 ⇒ 原件保管单位默认落档案归属公司；
     * 集中模式原件已寄送共享中心，两列保持 NULL（不写"共享中心"占位，保管地点属人工登记规则）。
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

    /** 单据/载体两条立卷路径共用的期间回落（archive_period yyyy-MM → period_year/period_month） */
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

    /** 按优先序取第一个非空值（全空返回 null），供卷号/标题这类"多级回落"取用 */
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