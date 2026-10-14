<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <!-- D-V3-146：sub-type 动态绑定路由 props（/corp-pay/settle → SETTLE）。
       原写死 PREPAY 导致 SETTLE 深链落单 bill_sub_type 恒 PREPAY；
       onTypeChange/onBeforeSubmit 既有 patch 逻辑保持不变，此处仅为初始化口径对齐。 -->
  <BillWorkbench ref="wb" bill-type="PAY" :sub-type="subType" :pay="true" :before-submit="onBeforeSubmit" :on-counter-pick="onCounterChange">
    <template #create="b">
      <!-- ============ 单据类型 ============ -->
      <el-card v-if="!isSingle" header="单据类型">
        <el-radio-group v-model="subType" @change="onTypeChange(b)">
          <el-radio-button value="PREPAY">费用预付款申请</el-radio-button>
          <el-radio-button value="SETTLE">费用对公结算</el-radio-button>
        </el-radio-group>
      </el-card>

      <!-- ============ 子类型业务说明 ============ -->
      <el-alert
        :title="subTypeDesc.title"
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      >
        <template #default>
          <div style="line-height: 1.7">{{ subTypeDesc.detail }}</div>
          <div v-if="subTypeDesc.flow" style="margin-top: 4px; color: #909399; font-size: 12px">
            <el-icon style="vertical-align: middle"><InfoFilled /></el-icon>
            流程：{{ subTypeDesc.flow }}
          </div>
        </template>
      </el-alert>

      <!-- ============ 单据概要 ============ -->
      <el-card header="单据概要" style="margin-top: 12px">
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
            <el-col :xs="24" :md="8"><el-form-item label="签订公司"><el-input :model-value="applicantCompany" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="币种" prop="currencyCode"><el-select v-model="b.form.bill.currencyCode" style="width: 100%"><el-option label="人民币 CNY" value="CNY" /><el-option label="美元 USD" value="USD" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="预算费用年度" prop="periodYear"><el-select v-model="b.form.bill.periodYear" style="width: 100%"><el-option v-for="y in years" :key="y" :label="y + ' 年'" :value="y" /></el-select></el-form-item>
            </el-col>
            <!-- PREPAY / SETTLE: 付款类型 + 往来单位 -->
            <el-col v-if="subType === 'PREPAY' || subType === 'SETTLE'" :xs="24" :md="8">
              <el-form-item label="付款类型" prop="extJson.payType">
                <el-select v-model="b.form.bill.extJson.payType" style="width: 100%">
                  <el-option label="银行转账" value="BANK" />
                  <el-option label="电汇" value="TRANSFER" />
                  <el-option label="现金" value="CASH" />
                  <el-option label="票据" value="BILL" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="subType === 'PREPAY' || subType === 'SETTLE'" :xs="24" :md="8">
              <el-form-item label="往来单位类型">
                <el-radio-group v-model="b.form.bill.extJson.counterType">
                  <el-radio-button value="SUPPLIER">供应商</el-radio-button>
                  <el-radio-button value="CUSTOMER">客户</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col v-if="subType === 'PREPAY' || subType === 'SETTLE'" :xs="24" :md="12">
              <el-form-item label="往来单位" prop="customerId">
                <el-select v-model="b.form.bill.customerId" filterable style="width: 100%" placeholder="选择供应商/客户" @change="(id) => onCounterChange(b, id)">
                  <el-option v-for="c in b.customers" :key="c.id" :label="c.customerName" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="subType === 'PREPAY' || subType === 'SETTLE'" :xs="24" :md="12">
              <el-form-item label="付款合同（框架协议，可选）">
                <el-select v-model="b.form.bill.extJson.contractId" filterable clearable style="width: 100%" placeholder="按往来单位过滤未执行完毕合同">
                  <el-option v-for="c in contracts" :key="c.id" :label="c.contractNo + ' ' + c.contractName + '（剩 ' + fmt(Number(c.amount || 0) - Number(c.paidAmount || 0)) + '）'" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- SETTLE: 采购订单 + 收货单（三单匹配） -->
            <el-col v-if="subType === 'SETTLE'" :xs="24" :md="12">
              <el-form-item label="采购订单（三单匹配）">
                <el-select v-model="b.form.bill.extJson.poId" filterable clearable style="width: 100%" placeholder="选择采购订单（PO）" @change="(id) => onPoPick(b, id)">
                  <el-option v-for="po in b.pos" :key="po.id" :label="po.poNo + ' ' + po.supplierName + '（剩 ' + fmt(Number(po.amount || 0) - Number(po.receivedAmount || 0)) + '）'" :value="po.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="subType === 'SETTLE'" :xs="24" :md="12">
              <el-form-item label="收货单（三单匹配）">
                <el-select v-model="b.form.bill.extJson.poReceiptId" filterable clearable style="width: 100%" :placeholder="b.receipts && b.receipts.length ? '选择收货单' : '先选采购订单，或留空走手工关联'" @change="(rid) => onReceiptChange(b, rid)">
                  <el-option v-for="rc in b.receipts" :key="rc.id" :label="rc.receiptNo + ' ' + rc.supplierName + '（' + fmt(rc.amount) + '）'" :value="rc.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="subType === 'PREPAY' || subType === 'SETTLE'" :xs="24" :md="12">
              <el-form-item label="合同已付/总额">
                <el-input :model-value="contractSummary(b)" disabled />
              </el-form-item>
            </el-col>
            <!-- AGENCY / AIR: 发票张数 -->
            <el-col v-if="subType === 'AGENCY' || subType === 'AIR'" :xs="24" :md="8">
              <el-form-item label="发票张数" prop="extJson.invoiceCount"><el-input-number v-model="b.form.bill.extJson.invoiceCount" :min="0" :precision="0" style="width: 100%" /></el-form-item>
            </el-col>
            <!-- AGENCY: 是否项目承担 -->
            <el-col v-if="subType === 'AGENCY'" :xs="24" :md="8">
              <el-form-item label="是否项目承担">
                <el-select v-model="b.form.bill.extJson.projectFlag" style="width: 100%">
                  <el-option label="否（成本中心承担）" :value="false" />
                  <el-option label="是（项目承担）" :value="true" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="标题" prop="title"><el-input v-model="b.form.bill.title" :placeholder="subTypeLabel + '标题'" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="单据说明" prop="remark"><el-input v-model="b.form.bill.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
          </el-row>
          <!-- 往来单位与采购订单指向不同主体时必须显式提示：服务端不派生收款人，静默取其一
               会让落库的收款人/标题与单据真实归属错配，并连带凭证摘要与支付闸门文案错指上一单主体。 -->
          <el-alert v-if="subjectConflict" type="warning" :closable="false" show-icon :title="subjectConflict" style="margin-bottom: 8px" />
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
        <el-table :data="b.form.lines" size="small" border empty-text="暂无数据" @row-dblclick="openTrace">
          <el-table-column label="费用类型" width="150"><template #header><span style="color:#f56c6c">*</span>费用类型</template>
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(b, row, id)">
                <el-option v-for="it in b.items" :key="it.id" :label="it.itemName" :value="it.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="shareType === 'COST_CENTER' ? '成本中心' : '项目'" width="140">
            <template #default="{ row }">
              <el-select v-if="shareType === 'COST_CENTER'" v-model="row.costCenterId" filterable clearable style="width: 100%">
                <el-option v-for="c in b.costCenters" :key="c.id" :label="ccLabel(c)" :value="c.id" />
              </el-select>
              <el-select v-else v-model="row.projectId" filterable clearable style="width: 100%">
                <el-option v-for="p in b.projects" :key="p.id" :label="p.projectName" :value="p.id" />
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
          <el-table-column label="金额" width="110"><template #header><span style="color:#f56c6c">*</span>金额</template>
            <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(b, row)" /></template>
          </el-table-column>
          <!-- SETTLE: 原币金额/汇率 -->
          <el-table-column v-if="subType === 'SETTLE'" label="原币金额" width="110">
            <template #default="{ row }"><el-input-number v-model="row.origAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column v-if="subType === 'SETTLE'" label="汇率" width="90">
            <template #default="{ row }"><el-input-number v-model="row.exchangeRate" :min="0" :precision="4" style="width: 100%" /></template>
          </el-table-column>
          <!-- AGENCY: 含税金额/税率 -->
          <el-table-column v-if="subType === 'AGENCY'" label="含税金额" width="110">
            <template #default="{ row }"><el-input-number v-model="row.taxAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column v-if="subType === 'AGENCY'" label="税率(%)" width="90">
            <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <!-- AIR: 机票详情 -->
          <template v-if="subType === 'AIR'">
            <el-table-column label="乘机人" width="90">
              <template #default="{ row }"><el-input v-model="row.extJson.airPassenger" size="small" /></template>
            </el-table-column>
            <el-table-column label="出发地" width="90">
              <template #default="{ row }"><el-input v-model="row.extJson.airFrom" size="small" /></template>
            </el-table-column>
            <el-table-column label="目的地" width="90">
              <template #default="{ row }"><el-input v-model="row.extJson.airTo" size="small" /></template>
            </el-table-column>
            <el-table-column label="成交净价" width="95">
              <template #default="{ row }"><el-input-number v-model="row.extJson.airNet" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="税" width="90">
              <template #default="{ row }"><el-input-number v-model="row.extJson.airTax" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="退票费" width="90">
              <template #default="{ row }"><el-input-number v-model="row.extJson.airRefund" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="实收实付" width="95">
              <template #default="{ row }"><el-input-number v-model="row.extJson.airPaid" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column label="事前申请单" width="120">
              <template #default="{ row }"><el-input v-model="row.extJson.applyNo" size="small" /></template>
            </el-table-column>
          </template>
          <el-table-column label="时间" width="120">
            <template #default="{ row }"><el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="费用事由" min-width="110">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeLine(b, $index)">删</el-button></template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addLine(b)">+ 添加费用明细</el-button>
        <div style="margin-top: 6px">费用合计：<span style="color:#f59a23;font-weight:700">{{ b.fmt(totalAmount(b)) }}</span> 元</div>
        <div style="margin-top: 2px; color:#606266">大写：{{ b.form.bill.amountCn || '待保存生成' }}</div>
      </el-card>

      <!-- ============ 付款信息 ============ -->
      <el-card header="付款信息" style="margin-top: 12px">
        <el-form ref="payFormRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item :label="subType === 'AGENCY' ? '应付金额' : '收款金额'">
                <span style="color:#f59a23;font-weight:700">{{ b.fmt(payable(b)) }} 元</span>
                <div v-if="subType === 'SETTLE'" style="font-size:12px;color:#909399">总额 {{ b.fmt(totalAmount(b)) }} − 冲销预付款 {{ b.fmt(prepayOffsetTotal(b)) }}</div>
              </el-form-item>
            </el-col>
