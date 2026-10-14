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
              <div style="font-size: 13px; color: #909399">付款单总数</div>
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
              <div style="font-size: 13px; color: #909399">三单一致</div>
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
              <div style="font-size: 13px; color: #909399">差异预警</div>
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
              <div style="font-size: 13px; color: #909399">匹配失败（冻结）</div>
              <div style="font-size: 24px; font-weight: 700; color: #f56c6c">{{ stats.failed }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <b>三单匹配</b>
          <el-input v-model="q.keyword" placeholder="单号/标题" clearable style="width: 160px" @keyup.enter="load" />
          <el-select v-model="q.matchStatus" placeholder="匹配状态" clearable style="width: 140px" @change="load">
            <el-option label="三单一致" value="MATCHED" />
            <el-option label="差异预警" value="WARN" />
            <el-option label="匹配失败" value="FAILED" />
            <!-- D-DEC-077：手工匹配链办结后的终态，结论落 exp_bill.ext_json.matchVerdict，此前无处显形 -->
            <el-option label="人工匹配已办结" value="MANUAL_MATCHED" />
          </el-select>
          <el-button type="primary" size="small" @click="load">查询</el-button>
          <el-button size="small" @click="batchRecheck" :loading="batchLoading">批量重算</el-button>
          <el-tooltip content="三单匹配：采购订单 → 收货单 → 发票 → 付款单（付款 ≤ 发票 ≤ 收货 ≤ 订单）" placement="left">
            <el-button size="small" link>匹配规则说明</el-button>
          </el-tooltip>
        </div>
      </template>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table class="cm-target-match" :data="rows" size="small" v-else max-height="520"
                @row-click="openDetail" @selection-change="handleSelectionChange"
                :row-class-name="matchRowClass" empty-text="暂无数据" @row-dblclick="openDetail">
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
                {{ statusLabel(row.matchStatus) }} · {{ inlineChainNodes(row).filter(n => n.ok).length }}/4 环节就绪
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="billNo" label="付款单号" width="145" fixed  sortable />
        <el-table-column prop="title" label="标题" min-width="110" show-overflow-tooltip  sortable />
        <el-table-column label="匹配链路" width="160" align="center">
          <template #default="{ row }">
            <div class="chain-dots">
              <el-tooltip v-for="d in chainDots(row)" :key="d.key" :content="d.tip" placement="top">
                <span class="chain-dot" :class="d.cls">{{ d.label }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="采购订单" width="130">
          <template #default="{ row }">
            <span v-if="row.poNo" style="color: #409eff">{{ row.poNo }}</span>
            <el-tag v-else type="danger" size="small">未关联</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货单" width="130">
          <template #default="{ row }">
            <span v-if="row.receiptNo">{{ row.receiptNo }}</span>
            <el-tag v-else type="danger" size="small">未关联</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联发票" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.invoiceAmount) }}</template>
        </el-table-column>
        <el-table-column label="本次付款" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.payAmount > (row.invoiceAmount || 0) ? '#f56c6c' : '' }">{{ fmt(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="匹配状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.matchStatus)" size="small">{{ statusLabel(row.matchStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="冻结" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.frozen" type="danger" size="small">冻结</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对账" width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reconStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ row.reconStatus === 'CONFIRMED' ? '已确认' : '未确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题" width="55" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.issues && row.issues.length" :value="row.issues.length" type="warning" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            <el-button link size="small" type="primary" @click.stop="openDetail(row)">明细</el-button>
            <el-button link size="small" type="warning" @click.stop="openLinkDialog(row)">关联单据</el-button>
            <!-- D-DEC-077：人工匹配链已办结的行不再提供「派单」出口，否则终态会被重复派工淹没 -->
            <el-button v-if="row.matchStatus !== 'MATCHED' && row.matchStatus !== 'MANUAL_MATCHED'" link size="small" type="danger" @click.stop="routeIssue(row)">派单</el-button>
            <el-button link size="small" type="primary" @click.stop="manualMatch(row)">手工匹配</el-button>
            <el-button link size="small" @click.stop="checkOne(row)">重算</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="total" :page-size="q.pageSize" :current-page="q.pageNum"
                     @current-change="(n) => { q.pageNum = n; load() }" />
    </el-card>

    <!-- 三单匹配明细弹窗 -->
    <el-dialog v-model="detailVisible" title="三单匹配明细" width="900px" top="5vh">
      <template v-if="detail">
        <!-- 付款单信息 -->
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="付款单号">{{ detail.billNo }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="单据状态">{{ billStatusText(detail.billStatus) }}</el-descriptions-item>
          <el-descriptions-item label="本次付款金额">
            <span style="font-size: 16px; font-weight: 700; color: #409eff">{{ fmt(detail.payAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="匹配状态">
            <el-tag :type="statusType(detail.matchStatus)" size="small">{{ statusLabel(detail.matchStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="付款冻结">
            <el-tag :type="detail.frozen ? 'danger' : 'success'" size="small">{{ detail.frozen ? '已冻结' : '未冻结' }}</el-tag>
          </el-descriptions-item>
          <!-- D-DEC-077：人工匹配链的办结结论（此前终态零回写，判定意见无处留存） -->
          <el-descriptions-item v-if="detail.matchStatus === 'MANUAL_MATCHED'" label="人工办结结论" :span="3">
            <span style="color: #67c23a; font-weight: 600">{{ detail.manualVerdict || '人工匹配已办结' }}</span>
            <span v-if="detail.manualProcInstId" style="margin-left: 12px; color: #909399; font-size: 12px">
              流程实例 {{ detail.manualProcInstId }}
            </span>
            <span v-else style="margin-left: 12px; color: #909399; font-size: 12px">流程实例未记录</span>
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
              收货 ≤ PO
            </span>
            <span class="rule-item" :class="ruleGrnInv ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleGrnInv" /><CircleClose v-else /></el-icon>
              发票 ≤ 收货
            </span>
            <span class="rule-item" :class="ruleInvPay ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleInvPay" /><CircleClose v-else /></el-icon>
              付款 ≤ 发票
            </span>
            <span class="rule-item" :class="ruleAllPresent ? 'rule-ok' : 'rule-bad'">
              <el-icon size="12"><CircleCheck v-if="ruleAllPresent" /><CircleClose v-else /></el-icon>
              四单齐全
            </span>
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
        <el-table :data="comparisonRows" border size="small" style="margin-bottom: 16px" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="label" label="对比维度" width="110"  sortable />
          <el-table-column label="采购订单 (PO)" min-width="120">
            <template #default="{ row }">
              <span v-if="row.po !== null && row.po !== undefined">{{ row.po }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="收货单" min-width="120">
            <template #default="{ row }">
              <span v-if="row.receipt !== null && row.receipt !== undefined">{{ row.receipt }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="发票" min-width="120">
            <template #default="{ row }">
              <span v-if="row.invoice !== null && row.invoice !== undefined">{{ row.invoice }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="付款单" min-width="120">
            <template #default="{ row }">
              <span v-if="row.pay !== null && row.pay !== undefined" style="font-weight: 600">{{ row.pay }}</span>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 关联发票明细 -->
        <div v-if="invoiceList.length" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px; font-size: 14px">关联发票明细</div>
          <el-table :data="invoiceList" border size="small" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="invoiceNo" label="发票号" width="140"  sortable />
            <el-table-column prop="invoiceCode" label="发票代码" width="120"  sortable />
            <el-table-column prop="sellerName" label="销方名称" min-width="120" show-overflow-tooltip  sortable />
            <el-table-column prop="invoiceDate" label="开票日期" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column prop="netAmount" label="不含税金额" width="110" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
            </el-table-column>
            <el-table-column prop="taxAmount" label="税额" width="90" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="价税合计" width="110" align="right" sortable>
              <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="核销金额" width="100" align="right" sortable>
              <template #default="{ row }"><span style="color: #409eff">{{ fmt(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 差异/问题列表 -->
        <div v-if="detail.issues && detail.issues.length" style="margin-bottom: 16px">
          <div style="font-weight: 600; margin-bottom: 8px; font-size: 14px; color: #e6a23c">差异与问题</div>
          <el-table :data="detail.issues" border size="small" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column label="级别" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">{{ row.level === 'ERROR' ? '硬异常' : '预警' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="差异类型" width="180" sortable>
              <template #default="{ row }">{{ issueCodeText(row.code) }}</template>
            </el-table-column>
            <el-table-column prop="msg" label="说明" min-width="250" show-overflow-tooltip  sortable />
            <el-table-column label="责任岗位" width="100">
              <template #default="{ row }">{{ issueRouteText(row.code) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 合同信息（展示维度） -->
        <el-descriptions v-if="detail.contractNo" :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="关联合同">{{ detail.contractNo }}</el-descriptions-item>
          <el-descriptions-item label="合同名称">{{ detail.contractName }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">{{ fmt(detail.contractAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应付对账（函）">
            <el-tag :type="detail.reconStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ detail.reconStatus === 'CONFIRMED' ? '已确认' : (detail.supplierName ? '未确认' : '无法判定（未取到收款方）') }}
            </el-tag>
            <span v-if="detail.supplierName" style="margin-left: 8px; color: #909399; font-size: 12px">{{ detail.supplierName }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="warning" @click="openLinkDialog(detail)">关联单据</el-button>
        <el-button v-if="detail && detail.matchStatus !== 'MATCHED' && detail.matchStatus !== 'MANUAL_MATCHED'" type="danger" @click="routeIssue(detail)">异常派单</el-button>
        <!-- D-DEC-077：人工匹配链办结后，派单/复审两个「未办结」出口必须收起，否则同一单据可被反复发起人工链 -->
        <el-button v-if="detail && detail.matchStatus !== 'MATCHED' && detail.matchStatus !== 'MANUAL_MATCHED'" type="primary" @click="reviewBill(detail)">发起复审</el-button>
        <el-button v-if="detail && detail.matchStatus !== 'MANUAL_MATCHED'" type="primary" plain @click="manualMatch(detail)">手工匹配</el-button>
        <el-button v-if="detail && detail.matchStatus === 'MANUAL_MATCHED'" type="primary" plain @click="viewManualVerdict">查看人工办结记录</el-button>
      </template>
    </el-dialog>

    <!-- 手工关联PO/收货单弹窗 -->
    <el-dialog v-model="linkVisible" title="手工关联采购订单/收货单" width="600px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        选择采购订单和收货单进行三单匹配关联。关联后系统将自动重新执行匹配校验。
      </el-alert>
      <el-form :model="linkForm" :rules="linkRules" label-width="100px" size="default">
        <el-form-item label="付款单号">
          <el-input :model-value="linkRow?.billNo" disabled />
        </el-form-item>
        <el-form-item label="采购订单">
          <el-select v-model="linkForm.poId" filterable clearable placeholder="选择采购订单（PO）" style="width: 100%"
                     @change="onPoChange" :loading="poLoading">
            <el-option v-for="po in poOptions" :key="po.id"
                       :label="`${po.poNo} | ${po.supplierName || ''} | ${fmt(po.amount)}`"
                       :value="po.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="收货单">
          <el-select v-model="linkForm.poReceiptId" filterable clearable placeholder="选择收货单" style="width: 100%"
                     :loading="receiptLoading" :disabled="!linkForm.poId">
            <el-option v-for="rc in receiptOptions" :key="rc.id"
                       :label="`${rc.receiptNo} | ${rc.supplierName || ''} | ${fmt(rc.amount)}`"
                       :value="rc.id" />
          </el-select>
          <div v-if="linkForm.poId && !receiptOptions.length" style="color: #909399; font-size: 12px; margin-top: 4px">
            当前采购订单暂无关联收货单
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkVisible = false">取消</el-button>
        <el-button type="primary" @click="doLink" :loading="linkLoading">确认关联并校验</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="INVOICE_MATCH" />

  <CoachMark ref="matchCm" :guide-key="_guideKey" :selector="'.cm-target-match'" title="三单匹配" text="三单匹配页面可视化展示采购订单、入库单和发票的匹配状态。" />
</template>

<script setup>

import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, CircleCheck, Warning, CircleClose, ArrowRight, ArrowDown, Check } from '@element-plus/icons-vue'
import { opApi, purchaseApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { STATUS_LABEL } from '@/constants/billStatus.js'
import { formatDate, formatDateTime } from '@/utils/format'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const matchCm = ref(null)

// R4-02: BillTraceDrawer
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
  // D-DEC-077：MANUAL_MATCHED＝人工匹配链已办结并解除冻结（自动判定仍存在的差异在明细里照常列出，
  // 终态结论与原判定两件事都要看得见，故文案写"人工匹配已办结"而不是"三单一致"）
  return { MATCHED: '三单一致', WARN: '差异预警', FAILED: '匹配失败', MANUAL_MATCHED: '人工匹配已办结' }[s] || s
}

// ========== 可视化匹配链路 ==========
function matchRowClass({ row }) {
  if (row.matchStatus === 'MATCHED') return 'row-green'
  if (row.matchStatus === 'FAILED') return 'row-red'
  if (row.matchStatus === 'WARN') return 'row-yellow'
  return ''
}

function chainDots(row) {
  const has = (v) => v != null && v !== '' && v !== 0
  return [
    { key: 'po',  label: 'PO',  tip: row.poNo ? `采购订单 ${row.poNo}` : '未关联采购订单',
      cls: has(row.poNo) ? 'dot-green' : 'dot-red' },
    { key: 'grn', label: 'GRN', tip: row.receiptNo ? `收货单 ${row.receiptNo}` : '未关联收货单',
      cls: has(row.receiptNo) ? 'dot-green' : 'dot-red' },
    { key: 'inv', label: 'INV', tip: row.invoiceAmount ? `发票 ${fmt(row.invoiceAmount)}` : '未关联发票',
      cls: has(row.invoiceAmount) ? 'dot-green' : 'dot-red' },
    { key: 'pay', label: 'PAY', tip: `付款 ${fmt(row.payAmount)}`,
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
    { key: 'po', label: '采购订单', value: d.poNo || '未关联', ok: has(d.poNo),
      amount: has(d.poNo) && poAmt ? '¥ ' + fmt(poAmt) : '', amountColor: '#409eff',
      cls: nodeCls(has(d.poNo)), arrow: true, arrowCls: arrowCls(has(d.poNo)),
      varText: fmtVar(varPoGrn), varCls: varCls(varPoGrn) },
    { key: 'grn', label: '收货单', value: d.receiptNo || '未关联', ok: has(d.receiptNo),
      amount: has(d.receiptNo) && grnAmt ? '¥ ' + fmt(grnAmt) : '', amountColor: '#909399',
      cls: nodeCls(has(d.receiptNo)), arrow: true, arrowCls: arrowCls(has(d.receiptNo) && has(d.poNo)),
      varText: fmtVar(varGrnInv), varCls: varCls(varGrnInv) },
    { key: 'inv', label: '发票', value: has(d.invoiceAmount) ? fmt(d.invoiceAmount) + ' 元' : '未关联', ok: has(d.invoiceAmount),
      amount: has(d.invoiceAmount) ? '¥ ' + fmt(invAmt) : '', amountColor: '#e6a23c',
      cls: nodeCls(has(d.invoiceAmount)), arrow: true, arrowCls: arrowCls(has(d.invoiceAmount) && has(d.receiptNo)),
      varText: fmtVar(varInvPay), varCls: varCls(varInvPay) },
    { key: 'pay', label: '付款单', value: fmt(d.payAmount) + ' 元', ok: amtOk(d.payAmount, d.invoiceAmount),
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
      key: 'po', label: '采购订单', no: row.poNo || '—', amount: poAmt ? fmt(poAmt) : '',
      ok: has(row.poNo), cls: has(row.poNo) ? 'ic-green' : 'ic-red',
      varText: has(row.poNo) && has(row.receiptNo) ? fmtVar(grnAmt - poAmt) : '',
      varCls: grnAmt - poAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: grnAmt - poAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'grn', label: '收货单', no: row.receiptNo || '—', amount: grnAmt ? fmt(grnAmt) : '',
      ok: has(row.receiptNo), cls: has(row.receiptNo) ? 'ic-green' : 'ic-red',
      varText: has(row.receiptNo) && has(row.invoiceAmount) ? fmtVar(invAmt - grnAmt) : '',
      varCls: invAmt - grnAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: invAmt - grnAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'inv', label: '发票', no: invAmt ? fmt(invAmt) : '—', amount: '',
      ok: has(row.invoiceAmount), cls: has(row.invoiceAmount) ? 'ic-green' : 'ic-red',
      varText: has(row.invoiceAmount) ? fmtVar(payAmt - invAmt) : '',
      varCls: payAmt - invAmt <= 0 ? 'var-ok' : 'var-warn',
      varIcon: payAmt - invAmt > 0 ? 'ArrowDown' : 'Check',
    },
    {
      key: 'pay', label: '付款', no: fmt(payAmt), amount: '',
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
  return {
    NO_PO: '未关联采购订单',
    NO_RECEIPT: '未关联收货单',
    PAY_EXCEED_INVOICE: '超付（无发票覆盖）',
    INVOICE_OVER_RECEIPT: '发票超收货',
    INVOICE_IN_TOLERANCE: '发票超收货（容差内）',
    RECEIPT_OVER_PO: '收货超订单',
    RECEIPT_IN_TOLERANCE: '收货超订单（容差内）',
    RECON_UNCONFIRMED: '应付对账（函）未确认',
  }[code] || code
}
function issueRouteText(code) {
  return {
    NO_PO: '采购经理',
    RECEIPT_OVER_PO: '采购经理',
    RECEIPT_IN_TOLERANCE: '采购经理',
    NO_RECEIPT: '仓库管理员',
    PAY_EXCEED_INVOICE: '应付会计',
    INVOICE_OVER_RECEIPT: '应付会计',
    INVOICE_IN_TOLERANCE: '应付会计',
    RECON_UNCONFIRMED: '对账专员',
  }[code] || '应付会计'
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
    ElMessage.success('已重新计算：' + statusLabel(d.matchStatus))
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
  ElMessage.success(`批量重算完成：${ok}/${rows.value.length}`)
}

// ---- 明细弹窗 ----
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
    { label: '单号', po: d.poNo, receipt: d.receiptNo, invoice: d.invoiceList?.length ? d.invoiceList.map(i => i.invoiceNo).join(', ') : '—', pay: d.billNo },
    { label: '金额', po: d.poAmount != null ? fmt(d.poAmount) : null, receipt: d.receiptAmount != null ? fmt(d.receiptAmount) : null, invoice: d.invoiceAmount != null ? fmt(d.invoiceAmount) : null, pay: d.payAmount != null ? fmt(d.payAmount) : null },
    { label: '已收货金额', po: d.poReceived != null ? fmt(d.poReceived) : null, receipt: null, invoice: null, pay: null },
    { label: '数量', po: d.poQty != null ? d.poQty : null, receipt: d.receiptQty != null ? d.receiptQty : null, invoice: null, pay: null },
    { label: '已开票金额', po: null, receipt: d.receiptInvoice != null ? fmt(d.receiptInvoice) : null, invoice: null, pay: null },
  ]
})

// ---- 手工关联PO/收货单 ----
const linkVisible = ref(false)
const linkRow = ref(null)
const linkForm = reactive({ poId: null, poReceiptId: null })

const linkRules = {
  poId: [{ required: true, message: '请选择采购订单' }],
  poReceiptId: [{ required: true, message: '请选择收货单' }],
}
const poOptions = ref([])
const receiptOptions = ref([])
const poLoading = ref(false)
const receiptLoading = ref(false)
const linkLoading = ref(false)

async function openLinkDialog(row) {
  if (!row) return
  linkRow.value = row
  linkForm.poId = row.poId || null
  linkForm.poReceiptId = row.receiptId || null
  linkVisible.value = true
  detailVisible.value = false
  // 加载PO列表
  poLoading.value = true
  try {
    const r = await purchaseApi.orderPage({ pageNum: 1, pageSize: 100 })
    // D-V4R-47 同族：三单匹配的 PO 候选同样出现已作废订单
    poOptions.value = (r.list || []).filter((p) => String((p && p.status) || '').toUpperCase() !== 'VOID')
  } catch (e) { ElMessage.error('加载采购订单失败') } finally { poLoading.value = false }
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
    ElMessage.warning('请至少选择采购订单或收货单')
    return
  }
  linkLoading.value = true
  try {
    await opApi.matchLink(linkRow.value.billId, { poId: linkForm.poId, poReceiptId: linkForm.poReceiptId })
    ElMessage.success('关联成功，正在重新校验...')
    // 重新校验
    const d = await opApi.matchCheck(linkRow.value.billId)
    Object.assign(linkRow.value, d)
    linkVisible.value = false
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { linkLoading.value = false }
}

// ---- 异常派单 ----
async function routeIssue(row) {
  try {
    await ElMessageBox.confirm(
      `确认对付款单 ${row.billNo} 的三单匹配异常进行派单？\n系统将按差异类型路由到对应责任岗位。`,
      '异常派单确认',
      { type: 'warning' }
    )
    const r = await opApi.matchRoute(row.billId)
    const routing = r.routing || {}
    const roles = Object.entries(routing).map(([k, v]) => `${roleName(k)}：${v.length}项`).join('；')
    ElMessage.success(`派单成功：${roles}（流程实例：${r.procInstId}）`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

// ---- 发起复审 ----
async function reviewBill(row) {
  try {
    await ElMessageBox.confirm(
      `确认对付款单 ${row.billNo} 发起发票校验异常复审审批？`,
      '发起复审确认',
      { type: 'warning' }
    )
    const procInstId = await opApi.matchReview(row.billId)
    ElMessage.success(`复审流程已发起（流程实例：${procInstId}）`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

// ---- IFS 手工三单匹配 ----
async function manualMatch(row) {
  const matched = row.matchStatus === 'MATCHED'
  try {
    const { value } = await ElMessageBox.prompt(
      matched
        ? `系统已判定付款单 ${row.billNo} 三单一致，发起手工匹配需填写人工判定理由。`
        : `确认对付款单 ${row.billNo} 发起手工三单匹配审批？`,
      '手工三单匹配',
      {
        inputType: 'textarea',
        inputPlaceholder: '人工判定理由（系统判定一致时必填）',
        inputValidator: (v) => (matched && !(v || '').trim()) ? '系统判定为一致，必须填写人工判定理由' : true,
      }
    )
    const procInstId = await opApi.matchManualReview(row.billId, (value || '').trim())
    ElMessage.success(`手工匹配审批已发起（流程实例：${procInstId}）`)
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

function roleName(code) {
  return { PURCHASE_MANAGER: '采购经理', WH_KEEPER: '仓库管理员', FIN_AP: '应付会计', RECON_CLERK: '对账专员' }[code] || code
}

onMounted(load)

function exportCsv() {
  if (!invoiceList.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'invoiceNo', label: '发票号' }, { prop: 'invoiceCode', label: '发票代码' }, { prop: 'sellerName', label: '销方名称' }, { prop: 'invoiceDate', label: '开票日期' }, { prop: 'netAmount', label: '不含税金额' }, { prop: 'taxAmount', label: '税额' }, { prop: 'totalAmount', label: '价税合计' }, { prop: 'amount', label: '核销金额' }]
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
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
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
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_matchCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => matchCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
/* 行级颜色编码 */
:deep(.row-green) { background-color: #f0f9eb !important; }
:deep(.row-yellow) { background-color: #fdf6ec !important; }
:deep(.row-red) { background-color: #fef0f0 !important; }

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
.match-chain-vis { background: #f5f7fa; border-radius: 8px; padding: 16px; }
.chain-flow { display: flex; align-items: center; justify-content: center; gap: 0; flex-wrap: wrap; }
.chain-node-wrapper { display: flex; align-items: center; }
.chain-node {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  min-width: 100px; padding: 12px 16px; border-radius: 8px; border: 2px solid;
}
.chain-node-label { font-size: 13px; font-weight: 600; }
.chain-node-value { font-size: 12px; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-green { border-color: #67c23a; background: #f0f9eb; color: #67c23a; }
.node-yellow { border-color: #e6a23c; background: #fdf6ec; color: #e6a23c; }
.node-red { border-color: #f56c6c; background: #fef0f0; color: #f56c6c; }
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
.var-ok { color: #67c23a; background: #f0f9eb; }
.var-warn { color: #f56c6c; background: #fef0f0; }
.var-none { color: #c0c4cc; }

/* 规则合规指示 */
.chain-rules { display: flex; gap: 16px; justify-content: center; margin-top: 12px; flex-wrap: wrap; }
.rule-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 3px 10px; border-radius: 12px; font-weight: 500;
}
.rule-ok { color: #67c23a; background: #f0f9eb; }
.rule-bad { color: #f56c6c; background: #fef0f0; }

/* 行内联链路可视化 */
.inline-chain-vis { padding: 8px 16px 12px; background: #f9fafc; }
.inline-chain-flow { display: flex; align-items: center; gap: 0; flex-wrap: wrap; }
.inline-chain-item { display: flex; align-items: center; }
.inline-chain-node {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px 14px; border-radius: 6px; border: 1.5px solid; min-width: 80px;
}
.inline-node-label { font-size: 11px; font-weight: 700; letter-spacing: 0.5px; }
.inline-node-no { font-size: 12px; color: #606266; max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.inline-node-amt { font-size: 11px; color: #409eff; font-weight: 600; }
.ic-green { border-color: #67c23a; background: #f0f9eb; }
.ic-green .inline-node-label { color: #67c23a; }
.ic-yellow { border-color: #e6a23c; background: #fdf6ec; }
.ic-yellow .inline-node-label { color: #e6a23c; }
.ic-red { border-color: #f56c6c; background: #fef0f0; }
.ic-red .inline-node-label { color: #f56c6c; }
.inline-chain-variance {
  display: flex; align-items: center; gap: 2px;
  font-size: 11px; font-weight: 600; padding: 0 6px; white-space: nowrap;
}
</style>
