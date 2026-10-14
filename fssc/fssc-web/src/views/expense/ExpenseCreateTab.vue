<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card header="单据类型">
      <el-radio-group v-model="form.bill.billType">
        <el-radio-button v-for="t in typeOptions" :key="t.label" :value="t.billType" :disabled="false" @click="pickType(t)">
          {{ t.label }}
        </el-radio-button>
      </el-radio-group>
    </el-card>

    <el-row :gutter="16" style="margin-top: 12px">
      <el-col :xs="24" :md="14">
        <el-card header="基本信息">
          <el-form ref="formRef" :rules="formRules" :model="form.bill" label-width="90px" size="default">
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.bill.title" :placeholder="currentType.label + '申请'" />
            </el-form-item>
            <el-form-item label="申请日期" prop="applyDate">
              <el-date-picker v-model="form.bill.applyDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="币种" prop="currencyCode">
              <el-select v-model="form.bill.currencyCode" style="width: 120px">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
              </el-select>
            </el-form-item>
<el-form-item v-if="currentType.pay" label="收款方">
              <el-select v-model="form.bill.payeeName" filterable allow-create clearable style="width: 100%" placeholder="选择或输入收款方（供应商/单位）">
                <el-option v-for="p in payees" :key="p.id" :label="p.payeeName" :value="p.payeeName" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="currentType.pay" label="收款对象" prop="payTarget">
              <el-select v-model="form.bill.payTarget" style="width: 100%" placeholder="请选择收款对象">
                <el-option label="员工" value="EMPLOYEE" />
                <el-option label="供应商" value="SUPPLIER" />
                <el-option label="客户" value="CUSTOMER" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="currentType.pay" label="收款账号" prop="payeeAccountNo">
              <el-input v-model="form.bill.payeeAccountNo" placeholder="按收款对象填写对应账号（必填）" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.bill.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card header="费用明细（多行）" style="margin-top: 12px">
          <el-table :data="form.lines" size="small" border empty-text="暂无数据" v-loading="loading" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column label="费用项" width="160">
              <template #default="{ row }">
                <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(row, id)">
                  <el-option v-for="it in items" :key="it.id" :label="it.itemName" :value="it.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="费用日期" width="140">
              <template #default="{ row }">
                <el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="金额" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(row)" />
              </template>
            </el-table-column>
            <el-table-column label="税率%" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" @change="recalcLine(row)" />
              </template>
            </el-table-column>
            <el-table-column label="税额" width="90">
              <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column label="发票号" width="150">
              <template #default="{ row }">{{ invOf(row)?.invoiceNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="开票日期" width="110">
              <template #default="{ row }">{{ invOf(row)?.invoiceDate || '—' }}</template>
            </el-table-column>
            <el-table-column label="销方" min-width="130">
              <template #default="{ row }">{{ invOf(row)?.sellerName || '—' }}</template>
            </el-table-column>
            <el-table-column label="预算科目" width="120">
              <template #default="{ row }">
                <el-select v-model="row.budgetItemId" clearable filterable style="width: 100%">
                  <el-option v-if="row.budgetItemId != null && !budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                  <el-option v-for="b in budgets" :key="b.id" :label="budgetLabel(items, [], [], b)" :value="b.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="标准/超标" min-width="120">
              <template #default="{ row }">
                <span v-if="row.stdAmount != null">标准 {{ fmt(row.stdAmount) }}</span>
                <el-tag v-if="row.overStd === 1" type="danger" size="small">超标</el-tag>
                <span v-else-if="row.stdAmount != null" type="success">合规</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button type="danger" link @click="removeLine($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="form.lines.push(newLine())">+ 加一行</el-button>
          <div style="margin-top: 8px">合计：<b>{{ fmt(sumAmount()) }}</b> 元（税额合计 {{ fmt(sumTax()) }}）</div>
          <div style="margin-top: 4px; color: #606266">大写：{{ form.bill.amountCn || '待保存生成' }}</div>
        </el-card>

        <el-card v-if="currentType.travel" header="差旅明细" style="margin-top: 12px">
          <el-table :data="form.travels" size="small" border empty-text="暂无数据" v-loading="loading" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column label="出行人" width="120">
              <template #default="{ row }"><el-input v-model="row.travelerName" placeholder="出行人姓名" /></template>
            </el-table-column>
            <el-table-column label="出发城市" width="140">
              <template #default="{ row }">
                <el-select v-model="row.fromCityId" filterable style="width: 100%">
                  <el-option v-for="c in cities" :key="c.id" :label="c.cityName" :value="c.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="到达城市" width="140">
              <template #default="{ row }">
                <el-select v-model="row.toCityId" filterable style="width: 100%">
                  <el-option v-for="c in cities" :key="c.id" :label="c.cityName" :value="c.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="起/止" width="220">
              <template #default="{ row }">
                <el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 105px" />
                <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 105px" />
              </template>
            </el-table-column>
            <el-table-column label="交通方式" width="120">
              <template #default="{ row }">
                <el-select v-model="row.trafficType" style="width: 100%">
                  <el-option label="高铁" value="RAIL" />
                  <el-option label="飞机" value="AIR" />
                  <el-option label="汽车" value="CAR" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="住宿费" width="110">
              <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeTravel($index)">删</el-button></template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="form.travels.push(newTravel())">+ 加一行</el-button>
        </el-card>

        <el-card header="发票 / 附件" style="margin-top: 12px">
          <div class="attach-row">
            <UploadButton label="选择文件（发票自动识别解析）" accept=".jpg,.png,.jpeg,.bmp,.webp,.tif,.tiff,.ofd,.xml,.pdf" biz-type="INVOICE" :biz-id="form.bill.id || null" zone="TEMP" parse @done="onAttachUploaded" />
            <span v-if="form.attaches.length" class="attach-name">
              已上传：{{ form.attaches.map((a) => a.fileName).join('、') }}
              <el-button type="primary" link @click="previewAttach">预览</el-button>
              <el-button type="danger" link @click="clearAttaches()">清除</el-button>
            </span>
          </div>

          <div style="margin-top: 12px">
            <div style="font-weight: 600; margin-bottom: 6px">已解析发票 / 手工录入（勾选后自动填入费用明细行并合计）</div>
            <el-table :data="form.invoices" size="small" border empty-text="暂无发票，请上传发票文件或新增发票行手录">
              <el-table-column label="填单" width="52">
                <template #default="{ row }">
                  <el-checkbox v-model="row.checked" @change="toggleInvoice(row)" />
                </template>
              </el-table-column>
              <el-table-column label="发票号" min-width="160">
                <template #default="{ row }"><el-input v-model="row.invoiceNo" size="small" placeholder="发票号" /></template>
              </el-table-column>
              <el-table-column label="开票日期" width="130">
                <template #default="{ row }"><el-date-picker v-model="row.invoiceDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" /></template>
              </el-table-column>
              <el-table-column label="金额(价税)" width="120">
                <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" @change="recalcInvoice(row); syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column label="税率%" width="110">
                <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" style="width:100%" @change="recalcInvoice(row); syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column label="税额" width="90">
                <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
              </el-table-column>
              <el-table-column label="不含税" width="90">
                <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
              </el-table-column>
              <el-table-column label="销方" min-width="130">
                <template #default="{ row }"><el-input v-model="row.sellerName" size="small" placeholder="销方名称" @change="syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column label="货物/服务名称" min-width="140">
                <template #default="{ row }"><el-input v-model="row.primaryItemName" size="small" placeholder="解析自票面，可改后重匹配" @change="applyItemMatch(row)" /></template>
              </el-table-column>
              <el-table-column label="类型" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.invoiceType" size="small" style="width: 100%" @change="syncInvoiceLine(row)">
                    <el-option v-for="t in INVOICE_TYPES" :key="t.code" :label="t.label" :value="t.code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="来源" width="120">
                <template #default="{ row }">
                  <span v-if="row.parsed" class="attach-name">已解析</span>
                  <el-tag v-else type="warning" size="small">手工</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="52">
                <template #default="{ $index }"><el-button type="danger" link @click="removeInvoice($index)">删</el-button></template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top: 8px" @click="form.invoices.push(newInvoice())">+ 新增发票行（扫描件手工录入）</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card header="预算占用预览">
          <el-alert v-if="budgetWarn.length" type="warning" :closable="false" show-icon :title="`以下预算科目将超出可用额度（硬控拦截／软控提交时需填超支理由／预警仅提醒放行）`" style="margin-bottom: 8px" />
          <el-table :data="budgetPreview" size="small" border empty-text="暂无数据" v-loading="loading">
            <el-table-column prop="label" label="预算科目" min-width="120" show-overflow-tooltip sortable />
            <el-table-column label="本次申请" width="110">
              <template #default="{ row }">{{ fmt(row.used) }}</template>
            </el-table-column>
            <el-table-column label="可用余额" width="110">
              <template #default="{ row }">{{ row.unknown ? '—' : fmt(row.available) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.unknown ? 'warning' : row.over ? 'danger' : 'success'" size="small">
                  {{ row.unknown ? '额度未知' : row.over ? '超预算' : '可支' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="currentType.claim" header="冲销借款" style="margin-top: 12px">
          <el-button size="small" @click="openLoanDialog">+ 选择借款单冲抵</el-button>
          <el-table :data="form.loanOffsets" size="small" border style="margin-top: 8px" empty-text="暂无数据" v-loading="loading">
            <el-table-column prop="loanBillNo" label="借款单号" width="140" sortable />
            <el-table-column label="冲抵金额" width="120">
              <template #default="{ row }"><el-input-number v-model="row.offsetAmount" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeLoanOffset($index)">删</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="currentType.claim" header="关联事前申请（自动带出分摊）" style="margin-top: 12px">
          <el-button size="small" @click="openApplyDialog">+ 选择事前申请单</el-button>
          <el-table :data="form.relations" size="small" border style="margin-top: 8px" empty-text="暂无数据" v-loading="loading">
            <el-table-column prop="sourceBillNo" label="申请单号" width="140" sortable />
            <el-table-column prop="useAmount" label="可冲抵金额" width="120" sortable />
            <el-table-column label="操作" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeRelation($index)">删</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card header="操作" style="margin-top: 12px">
          <el-button @click="saveDraft">保存草稿</el-button>
          <el-button type="primary" @click="submit">提交审批</el-button>
          <el-button v-if="form.bill.id" link @click="resetForm">新建空白单</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 借款单选择 -->
    <el-dialog v-model="loanDialog" title="选择借款单冲抵" width="560px">
      <el-table :data="loanCandidates" size="small" empty-text="暂无数据" v-loading="loading" @row-click="pickLoan" highlight-current-row>
        <el-table-column prop="billNo" label="借款单号" width="150" sortable />
        <el-table-column prop="totalAmount" label="借款额" width="100" sortable :formatter="(r,c,v) => fmt(v)" />
        <el-table-column prop="loanBalance" label="未还余额" width="100" sortable :formatter="(r,c,v) => fmt(v)" />
      </el-table>
    </el-dialog>

    <!-- 事前申请选择 -->
    <el-dialog v-model="applyDialog" title="选择事前申请单" width="560px">
      <el-table :data="applyCandidates" size="small" empty-text="暂无数据" v-loading="loading" @row-click="pickApply" highlight-current-row>
        <el-table-column prop="billNo" label="申请单号" width="150" sortable />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip sortable />
        <el-table-column prop="totalAmount" label="申请金额" width="100" sortable :formatter="(r,c,v) => fmt(v)" />
      </el-table>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
const { rules: formRules } = useFormRules('EXPENSE')
import { onMounted, reactive, ref, computed, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, mdApi, detailApi, masterCfgApi, store, userApi } from '../../api'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { hasPerm } from '../../utils/perm'
import UploadButton from '../../components/UploadButton.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { INVOICE_TYPES } from '@/constants/invoiceType'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { formatMoney, budgetLabel, budgetUnseenLabel } from '@/utils/format'
import { pickBudgetLine } from '@/utils/budget'
import { splitTaxInclusive } from '@/utils/tax'
import { invoiceOutcome } from '@/utils/invoiceOutcome'
const { autofill, saveFormHistory } = useAutoFill()
const formRef = ref(null)
const guard = useSubmitGuard(formRef)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const emit = defineEmits(['saved', 'submitted'])
const attachViewer = inject('attachViewer')

// D-DEC-361：预填日期与后端落库时区对齐——toISOString() 是 UTC 串，东八区恒差 −8h、跨零点错成昨天，
// 故按本地时钟取 YYYY-MM-DD（与 composables/useBill.js today() 同口径，不引日期库）。
function todayLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const selRows = ref([])
const loading = ref(false)
const form = reactive({
  bill: { billType: 'CLAIM', billSubType: '', title: '', companyId: store.user?.companyId || null, deptId: store.user?.deptId || null, applyDate: todayLocal(), currencyCode: 'CNY', payeeName: '', remark: '' },
  lines: [],
  travels: [],
  shares: [],
  loanOffsets: [],
  relations: [],
  attaches: [],
  invoices: [],
})
const items = ref([])
const budgets = ref([])
const cities = ref([])
const payees = ref([])
const users = ref([])
const loanDialog = ref(false)
const applyDialog = ref(false)
const loanCandidates = ref([])
const applyCandidates = ref([])

const typeOptions = ref([
  { label: '员工费用报销', billType: 'CLAIM', sub: '', travel: false, claim: true, pay: false },
  { label: '差旅培训报销', billType: 'CLAIM', sub: 'TRAVEL', travel: true, claim: true, pay: false },
  { label: '费用预付款', billType: 'PAY', sub: 'PREPAY', travel: false, claim: false, pay: true },
  { label: '对公结算', billType: 'PAY', sub: 'SETTLE', travel: false, claim: false, pay: true },
  { label: '代收代付', billType: 'PAY', sub: 'AGENCY', travel: false, claim: false, pay: true },
  { label: '机票结算', billType: 'PAY', sub: 'AIR', travel: false, claim: false, pay: true },
])
const currentType = computed(() => typeOptions.value.find((t) => t.billType === form.bill.billType && t.sub === form.bill.billSubType) || typeOptions.value[0])

function pickType(t) {
  form.bill.billType = t.billType
  form.bill.billSubType = t.sub
  if (!t.travel) form.travels = []
}

function newLine() {
  return { itemId: null, itemName: '', expenseDate: todayLocal(), amount: 0, taxRate: 0, taxAmount: 0, netAmount: 0, budgetItemId: null, stdAmount: null, overStd: 0, overReason: '', remark: '', invoice: null }
}
function newTravel() {
  return { travelerName: '', fromCityId: null, toCityId: null, beginDate: '', endDate: '', days: 0, trafficType: 'RAIL', trafficClass: '', hotelAmount: 0, trafficAmount: 0, allowanceAmount: 0, otherAmount: 0, totalAmount: 0, overStd: 0, remark: '' }
}
function fmt(v) { return formatMoney(v, '0.00') }
function recalcLine(row) {
  // 价内税口径：含税金额 ÷ (1+税率) 反算不含税，税额 = 含税 − 不含税（与票面/税务口径一致，）
  const r = splitTaxInclusive(row.amount, row.taxRate)
  row.taxAmount = r.taxAmount
  row.netAmount = r.netAmount
}
function onItemChange(row, id) {
  const it = items.value.find((x) => x.id === id)
  row.itemName = it ? it.itemName : ''
  // FINAL-01: 按费用类型自动带出映射的预算科目；手选过则不覆盖（主体口径见 utils/budget.js）
  if (id != null && row.budgetItemId == null) {
    const bid = pickBudgetLine({
      pool: budgets.value, itemId: id, map: budgetMap.value,
      companyId: form.bill.companyId, deptId: form.bill.deptId,
    })
    if (bid != null) row.budgetItemId = bid
  }
}
// FINAL-01: 费用类型→预算科目映射缓存(itemId→budgetItemId)
const budgetMap = ref({})
// ---------- 发票清单（上传解析 + 手动录入）→ 生成费用明细行 ----------
let invoiceSeq = 0
function newInvoice() {
  return { uid: ++invoiceSeq, checked: false, lineRef: null, fileId: null, invoiceNo: '', invoiceDate: '', amount: 0, netAmount: 0, taxAmount: 0, taxRate: 0, sellerName: '', sellerTaxNo: '', invoiceType: '', primaryItemName: '', parsed: false, sourceName: '' }
}
function recalcInvoice(inc) {
  // 与明细行同一价内税口径，避免"发票清单行 / 明细行"两套算法（）
  const r = splitTaxInclusive(inc.amount, inc.taxRate)
  inc.taxAmount = r.taxAmount
  inc.netAmount = r.netAmount
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
function newLineFromInvoice(inc) {
  const line = newLine()
  line.expenseDate = inc.invoiceDate || todayLocal()
  line.amount = Number(inc.amount || 0)
  line.taxRate = Number(inc.taxRate || 0)
  recalcLine(line)
  line.remark = inc.invoiceNo ? `发票 ${inc.invoiceNo}` : ''
  line.invoice = { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount }
  // 费用类型由发票货名智能匹配决定：命中才填，未命中留空待填（不再默认职工福利）
  applyItemMatch(inc)
  return line
}
function syncInvoiceLine(inc) {
  if (!inc.checked || !inc.lineRef) return
  const line = inc.lineRef
  line.amount = Number(inc.amount || 0)
  line.taxRate = Number(inc.taxRate || 0)
  recalcLine(line)
  line.expenseDate = inc.invoiceDate || line.expenseDate
  line.remark = inc.invoiceNo ? `发票 ${inc.invoiceNo}` : line.remark
  line.invoice = { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount }
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
// 行的发票元信息：优先用内存对象，否则从持久化 extJson 解析回填
function invOf(line) {
  if (line.invoice) return line.invoice
  if (line.extJson) {
    try { const o = typeof line.extJson === 'string' ? JSON.parse(line.extJson) : line.extJson; if (o && o.invoice) { line.invoice = o.invoice; return o.invoice } } catch (e) { /* ignore */ }
  }
  return null
}
// 合计含发票生成的明细行（这些行也可能暂无费用项 itemId，但金额已确定）
function sumAmount() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0) }
function sumTax() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.taxAmount || 0), 0) }

