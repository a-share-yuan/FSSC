<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>采购订单（PO）</b>
        <el-button type="primary" size="small" @click="openCreate()">新增采购订单</el-button>
        <el-input v-model="q.keyword" placeholder="订单号/供应商" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="执行中" value="OPEN" />
          <el-option label="已审批" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已开票" value="INVOICED" />
          <el-option label="已付款" value="PAID" />
          <el-option label="已关闭" value="CLOSED" />
          <el-option label="已归档" value="ARCHIVED" />
          <el-option label="已作废" value="VOID" />
        </el-select>
        <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button type="info" size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 12px">采购订单 -> 收货(GRN) -> 三单匹配 -> 发票校验 -> 付款</span>
      </div>
    </template>

    <!-- R5-D-072：无查看权限时显式无权限态（4030 不得被吞成「没数据」空表） -->
    <el-empty v-if="!canList" description="当前账号无采购订单查看权限，请联系管理员授权 po:order:list" />
    <SkeletonList v-if="loading && canList" :rows="8" :cols="5" />
    <el-table v-else-if="canList" :data="rows" size="small" stripe max-height="520" border @selection-change="handleSelectionChange" @row-dblclick="viewDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="poNo" label="订单号" width="150" fixed="left"  sortable />
      <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column label="币种" width="70" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.currency || 'CNY' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderDate" label="下单日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
      <el-table-column prop="deliveryDate" label="交货日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
      <el-table-column label="订单金额" width="120" align="right">
        <template #default="{ row }">
          <span style="font-weight:600">{{ fmt(row.amount) }}</span>
          <span style="color:#909399; font-size:11px; margin-left:2px">{{ row.currency || 'CNY' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="累计收货" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.receivedAmount) }}</template>
      </el-table-column>
      <el-table-column label="剩余可收" width="110" align="right">
        <template #default="{ row }">
          <span :style="{ color: Number(row.amount || 0) - Number(row.receivedAmount || 0) < 0 ? '#f56c6c' : '#67c23a' }">
            {{ fmt(Number(row.amount || 0) - Number(row.receivedAmount || 0)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="paymentTerms" label="付款条件" width="120" show-overflow-tooltip  sortable />
      <el-table-column prop="buyerName" label="采购员" width="90"  sortable />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="poListTagType(row.status)" size="small">{{ poListLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <!-- 批次1 状态门控：编辑/提交仅草稿；驳回单走「编辑重提」回退草稿后重报 -->
          <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openCreate(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="submitOrder(row)">提交审批</el-button>
          <el-button v-if="row.status === 'REJECTED'" link type="warning" size="small" @click="returnToEdit(row)">编辑重提</el-button>
          <el-button link type="info" size="small" @click="viewDetail(row)">详情</el-button>
          <!-- 删除：仅草稿(DRAFT)且无下游关联可删，逻辑删除 -->
          <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="removeOrder(row)">删除</el-button>
          <!-- 作废：非终态（含已驳回）可作废，需填写作废原因；与后端 voidOrder 同口径 -->
          <el-button v-if="['DRAFT','OPEN','APPROVED','REJECTED'].includes(row.status)" link type="danger" size="small" @click="voidOrder(row)">作废</el-button>
          <!-- 终态机人工腿（X-20）：「权限码 + 状态」双重门控，状态口径同 PurchaseService.terminateOrder
               （非 VOID 且非 PAID/CLOSED/ARCHIVED）。与「作废」并列可见：作废拦在下游收货/付款数据上，
               那种单改走「终止」，谁能点由后端状态前置兜底。 -->
          <el-button v-if="['OPEN','APPROVED','REJECTED','INVOICED'].includes(row.status) && hasPerm('po:order:update')" link type="danger" size="small" @click="terminateOrderRow(row)">终止</el-button>
          <!-- 归档：仅已关闭(CLOSED)可归档，落终态 ARCHIVED 后不可再变更；口径同 PurchaseService.archiveOrder -->
          <el-button v-if="row.status === 'CLOSED' && hasPerm('po:order:update')" link type="warning" size="small" @click="archiveOrderRow(row)">归档</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑采购订单' : '新增采购订单'" width="780px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="100px" size="small">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <!-- 订单号＝系统取号（后端留空自动生成 PO+年月+序号），按治理口径 Q1 人工只读字段不加红星 -->
            <el-form-item label="订单号">
              <el-input v-model="form.poNo" placeholder="留空则由系统自动取号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下单日期" prop="orderDate">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" filterable style="width: 100%" placeholder="搜索选择供应商" @change="(id) => { const s = suppliers.find(x => x.id === id); if (s) form.supplierName = s.supplierName }">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联合同">
              <el-select v-model="form.contractId" filterable clearable style="width: 100%" placeholder="选择框架协议合同（可选）">
                <el-option v-for="c in contracts" :key="c.id" :label="c.contractNo + ' ' + c.contractName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 采购订单关联在建工程（CIP 项目）：后端契约 /tenant/cip/list 未部署(404)时隐藏本字段 -->
        <el-row v-if="cipAvailable" :gutter="16">
          <el-col :span="12">
            <el-form-item label="CIP 项目">
              <el-select v-model="form.cipId" filterable clearable style="width: 100%" placeholder="关联在建工程项目（可选）">
                <el-option v-for="c in cipOptions" :key="c.id" :label="cipLabel(c)" :value="c.id" />
              </el-select>
              <div style="font-size:12px;color:#909399">仅显示在建项目；关联后收货/发票可按项目归集</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">商务条件</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="订单金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />

                
                
                
                
                
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交货日期">
              <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="预计交货日期" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="付款条件">
              <el-select v-model="form.paymentTerms" filterable allow-create style="width: 100%" placeholder="选择或输入付款条件">
                <el-option label="货到付款" value="货到付款" />
                <el-option label="30天净额" value="30天净额" />
                <el-option label="60天净额" value="60天净额" />
                <el-option label="90天净额" value="90天净额" />
                <el-option label="预付30%" value="预付30%" />
                <el-option label="预付50%" value="预付50%" />
                <el-option label="分期付款" value="分期付款" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" :disabled="!!form.id" style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="执行中" value="OPEN" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
              <div style="font-size:12px;color:#909399">草稿单不发起审批，可直接删除；执行中保存即发起订单审批</div>
              <div v-if="form.id" style="font-size:12px;color:#909399">已保存订单的状态由审批/收货/付款/终态动作推进，编辑单据不改变状态</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">联系信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="采购员">
              <el-input v-model="form.buyerName" placeholder="采购负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" placeholder="联系方式" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="法人主体" prop="companyId">
              <el-tree-select
                v-model="form.companyId"
                :data="companyTree"
                :props="{ label: 'companyName', value: 'id', children: 'children' }"
                check-strictly
                placeholder="选择公司"
                style="width: 100%"
              />
              <div style="font-size:12px;color:#909399">决定入哪本账与后续三单匹配范围；公司账号建单时自动带入本公司</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="交货地址">
          <el-input v-model="form.deliveryAddress" placeholder="详细交货地址" />
        </el-form-item>

        <el-divider content-position="left">行项目明细</el-divider>
        <el-table :data="formLines" size="small" border style="margin-bottom: 8px" max-height="240" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
          <el-table-column label="行号" width="55" align="center">
            <!-- D-UI-02（2026-10-19 G2 幕真跑发现）：行项目明细不在 el-form 内，guardSubmit 校验不到，
                 validateLines 失败只弹一条 3 秒即逝的 ElMessage.warning ⇒ 点【保存并提交】毫无反应，
                 自动化脚本与新手都抓不到原因。此处把出错行号染红，配合下方持久提示条形成可见反馈。 -->
            <template #default="{ $index }">
              <span :style="lineErrorAt($index) ? 'color:#f56c6c;font-weight:600' : ''">
                {{ $index + 1 }}{{ lineErrorAt($index) ? ' ' : '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="物料编码" width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.materialCode"
                size="small"
                filterable
                remote
                reserve-keyword
                allow-create
                default-first-option
                placeholder="搜索物料编码/名称"
                :remote-method="searchMaterials"
                :loading="materialSearching"
                style="width: 100%"
                @change="(val) => { const m = materialOptions.find(x => (x.itemCode || x.code) === val); if (m) row.materialName = m.itemName || m.name || '' }"
              >
                <el-option
                  v-for="m in materialOptions"
                  :key="m.id"
                  :label="(m.itemCode || m.code || '') + ' ' + (m.itemName || m.name || '')"
                  :value="m.itemCode || m.code || ''"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="150">
            <template #header"><span style="color:#f56c6c">*</span>物料名称</template>
            <template #default="{ row }">
              <el-input v-model="row.materialName" size="small" placeholder="物料/服务名称（勿填金额）" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="2" :controls="false" size="small" style="width: 100%" @change="calcLineAmt(row)" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">
              <el-select v-model="row.unit" size="small" filterable allow-create default-first-option placeholder="单位" style="width: 100%"><el-option label="个" value="个" /><el-option label="台" value="台" /><el-option label="套" value="套" /><el-option label="件" value="件" /><el-option label="批" value="批" /><el-option label="米" value="米" /><el-option label="吨" value="吨" /><el-option label="kg" value="kg" /><el-option label="箱" value="箱" /><el-option label="包" value="包" /></el-select>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false" size="small" style="width: 100%" @change="calcLineAmt(row)" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">
              <span style="font-weight: 600">{{ fmt(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="formLines.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display: flex; gap: 12px; align-items: center; margin-bottom: 12px">
          <el-button size="small" type="primary" plain @click="addLine">+ 添加行</el-button>
          <!-- D-UI-02：行项目校验失败的持久提示条（红色，不随时间消失） -->
          <span v-if="lineError" style="color: #f56c6c; font-size: 13px; font-weight: 600">
             {{ lineError }}（请修正标  的行后再提交）
          </span>
          <!-- D-V4R-91：勾稽实时提示。原先行项目合计只作展示、与订单金额毫无比对，
               导致「表头 60000 / 行合计 0」一路放行。 -->
          <span v-if="amountMismatch" style="color: #f56c6c; font-size: 13px">
             行项目合计 {{ fmt(lineTotal) }} 与订单金额 {{ fmt(Number(form.amount || 0)) }} 不一致，保存将被拒
          </span>
          <span style="margin-left: auto; color: #909399; font-size: 13px">
            行项目合计：<b style="color: #409eff; font-size: 15px">{{ fmt(lineTotal) }}</b> {{ form.currency || 'CNY' }}
          </span>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="订单备注说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save(false)">保存</el-button>
        <!-- D-V4R-89(P3-UI-01)：弹窗内补【保存并提交】，免去"关闭弹窗→回列表找行→点提交"三步跳转 -->
        <el-button type="success" @click="save(true)">保存并提交</el-button>
        <div style="flex:1"></div>
        <span style="font-size:12px;color:#909399;margin-right:8px">
          仅【保存】＝存为草稿（不进审批）；【保存并提交】＝直接进入采购经理审批流
        </span>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="dlgDetail" title="采购订单详情" width="700px">
      <el-descriptions :column="2" border size="small" v-if="detailRow">
        <el-descriptions-item label="订单号">{{ detailRow.poNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === 'OPEN' ? 'success' : statusType(detailRow.status)" size="small">{{ detailRow.status === 'OPEN' ? '执行中' : statusLabel(detailRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailRow.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="下单日期">{{ detailRow.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ fmt(detailRow.amount) }} {{ detailRow.currency || 'CNY' }}</el-descriptions-item>
        <el-descriptions-item label="累计收货">{{ fmt(detailRow.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="剩余可收">{{ fmt(Number(detailRow.amount || 0) - Number(detailRow.receivedAmount || 0)) }}</el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detailRow.deliveryDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="付款条件">{{ detailRow.paymentTerms || '—' }}</el-descriptions-item>
        <el-descriptions-item label="采购员">{{ detailRow.buyerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailRow.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailCipLabel" label="CIP 项目">{{ detailCipLabel }}</el-descriptions-item>
        <el-descriptions-item label="交货地址" :span="2">{{ detailRow.deliveryAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left" v-if="detailLines.length">行项目明细</el-divider>
      <el-table :data="detailLines" size="small" border v-if="detailLines.length" max-height="300" style="margin-bottom: 12px" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
        <el-table-column label="行号" width="55" align="center" type="index" />
        <el-table-column prop="materialCode" label="物料编码" width="120"  sortable />
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column prop="quantity" label="数量" width="90" align="right"  sortable />
        <el-table-column prop="unit" label="单位" width="70" align="center"  sortable />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right"  sortable />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600">{{ fmt(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dlgDetail = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="PURCHASE_ORDER_REVIEW" />
</template>

<script setup>

const { rules: formRules } = useFormRules('PO_ORDER')
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { purchaseApi, supplierApi, orgApi, masterApi, cipFlowApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import { useCurrency } from '@/composables/useCurrency'
// R5-13: 快捷键
useKeyboard({
  'ctrl+s': () => { try { save() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
// 工作台「我的单据」深链：?kw=订单号 直接定位该行（台账页自带行内「编辑重提」）
const route = useRoute()
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

/** R5-D-072：列表权限预判（与后端 @RequiresPerm("po:order:list") 同码）——无权不打接口、渲染无权限态 */
const canList = hasPerm('po:order:list')
const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const suppliers = ref([])
const contracts = ref([])
const companyTree = ref([])
const dlg = ref(false)
const dlgDetail = ref(false)
const detailRow = ref(null)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: route.query.kw ? String(route.query.kw) : '', status: '' })
// 列表显示口径（业务确认 2026-09-21）：「执行中」只对应状态 OPEN；
// 其余状态一律走 billStatus 统一映射（APPROVED=已审批、DRAFT=草稿、CLOSED=已关闭、VOID=已作废），
// 不再用行内三元兜底把未列举状态误渲染为「已关闭」。
function poListLabel(s) {
  if (s === 'OPEN') return '执行中'
  return statusLabel(s)
}
function poListTagType(s) {
  if (s === 'OPEN') return 'success'
  return statusType(s)
}
const form = reactive({})
const formRef = ref(null)
const { submit: guardSubmit } = useSubmitGuard(formRef)
const { autofill, saveFormHistory } = useAutoFill()
const formLines = ref([])
// D-UI-02：行项目校验失败的持久提示（原文案只有 3 秒即逝的 toast，点【保存并提交】等于毫无反应）
const lineError = ref('')
const lineErrorRows = ref([])
function lineErrorAt(rowIndex) {
  return lineErrorRows.value.includes(rowIndex)
}
const detailLines = ref([])
const materialOptions = ref([])
const materialSearching = ref(false)

// ---------------- 采购订单关联在建工程（CIP 项目） 
// 数据源 GET /api/tenant/cip/list?status=IN_PROGRESS（后端并行部署中）：
// 404/加载失败/空列表时隐藏「CIP 项目」字段，不弹错误、不影响新增/编辑
const cipAvailable = ref(false)
const cipOptions = ref([])

function cipLabel(c) {
  if (!c) return ''
  return ((c.cipCode || '') + ' ' + (c.cipName || '')).trim() || String(c.id ?? '')
}

async function loadCip() {
  try {
    const d = await cipFlowApi.inProgress()
    const list = Array.isArray(d) ? d : (d?.list || d?.records || [])
    if (list.length) {
      cipOptions.value = list
      cipAvailable.value = true
    }
  } catch (e) { cipAvailable.value = false }
}

const detailCipLabel = computed(() => {
  const row = detailRow.value
  if (!row || !row.cipId) return ''
  const c = cipOptions.value.find((x) => x.id === row.cipId)
  return (c && cipLabel(c)) || row.cipName || ('CIP#' + row.cipId)
})

const lineTotal = computed(() => formLines.value.reduce((s, r) => s + Number(r.amount || 0), 0))

// D-V4R-91：表头金额与行项目合计勾稽（容差 0.01，与后端 assertHeaderLineAmountConsistent 同口径）
// 无行项目时不判（采购订单允许只录表头金额）
const amountMismatch = computed(() => {
  const header = Number(form.value?.amount || form.amount || 0)
  const sum = Number(lineTotal.value || 0)
  return sum > 0 && Math.abs(header - sum) > 0.01
})

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

function addLine() {
  formLines.value.push({ materialCode: '', materialName: '', quantity: 1, unit: '', unitPrice: 0, amount: 0 })
}

function calcLineAmt(row) {
  row.amount = Number(row.quantity || 0) * Number(row.unitPrice || 0)
}

async function searchMaterials(query) {
  if (!query) { materialOptions.value = []; return }
  materialSearching.value = true
  try {
    const d = await masterApi.item.page({ pageNum: 1, pageSize: 20, keyword: query })
    materialOptions.value = d.list || []
  } catch (e) { materialOptions.value = [] }
  finally { materialSearching.value = false }
}

function onMaterialSelect(row, itemId) {
  const m = materialOptions.value.find(x => x.id === itemId)
  if (m) {
    row.materialCode = m.itemCode || m.code || ''
    row.materialName = m.itemName || m.name || ''
  }
}

async function load() {
  if (!canList) return // R5-D-072：无权不请求，避免 4030 被吞成空表
  loading.value = true
  try {
    const r = await purchaseApi.orderPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate(row) {
  Object.assign(form, row ? { ...row } : {
    poNo: '', supplierId: null, supplierName: '', contractId: null, cipId: null,
    orderDate: new Date().toISOString().slice(0, 10), amount: 0,
    currency: 'CNY', deliveryDate: '', paymentTerms: '', buyerName: '',
    contactPhone: '', deliveryAddress: '', companyId: store.user?.companyId ?? null,
    status: 'DRAFT', remark: '',
  })
  formLines.value = row && row.lines ? row.lines.map(l => ({ ...l })) : []
  if (!formLines.value.length) addLine()
  dlg.value = true
}

function viewDetail(row) {
  detailRow.value = row
  detailLines.value = row && row.lines ? row.lines : []
  dlgDetail.value = true
}

/**
 * D-V4R-91：行项目逐行校验（与后端 PurchaseService.assertOrderLine 同口径）。
 * guardSubmit 只能校验带 prop 的表单项，行项目明细不在 el-form 内，
 * 历史上从未被任何一层校验过——自动化脚本把 60000 填进「物料名称」、
 * 数量/单价留默认值(1/0) 时照样落库，直到三单匹配才暴露。
 * 后端为最终闸门，此处提前拦是为了让用户在点【保存】当场就看到红字，
 * 而不是提交被拒后去猜哪一行错了。
 */
function validateLines() {
  const lines = formLines.value || []
  // 全空行忽略（模板新增的空行不算填写内容）
  const active = lines.filter(l => l.materialName || l.materialCode
    || Number(l.quantity || 0) !== 0 || Number(l.amount || 0) !== 0)
  // D-UI-02：active 是过滤后的下标，原实现按 active 下标提示「第 N 行」，
  // 与表格实际行号可能错位（空行夹在中间时）。此处回填真实表格下标供行号染红用。
  const activeIdx = []
  lines.forEach((l, i) => {
    if (l.materialName || l.materialCode || Number(l.quantity || 0) !== 0 || Number(l.amount || 0) !== 0) activeIdx.push(i)
  })
  const fail = (msg, activePos) => {
    lineError.value = msg
    lineErrorRows.value = [activeIdx[activePos] ?? -1]
    ElMessage.warning(msg)
    return false
  }
  lineError.value = ''
  lineErrorRows.value = []
  if (!active.length) return true // 无行项目：采购订单允许只录表头金额

  for (let i = 0; i < active.length; i++) {
    const l = active[i]
    const pos = `第 ${i + 1} 行`
    if (!String(l.materialName || '').trim()) {
      return fail(`${pos}物料名称必填（只有物料编码不够）`, i)
    }
    const name = String(l.materialName).trim()
    if (/^[0-9][0-9,]*(\.[0-9]+)?$/.test(name)) {
      return fail(`${pos}物料名称「${name}」是纯数字，疑似把数量/金额填进了名称栏，请填实际品名`, i)
    }
    if (!(Number(l.quantity) > 0)) {
      return fail(`${pos}数量必须大于 0（当前 ${l.quantity}）`, i)
    }
    if (!(Number(l.unitPrice) > 0)) {
      return fail(`${pos}单价必须大于 0（当前 ${l.unitPrice}）`, i)
    }
    const expect = Number(l.quantity) * Number(l.unitPrice)
    const actual = Number(l.amount || 0)
    if (actual > 0 && Math.abs(expect - actual) > 0.01) {
      return fail(`${pos}量价勾稽不符：数量 × 单价 = ${expect.toFixed(2)}，行金额 = ${actual.toFixed(2)}`, i)
    }
  }
  lineError.value = ''
  lineErrorRows.value = []

  // 表头与行合计勾稽（与后端 assertHeaderLineAmountConsistent 同口径）
  const sum = active.reduce((s, l) => s + Number(l.amount || 0), 0)
  const header = Number(form.value?.amount || form.amount || 0)
  if (sum > 0 && Math.abs(header - sum) > 0.01) {
    ElMessage.warning(`订单金额(${header.toFixed(2)}) ≠ 行项目合计(${sum.toFixed(2)})，请检查行明细`)
    return false
  }
  return true
}

/**
 * D-V4R-89：保存 / 保存并提交。
 * 历史设计把「提交审批」只放在列表行内（PoOrder.vue 行操作列），弹窗底部只有【保存】，
 * 新手按手册「→【保存】→【提交】」操作时会在弹窗里找不到提交键，误以为功能缺失
 * （P3-UI-01）。此处补【保存并提交】：一次点击完成「保存 → 提交审批」，
 * 省掉关闭弹窗、回列表找行、再点一次的三步跳转。
 * 编辑态（form.id 已存在且已是草稿）同样允许——驳回重提场景也需要一键重提。
 */
async function save(thenSubmit = false) {
  saveFormHistory('PO_ORDER', form.value || form)

  await guardSubmit(async () => {
    if (!validateLines()) return          // D-V4R-91：行项目闸门，先于请求发出
    try {
      const payload = { ...form, lines: formLines.value.filter(l => l.materialName || l.materialCode) }
      let saved
      if (form.id) saved = await purchaseApi.orderUpdate(payload)
      else saved = await purchaseApi.orderCreate(payload)

      if (!thenSubmit) {
        ElMessage.success('已保存')
        dlg.value = false
        load()
        return
      }

      // 保存并提交：create 接口 Result<Long> 经 http 拦截器解包后直接是 id；
      //              update 返回 void，故编辑态用回原 form.id。
      const id = form.id ?? (typeof saved === 'number' ? saved : saved?.id ?? saved?.data?.id ?? null)
      if (!id) {
        ElMessage.warning('已保存，但未能取得单据标识，请到列表中点击【提交审批】')
        dlg.value = false
        load()
        return
      }
      await purchaseApi.orderSubmit(id)
      ElMessage.success('已保存并提交审批')
      dlg.value = false
      load()
    } catch (e) {
      // 提交阶段失败但草稿已落库：明确告知"已保存为草稿"，避免用户重复建单
      ElMessage.error(thenSubmit && (form.id || form.poNo)
        ? `${e.message}（单据已保存为草稿，可在列表中重试【提交审批】）`
        : e.message)
    }
  })
}

// 批次1：草稿订单提交审批（DRAFT → OPEN + 起 PURCHASE_ORDER_REVIEW）
async function submitOrder(row) {
  try {
    await purchaseApi.orderSubmit(row.id)
    ElMessage.success(`订单 ${row.poNo} 已提交审批`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 批次1：驳回订单编辑重提（REJECTED → DRAFT），随即打开编辑表单
async function returnToEdit(row) {
  try {
    await purchaseApi.orderReturnToEdit(row.id)
    await load()
    const fresh = rows.value.find((x) => x.id === row.id)
    openCreate(fresh || { ...row, status: 'DRAFT' })
    ElMessage.success('已转回草稿，修改后请重新提交审批')
  } catch (e) { ElMessage.error(e.message) }
}

// 删除采购订单：仅草稿(DRAFT)且无下游关联可删（后端逻辑删除）
async function removeOrder(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除草稿订单 ${row.poNo}（${row.supplierName || ''}）？删除后列表不再显示`,
      '删除采购订单',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch (_) { return }
  try {
    await purchaseApi.orderDelete(row.id)
    ElMessage.success(`订单 ${row.poNo} 已删除`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 作废采购订单：仅 DRAFT/OPEN 等非终态可作废，必填作废原因（≥2字）；
// 后端会拒绝已收货/已付款订单，错误信息经 request 统一拦截器+此处 catch 原样展示（不吞错）
async function voidOrder(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认作废订单 ${row.poNo}（${row.supplierName || ''}）？作废后不可恢复`,
      '作废采购订单',
      {
        inputPlaceholder: '请填写作废原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的作废原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await purchaseApi.orderVoid({ orderId: row.id, reason })
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 终止采购订单（终态机人工腿，权限码 po:order:update）：处理已收货/已挂付款但不再履约的单，
// 与「作废」的分工由后端同口径把关；终止原因必填（≥2字），落终态后不可恢复
async function terminateOrderRow(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认终止订单 ${row.poNo}（${row.supplierName || ''}）？终止后剩余未执行部分不再履约，且不可恢复`,
      '终止采购订单',
      {
        inputPlaceholder: '请填写终止原因（必填，不少于 2 个字）',
        inputValidator: (v) => (v != null && String(v).trim().length >= 2) || '请填写不少于 2 个字的终止原因',
        closeOnClickModal: false,
        distinguishCancelAndClose: true,
      },
    )
    reason = String(value || '').trim()
  } catch (_) { return } // 用户取消
  try {
    await purchaseApi.orderTerminate(row.id, { reason })
    ElMessage.success('已终止')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 归档采购订单（终态机人工腿，权限码 po:order:update）：仅已关闭(CLOSED)单可归档，落终态 ARCHIVED
async function archiveOrderRow(row) {
  try {
    await ElMessageBox.confirm(
      `确认归档订单 ${row.poNo}（${row.supplierName || ''}）？归档后进入终态，不可再变更`,
      '归档采购订单',
      { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' },
    )
  } catch (_) { return }
  try {
    await purchaseApi.orderArchive(row.id)
    ElMessage.success(`订单 ${row.poNo} 已归档`)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

onMounted(async () => {
  loadCurrency()
  autofill(form, 'PO_ORDER')
  try {
    const [s, ct] = await Promise.all([
      supplierApi.page({ pageNum: 1, pageSize: 100 }),
      orgApi.companyTree(),
    ])
    suppliers.value = s.list || []
    companyTree.value = ct || []
  } catch (e) { /* 忽略 */ }
  loadCip() // CIP 项目下拉数据源（失败/404 静默隐藏字段）
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'poNo', label: '订单号' }, { prop: 'supplierName', label: '供应商' }, { prop: 'orderDate', label: '下单日期' }, { prop: 'deliveryDate', label: '交货日期' }, { prop: 'paymentTerms', label: '付款条件' }, { prop: 'buyerName', label: '采购员' }]
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
  a.download = `PoOrder_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `PoOrder_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
/* D-V4R-89：footer 内说明文案靠弹性占位推到右端 */
:deep(.el-dialog__footer) {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
