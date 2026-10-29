// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfCc;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WfCcService;
import com.fssc.workflow.service.WfQueryService;
import com.fssc.workflow.service.WfReadGuard;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程查询视图（/api/tenant/wf/query/**）：
 * 实例/待办/历史/抄送，全部查业务视图表（wf_*），不与 ACT_* 联查。
 */
@RestController
@RequestMapping("/api/tenant/wf/query")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class WfQueryController {

    private final WfQueryService wfQueryService;
    /** G2 传阅：抄送候选人只读查询（审批弹窗「抄送给」的数据源） */
    private final WfCcService wfCcService;
    /** 审批历史读侧归属判定（RA-TRC-04 前置：授权扩面前先办结） */
    private final OrgGuard orgGuard;
    private final NodeOrgResolver nodeOrgResolver;
    /** 流程侧只读预览的归属校验（chain 与四个预览端点同一把尺） */
    private final WfReadGuard wfReadGuard;

    /**
     * 流程实例列表（流程<b>管理</b>中心）。
     *
     * <p>G4 治理组织隔离：{@code @DataScope} 把登录人的数据范围写入 {@code DataScopeHolder}，
     * 由 {@code WfQueryService} 内的 {@code WfManageOrgScope} 在
     * {@code sys_config.wf.manage.org-isolated=1} 时按登录人 companyId 办结。
     * 开关缺省关 ⇒ 本注解不产生任何过滤（零行为变化），也不影响既有权限判定。
     */
    @RequiresPerm({"wf:manage:instance", "wf:instance:list"})
    @DataScope
    @GetMapping("/instance/page")
    public Result<PageResult<WfInstance>> instancePage(PageQuery query,
                                                       @RequestParam(required = false) String bizType,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword,
                                                       // 模型详情抽屉的「本模型实例」一直在传 modelKey，
                                                       // 端点不接 ⇒ 参数被丢弃、实际返回全模型。补签名接回，不传则不过滤。
                                                       @RequestParam(required = false) String modelKey) {
        return Result.success(wfQueryService.instancePage(
                query.getPageNum(), query.getPageSize(), bizType, status, keyword, modelKey));
    }

    @RequiresPerm("wf:task:list")
    @GetMapping("/task/page")
    public Result<PageResult<WfTask>> taskPage(PageQuery query,
                                               @RequestParam(required = false) Long assigneeId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(wfQueryService.taskPage(
                query.getPageNum(), query.getPageSize(), assigneeId, status, keyword));
    }

    /** 我的待办（PENDING，处理人=我 或 候选含我） */
    @GetMapping("/todo")
    public Result<PageResult<WfTask>> myTodo(PageQuery query) {
        return Result.success(wfQueryService.myTodo(
                query.getPageNum(), query.getPageSize(), UserContext.getRequired().getUserId()));
    }

    /**
     * 审批历史分页。
     *
     * <p>新增 {@code bizNo} 入参——按单号查历史不再要求调用方先知道 bizId。
     *
     * <p><b>RA-TRC-04 前置（读侧归属过滤）</b>：本接口要授给全业务角色，所以先办结再放权——
     * 按单/按号查询走 {@code OrgGuard.assertVisible}（本公司 / 本人提单 / 集团数据范围放行）；
     * 判为不可见的单据<b>不再整表放开</b>，也不甩 403 打断详情页：结果收敛为<b>本人参与过</b>的
     * 那些行（办理过 / 被转办加签点名 / 待办在我名下或候选含我）。既不泄露他人审批意见，
     * 又让「曾经办过这张单的人回看自己那几步」照常工作。
     *
     * <p>解析到流程实例时按 {@code (biz_type,biz_id)} <b>成对</b>查询，不再把调用方传来的
     * bizType 直接当过滤条件——运行期真值可能带 {@code EXP_} 前缀，按传值硬筛会把在途单筛成空。
     *
     * <p><b>后端环节</b>：传值连实例都解析不到时，以前会退回「bizId＋bizType 双硬筛」，
     * 而 {@code wf_task_history.biz_type} 存的同样是运行期真值 ⇒ 两个名称表都对不上就必筛成空页
     * HTTP 200、list 为空，读侧把这种空当成业务结论「本单没有前序环节」。现改为：
     * bizType 只当<b>提示</b>，兜一次 {@code instancesById(bizId)}；仍空则页上带可诊断标记，
     * 服务端同文 {@code log.warn}，空页自此可自证成因。
     */
    @RequiresPerm("wf:history:list")
    @GetMapping("/history/page")
    public Result<PageResult<WfTaskHistory>> historyPage(PageQuery query,
                                                         @RequestParam(required = false) Long bizId,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String bizNo) {
        LoginUser me = UserContext.getRequired();
        List<WfInstance> docs = null;
        Long restrictToUserId = null;
        boolean pairFiltered = false;
        // rawBizType 是「确实可以当硬筛的那份传值」——降级为提示后置空；
        // emptyHint 记录空页成因，仅在 total=0 时随响应回带（见 withEmptyPageHint）。
        String rawBizType = bizType;
        String emptyHint = null;
        if (bizNo != null && !bizNo.isBlank()) {
            List<WfInstance> byNo = wfQueryService.instancesByBizNo(bizNo).stream()
                    .filter(i -> bizType == null || bizType.isBlank() || bizType.equals(i.getBizType()))
                    .filter(i -> bizId == null || bizId.equals(i.getBizId()))
                    .collect(Collectors.toList());
            List<WfInstance> visible = byNo.stream().filter(this::isVisibleDoc).toList();
            if (visible.isEmpty()) {
                if (!byNo.isEmpty()) {
                    throw new BizException(ErrorCode.FORBIDDEN,
                            "无权查看该单据的审批历史");
                }
                // 单号没有任何流程实例：确实没有历史，空页而不是报错（草稿单从未进流程）
                return withEmptyPageHint(PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize()),
                        "单号 " + bizNo.trim() + " 没有任何流程实例（草稿单从未进流程）");
            }
            docs = visible;
            pairFiltered = true;
        } else if (bizId != null) {
            boolean typeGiven = bizType != null && !bizType.isBlank();
            List<WfInstance> found = typeGiven
                    ? wfQueryService.instancesByBiz(bizType, bizId)
                    : wfQueryService.instancesById(bizId);
            if (typeGiven && found.isEmpty()) {
                // 成对前缀（instancesByBiz 已容忍 EXP_ 一档）仍解析不到：与上面 bizNo 环节同一规则，
                // 只按 bizId 再兜一次。传值当提示、不再下推到审计表硬筛。
                found = wfQueryService.instancesById(bizId);
                rawBizType = null;
                emptyHint = found.isEmpty()
                        ? "bizId=" + bizId + " 没有任何流程实例，传值 bizType=" + bizType + " 亦未命中（只按 bizId 查询本人参与过的记录）"
                        : "传值 bizType=" + bizType + " 与该单据在途实例的 biz_type 不一致，已按 bizId 默认解析（bizType 仅作提示，未参与过滤）";
            }
            List<WfInstance> visible = found.stream().filter(this::isVisibleDoc).toList();
            if (visible.isEmpty()) {
                // 单据不可见（或从未进流程）：只看本人参与过的行
                restrictToUserId = me.getUserId();
                if (!found.isEmpty()) {
                    docs = found;
                    pairFiltered = true;
                }
            } else {
                // 只放可见的那几张：同主键的他域单据不随 bizId 一起漏出来
                docs = visible;
                pairFiltered = true;
            }
        } else {
            restrictToUserId = me.getUserId();
            if (bizType != null && !bizType.isBlank()) {
                // 既无单号也无主键，只剩 bizType 可用：无从解析实例，成对规则在这里建立不起来，
                // 空页必须说明是名称表问题（审计表存的是运行期真值，可能与调用方手里的域名不是一套）。
                emptyHint = "本次仅按 bizType=" + bizType + " 过滤（未传 bizId/bizNo，无法按实例成对查询）；"
                        + "空页不代表该域没有审批，可能是记录表 biz_type 实际值与传入值不同名";
            }
        }
        return withEmptyPageHint(wfQueryService.historyPage(query.getPageNum(), query.getPageSize(),
                pairFiltered ? null : bizId, pairFiltered ? null : rawBizType, docs, restrictToUserId), emptyHint);
    }

    /**
     * 空页要能自证成因，不再让「查询范围没命中」和「这单确实没审过」在页面上同形。
     *
     * <p>{@code code} 仍为 0——空页是合法结果（草稿单从未进流程），不能伪装成 4xx 打断详情页；
     * 成因写在 {@code msg}，服务端同时 {@code log.warn}。
     *
     * <p>可见性：前端响应拦截器在 {@code code=0} 时只回传 {@code data}，页面要显示这条成因
     * 需另落一环节（WEB 侧禁改，已列入移交）。此处不改 {@code PageResult}/{@code Result} 的字段——
     * 那两个类不在本面内。
     */
    private Result<PageResult<WfTaskHistory>> withEmptyPageHint(PageResult<WfTaskHistory> page, String hint) {
        Result<PageResult<WfTaskHistory>> r = Result.success(page);
        if (hint == null) {
            return r;
        }
        if (page == null || page.getTotal() == 0) {
            log.warn("审批历史空页诊断: {}", hint);
            r.setMsg("成功。" + hint);
        }
        return r;
    }

    /** TRC-B 同档归属判定：单据公司 + 提单人，集团数据范围角色在 OrgGuard 内部放行 */
    private boolean isVisibleDoc(WfInstance inst) {
        try {
            orgGuard.assertVisible(nodeOrgResolver.companyIdOfBill(inst.getBizType(), inst.getBizId()),
                    inst.getStartUserId());
            return true;
        } catch (BizException ex) {
            return false;
        }
    }

    /**
     * 单据审批链全景（全节点 + 已过/当前/待到达 + 办理人与意见），填单侧与审批侧共用。
     *
     * <p>不挂 wf:node:list / wf:history:list：业务角色看不到节点配置也读不到历史分页，
     * 却必须知道单据走到了哪一节。不靠「实例 UUID 不可枚举」——见下方 归属校验。
     */
    @GetMapping("/chain")
    public Result<List<Map<String, Object>>> chain(@RequestParam String procInstId) {
        // 本端点连同四个预览端点此前只靠「实例 UUID 不可枚举」当防线。
        // 实测同租户他公司账号换个 UUID 就能读到整条链（含各节点审批意见原文），
        // 而单据联查侧同类查询早被 OrgGuard 拒 403 ⇒ 补齐，规则一致。
        wfReadGuard.assertInstanceReadable(procInstId);
        return Result.success(wfQueryService.chain(procInstId));
    }

    @RequiresPerm("wf:cc:list")
    @GetMapping("/cc/page")
    public Result<PageResult<WfCc>> ccPage(PageQuery query,
                                           @RequestParam(required = false) Long ccUserId,
                                           @RequestParam(required = false) Integer isRead) {
        return Result.success(wfQueryService.ccPage(
                query.getPageNum(), query.getPageSize(), ccUserId, isRead));
    }

    /** 我的抄送 */
    @GetMapping("/cc/mine")
    public Result<PageResult<WfCc>> myCc(PageQuery query, @RequestParam(required = false) Integer isRead) {
        return Result.success(wfQueryService.ccPage(
                query.getPageNum(), query.getPageSize(), UserContext.getRequired().getUserId(), isRead));
    }

    /**
     * 抄送候选人（只读）：审批弹窗「抄送给」多选的数据源。
     *
     * <p>不挂权限码，与 {@code /cc/mine} 同一规则 —— 能审批的人本就看得见同事姓名，此处不额外设。
     * 返回范围限本租户<b>在职</b>用户、排除管理员，上限 500 条。
     */
    @GetMapping("/cc/candidates")
    public Result<List<Map<String, Object>>> ccCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(wfCcService.candidates(keyword, limit));
    }

    /**
     * 标记抄送已读。
     *
     * <p>此前挂 {@code @RequiresPerm("wf:cc:update")}，而该码只授 CONFIG_ADMIN／FIN_MANAGER／IT_ADMIN，
     * 真正收到抄送的岗位（现网收件人 3901／3915＝FSSC_SUPERVISOR＋COMMON_EMP）皆不持 ⇒
     * 收件人永远点不动「标记已读」，{@code wf_cc.is_read} 结构性恒 0（实测 12 行全未读）。
     * 已读是收件人自己的状态，与 {@code GET /cc/mine} 同一规则改为<b>本人视角免权限码</b>，
     * 归属判据在 {@link WfCcService#markRead} 内硬校验（id 可枚举，不可裸放行）；
     * 持 {@code wf:cc:update} 者代人标记的维护通道保留。
     */
    @PostMapping("/cc/{id}/read")
    public Result<Void> markCcRead(@PathVariable Long id) {
        wfCcService.markRead(id, UserContext.getRequired());
        return Result.success();
    }
}
