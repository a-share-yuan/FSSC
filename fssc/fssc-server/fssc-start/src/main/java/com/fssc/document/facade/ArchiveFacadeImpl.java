// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.ArchiveAccessLog;
import com.fssc.document.entity.ArchiveDestroyApproval;
import com.fssc.document.mapper.ArchiveAccessLogMapper;
import com.fssc.document.mapper.ArchiveDestroyApprovalMapper;
import com.fssc.document.service.ArchiveDestroyService;
import com.fssc.document.service.ArchiveService;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.framework.facade.BillFacade;
import com.fssc.framework.facade.dto.ArchiveInfoDTO;
import com.fssc.framework.facade.dto.ArchiveRegisterDTO;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ArchiveFacade 实现：档案域内部直接访问 OpArchiveMapper 及 compliance 档案 Mapper，
 * 对外只暴露接口方法，实现依赖反转。
 *
 * 档案中心拆分后，本实现类与三张表一起迁出，调用方零改动。
 *
 * <p>类级路由＝档案域路由键 {@code document}（独立库就绪前别名 master）：本类直接打
 * {@code op_archive}／{@code archive_destroy_approval}／{@code archive_access_log} 的方法都归档案域。
 * 跨域协作者各自带路由，不依赖本类的 ThreadLocal：{@code ArchiveService#enrichFlowKey}（wf_instance）、
 * {@code AuditTrailService}（audit_trail）、{@code BillFacadeImpl}（exp_bill）均已标注 master。
 * 切独立库前仍需按迁移清单逐项复核（见 {@code docker/README-document-invoice-split.md}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveFacadeImpl implements ArchiveFacade {

    private final OpArchiveMapper opArchiveMapper;
    private final ArchiveDestroyApprovalMapper destroyApprovalMapper;
    private final ArchiveAccessLogMapper accessLogMapper;
    private final BillFacade billFacade;
    /** W1（D-DEC-312）：撤 FM_ARCHIVE Task_3 人工复核后，归档完整性判据由档案服务给出逐条未通过清单 */
    private final com.fssc.document.service.ArchiveService archiveService;
    /** D-DEC-180（P1）：完整性快照哈希的唯一算式（回单登记卷此前从不封哈希 ⇒ 永不可校验） */
    private final com.fssc.document.service.ArchiveIntegrityService archiveIntegrityService;
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** R5-D-197：销毁唯一合规链入口（到期校验＋双人复核双签＋FM_DESTROY 起流程） */
    private final ArchiveDestroyService archiveDestroyService;

    @Override
    public ArchiveInfoDTO findLatestByRefId(Long refId) {
        // D-DEC-005：ref_id 在本接口文档里＝「关联单据 ID」⇒ 只取单据语义类型的卷，防同号合同/回单卷冒充（断点5 调用方传的是 billId）
        OpArchive archive = opArchiveMapper.selectOne(new LambdaQueryWrapper<OpArchive>()
                .eq(OpArchive::getRefId, refId)
                .in(OpArchive::getArchiveType, com.fssc.document.service.ArchiveService.BILL_SCOPED_ARCHIVE_TYPES)
                .orderByDesc(OpArchive::getId)
                .last("LIMIT 1"));
        if (archive == null) {
            return null;
        }
        // D-DEC-185：跨域档案读出点也挂 028 的读侧补充（改前只有 /page 与 /list 有，
        // 工作台/联查走的是本 Facade ⇒ 同一卷在两个界面上"一个有归档流号、一个没有"）
        archiveService.enrichFlowKey(List.of(archive));
        return toDTO(archive);
    }

    @Override
    public List<ArchiveInfoDTO> findByBillId(Long billId) {
        if (billId == null) {
            return List.of();
        }
        List<OpArchive> rows = opArchiveMapper.selectList(new LambdaQueryWrapper<OpArchive>()
                // D-DEC-005：bill_id 臂改前不分类型匹配 ⇒ 合同/回单卷的 bill_id 与单据主键同号即被联查成"这张单据的档案"
                .and(w -> w.and(x -> x.eq(OpArchive::getBillId, billId)
                                .in(OpArchive::getArchiveType,
                                        com.fssc.document.service.ArchiveService.BILL_SCOPED_ARCHIVE_TYPES))
                        .or(x -> x.eq(OpArchive::getRefId, billId).eq(OpArchive::getArchiveType, "BILL")))
                .orderByAsc(OpArchive::getId));
        archiveService.enrichFlowKey(rows);   // D-DEC-185
        return rows.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArchiveInfoDTO> findByProcInstIds(java.util.Collection<String> procInstIds) {
        List<String> ids = procInstIds == null ? List.of() : procInstIds.stream()
                .filter(x -> x != null && !x.isBlank()).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<OpArchive> rows = opArchiveMapper.selectList(new LambdaQueryWrapper<OpArchive>()
                .in(OpArchive::getProcInstId, ids)
                .orderByAsc(OpArchive::getId));
        // D-DEC-185：这里查的是「单据流实例 → 档案」正向键（proc_inst_id 的 D-TRC-05 语义，不翻转）；
        // 顺带把每卷自己的归档流实例号也补充出来，正向/反向两个方向在同一次返回里都给全。
        archiveService.enrichFlowKey(rows);
        return rows.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ArchiveInfoDTO findById(Long archiveId) {
        if (archiveId == null) {
            return null;
        }
        OpArchive archive = opArchiveMapper.selectById(archiveId);
        if (archive == null) {
            return null;
        }
        archiveService.enrichFlowKey(List.of(archive));   // D-DEC-185
        return toDTO(archive);
    }

    @Override
    public Long registerBankSlipArchive(ArchiveRegisterDTO dto) {
        OpArchive ar = new OpArchive();
        ar.setTenantId(TenantContext.getTenantId());
        ar.setArchiveNo(dto.getArchiveNo());
        // D-DEC-184：档案编码＝编号镜像（列注释"档案编码(冗余)"），回单登记路径同一规则补上；
        // 该 DTO 无年度/期间入参 ⇒ archive_year/archive_period 仍为空，不凭默认值造期间（184 后半属载体缺位，已登记）。
        ar.setArchiveCode(dto.getArchiveNo());
        // D-DEC-005：登记簿的类型名称表只有一处权威（ArchiveService.ARCHIVE_TYPES），回单登记入口同样过校验
        ar.setArchiveType(com.fssc.document.service.ArchiveService.assertArchiveType(dto.getArchiveType()));
        ar.setRefId(dto.getRefId());
        // R5-D-210：登记带公司即写入数据库（档案读面按公司数据域办结的查询前提）；
        // 来源未带公司时保持 NULL（不虚构归属），由来源域补齐后随登记链路写入。
        ar.setCompanyId(dto.getCompanyId());
        // proc_inst_id 有意留空：银行回单挂的是流水/回单（refId），非单据审批链，无来源流程实例可回溯（D-TRC-05）
        // D-DEC-005：bill_id 只在单据语义的类型集内才有意义 ⇒ 回单等非单据载体不再写它（来源仍由 ref_id+archive_type 承载）
        ar.setBillId(com.fssc.document.service.ArchiveService.isBillScopedType(dto.getArchiveType())
                ? dto.getBillId() : null);
        ar.setRefNo(dto.getRefNo());
        ar.setTitle(dto.getTitle());
        ar.setFileId(dto.getFileId());
        ar.setFilingUserId(dto.getFilingUserId());
        ar.setFilingTime(LocalDateTime.now());
        ar.setArchiveStatus(dto.getArchiveStatus());
        // D-DEC-182：镜像列不再由调用方随手传值，一律由真相源 archive_status 派生
        // （银行回单登记本来就传 archiveStatus=ARCHIVED + status=FILED，两者同式；此处只是把规则钉死在一处）。
        ar.setStatus(com.fssc.document.service.ArchiveService.dispositionOf(dto.getArchiveStatus()));
        ar.setIntegrityStatus(dto.getIntegrityStatus());
        ar.setRemark(dto.getRemark());
        opArchiveMapper.insert(ar);
        // D-DEC-180（P1）：回单卷此前<b>从不封哈希</b>（integrity_hash 恒 NULL）⇒ "档案是否被改动"这一问
        // 对该类档案永远无从回答。主键入库后按唯一算式封存快照；哈希已算≠已校验（#20 规则），
        // 状态仍按调用方传入的 UNKNOWN，由校验/审批闭环定 VERIFIED。
        String seal = archiveIntegrityService.snapshot(ar);
        OpArchive hashUpd = new OpArchive();
        hashUpd.setId(ar.getId());
        hashUpd.setIntegrityHash(seal);
        opArchiveMapper.updateById(hashUpd);
        ar.setIntegrityHash(seal);
        return ar.getId();
    }

    @Override
    public void handleArchiveApproval(Long archiveId, boolean approved) {
        OpArchive archive = opArchiveMapper.selectById(archiveId);
        if (archive == null) {
            log.warn("档案收尾: 不存在的档案 {}", archiveId);
            return;
        }
        if (!approved) {
            // D-DEC-181（12-31:159）"驳回无回退路径"：执行归档已把档案推到 ARCHIVING（归档中），
            // 审批驳回后不得停在途态——回退到 PENDING（待归档）。取的是 op_archive.archive_status
            // 既有取值（fssc_op_archive.sql L51 PENDING/ARCHIVING/ARCHIVED/DISPOSED），前端 archStatusLabel
            // 亦已把 PENDING 显示为「待归档」，不新造状态。
            // 源单据侧无需回退：D-DEC-027 修后"执行归档"不再前置写 exp_bill.archive_status，
            // 审批在途期间单据一直是 UNARCHIVED。历史行若被旧版前置写成 ARCHIVED 也不在此反向解绑——
            // 反归档（unarchive）全仓未实现（仅注释提及），解绑属产品裁决。
            OpArchive rollback = new OpArchive();
            rollback.setId(archiveId);
            rollback.setArchiveStatus("PENDING");
            opArchiveMapper.updateById(rollback);
            auditTrailService.recordUpdate("FM_ARCHIVE", archiveId, archive.getArchiveNo(), "op_archive",
                    String.valueOf(archiveId),
                    java.util.Collections.singletonMap("archiveStatus", archive.getArchiveStatus()),
                    java.util.Collections.singletonMap("archiveStatus", "PENDING"),
                    "归档审批驳回：在途态回退为待归档（PENDING），可整改后重新执行归档");
            log.info("档案 {} 审批驳回 → archive_status {} → PENDING（待归档）",
                    archive.getArchiveNo(), archive.getArchiveStatus());
            return;
        }
        // W1（D-DEC-312）：撤掉 Task_3 人工复核后，定稿前必须过归档完整性判据。
        // 未通过＝不静默定稿、逐条写进记录（清单形态，非布尔）交人工处理；归档人/归档时间在立卷时已固化，不随节点撤除而丢失。
        java.util.List<String> failures = archiveService.preArchiveFailures(archiveId);
        if (!failures.isEmpty()) {
            OpArchive flagged = new OpArchive();
            flagged.setId(archiveId);
            flagged.setIntegrityStatus("MISMATCH");
            opArchiveMapper.updateById(flagged);
            java.util.Map<String, Object> statusOnly = new java.util.LinkedHashMap<>();
            statusOnly.put("status", archive.getStatus());
            auditTrailService.recordUpdate("FM_ARCHIVE", archiveId, archive.getArchiveNo(), "op_archive",
                    String.valueOf(archiveId), statusOnly, statusOnly,
                    "归档完整性检查未通过，未自动定稿：" + String.join("；", failures));
            log.warn("档案 {} 归档完整性检查未通过（{} 项），保持 {} 不定稿：{}",
                    archive.getArchiveNo(), failures.size(), archive.getStatus(), failures);
            return;
        }
        // 归档人：审批收尾通常有登录上下文，取不到时保持 NULL，不造假人。
        // 同一 operator 既用于档案侧「上架登记字段」补写，也用于单据侧 archive_by，两端规则一致。
        Long operator = null;
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.get();
        if (u != null) {
            operator = u.getUserId();
        }
        LocalDateTime finalizedAt = LocalDateTime.now();
        // D-DEC-181／D-DEC-027：最终状态三件套（archive_status=ARCHIVED + archived_at + integrity_status=VERIFIED）
        // 只在审批闭环这一处写。原先由"执行归档"前置写完（op_archive id=24：12:46:39 即 ARCHIVED＋VERIFIED，
        // 三节审批后只有 update_time 变化），现执行归档只进 ARCHIVING，本处定稿；
        // 完整性判据刚通过（哈希重算与归档快照一致）才置 VERIFIED——"已算哈希"不等于"已校验"。
        // D-DEC-182（12-31:160）：改前两列都写 'ARCHIVED'，而 'ARCHIVED' 不在 status 的名称表
        // （FILED/EXPIRED/DESTROYED）里 —— 现 archive_status＝真相源落 ARCHIVED，
        // status＝同源镜像按 ArchiveService.dispositionOf 派生（落 FILED＝案卷已登记在架）。
        OpArchive upd = new OpArchive();
        upd.setId(archiveId);
        upd.setArchiveStatus("ARCHIVED");
        upd.setStatus(com.fssc.document.service.ArchiveService.dispositionOf("ARCHIVED"));
        upd.setArchivedAt(finalizedAt);
        upd.setIntegrityStatus("VERIFIED");
        // D-DEC-021 第二半（12-08:69「filing_user_id/filing_time 审批通过后仍 NULL」）：
        // 立卷留人那半条已由 D-DEC-276a 落在 ArchiveService.createArchive L163-164（jar 内），本处补的是
        // 收尾默认处理——276a 上线前立的历史卷、以及无登录上下文的立卷行，两列至今为空，归档结论无人可追。
        // 仅在 NULL 时补写，已有立卷人／立卷时间一律不覆盖（立卷动作与定稿动作各自记录，不混为一谈）。
        if (archive.getFilingUserId() == null) {
            upd.setFilingUserId(operator);
        }
        if (archive.getFilingTime() == null) {
            upd.setFilingTime(finalizedAt);
        }
        opArchiveMapper.updateById(upd);
        log.info("档案 {} 归档审批通过: → ARCHIVED（定稿人={}）", archive.getArchiveNo(), operator);
        // P0-5 状态机闭环：归档审批通过同步回写源单据归档状态（去重）。
        // exp_bill 用 status(业务态:PAID/...) + archiveStatus(归档态:ARCHIVED) 双字段表达状态机 ARCHIVED 段。
        // D-DEC-278（2026-09-28 已裁 A1）：守卫原先只放行 BILL ⇒ 凭证归档（EXPENSE）审批通过不回写，
        // 单据侧要等次日 02:00 定时任务代写、且代写不留归档人。
        // D-DEC-095（12-22:113）：白名单再按 ref_id 语义补齐为 ArchiveService.BILL_SCOPED_ARCHIVE_TYPES
        // ＝{BILL, EXPENSE, PAYMENT}（付款/结算单本身就在 exp_bill）；VOUCHER/CONTRACT/INVOICE/REPORT/OTHER
        // 与 BANK_SLIP 的 ref_id 属别的域主键，跨域裸主键回写必串号，仍旧不放行。
        // 谓词收拢到 ArchiveService 一处，与（已摘除的）执行归档前置写共用同一把尺，避免两条链路再次分叉。
        Long billId = com.fssc.document.service.ArchiveService.resolveSourceBillId(archive);
        if (billId != null && billFacade.findBillForArchive(billId) != null) {
            try {
                billFacade.markArchived(billId, operator, finalizedAt);
                log.info("档案 {} 回写源单据 {} 归档状态 ARCHIVED（归档人={}）",
                        archive.getArchiveNo(), billId, operator);
            } catch (Exception ex) {
                log.error("档案 {} 回写源单据归档状态失败: {}", archive.getArchiveNo(), ex.getMessage(), ex);
            }
        } else if (com.fssc.document.service.ArchiveService.isBillScopedType(archive.getArchiveType())) {
            log.warn("档案 {} 未回写源单据：bill_id/ref_id 未指向费用单据（类型 {}）",
                    archive.getArchiveNo(), archive.getArchiveType());
        }
    }

    @Override
    public void handleDestroyApproval(Long approvalId, boolean approved) {
        ArchiveDestroyApproval approval = destroyApprovalMapper.selectById(approvalId);
        if (approval == null) {
            log.warn("档案销毁收尾: 不存在的销毁审批单 {}", approvalId);
            return;
        }
        ArchiveDestroyApproval upd = new ArchiveDestroyApproval();
        upd.setId(approvalId);
        upd.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        if (approved) {
            upd.setApprovedAt(LocalDateTime.now());
        }
        destroyApprovalMapper.updateById(upd);
        if (!approved) {
            log.info("档案销毁 {} 审批驳回 → REJECTED", approval.getDestroyCode());
            return;
        }
        // 审批通过：销毁审批单涉及的档案全部置 DESTROYED
        // D-DEC-182 规则说明（现有例外，不改行为）：本处只落<b>保管处置轴</b>的结论（status=DESTROYED），
        // 归档流程轴 archive_status 要等「执行销毁」真正落地才置 DISPOSED
        // （ArchiveDestroyService.executeDestroy 两列同源写）。也就是说"已批准·待执行"这一窗口内
        // 两列本就该一个说还在架、一个说已判销毁——读侧判"是否已销毁"必须两列取或，
        // 现有实现已经是这个规则（ArchiveDestroyService.assertAllExpired:189、
        // SharingEnhanceService.createArchive 的档案号释放判定），故此处维持不扩权。
        List<Long> ids = parseIds(approval.getArchiveIds());
        if (!ids.isEmpty()) {
            opArchiveMapper.update(null, new LambdaUpdateWrapper<OpArchive>()
                    .in(OpArchive::getId, ids)
                    .set(OpArchive::getStatus, "DESTROYED"));
        }
        log.info("档案销毁 {} 审批通过 → APPROVED，{} 个档案 DESTROYED", approval.getDestroyCode(), ids.size());
    }

    @Override
    public void handleBorrowApproval(Long accessLogId, boolean approved) {
        handleBorrowApproval(accessLogId, approved, null);
    }

    /** D-DEC-443：审批收尾补写 approver_id＝终审操作人（引擎线程无登录态，只认事件带回的值，null 不猜）。 */
    @Override
    public void handleBorrowApproval(Long accessLogId, boolean approved, Long endOperUserId) {
        ArchiveAccessLog log0 = accessLogMapper.selectById(accessLogId);
        if (log0 == null) {
            log.warn("档案借阅收尾: 不存在的借阅单 {}", accessLogId);
            return;
        }
        ArchiveAccessLog upd = new ArchiveAccessLog();
        upd.setId(accessLogId);
        upd.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        if (approved) {
            upd.setApprovedAt(LocalDateTime.now());
            if (endOperUserId != null) {
                upd.setApproverId(endOperUserId);
            }
        }
        accessLogMapper.updateById(upd);
        log.info("档案借阅单 {} 审批收尾 → {}（审批人 {}）", accessLogId, upd.getApprovalStatus(), upd.getApproverId());
    }

    // ==================== 域外读取（报表／联查／合规导出） ====================

    @Override
    public List<OpArchive> findAllForExport() {
        return opArchiveMapper.selectList(new LambdaQueryWrapper<OpArchive>()
                .orderByAsc(OpArchive::getId));
    }

    @Override
    public List<OpArchive> findByBillIdOrVoucherIds(Long billId, java.util.Collection<Long> voucherIds) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        if (voucherIds == null || voucherIds.isEmpty()) {
            qw.eq(OpArchive::getBillId, billId);
        } else {
            qw.and(w -> w.eq(OpArchive::getBillId, billId)
                    .or().in(OpArchive::getVoucherId, voucherIds));
        }
        qw.orderByDesc(OpArchive::getCreateTime);
        return opArchiveMapper.selectList(qw);
    }

    // ==================== 域外写入（共享作业页的档案箱） ====================

    @Override
    public PageResult<OpArchive> pageArchives(long pageNum, long pageSize, String archiveType, String status) {
        LambdaQueryWrapper<OpArchive> qw = new LambdaQueryWrapper<>();
        if (archiveType != null && !archiveType.isBlank()) {
            qw.eq(OpArchive::getArchiveType, archiveType);
        }
        if (status != null && !status.isBlank()) {
            // D-DEC-182：入参名沿用前端的 status，但判定列改按真相源 archive_status——
            // 传 ARCHIVED/PENDING/ARCHIVING/DISPOSED 按归档流程轴筛；兼容老调用方传保管轴取值（FILED/DESTROYED）。
            qw.eq(OpArchive::getArchiveStatus, toArchiveStatusAxis(status));
        }
        qw.orderByDesc(OpArchive::getId);
        Page<OpArchive> p = opArchiveMapper.selectPage(Page.of(pageNum, pageSize), qw);
        archiveService.enrichFlowKey(p.getRecords());   // D-DEC-185：档案列表端点统一挂归档流补充
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** D-DEC-182：保管轴取值 → 归档流程轴取值的兼容折算（FILED→ARCHIVED、DESTROYED→DISPOSED，其余原样） */
    private static String toArchiveStatusAxis(String status) {
        String s = status.trim().toUpperCase();
        return switch (s) {
            case "FILED" -> "ARCHIVED";
            case "DESTROYED" -> "DISPOSED";
            default -> s;
        };
    }

    @Override
    public Long createArchive(OpArchive a) {
        if (a.getArchiveNo() == null || a.getArchiveNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案编号必填");
        }
        // 查重：已销毁(DESTROYED)的档案释放档案号（物理清理），其余报重
        List<OpArchive> exist = opArchiveMapper.selectList(new LambdaQueryWrapper<OpArchive>()
                .eq(OpArchive::getArchiveNo, a.getArchiveNo()));
        for (OpArchive e : exist) {
            if (!"DESTROYED".equals(e.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "档案编号已存在: " + a.getArchiveNo());
            }
            opArchiveMapper.hardDelete(e.getId());
        }
        a.setId(null);
        if (a.getStatus() == null) {
            // D-DEC-182：status 是 archive_status 的冗余镜像，取值走档案域同一把尺（ArchiveService.dispositionOf）
            String mirrored = ArchiveService.dispositionOf(a.getArchiveStatus());
            a.setStatus(mirrored != null ? mirrored : "FILED");
        }
        if (a.getFilingTime() == null) {
            a.setFilingTime(LocalDateTime.now());
        }
        opArchiveMapper.insert(a);
        return a.getId();
    }

    @Override
    public void updateArchive(OpArchive a) {
        if (a == null || a.getId() == null || opArchiveMapper.selectById(a.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "档案不存在: " + (a == null ? null : a.getId()));
        }
        opArchiveMapper.updateById(a);
    }

    @Override
    public Long requestDestroy(List<Long> archiveIds, String reason, Long operatorId) {
        return archiveDestroyService.createDestroyRequest(archiveIds, reason, operatorId);
    }

    /** 解析 JSON 数组字符串形式的档案 ID 列表（容错纯 CSV） */
    private List<Long> parseIds(String archiveIds) {
        if (archiveIds == null || archiveIds.isBlank()) {
            return List.of();
        }
        String cleaned = archiveIds.replace("[", "").replace("]", "").replace("\"", "").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toList());
    }

    private ArchiveInfoDTO toDTO(OpArchive a) {
        ArchiveInfoDTO dto = new ArchiveInfoDTO();
        dto.setId(a.getId());
        dto.setArchiveNo(a.getArchiveNo());
        dto.setArchiveCode(a.getArchiveCode());
        dto.setArchiveType(a.getArchiveType());
        dto.setArchiveYear(a.getArchiveYear());
        dto.setArchivePeriod(a.getArchivePeriod());
        dto.setRefId(a.getRefId());
        dto.setBillId(a.getBillId());
        dto.setProcInstId(a.getProcInstId());
        // D-DEC-185：两个方向的实例号都给（源单据流＝procInstId，本卷归档流＝archiveProcInstId／archiveFlowStatus）
        dto.setArchiveProcInstId(a.getArchiveProcInstId());
        dto.setArchiveFlowStatus(a.getArchiveFlowStatus());
        dto.setVoucherId(a.getVoucherId());
        dto.setRelatedIds(a.getRelatedIds());
        dto.setRefNo(a.getRefNo());
        dto.setTitle(a.getTitle());
        dto.setCompanyId(a.getCompanyId());
        dto.setPeriodYear(a.getPeriodYear());
        dto.setPeriodMonth(a.getPeriodMonth());
        dto.setFileId(a.getFileId());
        dto.setFilingUserId(a.getFilingUserId());
        dto.setFilingTime(a.getFilingTime());
        dto.setRetentionYears(a.getRetentionYears());
        dto.setDestroyDate(a.getDestroyDate());
        dto.setIntegrityHash(a.getIntegrityHash());
        dto.setIntegrityStatus(a.getIntegrityStatus());
        dto.setArchiveStatus(a.getArchiveStatus());
        dto.setArchivedAt(a.getArchivedAt());
        dto.setDestroyedAt(a.getDestroyedAt());
        dto.setStatus(a.getStatus());
        dto.setRemark(a.getRemark());
        dto.setCreateTime(a.getCreateTime());
        dto.setUpdateTime(a.getUpdateTime());
        return dto;
    }
}
