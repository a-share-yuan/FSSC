<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ $t('receivable.main.Receivables') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('receivable.main.Receivables2') }}</span>
        <span style="display:flex; gap:8px; margin-left:auto; align-items:center">
          <el-button size="small" @click="reload">{{ $t('common.refresh') }}</el-button>
        </span>
      </div>
    </template>

    <el-tabs v-model="tab">
      <!-- 应收单 -->
      <el-tab-pane label="应收单" name="receivable">
        <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
          <el-input v-model="kw" placeholder="单据号/往来单位" clearable style="width:220px" @keyup.enter="loadReceivable" />
          <el-button type="primary" @click="loadReceivable">查询</el-button>
          <el-button type="success" @click="openReceivable">按销售订单生成应收</el-button>
          <!-- 逾期批处理补「账期／截止日」入参。留空＝按系统当前日跑（旧行为）；
               业务期间晚于系统日（补跑历史／跑到未来账期）时必须填期间，否则到期日永不早于基准日、逾期集恒空。 -->
          <el-input v-model="overdueBaseline" placeholder="账期/截止日（202611 或 2026-11-30，留空＝当天）" clearable size="small" style="width:250px" />
          <el-button type="warning" @click="accrueOverdue">逾期坏账计提批处理</el-button>
      <el-button size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="receivables" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="denied ? '无权限查看：当前账号缺少应收单读权限，请联系管理员开通' : '暂无数据'">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="单据号" width="160"  sortable />
          <el-table-column label="往来单位" min-width="120">
            <template #default="{ row }">{{ customerName(row.customerId) }}</template>
          </el-table-column>
          <!-- 客户超额预警：高风险信用档（C/高风险）行展示橙色「信用预警」tag（仅展示，不阻断操作） -->
          <el-table-column label="信用等级" width="170" align="center">
            <template #default="{ row }">
              <template v-if="customerCreditLevel(row.customerId)">
                <el-tag :type="isHighRiskCustomer(row.customerId) ? 'danger' : 'success'" size="small">{{ customerCreditLevel(row.customerId) }}</el-tag>
                <el-tooltip v-if="isHighRiskCustomer(row.customerId)" content="该客户信用等级为高风险档，请关注回款与超额度风险" placement="top">
                  <el-tag type="warning" effect="dark" size="small" style="margin-left: 4px">信用预警</el-tag>
                </el-tooltip>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="batchNo" label="批次号" width="140"  sortable />
          <el-table-column prop="currencyCode" label="币种" width="70"  sortable />
          <el-table-column label="应收金额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="已收金额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.receivedAmount) }}</template>
          </el-table-column>
          <el-table-column label="应收余额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.remainAmount) }}</template>
          </el-table-column>
          <el-table-column prop="dueDate" label="到期日" width="110"  sortable />
          <el-table-column label="收款态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'DONE' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'warning'" size="small">
                {{ rcStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 单据态是「收款规则」（NONE 直译"未开始"），审批期间看不出流程在跑 ⇒
               后端 page() 按 wf_instance 投影一列流程态（未起流程/审批中/审批通过/已驳回/已撤销/已终止）。 -->
          <el-table-column label="流程态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="flowTagType(row.flowStatus)" size="small" effect="plain">
                {{ row.flowStatusText || '未起流程' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next" :total="recvTotal"
                       :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; loadReceivable() }" />
      </el-tab-pane>

      <!-- 收款核销 -->
      <el-tab-pane label="收款核销" name="receipt">
        <div style="display:flex; gap:8px; margin-bottom:10px">
          <el-button type="success" @click="openReceipt">登记收款单</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="receipts" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="收款单号" width="130"  sortable />
          <el-table-column label="单位" min-width="120">
            <template #default="{ row }">{{ customerName(row.customerId) }}</template>
          </el-table-column>
          <el-table-column label="收款金额" width="130" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <!-- ④：判据不能写成 row.status !== 'APPLIED'，库值名称表对不上（登记环节写的是 WRITTEN_OFF），
                   于是"已核销"行的「先进先出核销」按钮常驻可点＝重复核销的诱因；现按真实最终状态名称表隐藏。
                   后端 applyReceipt 同步改去重跳过（登记即冲减），二次点击也不会再冲一遍余额。 -->
              <el-button v-if="!receiptAppliedStatuses.includes(row.status)" link size="small" type="primary" @click="applyReceipt(row)">先进先出核销</el-button>
              <el-tag v-else size="small" type="success" effect="plain">登记时已核销</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 催收待办 -->
      <el-tab-pane label="催收待办" name="collect">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <el-input-number v-model="overdueDays" :min="0" size="small" />
          <!-- 与「应收单」tab 共用同一个账期基准日（逾期天数阈值 days 不是空集成因，已自纠）；
               打卡 OVERDUE 与本处生成待办必须同一把尺子，否则扫描与催收各按各的基准日。 -->
          <el-input v-model="overdueBaseline" placeholder="账期/截止日（202611 或 2026-11-30，留空＝当天）" clearable size="small" style="width:250px" />
          <el-button type="warning" @click="createOverdue">扫描逾期生成待办</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="collects" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="应收单号" min-width="180" show-overflow-tooltip  sortable />
          <el-table-column prop="dueDate" label="到期日" width="110"  sortable />
          <el-table-column prop="overdueDays" label="逾期天数" width="100"  sortable />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="contactName" label="联系人" width="100" show-overflow-tooltip  sortable />
          <!-- 乙：同人多岗只做复核提示，不拦办结——提示必须在界面看得见才算控制点 -->
          <el-table-column label="复核" width="80" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="row.multiRoleWarning" :content="row.multiRoleWarning" placement="top">
                <el-tag size="small" type="warning">同人多岗</el-tag>
              </el-tooltip>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status !== 'RESOLVED'" link size="small" type="primary" @click="solveCollect(row)">解决</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 坏账计提 -->
      <el-tab-pane label="坏账计提" name="badDebt">
        <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
          <el-date-picker v-model="badDebtPeriod" type="month" value-format="YYYYMM" placeholder="期间" style="width:160px" />
          <el-button type="warning" @click="provision">按逾期账龄计提</el-button>
          <el-button type="primary" plain @click="reconStart">应收账款对账</el-button>
        </div>
        <SkeletonList v-if="loading" :rows="8" :cols="5" />
        <el-table :data="badDebts" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="该账期暂无计提记录">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="应收单号" min-width="160" show-overflow-tooltip  sortable />
          <el-table-column prop="period" label="期间" width="110"  sortable />
          <el-table-column prop="provisionDate" label="计提日期" width="110"  sortable />
          <el-table-column label="计提比例" width="100" align="right">
            <template #default="{ row }">{{ row.provisionRate ? (row.provisionRate * 100).toFixed(0) + '%' : '—' }}</template>
          </el-table-column>
          <el-table-column label="计提金额" width="130" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <el-button v-if="row.status === 'PROVISIONED'" link size="small" type="success" @click="confirmBadDebt(row)">确认核销</el-button>
              <el-popconfirm
                v-if="row.status === 'PROVISIONED'"
                title="确认转回该笔坏账？将生成红字凭证（借1231/贷6701）。"
                width="260"
                confirm-button-text="确认转回"
                cancel-button-text="取消"
                @confirm="reverseBadDebt(row)"
              >
                <template #reference>
                  <el-button link size="small" type="warning">转回</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 应收单生成弹窗 -->
    <el-dialog v-model="dlgReceivable" title="按销售订单生成应收单" width="500px" destroy-on-close>
      <el-form ref="recvFormRef" :model="rForm" :rules="formRules" label-width="120px">
        <el-form-item label="往来单位" prop="customerId">
          <el-select v-model="rForm.customerId" filterable placeholder="选择客户/供应商" style="width:100%">
            <el-option v-for="c in customers" :key="c.id" :label="(c.customerName || c.supplierName || c.name || '') + '（' + c.id + '）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="应收金额" prop="amount"><el-input-number v-model="rForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="币种" prop="currencyCode">
          <el-select v-model="rForm.currencyCode" style="width:100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />





          </el-select>
        </el-form-item>
        <el-form-item label="到期日" prop="dueDate"><el-date-picker v-model="rForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="rForm.batchNo" placeholder="请输入批次号" />
          <div style="color:#909399;font-size:12px">批次号仅作人工分组标记，系统不据此勾稽，可留空</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgReceivable = false">取消</el-button>
        <el-button type="primary" @click="saveReceivable">保存</el-button>
      </template>
    </el-dialog>

    <!-- 收款单登记弹窗 -->
    <el-dialog v-model="dlgReceipt" title="登记收款单" width="480px" destroy-on-close>
      <el-form ref="receiptFormRef" :model="rcForm" :rules="receiptRules" label-width="120px">
        <el-form-item label="往来单位" prop="customerId">
          <el-select v-model="rcForm.customerId" filterable placeholder="选择客户/供应商" style="width:100%">
            <el-option v-for="c in customers" :key="c.id" :label="(c.customerName || c.supplierName || c.name || '')" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款金额" prop="amount"><el-input-number v-model="rcForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="收款日期" prop="receiptDate"><el-date-picker v-model="rcForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgReceipt = false">取消</el-button>
        <el-button type="primary" @click="saveReceipt">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { rcStatusLabel } from '@/constants/enums'

import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receivableApi, mdApi } from '../../api'
import { http } from '../../request'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useCurrency } from '@/composables/useCurrency'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  const t = tab.value
  traceBillId.value = t === 'collect' ? row.receivableId : row.id
  traceBizType.value = t === 'collect' ? 'RECEIVABLE' : (t === 'badDebt' ? 'BAD_DEBT_PROV' : null)
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('common.details') })
}

