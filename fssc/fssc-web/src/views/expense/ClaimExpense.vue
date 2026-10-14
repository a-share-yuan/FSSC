<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <BillWorkbench ref="wb" bill-type="CLAIM" sub-type="" :claim="true" :before-submit="onBeforeSubmit">
    <template #create="b">
      <!-- ============ 单据概要 ============ -->
      <el-card header="单据概要">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item label="单据编号"><el-input :model-value="b.form.bill.billNo || '（保存后自动生成）'" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单时间"><el-input :model-value="b.nowTs()" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单人"><el-input :model-value="currentUserName" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item label="申请人" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" @change="(id) => onApplicantChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="申请人部门"><el-input :model-value="applicantDept" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="申请人公司"><el-input :model-value="applicantCompany" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item label="币种" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
                  <el-option label="人民币 CNY" value="CNY" />
                  <el-option label="美元 USD" value="USD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="录入口径">
                <el-radio-group v-model="b.form.bill.priceType" @change="(v) => onPriceTypeChange(b, v)">
                  <el-radio-button value="TAX_IN">含税录入</el-radio-button>
                  <el-radio-button value="TAX_EX">不含税录入</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="总金额"><span style="color:#f59a23;font-weight:700;font-size:15px">{{ b.fmt(totalAmount(b)) }} 元</span></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item label="预算费用年度" prop="periodYear">
                <el-select v-model="b.form.bill.periodYear" style="width: 100%">
                  <el-option v-for="y in years" :key="y" :label="y + ' 年'" :value="y" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="纸质附件张数" prop="extJson.paperAttachCount"><el-input-number v-model="b.form.bill.extJson.paperAttachCount" :min="0" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="纸质附件张数(扫描)"><el-input :model-value="'影像扫描后回填'" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item label="标题" prop="title"><el-input v-model="b.form.bill.title" placeholder="费用报销标题" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="单据说明" prop="remark"><el-input v-model="b.form.bill.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 费用详情 ============ -->
      <el-card header="费用详情" style="margin-top: 12px">
        <el-form label-width="110px" size="default">
          <!-- 单选组恒有默认值，不存在漏填，故不加红星 -->
          <el-form-item label="费用承担类型">
            <el-radio-group v-model="shareType">
              <el-radio-button value="COST_CENTER">成本中心承担</el-radio-button>
              <el-radio-button value="PROJECT">项目承担</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-table :data="b.form.lines" size="small" border empty-text="暂无数据" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column label="费用类型" width="150"><template #header><span style="color:#f56c6c">*</span>费用类型</template>
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(b, row, id)">
                <el-option v-for="it in b.items" :key="it.id" :label="it.itemName" :value="it.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="shareType === 'COST_CENTER' ? '成本中心' : '项目'" width="140">
            <template #default="{ row }">
              <el-select v-if="shareType === 'COST_CENTER'" v-model="row.costCenterId" filterable clearable style="width: 100%" placeholder="选择成本中心">
                <el-option v-for="c in b.costCenters" :key="c.id" :label="ccLabel(c)" :value="c.id" />
              </el-select>
              <el-select v-else v-model="row.projectId" filterable clearable style="width: 100%" placeholder="选择项目">
                <el-option v-for="p in b.projects" :key="p.id" :label="projectLabel(p)" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="预算科目" width="130"><template #header><span style="color:#f56c6c">*</span>预算科目</template>
            <template #default="{ row }">
              <el-select v-model="row.budgetItemId" filterable clearable style="width: 100%">
                <el-option v-if="row.budgetItemId != null && !b.budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                <el-option v-for="bg in b.budgets" :key="bg.id" :label="budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects })" :value="bg.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="原币金额" width="130"><template #header><span style="color:#f56c6c">*</span>原币金额</template>
            <template #default="{ row }">
              <el-input-number v-if="!isForeignLine(row)" v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(b, row)" />
              <el-input-number v-else :model-value="Number(row.origAmount ?? row.amount)" :min="0" :precision="2" style="width: 100%" @change="(v) => onOrigAmt(b, row, v)" />
              <div v-if="b.form.bill.priceType === 'TAX_EX'" style="font-size:11px;color:#e6a23c;line-height:1.2">按不含税录入，提交后自动价税分离</div>
            </template>
          </el-table-column>
          <el-table-column label="币种" width="95">
            <template #default="{ row }">
              <el-select :model-value="lineCur(row)" style="width: 100%" @change="(v) => onCurChange(b, row, v)">
                <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="汇率" width="150">
            <template #default="{ row }">
              <template v-if="isForeignLine(row)">
                <el-input :model-value="row.exchangeRate ?? ''" placeholder="留空自动带出当月汇率" size="small" @change="(v) => onRateInput(b, row, v)" />
                <div v-if="row._sysRate" style="font-size:11px;color:#909399;line-height:1.2">系统价 {{ row._sysRate }}</div>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="120">
            <template #default="{ row }"><el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="费用事由" min-width="110">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="招待费类型" width="130">
            <template #default="{ row }">
              <!-- D-DEC-373：绑真实列 entertain_type，ext_json 仅作历史单据回显兜底（取数口径见 entertainOfLine） -->
              <el-select :model-value="entertainOfLine(row).type" style="width: 100%" :disabled="!isEntertain(b, row)" clearable
                :placeholder="isEntertain(b, row) ? '选择招待费类型' : '选招待类费用后可填'"
                @update:model-value="(v) => setEntertain(row, { entertainType: v })">
                <el-option label="商务宴请" value="BUSINESS" /><el-option label="工作宴请" value="WORK" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="接待对象" width="110">
            <template #default="{ row }">
              <!-- D-DEC-023：接待对象 → exp_bill_line.attendees（列注释即「参与人员(招待费/会议费)」） -->
              <el-input :model-value="row.attendees || ''" :disabled="!isEntertain(b, row)" size="small" placeholder="招待对象"
                @update:model-value="(v) => setEntertain(row, { attendees: v })" />
            </template>
          </el-table-column>
          <el-table-column label="人数" width="120">
            <template #default="{ row }">
              <!-- D-DEC-023：接待人数 → exp_bill_line.person_count（列注释「涉及人数(招待费必填)」） -->
              <el-input-number :model-value="row.personCount ?? undefined" :min="1" :disabled="!isEntertain(b, row)" style="width: 100%" :controls-position="'right'"
                @update:model-value="(v) => setEntertain(row, { personCount: v })" />
            </template>
          </el-table-column>
          <el-table-column label="人均标准" width="100">
            <template #default="{ row }">
              <!-- D-DEC-023：人均标准随单入库（ext_json.perCapita），审批人不再只能口述 -->
              <span style="font-size:12px">{{ perCapita(row) != null ? b.fmt(perCapita(row)) : '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标准/超标" width="90">
            <template #default="{ row }">
              <span v-if="row.stdAmount != null" style="font-size:12px">标准{{ b.fmt(row.stdAmount) }}</span>
              <el-tag v-if="row.overStd === 1" type="danger" size="small">超标</el-tag>
              <el-tag v-else-if="row.stdAmount != null" type="success" size="small">合规</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="凭证形态" width="105">
            <template #default="{ row }">
              <el-select v-model="row.voucherForm" size="small" style="width: 100%">
                <el-option label="境内发票" value="FAPIAO" />
                <el-option label="境外收据" value="RECEIPT" />
                <el-option label="行程单" value="ITINERARY" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="中文说明" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.cnDesc" size="small" :placeholder="needCnDesc(row) ? '必填：行程/地点/费用/人名' : '境外凭证需填中文说明'" />
              <div v-if="needCnDesc(row) && !(row.cnDesc || '').trim()" style="color:#f56c6c;font-size:11px;line-height:1.3">境外凭证须填中文说明（19号公告税前扣除要件）</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyLine(b, $index)">复制</el-button>
              <el-button link size="small" type="danger" @click="b.form.lines.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="b.form.lines.push(newLine(b))">+ 添加费用明细</el-button>
        <div style="margin-top: 6px">费用合计：<span style="color:#f59a23;font-weight:700">{{ b.fmt(totalAmount(b)) }}</span> 元，税额合计 {{ b.fmt(b.sumTax()) }} 元</div>
      </el-card>

      <!-- ============ 结算信息 ============ -->
      <el-card header="结算信息" style="margin-top: 12px">
<el-form ref="settleFormRef" :model="b.form.bill" :rules="formRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item label="收款金额">
                <span style="color:#f59a23;font-weight:700">{{ b.fmt(payable(b)) }} 元</span>
                <div style="font-size:12px;color:#909399">费用总额 {{ b.fmt(totalAmount(b)) }} − 合计冲销 {{ b.fmt(offsetTotal(b)) }}</div>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="收款方式" prop="extJson.payMethod">
                <el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%">
                  <el-option label="银行转账" value="BANK" /><el-option label="现金" value="CASH" /><el-option label="支票" value="CHECK" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="收款对象" prop="payTarget">
                <el-select v-model="b.form.bill.payTarget" style="width: 100%" placeholder="请选择收款对象">
                  <el-option label="员工" value="EMPLOYEE" />
                  <el-option label="供应商" value="SUPPLIER" />
                  <el-option label="客户" value="CUSTOMER" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户名称" prop="extJson.accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="收款账号" prop="payeeAccountNo">
                <el-input v-model="b.form.bill.payeeAccountNo" placeholder="按收款对象填写对应账号" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行" prop="extJson.bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
          </el-row>

          <!-- P2 #18 跨组织四维度（承担/结算/支付/借款）+ 代理报销 分离 -->
          <el-divider content-position="left" style="margin: 4px 0 12px">跨组织与代理报销（选填）</el-divider>
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item label="结算(报销)单位">
                <el-select v-model="b.form.bill.settleCompanyId" filterable clearable style="width: 100%" placeholder="默认同费用承担单位">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="支付单位">
                <el-select v-model="b.form.bill.payCompanyId" filterable clearable style="width: 100%" placeholder="实际付款主体">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="借款单位">
                <el-select v-model="b.form.bill.loanCompanyId" filterable clearable style="width: 100%" placeholder="默认同费用承担单位">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item label="收款人">
                <UserSelect v-model="b.form.bill.receiverUserId" :users="users" placeholder="默认同报销人；代理报销时选择实际收款人" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert v-if="b.form.bill.receiverUserId && b.form.bill.receiverUserId !== b.form.bill.applyUserId"
            type="warning" :closable="false" show-icon
            :title="`代理报销：报销人为「${applyUserName(b)}」，款项将支付给所选收款人，请确认已获授权`" style="margin-top: 6px" />
        </el-form>
        <el-alert type="info" :closable="false" title="若员工有借款（不含备用金），需在右侧“冲销借款”中强制冲销；合计冲销会从收款金额中扣除" style="margin-top: 6px" />
      </el-card>
    </template>
  </BillWorkbench>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { userApi, orgApi, masterApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { validateForms } from '@/composables/useSubmitGuard'
import { budgetLabel, budgetUnseenLabel, entertainOfLine } from '@/utils/format'
const { saveFormHistory } = useAutoFill()

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const wb = ref(null)
const loading = ref(false)
const users = ref([])
const companies = ref([])
const shareType = ref('COST_CENTER')
const currentUserName = ref(store.user?.username || store.user?.userName || '当前用户')
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]

// ---------- 表头必填：红星/规则/提交闸门同源（constants/billRequired.js），两个 el-form 共用一份 rules ----------
const formRules = computed(() => billRules('CLAIM', ''))
const formRef = ref(null)
const settleFormRef = ref(null)

// ---------------- 海外报账①：外币行汇率（留空自动带出当月汇率 + 系统价只读提示） ----------------
const currencies = ['CNY', 'USD', 'EUR', 'JPY', 'HKD', 'GBP', 'SGD']
function lineCur(row) { return row.currencyCode || 'CNY' }
function isForeignLine(row) { return lineCur(row) !== 'CNY' }
function effRate(row) {
  const r = Number(row.exchangeRate)
  return r > 0 ? r : (row._sysRate || 0)
}
function onCurChange(b, row, cur) {
  row.currencyCode = cur
  if (cur === 'CNY') {
    row.origAmount = null
    row.exchangeRate = null
    row._sysRate = null
    return
  }
  if (row.origAmount == null) row.origAmount = row.amount
  fetchSysRate(b, row)
}
async function fetchSysRate(b, row) {
  row._sysRate = null
  const date = row.expenseDate || b.form.bill.applyDate
  if (!date) return
  try {
    const r = await masterApi.rate.lookup(row.currencyCode, 'CNY', date)
    if (r != null) {
      row._sysRate = Number(r)
      // 汇率留空时仅前端预览本位币金额；保存草稿由后端按表价权威反算（R3）
      if (!(Number(row.exchangeRate) > 0) && row.origAmount != null) {
        row.amount = Math.round(row.origAmount * row._sysRate * 100) / 100
      }
    }
  } catch (e) { /* 无权限或表内无价：放行手填 */ }
}
function onOrigAmt(b, row, v) {
  if (isForeignLine(row)) {
    row.origAmount = v
    const r = effRate(row)
    row.amount = r > 0 ? Math.round(v * r * 100) / 100 : v
  } else {
    // R5-D-055：本币行同步回写原币金额与汇率（与后端 saveDraft 兜底形成双侧保证）
    row.amount = v
    row.origAmount = v
    row.exchangeRate = 1
    recalcLine(b, row)
  }
}
function onRateInput(b, row, v) {
  const r = Number(v)
  row.exchangeRate = r > 0 ? r : null
  if (row.origAmount != null) {
    const use = r > 0 ? r : (row._sysRate || 0)
    row.amount = use > 0 ? Math.round(row.origAmount * use * 100) / 100 : row.origAmount
  }
}

function newLine(b) {
  const l = b.newLine()
  l.priceType = b.form.bill.priceType || null
  // 境外凭证合规⑤（V1.3.14）：凭证形态默认境内发票；选境外收据/行程单时中文说明必填（19号公告）
  l.voucherForm = 'FAPIAO'
  l.cnDesc = ''
  return l
}
function needCnDesc(row) { return row.voucherForm === 'RECEIPT' || row.voucherForm === 'ITINERARY' }
function recalcLine(b, row) {
  if (b.form.bill.priceType) row.priceType = b.form.bill.priceType
  b.recalcLine(row)
  // D-DEC-023：金额变了，人均标准（ext_json.perCapita）要跟着重算，不能只在人数输入时刷新
  syncPerCapitaExt(row)
}
// 录入口径变化：表头统一口径，逐行同步 priceType（行不单独选，保存草稿时随每行提交）
function onPriceTypeChange(b, val) {
  b.form.lines.forEach((l) => { l.priceType = val || null })
}
function onItemChange(b, row, id) { b.onItemChange(row, id) }
function copyLine(b, i) {
  const l = b.form.lines[i]
  b.form.lines.push({ ...l, id: null })
}
function isEntertain(b, row) {
  const it = b.items.find((x) => x.id === row.itemId)
  return it && (it.itemName || '').includes('招待')
}

// ───────── D-DEC-023：招待三项（接待对象 / 接待人数 / 人均标准）随单入库 ─────────
// 原实现把三项绑在 row.entertainTarget / row.entertainCount / row.entertainType 三个「裸字段」上，
// 后端 ExpBillLine 实体没有这三个同名属性 ⇒ 请求体反序列化时被丢弃 ⇒ exp_bill_line.ext_json={} ，
// 详情页三项全空、人均标准校验只能靠审批人口述（12-09 单A/单B 两次实测）。
// 现按真实承载落库：接待对象→attendees、接待人数→person_count、人均标准→ext_json.perCapita，
// 三项另冗余写进 ext_json（后端可据此做标准校验）。
// D-DEC-373：招待费类型改落真实列 exp_bill_line.entertain_type，ext_json.entertainType 同步写一份以兼容历史取数。
function lineExt(row) {
  if (typeof row.extJson === 'string') {
    try { row.extJson = JSON.parse(row.extJson || '{}') } catch { row.extJson = {} }
  }
  if (!row.extJson || typeof row.extJson !== 'object') row.extJson = {}
  return row.extJson
}
function perCapita(row) {
  const n = Number(row.personCount || 0)
  const a = Number(row.amount || 0)
  return n >= 1 && a > 0 ? Math.round((a / n) * 100) / 100 : null
}
function syncPerCapitaExt(row) {
  const ext = lineExt(row)
  const pc = perCapita(row)
  if (pc == null) delete ext.perCapita
  else ext.perCapita = pc
}
function setEntertain(row, patch) {
  if ('attendees' in patch) row.attendees = patch.attendees
  if ('personCount' in patch) row.personCount = patch.personCount
  if ('entertainType' in patch) {
    row.entertainType = patch.entertainType || null
    lineExt(row).entertainType = patch.entertainType || null
  }
  const ext = lineExt(row)
  // ext_json 冗余三项：详情页与服务端标准校验按 ext_json 取数时不必再猜列名
  ext.entertainTarget = row.attendees || null
  ext.entertainCount = row.personCount ?? null
  syncPerCapitaExt(row)
}
// 主数据展示标签：兼容后端 ccCode/ccName（避免选中后因无匹配 option 而回退显示原始 ID 数字）
function ccLabel(c) {
  if (!c) return ''
  return ((c.ccCode || '') + ' ' + (c.ccName || c.costCenterName || '')).trim()
}
function projectLabel(p) {
  if (!p) return ''
  return ((p.projectCode || '') + ' ' + (p.projectName || '')).trim()
}
// 修复：发票勾选生成的明细行（可能暂无费用类型 itemId，但金额/税额已自动填入）
// 必须即时计入总金额/费用合计/收款金额，与 useBill.sumAmount 同口径，否则勾选后合计不动像"无反应"
function totalAmount(b) {
  return b.form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0)
}
function offsetTotal(b) {
  return b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
}
function payable(b) {
  return Math.max(0, Math.round((totalAmount(b) - offsetTotal(b)) * 100) / 100)
}

/** 收款人姓名（代理报销提示用） */
function applyUserName(b) {
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return u ? (u.nickName || u.userName) : (b.form.bill.applyUserName || '-')
}

async function onBeforeSubmit(b) {
  // 表头 + 结算必填闸门（红星同源，见 constants/billRequired.js）
  if (!(await validateForms([formRef, settleFormRef]))) return false
  // 校验：至少一条费用明细
  if (!b.form.lines.filter((l) => l.itemId).length) {
    ElMessage.warning('请至少添加一条费用明细'); return false
  }
  // P0-6 防付错的后端兜底口径（assertPayeeAccount）：payTarget 驱动 payeeAccountNo，已由红星+闸门覆盖
  // 收款账号同步到 extJson（兼容既有打印/导出口径）
  if (b.form.bill.payeeAccountNo) b.form.bill.extJson.accountNo = b.form.bill.payeeAccountNo
  // 费用承担：把每行的成本中心/项目/预算科目同步到分摊信息（exp_bill_share 持久化）
  b.form.shares = b.form.lines
    .filter((l) => l.itemId && (l.costCenterId || l.projectId))
    .map((l) => ({
      costCenterId: shareType.value === 'COST_CENTER' ? l.costCenterId : null,
      projectId: shareType.value === 'PROJECT' ? l.projectId : null,
      budgetItemId: l.budgetItemId,
      shareAmount: l.amount || 0,
      shareType: shareType.value,
      remark: l.remark || '',
    }))
  // 逐行必填校验（与表头 * 号对齐）：费用类型/金额>0/预算科目/承担对象；招待费行类型+人数
  for (const l of b.form.lines) {
    if (!l.itemId) continue // 完全空白行忽略，交给下方"至少一条"校验
    if (!(Number(l.amount) > 0)) {
      ElMessage.warning('费用明细存在原币金额为空或为 0 的行，请补充金额'); return false
    }
    if (!l.budgetItemId) {
      ElMessage.warning('费用明细必须选择预算科目'); return false
    }
    // 承担对象按当前口径校验（单选成本中心或项目）
    if (shareType.value === 'COST_CENTER' && !l.costCenterId) {
      ElMessage.warning('费用明细必须选择成本中心'); return false
    }
    if (shareType.value === 'PROJECT' && !l.projectId) {
      ElMessage.warning('费用明细必须选择项目'); return false
    }
    if (isEntertain(b, l)) {
      // D-DEC-023：三项承载＝entertain_type 列 / attendees / person_count
      // D-DEC-373：历史草稿类型只在 ext_json 里，提交前回填进列，否则随单丢掉
      if (!l.entertainType && lineExt(l).entertainType) l.entertainType = lineExt(l).entertainType
      if (!l.entertainType) { ElMessage.warning('招待费明细必须选择招待费类型'); return false }
      if (!(Number(l.personCount) >= 1)) { ElMessage.warning('招待费明细必须填写人数（≥1）'); return false }
      if (!(l.attendees || '').trim()) { ElMessage.warning('招待费明细必须填写接待对象'); return false }
      syncPerCapitaExt(l)
    }
  }
  // 境外凭证合规⑤（V1.3.14）：境外收据/行程单必填中文说明（后端 submit 同口径兜底）
  if (b.form.lines.some((l) => l.itemId && needCnDesc(l) && !(l.cnDesc || '').trim())) {
    ElMessage.warning('境外收据/行程单须填写中文说明（19号公告税前扣除要件）'); return false
  }
  return true
}

async function onApplicantChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}

const applicantDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})
const applicantCompany = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.companyName || u.company)) || '—'
})

async function openRowDetail(row) {
  // 空白/未选费用类型的行双击无意义：给出提示，避免弹出满屏空字段
  if (!row || !row.itemId) {
    ElMessage.info('请先选择费用类型，再查看该行详情')
    return
  }
  const b = wb.value && wb.value.b
  const it = (b?.items.find((x) => x.id === row.itemId)?.itemName) || '—'
  const cc = shareType.value === 'COST_CENTER'
    ? ccLabel(b?.costCenters.find((x) => x.id === row.costCenterId))
    : projectLabel(b?.projects.find((x) => x.id === row.projectId))
  const bg = b?.budgets.find((x) => String(x.id) === String(row.budgetItemId))
  const budget = bg ? budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects }) : (budgetUnseenLabel(row.budgetItemId) || '—')
  const lines = [
    ['费用类型', it],
    ['成本中心 / 项目', cc || '—'],
    ['预算科目', budget],
    ['费用日期', row.expenseDate || '—'],
    ['原币金额', `${b?.fmt ? b.fmt(row.amount) : row.amount} 元`],
    ['税率', row.taxRate != null ? row.taxRate + '%' : '—'],
    ['税额', (b?.fmt ? b.fmt(row.taxAmount) : row.taxAmount) + ' 元'],
    ['费用事由', row.remark || '—'],
    ['招待费类型', { BUSINESS: '商务宴请', WORK: '工作宴请' }[entertainOfLine(row).type] || '—'],
    ['接待对象', row.attendees || '—'],
    ['人数', row.personCount || '—'],
    ['人均标准', perCapita(row) != null ? `${b?.fmt ? b.fmt(perCapita(row)) : perCapita(row)} 元/人` : '—'],
    ['标准/超标', row.overStd === 1 ? '超标（标准 ' + (b?.fmt ? b.fmt(row.stdAmount) : row.stdAmount) + '）' : (row.stdAmount != null ? '合规' : '—')],
  ]
  const items = lines.filter(([, v]) => v && v !== '—')
  if (!items.length) return
  const { h } = await import('vue')
  // Element Plus 契约：alert 的 message 直接传 VNode（{__isVNode, default} 对象会被 JSON.stringify
  // 成一屏节点 JSON——与 format.js showRowDetail 曾踩的同一坑）
  ElMessageBox.alert(
    h('div', { class: 'row-detail' },
      items.map(([k, v]) => h('div', { class: 'row-detail-item' }, [
        h('span', { class: 'rd-key' }, k + '：'),
        h('span', { class: 'rd-val' }, v),
      ]))),
    '费用行详情', { customClass: 'row-detail-dialog' })
}

