# 模块地图（module-map）

后端按域分包：`fssc-server/fssc-start/src/main/java/com/fssc/<domain>/`。
本文逐一说明各域职责、核心表与入口，帮助快速定位"改某功能该动哪里"。

## 平台基础域

| 域 | 职责 | 核心表 | 入口 |
|---|---|---|---|
| `platform` | 租户/套餐/登录/操作日志 | `pf_tenant`/`pf_package`/`pf_feature`/`pf_oper_log` | `/api/tenant/auth/**` |
| `system` | 用户/角色/菜单/字典/号段/参数 | `sys_user`/`sys_role`/`sys_menu`/`sys_dict_*`/`sys_number_*` | `/api/tenant/system/**` |
| `auth` | 认证鉴权、JWT、令牌 | `pf_login_log`/`sys_file_token` | `/api/tenant/auth/**` |
| `audit` | 审计留痕、对账稽核 | `audit_trail`/`audit_reconcile` | 各域内嵌 + `/api/tenant/audit/**` |
| `masterdata` | 公司/科目/客商/城市等主数据 | `sys_company`/`md_subject`/`md_city` | `/api/tenant/masterdata/**` |

## 流程引擎域

| 域 | 职责 | 核心表 |
|---|---|---|
| `workflow` | 流程实例/任务/节点配置/委托/加签/传阅 | `wf_instance`/`wf_task`/`wf_node_config`/`wf_process_config` |
| `ai` | AI 动作编目、工具调用、巡检规则 | `ai_action_confirm`/`ai_tool_call_log` |
| `connector` | 外部系统连接器 | `integration_call_log`/`ext_interface_config` |

引擎侧表由 Flowable 管理（`act_*`/`flw_*`，共 43 张），部署/实例/任务/历史都在那里。

## 业务域（按报销、应收、应付、总账、资金、影像、运营分组）

### 费用报销组
| 域 | 职责 | 核心表 |
|---|---|---|
| `expense` | 报销单/申请/差旅/借款/分摊/冷归档 | `exp_bill`(+`_line`/`_payment`/`_travel`/`_share`/`_cold`) |
| `compliance` | 合规预检、费用政策、服务目录 | `precheck_result`/`expense_policy*`/`service_catalog` |
| `budget` | 预算方案/占用/调整 | `bg_scheme`/`bg_item`/`bg_occupy`/`bg_adjust` |

### 应收组
| 域 | 职责 | 核心表 |
|---|---|---|
| `receivable` | 对账/开票/应收/收款核销/坏账 | `rc_receivable`/`rc_receipt`/`rc_bad_debt` |
| `arflow` | 应收流程/邮件通知/月结 | `ar_statement`/`ar_mail_*`/`ar_month_close` |
| `customer` | 客户主数据/交易对手 | `crm_customer` |

### 应付组
| 域 | 职责 | 核心表 |
|---|---|---|
| `apflow` | 服务申请/派单规则/月结 | `ap_service_request`/`ap_dispatch_rule` |
| `purchase` | 采购订单/收货/退货 | `po_order`/`po_receipt`/`pur_return` |
| `match` | 三单匹配/容差 | `match_tolerance` |
| `invoice` | 发票（进/销/查验） | `inv_invoice`(+`_line`/`_global`)/`inv_verify_log` |

### 总账组
| 域 | 职责 | 核心表 |
|---|---|---|
| `gl` | 账簿/凭证/科目/月结/合并/税务 | `gl_book_*`/`gl_journal_entry`/`gl_month_close`/`tax_*` |
| `glex` | 总账扩展（薪酬/工资/结转） | `gl_salary_run`/`gl_consolidate` |

### 资金组
| 域 | 职责 | 核心表 |
|---|---|---|
| `treasury` | 资金计划/票据/调拨 | `tr_fund_plan`/`tr_transfer`/`tr_credit_bill` |
| `payplan` | 付款计划/执行 | `pay_plan`/`op_plan_paid_log` |
| `bank` | 银行主数据/余额/对账 | `md_bank`/`md_bank_account`/`bank_balance`/`bank_reconcile` |

### 影像与文档组
| 域 | 职责 | 核心表 |
|---|---|---|
| `image` | 影像库桥接（独立库 `fssc_image`） | `img_batch`/`img_file`/`ocr_record` 等 6 张 |
| `document` | 档案/装订/销毁/借阅；档案集中/非集中模式见 [档案管理模式](业务流程-档案管理模式.md) | `archive_*` |

### 共享运营组
| 域 | 职责 | 核心表 |
|---|---|---|
| `sharing` | 作业控制台/派工/排班/SLA/质量 | `op_*` 系列 28 张 |
| `returns` | 退货管理 | `sale_return`/`pur_return` |
| `collab` | 产业链协同（BCP 对接） | `collab_scp_*`/`collab_ccp_*` |

### 资产存货组
| 域 | 职责 | 核心表 |
|---|---|---|
| `asset` | 资产/折旧/变更 | `md_asset`/`md_asset_depr_detail` |
| `contract` | 合同台账 | `cont_contract`/`cont_pay_*` |

### 报表与集成
| 域 | 职责 | 核心表 |
|---|---|---|
| `report` | 报表定义/收藏/汇总 | `rpt_report_def`/`rpt_summary` |
| `openapi` | 开放 API | `pf_order` 等 |
| `bcp` | BCP 门户适配 | `linku_sync_log` |
| `tracing` | 链路追踪/单据联查 | `exp_bill_trace` 等 |

## 主数据域（`md_*` 53 张表）

`md_` 前缀是主数据字典域：科目、客商、银行、城市、费用项目、预算、打印模板、
导入导出模板、表单字段元数据、单据类型等。**改配置先来 `md_*` 找**——
本系统的设计原则是"运营可配的不写死"。

## 快速定位口诀

- 找表：`md_`=主数据、`exp_`=报销、`rc_`/`ar_`=应收、`po_`/`ap_`=应付、
  `gl_`=总账、`tr_`/`bank_`=资金、`op_`=共享运营、`img_`=影像、`wf_`=流程配置、
  `sys_`/`pf_`=平台、`act_`=引擎。
- 找入口：`<域>/controller/XxxController.java`，统一前缀 `/api/tenant/<域>/`。
- 找取数：`<域>/mapper/XxxMapper.java`（纯注解无 XML）。
