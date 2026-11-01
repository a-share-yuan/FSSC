// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.service.WfAssigneeReplaceService;
import com.fssc.workflow.service.WfBpmnChecker;
import com.fssc.workflow.service.WfBpmnRepoService;
import com.fssc.workflow.service.WfModelService;
import com.fssc.workflow.service.WfQueryService;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 可视化流程设计器增强（需求文档：可视化流程设计器功能用例-财务共享专用）。
 * UC08 流程校验 / UC10 模拟调试 / UC09 版本发布 / UC11 导入导出。
 *
 * <p><b>G4 治理：三权分立</b>——本类只属<b>流程设计中心</b>（改 BPMN 模型本身），权限码由粗粒度
 * {@code wf:model} 迁移到 {@code wf:design:*}。迁移期用 {@code @RequiresPerm} 的<b>多码 OR 语义</b>
 * 并保留旧码 {@code wf:model}，故历史授权（CONFIG_ADMIN / IT_ADMIN）上线后不会 403。
 *
 * <p><b>摘旧码（写读分面收尾）</b>：OR 语义下"留旧码"等于"旧码单独开全族"，
 * 只持 {@code wf:model} 的 FSSC_SUPERVISOR 可直接调 {@code /publish} 越过界面发版，
 * 故按危害面分两档收窄：
 * <ul>
 *   <li><b>改模型/发版/建模型档</b>（validate / simulate / publish / import / batch-replace-assignee）
 *       ＝ {@code wf:design:update} 专码，旧码已摘。持有面 CONFIG_ADMIN／IT_ADMIN／WF_ADMIN 共 4 人，
 *       与前端「发布」按钮的 {@code wf:model:update}(112)、导入按钮的 {@code wf:model:create}(3359)
 *       逐字同批，故"按钮可见即接口可用"，不会点了 403。</li>
 *   <li><b>只读档</b>（versions / export / export-repo / audit-models / in-flight / publish-history /
 *       publish-diff）＝ {@code wf:design:list} 或 {@code wf:model}。此处<b>刻意保留旧码</b>：
 *       {@code FlowConfig.vue} 详情面板的「在途影响／导出入仓／导出」三枚按钮无 {@code v-if} 门控，
 *       持 104＋3358 即可见可点，摘掉 {@code wf:model} 会让这 2 人由"能看"变 403。
 *       先补前端门控（纯 {@code :8080} 热更，不需重启）再摘此档旧码。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf/designer")
@RequiredArgsConstructor
public class WfDesignerController {

    private final WfModelMapper modelMapper;
    private final WfModelService modelService;
    private final WfQueryService wfQueryService;
    private final WfBpmnRepoService bpmnRepoService;
    private final WfBpmnChecker bpmnChecker;
    private final WfAssigneeReplaceService assigneeReplaceService;

    // UC08 流程合法性校验

