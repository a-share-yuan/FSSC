package com.fssc.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.audit.entity.AuditTrail;
import com.fssc.audit.mapper.AuditTrailMapper;
import com.fssc.common.core.PageResult;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计留痕服务（P0-3）。
 * 记录关键业务变更 before/after，只追加不更新；提供分页查询与按单据导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private final AuditTrailMapper trailMapper;
    private final SysUserMapper sysUserMapper;
    /** 用容器里的 ObjectMapper（带 JavaTimeModule）：裸 new 遇 LocalDateTime 直接抛，前后值又退回 NULL */
    private final ObjectMapper objectMapper;

    /**
     * 记录一条审计流水（内部调用；tenantId 由多租户拦截器自动写入）。
     * 业务域请优先用 {@link #recordChange}：只有它会把对象序列化成前后值，
     * 本方法的 beforeJson/afterJson 允许为空，而"前后值全 NULL 的轨迹"正是 D-45 的症状，
     * 因此两侧都空时在本方法统一告警，不静默。
     */
    public void record(String bizType, Long bizId, String bizNo, String objType, String objId,
                       String action, String beforeJson, String afterJson, String remark) {
        try {
            if (beforeJson == null && afterJson == null) {
                log.warn("审计留痕无前后值（仅说明文字，无法复核）bizType={} bizId={} action={}", bizType, bizId, action);
            }
            AuditTrail t = new AuditTrail();
            t.setBizType(bizType);
            t.setBizId(bizId);
            t.setBizNo(bizNo);
            t.setObjType(objType);
            t.setObjId(objId);
            t.setAction(action);
            t.setBeforeJson(beforeJson);
            t.setAfterJson(afterJson);
            t.setRemark(remark);
            try {
                LoginUser u = UserContext.getRequired();
                t.setOperator(u.getUserId());
            } catch (Exception ignored) {
                // 无登录上下文（定时任务等）时不记录操作人
            }
            trailMapper.insert(t);
        } catch (Exception e) {
            // 审计写入失败不阻断业务
            log.warn("审计留痕写入失败 bizType={} bizId={}", bizType, bizId, e);
        }
    }

    /** 便捷：变更类审计（before/after 字段值） */
    public void recordUpdate(String bizType, Long bizId, String bizNo, String objType, String objId,
                             String beforeJson, String afterJson, String remark) {
        record(bizType, bizId, bizNo, objType, objId, "UPDATE", beforeJson, afterJson, remark);
    }

    /**
     * 便捷：变更类审计（入参为对象，由本服务内部序列化）。
     * <p>P4-3 扩面：调用点无需各自注入 ObjectMapper，统一复用容器里的带 JavaTimeModule 的 mapper
     * （裸 new 遇 LocalDateTime 会抛，此前已踩过）；序列化失败降级为 null，由 {@link #record} 统一告警，不阻断业务。
     * <p>典型用法：纯状态跃迁传 {@code Collections.singletonMap("status", 旧值)}；
     * 同时改了多个字段的点传一个只含本次实际变化字段的 Map。
     */
    public void recordUpdate(String bizType, Long bizId, String bizNo, String objType, String objId,
                             Object before, Object after, String remark) {
        record(bizType, bizId, bizNo, objType, objId, "UPDATE", toJson(before), toJson(after), remark);
    }

    /** 对象 → JSON 字符串；失败返回 null（降级为无快照并告警），绝不抛出。 */
    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("审计前后值序列化失败，降级为无快照 type={}", o.getClass().getSimpleName(), e);
            return null;
        }
    }

    public PageResult<AuditTrail> page(long pageNum, long pageSize, String bizType, String bizNo, String keyword) {
        LambdaQueryWrapper<AuditTrail> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(AuditTrail::getBizType, bizType);
        }
        if (bizNo != null && !bizNo.isBlank()) {
            qw.like(AuditTrail::getBizNo, bizNo);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(AuditTrail::getObjType, keyword).or().like(AuditTrail::getRemark, keyword));
        }
        qw.orderByDesc(AuditTrail::getId);
        Page<AuditTrail> p = trailMapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillOperatorName(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 按条件导出（bizNo/bizType/keyword 任一或全部为空即不加该条件），上限 limit 行 */
    public List<AuditTrail> listByFilter(String bizType, String bizNo, String keyword, long limit) {
        LambdaQueryWrapper<AuditTrail> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(AuditTrail::getBizType, bizType);
        }
        if (bizNo != null && !bizNo.isBlank()) {
            qw.like(AuditTrail::getBizNo, bizNo);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(AuditTrail::getObjType, keyword).or().like(AuditTrail::getRemark, keyword));
        }
        qw.orderByDesc(AuditTrail::getId).last("limit " + Math.max(1, Math.min(limit, 50000)));
        List<AuditTrail> list = trailMapper.selectList(qw);
        fillOperatorName(list);
        return list;
    }

    /** 把裸用户 ID 换成「姓名（工号）」；无上下文（定时任务）或查不到时保留原样 */
    private void fillOperatorName(List<AuditTrail> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> ids = new LinkedHashSet<>();
        for (AuditTrail t : list) {
            if (t.getOperator() != null) ids.add(t.getOperator());
        }
        if (ids.isEmpty()) return;
        Map<Long, String> names = new HashMap<>();
        try {
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                names.put(u.getId(), (u.getNickName() == null ? "" : u.getNickName())
                        + (u.getUserName() == null ? "" : "（" + u.getUserName() + "）"));
            }
        } catch (Exception e) {
            log.warn("审计轨迹操作人姓名回填失败，降级显示用户ID", e);
            return;
        }
        for (AuditTrail t : list) {
            String name = t.getOperator() == null ? null : names.get(t.getOperator());
            t.setOperatorName(name != null ? name : (t.getOperator() == null ? "系统" : "用户#" + t.getOperator()));
        }
    }

    /** 按单据导出：查询该业务单号的全部审计轨迹（CSV 导出用） */
    public List<AuditTrail> listByBill(String bizNo) {
        return trailMapper.selectList(new LambdaQueryWrapper<AuditTrail>()
                .eq(AuditTrail::getBizNo, bizNo)
                .orderByAsc(AuditTrail::getId));
    }

    /** 内部校验审计（如校验和失败），供业务域调用 */
    public void recordChecksumFailure(String billType, Long billId, String billNo, String remark) {
        record(billType, billId, billNo, "exp_bill", String.valueOf(billId), "CHECKSUM", null, null, remark);
    }
}