<el-col :xs="24" :md="8">
              <el-form-item label="收款对象" prop="payTarget">
                <el-select v-model="b.form.bill.payTarget" style="width: 100%" placeholder="请选择收款对象">
                  <el-option label="员工" value="EMPLOYEE" /><el-option label="供应商" value="SUPPLIER" /><el-option label="客户" value="CUSTOMER" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="subType === 'AGENCY' ? '付款方式' : '收款方式'" prop="extJson.payMethod">
                <el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%">
                  <el-option label="银行转账" value="BANK" /><el-option label="电汇" value="TELEGRAPHIC" /><el-option label="承兑汇票" value="DRAFT" /><el-option label="现金" value="CASH" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="账户类型"><el-input :model-value="subType === 'AGENCY' ? '代收代付' : '供应商'" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="subType === 'AGENCY' ? '收款单位' : '账户名称（供应商）'" prop="payeeName">
                <el-select v-model="b.form.bill.payeeName" filterable style="width: 100%" @change="onPayeeChange(b)">
                  <el-option v-for="c in supplierCustomers(b)" :key="c.id" :label="c.customerName" :value="c.customerName" />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 后端 assertPayeeAccount：收款对象非空则收款账号必填（防付错），故此处与「*收款对象」同为必填 -->
            <el-col :xs="24" :md="8"><el-form-item :label="subType === 'AGENCY' ? '银行账号' : '账号'" prop="extJson.payeeAccountNo"><el-input v-model="b.form.bill.extJson.payeeAccountNo" placeholder="请输入收款方银行账号" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行"><el-input v-model="b.form.bill.extJson.payeeBankName" /></el-form-item></el-col>
          </el-row>
          <el-alert v-if="blacklistHit" type="error" :closable="false" :title="blacklistHit" style="margin-top: 8px" />
        </el-form>
      </el-card>

      <!-- 冲销预付款（SETTLE 独立子组件） -->
      <PayPrepayOffset v-if="subType === 'SETTLE'" :customer-id="b.form.bill.customerId" ref="prepayOffsetRef" />
    </template>
  </BillWorkbench>

  <!-- 三单匹配 · 异常派单（独立子组件） -->
  <PayMatchRoute ref="matchRouteRef" />
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="EXP_PAY" />
</template>

