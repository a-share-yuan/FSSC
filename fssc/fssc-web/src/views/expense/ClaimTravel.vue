<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <BillWorkbench ref="wb" bill-type="CLAIM" sub-type="TRAVEL" :claim="true" :travel="true" :before-submit="onBeforeSubmit" :on-apply-pick="onApplyPick">
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
            <el-col :xs="24" :md="8"><el-form-item label="申请人部门"><el-input :model-value="applicantDept" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="申请人公司"><el-input :model-value="applicantCompany" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="币种" prop="currencyCode"><el-select v-model="b.form.bill.currencyCode" style="width: 100%"><el-option label="人民币 CNY" value="CNY" /><el-option label="美元 USD" value="USD" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="总金额"><span style="color:#f59a23;font-weight:700;font-size:15px">{{ b.fmt(totalAmount(b)) }} 元</span></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="预算费用年度" prop="periodYear"><el-select v-model="b.form.bill.periodYear" style="width: 100%"><el-option v-for="y in years" :key="y" :label="y + ' 年'" :value="y" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="纸质附件张数" prop="extJson.paperAttachCount"><el-input-number v-model="b.form.bill.extJson.paperAttachCount" :min="0" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="报销类型" prop="extJson.reimburseType">
                <el-select v-model="b.form.bill.extJson.reimburseType" style="width: 100%">
                  <el-option label="差旅" value="TRAVEL" />
                  <el-option label="培训（涉及出差）" value="TRAIN_TRAVEL" />
                  <el-option label="培训（不涉及出差）" value="TRAIN" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="出差类型" prop="extJson.travelType">
                <el-select v-model="b.form.bill.extJson.travelType" style="width: 100%">
                  <el-option label="国内" value="DOMESTIC" /><el-option label="国外" value="ABROAD" /><el-option label="三地" value="TRIAREA" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="是否含招待费">
                <el-select v-model="b.form.bill.extJson.hasEntertain" style="width: 100%"><el-option label="否" :value="false" /><el-option label="是" :value="true" /></el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="标题" prop="title"><el-input v-model="b.form.bill.title" placeholder="差旅培训报销标题" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="单据说明" prop="remark"><el-input v-model="b.form.bill.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
            <!-- 超申请 -->
            <el-col :xs="24">
              <el-form-item label="超申请内容"><el-input :model-value="overApplyContent(b)" disabled /></el-form-item>
            </el-col>
            <el-col v-if="isOverApply(b)" :xs="24">
              <el-form-item label="超申请说明" prop="extJson.overApplyReason"><el-input v-model="b.form.bill.extJson.overApplyReason" placeholder="报销金额超过申请金额时必填" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-alert type="info" :closable="false" title="提示：必须关联一张已审批通过的事前申请单（右侧“关联事前申请”）；报销金额超过申请金额时需填写“超申请说明”并走长审批流程" />
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 费用详情（按类别 tab） ============ -->
      <el-card header="费用详情" style="margin-top: 12px">
        <el-tabs v-model="activeCat" type="card">
          <el-tab-pane label="全部" name="ALL" />
          <el-tab-pane label="培训费" name="TRAIN_FEE" />
          <el-tab-pane label="住宿费" name="HOTEL" />
          <el-tab-pane label="长途交通费" name="TRAFFIC" />
          <el-tab-pane label="市内交通费" name="LOCAL_TRAFFIC" />
          <el-tab-pane label="餐补" name="MEAL" />
          <el-tab-pane label="退改签" name="CHANGE" />
          <el-tab-pane v-if="b.form.bill.extJson.travelType === 'ABROAD'" label="签证费" name="VISA" />
          <el-tab-pane label="其他费用" name="OTHER" />
        </el-tabs>
        <el-table :data="filteredLines(b)" size="small" border style="margin-top: 8px" empty-text="暂无数据" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column label="费用类别" width="120"><template #header><span style="color:#f56c6c">*</span>费用类别</template>
            <template #default="{ row }">
              <el-select v-model="row.category" style="width: 100%">
                <el-option v-for="c in cats" :key="c.v" :label="c.label" :value="c.v" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="费用类型" width="140"><template #header><span style="color:#f56c6c">*</span>费用类型</template>
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(b, row, id)">
                <el-option v-for="it in b.items" :key="it.id" :label="it.itemName" :value="it.id" />
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
          <el-table-column v-if="isTrafficCat(row)" label="出发地" width="110">
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable clearable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="isTrafficCat(row)" label="目的地" width="110">
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable clearable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="isTrafficCat(row)" label="出发/结束日期" width="220">
            <template #default="{ row }">
              <el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" />
              <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" />
            </template>
          </el-table-column>
          <el-table-column v-if="row && row.category === 'HOTEL'" label="出差地/房间数" width="160">
            <template #default="{ row }">
              <el-select v-model="row.toCityId" filterable clearable style="width: 90px"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select>
              <el-input-number v-model="row.roomCount" :min="1" style="width: 60px" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110"><template #header><span style="color:#f56c6c">*</span>金额</template>
            <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(b, row)" /></template>
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
          <el-table-column label="备注" min-width="100">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyLine(b, $index)">复制</el-button>
              <el-button link size="small" type="danger" @click="b.form.lines.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addLine(b)">+ 添加费用明细</el-button>
        <div style="margin-top: 6px">费用合计：<span style="color:#f59a23;font-weight:700">{{ b.fmt(totalAmount(b)) }}</span> 元</div>
      </el-card>

      <!-- ============ 行程信息（差旅/培训涉及出差时） ============ -->
      <el-card v-if="b.form.bill.extJson.reimburseType !== 'TRAIN'" header="行程信息" style="margin-top: 12px">
        <el-table :data="b.form.travels" size="small" border empty-text="暂无数据" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column label="出行人" width="100">
            <template #default="{ row }"><el-select v-model="row.travelerName" filterable style="width: 100%" placeholder="选择出差人员"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" /></el-select></template>
          </el-table-column>
          <el-table-column label="出发地" width="110">
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column label="目的地" width="110">
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column label="目的地国家" width="120">
            <template #default="{ row }"><el-input v-model="row.destCountry" placeholder="留空=国内" /></template>
          </el-table-column>
          <el-table-column label="起/止日期" width="220">
            <template #default="{ row }">
              <el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" @change="calcTravel(b, row)" />
              <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" @change="calcTravel(b, row)" />
            </template>
          </el-table-column>
          <el-table-column label="交通方式" width="100">
            <template #default="{ row }"><el-select v-model="row.trafficType" style="width: 100%"><el-option label="飞机" value="AIR" /><el-option label="高铁" value="RAIL" /><el-option label="火车" value="TRAIN" /><el-option label="汽车" value="CAR" /></el-select></template>
          </el-table-column>
          <el-table-column label="住宿费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="交通费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.trafficAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="补贴/日×天" width="100">
            <template #default="{ row }"><el-input-number v-model="row.allowanceAmount" :min="0" :precision="2" style="width: 100%" placeholder="空=自动" /></template>
          </el-table-column>
          <el-table-column label="预估天数" width="80">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="b.form.travels.splice($index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="b.form.travels.push(b.newTravel())">+ 添加行程</el-button>
        <el-alert style="margin-top: 6px" type="info" :closable="false"
          title="提示：目的地国家填写后（非空且不含“中国”）自动按境外标准校验超标，并按 预估天数×补贴/日 自动核算出差补贴（留空补贴时生效）；起/止日期变化自动预估天数（首末日全算）" />
      </el-card>

      <!-- ============ 结算信息 ============ -->
      <el-card header="结算信息" style="margin-top: 12px">
        <el-form ref="settleFormRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item label="收款金额">
                <span style="color:#f59a23;font-weight:700">{{ b.fmt(payable(b)) }} 元</span>
                <div style="font-size:12px;color:#909399">费用总额 {{ b.fmt(totalAmount(b)) }} − 合计冲销 {{ b.fmt(offsetTotal(b)) }}</div>
              </el-form-item>
            </el-col>
