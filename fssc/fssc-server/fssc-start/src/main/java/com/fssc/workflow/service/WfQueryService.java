// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfCc;
import com.fssc.workflow.entity.WfAddSign;
import com.fssc.workflow.mapper.WfAddSignMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfCcMapper;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.fssc.workflow.mapper.WfTaskHistoryMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.entity.WfModel;
import java.io.StringReader;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 流程查询视图（wf_instance / wf_task / wf_task_history / wf_cc，均带 tenant_id）。
 * 待办/历史/抄送列表只查业务视图表，不与 ACT_* 联查（引擎表是隔离盲区 + 历史会被清理）。
 * 双写由 WfEngineFacade 调本服务的 record* 方法统一保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfQueryService {

    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    /**
     * 业务单据最终状态判定（作废/关闭/归档）。
     * 待办查询就地剔除最终状态单待办，与超时催办校验、WfEngineFacade.todo 共用同一规则。
     */
    private final BillTerminalChecker billTerminalChecker;
    private final WfTaskHistoryMapper historyMapper;
    private final WfCcMapper ccMapper;
    private final WfAddSignMapper addSignMapper;
    private final WfNodeConfigMapper nodeConfigMapper;
    private final SysUserMapper sysUserMapper;
    /** 节点配置解析：与超时催办 Job 同一份规则，看板/列表的"超时"判定不再各算各的 */
    private final WfNodeConfigResolver wfNodeConfigResolver;
    /** 流程管理中心组织隔离（开关缺省关，零行为变化） */
    private final WfManageOrgScope wfManageOrgScope;
    /** chain 分支感知要读 wf_model.bpmn_xml 建前向可达图 */
    private final WfModelMapper modelMapper;
    /**
     * 审批历史补操作人部门名（{@code wf_task_history.oper_dept_name}）用。
     * sys_dept 是租户过滤表 ⇒ 只在有 {@link TenantContext} 的线程里可查，取不到就留空（见
     * {@link #fillOperatorDept}）；与 {@code OperLogAspect.resolveDeptName} 同一规则，
     * 两处重复的部门反查可以后续抽公共件。
     */
    private final SysDeptMapper sysDeptMapper;

    /**
     * BPMN 前向可达图缓存，key = modelKey@version（重发布必然升版本，天然失效）。
     * 值为 null 表示"该模型无法建图"（XML 缺失/解析失败/结构异常），按 fail-open 处理＝不做任何 SKIPPED 标注。
     */
    private final Map<String, FlowGraph> flowGraphCache = new ConcurrentHashMap<>();

    // 实例

    /**
     * 实例分页。
     *
     * <p>模型详情抽屉的「本模型实例」页签一直在传
     * {@code modelKey}，但端点签名里没有这个参数 ⇒ 参数被丢弃、取回的是**全模型**实例，
     * 标题与内容不符。签名收下这个参数即把该维度接回。
     *
     * <p>纯增量：只有非空才 eq。监控大屏（FlowMonitor/MonthEndBoard/ApprovalFlowBoard）
     * 这三处调用方本就不传 modelKey，语义保持"全模型"不变。
     */
    public PageResult<WfInstance> instancePage(long pageNum, long pageSize, String bizType, String status,
                                               String keyword, String modelKey) {
        LambdaQueryWrapper<WfInstance> qw = new LambdaQueryWrapper<>();
        // 组织隔离（sys_config.wf.manage.org-isolated，缺省关 ⇒ 零行为变化）
        wfManageOrgScope.applyInstanceScope(qw);
        if (modelKey != null && !modelKey.isBlank()) {
            qw.eq(WfInstance::getModelKey, modelKey);
        }
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(WfInstance::getBizType, bizType);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(WfInstance::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(WfInstance::getBizNo, keyword)
                    .or().like(WfInstance::getTitle, keyword));
        }
        qw.orderByDesc(WfInstance::getId);
        Page<WfInstance> p = instanceMapper.selectPage(Page.of(pageNum, pageSize), qw);
        fillCurrentHandlers(p.getRecords());
        fillInitiatorNames(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 发起人姓名回填：列表绑的是 {@code initiatorName}，而后端从不返回该字段
     * ⇒ 列恒空。规则与 {@link #loadUserNames} 一致：nick_name 优先、缺失回退登录号。
     * 与 currentHandlers 分开填，因为后者在没有 PENDING 任务时提前返回（最终状态实例无人可填）。
     */
    private void fillInitiatorNames(List<WfInstance> rows) {
        Set<Long> ids = new HashSet<>();
        for (WfInstance i : rows) {
            if (i.getStartUserId() != null) {
                ids.add(i.getStartUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = namesByIds(ids);
        for (WfInstance i : rows) {
            if (i.getStartUserId() != null) {
                i.setInitiatorName(names.get(i.getStartUserId()));
            }
        }
    }

    /**
     * 发布新版本后的「在途旧实例」清单。
     *
     * <p>Flowable 的 {@code startProcessInstanceByKey} 只让之后发起的实例用新定义，
     * 已存在的实例终身绑定发起时的 BPMN XML——本系统没有实例迁移能力，
     * 所以发布必须让设计者看见「有多少单还在跑旧流程、卡在哪个节点、谁手上」，
     * 否则改完流程会出现新老混杂无人负责的现场事故。
     *
     * @param newProcDefId 本次发布回写的流程定义 ID；为 null 时全部在途都算「旧」
     */
    public Map<String, Object> inFlightByModel(String modelKey, String newProcDefId) {
        List<WfInstance> running = instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getModelKey, modelKey)
                .eq(WfInstance::getStatus, "RUNNING")
                .orderByDesc(WfInstance::getId));
        fillCurrentHandlers(running);
        List<WfInstance> stale = new ArrayList<>();
        Map<String, Long> byDef = new LinkedHashMap<>();
        for (WfInstance i : running) {
            byDef.merge(i.getProcDefId() == null ? "(未知)" : i.getProcDefId(), 1L, Long::sum);
            if (newProcDefId == null || !newProcDefId.equals(i.getProcDefId())) {
                stale.add(i);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modelKey", modelKey);
        r.put("newProcDefId", newProcDefId);
        r.put("runningTotal", running.size());
        r.put("staleCount", stale.size());
        r.put("byProcDef", byDef);
        r.put("items", stale.size() > 100 ? stale.subList(0, 100) : stale);
        r.put("truncated", stale.size() > 100);
        r.put("note", "新版本只对之后发起的实例生效；在途实例仍按发起时的流程定义执行。"
                + "需要改走新流程只能撤销后重新提交，或在节点上做改派/转办，本系统不做实例迁移。");
        return r;
    }

    /** 在途旧实例计数（发布日志用，只数不回填） */
    public long countRunningOld(String modelKey, String procDefId) {
        LambdaQueryWrapper<WfInstance> qw = new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getModelKey, modelKey)
                .eq(WfInstance::getStatus, "RUNNING");
        if (procDefId != null && !procDefId.isBlank()) {
            qw.ne(WfInstance::getProcDefId, procDefId);
        }
        return instanceMapper.selectCount(qw);
    }

    /**
     * current_assignee 列存的是裸用户 ID 且语义为"上一节操作人"，看板不能拿它催办/改派。
     * 这里按 wf_task 镜像行回填真实在办 taskId、可办理人 ID 与姓名（20260922 体检 G16）。
     */
    private void fillCurrentHandlers(List<WfInstance> rows) {
        List<String> procIds = new ArrayList<>();
        for (WfInstance i : rows) {
            if (i.getProcInstId() != null) procIds.add(i.getProcInstId());
        }
        if (procIds.isEmpty()) {
            return;
        }
        List<WfTask> pend = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .in(WfTask::getProcInstId, procIds)
                .eq(WfTask::getStatus, "PENDING")
                .orderByAsc(WfTask::getId));
        if (pend.isEmpty()) {
            return;
        }
        Map<Long, String> names = loadUserNames(pend);
        Map<String, WfNodeConfig> nodeConfigs = wfNodeConfigResolver.index(pend);
        Map<String, List<WfTask>> byProc = new LinkedHashMap<>();
        for (WfTask t : pend) {
            byProc.computeIfAbsent(t.getProcInstId(), k -> new ArrayList<>()).add(t);
        }
        for (WfInstance inst : rows) {
            List<WfTask> ts = byProc.get(inst.getProcInstId());
            if (ts == null || ts.isEmpty()) {
                continue;
            }
            fillStay(inst, ts, nodeConfigs);
            inst.setCurrentTaskId(ts.get(0).getTaskId());
            Set<Long> ids = new LinkedHashSet<>();
            for (WfTask t : ts) {
                if (t.getAssigneeId() != null) {
                    ids.add(t.getAssigneeId());
                } else {
                    ids.addAll(parseCandidateIds(t.getCandidateIds()));
                }
            }
            StringBuilder idSb = new StringBuilder();
            StringBuilder nameSb = new StringBuilder();
            for (Long uid : ids) {
                if (idSb.length() > 0) {
                    idSb.append(',');
                    nameSb.append('、');
                }
                idSb.append(uid);
                nameSb.append(names.getOrDefault(uid, String.valueOf(uid)));
            }
            inst.setCurrentAssigneeIds(idSb.toString());
            inst.setCurrentAssigneeNames(nameSb.toString());
        }
    }

    /**
     * 当前节点停留与超时（监控大屏"停留时长/状态"两列的真规则）。
     *
     * <p>返回体里必须有 {@code stayMinutes}/{@code timeout} 这两个字段，否则前端按它们渲染时：
     * "停留时长"整列是 "-"、"状态"永远是"正常"——看板看起来一切正常，实际是把空值当正常。
     * 按最早的 PENDING 镜像行 create_time 起算停留，超时判定复用
     * {@link WfNodeConfigResolver} 同一份节点配置规则（与催办 Job 判得一致，不会出现"看板说超时、Job 不催"）。
     *
     * <p>移交合入：超时规则与 {@code WfPerfService.overdueOf} 收敛为同一真相源——
     * 有 {@code due_time} 就按 now−due_time 实算，无 due_time 才退回「create_time+节点 SLA」推算，
     * 不让底部在途表与上方 KPI 各说各话。
     */
    private void fillStay(WfInstance inst, List<WfTask> pending, Map<String, WfNodeConfig> nodeConfigs) {
        LocalDateTime earliest = null;
        LocalDateTime now = LocalDateTime.now();
        long overdueHours = 0;
        for (WfTask t : pending) {
            if (t.getCreateTime() == null) {
                continue;
            }
            if (earliest == null || t.getCreateTime().isBefore(earliest)) {
                earliest = t.getCreateTime();
            }
            if (t.getDueTime() != null) {
                long over = Duration.between(t.getDueTime(), now).toHours();
                if (over > overdueHours) {
                    overdueHours = over;
                }
                continue;
            }
            WfNodeConfig nc = nodeConfigs.get(t.getModelKey() + ":" + t.getNodeKey());
            if (nc != null && nc.getTimeoutHours() != null && nc.getTimeoutHours() > 0) {
                long over = Duration.between(t.getCreateTime().plusHours(nc.getTimeoutHours()), now).toHours();
                if (over > overdueHours) {
                    overdueHours = over;
                }
            }
        }
        inst.setStayMinutes(earliest == null ? null : Duration.between(earliest, now).toMinutes());
        inst.setOverdueHours(overdueHours);
        inst.setTimeout(overdueHours > 0);
    }

    /** 候选组列 {@code ,1,2,3,} → 用户 ID 列表。公开给超时 Job 与绩效分析复用，保证"谁能办"只有一份规则。 */
    public static List<Long> parseCandidateIds(String candidateIds) {
        List<Long> out = new ArrayList<>();
        if (candidateIds == null || candidateIds.isBlank()) {
            return out;
        }
        for (String seg : candidateIds.split(",")) {
            if (!seg.isBlank()) {
                try {
                    out.add(Long.parseLong(seg.trim()));
                } catch (NumberFormatException ignore) {
                    // 候选组列可能被写入角色码等非数字值，跳过
                }
            }
        }
        return out;
    }

    public WfInstance instanceByProcInstId(String procInstId) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcInstId, procInstId));
    }

    /**
     * 批量取实例状态（{@code proc_inst_id → status}），500 个一批 IN。
     * <p>供定时任务一次装载闸门用：口径与 {@link #instanceByProcInstId(String)} 同源，
     * 同一 {@code wf_instance} 表、同一租户过滤；查无实例不在结果里，调用方按原规则跳过。
     */
    public Map<String, String> statusesByProcInstIds(Collection<String> procInstIds) {
        Map<String, String> out = new LinkedHashMap<>();
        if (procInstIds == null || procInstIds.isEmpty()) {
            return out;
        }
        List<String> list = new ArrayList<>(procInstIds);
        for (int i = 0; i < list.size(); i += 500) {
            List<String> chunk = list.subList(i, Math.min(i + 500, list.size()));
            for (WfInstance r : instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                    .select(WfInstance::getProcInstId, WfInstance::getStatus)
                    .in(WfInstance::getProcInstId, chunk))) {
                if (r.getProcInstId() != null) {
                    out.put(r.getProcInstId(), r.getStatus());
                }
            }
        }
        return out;
    }

    /**
     * 单据的全部流程实例（驳回重提会有多轮）：按 biz_type+biz_id 精确定位， newest first。
     *
     * <p>费用域<b>成对匹配</b>（与 {@code WfTaskController} 待办回填同一规则）：运行期 {@code wf_instance.biz_type}
     * 可能是带前缀的 {@code EXP_CLAIM}，而业务页手里的是不带前缀的 {@code CLAIM}——只按传值精确匹配
     * 会查不到实例，读侧就把这张单判成"与我无关"，历史分页直接空（比空更糟的是让人以为没有审批）。</p>
     */
    public List<WfInstance> instancesByBiz(String bizType, Long bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return List.of();
        }
        String bare = bizType.startsWith("EXP_") ? bizType.substring(4) : bizType;
        return instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .and(w -> w.eq(WfInstance::getBizType, bizType)
                        .or().eq(WfInstance::getBizType, "EXP_" + bare)
                        .or().eq(WfInstance::getBizType, bare))
                .eq(WfInstance::getBizId, bizId)
                .orderByDesc(WfInstance::getId));
    }

    /**
     * 同一主键在多个单据域都有流程时，这里取全部（历史分页只传 bizId 的老调用方用）。
     * 上限 50 条：够覆盖一张单的多轮重提，也防同 id 撞出的大批他域实例拼成超长 OR 条件。
     */
    public List<WfInstance> instancesById(Long bizId) {
        if (bizId == null) {
            return List.of();
        }
        return instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizId, bizId)
                .orderByDesc(WfInstance::getId)
                .last("limit 50"));
    }

    /** 发起流程后双写实例视图（WfEngineFacade.start 调用） */
    @Transactional(rollbackFor = Exception.class)
    public void recordInstance(WfInstance inst) {
        inst.setId(null);
        inst.setStatus(inst.getStatus() == null ? "RUNNING" : inst.getStatus());
        instanceMapper.insert(inst);
    }

    public void updateInstanceStatus(String procInstId, String status, LocalDateTime endTime) {
        WfInstance upd = new WfInstance();
        upd.setStatus(status);
        upd.setEndTime(endTime);
        // duration 列不靠查询侧减法：收尾时按开始时刻补齐流程总时长。
        if (endTime != null) {
            WfInstance cur = instanceByProcInstId(procInstId);
            if (cur != null && cur.getStartTime() != null) {
                upd.setDuration(Duration.between(cur.getStartTime(), endTime).toMinutes());
            }
        }
        // 实例进最终状态时必须把"当前节/当前办理人"抹掉。留着不清，已办结的单在列表/看板/联查里
        // 仍然带着"当前节点=生成凭证【总账会计】、当前办理人=3836"，于是已结流程被计成在办。
        // 办结点选在本方法而不是各写入位置——引擎办结/驳回兜底/撤回/运维清理/投影
        // 五条最终状态路径全部经过这里，写在这些调用方里必然漏一条。
        // 实现规则：<b>必须用 LambdaUpdateWrapper 显式 set null</b>——本仓库已知坑：
        // {@code update(entity, wrapper)} 会忽略实体里的 null 字段（WfEngineFacade 空串清 current_assignee
        // 同因），传 null 等于什么都没改。current_assignee_ids/_names 是查询期由 wf_task PENDING 反查的
        // 非表列（见 WfInstance 的 @TableField(exist=false)），最终状态时那批 PENDING 行已由
        // closePendingTasksByInstance 办结，故无需、也无列可清。
        if (isTerminalInstanceStatus(status)) {
            LambdaUpdateWrapper<WfInstance> uw =
                    new LambdaUpdateWrapper<WfInstance>()
                            .eq(WfInstance::getProcInstId, procInstId)
                            .set(WfInstance::getStatus, status)
                            .set(WfInstance::getEndTime, endTime)
                            .set(WfInstance::getCurrentNode, null)
                            .set(WfInstance::getCurrentAssignee, null);
            if (upd.getDuration() != null) {
                uw.set(WfInstance::getDuration, upd.getDuration());
            }
            instanceMapper.update(null, uw);
            return;
        }
        instanceMapper.update(upd, new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcInstId, procInstId));
    }

    /**
     * 实例最终状态判定：wf_instance.status 的取值域只有
     * RUNNING/APPROVED/REJECTED/CANCELLED/TERMINATED 五个，除 RUNNING 外都是"这单已经死了"。
     * 按白名单反向判（非 RUNNING 即最终状态）而不是枚举四个值，哪天加个新最终状态也不会漏清。
     *
     * <p>起对本仓库外可见（public）：联查面（BillTraceChainController）要用<b>同一把</b>
     * 最终状态尺子做"当前节/当前办理人"的展示抑制，两处各写一份规则必然漂移——清列的与露列的必须同一规则。
     */
    public static boolean isTerminalInstanceStatus(String status) {
        return status != null && !status.isBlank() && !"RUNNING".equals(status.trim().toUpperCase());
    }

    /** 部分更新流程实例（如当前节点推进） */
    public void updateInstance(WfInstance upd, String procInstId) {
        instanceMapper.update(upd, new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcInstId, procInstId));
    }

    // 待办任务

    public PageResult<WfTask> taskPage(long pageNum, long pageSize, Long assigneeId, String status, String keyword) {
        LambdaQueryWrapper<WfTask> qw = new LambdaQueryWrapper<>();
        if (assigneeId != null) {
            qw.eq(WfTask::getAssigneeId, assigneeId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(WfTask::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(WfTask::getBizNo, keyword)
                    .or().like(WfTask::getTitle, keyword));
        }
        qw.orderByDesc(WfTask::getId);
        Page<WfTask> p = taskMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 我的待办（PENDING 且处理人=我）。
     *
     * <p>单据已最终状态（VOID 作废 / CLOSED 关闭 / ARCHIVED 归档）或已逻辑删除的待办
     * 一律就地剔除——即使作废等终止入口漏走流程、或存在历史脏数据使镜像行滞留 PENDING，
     * 也不会再冒出来。与 {@code WfEngineFacade.todo}（角标/工作台）同一规则，三处查询一次性对齐。
     */
    public PageResult<WfTask> myTodo(long pageNum, long pageSize, Long userId) {
        LambdaQueryWrapper<WfTask> qw = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .and(w -> w.eq(WfTask::getAssigneeId, userId)
                        .or().like(WfTask::getCandidateIds, "," + userId + ","))
                // 单据最终状态剔除：非采购订单面 / 无业务单据 / 单据未最终状态 均放行，只有「采购订单且已最终状态」被排除。
                .and(w -> w.isNull(WfTask::getBizType)
                        .or().ne(WfTask::getBizType, BillTerminalChecker.BIZ_PURCHASE_ORDER_REVIEW)
                        .or().isNull(WfTask::getBizId)
                        .or().notInSql(WfTask::getBizId, BillTerminalChecker.PO_TERMINAL_IDS_SQL))
                .orderByDesc(WfTask::getId);
        Page<WfTask> p = taskMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * POOL 软锁可见性：镜像里"我是候选人、但已被同池他人领取"的 POOL 任务 id。
     *
     * <p>为什么单独查：Flowable 的候选组查询只回未指派的任务，于是任务一旦被同池某人领取，
     * 就从其他人的待办里整行消失——正确行为是"任务在别人列表里仍可见，只是不能处理，且能看到锁定者"，
     * 行都看不见就无从灰显。这里只按 POOL 节点规则回捞，AUTO/PICK 的可见性保持原样。
     */
    public List<String> poolTasksClaimedByOthers(Long userId, Set<String> poolNodeKeys) {
        if (userId == null || poolNodeKeys == null || poolNodeKeys.isEmpty()) {
            return List.of();
        }
        // 不能带 ORDER BY id DESC LIMIT 50：优化器会为此走 PRIMARY 反向扫，而候选基本不命中，
        // 等于把几十万行 PENDING 扫一遍才返回。回捞结果只当 id 集合用、顺序无意义，去掉排序后
        // 优化器改用 (tenant_id,status) 前缀索引，只走 PENDING 的索引项。
        // 候选列口径以引擎写入端为准＝首尾带逗号（WfEngineFacade / WfMirrorProjectionListener
        // 都是 append(","+uid) 再补尾逗号）；库里若落成 "a,b" 这种无逗号形态，本 LIKE 恒不命中，
        // 写入口径必须与查询口径一致。
        List<WfTask> rows = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .like(WfTask::getCandidateIds, "," + userId + ",")
                .isNotNull(WfTask::getAssigneeId)
                .ne(WfTask::getAssigneeId, userId)
                .last("limit 50"));
        return rows.stream()
                .filter(r -> r.getTaskId() != null && r.getModelKey() != null && r.getNodeKey() != null
                        && poolNodeKeys.contains(r.getModelKey() + ":" + r.getNodeKey()))
                .map(WfTask::getTaskId)
                .toList();
    }

    /**
     * 回捞「AUTO 档 + 同岗位角色 + assignee 是他人」的在途任务 id。
     *
     * <p>这批任务在 Flowable 侧因 {@code taskCandidateGroupIn} 只回未指派任务而查不到，
     * 但服务端 {@code approve} 允许同岗位的人办理（只按登录人判权限与两道校验，不校验 assignee），
     * 于是造成「能办却看不见」——用户工作台空白、单子静默停摆。
     *
     * <p>规则与 POOL 回捞一致：**只负责可见**，能否办理仍由 {@code approve} 的
     * 连办校验/自审限制与 {@code canOperate} 决定，本方法不做任何放行。
     *
     * @param autoNodeRoleMap {@code modelKey:nodeKey -> 角色码}（见 WfNodeConfigResolver#autoNodeRoleMap）
     * @param myRoles 我持有的角色码
     */
    public List<String> autoTasksOfSameRole(Long userId,
                                            Map<String, String> autoNodeRoleMap,
                                            Collection<String> myRoles) {
        if (userId == null || autoNodeRoleMap == null || autoNodeRoleMap.isEmpty()
                || myRoles == null || myRoles.isEmpty()) {
            return List.of();
        }
        // 不能「全量 PENDING + ORDER BY id DESC LIMIT 200」：在办任务到几万行后，
        // 每次都要走一遍索引反向扫 + 排序才能凑出 200 行，抖动大且占 todo/page 耗时首位。
        // 回捞只认「我持有的角色所对应的 AUTO 节点」，把 model_key/node_key 下推到 SQL 后
        // 候选集本来就小，排序也失去意义（"取最新的 200 条"只是近似，命中与否全看运气），故一并去掉。
        // 下面的 Java 过滤保持不动（口径不放宽）。
        Set<String> models = new LinkedHashSet<>();
        Set<String> nodes = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : autoNodeRoleMap.entrySet()) {
            if (!myRoles.contains(e.getValue())) {
                continue;
            }
            int i = e.getKey().indexOf(':');
            if (i > 0) {
                models.add(e.getKey().substring(0, i));
                nodes.add(e.getKey().substring(i + 1));
            }
        }
        if (models.isEmpty() || nodes.isEmpty()) {
            return List.of();
        }
        List<WfTask> rows = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, "PENDING")
                .isNotNull(WfTask::getAssigneeId)
                .ne(WfTask::getAssigneeId, userId)
                .in(WfTask::getModelKey, models)
                .in(WfTask::getNodeKey, nodes)
                .last("limit 200"));
        return rows.stream()
                .filter(r -> r.getTaskId() != null && r.getModelKey() != null && r.getNodeKey() != null)
                .filter(r -> myRoles.contains(autoNodeRoleMap.getOrDefault(
                        r.getModelKey() + ":" + r.getNodeKey(), "")))
                .map(WfTask::getTaskId)
                .toList();
    }

    /** 任务视图双写（WfEngineFacade 调用） */
    @Transactional(rollbackFor = Exception.class)
    public void recordTask(WfTask task) {
        task.setId(null);
        task.setStatus(task.getStatus() == null ? "PENDING" : task.getStatus());
        task.setUrgedCount(0);
        taskMapper.insert(task);
    }

    /**
     * 任务完成回写：置 DONE 并回写真实办理人，保证审计追溯落到实际操作人。
     * 候选组（角色）任务完成后 assignee_id 可能为空或为预指派值，必须覆盖为实际操作人。
     */
    public void markTaskDone(String taskId, Long operUserId) {
        WfTask upd = new WfTask();
        upd.setStatus("DONE");
        upd.setAssigneeId(operUserId);
        upd.setUpdateTime(LocalDateTime.now());
        taskMapper.update(upd, new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId));
    }

    /**
     * 催办次数落账：{@code WfTimeoutJob} 不能直接 {@code wfTaskMapper.updateById} 写
     * {@code urged_count}，那会绕过镜像表的统一办结点。催办计数是超时治理的判定依据（阶梯档按它决定是否升级、
     * 作业池按它决定是否回收），规则必须与其它镜像写同源，故上提到本服务。
     */
    public void bumpUrgedCount(String taskId, int urgedCount) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        WfTask upd = new WfTask();
        upd.setUrgedCount(urgedCount);
        upd.setUpdateTime(LocalDateTime.now());
        taskMapper.update(upd, new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId));
    }

    /**
     * 「卡单」阈值：同一待办累计催办满 N 次仍 PENDING ＝ 卡单。
     * 单一读取点，超时作业（发聚合告警）与看板（红牌）共用，避免两处规则漂移。
     * {@code sys_config.wf.timeout.stuck.alert.after_urges}；配 0/负数＝该机制关闭。
     */
    public static final String STUCK_AFTER_URGES_KEY = "wf.timeout.stuck.alert.after_urges";
    public static final int STUCK_AFTER_URGES_DEFAULT = 3;

    private final SysConfigMapper sysConfigMapper;
    /** 卡单阈值快照（5 秒 TTL）：看板/列表是在线路径，不能逐行查 sys_config */
    private volatile int stuckAfterUrges = STUCK_AFTER_URGES_DEFAULT;
    private volatile long stuckThresholdAt;

    /** 卡单阈值（读失败/未配置＝默认 3；配 0 或负数＝机制关闭，既不告警也不打标） */
    public int stuckAfterUrges() {
        long now = System.currentTimeMillis();
        if (now - stuckThresholdAt < 5_000L) {
            return stuckAfterUrges;
        }
        int v = STUCK_AFTER_URGES_DEFAULT;
        try {
            SysConfig c = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, STUCK_AFTER_URGES_KEY)
                            .last("limit 1"));
            String raw = c == null ? null : c.getConfigValue();
            if (raw != null && !raw.isBlank()) {
                v = Integer.parseInt(raw.trim());
            }
        } catch (NumberFormatException nf) {
            log.warn("卡单阈值配置 {} 非法，回退默认 {}", STUCK_AFTER_URGES_KEY, STUCK_AFTER_URGES_DEFAULT);
        } catch (Exception ex) {
            log.warn("读取卡单阈值失败，按默认 {} 处理: {}", STUCK_AFTER_URGES_DEFAULT, ex.getMessage());
        }
        stuckAfterUrges = v;
        stuckThresholdAt = now;
        return v;
    }

    /**
     * 是否卡单（派生态，<b>不写入数据库</b>）：催办次数已达阈值且仍在办。
     * 阈值 ≤0 时恒 false＝机制关闭，看板与告警同时熄火。
     */
    public boolean isStuck(Integer urgedCount) {
        int threshold = stuckAfterUrges();
        return threshold > 0 && urgedCount != null && urgedCount >= threshold;
    }

    /**  转办：待办视图受理人变更（Flowable assignee 已由门面同步改派，这里只改查询视图） */
    public void updateTaskAssignee(String taskId, Long targetUserId) {
        WfTask upd = new WfTask();
        upd.setAssigneeId(targetUserId);
        upd.setUpdateTime(LocalDateTime.now());
        taskMapper.update(upd, new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING"));
    }

    /**
     * 本线程<b>正在办结</b>的那个任务 id。
     *
     * <p>门面的次序是「先 {@code taskService.complete()}、后 {@code markTaskDone()}」，而 PROCESS_COMPLETED
     * 监听器在 complete() 内<b>同步</b>派发（{@code runInTenant}→{@code TenantContext.callAs} 是原地换线程变量，
     * 不换线程）。于是实例最后一条任务办结时，办结环节看到的镜像行仍是 PENDING——它其实正在被真人办结，
     * 只是 DONE 还没写。不排除这条，就会在收尾任务上写出"假作废记录"，
     * 而真正没人办过的那些 PENDING 行反倒一行记录都没有。
     * 故实例级/单任务级两条作废通道都必须排除本线程正在办结的这条。</p>
     *
     * <p>成对调用见 {@code WfEngineFacade} 的两处 {@code taskService.complete()}（人工办结、超时自动通过）。
     * 用 save/restore 而非 remove，是因为同线程可能嵌套办结（会签/子流程），内层不得抹掉外层的标记。</p>
     */
    private static final ThreadLocal<String> COMPLETING_TASK_ID = new ThreadLocal<>();

    /** 与 {@link #endCompletingTask} 成对使用（放在 try/finally 里）；返回上一层的标记供还原。 */
    public static String beginCompletingTask(String taskId) {
        String previous = COMPLETING_TASK_ID.get();
        COMPLETING_TASK_ID.set(taskId);
        return previous;
    }

    public static void endCompletingTask(String previous) {
        if (previous == null) {
            COMPLETING_TASK_ID.remove();
        } else {
            COMPLETING_TASK_ID.set(previous);
        }
    }

    /**
     * 实例最终状态办结：把该实例在待办视图（wf_task）的所有 PENDING 行置 CANCELLED，并逐条补节点级作废记录。
     *
     * <p>背景：引擎侧任务随实例结束被 Flowable 删除，但业务侧
     * wf_task 只有办理路径（markTaskDone→DONE）——取消（cancelInstance）、驳回
     * terminateAsRejected、会签/票签办结后同节点兄弟任务这三类场景都会留下
     * 「实例已最终状态、待办还挂着」的幽灵任务。</p>
     *
     * <p>CANCELLED 是视图清理最终状态，区别于「真实办理完」的 DONE；待办查询只取 PENDING，
     * 置任何非 PENDING 值都会让幽灵任务从待办消失，选 CANCELLED 是为语义可审计。</p>
     *
     * <p>只有状态没有记录等于没做一半——{@code wf_task.status='CANCELLED'} 的行里有相当一批
     * 在 {@code wf_task_history} 零行，所属实例全库只有一行 CANCEL、一行 WITHDRAW（且 {@code task_id} 为 NULL）。
     * 后果不是"少一行日志"，而是<b>追溯链上这一节凭空消失</b>：审批链读的是 wf_task_history，
     * 没有行就判 TODO（尚未到达），于是一单被驳回后链上显示"配置 6 节 / 已发生 2 节"，
     * 与"这些节确实到了、人确实被派了、只是没办理"不符。故「状态写＋记录写」必须成对。</p>
     *
     * @param reason 作废因由（写进记录 comment，调用方各自给本路径的规则）
     * @return 关闭的行数（仅日志/排障用途，失败不阻断主链路——调用方各自 try/catch）
     */
    @Transactional(rollbackFor = Exception.class)
    public int closePendingTasksByInstance(String procInstId, String reason) {
        if (procInstId == null || procInstId.isBlank()) return 0;
        // 加签收口（必须先于下面的"无待办即返回"）：
        //   流程被终止时引擎侧任务直接被删，wf_add_sign 上仍是 ACTIVE/PENDING 的行
        //   就成了一条"活着的加签挂在死流程上"——加签面板按 ACTIVE 展示，办理人打开
        //   找不到可办任务，事后也查不出它是随流程一起终止的。
        //   撤回/驳回终止/取消三条路径都经本方法收尾，故在这里一次性收口，
        //   而不是在每条路径各补一遍（漏一条就漏一类残留）。
        //   放在 early-return 之前：本方法被调用时待办行可能已被别的通道翻成 CANCELLED，
        //   此时 open 为空但加签行仍在。
        closeOpenAddSignsByInstance(procInstId, reason);
        // 本线程正在办结的任务不算"未办理"——它的镜像行只是还没被 markTaskDone 翻成 DONE。
        final String completing = COMPLETING_TASK_ID.get();
        // 先取待关的行：作废记录要按行写（task_id/node_key 是记录的主键语义，不能只留实例级一行）
        List<WfTask> open = new ArrayList<>(taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getProcInstId, procInstId)
                .eq(WfTask::getStatus, "PENDING")));
        if (completing != null && open.removeIf(t -> completing.equals(t.getTaskId()))) {
            log.info("实例完结跳过本线程正在办结的任务: procInstId={}, taskId={}", procInstId, completing);
        }
        if (open.isEmpty()) return 0;
        WfTask upd = new WfTask();
        upd.setStatus("CANCELLED");
        upd.setUpdateTime(LocalDateTime.now());
        LambdaQueryWrapper<WfTask> closeWhere = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getProcInstId, procInstId)
                .eq(WfTask::getStatus, "PENDING");
        if (completing != null) {
            closeWhere.ne(WfTask::getTaskId, completing);
        }
        int closed = taskMapper.update(upd, closeWhere);
        // 并发/双通道下若已被另一条路径翻走（返回 0），记录也已由那一方写好，此处不重复落行
        if (closed > 0) {
            recordVoidTrails(open, reason);
        }
        return closed;
    }

    /**
     * 把该实例上仍未收口的加签记录置 {@code CANCELLED}。
     *
     * <p>只动 {@code PENDING}/{@code ACTIVE} 两种"还没走完"的状态：{@code DONE} 是加签链正常
     * 走完、{@code CANCELLED} 是人工取消，两者都已有 {@code done_time}，不得被实例级收尾覆写。
     * {@code FAILED} 同理（后加签未能生效，已告知发起人）。</p>
     *
     * <p>终态取值用 {@link WfAddSign#STATUS_CANCELLED} 而不是新增一个"随流程终止"的状态：
     * 状态机已在用五种取值，加第六种会让下游按状态判断显示的地方（加签面板、审批链追溯）
     * 全部要改；终止原因写进 {@code reason} 列，语义由文案承载。</p>
     */
    private void closeOpenAddSignsByInstance(String procInstId, String reason) {
        try {
            int n = addSignMapper.update(null, new LambdaUpdateWrapper<WfAddSign>()
                    .eq(WfAddSign::getProcInstId, procInstId)
                    .in(WfAddSign::getStatus, WfAddSign.STATUS_PENDING, WfAddSign.STATUS_ACTIVE)
                    .set(WfAddSign::getStatus, WfAddSign.STATUS_CANCELLED)
                    .set(WfAddSign::getDoneTime, LocalDateTime.now())
                    .set(WfAddSign::getUpdateTime, LocalDateTime.now())
                    .set(WfAddSign::getReason, "随流程终止关闭：" + (reason == null ? "实例已结束" : reason)));
            if (n > 0) {
                log.info("实例 {} 收尾关闭进行中加签 {} 条：{}", procInstId, n, reason);
            }
        } catch (Exception ex) {
            // 加签收口失败不阻断主链路（任务/实例状态已经写好，回滚反而是更大的问题）
            log.warn("实例 {} 加签收口失败: {}", procInstId, ex.getMessage());
        }
    }


    /**
     * 单个任务的待办视图作废＋节点级作废记录。
     *
     * <p>引擎 {@code TASK_DELETED} 事件的投影落点：实例终止、会签/票签兄弟任务清理、改道搬执行等
     * 场景下引擎任务被删除，而 wf_task 的 PENDING 行若只走"实例级"关闭通道
     * （{@link #closePendingTasksByInstance}），会把同实例其他仍在办的任务一起关掉，语义过宽。
     * 故单开一条按 task_id 精确作废的口子。
     *
     * <p>只动 {@code status='PENDING'} 的行：已 DONE 的任务若随后触发 TASK_DELETED 不得被改写为 CANCELLED。
     * 同理，只有本方真的把行从 PENDING 翻走时才补作废记录，避免与实例级通道写下同一任务的重复行。
     *
     * @param reason 作废因由（写进记录 comment）
     * @return 关闭的行数（仅日志/排障用途）
     */
    @Transactional(rollbackFor = Exception.class)
    public int markTaskCancelled(String taskId, String reason) {
        if (taskId == null || taskId.isBlank()) {
            return 0;
        }
        // 本线程正在办结的任务不得作废（镜像行只是还没翻成 DONE，不是没办理）
        if (taskId.equals(COMPLETING_TASK_ID.get())) {
            log.info("单任务作废跳过本线程正在办结的任务: taskId={}", taskId);
            return 0;
        }
        List<WfTask> open = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING"));
        if (open.isEmpty()) {
            return 0;
        }
        WfTask upd = new WfTask();
        upd.setStatus("CANCELLED");
        upd.setUpdateTime(LocalDateTime.now());
        int closed = taskMapper.update(upd, new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING"));
        if (closed > 0) {
            recordVoidTrails(open, reason);
        }
        return closed;
    }

    /** 作废记录的动作码：既非办理（APPROVE/AUTO_PASS）也非用户撤销（CANCEL），单开一词 */
    public static final String ACTION_VOID = "VOID";

    /**
     * 加签记录的动作码。
     *
     * <p>它记的是"临时插一道"（前/后加签＋回跳各写一行），<b>不是这一节的定论</b>：
     * 既不得顶掉同节的真实办理行（按 id 倒序取"最后一行"时，加签行后落账会把该节判成
     * 「已通过 · 办理人＝加签人」），也不得给该节撑起一个序号（分母错位即由此而来）。
     * 本服务把它单独归堆、以 {@code addSigns} 附在宿主节上呈现。
     */
    public static final String ACTION_ADDSIGN = "ADDSIGN";

    /**
     * 加签<b>审结</b>行的动作码。
     *
     * <p>发起行与审结行若同写 {@link #ACTION_ADDSIGN}，审计表里两行只差一个 target_user_id，
     * 「这条加签完成了没有」只能靠 {@code wf_add_sign.status} 反推。现把审结（回跳）那一行改记本码；
     * <b>取消加签</b>仍走 {@code ADDSIGN}（它既非发起也非审结）。
     *
     * <p>语义与 {@code ADDSIGN} 完全同类——都是"临时插一道"，<b>不是这一节的定论</b>：
     * 因此下面所有「加签行归堆／不占序号」的判定一律走 {@link #isAddSignTrail(String)}，
     * 两个码同进同出，序号与分母规则不因本码新增而漂移。
     * 历史行不订正（审计表只增不改），老数据继续以 {@code ADDSIGN} 呈现为「加签」。
     */
    public static final String ACTION_ADDSIGN_DONE = "ADDSIGN_DONE";

    /** 加签记录判定：发起/取消（{@code ADDSIGN}）与审结（{@code ADDSIGN_DONE}）都算加签行。 */
    public static boolean isAddSignTrail(String action) {
        return ACTION_ADDSIGN.equals(action) || ACTION_ADDSIGN_DONE.equals(action);
    }

    /**
     * <b>非办理动作</b>的动作码：这些记录记录的是"这一节被催过/被指派过/被谁领走又放了"，
     * <b>不是这一节的结论</b>——单据仍在原处等人办，链上把它算成「已办理／已通过」就是把流程动作
     * 当成了审批意见（追责时读到的"这节批过了"根本没人批过）。
     *
     * <p>准入清单：办理/结论类
     * APPROVE、REJECT、AUTO_PASS、SUBMIT、REJECT_TO、REJECT_TO_DONE（驳回关联，仍是结论）＋
     * 实例级 APPROVED/REJECTED/WITHDRAW/CANCEL（已由 {@link #INSTANCE_LEVEL_ACTIONS} 排除）＋
     * VOID／ADDSIGN（各走专用通道）＋ <b>本集六个</b> URGE、ASSIGN、CLAIM、UNCLAIM、TRANSFER、ESCALATE。
     * 反例：一节里只有 {@code ASSIGN,URGE,VOID} 或 {@code UNCLAIM,CLAIM,ESCALATE,URGE} 这类行时，
     * "没有真实办理行就用存在性兜底"的判法会把该节判成已办——升级转办过四轮、一次没办，
     * 链上却显示「已通过」，而办理人为空。
     *
     * <p>只从"本节结论行"（{@code lastHis}）里排除，<b>不</b>从"到过这一节"（{@code trailNodes}）里排除：
     * 有催办/指派行的节点确实到过，/110 的分母规则一字不动。
     */
    public static final Set<String> NON_HANDLING_ACTIONS = Set.of(
            "URGE", "ASSIGN", "CLAIM", "UNCLAIM", "TRANSFER", "ESCALATE");

    /**
     * 待办视图作废的节点级记录。
     *
     * <p><b>操作人一律留空</b>：作废不是"某人把这个节点办完了"。{@code RequisitionWorkbenchService}
     * 的「我的已办」按 {@code oper_user_id} 计数，这里塞人就会把没办过的节算进已办（先例）；
     * 现网系统自写的 URGE 行同样是 {@code oper_user_id}/{@code oper_user_name} 双空规则。</p>
     *
     * <p>{@code duration} 按 {@code wf_task.create_time} → 现在补，
     * 这样"任务在谁手上挂了多久后被作废"在时间线上直接可读，不必人肉比时间戳。</p>
     */
    private void recordVoidTrails(List<WfTask> rows, String reason) {
        LocalDateTime now = LocalDateTime.now();
        WfInstance inst = null;
        boolean instLoaded = false;
        for (WfTask t : rows) {
            WfTaskHistory h = new WfTaskHistory();
            h.setTenantId(t.getTenantId() != null ? t.getTenantId() : TenantContext.getTenantId());
            h.setProcInstId(t.getProcInstId());
            h.setTaskId(t.getTaskId());
            h.setNodeKey(t.getNodeKey());
            h.setNodeName(t.getNodeName());
            h.setAction(ACTION_VOID);
            // biz_type NOT NULL：镜像行自带，缺失才回退实例行（实例行也没有时按 UNKNOWN 明示）
            h.setBizType(t.getBizType() != null ? t.getBizType() : "UNKNOWN");
            h.setBizId(t.getBizId());
            if (h.getBizId() == null) {
                // 待办行建得早、单据归属当时未解析的少量情形：回退实例行，否则追溯链按 biz_id 取不到这条作废
                if (!instLoaded) {
                    inst = instanceByProcInstId(t.getProcInstId());
                    instLoaded = true;
                }
                h.setBizId(inst == null ? null : inst.getBizId());
                if ("UNKNOWN".equals(h.getBizType()) && inst != null && inst.getBizType() != null) {
                    h.setBizType(inst.getBizType());
                }
            }
            h.setComment(truncateComment(reason));
            h.setOperTime(now);
            if (t.getCreateTime() != null) {
                h.setDuration(Duration.between(t.getCreateTime(), now).toMinutes());
            }
            recordHistory(h);
        }
    }

    /** comment 列宽 2000：撤销事由是自由文本，写侧不截断会撑爆列并连累状态变更一起回滚 */
    private String truncateComment(String comment) {
        return (comment == null || comment.length() <= 2000) ? comment : comment.substring(0, 1997) + "...";
    }

    /**
     * 引擎旁路推进的镜像办结。
     *
     * <p>空策略 {@code SKIP} 走的是 {@code NodeOrgResolver} 里的 {@code flowTaskService.complete()}——
     * 直接推引擎、绕开 {@code WfEngineFacade.complete()}。后果有二：
     * <ol>
     *   <li>{@code wf_task} 里该任务的 PENDING 行无人办结，流程继续走时它永远挂在待办（幽灵任务）；</li>
     *   <li>{@code wf_task_history} 缺这一段，审批时间线凭空断节。</li>
     * </ol>
     * 本方法把这两件事补上，使"旁路"与"正路"在镜像规则上完全一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordEngineBypass(String procInstId, String taskId, String nodeKey, String nodeName,
                                   String action, String comment) {
        if (taskId != null && !taskId.isBlank()) {
            // 旁路节点无真实办理人（空策略 SKIP 的前提就是 assignee 为空），
            // markTaskDone 传 null 不会覆盖 assignee（MP 的 update(entity,wrapper) 忽略 null 字段）。
            markTaskDone(taskId, null);
        }
        WfTaskHistory h = new WfTaskHistory();
        h.setProcInstId(procInstId);
        h.setTaskId(taskId);
        h.setNodeKey(nodeKey);
        h.setNodeName(nodeName);
        h.setAction(action);
        h.setComment(comment);
        // wf_task_history.oper_time 是 NOT NULL，而旁路行没有登录上下文可派生（正路的 oper_time 由
        // WfEngineFacade.writeHistory 落）。缺这一列 ⇒ 1048 ⇒ 整条 SKIP 冒泡成「镜像办结失败
        // [WF-MIRRO1]，已阻止引擎推进」，实例停在 RUNNING 且无 PENDING 行＝无人可办、无出口。
        h.setOperTime(LocalDateTime.now());
        WfInstance inst = (procInstId == null || procInstId.isBlank()) ? null : instanceByProcInstId(procInstId);
        h.setBizType(inst == null ? "UNKNOWN" : inst.getBizType());
        h.setBizId(inst == null || inst.getBizId() == null ? 0L : inst.getBizId());
        recordHistory(h);
    }

    /** 待办视图是否已有该任务的 PENDING 行（双账补账去重：uk_tenant_task_id 会拒绝重复插入） */
    public boolean pendingTaskExists(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        return taskMapper.selectCount(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING")) > 0;
    }

    /** 按 Flowable taskId 取当前 PENDING 镜像行（待办提醒取 candidate_ids 用；无行返回 null） */
    public WfTask pendingTaskByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING")
                .orderByDesc(WfTask::getId)
                .last("limit 1"));
    }

    /**
     * 释放（取消签收）：把待办视图的处理人清空、回到"仅候选人可办"态。
     * 必须用 LambdaUpdateWrapper 显式 set null——MyBatis-Plus 的 update(entity, wrapper) 忽略实体的
     * null 字段，用实体置空会静默失效（表现是"释放成功但待办还在原人名下"）。
     */
    public void clearTaskAssignee(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<WfTask>()
                .set(WfTask::getAssigneeId, null)
                .set(WfTask::getUpdateTime, LocalDateTime.now())
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING"));
    }

    // 审批历史（审计表，只增不改）

    /** 按单据号取流程实例（历史表不加 biz_no 列，单号维度在接口侧 join wf_instance 解析） */
    public List<WfInstance> instancesByBizNo(String bizNo) {
        if (bizNo == null || bizNo.isBlank()) {
            return List.of();
        }
        return instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getBizNo, bizNo.trim())
                .orderByDesc(WfInstance::getId));
    }

    /**
     * 审批历史分页。
     *
     * <p><b>单号维度</b>：{@code docs} 为由 {@code bizNo} 解析出的单据
     * 控制器已按归属过滤，按其 (biz_type,biz_id) 成对匹配——审计表是只增不改的存根，
     * 不为查询便利加列。
     *
     * <p><b>读侧办结</b>：{@code wf:history:list} 要授给全业务角色，
     * 无单据维度的整表查询就必须限制在<b>本人参与过</b>的流程（办理过 / 被转办加签点名 /
     * 当前待办在我名下或候选含我），否则等于把全租户的审批意见开放给任何持证角色。
     * 集团数据范围角色由调用方放行（不传 restrictToUserId）。
     */
    public PageResult<WfTaskHistory> historyPage(long pageNum, long pageSize, Long bizId, String bizType,
                                                 List<WfInstance> docs, Long restrictToUserId) {
        LambdaQueryWrapper<WfTaskHistory> qw = new LambdaQueryWrapper<>();
        if (docs != null) {
            List<WfInstance> keyed = docs.stream()
                    .filter(i -> i.getBizType() != null && i.getBizId() != null)
                    .collect(Collectors.toList());
            if (keyed.isEmpty()) {
                return PageResult.of(List.of(), 0L, pageNum, pageSize);
            }
            qw.and(w -> {
                for (WfInstance i : keyed) {
                    w.or(x -> x.eq(WfTaskHistory::getBizType, i.getBizType())
                            .eq(WfTaskHistory::getBizId, i.getBizId()));
                }
            });
        }
        if (bizId != null) {
            qw.eq(WfTaskHistory::getBizId, bizId);
        }
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(WfTaskHistory::getBizType, bizType);
        }
        if (restrictToUserId != null) {
            qw.inSql(WfTaskHistory::getProcInstId, involvedInstancesSql(restrictToUserId));
        }
        qw.orderByDesc(WfTaskHistory::getId);
        Page<WfTaskHistory> p = historyMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // 089：分页列表按"最新在前"呈现，实例级定论行要排在同实例末节办理行<b>之前</b>
        // 才符合因果（它写的其实是"整单结论"这个最后发生的事件）。写入数据库序、SQL 排序都不动（审计表
        // 只增不改，也不给读侧加 case-when 表达式——一旦解析出问题影响的是整张历史页），
        // 只在返回前按 {@link #causalOrder} 重排<b>本页</b>：每枚实例至多一行定论行，跨页不互相顶位。
        return PageResult.of(causalOrder(p.getRecords(), true), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 「本人参与过的流程实例」子查询，供 {@link #historyPage} 的读侧收敛用。
     *
     * <p><b>为什么不再写成相关子查询</b>：原来的写法是
     * {@code oper_user_id=? OR target_user_id=? OR EXISTS(select 1 from wf_task t where
     * t.proc_inst_id = wf_task_history.proc_inst_id and (t.assignee_id=? or find_in_set(?, t.candidate_ids)))}。
     * 三路 OR 使两枚单列索引都用不上，外层退化为审计表全扫（161 万行拆在 22 个月分区里），
     * 而每行再对 wf_task（163 万行、无 proc_inst_id 索引）做一次相关子查询 ⇒ 实测超 120 秒不返回，
     * 前端「我的流程历史」直接卡死。
     *
     * <p><b>现在的写法</b>：先一次性物化出实例集合，再用单枚 {@code proc_inst_id IN (…)} 走
     * {@code idx_tenant_inst} 范围扫描。集合由四条各有索引的查询拼成——
     * 待办/已办在我名下（{@code idx_tenant_assignee}）、候选含我且仍在办（池任务候选，按 PENDING 收窄）、
     * 我操作过、我被转办加签点名（{@code idx_tenant_user}／{@code idx_wth_tenant_target}）。
     * 实测同一账号：改前 120 秒仍不返回，改后 0.38 秒。
     *
     * <p>必须套一层 {@code SELECT proc_inst_id FROM (…UNION…) d}：直接把 UNION 放在 IN 里，
     * 优化器会把它退化成对外层的逐行相关求值，又回到原病（实测 60 秒超时）。
     * 子查询里显式写 {@code tenant_id}——租户拦截器只补主表，不补原生 SQL 片段。
     */
    private String involvedInstancesSql(Long userId) {
        long t = TenantContext.getTenantId();
        return "SELECT proc_inst_id FROM ("
                + "SELECT proc_inst_id FROM wf_task WHERE tenant_id = " + t
                + " AND assignee_id = " + userId
                + " UNION SELECT proc_inst_id FROM wf_task WHERE tenant_id = " + t
                + " AND status = 'PENDING' AND find_in_set(" + userId + ", candidate_ids)"
                + " UNION SELECT proc_inst_id FROM wf_task_history WHERE tenant_id = " + t
                + " AND oper_user_id = " + userId
                + " UNION SELECT proc_inst_id FROM wf_task_history WHERE tenant_id = " + t
                + " AND target_user_id = " + userId
                + ") d";
    }

    /** 审批历史写入数据库（只增不改，审计表） */
    @Transactional(rollbackFor = Exception.class)
    public void recordHistory(WfTaskHistory history) {
        history.setId(null);
        completeTerminalHistoryRow(history);
        fillOperatorDept(history);
        historyMapper.insert(history);
    }

    /**
     * 审批历史行的操作人部门名补齐。
     *
     * <p><b>缺陷</b>：{@code wf_task_history.oper_dept_name} 一直是空的，
     * 而同一方法名下的 {@code oper_user_name} 是有值的——历史页/联查面板要回答"这一票是哪个部门办的"
     * （共享中心派工、组织维度审批质量报表都读这一列）时只能空着；{@code sys_oper_log} 那一面
     * 已由 {@code OperLogAspect} 补齐，审计历史表这一面还漏着。
     *
     * <p><b>为什么写在这个方法里</b>：{@code historyMapper.insert} 在本类只有 {@link #recordHistory}
     * 一个出口（引擎门面 writeHistory／投影服务／抄送与定论行都走它），在这里补一次就覆盖全部写入位置，
     * 不必逐处补查询，也不必改动门面。
     *
     * <p><b>规则</b>：{@code oper_user_id} → {@code sys_user.dept_id} → {@code sys_dept.dept_name}，
     * 取的是<b>办理当时</b>该用户的部门（人后来调部门，历史行不回填——审计表只增不改）。
     * 写入位置自己给了部门名就让位，不覆盖。
     *
     * <p>失败语义＝留空，绝不抛：sys_user / sys_dept 都是租户过滤表，机器线程（超时自动通过、
     * 定时收尾）里 {@link TenantContext} 缺失时会直接抛 {@code TENANT_CONTEXT_MISSING}；部门名是审计
     * 增强项，不能因为它把整条历史记录写不进库（同 {@code OperLogAspect.resolveDeptName} 的取舍）。
     */
    private void fillOperatorDept(WfTaskHistory h) {
        try {
            if (h.getOperUserId() == null) {
                return;
            }
            if (h.getOperDeptName() != null && !h.getOperDeptName().isBlank()) {
                return;
            }
            SysUser u = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .select(SysUser::getDeptId)
                    .eq(SysUser::getId, h.getOperUserId())
                    .last("limit 1"));
            if (u == null || u.getDeptId() == null || u.getDeptId() <= 0L) {
                return;
            }
            SysDept dept = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                    .select(SysDept::getDeptName)
                    .eq(SysDept::getId, u.getDeptId())
                    .last("limit 1"));
            String name = dept == null || dept.getDeptName() == null ? null : dept.getDeptName().trim();
            if (name != null && !name.isEmpty()) {
                h.setOperDeptName(name);
            }
        } catch (Exception ex) {
            log.warn("审批历史操作人部门反查失败 operUserId={}: {}", h.getOperUserId(), ex.getMessage());
        }
    }

    /**
     * 流程级收尾动作码：{@code APPROVED}/{@code REJECTED} 由 PROCESS_COMPLETED 监听器写，
     * 记的是「整个流程定论」而不是「某一节被谁办掉了」。
     */
    public static final Set<String> TERMINAL_ACTIONS = Set.of("APPROVED", "REJECTED");

    /**
     * 实例级动作全集：除流程定论 {@code APPROVED}/{@code REJECTED} 外，
     * 撤回（{@code WITHDRAW}，WfEngineFacade:5680）与管理/运维终止（{@code CANCEL}，WfEngineFacade:3192）
     * 同样是「整枚实例的结论」而不是「某一节被谁办掉了」——三个写入位置一律传 {@code nodeKey=null}
     * 表达这个语义，故与 {@link #TERMINAL_ACTIONS} 同批补定位信息（见
     * {@code completeTerminalHistoryRow}），并在所有"按节聚合"的读数通道里一起排除。
     *
     * <p>与 {@link #TERMINAL_ACTIONS} 的分工是刻意的：前者还兼作「我的已办」排除集
     * （{@code RequisitionWorkbenchService} 按 {@code oper_user_id} 计数），撤回/终止行本来就带
     * 操作人、也已经计进已办，把它们并进去会改变已办规则；本集合只管<b>读侧的"按节"聚合与排序</b>。
     */
    public static final Set<String> INSTANCE_LEVEL_ACTIONS = Set.of("APPROVED", "REJECTED", "WITHDRAW", "CANCEL");

    /** 是否实例级定论行（/089 展示排序与联查透出用） */
    public static boolean isInstanceLevelTrail(WfTaskHistory h) {
        return h != null && h.getAction() != null && INSTANCE_LEVEL_ACTIONS.contains(h.getAction());
    }

    /**
     * 审批轨迹的「因果序」重排。
     *
     * <p><b>缺陷</b>：实例级定论行由 {@code PROCESS_COMPLETED} 监听器在 {@code taskService.complete()}
     * <b>之内</b>同步落账，而末节的办理行（{@code APPROVE}）是门面在 {@code complete()} <b>返回之后</b>
     * 才写的——于是定论行的 {@code id} 更小（两者常落在同一秒，{@code id} 却已先后倒置），
     * 按 id 排与按时间排给出<b>两种</b>链路顺序，两者都会读出"流程先结束、末节才审批"的假因果。
     *
     * <p>取排序键（本方法），不动写序：两行的时间戳都是真实时钟，审计表只增不改
     * 「改规则不删史」；把定论行的 {@code oper_time} 拨到末节之后
     * 等于伪造审计时间；改 {@code complete()} 的写序则要动引擎回调时序，代价与风险都大得多。
     * 故在<b>读数侧</b>按"事件语义层级"排：实例级定论行恒排在同实例的节点行之后（正序）／之前
     * 倒序＝最新在前，层级内部仍按 {@code id}（＝落账序）走。写入数据库序与本方法互不影响。
     *
     * <p>{@code id} 为空的行（理论上不存在，审计表自增主键）按"最大落账序"处理，即正序排同层末尾、
     * 倒序排同层首位，不猜它在中间的哪个位置。
     *
     * @param newestFirst true＝最新在前（分页列表），false＝时间正序（时间线/轨迹）
     */
    public static List<WfTaskHistory> causalOrder(List<WfTaskHistory> rows, boolean newestFirst) {
        if (rows == null || rows.size() < 2) {
            return rows;
        }
        List<WfTaskHistory> out = new ArrayList<>(rows);
        Comparator<WfTaskHistory> byId = Comparator.comparingLong(
                h -> h.getId() == null ? Long.MAX_VALUE : h.getId());
        out.sort((a, b) -> {
            int la = isInstanceLevelTrail(a) ? 1 : 0;
            int lb = isInstanceLevelTrail(b) ? 1 : 0;
            if (la != lb) {
                // 正序：定论行沉底（最后发生的是"结论"）；倒序＝最新在前：定论行置顶
                return newestFirst ? Integer.compare(la, lb) : Integer.compare(lb, la);
            }
            return newestFirst ? byId.reversed().compare(a, b) : byId.compare(a, b);
        });
        return out;
    }

    /**
     * 最终状态历史行的定位信息补全——{@code node_key}/{@code task_id}/{@code oper_user_id}
     * 三列全空，事后只看得到一行光秃秃的 APPROVED，
     * 「流程是在哪一节、由谁的最后一票定论的」在审计表里查不出来，联查/追责全靠猜。
     *
     * <p><b>为什么写在这里而不是写入位置</b>：唯一的产出方是 {@code WfEngineFacade} 的
     * PROCESS_COMPLETED 监听器，而它拿到的只有流程变量
     * approved/comment，没有节点与办理人。本方法是所有历史行的唯一出口，故在这里补。
     *
     * <p><b>查询范围</b>：最终状态行<b>先于</b>最后一节的办理行写入数据库——门面在 {@code taskService.complete()}
     * 内同步触发本事件，而它自己的 {@code writeHistory(APPROVE)} 在 complete() <b>之后</b>。
     * 所以不能"回看最后一条办理行"，
     * 那样会取到<b>上一节</b>的人和节点。这里改取镜像 {@code wf_task} 里该实例 id 最大的一行
     * = 最后派生的人工节点，节点键/节点名/任务号一次给全；办理人优先该行的 {@code assignee_id}
     * （已认领场景，与最后一条 APPROVE 行的操作人一致），未认领（候选组任务、镜像 assignee 为空）
     * 时退回"同一节点已有的真实办理行"按节点键精确匹配——取不到就<b>保持为空</b>，
     * 绝不把"当时恰好点按钮的人"以外的猜测写进审计表。
     *
     * <p><b>刻意不补的两项</b>：①{@code duration} 不补——{@code DispatchPolicyService} 的节点平均停留
     * 按 {@code duration IS NOT NULL} 取样，最终状态行是"收尾"不是"停留"，补了就是把最后一节的时长算两遍；
     * ②写入位置已给 {@code node_key}/{@code task_id} 即整体让位、{@code oper_user_id} 有值即不覆盖
     * （逐列判定：撤回/终止行带操作人但缺节点信息，只补节点那一半）。
     *
     * <p>连带两处护栏（都是为了让"补全"不改变既有语义）：
     * {@link #chain(String)} 的节点聚合会跳过收尾行，否则驳回路径上收尾行会后落账并把该节判成「已通过」；
     * 「我的已办」按 {@code oper_user_id} 计数/列示，收尾行归入 {@code TERMINAL_ACTIONS} 排除，
     * 否则一单办结就给办理人凭空加一条已办（同因）。
     */
    private void completeTerminalHistoryRow(WfTaskHistory h) {
        try {
            if (h.getProcInstId() == null || h.getProcInstId().isBlank()) {
                return;
            }
            // 补全范围由「流程定论」两码扩到实例级动作全集（＋WITHDRAW／CANCEL），
            // 撤回行同样要答得出"从哪一节撤回的"。改为逐列判定：写入位置已给 node_key/task_id 即让位
            // 原三列全空才补的规则保留其意图，oper_user_id 有值则不动——撤回/终止行的操作人
            // 本来就是点按钮的人，与 「记录只认操作人本人」一致。
            if (!INSTANCE_LEVEL_ACTIONS.contains(h.getAction())) {
                return;
            }
            if (h.getNodeKey() != null || h.getTaskId() != null) {
                return;
            }
            WfTask last = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getProcInstId, h.getProcInstId())
                    .isNotNull(WfTask::getNodeKey)
                    .orderByDesc(WfTask::getId)
                    .last("limit 1"));
            if (last == null || last.getNodeKey() == null) {
                return;
            }
            h.setNodeKey(last.getNodeKey());
            h.setNodeName(last.getNodeName());
            h.setTaskId(last.getTaskId());
            if (h.getOperUserId() == null) {
                Long operator = last.getAssigneeId() != null ? last.getAssigneeId()
                        : lastNodeHandler(h.getProcInstId(), last.getNodeKey());
                if (operator != null) {
                    h.setOperUserId(operator);
                    h.setOperUserName(namesByIds(Set.of(operator)).get(operator));
                }
            }
        } catch (Exception ex) {
            // 收尾行的定位信息是附加项：补不上就维持原样，绝不能把记录本身（失败会冒泡的那条链路）带崩
            log.warn("终态历史行定位信息补全失败 procInstId={} action={}: {}",
                    h.getProcInstId(), h.getAction(), ex.getMessage());
        }
    }

    /** 该节最近一次真实办理人（最终状态行后落账的场景才取得到，如驳回终止）；取不到返回 null 表示留空 */
    private Long lastNodeHandler(String procInstId, String nodeKey) {
        List<WfTaskHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .select(WfTaskHistory::getOperUserId)
                .eq(WfTaskHistory::getProcInstId, procInstId)
                .eq(WfTaskHistory::getNodeKey, nodeKey)
                .in(WfTaskHistory::getAction, "APPROVE", "REJECT", "AUTO_PASS")
                .isNotNull(WfTaskHistory::getOperUserId)
                .orderByDesc(WfTaskHistory::getId)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0).getOperUserId();
    }

    /**
     * 审批历史按条件取回（只读，供机器路径自查，如委托回跳要找回"当初改派了哪几条"）。
     * 条件由调用方给全（action + 操作人 + 目标人 + 时间窗），本方法不加业务规则，避免两处规则漂移。
     */
    public List<WfTaskHistory> listHistory(LambdaQueryWrapper<WfTaskHistory> wrapper) {
        return historyMapper.selectList(wrapper);
    }

    // 审批链全景

    /**
     * 单据审批链全景：按流程配置的节点顺序给出每一节的状态、办理人、时间与意见。
     *
     * <p>填单侧与审批侧都要回答「走到哪一节了」，但节点配置读接口挂的是配置权限
     * wf:node:list，业务角色拿不到；这里按流程实例聚合 wf_node_config +
     * wf_task_history + wf_task 后一次给出，前端不再拼三份数据。
     *
     * @param procInstId Flowable 流程实例 ID（UUID 不可猜测，tenant_id 由框架自动过滤）
     */
    public List<Map<String, Object>> chain(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            return List.of();
        }
        WfInstance inst = instanceByProcInstId(procInstId);
        List<WfTaskHistory> his = historyMapper.selectList(new LambdaQueryWrapper<WfTaskHistory>()
                .eq(WfTaskHistory::getProcInstId, procInstId)
                .orderByAsc(WfTaskHistory::getId));
        List<WfTask> pending = taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getProcInstId, procInstId)
                .eq(WfTask::getStatus, "PENDING")
                .orderByAsc(WfTask::getId));
        if (inst == null && his.isEmpty() && pending.isEmpty()) {
            return List.of();
        }
        String modelKey = inst != null ? inst.getModelKey() : null;
        if (modelKey == null) {
            // 视图行缺失的老单据：流程 key 与当前办理人退回 wf_task / 历史里取
            WfTask any = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                    .eq(WfTask::getProcInstId, procInstId)
                    .orderByDesc(WfTask::getId).last("limit 1"));
            if (any != null) {
                modelKey = any.getModelKey();
            }
        }
        final String key = modelKey;
        List<WfNodeConfig> cfgs = key == null ? List.of()
                : nodeConfigMapper.selectList(new LambdaQueryWrapper<WfNodeConfig>()
                        .eq(WfNodeConfig::getModelKey, key)
                        .orderByAsc(WfNodeConfig::getSort));

        // 同一节点会签、驳回重提后再走：以最后一次办理记录为准
        Map<String, WfTaskHistory> lastHis = new LinkedHashMap<>();
        // 加签记录单独归堆（节点键 → 各道加签），不参与"这一节谁定的论"的聚合
        Map<String, List<Map<String, Object>>> addSignTrail = new LinkedHashMap<>();
        Map<String, String> addSignNodeNames = new LinkedHashMap<>();
        // 110：记录过的节点键（不含收尾行/加签行）——分母的另一侧真相源
        Set<String> trailNodes = new HashSet<>();
        for (WfTaskHistory h : his) {
            if (h.getNodeKey() == null) continue;
            // 收尾行现在也带 node_key（"流程在哪一节定论"），但它不是这一节的办理行——
            // 参与聚合会让驳回路径上后落账的收尾行把该节从「已驳回」改判成「已通过」。
            // 排除集含定论两码与实例级全集（WITHDRAW／CANCEL）：它们同样不是某一节的结论，
            // 不跳过就会把被撤回/被终止的那一节判成"这一节的结论是撤回"。
            if (INSTANCE_LEVEL_ACTIONS.contains(h.getAction())) continue;
            // 加签行不是本节的定论行，抽出来单独呈现（宿主节的序号与办理人都不受它影响）
            // 发起（ADDSIGN）与审结（ADDSIGN_DONE）同归这一堆，判定归口为一
            if (isAddSignTrail(h.getAction())) {
                addSignTrail.computeIfAbsent(h.getNodeKey(), k -> new ArrayList<>()).add(addSignRow(h));
                if (h.getNodeName() != null && !h.getNodeName().isBlank()) {
                    addSignNodeNames.putIfAbsent(h.getNodeKey(), h.getNodeName());
                }
                continue;
            }
            trailNodes.add(h.getNodeKey());
            // 催办/指派/领取/释放/转办/升级只是流程动作，不是这一节的结论——不进 lastHis，
            // 否则只写过这类行的节会被 chainNode 判成「已通过」（办理人还会显示成催办人）。
            // 上面的 trailNodes 照常登记："到过这一节"与"这一节办完了"是两件事（091/110 分母不动）。
            if (NON_HANDLING_ACTIONS.contains(h.getAction())) {
                continue;
            }
            // 作废行不代表这一节办过了。会签"一人签、兄弟被作废"时，
            // 后落账的作废行不得把该节从「已通过」改判成「已作废」，故只在没有真实办理行时才用它。
            WfTaskHistory kept = lastHis.get(h.getNodeKey());
            if (ACTION_VOID.equals(h.getAction()) && kept != null && !ACTION_VOID.equals(kept.getAction())) {
                continue;
            }
            lastHis.put(h.getNodeKey(), h);
        }
        // 110：分母的另一半真相源＝本实例<b>实际生成过任务</b>的节点（不限 PENDING：
        // DONE/CANCELLED 都是"这一节真的到过"）。配置里从未生成过的节（条件分支没命中、网关走了另一条）
        // 永远不会到，不能占分母——这就是"配置 14 节实走 6 节""配置 4 节而 3 节即 END"的根因。
        Set<String> everTaskNodes = new HashSet<>();
        for (WfTask t : taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .select(WfTask::getNodeKey)
                .eq(WfTask::getProcInstId, procInstId))) {
            if (t.getNodeKey() != null) {
                everTaskNodes.add(t.getNodeKey());
            }
        }
        Map<String, WfTask> pendByNode = new LinkedHashMap<>();
        for (WfTask t : pending) {
            if (t.getNodeKey() != null) pendByNode.putIfAbsent(t.getNodeKey(), t);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        // 引擎在办任务未必同步到 wf_task 镜像（历史缺陷），用 wf_instance.current_node 名称兜底定位当前节
        String curNode = inst != null ? inst.getCurrentNode() : null;
        // 配套读数：实例是否已最终状态。最终状态后链上"从未生成、从未记录"的配置节永远不会再走，
        // 不再计入分母（RUNNING 且无法建图时维持旧规则＝全计入，fail-open，不冤枉老版本定义的在途单）。
        final boolean terminal = inst != null && inst.getStatus() != null && !"RUNNING".equals(inst.getStatus());
        Map<Long, String> userNames = loadUserNames(pending);
        // 分支感知。从在办节沿 BPMN 图做前向可达——可达者维持 TODO（真的还没到），
        // 不可达者标 SKIPPED（走了排他网关的另一条分支，这些节永远不会到）。否则合并分档模型（招待/差旅/金额档）
        // 会把未走分支全部列成 TODO＝假"没走到"。图缺失/在办节不在图上（老定义在途单）一律 fail-open 不标注。
        Set<String> forward = forwardReachable(key, pendByNode.keySet());
        for (WfNodeConfig c : cfgs) {
            // 节点名统一取运行时名。wf_node_config.node_name 与运行时名
            // （wf_task / wf_task_history 的 node_name，源自 bpmn 定义）常常不同字，
            // 而待办列表「当前节点」列取的是运行时名 ⇒ 同一个审批弹窗里并存两套节点名，
            // 看起来像两条不同的链。有运行时名即以它为准，配置名只兜底从未生成过任务/记录的节。
            WfTask pendTask = pendByNode.get(c.getNodeKey());
            WfTaskHistory lastTrail = lastHis.get(c.getNodeKey());
            String runtimeName = pendTask != null ? pendTask.getNodeName()
                    : (lastTrail != null ? lastTrail.getNodeName() : null);
            String nodeName = runtimeName != null && !runtimeName.isBlank() ? runtimeName : c.getNodeName();
            Map<String, Object> node = chainNode(c.getNodeKey(), nodeName, lastTrail,
                    pendTask, userNames, curNode);
            if ("TODO".equals(node.get("state")) && forward != null && !forward.contains(c.getNodeKey())) {
                node.put("state", "SKIPPED");
            }
            out.add(node);
            if (c.getNodeKey() != null) emitted.add(c.getNodeKey());
        }
        // 节点配置缺失或 key 改过：按实际轨迹补上，链上不丢节点
        for (WfTaskHistory h : his) {
            // 同上，收尾行不参与"这一节"的呈现（它落账常早于该节真实办理行，
            // 不跳过就会把该节显示成收尾行、并把真实办理行挤成重复行）；一并跳 WITHDRAW/CANCEL
            if (INSTANCE_LEVEL_ACTIONS.contains(h.getAction())) continue;
            // 加签行改由下方"单独补行"通道处理（不进序号分母），此处不再当作一节
            // 审结行同样不参与补节（它与发起行同堆）
            if (isAddSignTrail(h.getAction())) continue;
            // 补行同样不能把催办/指派/领取/释放/转办/升级当成该节的结论——
            // 节点仍然上链（轨迹不丢），但以"没有结论行"呈现（state=TODO/CURRENT），与上方聚合规则一致。
            WfTaskHistory asConclusion = NON_HANDLING_ACTIONS.contains(h.getAction()) ? null : h;
            if (h.getNodeKey() != null && emitted.add(h.getNodeKey())) {
                out.add(chainNode(h.getNodeKey(), h.getNodeName(), asConclusion, pendByNode.get(h.getNodeKey()), userNames, curNode));
            }
        }
        for (WfTask t : pending) {
            if (t.getNodeKey() != null && emitted.add(t.getNodeKey())) {
                out.add(chainNode(t.getNodeKey(), t.getNodeName(), lastHis.get(t.getNodeKey()), t, userNames, curNode));
            }
        }
        // 加签留下的配置外节点键（宿主节不在 wf_node_config，或配置行改过键）单独补一行。
        // 它带着 addSignOnly 标记 ⇒ 看得到"这一节被人加签过"，但不占序号、不进分母。
        for (Map.Entry<String, List<Map<String, Object>>> e : addSignTrail.entrySet()) {
            if (!emitted.add(e.getKey())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeKey", e.getKey());
            String nm = addSignNodeNames.get(e.getKey());
            m.put("nodeName", nm == null || nm.isBlank() ? e.getKey() : nm);
            m.put("state", ACTION_ADDSIGN);
            m.put("action", ACTION_ADDSIGN);
            m.put("handler", null);
            m.put("time", e.getValue().get(e.getValue().size() - 1).get("time"));
            m.put("comment", null);
            m.put("addSignOnly", Boolean.TRUE);
            out.add(m);
        }
        // 加签痕迹挂回宿主节（前端据此"单独呈现"：一格「含加签 N 道」，而不是凭空多一节）
        for (Map<String, Object> n : out) {
            List<Map<String, Object>> as = n.get("nodeKey") == null
                    ? null : addSignTrail.get(String.valueOf(n.get("nodeKey")));
            if (as != null) {
                n.put("addSign", Boolean.TRUE);
                n.put("addSignCount", as.size());
                n.put("addSigns", as);
            }
        }
        // 110/147：序号与分母按"实际会经过的节点序列"给，不再按配置节点数
        applySequenceScope(out, everTaskNodes, trailNodes, terminal, forward);
        return out;
    }

    /** 加签记录行 → 链上附加信息（只给"谁、何时、说了什么"，不给状态位） */
    private Map<String, Object> addSignRow(WfTaskHistory h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", h.getOperTime());
        m.put("handler", h.getOperUserName());
        m.put("comment", h.getComment());
        m.put("targetUserId", h.getTargetUserId());
        // 把"发起／审结"这一区分从动作码带出来。历史上两类行的 action 都是 ADDSIGN，
        // 链上的加签明细列只能逐条写"加签"，看不出这一道签完了没有；现在前端按 phase 直读
        // （历史老行仍是 ADDSIGN ⇒ phase 归 OPEN，语义为"发起/未分型"，不误报已审结）。
        m.put("action", h.getAction());
        m.put("phase", WfQueryService.ACTION_ADDSIGN_DONE.equals(h.getAction()) ? "DONE" : "OPEN");
        return m;
    }

    /**
     * 审批链的序号与分母。
     *
     * <p><b>缺陷</b>：链的格子和「第 N/M 节」的分母若一律取 {@code wf_node_config} 的行数，
     * 就等于把"这个流程<b>可能</b>走哪些节"当成了"这一单<b>实际</b>走哪些节"——
     * 配置十几节而实走六节、运行到中途即 END、加签再插一道，
     * 都会让分母虚高，审批人据此误判自己处在链条后段、剩余周期比实际长得多。
     *
     * <p>规则（真相源＝已生成 ∪ 已记录，再看网关前向可达）：
     * <ul>
     *   <li>本实例真生成过任务（{@code wf_task} 任意状态）或真留过痕（{@code wf_task_history} 办理行）
     *       ⇒ 一定进分母：这一节确实到过；</li>
     *   <li>在办节（CURRENT）⇒ 进分母，即使镜像行缺失（老缺陷：引擎任务未必同步 {@code wf_task}）；</li>
     *   <li>尚未到达的节：<b>实例已最终状态</b> ⇒ 不进（永远不会再走）；
     *       在途且能建 BPMN 前向图 ⇒ 只有前向可达的才进（{@code SKIPPED} 按此判据，两处不重复判定）；
     *       在途但建不出图／实例行缺失（老单据读不到 modelKey）⇒ <b>fail-open 全进</b>，
     *       绝不把真会走的节判成不走；</li>
     *   <li>加签留下的节（{@code addSignOnly}）⇒ 永远不进分母，单独呈现。</li>
     * </ul>
     *
     * <p><b>与单据生命周期那条链的关系</b>：那条管的是「记账/付款/组批/归档」四段
     * 的最终状态计数与 {@code segmentSupport} 档位，规则在 {@code BillChainTimeline}/{@code BillTraceChain} 侧；
     * 本方法只改审批链内部的<b>节</b>序号，两套规则互不覆盖、互不回退。
     *
     * <p>下发字段是纯增量：旧前端只读 {@code nodeKey/nodeName/state/...} 时行为不变，
     * {@code counted}（是否进分母）／{@code seq}（第几节，不进分母的为 null）／{@code seqTotal}（分母）
     * 供新旧包共存期任一读取方直接使用。
     */
    private void applySequenceScope(List<Map<String, Object>> out, Set<String> everTaskNodes,
                                    Set<String> trailNodes, boolean terminal, Set<String> forward) {
        int total = 0;
        for (Map<String, Object> n : out) {
            String state = String.valueOf(n.get("state"));
            String nodeKey = n.get("nodeKey") == null ? null : String.valueOf(n.get("nodeKey"));
            boolean arrived = (nodeKey != null && (everTaskNodes.contains(nodeKey) || trailNodes.contains(nodeKey)))
                    || "CURRENT".equals(state);
            boolean stillToCome = !terminal && (forward == null || (nodeKey != null && forward.contains(nodeKey)));
            boolean counted = !Boolean.TRUE.equals(n.get("addSignOnly"))
                    && !"SKIPPED".equals(state)
                    && (arrived || stillToCome);
            n.put("counted", counted);
            if (counted) {
                n.put("seq", ++total);
            }
        }
        for (Map<String, Object> n : out) {
            n.put("seqTotal", total);
        }
    }

    /** 在办节点的 assignee_id 是用户 ID，链上要么显示姓名要么不显示，不能把裸 ID 丢给业务看 */
    private Map<Long, String> loadUserNames(List<WfTask> pending) {
        Set<Long> ids = new HashSet<>();
        for (WfTask t : pending) {
            if (t.getAssigneeId() != null) {
                ids.add(t.getAssigneeId());
            }
            ids.addAll(parseCandidateIds(t.getCandidateIds()));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return namesByIds(ids);
    }

    /** 用户 ID → 显示名（nick_name 优先，回退登录号）；全局唯一的取名规则，别再另写一份 */
    private Map<Long, String> namesByIds(Set<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
            names.put(u.getId(), u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName());
        }
        return names;
    }

    /** 链上单节：state = DONE 已过 / REJECTED 已驳回 / VOID 到达但未办理即作废 / CURRENT 当前停留 / TODO 尚未到达 / SKIPPED 分支未走 */
    private Map<String, Object> chainNode(String nodeKey, String nodeName, WfTaskHistory h,
                                          WfTask open, Map<Long, String> userNames, String curNode) {
        Map<String, Object> m = new LinkedHashMap<>();
        String name = nodeName == null || nodeName.isBlank() ? nodeKey : nodeName;
        m.put("nodeKey", nodeKey);
        m.put("nodeName", name);
        boolean current = open != null || (h == null && curNode != null && curNode.equals(name));
        String state;
        if (current) {
            state = "CURRENT";
        } else if (h == null) {
            state = "TODO";
        } else if (ACTION_VOID.equals(h.getAction())) {
            // 既不能算「已通过」（没人办过），也不能算「尚未到达」（任务确实派到过这一节）
            state = ACTION_VOID;
        } else {
            state = "REJECT".equals(h.getAction()) ? "REJECTED" : "DONE";
        }
        m.put("state", state);
        m.put("action", h == null ? null : h.getAction());
        // 当前节在办人只认镜像行：wf_instance.current_assignee 由引擎写成"上一节操作人"，语义不同不能借用
        m.put("handler", current
                ? (open == null || open.getAssigneeId() == null ? null : userNames.get(open.getAssigneeId()))
                : (h == null ? null : h.getOperUserName()));
        m.put("time", open != null ? open.getCreateTime() : (h == null ? null : h.getOperTime()));
        m.put("comment", h == null ? null : h.getComment());
        return m;
    }

    // chain 分支感知

    /**
     * 从在办节沿 BPMN 图做前向可达（忽略网关条件——排他网关只会走一条，两条都算"可达"是保守规则，
     * 只要不把别的分支标成 TODO 即可）。返回 null 表示无法建图或入参不在图上 ⇒ 调用方不做任何 SKIPPED 标注：
     * <ul>
     *   <li>模型/XML 缺失、解析失败——fail-open，面板维持旧行为；</li>
     *   <li>在办 nodeKey 不在图上——多半是老版本定义的在途单（重发布换了节点 id），标 SKIPPED 会冤枉真节点。</li>
     * </ul>
     */
    private Set<String> forwardReachable(String modelKey, Set<String> fromKeys) {
        if (modelKey == null || fromKeys == null || fromKeys.isEmpty()) {
            return null;
        }
        FlowGraph g = flowGraph(modelKey);
        if (g == null) {
            return null;
        }
        for (String k : fromKeys) {
            if (!g.adj.containsKey(k)) {
                return null;
            }
        }
        Set<String> seen = new HashSet<>(fromKeys);
        Deque<String> queue = new ArrayDeque<>(fromKeys);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String nxt : g.adj.getOrDefault(cur, List.of())) {
                if (seen.add(nxt)) {
                    queue.add(nxt);
                }
            }
        }
        return seen;
    }

    /** 按 modelKey 取前向图（缓存键带版本号，重发布升版本自然失效）；不可建图缓存 null 语义由调用方判空承担。 */
    private FlowGraph flowGraph(String modelKey) {
        WfModel m = modelMapper.selectOne(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, modelKey)
                .orderByDesc(WfModel::getVersion)
                .last("limit 1"));
        if (m == null || m.getBpmnXml() == null || m.getBpmnXml().isBlank()) {
            return null;
        }
        String cacheKey = modelKey + "@" + m.getVersion();
        return flowGraphCache.computeIfAbsent(cacheKey, k -> parseFlowGraph(modelKey, m.getBpmnXml()));
    }

    /** 解析 BPMN XML 为"全元素有向图"；任何异常返回 null（调用方 fail-open）。 */
    private FlowGraph parseFlowGraph(String modelKey, String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // 防外部实体（XML 里只有我们自家发布器写出的静态定义，不开扩展）
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            FlowGraph g = new FlowGraph();
            // 全元素 id 集合（节点类型不设限：网关/系统任务也要参与路径传导）
            Set<String> allIds = new HashSet<>();
            collectFlowNodeIds(root, allIds);
            // sequenceFlow → 邻接表
            NodeList flows = root.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < flows.getLength(); i++) {
                Element fl = (Element) flows.item(i);
                String src = fl.getAttribute("sourceRef");
                String tgt = fl.getAttribute("targetRef");
                if (src == null || src.isBlank() || tgt == null || tgt.isBlank()) continue;
                g.adj.computeIfAbsent(src, x -> new ArrayList<>()).add(tgt);
            }
            g.allNodeIds = allIds;
            return g;
        } catch (Exception ex) {
            log.warn("chain 分支感知：模型 {} BPMN 建图失败，按不分板处理: {}", modelKey, ex.getMessage());
            return null;
        }
    }

    private void collectFlowNodeIds(Element parent, Set<String> out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            if (("startEvent".equals(local) || "endEvent".equals(local) || "userTask".equals(local)
                    || "serviceTask".equals(local) || "scriptTask".equals(local) || "task".equals(local)
                    || "exclusiveGateway".equals(local) || "parallelGateway".equals(local)
                    || "inclusiveGateway".equals(local) || "eventGateway".equals(local)
                    || "subProcess".equals(local) || "callActivity".equals(local)
                    || "intermediateCatchEvent".equals(local) || "intermediateThrowEvent".equals(local))
                    && !el.getAttribute("id").isBlank()) {
                out.add(el.getAttribute("id"));
            }
            if (n.hasChildNodes()) {
                collectFlowNodeIds(el, out);
            }
        }
    }

    /** BPMN 前向图：邻接表＋全元素 id 集（allNodeIds 保留给后续按类型过滤用） */
    private static final class FlowGraph {
        final Map<String, List<String>> adj = new HashMap<>();
        Set<String> allNodeIds = Set.of();
    }

    // 抄送
    public PageResult<WfCc> ccPage(long pageNum, long pageSize, Long ccUserId, Integer isRead) {
        LambdaQueryWrapper<WfCc> qw = new LambdaQueryWrapper<>();
        if (ccUserId != null) {
            qw.eq(WfCc::getCcUserId, ccUserId);
        }
        if (isRead != null) {
            qw.eq(WfCc::getIsRead, isRead);
        }
        qw.orderByDesc(WfCc::getId);
        Page<WfCc> p = ccMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 抄送写入数据库 */
    @Transactional(rollbackFor = Exception.class)
    public void recordCc(WfCc cc) {
        cc.setId(null);
        cc.setIsRead(0);
        ccMapper.insert(cc);
    }

    public void markCcRead(Long id) {
        WfCc upd = new WfCc();
        upd.setId(id);
        upd.setIsRead(1);
        upd.setReadTime(LocalDateTime.now());
        ccMapper.updateById(upd);
    }

    /** 供 WfEngineFacade 等内部使用的租户上下文安全获取 */
    public Long tenantId() {
        return TenantContext.getTenantId();
    }
}