<script setup>
import { ref, computed, watch, provide } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import PayMatchRoute from './PayMatchRoute.vue'
import PayPrepayOffset from './PayPrepayOffset.vue'
import { userApi, billApi, contractApi, reportApi, purchaseApi, supplierApi, store } from '../../api'
// D-DEC-346：收货单候选是辅助加载，需 silent（无 po:receipt:list 时不可弹误导性报错），
// 而 purchaseApi.receiptPage 未开 cfg 位，故按本页既有写法直取共享 http 实例。
import { http } from '../../request'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { validateForms } from '@/composables/useSubmitGuard'
import { ccLabel, budgetLabel, budgetUnseenLabel } from '@/utils/format'
import { useKeyboard } from '@/composables/useKeyboard'
// R5-13: 快捷键
useKeyboard({
  'ctrl+s': () => { try { wb.value?.b?.saveDraft() } catch { /* noop */ } },
  'ctrl+r': () => location.reload(),
})
const { saveFormHistory } = useAutoFill()
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const wb = ref(null)
// provide wb ref for child components (PayPrepayOffset) to inject
provide('wb', wb)

const props = defineProps({ subType: { type: String, default: 'PREPAY' } })
// P: 一 TAB = 一菜单——由独立子路由(费用预付款/对公结算)深链进入时，隐藏单据类型切换按钮。
// R5-D-080(b)：AGENCY(代收代付)/AIR(机票结算) 在流程登记层零登记（BillRouteRegistry/ExpenseWfEventListener
// 无该两子类型）且库内 0 行落库，依唯一荐法(b) 下线该两子类型入口与其深链路由，收敛 isSingle 路径集。
const route = useRoute()
const router = useRouter()
const isSingle = computed(() => ['/corp-pay/prepay', '/corp-pay/settle'].includes(route.path))
const subType = ref(props.subType)
watch(() => props.subType, (v) => { if (v) subType.value = v })
// 表头红星 + 校验规则与提交闸门同源（constants/billRequired.js）；两个 el-form 共用同一份 rules
const formRef = ref(null)
const payFormRef = ref(null)
const formRules = computed(() => billRules('PAY', subType.value))
const shareType = ref('COST_CENTER')
const users = ref([])
const currentUserName = ref(store.user?.username || store.user?.userName || '当前用户')
const contracts = ref([])
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]
const blacklistHit = ref('')
const subjectConflict = ref('')
// 标题/单据说明的「本页自动生成的那一份」哨兵：只有空值、仍是哨兵值、或仍是子类型默认词
// 才允许按主体重新生成——用户手写过的、草稿编辑带回来的一律保留，改名不冲掉用户输入。
const autoTitle = ref('')
const autoRemark = ref('')
const matchRouteRef = ref(null)
const prepayOffsetRef = ref(null)