const route = useRoute()
const router = useRouter()
onMounted(async () => {
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      router.replace({ query: {} })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
    users.value = list
currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '当前用户'
    const b = wb.value && wb.value.b
    const uid = store.user?.userId ?? store.user?.id
    if (b && !b.form.bill.applyUserId) {
      // P1-5 默认申请人 = 当前登录人，严格匹配 me.id，不回退 list[0]
      const me = uid ? list.find((x) => x.id === uid) : null
      if (me) {
        b.form.bill.applyUserId = me.id
        b.form.bill.applyUserName = me.nickName || me.userName
      } else {
        b.form.bill.applyUserId = null
        b.form.bill.applyUserName = ''
        if (uid != null) ElMessage.warning('未在用户列表中匹配到当前登录人，请手动选择申请人')
      }
    }
    if (b && b.form.bill.title === '') b.form.bill.title = '员工费用报销'
    if (b && !b.form.bill.payTarget) b.form.bill.payTarget = 'EMPLOYEE'
  } catch (e) { /* ignore */ }
  // P2 跨组织四维度：公司树扁平化，供结算单位/支付单位选择
  try {
    const tree = (await orgApi.companyTree()) || []
    const flat = []
    const walk = (ns) => (ns || []).forEach((n) => {
      flat.push({ id: n.id, name: n.name || n.companyName || n.label })
      walk(n.children)
    })
    walk(tree)
    companies.value = flat
  } catch (e) { /* ignore */ }
})
</script>