// 客户/供应商列表（用于往来单位 ID → 名称）
const selRows = ref([])
const customers = ref([])
function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? (c.customerName || c.supplierName || c.name || '—') : (id ? '#' + id : '—') }

// 流程态配色（wf_instance.status）——审批中给主色，最终状态按结果分色，未起流程灰
function flowTagType(s) {
  return { RUNNING: 'primary', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info', TERMINATED: 'info' }[s] || 'info'
}

// 客户信用等级：等级取值 A/B/C（见 master/Customer.vue），C 为最低档（高风险）
function customerCreditLevel(id) {
  const c = customers.value.find((x) => x.id === id)
  return c?.creditLevel || ''
}
// 高风险档判定：creditLevel 含 'C' 或 '高风险'（等级仅 A/B/C，含 C 即命中最低档）
function isHighRiskCustomer(id) {
  const lv = String(customerCreditLevel(id) || '').toUpperCase()
  return !!lv && (lv.includes('C') || lv.includes('高风险'))
}

// 应收状态翻译

// 四个子视图在 sys_menu 里只有 3070「应收管理」一条菜单行，没有拆出的子菜单。
// 原先按「一 TAB = 一菜单」的约定隐藏页签条 ⇒ 收款核销/催收待办/坏账计提只能手敲 URL。
// 菜单未拆的页不藏页签：深链进来仍定位到指定页签，同时把其余页签露出来可点。
const props = defineProps({ tab: { type: String, default: '' } })
const tab = ref(props.tab || 'receivable')
const loading = ref(false)
const kw = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const receivables = ref([])
const recvTotal = ref(0)
// 4030 权限拒绝独立空态（原先与「暂无数据」同形，无权限被读成没数据）
const denied = ref(false)
const receipts = ref([])
const collects = ref([])
const badDebts = ref([])
const overdueDays = ref(30)
// 逾期批处理的账期基准日（留空＝系统当前日）。判据若写死 LocalDate.now()，
// "业务期间晚于系统日"时到期日永不早于基准日 ⇒ 逾期集恒空、催收池零成员、坏账计提与账龄同因空转。
const overdueBaseline = ref('')
const badDebtPeriod = ref('')

const dlgReceivable = ref(false)
const rForm = ref({ customerId: null, orderId: null, amount: 0, currencyCode: 'CNY', dueDate: '', batchNo: '' })
const dlgReceipt = ref(false)
const rcForm = ref({ customerId: null, amount: 0, receiptDate: '' })
const recvFormRef = ref(null)
const receiptFormRef = ref(null)

const { rules: formRules } = useFormRules('RECEIVABLE')
const { rules: receiptRules } = useFormRules('RECEIPT')
const { submit: guardReceivable } = useSubmitGuard(recvFormRef)
const { submit: guardReceipt } = useSubmitGuard(receiptFormRef)

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const uid = () => Date.now()

async function loadReceivable() {
  loading.value = true
  try {
    const d = await receivableApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: kw.value })
    receivables.value = d.list || []
    recvTotal.value = Number(d.total || 0)
    denied.value = false
  } catch (e) {
    // 4030（无应收读权限）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { denied.value = true; return }
    ElMessage.error(e.message)
  } finally { loading.value = false }
}
async function loadReceipt() { try { receipts.value = await receivableApi.receiptList() } catch (e) { ElMessage.error(e.message) } }
async function loadCollect() { try { collects.value = await receivableApi.collectList() } catch (e) { ElMessage.error(e.message) } }
async function loadBadDebt() { try { badDebts.value = await receivableApi.badDebtList(badDebtPeriod.value) } catch (e) { ElMessage.error(e.message) } }

