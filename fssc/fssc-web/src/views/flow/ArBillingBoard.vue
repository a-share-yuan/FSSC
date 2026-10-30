<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.bill.pageTitle') }}</span>
        <div class="ops">
          <el-radio-group v-model="tab" size="small" @change="load">
            <el-radio-button value="statement">{{ $t('flow.bill.statement') }}</el-radio-button>
            <el-radio-button value="pre">{{ $t('flow.bill.preInvoice') }}</el-radio-button>
            <el-radio-button value="mail">{{ $t('flow.bill.mailPackage') }}</el-radio-button>
            <el-radio-button value="rule">{{ $t('flow.bill.invoiceRule') }}</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button v-if="tab === 'statement'" type="primary" size="small" @click="openImport">{{ $t('flow.bill.importStatement') }}</el-button>
          <el-button v-if="tab === 'statement'" size="small" @click="openEbsAdjust">{{ $t('flow.bill.erpAdjustPush') }}</el-button>
          <el-button v-if="tab === 'pre'" size="small" @click="openRedPush">{{ $t('flow.bill.erpNegativePush') }}</el-button>
          <el-upload v-if="tab === 'statement'" :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportExcel" style="display:inline-block">
            <el-button type="success" size="small">{{ $t('flow.bill.excelTemplateImport') }}</el-button>
          </el-upload>
          <el-button v-if="tab === 'pre'" type="primary" size="small" @click="genVisible = true">{{ $t('flow.bill.splitMergeGenerate') }}</el-button>
          <el-button v-if="tab === 'mail'" type="primary" size="small" @click="openMailCreate">{{ $t('flow.bill.createMail') }}</el-button>
          <el-button v-if="tab === 'mail'" size="small" @click="openRecipientManage">{{ $t('flow.bill.recipientManage') }}</el-button>
          <el-button v-if="tab === 'mail'" size="small" @click="openMailLog()">{{ $t('flow.bill.mailLog') }}</el-button>
        </div>
      </div>
    </template>

    <!-- 对账单 -->
    <el-table v-show="tab === 'statement'" :data="statements" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" :label="$t('field.reconNo')" min-width="180" />
      <el-table-column prop="customerCode" :label="$t('flow.collab.colCustomerCode')" width="100" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="ccy" :label="$t('flow.bill.colCurrency')" width="60" />
      <el-table-column prop="amount" :label="$t('field.amount')" width="105" align="right" />
      <el-table-column prop="taxAmount" :label="$t('field.taxAmount')" width="100" align="right" />
      <el-table-column :label="$t('flow.bill.colCheck')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.checkStatus === 'CHECK_OK' ? 'success' : row.checkStatus === 'CHECK_FAILED' ? 'danger' : 'info'">
            {{ { UNCHECKED: t('flow.bill.statusUnchecked'), CHECK_OK: t('field.approve'), CHECK_FAILED: t('flow.collab.stFailed') }[row.checkStatus] || row.checkStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="diffReason" :label="$t('flow.bill.colDiffReason')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'RECONCILED'" link type="primary" size="small" @click="doCheck(row)">{{ $t('flow.bill.checkData') }}</el-button>
          <el-button v-if="row.status === 'ABNORMAL'" link type="warning" size="small" @click="doAdjust(row)">{{ $t('flow.bill.financeAdjust') }}</el-button>
          <el-button v-if="row.status === 'RECONCILED'" link type="success" size="small" @click="doGen(row.billNo)">{{ $t('flow.bill.goInvoice') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 预制发票 -->
    <el-table v-show="tab === 'pre'" :data="invoices" v-loading="loading" size="small" border stripe>
      <el-table-column prop="preInvoiceNo" :label="$t('flow.collab.colPreInvNo')" min-width="190" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="invoiceType" :label="$t('field.type')" width="60" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="totalAmount" :label="$t('field.totalWithTax')" width="110" align="right" />
      <el-table-column :label="$t('common.status')" width="105">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ arBillingStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="$t('flow.bill.colTaxWriteback')" min-width="140">
        <template #default="{ row }">{{ row.invoiceNo || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="success" size="small" @click="biz(row, 'bizConfirm')">{{ $t('flow.bill.bizConfirm') }}</el-button>
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="danger" size="small" @click="bizReject(row)">{{ $t('flow.bill.bizReject') }}</el-button>
          <el-button v-if="row.status === 'TO_INVOICE'" link type="primary" size="small" @click="simple(row, 'pushTax')">{{ $t('flow.bill.pushTax') }}</el-button>
          <el-button v-if="row.status === 'PUSHED'" link type="primary" size="small" @click="openWriteback(row)">{{ $t('flow.bill.writebackInvoiceNo') }}</el-button>
          <el-button v-if="row.status === 'INVOICED' && !row.printedFlag" link type="primary" size="small" @click="simple(row, 'print')">{{ $t('flow.bill.print') }}</el-button>
          <el-button v-if="row.status === 'INVOICED'" link type="danger" size="small" @click="openRed(row)">{{ $t('flow.bill.creditNote') }}</el-button>
          <el-button v-if="['INVOICED', 'TO_INVOICE', 'PUSHED'].includes(row.status)" link type="warning" size="small" @click="openExchange(row)">{{ $t('flow.bill.replaceInvoice') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 邮包 -->
    <el-table v-show="tab === 'mail'" :data="mails" v-loading="loading" size="small" border stripe>
      <el-table-column prop="packageNo" :label="$t('flow.bill.colMailNo')" min-width="185" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" />
      <el-table-column :label="$t('flow.bill.mailReceiver')" min-width="150">
        <template #default="{ row }">
          <div>{{ row.receiver || '—' }}</div>
          <div class="sub">{{ row.receiverPhone || '' }} {{ row.receiverEmail || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="courierNo" :label="$t('flow.bill.colTrackingNo')" min-width="130" />
      <el-table-column :label="$t('flow.bill.colNotify')" width="80">
        <template #default="{ row }">{{ row.mailNotified === 1 ? t('flow.bill.notified') : t('flow.bill.notNotified') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ { CREATED: t('flow.bill.mailCreated'), SENT: t('flow.bill.mailSent'), SIGNED: t('flow.bill.mailSigned'), ARCHIVED: t('flow.bill.mailArchived') }[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'CREATED'" link type="primary" size="small" @click="mailAct(row, 'send')">{{ $t('flow.bill.send') }}</el-button>
          <el-button v-if="row.status === 'SENT' && !row.mailNotified" link type="warning" size="small" @click="mailAct(row, 'notify')">{{ $t('flow.bill.emailNotify') }}</el-button>
          <el-button v-if="row.status === 'SENT'" link type="primary" size="small" @click="mailAct(row, 'sign')">{{ $t('flow.bill.signReceipt') }}</el-button>
          <el-button v-if="row.status === 'SIGNED'" link type="success" size="small" @click="mailAct(row, 'archive')">{{ $t('flow.bill.archive') }}</el-button>
          <el-button link type="info" size="small" @click="openMailLog(row.packageNo)">{{ $t('flow.bill.mailLog') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 开票规则 -->
    <el-table v-show="tab === 'rule'" :data="rules" v-loading="loading" size="small" border stripe>
      <el-table-column prop="customerCode" :label="$t('flow.collab.colCustomerCode')" width="105" />
      <el-table-column prop="customerName" :label="$t('flow.collab.colCustomerName')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="invoiceType" :label="$t('field.type')" width="65" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="limitAmount" :label="$t('flow.bill.colPerInvoiceLimit')" width="110" align="right" />
      <el-table-column prop="maxLines" :label="$t('flow.bill.colMaxLines')" width="85" align="right" />
      <el-table-column prop="splitDim" :label="$t('flow.bill.colSplitDim')" width="95" :formatter="(row, col, val) => arSplitDimLabel(val)" />
      <el-table-column :label="$t('flow.bill.bizConfirm')" width="90">
        <template #default="{ row }">{{ row.needBizConfirm === 1 ? t('flow.bill.needed') : t('flow.bill.notNeeded') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" size="small"
            @click="saveRule({ ...row, status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })">{{ row.status === 'ENABLED' ? t('flow.bill.disable') : t('flow.bill.enable') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 对账单导入 -->
    <el-dialog v-model="importVisible" :title="$t('flow.bill.importDialogTitle')" width="560px" destroy-on-close>
      <el-form ref="stFormRef" :model="stForm" :rules="stRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colCustomerName')" prop="customerCode">
            <CustomerSelect v-model="stForm.customerCode" @change="onStatementCustomer" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colCustomerCode')"><el-input v-model="stForm.customerCode" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.amount')" prop="amount"><el-input v-model="stForm.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.taxAmount')" prop="taxAmount"><el-input v-model="stForm.taxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.bill.colCurrency')">
            <el-select v-model="stForm.ccy" style="width: 100%">
              <el-option v-for="c in ['CNY', 'USD', 'EUR', 'HKD', 'JPY']" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.bill.importUser')"><el-input v-model="stForm.importUser" disabled /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="importVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doImport">{{ $t('flow.bill.import') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mailVisible" :title="$t('flow.bill.createMailTitle')" width="560px" destroy-on-close>
      <el-form ref="mailFormRef" :model="mailForm" :rules="mailRules" label-width="100px" size="small">
        <el-form-item :label="$t('flow.bill.mailInvoices')" prop="invoiceNos">
          <el-select v-model="mailForm.invoiceNos" multiple filterable style="width: 100%" :placeholder="$t('flow.bill.mailInvoicesPh')">
            <el-option v-for="i in mailInvoiceOptions" :key="i.preInvoiceNo"
              :label="`${i.preInvoiceNo} · ${i.customerName || ''} · ${i.totalAmount}`" :value="i.preInvoiceNo" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('field.customer')">
          <CustomerSelect v-model="mailForm.customerCode" @change="onMailCustomerPick" />
          <div v-if="mailForm.customerCode" style="color:#909399;font-size:12px">{{ $t('flow.bill.hintPickedFromProfile') }}</div>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.recipientPick')">
          <el-select v-model="mailForm.recipientId" filterable clearable style="width: 100%" :placeholder="$t('flow.bill.recipientPickPh')" @change="onRecipientChange">
            <el-option v-for="r in mailRecipients" :key="r.id" :value="r.id"
              :label="`${r.recipientName}${r.customerName ? ' · ' + r.customerName : ''}${r.email ? ' · ' + r.email : ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.mailReceiver')" prop="receiver"><el-input v-model="mailForm.receiver" :placeholder="$t('flow.bill.mailReceiverPh')" maxlength="64" /></el-form-item>
        <el-form-item :label="$t('flow.bill.mailPhone')"><el-input v-model="mailForm.receiverPhone" :placeholder="$t('flow.bill.mailPhonePh')" maxlength="32" /></el-form-item>
        <el-form-item :label="$t('flow.bill.mailEmail')" prop="receiverEmail"><el-input v-model="mailForm.receiverEmail" :placeholder="$t('flow.bill.mailEmailPh')" />
          <div v-if="mailForm.customerCode && !mailForm.receiverEmail" style="color:#e6a23c;font-size:12px">{{ $t('flow.bill.hintNoEmailInProfile') }}</div>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.mailAddress')" prop="address"><el-input v-model="mailForm.address" type="textarea" :rows="2" :placeholder="$t('flow.bill.mailAddressPh')" maxlength="255" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="mailVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreateMail">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 寄送：快递公司 + 单号 -->
    <el-dialog v-model="courierVisible" :title="$t('flow.bill.mailDialogTitle')" width="420px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item :label="$t('flow.bill.mailCourierCompany')">
          <el-select v-model="courierForm.courierCompany" style="width: 100%" filterable allow-create default-first-option>
            <el-option v-for="c in COURIER_COMPANIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.colTrackingNo')">
          <el-input v-model="courierForm.courierNo" :placeholder="$t('flow.bill.trackingNoPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="courierVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doSendMail">{{ $t('flow.bill.confirmSend') }}</el-button>
      </template>
    </el-dialog>

    <!-- 收件人管理 -->
    <el-dialog v-model="recipientVisible" :title="$t('flow.bill.recipientManage')" width="960px" destroy-on-close>
      <div class="ops" style="margin-bottom: 8px">
        <el-input v-model="recipientQuery.keyword" size="small" style="width: 220px" :placeholder="$t('flow.bill.recipientSearchPh')" clearable @keyup.enter="loadRecipients" />
        <el-button size="small" @click="loadRecipients">{{ $t('common.search') }}</el-button>
        <el-button type="primary" size="small" @click="openRecipientForm()">{{ $t('flow.bill.recipientAdd') }}</el-button>
      </div>
      <el-table :data="recipientRows" size="small" border stripe max-height="420">
        <el-table-column prop="recipientName" :label="$t('flow.bill.mailReceiver')" width="110" />
        <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" />
        <el-table-column prop="phone" :label="$t('flow.bill.mailPhone')" width="120" />
        <el-table-column prop="email" :label="$t('flow.bill.mailEmail')" min-width="160" />
        <el-table-column prop="address" :label="$t('flow.bill.mailAddress')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('flow.bill.recipientDefault')" width="80">
          <template #default="{ row }">{{ row.isDefault === 1 ? $t('common.yes') : '—' }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRecipientForm(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="deleteRecipient(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="recipientFormVisible" :title="recipientForm.id ? $t('flow.bill.recipientEdit') : $t('flow.bill.recipientAdd')" width="560px" destroy-on-close>
      <el-form ref="recipientFormRef" :model="recipientForm" :rules="recipientRules" label-width="100px" size="small">
        <el-form-item :label="$t('field.customer')">
          <CustomerSelect v-model="recipientForm.customerCode" @change="onRecipientCustomer" />
        </el-form-item>
        <el-form-item :label="$t('flow.bill.mailReceiver')" prop="recipientName"><el-input v-model="recipientForm.recipientName" maxlength="64" /></el-form-item>
        <el-form-item :label="$t('flow.bill.mailPhone')"><el-input v-model="recipientForm.phone" maxlength="32" /></el-form-item>
        <el-form-item :label="$t('flow.bill.mailEmail')" prop="email"><el-input v-model="recipientForm.email" />
          <div v-if="recipientForm.customerCode && !recipientForm.email" style="color:#e6a23c;font-size:12px">{{ $t('flow.bill.hintNoEmailInProfile') }}</div>
        </el-form-item>
        <el-form-item :label="$t('flow.bill.mailAddress')"><el-input v-model="recipientForm.address" type="textarea" :rows="2" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item :label="$t('flow.bill.recipientDefault')"><el-switch v-model="recipientForm.isDefault" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="recipientForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="recipientFormVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="saveRecipient">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 邮件记录 -->
    <el-dialog v-model="logVisible" :title="$t('flow.bill.mailLog')" width="1000px" destroy-on-close>
      <div class="ops" style="margin-bottom: 8px">
        <el-input v-model="logQuery.packageNo" size="small" style="width: 220px" :placeholder="$t('flow.bill.mailLogSearchPh')" clearable @keyup.enter="loadMailLog" />
        <el-select v-model="logQuery.status" size="small" style="width: 140px" clearable :placeholder="$t('common.status')">
          <el-option label="SENT" value="SENT" />
          <el-option label="SIMULATED" value="SIMULATED" />
          <el-option label="FAILED" value="FAILED" />
        </el-select>
        <el-button size="small" @click="loadMailLog">{{ $t('common.search') }}</el-button>
      </div>
      <el-table :data="mailLogs" size="small" border stripe max-height="420">
        <el-table-column prop="sendTime" :label="$t('flow.bill.mailLogTime')" width="170" />
        <el-table-column prop="packageNo" :label="$t('flow.bill.colMailNo')" width="190" />
        <el-table-column prop="receiver" :label="$t('flow.bill.mailReceiver')" width="100" />
        <el-table-column prop="email" :label="$t('flow.bill.mailEmail')" min-width="170" />
        <el-table-column :label="$t('common.status')" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sendStatus === 'FAILED' ? 'danger' : row.sendStatus === 'SENT' ? 'success' : 'info'">{{ row.sendStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" :label="$t('flow.bill.mailLogResult')" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- ERP 财务调整推送 -->
    <el-dialog v-model="ebsVisible" :title="$t('flow.bill.erpAdjustBoxTitle')" width="560px" destroy-on-close>
      <el-form :model="ebsForm" label-width="110px" size="small">
        <el-form-item :label="$t('flow.bill.erpAdjustNo')" required><el-input v-model="ebsForm.adjustNo" :placeholder="$t('flow.bill.erpAdjustNoPh')" maxlength="48" /></el-form-item>
        <el-form-item :label="$t('field.customer')"><CustomerSelect v-model="ebsForm.customerCode" /></el-form-item>
        <el-form-item :label="$t('flow.bill.erpAdjustType')" required><el-input v-model="ebsForm.adjustType" :placeholder="$t('flow.bill.erpAdjustTypePh')" maxlength="16" /></el-form-item>
        <el-form-item :label="$t('field.amount')"><el-input v-model="ebsForm.amount" /></el-form-item>
        <el-form-item :label="$t('field.taxAmount')"><el-input v-model="ebsForm.taxAmount" /></el-form-item>
        <el-form-item :label="$t('flow.bill.erpStatement')">
          <el-select v-model="ebsForm.statementNo" filterable clearable style="width: 100%" :placeholder="$t('flow.bill.erpStatementPh')">
            <el-option v-for="s in statements" :key="s.id" :value="s.billNo" :label="`${s.billNo} · ${s.customerName || ''} · ${arStatementStatusLabel(s.status)}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ebsVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doEbsAdjust">{{ $t('field.pushBtn') }}</el-button>
      </template>
    </el-dialog>

    <!-- ERP 负数发票（红字来源）推送 -->
    <el-dialog v-model="redPushVisible" :title="$t('flow.bill.erpNegativeBoxTitle')" width="520px" destroy-on-close>
      <el-form label-width="110px" size="small">
        <el-form-item :label="$t('flow.bill.erpMemoNo')" required><el-input v-model="redPushForm.ebsCreditMemoNo" :placeholder="$t('flow.bill.erpMemoNoPh')" /></el-form-item>
        <el-form-item :label="$t('flow.bill.mailInvoices')" required>
          <el-select v-model="redPushForm.preInvoiceNo" filterable style="width: 100%" :placeholder="$t('flow.bill.erpInvoicePickPh')">
            <el-option v-for="i in invoices" :key="i.preInvoiceNo" :value="i.preInvoiceNo"
              :label="`${i.preInvoiceNo} · ${i.customerName || ''} · ${i.invoiceNo || ''}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="redPushVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doRedPush">{{ $t('field.pushBtn') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="genVisible" :title="$t('flow.bill.genDialogTitle')" width="460px" destroy-on-close>
      <el-select v-model="genBillNo" filterable style="width: 100%" :placeholder="$t('flow.bill.genBillNoPlaceholder')">
        <el-option v-for="s in reconciledStatements" :key="s.id" :value="s.billNo"
          :label="`${s.billNo} · ${s.customerName || ''} · ${s.amount}`" />
      </el-select>
      <div class="tip">{{ $t('flow.bill.genTip') }}</div>
      <template #footer>
        <el-button size="small" @click="genVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doGen(genBillNo)">{{ $t('flow.bill.generate') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { arBillingStatusLabel, arStatementStatusLabel, arSplitDimLabel, COURIER_COMPANIES } from '@/constants/enums'
import { invoiceTypeLabel } from '@/constants/invoiceType'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { arFlowApi, arGapApi, store } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tab = ref('statement')
const loading = ref(false)
const statements = ref([])
const invoices = ref([])
const mails = ref([])
const rules = ref([])
const importVisible = ref(false)
const genVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const stForm = reactive({ ccy: 'CNY' })
const stFormRef = ref(null)
const mailVisible = ref(false)
const mailFormRef = ref(null)
const mailForm = reactive({ invoiceNos: [], receiver: '', receiverPhone: '', receiverEmail: '', address: '', recipientId: null })
const mailInvoiceOptions = ref([])
const mailRecipients = ref([])
const courierVisible = ref(false)
const courierForm = reactive({ courierCompany: '', courierNo: '' })
const currentMail = ref(null)
const recipientVisible = ref(false)
const recipientRows = ref([])
const recipientQuery = reactive({ keyword: '' })
const recipientFormVisible = ref(false)
const recipientFormRef = ref(null)
const recipientForm = reactive({})
const logVisible = ref(false)
const mailLogs = ref([])
const logQuery = reactive({ packageNo: '', status: '' })
const ebsVisible = ref(false)
const ebsForm = reactive({})
const redPushVisible = ref(false)
const redPushForm = reactive({})
// 客户编码是对账单归到哪家客户的唯一依据，缺失则导入的行无法核对
const stRules = computed(() => ({
  customerCode: commonRules.required(t('flow.bill.customerCodePh')),
  amount: commonRules.numberText(t('flow.request.phAmount')),
  taxAmount: commonRules.numberText(t('flow.bill.taxAmountPh')),
}))
const mailRules = computed(() => ({
  invoiceNos: commonRules.multiSelect(t('flow.bill.mailInvoicesPh')),
  receiver: commonRules.required(t('flow.bill.mailReceiverPh')),
  receiverEmail: [...commonRules.required(t('flow.bill.mailEmailPh')), ...commonRules.email()],
  address: commonRules.required(t('flow.bill.mailAddressPh')),
}))
const recipientRules = computed(() => ({
  recipientName: commonRules.required(t('flow.bill.mailReceiverPh')),
  email: [...commonRules.required(t('flow.bill.mailEmailPh')), ...commonRules.email()],
}))
const reconciledStatements = computed(() => (statements.value || []).filter((s) => s.status === 'RECONCILED'))
const genBillNo = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

const statusTag = (s) => ({ INVOICED: 'success', VOIDED: 'danger', RED_INVOICED: 'danger', PUSHED: 'warning' }[s] || 'primary')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'statement') statements.value = await arFlowApi.statements({})
    else if (tab.value === 'pre') invoices.value = await arFlowApi.preInvoices({})
    else if (tab.value === 'mail') mails.value = await arFlowApi.mailPackages({})
    else rules.value = await arFlowApi.invoiceRules()
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function doCheck(row) {
  await arFlowApi.checkStatement(row.id)
  ElMessage.success(t('flow.bill.msgChecked'))
  await load()
}

async function doAdjust(row) {
  // 41号审查 同源整改：window.prompt -> ElMessageBox.prompt（统一样式/可取消）
  const { value: note } = await ElMessageBox.prompt(t('flow.bill.adjustPromptTitle'), t('flow.bill.adjustBoxTitle'), {
    inputPlaceholder: t('flow.bill.adjustNotePh'), inputType: 'textarea',
    confirmButtonText: t('flow.request.btnConfirm'), cancelButtonText: t('common.cancel'),
  }).catch(() => ({}))
  if (note === undefined) return
  await arFlowApi.adjustStatement(row.id, note)
  ElMessage.success(t('flow.bill.msgReconciled'))
  await load()
}

async function doGen(billNo) {
  if (!String(billNo || '').trim()) return ElMessage.warning(t('flow.bill.reconciledBillNoPh'))
  const r = await arFlowApi.generate(billNo)
  ElMessage.success(t('flow.bill.msgGeneratedPre', { v1: r.length }))
  genVisible.value = false
  tab.value = 'pre'
  await load()
}

async function biz(row, action) {
  if (action === 'bizConfirm') {
    await arFlowApi.bizConfirm(row.id, t('flow.bill.operatorBiz'))
  } else {
    // 41号审查 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
    const { value: reason } = await ElMessageBox.prompt(t('flow.bill.rejectPromptTitle'), t('flow.bill.bizReject'), {
      inputPlaceholder: t('flow.bill.rejectReasonPh'), inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : t('flow.collab.errRejectReason')),
      confirmButtonText: t('flow.bill.confirmReject'), cancelButtonText: t('common.cancel'),
    }).catch(() => ({}))
    if (!reason) return
    await arFlowApi.bizReject(row.id, reason)
  }
  ElMessage.success(t('common.success'))
  await load()
}

function openWriteback(row) {
  current.value = row
  inputKind.value = 'writeback'
  inputTitle.value = t('flow.bill.writebackPromptTitle')
  inputText.value = ''
  inputVisible.value = true
}

function openRed(row) {
  current.value = row
  inputKind.value = 'red'
  inputTitle.value = t('flow.bill.creditNoticeNoLabel')
  inputText.value = ''
  inputVisible.value = true
}

function openExchange(row) {
  current.value = row
  inputKind.value = 'exchange'
  inputTitle.value = t('flow.bill.replaceReasonLabel')
  inputText.value = ''
  inputVisible.value = true
}

async function doInput() {
  // 回写的金税发票号、红字通知单号、换票原因都是后续税务/审计凭据，留空即无从追溯
  if (!inputText.value.trim()) return ElMessage.warning(t('flow.bill.msgFillRequired', { v1: inputTitle.value }))
  const id = current.value.id
  if (inputKind.value === 'writeback') await arFlowApi.writeback(id, { invoiceNo: inputText.value })
  else if (inputKind.value === 'red') await arFlowApi.red(id, inputText.value)
  else await arFlowApi.exchange(id, inputText.value)
  ElMessage.success(t('common.success'))
  inputVisible.value = false
  await load()
}

async function simple(row, action) {
  await arFlowApi[action](row.id)
  ElMessage.success(t('common.success'))
  await load()
}

async function mailAct(row, action) {
  if (action === 'send') {
    currentMail.value = row
    courierForm.courierCompany = ''
    courierForm.courierNo = ''
    courierVisible.value = true
    return
  }
  await arFlowApi[action](row.id)
  ElMessage.success(t('common.success'))
  await load()
}

async function doSendMail() {
  if (!courierForm.courierNo?.trim()) return ElMessage.warning(t('flow.bill.msgTrackingRequired'))
  await arFlowApi.sendMail(currentMail.value.id, { courierCompany: courierForm.courierCompany, courierNo: courierForm.courierNo })
  ElMessage.success(t('common.success'))
  courierVisible.value = false
  await load()
}

async function saveRule(rule) {
  await arFlowApi.saveRule(rule)
  ElMessage.success(t('flow.bill.msgSaved'))
  await load()
}

function openImport() {
  Object.keys(stForm).forEach((k) => delete stForm[k])
  stForm.ccy = 'CNY'
  // 导入人默认当前登录人，留空的对账单导入无法追溯经手人
  stForm.importUser = store.user?.realName || store.user?.username || ''
  importVisible.value = true
}

async function openMailCreate() {
  Object.keys(mailForm).forEach((k) => delete mailForm[k])
  mailForm.invoiceNos = []
  mailForm.recipientId = null
  try {
    const [list, recips] = await Promise.all([
      arFlowApi.preInvoices({ status: 'INVOICED' }),
      arFlowApi.mailRecipients({}),
    ])
    mailInvoiceOptions.value = (list || []).filter((i) => i.printedFlag)
    mailRecipients.value = recips || []
  } catch (e) {
    mailInvoiceOptions.value = []
    mailRecipients.value = []
    ElMessage.error(e.message)
  }
  mailVisible.value = true
}

function onRecipientChange(id) {
  const r = mailRecipients.value.find((x) => x.id === id)
  if (!r) return
  mailForm.receiver = r.recipientName || ''
  mailForm.receiverPhone = r.phone || ''
  mailForm.receiverEmail = r.email || ''
  mailForm.address = r.address || ''
}

// 新建邮包时直接选往来单位：四项从客户档案带出，已填过的不覆盖（允许就地改成本次的实际收件人）
function onMailCustomerPick(c) {
  if (!c) return
  if (!mailForm.receiver) mailForm.receiver = c.contactName || ''
  if (!mailForm.receiverPhone) mailForm.receiverPhone = c.contactPhone || ''
  if (!mailForm.receiverEmail) mailForm.receiverEmail = c.email || ''
  if (!mailForm.address) mailForm.address = c.address || ''
}

async function doCreateMail() {
  if (!(await validateForms([mailFormRef]))) return
  await arFlowApi.createMail({
    recipientId: mailForm.recipientId || null,
    receiver: mailForm.receiver,
    receiverPhone: mailForm.receiverPhone,
    receiverEmail: mailForm.receiverEmail,
    address: mailForm.address,
  }, mailForm.invoiceNos.join(','))
  ElMessage.success(t('flow.bill.msgMailCreated'))
  mailVisible.value = false
  await load()
}

// 收件人管理 / 邮件记录

function openRecipientManage() {
  recipientVisible.value = true
  loadRecipients()
}

async function loadRecipients() {
  try {
    recipientRows.value = await arFlowApi.mailRecipients({ keyword: recipientQuery.keyword || undefined })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openRecipientForm(row) {
  Object.keys(recipientForm).forEach((k) => delete recipientForm[k])
  Object.assign(recipientForm, row ? { ...row } : { isDefault: 0 })
  recipientFormVisible.value = true
}

// 选往来单位＝把客户档案里的联系人/电话/邮箱/地址带进地址簿表单；已填过的不覆盖，改完只落到地址簿，不要回写客户主数据
function onRecipientCustomer(c) {
  recipientForm.customerName = c?.customerName || ''
  if (!c) return
  if (!recipientForm.recipientName) recipientForm.recipientName = c.contactName || ''
  if (!recipientForm.phone) recipientForm.phone = c.contactPhone || ''
  if (!recipientForm.email) recipientForm.email = c.email || ''
  if (!recipientForm.address) recipientForm.address = c.address || ''
}

async function saveRecipient() {
  if (!(await validateForms([recipientFormRef]))) return
  if (recipientForm.id) await arFlowApi.updateMailRecipient({ ...recipientForm })
  else await arFlowApi.createMailRecipient({ ...recipientForm })
  ElMessage.success(t('flow.bill.msgRecipientSaved'))
  recipientFormVisible.value = false
  await loadRecipients()
}

async function deleteRecipient(row) {
  try {
    await ElMessageBox.confirm(t('flow.bill.recipientDeleteConfirm', { v1: row.recipientName }), t('common.tip'), { type: 'warning' })
  } catch (e) {
    return
  }
  await arFlowApi.deleteMailRecipient(row.id)
  ElMessage.success(t('common.success'))
  await loadRecipients()
}

async function openMailLog(packageNo) {
  logQuery.packageNo = packageNo || ''
  logQuery.status = ''
  logVisible.value = true
  await loadMailLog()
}

async function loadMailLog() {
  try {
    mailLogs.value = await arFlowApi.mailNotifyLog({
      packageNo: logQuery.packageNo || undefined,
      status: logQuery.status || undefined,
    })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doImport() {
  if (!(await validateForms([stFormRef]))) return
  await arFlowApi.importStatement({ ...stForm, companyId: store.user?.companyId })
  ElMessage.success(t('flow.bill.msgImported'))
  importVisible.value = false
  await load()
}

async function doImportExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await arFlowApi.importStatementExcel(fd)
  ElMessage.success(t('flow.bill.msgExcelImported', { v1: res.created }) + (res.errors?.length ? t('flow.badDebt.msgExcelFailSuffix', { v1: res.errors.length, v2: res.errors[0] }) : ''))
  await load()
}

function onStatementCustomer(c) {
  stForm.customerName = c?.customerName || ''
}

function openEbsAdjust() {
  Object.keys(ebsForm).forEach((k) => delete ebsForm[k])
  ebsVisible.value = true
}

async function doEbsAdjust() {
  if (!ebsForm.adjustNo?.trim()) return ElMessage.warning(t('flow.bill.erpAdjustNoPh'))
  if (!ebsForm.adjustType?.trim()) return ElMessage.warning(t('flow.bill.erpAdjustTypePh'))
  const r = await arGapApi.pushEbsAdjust({
    adjustNo: ebsForm.adjustNo,
    customerCode: ebsForm.customerCode || null,
    adjustType: ebsForm.adjustType,
    amount: ebsForm.amount || 0,
    taxAmount: ebsForm.taxAmount || 0,
    statementNo: ebsForm.statementNo || null,
  })
  ElMessage.success(t('flow.bill.msgAdjustReceived', { v1: r.status }) + (r.statementNo ? t('flow.bill.msgAutoRecheck') : ''))
  ebsVisible.value = false
  await load()
}

function openRedPush() {
  Object.keys(redPushForm).forEach((k) => delete redPushForm[k])
  redPushVisible.value = true
}

async function doRedPush() {
  if (!redPushForm.ebsCreditMemoNo?.trim()) return ElMessage.warning(t('flow.bill.erpMemoNoPh'))
  if (!redPushForm.preInvoiceNo) return ElMessage.warning(t('flow.bill.erpInvoicePickPh'))
  await arGapApi.pushRedInvoice({
    ebsCreditMemoNo: redPushForm.ebsCreditMemoNo,
    preInvoiceNo: redPushForm.preInvoiceNo,
  })
  ElMessage.success(t('flow.bill.msgNegativeReceived'))
  redPushVisible.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.tip { font-size: 12px; color: #e6a23c; margin-top: 6px; }
.sub { font-size: 11px; color: #909399; }
</style>