const budgetPreview = computed(() => {
  const map = {}
  form.lines.filter((l) => l.budgetItemId).forEach((l) => { map[l.budgetItemId] = (map[l.budgetItemId] || 0) + Number(l.amount || 0) })
  return Object.keys(map).map((bid) => {
    const b = budgets.value.find((x) => String(x.id) === String(bid))
    const loaded = !!b
    const b0 = b || {}
    const total = Number(b0.totalAmount || 0)
    const adjust = Number(b0.adjustAmount || 0)
    const occupy = Number(b0.occupyAmount || 0)
    const used = Number(b0.usedAmount || 0)
    const avail = total + adjust - occupy - used
    // D-P2-02：科目取不到（跨主体/未下达）时按未知处理，不能拿 avail=0 误判成"超预算"，也不能显示空白
    return {
      label: loaded ? budgetLabel(items.value, [], [], b0) : budgetUnseenLabel(bid),
      used: map[bid], available: loaded ? avail : null, over: loaded && map[bid] > avail, unknown: !loaded,
      // D-DEC-372：控制方式随 pick 行带出，预览提示按档位改口（缺失按 HARD fail-closed，与服务端同口径）
      control: b0.controlType || 'HARD',
    }
  })
})
const budgetWarn = computed(() => budgetPreview.value.filter((x) => x.over))