const applicantCompany = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.companyName || u.company)) || '—'
})

const subTypeLabel = computed(() => ({ PREPAY: '费用预付款', SETTLE: '对公结算', AGENCY: '代收代付', AIR: '机票结算' })[subType.value] || '对公支付')

const subTypeDesc = computed(() => ({
  PREPAY: {
    title: '费用预付款申请',
    detail: '用于在费用实际发生前向供应商预付资金。需关联往来单位（供应商/客户），可选择关联合同框架协议。审批通过后进入出纳付款环节，预付款可在后续对公结算时冲销核销。',
    flow: '制单 → 提交审批 → 出纳付款 → 预付款挂账 → 后续对公结算时冲销',
  },
  SETTLE: {
    title: '费用对公结算',
    detail: '用于完成采购订单与收货单的三单匹配结算。支持原币金额与汇率换算，可冲销此前已支付的预付款。结算完成后进入付款审批与凭证记账流程。',
    flow: '制单（选PO+收货单） → 三单匹配 → 冲销预付款 → 提交审批 → 出纳付款 → 自动记账',
  },
  AGENCY: {
    title: '代收代付报销单',
    detail: '用于处理代收代付款项的报销。需填写发票张数、含税金额与税率，可选择由成本中心或项目承担费用。付款完成后自动生成凭证记账。',
    flow: '制单（填发票+税率） → 提交审批 → 代收代付 → 自动记账',
  },
  AIR: {
    title: '机票结算申请',
    detail: '用于员工差旅机票的费用结算。需逐行填写乘机人、出发地、目的地、成交净价、税费及退票费等明细，关联事前申请单后进入审批流程。',
    flow: '制单（填乘机人+航线+净价） → 关联事前申请 → 提交审批 → 出纳付款 → 记账',
  },
})[subType.value] || { title: '对公支付', detail: '请选择单据类型', flow: '' })

