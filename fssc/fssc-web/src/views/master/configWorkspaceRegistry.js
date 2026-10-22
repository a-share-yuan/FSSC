/**
 * 方案B（页面Tab化重构册 §乙）配置工作区注册表：一组＝一个工作区菜单＝一个 host。
 * 后续波次（W1~W5/W8）批准时只在这里加分组，菜单/路由各加一行。
 * comp 一律复用既有通用组件（MasterConfigTable/BudgetConfigTable/SharingConfigTable），零逻辑改动。
 */
import MasterConfigTable from './MasterConfigTable.vue'
import SharingConfigTable from '../sharing/SharingConfigTable.vue'
import BudgetConfigTable from '../budget/BudgetConfigTable.vue'
import ImageManager from '../image/ImageManager.vue'
import ImageTask from '../image/ImageTask.vue'
import ImageReview from '../image/ImageReview.vue'
import ImageSetting from '../system/ImageSetting.vue'
// RA-PAGE-21 批次2 同域合并：业务页 tab 化（成员页路由全保留，深链不受影响）
import ExpenseApplyList from '../expense/ExpenseApplyList.vue'
import ApplyMeeting from '../expense/ApplyMeeting.vue'
import ApplyTravel from '../expense/ApplyTravel.vue'
import ApplyTrain from '../expense/ApplyTrain.vue'
import Contract from '../contract/Contract.vue'
import ContractPay from '../pay/ContractPay.vue'
import MatchList from '../pay/MatchList.vue'
import MatchTolerance from '../pay/MatchTolerance.vue'
import BankReconcile from '../bank/BankReconcile.vue'
import BankSlip from '../bank/BankSlip.vue'
import ReconcileSheet from '../bank/ReconcileSheet.vue'
import ArchivePage from '../sharing/ArchivePage.vue'
import SharingStatement from '../sharing/SharingStatement.vue'
import ArchiveBoard from '../archive/ArchiveBoard.vue'
import BudgetItem from '../budget/BudgetItem.vue'
import BudgetScheme from '../budget/BudgetScheme.vue'
import BudgetAdjust from '../budget/BudgetAdjust.vue'
import BudgetOccupy from '../budget/BudgetOccupy.vue'
import FraudBoard from '../compliance/FraudBoard.vue'
import ComplianceRule from './ComplianceRule.vue'
// D-DEC-320：流程收尾动作配置表（wf_event_action_config）此前无任何页面入口
import WfEventActionConfig from '../workflow/WfEventActionConfig.vue'
// R5-D-034：SoD 检查改为独立页（通用配置表无执行入口、列与返回体错位）
import SoDCheck from '../system/SoDCheck.vue'