async function loadMd() {
  try {
    items.value = await mdApi.expenseItems()
    budgets.value = (await mdApi.budgets({ pageNum: 1, pageSize: 200 })).list || []
    // FINAL-01: 加载费用类型→预算科目映射(选费用类型自动带出预算科目)
    try {
      const maps = (await masterCfgApi.itemBudgetMap.options()) || []
      const m = {}
      maps.forEach((x) => { if (x?.itemId != null && x?.budgetItemId != null) m[String(x.itemId)] = x.budgetItemId })
      budgetMap.value = m
    } catch (e) { budgetMap.value = {} }
    cities.value = (await mdApi.cities({ pageNum: 1, pageSize: 200 })).list || []
    payees.value = (await mdApi.payees({ pageNum: 1, pageSize: 200 })).list || []
    // P2-3 出差人受控选择：加载用户列表供出行人下拉
    users.value = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
  } catch (e) { ElMessage.error(e.message) }
}
async function loadBillTypes() {
  try {
    const list = await masterCfgApi.form.options()
    if (list && list.length) {
      typeOptions.value = list.filter((t) => t.category === 'CLAIM' || t.category === 'PAY').map((t) => ({
        label: t.formName, billType: t.billType, sub: t.billSubType || '',
        travel: t.billSubType === 'TRAVEL', claim: t.category === 'CLAIM', pay: t.category === 'PAY', modelKey: t.modelKey,
      }))
    }
  } catch (e) { ElMessage.error(e.message) }
}