async function openReceivable() { rForm.value = { customerId: null, orderId: null, amount: 0, currencyCode: 'CNY', dueDate: '', batchNo: '' }; dlgReceivable.value = true }
async function saveReceivable() {
  await guardReceivable(async () => {
    try { await receivableApi.create(rForm.value); ElMessage.success(t('receivable.main.Receivable')); dlgReceivable.value = false; loadReceivable() }
    catch (e) { ElMessage.error(e.message) }
  })
}
/**
 * 把界面填的基准日折成后端两个可选入参之一——
 * yyyyMM／yyyy-MM ＝期间（后端按期末日跑），yyyy-MM-dd／yyyy/MM/dd／yyyyMMdd ＝截止日（逐字取该日）；
 * 留空＝不传（后端回退系统当前日，与原先逐字一致）；格式不认识时显式提示，不静默兜底。
 * @returns {{asOfDate?: string, period?: string}}
 */
function overdueBaselineParams() {
  const v = String(overdueBaseline.value || '').trim()
  if (!v) return {}
  if (/^\d{6}$/.test(v) || /^\d{4}-\d{2}$/.test(v)) return { period: v }
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return { asOfDate: v }
  if (/^\d{8}$/.test(v)) return { asOfDate: `${v.slice(0, 4)}-${v.slice(4, 6)}-${v.slice(6, 8)}` }
  throw new Error('账期/截止日格式不识别：' + v + '（应填 202611、2026-11 或 2026-11-30）')
}