function recalcLine(b, row) { b.recalcLine(row) }
function onItemChange(b, row, id) { b.onItemChange(row, id) }
function addLine(b) {
  const line = b.newLine()
  if (subType.value === 'AIR' && !line.extJson) {
    line.extJson = { airPassenger: '', airFrom: '', airTo: '', airNet: 0, airTax: 0, airRefund: 0, airPaid: 0, applyNo: '' }
  }
  b.form.lines.push(line)
}
async function removeLine(b, i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  b.form.lines.splice(i, 1)
}
function onTypeChange(b) {
  b.form.bill.billSubType = subType.value
  if (b.form.bill.title === '') b.form.bill.title = subTypeLabel.value
  if (subType.value !== 'SETTLE') b.form.bill.extJson.prepayOffsets = []
}
function supplierCustomers(b) {
  return (b.customers || []).filter((c) => (c.customerType || '').toUpperCase().includes('SUPPLIER') || (c.customerType || '') === '')
}
function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function totalAmount(b) {
  return b.form.lines.filter((l) => l.itemId).reduce((a, l) => a + Number(l.amount || 0), 0)
}
function prepayOffsetTotal(b) {
  const arr = b.form.bill.extJson.prepayOffsets || []
  return arr.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
}
function payable(b) {
  return Math.max(0, Math.round((totalAmount(b) - prepayOffsetTotal(b)) * 100) / 100)
}
function contractSummary(b) {
  const c = contracts.value.find((x) => x.id === b.form.bill.extJson.contractId)
  if (!c) return ''
  return `已付 ${fmt(Number(c.paidAmount || 0))} / 总额 ${fmt(Number(c.amount || 0))}`
}

function onPayeeChange(b) {
  const c = (b.customers || []).find((x) => x.customerName === b.form.bill.payeeName)
  if (c) {
    b.form.bill.extJson.payeeAccountNo = c.accountNo || b.form.bill.extJson.payeeAccountNo
    b.form.bill.extJson.payeeBankName = c.bankName || b.form.bill.extJson.payeeBankName
  }
  if ((b.form.bill.extJson.counterType || '').toUpperCase() === 'SUPPLIER') checkPayeeBlacklist(b.form.bill.payeeName)
}
/** 往来单位＝单据主体，收款人姓名与标题必须跟着主体的权威名字走：
 *  服务端不派生 payee_name/title，落库照前端传值，此处不回写则改名后单据仍带上一单的收款人与标题。 */
function applySubjectName(b, name) {
  if (!name) return
  b.form.bill.payeeName = name
  const label = subTypeLabel.value
  const title = String(b.form.bill.title || '').trim()
  if (!title || b.form.bill.title === autoTitle.value || title === label) {
    autoTitle.value = `${name}${label}`
    b.form.bill.title = autoTitle.value
  }
  const remark = String(b.form.bill.remark || '').trim()
  if (!remark || b.form.bill.remark === autoRemark.value) {
    autoRemark.value = `${label}：${name}`
    b.form.bill.remark = autoRemark.value
  }
}
/** 两个来源都指认了主体且不是同一个 ⇒ 页面提示。
 *  crm_customer 名与 PO 供应商名不同源（互为包含的写法差异不算冲突，避免误报）。
 *  PO 供应商名按 id 从「取过的全量候选」里查，不能只查当前下拉里那几条：
 *  换了往来单位后候选会被重筛，上一单选中的 PO 会从列表里消失，只查 b.pos 就查不到、冲突就漏报了。 */
