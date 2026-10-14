<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
        <b>总账（GL）</b>
        <el-tabs v-model="tab" style="flex: 1" :class="{ tabless: isSingle }">
          <el-tab-pane label="总账分录" name="entry" />
          <el-tab-pane label="科目余额表" name="balance" />
          <el-tab-pane label="试算平衡" name="trial" />
          <el-tab-pane label="凭证台账" name="voucher" />
        </el-tabs>
        <el-select v-model="companyId" placeholder="公司" clearable style="width: 160px">
          <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
        </el-select>
        <el-input-number v-model="year" :min="2000" :max="2100" controls-position="right" style="width: 130px" />
        <el-input-number v-model="month" :min="1" :max="12" controls-position="right" style="width: 120px" />
        <!-- D-DEC-168：本页唯一的「查询」入口（原凭证台账页签内还有一只同名同行为的重复按钮，已去除） -->
        <el-button type="primary" size="small" @click="runQuery">查询</el-button>
        <el-tag v-if="trial" :type="trial.balanced ? 'success' : 'danger'" effect="dark">
          {{ trial.balanced ? '借贷平衡' : '不平衡' }}
        </el-tag>
      </div>
    </template>

    <!-- 总账分录 -->
    <template v-if="tab === 'entry'">
      <el-row :gutter="12" style="margin-bottom: 10px">
        <el-col :span="6">
          <el-select v-model="subjectId" placeholder="按科目筛选" clearable filterable style="width: 100%">
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectCode + ' ' + s.subjectName" :value="s.id" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button size="small" @click="runQuery">筛选</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </el-col>
      </el-row>
      <!-- R5-D-149：排序改服务端 custom（sortable="custom" + @sort-change 回后端白名单重排全量结果）——
           原裸 sortable 只排当前页 20 行，翻页即乱序，且与后端默认 id 升序矛盾 -->
      <el-table :data="rows" size="small" border stripe v-loading="loading" :max-height="520" @selection-change="handleSelectionChange" @row-dblclick="openVoucherDetail" @sort-change="onEntrySortChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="voucherNo" label="凭证号" width="150" fixed  sortable="custom" />
        <el-table-column prop="voucherId" label="凭证ID" width="80"  sortable="custom" />
        <el-table-column prop="subjectCode" label="科目编码" width="110"  sortable="custom" />
        <el-table-column prop="subjectName" label="科目名称" min-width="140" show-overflow-tooltip  sortable="custom" />
        <el-table-column prop="direction" label="方向" width="80" align="center" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'DEBIT' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'DEBIT' ? '借' : '贷' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right" sortable="custom">
          <template #default="{ row }"><span :class="row.direction === 'DEBIT' ? 'deb' : 'crd'">{{ fmt(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip  sortable="custom" />
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务单ID" width="90"  sortable="custom" />
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
      <el-pagination
        style="margin-top: 10px; justify-content: flex-end"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="(p) => { pageNum = p; load() }"
        @size-change="(s) => { pageSize = s; load() }" />
    </template>

    <!-- 科目余额表 -->
    <template v-else-if="tab === 'balance'">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        title="期初 = 该科目在小于本期期间的全部发生净额（按方向归借或贷一边，与期末同口径）；期末 = 期初 + 本期借 − 本期贷；表内列出「本期有发生」与「只有期初余额」的全部科目，合计行分列核对（余额实时聚合）" />
      <!-- R5-D-149：本页签为不分页的完整聚合列表，列头 sortable 对全量数据前端排序即正确（无需服务端排序）；
           分页的总账分录/凭证台账两个页签已改服务端 custom 排序 -->
      <el-table :data="balance" size="small" border stripe v-loading="loading" :max-height="560"
                show-summary :summary-method="balanceSummary" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="subjectCode" label="科目编码" width="110" fixed  sortable />
        <el-table-column prop="subjectName" label="科目名称" min-width="160" show-overflow-tooltip  sortable />
        <el-table-column prop="openDebit" label="期初借方" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.openDebit) }}</template>
        </el-table-column>
        <el-table-column prop="openCredit" label="期初贷方" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.openCredit) }}</template>
        </el-table-column>
        <el-table-column prop="periodDebit" label="本期借方" width="130" align="right" sortable>
          <template #default="{ row }"><b>{{ fmt(row.periodDebit) }}</b></template>
        </el-table-column>
        <el-table-column prop="periodCredit" label="本期贷方" width="130" align="right" sortable>
          <template #default="{ row }"><b>{{ fmt(row.periodCredit) }}</b></template>
        </el-table-column>
        <el-table-column label="期末方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'DEBIT' ? 'danger' : 'success'" size="small">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="endDebit" label="期末借方" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.endDebit) }}</template>
        </el-table-column>
        <el-table-column prop="endCredit" label="期末贷方" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.endCredit) }}</template>
        </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </template>

    <!-- 凭证台账（：过账状态 + 批量过账 + 制审分离） -->
    <template v-else-if="tab === 'voucher'">
      <el-row :gutter="12" style="margin-bottom: 10px; align-items: center">
        <el-col :span="8">
          <el-select v-model="voucherStatus" placeholder="过账状态" clearable style="width: 100%" @change="runQuery">
            <el-option label="未过账" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已作废" value="VOID" />
          </el-select>
        </el-col>
        <el-col :span="16">
          <!-- D-DEC-168：这里原本还有一只「查询」按钮（与表头那只同名、同 @click），同屏两只 → 已去除。
               过账状态、公司、年度、月度统一由表头「查询」提交；本页签改状态筛选时自动重取，少一次点击。 -->
          <el-tooltip content="请先勾选未过账且非本人制单的凭证" placement="top" :disabled="postableRows.length > 0">
            <el-button type="success" size="small" :loading="batchPosting" :disabled="!postableRows.length" @click="batchPost">
              批量过账({{ postableRows.length }})
            </el-button>
          </el-tooltip>
          <span style="margin-left: 8px; color: #909399; font-size: 12px">制审分离：制单人不可自审（悬停「制单人」列查看提示），已过账凭证不可重复过账</span>
          <el-button size="small" type="danger" plain :disabled="!singleVoucher" @click="voidSel">作废</el-button>
          <el-button size="small" type="warning" plain :disabled="!singleVoucher" @click="reverseSel">红字冲销</el-button>
        </el-col>
      </el-row>
      <!-- D-DEC-288（X-49 乙）：仅当本页有"已记账而资金指令未下发"的凭证时出现；样式沿用上面那行次要文字（不新开弹窗） -->
      <div v-if="unsettledLegCount" style="margin: -2px 0 8px; color: #909399; font-size: 12px">
        本页 {{ unsettledLegCount }} 张凭证已过账但资金指令未下发（款项尚未付出），账面已记账≠已结清；存量凭证的账务处置须财务另签。
      </div>
      <!-- R5-D-149：凭证台账排序同为服务端 custom（凭证号/摘要两列白名单） -->
      <el-table ref="voucherTableRef" :data="voucherRows" size="small" border stripe v-loading="loading" :max-height="520"
                @selection-change="handleVoucherSelectionChange" @sort-change="onVoucherSortChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="voucherNo" label="凭证号" width="170" fixed sortable="custom" />
        <el-table-column label="期间" width="100">
          <template #default="{ row }">{{ row.periodYear ? row.periodYear + '-' + String(row.periodMonth || '').padStart(2, '0') : '—' }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip sortable="custom" />
        <el-table-column label="来源类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ voucherSourceLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="来源单据" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 费用族的 bill_id 才是费用单主键；其余来源 bill_id 是别域主键，只标来源域＋主键，不猜单号（D-DEC-244） -->
            <span v-if="row.sourceBillNo && isExpenseFamilyVoucherSource(row.sourceType)">{{ row.sourceBillNo }}</span>
            <span v-else-if="row.billId" style="color: #909399">{{ voucherSourceLabel(row.sourceType) }} #{{ row.billId }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="借方" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.debitTotal) }}</template>
        </el-table-column>
        <el-table-column label="贷方" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.creditTotal) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <!-- 凭证 status：DRAFT=未过账 / POSTED=已过账 / VOID=已作废 -->
            <el-tag :type="row.status === 'POSTED' ? 'success' : row.status === 'VOID' ? 'info' : 'warning'" size="small">{{ voucherStatusLabel(row.status) }}</el-tag>
            <!-- D-DEC-288（X-49 乙）：付款腿凭证已记账而资金指令未下发——存量保持不动账，只在行上明示注记 -->
            <div v-if="row.payLegUnsettled === true" style="color: #e6a23c; font-size: 12px; line-height: 1.4">资金指令未下发</div>
          </template>
        </el-table-column>
        <el-table-column label="制单人" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 制审分离：当前登录人 = 凭证 create_by 的行不可勾选过账，tooltip 提示 -->
            <el-tooltip v-if="isSelfCreated(row)" content="制单人不可自审" placement="top">
              <span style="color: #e6a23c">{{ creatorLabel(row) }}（本人）</span>
            </el-tooltip>
            <span v-else>{{ creatorLabel(row) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
      <el-pagination
        style="margin-top: 10px; justify-content: flex-end"
        layout="total, prev, pager, next, sizes"
        :total="voucherTotal"
        :page-size="voucherPageSize"
        :current-page="voucherPageNum"
        @current-change="(p) => { voucherPageNum = p; loadVouchers() }"
        @size-change="(s) => { voucherPageSize = s; loadVouchers() }" />
    </template>

    <!-- 试算平衡 -->
    <template v-else>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="借方发生额合计" :value="Number(trial?.debitTotal || 0)" :precision="2" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="贷方发生额合计" :value="Number(trial?.creditTotal || 0)" :precision="2" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="借贷差异" :value="Number(trial?.diff || 0)" :precision="2">
              <template #suffix>
                <el-tag v-if="trial?.balanced" type="success" size="small">平衡</el-tag>
                <el-tag v-else type="danger" size="small">差异</el-tag>
              </template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <!-- R5-D-219：el-statistic 的 value 是数值型组件（内部 Number 化+分组/精度格式化），
                 「截至期间」是 YYYY-MM 期间标识而非统计量，穿字符串会被当数值渲染成乱码/NaN；
                 期间语义改普通文本渲染（与同排三张统计卡视觉对齐）。 -->
            <div class="period-stat">
              <div class="period-stat__title">截至期间</div>
              <div class="period-stat__value">{{ trialPeriod }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-result
        v-if="trial?.balanced"
        icon="success"
        title="试算平衡通过"
        :sub-title="'期间 ' + (trial.periodYear || year) + '-' + (trial.periodMonth || month) + ' 借方 = 贷方，总分类账借贷一致。'">
      </el-result>
      <el-result v-else icon="error" title="发现借贷差异"
        :sub-title="'差异金额：' + fmt(Math.abs(Number(trial?.diff || 0))) + '，请核查该期间凭证落账情况。'">
        <template #extra>
          <el-button type="primary" @click="load">重新试算</el-button>
        </template>
      </el-result>
    </template>
  </el-card>

  <!-- 凭证详情抽屉 -->
  <el-drawer v-model="voucherDrawer" title="凭证详情" size="50%" direction="rtl">
    <el-descriptions :column="2" border size="small" v-if="voucherDetail">
      <el-descriptions-item label="凭证号">{{ voucherDetail.voucherNo || '—' }}</el-descriptions-item>
      <el-descriptions-item label="凭证ID">{{ voucherDetail.voucherId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="科目编码">{{ voucherDetail.subjectCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="科目名称">{{ voucherDetail.subjectName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="方向">
        <el-tag :type="voucherDetail.direction === 'DEBIT' ? 'danger' : 'success'" size="small">{{ voucherDetail.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="金额"><b>{{ fmt(voucherDetail.amount) }}</b></el-descriptions-item>
      <el-descriptions-item label="摘要" :span="2">{{ voucherDetail.summary || '—' }}</el-descriptions-item>
      <el-descriptions-item label="业务类型">{{ bizTypeLabel(voucherDetail.bizType) }}</el-descriptions-item>
      <el-descriptions-item label="业务单ID">{{ voucherDetail.bizId || '—' }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="voucherDetail && voucherDetail.bizId" style="margin-top: 16px">
      <el-button type="primary" size="small" @click="traceBill(voucherDetail)">追溯原始单据</el-button>
    </div>
  </el-drawer>
  <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>

import { onMounted, ref, watch, computed, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { glApi, mdApi, orgApi, sharingApi, store } from '../../api'
import { useKeyboard } from '@/composables/useKeyboard'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { bizTypeOfVoucherSource, voucherSourceLabel, isExpenseFamilyVoucherSource } from '../../utils/bizEntry'
import { bizTypeLabel } from '@/constants/enums.js'

// 凭证详情抽屉
const voucherDrawer = ref(false)
const voucherDetail = ref(null)
function openVoucherDetail(row) {
  voucherDetail.value = row
  voucherDrawer.value = true
}

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function traceBill(row) {
  traceBillId.value = row.bizId || row.id
  traceBizType.value = bizTypeOfVoucherSource(row.bizType)
  traceVisible.value = true
}
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

const selRows = ref([])
// GAP-01: 凭证作废/红字冲销（在凭证台账勾选单张凭证后可用）
const singleVoucher = computed(() => voucherSelRows.value.length === 1 ? voucherSelRows.value[0] : null)
async function voidSel() {
  const v = singleVoucher.value
  if (!v) return
  if (v.status !== 'DRAFT') { ElMessage.warning('仅未过账（草稿）凭证可作废'); return }
  try {
    await ElMessageBox.confirm(`确认作废凭证 ${v.voucherNo || v.id}？作废为终态，不可恢复。`, '作废', { type: 'warning' })
  } catch { return }
  try {
    await glApi.voidVoucher(v.id)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message) }
}
async function reverseSel() {
  const v = singleVoucher.value
  if (!v) return
  if (v.status !== 'POSTED') { ElMessage.warning('仅已过账凭证可做红字冲销'); return }
  let reason = ''
  try {
    const r = await ElMessageBox.prompt(`对凭证 ${v.voucherNo || v.id} 做红字冲销？将生成方向相反、金额相等的冲销凭证并落账。`, '红字冲销', {
      inputPlaceholder: '冲销原因（必填留痕）',
      inputValidator: (x) => (x && x.trim()) ? true : '请填写冲销原因',
    })
    reason = (r.value || '').trim()
  } catch { return }
  try {
    const newId = await glApi.reverseVoucher(v.id, reason)
    ElMessage.success(`已生成红字冲销凭证 #${newId}`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'entry')
const companyId = ref(null)
const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const subjectId = ref(null)

const companies = ref([])
const subjects = ref([])

const rows = ref([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const balance = ref([])
const trial = ref(null)
// R5-D-219：截至期间文本（YYYY-MM）——试算结果未回时以当前查询条件兜底，替换原被当数字渲染的 el-statistic
const trialPeriod = computed(() => String(trial.value?.periodYear || year.value) + '-' + String(trial.value?.periodMonth || month.value).padStart(2, '0'))

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

// D-DEC-196：科目余额表合计行——期初/本期/期末<b>各自成对</b>合计（此前无合计行，
// 且接口只列"本期有发生"的科目，只有期初余额的科目连金额一起被吞 ⇒ 手工相加必然不平）。
// 三对里任何一对不等就是口径问题，直接在"科目名称"列点出来，不留给人肉核对。
const BALANCE_SUM_PROPS = ['openDebit', 'openCredit', 'periodDebit', 'periodCredit', 'endDebit', 'endCredit']
function balanceSummary({ columns, data }) {
  const sum = {}
  BALANCE_SUM_PROPS.forEach((p) => {
    sum[p] = (data || []).reduce((s, r) => s + Number(r[p] || 0), 0)
  })
  const pairs = [['openDebit', 'openCredit', '期初'], ['periodDebit', 'periodCredit', '本期'], ['endDebit', 'endCredit', '期末']]
  const bad = pairs.filter(([d, c]) => Math.abs(sum[d] - sum[c]) > 0.005).map(([, , n]) => n)
  return columns.map((col, idx) => {
    if (idx === 0) return '合计'
    if (col.property === 'subjectName') {
      return bad.length ? `${bad.join('/')}借贷合计不平，口径需排查` : '期初·本期·期末 借贷三对均平'
    }
    return col.property && col.property in sum ? fmt(sum[col.property]) : ''
  })
}

// ---------------- 凭证台账：过账状态 + 批量过账 + 制审分离 
// 凭证 status 映射（DRAFT=未过账 / POSTED=已过账，缺失取值原样回显）
const VOUCHER_STATUS_LABEL = { DRAFT: '未过账', POSTED: '已过账', VOID: '已作废' }
function voucherStatusLabel(s) { return VOUCHER_STATUS_LABEL[s] || s || '—' }

const voucherRows = ref([])
const voucherTotal = ref(0)
const voucherPageNum = ref(1)
const voucherPageSize = ref(20)
const voucherStatus = ref(null)
const voucherSelRows = ref([])
const voucherTableRef = ref(null)
const batchPosting = ref(false)

// 当前登录人的候选标识（create_by 为用户名字符串；宽容比对多种身份字段）
function userIdentities() {
  const u = store.user || {}
  return [u.username, u.userName, u.realName, u.userId, u.id]
    .filter((v) => v !== undefined && v !== null && String(v).trim() !== '')
    .map((v) => String(v).trim())
}
// 制审分离：当前登录人 = 凭证 create_by → 不可自审
function isSelfCreated(row) {
  const by = String(row?.createBy || '').trim()
  if (!by) return false
  return userIdentities().includes(by)
}
// op_voucher.create_by 存的是 sys_user.id，后端按 id 回填 createByName；缺回填时兜底显示 #ID，避免裸数字像坏数据
function creatorLabel(row) {
  if (row?.createByName) return row.createByName
  return row?.createBy ? `用户#${row.createBy}` : '—'
}
// 可过账：未过账 且 非本人制单
function canPostRow(row) { return row?.status !== 'POSTED' && !isSelfCreated(row) }
// 勾选区中可过账的行（按钮计数与提交均以此为准）
const postableRows = computed(() => (voucherSelRows.value || []).filter(canPostRow))
// D-DEC-288（X-49 乙）：后端读侧打标的"已过账而资金指令未下发"行数（只注记、不动账，判据＝行上 payLegUnsettled === true）
const unsettledLegCount = computed(() => (voucherRows.value || []).filter((r) => r.payLegUnsettled === true).length)

async function loadVouchers() {
  loading.value = true
  try {
    const p = { pageNum: voucherPageNum.value, pageSize: voucherPageSize.value }
    if (voucherStatus.value) p.status = voucherStatus.value
    // D-DEC-168 根因一（前端腿）：表头的公司/年度/月度控件此前只喂给「总账分录／科目余额表／试算平衡」三个页签，
    // 凭证页签的取数函数只发 pageNum/pageSize/status —— 屏幕上摆着的三个条件从未进入请求，
    // 所以点「查询」列表纹丝不动。现按与「总账分录」相同的口径带上（未选＝不限，不臆造默认值）。
    if (companyId.value) p.companyId = companyId.value
    if (year.value) p.periodYear = year.value
    if (month.value) p.periodMonth = month.value
    // R5-D-149：排序状态随请求下发（凭证号/摘要列白名单；未排序不带参数＝后端默认 id 倒序）
    if (voucherOrderBy.value) { p.orderBy = voucherOrderBy.value; p.order = voucherOrder.value || 'asc' }
    const d = await sharingApi.voucherPage(p)
    voucherRows.value = d.list || []
    voucherTotal.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

// D-DEC-168：「查询/筛选」＝条件变了，先回第 1 页再取数。
// 此前直接 load()：停在第 3 页时改条件仍然只翻第 3 页，窄条件下往往没有第 3 页，
// 界面看起来就是「点了没反应」的第二半原因。翻页路径（pagination 回调）不受影响。
function runQuery() {
  if (tab.value === 'voucher') voucherPageNum.value = 1
  else pageNum.value = 1
  load()
}

function handleVoucherSelectionChange(val) { voucherSelRows.value = val }

// ---------------- R5-D-149：服务端排序（分页页签专用） ----------------
// element-plus 的裸 sortable 只对「当前页 20 行」做前端排序——翻页即乱序，用户看到的「排好了」
// 只是页内假象；且与后端默认 id 升序相互矛盾。改为 sortable="custom" + 本回调把 { prop, order }
// 回传后端，由白名单列在 SQL 层重排全量结果（orderBy/order 参数）；取消排序回默认序。
const entryOrderBy = ref('')
const entryOrder = ref('')
function onEntrySortChange({ prop, order }) {
  entryOrderBy.value = order ? String(prop || '') : ''
  entryOrder.value = order === 'descending' ? 'desc' : order === 'ascending' ? 'asc' : ''
  pageNum.value = 1 // 排序变化＝取数口径变化，回第 1 页
  load()
}
const voucherOrderBy = ref('')
const voucherOrder = ref('')
function onVoucherSortChange({ prop, order }) {
  voucherOrderBy.value = order ? String(prop || '') : ''
  voucherOrder.value = order === 'descending' ? 'desc' : order === 'ascending' ? 'asc' : ''
  voucherPageNum.value = 1
  loadVouchers()
}

// 批量过账：POST /tenant/sharing/voucher/batch-post，请求体为凭证 id 数组
// 响应 data={ success, fail:[{id, reason}] }，按 Approval.vue 批量审批的失败明细模式展示
//
// D-DEC-165（前后端契约断裂 → 说明整段丢弃）：原实现用 ElMessageBox.prompt 把「过账说明」设成必填
// （≥2 字，不填就无法过账），但适配层只发纯 id 数组、端点签名就是 @RequestBody List<Long> ids、
// VoucherService.post() 也没有 comment 形参 ⇒ 用户键入的说明 100% 静默丢弃，
// 界面承诺了一条根本不存在的入账留痕（假必填）。这里先停止假必填，改为不带输入框的确认。
// 留痕的两条真出路（择一落地，均需后端改动／人工裁决）：
//   A. 复用既有承载列 op_voucher.remark（该列存在且同域同类动作已在用：红字冲销 reverseVoucher
//      把 reason 写成「红字冲销：xxx」）——由 SharingController.voucherBatchPost 收 {ids, comment}、
//      VoucherService.post() 增 remark 形参落该列；属共享/凭证域后端接线（不在本面）。
//   B. 若要的是「每次过账一条独立入账理由」（不覆盖凭证备注、一次批量共用一条并留操作人），
//      则需新列或新表 ⇒ DDL 变更，需人工裁决。
// 任一方案落地后，把下面的 confirm 换回 prompt 并随 body.comment 送出即可（适配层按 body 透传 id）。
async function batchPost() {
  const rows = postableRows.value
  if (!rows.length) { ElMessage.warning('请先勾选未过账且非本人制单的凭证'); return }
  try {
    await ElMessageBox.confirm(
      `将对勾选的 ${rows.length} 张凭证批量过账（过账后落账总账）。确认继续？`,
      '批量过账',
      {
        type: 'warning',
        confirmButtonText: '确认过账',
        cancelButtonText: '取消',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
  } catch (_) {
    return // 用户取消
  }
  batchPosting.value = true
  try {
    const voucherIds = rows.map((r) => r.id)
    const res = await glApi.batchPostVoucher({ voucherIds })
    // 宽容解析：data = { success?, fail?/failed?: [{ id, reason?/msg? }] }
    const failList = Array.isArray(res?.fail) ? res.fail : Array.isArray(res?.failed) ? res.failed : []
    const declared = Number(res?.success)
    const okCount = Number.isFinite(declared) ? declared : Math.max(0, rows.length - failList.length)
    if (!failList.length) {
      ElMessage.success(`批量过账完成：成功 ${okCount}/${rows.length} 张`)
    } else {
      ElMessage.warning(`批量过账完成：成功 ${okCount} 张，失败 ${failList.length} 张，明细见弹窗`)
      const lines = failList.map((f, i) => {
        const r = rows.find((x) => String(x.id) === String(f?.id))
        const name = r?.voucherNo || '凭证 #' + (f?.id ?? i + 1)
        return `${i + 1}. ${name}：${f?.reason || f?.msg || f?.message || '过账失败'}`
      })
      ElMessageBox.alert(
        h('div', null, lines.map((l) => h('div', { style: 'margin:2px 0;line-height:1.6' }, l))),
        `批量过账失败明细（${failList.length} 张）`,
        { type: 'error', confirmButtonText: '知道了' },
      ).catch(() => {})
    }
    voucherTableRef.value?.clearSelection()
    voucherSelRows.value = []
    loadVouchers()
  } catch (e) {
    // 接口 404（后端未部署该路由）→ 给出可行动的契约提示而不是英文原文
    if (e?.status === 404 || /404/.test(String(e?.message))) {
      ElMessage.error('批量过账功能暂不可用（后端接口未部署），请联系管理员')
    } else {
      ElMessage.error(e?.message || '批量过账失败，请稍后重试')
    }
  } finally { batchPosting.value = false }
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'entry') {
      // R5-D-149：orderBy/order 走后端白名单排序；未排序时传空＝后端默认 id 升序
      const d = await glApi.entry(pageNum.value, pageSize.value, companyId.value, year.value, month.value, subjectId.value, entryOrderBy.value || undefined, entryOrder.value || undefined)
      rows.value = d.list || []
      total.value = Number(d.total || 0)
    } else if (tab.value === 'balance') {
      balance.value = await glApi.balance(companyId.value || 1, year.value, month.value) || []
    } else if (tab.value === 'voucher') {
      await loadVouchers()
    } else {
      trial.value = await glApi.trial(companyId.value || 1, year.value, month.value)
    }
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

watch(tab, () => { pageNum.value = 1; load() })
// R5-D-220：props.tab 此前只取一次初值（深链进入时定页签），同路由复用组件/父级改 tab 时
// 页签不跟随，界面停留旧页签。补 watcher 同步 props → tab（重载交由上面的 watch(tab) 处理）。
watch(() => props.tab, (v) => { if (v && v !== tab.value) tab.value = v })

onMounted(async () => {
  try {
    const c = await orgApi.companyTree()
    companies.value = c || []
  } catch { /* 忽略 */ }
  try {
    const s = await mdApi.subjects({ pageNum: 1, pageSize: 500 })
    subjects.value = (s.list || []).map(x => x)
  } catch { /* 忽略 */ }
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'voucherNo', label: '凭证号' }, { prop: 'voucherId', label: '凭证ID' }, { prop: 'subjectCode', label: '科目编码' }, { prop: 'subjectName', label: '科目名称' }, { prop: 'direction', label: '方向' }, { prop: 'amount', label: '金额' }, { prop: 'summary', label: '摘要' }, { prop: 'bizId', label: '业务单ID' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `GlLedger_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `GlLedger_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
.deb { color: #f56c6c; font-weight: 600; }
.crd { color: #67c23a; font-weight: 600; }
.tabless :deep(.el-tabs__header) { display: none; }
/* R5-D-219：期间文本卡样式——对齐同排 el-statistic 的标题/值两行视觉 */
.period-stat__title { font-size: 14px; color: var(--el-text-color-secondary, #909399); margin-bottom: 10px; line-height: 1; }
.period-stat__value { font-size: 24px; color: var(--el-text-color-primary, #303133); font-weight: 400; line-height: 1; }
</style>