function onAttachUploaded(rec) {
  if (!rec) return
  const attach = { fileId: rec.id, fileName: rec.fileName, attachType: rec.attachType === 'INVOICE' ? 'INVOICE' : 'OTHER' }
  // 同一文件不重复登记附件
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
function previewAttach() {
  const ids = form.attaches.map((a) => a.fileId)
  if (!ids.length) return
  attachViewer.value?.open(ids, '发票/附件')
}

async function saveDraft() {
  try {
    const id = await billApi.saveDraft({
      bill: { ...form.bill, totalAmount: sumAmount() },
      lines: form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).map((l) => {
        const clone = { ...l }
        const base = (clone.extJson && typeof clone.extJson === 'object') ? clone.extJson : {}
        if (clone.invoice) base.invoice = clone.invoice
        clone.extJson = JSON.stringify(base)
        return clone
      }),
      attaches: form.attaches,
    })
    form.bill.id = id
    if (currentType.value.travel && form.travels.length) await detailApi.saveTravels(id, form.travels)
    // D-V4R-42：无 exp:share:update 就不发这一跳（员工角色不在授权名册内 ⇒ 原先每次保存都红一发 4030、
    // 分摊行零落库）。承担口径权威值在 exp_bill_line 各列上，分摊行至今无消费方（同 useBill.js 那条）。
    if (form.shares.length && hasPerm('exp:share:update')) await detailApi.replaceShares(id, form.shares)
    for (const o of form.loanOffsets) await detailApi.addLoanOffset(id, { ...o, billId: id })
    for (const r of form.relations) await detailApi.addRelation(r.sourceBillId, id)
    ElMessage.success('草稿已保存')
    emit('saved')
    return id
  } catch (e) { ElMessage.error(e.message) }
}
async function submit() {
  saveFormHistory('EXPENSE', form.value || form)

  try {
    // 表头必填由 formRules + guard 统一收口；明细/差旅的逐行规则在表格内，el-form 覆盖不到，仍在此校验
    await guard.submit(async () => {
      if (!form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).length) { ElMessage.warning('请至少添加一条费用明细（可勾选已解析发票或新增发票行）'); return }
      // P1-1 提交前逐行校验：金额必须大于 0
      const badAmt = form.lines.find((l) => Number(l.amount || 0) <= 0)
      if (badAmt) { ElMessage.warning('费用明细存在金额未填或为 0 的行，请补充金额'); return }
      // P1-1 差旅明细日期范围校验：止日不得早于起日
      const badTravel = form.travels.find((t) => t.beginDate && t.endDate && t.endDate < t.beginDate)
      if (badTravel) { ElMessage.warning('差旅明细存在「到达日期早于出发日期」的行，请修正'); return }
      // P1-1 差旅明细交通/住宿金额不得为负
      if (form.travels.some((t) => Number(t.hotelAmount) < 0 || Number(t.trafficAmount) < 0 || Number(t.allowanceAmount) < 0 || Number(t.otherAmount) < 0)) { ElMessage.warning('差旅明细存在负金额，请修正'); return }
      const id = await saveDraft()
      await billApi.submit(id)
      ElMessage.success('已提交，等待审批')
      emit('submitted')
    })
  } catch (e) { ElMessage.error(e.message) }
}
function resetForm() {
  form.bill = { billType: 'CLAIM', billSubType: '', title: '', companyId: 1, deptId: 1, applyDate: todayLocal(), currencyCode: 'CNY', payeeName: '', remark: '' }
  form.lines = [newLine()]
  form.travels = []
  form.shares = []
  form.loanOffsets = []
  form.relations = []
  form.attaches = []
  form.invoices = []
}

