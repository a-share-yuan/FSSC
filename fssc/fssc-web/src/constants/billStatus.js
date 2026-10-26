// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * FSSC 统一单据状态枚举
 * 全系统所有页面、组件共用此文件，禁止在各组件中重复定义。
 *
 * 标准 6 步生命周期：DRAFT → APPROVING → APPROVED → PAID → POSTED → ARCHIVED
 * 异常分支：REJECTED / VOID / CLOSED / TERMINATED
 */

// 【2026-10-15 第 3 批】字典读取（同步、只读内存缓存，不发请求）
// 依赖方向：billStatus.js → useDict.js → api.js，三者均不反向引用本文件，无循环依赖。
import { dictLabelOf, dictCssClassOf } from '@/composables/useDict.js'

// 状态码
export const BillStatus = {
  DRAFT: 'DRAFT',
  SUBMITTED: 'SUBMITTED',
  APPROVING: 'APPROVING',
  APPROVED: 'APPROVED',
  PAID: 'PAID',
  POSTED: 'POSTED',
  ARCHIVED: 'ARCHIVED',
  REJECTED: 'REJECTED',
  VOID: 'VOID',
  CLOSED: 'CLOSED',
  TERMINATED: 'TERMINATED',
}

// 状态 → 中文 label
// 前 13 项为标准生命周期；其后是流程实例/任务/主数据等在各页面流转的枚举码，
// 缺键时 statusLabel 会把英文原码直接渲染到界面（历史泄露根因），新增枚举码须同步登记。
export const STATUS_LABEL = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  ISSUED: '已下达',
  ACTIVE: '启用中',
  APPROVED: '已审批',
  PAID: '已付款',
  POSTED: '已记账',
  ARCHIVED: '已归档',
  REJECTED: '已驳回',
  VOID: '已作废',
  CLOSED: '已关闭',
  TERMINATED: '已终止',
  // 记账域红字冲销/作废后的单据最终状态（gl_journal_entry.status=REVERSED，D-DEC-128）；
  // 缺键时列表直接显示英文码，故在此登记（与汇兑重估反冲 REVERSED 同码同义）
  REVERSED: '已冲销',
  // 流程实例（wf_instance.status）
  RUNNING: '运行中',
  COMPLETED: '已完成',
  PROCESS_COMPLETED: '已完成',
  CANCELLED: '已取消',
  WITHDRAWN: '已撤回',
  SUSPENDED: '已挂起',
  // 借款/核销生命周期
  REPAID: '已还款',
  PARTIAL_REPAID: '部分还款',
  // 主数据与定义状态
  NORMAL: '正常',
  DISABLED: '已停用',
  EXPIRED: '已过期',
  OVERDUE: '已逾期',
  // 作业/异步任务状态
  PENDING: '待处理',
  PROCESSING: '处理中',
  TODO: '待办',
  DONE: '已完成',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败',
  SKIPPED: '已跳过',
  GENERATED: '已生成',
  // 付款批次出纳确认态（后端 op_pay_batch.status=CONFIRMED）；缺键时列表原样渲染英文码
  CONFIRMED: '已确认',
  // 归档/反归档与档案立卷态（exp_bill.archive_status / op_archive.status）
  UNARCHIVED: '未归档',
  FILED: '已立卷',
  // 在建工程转固态（md_cip.status）；缺 IN_PROGRESS 时看板「状态」列直接显示英文码
  IN_PROGRESS: '进行中',
  TRANSFERRING: '转固审批中',
  TRANSFERRED: '已转固',
  // 配置启用态（ext_interface_config.status=ENABLED，与主数据 DISABLED 成对）
  ENABLED: '已启用',
  // 银行流水勾对态（op_bank_statement.recon_status / bank_reconcile.match_status）
  MATCHED: '已勾对',
  UNRECON: '未对账',
  // 采购收货态（po_receipt.status / quality_status）
  RECEIVED: '已收货',
  PASSED: '已验收',
  // 批次级补充哨兵：一个批次覆盖多张单据且状态不一致时，后端置 MIXED（D-DEC-252）
  MIXED: '状态不一',
  // 合同生命周期（cont_contract.status=EFFECTIVE，变更/关闭按钮的判据）
  EFFECTIVE: '已生效',
  // 合同执行态（cont_contract.status 库内现值 SIGNED/EXECUTING，10-25 报表走查实测直接显示英文后补登）
  SIGNED: '已签约',
  EXECUTING: '执行中',
  // 报表定义启停（rpt_report_def.status=ACTIVE/INACTIVE）
  INACTIVE: '已停用',
  // 总账会计期间关账态（gl_month_close.status=UNCLOSED/CLOSED）
  UNCLOSED: '未结账',
  // 流程模型发布态（wf_model.status=DEPLOYED，WfDesignerController 发布写入）
  DEPLOYED: '已发布',
  // 角色停用态：界面开关写 DISABLED，历史种子用的是 DISABLE
  DISABLE: '已停用',
  // —— 跨域「我发起的单据」汇总进来的各域本表状态码（D-DEC-406：工作台该卡由费用单表改跨域归并后，
  //    这些列值第一次被喂进统一 statusLabel，缺键即直接显示英文）——
  // 采购：po_order／po_receipt 关联发票后 RECEIVED/APPROVED→INVOICED，冲销后回退（PurchaseService:636-648）
  INVOICED: '已开票',
  // 合并报表已出表（gl_consolidate.status，GlConsolidateService:75）
  CONSOLIDATED: '已合并',
  // 内部往来抵消已记账（gl_inter_offset.offset_status，GlInterOffsetService:148/246 写入 OFFSET；
  // R5-D-161 补：此前名称表无此键，GL 专项页签状态列直接显示英文 OFFSET）
  OFFSET: '已抵消',
  // 供应商对账不平（supplier_recon.status，SupplierReconService:386/433/596）
  DIFF: '有差异',
  // 坏账准备已计提（rc_bad_debt.status＝PROVISIONED/REVERSED/WRITTEN_OFF 之首）
  PROVISIONED: '已计提',
  // 催收待办（rc_collect_todo.status=DOING，实测 442 行；全库仅这一列用此码）
  DOING: '催收中',
}

