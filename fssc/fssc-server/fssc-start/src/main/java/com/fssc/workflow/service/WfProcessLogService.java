// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.workflow.entity.WfProcessLog;
import com.fssc.workflow.mapper.WfProcessLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 流程处理详细日志（{@code wf_process_log}）——流程配置「详细日志开关」（参数 4.3）：
 * 「勾选后流程处理详细日志写 log 与流程日志（管理中心可查）」。
 *
 * <p><b>开关规则</b>：{@code sys_config.wf.log.detailed.enabled}，缺省 {@code 0}。
 * 取 <b>fail-closed</b>（读不到即视为关闭）——与 {@code wf.reject.free.enabled} 同一规则：
 * 详细日志会成倍放大写入量，宁可少记不可默认打开。
 *
 * （每次直读 {@code sys_config}，走 config_key 索引）。事件频率＝审批动作频率（量级极低），
 * 换来的好处是运营在管理中心勾选后<b>立即生效、无需重启</b>——这正是"参数"该有的语义。
 *
 * <p><b>零影响</b>：开关关闭时 {@link #write} 在读配置后立即返回，不写任何行、不抛任何异常。
 * 写入失败也只记一行 warn —— 详细日志是<b>可观测性</b>，绝不能因为"记日志失败"阻断审批主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessLogService {

    /** 详细日志开关（sys_config.config_key） */
    public static final String CFG_KEY_DETAILED = "wf.log.detailed.enabled";

    private final WfProcessLogMapper wfProcessLogMapper;
    private final SysConfigMapper sysConfigMapper;

    /** 详细日志是否开启（fail-closed：读不到/异常一律视为关闭） */
    public boolean enabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, CFG_KEY_DETAILED)
                    .last("limit 1"));
            return c != null && c.getConfigValue() != null && "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.warn("读取 {} 异常，fail-closed 视为关闭: {}", CFG_KEY_DETAILED, ex.getMessage());
            return false;
        }
    }

    /**
     * 记一条流程处理日志。<b>开关关闭时直接返回</b>（不写库）。
     *
     * <p>同时写 SLF4J（通行参数原文要求"写 log 与流程日志"两处）：文件日志给运维 grep，
     * 本表给管理中心按实例查询。两处内容同源，不各写一套。
     *
     * <p>失败语义：整体 try/catch，失败只 warn。原因见类注释——日志写失败不该让审批失败。
     */
    public void write(WfProcessLog row) {
        if (row == null || !enabled()) {
            return;
        }
        try {
            if (row.getTenantId() == null) {
                row.setTenantId(TenantContext.hasContext() && !TenantContext.isIgnoreTenant()
                        ? TenantContext.getTenantId() : null);
            }
            if (row.getLevel() == null) {
                row.setLevel("INFO");
            }
            row.setCreateTime(LocalDateTime.now());
            wfProcessLogMapper.insert(row);
            log.info("[流程日志] type={} proc={} task={} node={} biz={}#{} {}",
                    row.getLogType(), row.getProcInstId(), row.getTaskId(), row.getNodeKey(),
                    row.getBizType(), row.getBizId(), row.getContent());
        } catch (Exception ex) {
            log.warn("流程处理详细日志写入失败（不影响主链路）type={} proc={}: {}",
                    row.getLogType(), row.getProcInstId(), ex.getMessage());
        }
    }

    // 管理中心查询

    /** 按实例/单据/类型/时间分页查询（管理中心「流程日志」视图） */
    public PageResult<WfProcessLog> page(PageQuery q, String procInstId, String bizNo, String bizType,
                                        String logType, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<WfProcessLog> w = new LambdaQueryWrapper<>();
        if (procInstId != null && !procInstId.isBlank()) {
            w.eq(WfProcessLog::getProcInstId, procInstId);
        }
        if (bizNo != null && !bizNo.isBlank()) {
            w.eq(WfProcessLog::getBizNo, bizNo);
        }
        if (bizType != null && !bizType.isBlank()) {
            w.eq(WfProcessLog::getBizType, bizType);
        }
        if (logType != null && !logType.isBlank()) {
            w.eq(WfProcessLog::getLogType, logType);
        }
        if (from != null) {
            w.ge(WfProcessLog::getCreateTime, from);
        }
        if (to != null) {
            w.le(WfProcessLog::getCreateTime, to);
        }
        w.orderByDesc(WfProcessLog::getId);
        Page<WfProcessLog> p = wfProcessLogMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize());
    }

    /** 某实例的完整流程日志（时间线，升序）——排查"这单为什么卡住"的主入口 */
    public List<WfProcessLog> byInstance(String procInstId) {
        return wfProcessLogMapper.selectList(new LambdaQueryWrapper<WfProcessLog>()
                .eq(WfProcessLog::getProcInstId, procInstId)
                .orderByAsc(WfProcessLog::getId));
    }

    /**
     * 按保留天数清理（物理删除）。
     *
     * <p>为什么必须提供：详细日志打开后按事件逐条累积，没有清理通道就会无限膨胀——
     * 这正是"日志开关配了却没人敢长期打开"的常见成因。物理删除（非逻辑删）才真正释放空间；
     * 日志不是审计凭证（审计在 {@code wf_task_history}），可安全清理。
     *
     * @param keepDays 保留最近天数（&lt;1 时按 1 处理，防止误传 0 清空全表）
     * @return 删除行数
     */
    public int cleanByRetentionDays(int keepDays) {
        int days = Math.max(keepDays, 1);
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        return wfProcessLogMapper.delete(new LambdaQueryWrapper<WfProcessLog>()
                .lt(WfProcessLog::getCreateTime, before));
    }
}