<el-col :xs="24" :md="8">
              <el-form-item label="收款方式" prop="extJson.payMethod"><el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%"><el-option label="银行转账" value="BANK" /><el-option label="现金" value="CASH" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="收款对象" prop="payTarget"><el-select v-model="b.form.bill.payTarget" style="width: 100%" placeholder="请选择收款对象"><el-option label="员工" value="EMPLOYEE" /><el-option label="供应商" value="SUPPLIER" /><el-option label="客户" value="CUSTOMER" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户类型"><el-input :model-value="b.form.bill.payTarget === 'SUPPLIER' || b.form.bill.payTarget === 'CUSTOMER' ? '对公' : '员工'" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="收款账号" prop="payeeAccountNo"><el-input v-model="b.form.bill.payeeAccountNo" placeholder="按收款对象填写对应账号（必填）" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户名称" prop="extJson.accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账号"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>
  </BillWorkbench>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { userApi, billApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { showRowDetail, budgetLabel, budgetUnseenLabel } from '@/utils/format'
const { saveFormHistory } = useAutoFill()
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const wb = ref(null)
// 表头红星/规则/闸门同源（constants/billRequired.js），差旅页特有的三个下拉再叠加本地规则
const formRef = ref(null)
const settleFormRef = ref(null)
const formRules = computed(() => ({
  ...billRules('CLAIM', 'TRAVEL'),
  'extJson.reimburseType': [{ required: true, message: '请选择报销类型' }],
  'extJson.travelType': [{ required: true, message: '请选择出差类型' }],
  'extJson.overApplyReason': commonRules.required('请填写超申请说明'),
}))
const loading = ref(false)
const users = ref([])
const activeCat = ref('ALL')
const currentUserName = ref(store.user?.username || store.user?.userName || '当前用户')
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]
const cats = [
  { v: 'TRAIN_FEE', label: '培训费' }, { v: 'HOTEL', label: '住宿费' }, { v: 'TRAFFIC', label: '长途交通费' },
  { v: 'LOCAL_TRAFFIC', label: '市内交通费' }, { v: 'MEAL', label: '餐补' }, { v: 'CHANGE', label: '退改签' },
  { v: 'VISA', label: '签证费' }, { v: 'OTHER', label: '其他费用' },
]

