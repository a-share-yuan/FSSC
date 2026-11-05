<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">{{ $t('contract.main.Contract') }}</h2>
    </div>
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-button type="primary" size="small" @click="openCreate">{{ $t('contract.main.New') }}</el-button>
      <el-input v-model="kw" :placeholder="$t('contract.main.Search')" style="width: 240px" @keyup.enter="load" />
      <el-button type="primary" size="small" @click="load">{{ $t('common.search') }}</el-button>
      <el-button size="small" @click="kw = ''; load()">{{ $t('common.reset') }}</el-button>
      <el-button type="info" size="small" @click="exportCsv">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      <el-button type="info" size="small" @click="downloadTemplate">{{ $t('contract.main.Export') }}</el-button>
      <el-button type="success" size="small" @click="importVis = true">{{ $t('contract.main.Import') }}</el-button>
    </div>
    <!-- 导入历史合同 -->
    <el-dialog v-model="importVis" :title="$t('contract.main.Import')" width="620px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon
        :title="$t('contract.main.Format')" style="margin-bottom: 8px" />
      <el-input v-model="importCsv" type="textarea" :rows="8" :placeholder="$t('contract.main.Paste')" />
      <template #footer>
        <el-button @click="importVis = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doImport">{{ $t('common.import') }}</el-button>
      </template>
    </el-dialog>
    <el-table class="cm-target-contract" :data="pagedData" size="small" stripe border v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据" @row-dblclick="openDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="contractNo" label="合同号" width="140"  sortable />
      <el-table-column prop="contractName" label="名称"  min-width="200" show-overflow-tooltip sortable />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ contractTypeLabel(row.contractType) }}</template>
      </el-table-column>
      <el-table-column prop="partyBName" label="乙方" width="120"  sortable />
      <el-table-column label="金额" width="100"  sortable>
        <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag :type="stType(row.status)" size="small">{{ contractStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <!-- 合同提交即发起 CONTRACT 流程（ContractService.submit 尾行 wfEngineFacade.start），
             审批中的合同必有在途任务，而 ContractService.approve 首行 assertNotInFlow 一律拒绝直批，
             故这里的【通过】【驳回】对任何账号都是「点了必失败」的误导入口（后端拦、数据不变）。
             办理入口只有 流程中心›我的待办（或 #/approval）。原「模块状态机直批」的注释规则已不成立。 -->
        <template #default="{ row }">
          <InlineActions :visible="true" @view="openDetail(row)" :show-trace="true" @trace="openTrace(row)"
            :show-approve="false" :show-reject="false" />
          <el-button v-if="row.status === BillStatus.DRAFT" link size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === BillStatus.REJECTED" link size="small" type="warning" @click="returnToEdit(row)">编辑重提</el-button>
          <el-button v-if="row.status === BillStatus.DRAFT" link size="small" type="primary" @click="act('submit', row)">提交</el-button>
          <span v-if="row.status === BillStatus.APPROVING" style="color:#909399;font-size:12px">审批中·请到我的待办办理</span>
          <el-button v-if="row.status === 'EFFECTIVE'" link size="small" @click="changeContract(row)">变更</el-button>
          <el-button v-if="row.status === 'EFFECTIVE'" link size="small" type="warning" @click="closeContract(row)">关闭</el-button>
          <el-button v-if="row.status === 'CLOSED'" link size="small" type="success" @click="reopenContract(row)">{{ $t('contract.main.BtnReopen') }}</el-button>
          <el-button v-if="row.status !== BillStatus.VOID" link size="small" @click="act('supplement', row)">补充协议</el-button>
          <el-button v-if="row.status === BillStatus.DRAFT" link size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="load" />

    <el-dialog v-model="vis" :title="form.id ? '编辑合同' : '新增合同'" width="680px" top="5vh" class="contract-dialog" destroy-on-close>
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="100px" size="default">
        <el-row class="cm-target-contract" :gutter="12">
          <el-col :md="12"><el-form-item label="合同号"><el-input v-model="form.contractNo" readonly placeholder="保存后由系统自动生成" /></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="合同名称" prop="contractName"><el-input v-model="form.contractName" placeholder="请输入合同名称" /></el-form-item></el-col>
          <el-col :md="12">
            <el-form-item label="合同类型" prop="contractType">
              <el-select v-model="form.contractType" style="width: 100%">
                <el-option label="采购" value="PURCHASE" /><el-option label="销售" value="SALE" />
                <el-option label="服务" value="SERVICE" /><el-option label="框架" value="FRAMEWORK" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="合同大类">
              <el-select v-model="form.contractClass" style="width: 100%">
                <el-option label="费用类" value="费用类" /><el-option label="资产类" value="资产类" />
                <el-option label="成本类" value="成本类" /><el-option label="外包类" value="外包类" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12"><el-form-item label="费用类型" prop="itemId">
            <el-select v-model="form.itemId" filterable style="width:100%" @change="(id) => (form.itemName = (items.find(x => x.id === id) || {}).itemName || '')">
              <el-option v-for="it in items" :key="it.id" :label="it.itemName" :value="it.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :md="12"><el-form-item label="客商名称">
            <el-select v-model="form.customerId" filterable clearable style="width:100%" @change="onCustomerChange">
              <el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :md="12"><el-form-item label="甲方"><el-select v-model="form.partyAName" filterable allow-create default-first-option placeholder="请选择或输入甲方" style="width:100%" @change="onPartyAChange"><el-option label="本公司" value="本公司" /><el-option label="母公司" value="母公司" /><el-option label="子公司" value="子公司" /><el-option label="关联方" value="关联方" /></el-select></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="乙方" prop="partyBName"><el-input v-model="form.partyBName" placeholder="请输入乙方名称" /></el-form-item></el-col>
          <el-col :md="12">
            <el-form-item label="合同总额" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" :disabled="!!form.noFixedAmount" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="无固定金额">
              <el-switch v-model="form.noFixedAmount" :active-value="1" :inactive-value="0" active-text="是（不控制预算）" />
            </el-form-item>
          </el-col>
          <el-col :md="12"><el-form-item label="币种"><el-select v-model="form.currencyCode" style="width: 120px">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
</el-select></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="签订公司"><el-select v-model="form.companyName" filterable allow-create placeholder="选择或输入签订公司" style="width:100%"><el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.name" :value="c.companyName || c.name" /></el-select></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="签约日"><el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="合同有效期起"><el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => dayAfter(d, form.endDate)" /></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="合同有效期止"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => dayBefore(d, form.beginDate)" /></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="是否同比价格递减">
            <el-switch v-model="form.priceDecrease" :active-value="1" :inactive-value="0" />
          </el-form-item></el-col>
          <el-col :md="12"><el-form-item label="是否招标比价">
            <el-switch v-model="form.bidCompare" :active-value="1" :inactive-value="0" />
          </el-form-item></el-col>
          <el-col v-if="form.bidCompare === 1" :md="12"><el-form-item label="招标比价过程"><el-input v-model="form.bidProcess" placeholder="请输入招标比价过程" /></el-form-item></el-col>
          <el-col :md="12"><el-form-item label="中标原因"><el-select v-model="form.bidReason" filterable allow-create default-first-option placeholder="请选择或输入" style="width: 100%"><el-option label="价格优势" value="价格优势" /><el-option label="技术方案优" value="技术方案优" /><el-option label="资质齐全" value="资质齐全" /><el-option label="服务承诺好" value="服务承诺好" /><el-option label="综合评分最高" value="综合评分最高" /><el-option label="唯一供应商" value="唯一供应商" /></el-select></el-form-item></el-col>
          <el-col :md="24"><el-form-item label="中标说明"><el-input v-model="form.bidNote" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item></el-col>
          <el-col :md="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="vis = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 合同详情：付款条件 / 支付记录 / 预算情况 -->
    <el-drawer v-model="detailVisible" :title="`合同详情 ${detail.contractNo || ''} ${detail.contractName || ''}`" size="640px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="基本信息" name="base">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同号">{{ detail.contractNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ contractStatusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ contractTypeLabel(detail.contractType) }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ fmtMoney(detail.amount) }} {{ detail.currencyCode }}</el-descriptions-item>
            <el-descriptions-item label="甲方">{{ detail.partyAName }}</el-descriptions-item>
            <el-descriptions-item label="乙方">{{ detail.partyBName }}</el-descriptions-item>
            <el-descriptions-item label="签约日">{{ detail.signDate }}</el-descriptions-item>
            <el-descriptions-item label="有效期">{{ detail.beginDate }} ~ {{ detail.endDate }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
          </el-descriptions>
          <h4 style="margin:14px 0 6px">预算情况</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同总额">{{ budgetRow.contractAmount ?? detail.amount }}</el-descriptions-item>
            <el-descriptions-item label="已付款金额">{{ budgetRow.paidAmount ?? '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 分段式付款进度 -->
          <h4 style="margin:14px 0 6px">分段付款进度</h4>
          <div class="pay-segments">
            <div v-for="(seg, idx) in paySegments" :key="idx" class="pay-seg-item">
              <div class="pay-seg-bar-wrap">
                <div class="pay-seg-bar" :class="seg.cssClass" :style="{ flex: seg.flex || 1 }">
                  <span class="pay-seg-pct">{{ seg.pct }}%</span>
                </div>
                <el-icon v-if="seg.status === 'paid'" class="pay-seg-check"><CircleCheckFilled /></el-icon>
              </div>
              <div class="pay-seg-label">{{ seg.node }}</div>
              <div class="pay-seg-amount">
                <span class="amt-paid">{{ fmtMoney(seg.paidAmount) }}</span>
                <span class="amt-sep"> / </span>
                <span class="amt-plan">{{ fmtMoney(seg.planAmount) }}</span>
              </div>
              <el-tag :type="seg.tagType" size="small" effect="plain">{{ seg.statusText }}</el-tag>
            </div>
          </div>
          <!-- 汇总条 -->
          <div class="pay-seg-summary">
            <span>合计已付：<b style="color:#67c23a">{{ fmtMoney(totalPaid) }}</b></span>
            <span style="margin-left:16px">合同总额：<b>{{ fmtMoney(totalPlan) }}</b></span>
            <span style="margin-left:16px">整体进度：<b style="color:#409eff">{{ totalPct }}%</b></span>
          </div>
        </el-tab-pane>
        <el-tab-pane label="付款条件" name="cond">
          <div style="margin-bottom:8px;display:flex;gap:8px">
            <el-button size="small" type="primary" @click="condDialog = true">+ 新增付款条件</el-button>
          </div>
          <el-table :data="conditions" size="small" border v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="payNode" label="付款节点"  min-width="120" show-overflow-tooltip sortable />
            <el-table-column label="支付金额" width="110"  sortable>
              <template #default="{ row }">{{ fmtMoney(row.payAmount) }}</template>
            </el-table-column>
            <el-table-column prop="planDate" label="预计付款日期" width="120"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column label="操作" width="60">
              <template #default="{ $index }"><el-button link size="small" type="danger" @click="delCondition($index)">删</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="支付记录" name="rec">
          <div style="margin-bottom:8px;display:flex;gap:8px">
            <el-button size="small" type="primary" @click="recDialog = true">+ 新增支付记录</el-button>
          </div>
          <el-table :data="records" size="small" border v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="单据号" width="150"  sortable />
            <el-table-column label="实际付款" width="100"  sortable>
              <template #default="{ row }">{{ fmtMoney(row.payAmount) }}</template>
            </el-table-column>
            <el-table-column prop="settleQty" label="数量" width="80"  sortable />
            <el-table-column prop="unitPrice" label="单价" width="90"  sortable />
            <el-table-column prop="payDate" label="支付日期" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ payRecStatusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }"><el-button link size="small" type="danger" @click="delRecord($index)">删</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 付款条件/支付记录 新增弹窗 -->
    <el-dialog v-model="condDialog" title="新增付款条件" width="480px" destroy-on-close>
      <el-form ref="condFormRef" :model="condForm" :rules="condRules" label-width="100px" size="default">
        <el-form-item label="付款节点" prop="payNode"><el-select v-model="condForm.payNode" filterable allow-create default-first-option placeholder="请选择或输入" style="width: 100%"><el-option label="预付款" value="预付款" /><el-option label="进度款" value="进度款" /><el-option label="验收款" value="验收款" /><el-option label="质保金" value="质保金" /><el-option label="尾款" value="尾款" /><el-option label="竣工结算款" value="竣工结算款" /></el-select></el-form-item>
        <el-form-item label="支付金额" prop="payAmount"><el-input-number v-model="condForm.payAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="预计付款日期" prop="planDate"><el-date-picker v-model="condForm.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="condForm.remark" placeholder="请输入备注" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="saveCondition">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="recDialog" title="新增支付记录" width="520px" destroy-on-close>
      <el-form ref="recFormRef" :model="recForm" :rules="recRules" label-width="100px" size="default">
        <el-form-item label="实际付款金额" prop="payAmount"><el-input-number v-model="recForm.payAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="结算数量"><el-input-number v-model="recForm.settleQty" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="recForm.unitPrice" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="单据号"><el-input v-model="recForm.billNo" placeholder="请输入单据号" /></el-form-item>
        <el-form-item label="单据类型"><el-select v-model="recForm.billType" style="width:100%"><el-option label="付款" value="PAY" /><el-option label="报销" value="CLAIM" /><el-option label="借款" value="LOAN" /></el-select></el-form-item>
        <el-form-item label="支付日期" prop="payDate"><el-date-picker v-model="recForm.payDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="recForm.status" style="width:100%"><el-option label="已付" value="PAID" /><el-option label="处理中" value="IN_PROGRESS" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="saveRecord">保存</el-button></template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="CONTRACT_APPROVE" />

  <CoachMark ref="contractCm" :guide-key="_guideKey" :selector="'.cm-target-contract'" title="合同管理" text="合同管理页面展示合同列表，支持分段付款计划和进度跟踪。" />
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import { contractApi, reportApi, customerApi, mdApi, orgApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useFormRules, commonRules, validateForm } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { useCurrency } from '@/composables/useCurrency'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+s': () => { try { save() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})
const contractCm = ref(null)
// 工作台「我的单据」深链：?kw=合同号 直接定位该行（台账页自带行内「编辑重提」）
const route = useRoute()

const selRows = ref([])
const rows = ref([])
const loading = ref(false)
const pager = useTablePager({ mode: 'server', storageKey: 'contract_list' })
const { pagedData } = pager
const kw = ref(route.query.kw ? String(route.query.kw) : '')
const vis = ref(false)
const customers = ref([])
const items = ref([])
const companies = ref([])
const form = reactive({
  id: null, contractNo: '', contractName: '', contractType: 'PURCHASE', contractClass: '费用类',
  partyAName: '', partyBName: '', amount: null, currencyCode: 'CNY', signDate: '', beginDate: '', endDate: '',
  customerId: null, companyName: '', // 签订公司由下拉选择（orgApi.companyTree），LoginUser 无 companyName 字段
  itemId: null, itemName: '',
  noFixedAmount: 0, priceDecrease: 0, bidCompare: 0, bidProcess: '', bidReason: '', bidNote: '', remark: '',
})
const { autofill, saveFormHistory } = useAutoFill()
const formRef = ref(null)
const guard = useSubmitGuard(formRef)
const { rules: baseRules } = useFormRules('CONTRACT')
// 合同总额只在「固定金额」合同下必填，与后端 ContractService.create 同一规则
const formRules = computed(() => ({
  ...baseRules.value,
  amount: [{
    required: true,
    message: '请输入合同总额',
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (form.noFixedAmount === 1) return cb()
      if (value === null || value === undefined || value === '') return cb(new Error('请输入合同总额，或勾选「无固定金额」'))
      cb()
    },
  }],
}))
const detailVisible = ref(false)
const detailTab = ref('base')
const detail = reactive({})
const conditions = ref([])
const records = ref([])
const budgetRow = reactive({ contractAmount: 0, paidAmount: 0 })
const condDialog = ref(false)
const condForm = reactive({ payNode: '', payAmount: 0, planDate: '', remark: '' })
const recDialog = ref(false)
const recForm = reactive({ payAmount: 0, settleQty: 0, unitPrice: 0, billNo: '', billType: 'PAY', payDate: '', status: 'PAID' })
// 付款条件/支付记录弹窗接入中央框架（原只有 toast 式必填，无格式/必填双校验）
const condFormRef = ref(null)
const recFormRef = ref(null)
const condRules = {
  payNode: commonRules.required('请填写付款节点'),
  payAmount: [{ required: true, type: 'number', min: 0, message: t('contract.main.Please') }],
  planDate: commonRules.date('请选择预计付款日期'),
}
const recRules = {
  payAmount: [{ required: true, type: 'number', min: 0.01, message: t('contract.main.Please2') }],
  payDate: commonRules.date('请选择支付日期'),
}

const payRate = computed(() => {
  const total = Number(budgetRow.contractAmount || 0)
  const paid = Number(budgetRow.paidAmount || 0)
  if (!total) return 0
  return Math.min(100, Math.round((paid / total) * 100))
})

// 分段式付款进度
const STANDARD_NODES = ['预付款', '进度款', '验收款', '质保金', '尾款']

function fmtMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 付款节点 → 标准化名称（容错别名）
function normalizeNode(name) {
  if (!name) return ''
  const s = name.trim()
  for (const std of STANDARD_NODES) {
    if (s.includes(std)) return std
  }
  if (s.includes('竣工') || s.includes('结算')) return '尾款'
  return s
}

// 计算每个标准节点的计划额和已付额
const paySegments = computed(() => {
  const conds = conditions.value || []
  const recs = records.value || []

  // 1) 按标准节点聚合付款条件
  const nodeMap = {}
  STANDARD_NODES.forEach((n) => { nodeMap[n] = { node: n, planAmount: 0, paidAmount: 0 } })
  conds.forEach((c) => {
    const node = normalizeNode(c.payNode)
    if (!nodeMap[node]) nodeMap[node] = { node: c.payNode, planAmount: 0, paidAmount: 0 }
    nodeMap[node].planAmount += Number(c.payAmount || 0)
  })

  // 2) 按标准节点聚合支付记录（尝试匹配 payNode，无则按比例分配）
  const unmatchedRecs = []
  recs.forEach((r) => {
    const amt = Number(r.payAmount || 0)
    const node = normalizeNode(r.payNode || r.payCondition || '')
    if (node && nodeMap[node]) {
      nodeMap[node].paidAmount += amt
    } else {
      unmatchedRecs.push(amt)
    }
  })
  // 未匹配的记录按比例分配到有计划额的节点
  if (unmatchedRecs.length) {
    const totalUnmatched = unmatchedRecs.reduce((a, b) => a + b, 0)
    const totalPlan = Object.values(nodeMap).reduce((a, n) => a + n.planAmount, 0)
    if (totalPlan > 0) {
      Object.values(nodeMap).forEach((n) => {
        if (n.planAmount > 0) n.paidAmount += totalUnmatched * (n.planAmount / totalPlan)
      })
    } else {
      // 无计划额时，全部计入第一个节点
      nodeMap[STANDARD_NODES[0]].paidAmount += totalUnmatched
    }
  }

  // 3) 如果没有任何条件，用合同总额按默认比例分配
  const totalPlanCheck = Object.values(nodeMap).reduce((a, n) => a + n.planAmount, 0)
  if (totalPlanCheck === 0 && Number(detail.amount || 0) > 0) {
    const defaultRatios = [0.3, 0.3, 0.2, 0.1, 0.1]
    const contractTotal = Number(detail.amount || 0)
    const totalPaid = Object.values(nodeMap).reduce((a, n) => a + n.paidAmount, 0)
    STANDARD_NODES.forEach((n, i) => {
      nodeMap[n].planAmount = Math.round(contractTotal * defaultRatios[i] * 100) / 100
    })
  }

  // 4) 转为数组并计算状态
  const totalPlan = Math.max(1, Object.values(nodeMap).reduce((a, n) => a + n.planAmount, 0))
  const segments = STANDARD_NODES.map((n) => {
    const seg = nodeMap[n]
    const pct = seg.planAmount > 0 ? Math.min(100, Math.round((seg.paidAmount / seg.planAmount) * 100)) : 0
    let status, cssClass, tagType, statusText
    if (seg.planAmount === 0) { status = 'none'; cssClass = 'seg-none'; tagType = 'info'; statusText = '未设' }
    else if (seg.paidAmount >= seg.planAmount * 0.999) { status = 'paid'; cssClass = 'seg-paid'; tagType = 'success'; statusText = '已付清' }
    else if (seg.paidAmount > 0) { status = 'partial'; cssClass = 'seg-partial'; tagType = 'warning'; statusText = '部分付' }
    else { status = 'pending'; cssClass = 'seg-pending'; tagType = 'info'; statusText = '待付' }
    return {
      node: n, planAmount: seg.planAmount, paidAmount: seg.paidAmount,
      pct, status, cssClass, tagType, statusText,
      flex: Math.max(0.3, seg.planAmount / totalPlan * 10),
    }
  })
  return segments
})

const totalPaid = computed(() => paySegments.value.reduce((a, s) => a + s.paidAmount, 0))
const totalPlan = computed(() => paySegments.value.reduce((a, s) => a + s.planAmount, 0))
const totalPct = computed(() => {
  const t = totalPlan.value
  if (!t) return 0
  return Math.min(100, Math.round((totalPaid.value / t) * 100))
})

function stType(s) { return STATUS_TYPE[s] || 'info' }
function contractTypeLabel(t) {
  // 库内现值是 SALES（132 行），本地名称表误写成 SALE ⇒ 销售合同全部直接显示英文 SALES；保留 SALE 兼容旧数据
  return { PURCHASE: '采购', SALE: '销售', SALES: '销售', SERVICE: '服务', FRAMEWORK: '框架' }[t] || t || ''
}
function contractStatusLabel(s) { return STATUS_LABEL[s] || s || '' }
function payRecStatusLabel(s) {
  return { PAID: '已付', IN_PROGRESS: '处理中', PENDING: '待付' }[s] || s || ''
}
async function load() {
  await pager.load(async ({ pageNum, pageSize }) => {
    const r = await contractApi.page({ pageNum, pageSize, keyword: kw.value })
    rows.value = r.list || []
    return { list: r.list || [], total: r.total || 0 }
  })
}
function emptyForm() {
  return {
    id: null, contractNo: '', contractName: '', contractType: 'PURCHASE', contractClass: '费用类',
    partyAName: '', partyBName: '', amount: null, currencyCode: 'CNY', signDate: '', beginDate: '', endDate: '',
customerId: null, companyName: '', // 签订公司由下拉选择（orgApi.companyTree），LoginUser 无 companyName 字段
    itemId: null, itemName: '',
    noFixedAmount: 0, priceDecrease: 0, bidCompare: 0, bidProcess: '', bidReason: '', bidNote: '', remark: '',
  }
}
function openCreate() {
  Object.assign(form, emptyForm())
  vis.value = true
}
function openEdit(row) {
  Object.assign(form, emptyForm(), { ...row })
  vis.value = true
}
function onCustomerChange(id) {
  const c = customers.value.find((x) => x.id === id)
  if (c) { form.partyBName = c.customerName; form.customerId = id }
}
function onPartyAChange(val) {
  if (!val) return
  if (val === '本公司' && companies.value.length) {
    form.companyName = companies.value[0].companyName || companies.value[0].name || ''
  } else if (companies.value.length) {
    const match = companies.value.find(c => (c.companyName || c.name) === val)
    if (match) form.companyName = match.companyName || match.name
  }
}
async function changeContract(row) {
  try {
    await ElMessageBox.confirm('变更将复制生成新合同版本并关闭原合同（释放预算），确认变更？', '合同变更', { type: 'warning' })
    await contractApi.change(row.id)
    ElMessage.success(t('contract.main.Change'))
    await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}
async function closeContract(row) {
  try {
    await ElMessageBox.confirm('关闭后该合同不能再被业务单据引用，并释放冻结预算，确认关闭？', '合同关闭', { type: 'warning' })
    await contractApi.close(row.id)
    ElMessage.success(t('purchase.poOrder.Closed'))
    await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

/** 重开已关闭合同：恢复生效并重新占用预算（预算不足会被后端拒绝，合同保持关闭）。 */
async function reopenContract(row) {
  try {
    await ElMessageBox.confirm(
      t('contract.main.AskReopen'),
      t('contract.main.TtlReopen'), { type: 'warning' })
    await contractApi.reopen(row.id)
    ElMessage.success(t('contract.main.StReopened'))
    await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}
function dayStr(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function dayBefore(d, min) { return !!min && dayStr(d) < min }
function dayAfter(d, max) { return !!max && dayStr(d) > max }
async function save() {
  if (form.beginDate && form.endDate && form.beginDate > form.endDate) {
    ElMessage.error(t('contract.main.Contract2')); return
  }
  saveFormHistory('CONTRACT', form.value || form)

  try {
    await guard.submit(async () => {
      if (form.id) await contractApi.update(form)
      else { const id = await contractApi.create(form); form.id = id }
      ElMessage.success(t('useCrud.main.Saved')); vis.value = false; await load()
    })
  } catch (e) { ElMessage.error(e.message) }
}
async function act(op, row) {
  try { await contractApi[op](row.id); ElMessage.success(t('common.success')); await load() }
  catch (e) { ElMessage.error(e.message) }
}
/** 驳回合同编辑重提（后端 REJECTED→DRAFT），随即打开编辑表单 */
async function returnToEdit(row) {
  try {
    await contractApi.returnToEdit(row.id)
    await load()
    const fresh = rows.value.find((x) => x.id === row.id)
    openEdit(fresh || row)
    ElMessage.success(t('contract.main.Returned'))
  } catch (e) { ElMessage.error(e.message) }
}
async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除该草稿合同？', '删除', { type: 'warning' })
    await contractApi.delete(row.id); ElMessage.success(t('useCrud.main.Deleted')); await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}
function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('contract.main.No')); return }
  const cols = [{ prop: 'contractNo', label: t('contract.main.Contract3') }, { prop: 'contractName', label: '合同' ? '名称' : '名称' }, { prop: 'contractType', label: t('finance.fxReval.Type') }, { prop: 'partyBName', label: t('contract.main.Party') }, { prop: 'amount', label: t('common.amount') }, { prop: 'status', label: t('common.status') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((r) => cols.map((c) => { const v = r?.[c.prop]; let s = v == null ? '' : String(v); if (s.includes(',') || s.includes('"')) s = '"' + s + '"'; return s }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); a.download = `合同_${new Date().toISOString().slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('utils.csv.Exported'))
}
const importVis = ref(false)
const importCsv = ref('')
async function downloadTemplate() {
  try {
    const csv = await contractApi.template()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' }))
    a.download = `合同导入模板_${new Date().toISOString().slice(0, 10)}.csv`
    a.click(); URL.revokeObjectURL(a.href)
  } catch (e) { ElMessage.error(e.message) }
}
async function doImport() {
  if (!importCsv.value.trim()) return ElMessage.warning(t('contract.main.Paste2'))
  try {
    const r = await contractApi.importContract(importCsv.value)
    ElMessage.success(t('contract.main.Import2'))
    importVis.value = false
    importCsv.value = ''
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}
async function loadRefs() {
  try {
    customers.value = (await customerApi.page({ pageNum: 1, pageSize: 200 })).list || []
    const tree = (await mdApi.expenseItems()) || []
    const flat = []
    for (const it of tree) { if (it.children && it.children.length) flat.push(...it.children); else flat.push(it) }
    items.value = flat
    const compTree = (await orgApi.companyTree()) || []
    const compFlat = []
    const walkComp = (arr) => { arr.forEach((n) => { if (n) { compFlat.push({ id: n.id, companyName: n.companyName || n.name || '' }); if (n.children?.length) walkComp(n.children) } }) }
    walkComp(compTree)
    companies.value = compFlat
  } catch { /* ignore */ }
}
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

onMounted(() => {
  loadCurrency()
  autofill(form, 'CONTRACT')
 load(); loadRefs() })

// 详情抽屉：付款条件/支付记录/预算情况
async function openDetail(row) {
  Object.assign(detail, row)
  detailVisible.value = true
  detailTab.value = 'base'
  const [c, r] = await Promise.all([contractApi.conditions(row.id), contractApi.records(row.id)])
  conditions.value = c || []
  records.value = r || []
  try {
    const list = (await reportApi.contractSummary()) || []
    const hit = list.find((x) => String(x.contractNo) === String(row.contractNo))
    budgetRow.contractAmount = hit ? hit.contractAmount : (row.amount || 0)
    budgetRow.paidAmount = hit ? hit.paidAmount : 0
  } catch (e) { /* ignore */ }
}
async function saveCondition() {
  if (!(await validateForm(condFormRef.value))) return
  if (!condForm.payNode) return ElMessage.warning(t('contract.main.Fill'))
  contractApi.conditionCreate({ contractId: detail.id, ...condForm })
    .then(async () => { ElMessage.success(t('useCrud.main.Saved')); condDialog.value = false; Object.assign(condForm, { payNode: '', payAmount: 0, planDate: '', remark: '' }); conditions.value = await contractApi.conditions(detail.id) })
    .catch((e) => ElMessage.error(e.message))
}
function delCondition(i) {
  const c = conditions.value[i]
  contractApi.conditionDelete(c.id).then(async () => { conditions.value = await contractApi.conditions(detail.id) }).catch((e) => ElMessage.error(e.message))
}
async function saveRecord() {
  if (!(await validateForm(recFormRef.value))) return
  if (!recForm.payAmount) return ElMessage.warning(t('contract.main.Fill2'))
  contractApi.recordCreate({ contractId: detail.id, ...recForm })
    .then(async () => { ElMessage.success(t('useCrud.main.Saved')); recDialog.value = false; Object.assign(recForm, { payAmount: 0, settleQty: 0, unitPrice: 0, billNo: '', billType: 'PAY', payDate: '', status: 'PAID' }); records.value = await contractApi.records(detail.id) })
    .catch((e) => ElMessage.error(e.message))
}
function delRecord(i) {
  const r = records.value[i]
  contractApi.recordDelete(r.id).then(async () => { records.value = await contractApi.records(detail.id) }).catch((e) => ElMessage.error(e.message))
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
  a.download = `Contract_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}


// Show guide on first visit
const _guideKey = 'fssc_guide_contractCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => contractCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
/* 分段式付款进度 */
.pay-segments {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  min-height: 70px;
  padding: 10px 4px 0;
}
.pay-seg-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.pay-seg-bar-wrap {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
}
.pay-seg-bar {
  width: 100%;
  min-height: 36px;
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  transition: all 0.3s;
}
.pay-seg-bar.seg-paid { background: linear-gradient(180deg, #67c23a, #85ce61); }
.pay-seg-bar.seg-partial { background: linear-gradient(180deg, #e6a23c, #f0c78a); }
.pay-seg-bar.seg-pending { background: linear-gradient(180deg, #c0c4cc, #d3d4d6); }
.pay-seg-bar.seg-none { background: linear-gradient(180deg, #f2f6fc, #e4e7ed); color: #c0c4cc; }
.pay-seg-pct { font-size: 12px; }
.pay-seg-check {
  position: absolute;
  top: -2px;
  right: -2px;
  color: #67c23a;
  font-size: 16px;
  background: #fff;
  border-radius: 50%;
}
.pay-seg-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text, #303133);
}
.pay-seg-amount {
  font-size: 11px;
  color: #909399;
}
.pay-seg-amount .amt-paid { color: #67c23a; font-weight: 600; }
.pay-seg-amount .amt-sep { color: #c0c4cc; }
.pay-seg-amount .amt-plan { color: var(--app-text-secondary, #606266); }
.pay-seg-summary {
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--app-bg, #f5f7fa);
  border-radius: 6px;
  font-size: 13px;
  color: var(--app-text-secondary, #606266);
}
</style>
