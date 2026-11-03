// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysRole;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfProcessConfig;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfProcessConfigMapper;
import java.math.BigDecimal;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xml.sax.InputSource;

/**
 * BPMN 静态体检（批次 2 发版）。原实现只查"有没有配参与人"，改不了三类真事故：
 * ① role_code 写错或角色已停用 → 运行时零候选（批次 1 的 285 条 ESCALATE 就是被这个喂出来的）；
 * ② 节点无出流/从开始不可达 → 流程走到那儿直接卡死或永远走不到；
 * ③ 排他网关无显式默认流 → 金额缺失时 JUEL 全部条件为 false，Flowable 抛"没有可走的出线"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfBpmnChecker {

    private final SysRoleMapper sysRoleMapper;
    private final WfProcessConfigMapper processConfigMapper;
    private final WfModelMapper modelMapper;

    public static class Report {
        public final List<String> errors = new ArrayList<>();
        /** 连解析都没过＝其余设计项无从谈起。导入/保存前的硬闸只认这一枚（其余 errors 属设计缺陷，允许留草稿）。 */
        public boolean parseFailed;
        public final List<String> warnings = new ArrayList<>();
        /** 参与人引用了哪些角色（含停用/不存在），供批量改绑与影响面统计复用 */
        public final Set<String> roleRefs = new LinkedHashSet<>();
        public final List<NodeInfo> nodes = new ArrayList<>();
        /** 本流程是否按金额分档（＝任一网关出线条件引用 amount），供全库体检与告警规则复用 */
        public boolean amountTiered;
        /** 金额档位阈值（升序去重，取自条件表达式原文） */
        public final List<String> amountThresholds = new ArrayList<>();
        /** 该模型绑定的 bizType（wf_process_config ∪ wf_model.biz_type ∪ 同名隐式），体检清单用 */
        public final List<String> bizTypes = new ArrayList<>();
        public boolean valid() { return errors.isEmpty(); }
    }

    public static class NodeInfo {
        public String id;
        public String name;
        public String kind;      // userTask / exclusiveGateway / parallelGateway / startEvent / endEvent / serviceTask ...
        public String candidateGroups;
        public String assignee;
    }

    /** 解析 + 体检。XML 解析失败进 errors，不抛异常。 */
    public Report check(String bpmnXml) {
        Report r = new Report();
        Element process = parseProcess(bpmnXml, r);
        if (process == null) return r;
        collect(process, r);
        Map<String, List<String>> outgoing = outgoingMap(process);
        reachability(process, r, outgoing);
        gateways(process, r, outgoing);
        rolesExist(process, r);
        branchVarCoverage(process, r);
        amountSourceCoverage(r, process.getAttribute("id"));
        return r;
    }

    /** 只做结构解析，不产错误项：批量改绑工具用它拿节点清单 */
    public List<NodeInfo> nodes(String bpmnXml) {
        Report r = new Report();
        Element process = parseProcess(bpmnXml, r);
        if (process == null) return List.of();
        collect(process, r);
        return r.nodes;
    }

    private Element parseProcess(String bpmnXml, Report r) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            r.errors.add("BPMN XML 为空");
            return null;
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new InputSource(new StringReader(bpmnXml)));
            NodeList ps = doc.getElementsByTagNameNS("*", "process");
            if (ps.getLength() == 0) {
                r.errors.add("缺少 process 流程定义");
                return null;
            }
            return (Element) ps.item(0);
        } catch (Exception e) {
            // 禁止把解析器原文（如「前言中不允许有内容」）抛给业务人员：那是 XML 层术语，看不出该干什么。
            // 原文降级进日志备查，界面上给可执行结论。
            log.warn("BPMN XML 解析失败，解析器原文：{}", e.getMessage());
            r.parseFailed = true;
            r.errors.add("流程图内容不是有效的 BPMN XML（常见原因：文件被截断、开头多了非 XML 内容，或选错了文件）。"
                    + "请在「流程图」页签重新设计并保存，或改用系统导出的 .bpmn20.xml 再导入。");
            return null;
        }
    }

    private void collect(Element process, Report r) {
        boolean hasStart = false, hasEnd = false;
        NodeList all = process.getChildNodes();
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE || n.getLocalName() == null) continue;
            String ln = n.getLocalName();
            if ("startEvent".equals(ln)) hasStart = true;
            if ("endEvent".equals(ln)) hasEnd = true;
            if ("sequenceFlow".equals(ln) || "BPMNDiagram".equals(ln)) continue;
            Element el = (Element) n;
            NodeInfo ni = new NodeInfo();
            ni.id = el.getAttribute("id");
            ni.name = el.getAttribute("name");
            ni.kind = ln;
            ni.candidateGroups = el.getAttribute("flowable:candidateGroups");
            ni.assignee = el.getAttribute("flowable:assignee");
            r.nodes.add(ni);
            if ("userTask".equals(ln)) {
                if (ni.candidateGroups.isBlank() && ni.assignee.isBlank()) {
                    r.errors.add("人工节点「" + label(ni) + "」未配置参与人，运行时必然零候选");
                }
                for (String tok : tokens(ni.candidateGroups)) r.roleRefs.add(tok);
            }
        }
        if (!hasStart) r.errors.add("缺少开始节点");
        if (!hasEnd) r.errors.add("缺少结束节点");
        if (r.nodes.stream().noneMatch(n -> "userTask".equals(n.kind))) {
            r.warnings.add("流程中没有人工节点（纯自动链）");
        }
    }

    private Map<String, List<String>> outgoingMap(Element process) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        NodeList flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element fl = (Element) flows.item(i);
            out.computeIfAbsent(fl.getAttribute("sourceRef"), k -> new ArrayList<>())
                    .add(fl.getAttribute("targetRef"));
        }
        return out;
    }

    /** 从 startEvent 沿出线遍历（网关扇出全走），不可达的人工节点=死结构 */
    private void reachability(Element process, Report r, Map<String, List<String>> outgoing) {
        String startId = null;
        Set<String> reachable = new HashSet<>();
        for (NodeInfo ni : r.nodes) {
            if ("startEvent".equals(ni.kind) && startId == null) startId = ni.id;
        }
        if (startId == null) return;   // collect 已报错
        Deque<String> q = new ArrayDeque<>();
        q.push(startId);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 5000) {
            String cur = q.pop();
            if (!reachable.add(cur)) continue;
            for (String nxt : outgoing.getOrDefault(cur, List.of())) q.push(nxt);
        }
        for (NodeInfo ni : r.nodes) {
            if ("startEvent".equals(ni.kind) || "BPMNDiagram".equals(ni.kind)) continue;
            if (!reachable.contains(ni.id)) {
                r.errors.add("节点「" + label(ni) + "」从开始节点不可达（孤立结构，永远不会执行）");
            }
        }
    }

    private void gateways(Element process, Report r, Map<String, List<String>> outgoing) {
        Map<String, NodeInfo> byId = new LinkedHashMap<>();
        for (NodeInfo ni : r.nodes) byId.putIfAbsent(ni.id, ni);
        Set<String> beforeFirstApproval = beforeFirstApproval(r, outgoing);
        NodeList all = process.getChildNodes();
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            String ln = n.getLocalName();
            if (!"exclusiveGateway".equals(ln) && !"inclusiveGateway".equals(ln) && !"parallelGateway".equals(ln)) continue;
            Element el = (Element) n;
            String id = el.getAttribute("id");
            int out = outgoing.getOrDefault(id, List.of()).size();
            String label = id + "(" + (el.getAttribute("name").isBlank() ? ln : el.getAttribute("name")) + ")";
            if ("parallelGateway".equals(ln)) continue;   // 并行是多路并发，不适用"默认流"规则
            if (out < 2) {
                r.errors.add("网关「" + label + "」出口连线不足 2 条，分支不完整");
                continue;
            }
            boolean hasDefault = !el.getAttribute("default").isBlank();
            boolean hasUnconditional = false;
            NodeList flows = process.getElementsByTagNameNS("*", "sequenceFlow");
            for (int k = 0; k < flows.getLength(); k++) {
                Element fl = (Element) flows.item(k);
                if (!id.equals(fl.getAttribute("sourceRef"))) continue;
                String cond = fl.getTextContent() == null ? "" : fl.getTextContent().trim();
                if (cond.isEmpty()) hasUnconditional = true;
                else if (evalCondition(cond, Map.of("amount", 0.0)) == null) {
                    r.warnings.add("网关「" + label + "」的出口「" + fl.getAttribute("id")
                            + "」条件无法静态求值（仅支持 变量 与 数字/字符串 的比较）：" + cond);
                }
            }
            if (!hasDefault && !hasUnconditional) {
                r.errors.add("网关「" + label + "」既无 default 属性也无无条件出口：金额缺失时全部条件为 false，实例会卡在网关。必须补显式默认流");
            }
            amountTiers(process, r, el, id, label, ln, byId, beforeFirstApproval);
        }
        // 任何非 endEvent 节点缺出流 = 走进去就结束不了（BPMN 语义上会停在原地）
        for (NodeInfo ni : r.nodes) {
            if ("endEvent".equals(ni.kind) || "startEvent".equals(ni.kind)) continue;
            if (outgoing.getOrDefault(ni.id, List.of()).isEmpty()) {
                r.errors.add("节点「" + label(ni) + "」没有出流，流程走到此处即悬挂");
            }
        }
    }

    /** 网关出线（BPMN 文档序＝Flowable 排他网关的判定顺序） */
    private record FlowRef(String id, String target, String cond) {
    }

    private List<FlowRef> flowsOf(Element process, String sourceRef) {
        List<FlowRef> out = new ArrayList<>();
        NodeList flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int k = 0; k < flows.getLength(); k++) {
            Element fl = (Element) flows.item(k);
            if (!sourceRef.equals(fl.getAttribute("sourceRef"))) continue;
            NodeList ce = fl.getElementsByTagNameNS("*", "conditionExpression");
            String cond = ce.getLength() > 0 && ce.item(0).getTextContent() != null
                    ? ce.item(0).getTextContent().trim()
                    : (fl.getTextContent() == null ? "" : fl.getTextContent().trim());
            out.add(new FlowRef(fl.getAttribute("id"), fl.getAttribute("targetRef"), cond));
        }
        return out;
    }

    /**
     * 从开始节点出发、不穿过任何人工节点就能到达的节点集合＝"零审批可达面"。
     * 落在这个集合里的网关若又直通结束，就意味着整单一个人都没审过（比"跳档"严重一档，见 {@link #amountTiers}）。
     */
    private Set<String> beforeFirstApproval(Report r, Map<String, List<String>> outgoing) {
        Set<String> seen = new LinkedHashSet<>();
        Map<String, NodeInfo> byId = new LinkedHashMap<>();
        NodeInfo start = null;
        for (NodeInfo ni : r.nodes) {
            byId.putIfAbsent(ni.id, ni);
            if (start == null && "startEvent".equals(ni.kind)) start = ni;
        }
        if (start == null) return seen;
        Deque<String> q = new ArrayDeque<>();
        q.push(start.id);
        seen.add(start.id);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 5000) {
            for (String nxt : outgoing.getOrDefault(q.pop(), List.of())) {
                NodeInfo ni = byId.get(nxt);
                if (ni == null || seen.contains(nxt)) continue;
                if ("userTask".equals(ni.kind)) continue;   // 人工节点即止，越过它就不算"零审批"
                seen.add(nxt);
                q.push(nxt);
            }
        }
        return seen;
    }

    /** 按 Flowable 语义选路：排他＝文档序第一条为真（default 连线不参与判定），包容＝全部为真；都不成立走默认流 */
    private List<FlowRef> pickFlows(List<FlowRef> outs, String defaultId, String gwKind, double amount) {
        List<FlowRef> hit = new ArrayList<>();
        for (FlowRef f : outs) {
            if (f.cond().isEmpty() || f.id().equals(defaultId)) continue;
            if (Boolean.TRUE.equals(evalCondition(f.cond(), Map.of("amount", amount)))) {
                hit.add(f);
                if (!"inclusiveGateway".equals(gwKind)) return hit;   // 排他：第一条为真即走
            }
        }
        if (!hit.isEmpty()) return hit;
        for (FlowRef f : outs) {
            if (f.id().equals(defaultId) || f.cond().isEmpty()) return List.of(f);
        }
        return List.of();   // 无默认流：由"既无 default 也无无条件出口"那条 error 负责
    }

    /**
     * 金额网关的档位可达性与"低档短路"判定。
     *
     * <p>三条规则都按"会不会挡住已发布在用的流程"来定档：
     * <ul>
     *   <li><b>error</b> 永不命中的条件分支＝假档位（该档审批人永远收不到单，不会误伤）；</li>
     *   <li><b>error</b> 零审批短路：网关在"开始→网关"之间没有任何人工节点，且金额不达标时直通 End
     *       （＝不传 amount 引擎按 0 计算，整单无人审批即通过）；</li>
     *   <li><b>warning</b> 低档出口直达 End 但网关上游已有人工审批＝金额升级链的标准形态，
     *       金额不达标就不再追加高管审批。这类不拦截，只降级为 warning 并登记，免得默默扩大拦截面。</li>
     * </ul>
     * 只要有一条出口条件静态求不出（{@code &&}/{@code ||}/函数/未传变量），本网关整体不给结论——
     * 宁可漏报，也不给设计器一个假的"档位可达"。
     */
    private void amountTiers(Element process, Report r, Element gw, String gwId, String gwLabel, String gwKind,
                             Map<String, NodeInfo> byId, Set<String> beforeFirstApproval) {
        List<FlowRef> outs = flowsOf(process, gwId);
        boolean amountGw = outs.stream().anyMatch(f -> referencesAmount(f.cond()));
        if (!amountGw) return;
        r.amountTiered = true;
        String defaultId = gw.getAttribute("default");
        TreeSet<Double> probes = new TreeSet<>();
        TreeSet<Double> thresholds = new TreeSet<>();
        probes.add(0d);   // 发起不传 amount 时引擎实际写入的值
        boolean evaluable = true;
        for (FlowRef f : outs) {
            if (f.cond().isEmpty()) continue;
            Matcher m = AMOUNT_CMP.matcher(f.cond());
            if (!m.find() || evalCondition(f.cond(), Map.of("amount", 1.0)) == null) {
                evaluable = false;
                continue;
            }
            double t = Double.parseDouble(m.group(1));
            thresholds.add(t);
            probes.add(Math.max(0, t - 0.01));
            probes.add(t);
            probes.add(t + 0.01);
        }
        if (!thresholds.isEmpty()) probes.add(thresholds.last() + 1);
        for (double t : thresholds) r.amountThresholds.add(trimNum(t));
        if (!evaluable) return;

        Set<String> winners = new HashSet<>();
        for (double p : probes) {
            for (FlowRef f : pickFlows(outs, defaultId, gwKind, p)) winners.add(f.id());
        }
        for (FlowRef f : outs) {
            if (f.cond().isEmpty() || f.id().equals(defaultId) || winners.contains(f.id())) continue;
            NodeInfo t = byId.get(f.target());
            r.errors.add("金额网关「" + gwLabel + "」的出口「" + flowLabel(f, byId) + "」条件 " + f.cond()
                    + " 在任意金额下都不会被选中（区间被文档序更靠前的出口覆盖）＝假档位"
                    + (t != null && "userTask".equals(t.kind) ? "，节点「" + label(t) + "」的审批人永远收不到单" : "")
                    + "。请按阈值从小到大排布出口");
        }
        Set<FlowRef> lowLandings = new LinkedHashSet<>();
        lowLandings.addAll(pickFlows(outs, defaultId, gwKind, 0d));
        for (FlowRef f : outs) if (f.id().equals(defaultId)) lowLandings.add(f);
        for (FlowRef f : lowLandings) {
            NodeInfo t = byId.get(f.target());
            if (t == null || !"endEvent".equals(t.kind)) continue;
            if (beforeFirstApproval.contains(gwId)) {
                r.errors.add("金额网关「" + gwLabel + "」在金额不达标（含发起未传 amount，引擎按 0 计算）时经出口「"
                        + flowLabel(f, byId) + "」直达结束，且该网关之前没有任何人工审批节点＝整单零审批通过。"
                        + "请把该出口改指最低档审批节点，或把金额判定挪到已有审批之后");
            } else {
                r.warnings.add("金额网关「" + gwLabel + "」的低档出口「" + flowLabel(f, byId)
                        + "」直达结束：金额不达标（含未传 amount 按 0 计算）时本链不再追加任何审批。"
                        + "金额升级链即此形态（网关前已有人工审批），故只提示不拦截；"
                        + "若这一档本应有人审批，请把出口改指对应节点");
            }
        }
    }

    private static String flowLabel(FlowRef f, Map<String, NodeInfo> byId) {
        NodeInfo t = byId.get(f.target());
        return f.id() + "→" + (t == null ? f.target() : label(t));
    }

    private static String trimNum(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    /**
     * 规则①：分档流程 × 绑定 bizType 的金额来源核对（只 warning，不 error）。
     *
     * <p>本想按任务规则做成 error——"链上有金额网关、绑定的 bizType 又拿不到金额"＝发起侧必然不传 amount
     * ＝静默降档。但这样判会误伤：
     * {@link BillRouteRegistry.Route#amtCol()} 的规则是"待办回填用哪一列"，与"发起点是否传 amount"不是一回事——
     * MEETING 的注册表行 amtCol=NULL、TRAIN/TRAVEL 绑定的 EXP_APPLY 根本没有注册表行，
     * 而这三条流程的发起点确实把 exp_bill.total_amount 传进了引擎。按 amtCol 判 error 会挡住
     * 几条已发布在用的流程，所以只降级为 warning 并登记；
     * 真正的回归拦截由全库体检（{@code /designer/audit-models}）逐格比对承担。
     */
    private void amountSourceCoverage(Report r, String modelKey) {
        if (modelKey == null || modelKey.isBlank()) return;
        Set<String> bts = new LinkedHashSet<>();
        try {
            for (WfProcessConfig c : processConfigMapper.selectList(new LambdaQueryWrapper<WfProcessConfig>()
                    .eq(WfProcessConfig::getModelKey, modelKey))) {
                if (c.getBizType() == null || c.getBizType().isBlank()) continue;
                if ("DISABLED".equalsIgnoreCase(c.getStatus())) continue;
                bts.add(c.getBizType());
            }
            for (WfModel m : modelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                    .eq(WfModel::getModelKey, modelKey))) {
                if (m.getBizType() == null || m.getBizType().isBlank()) continue;
                bts.addAll(tokens(m.getBizType()));
            }
        } catch (Exception e) {
            log.warn("金额来源核对跳过（绑定表查询失败）: {}", e.getMessage());
            return;
        }
        r.bizTypes.addAll(bts);
        if (!r.amountTiered) return;
        for (String bt : bts) {
            BillRouteRegistry.Route route = BillRouteRegistry.of(bt);
            if (route != null && route.amtCol() != null) continue;
            r.warnings.add("本流程按金额分档，绑定单据类型「" + bt + "」在 BillRouteRegistry 没有金额列"
                    + (route == null ? "（注册表无该 bizType 行）" : "")
                    + "。注册表统计方式＝待办回填金额列，不代表发起点传了 amount；"
                    + "请核对该 bizType 的 start() 调用点（不传 amount 时引擎按 0 计算＝静默降到低档，高档审批形同虚设）");
        }
    }

    /** 角色码必须在 sys_role 存在且启用：写错一个字母＝该节点运行时零候选 */
    private void rolesExist(Element process, Report r) {
        if (r.roleRefs.isEmpty()) return;
        Set<String> known = new HashSet<>();
        Set<String> disabled = new HashSet<>();
        try {
            List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getRoleCode, r.roleRefs));
            for (SysRole role : roles) {
                boolean off = "DISABLED".equalsIgnoreCase(role.getStatus());
                (off ? disabled : known).add(role.getRoleCode());
            }
        } catch (Exception e) {
            log.warn("BPMN 角色存在性体检跳过（sys_role 查询失败）: {}", e.getMessage());
            return;
        }
        for (String code : r.roleRefs) {
            if (known.contains(code)) continue;
            if (disabled.contains(code)) {
                r.warnings.add("角色「" + code + "」已停用，运行时零候选（如需保留请走批量改绑迁移到在用车档）");
            } else {
                r.errors.add("角色「" + code + "」在 sys_role 中不存在（运行时必然零候选卡单）");
            }
        }
    }

    private static String label(NodeInfo ni) {
        return ni.name == null || ni.name.isBlank() ? ni.id : ni.name + "(" + ni.id + ")";
    }

    /** 逗号分隔候选组的精确词元 */
    public static List<String> tokens(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * 条件表达式是否引用金额变量——"这条流程按金额分档"的唯一判据。
     * 发起侧告警（不传 amount 会被静默降档）与发布期硬校验共用这一句法规则，避免两处各判一次。
     */
    public static boolean referencesAmount(String expr) {
        return expr != null && AMOUNT_REF.matcher(expr).find();
    }

    private static final Pattern AMOUNT_REF = Pattern.compile("\\bamount\\b");

    /** {@code amount 与数字字面量的比较}——组1＝阈值。金额网关的档位枚举用它取阈值，取不到就不参与可达性判定 */
    private static final Pattern AMOUNT_CMP =
            Pattern.compile("amount\\s*(?:>=|<=|!=|==|>|<)\\s*([0-9]+(?:\\.[0-9]+)?)");

    /**
     * 静态条件求值：只支持 {@code ${var op literal}} 的单个比较（现网全部 DOA/类型分支都是这个形态）。
     * 返回 null 表示"求不出"，由调用方降级处理；不支持 && / || / 函数调用，避免给出假绿色的路径预测。
     */
    public static Boolean evalCondition(String expr, Map<String, Object> vars) {
        if (expr == null) return null;
        String s = expr.trim();
        if (s.startsWith("${")) s = s.substring(2);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        s = s.trim();
        Matcher m = Pattern
                .compile("^(\\w+)\\s*(>=|<=|!=|==|>|<)\\s*(.+)$").matcher(s);
        if (!m.matches()) return null;
        Object left = vars.get(m.group(1));
        String op = m.group(2);
        String rightRaw = m.group(3).trim();
        boolean quoted = (rightRaw.startsWith("'") && rightRaw.endsWith("'")) || (rightRaw.startsWith("\"") && rightRaw.endsWith("\""));
        String right = quoted ? rightRaw.substring(1, rightRaw.length() - 1) : rightRaw;
        if (left == null) return null;
        if (left instanceof Number) {
            try {
                double a = ((Number) left).doubleValue();
                double b = Double.parseDouble(right);
                switch (op) {
                    case ">": return a > b;
                    case ">=": return a >= b;
                    case "<": return a < b;
                    case "<=": return a <= b;
                    case "==": return a == b;
                    case "!=": return a != b;
                    default: return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String a = String.valueOf(left);
        switch (op) {
            case "==": return a.equals(right);
            case "!=": return !a.equals(right);
            default: return null;   // 字符串不支持大小比较
        }
    }

    /** 模型是否可发布：供 WfModelService/批量改绑工具在部署前统一把关 */
    public void assertPublishable(String bpmnXml) {
        Report r = check(bpmnXml);
        if (!r.valid()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "流程校验未通过: " + String.join("; ", r.errors));
        }
    }

    /** 取当前租户已知角色码（批量改绑的目标角色存在性校验） */
    public Set<String> existingRoleCodes() {
        Set<String> out = new LinkedHashSet<>();
        for (SysRole role : sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>())) {
            if (role.getRoleCode() != null) out.add(role.getRoleCode());
        }
        return out;
    }

    /**
     * 治理校验②：分支变量白名单。
     * 网关条件表达式里引用的每个变量必须 ∈ {@link #BRANCH_VAR_WHITELIST}（＝ WfEngineFacade.start() 实际注入引擎的变量集），
     * 否则运行时该变量恒为 null → 条件求不出 → 分支永远无法选中（"看着能配、实际不生效"）。
     * 直接引用未注入变量（含拼写错误，如 amout）一律标 error，发布（assertPublishable）会拦截。
     * 经验证：全库 66 条条件表达式仅引用 {@code amount} 一个变量，本白名单覆盖全部已注入变量，
     * 不会对任何在用流程误报，仅拦截真正未注入/拼错的变量。
     */
    private static final Set<String> BRANCH_VAR_WHITELIST = Set.of(
            "amount", "orgType", "expenseType", "overStandard", "abroad",
            "hasLoan", "outOfPlan", "overLimit", "hasContract", "billType",
            // 同批补漏：bizType / subType 一直由 start() 真实注入（:516-:517 与 billType 归一环节），
            // 但白名单从未收录 ⇒ 设计器里把分支条件写在 bizType 上会被本校验误拦（"变量没注入"），
            // 而写在 subType 上（simulate 面板一直列着它）运行时又真的取不到。门面已把 subType 补为
            // 真实注入变量，两侧规则在此办结：白名单＝start() 实际注入集。
            "bizType", "subType");

    private static final Set<String> JUEL_KEYWORDS = Set.of(
            "true", "false", "null", "and", "or", "not", "eq", "ne", "gt", "lt", "ge", "le", "empty");

    private void branchVarCoverage(Element process, Report r) {
        NodeList flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element fl = (Element) flows.item(i);
            NodeList ce = fl.getElementsByTagNameNS("*", "conditionExpression");
            String cond = ce.getLength() > 0 && ce.item(0).getTextContent() != null
                    ? ce.item(0).getTextContent().trim()
                    : (fl.getTextContent() == null ? "" : fl.getTextContent().trim());
            if (cond.isBlank()) continue;
            String s = cond.replaceAll("\\$\\{", " ").replaceAll("\\}", " ");
            s = s.replaceAll("'[^']*'", " ").replaceAll("\"[^\"]*\"", " ");
            Matcher m = Pattern.compile("\\b[A-Za-z_]\\w*\\b").matcher(s);
            while (m.find()) {
                String tok = m.group();
                if (JUEL_KEYWORDS.contains(tok.toLowerCase())) continue;
                if (!BRANCH_VAR_WHITELIST.contains(tok)) {
                    r.errors.add("网关出口「" + fl.getAttribute("id") + "」条件引用了引擎未注入的分支变量「" + tok
                            + "」（不在已知注入集合 " + BRANCH_VAR_WHITELIST + " 内）。运行时该变量恒为 null，"
                            + "分支将无法选中，发布被拦截。请改用引擎已注入的变量，或在 WfEngineFacade.start() 中补充注入");
                }
            }
        }
    }
}