    /**
     * 校验逻辑抽到 {@link WfBpmnChecker}：发版与"参与人批量替换"共用同一道。
     * 原实现只查"有没有配参与人"，查不出角色码写错（运行时零候选）、节点不可达、网关缺默认流这三类会直接卡单的结构问题。
     */
    @RequiresPerm("wf:design:update")
    @PostMapping("/validate")
    public Result<Map<String, Object>> validate(@RequestBody Map<String, Object> body) {
        WfBpmnChecker.Report r = bpmnChecker.check((String) body.get("bpmnXml"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", r.valid());
        out.put("errors", r.errors);
        out.put("warnings", r.warnings);
        out.put("roleRefs", r.roleRefs);
        return Result.success(out);
    }

    private String elAttr(Element el, String name) {
        return el.getAttribute(name);
    }

    // UC10 模拟调试运行

    /**
     * 路径预测（UC10）：按传入的分支变量真走一遍图。
     *
     * <p>原实现有三处假绿色：只认 {@code amount>N} 一种条件、网关永远取第一条出线、并行网关完全不处理，
     * 且把入参 itemName 收下不用。批次 2 每条链都要补金额网关，预测不可信就等于没验收。
     * 现在：条件按 {@code 变量 op 字面量} 求值（数字/字符串、{@code > >= < <= == !=}），排他取文档序第一条为真、
     * 不成立走默认流，包容/并行扇出全展开，汇聚点去重计入。求不出的条件明说"求不出"，不猜。
     */
    @RequiresPerm("wf:design:update")
    @PostMapping("/simulate")
    public Result<Map<String, Object>> simulate(@RequestBody Map<String, Object> body) {
        String bpmnXml = (String) body.get("bpmnXml");
        Map<String, Object> vars = new LinkedHashMap<>();
        Object amt = body.get("amount");
        double amount = amt == null || String.valueOf(amt).isBlank() ? 0 : Double.parseDouble(String.valueOf(amt));
        vars.put("amount", amount);
        for (String k : new String[]{"expenseType", "billType", "subType", "hasInvoice", "payMethod", "orgType"}) {
            Object v = body.get(k);
            if (v != null && !String.valueOf(v).isBlank()) vars.put(k, String.valueOf(v));
        }
        String itemName = body.get("itemName") == null ? "" : String.valueOf(body.get("itemName"));
        if (!vars.containsKey("expenseType") && !itemName.isBlank()) vars.put("expenseType", itemName);

        List<Map<String, String>> trace = new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("trace", trace);
        r.put("vars", vars);
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new InputSource(new StringReader(bpmnXml)));
            Element process = (Element) doc.getElementsByTagNameNS("*", "process").item(0);
            Map<String, List<Element>> outs = outgoingBySource(process);
            String startId = null;
            for (Element fl : outs.getOrDefault(startIdOf(process), List.of())) {
                startId = fl.getAttribute("targetRef");
                break;
            }
            if (startId == null) {
                trace.add(row("error", "无起点", "开始节点没有出线"));
                return Result.success(r);
            }
            int approvals = walk(process, outs, startId, vars, trace, new LinkedHashSet<>());
            r.put("approvalSteps", approvals);
            // 与发版同一套校验：预测顺手把结构问题带出来，设计器不必先点"校验"再点"模拟"
            WfBpmnChecker.Report chk = bpmnChecker.check(bpmnXml);
            r.put("errors", chk.errors);
            r.put("warnings", chk.warnings);
        } catch (Exception e) {
            // 设计器预演：模拟异常作为 trace 行回传前端呈现，属预期错误展示，不抛出
            trace.add(row("error", "模拟失败", e.getMessage()));
        }
        return Result.success(r);
    }