const poSupplierNames = new Map()
function poSupplierNameOf(b, poId) {
  if (poId == null) return ''
  const inList = (b.pos || []).find((x) => x.id === poId)
  if (inList) return String(inList.supplierName || '').trim()
  return poSupplierNames.get(Number(poId)) || ''
}
function refreshSubjectConflict(b) {
  const c = (b.customers || []).find((x) => x.id === b.form.bill.customerId)
  const cn = String((c && c.customerName) || '').trim()
  const pn = poSupplierNameOf(b, b.form.bill.extJson.poId)
  if (!cn || !pn || cn === pn || cn.includes(pn) || pn.includes(cn)) { subjectConflict.value = ''; return }
  subjectConflict.value = `往来单位「${cn}」与采购订单供应商「${pn}」不是同一主体，请核对后再提交（收款人以往来单位为准）`
}
function onCounterChange(b, id) {
  const c = (b.customers || []).find((x) => x.id === id)
  if (c) {
    b.form.bill.extJson.counterName = c.customerName
    b.form.bill.extJson.counterType = (c.customerType || '').toUpperCase().includes('SUPPLIER') ? 'SUPPLIER' : 'CUSTOMER'
    applySubjectName(b, c.customerName)
    b.form.bill.extJson.payeeAccountNo = c.accountNo || b.form.bill.extJson.payeeAccountNo
    b.form.bill.extJson.payeeBankName = c.bankName || b.form.bill.extJson.payeeBankName
    refreshSubjectConflict(b)
    if (subjectConflict.value) ElMessage.warning(subjectConflict.value)
    // 采购订单(PO)只在 SETTLE(费用对公结算/三单匹配)才有意义。
    // 其他子类型(如 PREPAY 预付)加载它属于无谓请求，且普通制单人往往没有 po:order:list 权限，
    // 会弹出"无该功能/按钮权限：缺少权限：po:order:list"的误导性报错。
    if (subType.value === 'SETTLE') loadPos(b, c.customerName)
  }
}
/** D-DEC-346：slot 下发的 b 每轮渲染都是浅拷贝 ⇒ 对 b.pos/b.receipts 整体赋值会丢。
 *  容器已预声明这两个数组，页面侧只做原地增删，靠引用共享回写。 */
function setArr(target, next) {
  target.splice(0, target.length, ...(next || []))
}
async function loadPos(b, counterName) {
  setArr(b.receipts, [])
  try {
    // silent: 这是辅助性后台加载(为用户准备 PO 下拉)，失败(如无 po:order:list 权限)不应打扰用户
    // D-DEC-346 取数键：此前把「往来单位名」当 keyword 打给 order/page，而后端只按
    // po_no/supplier_name 模糊匹配 —— crm_customer 无 supplier_id 可映射、两套名称不同源
    // （实测「苏州科韵精密设备有限公司」在 crm_customer 里 0 命中）⇒ 下拉恒空。
    // 现改为不带 keyword 取单，名称只做尽力收窄：命中才收窄，落空退回全量，绝不再恒空。
    const r = await purchaseApi.orderPage({ pageNum: 1, pageSize: 100 }, { silent: true })
    const all0 = r.list || []
    // D-V4R-47：已作废（VOID）的采购订单不得再进结算候选——原先只按供应商名收窄，
    // 实测本轮作废的 PO1791909414058 仍挂在第二枚候选上，能把钱结算到一张作废订单上。
    const all = all0.filter((p) => String((p && p.status) || '').toUpperCase() !== 'VOID')
    all0.forEach((p) => { if (p && p.id != null) poSupplierNames.set(Number(p.id), String(p.supplierName || '').trim()) })
    const key = String(counterName || '').trim()
    const named = key
      ? all.filter((p) => {
          const s = String(p.supplierName || '').trim()
          return s && (s.includes(key) || key.includes(s))
        })
      : []
    // D-DEC-346 第二重必空：原「剩余可收>0」是收货页口径，结算时 PO 通常已全量收货
    // ⇒ 该过滤会把要匹配的单全部剔掉，故去除（选项标签本就展示剩余额，由人判断）。
    setArr(b.pos, named.length ? named : all)
    // 草稿重编辑：PO 候选到位后才能判「往来单位 vs PO 供应商」是否同主体，此前无从比
    refreshSubjectConflict(b)
    // 草稿重编辑：单上已指认过 PO 的，按它把收货单候选一并备好
    if (b.form.bill.extJson.poId) loadReceipts(b, b.form.bill.extJson.poId)
    // 刻意不再默认选中 b.pos[0]：三单匹配的 PO 必须由人指认，猜首行会把别人的单挂到本单上
  } catch (e) { setArr(b.pos, []); setArr(b.receipts, []) }
}
/** 用户改选 PO ⇒ 原收货单归属失效，重取候选 */
function onPoPick(b, poId) {
  // D-V4R-27：下拉绑定的是 ext_json.poId（展示/收货单候选都读它），但 exp_bill.po_id 列的取值源
  // 是顶层 bill.poId。保存草稿不经 onBeforeSubmit 的顶层拷贝，若只写 ext_json 则列位为空——
  // 服务端收款方交叉校验看不到 PO、两源不同家也会被放过。这里把「选中那条 PO 的主键」同步进列位字段。
  b.form.bill.poId = b.form.bill.extJson.poId
  b.form.bill.extJson.poReceiptId = null
  loadReceipts(b, poId)
  refreshSubjectConflict(b)
  if (subjectConflict.value) ElMessage.warning(subjectConflict.value)
}
/** D-DEC-346 死控件补齐：b.receipts 此前全仓无赋值点 ⇒「收货单」下拉恒 0 项 */
async function loadReceipts(b, poId) {
  setArr(b.receipts, [])
  const po = b.pos.find((x) => x.id === poId)
  if (!po || !po.poNo) return
  try {
    // 收货单接口无 poId 入参，keyword 命中 po_no；取回后再按 poId 复核，防单号像形串单
    const r = await http.get('/tenant/purchase/receipt/page', {
      params: { pageNum: 1, pageSize: 100, keyword: po.poNo }, silent: true,
    })
    setArr(b.receipts, ((r && r.list) || []).filter((x) => Number(x.poId) === Number(po.id)))
  } catch (e) { setArr(b.receipts, []) }
}
async function onReceiptChange(b, rid) {
  const rc = b.receipts.find((x) => x.id === rid)
  if (rc && rc.poId && !b.form.bill.extJson.poId) {
    b.form.bill.extJson.poId = rc.poId
    // D-V4R-27：由收货单反推出的 PO 主键同样要落进列位字段，保存草稿腿才能被服务端交叉校验看到
    b.form.bill.poId = rc.poId
    refreshSubjectConflict(b)
  }
}
async function checkPayeeBlacklist(payeeName) {
  if (!payeeName) { blacklistHit.value = ''; return }
  blacklistHit.value = ''
  try {
    const r = await supplierApi.page({ pageNum: 1, pageSize: 20, keyword: payeeName })
    const hit = (r.list || []).find((s) => s.blacklist === 1 && (s.supplierName || '').trim() === payeeName.trim())
    if (hit) {
      blacklistHit.value = `${hit.supplierName}(${hit.supplierCode || ''}) 已列入黑名单，禁止对公付款`
      ElMessage.warning(blacklistHit.value)
    }
  } catch (e) { /* ignore */ }
}
async function onApplicantChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}

