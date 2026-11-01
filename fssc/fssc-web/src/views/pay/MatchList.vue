<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <!-- 概览统计卡片 -->
    <el-row class="cm-target-match" :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card class="cm-target-match" shadow="hover" body-style="padding: 12px 16px">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#409eff"><Document /></el-icon>
            <div>
              <div style="font-size: 13px; color: #909399">{{ $t('pay.match.statTotalBills') }}</div>
              <div style="font-size: 24px; font-weight: 700">{{ stats.total }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 12px 16px">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#67c23a"><CircleCheck /></el-icon>
            <div>
              <div style="font-size: 13px; color: #909399">{{ $t('pay.match.stMatched') }}</div>
              <div style="font-size: 24px; font-weight: 700; color: #67c23a">{{ stats.matched }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 12px 16px">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#e6a23c"><Warning /></el-icon>
            <div>
              <div style="font-size: 13px; color: #909399">{{ $t('pay.match.stWarnDiff') }}</div>
              <div style="font-size: 24px; font-weight: 700; color: #e6a23c">{{ stats.warn }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 12px 16px">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-icon size="32" color="#f56c6c"><CircleClose /></el-icon>
            <div>
              <div style="font-size: 13px; color: #909399">{{ $t('pay.match.stFailedFrozen') }}</div>
              <div style="font-size: 24px; font-weight: 700; color: #f56c6c">{{ stats.failed }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <b>{{ $t('pay.match.pageTitle') }}</b>
          <el-input v-model="q.keyword" :placeholder="$t('pay.match.phKeyword')" clearable style="width: 160px" @keyup.enter="load" />
          <el-select v-model="q.matchStatus" :placeholder="$t('pay.match.colMatchStatus')" clearable style="width: 140px" @change="load">
            <el-option :label="$t('pay.match.stMatched')" value="MATCHED" />
            <el-option :label="$t('pay.match.stWarnDiff')" value="WARN" />
            <el-option :label="$t('pay.match.stFailed')" value="FAILED" />
            <!-- 手工匹配链办结后的最终状态，结论落 exp_bill.ext_json.matchVerdict -->
            <el-option :label="$t('pay.match.stManualClosed')" value="MANUAL_MATCHED" />
          </el-select>
          <el-button type="primary" size="small" @click="load">{{ $t('common.search') }}</el-button>
          <el-button size="small" @click="batchRecheck" :loading="batchLoading">{{ $t('pay.match.btnBatchRecalc') }}</el-button>
          <el-tooltip :content="$t('pay.match.hintRuleChain')" placement="left">
            <el-button size="small" link>{{ $t('pay.match.btnRuleGuide') }}</el-button>
          </el-tooltip>
        </div>
      </template>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table class="cm-target-match" :data="rows" size="small" v-else max-height="520"
                @row-click="openDetail" @selection-change="handleSelectionChange" style="cursor:pointer"
                :row-class-name="matchRowClass" :empty-text="$t('common.empty')" @row-dblclick="openDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="inline-chain-vis">
              <div class="inline-chain-flow">
                <div v-for="(node, i) in inlineChainNodes(row)" :key="node.key" class="inline-chain-item">
                  <div class="inline-chain-node" :class="node.cls">
                    <span class="inline-node-label">{{ node.label }}</span>
                    <span class="inline-node-no">{{ node.no }}</span>
                    <span class="inline-node-amt">{{ node.amount }}</span>
                  </div>
              <div v-if="i < inlineChainNodes(row).length - 1" class="inline-chain-variance" :class="node.varCls">
                {{ node.varText }}
              </div>
                </div>
              </div>
              <el-tag :type="statusType(row.matchStatus)" size="small" effect="dark" style="margin-top:6px">
                {{ $t('pay.match.chainReady', { v1: statusLabel(row.matchStatus), v2: inlineChainNodes(row).filter(n => n.ok).length }) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="billNo" :label="$t('pay.shared.colPayBillNo')" width="145" fixed  sortable />
        <el-table-column prop="title" :label="$t('pay.shared.colTitle')" min-width="110" show-overflow-tooltip  sortable />
        <el-table-column :label="$t('pay.match.colChain')" width="160" align="center">
          <template #default="{ row }">
            <div class="chain-dots">
              <el-tooltip v-for="d in chainDots(row)" :key="d.key" :content="d.tip" placement="top">
                <span class="chain-dot" :class="d.cls">{{ d.label }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colPo')" width="130">
          <template #default="{ row }">
            <span v-if="row.poNo" style="color: #409eff">{{ row.poNo }}</span>
            <el-tag v-else type="danger" size="small">{{ $t('pay.match.stUnlinked') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colGrn')" width="130">
          <template #default="{ row }">
            <span v-if="row.receiptNo">{{ row.receiptNo }}</span>
            <el-tag v-else type="danger" size="small">{{ $t('pay.match.stUnlinked') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colLinkedInvoice')" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colThisPayment')" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.payAmount > (row.invoiceAmount || 0) ? '#f56c6c' : '' }">{{ fmt(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colMatchStatus')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.matchStatus)" size="small">{{ statusLabel(row.matchStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colFrozen')" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.frozen" type="danger" size="small">{{ $t('pay.match.colFrozen') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colRecon')" width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reconStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ row.reconStatus === 'CONFIRMED' ? t('pay.shared.stConfirmed') : t('pay.match.stUnconfirmed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('pay.match.colIssue')" width="55" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.issues && row.issues.length" :value="row.issues.length" type="warning" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('pay.shared.btnTrace') }}</el-button>
            <el-button link size="small" type="primary" @click.stop="openDetail(row)">{{ $t('pay.shared.btnDetails') }}</el-button>
            <el-button link size="small" type="warning" @click.stop="openLinkDialog(row)">{{ $t('pay.match.btnLinkBills') }}</el-button>
            <!-- 人工匹配链已办结的行不再提供「派单」出口，否则最终状态会被重复派工淹没 -->
            <el-button v-if="row.matchStatus !== 'MATCHED' && row.matchStatus !== 'MANUAL_MATCHED'" link size="small" type="danger" @click.stop="routeIssue(row)">{{ $t('pay.match.btnRoute') }}</el-button>
            <el-button link size="small" type="primary" @click.stop="manualMatch(row)">{{ $t('pay.match.btnManualMatch') }}</el-button>
            <el-button link size="small" @click.stop="checkOne(row)">{{ $t('pay.match.btnRecalc') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('pay.shared.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="q.pageSize" :current-page="q.pageNum"
                     @current-change="(n) => { q.pageNum = n; load() }" />
    </el-card>

    <!-- 三单匹配明细弹窗 -->
    <el-dialog v-model="detailVisible" :title="$t('pay.match.ctxDetailTitle')" width="900px" top="5vh">
      <template v-if="detail">
        <!-- 付款单信息 -->
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item :label="$t('pay.shared.colPayBillNo')">{{ detail.billNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.shared.colTitle')">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.shared.colBillStatus')">{{ billStatusText(detail.billStatus) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.match.ctxThisPayAmount')">
            <span style="font-size: 16px; font-weight: 700; color: #409eff">{{ fmt(detail.payAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('pay.match.colMatchStatus')">
            <el-tag :type="statusType(detail.matchStatus)" size="small">{{ statusLabel(detail.matchStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('pay.match.ctxPayFrozen')">
            <el-tag :type="detail.frozen ? 'danger' : 'success'" size="small">{{ detail.frozen ? t('pay.match.stFrozen') : t('pay.match.stNotFrozen') }}</el-tag>
          </el-descriptions-item>
          <!-- 人工匹配链的办结结论（最终状态与判定意见都在这里显形） -->
          <el-descriptions-item v-if="detail.matchStatus === 'MANUAL_MATCHED'" :label="$t('pay.match.ctxManualVerdict')" :span="3">
            <span style="color: #67c23a; font-weight: 600">{{ detail.manualVerdict || t('pay.match.stManualClosed') }}</span>
            <span v-if="detail.manualProcInstId" style="margin-left: 12px; color: #909399; font-size: 12px">
              {{ $t('pay.match.procInst', { v1: detail.manualProcInstId }) }}</span>
            <span v-else style="margin-left: 12px; color: #909399; font-size: 12px">{{ $t('pay.match.noProcInst') }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 可视化匹配链路 -->
        <div class="match-chain-vis" style="margin-bottom: 16px">
          <div class="chain-flow">
            <div v-for="(node, idx) in chainNodes" :key="node.key" class="chain-node-wrapper">
              <div class="chain-node" :class="node.cls">
                <el-icon v-if="node.ok" size="18"><CircleCheck /></el-icon>
                <el-icon v-else size="18"><CircleClose /></el-icon>
                <span class="chain-node-label">{{ node.label }}</span>
                <span class="chain-node-value">{{ node.value }}</span>
                <span v-if="node.amount" class="chain-node-amount" :style="{ color: node.amountColor }">{{ node.amount }}</span>
              </div>
              <div v-if="node.arrow" class="chain-arrow-wrap">
                <div class="chain-arrow" :class="node.arrowCls">
                  <el-icon size="16"><ArrowRight /></el-icon>
                </div>
                <div v-if="node.varText" class="chain-variance" :class="node.varCls">
                  {{ node.varText }}
                </div>
              </div>
            </div>
          </div>
          <!-- 规则合规指示 -->
          <div class="chain-rules">
            <span class="rule-item" :class="rulePoGrn ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="rulePoGrn" /><CircleClose v-else /></el-icon>
              {{ $t('pay.match.ruleGrnVsPo') }}</span>
            <span class="rule-item" :class="ruleGrnInv ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleGrnInv" /><CircleClose v-else /></el-icon>
              {{ $t('pay.match.ruleInvVsGrn') }}</span>
            <span class="rule-item" :class="ruleInvPay ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleInvPay" /><CircleClose v-else /></el-icon>
              {{ $t('pay.match.rulePayVsInv') }}</span>
            <span class="rule-item" :class="ruleAllPresent ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleAllPresent" /><CircleClose v-else /></el-icon>
              {{ $t('pay.match.ruleAllFour') }}</span>
          </div>
          <el-progress
            v-if="detail.matchStatus"
            :percentage="matchPercent"
            :color="matchBarColor"
            :show-text="true"
            :format="() => statusLabel(detail.matchStatus)"
            style="margin-top: 12px"
          />
        </div>

        <!-- 三单对比表 -->
        <el-table :data="comparisonRows" border size="small" style="margin-bottom: 16px" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="label" :label="$t('pay.match.colCompareDim')" width="110"  sortable />
          <el-table-column :label="$t('pay.match.colPoFull')" min-width="120">
            <template #default="{ row }">
              <span v-if="row.po !== null && row.po !== undefined">{{ row.po }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('pay.match.colGrn')" min-width="120">
            <template #default="{ row }">
              <span v-if="row.receipt !== null && row.receipt !== undefined">{{ row.receipt }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('pay.match.colInvoice')" min-width="120">
            <template #default="{ row }">
              <span v-if="row.invoice !== null && row.invoice !== undefined">{{ row.invoice }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('pay.match.colPayBill')" min-width="120">
            <template #default="{ row }">
              <span v-if="row.pay !== null && row.pay !== undefined" style="font-weight: 600">{{ row.pay }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 关联发票明细 -->
        <div v-if="invoiceList.length" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px; font-size: 14px">{{ $t('pay.match.secLinkedInvoices') }}</div>
          <el-table :data="invoiceList" border size="small" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="invoiceNo" :label="$t('pay.shared.colInvoiceNo')" width="140"  sortable />
            <el-table-column prop="invoiceCode" :label="$t('pay.match.colInvoiceCode')" width="120"  sortable />
            <el-table-column prop="sellerName" :label="$t('pay.match.colSellerName')" min-width="120" show-overflow-tooltip  sortable />
            <el-table-column prop="invoiceDate" :label="$t('field.invoiceDate')" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column prop="netAmount" :label="$t('pay.match.colNetAmount')" width="110" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
            </el-table-column>
            <el-table-column prop="taxAmount" :label="$t('field.taxAmount')" width="90" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column prop="totalAmount" :label="$t('field.totalWithTax')" width="110" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="amount" :label="$t('pay.match.colWriteOffAmount')" width="100" align="right" sortable>
              <template #default="{ row }"><span style="color: #409eff">{{ fmt(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 差异/问题列表 -->
        <div v-if="detail.issues && detail.issues.length" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px; font-size: 14px; color: #e6a23c">{{ $t('pay.match.secDiffIssues') }}</div>
          <el-table :data="detail.issues" border size="small" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
            <el-table-column :label="$t('pay.match.colLevel')" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">{{ row.level === 'ERROR' ? t('pay.match.stHardError') : t('pay.match.stWarn') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" :label="$t('pay.shared.colDiffType')" width="180" sortable>
              <template #default="{ row }">{{ issueCodeText(row.code) }}</template>
            </el-table-column>
            <el-table-column prop="msg" :label="$t('field.desc')" min-width="250" show-overflow-tooltip  sortable />
            <el-table-column :label="$t('pay.match.colOwner')" width="100">
              <template #default="{ row }">{{ issueRouteText(row.code) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 合同信息（展示维度） -->
        <el-descriptions v-if="detail.contractNo" :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item :label="$t('pay.match.ctxLinkedContract')">{{ detail.contractNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.shared.colContractName')">{{ detail.contractName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.shared.colContractAmount')">{{ fmt(detail.contractAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('pay.shared.secReconLetter')">
            <el-tag :type="detail.reconStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ detail.reconStatus === 'CONFIRMED' ? t('pay.shared.stConfirmed') : (detail.supplierName ? t('pay.match.stUnconfirmed') : t('pay.match.stUnknownPayee')) }}
            </el-tag>
            <span v-if="detail.supplierName" style="margin-left: 8px; color: #909399; font-size: 12px">{{ detail.supplierName }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
        <el-button type="warning" @click="openLinkDialog(detail)">{{ $t('pay.match.btnLinkBills') }}</el-button>
        <el-button v-if="detail && detail.matchStatus !== 'MATCHED' && detail.matchStatus !== 'MANUAL_MATCHED'" type="danger" @click="routeIssue(detail)">{{ $t('pay.shared.btnExceptionRoute') }}</el-button>
        <!-- 人工匹配链办结后，派单/复审两个「未办结」出口必须收起，否则同一单据可被反复发起人工链 -->
        <el-button v-if="detail && detail.matchStatus !== 'MATCHED' && detail.matchStatus !== 'MANUAL_MATCHED'" type="primary" @click="reviewBill(detail)">{{ $t('pay.match.btnStartReview') }}</el-button>
        <el-button v-if="detail && detail.matchStatus !== 'MANUAL_MATCHED'" type="primary" plain @click="manualMatch(detail)">{{ $t('pay.match.btnManualMatch') }}</el-button>
        <el-button v-if="detail && detail.matchStatus === 'MANUAL_MATCHED'" type="primary" plain @click="viewManualVerdict">{{ $t('pay.match.btnViewVerdict') }}</el-button>
      </template>
    </el-dialog>

    <!-- 手工关联PO/收货单弹窗 -->
    <el-dialog v-model="linkVisible" :title="$t('pay.match.ctxLinkTitle')" width="600px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        {{ $t('pay.match.hintMatchFlow') }}
      </el-alert>
      <el-form :model="linkForm" :rules="linkRules" label-width="100px" size="default">
        <el-form-item :label="$t('pay.shared.colPayBillNo')">
          <el-input :model-value="linkRow?.billNo" disabled />
        </el-form-item>
        <el-form-item :label="$t('pay.match.colPo')">
          <el-select v-model="linkForm.poId" filterable clearable :placeholder="$t('pay.shared.phPickPo')" style="width: 100%"
                     @change="onPoChange" :loading="poLoading">
            <el-option v-for="po in poOptions" :key="po.id"
                       :label="`${po.poNo} | ${po.supplierName || ''} | ${fmt(po.amount)}`"
                       :value="po.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('pay.match.colGrn')">
          <el-select v-model="linkForm.poReceiptId" filterable clearable :placeholder="$t('pay.match.phPickGrn')" style="width: 100%"
                     :loading="receiptLoading" :disabled="!linkForm.poId">
            <el-option v-for="rc in receiptOptions" :key="rc.id"
                       :label="`${rc.receiptNo} | ${rc.supplierName || ''} | ${fmt(rc.amount)}`"
                       :value="rc.id" />
          </el-select>
          <div v-if="linkForm.poId && !receiptOptions.length" style="color: #909399; font-size: 12px; margin-top: 4px">
            {{ $t('pay.match.hintNoGrn') }}</div>
        </el-form-item>
        <!-- 合同环节补挂入口（缺了它，NO_CONTRACT 的出口文案会指向不存在的入口） -->
        <el-form-item :label="$t('pay.match.colContract')">
          <el-select v-model="linkForm.contractId" filterable clearable :placeholder="$t('pay.match.phPickContract')" style="width: 100%"
                     :loading="contractLoading">
            <el-option v-for="c in contractOptions" :key="c.id"
                       :label="`${c.contractNo} | ${c.partyBName || c.contractName || ''} | ${fmt(c.amount)}`"
                       :value="c.id" />
          </el-select>
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            {{ $t('pay.match.hintContractLeg') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doLink" :loading="linkLoading">{{ $t('pay.match.btnConfirmLink') }}</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="INVOICE_MATCH" />

  <CoachMark ref="matchCm" :guide-key="_guideKey" :selector="'.cm-target-match'" :title="$t('pay.match.pageTitle')" :text="$t('pay.match.coachText')" />
</template>

<script setup>

import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, CircleCheck, Warning, CircleClose, ArrowRight, ArrowDown, Check } from '@element-plus/icons-vue'
import { opApi, purchaseApi, contractApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { STATUS_LABEL } from '@/constants/billStatus.js'
import { formatDate, formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const matchCm = ref(null)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const batchLoading = ref(false)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: '', matchStatus: '' })

const stats = computed(() => {
  const s = { total: total.value, matched: 0, warn: 0, failed: 0 }
  rows.value.forEach(r => {
    if (r.matchStatus === 'MATCHED') s.matched++
    else if (r.matchStatus === 'FAILED') s.failed++
    else if (r.matchStatus === 'WARN') s.warn++
  })
  return s
})

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function statusType(s) {
  return s === 'MATCHED' ? 'success' : s === 'FAILED' ? 'danger' : s === 'WARN' ? 'warning'
    : s === 'MANUAL_MATCHED' ? 'success' : 'info'
}
function statusLabel(s) {
  // MANUAL_MATCHED＝人工匹配链已办结并解除冻结（自动判定仍存在的差异在明细里照常列出，
  // 最终状态结论与原判定两件事都要看得见，故文案写"人工匹配已办结"而不是"三单一致"）
  // 状态全集＝后端 setMatchStatus 五值（MATCHED/WARN/FAILED/UNMATCHED/MANUAL_MATCHED），漏一枚就直接显示英文
  return { MATCHED: t('pay.match.stMatched'), UNMATCHED: t('pay.match.stUnmatched'), WARN: t('pay.match.stWarnDiff'), FAILED: t('pay.match.stFailed'), MANUAL_MATCHED: t('pay.match.stManualClosed') }[s] || s
}

// 可视化匹配链路
function matchRowClass({ row }) {
  if (row.matchStatus === 'MATCHED') return 'row-green'
  if (row.matchStatus === 'FAILED') return 'row-red'
  if (row.matchStatus === 'WARN') return 'row-yellow'
  return ''
}

function chainDots(row) {
  const has = (v) => v != null && v !== '' && v !== 0
  return [
    { key: 'po',  label: 'PO',  tip: row.poNo ? t('pay.match.tipPo', { v1: row.poNo }) : t('pay.match.noPoLinked'),
      cls: has(row.poNo) ? 'dot-green' : 'dot-red' },
    { key: 'grn', label: 'GRN', tip: row.receiptNo ? t('pay.match.tipGrn', { v1: row.receiptNo }) : t('pay.match.noGrnLinked'),
      cls: has(row.receiptNo) ? 'dot-green' : 'dot-red' },
    { key: 'inv', label: 'INV', tip: row.invoiceAmount ? t('pay.match.tipInv', { v1: fmt(row.invoiceAmount) }) : t('pay.match.noInvLinked'),
      cls: has(row.invoiceAmount) ? 'dot-green' : 'dot-red' },
    { key: 'pay', label: 'PAY', tip: t('pay.match.tipPay', { v1: fmt(row.payAmount) }),
      cls: row.payAmount > (row.invoiceAmount || 0) ? 'dot-red'
         : row.matchStatus === 'MATCHED' ? 'dot-green'
         : row.matchStatus === 'WARN' ? 'dot-yellow' : 'dot-red' },
  ]
}

const chainNodes = computed(() => {
  if (!detail.value) return []
  const d = detail.value
  const has = (v) => v != null && v !== '' && v !== 0
  const amtOk = (pay, inv) => !has(inv) || Number(pay) <= Number(inv)
  const allPresent = has(d.poNo) && has(d.receiptNo) && has(d.invoiceAmount)
  const fullyMatched = d.matchStatus === 'MATCHED'
  const nodeCls = (present, ok) => {
    if (!present) return 'node-red'
    if (fullyMatched) return 'node-green'
    return ok ? 'node-yellow' : 'node-yellow'
  }
  const arrowCls = (ok) => fullyMatched ? 'arrow-green' : ok ? 'arrow-yellow' : 'arrow-red'

  // 差异计算
  const poAmt = Number(d.poAmount || 0)
  const grnAmt = Number(d.receiptAmount || 0)
  const invAmt = Number(d.invoiceAmount || 0)
  const payAmt = Number(d.payAmount || 0)

  const varPoGrn = has(d.poNo) && has(d.receiptNo) ? (grnAmt - poAmt) : null
  const varGrnInv = has(d.receiptNo) && has(d.invoiceAmount) ? (invAmt - grnAmt) : null
  const varInvPay = has(d.invoiceAmount) ? (payAmt - invAmt) : null

  const fmtVar = (v) => {
    if (v == null) return ''
    const sign = v > 0 ? '+' : ''
    return sign + fmt(v)
  }
  const varCls = (v) => {
    if (v == null) return 'var-none'
    if (v <= 0) return 'var-ok'
    return 'var-warn'
  }

  return [
    { key: 'po', label: t('pay.match.colPo'), value: d.poNo || t('pay.match.stUnlinked'), ok: has(d.poNo),
      amount: has(d.poNo) && poAmt ? '¥ ' + fmt(poAmt) : '', amountColor: '#409eff',
      cls: nodeCls(has(d.poNo)), arrow: true, arrowCls: arrowCls(has(d.poNo)),
      varText: fmtVar(varPoGrn), varCls: varCls(varPoGrn) },
    { key: 'grn', label: t('pay.match.colGrn'), value: d.receiptNo || t('pay.match.stUnlinked'), ok: has(d.receiptNo),
      amount: has(d.receiptNo) && grnAmt ? '¥ ' + fmt(grnAmt) : '', amountColor: '#909399',
      cls: nodeCls(has(d.receiptNo)), arrow: true, arrowCls: arrowCls(has(d.receiptNo) && has(d.poNo)),
      varText: fmtVar(varGrnInv), varCls: varCls(varGrnInv) },
    { key: 'inv', label: t('pay.match.colInvoice'), value: has(d.invoiceAmount) ? fmt(d.invoiceAmount) + ' 元' : t('pay.match.stUnlinked'), ok: has(d.invoiceAmount),
      amount: has(d.invoiceAmount) ? '¥ ' + fmt(invAmt) : '', amountColor: '#e6a23c',
      cls: nodeCls(has(d.invoiceAmount)), arrow: true, arrowCls: arrowCls(has(d.invoiceAmount) && has(d.receiptNo)),
      varText: fmtVar(varInvPay), varCls: varCls(varInvPay) },
    { key: 'pay', label: t('pay.match.colPayBill'), value: fmt(d.payAmount) + ' 元', ok: amtOk(d.payAmount, d.invoiceAmount),
      amount: '¥ ' + fmt(payAmt), amountColor: '#67c23a',
      cls: fullyMatched ? 'node-green' : amtOk(d.payAmount, d.invoiceAmount) ? 'node-yellow' : 'node-red',
      arrow: false, arrowCls: '', varText: '', varCls: '' },
  ]
})

// 规则合规
const rulePoGrn = computed(() => {
  if (!detail.value) return false
  const d = detail.value
  const has = (v) => v != null && v !== '' && v !== 0
  if (!has(d.poNo) || !has(d.receiptNo)) return false
  return Number(d.receiptAmount || 0) <= Number(d.poAmount || 0)
})
const ruleGrnInv = computed(() => {
  if (!detail.value) return false
  const d = detail.value
  const has = (v) => v != null && v !== '' && v !== 0
  if (!has(d.receiptNo) || !has(d.invoiceAmount)) return false
  return Number(d.invoiceAmount || 0) <= Number(d.receiptAmount || 0)
})
const ruleInvPay = computed(() => {
  if (!detail.value) return false
  const d = detail.value
  const has = (v) => v != null && v !== '' && v !== 0
  if (!has(d.invoiceAmount)) return false
  return Number(d.payAmount || 0) <= Number(d.invoiceAmount || 0)
})
const ruleAllPresent = computed(() => {
  if (!detail.value) return false
  const d = detail.value
  const has = (v) => v != null && v !== '' && v !== 0
  return has(d.poNo) && has(d.receiptNo) && has(d.invoiceAmount) && has(d.payAmount)
})

// 列表行内联链路
function inlineChainNodes(row) {
  const has = (v) => v != null && v !== '' && v !== 0
  const poAmt = Number(row.poAmount || 0)
  const grnAmt = Number(row.receiptAmount || 0)
  const invAmt = Number(row.invoiceAmount || 0)
  const payAmt = Number(row.payAmount || 0)

  const fmtVar = (v) => {
    if (v == null) return ''
    const sign = v > 0 ? '+' : ''
    return sign + fmt(Math.abs(v) > 0 ? v : 0)
  }

  return [
    {
      key: 'po', label: t('pay.match.colPo'), no: row.poNo || '—', amount: poAmt ? fmt(poAmt) : '',
      ok: has(row.poNo), cls: has(row.poNo) ? 'ic-green' : 'ic-red',
      varText: has(row.poNo) && has(row.receiptNo) ? fmtVar(grnAmt - poAmt) : '',
      varCls: grnAmt - poAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: grnAmt - poAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'grn', label: t('pay.match.colGrn'), no: row.receiptNo || '—', amount: grnAmt ? fmt(grnAmt) : '',
      ok: has(row.receiptNo), cls: has(row.receiptNo) ? 'ic-green' : 'ic-red',
      varText: has(row.receiptNo) && has(row.invoiceAmount) ? fmtVar(invAmt - grnAmt) : '',
      varCls: invAmt - grnAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: invAmt - grnAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'inv', label: t('pay.match.colInvoice'), no: invAmt ? fmt(invAmt) : '—', amount: '',
      ok: has(row.invoiceAmount), cls: has(row.invoiceAmount) ? 'ic-green' : 'ic-red',
      varText: has(row.invoiceAmount) ? fmtVar(payAmt - invAmt) : '',
      varCls: payAmt - invAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: payAmt - invAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'pay', label: t('pay.match.legPay'), no: fmt(payAmt), amount: '',
      ok: row.matchStatus === 'MATCHED', cls: row.matchStatus === 'MATCHED' ? 'ic-green' : row.matchStatus === 'WARN' ? 'ic-yellow' : 'ic-red',
      varText: '', varCls: '', varIcon: '',
    },
  ]
}

const matchPercent = computed(() => {
  if (!detail.value) return 0
  if (detail.value.matchStatus === 'MATCHED') return 100
  const d = detail.value
  let score = 0
  if (d.poNo) score += 25
  if (d.receiptNo) score += 25
  if (d.invoiceAmount) score += 25
  if (d.payAmount <= (d.invoiceAmount || 0)) score += 25
  return score
})

const matchBarColor = computed(() => {
  const p = matchPercent.value
  if (p >= 100) return '#67c23a'
  if (p >= 50) return '#e6a23c'
  return '#f56c6c'
})
function billStatusText(s) { return STATUS_LABEL[s] || s }
function issueCodeText(code) {
  // 差异码全集＝后端 ThreeWayMatchService 的 MatchIssue 码（12 枚）；缺映射会直接显示英文码，
  // 兜底改「其他差异」，原始码仍可在「差异描述」列（后端中文）里看到，不丢信息。
  return {
    NO_PO: t('pay.match.noPoLinked'),
    NO_RECEIPT: t('pay.match.noGrnLinked'),
    NO_CONTRACT: t('pay.match.codeNoContract'),
    PAY_EXCEED_INVOICE: t('pay.match.codePayExceed'),
    PAY_EXCEED_PO: t('pay.match.codePayExceedPo'),
    INVOICE_OVER_RECEIPT: t('pay.match.codeInvOverGrn'),
    INVOICE_IN_TOLERANCE: t('pay.match.codeInvInTolerance'),
    RECEIPT_OVER_PO: t('pay.match.codeGrnOverPo'),
    RECEIPT_QTY_OVER_PO: t('pay.match.codeGrnQtyOverPo'),
    RECEIPT_IN_TOLERANCE: t('pay.match.codeGrnInTolerance'),
    RECON_UNCONFIRMED: t('pay.match.codeReconUnconfirmed'),
    PAYEE_NOT_SUPPLIER: t('pay.match.codePayeeNotSupplier'),
  }[code] || t('pay.match.codeOtherDiff')
}
function issueRouteText(code) {
  // 与后端 ISSUE_ROUTE（ThreeWayMatchService:183）逐枚对齐；后端未登记的码 getOrDefault=FIN_AP，
  // 故此处兜底同为应付会计＝同一规则，别在前端另起一套责任岗。
  return {
    NO_PO: t('pay.match.rolePoManager'),
    NO_CONTRACT: t('pay.match.rolePoManager'),
    RECEIPT_OVER_PO: t('pay.match.rolePoManager'),
    RECEIPT_QTY_OVER_PO: t('pay.match.rolePoManager'),
    RECEIPT_IN_TOLERANCE: t('pay.match.rolePoManager'),
    NO_RECEIPT: t('pay.match.roleWarehouse'),
    PAY_EXCEED_INVOICE: t('pay.match.roleApAccountant'),
    INVOICE_OVER_RECEIPT: t('pay.match.roleApAccountant'),
    INVOICE_IN_TOLERANCE: t('pay.match.roleApAccountant'),
    RECON_UNCONFIRMED: t('pay.match.roleReconClerk'),
  }[code] || t('pay.match.roleApAccountant')
}

async function load() {
  loading.value = true
  try {
    const r = await opApi.matchPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function checkOne(row) {
  try {
    const d = await opApi.matchCheck(row.billId)
    Object.assign(row, d)
    ElMessage.success(t('pay.match.msgRecalcDone') + statusLabel(d.matchStatus))
  } catch (e) { ElMessage.error(e.message) }
}

async function batchRecheck() {
  batchLoading.value = true
  let ok = 0
  for (const row of rows.value) {
    try {
      const d = await opApi.matchCheck(row.billId)
      Object.assign(row, d)
      ok++
    } catch (e) { /* continue */ }
  }
  batchLoading.value = false
  ElMessage.success(t('pay.match.msgBatchRecalcDone', { v1: ok, v2: rows.value.length }))
}

// 明细弹窗
const detailVisible = ref(false)
const detail = ref(null)
const invoiceList = ref([])

async function openDetail(row) {
  detail.value = row
  detailVisible.value = true
  invoiceList.value = []
  try {
    invoiceList.value = await opApi.matchInvoices(row.billId) || []
  } catch (e) { /* ignore */ }
}

const comparisonRows = computed(() => {
  if (!detail.value) return []
  const d = detail.value
  return [
    { label: t('field.docNo'), po: d.poNo, receipt: d.receiptNo, invoice: d.invoiceList?.length ? d.invoiceList.map(i => i.invoiceNo).join(', ') : '—', pay: d.billNo },
    { label: t('field.amount'), po: d.poAmount != null ? fmt(d.poAmount) : null, receipt: d.receiptAmount != null ? fmt(d.receiptAmount) : null, invoice: d.invoiceAmount != null ? fmt(d.invoiceAmount) : null, pay: d.payAmount != null ? fmt(d.payAmount) : null },
    { label: t('pay.match.lblPoReceivedAmount'), po: d.poReceived != null ? fmt(d.poReceived) : null, receipt: null, invoice: null, pay: null },
    { label: t('pay.match.lblQty'), po: d.poQty != null ? d.poQty : null, receipt: d.receiptQty != null ? d.receiptQty : null, invoice: null, pay: null },
    { label: t('pay.match.lblInvoicedAmount'), po: null, receipt: d.receiptInvoice != null ? fmt(d.receiptInvoice) : null, invoice: null, pay: null },
  ]
})

// 手工关联PO/收货单
const linkVisible = ref(false)
const linkRow = ref(null)
const linkForm = reactive({ poId: null, poReceiptId: null, contractId: null })

const linkRules = computed(() => ({
  poId: [{ required: true, message: t('pay.match.errPickPo') }],
  poReceiptId: [{ required: true, message: t('pay.match.errPickGrn') }],
}))
const poOptions = ref([])
const receiptOptions = ref([])
const poLoading = ref(false)
const receiptLoading = ref(false)
const linkLoading = ref(false)
// 合同环节候选（四单之合同环节的补挂入口，见 doLink 注释）
const contractOptions = ref([])
const contractLoading = ref(false)

async function openLinkDialog(row) {
  if (!row) return
  linkRow.value = row
  linkForm.poId = row.poId || null
  linkForm.poReceiptId = row.receiptId || null
  linkForm.contractId = row.contractId || null
  linkVisible.value = true
  detailVisible.value = false
  // 加载PO列表
  poLoading.value = true
  try {
    const r = await purchaseApi.orderPage({ pageNum: 1, pageSize: 100 })
    // 同族：三单匹配的 PO 候选同样不要出现已作废订单
    poOptions.value = (r.list || []).filter((p) => String((p && p.status) || '').toUpperCase() !== 'VOID')
  } catch (e) { ElMessage.error(t('pay.match.msgLoadPoFail')) } finally { poLoading.value = false }
  // 加载合同候选
  contractLoading.value = true
  try {
    const rc = await contractApi.page({ pageNum: 1, pageSize: 100 })
    contractOptions.value = (rc.list || []).filter(
      (c) => String((c && c.status) || '').toUpperCase() !== 'VOID')
  } catch (e) { contractOptions.value = [] } finally { contractLoading.value = false }
  // 如果有已选PO，加载对应收货单
  if (linkForm.poId) await onPoChange(linkForm.poId)
}

async function onPoChange(poId) {
  linkForm.poReceiptId = null
  receiptOptions.value = []
  if (!poId) return
  receiptLoading.value = true
  try {
    const r = await purchaseApi.receiptPage({ pageNum: 1, pageSize: 100, poId })
    receiptOptions.value = r.list || []
  } catch (e) { /* ignore */ } finally { receiptLoading.value = false }
}

async function doLink() {
  if (!linkForm.poId && !linkForm.poReceiptId) {
    ElMessage.warning(t('pay.match.msgPickPoOrGrn'))
    return
  }
  linkLoading.value = true
  try {
    await opApi.matchLink(linkRow.value.billId, { poId: linkForm.poId, poReceiptId: linkForm.poReceiptId, contractId: linkForm.contractId || null })
    ElMessage.success(t('pay.match.msgLinkedRecheck'))
    // 重新校验
    const d = await opApi.matchCheck(linkRow.value.billId)
    Object.assign(linkRow.value, d)
    linkVisible.value = false
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { linkLoading.value = false }
}

// 异常派单
async function routeIssue(row) {
  try {
    await ElMessageBox.confirm(
      t('pay.match.ctxRouteConfirm', { v1: row.billNo }),
      t('pay.match.ctxRouteTitle'),
      { type: 'warning' }
    )
    const r = await opApi.matchRoute(row.billId)
    const routing = r.routing || {}
    const roles = Object.entries(routing).map(([k, v]) => t('pay.match.routeSummary', { v1: roleName(k), v2: v.length })).join('；')
    ElMessage.success(t('pay.match.msgRouteOk', { v1: roles, v2: r.procInstId }))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

// 发起复审
async function reviewBill(row) {
  try {
    await ElMessageBox.confirm(
      t('pay.match.ctxReviewConfirm', { v1: row.billNo }),
      t('pay.match.ctxReviewTitle'),
      { type: 'warning' }
    )
    const procInstId = await opApi.matchReview(row.billId)
    ElMessage.success(t('pay.match.msgReviewStarted', { v1: procInstId }))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

// 手工三单匹配
async function manualMatch(row) {
  const matched = row.matchStatus === 'MATCHED'
  try {
    const { value } = await ElMessageBox.prompt(
      matched
        ? t('pay.match.ctxManualAlreadyOk', { v1: row.billNo })
        : t('pay.match.ctxManualConfirm', { v1: row.billNo }),
      t('pay.match.ctxManualTitle'),
      {
        inputType: 'textarea',
        inputPlaceholder: t('pay.match.phManualReason'),
        inputValidator: (v) => (matched && !(v || '').trim()) ? t('pay.match.errManualReason') : true,
      }
    )
    const procInstId = await opApi.matchManualReview(row.billId, (value || '').trim())
    ElMessage.success(t('pay.match.msgManualStarted', { v1: procInstId }))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

function roleName(code) {
  return { PURCHASE_MANAGER: t('pay.match.rolePoManager'), WH_KEEPER: t('pay.match.roleWarehouse'), FIN_AP: t('pay.match.roleApAccountant'), RECON_CLERK: t('pay.match.roleReconClerk') }[code] || code
}

onMounted(load)

function exportCsv() {
  if (!invoiceList.value.length) { ElMessage.warning(t('pay.shared.msgNoExportData')); return }
  const cols = [{ prop: 'invoiceNo', label: t('pay.shared.colInvoiceNo') }, { prop: 'invoiceCode', label: t('pay.match.colInvoiceCode') }, { prop: 'sellerName', label: t('pay.match.colSellerName') }, { prop: 'invoiceDate', label: t('field.invoiceDate') }, { prop: 'netAmount', label: t('pay.match.colNetAmount') }, { prop: 'taxAmount', label: t('field.taxAmount') }, { prop: 'totalAmount', label: t('field.totalWithTax') }, { prop: 'amount', label: t('pay.match.colWriteOffAmount') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = invoiceList.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `MatchList_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedCsv'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('pay.shared.msgPickExportRows')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('pay.shared.msgNoColumns')); return }
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
  a.download = `MatchList_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedRows', { v1: selRows.value.length }))
}


// Show guide on first visit
const _guideKey = 'fssc_guide_matchCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => matchCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
/* 行级颜色编码 */
:deep(.row-green) { background-color: rgba(103, 194, 58, 0.14) !important; }
:deep(.row-yellow) { background-color: rgba(230, 162, 60, 0.14) !important; }
:deep(.row-red) { background-color: rgba(245, 108, 108, 0.13) !important; }

/* 匹配链路小圆点 */
.chain-dots { display: flex; gap: 6px; justify-content: center; align-items: center; }
.chain-dot {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 50%; font-size: 10px; font-weight: 700;
  color: #fff; cursor: default;
}
.dot-green { background: #67c23a; }
.dot-yellow { background: #e6a23c; }
.dot-red { background: #f56c6c; }

/* 详情弹窗可视化链路 */
.match-chain-vis { background: var(--app-bg, #f5f7fa); border-radius: 8px; padding: 16px; }
.chain-flow { display: flex; align-items: center; justify-content: center; gap: 0; flex-wrap: wrap; }
.chain-node-wrapper { display: flex; align-items: center; }
.chain-node {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  min-width: 100px; padding: 12px 16px; border-radius: 8px; border: 2px solid;
}
.chain-node-label { font-size: 13px; font-weight: 600; }
.chain-node-value { font-size: 12px; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-green { border-color: #67c23a; background: rgba(103, 194, 58, 0.14); color: #67c23a; }
.node-yellow { border-color: #e6a23c; background: rgba(230, 162, 60, 0.14); color: #e6a23c; }
.node-red { border-color: #f56c6c; background: rgba(245, 108, 108, 0.13); color: #f56c6c; }
.chain-arrow { padding: 0 8px; color: #c0c4cc; }
.arrow-green { color: #67c23a; }
.arrow-yellow { color: #e6a23c; }
.arrow-red { color: #f56c6c; }

/* 详情链路金额与差异 */
.chain-arrow-wrap { display: flex; flex-direction: column; align-items: center; padding: 0 8px; }
.chain-node-amount { font-size: 13px; font-weight: 700; margin-top: 2px; }
.chain-variance {
  font-size: 11px; font-weight: 600; margin-top: 2px; white-space: nowrap;
  padding: 1px 6px; border-radius: 8px;
}
.var-ok { color: #67c23a; background: rgba(103, 194, 58, 0.14); }
.var-warn { color: #f56c6c; background: rgba(245, 108, 108, 0.13); }
.var-none { color: #c0c4cc; }

/* 规则合规指示 */
.chain-rules { display: flex; gap: 16px; justify-content: center; margin-top: 12px; flex-wrap: wrap; }
.rule-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 3px 10px; border-radius: 12px; font-weight: 500;
}
.rule-ok { color: #67c23a; background: rgba(103, 194, 58, 0.14); }
.rule-bad { color: #f56c6c; background: rgba(245, 108, 108, 0.13); }

/* 行内联链路可视化 */
.inline-chain-vis { padding: 8px 16px 12px; background: var(--app-hover, #f9fafc); }
.inline-chain-flow { display: flex; align-items: center; gap: 0; flex-wrap: wrap; }
.inline-chain-item { display: flex; align-items: center; }
.inline-chain-node {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px 14px; border-radius: 6px; border: 1.5px solid; min-width: 80px;
}
.inline-node-label { font-size: 11px; font-weight: 700; letter-spacing: 0.5px; }
.inline-node-no { font-size: 12px; color: var(--app-text-secondary, #606266); max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.inline-node-amt { font-size: 11px; color: #409eff; font-weight: 600; }
.ic-green { border-color: #67c23a; background: rgba(103, 194, 58, 0.14); }
.ic-green .inline-node-label { color: #67c23a; }
.ic-yellow { border-color: #e6a23c; background: rgba(230, 162, 60, 0.14); }
.ic-yellow .inline-node-label { color: #e6a23c; }
.ic-red { border-color: #f56c6c; background: rgba(245, 108, 108, 0.13); }
.ic-red .inline-node-label { color: #f56c6c; }
.inline-chain-variance {
  display: flex; align-items: center; gap: 2px;
  font-size: 11px; font-weight: 600; padding: 0 6px; white-space: nowrap;
}
</style>
