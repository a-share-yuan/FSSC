// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 流程节点审批人配置（wf_node_config，带 tenant_id）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfNodeConfigService {

    private final WfNodeConfigMapper nodeConfigMapper;
    private final WfModelMapper modelMapper;

    /** 从 BPMN 抽取 userTask 的 taskDefinitionKey（兼容带/不带 bpmn: 前缀） */
    private static final Pattern USER_TASK_ID = Pattern.compile(
            "<(?:bpmn:)?userTask\\b[^>]*?\\bid\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL);

    /** 某模型全部节点配置（按 sort 排序） */
    public List<WfNodeConfig> listByModel(String modelKey) {
        return nodeConfigMapper.selectList(new LambdaQueryWrapper<WfNodeConfig>()
                .eq(WfNodeConfig::getModelKey, modelKey)
                .orderByAsc(WfNodeConfig::getSort));
    }

    public Long create(WfNodeConfig cfg) {
        validateKeyAndName(cfg);
        long dup = nodeConfigMapper.selectCount(new LambdaQueryWrapper<WfNodeConfig>()
                .eq(WfNodeConfig::getModelKey, cfg.getModelKey())
                .eq(WfNodeConfig::getNodeKey, cfg.getNodeKey()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "节点配置已存在: " + cfg.getModelKey() + "/" + cfg.getNodeKey());
        }
        cfg.setId(null);
        if (cfg.getSort() == null) {
            cfg.setSort(0);
        }
        // 铁律守卫：新建节点一律强制合规默认值（不依赖列默认，防 DDL 被回滚后复发）
        if (cfg.getEmptyStrategy() == null || cfg.getEmptyStrategy().isBlank()) {
            cfg.setEmptyStrategy("ERROR");
        }
        normalizeGovernance(cfg);
        assertNodeKeyMatchesBpmn(cfg);
        nodeConfigMapper.insert(cfg);
        WfNodeConfigResolver.evictAll();
        return cfg.getId();
    }

    public void update(WfNodeConfig cfg) {
        if (nodeConfigMapper.selectById(cfg.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "节点配置不存在: " + cfg.getId());
        }
        // 整单更新（前端提交完整对象），故与 create 同一规则拦空
        validateKeyAndName(cfg);
        // 仅当本次显式传入该字段时才归一化（MyBatis-Plus 对 null 字段不更新，
        // 避免"只改 sort 的部分更新"把用户已配置的 SKIP/ESCALATE 误刷成 ERROR）
        normalizeGovernance(cfg);
        assertNodeKeyMatchesBpmn(cfg);
        nodeConfigMapper.updateById(cfg);
        WfNodeConfigResolver.evictAll();
    }

    /**
     * 必填办结（批次 2C，与前端 FlowConfig「节点配置」/ GenericList 节点弹窗同一规则）：
     * modelKey 决定这行节点挂在哪个流程上，nodeKey 要与 BPMN 的 userTask id 精确对齐，nodeName 是待办上看得见的环节名。
     */
    private void validateKeyAndName(WfNodeConfig cfg) {
        AssertUtil.notBlank(cfg.getModelKey(), ErrorCode.PARAM_INVALID, "请选择模型编码");
        AssertUtil.notBlank(cfg.getNodeKey(), ErrorCode.PARAM_INVALID, "请输入节点编码");
        AssertUtil.notBlank(cfg.getNodeName(), ErrorCode.PARAM_INVALID, "请输入节点名称");
    }

    /**
     * 治理字段写入校验（用户铁律：admin 不得作为流程节点用户参与任何流程）。
     *
     * <p><b>为什么要有它</b>：{@code wf_node_config} 曾因列默认值为 {@code ADMIN}、{@code auto_pass_self=1}，
     * 使新建节点静默继承两项违规默认——前者在"节点找不到审批人"时把待办<b>直派租户管理员 admin</b>
     * （{@link NodeOrgResolver#applyEmptyStrategy} 的 ADMIN 分支），直接违反节点禁入 admin 铁律；
     * 后者一旦后端实现读取点即成"自提自过"的 SoD 漏洞。列默认值已由
     * {@code sql/alter_wf_node_config_defaults.sql} 固化为 ERROR/0，此处是<b>代码层第二道校验</b>——
     * 任何走本 Service 的写入路径都无法绕过，杜绝新入口重新引入。
     *
     * <ul>
     *   <li>{@code empty_strategy}：{@code ADMIN} → <b>直接拒绝写入</b>（不留静默降级口子，
     *       让配置者在保存时就知道该选项被禁用）；其他值转大写存储；</li>
     *   <li>{@code auto_pass_self}：非 0 一律强制归 0（该字段当前无读取点，预留防 SoD）。</li>
     * </ul>
     *
     * @param cfg 待写入的节点配置（为 null 时忽略；字段为 null 时亦忽略，交由调用方决定是否补默认）
     */
    private void normalizeGovernance(WfNodeConfig cfg) {
        if (cfg == null) {
            return;
        }
        String es = cfg.getEmptyStrategy();
        if (es != null && !es.isBlank()) {
            String v = es.trim().toUpperCase();
            if ("ADMIN".equals(v)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "节点「" + cfg.getNodeName() + "」不允许配置空策略 ADMIN：流程节点禁止 admin 参与（铁律），"
                                + "找不到审批人时应报错暴露而非转管理员。请改为 ERROR（报错阻断）/ "
                                + "ESCALATE（沿汇报线升级到真实负责人）/ SKIP（自动跳过）");
            }
            cfg.setEmptyStrategy(v);
        }
        if (cfg.getAutoPassSelf() != null && cfg.getAutoPassSelf() != 0) {
            cfg.setAutoPassSelf(0);
        }
        // 多实例写入校验：multi_type 与 BPMN multiInstanceLoopCharacteristics 一一对应，
        // 后端 WfBpmnTemplateService.build() 只消费 ALL/SEQ/VOTE（其余值含 ANY 一律生成普通单任务节点）。
        // 名称表以 wf_node_config.multi_type 列注释为唯一基准：
        //   ANY 任一通过（默认，单任务）/ ALL 会签（全员通过）/ SEQ 顺序会签 / VOTE 票签（按 vote_ratio）。
        // 为什么必须硬拒：历史前端把「顺序会签」写成 SEQUENTIAL——后端不识别即静默退化成单人审批，
        // 配置者以为配了会签、实际只走一个人，属"看着能配、实际不生效"的隐形缺陷。故写入即拒（fail-fast）。
        String mt = cfg.getMultiType();
        if (mt != null && !mt.isBlank()) {
            String v = mt.trim().toUpperCase();
            // 报错要能定位到具体节点：前端整表单提交时 nodeName 必有；但脚本/部分更新只传 id+字段，
            // 此时 nodeName 为 null，直接拼会输出"节点「null」"——按 nodeKey → id 逐级兜底。
            String tag = cfg.getNodeName() != null && !cfg.getNodeName().isBlank() ? cfg.getNodeName()
                    : (cfg.getNodeKey() != null && !cfg.getNodeKey().isBlank() ? cfg.getNodeKey()
                            : ("#" + cfg.getId()));
            if (!MULTI_TYPES.contains(v)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "节点「" + tag + "」会签模式非法：" + mt
                                + "。合法值：ANY(任一通过) / ALL(会签全员通过) / SEQ(顺序会签) / VOTE(票签)");
            }
            cfg.setMultiType(v);
            if ("VOTE".equals(v)) {
                Integer wr = cfg.getVoteRatio();
                if (wr == null || wr < 1 || wr > 100) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "节点「" + tag + "」票签阈值 vote_ratio 必须在 1~100 之间（当前："
                                    + wr + "）。阈值含义：赞成票占比 ≥ 该值即提前通过；反对票占比 > (100-该值) 即提前否决。");
                }
            }
        }
        // 升级层级写入校验：up_level 是 NodeOrgResolver.escalateTarget 的消费点，
        // 决定 ESCALATE 档（超时升级 + 空策略升级两条路径共用）沿汇报线向上取第几级真实负责人。
        // 上限 10 与 escalateTarget 的"防环最多 10 跳"一致——配了比 10 大的值没有任何一跳能到达，
        // 只会让人误以为"升得更高"，属"看着能配、实际不生效"，故超界即拒。
        Integer ul = cfg.getUpLevel();
        if (ul != null) {
            if (ul < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "节点「" + cfg.getNodeName() + "」升级层级 up_level 不能为负数（当前：" + ul + "）");
            }
            if (ul > 10) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "节点「" + cfg.getNodeName() + "」升级层级 up_level 必须在 0~10 之间（当前：" + ul
                                + "）。沿汇报线上收最多 10 跳，超出部分无实际效果；0 或 1 均表示升 1 级。");
            }
        }
    }

    /** 多实例会签模式合法值（wf_node_config.multi_type，与 DB 列注释一致） */
    private static final Set<String> MULTI_TYPES =
            Set.of("ANY", "ALL", "SEQ", "VOTE");

    /**
     * node_key 合法性校验：{@code node_key} 必须是该模型 BPMN 的
     * userTask {@code taskDefinitionKey}，拒绝 {@code CLAIM_T1 / N010} 这类业务码写入——
     * 它们是"两表脱节"的根源，且经位置兜底会污染到无关节点（造成越权/超时错位）。
     *
     * <p>宽容处理：模型不存在、无 BPMN、或无 userTask（如纯脚本/无审批流程）时<b>不校验</b>，
     * 避免误伤合法场景；仅当能解析出 userTask 集合时才强制约束。
     */
    private void assertNodeKeyMatchesBpmn(WfNodeConfig cfg) {
        if (cfg == null) {
            return;
        }
        String modelKey = cfg.getModelKey();
        String nodeKey = cfg.getNodeKey();
        if (modelKey == null || modelKey.isBlank() || nodeKey == null || nodeKey.isBlank()) {
            return;
        }
        List<WfModel> models = modelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, modelKey));
        if (models.isEmpty()) {
            log.warn("节点配置写入：模型 {} 不存在，跳过 node_key 校验", modelKey);
            return;
        }
        String xml = models.get(0).getBpmnXml();
        if (xml == null || xml.isBlank()) {
            log.warn("节点配置写入：模型 {} 无 BPMN，跳过 node_key 校验", modelKey);
            return;
        }
        Set<String> taskIds = new LinkedHashSet<>();
        Matcher m = USER_TASK_ID.matcher(xml);
        while (m.find()) {
            taskIds.add(m.group(1));
        }
        if (taskIds.isEmpty()) {
            log.warn("节点配置写入：模型 {} 的 BPMN 无 userTask，跳过 node_key 校验", modelKey);
            return;
        }
        if (!taskIds.contains(nodeKey)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "节点键「" + nodeKey + "」不是模型 " + modelKey + " BPMN 中的 userTask 节点。"
                            + "node_key 必须与 BPMN 的 taskDefinitionKey 逐字符一致（两表脱节的根源即业务码 vs Task_N 不同形）。"
                            + "该模型合法节点：" + taskIds);
        }
    }

    public void delete(Long id) {
        nodeConfigMapper.hardDelete(id);
    }
}