// 境外凭证合规⑤（V1.3.14）：凭证形态（境内发票/境外收据/行程单）+ 中文说明（19号公告税前扣除要件）
function newLine(b) { return Object.assign(b.newLine(), { category: 'TRAFFIC', roomCount: 1, endDate: '', fromCityId: null, toCityId: null, voucherForm: 'FAPIAO', cnDesc: '' }) }
function needCnDesc(row) { return row.voucherForm === 'RECEIPT' || row.voucherForm === 'ITINERARY' }
function addLine(b) { b.form.lines.push(newLine(b)) }
function copyLine(b, i) { b.form.lines.push({ ...b.form.lines[i], id: null }) }
function recalcLine(b, row) { b.recalcLine(row) }
function onItemChange(b, row, id) { b.onItemChange(row, id) }
function isTrafficCat(row) { return row && ['TRAFFIC', 'LOCAL_TRAFFIC'].includes(row.category) }
function filteredLines(b) {
  if (activeCat.value === 'ALL') return b.form.lines
  return b.form.lines.filter((l) => l.category === activeCat.value)
}
function calcTravel(b, row) {
  if (row.beginDate && row.endDate) {
    row.days = Math.max(1, Math.round((new Date(row.endDate) - new Date(row.beginDate)) / 86400000) + 1)
  }
}
// 同口径：发票勾选生成的明细行（暂无 itemId 但金额已填）即时计入合计
function totalAmount(b) {
  return b.form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0)
}
function offsetTotal(b) {
  return b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
}
function payable(b) { return Math.max(0, Math.round((totalAmount(b) - offsetTotal(b)) * 100) / 100) }

// 超申请：报销总额 > 关联申请单可冲抵金额
function applyAmount(b) {
  return b.form.relations.reduce((a, r) => a + Number(r.useAmount || 0), 0)
}
function isOverApply(b) { return applyAmount(b) > 0 && totalAmount(b) > applyAmount(b) }
function overApplyContent(b) {
  if (!b.form.relations.length) return '（请先在右侧关联事前申请单）'
  if (isOverApply(b)) return `报销总额 ${b.fmt(totalAmount(b))} 元超过申请金额 ${b.fmt(applyAmount(b))} 元，需填写超申请说明`
  return `报销总额 ${b.fmt(totalAmount(b))} 元，申请金额 ${b.fmt(applyAmount(b))} 元，未超申请`
}

