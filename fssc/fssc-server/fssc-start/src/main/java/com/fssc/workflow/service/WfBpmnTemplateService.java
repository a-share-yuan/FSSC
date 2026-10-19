package com.fssc.workflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * BPMN 模板生成：由 wf_business_flow.node_json（对象数组 name/approver/condition）生成可部署 BPMN。
 * 规则：
 *  - 系统自动节点跳过（预算检查/发票验真/记账等系统动作在单据提交时已由业务代码完成，流程引擎不生成任务）
 *  - 审批角色节点 → userTask + flowable:candidateGroups="角色编码"（按角色路由）
 *  - 直接主管/经办人等动态节点 → userTask + flowable:assignee="${approver}"
 *  - condition 含"金额>X"的角色节点 → 前置排他网关（${amount > X} 走该节点，否则跳过）
 */
@Service
public class WfBpmnTemplateService {

    @Autowired
    private WfNodeConfigMapper wfNodeConfigMapper;

    /** 审批角色中文名 → 角色编码（需求31 审批人角色体系） */
    private static final Map<String, String> ROLE_MAP = new LinkedHashMap<>();
    static {
        ROLE_MAP.put("部门经理", "DEPT_MANAGER");
        ROLE_MAP.put("分管领导", "DIV_LEADER");
        ROLE_MAP.put("中心总监", "CENTER_DIRECTOR");              // 板块中心总监（EER02/04 流程）
        ROLE_MAP.put("区域总监", "CENTER_DIRECTOR");
        ROLE_MAP.put("财务审核岗", "FIN_AUDIT");
        ROLE_MAP.put("财务审核", "FIN_AUDIT");
        ROLE_MAP.put("财务经理", "FIN_MANAGER");
        ROLE_MAP.put("预算管理员", "BUDGET_MANAGER");
        ROLE_MAP.put("资金岗", "FUND_STAFF");
        ROLE_MAP.put("行政核对岗", "ADMIN_CHECK");
        ROLE_MAP.put("行政", "ADMIN_CHECK");
        ROLE_MAP.put("培训部门负责人", "TRAIN_MANAGER");
        // P0-2 补 10 角色：流程 v3.0 + 会议纪要要求删除/新增/角色映射（EER OTC PTP TR GL FM）
        ROLE_MAP.put("项目会计", "PROJECT_ACCOUNTANT");        // EER02 纪要要求删除，保留映射防止 BPMN 残留时 roleCode=null 派不出任务
        ROLE_MAP.put("基地分管领导", "BASE_DIV_LEADER");        // EER02 纪要要求删除，同上
        ROLE_MAP.put("财务总监", "CFO");                        // EER03-03 一般报销加财务总监阈值节点
        ROLE_MAP.put("总裁", "PRESIDENT");                      // EER03-01 国外出差 → 总裁特批
        ROLE_MAP.put("总经理", "GM");                            // LOAN/CLAIM 金额阈值 >5 万 总经理特批
        ROLE_MAP.put("HRD", "HRD");                              // EER04-01-02 培训事前申请金额阈值 → HRD
        ROLE_MAP.put("人力资源总监", "HRD");                     // 别名
        ROLE_MAP.put("共享会计", "FSSC_AUDITOR");                // 共享作业链 AUDIT/票据审核（费用组/应付组会计）
        ROLE_MAP.put("共享费用会计", "FSSC_AUDITOR");
        ROLE_MAP.put("共享应付会计", "FSSC_AUDITOR");
        ROLE_MAP.put("共享主管", "FSSC_SUPERVISOR");             // 共享主管复核（PAY 前 / BOOKKEEP 前）
        ROLE_MAP.put("共享费用主管", "FSSC_SUPERVISOR");
        ROLE_MAP.put("共享应付主管", "FSSC_SUPERVISOR");
        ROLE_MAP.put("出纳", "CASHIER");                         // LOAN/PREPAY/CLAIM 付款节点、收款
        ROLE_MAP.put("资金出纳", "CASHIER");
        // P0-1a 续补 PTP/OTC/FA/TR/GL/FM 6 域角色映射（按流程 v3.0 节点中出现的审批人）
        ROLE_MAP.put("采购经理", "PURCHASE_MANAGER");
        ROLE_MAP.put("采购负责人", "PURCHASE_MANAGER");
        ROLE_MAP.put("采购部经理", "PURCHASE_MANAGER");
        ROLE_MAP.put("应付会计", "FIN_AP");                     // PTP 基地应付会计（区别共享应付会计 FSSC_AUDITOR）
        ROLE_MAP.put("基地应付会计", "FIN_AP");
        ROLE_MAP.put("应收会计", "FIN_AR");                     // OTC 基地应收
        ROLE_MAP.put("基地应收会计", "FIN_AR");
        ROLE_MAP.put("信贷/催收专员", "AR_COLLECTOR");
        ROLE_MAP.put("催收专员", "AR_COLLECTOR");
        ROLE_MAP.put("供应商对账专员", "RECON_CLERK");
        ROLE_MAP.put("对账专员", "RECON_CLERK");
        ROLE_MAP.put("税务经理", "TAX_MANAGER");                // GL 税金计提/支付
        ROLE_MAP.put("资产管理员", "FA_ASSET_ADMIN");           // FA 资产新增/调拨/报废
        ROLE_MAP.put("资产管理岗", "FA_ASSET_ADMIN");
        ROLE_MAP.put("资产核算岗", "FA_ASSET_ACCOUNT");          // FA_DEPRECIATION 月度折旧计提审批责任岗（设计稿共同中心资产核算岗）
        ROLE_MAP.put("仓库管理员", "WH_KEEPER");                // FA 存货出入库
        ROLE_MAP.put("库管", "WH_KEEPER");
        ROLE_MAP.put("总账会计", "FIN_GL");                     // GL 手工凭证/月结/预提待摊
        ROLE_MAP.put("档案管理员", "FM_ARCHIVIST");             // FM 凭证归档/借阅/销毁
        ROLE_MAP.put("档案岗", "FM_ARCHIVIST");
        // 逐节点比对恢复：OTC02/FA06/TR01 设计层级新增角色（基地/板块多层 + 金额分档授权）
        ROLE_MAP.put("直线上级", "DIRECT_SUPERVISOR");          // OTC02 开票业务真实性初审
        ROLE_MAP.put("基地财务经理", "BASE_FIN_MANAGER");        // OTC02/FA06/TR01 基地财务经理审核
        ROLE_MAP.put("板块财务经理", "PLATE_FIN_MANAGER");       // OTC02/TR01 板块财务经理审核
        ROLE_MAP.put("项目管理中心总监", "PM_CENTER_DIRECTOR"); // OTC02 开票合规性审核
        ROLE_MAP.put("基地总经理", "BASE_GM");                  // FA06 原值分档授权
        ROLE_MAP.put("板块分管领导", "PLATE_DIV_LEADER");        // FA06 原值 10-50 万档
        // V1.1.8 上会环节：董办/董会决议（大额/重大损失上会）
        ROLE_MAP.put("董会", "BOARD_OF_DIRECTORS");
        ROLE_MAP.put("董事会", "BOARD_OF_DIRECTORS");
        ROLE_MAP.put("董办", "BOARD_OFFICE");
        ROLE_MAP.put("总裁办公会", "BOARD_OFFICE");
        ROLE_MAP.put("董办/总裁办公会", "BOARD_OFFICE");
        // V1.1.10 PTP02-02 并行会签 + 高管审批链
        ROLE_MAP.put("采购总监", "PURCHASE_DIRECTOR");           // 采购负责人会签后审核
        ROLE_MAP.put("采购管理部总监", "PURCHASE_DIRECTOR");     // 采购管理部总监（PTP01 供应商准入/FA01 会签）
        ROLE_MAP.put("副总裁", "VP");                            // 大额付款高级审批
        // V1.1.11 FA01 资本性支出 / PTP01 供应商准入 补链：新增归口部门审批角色
        ROLE_MAP.put("外包及工程管理部经理", "OUTSOURCE_ENGG_MGR");  // FA01 基建/工程类支出首审
        ROLE_MAP.put("研发设计中心总监", "RND_CENTER_DIRECTOR");     // PTP01 研发/设备类供应商审核
        ROLE_MAP.put("企业管理部经理", "ENT_MGMT_MGR");              // PTP01 企管归口审核
        // V1.2.0 拆分流程（FA/GL/OTC/PTP 域）补 3 角色：新建/拆分流程节点中出现的审批人
        ROLE_MAP.put("财务专员", "FIN_STAFF");                        // INV_DISPOSAL_EXEC 签订处置销售协议
        ROLE_MAP.put("财务扫描岗", "SCAN_STAFF");                     // 各流程扫描影像/单据节点
        ROLE_MAP.put("资金支付岗", "FUND_PAY_STAFF");                 // GL_SALARY_PAY 网银录入/审核
        // V1.2.1 全量 node_json 按 sys_role.role_name 重建（sync_all_bizflow）补 6 角色中文名
        ROLE_MAP.put("业务财务经理", "BIZ_FIN_MANAGER");              // LOAN 业务财务经理节点
        ROLE_MAP.put("普通员工", "COMMON_EMP");                       // 员工默认角色（CLAIAM 兜底）
        ROLE_MAP.put("成本费用岗", "COST_STAFF");                     // LOAN/费用成本核算
        ROLE_MAP.put("板块财务总监", "FIN_DIRECTOR");                 // GL/FA 板块财务总监审批
        ROLE_MAP.put("项目经理/费用承担部门负责人", "PROJECT_MANAGER"); // FA/INV 项目费用承担部门
        ROLE_MAP.put("项目经理", "PROJECT_MANAGER");                 // PAY_PLAN_PLATE 多部门并行会签拆分项(逆序"费用承担部门负责人/项目经理"整串不命中)
        ROLE_MAP.put("费用承担部门负责人", "PROJECT_MANAGER");        // 同上拆分项
        ROLE_MAP.put("AR收入确认核算岗", "FIN_AR");                  // EER31-01 AR服务申请收入确认审核(缺映射曾退化为 assignee 直派 admin)
        ROLE_MAP.put("共享支持岗", "SHARING_SUPPORT");                // LOAN 共享支持岗受理
    }

