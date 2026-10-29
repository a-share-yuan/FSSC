<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.entry.apServiceTitle') }}</span>
        <div class="ops">
          <el-input v-model="q.vendorName" size="small" style="width: 150px" :placeholder="$t('flow.request.phSupplierSearch')" clearable @keyup.enter="search" @clear="search" />
          <el-select v-model="q.reqType" size="small" style="width: 140px" :placeholder="$t('flow.request.phAllTypes')" clearable @change="search">
            <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="q.status" size="small" style="width: 110px" :placeholder="$t('common.status')" clearable @change="search">
            <el-option v-for="(l, c) in AP_REQ_STATUS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-button size="small" @click="search">{{ $t('common.refresh') }}</el-button>
          <el-button v-if="hasPerm('apflow:request:create')" type="primary" size="small" @click="openCreate">{{ $t('flow.request.btnNew') }}</el-button>
          <el-upload v-if="hasPerm('apflow:request:create')" :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportPlan">
            <el-button size="small" type="warning">{{ $t('flow.request.importExcelTitle') }}</el-button>
          </el-upload>
          <el-button v-if="hasPerm('apflow:request:dispatch')" size="small" @click="openRules">{{ $t('flow.request.btnDispatchRules') }}</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" :label="$t('flow.request.colApplyNo')" min-width="175" />
      <el-table-column :label="$t('field.type')" width="100">
        <template #default="{ row }"><el-tag size="small" type="info">{{ apReqTypeLabel(row.reqType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="vendorName" :label="$t('field.supplier')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="paymentWay" :label="$t('field.method')" width="90" />
      <el-table-column :label="$t('field.amount')" width="120" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.request.colCompany')" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ companyName(row.companyId) }}</template>
      </el-table-column>
      <el-table-column prop="invoiceNo" :label="$t('flow.request.colInvoiceNo')" min-width="130" show-overflow-tooltip />
      <el-table-column :label="$t('common.status')" width="92">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ apReqStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="$t('flow.request.colHandler')" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.handler ? userLabel(row.handler) : (row.handlerPool ? t('flow.request.pickCount', { v1: poolLabels(row.handlerPool) }) : '—') }}</template>
      </el-table-column>
      <el-table-column prop="voucherNo" :label="$t('flow.request.colErpVoucher')" width="150" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" v-show="canAct(a.key)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
          <el-button link type="info" size="small" @click="openDetail(row)">{{ $t('field.detail') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 服务端分页：原实现固定取前 20 条且无翻页控件，第 21 张单起界面上根本不存在 -->
    <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
                   :page-size="q.pageSize" :current-page="q.pageNum" :page-sizes="[10, 20, 50, 100]"
                   style="margin-top: 10px; justify-content: flex-end"
                   @size-change="(s) => { q.pageSize = s; q.pageNum = 1; load() }"
                   @current-change="(p) => { q.pageNum = p; load() }" />

    <!-- 新建 -->
    <el-dialog v-model="createVisible" :title="$t('flow.request.dlgNew')" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('field.billType')" prop="reqType">
            <el-select v-model="form.reqType" style="width: 100%">
              <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.method')" prop="paymentWay">
            <el-select v-model="form.paymentWay" style="width: 100%">
              <el-option :label="$t('flow.request.payMethodBankTransfer')" value="银行转账" /><el-option :label="$t('flow.request.colCheque')" value="支票" /><el-option :label="$t('flow.request.colApNote')" value="应付票据" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colCompany')" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width: 100%" :placeholder="$t('flow.request.phPickCompany')">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colSupplierName')" prop="vendorName">
            <!-- 供应商从档案选取：手输名称会与应付对账/发票三单匹配的客户规则对不上 -->
            <SupplierSelect v-model="form.vendorCode" :placeholder="$t('flow.request.phPickSupplier')" @change="onVendor" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colBu')"><el-input v-model="form.sbu" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.amount')" prop="amount"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colInvoiceNo')" prop="invoiceNo"><el-input v-model="form.invoiceNo" :placeholder="$t('flow.request.hintInvoiceNoRange')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colPoNo')" prop="poNo"><el-input v-model="form.poNo" :placeholder="$t('flow.request.errPrepayRequired')" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.account')" prop="bankNo"><el-input v-model="form.bankNo" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colDraftDue')" prop="expireDate"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :placeholder="$t('flow.request.errApNoteRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colEndorseAmount')"><el-input-number v-model="form.paymentAmount" :min="0" :precision="2" style="width: 100%" :placeholder="$t('flow.request.needInvoiceTotal')" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="$t('common.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreate">{{ $t('field.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- 确认服务请求 -->
    <el-dialog v-model="confirmVisible" :title="$t('flow.request.confirmTip')" width="520px" destroy-on-close>
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="110px" size="small">
        <el-form-item :label="$t('field.account')" prop="bankNo"><el-input v-model="confirmForm.bankNo" /></el-form-item>
        <el-form-item :label="$t('field.method')" prop="paymentWay">
          <el-select v-model="confirmForm.paymentWay" style="width: 100%">
            <el-option :label="$t('flow.request.payMethodBankTransfer')" value="银行转账" /><el-option :label="$t('flow.request.colCheque')" value="支票" /><el-option :label="$t('flow.request.colApNote')" value="应付票据" />
          </el-select>
          <div class="tip">{{ $t('flow.request.hintMethodChange') }}</div>
        </el-form-item>
        <el-form-item v-if="confirmForm.paymentWay === '应付票据'" :label="$t('flow.request.colDraftDue')" prop="expireDate">
          <el-date-picker v-model="confirmForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :placeholder="$t('flow.request.errApNoteRequired')" />
        </el-form-item>
        <el-form-item :label="$t('flow.request.colDiscount')" prop="rebateAmount"><el-input-number v-model="confirmForm.rebateAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="confirmVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doConfirm">{{ $t('flow.request.btnConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 组长审核 -->
    <el-dialog v-model="reviewVisible" :title="$t('flow.request.stApLeaderReview')" width="420px" destroy-on-close>
      <el-input v-model="reviewNote" type="textarea" :rows="2" :placeholder="$t('flow.request.errRejectReasonRequired')" />
      <template #footer>
        <el-button size="small" @click="doReview(false)">{{ $t('field.reject') }}</el-button>
        <el-button type="primary" size="small" @click="doReview(true)">{{ $t('field.approve') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <!-- 选单只能在本单选单池成员中认领：后端 claim 按池校验，手输池外姓名必被拒 -->
      <el-select v-if="inputKind === 'claim'" v-model="inputText" filterable style="width: 100%" :placeholder="$t('flow.request.phPickClaimant')">
        <el-option v-for="u in claimOptions" :key="u" :label="userLabel(u)" :value="u" />
      </el-select>
      <!-- 派工/转单候选＝启用中派工规则的负责人＋组长（跨公司共享岗，故不能按登录人公司过滤人员） -->
      <el-select v-else-if="inputKind === 'assign'" v-model="inputText" filterable style="width: 100%" :placeholder="$t('flow.request.phPickHandler')">
        <el-option v-for="u in handlerOptions" :key="u" :label="userLabel(u)" :value="u" />
      </el-select>
      <el-input v-else v-model="inputText" type="textarea" :rows="3" :placeholder="$t('flow.request.phVoidReason')" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 单据详情：apFlowApi.get 得有人消费，否则办理人看不到税额/折扣/时间戳等确认依据 -->
    <el-drawer v-model="detailVisible" :title="$t('flow.request.drawerDetailTitle')" size="520px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item :label="$t('flow.request.colApplyNo')">{{ detailRow.billNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.type')">{{ apReqTypeLabel(detailRow.reqType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.status')">{{ apReqStatusLabel(detailRow.status) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colCompany')">{{ companyName(detailRow.companyId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.supplier')">{{ detailRow.vendorName || detailRow.vendorCode || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colSupplierCode')">{{ detailRow.vendorCode || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colBu')">{{ detailRow.sbu || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.amount')">{{ formatMoney(detailRow.amount) }} {{ detailRow.ccy || '' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.taxAmount')">{{ formatMoney(detailRow.taxAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colDiscount')">{{ formatMoney(detailRow.rebateAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.method')">{{ detailRow.paymentWay || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.account')">{{ detailRow.bankNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colDraftDue')">{{ detailRow.expireDate || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colEndorseAmount')">{{ detailRow.paymentAmount == null ? '—' : formatMoney(detailRow.paymentAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colPoNo')">{{ detailRow.poNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colInvoiceNo')">{{ detailRow.invoiceNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colEkpNo')">{{ detailRow.ekpNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colHandler')">{{ userLabel(detailRow.handler) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colHandlerPool')">{{ poolLabels(detailRow.handlerPool) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.leader')">{{ userLabel(detailRow.groupLeader) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colModifyNote')">{{ detailRow.modifyNote || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colErpVoucher')">{{ detailRow.voucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colPayVoucher')">{{ detailRow.payVoucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colVoidReason')">{{ detailRow.voidReason || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.remark')">{{ detailRow.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.request.colTimeChain')">{{ timeChain(detailRow) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else :description="$t('common.empty')" />
    </el-drawer>

    <!-- 派工规则维护：POST /ap-request/dispatch-rules 补上写环节（原先只有派工下拉用的读环节，规则只能改库） -->
    <el-dialog v-model="rulesVisible" :title="$t('flow.request.rulesDialogTitle')" width="820px" destroy-on-close>
      <div class="toolbar">
        <el-button type="primary" size="small" @click="openRuleForm()">{{ $t('flow.request.btnRuleNew') }}</el-button>
        <span style="color:#909399;font-size:12px">{{ $t('flow.request.rulesDialogHint') }}</span>
      </div>
      <el-alert v-if="hasUnnamedHandler" type="info" :closable="false" show-icon :title="$t('flow.request.hintHandlerScope')" style="margin:6px 0 10px" />
      <el-table :data="apRules" v-loading="rulesLoading" size="small" border stripe>
        <el-table-column prop="ruleName" :label="$t('flow.request.colRuleName')" min-width="130" show-overflow-tooltip />
        <el-table-column prop="paymentWay" :label="$t('flow.request.colRulePayMethod')" width="110" />
        <el-table-column :label="$t('flow.request.colRuleHandlers')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ poolLabels(row.handlers) }}</template>
        </el-table-column>
        <el-table-column :label="$t('field.leader')" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ userLabel(row.groupLeader) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.enabled) === 1 ? 'success' : 'info'">{{ Number(row.enabled) === 1 ? $t('flow.request.stRuleEnabled') : $t('flow.request.stRuleDisabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openRuleForm(row)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" :type="Number(row.enabled) === 1 ? 'warning' : 'success'" @click="toggleRule(row)">{{ Number(row.enabled) === 1 ? $t('flow.request.btnRuleDisable') : $t('flow.request.btnRuleEnable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="ruleFormVisible" :title="ruleForm.id ? $t('flow.request.ruleEditTitle') : $t('flow.request.ruleNewTitle')" width="520px" destroy-on-close append-to-body>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleFormRules" label-width="100px" size="small">
        <el-form-item :label="$t('flow.request.colRuleName')" prop="ruleName"><el-input v-model="ruleForm.ruleName" :placeholder="$t('flow.request.phRuleName')" /></el-form-item>
        <el-form-item :label="$t('flow.request.colRulePayMethod')" prop="paymentWay">
          <el-select v-model="ruleForm.paymentWay" style="width: 100%" :placeholder="$t('flow.request.phRulePayMethod')">
            <el-option :label="$t('flow.request.payMethodBankTransfer')" value="银行转账" /><el-option :label="$t('flow.request.colCheque')" value="支票" /><el-option :label="$t('flow.request.colApNote')" value="应付票据" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.request.colRuleHandlers')" prop="handlerList">
          <el-select v-model="ruleForm.handlerList" multiple filterable style="width: 100%" :placeholder="$t('flow.request.phRuleHandlers')">
            <el-option v-for="u in ruleUserOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('field.leader')">
          <el-select v-model="ruleForm.groupLeader" filterable clearable style="width: 100%" :placeholder="$t('flow.request.phRuleLeader')">
            <el-option v-for="u in ruleUserOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" :active-text="$t('flow.request.stRuleEnabled')" :inactive-text="$t('flow.request.stRuleDisabled')" />
        </el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="ruleForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ruleFormVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="saveRuleForm">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AP_REQ_TYPE, AP_REQ_STATUS, apReqTypeLabel, apReqStatusLabel } from '@/constants/enums'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apFlowApi, orgApi, userApi, store } from '../../api'
import SupplierSelect from '@/components/common/SupplierSelect.vue'
import { hasPerm } from '@/utils/perm'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { formatDateTime, formatMoney } from '../../utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const rows = ref([])
const loading = ref(false)
const total = ref(0)
const q = reactive({ pageNum: 1, pageSize: 20, reqType: '', status: '', vendorName: '' })
const createVisible = ref(false)
const confirmVisible = ref(false)
const reviewVisible = ref(false)
const inputVisible = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
const companies = ref([])
const users = ref([])
// 条件必输与后端 ApRequestService.validateForms 同一规则：预付款要有 PO 编号，付款方法=应付票据要有汇票到期日
const createRules = computed(() => ({
  reqType: commonRules.select(t('flow.request.phDocType')),
  paymentWay: commonRules.select(t('flow.request.phPayMethod')),
  companyId: commonRules.select(t('flow.request.phPickCompany')),
  vendorName: commonRules.select(t('flow.request.phPickSupplier')),
  amount: commonRules.amountText(t('flow.request.phAmount')),
  invoiceNo: commonRules.required(t('flow.request.phInvoiceNo')),
  bankNo: commonRules.bankAccount(),
  poNo: form.reqType === 'PREPAY' ? commonRules.required(t('flow.request.errPoRequired')) : [],
  expireDate: form.paymentWay === '应付票据' ? commonRules.date(t('flow.request.errNoteDueDate')) : [],
}))
const confirmForm = reactive({})
// 确认服务请求弹窗（原无任何校验）——收款账号校银行账号格式（防付错账），
// 付款方式必选，返利金额填了就必须是数字（现金折扣，允许留空）
const confirmFormRef = ref(null)
const confirmRules = computed(() => ({
  bankNo: commonRules.bankAccount('收款账号应为 6-30 位数字'),
  paymentWay: commonRules.select(t('flow.request.phPayMethod')),
  expireDate: confirmForm.paymentWay === '应付票据' ? commonRules.date(t('flow.request.errNoteDueDate')) : [],
  rebateAmount: [{
    validator: (rule, value, cb) => {
      if (value == null || value === '') return cb()
      if (!isFinite(Number(value))) return cb(new Error('返利金额必须为数字'))
      cb()
    },
  }],
}))
const reviewNote = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

// 选单池成员＝后端按 paymentWay 命中的派工规则负责人清单（存工号），认领只能从中选
const claimOptions = computed(() => String(current.value?.handlerPool || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean))

// 手工派工候选：启用中的 AP 派工规则负责人＋组长去重。共享岗可跨公司，故不走「同公司人员」过滤
const apRules = ref([])
const handlerOptions = computed(() => {
  const set = new Set()
  apRules.value.filter((r) => Number(r.enabled) === 1).forEach((r) => {
    String(r.handlers || '').split(/[,，]/).forEach((s) => s.trim() && set.add(s.trim()))
    if (r.groupLeader) set.add(String(r.groupLeader).trim())
  })
  // 单据当前办理人也要在候选里，否则转单给「已在岗的人」时选不到
  if (current.value?.handler) set.add(String(current.value.handler).trim())
  return [...set]
})

// 派工规则维护（原界面只读不写：规则靠改库，派工下拉候选也因此无来源）
const rulesVisible = ref(false)
const rulesLoading = ref(false)
const ruleFormVisible = ref(false)
const ruleFormRef = ref(null)
const ruleForm = reactive({ id: null, ruleName: '', paymentWay: '', handlerList: [], groupLeader: '', enabled: 1, remark: '', editFingerprint: null })
const ruleFormRules = computed(() => ({
  ruleName: commonRules.required(t('flow.request.errRuleName')),
  paymentWay: commonRules.select(t('flow.request.errRulePayMethod')),
  handlerList: commonRules.multiSelect(t('flow.request.errRuleHandlers')),
}))
// 处理人候选＝工号（后端 assertRuleUsers 按 sys_user.user_name 校验，存姓名会让派工在保存后报「负责人不存在或已停用」）。
// 人员分页口按登录人公司强制收窄（只返回本公司的人、取不到外公司的人），故并入现有规则里已在岗的工号，
// 否则编辑既有跨公司规则时会在下拉里看不见、保存即把人丢掉。
const ruleUserOptions = computed(() => {
  const map = new Map()
  users.value.filter((u) => u.userName).forEach((u) => map.set(u.userName, `${u.nickName || u.userName}（${u.userName}）`))
  apRules.value.forEach((r) => {
    String(r.handlers || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
      .concat(r.groupLeader ? [String(r.groupLeader).trim()] : [])
      .forEach((name) => { if (!map.has(name)) map.set(name, nameOf(name) ? `${nameOf(name)}（${name}）` : name) })
  })
  return [...map].map(([value, label]) => ({ value, label }))
})

async function loadRulesLoading() {
  rulesLoading.value = true
  try { apRules.value = await apFlowApi.rules() } catch (e) { ElMessage.error(e.message) } finally { rulesLoading.value = false }
}

function openRules() {
  rulesVisible.value = true
  loadRulesLoading()
}

function openRuleForm(row) {
  Object.assign(ruleForm, row ? {
    id: row.id,
    ruleName: row.ruleName || '',
    paymentWay: row.paymentWay || '',
    handlerList: String(row.handlers || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean),
    groupLeader: row.groupLeader || '',
    enabled: Number(row.enabled) === 1 ? 1 : 0,
    remark: row.remark || '',
    editFingerprint: row.editFingerprint || null,
  } : { id: null, ruleName: '', paymentWay: '', handlerList: [], groupLeader: '', enabled: 1, remark: '', editFingerprint: null })
  ruleFormVisible.value = true
  nextTick(() => ruleFormRef.value?.clearValidate())
}

async function saveRuleForm() {
  if (!(await validateForms([ruleFormRef]))) return
  try {
    await apFlowApi.saveRule({
      id: ruleForm.id,
      ruleName: ruleForm.ruleName,
      paymentWay: ruleForm.paymentWay,
      handlers: ruleForm.handlerList.join(','),
      groupLeader: ruleForm.groupLeader || null,
      enabled: Number(ruleForm.enabled),
      remark: ruleForm.remark || null,
      editFingerprint: ruleForm.editFingerprint,
    })
    ElMessage.success(t('flow.request.msgRuleSaved'))
    ruleFormVisible.value = false
    await loadRulesLoading()
  } catch (e) {
    ElMessage.error(e.message)
    // 保存失败多半是并发校验拦下（他人已改），凭据随之失效 ⇒ 立刻重取列表，界面显示他人版本，改后的值留在弹窗里可复制
    if (ruleForm.id) await loadRulesLoading()
  }
}

async function toggleRule(row) {
  try {
    await apFlowApi.saveRule({ ...row, enabled: Number(row.enabled) === 1 ? 0 : 1 })
    await loadRulesLoading()
  } catch (e) {
    ElMessage.error(e.message)
    await loadRulesLoading()
  }
}

// 后端 listRules 已按 user_name 回查显示名（handlerNames/groupLeaderName 与工号逐位对齐），跨公司共享岗也能出姓名；
// 本账号可见名单命中优先，两处都取不到＝档案已删或停用，此时只回显工号。
const ruleNameMap = computed(() => {
  const map = {}
  apRules.value.forEach((r) => {
    const keys = String(r.handlers || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    const vals = String(r.handlerNames || '').split(/[,，]/).map((s) => s.trim())
    keys.forEach((k, i) => { if (vals[i] && vals[i] !== k) map[k] = vals[i] })
    const gl = String(r.groupLeader || '').trim()
    if (gl && r.groupLeaderName && r.groupLeaderName !== gl) map[gl] = r.groupLeaderName
  })
  return map
})
const nameOf = (token) => ruleNameMap.value[token] || users.value.find((x) => x.userName === token)?.nickName || ''
// 仅当有工号取不到姓名时才提示，其余情况不打扰
const hasUnnamedHandler = computed(() => apRules.value.some((r) => String(r.handlers || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  .concat(String(r.groupLeader || '').trim() ? [String(r.groupLeader).trim()] : []).some((t) => !nameOf(t))))

function userLabel(name) {
  if (!name) return '—'
  if (ruleNameMap.value[name]) return `${ruleNameMap.value[name]}（${name}）`
  const u = users.value.find((x) => x.userName === name)
  return u ? `${u.nickName || u.userName}（${u.userName}）` : name
}
function poolLabels(pool) {
  const list = String(pool || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  return list.length ? list.map(userLabel).join('、') : '—'
}
function companyName(id) {
  if (!id) return '—'
  return companies.value.find((c) => c.id === id)?.name || `#${id}`
}
function onVendor(s) {
  form.vendorName = s?.supplierName || s?.shortName || ''
  form.vendorCode = s?.supplierCode || ''
}
function timeChain(row) {
  const leg = (label, v) => (v ? `${label} ${formatDateTime(v)}` : '')
  return [leg(t('flow.request.legDispatch'), row.dispatchTime), leg(t('flow.request.legConfirm'), row.confirmTime),
    leg(t('flow.request.legPost'), row.postTime), leg(t('flow.request.legPay'), row.payTime)].filter(Boolean).join('　/　') || '—'
}

const statusTag = (s) => ({ PAID: 'success', POSTED: 'success', COMPLETED: 'info', VOIDED: 'danger', GROUP_REVIEW: 'warning' }[s] || 'primary')

// 动作按钮与后端码一一对应：无码只隐藏按钮，不制造“点了才报缺权限”
const ACT_PERMS = {
  dispatch: 'apflow:request:dispatch',
  assign: 'apflow:request:dispatch',
  claim: 'apflow:request:dispatch',
  confirm: 'apflow:request:confirm',
  review: 'apflow:request:review',
  post: 'apflow:request:post',
  pay: 'apflow:request:pay',
  void: 'apflow:request:void',
  close: 'apflow:request:void',
}
function canAct(key) {
  const p = ACT_PERMS[key]
  return !p || hasPerm(p)
}

function actions(row) {
  switch (row.status) {
    case 'PENDING_DISPATCH': return [{ key: 'dispatch', label: t('flow.request.actAutoDispatch') }, { key: 'assign', label: t('flow.request.actManualDispatch') }, { key: 'void', label: t('field.void') }]
    case 'DISPATCHED': return row.handlerPool ? [{ key: 'claim', label: t('flow.request.actPick') }, { key: 'assign', label: t('flow.request.actOpsDispatch') }] : [{ key: 'confirm', label: t('flow.request.confirmTitle') }, { key: 'post', label: t('flow.request.actPost') }, { key: 'void', label: t('field.void') }]
    case 'GROUP_REVIEW': return [{ key: 'review', label: t('flow.request.stLeaderReview') }, { key: 'void', label: t('field.void') }]
    case 'POSTED': return [{ key: 'pay', label: t('flow.request.actFundPay') }]
    case 'PAID': return [{ key: 'close', label: t('field.complete') }]
    default: return []
  }
}

async function load() {
  loading.value = true
  try {
    const r = await apFlowApi.page({
      ...q,
      reqType: q.reqType || undefined,
      status: q.status || undefined,
      vendorName: q.vendorName || undefined,
    })
    rows.value = r.list || []
    total.value = Number(r.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function search() {
  q.pageNum = 1
  return load()
}

async function openDetail(row) {
  try {
    detailRow.value = await apFlowApi.get(row.id)
    detailVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function api(fn, okMsg) {
  await fn()
  ElMessage.success(okMsg || t('common.success'))
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    dispatch: () => api(() => apFlowApi.dispatch(id)),
    post: () => api(() => apFlowApi.post(id)),
    pay: () => api(() => apFlowApi.pay(id)),
    close: () => api(() => apFlowApi.close(id)),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'claim' || a.key === 'assign' || a.key === 'void') {
    inputKind.value = a.key
    inputTitle.value = { claim: t('flow.request.actPickClaim'), assign: t('flow.request.actOpsDispatchTransfer'), void: t('flow.request.actVoidStop') }[a.key]
    inputText.value = ''
    inputVisible.value = true
    return
  }
  if (a.key === 'confirm') {
    Object.assign(confirmForm, { bankNo: row.bankNo || '', paymentWay: row.paymentWay || '', expireDate: row.expireDate || '', rebateAmount: row.rebateAmount ?? 0 })
    confirmVisible.value = true
    return
  }
  if (a.key === 'review') { reviewNote.value = ''; reviewVisible.value = true }
}

async function doInput() {
  const id = current.value.id
  // 选单要认领人、派工要负责人清单、作废要原因，留空后端一律拒（ApRequestService 负责人清单必填）
  if (!inputText.value.trim()) {
    return ElMessage.warning({ claim: t('flow.request.errFillClaimant'), assign: t('flow.request.errFillHandler'), void: t('flow.request.errVoidReason') }[inputKind.value] || t('flow.request.errRequired'))
  }
  if (inputKind.value === 'claim') await api(() => apFlowApi.claim(id, inputText.value), t('flow.request.msgPicked'))
  else if (inputKind.value === 'assign') await api(() => apFlowApi.assign(id, inputText.value))
  else await api(() => apFlowApi.void(id, inputText.value))
  inputVisible.value = false
}

async function doConfirm() {
  if (!(await validateForms([confirmFormRef]))) return
  await api(() => apFlowApi.confirm(current.value.id, { ...confirmForm }))
  confirmVisible.value = false
}

async function doReview(pass) {
  if (!pass && !reviewNote.value.trim()) return ElMessage.warning(t('flow.request.errRejectReason'))
  await api(() => apFlowApi.groupReview(current.value.id, { pass, note: reviewNote.value }))
  reviewVisible.value = false
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => apFlowApi.create({ ...form }), t('flow.request.msgCreated'))
  createVisible.value = false
}

/** 新建前清空上一次的录入，避免带着旧单内容开单（金额/发票号残留最易造成错单） */
async function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  // 归属公司取登录人所在公司：原实现写死 companyId=1，公司 33 的人建的单永远挂到公司 1 名下
  form.companyId = store.user?.companyId || null
  createVisible.value = true
  // 逐个 delete 会被 el-form 当成"值变更"，于是开窗即飘红；开完窗清一次校验，红星只在提交时生效
  await nextTick()
  createFormRef.value?.clearValidate()
}

async function loadCompanies() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { companies.value = [] }
}

async function loadUsers() {
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 500 })).list || [] } catch { users.value = [] }
}

async function loadRules() {
  try { apRules.value = await apFlowApi.rules() } catch { apRules.value = [] }
}

async function doImportPlan(file) {
  try {
    const fd = new FormData()
    fd.append('file', file.raw)
    const r = await apFlowApi.importPlan(fd)
    ElMessage.success(t('flow.request.msgImported', { v1: r.created, v2: r.errors.length, v3: r.errors.length ? '：' + r.errors.join('；') : '' }))
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => { load(); loadCompanies(); loadUsers(); loadRules() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.tip { font-size: 12px; color: #e6a23c; }
</style>