async function onBeforeSubmit(b) {
  // 表头/结算必填闸门：与红星同源，漏填即中止、不发请求
  if (!(await validateForms([formRef, settleFormRef]))) return false
  if (!b.form.relations.length) {
    ElMessage.warning('差旅培训报销必须关联一张事前申请单（右侧“关联事前申请”）'); return false
  }
  if (!b.form.lines.filter((l) => l.itemId).length) {
    ElMessage.warning('请至少添加一条费用明细'); return false
  }
  // 境外凭证合规⑤（V1.3.14）：境外收据/行程单必填中文说明（后端 submit 同口径兜底）
  if (b.form.lines.some((l) => l.itemId && needCnDesc(l) && !(l.cnDesc || '').trim())) {
    ElMessage.warning('境外收据/行程单须填写中文说明（19号公告税前扣除要件）'); return false
  }
  b.form.bill.extJson.relations = b.form.relations.map((r) => ({ sourceBillId: r.sourceBillId, useAmount: r.useAmount }))
  return true
}

// 关联申请单后自动带入费用详情/分摊：根据分摊规则将申请单分摊信息带入各费用详情
function catOf(itemName) {
  const n = itemName || ''
  if (n.includes('培训')) return 'TRAIN_FEE'
  if (n.includes('住宿')) return 'HOTEL'
  if (n.includes('市内')) return 'LOCAL_TRAFFIC'
  if (n.includes('交通') || n.includes('机票') || n.includes('火车') || n.includes('高铁')) return 'TRAFFIC'
  if (n.includes('餐')) return 'MEAL'
  if (n.includes('退改') || n.includes('签')) return 'CHANGE'
  return 'OTHER'
}
// category 不是数据库列：草稿/驳回重提回填的行按费用项名补齐分类，否则「住宿费」等分类签整表为空，
// 看起来就像明细金额丢了。
watch(() => wb.value?.b?.form.lines, (lines) => {
  ;(lines || []).forEach((l) => { if (!l.category) l.category = catOf(l.itemName) })
})
async function onApplyPick(row, form) {
  try {
    const d = await billApi.detail(row.id)
    const srcLines = (d.lines || []).filter((l) => l.itemId)
    if (!srcLines.length) return
    try {
      await ElMessageBox.confirm('将申请单费用明细按分摊规则带入本单，会覆盖已生成的费用详情，是否继续？', '关联申请单', { type: 'warning' })
    } catch (e) { return }
    form.lines = srcLines.map((l) => Object.assign(newLine(wb.value.b), {
      itemId: l.itemId, itemName: l.itemName, amount: l.amount || 0, budgetItemId: l.budgetItemId || null,
      // D-DEC-361：带入日期与后端落库时区一致，走 useBill.today()（本地时钟），不再用 UTC 的 toISOString
      expenseDate: l.expenseDate || wb.value.b.today(), category: catOf(l.itemName), remark: l.remark || '',
    }))
    form.shares = (d.shares || []).map((s) => ({
      costCenterId: s.costCenterId, projectId: s.projectId, budgetItemId: s.budgetItemId,
      shareAmount: s.shareAmount || 0, shareType: s.shareType, remark: s.remark || '',
    }))
    if (d.travels && d.travels.length) form.travels = d.travels.map((t) => ({ ...t, id: null }))
    ElMessage.success('已带入申请单费用明细与分摊信息')
  } catch (e) { ElMessage.error(e.message) }
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

function openRowDetail(row) {
  if (!row || !Object.keys(row).length) {
    ElMessage.info('请先填写该行后再查看详情')
    return
  }
  // 双行详情：非原始字段 dump，友好展示既有值
  return showRowDetail(row, {
    title: '行详情',
    labels: {
      itemId: '费用类型ID', itemName: '费用类型', costCenterName: '成本中心', projectName: '项目',
      budgetItemId: '预算科目ID', amount: '金额', taxRate: '税率%', taxAmount: '税额', netAmount: '不含税',
      expenseDate: '费用日期', remark: '事由', overStd: '是否超标', stdAmount: '标准金额',
    },
  })
}

onMounted(async () => {
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
    if (b && b.form.bill.title === '') b.form.bill.title = '差旅培训报销'
  } catch (e) { /* ignore */ }
})
</script>