// 状态 → el-tag type
export const STATUS_TYPE = {
  DRAFT: 'info',
  SUBMITTED: 'info',
  APPROVING: 'warning',
  ISSUED: 'success',
  ACTIVE: 'success',
  APPROVED: 'success',
  PAID: 'success',
  POSTED: 'success',
  ARCHIVED: 'success',
  REJECTED: 'danger',
  VOID: 'danger',
  CLOSED: 'danger',
  TERMINATED: 'danger',
  RUNNING: 'primary',
  COMPLETED: 'success',
  PROCESS_COMPLETED: 'success',
  DONE: 'success',
  SUCCESS: 'success',
  GENERATED: 'success',
  REPAID: 'success',
  NORMAL: 'success',
  CANCELLED: 'info',
  WITHDRAWN: 'info',
  DISABLED: 'info',
  PENDING: 'info',
  TODO: 'info',
  SKIPPED: 'info',
  PARTIAL_REPAID: 'warning',
  CONFIRMED: 'warning',
  SUSPENDED: 'warning',
  PROCESSING: 'warning',
  OVERDUE: 'warning',
  EXPIRED: 'warning',
  // R5-D-161：内部往来抵消已记账态与「已合并」同色（成功最终状态）
  OFFSET: 'success',
  // 催收进行中（rc_collect_todo.status=DOING）
  DOING: 'warning',
  FAIL: 'danger',
  FAILED: 'danger',
}

/**
 * 统一 statusLabel 函数
 * 未登记的状态码仍返回原码（保证不吞值），但会在开发期告警，避免英文码静默泄露到界面。
 */
