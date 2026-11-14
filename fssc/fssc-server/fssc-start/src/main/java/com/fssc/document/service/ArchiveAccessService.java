package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.ArchiveAccessLog;
import com.fssc.document.mapper.ArchiveAccessLogMapper;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.OpArchiveMapper;
import com.fssc.framework.security.PermDependency;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.framework.datasource.FsscDataSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveAccessService {

    private final ArchiveAccessLogMapper archiveAccessLogMapper;
    /** 调阅申请前置档案状态守卫（DISPOSED 卷拒绝新申请） */
    private final OpArchiveMapper archiveCatalogMapper;
    private final WfEngineFacade wfEngineFacade;
    /** 申请人姓名/工号回查（显示层补充，不改任何历史行） */
    private final SysUserMapper sysUserMapper;

    /**
     * 调阅类型名称表（· 产品确认）：取**前端可渲染的键集**
     * （ArchiveBoard.vue accessTypeLabel 的 READ/VIEW/COPY），写进去的值必然读得出来。
     * archive_access_log.access_type 的列注释写着 VIEW/DOWNLOAD/PRINT，但现网零行
     * 用过后两者，且前端无对应文案 ⇒ 列注释非权威，不并入白名单（仅登记，DDL 不动）。
     */
    static final Set<String> ACCESS_TYPES = Set.of("READ", "COPY", "VIEW");

    /** 名称表守卫：集外值拒写，不静默清洗成默认值（清洗会把错值变成"看不见的错值"）。历史越词行不回写。 */
    private static String assertAccessType(String accessType) {
        String v = accessType == null ? null : accessType.trim().toUpperCase();
        if (v == null || !ACCESS_TYPES.contains(v)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "调阅类型取值不在词表内: " + accessType + "，可选 " + new TreeSet<>(ACCESS_TYPES));
        }
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long requestAccess(Long archiveId, String accessType, String reason, Long requesterId) {
        // 必填守卫：控制器把缺失的 archiveId 兜成 0L，会写出指向不存在档案的调阅申请，
        // 且 access_reason 为 NOT NULL 时直接 500 —— 在写库前按前端同一规则拦下。
        if (archiveId == null || archiveId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择调阅档案");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写申请理由");
        }
        // 已销毁（DISPOSED）的档案是最终状态、实体已不在 ⇒ 拒绝新调阅申请；
        // 与销毁事务内的级联关闭互为成对：前脚销毁的卷后脚也不能被新申请挂上。
        OpArchive arc = archiveCatalogMapper.selectById(archiveId);
        if (arc == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案不存在: " + archiveId);
        }
        if ("DISPOSED".equals(arc.getArchiveStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "档案已销毁，不可再申请调阅: " + archiveId);
        }
        accessType = assertAccessType(accessType);
        ArchiveAccessLog accessLog = new ArchiveAccessLog();
        accessLog.setArchiveId(archiveId);
        accessLog.setAccessType(accessType);
        accessLog.setAccessReason(reason);
        accessLog.setRequesterId(requesterId);
        accessLog.setApprovalStatus("PENDING");
        archiveAccessLogMapper.insert(accessLog);

        // 调阅申请发起 FM03 凭证借阅审批流（FM_BORROW，借阅申请→共享主管复核→财务经理审批）
        try {
            wfEngineFacade.start("FM_BORROW", accessLog.getId(), userId(),
                    "档案调阅-" + archiveId);
        } catch (Exception ex) {
            log.error("调阅申请 {} 发起借阅审批失败: {}", accessLog.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡：静默降级会留下没有流程的孤儿单
        }

        log.info("申请档案调阅: accessLogId={}, archiveId={}, requesterId={}",
                accessLog.getId(), archiveId, requesterId);
        return accessLog.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveAccess(Long accessLogId, Long approverId, boolean approved) {
        assertApproverCanList();
        ArchiveAccessLog accessLog = archiveAccessLogMapper.selectById(accessLogId);
        if (accessLog == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调阅申请不存在: " + accessLogId);
        }
        accessLog.setApproverId(approverId);
        accessLog.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        accessLog.setApprovedAt(LocalDateTime.now());
        archiveAccessLogMapper.updateById(accessLog);
        log.info("审批调阅申请: accessLogId={}, approverId={}, approved={}",
                accessLogId, approverId, approved);
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantAccess(Long accessLogId) {
        assertApproverCanList();
        ArchiveAccessLog accessLog = archiveAccessLogMapper.selectById(accessLogId);
        if (accessLog == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调阅申请不存在: " + accessLogId);
        }
        if (!"APPROVED".equals(accessLog.getApprovalStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调阅申请未获批准: " + accessLogId);
        }
        accessLog.setAccessedAt(LocalDateTime.now());
        archiveAccessLogMapper.updateById(accessLog);
        log.info("授予访问: accessLogId={}", accessLogId);
    }

    /**
     * 批准/授予都必须同时持有「调阅申请查看」{@code compliance:archiveaccess:list}。
     * 配置期已有拦截（查看是其余权限的前置，不能把查看设为无权），运行层是第二道：
     * 两个权限的持有人若没有交集，有 approve 的人列不出申请却仍能批准放行，
     * 等于在看不见台账的情况下开放档案访问。此处只做代码校验，不改动权限数据。
     */
    private void assertApproverCanList() {
        PermDependency
                .assertHolds(UserContext.get(), "compliance:archiveaccess:approve");
    }

    public ArchiveAccessLog getById(Long id) {
        ArchiveAccessLog accessLog = archiveAccessLogMapper.selectById(id);
        if (accessLog == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调阅申请不存在: " + id);
        }
        fillRequesters(List.of(accessLog));
        return accessLog;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ArchiveAccessLog accessLog = archiveAccessLogMapper.selectById(id);
        if (accessLog == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调阅申请不存在: " + id);
        }
        archiveAccessLogMapper.deleteById(id);
        log.info("删除调阅申请: id={}", id);
    }

    public List<ArchiveAccessLog> listByArchive(Long archiveId) {
        LambdaQueryWrapper<ArchiveAccessLog> qw = new LambdaQueryWrapper<>();
        qw.eq(ArchiveAccessLog::getArchiveId, archiveId)
                .orderByDesc(ArchiveAccessLog::getCreateTime);
        List<ArchiveAccessLog> rows = archiveAccessLogMapper.selectList(qw);
        fillRequesters(rows);
        return rows;
    }

    public List<ArchiveAccessLog> listMyRequests(Long requesterId) {
        LambdaQueryWrapper<ArchiveAccessLog> qw = new LambdaQueryWrapper<>();
        qw.eq(ArchiveAccessLog::getRequesterId, requesterId)
                .orderByDesc(ArchiveAccessLog::getCreateTime);
        List<ArchiveAccessLog> rows = archiveAccessLogMapper.selectList(qw);
        fillRequesters(rows);
        return rows;
    }

    public List<ArchiveAccessLog> listPendingApproval() {
        LambdaQueryWrapper<ArchiveAccessLog> qw = new LambdaQueryWrapper<>();
        qw.eq(ArchiveAccessLog::getApprovalStatus, "PENDING")
                .orderByAsc(ArchiveAccessLog::getCreateTime)
                // 本端点无分页入参（界面已改走 /pending/page）。20 万行待审语料实测单响应 13.4 MB，
                // 上限 200 行只防无界载荷，正常待审队列远小于此；需要翻页请走 pagePendingApproval。
                .last("LIMIT 200");
        List<ArchiveAccessLog> rows = archiveAccessLogMapper.selectList(qw);
        fillRequesters(rows);
        return rows;
    }

    /** 待审批调阅申请分页（前端 server 模式用；原 listPendingApproval 保留兼容） */
    public PageResult<ArchiveAccessLog> pagePendingApproval(long pageNum, long pageSize) {
        return pageApproval("PENDING", pageNum, pageSize);
    }

    /**
     * 调阅审批分页按状态可查。
     * 原端点只返回 PENDING ⇒ 已批准/已驳回的调阅记录在界面上永不可见
     * （已批、已驳的记录都拿不到，页面上只剩待审那一行）。
     * status 传空或 ALL 返回全部，其余按等值过滤；控制器默认给 PENDING 以保持既有调用方行为不变。
     */
    public PageResult<ArchiveAccessLog> pageApproval(String status, long pageNum, long pageSize) {
        LambdaQueryWrapper<ArchiveAccessLog> qw = new LambdaQueryWrapper<>();
        String st = status == null ? "" : status.trim();
        if (!st.isEmpty() && !"ALL".equalsIgnoreCase(st)) {
            qw.eq(ArchiveAccessLog::getApprovalStatus, st.toUpperCase());
        }
        qw.orderByAsc(ArchiveAccessLog::getCreateTime);
        Page<ArchiveAccessLog> p = archiveAccessLogMapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillRequesters(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 申请人姓名/工号回填：
     * 后端只给裸 requester_id，前端本地 join 出来的是工号而不是姓名；
     * 且本地用户表只拉一页 500 行，翻页外的申请人会退化成「用户#ID」。
     * 规则与流程侧 {@code WfQueryService.namesByIds} 一致：nick_name 优先、缺失回退登录号；
     * 姓名与工号并存，不做互相替换，也不回写历史行。
     */
    private void fillRequesters(List<ArchiveAccessLog> rows) {
        Set<Long> ids = new HashSet<>();
        for (ArchiveAccessLog r : rows) {
            if (r.getRequesterId() != null) {
                ids.add(r.getRequesterId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, SysUser> byId = new HashMap<>();
        sysUserMapper.selectBatchIds(ids).forEach(u -> byId.put(u.getId(), u));
        for (ArchiveAccessLog r : rows) {
            SysUser u = r.getRequesterId() == null ? null : byId.get(r.getRequesterId());
            if (u == null) {
                continue;
            }
            r.setRequesterUserName(u.getUserName());
            r.setRequesterName(u.getNickName() == null || u.getNickName().isBlank()
                    ? u.getUserName() : u.getNickName());
        }
    }
}