async function openLoanDialog() {
  try {
    loanCandidates.value = (await billApi.myPage({ pageNum: 1, pageSize: 50, billType: 'LOAN', status: 'APPROVED' })).list || []
    loanDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
function pickLoan(row) {
  if (!form.loanOffsets.find((o) => o.loanBillId === row.id)) {
    form.loanOffsets.push({ loanBillId: row.id, loanBillNo: row.billNo, offsetAmount: Number(row.loanBalance || 0), offsetType: 'CLAIM' })
  }
  loanDialog.value = false
}
async function openApplyDialog() {
  try {
    applyCandidates.value = (await billApi.myPage({ pageNum: 1, pageSize: 50, billType: 'APPLY', status: 'APPROVED' })).list || []
    applyDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
function pickApply(row) {
  if (!form.relations.find((r) => r.sourceBillId === row.id)) {
    form.relations.push({ sourceBillId: row.id, sourceBillNo: row.billNo, useAmount: Number(row.totalAmount || 0) })
  }
  applyDialog.value = false
}

function handleSelectionChange(val) { selRows.value = val }

async function removeLine(i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  form.lines.splice(i, 1)
}
async function removeTravel(i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  form.travels.splice(i, 1)
}
async function removeLoanOffset(i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  form.loanOffsets.splice(i, 1)
}
async function removeRelation(i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  form.relations.splice(i, 1)
}
async function clearAttaches() {
  try {
    await ElMessageBox.confirm('确认清除所有附件？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  form.attaches = []
}

onMounted(() => {
  autofill(form, 'EXPENSE')

  form.lines = [newLine()]
  loadMd()
  loadBillTypes()
})

defineExpose({ saveDraft, submit, resetForm })
</script>

<style scoped>
.attach-row { display: flex; align-items: center; gap: 8px; }
.attach-name { font-size: 12px; color: #67c23a; }
</style>
