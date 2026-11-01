// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfEventActionConfig;
import com.fssc.workflow.event.EventOrchestrationBus;
import com.fssc.workflow.mapper.WfEventActionConfigMapper;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * 流程事件收尾动作配置管理面（{@code /api/tenant/wf/event-action-config/**}）。
 *
 * <p><b>为什么要有这个面</b>：{@code wf_event_action_config} 是
 * {@link EventOrchestrationBus} 的表驱动钩子配置（bizType → actionBean + triggerWhen + 排序 + 启用），
 * 建库以来一直"只有引擎在读、没有任何 Controller 在看"——配置只能靠 SQL 手改，
 * 页面上零可见零可控。本类补齐<b>只读列表 + 有限编辑</b>这一段：
 * 列表 / 详情 / 选项字典 / 引擎生效快照（读）＋ 描述与启用位（写）。
 *
 * <p><b>刻意不做的能力</b>：不提供新增与删除。配置行的增删等于给某条业务流挂/摘收尾动作，
 * 属待定范围（要连带校验 actionBean 是否真在 Spring 容器里、bizType 是否与
 * {@code WfBizAction#bizTypes()} 声明一致），按 规则不在此开放。
 * 同理不提供"热重载"入口——总线只在 {@code @PostConstruct} 读一次表，
 * 手工触发 {@code refreshDispatchMap()} 会改写运行中的分发映射，属引擎行为变更，需另行判定。
 * 因此本页改完 enabled/description 后，<b>下次应用重启才影响运行期</b>，页面已按此规则提示。
 *
 * <p><b>权限码（不发明新码）</b>：全部复用 wf 域既有码
 * ——读 {@code wf:config:list}、写 {@code wf:config:update}（现库已有角色持有）。
 * 新造 {@code wf:eventaction:*} 会让页面即时锁死（无数据落家）。
 *
 * <p><b>租户规则</b>：{@code wf_*} 表不在租户忽略名单内，MP 默认追加 {@code tenant_id = 当前租户}。
 * 但本表 74 行里有 39 行是历史种子行（{@code tenant_id = 0}，建库早期未按租户约定写入），
 * 而引擎读侧是 {@code runIgnoreTenant} 全量加载——这 39 行<b>确实在给当前账套的流程干活</b>。
 * 若沿用默认过滤，页面会对本账套一半以上的在跑配置失明，正是要修的缺陷。
 * 故本类在 {@link TenantContext#runIgnoreTenant} 内自带显式范围条件
 * {@code (tenant_id = 0 OR tenant_id = 当前租户)}：0＝全平台种子分区，其余租户的行一律取不到，
 * 读到的范围与引擎实际消费的范围一致且不越他户。写法与 {@code WfDelegateExpireJob}/{@code WfInstanceArchiveJob}
 * 的"ignore 上下文 + holder 数组带回结果"同族。
 *
 * <p><b>列名规则</b>：以现库 {@code SHOW COLUMNS FROM wf_event_action_config} 为准
 * （id / tenant_id / biz_type / action_bean / trigger_when / sort_order / enabled / description /
 * create_by / create_time / update_by / update_time / del_flag）。
 * 表上<b>没有 remark 列</b>，故实体、DTO 与编辑面都不提供 remark——补上去会让 MP 在所有写入上炸列。
 */
@RestController
@RequestMapping("/api/tenant/wf/event-action-config")
public class WfEventActionConfigController {

    /** 历史种子分区：tenant_id=0 的行按全平台默认配置被引擎读取（见类注释「租户规则」）。 */
    private static final Long SEED_TENANT_ID = 0L;

    /** description 列宽 varchar(256)，超长在写入库前拦下，避免 MySQL 静默截断。 */
    private static final int DESCRIPTION_MAX = 256;

    private final WfEventActionConfigMapper configMapper;
    private final EventOrchestrationBus orchestrationBus;

    public WfEventActionConfigController(WfEventActionConfigMapper configMapper,
                                         EventOrchestrationBus orchestrationBus) {
        this.configMapper = configMapper;
        this.orchestrationBus = orchestrationBus;
    }

    // 1. 只读列表

    /**
     * 分页列表：可按 {@code enabled}（1=启用 0=停用）、{@code actionBean}、{@code bizType} 过滤，
     * {@code keyword} 命中 bizType / actionBean / description 任一列。
     * 排序固定 bizType + sortOrder 升序（与引擎分组内执行顺序同一规则），不接受前端传 orderBy。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/page")
    public Result<PageResult<WfEventActionConfig>> page(PageQuery q,
                                                         @RequestParam(required = false) Integer enabled,
                                                         @RequestParam(required = false) String actionBean,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String keyword) {
        Page<WfEventActionConfig> p = Page.of(q.getPageNum(), q.getPageSize());
        final Long tid = TenantContext.getTenantId();
        // ignore 上下文里 selectPage 不会被追加 tenant 条件，范围由 scopeCondition 自己守住
        runInScope(() -> configMapper.selectPage(p, buildListWrapper(tid, enabled, actionBean, bizType, keyword)));
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    /** 单行详情（编辑弹窗回显用；同样只放本账套 + 全平台种子分区）。 */
    @RequiresPerm("wf:config:list")
    @GetMapping("/detail/{id}")
    public Result<WfEventActionConfig> detail(@PathVariable Long id) {
        final Long tid = TenantContext.getTenantId();
        final WfEventActionConfig[] holder = new WfEventActionConfig[1];
        runInScope(() -> holder[0] = configMapper.selectOne(new LambdaQueryWrapper<WfEventActionConfig>()
                .eq(WfEventActionConfig::getId, id)
                .eq(WfEventActionConfig::getDelFlag, 0)
                .and(scopeCondition(tid))));
        AssertUtil.notNull(holder[0], ErrorCode.NOT_FOUND, "该收尾动作配置不存在，或不属于当前账套");
        return Result.success(holder[0]);
    }

    /**
     * 过滤下拉的取值字典（只读）：在跑配置表本身很小（全库 74 行），
     * 直接按当前可见范围取 distinct，避免前端在分页数据里凑选项导致"筛不全"。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/options")
    public Result<Map<String, Object>> options() {
        final Long tid = TenantContext.getTenantId();
        final List<WfEventActionConfig> rows = new ArrayList<>();
        runInScope(() -> rows.addAll(configMapper.selectList(new LambdaQueryWrapper<WfEventActionConfig>()
                .eq(WfEventActionConfig::getDelFlag, 0)
                .and(scopeCondition(tid)))));
        TreeSet<String> bizTypes = new TreeSet<>();
        TreeSet<String> beans = new TreeSet<>();
        for (WfEventActionConfig r : rows) {
            if (r.getBizType() != null) bizTypes.add(r.getBizType());
            if (r.getActionBean() != null) beans.add(r.getActionBean());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bizTypes", new ArrayList<>(bizTypes));
        out.put("actionBeans", new ArrayList<>(beans));
        out.put("total", rows.size());
        out.put("enabledCount", rows.stream().filter(r -> Integer.valueOf(1).equals(r.getEnabled())).count());
        return Result.success(out);
    }

    /**
     * 引擎生效快照（只读）：{@link EventOrchestrationBus} 启动时按本表构建的分发映射，
     * 回答"某个 bizType 现在到底挂了哪些收尾动作、按什么顺序、什么时机触发"。
     * 表里的行只有同时满足 enabled=1 且 actionBean 在容器内且 bizType 被该 Bean 声明，
     * 才会出现在这里——这也是"配了却不生效"的唯一权威解释，故只回投影不回 Bean 本体。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/runtime-snapshot")
    public Result<Map<String, Object>> runtimeSnapshot() {
        Map<String, List<EventOrchestrationBus.ActionEntry>> dispatch = orchestrationBus.getDispatchMap();
        List<Map<String, Object>> bizTypes = new ArrayList<>();
        for (String bizType : new TreeSet<>(dispatch.keySet())) {
            List<Map<String, Object>> actions = new ArrayList<>();
            for (EventOrchestrationBus.ActionEntry e : dispatch.get(bizType)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("actionBean", e.beanName);
                a.put("triggerWhen", e.triggerWhen);
                a.put("sortOrder", e.sortOrder);
                a.put("declaredBizTypes", e.action == null ? Collections.emptyList() : new TreeSet<>(e.action.bizTypes()));
                actions.add(a);
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("bizType", bizType);
            one.put("actionCount", actions.size());
            one.put("actions", actions);
            bizTypes.add(one);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bizTypeCount", dispatch.size());
        out.put("actionCount", dispatch.values().stream().mapToInt(List::size).sum());
        out.put("bizTypes", bizTypes);
        out.put("note", "本快照取自应用启动时构建的分发映射；改完配置需重启应用才会在此反映。");
        return Result.success(out);
    }

    // 2. 有限编辑（只碰 description / enabled 两个字段）

    /**
     * 字段级编辑：只接受 id + description + enabled，其余列（biz_type / action_bean / trigger_when /
     * sort_order / tenant_id / del_flag）一律不下发也不更新——它们决定引擎怎么跑，改动属待定范围。
     * 走 {@code update(entity, wrapper)}：SET 只含实体非空字段，update_by/update_time 由
     * {@code FsscMetaObjectHandler.updateFill} 自动记录。
     */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_EVENT_ACTION_CONFIG", operType = "UPDATE", desc = "修改流程收尾动作配置")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody EditRequest req) {
        AssertUtil.notNull(req, ErrorCode.PARAM_INVALID, "缺少提交内容");
        AssertUtil.notNull(req.getId(), ErrorCode.PARAM_INVALID, "缺少配置行 ID");
        String desc = req.getDescription();
        if (desc != null) {
            AssertUtil.isTrue(desc.length() <= DESCRIPTION_MAX, ErrorCode.PARAM_INVALID,
                    "配置说明不能超过 " + DESCRIPTION_MAX + " 字");
        }
        WfEventActionConfig patch = new WfEventActionConfig();
        boolean touched = false;
        if (desc != null) {
            // 允许清空描述：传空串（而非 null）才会进 SET 子句，null 的语义是"这次不改这一列"
            patch.setDescription(desc.trim());
            touched = true;
        }
        if (req.getEnabled() != null) {
            AssertUtil.isTrue(Integer.valueOf(0).equals(req.getEnabled()) || Integer.valueOf(1).equals(req.getEnabled()),
                    ErrorCode.PARAM_INVALID, "启用位只能是 1（启用）或 0（停用）");
            patch.setEnabled(req.getEnabled());
            touched = true;
        }
        AssertUtil.isTrue(touched, ErrorCode.PARAM_INVALID, "没有要修改的字段（配置说明 / 启用状态）");
        int affected = updateInScope(patch, req.getId());
        AssertUtil.isTrue(affected > 0, ErrorCode.NOT_FOUND, "该收尾动作配置不存在，或不属于当前账套，未做任何修改");
        return Result.success();
    }

    // 3. 内部工具

    private LambdaQueryWrapper<WfEventActionConfig> buildListWrapper(Long tid, Integer enabled, String actionBean,
                                                                     String bizType, String keyword) {
        LambdaQueryWrapper<WfEventActionConfig> w = new LambdaQueryWrapper<WfEventActionConfig>()
                .eq(WfEventActionConfig::getDelFlag, 0)
                .and(scopeCondition(tid));
        if (enabled != null) {
            w.eq(WfEventActionConfig::getEnabled, enabled);
        }
        if (isNotBlank(actionBean)) {
            w.eq(WfEventActionConfig::getActionBean, actionBean.trim());
        }
        if (isNotBlank(bizType)) {
            w.eq(WfEventActionConfig::getBizType, bizType.trim());
        }
        if (isNotBlank(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(WfEventActionConfig::getBizType, k)
                    .or().like(WfEventActionConfig::getActionBean, k)
                    .or().like(WfEventActionConfig::getDescription, k));
        }
        w.orderByAsc(WfEventActionConfig::getBizType).orderByAsc(WfEventActionConfig::getSortOrder);
        return w;
    }

    /**
     * 可见/可改范围：全平台种子分区（tenant_id=0）+ 当前租户自己的行。
     * 当前租户ID 必须由调用侧在<b>有租户上下文的请求线程里</b>先取好再闭包进来——
     * {@link TenantContext#getTenantId()} 在 ignore 上下文里返回 null，届时条件会退化成"只看种子行"。
     * 别的租户的行取不到，所以这不是跨租户明细读。
     */
    private static Consumer<LambdaQueryWrapper<WfEventActionConfig>> scopeCondition(Long tid) {
        return q -> {
            q.eq(WfEventActionConfig::getTenantId, SEED_TENANT_ID);
            if (tid != null && !SEED_TENANT_ID.equals(tid)) {
                q.or().eq(WfEventActionConfig::getTenantId, tid);
            }
        };
    }

    /** 读：ignore 租户上下文里执行，范围条件由调用方自带（本表无跨租户明细可取）。 */
    private void runInScope(Runnable action) {
        TenantContext.runIgnoreTenant(action);
    }

    /**
     * 写：id 命中 + (tenant_id=0 或 tenant_id=当前租户) + del_flag=0 三重条件，
     * 一条 SQL 内完成"存在性 + 归属"校验，越界的 id 直接 0 行受影响并回错误提示。
     */
    private int updateInScope(WfEventActionConfig patch, Long id) {
        Long tid = TenantContext.getTenantId();
        LambdaUpdateWrapper<WfEventActionConfig> w = new LambdaUpdateWrapper<WfEventActionConfig>()
                .eq(WfEventActionConfig::getId, id)
                .eq(WfEventActionConfig::getDelFlag, 0)
                .and(q -> q.eq(WfEventActionConfig::getTenantId, SEED_TENANT_ID)
                        .or().eq(WfEventActionConfig::getTenantId, tid));
        int[] holder = new int[1];
        runInScope(() -> holder[0] = configMapper.update(patch, w));
        return holder[0];
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 编辑入参：字段级白名单（只 id + description + enabled）。
     * 刻意不接整个实体——那会让页面顺手改到 action_bean / trigger_when / sort_order，
 * 等于绕开既定口径改引擎行为。
     */
    @Data
    public static class EditRequest {
        private Long id;
        private String description;
        private Integer enabled;
    }
}