async function accrueOverdue() {
  let baseline
  try { baseline = overdueBaselineParams() } catch (e) { return ElMessage.error(e.message) }
  try {
    const n = await receivableApi.accrueOverdue(baseline)
    ElMessage.success('逾期计提完成，影响 ' + n + ' 条' + (baseline.period || baseline.asOfDate ? `（基准日 ${baseline.asOfDate || baseline.period}）` : ''))
    loadBadDebt()
  } catch (e) { ElMessage.error(e.message) }
}

/* OTC05 应收管理分析报告（AR_REPORT）：审批流已按用户 09-29 指令摘除（流程侧 V2.2.32_ar_report_softdel），
   「发起分析报告」入口随之撤下；receivableApi.report 与后端端点保留未动，如需恢复审批另行立项。 */

// 收款日期字段名对齐实体 RcReceipt.receiptDate：原先绑的是 payDate，实体无此属性，
// Jackson 忽略后后端默默按「今天」补值，用户选的日期从未生效。
async function openReceipt() { rcForm.value = { customerId: null, amount: 0, receiptDate: '' }; dlgReceipt.value = true }
async function saveReceipt() {
  await guardReceipt(async () => {
    // 孪生面同修：保存后 await 刷新，避免"点了没反应"的误判；
    // 本页无检索工具条，新行必在列表首屏（后端按 id 倒序），故不做「不在视野」提示。
    try { await receivableApi.receiptCreate(rcForm.value); ElMessage.success(t('receivable.main.Receipt')); dlgReceipt.value = false; await loadReceipt() }
    catch (e) { ElMessage.error(e.message) }
  })
}
// ④：收款单"已核销"最终状态名称表（后端 RcReceiptService.APPLIED_RECEIPT_STATUSES 同一规则）。
// 登记环节 create() 自 起在同一事务里建明细＋冲减应收环节＋置 WRITTEN_OFF，
// 故 WRITTEN_OFF／WRITE_OFF／APPLIED 都＝冲减已发生，行内不再给「先进先出核销」按钮。
const receiptAppliedStatuses = ['WRITTEN_OFF', 'WRITE_OFF', 'APPLIED']

