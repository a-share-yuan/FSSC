// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.ArchiveDestroyApproval;
import com.fssc.document.mapper.ArchiveDestroyApprovalMapper;
import com.fssc.document.entity.ArchiveAccessLog;
import com.fssc.document.mapper.ArchiveAccessLogMapper;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.framework.datasource.FsscDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveDestroyService {

    private final ArchiveDestroyApprovalMapper archiveDestroyApprovalMapper;
    private final OpArchiveMapper archiveCatalogMapper;
    /** 销毁事务内级联关闭该卷仍挂起的调阅申请（置 REJECTED＋原因） */
    private final ArchiveAccessLogMapper archiveAccessLogMapper;
    private final ObjectMapper objectMapper;
    private final NumberRuleService numberRuleService;
    private final WfEngineFacade wfEngineFacade;
    /** 申请人姓名/工号回查（显示层补充，不改任何历史行） */
    private final SysUserMapper sysUserMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long createDestroyRequest(List<Long> archiveIds, String reason, Long applicantId) {
        // 必填守卫（与前端 ARCHIVE_DESTROY 登记表同一规则）：销毁理由是审批与追溯的唯一依据，
        // 控制器把缺失值兜成空串会写出无意义的申请单。
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写销毁理由");
        }
        // 销毁前强校验：待销毁档案必须全部已到期且在库（未到期禁止进入销毁流程）
        assertAllExpired(archiveIds);
        ArchiveDestroyApproval approval = new ArchiveDestroyApproval();
        approval.setDestroyCode(nextDestroyCode());
        approval.setDestroyReason(reason);
        approval.setApplicantId(applicantId);
        approval.setApprovalStatus("PENDING");
        try {
            approval.setArchiveIds(objectMapper.writeValueAsString(archiveIds));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "档案ID序列化失败");
        }
        archiveDestroyApprovalMapper.insert(approval);

        // 销毁申请发起 FM04 凭证销毁审批流（FM_DESTROY，销毁申请→共享主管复核→财务总监→总裁）
        try {
            wfEngineFacade.start("FM_DESTROY", approval.getId(), userId(),
                    "档案销毁-" + approval.getDestroyCode());
        } catch (Exception ex) {
            log.error("销毁申请 {} 发起审批失败: {}", approval.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
        }

        log.info("创建销毁申请: destroyId={}, archiveIds={}, applicantId={}",
                approval.getId(), archiveIds, applicantId);
        return approval.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 销毁审批（#4 双人复核双签）。
     *
     * <p>档案销毁不可逆且需永久记录，按《会计档案管理办法》要求双人复核：
     * 第一人签署通过 → 状态仍为 PENDING（待第二人复核）；第二人（须为不同人）通过 → APPROVED。
     * 任一人否决 → 直接 REJECTED，流程终止。同一人不能完成双签，防止自签自审。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveDestroy(Long destroyId, Long approverId, boolean approved) {
        ArchiveDestroyApproval approval = archiveDestroyApprovalMapper.selectById(destroyId);
        if (approval == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "销毁申请不存在: " + destroyId);
        }
        if (!"PENDING".equals(approval.getApprovalStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该销毁申请已结束（" + approval.getApprovalStatus() + "），不可再签署: " + destroyId);
        }
        if (approverId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "签署人不能为空");
        }
        if (!approved) {
            // 任一人否决即终止
            approval.setApprovalStatus("REJECTED");
            if (approval.getApproverId() == null) {
                approval.setApproverId(approverId);
            } else {
                approval.setSecondApproverId(approverId);
            }
            approval.setApprovedAt(LocalDateTime.now());
            archiveDestroyApprovalMapper.updateById(approval);
            log.info("销毁申请被否决: destroyId={}, approverId={}", destroyId, approverId);
            return;
        }
        // 通过：第一签 / 第二签
        if (approval.getApproverId() == null) {
            approval.setApproverId(approverId);
            approval.setApprovedAt(LocalDateTime.now());
            log.info("销毁申请第一签完成，待第二人复核: destroyId={}, approverId={}", destroyId, approverId);
        } else if (approverId.equals(approval.getApproverId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "双签要求两名不同复核人，当前签署人与第一审批人相同: " + approverId);
        } else {
            approval.setSecondApproverId(approverId);
            approval.setSecondApprovedAt(LocalDateTime.now());
            approval.setApprovalStatus("APPROVED");
            log.info("销毁申请双签完成: destroyId={}, 第一审批人={}, 第二复核人={}",
                    destroyId, approval.getApproverId(), approverId);
        }
        archiveDestroyApprovalMapper.updateById(approval);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeDestroy(Long destroyId) {
        ArchiveDestroyApproval approval = archiveDestroyApprovalMapper.selectById(destroyId);
        if (approval == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "销毁申请不存在: " + destroyId);
        }
        if (!"APPROVED".equals(approval.getApprovalStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "销毁申请未获批准: " + destroyId);
        }
        if (approval.getSecondApproverId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "销毁须双人复核完成，缺第二复核人: " + destroyId);
        }

        List<Long> archiveIds;
        try {
            archiveIds = objectMapper.readValue(approval.getArchiveIds(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "档案ID反序列化失败");
        }
        // 执行前复校验：审批期间若档案被延期，仍禁止销毁（防"申请时已到期、执行时被延期"绕过）
        assertAllExpired(archiveIds);

        approval.setDestroyedAt(LocalDateTime.now());
        archiveDestroyApprovalMapper.updateById(approval);

        for (Long archiveId : archiveIds) {
            OpArchive archive = archiveCatalogMapper.selectById(archiveId);
            if (archive != null) {
                archive.setArchiveStatus("DISPOSED");
                // 两列由同一处、按同一映射写（本方法就是"同源"的样板，其余写入位置已并入此规则）
                archive.setStatus(ArchiveService.dispositionOf(archive.getArchiveStatus()));
                archive.setDestroyedAt(LocalDateTime.now());
                // 销毁是独立业务最终状态，完整性列写"不适用"专用值——
                // 旧规则硬写不一致值，把"已按制度销毁"与"检出被篡改"两件事混成同一个标记。
                archive.setIntegrityStatus("NOT_APPLICABLE");
                archiveCatalogMapper.updateById(archive);
                // 销毁不可逆 ⇒ 同事务级联关闭该卷仍挂起的调阅申请（REJECTED＋原因），
                // 不让申请悬空等一个已不存在的实体。
                closePendingAccessRequests(archiveId, LocalDateTime.now());
            }
        }
        log.info("执行销毁: destroyId={}, archiveIds={}", destroyId, archiveIds);
    }

    /**
     * 销毁事务的级联关闭环节——该卷 PENDING 调阅一律置 REJECTED，
     * 并在原因列追加级联关闭备注（记录不删行，事后可查"为什么被关"）。
     */
    private void closePendingAccessRequests(Long archiveId, LocalDateTime closedAt) {
        List<ArchiveAccessLog> pendings = archiveAccessLogMapper.selectList(new LambdaQueryWrapper<ArchiveAccessLog>()
                .eq(ArchiveAccessLog::getArchiveId, archiveId)
                .eq(ArchiveAccessLog::getApprovalStatus, "PENDING"));
        for (ArchiveAccessLog al : pendings) {
            al.setApprovalStatus("REJECTED");
            al.setAccessReason((al.getAccessReason() == null || al.getAccessReason().isEmpty() ? "" : al.getAccessReason() + "；")
                    + "档案已销毁，级联关闭");
            al.setApprovedAt(closedAt);
            archiveAccessLogMapper.updateById(al);
        }
        if (!pendings.isEmpty()) {
            log.info("销毁级联关闭调阅申请: archiveId={}, closed={}", archiveId, pendings.size());
        }
    }

    /**
     * 销毁强校验（合规红线）：待销毁档案必须"全部已到期"且仍为在库已归档状态。
     *
     * <p>《会计档案管理办法》规定保管期满方可销毁。executeDestroy 只校验审批状态
     * APPROVED 而不校验 destroyDate，就有"未到期档案被合法销毁"的合规风险；
     * createDestroyRequest 同样无任何到期校验。故申请与执行两处均加本校验，
     * 执行侧复校验另可防"申请时已到期、审批期间被延期"的绕过路径。
     */
    private void assertAllExpired(List<Long> archiveIds) {
        if (archiveIds == null || archiveIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "待销毁档案列表不能为空");
        }
        LocalDate today = LocalDate.now();
        for (Long id : archiveIds) {
            OpArchive a = archiveCatalogMapper.selectById(id);
            if (a == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + id);
            }
            String no = a.getArchiveNo() == null ? String.valueOf(id) : a.getArchiveNo();
            if ("DESTROYED".equals(a.getStatus()) || "DISPOSED".equals(a.getArchiveStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "档案 " + no + " 已销毁，不可重复处理");
            }
            if (a.getDestroyDate() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "档案 " + no + " 未设置到期销毁日期，禁止销毁");
            }
            if (a.getDestroyDate().isAfter(today)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "档案 " + no + " 保管期至 " + a.getDestroyDate() + " 尚未届满，禁止提前销毁");
            }
        }
    }

    public List<ArchiveDestroyApproval> listByStatus(String approvalStatus) {
        LambdaQueryWrapper<ArchiveDestroyApproval> qw = new LambdaQueryWrapper<>();
        if (approvalStatus != null && !approvalStatus.isEmpty()) {
            qw.eq(ArchiveDestroyApproval::getApprovalStatus, approvalStatus);
        }
        qw.orderByDesc(ArchiveDestroyApproval::getCreateTime);
        List<ArchiveDestroyApproval> rows = archiveDestroyApprovalMapper.selectList(qw);
        fillApplicants(rows);
        return rows;
    }

    /** 销毁申请分页（原 listByStatus 保留兼容） */
    public PageResult<ArchiveDestroyApproval> pageByStatus(String approvalStatus, long pageNum, long pageSize) {
        LambdaQueryWrapper<ArchiveDestroyApproval> qw = new LambdaQueryWrapper<>();
        if (approvalStatus != null && !approvalStatus.isEmpty()) {
            qw.eq(ArchiveDestroyApproval::getApprovalStatus, approvalStatus);
        }
        qw.orderByDesc(ArchiveDestroyApproval::getCreateTime);
        Page<ArchiveDestroyApproval> p = archiveDestroyApprovalMapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillApplicants(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public ArchiveDestroyApproval getById(Long id) {
        ArchiveDestroyApproval approval = archiveDestroyApprovalMapper.selectById(id);
        if (approval == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "销毁申请不存在: " + id);
        }
        fillApplicants(List.of(approval));
        return approval;
    }

    /**
     * 申请人姓名/工号回填（与 ArchiveAccessService.fillRequesters 同一规则）：
     * 前端「申请人」列不必拿裸 applicant_id 本地 join，否则显示出来是工号。
     */
    private void fillApplicants(List<ArchiveDestroyApproval> rows) {
        Set<Long> ids = new HashSet<>();
        for (ArchiveDestroyApproval r : rows) {
            if (r.getApplicantId() != null) {
                ids.add(r.getApplicantId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, SysUser> byId = new HashMap<>();
        sysUserMapper.selectBatchIds(ids).forEach(u -> byId.put(u.getId(), u));
        for (ArchiveDestroyApproval r : rows) {
            SysUser u = r.getApplicantId() == null ? null : byId.get(r.getApplicantId());
            if (u == null) {
                continue;
            }
            r.setApplicantUserName(u.getUserName());
            r.setApplicantName(u.getNickName() == null || u.getNickName().isBlank()
                    ? u.getUserName() : u.getNickName());
        }
    }

    private String nextDestroyCode() {
        try {
            return numberRuleService.next("ARCHIVE_DESTROY");
        } catch (Exception e) {
            return "DESTROY-" + System.currentTimeMillis();
        }
    }
}