async function onBeforeSubmit(b) {
  b.form.bill.billSubType = subType.value
  // P0-6 对公付款：extJson 收款账户字段同步到顶层 bill 字段（后端校验读取顶层 payeeAccountNo）
  b.form.bill.payeeAccountNo = b.form.bill.extJson.payeeAccountNo || b.form.bill.payeeAccountNo
  // 表头必填闸门：红星与规则同源（constants/billRequired.js），漏填即中止、不发请求
  if (!(await validateForms([formRef, payFormRef]))) return false
  if (blacklistHit.value) { ElMessage.error(blacklistHit.value + '，无法提交'); return false }
  if ((b.form.bill.extJson.counterType || '').toUpperCase() === 'SUPPLIER') {
    const hit = (b.customers || []).find((c) => c.customerName === b.form.bill.payeeName && c.blacklist === 1)
    if (hit) { ElMessage.error(hit.customerName + ' 已列入黑名单，禁止对公付款'); return false }
  }
  // 往来单位必填与登记表同口径（billRequired：PAY + sub=['PREPAY','SETTLE']）。
  // 代收代付/机票结算页不渲染本单据专属的往来单位选择，若仍强制会把提交拦成无解，故只按已登记类型校验。
  if ((subType.value === 'PREPAY' || subType.value === 'SETTLE') && !b.form.bill.customerId) { ElMessage.warning('请选择往来单位（供应商/客户）'); return false }
  // 提交前复核一次两个来源的主体指向，提示与页面当前状态保持同步（硬闸在服务端，此处只负责少跑一趟）
  refreshSubjectConflict(b)
  if (!b.form.lines.filter((l) => l.itemId).length) { ElMessage.warning('请至少添加一条费用明细'); return false }
  if (subType.value === 'SETTLE') {
    if (b.form.bill.extJson.poId) b.form.bill.poId = b.form.bill.extJson.poId
    if (b.form.bill.extJson.poReceiptId) b.form.bill.poReceiptId = b.form.bill.extJson.poReceiptId
    if (b.form.bill.extJson.contractId) b.form.bill.contractId = b.form.bill.extJson.contractId
  }
  if (subType.value === 'AIR' && b.form.lines.some((l) => l.itemId && !(l.extJson && l.extJson.airPassenger))) {
    ElMessage.warning('机票明细需填写乘机人'); return false
  }
  b.form.shares = b.form.lines
    .filter((l) => l.itemId && (l.costCenterId || l.projectId))
    .map((l) => ({
      costCenterId: shareType.value === 'COST_CENTER' ? l.costCenterId : null,
      projectId: shareType.value === 'PROJECT' ? l.projectId : null,
      budgetItemId: l.budgetItemId, shareAmount: l.amount || 0, shareType: shareType.value, remark: l.remark || '',
    }))
  const arr = b.form.bill.extJson.prepayOffsets || []
  if (arr.length && !b.form.relations.length) {
    b.form.relations = arr.map((o) => ({ sourceBillId: o.billId, sourceBillNo: o.billNo, useAmount: o.offsetAmount, relationType: 'PREPAY_SETTLE' }))
  }
  return true
}