async function applyReceipt(row) {
  try {
    await receivableApi.receiptApply(row.id)
    ElMessage.success(t('receivable.main.Write'))
    loadReceipt(); loadReceivable()
  } catch (e) { ElMessage.error(e.message) }
}

async function createOverdue() {
  let baseline
  try { baseline = overdueBaselineParams() } catch (e) { return ElMessage.error(e.message) }
  try {
    const n = await receivableApi.collectCreateOverdue(overdueDays.value, baseline)
    ElMessage.success('生成催收待办 ' + n + ' 条' + (baseline.period || baseline.asOfDate ? `（基准日 ${baseline.asOfDate || baseline.period}）` : ''))
    loadCollect()
  } catch (e) { ElMessage.error(e.message) }
}
async function solveCollect(row) {
  try {
    // 乙：办结照做，只把"同一人在这条催收任务上占多岗"的复核提示显示出来（不拦、不报错）
    const r = await receivableApi.collectSolve(row.id)
    if (r && r.warning) ElMessage({ type: 'warning', message: '待办已解决：' + r.warning, duration: 6000 })
    else ElMessage.success(t('receivable.main.Do'))
    loadCollect()
  } catch (e) { ElMessage.error(e.message) }
}

async function provision() {
  if (!badDebtPeriod.value) return ElMessage.warning(t('receivable.main.Please'))
  try { const n = await receivableApi.badDebtProvision(badDebtPeriod.value); ElMessage.success(t('approval.main.Records')); loadBadDebt() }
  catch (e) { ElMessage.error(e.message) }
}
async function reverseBadDebt(row) {
  try {
    const d = await http.post(`/tenant/bad-debt/${row.id}/reverse`)
    ElMessage.success('转回成功，红字凭证号 ' + (d.voucherId || '—') + '，金额 ' + fmt(d.amount))
    loadBadDebt()
  } catch (e) { ElMessage.error(e.message) }
}
async function confirmBadDebt(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('确认该笔坏账核销？输入确认说明', '坏账确认（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputValue: t('receivable.main.Bad') })
    await receivableApi.badDebtConfirm(row.id, reason)
    ElMessage.success(t('receivable.main.Bad2'))
    loadBadDebt()
  } catch (e) { /* cancelled */ }
}
async function reconStart() {
  try {
    const { value: period } = await ElMessageBox.prompt('输入对账期间(yyyyMM)', '应收账款对账（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputValue: badDebtPeriod.value || '', inputValidator: (v) => (v != null && String(v).trim().length >= 6) || '请填写对账期间（yyyyMM）' })
    await receivableApi.reconStart(period, null)
    ElMessage.success(t('receivable.main.Reconciliation'))
  } catch (e) { /* cancelled */ }
}

function reload() {
  loadReceivable(); loadReceipt(); loadCollect(); loadBadDebt()
}
// 消费半：工作台「我的单据」台账跳转同写 kw 与 billNo/billId 两套取参规则（Workbench:269-275），
// 本页完全不吃 URL 的话——跳过来落的仍是全量首屏，"别的页面能定位到这一单"在此失明。
const route = useRoute()
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

onMounted(() => {
  loadCurrency()
  const q = route.query || {}
  const deep = q.billNo || q.kw || q.billId
  if (deep) kw.value = String(deep)
  if (q.tab && !props.tab) tab.value = String(q.tab)
  mdApi.customers({ pageNum: 1, pageSize: 200 }).then((d) => { customers.value = d?.list || [] }).catch(() => {})
  reload()
})

function exportCsv() {
  if (!receivables.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'billNo', label: t('budget.budgetOccupy.Document') }, { prop: 'batchNo', label: t('image.imageTask.Batch') }, { prop: 'currencyCode', label: t('bill.billDetailPanel.Currency') }, { prop: 'dueDate', label: t('receivable.main.Due') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = receivables.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ReceivableBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('utils.csv.Select')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('utils.csv.Cannot')); return }
  const header = keys.join(',')
  const lines = selRows.value.map((row) => keys.map((k) => {
    const v = row[k]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ReceivableBoard_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
</style>