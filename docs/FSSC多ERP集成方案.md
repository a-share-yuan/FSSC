# FSSC 多 ERP 集成方案（AR/AP 协同 · 对接 SAP / 用友 / 金蝶 / EBS / 自研 ERP）

> 版本 V1.4 · 2026-09-17 · 配套代码：`ErpChannelResolver`（已落地）+ `ext_interface_config`（分类扩展）+ 前端「外部接口配置」ERP Tab
> 背景：原实现把「EBS」硬编码在业务代码（通道分类 `EBS_AP`/`EBS_ORDER_GET`）、页面文案、菜单名共 70+ 处。本期完成 ①用户可见文案全部中文化为「ERP」②通道按品牌可配置化。

---


| 维度 | SAP PI/PO（现 BTP Integration Suite） | 金蝶 K3 Cloud / EAS | 用友 NC/NCC · YonBIP |
|---|---|---|---|
| 集成形态 | **中间件总线**：业务系统把消息投到集成引擎（Integration Engine），对端是 EBS/SAP/文件完全由**通信通道（Communication Channel）**决定，业务零感知 | **统一 WebAPI**：单一登录端点（acctID+用户+密码/License），Save / Submit / Audit / UnAudit / ExecuteBillQuery / View 六大统一操作集，按 FormID 区分单据类型 | **统一 OpenAPI 网关**：app_key + app_secret 签名（HMAC-SHA256）换 token，RESTful 业务接口按 YonBIP 租户路由 |
| 协议适配 | Adapter Engine：IDoc/BAPI/RFC/OData/REST/SOAP/JDBC 各类适配器即插即用 | HTTP + JSON，全部走 k3cloudapi 网关 | HTTP + REST，网关统一鉴权 |
| 关键设计点 | ①通道与业务解耦 ②消息路由规则（按字段值路由到不同目标）③SLA 监控与重放 | ①操作集收敛（6 个动词打天下）②单据模型即接口模型 ③第三方面向 FormID 编程 | ①租户级 app_key ②签名防篡改 ③幂等（外部分单号） |
| 错误处理 | 消息监控（Message Monitoring）+ 重放（Restart）+ 死信告警 | 同步返回错误码，失败即回滚业务状态 | 网关限流 + 业务错误码 + 回查 |
| 可借鉴到 FSSC | **通道可配置化、业务不感知品牌**（本方案核心） | **业务动作收敛为少量动词**（发运推送/发票回写/记账/订单获取/负数推送） | **幂等键设计**（EBS 调整单号/贷项凭证号已是幂等键，延续）+ **调用留痕**（collab_sync_log / integration_call_log 已具备） |

**结论**：FSSC 已有的 `IntegrationGateway` + `ext_interface_config` 骨架与上述模式同构，缺的只是「品牌路由」一层——本期补上 `ErpChannelResolver`。

## 2. 目标架构

```
业务服务（ScpFlowService / ApRequestService / CcpFlowService / ArEbsAdjustService）
        │  只声明业务动作，不感知 ERP 品牌
        │  erpChannelResolver.resolve("AP")          → "SAP_AP"
        │  erpChannelResolver.resolve("ORDER_GET")   → "YONYOU_ORDER_GET"
        ▼
ErpChannelResolver（品牌路由层，新增）
        │  读 ext_interface_config(category=ERP, 启用+默认) 的 provider
        │  provider ∈ {EBS, SAP, YONYOU, KINGDEE, CUSTOM}，未配置回落 EBS（蓝图兼容）
        ▼
IntegrationGateway.post/get(category, body)   （既有网关：鉴权/超时/重试/调用日志）
        ▼
ext_interface_config：每品牌每动作一条接口实例（apiUrl/apiKey/apiSecret/extraConfig）
        ▼
外部 ERP（EBS / SAP / 用友 / 金蝶 / 自研）   未配置通道 → MOCK 落日志（离线可跑）
```

### 2.1 三层配置模型（与 SAP PI 通道模型对齐）

| 层 | 载体 | 内容 | 示例 |
|---|---|---|---|
| ① ERP 实例声明 | `ext_interface_config` category=`ERP`，**每租户一条启用默认** | provider（品牌）+ 基础地址/凭证/网关参数 | provider=SAP, apiUrl=`https://erp.example.com` |
| ② 业务通道 | `ext_interface_config` category=`{PROVIDER}_{ACTION}`，每动作一条 | 该品牌该动作的端点/报文映射/超时重试（extraConfig） | category=`SAP_AP`（三单匹配记账） |
| ③ 调用留痕 | `integration_call_log` + `collab_sync_log` | 每次调用请求/响应/耗时/方向 | direction=`SCP_TO_EBS`（枚举值保留，见 §4） |


