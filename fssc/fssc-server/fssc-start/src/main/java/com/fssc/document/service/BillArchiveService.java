package com.fssc.document.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.facade.BillFacade;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.dto.BillArchiveDTO;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 档案归档（需求 6.6 关联：档案调阅/归档）。
 * 闭环：审批完成 + 影像完整的单据进入待归档清单 → 归档执行（关联文件迁移至 archive 存储区
 * + 生成档案记录 + 单据标记 ARCHIVED）→ 已归档档案可查可看影像。
 *
 * 断点2-4 改造：
 * - 移除 ImgFileMapper / ExpBillMapper / ExpBillAttachMapper / FileService / OrgGuard 直接依赖
 * - 通过 ImageFacade 查影像文件 ID 和迁移文件（断点2、断点4）
 * - 通过 BillFacade 查待归档单据和标记归档（断点3）
 * - pendingBills() 返回类型从 PageResult<ExpBill> 改为 PageResult<BillArchiveDTO>
 */
@Slf4j
@Service("billArchiveService")
@RequiredArgsConstructor
public class BillArchiveService {

    private final OpArchiveMapper archiveMapper;
    private final JobHeartbeatService jobHeartbeatService;
    private final NumberRuleService numberRuleService;
    private final BillFacade billFacade;
    private final ImageFacade imageFacade;
    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** D-DEC-315／#56 甲档：归档写腿按 voucher_no 正查凭证ID */
    private final com.fssc.sharing.service.VoucherService voucherService;
    /** D-DEC-180（P1）：完整性哈希的唯一算式（本类原先把主文件裸 MD5 当哈希写库，是第三种算式并存的一支） */
    private final ArchiveIntegrityService archiveIntegrityService;
    /**
     * D-DEC-048（12-16:60）：重复立卷判重的唯一口径在 {@code ArchiveService}（在册/在途卷查询），
     * 本类的影像归档链路复用同一个方法，不再自写第二套谓词（D-DEC-095 的成因就是各写一套）。
     */
    private final ArchiveService archiveService;
    /**
     * V1.4.92：档案非集中管理公司（archive_mode=NON_CENTRAL）不产生实物流，
     * R5-D-244 的「实物未签收不得归档」前置闸对这类公司不适用（模式判定复用 DeliveryService 单一读点）。
     */
    private final com.fssc.sharing.service.DeliveryService deliveryService;

    /** 待归档单据：审批完成（APPROVED/PAID/CLOSED）+ 影像完整（PARTIAL/COMPLETE）；archiveStatus 空＝只看未归档，ARCHIVED＝已归档，ALL＝不限（D-DEC-276b/B1） */
    public PageResult<BillArchiveDTO> pendingBills(long pageNum, long pageSize, String billType, String keyword,
                                                   String archiveStatus) {
        return billFacade.findPendingArchiveBills(pageNum, pageSize, billType, keyword, archiveStatus);
    }

