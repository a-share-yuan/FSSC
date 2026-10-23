# 流程引擎（workflow-engine）

## 1. 技术底座

- **引擎**：Flowable 8.0.0（`act_*`/`flw_*` 43 张表由引擎管理，首启自动建表校准）；
- **设计器**：bpmn-js 16.5 在线画流程，模型版本化存于 `bpmn-repo/models/<流程>/`；
- **部署**：设计器里**"发布"才生效**（保存 ≠ 部署），部署后生成
  `act_re_deployment`/`act_re_procdef`，演示库内置 315 个已部署定义，开箱可跑。

## 2. 流程定义与节点配置的解耦

```
BPMN 模型（画形状、连流向）          节点配置 wf_node_config（谁在哪审批）
   userTask id: Task_a1     ←——精确键匹配——►    node_key: Task_a1
```

- **Resolver 是精确键匹配**：`node_key` 必须逐字命中已部署 BPMN 的 userTask id；
- 按"设计位置编号"（Task_1..N）猜着配 → 孤儿行，**永不生效**；
- 实测约四成历史配置行是孤儿行，**引用任何 node_config 统计前先扣掉**；
- 部署新流程后必跑核对脚本（对照 `act_ge_bytearray` 里的真实 XML）。

## 3. 内置流程（8 个模型）

| 模型 | 用途 |
|---|---|
| `CLAIM` | 费用报销主流程 |
| `LOAN` | 借款还款 |
| `SETTLE` | 对公结算 |
| `INVOICE_ISSUE` | 开票 |
| `RECEIVABLE` | 应收确认 |
| `GL_MANUAL_VOUCHER` | 手工凭证 |
| `GL_MERGE` | 凭证合并 |
| `P4_G4` | 自由驳回演示流程（任意级次回退） |

## 4. 高级流程特性（P1~P4，全部运行期实证）

| 特性 | 说明 |
|---|---|
| 条件分支 | 网关条件驱动不同审批链（金额/类型/公司维度） |
| 会签 | 多人审批：ALL（全过）/ SEQ（顺序）/ VOTE（票签） |
| 超时升级 | 超时未办自动催办，按阶梯升级（提醒→升级→自动通过白名单） |
| 转办/委托 | 审批人可转办；委托有过期时间与公司边界闸 |
| 加签 | 前方节点临时加签，审完回跳原节点 |
| 撤回 | 发起人在满足条件时撤回 |
| 传阅 | `WfCcService` 是 wf_cc 唯一写入点 |
| 自由驳回 | 可驳回到任意历史级次；实现是 `deleteProcessInstance` 作废重提，可用 `GET /wf/reject-targets` 预览可驳节点 |
| 同人连办拦截 | 禁止同一人连续办理多节点，可配流程链 |
| 空节点策略 | `empty_strategy='ADMIN'` 是 admin 进禁入节点的唯一通道 |

## 5. 审批人解析顺序

```
节点配置(角色/维度/白名单) → 委托(有效期内) → 加签(临时) → 兜底策略
```

- 维度取人（`wf_assignee_dim`/`wf_assignee_scheme`）：按公司/部门/项目维度找人；
- 灰度白名单：`approve.pick_mode.models` 等 sys_config 控制哪些流程按新逻辑取人；
- **配置了审批人却不生效**的三大根因：node_key 孤儿、维度值不存在、
  白名单没放行该流程。

## 6. 实例状态与业务单据对齐

- `wf_instance` 是业务视角的流程实例，与引擎实例一一对应；
- 审批动作的落点：`complete()` / `rejectToNodeKey()` / `deleteProcessInstance()`；
- **僵尸实例收敛**：单据已终态而实例仍 RUNNING 的，定时任务双向收敛，
  fail-open 不误杀；
- 时间线展示以 `chain.summary.reached` 为唯一真相源。

## 7. 二次开发指南

| 想做什么 | 动哪里 |
|---|---|
| 加一个审批节点 | 设计器画 → 发布 → wf_node_config 配 node_key |
| 改审批人规则 | wf_node_config / wf_assignee_scheme |
| 加一种流程动作 | workflow 域 + 事件动作配置 `wf_event_action_config` |
| 改单据状态机 | 各业务域 service（状态常量在域字典） |
| 接外部流程 | connector 域 SPI |

## 8. 常见坑

1. **保存≠部署**：设计器保存后不发布，库里纹丝不动；
2. **node_key 猜编号**：Task_1..N 是设计位置，不是引擎 id；
3. **空跑没人的节点**：没配兜底策略又没人，单子卡死无提示；
4. **改流程不看版本**：bpmn-repo 里 `latest.bpmn20.xml` 才是生效版，历史版仅回溯用。
