<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ modeTitle }}</span>
        <div class="ops">
          <template v-if="!isAr">
            <el-tag v-if="freeze.frozen" type="danger" size="small">{{ freeze.period ? $t('flow.collab.frozenWithPeriod', { v1: freeze.period }) : $t('flow.collab.frozenNoPeriod') }}</el-tag>
            <el-tag v-else type="success" size="small">{{ $t('flow.collab.tagDataExchangeOk') }}</el-tag>
          </template>
          <el-button size="small" @click="loadAll">{{ $t('common.refresh') }}</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadAll">
      <el-tab-pane v-if="!isAr && hasPerm('collab:scp:list')" :label="$t('flow.collab.scpInvoiceChain')" name="scp">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="scpQuery.status" size="small" style="width: 120px" :placeholder="$t('common.status')" clearable @change="loadScp">
            <el-option v-for="(l, s) in SCP_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-input v-model="scpQuery.keyword" size="small" style="width: 190px" :placeholder="$t('flow.collab.phSearch')" clearable @keyup.enter="loadScp" />
          <el-button size="small" type="primary" @click="loadScp">{{ $t('common.search') }}</el-button>
          <el-button v-if="hasPerm('collab:scp:supplier')" size="small" type="success" @click="openCreate">{{ $t('flow.collab.tabInvLanding') }}</el-button>
        </div>
        <el-table :data="scpRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="billNo" :label="$t('flow.collab.colScpNo')" min-width="165" show-overflow-tooltip />
          <el-table-column prop="reconBillNo" :label="$t('field.reconNo')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="preInvoiceNo" :label="$t('flow.collab.colPreInvNo')" min-width="130" show-overflow-tooltip />
          <el-table-column prop="supplierName" :label="$t('field.supplier')" min-width="110" show-overflow-tooltip />
          <el-table-column prop="amount" :label="$t('flow.collab.colInvoiceAmount')" width="100" align="right" />
          <el-table-column prop="invoiceNo" :label="$t('field.invoiceNo')" min-width="110" show-overflow-tooltip />
          <el-table-column :label="$t('common.status')" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="{ CERTIFIED: 'success', POSTED: 'success', MATCH_FAILED: 'danger', CHECK_FAILED: 'danger', FORCE_PENDING: 'danger', WAIT_MATCH: 'warning', CHECKING: 'warning', VOIDED: 'info' }[row.status] || 'primary'">
                {{ SCP_STATUS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('flow.collab.colPayCert')" min-width="160">
            <template #default="{ row }">
              <span v-if="row.payVoucherNo">{{ $t('flow.collab.payCount', { v1: row.payVoucherNo }) }}</span>
              <span v-else-if="row.certifyDate">{{ $t('flow.collab.certCount', { v1: row.certifyDate }) }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="330" fixed="right">
            <template #default="{ row }">
              <el-button v-for="a in scpActions(row)" :key="a.key" link type="primary" size="small" @click="scpAct(row, a)">{{ a.label }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 演示沙盘：与真实供应商对账(/pay/recon)重复，暂下线，恢复时换回 hasPerm('collab:scp:list') -->
      <el-tab-pane v-if="false" :label="$t('flow.collab.tabSupplierRecon')" name="recon">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="reconQuery.status" size="small" style="width: 140px" :placeholder="$t('common.status')" clearable @change="loadRecons">
            <el-option :label="$t('flow.collab.stPrePending')" value="PRELIM" />
            <el-option :label="$t('flow.collab.finalStatement')" value="CONFIRMED" />
            <el-option :label="$t('flow.collab.stVoided')" value="VOIDED" />
          </el-select>
          <el-input v-model="reconQuery.keyword" size="small" style="width: 170px" :placeholder="$t('flow.collab.colStatementSupplier')" clearable @keyup.enter="loadRecons" />
          <el-button size="small" type="primary" @click="loadRecons">{{ $t('common.search') }}</el-button>
          <el-button v-if="hasPerm('collab:scp:supplier')" type="success" size="small" @click="openGoods">{{ $t('flow.collab.tabReceiptImport') }}</el-button>
        </div>
        <el-table :data="reconRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="reconNo" :label="$t('field.reconNo')" min-width="165" show-overflow-tooltip />
          <el-table-column prop="supplierName" :label="$t('field.supplier')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="amount" :label="$t('field.amount')" width="100" align="right" />
          <el-table-column prop="taxAmount" :label="$t('field.taxAmount')" width="95" align="right" />
          <el-table-column prop="totalAmount" :label="$t('field.totalWithTax')" width="105" align="right" />
          <el-table-column prop="invoiceType" :label="$t('field.invoiceType')" width="85" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
          <el-table-column :label="$t('common.status')" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="{ CONFIRMED: 'success', PRELIM: 'warning', VOIDED: 'info' }[row.status] || 'info'">
                {{ { PRELIM: t('flow.collab.stPrePending'), CONFIRMED: t('flow.collab.stConfirmed'), VOIDED: t('flow.collab.stVoided') }[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('flow.collab.tabDispute')" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.objectionStatus === 'SUBMITTED'" size="small" type="danger">{{ $t('flow.close.stPending') }}</el-tag>
              <span v-else-if="row.objectionStatus === 'RESOLVED'">{{ $t('flow.collab.stProcessed') }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="300" fixed="right">
            <template #default="{ row }">
              <el-button v-for="a in reconActions(row)" :key="a.key" link type="primary" size="small" @click="reconAct(row, a)">{{ a.label }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="!isAp && hasPerm('collab:ccp:manage')" :label="$t('flow.collab.ccpShip')" name="ccp">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="ccpKeyword" size="small" style="width: 180px" :placeholder="$t('flow.collab.shipInvCustomer')" clearable @keyup.enter="loadCcp" />
          <el-button size="small" type="primary" @click="loadCcp">{{ $t('common.search') }}</el-button>
          <el-button size="small" type="success" @click="openShipping">{{ $t('flow.collab.tabShipPush') }}</el-button>
          <el-button size="small" @click="openReconSync">{{ $t('flow.collab.actStatusSync') }}</el-button>
          <el-button size="small" @click="openInvoiceWb">{{ $t('flow.collab.textInvWriteBack') }}</el-button>
        </div>
        <el-table :data="ccpRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="shippingNo" :label="$t('flow.collab.colShipNo')" min-width="150" show-overflow-tooltip />
          <el-table-column prop="soInvoiceNo" :label="$t('flow.collab.colErpInvNo')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" show-overflow-tooltip />
          <el-table-column prop="amount" :label="$t('flow.collab.colShipAmount')" width="110" align="right" />
          <el-table-column prop="taxAmount" :label="$t('field.taxAmount')" width="100" align="right" />
          <el-table-column prop="shippingDate" :label="$t('flow.collab.colShipDate')" width="105" />
          <el-table-column prop="statementNo" :label="$t('field.relatedRecon')" min-width="130" show-overflow-tooltip />
          <el-table-column :label="$t('common.status')" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'RECEIVED' ? 'primary' : 'info'">{{ row.status === 'RECEIVED' ? t('flow.collab.stReceived') : t('flow.collab.stConsumed') }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="hasPerm('collab:log:view')" :label="$t('flow.collab.tabApiLog')" name="logs">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="logQuery.ifaceCode" size="small" style="width: 190px" :placeholder="$t('field.api')" clearable @change="loadLogs">
            <el-option v-for="(l, c) in visibleIfaces" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="logQuery.status" size="small" style="width: 110px" :placeholder="$t('common.status')" clearable @change="loadLogs">
            <el-option :label="$t('flow.collab.stOk')" value="SUCCESS" />
            <el-option :label="$t('flow.collab.stFailed')" value="FAILED" />
            <el-option :label="$t('flow.collab.stMock')" value="MOCK" />
          </el-select>
          <el-button size="small" type="primary" @click="loadLogs">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="logRows" v-loading="loading" size="small" border stripe>
          <el-table-column label="序号" width="60" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column :label="$t('field.api')" min-width="170">
            <template #default="{ row }">{{ IFACES[row.ifaceCode] || row.ifaceCode }}</template>
          </el-table-column>
          <el-table-column :label="$t('field.direction')" width="140" show-overflow-tooltip><template #default="{ row }">{{ DIRECTION_LABEL[row.direction] || row.direction }}</template></el-table-column>
          <el-table-column prop="refNo" :label="$t('field.relatedNo')" min-width="150" show-overflow-tooltip />
          <el-table-column :label="$t('common.status')" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="{ SUCCESS: 'success', FAILED: 'danger', MOCK: 'info' }[row.status] || 'info'">{{ { SUCCESS: t('flow.collab.stOk'), FAILED: t('flow.collab.stFailed'), MOCK: t('flow.collab.stMock') }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="$t('field.message')" min-width="220" show-overflow-tooltip />
          <el-table-column :label="$t('field.time')" width="160"><template #default="{ row }">{{ row.createTime ? formatDateTime(row.createTime) : '—' }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="createVisible" :title="$t('flow.collab.tabInvLanding')" width="660px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('field.reconNo')" prop="reconBillNo"><el-input v-model="form.reconBillNo" maxlength="48" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colSupplierName')">
            <SupplierSelect v-model="form.supplierCode" :placeholder="$t('flow.collab.phPickSupplier')" @change="onSupplier" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colPoNo')"><el-input v-model="form.poNo" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.invoiceType')">
            <el-select v-model="form.invoiceType" style="width: 100%">
              <el-option :label="$t('flow.collab.kindSpecial')" value="VAT_SPECIAL" /><el-option :label="$t('flow.collab.kindPlain')" value="VAT_NORMAL" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colInvoiceAmount')" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colInvoiceTax')"><el-input v-model="form.taxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.totalWithTax')"><el-input v-model="form.totalAmount" :placeholder="$t('flow.collab.emptyAuto')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colBaseAmount')"><el-input v-model="form.reconAmount" :placeholder="$t('flow.collab.colReconSideAmount')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colBaseTax')"><el-input v-model="form.reconTaxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colBuyerTaxNo')"><el-input v-model="form.buyerTaxNo" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colSellerTaxNo')"><el-input v-model="form.sellerTaxNo" maxlength="64" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreate">{{ $t('flow.collab.btnLand') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipVisible" :title="$t('flow.collab.tabShipPush')" width="520px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="120px" size="small">
        <el-form-item :label="$t('flow.collab.colShipNo')" prop="shippingNo"><el-input v-model="shipForm.shippingNo" maxlength="48" /></el-form-item>
        <el-form-item :label="$t('flow.collab.colErpInvNo')"><el-input v-model="shipForm.soInvoiceNo" maxlength="48" /></el-form-item>
        <el-form-item :label="$t('field.customer')">
          <CustomerSelect v-model="shipForm.customerCode" :placeholder="$t('flow.collab.phPickCustomer')" @change="onShipCustomer" />
        </el-form-item>
        <el-form-item :label="$t('flow.collab.colShipAmount')" prop="amount"><el-input v-model="shipForm.amount" /></el-form-item>
        <el-form-item :label="$t('field.taxAmount')"><el-input v-model="shipForm.taxAmount" /></el-form-item>
        <el-form-item :label="$t('flow.collab.colShipDate')"><el-date-picker v-model="shipForm.shippingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('flow.collab.colLinkedStatement')"><el-input v-model="shipForm.statementNo" maxlength="48" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="shipVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doPushShipping">{{ $t('field.pushBtn') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invWbVisible" :title="$t('flow.collab.actInvWriteBack')" width="460px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item :label="$t('flow.collab.colPreInvNo')" required>
          <el-select v-model="invWbId" filterable :placeholder="$t('flow.collab.phPickPreInv')" style="width: 100%">
            <el-option v-for="p in preInvoiceOptions" :key="p.id" :value="p.id"
              :label="`${p.preInvoiceNo}（${p.customerName || ''}）`" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="invWbVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInvoiceWb">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="440px" destroy-on-close>
      <!-- 创建邮包：快递公司＋单号分格（「快递公司,单号」逗号串，输错一个逗号就整条报废） -->
      <el-form v-if="inputKind === 'send'" label-width="90px" size="small">
        <el-form-item :label="$t('flow.bill.mailCourierCompany')">
          <el-select v-model="sendForm.courierCompany" filterable allow-create default-first-option style="width: 100%" :placeholder="$t('flow.collab.phCourierCompany')">
            <el-option v-for="c in COURIER_COMPANIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.colTrackingNo')">
          <el-input v-model="sendForm.courierNo" :placeholder="$t('flow.collab.phCourierNo')" />
        </el-form-item>
      </el-form>
      <el-input v-else v-model="inputText" :placeholder="inputPlaceholder" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { invoiceTypeLabel } from '@/constants/invoiceType'
import { COURIER_COMPANIES } from '@/constants/enums'
import { formatDateTime } from '../../utils/format'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage } from 'element-plus'
import { collabApi, arFlowApi, store } from '../../api'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import SupplierSelect from '@/components/common/SupplierSelect.vue'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { hasPerm } from '@/utils/perm'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * 域归属（41-非费用域完成度审查 后续）：
 * SCP 是供应商发票链（应付方向），CCP 是客户发运/对账同步（应收方向），原先合并成一个 tab 挂在应收工作台，
 * 默认页签还是 SCP ⇒ 应收角色打开看到应付内容、应付角色又因门控码错位看不到。
 * 现按宿主拆分：'ap' 只渲染 SCP + SCP 日志，'ar' 只渲染 CCP + CCP 日志；'' 保留旧合并页形态。
 */
const props = defineProps({ mode: { type: String, default: '' } })
const isAp = computed(() => props.mode === 'ap')
const isAr = computed(() => props.mode === 'ar')
const modeTitle = computed(() => (isAp.value
  ? t('flow.collab.titleAp')
  : isAr.value ? t('flow.collab.titleAr') : t('flow.collab.title')))

const SCP_STATUS = computed(() => ({
  WAIT_MATCH: t('flow.collab.stPendingMatch'), WAIT_SEND: t('flow.collab.stPendingMail'), MATCH_FAILED: t('flow.collab.stMatchFail'), WAIT_SIGN: t('flow.collab.stPendingSign'),
  WAIT_SCAN: t('flow.collab.stPendingScan'), CHECKING: t('flow.collab.stVerifyIng'), CHECK_OK: t('flow.collab.stVerifyOk'), CHECK_FAILED: t('flow.collab.stVerifyFail'),
  POSTED: t('flow.collab.msgPosted'), CERTIFIED: t('flow.collab.msgCertDone'), FORCE_PENDING: t('flow.collab.stForceApproving'), VOIDED: t('flow.collab.stVoided'),
}))
const IFACES = computed(() => ({
  SCP_IF03_MATCH_POST: t('flow.collab.actThreeWay'),
  SCP_IF05_PAY: t('flow.collab.actWriteBackPay'),
  SCP_IF06_CERTIFY: t('flow.collab.colCertStatus'),
  SCP_IF07_FREEZE: t('flow.collab.actFreezeNotice'),
  SCP_IF07_UNFREEZE: t('flow.collab.actUnfreezeNotice'),
  AR_IF01_SHIPPING: t('flow.collab.actErpPush'),
  AR_IF02_INVOICE_WB: t('flow.collab.actInvWriteBack'),
  AR_IF10_RECON_SYNC: t('flow.collab.actStatusSync'),
}))

// 协同日志方向英文枚举→中文映射（未收录值原样兜底）
const DIRECTION_LABEL = computed(() => ({ SCP_TO_AP: 'SCP→AP', AP_TO_SCP: 'AP→SCP', SCP_TO_EBS: 'SCP→ERP', EBS_TO_SHARED: t('flow.collab.ebsToShared'), SHARED_TO_EBS: t('flow.collab.sharedToEbs'), CCP_TO_SHARED: t('flow.collab.ccpToShared') }))

// 日志接口码按域办结：接口前缀即方向（SCP_* 供应商链 / AR_* 客户端）
const ifacePrefix = computed(() => (isAp.value ? 'SCP_' : isAr.value ? 'AR_' : ''))
const visibleIfaces = computed(() => {
  const all = IFACES.value
  if (!ifacePrefix.value) return all
  return Object.fromEntries(Object.entries(all).filter(([k]) => k.startsWith(ifacePrefix.value)))
})

const tab = ref(isAr.value ? 'ccp' : 'scp')
const loading = ref(false)
const scpRows = ref([])
const ccpRows = ref([])
const logRows = ref([])
const scpQuery = reactive({ status: '', keyword: '' })
const logQuery = reactive({ ifaceCode: '', status: '' })
const ccpKeyword = ref('')
const freeze = reactive({ frozen: false, period: '' })
const reconRows = ref([])
const reconQuery = reactive({ status: '', keyword: '' })

const createVisible = ref(false)
const shipVisible = ref(false)
const inputVisible = ref(false)
const invWbVisible = ref(false)
const invWbId = ref(null)
const preInvoiceOptions = ref([])
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
// 对账单号是预制发票号的取号依据（：预制发票号=对账单号+流水），留空整张票无从生成
const createRules = computed(() => ({
  reconBillNo: commonRules.required(t('flow.collab.phStatementNo')),
  amount: commonRules.amountText(t('flow.collab.phInvoiceAmount')),
}))
const shipForm = reactive({})
const shipFormRef = ref(null)
const shipRules = computed(() => ({
  shippingNo: commonRules.required(t('flow.collab.phShipmentNo')),
  amount: commonRules.amountText(t('flow.collab.phShipmentAmount')),
}))
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')
const sendForm = reactive({ courierCompany: '', courierNo: '' })
const inputPlaceholder = ref('')

const today = () => new Date().toISOString().slice(0, 10)

// 每个动作对应的后端 @RequiresPerm：无码不渲染，避免点开才 403
const ACTION_PERM = {
  match: 'collab:scp:supplier', cancelMatch: 'collab:scp:supplier', send: 'collab:scp:supplier', rematch: 'collab:scp:supplier',
  manual: 'collab:scp:match:manual', force: 'collab:scp:force-appr',
  forceApprove: 'collab:scp:force-appr', forceReject: 'collab:scp:force-appr',
  sign: 'collab:scp:sign', scan: 'collab:scp:sign',
  post: 'collab:scp:post', preCertify: 'collab:scp:certify', certify: 'collab:scp:certify', pay: 'collab:scp:pay',
}

function scpActions(row) {
  const actions = (() => {
    switch (row.status) {
      case 'WAIT_MATCH':
      case 'MATCH_FAILED':
        return [{ key: 'match', label: t('flow.collab.actAutoMatch') }, { key: 'manual', label: t('flow.collab.actManualMatch') },
          { key: 'force', label: t('flow.collab.actApplyForce') }]
      case 'FORCE_PENDING':
        return [{ key: 'forceApprove', label: t('flow.collab.actApprovePass') }, { key: 'forceReject', label: t('field.reject') }]
      case 'WAIT_SEND':
        return [{ key: 'send', label: t('flow.collab.actMailPackage') }, { key: 'cancelMatch', label: t('flow.collab.actCancelMatch') }]
      case 'WAIT_SIGN':
        return [{ key: 'sign', label: t('flow.collab.actSigned') }]
      case 'WAIT_SCAN':
        return [{ key: 'scan', label: t('flow.collab.actScanVerify') }]
      case 'CHECK_FAILED':
        return [{ key: 'rematch', label: t('flow.collab.actRematch') }]
      case 'CHECK_OK':
        return [{ key: 'post', label: t('flow.collab.actPostPush') }]
      case 'POSTED':
        return [{ key: 'preCertify', label: t('flow.collab.stPreCert') }, { key: 'certify', label: t('flow.collab.actCentralCert') }, { key: 'pay', label: t('flow.collab.actPayWriteBack') }]
      case 'CERTIFIED':
        return [{ key: 'pay', label: t('flow.collab.actPayWriteBack') }]
      default:
        return []
    }
  })()
  return actions.filter((a) => !ACTION_PERM[a.key] || hasPerm(ACTION_PERM[a.key]))
}

async function loadScp() {
  loading.value = true
  try {
    scpRows.value = await collabApi.scpBills({ status: scpQuery.status || undefined, keyword: scpQuery.keyword || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadCcp() {
  loading.value = true
  try {
    ccpRows.value = await collabApi.shippings({ keyword: ccpKeyword.value || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadLogs() {
  loading.value = true
  try {
    const rows = await collabApi.syncLogs({ ifaceCode: logQuery.ifaceCode || undefined, status: logQuery.status || undefined })
    logRows.value = ifacePrefix.value
      ? rows.filter((r) => String(r.ifaceCode || '').startsWith(ifacePrefix.value))
      : rows
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadFreeze() {
  try {
    const m = await collabApi.freezeStatus({ companyId: store.user?.companyId })
    freeze.frozen = !!m.frozen
    freeze.period = m.period || ''
  } catch { /* 冻结横幅失败不打断页面 */ }
}

async function loadRecons() {
  loading.value = true
  try {
    reconRows.value = await collabApi.scpRecons({ status: reconQuery.status || undefined, keyword: reconQuery.keyword || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function loadAll() {
  if (!isAr.value) loadFreeze()
  if (tab.value === 'scp') loadScp()
  else if (tab.value === 'recon') loadRecons()
  else if (tab.value === 'ccp') loadCcp()
  else loadLogs()
}

async function api(fn, msg) {
  await fn()
  ElMessage.success(msg)
  await loadAll()
}

function scpAct(row, a) {
  current.value = row
  const id = row.id
  const m = {
    match: () => api(() => collabApi.scpMatch(id), t('flow.collab.msgAutoMatched')),
    cancelMatch: () => api(() => collabApi.scpCancelMatch(id), t('flow.collab.msgMatchCancelled')),
    sign: () => api(() => collabApi.scpSign(id), t('flow.collab.msgReceivedPendingScan')),
    rematch: () => api(() => collabApi.scpRematch(id), t('flow.collab.msgBackToPendingMatch')),
    preCertify: () => api(() => collabApi.scpPreCertify(id), t('flow.collab.msgPreCertified')),
    forceApprove: () => api(() => collabApi.scpForceApprove(id), t('flow.collab.msgForceApproved')),
    post: () => api(() => collabApi.scpPost(id), t('flow.collab.msgPushDone')),
    certify: () => api(() => collabApi.scpCertify(id, t('flow.collab.msgCertified')), t('flow.collab.msgCertDone')),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'send') {
    inputKind.value = 'send'; inputTitle.value = t('flow.collab.actMailPackage')
    sendForm.courierCompany = ''; sendForm.courierNo = ''
    inputVisible.value = true
  } else if (a.key === 'manual') {
    inputKind.value = 'manual'; inputTitle.value = t('flow.collab.actManualMatchDetail')
    inputPlaceholder.value = t('flow.collab.phAmounts')
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'force') {
    inputKind.value = 'force'; inputTitle.value = t('flow.collab.actApplyForceDetail')
    inputPlaceholder.value = t('flow.collab.forceReason')
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'forceReject') {
    inputKind.value = 'forceReject'; inputTitle.value = t('flow.collab.actRejectForce')
    inputPlaceholder.value = t('flow.collab.colRejectReason')
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'scan') {
    inputKind.value = 'scan'; inputTitle.value = t('flow.collab.actScanVerifyDetail')
    inputPlaceholder.value = t('flow.collab.phInvLine')
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'pay') {
    inputKind.value = 'pay'; inputTitle.value = t('flow.collab.actWriteBackPayDetail')
    inputPlaceholder.value = t('flow.collab.phPayLine')
    inputText.value = ''; inputVisible.value = true
  }
}

function parts() {
  return inputText.value.split(',').map((s) => s.trim())
}

async function doInput() {
  // 对账同步/发票回写是工具栏级动作，不依附选中行；按 row.id 取会整页 TypeError
  const id = current.value?.id
  if (inputKind.value === 'send') {
    if (!sendForm.courierCompany || !sendForm.courierNo) return ElMessage.warning(t('flow.collab.errCourierBoth'))
    const [c, n] = [sendForm.courierCompany.trim(), sendForm.courierNo.trim()]
    await api(() => collabApi.scpSend(id, { courierCompany: c, courierNo: n }), t('flow.collab.msgPacketCreated'))
  } else if (inputKind.value === 'scan') {
    const [invoiceNo, invoiceCode, amount, taxAmount, totalAmount, invoiceDate] = parts()
    const body = {}
    if (invoiceNo) body.invoiceNo = invoiceNo
    if (invoiceCode) body.invoiceCode = invoiceCode
    if (amount) body.amount = amount
    if (taxAmount) body.taxAmount = taxAmount
    if (totalAmount) body.totalAmount = totalAmount
    if (invoiceDate) body.invoiceDate = invoiceDate
    await api(() => collabApi.scpScan(id, body), t('flow.collab.msgScanVerified'))
  } else if (inputKind.value === 'manual') {
    const [a2, b2, c2] = parts()
    await api(() => collabApi.scpManualMatch(current.value.id, { reconAmount: a2, reconTaxAmount: b2, reconTotal: c2 }), t('flow.collab.msgManualMatched'))
  } else if (inputKind.value === 'force') {
    if (!inputText.value) return ElMessage.warning(t('flow.collab.errForceReason'))
    await api(() => collabApi.scpForceApply(current.value.id, inputText.value), t('flow.collab.msgForceApplied'))
  } else if (inputKind.value === 'forceReject') {
    if (!inputText.value) return ElMessage.warning(t('flow.collab.errRejectReason'))
    await api(() => collabApi.scpForceReject(current.value.id, inputText.value), t('flow.collab.msgRejected'))
  } else if (inputKind.value === 'pay') {
    const [voucher, date, amt] = parts()
    if (!voucher) return ElMessage.warning(t('flow.collab.errPayVoucherNo'))
    await api(() => collabApi.scpPayWriteback(id, { payVoucherNo: voucher, payDate: date || today(), payAmount: amt }), t('flow.collab.msgPayWrittenBack'))
  } else if (['confirm', 'objection', 'resolve', 'voidRecon', 'applyInvoice', 'importGoods'].includes(inputKind.value)) {
    await doReconInput()
  } else {
    await doInputCollab()
  }
  inputVisible.value = false
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  form.invoiceType = 'VAT_SPECIAL'
  createVisible.value = true
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await collabApi.scpCreate({ ...form })
  ElMessage.success(t('flow.collab.msgLanded'))
  createVisible.value = false
  await loadScp()
}

function openShipping() {
  Object.keys(shipForm).forEach((k) => delete shipForm[k])
  shipForm.shippingDate = today()
  shipVisible.value = true
}

function onSupplier(s) {
  form.supplierName = s?.supplierName || ''
}

function onShipCustomer(c) {
  shipForm.customerName = c?.customerName || ''
}

async function doPushShipping() {
  if (!(await validateForms([shipFormRef]))) return
  await collabApi.pushShipping({ ...shipForm })
  ElMessage.success(t('flow.collab.msgShipReceived'))
  shipVisible.value = false
  await loadCcp()
}

function reconActions(row) {
  const ops = []
  if (row.status === 'PRELIM') {
    ops.push({ key: 'confirm', label: t('flow.collab.actConfirmRecon') })
    if (hasPerm('collab:scp:supplier')) ops.push({ key: 'objection', label: t('flow.collab.actDiffFeedback') })
    ops.push({ key: 'voidRecon', label: t('field.void') })
  }
  if (row.objectionStatus === 'SUBMITTED' && hasPerm('collab:scp:sign')) ops.push({ key: 'resolve', label: t('flow.collab.actHandleDiff') })
  if (row.status === 'CONFIRMED' && hasPerm('collab:scp:supplier')) ops.push({ key: 'applyInvoice', label: t('flow.collab.actApplyInvoice') })
  return ops
}

function reconAct(row, a) {
  current.value = row
  inputKind.value = a.key
  inputText.value = ''
  if (a.key === 'confirm') inputTitle.value = t('flow.collab.actConfirmReconDetail')
  else if (a.key === 'objection') inputTitle.value = t('flow.collab.actDiffFeedbackDetail')
  else if (a.key === 'resolve') inputTitle.value = t('flow.collab.actHandleDiffDetail')
  else if (a.key === 'voidRecon') inputTitle.value = t('flow.collab.actVoidRecon')
  else if (a.key === 'applyInvoice') inputTitle.value = t('flow.collab.actApplyInvoice')
  inputVisible.value = true
}

function openGoods() {
  Object.keys(shipForm).forEach((k) => delete shipForm[k])
  inputKind.value = 'importGoods'
  inputTitle.value = t('flow.collab.actReceiptImport')
  inputPlaceholder.value = t('flow.collab.phSupplierLine')
  inputText.value = ''
  inputVisible.value = true
}

async function doReconInput() {
  const id = current.value.id
  if (inputKind.value === 'confirm') {
    // 解构变量不叫 t：会遮蔽 useI18n() 的取词函数
    const [tok, r] = inputText.value.split(',').map((s) => s.trim())
    await api(() => collabApi.scpConfirmRecon(id, { invoiceType: { 增专: 'VAT_SPECIAL', 增普: 'VAT_NORMAL' }[tok] || tok || 'VAT_SPECIAL', taxRatePct: r || undefined }), t('flow.collab.msgStatementConfirmed'))
  } else if (inputKind.value === 'objection') {
    await api(() => collabApi.scpObjection(id, inputText.value), t('flow.collab.msgDisputeSubmitted'))
  } else if (inputKind.value === 'resolve') {
    await api(() => collabApi.scpResolveObjection(id, inputText.value), t('flow.collab.msgDisputeHandled'))
  } else if (inputKind.value === 'voidRecon') {
    await api(() => collabApi.scpVoidRecon(id, inputText.value), t('flow.collab.msgStatementVoided'))
  } else if (inputKind.value === 'applyInvoice') {
    await api(() => collabApi.scpApplyInvoice(id, { poNo: inputText.value || undefined }), t('flow.collab.msgPreInvoiceLanded'))
  } else if (inputKind.value === 'importGoods') {
    const [sc, sn, amt, tax, linesStr] = inputText.value.split(',').map((s) => s.trim())
    if (!sc || !amt) return ElMessage.warning(t('flow.collab.fmtReceipt'))
    const lines = (linesStr || '').split(';').filter(Boolean).map((s, i) => {
      const seg = s.split('x')
      return { desc: (seg[0] || t('field.material') + (i + 1)).trim(), qty: (seg[1] || '1').trim(), amount: amt }
    })
    await api(() => collabApi.scpImportGoods({ supplierCode: sc, supplierName: sn, amount: Number(amt), taxAmount: Number(tax || 0), linesJson: JSON.stringify(lines) }), t('flow.collab.msgDraftStatementCreated'))
  }
  inputVisible.value = false
}

async function openReconSync() {
  current.value = null
  inputKind.value = 'reconSync'; inputTitle.value = t('flow.collab.actStatusSync')
  inputPlaceholder.value = t('flow.collab.phCustomerReconNo')
  inputText.value = ''; inputVisible.value = true
}

async function openInvoiceWb() {
  current.value = null
  invWbId.value = null
  preInvoiceOptions.value = []
  try {
    preInvoiceOptions.value = (await arFlowApi.preInvoices({ status: 'INVOICED' })) || []
  } catch (e) {
    ElMessage.error(e.message)
  }
  invWbVisible.value = true
}

async function doInvoiceWb() {
  if (!invWbId.value) return ElMessage.warning(t('flow.collab.errPreInvId'))
  await api(() => collabApi.invoiceWriteback(invWbId.value), t('flow.collab.msgInvWrittenBack'))
  invWbVisible.value = false
}

async function doInputCollab() {
  if (inputKind.value === 'reconSync') {
    if (!inputText.value) return ElMessage.warning(t('flow.collab.errReconNoRequired'))
    const st = await collabApi.reconSync(inputText.value)
    ElMessage.success(t('flow.collab.msgStatusSync', { v1: st.status, v2: st.checkStatus }))
  }
  inputVisible.value = false
}

onMounted(() => {
  if (!isAr.value) loadFreeze()
  if (tab.value === 'scp') loadScp()
  else if (tab.value === 'recon') loadRecons()
  else if (tab.value === 'ccp') loadCcp()
  else loadLogs()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
</style>