onMounted(async () => {
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单，并把子类型切到单据实际类型
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      const st = wb.value?.b?.form?.bill?.billSubType || wb.value?.b?.form?.bill?.subType
      if (st) subType.value = st
      router.replace({ query: {} })
      // D-V4-007（D-V3-134 余量）：草稿重编辑此前不加载 PO/GRN 候选——
      // loadPos 只挂在 onCounterChange（下拉 change 事件）上，深链载入不经过它，
      // 于是"已选好 PO/收货单的草稿"重开时两列下拉恒空、且看不到已选单的标签，
      // 用户一清空就再也选不回来。现按单上往来单位名补一次候选加载
      // （loadPos 内部会按 extJson.poId 把收货单候选一并备好）。
      const b0 = wb.value?.b
      const cName = b0?.form?.bill?.extJson?.counterName
      if (subType.value === 'SETTLE' && b0 && cName) loadPos(b0, cName)
    }
    // D-V4-007 补充：「我的单据→编辑」直接调 loadBillForEdit（不经深链），同样补候选加载。
    // 包装一次：编辑载入完成后按单上往来单位名补 PO/收货单候选（SETTLE 专属）。
    const wbHost = wb.value
    if (wbHost?.b?.loadBillForEdit && !wbHost.b.__loadPosWrapped) {
      const orig = wbHost.b.loadBillForEdit.bind(wbHost.b)
      wbHost.b.loadBillForEdit = async (row) => {
        const r = await orig(row)
        try {
          if (subType.value === 'SETTLE') {
            const cn = wbHost.b.form?.bill?.extJson?.counterName
            if (cn) await loadPos(wbHost.b, cn)
          }
        } catch { /* 候选加载失败不打断编辑 */ }
        return r
      }
      wbHost.b.__loadPosWrapped = true
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
    // R5-D-082：默认标题随子类型取 subTypeLabel（原先不分 subType 硬编码「费用预付款」，
    // 在 /corp-pay/settle 留空提交也拿预付款标题，与 bill_sub_type 矛盾污染凭证摘要/台账检索）
    if (b && b.form.bill.title === '') b.form.bill.title = subTypeLabel.value
    if (b) {
if (b.form.bill.extJson.payType == null) b.form.bill.extJson.payType = 'BANK'
      if (b.form.bill.extJson.counterType == null) b.form.bill.extJson.counterType = 'SUPPLIER'
      if (b.form.bill.payTarget == null) b.form.bill.payTarget = 'SUPPLIER'
    }
    if (subType.value === 'AIR' && b) {
      b.form.lines.forEach((l) => {
        if (!l.extJson) l.extJson = {}
        const d = l.extJson
        if (d.airPassenger == null) d.airPassenger = ''
        if (d.airFrom == null) d.airFrom = ''
        if (d.airTo == null) d.airTo = ''
        if (d.airNet == null) d.airNet = 0
        if (d.airTax == null) d.airTax = 0
        if (d.airRefund == null) d.airRefund = 0
        if (d.airPaid == null) d.airPaid = 0
        if (d.applyNo == null) d.applyNo = ''
      })
    }
    const clist = (await contractApi.page({ pageNum: 1, pageSize: 100 })).list || []
    const sums = (await reportApi.contractSummary()) || []
    const sumMap = {}
    sums.forEach((s) => { sumMap[s.contractNo] = Number(s.paidAmount || 0) })
    contracts.value = clist.map((c) => ({ ...c, paidAmount: sumMap[c.contractNo] || 0 }))
    if (matchRouteRef.value) matchRouteRef.value.loadApprovedPays()
  } catch (e) { /* ignore */ }
})
</script>