| 动作码 | 业务含义 | 蓝图接口 | 现 EBS 分类 | 未来示例 |
|---|---|---|---|---|
| `AP` | 三单匹配记账推送 | 接口9（打通点3） | `EBS_AP` | `SAP_AP` |
| `AP_BLUE` | 记账蓝字专用变体 | 接口变体 | `EBS_AP_BLUE` | `KINGDEE_AP_BLUE` |
| `ORDER_GET` | PO 订单信息校验（R-09） | 接口12 | `EBS_ORDER_GET` | `YONYOU_ORDER_GET` |
| （预留）`GL_PUSH` | 总账凭证推送 | AR 月结过总账 | `GL_PUSH`（品牌无关） | — |
| （预留）`AR_SHIPPING` | 发运数据抓取 | 接口1 | DB 视图定时抓取，暂无 HTTP 通道 | `{P}_AR_SHIPPING` |

> 原则：**动词收敛**。新增对接能力时先定义动作码 + 通道分类规范，业务代码只传动作码，绝不写死品牌。

## 3. 配置示例（运营侧操作手册）

场景：某租户从 EBS 切换到 SAP，同时在过渡期并行用用友做总账备份：

1. **外部接口配置 → ERP 总账对接** 新增：名称「SAP 主 ERP」，编码 `ERP_MAIN`，服务商 `SAP`，apiUrl=网关地址，apiKey/apiSecret=凭证，启用+设默认 → 此后 `resolve("AP")="SAP_AP"`。
2. 再新增一条：分类选 `SAP`（或直接新增 SAP_AP 通道），配置记账端点。
3. 切换即生效（网关每次调用实时读配置，无缓存重启）。
4. **回退**：把 EBS 实例设回默认即可（历史 EBS_* 配置全部保留，双向可切）。

> 并行多 ERP（分流）：V1.4 按租户路由一个默认品牌；若需「A 类单据走 SAP、B 类走用友」，在 `ErpChannelResolver` 加业务维度路由规则（extraConfig 里配 routing 规则即可，接口已预留 `activeInstance()` 读取 extraConfig 的能力），本期不实现，避免过度设计。

## 4. 兼容性红线（本期不改的部分，及原因）

| 项 | 保留原因 |
|---|---|
| direction 枚举 `SCP_TO_EBS / EBS_TO_SHARED / SHARED_TO_EBS` | collab_sync_log 历史数据 + DDL 注释 + 多处代码引用；语义是「数据流向」而非品牌，将来若品牌多义可加 `erp_provider` 列而不是改枚举 |
| 表名 `ar_ebs_adjust` / 列名（so_invoice_no 等） | DDL 变更成本 > 收益，注释已说明语义 |
| Java 注释中的「蓝图 EBS 接口N」 | 蓝图原文如此，是设计依据不是实现细节 |
| `VALID_CATEGORIES` 中旧分类 `EBS` | 兼容已存在的配置行 |

## 5. 本期已落地清单

| 层 | 改动 |
|---|---|
| 后端新增 | `system/gateway/ErpChannelResolver.java`：activeProvider / hasErpInstance / resolve(action) / activeInstance，未配置回落 EBS |
| 后端改造 | `ScpFlowService`：记账通道由 `EBS_AP` 硬编码 → `resolve("AP")`（蓝字变体 `resolve("AP_BLUE")` 优先）；`ApRequestService`：R-09 由 `EBS_ORDER_GET` → `resolve("ORDER_GET")`，日志文案动态带通道码；`ExtInterfaceConfigService`：VALID_CATEGORIES 扩展 ERP/各品牌 AP/AP_BLUE/ORDER_GET |
| 前端改造 | 「外部接口配置」新增 **ERP 总账对接** Tab；服务商下拉在 ERP 分类下切换为 EBS/SAP/YONYOU/KINGDEE/CUSTOM，附路由说明 |
| 文案清洗 | 用户可见 29 处 EBS → ERP/总账（前端 21 + 后端报错/落单 8 + 菜单 3491/3508），备份 `_backup_menuebs_20260917.json` |

## 6. 待办（后续批次）

- [ ] ERP 品牌 provider=非 EBS 时，报文映射层（payload 组装）尚未分品牌——当前各品牌 payload 结构一致（统一 JSON），真接 SAP BAPI/金蝶 FormID 时在 DefaultIntegrationGateway 增加 per-brand Adapter（对齐 SAP PI Adapter Engine），或在 extraConfig 里配字段映射模板。
- [ ] collab_sync_log 增加 `erp_provider` 冗余列，便于多品牌并行期对账。
- [ ] 菜单管理/节点配置页图标名校验（另案）。