/**
 * 【2026-10-15 第 3 批枚举办结】状态码 → 中文，改为「字典优先、本地常量默认处理」。
 *
 * 为什么能零风险切换：
 *   字典是运行时远程数据，可能未部署/未登录/未预热/网络不通；
 *   STATUS_LABEL 是编译期常量，同步可得、永不失败。
 *   故顺序固定为「先查字典缓存（同步、无请求）→ 未命中回落本地常量」：
 *     · 字典没到 ⇒ 结果与改造前**逐字一致**（不会白屏、不会变英文码）；
 *     · 字典到了 ⇒ 以字典为准，运营可在界面改文案而不必重新发版。
 *
 *  这里是**同步**取值：不发请求，只读 useDict 的内存缓存（由 App.vue 预热填充）。
 *    绝不能在此处 await —— statusLabel 被 55 个文件当作纯函数直接调用。
 *
 *  用扁平字典 bill_status_display 而非按域拆的 15 个字典：
 *    statusLabel 是**全局单一映射**，调用方不带域上下文，
 *    CLOSED/DRAFT/NORMAL 等跨义键无法判定归属 ⇒ 展示规则另立一套扁平字典。
 *    域字典用于下拉可选值，本字典用于列表展示，两者职责不同、互不覆盖。
 */
const DICT_DISPLAY = 'bill_status_display'

const warnedStatus = new Set()
export function statusLabel(s) {
  // ① 字典（运营可维护的权威源）
  const fromDict = dictLabelOf(DICT_DISPLAY, s)
  if (fromDict) return fromDict
  // ② 本地常量（默认处理，保证改造前后行为一致）
  if (STATUS_LABEL[s]) return STATUS_LABEL[s]
  if (import.meta.env.DEV && s && !warnedStatus.has(s)) {
    warnedStatus.add(s)
    console.warn(`[billStatus] 未登记的状态码「${s}」将原样显示为英文，请补入 STATUS_LABEL 或字典 ${DICT_DISPLAY}`)
  }
  return s
}

/**
 * 统一 statusType 函数
 * 同样改为「字典优先」：字典行的 css_class 存的就是 el-tag 的 type，
 * 未取到时回落 STATUS_TYPE，缺省 'info'。
 */
export function statusType(s) {
  return dictCssClassOf(DICT_DISPLAY, s) || STATUS_TYPE[s] || 'info'
}

/**
 * 驳回类型（exp_bill.reject_type，P0-5）→ 中文 label。
 * 取值规则与写侧一致（ExpenseWfEventListener:628 只落 THROW_BACK / GRADE_BY_GRADE 两值），
 * 未登记值原样返回，避免静默吞值。
 */
export const REJECT_TYPE_LABEL = {
  THROW_BACK: '漏单驳回',
  GRADE_BY_GRADE: '逐级驳回',
}
export function rejectTypeLabel(s) {
  if (!s) return ''
  return REJECT_TYPE_LABEL[s] || s
}

/**
 * 批次1 统一状态门控（唯一入口，各页禁止再写私有白名单）：
 * 草稿/已驳回单据可「编辑重提」；驳回单一律先调各域 edit 端点回退草稿再修改重报。
 */
export const EDIT_RESUBMIT_STATUSES = [BillStatus.DRAFT, BillStatus.REJECTED]
export function canEditResubmit(row) {
  return !!row && EDIT_RESUBMIT_STATUSES.includes(row.status)
}

/**
 * 费用行式单据（exp_bill）编辑重提的填单页路由：按 类型+子类型 定位，禁止各页再写私有映射。
 */
const BILL_SUBTYPE_ROUTE = {
  MEETING: '/expense/apply/meeting',
  TRAVEL: '/expense/apply/travel',
  TRAIN: '/expense/apply/train',
  PREPAY: '/corp-pay/prepay',
  SETTLE: '/corp-pay/settle',
  AGENCY: '/corp-pay/agency',
  AIR: '/corp-pay/air',
}
export function editBillPath(row) {
  const st = row.billSubType
  if (row.billType === 'APPLY') return BILL_SUBTYPE_ROUTE[st] || '/expense/apply'
  if (row.billType === 'CLAIM' && st === 'TRAVEL') return '/expense/claim-travel'
  if (row.billType === 'PAY' && BILL_SUBTYPE_ROUTE[st]) return BILL_SUBTYPE_ROUTE[st]
  return { CLAIM: '/expense/claim', LOAN: '/expense/loan', REPAY: '/expense/repay', PAY: '/corp-pay/settle' }[row.billType] || '/expense/claim'
}
