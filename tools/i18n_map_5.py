# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
# -*- coding: utf-8 -*-
"""中英对照表 · 第 5 批（模板剩余界面文案 182 条）"""

T = {
# ── 高频按钮（多数应复用 common，此处保留独立条目供语义键生成）──
"导出": "Export", "刷新": "Refresh", "查询": "Query", "重置": "Reset",
"重试": "Retry", "新增": "Add", "应用": "Apply", "上一步": "Previous",
"知道了": "Got it", "获取": "Get", "登 录": "Sign in", "保存": "Save",
"生成": "Generate", "清除": "Clear", "刷新列表": "Refresh list",
"跳过": "Skip", "跳过引导": "Skip the guide", "取消等待": "Cancel waiting",
"关闭": "Close", "核减": "Reduce", "通过": "Pass", "重置页面": "Reset the page",
"应用条件": "Apply condition", "连线条件": "Connection condition",
"开始/结束": "Start / End", "展开": "Expand", "收起": "Collapse",
"导入": "Import", "发起": "Start", "办结": "Close",
# ── 业务标题与说明 ──
"反欺诈风控": "Anti-fraud risk control", "新建退货单": "New return order",
"AI 助手": "AI assistant", "对话": "Chat", "付款记录": "Payment records",
"从左侧拖拽节点：开始 / 用户任务(审批) / 网关 / 结束，连线设置流转；点击节点配置审批人、会签、超时":
    "Drag nodes from the left: start / user task (approval) / gateway / end; connect them to set the flow. Click a node to configure its approvers, countersign and timeout.",
"拖拽排序 · 点击星标添加收藏": "Drag to reorder · click the star to add a favorite",
"正在加载审批轨迹…": "Loading the approval trail…",
"当前账号无审批历史查看权限，暂不能展示轨迹。": "The current account has no permission to view approval history, so the trail cannot be shown.",
"该待办未关联到业务单据，无法加载审批轨迹。": "This to-do is not linked to a business document, so the approval trail cannot be loaded.",
"归档整理 / 档案库 / 借阅利用 / 鉴定销毁": "Archiving / archive library / retrieval / appraisal and destruction",
"资产与存货管理": "Asset and inventory management",
"资产卡片 / 资产类别 / 存货收发存 / 税会差异 —— 折旧、报废、出入库全流程可视化":
    "Asset cards / asset categories / inventory movements / tax differences — the full life cycle of depreciation, scrapping and stock movement",
"资产建档": "Create asset records", "月度折旧批处理": "Monthly depreciation batch",
"—（待审批通过后生成）": "— (generated after approval)",
"新增类别": "Add category", "折旧计提批单": "Depreciation accrual batch order",
"月度折旧计提两阶段：生成批单 → 审批通过后自动落账（共享中心资产核算岗）":
    "Monthly depreciation has two stages: generate the batch order, then post it automatically once approved (shared service centre asset accounting post)",
"结存查询": "Closing balance query", "存货处置(申请)": "Inventory disposal (application)",
"刷新税会差异": "Refresh tax differences",
"会计折旧与税法折旧差异汇总，支撑企业所得税 A105080「固定资产折旧纳税调整」":
    "Summary of the differences between accounting and tax depreciation, supporting corporate income tax form A105080 \"fixed asset depreciation tax adjustment\"",
"审计看板": "Audit board", "一键勾对": "Match in one click", "调节表": "Reconciliation table",
"回单认领": "Claim receipt",
"银行流水（对账记录）": "Bank statements (reconciliation records)",
"收款认领核销与回单归档": "Receipt claiming, write-off and receipt archiving",
"银行流水 → 关联应收单冲余额（强勾稽防超领）→ 回单归档":
    "Bank statement → offset against the related receivable (strict cross-check to prevent over-claiming) → archive the receipt",
"未达账项调节表": "Unreconciled items reconciliation table",
"生成调节表": "Generate reconciliation table", "立即勾对": "Match now",
"追溯记录明细": "Trace record details",
"调节后余额 = 银行余额 − 企业已付银行未付 + 企业已收银行未收 + 银行已付企业未付 − 银行已收企业未收":
    "Balance after reconciliation = bank balance − paid by company not paid by bank + received by company not received by bank + paid by bank not paid by company − received by bank not received by company",
"新建调整单": "New adjustment order", "预算执行明细": "Budget execution details",
"按期间/部门/科目查询预算项的占用、核销与余额（占用与核销的统计方式）":
    "Query budget item occupation, write-off and balance by period / department / account",
"导出 Excel": "Export to Excel", "导入 Excel": "Import from Excel",
"录入预算": "Enter budget", "预算占用流水": "Budget occupation ledger",
"新增方案": "Add plan", "在建工程中心": "Construction in progress centre",
"在建工程卡片 / 成本归集 / 转固定资产 —— 归集成本、监督余额、转固进固定资产全流程":
    "Construction in progress cards / cost collection / capitalization — collect costs, monitor balances and convert to fixed assets",
"共享服务平台": "Shared service platform", "业财税金 · 全流程贯通": "Finance, business and tax · end-to-end",
"费用、应付、应收、总账、资产、税务、资金、合同八大业务域，单据到凭证全链路闭环":
    "Eight business domains — expense, payables, receivables, general ledger, assets, tax, treasury and contracts — with a closed loop from document to voucher",
"智能三单匹配 · 识票验真": "Smart three-way matching · invoice recognition and verification",
"采购订单/入库/发票自动比对，OCR 识票、验真验重、数电票一键认证抵扣":
    "Automatically compare purchase orders, receipts and invoices; OCR recognition, verification and duplicate checks, one-click certification for digital e-invoices",
"产业链协同": "Supply chain collaboration",
"供应商门户与客户门户，在线对账、开票、付款状态实时同步":
    "Supplier and customer portals with online reconciliation, invoicing and real-time payment status sync",
"银企直联 · 资金统筹": "Bank direct link · treasury coordination",
"银企直联一键付款，资金池归集下拨，现金流实时滚动预测":
    "One-click payment via bank direct link, cash pool collection and allocation, rolling cash flow forecast",
"企业员工登录": "Employee sign in", "提交注册": "Submit registration",
"出差/休假期间，可把待办审批临时委托给他人代为处理":
    "During a business trip or leave, you can temporarily delegate pending approvals to someone else",
"新增委托": "Add delegation", "返回工作台": "Back to workbench",
"&nbsp;返回主页": "&nbsp;Back to home",
"新增政策": "Add policy",
"三档处置：提示 → 人工待审（进队列不挡流程） → 阻断（硬阻断位在付款指令执行前） —— 风险概览 + 检测记录 + 人工复审":
    "Three tiers: advisory → manual review (queued without blocking the flow) → blocking (the hard block sits before the payment instruction runs) — risk overview + detection records + manual re-review",
"新增合同": "New contract", "导出导入模板": "Export import template",
"导出单据": "Export documents", "导出部门排行": "Export department ranking",
"导出预算执行率": "Export budget execution rate",
"查看全链路联查示例": "View a full-chain lookup example",
"聚焦角色:": "Focus role:",
"贯穿 7 域的全程控制点（每个域都会经过）": "A control point running through all 7 domains (every domain passes through it)",
"影像核对": "Image verification", "SLA 质检": "SLA inspection",
"共享任务池": "Shared task pool", "角色工作台": "Role workbench",
"当前角色:": "Current role:",
"当前为全局视图：切换只决定显示哪几张卡，读数不随所选角色收窄":
    "This is the global view: switching only chooses which cards are shown; the readings are not narrowed to the selected role",
"在途": "In transit", "超时": "Overdue", "查看大屏": "View the dashboard",
"重估期间:": "Revaluation period:", "② 确认执行重估": "② Confirm the revaluation",
"年度:": "Year:", "月份:": "Month:", "历史期间:": "Historical period:",
"审批详情": "Approval details", "FSSC 移动报销": "FSSC mobile reimbursement",
"影像管理": "Image management", "+ 上传影像": "+ Upload images",
"缩略图模式 | 点击批次查看页面 | 点击页面预览大图 + OCR 标注":
    "Thumbnail mode | click a batch to see its pages | click a page to preview the large image with OCR annotations",
"无法预览": "Cannot preview", "+ 录入待评价影像": "+ Enter images pending evaluation",
"影像任务": "Image tasks",
"采集来源：80 扫描客户端 / 81 手机 / 82 人工上传 / 83 第三方系统":
    "Collection source: 80 scan client / 81 mobile / 82 manual upload / 83 third-party system",
"OCR识别记录": "OCR recognition records",
"OCR 结构化票面识别结果 —— 发票号/购销方/价税明细/票面自洽校验":
    "Structured OCR results from the bill — invoice No. / buyer and seller / tax breakdown / internal consistency check",
"占用对账": "Occupation reconciliation",
"定时验真：每 5 分钟扫描待验发票，仅「有影像且非手工补录」的发票进队列（无影像票不参与定时验真，需走【实时验真】）；外部验真通道未启用时不判定通过（保持未验真/验真中）":
    "Scheduled verification: every 5 minutes the pending invoices are scanned; only invoices with images that were not entered manually enter the queue (invoices without images need real-time verification). Without an external verification channel, nothing is judged as passed (it stays unverified or verifying).",
"进项发票全生命周期": "Full life cycle of input invoices",
"认证勾选(仅专票可抵扣) / 取消勾选 / 进项转出 / 票账比对申报汇总":
    "Certify and select (only special invoices are deductible) / cancel selection / input tax transfer-out / bill-versus-ledger reconciliation summary",
"批量认证勾选": "Batch certify and select",
"银行账户管理（账户全要素维护 · 印鉴/网银变更审批）":
    "Bank account management (full account attributes · seal and online-banking change approval)",
"新增账户": "Add account", "合规规则配置": "Compliance rule configuration",
"重复发票 / 费用不一致 / 超标 / 黑名单四类风险规则":
    "Four risk rule types: duplicate invoice / expense mismatch / over standard / blacklist",
"新增规则": "Add rule", "新增客户": "New customer",
"报销标准(专属页 · 费用类型 × 岗位职级 × 城市等级 三维标准 + 生效期/优先级/控制方式)":
    "Expense standards (dedicated page · three dimensions: expense type × position level × city tier, plus validity period, priority and control method)",
"新增标准": "Add standard",
"报销勾选发票时，按发票「货物或应税劳务、服务名称」自动预填费用类型；命中才改，未命中费用类型留空待填。":
    "When selecting invoices for reimbursement, the expense type is pre-filled from the invoice's goods or taxable services description; it is only changed on a match, and left blank for manual entry when there is no match.",
"上移、排序越靠前，同关键词越优先（精确匹配优先于包含）":
    "The higher an item is moved, the earlier it matches the same keyword (exact matches take priority over partial ones)",
"方案": "Plan", "新增根节点": "Add root node", "平台看板": "Platform board",
"租户管理": "Tenant management", "套餐功能开关": "Package feature switches",
"数据导出": "Data export", "FSSC 平台运营控制台": "FSSC platform operations console",
"FSSC 运营平台": "FSSC operations platform", "管理中心": "Management centre",
"租户全生命周期": "Full tenant life cycle",
"注册审核、开通、冻结与注销一站式管理":
    "One-stop management of registration review, activation, freezing and cancellation",
"套餐与配额": "Packages and quotas",
"功能包开关、用户数与存储配额灵活下发":
    "Flexible delivery of feature package switches, user counts and storage quotas",
"运营监控": "Operations monitoring",
"租户统计、运行状态、系统日志一屏总览": "Tenant statistics, run status and system logs on one screen",
"平台级合规": "Platform compliance",
"统一身份鉴权、登录凭证管控、操作全程记录":
    "Unified identity authentication, login credential control and full operation logging",
"平台管理员登录": "Platform administrator sign in", "三单匹配容差参数": "Three-way matching tolerances",
"发票 三单匹配的金额容差控制（按公司维度维护）":
    "Amount tolerance control for invoice three-way matching (maintained per company)",
"采购付款计划": "Procurement payment plan", "+ 新建付款计划": "+ New payment plan",
"合同/采购订单签订后提交付款计划 → 审批通过后流转付款申请":
    "Submit a payment plan after the contract or purchase order is signed → once approved it flows into a payment application",
"采购订单（PO）": "Purchase order (PO)", "新增采购订单": "New purchase order",
"应收管理": "Receivables management",
"应收单 / 收款核销 / 催收待办 / 坏账计提 —— 全流程可视化":
    "Receivables / receipt write-off / collection to-dos / bad debt provision — the full process visualised",
"导出费用明细": "Export expense details", "导出部门排名": "Export department ranking",
"导出预算执行": "Export budget execution",
"采购退货单（AP 退货退款冲减应付）": "Purchase return order (AP refund reduces payables)",
"销售退货单（退货退款并冲减应收）": "Sales return order (refund and reduce receivables)",
"资金管理": "Treasury management",
"资金计划 / 收款(渠道流水) / 资金调拨 / 票据结算 —— 编制、选中、核销、执行全流程可视化":
    "Cash plans / receipts (channel statements) / cash transfers / bill settlement — preparation, selection, write-off and execution visualised",
"去处理待办": "Go handle the to-dos", "监控大屏 / 效率分析": "Monitoring dashboard / efficiency analysis",
"流程日志中心": "Workflow log centre",
"流程处理详细日志（诊断说明）· 实例时间线 · 开关与清理 · 归档预览":
    "Detailed workflow logs (diagnostics) · instance timeline · switches and cleanup · archive preview",
"流程移交": "Workflow handover",
"员工离职/换岗时把名下在办审批任务批量交给接手人；逐单独立成败，失败明细照实返回。":
    "When an employee leaves or changes role, batch hand over their in-progress approval tasks to the successor; each document succeeds or fails independently and the failures are reported as they are.",
"流程收尾动作配置": "Workflow closing-action configuration",
"审批办结后的收尾动作按配置表驱动（业务类型 → 收尾动作）· 本页提供只读清单与有限编辑":
    "The closing actions that run after approval are driven by a configuration table (business type → closing action); this page offers a read-only list and limited editing",
}