    private String startIdOf(Element process) {
        NodeList all = process.getChildNodes();
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "startEvent".equals(n.getLocalName())) {
                return elAttr((Element) n, "id");
            }
        }
        return "__none__";
    }

    private Map<String, List<Element>> outgoingBySource(Element process) {
        Map<String, List<Element>> out = new LinkedHashMap<>();
        NodeList flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element fl = (Element) flows.item(i);
            out.computeIfAbsent(fl.getAttribute("sourceRef"), k -> new ArrayList<>()).add(fl);
        }
        return out;
    }

    /** 深度优先展开路径，返回途经人工节点数（汇聚/回环只记一次，不重复计数） */
    private int walk(Element process, Map<String, List<Element>> outs, String id,
                     Map<String, Object> vars, List<Map<String, String>> trace, Set<String> visited) {
        if (id == null || trace.size() > 200) return 0;
        Element cur = findElement(process, id);
        if (cur == null) {
            trace.add(row("error", "连线指向不存在的节点", id));
            return 0;
        }
        String ln = cur.getLocalName() == null ? "" : cur.getLocalName();
        String name = cur.getAttribute("name");
        if (!visited.add(id)) {
            trace.add(row("join", name.isBlank() ? id : name, "汇聚点：另一分支已计入，本分支到此"));
            return 0;
        }
        if ("endEvent".equals(ln)) {
            trace.add(row("end", name.isBlank() ? "结束" : name, "流程结束"));
            return 0;
        }
        if ("exclusiveGateway".equals(ln) || "inclusiveGateway".equals(ln) || "parallelGateway".equals(ln)) {
            List<String> targets = gatewayTargets(cur, outs.getOrDefault(id, List.of()), vars, trace);
            int n = 0;
            for (String t : targets) n += walk(process, outs, t, vars, trace, visited);
            return n;
        }
        trace.add(row("task", name.isBlank() ? ln + " " + id : name, nodeTip(cur, ln, vars)));
        int self = "userTask".equals(ln) ? 1 : 0;
        List<Element> flows = outs.getOrDefault(id, List.of());
        if (flows.isEmpty()) {
            trace.add(row("error", "节点无出流", id + "：流程走到此处即悬挂"));
            return self;
        }
        if (flows.size() > 1) {
            // BPMN 语义：活动有多条无条件出线 = 隐式并行分叉，各支都会执行
            trace.add(row("gateway", "隐式并行分叉（" + id + "）", "出口 " + flows.size() + " 条，各支同时执行"));
            int n = 0;
            for (Element fl : flows) n += walk(process, outs, fl.getAttribute("targetRef"), vars, trace, visited);
            return n;
        }
        return self + walk(process, outs, flows.get(0).getAttribute("targetRef"), vars, trace, visited);
    }

    /** 网关选路：排他取文档序第一条为真、包容取全部为真、并行取全部；都不成立走默认流 */
    private List<String> gatewayTargets(Element gw, List<Element> flows,
                                        Map<String, Object> vars, List<Map<String, String>> trace) {
        String ln = gw.getLocalName();
        List<String> targets = new ArrayList<>();
        StringBuilder why = new StringBuilder();
        Element defFlow = null;
        boolean condHit = false;
        for (Element fl : flows) {
            String cond = condOf(fl);
            if (cond.isEmpty()) {
                if (defFlow == null) defFlow = fl;
                continue;
            }
            if ("parallelGateway".equals(ln)) continue;
            Boolean v = WfBpmnChecker.evalCondition(cond, vars);
            String flName = fl.getAttribute("name");
            String tag = flName.isBlank() ? fl.getAttribute("id") : flName;
            if (v == null) {
                why.append("[无法静态求值:").append(tag).append("] ");
                continue;
            }
            if (!v) continue;
            targets.add(fl.getAttribute("targetRef"));
            why.append("命中 ").append(tag).append(" ");
            condHit = true;
            if (!"inclusiveGateway".equals(ln)) break;   // 排他：第一条为真即走
        }
        if (!condHit || "parallelGateway".equals(ln)) {
            if ("parallelGateway".equals(ln)) {
                for (Element fl : flows) targets.add(fl.getAttribute("targetRef"));
                why.insert(0, "并行扇出 " + flows.size() + " 支（各支独立办理，全通过才汇聚）");
            } else {
                String def = gw.getAttribute("default");
                if (!def.isBlank()) {
                    // default 属性存的是"连线 id"，不是目标节点 id——必须解析成 targetRef，
                    // 否则模拟会把连线当节点走一步，路径预测直接失真。
                    Element dfl = flowById(flows, def);
                    if (dfl != null) {
                        targets.add(dfl.getAttribute("targetRef"));
                        why.append("→ 默认流 ").append(def);
                    } else {
                        why.append("→ default 指向不存在的连线 ").append(def).append("（运行期网关抛错）");
                    }
                } else if (defFlow != null) {
                    targets.add(defFlow.getAttribute("targetRef"));
                    why.append("→ 默认流(无条件出口) ").append(defFlow.getAttribute("id"));
                } else {
                    why.append("→ 无命中且无默认流：运行期会在网关抛错（发版校验已标 error）");
                }
            }
        }
        String label = gw.getAttribute("name");
        trace.add(row("gateway", (label.isBlank() ? gw.getAttribute("id") : label) + "（" + gw.getAttribute("id") + "）",
                why.length() == 0 ? "无条件出口" : why.toString()));
        return targets;
    }

    private static Element flowById(List<Element> flows, String id) {
        for (Element fl : flows) if (id.equals(fl.getAttribute("id"))) return fl;
        return null;
    }

    private static String condOf(Element flow) {
        NodeList c = flow.getElementsByTagNameNS("*", "conditionExpression");
        if (c.getLength() > 0 && c.item(0).getTextContent() != null) return c.item(0).getTextContent().trim();
        return flow.getTextContent() == null ? "" : flow.getTextContent().trim();
    }

    private Map<String, String> row(String type, String name, String tip) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("name", name);
        m.put("tip", tip);
        return m;
    }

    private String nodeTip(Element el, String ln, Map<String, Object> vars) {
        String cg = el.getAttribute("flowable:candidateGroups");
        String as = el.getAttribute("flowable:assignee");
        if (!cg.isBlank()) return "候选角色: " + cg;
        if (!as.isBlank()) return "办理人: " + as;
        if ("serviceTask".equals(ln) || "scriptTask".equals(ln)) return "系统自动执行";
        return "人工节点（未配参与人）";
    }

    private Element findElement(Element process, String id) {
        NodeList all = process.getChildNodes();
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) n;
                if (id.equals(el.getAttribute("id"))) return el;
            }
        }
        return null;
    }

    // UC09 版本发布 / UC11 导入导出

    /** 发布：bpmnXml 存入模型，版本+1，重新部署由 WfModelService.update 处理 */
    @RequiresPerm("wf:design:update")
    @OperLog(module = "WF_MODEL", operType = "PUBLISH", desc = "发布流程模型")
    @PostMapping("/publish")
    public Result<WfModel> publish(@RequestBody WfModel model) {
        if (model.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型ID必填");
        }
        WfModel old = modelMapper.selectById(model.getId());
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在");
        }
        // 校验
        Map<String, Object> v = new HashMap<>();
        v.put("bpmnXml", model.getBpmnXml());
        Map<String, Object> vres = validate(v).getData();
        if (!Boolean.TRUE.equals(vres.get("valid"))) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "流程校验未通过: " + String.join("; ", (List<String>) vres.get("errors")));
        }
        WfModel upd = new WfModel();
        upd.setId(model.getId());
        upd.setBpmnXml(model.getBpmnXml());
        upd.setModelName(model.getModelName() == null ? old.getModelName() : model.getModelName());
        upd.setStatus("DEPLOYED");
        // 走 Service 发布：无条件重新部署到 Flowable 并回写 deploy_id / proc_def_id / version。
        // （历史实现直接 modelMapper.updateById 绕过部署，导致 deploy_id 恒为 NULL、
        //   TEMPLATE 流程改了 BPMN 也不生效——见 WfModelService.publish 注释）
        modelService.publish(upd);
        return Result.success(modelMapper.selectById(model.getId()));
    }

    /** 历史版本：按 modelKey 查（当前实现单版本，返回当前 + 元数据） */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/versions")
    public Result<List<WfModel>> versions(@RequestParam String modelKey) {
        List<WfModel> list = modelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, modelKey).orderByDesc(WfModel::getVersion));
        return Result.success(list);
    }

    /** 导出：返回 BPMN XML（前端下载） */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/export")
    public Result<Map<String, Object>> export(@RequestParam Long id) {
        WfModel m = modelMapper.selectById(id);
        if (m == null) throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在");
        Map<String, Object> r = new HashMap<>();
        r.put("modelKey", m.getModelKey());
        r.put("modelName", m.getModelName());
        r.put("version", m.getVersion());
        r.put("bpmnXml", m.getBpmnXml());
        return Result.success(r);
    }

    /** 导入：接收 modelKey/modelName/bpmnXml 创建新模型 */
    @RequiresPerm("wf:design:update")
    @OperLog(module = "WF_MODEL", operType = "IMPORT", desc = "导入流程模型")
    @PostMapping("/import")
    public Result<Long> importModel(@RequestBody Map<String, Object> body) {
        String modelKey = (String) body.get("modelKey");
        String modelName = (String) body.get("modelName");
        String bpmnXml = (String) body.get("bpmnXml");
        if (modelKey == null || modelKey.isBlank() || bpmnXml == null || bpmnXml.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型编码与BPMN必填");
        }
        // 只判空会放进来一份"永远校验不过、也发布不了"的草稿模型（人工测试反馈：校验/模拟/发布三处全卡在同一份坏 XML 上）
        WfBpmnChecker.Report parsed = bpmnChecker.check(bpmnXml);
        if (parsed.parseFailed) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    parsed.errors.isEmpty() ? "BPMN 文件无法解析，请改用系统导出的 .bpmn20.xml 再导入" : parsed.errors.get(0));
        }
        long dup = modelMapper.selectCount(new LambdaQueryWrapper<WfModel>().eq(WfModel::getModelKey, modelKey));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型编码已存在: " + modelKey);
        }
        WfModel m = new WfModel();
        m.setModelKey(modelKey);
        m.setModelName(modelName == null ? modelKey : modelName);
        m.setBpmnXml(bpmnXml);
        m.setSource("IMPORT");
        m.setVersion(1);
        m.setStatus("DRAFT");
        m.setTenantId(TenantContext.getTenantId());
        modelMapper.insert(m);
        return Result.success(m.getId());
    }

    /**
     * 参与人批量替换：把某角色从所有流程节点上换成另一角色，
     * BPMN candidateGroups 与 wf_node_config 两侧同改并立即 publish（版本+1）。
     * 默认 dryRun=true 只出影响面清单。
     */
    @RequiresPerm("wf:design:update")
    @OperLog(module = "WF_MODEL", operType = "UPDATE", desc = "流程参与人批量替换")
    @PostMapping("/batch-replace-assignee")
    public Result<Map<String, Object>> batchReplaceAssignee(@RequestBody Map<String, Object> body) {
        String from = (String) body.get("fromRoleCode");
        String to = (String) body.get("toRoleCode");
        boolean dryRun = !"false".equalsIgnoreCase(String.valueOf(body.get("dryRun")));
        Object keys = body.get("modelKeys");
        List<String> modelKeys = new ArrayList<>();
        if (keys instanceof List<?> list) {
            for (Object o : list) if (o != null) modelKeys.add(String.valueOf(o));
        } else if (keys instanceof String s && !s.isBlank()) {
            for (String s2 : s.split(",")) if (!s2.isBlank()) modelKeys.add(s2.trim());
        }
        LoginUser lu = UserContext.get();
        Long operator = lu == null ? null : lu.getUserId();
        return Result.success(assigneeReplaceService.replace(from, to, modelKeys, dryRun, operator));
    }

    // 在途影响面 / BPMN 仓库化

    /**
     * 全库体检（只读）：把 wf_model 每个 modelKey 的最高版本逐个过一遍发布，
     * 回传"哪个模型有几条 error/warning、是否金额分档、档位阈值、绑定哪些 bizType"。
     *
     * <p>用途＝金额网关新规则的回归基线：规则变更前/变更后各跑一次逐格比对，在用模型的 error 必须零新增
     * ——网关出口顺序动一下就可能把整个流程族挡在发布门外，而这里是只读的，不碰任何单据。
     * 与 {@code /validate} 用的是同一个 checker，所以体检结论就等于下一次发布的校验结论。
     */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/audit-models")
    public Result<Map<String, Object>> auditModels(@RequestParam(defaultValue = "false") boolean includeClean) {
        List<WfModel> all = modelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                .orderByAsc(WfModel::getModelKey).orderByAsc(WfModel::getVersion));
        Map<String, WfModel> latest = new LinkedHashMap<>();
        for (WfModel m : all) latest.put(m.getModelKey(), m);   // 同 key 后写覆盖前写＝最高版本
        List<Map<String, Object>> items = new ArrayList<>();
        int errorTotal = 0;
        int tieredTotal = 0;
        for (WfModel m : latest.values()) {
            WfBpmnChecker.Report rep = bpmnChecker.check(m.getBpmnXml());
            if (rep.amountTiered) tieredTotal++;
            errorTotal += rep.errors.size();
            if (!includeClean && rep.errors.isEmpty() && rep.warnings.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelKey", m.getModelKey());
            row.put("version", m.getVersion());
            row.put("tiered", rep.amountTiered);
            row.put("thresholds", rep.amountThresholds);
            row.put("bizTypes", rep.bizTypes);
            row.put("errors", rep.errors);
            row.put("warnings", rep.warnings);
            items.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modelRows", all.size());
        // 对照行：distinct modelKey 明显少于在用量（现网 82）⇒ 说明读漏了，不能把体检当通过
        out.put("modelCount", latest.size());
        out.put("tieredCount", tieredTotal);
        out.put("errorTotal", errorTotal);
        out.put("items", items);
        return Result.success(out);
    }

    /**
     * 在途实例影响面：某模型当前 RUNNING 且仍绑定旧流程定义的实例清单。
     * 发布前先查它做提示，发布后再查它决定要不要逐笔跟进。
     */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/in-flight")
    public Result<Map<String, Object>> inFlight(@RequestParam String modelKey) {
        WfModel m = modelMapper.selectOne(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, modelKey)
                .orderByDesc(WfModel::getId).last("limit 1"));
        return Result.success(wfQueryService.inFlightByModel(modelKey, m == null ? null : m.getProcDefId()));
    }

    /** 全量导出 wf_model.bpmn_xml 到仓库目录（fssc.wf.bpmn-repo-dir），随代码一起提交即完成版本化 */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @OperLog(module = "WF_MODEL", operType = "EXPORT", desc = "BPMN 全量导出入仓")
    @PostMapping("/export-repo")
    public Result<Map<String, Object>> exportRepo() {
        return Result.success(bpmnRepoService.exportAll());
    }

    /** 某模型的发布 diff 记录清单（发布动作自动落盘，见 WfModelService.deployToEngine） */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/publish-history")
    public Result<List<Map<String, Object>>> publishHistory(@RequestParam String modelKey) {
        return Result.success(bpmnRepoService.publishHistory(modelKey));
    }

    /** 单个 diff 文件内容（仅允许 publish/&lt;key&gt;/ 下的 .diff 文件名） */
    @RequiresPerm({"wf:design:list", "wf:model"})
    @GetMapping("/publish-diff")
    public Result<Map<String, Object>> publishDiff(@RequestParam String modelKey, @RequestParam String name) {
        String content = bpmnRepoService.diffContent(modelKey, name);
        if (content == null) {
            // 校验不过与文件不存在同样处理：不把路径参数拼进路径，也不回显文件内容
            return Result.fail(400, "diff 记录不存在或文件名不合法（仅允许发布记录清单中列出的 .diff 文件名）");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modelKey", modelKey);
        r.put("name", name);
        r.put("content", content);
        return Result.success(r);
    }
}
