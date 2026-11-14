package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参与人批量替换：查找参与人中设置的用户并替换。
 *
 * <p>为什么必须做成一个工具而不是一条 SQL：本系统同一节点的参与人有<b>两处真值</b>——
 * BPMN 的 {@code flowable:candidateGroups} 与 {@code wf_node_config.assign_value}，且
 * {@code WfEngineFacade.applyNodeConfig} 的 ROLE 分支是<b>追加</b>候选组、从不摘除。
 * 只改配置不改 XML，被停用的老角色仍作为候选组可见可办（越权）；只改 XML 不改配置，
 * 配置会把 XML 盖掉。所以两侧必须同批替换，且改完立即 publish（版本+1）。
 *
 * <p>写库规则沿用本仓库纪律：默认 {@code dryRun=true} 只出清单；真正写入数据库时逐模型 publish，
 * 并同步 UPDATE {@code wf_node_config.assign_value}（回滚＝同工具反向替换 to→from）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfAssigneeReplaceService {

    private final WfModelMapper modelMapper;
    private final WfNodeConfigMapper nodeConfigMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfModelService modelService;
    private final WfBpmnChecker checker;

    /** 属性值内的逗号分隔词元精确匹配（A,OLD,B 里的 OLD），不误伤子串（OLD 与 OLDER 不互撞） */
    private static final Pattern CANDIDATE_GROUPS =
            Pattern.compile("flowable:candidateGroups=\"([^\"]*)\"");

    public Map<String, Object> replace(String fromRoleCode, String toRoleCode,
                                      List<String> modelKeys, boolean dryRun, Long operator) {
        if (fromRoleCode == null || fromRoleCode.isBlank() || toRoleCode == null || toRoleCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原角色与目标角色都必填");
        }
        String from = fromRoleCode.trim().toUpperCase();
        String to = toRoleCode.trim().toUpperCase();
        if (from.equals(to)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原角色与目标角色相同，无需替换");
        }
        if (checker.existingRoleCodes().stream().noneMatch(to::equals)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标角色 " + to + " 在 sys_role 中不存在，替换后该节点必然零候选");
        }

        LambdaQueryWrapper<WfModel> qw = new LambdaQueryWrapper<WfModel>()
                .isNotNull(WfModel::getBpmnXml);
        if (modelKeys != null && !modelKeys.isEmpty()) {
            qw.in(WfModel::getModelKey, modelKeys);
        }
        List<WfModel> models = modelMapper.selectList(qw.orderByAsc(WfModel::getModelKey));

        List<Map<String, Object>> hits = new ArrayList<>();
        int xmlReplaced = 0, published = 0, configRows = 0;
        for (WfModel m : models) {
            String xml = m.getBpmnXml();
            if (xml == null || xml.isBlank()) continue;
            Rewrite rw = rewriteXml(xml, from, to);
            List<WfNodeConfig> cfgs = nodeConfigMapper.selectList(new LambdaQueryWrapper<WfNodeConfig>()
                    .eq(WfNodeConfig::getModelKey, m.getModelKey())
                    .eq(WfNodeConfig::getAssignType, "ROLE"));
            List<WfNodeConfig> cfgHits = new ArrayList<>();
            for (WfNodeConfig c : cfgs) {
                if (!WfBpmnChecker.tokens(c.getAssignValue()).contains(from)) continue;
                cfgHits.add(c);
            }
            if (rw.replaced == 0 && cfgHits.isEmpty()) continue;

            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("modelKey", m.getModelKey());
            hit.put("modelName", m.getModelName());
            hit.put("modelId", m.getId());
            hit.put("bpmnNodes", rw.hitNodes);
            hit.put("configNodes", cfgHits.stream().map(c -> {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("nodeKey", c.getNodeKey());
                one.put("nodeName", c.getNodeName());
                one.put("before", c.getAssignValue());
                one.put("after", replaceToken(c.getAssignValue(), from, to));
                return one;
            }).toList());
            // BPMN 里有、配置里没有的节点：改完 XML 仍会被配置盖不掉（配置缺行＝按 XML 走），如实标出
            hit.put("bpmnOnlyNodes", rw.hitNodes.stream()
                    .filter(n -> cfgHits.stream().noneMatch(c -> n.get("nodeKey").equals(c.getNodeKey())))
                    .map(n -> String.valueOf(n.get("nodeKey"))).toList());
            hit.put("xmlReplaceCount", rw.replaced);
            hit.put("runningInstances", countRunning(m.getModelKey()));
            hits.add(hit);
            xmlReplaced += rw.replaced;
            configRows += cfgHits.size();

            if (dryRun) continue;

            if (rw.replaced > 0) {
                checker.assertPublishable(rw.xml);   // 角色存在性/可达性/默认流不通过就不许发版
                WfModel upd = new WfModel();
                upd.setId(m.getId());
                upd.setBpmnXml(rw.xml);
                upd.setModelName(m.getModelName());
                modelService.publish(upd);
                published++;
            }
            for (WfNodeConfig c : cfgHits) {
                WfNodeConfig u = new WfNodeConfig();
                u.setId(c.getId());
                u.setAssignValue(replaceToken(c.getAssignValue(), from, to));
                nodeConfigMapper.updateById(u);
            }
            if (!cfgHits.isEmpty()) {
                WfNodeConfigResolver.evictAll();
            }
            log.info("参与人批量替换：模型 {} 角色 {}→{}，BPMN {} 处、配置 {} 行，操作人 {}",
                    m.getModelKey(), from, to, rw.replaced, cfgHits.size(), operator);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", dryRun);
        out.put("fromRoleCode", from);
        out.put("toRoleCode", to);
        out.put("matchedModels", hits.size());
        out.put("bpmnReplaceCount", xmlReplaced);
        out.put("configReplaceCount", configRows);
        out.put("publishedModels", published);
        out.put("details", hits);
        out.put("note", dryRun
                ? "试算未写库。执行前请逐条确认：在途实例按旧版本继续走（本系统不做实例迁移），新实例才用替换后的参与人。"
                : "已 publish 的模型版本+1；回滚＝同工具反向替换（" + to + "→" + from + "），配置行同步还原。");
        return out;
    }

    private static class Rewrite {
        String xml;
        int replaced;
        List<Map<String, Object>> hitNodes = new ArrayList<>();
    }

    /** 只重写属性值本身，其余字节保持原样（发布 diff 才看得清） */
    private Rewrite rewriteXml(String xml, String from, String to) {
        Rewrite r = new Rewrite();
        Map<String, String> nodeNameById = new LinkedHashMap<>();
        for (WfBpmnChecker.NodeInfo ni : checker.nodes(xml)) {
            nodeNameById.put(ni.id, ni.name);
        }
        Matcher m = CANDIDATE_GROUPS.matcher(xml);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String val = m.group(1);
            if (!WfBpmnChecker.tokens(val).contains(from)) continue;
            String nv = replaceToken(val, from, to);
            sb.append(xml, last, m.start(1)).append(nv);
            last = m.end(1);
            r.replaced++;
            String id = lookBackId(xml, m.start());
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("nodeKey", id);
            one.put("nodeName", nodeNameById.getOrDefault(id, ""));
            one.put("before", val);
            one.put("after", nv);
            r.hitNodes.add(one);
        }
        sb.append(xml, last, xml.length());
        r.xml = r.replaced == 0 ? xml : sb.toString();
        return r;
    }

    /** 属性串形如 flowable:candidateGroups="..." 且 id 在前，回看最近的 id="..." */
    private static String lookBackId(String xml, int at) {
        String head = xml.substring(Math.max(0, at - 400), at);
        Matcher m = Pattern.compile("id=\"([^\"]+)\"[^>]*$").matcher(head);
        String id = null;
        while (m.find()) id = m.group(1);
        return id == null ? "?" : id;
    }

    private static String replaceToken(String csv, String from, String to) {
        List<String> toks = WfBpmnChecker.tokens(csv);
        StringBuilder sb = new StringBuilder();
        for (String t : toks) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.equals(from) ? to : t);
        }
        return sb.toString();
    }

    private long countRunning(String modelKey) {
        Long n = instanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getModelKey, modelKey)
                .eq(WfInstance::getStatus, "RUNNING"));
        return n == null ? 0 : n;
    }
}