    /** 归档执行：迁移文件到 archive 区 + 建档案记录 + 标记单据，返回归档单据数 */
    @Transactional(rollbackFor = Exception.class)
    public int doArchive(List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择待归档单据");
        }
        Long userId = null;
        try {
            com.fssc.framework.security.LoginUser u = UserContext.get();
            userId = u == null ? null : u.getUserId();
        } catch (Exception ignored) { /* 定时任务无用户上下文 */ }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        // D-DEC-048（12-16:60）：本链路改前只按"单据已 ARCHIVED"幂等，
        // 单据还是 UNARCHIVED 而档案行已存在（电子立卷登记的 PENDING 卷／人工建过目录）时，
        // 再点一次「归档所选」就多落一卷 ⇒ 同一单据两张卷。现按来源单据键查在册/在途卷：
        // 命中即跳过该单据（不整批报错，批次里别的单据照做），并在末尾如实提示原卷号。
        List<String> alreadyFiled = new ArrayList<>();
        // R5-D-244：归档前置闸——实物未签收（delivery_status=UNSIGNED）不得归档，防「签收沦为事后补章」；
        // 不整批报错，批次内其余单据照做，末尾如实提示（存量倒挂行只读清单交人工，NULL=历史未纳管放行）
        List<String> unsignedBills = new ArrayList<>();
        for (Long billId : billIds) {
            BillArchiveDTO bill = billFacade.findBillForArchive(billId);
            if (bill == null) {
                continue;
            }
            if ("ARCHIVED".equals(bill.getArchiveStatus())) {
                continue; // 幂等：已归档跳过
            }
            // V1.4.92：非集中管理公司无实物流，UNSIGNED 不构成阻断（集中公司行为不变）
            if ("UNSIGNED".equals(bill.getDeliveryStatus())
                    && !deliveryService.isNonCentralArchive(bill.getCompanyId())) {
                unsignedBills.add(bill.getBillNo() == null ? ("单据#" + billId) : bill.getBillNo());
                log.warn("归档跳过：单据 {} 实物未签收（delivery_status=UNSIGNED），归档前置未满足", bill.getBillNo());
                continue;
            }
            List<OpArchive> liveVolumes = archiveService.findLiveVolumes(billId, "BILL");
            if (!liveVolumes.isEmpty()) {
                StringBuilder nos = new StringBuilder(bill.getBillNo() == null ? ("单据#" + billId) : bill.getBillNo()).append("→");
                for (int i = 0; i < liveVolumes.size(); i++) {
                    OpArchive v = liveVolumes.get(i);
                    if (i > 0) {
                        nos.append('/');
                    }
                    nos.append(v.getArchiveNo() == null ? ("档案#" + v.getId()) : v.getArchiveNo());
                }
                alreadyFiled.add(nos.toString());
                log.warn("归档跳过：单据 {} 已存在在册/在途档案卷 {} 张，不重复立卷", bill.getBillNo(), liveVolumes.size());
                continue;
            }
            // 1) 收集关联文件（单据附件 + 影像页）—— 断点2：通过 Facade 查询
            Set<Long> fileIds = new LinkedHashSet<>();
            billFacade.getAttachFileIdsByBillId(billId).forEach(fileIds::add);
            imageFacade.getFileIdsByBillId(billId).forEach(fileIds::add);
            // 2) 迁移文件到 archive 区 —— 断点4：通过 ImageFacade 迁移
            List<SysFile> moved = new ArrayList<>();
            for (Long fileId : fileIds) {
                try {
                    SysFile m = imageFacade.moveFileToArchive(fileId);
                    if (m != null) {
                        moved.add(m);
                    }
                } catch (Exception e) {
                    log.warn("归档迁移文件失败 fileId={} billId={}", fileId, billId, e);
                }
            }
            // 3) 生成档案记录
            OpArchive a = new OpArchive();
            a.setArchiveNo(nextArchiveNo());
            // D-DEC-184（12-31:162）：目录要素补全。本链路是"有影像的单据"归档主路，
            // 改前 archive_code／archive_year／archive_period 三列从不写（全库该类型行恒 NULL），
            // 且只把 moved.get(0) 记进 file_id、其余迁移件无处登记 ⇒ 完整性清单看不到全貌（D-DEC-180）。
            a.setArchiveCode(a.getArchiveNo());                       // 列注释＝"档案编码(冗余)"⇒ 编号镜像
            a.setArchiveType("BILL");
            a.setRefId(billId);
            a.setRefNo(bill.getBillNo());
            a.setTitle(bill.getTitle() == null ? bill.getBillNo() : bill.getTitle());
            a.setCompanyId(bill.getCompanyId());
            a.setProcInstId(bill.getProcInstId());
            a.setVoucherId(voucherService.findVoucherIdForArchive(billId, bill.getVoucherNo(), bill.getTenantId()));
            a.setPeriodYear(bill.getPeriodYear());
            a.setPeriodMonth(bill.getPeriodMonth());
            a.setArchiveYear(bill.getPeriodYear());
            if (bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
                a.setArchivePeriod(String.format("%04d-%02d", bill.getPeriodYear(), bill.getPeriodMonth()));
            }
            a.setFileId(moved.isEmpty() ? null : moved.get(0).getId());
            a.setRelatedIds(movedFileIdList(moved));
            // 完整性校验：D-DEC-180——改前此处写的是 moved.get(0).getFileMd5()（主文件裸 MD5），
            // 既不含档案身份（两卷若首件相同即同哈希＝漏报），也永远无法被读侧按任何一套算式复算（＝误报）。
            // 现：状态口径不变（有载体记 VERIFIED、无载体 SKIP），哈希值改在 insert 之后按唯一算式落。
            if (!moved.isEmpty() && moved.get(0).getFileMd5() != null) {
                a.setIntegrityStatus("VERIFIED");
            } else {
                a.setIntegrityStatus("SKIP");
            }
            a.setFilingUserId(userId);
            a.setFilingTime(now);
            a.setRetentionYears(10);
            // 本链路改前从不设 destroy_date ⇒ 立进来的卷全部 NULL，销毁申请被
            // 「未设置到期销毁日期，禁止销毁」硬闸整批拦死；现与两条立卷入口共用一把尺。
            a.setDestroyDate(ArchiveService.destroyDateOf(a.getArchiveYear(),
                    bill.getPeriodYear(), a.getRetentionYears()));
            // D-DEC-182（12-31:160）：本方法过去只写 status='ARCHIVED'、从不写 archive_status
            // ⇒ 库里该列落到默认 PENDING，两列互斥（实测 id=4/10 与证据 id=21/22/23）；
            // 且 'ARCHIVED' 本身不在 status 的词表（FILED/EXPIRED/DESTROYED）里。
            // 现按"archive_status＝真相源、status＝同源镜像"落两列：
            // 归档结论＝ARCHIVED（本链路在同事务里 billFacade.markArchived 把单据也定成 ARCHIVED，两轴一致），
            // 保管轴＝FILED（案卷已登记在架）。
            a.setArchiveStatus("ARCHIVED");
            a.setStatus(ArchiveService.dispositionOf(a.getArchiveStatus()));
            a.setCreateTime(now);
            a.setUpdateTime(now);
            // V1.4.92（AMC-05）：非集中模式原件留原单位 ⇒ 补「原件保管单位」；集中模式保持 NULL
            archiveService.applyPaperKeeperDefault(a);
            archiveMapper.insert(a);
            // D-DEC-180：档案主键入库后才算快照（id 是可区分要素），回写 integrity_hash
            String integrityHash = archiveIntegrityService.snapshot(a);
            OpArchive hashUpd = new OpArchive();
            hashUpd.setId(a.getId());
            hashUpd.setIntegrityHash(integrityHash);
            archiveMapper.updateById(hashUpd);
            a.setIntegrityHash(integrityHash);
            // 4) 标记单据已归档 —— 断点3：通过 BillFacade 标记
            billFacade.markArchived(billId, userId, now);
            // P4-3 审计留痕：单据归档状态跃迁 → ARCHIVED
            auditTrailService.recordUpdate("ARCHIVE", billId, bill.getBillNo(), "exp_bill",
                    String.valueOf(billId),
                    java.util.Collections.singletonMap("archiveStatus", bill.getArchiveStatus()),
                    java.util.Collections.singletonMap("archiveStatus", "ARCHIVED"),
                    "归档执行（档案 " + a.getArchiveNo() + "）");
            count++;
        }
        // D-DEC-048（12-16:60）：整批都在"已立卷"上被跳过时，界面原来只显「归档完成 0 张」——
        // 零拒绝理由、零原卷号，人无从判断是没点中还是已经立过。此时按拒绝处理并报出原卷号；
        // 本方法一张都没写（count==0），抛异常不牵连任何已发生的归档动作（定时链路的整批语义因此不受损）。
        if (count == 0 && !alreadyFiled.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "所选单据均已立卷，不得重复立卷；原档案卷号：" + String.join("、", alreadyFiled)
                            + "。如需重新立卷，请先处置原卷（删除登记或执行销毁）");
        }
        // R5-D-244：整批都因"实物未签收"被跳过时同样给出可行动理由（此前只显「归档完成 0 张」）
        if (count == 0 && alreadyFiled.isEmpty() && !unsignedBills.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "所选单据实物均未签收（delivery_status=UNSIGNED），归档前置未满足；请先完成实物签收。未签收单据："
                            + String.join("、", unsignedBills));
        }
        log.info("档案归档完成：{} 单，已立卷跳过 {} 单，实物未签收跳过 {} 单", count, alreadyFiled.size(), unsignedBills.size());
        return count;
    }

    /**
     * 定时自动归档：通过 BillFacade 查询待归档单据，按租户上下文批量执行。
     * 每天凌晨 2:00（人工归档按钮仍保留，处理特殊情况）。
     *
     * 断点3 改造：不再直接查询 ExpBill 表，改为调用 BillFacade.findPendingBillsForScheduledArchive()。
     * BillTerminalEvent 发布端就绪后，可改为事件驱动收集，进一步去除主动查询。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledArchive() {
        jobHeartbeatService.run("ArchiveService", () -> {
        try {
            log.info("定时自动归档开始");
            List<BillArchiveDTO> pending = billFacade.findPendingBillsForScheduledArchive(200);
            if (pending.isEmpty()) {
                log.info("定时自动归档：无待归档单据");
                return;
            }
            Map<Long, List<Long>> byTenant = pending.stream().collect(Collectors.groupingBy(
                    BillArchiveDTO::getTenantId, Collectors.mapping(BillArchiveDTO::getId, Collectors.toList())));
            byTenant.forEach((tid, ids) -> {
                try {
                    TenantContext.callAs(tid, () -> doArchive(ids));
                    log.info("定时归档租户 {}：{} 单", tid, ids.size());
                } catch (Exception e) {
                    log.error("定时归档失败 tenantId={} ids={}", tid, ids, e);
                }
            });
        } catch (Exception e) {
            log.error("定时自动归档异常", e);
        }
        });
    }

    /**
     * 电子归档<b>立卷</b>（D-DEC-094／12-22:112 修后语义）：无纸质件的电子单据
     * （报销/付款/结算等 exp_bill 单据，影像状态 NONE/空）在审批通过（终态 APPROVED/PAID/CLOSED）后，
     * 由 {@code BillTerminalEventListener} 调用本方法<b>登记档案目录</b>（写入既有 op_archive 表：
     * 单据 + 凭证号 + 附件清单），使 /archive 档案管理页面可检索到该电子档案。
     *
     * <p><b>本方法不再置任何归档终态</b>。原实现在审批通过的同一秒把
     * {@code op_archive.archive_status/status} 写成 ARCHIVED、写 archived_at，并回写
     * {@code exp_bill.archive_status=ARCHIVED + archive_time + archive_by（取末节点审批人）}，
     * 而 {@code wf_instance} 里 biz_type=FM_ARCHIVE 无对应实例（实测 op_archive id=16 vs id=14/15）——
     * 即"归档"这个控制点对 PAY/SETTLE 域恒为自动通过，档案完整性（附件、影像）无人复核即成立。
     *
     * <p>修法（保守半，两条产品口径并列待裁，见 §报告）：
     * <ul>
     *   <li>A 案（<b>本实现</b>）：审批通过仅登记为<b>待归档</b>（{@code archive_status='PENDING'}，
     *       op_archive 既有取值，前端 archStatusLabel 已映射「待归档」），单据侧保持 UNARCHIVED，
     *       由归档岗在档案板点「执行归档」→ {@code ArchiveService.archive} 发起 FM_ARCHIVE 三节点。</li>
     *   <li>B 案：审批通过即自动 {@code createArchive + archive} 发起 FM_ARCHIVE 流程（链路同 A，
     *       只是把人工点击换成系统触发）。需归档岗人力与自审守卫（D-DEC-20：FM_ARCHIVIST 全租户仅 1 人，
     *       自动发起极易造出"提交人＝唯一审批人"的死锁实例）⇒ 未采。</li>
     * </ul>
     *
     * <p>与影像归档链路（{@link #doArchive}，需影像 PARTIAL/COMPLETE）互不影响；有影像的单据仍走原链路。
     * <p>幂等：单据已 ARCHIVED、或该单已存在 bill-scoped 的<b>在册/在途</b>档案目录行时跳过
     * （终态事件可能多次发布；判重口径与三条立卷入口共用，见 {@code ArchiveService.findLiveVolumes}，D-DEC-048）。
     * <p>REQUIRES_NEW：调用方为 AFTER_COMMIT 终态事件监听，原事务已提交，
     * 新开独立事务确保归档数据真实落库（不挂在已完成的连接上）。
     *
     * @return 1=已登记待归档档案；0=跳过（不存在/已归档/已立卷）
     */
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int eArchiveBill(Long billId) {
        if (billId == null) {
            return 0;
        }
        BillArchiveDTO bill = billFacade.findBillForArchive(billId);
        if (bill == null) {
            return 0;
        }
        // 幂等1：单据已归档（可能是人工/定时链路先到）
        if ("ARCHIVED".equals(bill.getArchiveStatus())) {
            return 0;
        }
        // R5-D-244：归档前置闸与 doArchive 同一把尺——实物未签收（UNSIGNED）时不得登记待归档，
        // 防「签收沦为事后补章」；事件语义按跳过并记日志处理（不抛异常），NULL=历史未纳管放行。
        // V1.4.92：非集中管理公司无实物流，UNSIGNED 不构成阻断（与 doArchive 同一把尺）
        if ("UNSIGNED".equals(bill.getDeliveryStatus())
                && !deliveryService.isNonCentralArchive(bill.getCompanyId())) {
            log.info("电子归档跳过: 单据 {} 实物未签收（delivery_status=UNSIGNED），归档前置未满足", bill.getBillNo());
            return 0;
        }
        // 仅电子单据（无纸质影像）走本链路；有影像的单据交还原影像归档链路
        String imageStatus = bill.getImageStatus();
        if ("PARTIAL".equals(imageStatus) || "COMPLETE".equals(imageStatus)) {
            log.info("电子归档跳过: 单据 {} 存在影像（{}），走影像归档链路", bill.getBillNo(), imageStatus);
            return 0;
        }
        // 幂等2：同一单据已有<b>在册/在途</b>档案目录行（BILL/EXPENSE/PAYMENT 三类 ref 均指向本单据，
        // 含历史人工立卷）→ 不再重复登记，也绝不代写单据归档标记（D-DEC-094：归档终态只能由审批闭环产生）。
        // 原实现这里调 markBillArchived() 把单据"补齐"成 ARCHIVED，正是"没走审批流的自动归档才回写"
        // 那半边反向逻辑（D-DEC-095），已随本条一并摘除。
        // D-DEC-048：判重改走 ArchiveService.findLiveVolumes 单点口径（与 createArchive/autoArchive/本类
        // doArchive 同一把尺；此处按"跳过并记日志"的事件语义处理，不抛异常——终态事件重试不该被硬闸打断）。
        List<OpArchive> existingVolumes = archiveService.findLiveVolumes(billId, "BILL");
        if (!existingVolumes.isEmpty()) {
            log.info("电子归档跳过: 单据 {} 已存在在册/在途档案目录记录 {} 行（{}），待归档登记不重复落，归档终态由 FM_ARCHIVE 审批闭环回写",
                    bill.getBillNo(), existingVolumes.size(),
                    existingVolumes.stream().map(v -> v.getArchiveNo() == null ? ("档案#" + v.getId()) : v.getArchiveNo())
                            .collect(java.util.stream.Collectors.joining("、")));
            return 0;
        }
        Long userId = null;
        try {
            com.fssc.framework.security.LoginUser u = UserContext.get();
            userId = u == null ? null : u.getUserId();
        } catch (Exception ignored) { /* 定时/事件上下文无用户 */ }
        LocalDateTime now = LocalDateTime.now();
        // 附件清单（电子附件原地保留，不做影像区迁移——无纸件无影像迁移语义）
        java.util.List<Long> attachFileIds = billFacade.getAttachFileIdsByBillId(billId);
        String relatedIds = attachFileIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        OpArchive a = new OpArchive();
        a.setTenantId(TenantContext.getTenantId());
        a.setArchiveNo(nextArchiveNo());
        // D-DEC-184：档案编码＝编号镜像；档案年度/档案期间按单据会计期间落，
        // 不再出现「archive_period 有值而 period_year/month 空」或反之的同页二源
        a.setArchiveCode(a.getArchiveNo());
        a.setArchiveType("BILL");
        a.setRefId(billId);
        a.setBillId(billId);
        a.setRefNo(bill.getBillNo());
        a.setTitle(bill.getTitle() == null ? bill.getBillNo() : bill.getTitle());
        a.setCompanyId(bill.getCompanyId());
        a.setProcInstId(bill.getProcInstId());
        a.setVoucherId(voucherService.findVoucherIdForArchive(billId, bill.getVoucherNo(), a.getTenantId()));
        a.setPeriodYear(bill.getPeriodYear());
        a.setPeriodMonth(bill.getPeriodMonth());
        a.setArchiveYear(bill.getPeriodYear());
        if (bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            a.setArchivePeriod(String.format("%04d-%02d", bill.getPeriodYear(), bill.getPeriodMonth()));
        }
        a.setRelatedIds(relatedIds.isEmpty() ? null : relatedIds);
        a.setFilingUserId(userId);
        a.setFilingTime(now);
        a.setRetentionYears(10);
        // D-DEC-094：审批通过只到「待归档」——archive_status=PENDING（既有取值），
        // 不写 archived_at（终态时间由 FM_ARCHIVE 闭环写），
        // D-DEC-182：status 不再"留空走库默认"，与真相源同源回写（PENDING 在保管轴上仍是 FILED，取值不变）；
        // 也不再回写 exp_bill.archive_status（单据在待归档清单里依旧可见，等审批闭环）。
        a.setArchiveStatus("PENDING");
        a.setStatus(ArchiveService.dispositionOf(a.getArchiveStatus()));
        a.setIntegrityStatus("SKIP");
        String voucherInfo = bill.getVoucherNo() == null || bill.getVoucherNo().isBlank()
                ? "无凭证" : ("凭证号 " + bill.getVoucherNo());
        a.setRemark("电子归档立卷（无纸质件，审批通过自动登记为待归档，须执行归档经 FM_ARCHIVE 审批；附件 "
                + attachFileIds.size() + " 个；" + voucherInfo + "）");
        // V1.4.92（AMC-05）：电子单据也可能属于非集中公司（无影像但原件在单位）⇒ 同口径补保管单位
        archiveService.applyPaperKeeperDefault(a);
        archiveMapper.insert(a);
        log.info("电子归档立卷: 单据 {} → 档案 {}（附件 {} 个，{}）→ 待归档 PENDING，未置归档终态",
                bill.getBillNo(), a.getArchiveNo(), attachFileIds.size(), voucherInfo);
        // P4-3 审计留痕：档案目录登记（原此处记的是 exp_bill archive_status → ARCHIVED 的单据跃迁，
        // 本方法已不再改单据列，改记档案行的登记动作，避免留下未发生的跃迁）
        auditTrailService.recordUpdate("ARCHIVE", billId, bill.getBillNo(), "op_archive",
                String.valueOf(a.getId()),
                java.util.Collections.emptyMap(),
                java.util.Collections.singletonMap("archiveStatus", "PENDING"),
                "审批通过自动登记待归档档案 " + a.getArchiveNo() + "（归档终态待 FM_ARCHIVE 审批闭环）");
        return 1;
    }

    /*
     * D-DEC-094：原 private markBillArchived(billId)（回写 exp_bill archive_status=ARCHIVED + archive_time +
     * archive_by）只有 eArchiveBill 在用，现随该前置/代偿写一并移除；
     * 影像归档链路 doArchive 仍按批次口径直接调 billFacade.markArchived(billId, userId, now)，
     * 审批闭环侧的回写单点是 ArchiveFacadeImpl.handleArchiveApproval。
     */

    private String nextArchiveNo() {
        try {
            return numberRuleService.next("ARCHIVE");
        } catch (Exception e) {
            return "AR" + System.currentTimeMillis();
        }
    }

    /**
     * D-DEC-184／D-DEC-180：把本卷迁移进归档区的全部载体登记到 {@code related_ids}（CSV，列宽 1000）。
     *
     * <p>改前只把 {@code moved.get(0)} 记进 file_id，其余迁移件在档案上无踪可寻
     * ⇒ 完整性清单（按载体 MD5 逐件入算）只能覆盖首件，"换掉第二页"这类改动测不出来。
     * 超出列宽时按已收集顺序截断（不静默丢：记 warn）；无迁移件返回 null＝不造空串占位。
     */
    static String movedFileIdList(java.util.List<SysFile> moved) {
        if (moved == null || moved.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (SysFile f : moved) {
            if (f == null || f.getId() == null) {
                continue;
            }
            String piece = (sb.length() == 0 ? "" : ",") + f.getId();
            if (sb.length() + piece.length() > 1000) {
                log.warn("档案载体清单超出 related_ids 列宽（1000），已截断：已记 {} 件/共 {} 件",
                        sb.toString().split(",").length, moved.size());
                break;
            }
            sb.append(piece);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