    /** 金额分档条件（支持「金额>X」与资产域「原值>X」，均映射到流程变量 amount） */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:金额|原值)\\s*[>≥]\\s*(\\d+)");

    /** 节点：name / approver / condition / amountThreshold / skip / 分支标记(国外出差/招待费) */
    private static class Node {
        String name; String approver; String condition;
        String key;   // 稳定节点编码：提供则 userTask id 用它，与 wf_node_config.node_key 精确对齐；缺省时退回位置式 Task_N
        Double amountThreshold; boolean skip; String roleCode; boolean isAssignee;
        boolean abroad; boolean entertain;
        boolean parallel; List<String> roles;   // 并行会签：approver 含"/"分割的多角色
        String multiType;  // 节点配置多签类型：ALL（并行会签）/SEQ（顺序会签）/VOTE（票签）；空=单办理人，不生成多实例
        Integer voteRatio; // VOTE 票签通过比例（1-100，按百分比），仅 VOTE 有意义；遗留列，仅作新列缺省回退
        // —— G2 第 2 条「票＋人双维」：与 wf_node_config 的四列一一对应，新列存在时优先于 voteRatio
        String voteMode;      // RATIO 比例（默认）/ COUNT 确定票数
        Integer voteValue;    // RATIO 时百分比 1~100；COUNT 时所需票数
        String voteBasis;     // BY_PERSON 分母=参与人总数(应到，默认) / BY_VOTE 分母=已投票数(实到)
        String voteFallback;  // 不达票兜底：REJECT（默认）/ PASS
        // —— G2 第 3 条「超时」：timeout_hours > 0 时生成边界定时器（仅非多实例 userTask）
        Integer timeoutHours;
        String timeoutAction;
    }

    /** 多实例节点规格（供运行时注入候选集合 / 计票变量） */
    public static class MiNode {
        public final String taskId;
        public final String multiType;
        public final Integer voteRatio;
        public final String roleCode; // 该节点的角色编码（用于运行时候选解析），可能为空
        // G2 票＋人双维与不达票兜底：与 BPMN 完成条件、WfEngineFacade 收口裁决同一套口径
        public final String voteMode;
        public final Integer voteValue;
        public final String voteBasis;
        public final String voteFallback;
        MiNode(String taskId, String multiType, Integer voteRatio, String roleCode,
               String voteMode, Integer voteValue, String voteBasis, String voteFallback) {
            this.taskId = taskId; this.multiType = multiType; this.voteRatio = voteRatio; this.roleCode = roleCode;
            this.voteMode = voteMode; this.voteValue = voteValue;
            this.voteBasis = voteBasis; this.voteFallback = voteFallback;
        }
    }

    /** 是否有分支条件（金额阈值 / 国外出差 / 招待费）触发前置排他网关 */
    private static boolean hasBranch(Node n) {
        return n.abroad || n.entertain || n.amountThreshold != null;
    }

    /** 分支条件表达式（多条件 OR 合并），供排他网关 conditionExpression */
    private static String branchExpr(Node n) {
        StringBuilder sb = new StringBuilder();
        if (n.abroad) sb.append("abroad == true");
        if (n.entertain) sb.append(sb.length() == 0 ? "" : " || ").append("expenseType == 'ENTERTAIN'");
        if (n.amountThreshold != null) sb.append(sb.length() == 0 ? "" : " || ")
                .append("amount > ").append(n.amountThreshold.longValue());
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 分支条件展示名（多条件 OR 合并） */
    private static String branchName(Node n) {
        StringBuilder sb = new StringBuilder();
        if (n.abroad) sb.append("国外出差");
        if (n.entertain) sb.append(sb.length() == 0 ? "" : "或").append("招待费");
        if (n.amountThreshold != null) sb.append(sb.length() == 0 ? "" : "或")
                .append("金额>").append(n.amountThreshold.longValue());
        return sb.toString();
    }

    public String build(String processName, String processKey, String nodeJson) {
        List<Node> nodes = parse(processKey, nodeJson);
        List<Node> kept = new ArrayList<>();
        for (Node n : nodes) {
            if (n.skip) continue;
            kept.add(n);
        }
        int N = kept.size();
        StringBuilder shapes = new StringBuilder();
        StringBuilder edges = new StringBuilder();
        StringBuilder tasks = new StringBuilder();
        StringBuilder flows = new StringBuilder();

        shapes.append("<bpmndi:BPMNShape id=\"StartEvent_1_di\" bpmnElement=\"StartEvent_1\"><dc:Bounds x=\"120\" y=\"102\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>\n");
        tasks.append("    <bpmn:startEvent id=\"StartEvent_1\" name=\"开始\" />\n");

        // 第一遍：生成任务/并签元素 + 坐标
        double[] xs = new double[N];          // 任务 x
        String[] taskIds = new String[N];
        boolean[] isPara = new boolean[N];    // 是否为并行会签节点
        List<String[]> paraSubs = new ArrayList<>();   // 并行会签节点的子任务 id 表
        double x = 210;
        for (int i = 0; i < N; i++) {
            Node n = kept.get(i);
            isPara[i] = n.parallel;
            String baseId = (n.key != null && !n.key.isBlank()) ? n.key : "Task_" + (i + 1);
            String taskId = baseId;
            taskIds[i] = taskId;
            // P1 会签/票签：按 node_key 从 wf_node_config 读取 multiType，标记本节点是否多实例
            attachMultiType(processKey, taskId, n);
            boolean hasGw = hasBranch(n);
            double taskX = x + (hasGw || isPara[i] ? 70 : 0);
            xs[i] = taskX;
            if (isPara[i]) {
                // —— 并行会签：parallelGateway 分叉 → K 个角色并行 userTask → parallelGateway 汇合
                String pgId = "Pg_" + (i + 1), jgId = "Jg_" + (i + 1);
                int k = n.roles.size();
                String[] subs = new String[k];
                shapes.append("<bpmndi:BPMNShape id=\"" + pgId + "_di\" bpmnElement=\"" + pgId + "\"><dc:Bounds x=\"" + (taskX - 70) + "\" y=\"102\" width=\"40\" height=\"40\" /></bpmndi:BPMNShape>\n");
                tasks.append("    <bpmn:parallelGateway id=\"" + pgId + "\" />\n");
                double ty = 20;
                for (int r = 0; r < k; r++) {
                    String subId = baseId + "_" + r;
                    subs[r] = subId;
                    String role = n.roles.get(r);
                    String subCode = ROLE_MAP.get(role);
                    tasks.append("    <bpmn:userTask id=\"" + subId + "\" name=\"" + esc(n.name + "【" + role + "】") + "\""
                            + (subCode != null ? " flowable:candidateGroups=\"" + subCode + "\""
                                            : " flowable:assignee=\"${approver}\"") + " />\n");
                    shapes.append("<bpmndi:BPMNShape id=\"" + subId + "_di\" bpmnElement=\"" + subId + "\"><dc:Bounds x=\"" + taskX + "\" y=\"" + ty + "\" width=\"130\" height=\"80\" /></bpmndi:BPMNShape>\n");
                    ty += 110;
                }
                paraSubs.add(subs);
                double jgX = taskX + 130 + 60;
                shapes.append("<bpmndi:BPMNShape id=\"" + jgId + "_di\" bpmnElement=\"" + jgId + "\"><dc:Bounds x=\"" + jgX + "\" y=\"102\" width=\"40\" height=\"40\" /></bpmndi:BPMNShape>\n");
                tasks.append("    <bpmn:parallelGateway id=\"" + jgId + "\" />\n");
                x = jgX + 40;
            } else {
                // 节点名自带审批人：如「财务审核【财务审核岗】」——bpmn-js 画布直接可见审批信息
                String displayName = n.approver != null && !n.approver.isBlank()
                        ? n.name + "【" + n.approver + "】" : n.name;
                if (n.multiType != null) {
                    // —— P1 会签/票签：userTask + multiInstanceLoopCharacteristics ——
                    // 集合=运行期注入的候选用户列表（<taskId>_assignees），每实例 one assignee=${assignee}。
                    // ALL=并行全批 / SEQ=顺序全批 / VOTE=比例通过（同时按通过/否决双条件提前收口）。
                    String coll = taskId + "_assignees";
                    boolean seq = "SEQ".equals(n.multiType);
                    StringBuilder mi = new StringBuilder();
                    mi.append("    <bpmn:userTask id=\"").append(taskId).append("\" name=\"").append(esc(displayName)).append("\"")
                            .append(" flowable:assignee=\"${assignee}\">\n");
                    mi.append("      <bpmn:multiInstanceLoopCharacteristics isSequential=\"").append(seq).append("\"")
                            .append(" flowable:collection=\"${").append(coll).append("}\" flowable:elementVariable=\"assignee\"");
                    if ("VOTE".equals(n.multiType)) {
                        // P1 票签计票：计数变量按节点 id 前缀隔离，避免同一流程内多个票签节点互相污染。
                        // 计数由 WfEngineFacade.complete() 在每次投票后累加，达标即触发 completionCondition 提前收口。
                        // G2：条件改由 voteCompletionCondition 生成（票＋人双维 + 不达票硬终止），
                        // 未配新列的存量行仍产出与改造前一致的表达式。
                        mi.append(">\n        <bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">")
                                .append(voteCompletionCondition(n, taskId))
                                .append("</bpmn:completionCondition>\n")
                                .append("      </bpmn:multiInstanceLoopCharacteristics>\n");
                    } else {
                        mi.append(">\n        <bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">")
                                .append("${nrOfCompletedInstances == nrOfInstances}</bpmn:completionCondition>\n")
                                .append("      </bpmn:multiInstanceLoopCharacteristics>\n");
                    }
                    mi.append("    </bpmn:userTask>\n");
                    tasks.append(mi);
                } else {
                    tasks.append("    <bpmn:userTask id=\"" + taskId + "\" name=\"" + esc(displayName) + "\""
                            + (n.roleCode != null ? " flowable:candidateGroups=\"" + n.roleCode + "\""
                            : " flowable:assignee=\"${approver}\"") + " />\n");
                    // G2 第 3 条：单办理人节点按配置挂非中断边界定时器（多实例节点不挂，见 appendTimeout）
                    appendTimeout(tasks, shapes, edges, flows, n, taskId, taskX);
                }
                shapes.append("<bpmndi:BPMNShape id=\"" + taskId + "_di\" bpmnElement=\"" + taskId + "\"><dc:Bounds x=\"" + taskX + "\" y=\"80\" width=\"130\" height=\"80\" /></bpmndi:BPMNShape>\n");
                x = taskX + 130 + 60;
            }
        }
        // 结束事件
        double endX = x;
        shapes.append("<bpmndi:BPMNShape id=\"EndEvent_1_di\" bpmnElement=\"EndEvent_1\"><dc:Bounds x=\"" + endX + "\" y=\"102\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>\n");
        tasks.append("    <bpmn:endEvent id=\"EndEvent_1\" name=\"结束\" />\n");

        // 第二遍：网关 + 连线
        String prev = "StartEvent_1";
        double prevEdgeX = 156;
        int paraSlot = 0;
        for (int i = 0; i < N; i++) {
            Node n = kept.get(i);
            String taskId = taskIds[i];
            boolean hasGw = (!isPara[i]) && hasBranch(n);
            String gwId = hasGw ? "Gw_" + (i + 1) : null;
            double taskX = xs[i];
            if (isPara[i]) {
                String[] subs = paraSubs.get(paraSlot++);
                String pgId = "Pg_" + (i + 1), jgId = "Jg_" + (i + 1);
                int k = subs.length;
                // prev → split
                flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_p\" sourceRef=\"" + prev + "\" targetRef=\"" + pgId + "\" />\n");
                edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_p_di\" bpmnElement=\"Flow_" + (i + 1) + "_p\"><di:waypoint x=\"" + prevEdgeX + "\" y=\"120\" /><di:waypoint x=\"" + (taskX - 50) + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");
                for (int r = 0; r < k; r++) {
                    double yy = (k == 1) ? 120 : (20 + r * 110 + 40);
                    // split → 会签任务
                    flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_p" + r + "\" sourceRef=\"" + pgId + "\" targetRef=\"" + subs[r] + "\" />\n");
                    edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_p" + r + "_di\" bpmnElement=\"Flow_" + (i + 1) + "_p" + r + "\"><di:waypoint x=\"" + (taskX - 50) + "\" y=\"120\" /><di:waypoint x=\"" + taskX + "\" y=\"" + yy + "\" /></bpmndi:BPMNEdge>\n");
                    // 会签任务 → join
                    flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_j" + r + "\" sourceRef=\"" + subs[r] + "\" targetRef=\"" + jgId + "\" />\n");
                    edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_j" + r + "_di\" bpmnElement=\"Flow_" + (i + 1) + "_j" + r + "\"><di:waypoint x=\"" + (taskX + 130) + "\" y=\"" + yy + "\" /><di:waypoint x=\"" + (taskX + 130 + 60 + 20) + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");
                }
                prev = jgId;
                prevEdgeX = taskX + 130 + 60 + 40;
            } else if (hasGw) {
                double gwX = taskX - 70;
                String bExpr = branchExpr(n);
                String bName = branchName(n);
                // 金额分档语义：金额不足（默认流）应跳过本节点及后续连续分档节点，
                // 直达下一个必办（无分支、非会签）节点继续走共享作业链；
                // 若无后续必办节点（分档为流程末尾，如 FA 单链）则直接结束，避免小金额走到更高级审批人。
                String defaultTarget = "EndEvent_1";
                for (int fw = i + 1; fw < N; fw++) {
                    if (!isPara[fw] && !hasBranch(kept.get(fw))) {
                        defaultTarget = entryRef(fw, taskIds, isPara);
                        break;
                    }
                }
                double defBranchX = (defaultTarget.startsWith("Task") || defaultTarget.startsWith("Pg")) ? xs[i + 1] + 65 : endX;
                // 网关元素
                tasks.insert(tasks.indexOf("    <bpmn:userTask id=\"" + taskId + "\""),
                        "    <bpmn:exclusiveGateway id=\"" + gwId + "\" default=\"Flow_" + (i + 1) + "_def\" />\n");
                shapes.insert(shapes.indexOf("<bpmndi:BPMNShape id=\"" + taskId + "_di\""),
                        "<bpmndi:BPMNShape id=\"" + gwId + "_di\" bpmnElement=\"" + gwId + "\"><dc:Bounds x=\"" + gwX + "\" y=\"102\" width=\"40\" height=\"40\" /></bpmndi:BPMNShape>\n");
                // prev → gw
                flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_in\" sourceRef=\"" + prev + "\" targetRef=\"" + gwId + "\" />\n");
                edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_in_di\" bpmnElement=\"Flow_" + (i + 1) + "_in\"><di:waypoint x=\"" + prevEdgeX + "\" y=\"120\" /><di:waypoint x=\"" + (gwX + 20) + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");
                // gw → task（满足分支条件：金额 / 国外出差 / 招待费）
                flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_cond\" name=\"" + esc(bName) + "\" sourceRef=\"" + gwId + "\" targetRef=\"" + taskId + "\"><bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">" + esc("${" + bExpr + "}") + "</bpmn:conditionExpression></bpmn:sequenceFlow>\n");
                edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_cond_di\" bpmnElement=\"Flow_" + (i + 1) + "_cond\"><di:waypoint x=\"" + (gwX + 40) + "\" y=\"120\" /><di:waypoint x=\"" + taskX + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");
                // gw 默认流：金额不足且非连续分档 → 达到末尾分档/末尾则直接结束，否则跳到下个必办节点
                flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "_def\" name=\"金额不足/其他\" sourceRef=\"" + gwId + "\" targetRef=\"" + defaultTarget + "\" />\n");
                edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_def_di\" bpmnElement=\"Flow_" + (i + 1) + "_def\"><di:waypoint x=\"" + (gwX + 20) + "\" y=\"142\" /><di:waypoint x=\"" + (gwX + 20) + "\" y=\"230\" /><di:waypoint x=\"" + defBranchX + "\" y=\"230\" /><di:waypoint x=\"" + defBranchX + "\" y=\"160\" /></bpmndi:BPMNEdge>\n");
                prev = taskId;
                prevEdgeX = taskX + 130;
            } else {
                flows.append("    <bpmn:sequenceFlow id=\"Flow_" + (i + 1) + "\" sourceRef=\"" + prev + "\" targetRef=\"" + taskId + "\" />\n");
                edges.append("<bpmndi:BPMNEdge id=\"Flow_" + (i + 1) + "_di\" bpmnElement=\"Flow_" + (i + 1) + "\"><di:waypoint x=\"" + prevEdgeX + "\" y=\"120\" /><di:waypoint x=\"" + (taskX + 65) + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");
                prev = taskId;
                prevEdgeX = taskX + 130;
            }
        }
        // 最后节点 → 结束
        flows.append("    <bpmn:sequenceFlow id=\"Flow_end\" sourceRef=\"" + prev + "\" targetRef=\"EndEvent_1\" />\n");
        edges.append("<bpmndi:BPMNEdge id=\"Flow_end_di\" bpmnElement=\"Flow_end\"><di:waypoint x=\"" + prevEdgeX + "\" y=\"120\" /><di:waypoint x=\"" + endX + "\" y=\"120\" /></bpmndi:BPMNEdge>\n");

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
                "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" " +
                "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" " +
                "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" " +
                "xmlns:flowable=\"http://flowable.org/bpmn\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "id=\"Definitions_" + esc(processKey) + "\" targetNamespace=\"http://fssc\">\n" +
                "  <bpmn:process id=\"" + esc(processKey) + "\" name=\"" + esc(processName) + "\" isExecutable=\"true\">\n" +
                tasks.toString() +
                flows.toString() +
                "  </bpmn:process>\n" +
                "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n" +
                "    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"" + esc(processKey) + "\">\n" +
                shapes.toString() +
                edges.toString() +
                "    </bpmndi:BPMNPlane>\n" +
                "  </bpmndi:BPMNDiagram>\n" +
                "</bpmn:definitions>";
    }

    /** 返回流程中第 i 个节点的可连接入口 id（并行会签节点 → 分叉网关 Pg_，其余 → 主任务） */
    private String entryRef(int i, String[] taskIds, boolean[] isPara) {
        return isPara[i] ? "Pg_" + (i + 1) : taskIds[i];
    }

    /**
     * 按 node_key 从 wf_node_config 读取本节点配置，标记：多签类型（ALL/SEQ/VOTE）、票签口径
     * （vote_mode/vote_value/vote_basis/vote_fallback，缺省回退遗留 vote_ratio）、超时（timeout_hours）。
     * 无配置或查询失败 → 保持单办理人、不生成边界定时器（<b>零行为变更</b>）。
     */
    private void attachMultiType(String processKey, String taskId, Node n) {
        if (processKey == null || taskId == null || wfNodeConfigMapper == null) return;
        try {
            WfNodeConfig cfg = wfNodeConfigMapper.selectOne(
                    new LambdaQueryWrapper<WfNodeConfig>()
                            .eq(WfNodeConfig::getModelKey, processKey)
                            .eq(WfNodeConfig::getNodeKey, taskId).last("LIMIT 1"));
            if (cfg == null) {
                return;
            }
            if (cfg.getMultiType() != null && !cfg.getMultiType().isBlank()
                    && !"ANY".equalsIgnoreCase(cfg.getMultiType())) {
                n.multiType = cfg.getMultiType().toUpperCase();
                if ("VOTE".equals(n.multiType)) {
                    // 遗留列仅作回退：新列（vote_mode/vote_value/vote_basis/vote_fallback）存在时优先，
                    // 保证存量"只配了 vote_ratio"的行生成与改造前逐字一致的完成条件。
                    n.voteMode = blankToNull(cfg.getVoteMode());
                    n.voteValue = cfg.getVoteValue();
                    n.voteBasis = blankToNull(cfg.getVoteBasis());
                    n.voteFallback = blankToNull(cfg.getVoteFallback());
                    if (cfg.getVoteRatio() != null) {
                        int r = cfg.getVoteRatio();
                        n.voteRatio = r < 1 ? 1 : (r > 100 ? 100 : r);
                    }
                }
            }
            // 超时：仅记录配置；是否真的生成边界定时器由调用方按"非多实例"判定（见 build()）
            if (cfg.getTimeoutHours() != null && cfg.getTimeoutHours() > 0) {
                n.timeoutHours = cfg.getTimeoutHours();
                n.timeoutAction = cfg.getTimeoutAction();
            }
        } catch (Exception ex) {
            // 配置缺失/异常 → 单办理人，不生成多实例、不生成边界定时器
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * G2 票签完成条件：把「票＋人双维」编译成 multiInstance 的 {@code completionCondition}。
     *
     * <p><b>为什么必须带硬终止项</b>：改造前不达票<b>没有任何语义</b>——条件永不满足时多实例既可能卡死、
     * 也可能被引擎默认收敛，两种结果都不可解释。现固定为
     * {@code <阈值条件> || nrOfCompletedInstances == nrOfInstances}：阈值只是<b>提前收口</b>，
     * 全部实例办完一定终止，随后由 {@code WfEngineFacade} 按 {@code vote_fallback} 落默认审批项。
     *
     * <p>三种口径（与运行期裁决同一套公式，避免"XML 与 Java 各算一遍"漂移）：
     * <ul>
     *   <li>{@code COUNT}：{@code 赞成票 >= 所需票数}；</li>
     *   <li>{@code RATIO + BY_PERSON}：{@code 赞成*100 >= 百分比*nrOfInstances}（分母＝应到人数）；</li>
     *   <li>{@code RATIO + BY_VOTE}：{@code 赞成*100 >= 百分比*(赞成+反对)}（分母＝实到票数）。</li>
     * </ul>
     * 遗留行（未配新列）：保留原「否决提前收口」项 {@code 反对*100 > (100-百分比)*nrOfInstances}。
     */
    private String voteCompletionCondition(Node n, String taskId) {
        String ack = taskId + "_approvedCount";
        String rjk = taskId + "_rejectedCount";
        String mode = n.voteMode == null ? "RATIO" : n.voteMode;
        String basis = n.voteBasis == null ? "BY_PERSON" : n.voteBasis;
        int legacy = n.voteRatio == null ? 50 : n.voteRatio;
        int value = n.voteValue == null ? legacy : n.voteValue;
        String primary;
        if ("COUNT".equals(mode)) {
            int need = Math.max(1, value);
            primary = ack + " &gt;= " + need;
        } else {
            int ratio = value < 1 ? 1 : Math.min(value, 100);
            if ("BY_VOTE".equals(basis)) {
                primary = ack + " * 100 &gt;= " + ratio + " * (" + ack + " + " + rjk + ")";
            } else {
                primary = ack + " * 100 &gt;= " + ratio + " * nrOfInstances";
                if (n.voteMode == null) {
                    primary = primary + " || " + rjk + " * 100 &gt; (100 - " + ratio + ") * nrOfInstances";
                }
            }
        }
        return "${" + primary + " || nrOfCompletedInstances == nrOfInstances}";
    }

    /**
     * G2 第 3 条：为 {@code timeout_hours > 0} 的<b>非多实例</b> userTask 生成非中断边界定时器，
     * 把 {@code timeout_action} 接到 {@code WfTimeoutBoundaryDelegate} 上（复用 WfTimeoutJob 的三档语义）。
     *
     * <p><b>为什么时长是字面量 {@code PT{N}H} 而不是表达式</b>：本方法产出会写进 {@code wf_model.bpmn_xml}
     * 并被 {@code WfModelService.initSync()} 用"内容是否相同"判断要不要升版本——若时长依赖
     * {@code now()}（工作日历推算），每次启动内容都不同 ⇒ 版本号会被无意义地反复 +1。
     * 故这里只放确定的自然小时数；<b>真正的到期真值是 {@code wf_task.due_time}</b>，
     * 由 {@code WorkCalendarService} 算出、由 Delegate 与兜底作业在触发时优先读取
     * （定时器在此仅充当"唤醒"，未到 due_time 时 Delegate 不动作，由安全网补齐）。
     *
     * <p>多实例（会签/票签）节点不挂边界定时器：票签的收口由完成条件与
     * {@code WfEngineFacade.resolveVoteOutcome} 负责，混入定时器只会让两条收口路径互相干扰。
     */
    private void appendTimeout(StringBuilder tasks, StringBuilder shapes, StringBuilder edges,
                               StringBuilder flows, Node n, String taskId, double taskX) {
        int hours = n.timeoutHours == null ? 0 : n.timeoutHours;
        if (hours <= 0) {
            return;
        }
        String bId = "Timeout_" + taskId;
        String actId = "TimeoutAct_" + taskId;
        String endId = "TimeoutEnd_" + taskId;
        // cancelActivity="false"：催办/转办/自动通过都不需要打断原任务（打断会把待办直接抹掉）
        tasks.append("    <bpmn:boundaryEvent id=\"" + bId + "\" name=\"超时\" attachedToRef=\"" + taskId
                + "\" cancelActivity=\"false\">\n"
                + "      <bpmn:timerEventDefinition>\n"
                + "        <bpmn:timeDuration xsi:type=\"bpmn:tFormalExpression\">PT" + hours + "H</bpmn:timeDuration>\n"
                + "      </bpmn:timerEventDefinition>\n"
                + "    </bpmn:boundaryEvent>\n");
        // 服务任务 id 内嵌 taskId：Delegate 从 currentActivityId 反解节点键，避免额外 extensionElements
        tasks.append("    <bpmn:serviceTask id=\"" + actId + "\" name=\"超时处置("
                + esc(n.timeoutAction == null ? "NOTIFY" : n.timeoutAction) + ")\""
                + " flowable:delegateExpression=\"${wfTimeoutBoundaryDelegate}\" />\n");
        tasks.append("    <bpmn:endEvent id=\"" + endId + "\" name=\"超时处置完成\" />\n");
        flows.append("    <bpmn:sequenceFlow id=\"TimeoutFlow_" + taskId + "\" sourceRef=\"" + bId
                + "\" targetRef=\"" + actId + "\" />\n");
        flows.append("    <bpmn:sequenceFlow id=\"TimeoutFlowEnd_" + taskId + "\" sourceRef=\"" + actId
                + "\" targetRef=\"" + endId + "\" />\n");
        // DI：边界事件贴任务下边框（36x36 圆心落在边框中点），处置链单独一排，避免与主链重叠
        double bx = taskX + 112;
        shapes.append("<bpmndi:BPMNShape id=\"" + bId + "_di\" bpmnElement=\"" + bId
                + "\"><dc:Bounds x=\"" + bx + "\" y=\"142\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>\n");
        shapes.append("<bpmndi:BPMNShape id=\"" + actId + "_di\" bpmnElement=\"" + actId
                + "\"><dc:Bounds x=\"" + (taskX + 130) + "\" y=\"230\" width=\"130\" height=\"80\" /></bpmndi:BPMNShape>\n");
        shapes.append("<bpmndi:BPMNShape id=\"" + endId + "_di\" bpmnElement=\"" + endId
                + "\"><dc:Bounds x=\"" + (taskX + 330) + "\" y=\"252\" width=\"36\" height=\"36\" /></bpmndi:BPMNShape>\n");
        edges.append("<bpmndi:BPMNEdge id=\"TimeoutFlow_" + taskId + "_di\" bpmnElement=\"TimeoutFlow_" + taskId
                + "\"><di:waypoint x=\"" + (bx + 18) + "\" y=\"178\" /><di:waypoint x=\"" + (taskX + 130) + "\" y=\"270\" /></bpmndi:BPMNEdge>\n");
        edges.append("<bpmndi:BPMNEdge id=\"TimeoutFlowEnd_" + taskId + "_di\" bpmnElement=\"TimeoutFlowEnd_" + taskId
                + "\"><di:waypoint x=\"" + (taskX + 260) + "\" y=\"270\" /><di:waypoint x=\"" + (taskX + 330) + "\" y=\"270\" /></bpmndi:BPMNEdge>\n");
    }

    /** 返回流程中所有多实例节点规格（taskId/multiType/voteRatio/roleCode），供 WfEngineFacade 启动期注入候选集合与计票变量。 */
    public List<MiNode> resolveMiNodes(String processKey, String nodeJson) {
        List<Node> nodes = parse(processKey, nodeJson);
        List<Node> kept = new ArrayList<>();
        for (Node n : nodes) { if (!n.skip) kept.add(n); }
        List<MiNode> mi = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Node n = kept.get(i);
            String baseId = (n.key != null && !n.key.isBlank()) ? n.key : "Task_" + (i + 1);
            attachMultiType(processKey, baseId, n);
            if (n.multiType != null) {
                mi.add(new MiNode(baseId, n.multiType, n.voteRatio, n.roleCode,
                        n.voteMode, n.voteValue, n.voteBasis, n.voteFallback));
            }
        }
        return mi;
    }

    private List<Node> parse(String processKey, String nodeJson) {
        List<Node> nodes = new ArrayList<>();
        try {
            Object arr = new com.fasterxml.jackson.databind.ObjectMapper().readValue(nodeJson, Object.class);
            if (arr instanceof List<?> list) {
                for (Object o : list) {
                    Node n = new Node();
                    if (o instanceof String str) {
                        n.name = str;
                    } else if (o instanceof Map<?, ?> m) {
                        n.name = str(m.get("name"));
                        n.approver = str(m.get("approver"));
                        n.condition = str(m.get("condition"));
                        String k = str(m.get("key"));
                        n.key = k.isBlank() ? null : k;
                    }
                    if (n.name == null || n.name.isBlank()) continue;
                    if ("系统自动".equals(n.approver) || "共享中心".equals(n.approver)) {
                        n.skip = true; // 系统检查/共享中心作业：由业务代码或任务池驱动，流程引擎不生成任务
                        nodes.add(n);
                        continue;
                    }
                    n.roleCode = n.approver == null ? null : ROLE_MAP.get(n.approver);
                    n.isAssignee = n.roleCode == null;
                    // V1.1.10 并行会签：approver 用"/"分隔多个会签角色 → parallelGateway 并行 fan-out/join。
                    // 缺陷修复（V1.3.17）：仅当整串未命中 ROLE_MAP 时才按"/"分割——否则"项目经理/费用承担部门负责人"
                    // 这类含斜杠的单一角色名会被误判为并行会签，导致 roleCode 丢失、退化动态指派 assignee=${approver}。
                    if (n.roleCode == null && n.approver != null && n.approver.contains("/")) {
                        n.parallel = true;
                        n.roles = new ArrayList<>();
                        for (String r : n.approver.split("/")) {
                            if (!r.isBlank()) n.roles.add(r.trim());
                        }
                    }
                    if (n.condition != null) {
                        Matcher m2 = AMOUNT_PATTERN.matcher(n.condition);
                        if (m2.find()) {
                            n.amountThreshold = Double.parseDouble(m2.group(1));
                        }
                        n.abroad = n.condition.contains("国外出差");
                        n.entertain = n.condition.contains("招待费");
                    }
                    nodes.add(n);
                }
            }
        } catch (Exception e) {
            // 兼容旧字符串数组
            String json = nodeJson == null ? "" : nodeJson.trim();
            if (json.startsWith("[")) json = json.substring(1, json.length() - 1);
            for (String seg : json.split(",")) {
                Node n = new Node();
                n.name = seg.trim().replaceAll("^\"|\"$", "");
                nodes.add(n);
            }
        }
        return nodes;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String esc(String v) {
        return v == null ? "" : v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
