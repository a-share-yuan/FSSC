// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 通用单据逻辑 composable：费用报销域所有单据页共享
// 覆盖：草稿保存/提交、我的单据、凭证预览/记账、预算占用预览、附件上传、冲借款、关联事前申请
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, mdApi, detailApi, fileApi, sharingApi, contractApi, customerApi, complianceApi, orgApi, flowApi, wfApi, store, masterCfgApi } from '../api'
import { statusType as _statusType, statusLabel as _statusLabel } from '../constants/billStatus'
import { budgetLabel, budgetUnseenLabel } from '../utils/format'
import { hasPerm } from '../utils/perm'
import { pickBudgetLine, resolveLineBudgetId } from '../utils/budget'
import { splitTaxInclusive } from '../utils/tax'
import { requiredOf, missingBillField } from '../constants/billRequired'
import { invoiceOutcome } from '../utils/invoiceOutcome'
import { isInvoiceFile } from '../utils/attachKind'


// D-DEC-361：预填值必须与后端写入数据库时区一致。`toISOString()` 给的是 UTC 串，东八区读出来恒差 −8h
// （实测「制单时间」预填 12:05 而页壳/库内 create_time 均为 20:05），跨零点还会把日期错成昨天。
// 后端 create_time / apply_date 走 LocalDateTime.now()（服务器本地钟），故前端一律按本地时钟取值，
// 不引日期库。
function today() {
  return localDateParts(new Date()).date
}
function nowTs() {
  const p = localDateParts(new Date())
  return `${p.date} ${p.time}`
}
/** 本地时钟的 YYYY-MM-DD / HH:mm:ss（零依赖；padStart 保位，不用 toISOString） */
function localDateParts(d) {
  const p = (n) => String(n).padStart(2, '0')
  return {
    date: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`,
    time: `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`,
  }
}

/**
 * 表头总金额的唯一规则：只加「会进本次载荷的行」＝已选费用类型的行。
 *
 * <p>D-V4R-37：saveDraft 的 lines 载荷历来只带 itemId 行（item_id 列 NOT NULL，带未选行整单 500），
 * 而 totalAmount 取的是界面合计（含未选费用类型那几行）⇒ 表头 3,900／明细写入数据库只有 2,700 的脏草稿，
 * 提交后预算占用按行算（2,700）、审批与付款按表头算（3,900）。界面「总金额」仍走 sumAmount()，
 * 让用户看见自己填的每一分；只有真正写库的表头金额跟着载荷走。
 */
export function submittedTotal(lines) {
  return Number((lines || []).filter((l) => l.itemId).reduce((a, l) => a + Number(l.amount || 0), 0).toFixed(2))
}

/**
 * D-V4R-35：事前申请页没有「费用明细」卡，整单靠 ensureLine() 合成的一条行承载金额，而服务端预算占用/核销
 * 只读 exp_bill_line.budget_item_id（ExpenseBillService 逐行 occupy；ExpBillShare 全仓仅 CRUD 四个引用点，
 * 没有任何消费方）⇒ 用户在分摊行选的预算科目与实际被校验、被占用的那条预算线是两套，屏显「可支 X」与
 * 拦截数字对不上。修法＝让「一屏一尺」：把分摊行的科目写进那条承载行，占用/核销/释放自动同科目。
 * 整单只落一条预算线 ⇒ 多科目分摊（或一页多行）在这里落不了地，返回 conflict 交调用方拒提，静默择一。
 * @returns {{applied: boolean, conflict: boolean, budgetItemId: (number|null)}}
 */
export function shareBudgetToLine(lines, shares) {
  const items = [...new Set((shares || []).map((s) => s && s.budgetItemId).filter((v) => v != null))]
  const ls = lines || []
  if (items.length > 1 || ls.length > 1) return { applied: false, conflict: true, budgetItemId: null }
  if (!items.length || !ls.length) return { applied: false, conflict: false, budgetItemId: null }
  ls[0].budgetItemId = items[0]
  return { applied: true, conflict: false, budgetItemId: items[0] }
}

export function useBill(opts) {
  // opts: { billType, subType, claim?, travel?, pay?, loadExtra? }
  const billType = opts.billType
  const subType = opts.subType || ''

  const activeTab = ref('create')
  const filterType = ref(billType)
  const form = reactive({
    bill: {
      id: null,
      billType,
      billSubType: subType,
      title: '',
      companyId: store.user?.companyId || null,
      deptId: store.user?.deptId || null,
      applyUserId: store.user?.userId ?? store.user?.id ?? null,
applyUserName: store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '',
      agentUserId: null,
      applyDate: today(),
      periodYear: new Date().getFullYear(),
      currencyCode: 'CNY',
      exchangeRate: 1,
      payeeName: '',
      remark: '',
      contractId: null,
      customerId: null,
      extJson: {},
    },
    lines: [newLine()],
    travels: [],
    shares: [],
    loanOffsets: [],
    relations: [],
    attaches: [],
    invoices: [],
  })

  const items = ref([])
  const budgets = ref([])
  // D-H5M-06：预算池是按单据费用年度取的，池对应的年度记在这里（池与年度必须一致）
  const budgetPoolYear = ref(null)
  // FINAL-01: 费用类型→预算科目映射(md_item_budget_map, itemId→budgetItemId)，
  // 选择费用类型时自动带出对应预算科目；用户已手选则不覆盖
  const itemBudgetMap = ref({})
  const cities = ref([])
  const payees = ref([])
  const contracts = ref([])
  const customers = ref([])
  const costCenters = ref([])
  const projects = ref([])
  const companies = ref([])
  const depts = ref([])
  const bills = ref([])
  const uploading = ref(false)
  const voucher = reactive({ id: null, voucherNo: '', status: '', debitTotal: 0, creditTotal: 0, isBalanced: 0 })
  const voucherLines = ref([])
  const selected = ref({})
  const loanDialog = ref(false)
  const applyDialog = ref(false)
  const loanCandidates = ref([])
  // D-UI-01：借款冲抵弹层的临时勾选与冲抵金额（支持部分冲抵，确定后才写入 form.loanOffsets）
  const applyCandidates = ref([])
  const detailVisible = ref(false)
  const detailData = reactive({ bill: {}, lines: [], shares: [], travels: [], loanOffsets: [], relations: [], attaches: [], wfTrace: [] })

  // 顶部「流程进度」跟着当前这张单走：详情抽屉打开时看抽屉，否则看当前页签（发起页看正在编辑的单，我的单据看选中行）。
  // 旧实现只看 detailData，抽屉开过一次就永久停在上一张单，切行/改单都不动。
  const focusedBill = computed(() => {
    if (detailVisible.value && detailData.bill?.id) return detailData.bill
    const editing = form.bill?.id ? form.bill : null
    const row = selected.value?.id ? selected.value : null
    if (activeTab.value === 'create') return editing || row
    return row || editing
  })

  // ---------------- 行/行程 构造 ----------------
  function newLine() {
    return {
      id: null, itemId: null, itemName: '', expenseDate: today(), amount: 0, taxRate: 0, taxAmount: 0,
      netAmount: 0, budgetItemId: null, stdAmount: null, overStd: 0, overReason: '', remark: '',
      quantity: 1, unitPrice: null,
      // D-DEC-023：招待费三项的真实承载列（exp_bill_line.attendees / person_count）。
      // D-DEC-373 起类型也有列（entertain_type）。缺这些键时页面 v-model 只能挂到裸字段上，后端实体无同名属性 ⇒ 提交即丢。
      attendees: null, personCount: null, entertainType: null,
      extJson: {},
    }
  }
  function newTravel() {
    return {
      // 出差人员默认＝当前登录人（同文件 81 行「申请人＝当前登录人」同源）。
      // 留空会让下拉首位（userApi.page 第 0 行实测＝id 1/admin/系统管理员）被回车选中，且单据上不是本人。
      travelerName: store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '',
      // 出差人员的外键：下拉只绑姓名 ⇒ traveler_id 恒 NULL（重名/改名即断，按 id 取标准的规则也拿不到）
      travelerId: store.user?.userId ?? store.user?.id ?? null,
      fromCityId: null, toCityId: null, destCountry: '', beginDate: '', endDate: '', days: 0,
      // 是否往返：这栏参与 calcTravel 的天数规则（往返 +1 天），原先连键都没有 ⇒ 保存不写入数据库、编辑回来是空的
      roundTrip: false,
      trafficType: 'RAIL', trafficClass: '', hotelAmount: 0, trafficAmount: 0, allowanceAmount: 0,
      otherAmount: 0, totalAmount: 0, overStd: 0, remark: '',
    }
  }
  function newShare() {
    return {
      companyId: null, deptId: null, costCenterId: null, projectId: null,
      budgetItemId: null, shareType: null,
      shareRatio: 100, shareAmount: 0, remark: '',
    }
  }

  // ---------------- 工具 ----------------
  function fmt(v) { return v == null ? '0.00' : Number(v).toFixed(2) }
  function recalcLine(row) {
    // 价内税规则：含税金额 ÷ (1+税率) 反算不含税，税额 = 含税 − 不含税（与票面/税务规则一致）
    const r = splitTaxInclusive(row.amount, row.taxRate)
    row.taxAmount = r.taxAmount
    row.netAmount = r.netAmount
  }
  // 量价联动：有数量且单价>0 时，数量×单价 反算金额（金额优先规则，前端即时勾稽）
  function qtyPriceChange(row) {
    const qty = Number(row.quantity || 0)
    const price = Number(row.unitPrice)
    if (qty > 0 && price > 0) {
      row.amount = Math.round(qty * price * 100) / 100
    }
    recalcLine(row)
  }
  // 量价勾稽状态：返回 null（未录入量价）或 {calc, match}（|数量×单价 − 金额| ≤ 0.01）
  function qtyCheck(row) {
    const qty = Number(row.quantity || 0)
    const price = Number(row.unitPrice)
    if (qty <= 0 || !(price > 0)) return null
    const calc = Math.round(qty * price * 100) / 100
    return { calc, match: Math.abs(calc - Number(row.amount || 0)) <= 0.01 }
  }
  function onItemChange(row, id) {
    const it = items.value.find((x) => x.id === id)
    row.itemName = it ? it.itemName : ''
    if (id == null) return
    // FINAL-01: 按费用类型自动带出映射的预算科目；手选过则不覆盖
    if (row.budgetItemId == null) {
      const bid = pickBudgetFor(id)
      if (bid != null) row.budgetItemId = bid
      return
    }
    // D-H5M-05：换了费用类型，原先按旧类型挂上的预算行要跟着重取。
    // 这条判据原先写在 resolveLineBudgetId() 里，那个函数同时被「重开草稿」调用，
    // 会把库里已保存且合法的预算科目清成空（D-V4R-1026-04）⇒ 收回到这里，只在用户真的换费用类型时执行。
    const hit = budgets.value.find((b) => String(b.id) === String(row.budgetItemId))
    if (hit && String(hit.itemId ?? '') !== String(id)) row.budgetItemId = pickBudgetFor(id)
  }
  /**
   * 费用类型 → 预算明细行 解析（规则见 utils/budget.js pickBudgetLine，D-P2-01）。
   * D-H5M-06：业务年度是硬条件 —— 预算行的年度必须与单据费用年度一致，
   * 否则 2026 年的单会命中 2025 年预算行，额度判断与超支提示全部失真。
   */
  function pickBudgetFor(itemId) {
    return pickBudgetLine({
      pool: budgets.value, itemId, map: itemBudgetMap.value,
      companyId: form.bill.companyId, deptId: form.bill.deptId,
      budgetYear: form.bill.periodYear,
    })
  }
  // ---------- 发票清单（上传解析 + 手动录入）→ 生成费用明细行 ----------
  let invoiceSeq = 0
  function newInvoice() {
    return { uid: ++invoiceSeq, checked: false, lineRef: null, fileId: null, invoiceNo: '', invoiceDate: '', amount: 0, netAmount: 0, taxAmount: 0, taxRate: 0, sellerName: '', sellerTaxNo: '', invoiceType: '', primaryItemName: '', parsed: false, sourceName: '' }
  }
  function recalcInvoice(inc) {
    // 与明细行同一价内税规则，避免"发票清单行 / 明细行"两套算法
    const r = splitTaxInclusive(inc.amount, inc.taxRate)
    inc.taxAmount = r.taxAmount
    inc.netAmount = r.netAmount
  }
  function newLineFromInvoice(inc) {
    const line = newLine()
    line.expenseDate = inc.invoiceDate || today()
    line.amount = Number(inc.amount || 0)
    line.taxRate = Number(inc.taxRate || 0)
    recalcLine(line)
    line.remark = inc.invoiceNo ? `发票 ${inc.invoiceNo}` : ''
    line.extJson = { invoice: { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount } }
    return line
  }
  // 按发票首条「货物或应税劳务、服务名称」智能匹配费用类型：命中才填，未命中留空待填
  async function applyItemMatch(inc) {
    if (!inc || !inc.lineRef) return
    const name = (inc.primaryItemName || '').trim()
    if (!name) return
    try {
      const m = await mdApi.invoiceRuleMatch(name)
      if (m && m.expenseItemId != null && inc.lineRef) {
        inc.lineRef.itemId = m.expenseItemId
        onItemChange(inc.lineRef, m.expenseItemId)
      }
    } catch (e) { /* 匹配失败静默，费用类型留空待填 */ }
  }
  function syncInvoiceLine(inc) {
    if (!inc.checked || !inc.lineRef) return
    const line = inc.lineRef
    line.amount = Number(inc.amount || 0)
    line.taxRate = Number(inc.taxRate || 0)
    recalcLine(line)
    line.expenseDate = inc.invoiceDate || line.expenseDate
    line.remark = inc.invoiceNo ? `发票 ${inc.invoiceNo}` : line.remark
    line.extJson = { invoice: { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount } }
  }
  function toggleInvoice(inc) {
    if (inc.checked) {
      if (!inc.lineRef) inc.lineRef = newLineFromInvoice(inc)
      if (!form.lines.includes(inc.lineRef)) form.lines.push(inc.lineRef)
      applyItemMatch(inc)
    } else {
      const idx = form.lines.findIndex((l) => l === inc.lineRef)
      if (idx >= 0) form.lines.splice(idx, 1)
      inc.lineRef = null
    }
  }
  function removeInvoice(i) {
    const inc = form.invoices[i]
    if (inc.lineRef) { const idx = form.lines.findIndex((l) => l === inc.lineRef); if (idx >= 0) form.lines.splice(idx, 1) }
    form.invoices.splice(i, 1)
  }
  // 行的发票元信息：优先内存对象，否则从持久化 extJson 解析回填
  function invOf(line) {
    if (line.invoice) return line.invoice
    if (line.extJson) {
      try { const o = typeof line.extJson === 'string' ? JSON.parse(line.extJson) : line.extJson; if (o && o.invoice) { line.invoice = o.invoice; return o.invoice } } catch (e) { /* ignore */ }
    }
    return null
  }
  // 从已存明细行的发票元信息（行 extJson.invoice）反查回填「已解析发票清单」，
  // 供草稿/复制单重新打开时继续勾选、调整（不重复生成明细行，直接复用原行）。
  function rebuildInvoicesFromLines() {
    let seq = 0
    form.invoices = []
    form.lines.forEach((l) => {
      const meta = invOf(l)
      if (!meta) return
      const inc = {
        uid: --seq,                                              // 反查行的 uid 取负号，避免与新增发票行冲突
        checked: true, lineRef: l, fileId: meta.fileId || null,
        invoiceNo: meta.invoiceNo || '', invoiceDate: meta.invoiceDate || '',
        amount: Number(meta.amount != null ? meta.amount : l.amount || 0),
        netAmount: Number(meta.netAmount != null ? meta.netAmount : l.netAmount || 0),
        taxAmount: Number(meta.taxAmount != null ? meta.taxAmount : l.taxAmount || 0),
        taxRate: Number(l.taxRate || 0),
        sellerName: meta.sellerName || '', sellerTaxNo: meta.sellerTaxNo || '',
        invoiceType: meta.invoiceType || '', primaryItemName: meta.primaryItemName || '', parsed: true,
        sourceName: meta.fileId ? '#文件' + meta.fileId : '',
      }
      if (inc.netAmount > 0 && inc.amount > 0) inc.taxRate = Math.round(((inc.amount - inc.netAmount) / inc.netAmount) * 10000) / 100
      form.invoices.push(inc)
    })
  }
  /**
   * D-DEC-367（乙档·前端回显环节）：按已存附件补出「待补录」发票行。
   * 票面解析结果只在上传响应里回传一次（ExpenseAttachController 只把文件写进 exp_bill_attach，
   * 无 ext_json 承载列），而「没勾选就不生成明细行」的单子行 extJson 里也没有 invoice——
   * 于是编辑重提重开表单时「已解析发票」整块空着，附件条与 image_status=COMPLETE 却都在，提交照样放行。
   * 这里给每个尚未在清单里的 INVOICE 附件补一行手工态（parsed=false / checked=false：
   * 不自动生成明细行、不改合计，票面由人补全），把「暂无发票」换成「有票可看、可补」。
   * inv_bill_invoice 关联写环节在提交服务端路径，本轮未动。
   */
  function restoreInvoiceRowsFromAttaches() {
    const known = new Set(form.invoices.map((x) => String(x.fileId || '')))
    ;(form.attaches || []).forEach((a) => {
      if (!a || a.attachType !== 'INVOICE') return
      const fid = a.fileId == null ? '' : String(a.fileId)
      if (fid && known.has(fid)) return
      if (fid) known.add(fid)
      const inc = newInvoice()
      inc.fileId = a.fileId || null
      inc.parsed = false
      inc.checked = false
      inc.sourceName = a.fileName || (fid ? '#文件' + fid : '')
      form.invoices.push(inc)
    })
  }
  // 合计含发票生成的明细行（可能暂无费用项 itemId，但金额已确定）
  function sumAmount() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0) }
  function sumTax() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.taxAmount || 0), 0) }

  // 预算占用预览：按预算科目聚合本次申请金额
  // D-BUD-1：b 未加载（自动映射科目不在前 200 条预载内）时按 avail 未知处理 → fail-open 不误拦；
  // 由下方 watch 按 id 精确回填真实额度后再重新判定。
  const budgetPreview = computed(() => {
    const map = {}
    // 多币种折算(BUG-03)：预算以本币(CNY)规则存储，非 CNY 明细按汇率折算后参与预算比较
    const rateOf = (l) => (String(form.bill.currencyCode || 'CNY').toUpperCase().startsWith('CNY') ? 1 : Number(form.bill.exchangeRate || l.exchangeRate || 1) || 1)
    form.lines.filter((l) => l.budgetItemId).forEach((l) => {
      const conv = String(form.bill.currencyCode || 'CNY').toUpperCase().startsWith('CNY')
        ? 1
        : (String(l.currencyCode || '').toUpperCase().startsWith('CNY') ? 1 : rateOf(l))
      map[l.budgetItemId] = (map[l.budgetItemId] || 0) + Number(l.amount || 0) * conv
    })
    return Object.keys(map).map((bid) => {
      const b = budgets.value.find((x) => String(x.id) === String(bid))
      const loaded = !!b
      const b0 = b || {}
      const total = Number(b0.totalAmount || 0)
      const adjust = Number(b0.adjustAmount || 0)
      const occupy = Number(b0.occupyAmount || 0)
      const used = Number(b0.usedAmount || 0)
      const avail = total + adjust - occupy - used
      const used2 = map[bid]
      // D-P2-02：额度取不到时不能一片空白还标"可支"——按未知显示，避免假绿
      const label = loaded ? budgetLabel(items.value, depts.value, costCenters.value, b0, { companies: companies.value, projects: projects.value })
        : budgetUnseenLabel(bid)
      // D-DEC-372：控制方式随 /budget/item/pick 行带出（BudgetService.controlTypeOf 同一规则），缺失按 HARD fail-closed
      return { label, used: used2, available: loaded ? avail : null, over: loaded && used2 > avail, unknown: !loaded, control: b0.controlType || 'HARD' }
    })
  })
  const budgetWarn = computed(() => budgetPreview.value.filter((x) => x.over))

  // D-BUD-1：引用到的预算科目若不在 budgets（前 200 条预载）内，按 id 精确回填真实额度。
  // budgetMissingKey 依赖 budgets.value —— loadMd 会用 splice 整体替换数组，替换后若把回填项
  // 冲掉，该 key 会变、watcher 再补取，最终收敛；in-flight 集合去重并发请求，不永久标记「已试过」。
  const budgetMissingKey = computed(() => {
    const loaded = new Set(budgets.value.map((x) => String(x.id)))
    const miss = new Set()
    form.lines.forEach((l) => {
      const id = l.budgetItemId
      if (id != null && id !== '') { const s = String(id); if (/^\d+$/.test(s) && !loaded.has(s)) miss.add(s) }
    })
    return [...miss].sort().join(',')
  })
  const budgetFetching = new Set()
  watch(budgetMissingKey, (key) => {
    if (!key) return
    const missing = key.split(',').filter((id) => !budgetFetching.has(id))
    if (!missing.length) return
    missing.forEach((id) => budgetFetching.add(id))
    mdApi.budgets({ pageNum: 1, pageSize: missing.length, ids: missing.join(',') })
      .then((res) => {
        ;((res && res.list) || []).forEach((it) => {
          if (it && !budgets.value.some((x) => String(x.id) === String(it.id))) budgets.value.push(it)
        })
      })
      .catch(() => { /* 失败静默：budgetPreview 对该项 fail-open 不误拦 */ })
      .finally(() => { missing.forEach((id) => budgetFetching.delete(id)) })
  }, { immediate: true })

  function statusType(s) { return _statusType(s) }
  function statusLabel(s) { return _statusLabel(s) }
  function billTypeLabel(t) {
    return { LOAN: '借款', REPAY: '还款', APPLY: '事前申请', CLAIM: '报销', PAY: '付款' }[t] || t || ''
  }
  function billSubTypeLabel(s) {
    return { LOAN: '借款', REPAY: '还款', PREPAY: '预付款', SETTLE: '结算', TRAVEL: '差旅报销',
      TRAIN: '培训报销', CLAIM_TRAVAL: '差旅报销', CLAIM: '日常报销', CORP: '对公付款',
      EXPENSE: '费用报销', FUND: '资金', AR: '应收', AP: '应付', AUDIT: '审计' }[s] || s || ''
  }
  function trafficTypeLabel(t) {
    return { RAIL: '火车', AIR: '飞机', ROAD: '汽车', HOTEL: '住宿', OTHER: '其他' }[t] || t || ''
  }

  // ---------------- 加载主数据 ----------------
  // 单项失败不拖垮其它基础数据（留空即可），mdLoadFailed 供提交时给出明确提示
  const mdLoadFailed = ref(false)
  // 就地填充（保留数组实例）：BillWorkbench 以 v-bind 把 useBill 展开传给插槽，ref 在首帧被
  // 解包快照成空数组实例，替换实例会让页面下拉永远为空；必须 splice 原数组
  const fillArr = (refArr, list) => { refArr.splice(0, refArr.length, ...(list || [])) }
  /**
   * 按单据费用年度取预算行（D-H5M-06）。
   * 不按年取时前 200 行被最早年度占满（bg_item 排序＝budget_year/budget_period/dim_key 升序），
   * 于是 2026 年的单会命中 2025 年预算行，额度与超支提示全部失真。
   * 取不到＝额度未知，不阻断填写，也不更新年度（下次还会重试）。
   *
   * seq 守卫：年度快速来回改时，后发的请求可能先回来，不守卫就会出现"池是 A 年、字段是 B 年"。
   */
  let budgetPoolSeq = 0
  async function loadBudgetPool(year) {
    const y = year ?? null
    if (String(budgetPoolYear.value) === String(y)) return
    const seq = ++budgetPoolSeq
    const res = await mdApi.budgets({ pageNum: 1, pageSize: 200, budgetYear: y ?? undefined }).catch(() => null)
    if (res === null || seq !== budgetPoolSeq) return
    fillArr(budgets.value, (res && res.list) || [])
    budgetPoolYear.value = y
  }
  /** 明细行的预算行按当前年度与费用项重解析（规则与移动端同源，见 utils/budget.js） */
  function resolveLineBudgets() {
    form.lines.forEach((l) => {
      l.budgetItemId = resolveLineBudgetId(l, {
        pool: budgets.value, map: itemBudgetMap.value,
        companyId: form.bill.companyId, deptId: form.bill.deptId,
        budgetYear: form.bill.periodYear,
      })
    })
  }
  // 费用年度一变，预算池与每行的预算行都要跟着变（D-H5M-06）：
  // 池是按年取的，不重取就会"池是上一年、行是这一年"，所有行都命中不到年度行。
  watch(() => form.bill.periodYear, async (y) => {
    await loadBudgetPool(y)
    resolveLineBudgets()
  })
  async function loadMd() {
    const safe = (p) => p.then((v) => v ?? []).catch(() => null)
    const [it, bg, ct, py] = await Promise.all([
      safe(mdApi.expenseItems()),
      safe(mdApi.budgets({ pageNum: 1, pageSize: 200, budgetYear: form.bill.periodYear ?? undefined })),
      safe(mdApi.cities({ pageNum: 1, pageSize: 2000 })),
      safe(mdApi.payees({ pageNum: 1, pageSize: 200 })),
    ])
    if (it !== null) fillArr(items.value, (it || []).filter((x) => x && x.itemName && x.itemName.trim()))
    if (bg !== null) { fillArr(budgets.value, (bg && bg.list) || []); budgetPoolYear.value = form.bill.periodYear ?? null }
    if (ct !== null) fillArr(cities.value, (ct && ct.list) || [])
    if (py !== null) fillArr(payees.value, (py && py.list) || [])
    mdLoadFailed.value = it === null || bg === null || ct === null || py === null
    mdLoadFailed.value = it === null || bg === null || ct === null || py === null
    // 主数据引用并行加载，避免串行 await 拖慢单据页挂载、缩短「值→中文名」的标签映射窗口。
    // 各加载项独立失败互不影响（失败置空，表单仍可填写）。
    const __fill = async (fn, onErr) => { try { await fn() } catch (e) { if (onErr) onErr() } }
    await Promise.all([
      __fill(async () => fillArr(contracts.value, ((await contractApi.page({ pageNum: 1, pageSize: 200 })) || {}).list || []), () => fillArr(contracts.value, [])),
      // D-V4R-96：往来单位候选必须覆盖供应商。
      // 原先只读 /customer/page（crm_customer 客户档案），而供应商在 md_supplier、两表零同步，
      // 现役库 crm_customer 近乎空表 ⇒ 对公结算下拉里一个供应商都没有，
      // SETTLE 单据挂不上 customer_id/po_id/po_receipt_id，三单匹配环节断裂、根本提交不了。
      // 改用 /customer/candidates：后端把供应商按 supplier_code 去重建档为往来单位档案，
      // 返回的 id 是语义正确的 crm_customer.id（下游应收核销/坏账/对账都按这个解析），
      // 并补出 accountNo/bankName（原档案表无账号字段，导致收款账号必填永远填不上）。
      __fill(async () => {
        const r = (await customerApi.candidates({ limit: 300 })) || {}
        const list = Array.isArray(r) ? r : (r.data || r.list || [])
        fillArr(customers.value, list)
      }, () => fillArr(customers.value, [])),
      __fill(async () => fillArr(costCenters.value, ((await mdApi.costCenters({ pageNum: 1, pageSize: 200 })) || {}).list || []), () => fillArr(costCenters.value, [])),
      __fill(async () => fillArr(projects.value, ((await mdApi.projects({ pageNum: 1, pageSize: 200 })) || {}).list || []), () => fillArr(projects.value, [])),
      // FINAL-01: 费用类型→预算科目映射(选费用类型自动带出预算科目；失败不影响主数据)
      __fill(async () => { const maps = (await masterCfgApi.itemBudgetMap.options()) || []; const m = {}; maps.forEach((x) => { if (x?.itemId != null && x?.budgetItemId != null) m[String(x.itemId)] = x.budgetItemId }); itemBudgetMap.value = m }, () => { itemBudgetMap.value = {} }),
      // 公司树：任登录用户可读；扁平化用于 公司 下拉。
      __fill(async () => { const compTree = (await orgApi.companyTree({ silent: true })) || []; const compFlat = []; const walkComp = (arr) => { arr.forEach((n) => { if (n) { compFlat.push({ id: n.id, name: n.companyName || n.name || '' }); if (n.children?.length) walkComp(n.children) } }) }; walkComp(compTree); fillArr(companies.value, compFlat) }, () => fillArr(companies.value, [])),
      // 部门树：各相关角色皆可读（不套 system:dept:list 管理权限），否则业务角色部门下拉为空。
      __fill(async () => { const deptTree = (await orgApi.deptTree({ silent: true })) || []; const deptFlat = []; const walkDept = (arr) => { arr.forEach((n) => { if (n) { deptFlat.push({ id: n.id, name: n.deptName || n.name || '', companyId: n.companyId }); if (n.children?.length) walkDept(n.children) } }) }; walkDept(deptTree); fillArr(depts.value, deptFlat) }, () => fillArr(depts.value, [])),
    ])
  }

  async function loadBills() {
    const p = { pageNum: 1, pageSize: 50, billType: filterType.value }
    if (subType && filterType.value === billType) p.billSubType = subType
    bills.value = (await billApi.myPage(p)).list || []
    // 列表刷新后把选中行换成新读到的行对象，否则顶部流程进度停在提交/审批前的旧快照
    if (selected.value?.id) focusRow(selected.value.id)
  }
  function focusRow(id) {
    const r = bills.value.find((x) => String(x.id) === String(id))
    if (r) selected.value = r
    return r
  }

  // ---------------- 附件 ----------------
  async function onAttachChange(e) {
    const file = e.target.files && e.target.files[0]
    if (!file) return
    uploading.value = true
    try {
      const rec = await fileApi.upload(file, 'INVOICE', form.bill.id || null)
      // D-V4R-44：同名查重（fileId 每次上传都是新值，按它查重防不住同一文件重复上传）
      if (form.attaches.some((a) => (a.fileName || '') === (rec.fileName || ''))) {
        ElMessage.warning(`附件「${rec.fileName}」已在本单据中，已跳过重复上传`)
        return
      }
      // D-V4R-44 附带修复：原实现整表覆盖（form.attaches = [{...}]），多附件场景互相踩掉，改为追加
      // 类型按扩展名定性（原先无条件写 INVOICE，Office 附件也被当成发票，后续"必须有发票附件"的
      // 校验会被假发票顶掉，附件列表还会给它挂「待识别」）
      form.attaches = [...form.attaches, { fileId: rec.id, fileName: rec.fileName, attachType: isInvoiceFile(rec.fileName) ? 'INVOICE' : 'OTHER' }]
      ElMessage.success(`附件已上传：${rec.fileName}`)
    } catch (err) {
      ElMessage.error('上传失败：' + err.message)
    } finally {
      uploading.value = false
      e.target.value = ''
    }
  }

  /** UploadButton 组件上传完成回调 */
  function onAttachUploaded(rec) {
    if (!rec) return
    const attach = { fileId: rec.id, fileName: rec.fileName, attachType: rec.attachType === 'INVOICE' ? 'INVOICE' : 'OTHER' }
    // D-V4R-44：同名文件查重——fileId 每次上传都生成新值，旧守卫 find(fileId) 形同虚设，
    // 同一发票文件可重复添加 N 次全部写入数据库（实测 4 行同名 INVOICE 附件）。同名即拦截。
    if (form.attaches.some((a) => (a.fileName || '') === (attach.fileName || ''))) {
      ElMessage.warning(`附件「${attach.fileName}」已在本单据中，已跳过重复上传`)
      return
    }
    if (!form.attaches.find((a) => a.fileId === attach.fileId)) form.attaches = [...form.attaches, attach]
    if (rec.attachType === 'INVOICE' && rec.invoice) {
      const inc = newInvoice()
      inc.parsed = !!rec.invoice.ok
      inc.fileId = rec.id
      inc.invoiceNo = rec.invoice.invoiceNo || ''
      inc.invoiceDate = rec.invoice.invoiceDate || ''
      inc.invoiceType = rec.invoice.invoiceType || ''
      inc.sellerName = rec.invoice.sellerName || ''
      inc.sellerTaxNo = rec.invoice.sellerTaxNo || ''
      inc.primaryItemName = rec.invoice.primaryItemName || ''
      inc.amount = Number(rec.invoice.amount || 0)
      inc.taxAmount = Number(rec.invoice.taxAmount || 0)
      inc.netAmount = Number(rec.invoice.netAmount || 0)
      if (inc.netAmount > 0 && inc.amount > 0) inc.taxRate = Math.round(((inc.amount - inc.netAmount) / inc.netAmount) * 10000) / 100
      inc.sourceName = rec.fileName
      form.invoices = [...form.invoices, inc]
      // D-DEC-009：解析失败此前也弹绿条"已识别发票"，提单人会以为票面已入单、直接提交一张没有票面的报销单
      // D-DEC-461（10-05）：只看 ok 还不够——"识别成功"和"票进了发票池"是两件事，
      // 分档一律走 invoiceOutcome（判据在后端 invoice.inPool，此处不重算金额自洽）
      const out = invoiceOutcome(rec.invoice, { fileName: rec.fileName || '', end: 'pc' })
      ElMessage({ type: out.tone, duration: 6000, message: out.message })
    } else {
      ElMessage.success(`附件已上传：${rec.fileName}`)
    }
  }

  // ---------------- 提单选人（首审环节办理人，方案 G2-3） ----------------
  // 配置驱动：只有该流程首审节点配成 PICK 档（且灰度放行）时 mode 才是 PICK，页面才渲染下拉；
  // POOL 档只显示"任务池抢单"提示，AUTO/未部署/老后端一律 mode=AUTO（不渲染、不阻断提交）。
  const firstPick = reactive({
    loading: false, mode: 'AUTO', nodeKey: '', nodeName: '',
    required: false, candidates: [], userId: null,
  })
  async function loadFirstPick(billId) {
    if (!billId) return
    firstPick.loading = true
    try {
      const d = (await wfApi.startCandidates({
        bizType: 'EXP_' + billType,
        bizId: billId,
        subType: form.bill.billSubType || subType || undefined,
        amount: sumAmount() || undefined,
      }, { silent: true })) || {}
      firstPick.mode = d.mode || 'AUTO'
      firstPick.nodeKey = d.nodeKey || ''
      firstPick.nodeName = d.nodeName || ''
      firstPick.required = !!d.required
      firstPick.candidates = Array.isArray(d.candidates) ? d.candidates : []
      // 候选唯一时后端已把 required 置 false：这里直接带出那个人，省掉一次无意义的单选
      if (firstPick.candidates.length === 1) {
        firstPick.userId = firstPick.candidates[0].userId
      } else if (!firstPick.candidates.some((c) => c.userId === firstPick.userId)) {
        firstPick.userId = null
      }
    } catch (e) {
      // 端点缺失/网络异常：降级为不显示选人区，绝不让提单因此提交不了
      firstPick.mode = 'AUTO'
      firstPick.required = false
      firstPick.candidates = []
      firstPick.userId = null
    } finally {
      firstPick.loading = false
    }
  }
  // 公司/金额变了＝流程可能换链、候选人可能换公司，之前选的人未必还在名单里
  // 用「多源」形式而非返回数组的单源：后者每轮都是新数组，任何一次改数都会重新拉一遍候选人
  watch([() => form.bill.companyId, () => sumAmount()], () => {
    if (form.bill.id && (firstPick.mode === 'PICK' || firstPick.mode === 'POOL')) loadFirstPick(form.bill.id)
  })

  // ---------------- 保存 / 提交 ----------------
  /**
   * D-DEC-023：行级 ext_json 写入数据库前的最后一道对齐。
   * 招待三项（接待对象/接待人数/人均标准）在界面上绑的是 exp_bill_line 的真实列
   * （attendees / person_count），但审批侧与详情页按 ext_json 查询最稳，
   * 因此保存时把 三项 + 人均标准 一并镜像进 ext_json，杜绝「界面填了、库里 {}」。
   */
  function lineExtForSave(l) {
    const ext = (l.extJson && typeof l.extJson === 'object') ? { ...l.extJson } : {}
    const pc = Number(l.personCount || 0)
    const amt = Number(l.amount || 0)
    if (l.attendees != null && String(l.attendees).trim() !== '') ext.entertainTarget = l.attendees
    if (pc >= 1) {
      ext.entertainCount = pc
      if (amt > 0) ext.perCapita = Math.round((amt / pc) * 100) / 100
    }
    // 差旅报销明细另有五栏在 exp_bill_line 没有列（费用类别/房间数/出差止日/出发地/目的地，工厂见 ClaimTravel.vue:296）：
    // 不镜像就是"界面填了、库里 {}"，且重载后 category 丢失会让住宿行的那组列整组换成交通行的列。
    for (const k of ['category', 'roomCount', 'endDate', 'fromCityId', 'toCityId']) {
      if (l[k] !== undefined && l[k] !== null && l[k] !== '') ext[k] = l[k]
    }
    return ext
  }
  async function saveDraft(extra) {
    try {
      // 修复：发票勾选联动生成的明细行在未选「费用类型」时（item_id 数据库 NOT NULL），
      // 随草稿一起提交会导致后端整单 500（表头一并回滚，用户看到"勾选后没反应/保存不了"）。
      // 因此只提交已选费用类型的行；未选行保留在页面上并给出明确提示，补选后再保存即可。
      const pendingItemLines = form.lines.filter((l) => !l.itemId && Number(l.amount || 0) > 0)
      const extForSave = form.bill.extJson || {}
      const payload = {
        bill: {
          ...form.bill,
          totalAmount: submittedTotal(form.lines),
          // D-DEC-368（前端载荷环节）：界面「账户名称」绑的是 ext_json.accountName，而付款行收款人取
          // exp_bill.payee_name（服务端两处都先读 bill.payeeName，见 ExpenseWfEventListener
          // createLoanDisbursementPayment:924 / createClaimPayment:1294）——不镜像则户名只活在 ext_json、
          // payee_name 恒空串，出纳与付款批次都拿不到户名。已有值（收款方下拉选中/库里已存）一律不覆盖。
          payeeName: form.bill.payeeName || extForSave.accountName || '',
          extJson: JSON.stringify(extForSave),
        },
        lines: form.lines.filter((l) => l.itemId).map((l) => ({
          ...l,
          // P1 缺陷修复：行级 extJson 若为对象，序列化为字符串，避免 saveDraft 反序列化失败(HTTP 500)
          extJson: typeof l.extJson === 'string' ? l.extJson : JSON.stringify(lineExtForSave(l)),
        })),
        attaches: form.attaches,
      }
      if (extra) Object.assign(payload, extra)
      const saved = await billApi.saveDraft(payload)
      // R5-D-054：saveDraft 只回主键（后端 Result<Long>）时单号不回显——「单据编号」输入框
      // （ClaimExpense.vue 绑 form.bill.billNo）与页首流程进度卡卡头都读不到新单号，保存成功后
      // 仍显占位文案/「单据 #id」。这里把 id 落表单后按 id 回读一次 billNo 回填进同一 form.bill
      // （进度卡 props 同步更新）；响应若已直带 billNo（后端升级响应形态）则不再多发一跳。
      const id = (saved && typeof saved === 'object') ? (saved.id ?? saved.billId ?? form.bill.id ?? null) : saved
      form.bill.id = id
      if (saved && typeof saved === 'object' && saved.billNo) {
        form.bill.billNo = saved.billNo
      } else if (id != null && !form.bill.billNo) {
        try {
          const d = await billApi.detail(id, { silent: true })
          if (d && d.bill && d.bill.billNo) form.bill.billNo = d.bill.billNo
        } catch (e) { /* 回读失败不吞保存结果：toast 照常，单号可在「我的单据」列表查得 */ }
      }
      if (opts.travel && form.travels.length) {
        await detailApi.saveTravels(id, form.travels)
      }
      // D-V4R-42：分摊表 exp:share:* 只授予财务/共享/审批类角色（普通员工不现有），原先无条件调用
      // ⇒ 员工每次保存草稿都吃一发 code:4030 红字、分摊行零写入数据库。写权限都没有时就不发这一跳：
      // 承担规则的权威值本就落在 exp_bill_line（成本中心/项目/预算科目列），分摊行至今无任何消费方
      // （见 D-V4R-35：ExpBillShare 全仓只有 CRUD 四个引用点）⇒ 少写这几行不影响预算占用、核销与凭证。
      if (form.shares.length && hasPerm('exp:share:update')) await detailApi.replaceShares(id, form.shares)
      for (const o of form.loanOffsets) {
        await detailApi.addLoanOffset(id, { ...o, billId: id })
        // E2E-001 修复：冲借款同步建立 LOAN_CLAIM 关系（source=借款单、target=报销单），
        // 使审批通过时 handleLoanClaimDiff 能识别借款关联并计算差额补付而非全额付款
        await detailApi.addRelation(o.loanBillId, id, 'LOAN_CLAIM', o.offsetAmount).catch(() => {})
      }
      for (const r of form.relations) {
        await detailApi.addRelation(r.sourceBillId, id, r.relationType || 'APPLY_CLAIM', r.useAmount)
      }
      ElMessage.success('草稿已保存')
      // 明确告知哪些勾选的发票行未随草稿保存（未选费用类型），避免"勾选了却没存上"的困惑
      if (pendingItemLines.length) {
        ElMessage.warning(`有 ${pendingItemLines.length} 行发票明细未选择费用类型，本次保存未包含；请在费用明细中补选费用类型后再次保存`)
      }
      loadBills()
      loadFirstPick(id)
      return id
    } catch (e) { ElMessage.error(e.message); throw e }
  }
  const submitting = ref(false)
  // 提交被拦时，「原因＋下一步动作」要能被结果面板常驻显示；只弹一次即消失的 toast 等于没告知。
  const submitHint = ref('')
  function blockSubmit(msg) { submitHint.value = String(msg || ''); return submitHint.value }
  async function submit() {
    if (submitting.value) return
    submitting.value = true
    submitHint.value = ''
    try {
      // 表头必填校验（登记表规则，见 constants/billRequired.js）：与页面红星同源，缺项不进合规预检
      // 子类型取单据上的实时值——页面的 subType 切换只回写 form.bill.billSubType
      const miss = missingBillField(form.bill, requiredOf(billType, form.bill.billSubType || subType))
      if (miss) {
        ElMessage.warning(blockSubmit(miss.tip))
        return
      }
      if (!form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).length) {
        // 借款/还款的明细行由金额自动生成，费用项取自基础数据；取不到通常是基础数据没加载出来，文案要与手工场景区分
        const label = { LOAN: '借款明细', REPAY: '还款明细' }[billType] || '费用明细'
        ElMessage.warning(blockSubmit(items.value.length
          ? `请至少添加一条${label}`
          : `基础数据（费用项目）加载失败，无法生成${label}，请刷新重试或联系管理员`))
        return
      }
      // D-DEC-372：校验分档与服务端 occupy 同一规则——HARD 超预算拦死；SOFT 须填超支理由（写 ext_json.overrunReason，
      // 服务端 ExpenseBillService 同列校验）；WARN 只提醒放行。原实现不分控制方式一律"无法提交"，
      // 预警/软控科目被前端越权拦死，且软控理由没有任何 UI 写口。
      if (budgetWarn.value.length) {
        const hard = budgetWarn.value.filter((x) => (x.control || 'HARD') === 'HARD')
        const soft = budgetWarn.value.filter((x) => x.control === 'SOFT')
        if (hard.length) {
          ElMessage.error(blockSubmit('预算超出可用额度（硬控方案），无法提交：请调减本单金额，或到「预算管理 · 预算调整」为该科目追加额度后重报'))
          return
        }
        if (soft.length) {
          const saved = String(form.bill.extJson?.overrunReason || '').trim()
          let reason = saved
          if (!reason) {
            try {
              // D-V5R-09（F-run-1017b 实测）：inputType 显式改 textarea——ElMessageBox.prompt 默认渲染
              // 单行 input，超支理由是长文本（要写清"为什么超、调剂规则"），单行既不好填也不好读；
              // 且自动化/无障碍定位按 textarea 找，单行 input 让检查脚本永远抓不到输入框。
              const r = await ElMessageBox.prompt(
                `${soft.length} 项预算超出可用额度，当前为软控方案：请填写超支理由（随单据记录，服务端按同一规则校验）`,
                '软控超支·需填写理由',
                {
                  type: 'warning',
                  inputType: 'textarea',
                  inputPlaceholder: '请填写超支原因与调整方式（必填，随单据记录）',
                  inputValidator: (v) => (v && v.trim() ? true : '超支理由不能为空'),
                })
              reason = String(r.value || '').trim()
            } catch { return } // 取消＝放弃本次提交
            form.bill.extJson = { ...(form.bill.extJson || {}), overrunReason: reason }
          }
        }
        const warn = budgetWarn.value.filter((x) => x.control === 'WARN')
        if (warn.length && !soft.length) {
          ElMessage.warning(`有 ${warn.length} 项预算超出可用额度（预警方案），本次放行、占用流水记录`)
        }
      }
      // D-B4-01：「必附发票」原本是服务端合规预检的判据（PrecheckService 数 exp_bill_attach 的 INVOICE 行），
      // 而预检入参是 billId ⇒ 必须先落草稿才能预检，表现为"草稿已保存"＋"提交被拦截"两条互相打脸的提示。
      // 这里把同一判据前移到写库之前，只覆盖费用项侧（政策侧仍由服务端默认处理），判据不宽于服务端，不会误拦。
      if (billType === 'CLAIM' && !form.attaches.some((a) => a.attachType === 'INVOICE')) {
        const needInvoiceItem = form.lines.filter((l) => l.itemId).some((l) => {
          const it = items.value.find((x) => x.id === l.itemId)
          return it && Number(it.needInvoice) === 1
        })
        if (needInvoiceItem) {
          ElMessage.error(blockSubmit('所选费用项要求必须附发票：请用「选择文件」上传发票影像后再提交（「新增发票行」手工录入只登记票面信息，不构成发票附件）'))
          return
        }
      }
      const id = await saveDraft()
      // 提单选人（G2-3）：草稿写入数据库后按"真实提交同一规则"重取首审候选人（金额/公司可能刚改过），
      // 需要点名而用户没选时拦在提交前——后端此时不会拒绝（点名是可选的），拦在页面上才不会
      // 出现"提交成功但单子流到了我没选的人"。
      await loadFirstPick(id)
      if (firstPick.mode === 'PICK' && !firstPick.candidates.length) {
        ElMessage.error(blockSubmit(`首审环节「${firstPick.nodeName || '第一道审批'}」在本单据公司下暂无可选办理人，请先在「系统管理 · 角色授权」补岗`))
        return
      }
      if (firstPick.mode === 'PICK' && firstPick.required && !firstPick.userId) {
        ElMessage.warning(blockSubmit(`请在单据页指定首审环节「${firstPick.nodeName || '第一道审批'}」的办理人后再提交`))
        return
      }
      // 合规预检（风险前置）：一次性展示全部不合规项，FAIL 拦截、WARN 确认后继续
      const checks = (await complianceApi.precheck(id)) || []
      const fails = checks.filter((c) => c.level === 'FAIL')
      const warns = checks.filter((c) => c.level === 'WARN')
      if (fails.length) {
        const detail = fails.map((c) => ' ' + c.msg)
        // ⑤-Ⅳa：服务端只说「当前未上传」，看不出下一步动作。预检入参是 billId ⇒ 附件行必须随草稿写入数据库
        // 才被数到（上传时单据还没 id，附件行是挂在 billId=NULL 的孤儿行上的），故这里补可执行动作。
        if (fails.some((c) => c.code === 'INVOICE_REQUIRED' || c.code === 'APPLY_ATTACH_REQUIRED')) {
          detail.push('下一步：请用「选择文件」上传发票影像，并点一次「保存草稿」把附件一并保存，再重新提交')
        }
        ElMessageBox.alert(blockSubmit(detail.join('\n')),
          `提交被拦截（${fails.length} 项不合规）`, { type: 'error', confirmButtonText: '知道了' })
        return
      }
      if (warns.length) {
        try {
          await ElMessageBox.confirm(warns.map((c) => ' ' + c.msg).join('\n'),
            '合规提示（可继续提交）', { type: 'warning', confirmButtonText: '仍然提交', cancelButtonText: '返回修改' })
        } catch (e) { return }
      }
      await billApi.submit(id, null, firstPick.mode === 'PICK' ? firstPick.userId : null)
      ElMessage.success('已提交，等待审批')
      activeTab.value = 'my'
      await loadBills()
      // 提交后把选中行指向这张单，顶部流程进度立刻显示「已提交/当前节点」，不用手点刷新
      focusRow(id)
    } catch (e) {
      const msg = String(e?.message || '')
      // 默认处理（软控超支理由）：服务端是预算校验的权威。
      // 前端 budgetPreview 只聚合 lines 中 budgetItemId 非空的金额；出差申请等场景该字段在提交前尚未
      // 解析 ⇒ 预览为空 ⇒ 上方 SOFT 弹窗永不触发 ⇒ 直接提交被服务端拒。
      // 这里捕获「需填写超支理由」报错：弹窗补填 → 随草稿写入数据库 → 自动重试一次提交。
      if (msg.includes('需填写超支理由')) {
        try {
          const r = await ElMessageBox.prompt(
            '本单超出预算可用额度，当前为软控方案：请填写超支理由（随单据记录，服务端按同一规则校验）',
            '软控超支 · 需填写理由',
            {
              type: 'warning',
              inputType: 'textarea',
              inputPlaceholder: '请填写超支原因与调整方式（必填，随单据记录）',
              inputValidator: (v) => (v && v.trim() ? true : '超支理由不能为空'),
            })
          const reason = String(r.value || '').trim()
          if (!reason) return
          form.bill.extJson = { ...(form.bill.extJson || {}), overrunReason: reason }
          await saveDraft()   // 让 extJson 随单据写入数据库（服务端 occupy 读 bill.ext_json.overrunReason）
          const rid = form.bill.id
          await billApi.submit(rid, null, firstPick.mode === 'PICK' ? firstPick.userId : null)
          ElMessage.success('已提交，等待审批')
          activeTab.value = 'my'
          await loadBills()
          focusRow(rid)
          return
        } catch (e2) {
          // 用户取消不提示；重试仍失败则回显真实原因
          const m2 = String((e2 && e2.message) || '')
          if (m2 && m2 !== 'cancel') ElMessage.error(blockSubmit(m2))
          return
        }
      }
      // 提交链路任何一步失败（合规预检/反欺诈拦截/收款账号校验/网络异常）都必须有可见错误提示，
      // 不允许把失败当成功流程继续。saveDraft 内部已提示过一次，这里同文案会被全局去重，不会双弹。
      ElMessage.error(blockSubmit(msg || '提交失败，请稍后重试'))
    } finally { submitting.value = false }
  }
  // 编辑草稿 / 驳回单编辑重提：把已有单据载入表单（保留 id，saveDraft 即更新，submit 走新流程实例）
  async function loadBillForEdit(row) {
    try {
      let d = await billApi.detail(row.id)
      // D-V4R-79：撤回单（WITHDRAWN）与驳回单同轨——先转 DRAFT 并解除旧实例关联再载入，
      // 否则前端拿到的仍是 WITHDRAWN，提交会被服务端拒；顺带消掉「撤回后无法重提」的死单。
      if ((d.bill || {}).status === 'REJECTED' || (d.bill || {}).status === 'WITHDRAWN') {
        await billApi.editForResubmit(row.id)
        d = await billApi.detail(row.id)
      }
      const bl = d.bill || {}
      let ext = {}
      try { ext = typeof bl.extJson === 'string' ? JSON.parse(bl.extJson || '{}') : (bl.extJson || {}) } catch { ext = {} }
      form.bill = { ...form.bill, ...bl, id: bl.id, extJson: ext }
      if (Array.isArray(d.lines) && d.lines.length) {
        form.lines = d.lines.map((l) => {
          const ex = typeof l.extJson === 'string' ? (() => { try { return JSON.parse(l.extJson || '{}') } catch { return {} } })() : (l.extJson || {})
          // 与 lineExtForSave 成对：那五栏只活在 ext_json 里，抬回行对象才看得见（房间数/止日/城市/类别）
          const lifted = {}
          for (const k of ['category', 'roomCount', 'endDate', 'fromCityId', 'toCityId']) if (ex[k] !== undefined) lifted[k] = ex[k]
          return {
            ...newLine(), ...l, id: null,
            // 数量/单价不随编辑回填（明细表格无此输入列）：留空则提交时跳过量价勾稽，避免"改了金额却改不了单价"的死锁
            quantity: null, unitPrice: null,
            extJson: ex,
            ...lifted,
          }
        })
      }
      // D-H5M-05/D-H5M-06：载入既有单据时，预算池按本单年度取、行上的预算行按「本单年度 + 行费用项」重解析。
      // 旧单带的是上一轮解析出来的行（可能是别的费用项/别的年度），直接沿用会把占用记到错的科目上。
      await loadBudgetPool(form.bill.periodYear)
      resolveLineBudgets()
      if (Array.isArray(d.attaches)) {
        form.attaches = d.attaches.map((a) => ({ ...a, id: null }))
      }
      // 明细行上的发票元信息（行 extJson.invoice）反查成「已解析发票」清单，否则重开草稿只剩明细、
      // 票面信息整块空白（与 copyBill 同一规则）
      rebuildInvoicesFromLines()
      // D-DEC-367：行上没有票面元信息时（发票上传了却从未勾选成明细行），再按 INVOICE 附件补出待补录行，
      // 让「编辑重提」看得见这张票，而不是显示「暂无发票」还照样放行提交。
      restoreInvoiceRowsFromAttaches()
      // 行程 / 关联事前申请 / 冲销借款各自独立成表，detail 不返回，必须逐表回读：
      // 关联丢了再提交会按申请单全额重复占用，被「累计超出事前申请金额」误拦。
      if (opts.travel) {
        form.travels = await detailApi.travels(bl.id).catch(() => []) || []
      }
      // D-V4R-34：分摊行也是独立成表、detail 不返回，这里原来漏读 ⇒ 编辑草稿时「分摊信息」整块空着，
      // 看不见也就改不了预算科目；科目已超预算的草稿于是永远出不了草稿态（库里 exp_bill_share 明明有行）。
      // 这跳的守卫已由 exp:share:list（分摊维护岗权限）改为 exp:bill:list（读单权限），与单据母表同一规则 ⇒ 无条件下发。
      form.shares = await detailApi.shares(bl.id).catch(() => []) || []
      const rels = await detailApi.relations(bl.id).catch(() => []) || []
      // LOAN_CLAIM 由「冲销借款」驱动，不回填进关联表，避免两条链路重复建关系
      form.relations = (rels || []).filter((r) => String(r.targetBillId) === String(bl.id) && r.relationType !== 'LOAN_CLAIM')
        .map((r) => ({ sourceBillId: r.sourceBillId, sourceBillNo: r.sourceBillNo || '', useAmount: Number(r.useAmount || 0), relationType: r.relationType }))
      const offs = await detailApi.loanOffsets(bl.id).catch(() => []) || []
      form.loanOffsets = (offs || []).map((o) => ({
        loanBillId: o.loanBillId, loanBillNo: o.loanBillNo, offsetAmount: Number(o.offsetAmount || 0), offsetType: o.offsetType || 'CLAIM',
        remark: o.remark || '',
      }))
      activeTab.value = 'create'
      ElMessage.success(`已载入 ${bl.billNo || '单据'}，修改后可直接提交审批`)
      loadBills()
      loadFirstPick(bl.id)
    } catch (e) { ElMessage.error(e?.message || '载入失败'); }
  }
  function resetForm() {
    form.bill = {
      id: null, billType, billSubType: subType, title: '',
      companyId: store.user?.companyId || null, deptId: store.user?.deptId || null,
      applyUserId: store.user?.userId ?? store.user?.id ?? null, applyUserName: store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '', agentUserId: null,
      applyDate: today(), periodYear: new Date().getFullYear(), currencyCode: 'CNY', exchangeRate: 1,
      payeeName: '', remark: '', contractId: null, customerId: null, extJson: {},
    }
    form.lines = [newLine()]
    form.travels = []
    form.shares = []
    form.loanOffsets = []
    form.relations = []
    form.attaches = []
    form.invoices = []
    firstPick.mode = 'AUTO'
    firstPick.candidates = []
    firstPick.userId = null
  }

  // ---------------- 冲借款 / 关联事前申请 ----------------
  // D-UI-01 / D-UI-03（2026-10-19 G1 幕真跑发现 + 全项目排查）：原实现点行即「按全额占用并关窗」——
  // 没有可发现入口、键盘不可达、误点无确认；更关键是金额硬取 row.loanBalance / row.totalAmount，
  // 只能全额：报销额小于借款额时差额被一并占掉，后续还款被后端「累计占用超出借款单金额」拦死；
  // 事前申请关联额同样硬取全额且入表后不可编辑（连删掉重选都做不到部分关联）。
  // 现统一到 CandidatePickerDialog：弹层内勾选 + 可改金额（支持部分冲抵/部分关联）+ 底部【确定】提交。
  // 本 composable 只做「把对话框产出的 [{ row, amount }] 落进表单」，勾选与金额交互都在组件里，
  // 金额合法性（> 0 且 ≤ 可冲抵余额）由 components/bill/candidateAmount.js 统一裁决。
  async function openLoanDialog() {
    // D-UI-03：改用候选面接口——它给的 availableBalance 已扣掉在途占用，
    // 与服务端放行校验同源（旧的 myPage 只给账面余额，默认勾全额必然被拒）。
    //  新建单时 form.bill.companyId 为空，原样传会被序列化成字符串 "null"，
    //   后端 @RequestParam Long companyId 转换失败返回 400，await 抛错把
    //   loanDialog.value = true 一起吞掉 ⇒ 表现为「点了『选择借款单冲抵』毫无反应」。
    //   故只在拿到有效 companyId 时才带该参数（后端 required=false，可省略）。
    const lp = { limit: 50 }
    const cid = form.bill.companyId
    if (cid !== null && cid !== undefined && cid !== '') lp.companyId = cid
    loanCandidates.value = (await detailApi.loanCandidates(lp)) || []
    loanDialog.value = true
  }
  /** @param items {{row: object, amount: number}[]} 组件已按金额校验校验过 */
  function confirmLoanPicks(items) {
    let added = 0
    for (const { row, amount } of items || []) {
      const exist = form.loanOffsets.find((o) => o.loanBillId === row.id)
      if (exist) {
        exist.offsetAmount = amount
      } else {
        form.loanOffsets.push({
          loanBillId: row.id, loanBillNo: row.billNo, offsetAmount: amount,
          maxAmount: Number(row.availableBalance ?? row.loanBalance ?? 0), offsetType: 'CLAIM',
        })
        added++
      }
    }
    if (added) ElMessage.success(`已加入 ${added} 笔借款冲抵`)
    loanDialog.value = false
  }
  async function openApplyDialog() {
    applyCandidates.value = (await billApi.myPage({ pageNum: 1, pageSize: 50, billType: 'APPLY', status: 'APPROVED' })).list || []
    applyDialog.value = true
  }
  /** D-UI-03：关联金额可改（支持部分关联），保留「关联后自动带入费用详情/分摊」的页面钩子 */
  function confirmApplyPicks(items) {
    let added = 0
    for (const { row, amount } of items || []) {
      const exist = form.relations.find((r) => r.sourceBillId === row.id)
      if (exist) {
        exist.useAmount = amount
      } else {
        form.relations.push({ sourceBillId: row.id, sourceBillNo: row.billNo, useAmount: amount })
        added++
      }
      // 关联申请单后自动带入费用详情/分摊（差旅培训报销等页面可覆写）
      if (opts.onApplyPick) opts.onApplyPick(row, form)
    }
    if (added) ElMessage.success(`已关联 ${added} 张事前申请`)
    applyDialog.value = false
  }

  // ---------------- 凭证 ----------------
  async function openVoucher(row) {
    selected.value = row
    if (!row.voucherNo) { ElMessage.info('该单据尚未生成凭证（需审批通过）'); return }
    activeTab.value = 'voucher'
    // D-DEC-379：原实现无 catch（接口报错只留控制台异常、界面停在空预览），
    // 且台账首页 50 条查不到时静默失明——空态文案还反向误导（让用户"去选带凭证号的单据"，可他已经选了）
    try {
      const list = (await sharingApi.voucherPage({ pageNum: 1, pageSize: 50 })).list || []
      const v = list.find((x) => x.billId === row.id) || list.find((x) => x.voucherNo === row.voucherNo)
      if (v) {
        Object.assign(voucher, v)
        voucherLines.value = await sharingApi.voucherLines(v.id)
      } else {
        voucher.id = null
        voucherLines.value = []
        ElMessage.warning(`凭证 ${row.voucherNo} 未出现在凭证台账首页，请到「凭证」台账按期间翻查`)
      }
    } catch (e) { if (e?.message) ElMessage.error(e.message) }
  }
  async function postVoucher() {
    try {
      await sharingApi.voucherPost(voucher.id)
      ElMessage.success('已记账')
      const list = (await sharingApi.voucherPage({ pageNum: 1, pageSize: 50 })).list || []
      const v = list.find((x) => x.id === voucher.id)
      if (v) Object.assign(voucher, v)
    } catch (e) { ElMessage.error(e.message) }
  }

  // ---------------- 详情抽屉（复制/关闭/终止） ----------------
  async function openDetail(row) {
    const d = await billApi.detail(row.id)
    detailData.bill = d.bill || {}
    detailData.lines = d.lines || []
    detailData.shares = d.shares || []
    detailData.travels = d.travels || []
    detailData.loanOffsets = d.loanOffsets || []
    detailData.relations = d.relations || []
    detailData.attaches = d.attaches || []
    detailData.wfTrace = []
    detailVisible.value = true
    // 异步加载审批轨迹（不阻塞抽屉打开）
    try {
      // silent：轨迹属辅助信息，无权限/无数据时静默（与 Approval.vue 懒加载同一规则）
      const res = await flowApi.historyPage({ bizId: row.id, bizType: row.billType, pageNum: 1, pageSize: 100 }, { silent: true })
      detailData.wfTrace = res?.list || []
    } catch (_) { /* 无权限或无数据时静默 */ }
  }
  async function copyBill(row) {
    try {
      const d = await billApi.detail(row.id)
      resetForm()
      Object.assign(form.bill, {
        title: (d.bill.title || '') + '（复制）',
        applyDate: today(),
        periodYear: new Date().getFullYear(),
        remark: d.bill.remark || '',
        extJson: d.bill.extJson ? (typeof d.bill.extJson === 'string' ? JSON.parse(d.bill.extJson) : d.bill.extJson) : {},
      })
      form.bill.billSubType = d.bill.billSubType || subType
      form.lines = (d.lines && d.lines.length ? d.lines : [{}]).map((l) => ({
        id: null, itemId: l.itemId, itemName: l.itemName, expenseDate: l.expenseDate || today(),
        amount: l.amount || 0, taxRate: l.taxRate || 0, taxAmount: l.taxAmount || 0, netAmount: l.netAmount || 0,
        budgetItemId: l.budgetItemId || null, stdAmount: l.stdAmount || null, overStd: l.overStd || 0, overReason: '', remark: l.remark || '',
        // D-DEC-023/373：复制单据时招待三项承载列（attendees / person_count / entertain_type）必须列进这份白名单，漏一项草稿就是空的
        attendees: l.attendees || null, personCount: l.personCount ?? null, entertainType: l.entertainType || null,
        // 保留行级发票元信息，供发票清单反查回填与继续勾选/调整
        extJson: (l.extJson && typeof l.extJson === 'string' ? l.extJson : JSON.stringify(l.extJson || {})),
      }))
      // 从已存明细行反查回填发票清单（勾选状态、金额、票面信息）
      rebuildInvoicesFromLines()
      if (opts.travel && d.travels && d.travels.length) form.travels = d.travels.map((t) => ({ ...t, id: null }))
      activeTab.value = 'create'
      ElMessage.success('已复制为草稿，请检查后保存')
    } catch (e) { ElMessage.error(e.message) }
  }

  // ---------------- 其它 ----------------
  async function voidBill(row) {
    try {
      await ElMessageBox.confirm('确认作废该草稿？此操作不可恢复。', '作废确认', { type: 'warning' })
      await billApi.voidBill(row.id)
      ElMessage.success('已作废'); loadBills()
    } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
  }
  // D-DEC-387（副环节）：撤回确认后到接口返回前按钮实际可连点，加防重入校验
  let withdrawBusy = false
  // D-DEC-387：撤回是不可逆动作（流程终止、不可复活），与作废/关闭同一规则必须先确认；
  // 原实现裸调 await 一旦服务端报错即未捕获异常，此处一并办结。
  async function withdraw(row) {
    if (withdrawBusy) return
    withdrawBusy = true
    try {
      await ElMessageBox.confirm('撤回后流程即终止，已办环节有记录，单据不可恢复到本次流程，确认撤回？', '撤回确认', { type: 'warning' })
      await billApi.withdraw(row.id)
      ElMessage.success('已撤回'); loadBills()
    } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
    finally { withdrawBusy = false }
  }
  /**
   * 催办（体检 E-10 / F1-5）：审批中的单据问一句"卡在哪了"。
   * 限频/鉴权都在服务端，命中限频要把原因显示出来——静默"催办成功"等于骗人。
   */
  async function urge(row) {
    if (!row?.procInstId) { ElMessage.warning('该单据尚未进入流程，无需催办'); return }
    try {
      const n = await wfApi.urgeInitiator(row.procInstId)
      ElMessage.success(`已催办 ${n || 1} 个环节的办理人`)
    } catch (e) { if (e?.message) ElMessage.error(e.message) }
  }
  async function closeApply(row) {
    try {
      await ElMessageBox.confirm('关闭后该申请单不可再生成报销单，并将释放已占用的预算，确认关闭？', '关闭确认', { type: 'warning' })
      await billApi.closeApply(row.id); ElMessage.success('已关闭，预算已释放'); loadBills()
    } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
  }
  async function terminateApply(row) {
    try {
      await ElMessageBox.confirm('终止后该申请单不能再被报销单关联，将释放未释放完毕的预算，确认终止？', '终止报销确认', { type: 'warning' })
      await billApi.terminateApply(row.id); ElMessage.success('已终止报销'); loadBills()
    } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
  }
  // 反审核（D-V3-135 配套 · 单据反操作链第二环）：APPROVED → DRAFT。
  // 前置校验（已制证/付款已执行/批次在途/存在有效勾稽）在服务层 fail-loud，此处透传文案。
  async function unaudit(row) {
    try {
      await ElMessageBox.confirm(
        '反审核后单据回到草稿态（预算占用按行回冲，WAITING 付款记录随之作废），修改后重新提交将走新流程实例。确认反审核？',
        '反审核确认', { type: 'warning' })
      await billApi.unaudit(row.id); ElMessage.success('已反审核，单据回到草稿态'); loadBills()
    } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
  }
  function exportCsv() {
    if (!bills.value.length) { ElMessage.warning('当前无数据可导出'); return }
    const cols = [
      { prop: 'billNo', label: '单号' }, { prop: 'billType', label: '类型' },
      { prop: 'billSubType', label: '子类' }, { prop: 'title', label: '标题' },
      { prop: 'totalAmount', label: '金额' }, { prop: 'status', label: '状态' }, { prop: 'voucherNo', label: '凭证号' },
    ]
    const header = cols.map((c) => c.label).join(',')
    const lines = bills.value.map((row) => cols.map((c) => {
      const v = row?.[c.prop]; if (v == null) return ''
      let s = String(v)
      if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
      return s
    }).join(','))
    const csv = '﻿' + [header, ...lines].join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `单据_${today()}.csv`
    a.click(); URL.revokeObjectURL(a.href)
    ElMessage.success('已导出 CSV')
  }

  onMounted(() => { loadMd(); loadBills() })

  return {
    // state
    activeTab, filterType, form, items, budgets, cities, payees, contracts, customers, costCenters, projects, companies, depts, bills,
    uploading, voucher, voucherLines, selected, loanDialog, applyDialog, loanCandidates, applyCandidates, submitHint,
    detailVisible, detailData, focusedBill,
    // builders
    newLine, newTravel, newShare,
    qtyPriceChange, qtyCheck,
    // invoicing：发票清单 → 明细行
    newInvoice, recalcInvoice, toggleInvoice, removeInvoice, syncInvoiceLine, invOf, rebuildInvoicesFromLines,
    // utils
    fmt, recalcLine, onItemChange, pickBudgetFor, sumAmount, sumTax, budgetPreview, budgetWarn, statusType, statusLabel, billTypeLabel, billSubTypeLabel, trafficTypeLabel, today, nowTs,
    // actions
    loadMd, loadBills, onAttachChange, onAttachUploaded, saveDraft, submit, resetForm, loadBillForEdit, submitting,
    firstPick, loadFirstPick,
    openLoanDialog, confirmLoanPicks, openApplyDialog, confirmApplyPicks,
    openVoucher, postVoucher, openDetail, copyBill, voidBill, withdraw, urge, closeApply, terminateApply, unaudit, exportCsv,
  }
}