export const CONFIG_GROUPS = {
  sharing: {
    title: '共享运营配置',
    sub: '作业岗位 · 分组 · 工作日历 · 抢派单规则',
    tabs: [
      { key: 'job', label: '作业岗位设置', comp: SharingConfigTable, props: { configType: 'job' } },
      { key: 'group', label: '分组管理', comp: SharingConfigTable, props: { configType: 'group' } },
      { key: 'calendar', label: '工作日历', comp: SharingConfigTable, props: { configType: 'calendar' } },
      { key: 'dispatch-rule', label: '抢派单规则', comp: SharingConfigTable, props: { configType: 'dispatch-rule' } },
    ],
  },
  system: {
    title: '系统参数',
    sub: '全局参数 · 字典 · 编号规则 · 文件 · 公告 · 消息',
    tabs: [
      { key: 'sys-config', label: '全局参数', comp: MasterConfigTable, props: { configKey: 'sys-config' }, perm: 'system:config:list' },
      { key: 'dict', label: '字典管理', comp: MasterConfigTable, props: { configKey: 'dict' }, perm: 'system:dict:list' },
      { key: 'sys-number', label: '编号规则', comp: MasterConfigTable, props: { configKey: 'sys-number' }, perm: 'system:number:list' },
      { key: 'sys-file', label: '文件管理', comp: MasterConfigTable, props: { configKey: 'sys-file' }, perm: 'system:file:list' },
      { key: 'sys-notice', label: '公告管理', comp: MasterConfigTable, props: { configKey: 'sys-notice' }, perm: 'system:notice:list' },
      { key: 'sys-message', label: '消息中心', comp: MasterConfigTable, props: { configKey: 'sys-message' }, perm: 'system:message:list' },
    ],
  },
  finance: {
    title: '财税主数据',
    sub: '币种 · 汇率 · 税码 · 现金流量 · 收支 · 记账',
    tabs: [
      { key: 'currency', label: '币种', comp: MasterConfigTable, props: { configKey: 'currency' } },
      { key: 'exchange-rate', label: '汇率管理', comp: MasterConfigTable, props: { configKey: 'exchange-rate' } },
      { key: 'tax-rate', label: '税码税率', comp: MasterConfigTable, props: { configKey: 'tax-rate' } },
      { key: 'cashFlow', label: '现金流量码', comp: MasterConfigTable, props: { configKey: 'cashFlow' } },
      { key: 'itemCashMap', label: '费用-现金流量映射', comp: MasterConfigTable, props: { configKey: 'itemCashMap' } },
      { key: 'incomeExpense', label: '收支设置', comp: MasterConfigTable, props: { configKey: 'incomeExpense' } },
      { key: 'glConfig', label: '记账配置', comp: MasterConfigTable, props: { configKey: 'glConfig' } },
    ],
  },
  org: {
    title: '组织人事主数据',
    sub: '组织类型 · 城市等级 · 员工等级 · 收款方 · 金融机构',
    tabs: [
      { key: 'orgType', label: '组织类型', comp: MasterConfigTable, props: { configKey: 'orgType' } },
      { key: 'city', label: '城市等级', comp: MasterConfigTable, props: { configKey: 'city' } },
      { key: 'employeeLevel', label: '员工等级', comp: MasterConfigTable, props: { configKey: 'employeeLevel' } },
      { key: 'payee', label: '收款方', comp: MasterConfigTable, props: { configKey: 'payee' } },
      { key: 'bank-inst', label: '金融机构', comp: MasterConfigTable, props: { configKey: 'bank-inst' } },
    ],
  },
  budget: {
    title: '预算规则配置',
    sub: '预算类型 · 类别 · 组织 · 期间 · 科目映射',
    tabs: [
      { key: 'budgetType', label: '预算类型', comp: MasterConfigTable, props: { configKey: 'budgetType' } },
      { key: 'budgetCategory', label: '预算类别', comp: BudgetConfigTable, props: { configType: 'budgetCategory', title: '预算类别' } },
      { key: 'budgetOrg', label: '预算组织', comp: BudgetConfigTable, props: { configType: 'budgetOrg', title: '预算组织' } },
      { key: 'budgetPeriod', label: '预算期间', comp: BudgetConfigTable, props: { configType: 'budgetPeriod', title: '预算期间' } },
      { key: 'itemBudgetMap', label: '费用-预算科目映射', comp: BudgetConfigTable, props: { configType: 'itemBudgetMap', title: '费用类型-预算科目映射' } },
      { key: 'costCenterMap', label: '成本中心映射', comp: MasterConfigTable, props: { configKey: 'costCenterMap' } },
    ],
  },
  expense: {
    title: '费用与单据配置',
    sub: '费用类型 · 表单配置 · 交通工具 · 项目 · 合同类型',
    tabs: [
      { key: 'expense-item', label: '费用类型', comp: MasterConfigTable, props: { configKey: 'expense-item' } },
      { key: 'form', label: '表单详情配置', comp: MasterConfigTable, props: { configKey: 'form' } },
      { key: 'vehicle', label: '交通工具', comp: MasterConfigTable, props: { configKey: 'vehicle' } },
      { key: 'project', label: '项目管理', comp: MasterConfigTable, props: { configKey: 'project' } },
      // RA 裁决⑤：合同类型语义＝业务主数据，从「运行监控与合规」移入；无 md:contracttype:list 码的角色不渲染此 tab（勿扩权）
      { key: 'contractType', label: '合同类型', comp: MasterConfigTable, props: { configKey: 'contractType' }, perm: 'md:contracttype:list' },
    ],
  },
  loan: {
    title: '借款规则配置',
    sub: '借款额度 · 初始借款余额 · 借款付款类型',
    tabs: [
      { key: 'loanLimit', label: '借款额度', comp: MasterConfigTable, props: { configKey: 'loanLimit' } },
      { key: 'loanInitial', label: '初始借款余额', comp: MasterConfigTable, props: { configKey: 'loanInitial' } },
      { key: 'loanPayType', label: '借款付款类型', comp: MasterConfigTable, props: { configKey: 'loanPayType' } },
    ],
  },
  ops: {
    title: '运行监控与合规',
    sub: '定时任务健康 · 不相容职责检查',
    tabs: [
      { key: 'job-heartbeat', label: '定时任务健康', comp: MasterConfigTable, props: { configKey: 'job-heartbeat' } },
      { key: 'sod-check', label: '不相容职责检查', comp: SoDCheck, perm: 'system:sod:list' },
      // D-DEC-320：引擎钩子配置表补齐可见可控；按 wf:config:list 门控（无码角色不渲染页签，勿扩权）
      { key: 'wf-event-action', label: '流程收尾动作配置', comp: WfEventActionConfig, perm: 'wf:config:list' },
    ],
  },
  image: {
    title: '影像管理',
    sub: '批次采集 · 影像任务 · 异常处理 · 系统设置',
    tabs: [
      { key: 'batch', label: '影像采集批次', comp: ImageManager },
      { key: 'task', label: '影像任务', comp: ImageTask },
      { key: 'exception', label: '异常处理', comp: ImageReview, props: { tab: 'exc' } },
      { key: 'setting', label: '系统设置', comp: ImageSetting },
    ],
  },
  // ===== RA-PAGE-21 批次2：同域业务页 tab 合并（成员页路由全保留，退的只是菜单入口）=====
  preapply: {
    title: '事前申请',
    sub: '申请列表 · 会议 · 出差 · 培训',
    tabs: [
      { key: 'list', label: '申请列表', comp: ExpenseApplyList },
      { key: 'meeting', label: '会议申请', comp: ApplyMeeting },
      { key: 'travel', label: '出差申请', comp: ApplyTravel },
      { key: 'train', label: '培训申请', comp: ApplyTrain },
    ],
  },
  contract: {
    title: '合同管理',
    sub: '合同台账 · 合同付款查询',
    tabs: [
      { key: 'ledger', label: '合同台账', comp: Contract },
      // 合同付款查询走报表接口（report:view）；多数合同角色只有 contract:list，无码不渲染页签
      { key: 'pay-query', label: '合同付款查询', comp: ContractPay, perm: 'report:view' },
    ],
  },
  match: {
    title: '对公三单匹配',
    sub: '匹配操作 · 匹配容差',
    tabs: [
      { key: 'match', label: '匹配操作', comp: MatchList },
      { key: 'tolerance', label: '匹配容差', comp: MatchTolerance, perm: 'pay:match:list' },
    ],
  },
  bankRecon: {
    title: '银企对账',
    sub: '银企对账 · 回单认领 · 余额调节表 · 银行流水',
    tabs: [
      { key: 'reconcile', label: '银企对账', comp: BankReconcile },
      { key: 'slip', label: '回单·收款认领', comp: BankSlip },
      { key: 'sheet', label: '未达账项调节表', comp: ReconcileSheet },
      // RA-PAGE-22 批次3：T-TR-5 银行对账作业上线后，原独立菜单 108 银行流水并入（门控 op:bank:list）
      { key: 'statement', label: '银行流水', comp: SharingStatement, perm: 'op:bank:list' },
    ],
  },
  archive: {
    title: '档案管理',
    sub: '档案归档 · 借阅销毁',
    tabs: [
      { key: 'archive', label: '档案归档', comp: ArchivePage },
      { key: 'borrow', label: '借阅与销毁', comp: ArchiveBoard, perm: 'compliance:archiveaccess:list' },
    ],
  },
  budgetManage: {
    title: '预算管理',
    sub: '科目 · 方案 · 调整 · 占用',
    tabs: [
      { key: 'item', label: '预算科目', comp: BudgetItem, perm: 'budget:item:list' },
      { key: 'scheme', label: '预算方案', comp: BudgetScheme, perm: 'budget:scheme:list' },
      { key: 'adjust', label: '预算调整', comp: BudgetAdjust, perm: 'budget:adjust:list' },
      { key: 'occupy', label: '预算占用', comp: BudgetOccupy, perm: 'budget:occupy:list' },
    ],
  },
  compliance: {
    title: '合规风控',
    sub: '反欺诈风控 · 合规规则',
    tabs: [
      { key: 'fraud', label: '反欺诈风控', comp: FraudBoard, perm: 'compliance:fraud:list' },
      { key: 'rule', label: '合规规则', comp: ComplianceRule, perm: 'compliance:rule:list' },
    ],
  },
}
