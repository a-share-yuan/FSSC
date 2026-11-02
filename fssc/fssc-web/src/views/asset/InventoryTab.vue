<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap">
      <el-button type="success" @click="openInv('IN')">{{ $t('asset.inventory.Stock') }}</el-button>
      <el-button type="warning" @click="openInv('OUT')">{{ $t('asset.inventory.Stock2') }}</el-button>
      <el-button type="primary" @click="queryBalance">{{ $t('asset.inventory.Closing') }}</el-button>
      <el-button plain @click="openMoves">{{ $t('asset.inventory.Stock3') }}</el-button>
      <el-button type="primary" plain @click="openInv('ISSUE')">{{ $t('asset.inventory.Issue') }}</el-button>
      <el-button type="danger" plain @click="openInv('DISPOSAL')">{{ $t('asset.inventory.Inventory') }}</el-button>
      <el-button type="warning" plain @click="openInv('DISPOSAL_CHECK')">{{ $t('asset.inventory.Disposition') }}</el-button>
      <el-button type="danger" plain @click="openInv('DISPOSAL_EXEC')">{{ $t('asset.inventory.Disposition2') }}</el-button>
      <el-button type="danger" @click="openInv('INTERNAL_SALE')">{{ $t('asset.inventory.Internal') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" border stripe v-else :empty-text="$t('asset.inventory.No')" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="itemCode" :label="$t('asset.inventory.Material')" width="140" sortable />
      <el-table-column prop="itemName" :label="$t('asset.inventory.Material2')" min-width="150" sortable />
      <el-table-column prop="spec" :label="$t('asset.inventory.Specification')" width="120" sortable />
      <el-table-column prop="unit" :label="$t('asset.inventory.Unit')" width="70" sortable />
      <el-table-column :label="$t('asset.inventory.Pricing')" width="90" align="center">
        <template #default="{ row }">{{ priceMethodName(row.priceMethod) }}</template>
      </el-table-column>
      <el-table-column label="仓库" width="100">
        <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
      </el-table-column>
      <el-table-column label="入库数量" width="110" align="right">
        <template #default="{ row }">{{ fmt0(row.inNum) }}</template>
      </el-table-column>
      <el-table-column label="出库数量" width="110" align="right">
        <template #default="{ row }">{{ fmt0(row.outNum) }}</template>
      </el-table-column>
      <el-table-column label="结存数量" width="110" align="right">
        <template #default="{ row }"><b>{{ fmt0(row.remainingNum) }}</b></template>
      </el-table-column>
      <el-table-column label="结存金额" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.remainingAmount) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="Number(row.remainingNum) > 0" link size="small" type="danger" @click="doInvScrap(row)">报废</el-button>
          <el-button link size="small" type="primary" @click="doInvCheck(row)">盘点差异</el-button>
          <el-button link size="small" type="warning" @click="doInvDevalue(row)">跌价准备</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="loadInventory" />
  </div>

  <!-- 出入库 / 领用 / 处置 / 内部销售 弹窗 -->
  <el-dialog v-model="dlgInv" title="存货操作" width="560px" destroy-on-close>
    <el-form ref="invFormRef" :model="invModel" :rules="formRules" label-width="130px" size="small">
      <el-form-item label="操作类型">
        <el-tag :type="invOperationTag.type" size="small">{{ invOperationTag.label }}</el-tag>
      </el-form-item>
      <el-form-item label="物料编码" prop="itemCode"><el-input v-model="invForm.itemCode" maxlength="64" /></el-form-item>
      <el-form-item label="物料名称" prop="itemName"><el-input v-model="invForm.itemName" /></el-form-item>
      <el-form-item label="规格/单位"><el-input v-model="invForm.spec" style="width:49%" /><el-input v-model="invForm.unit" placeholder="单位" style="width:49%; margin-left:2%" maxlength="32" /></el-form-item>
      <el-form-item label="仓库" prop="warehouseId">
        <el-select v-model="invForm.warehouseId" filterable clearable placeholder="请选择仓库" style="width:100%">
          <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <!-- 仓库候选的唯一来源＝本页存货台账行去重（见 script 内 loadInventory），台账 0 行⇒候选 0 个，
             而「仓库」是必填项⇒点确认只报「请选择仓库」却看不出为什么没得选，与"无权限被画成空态"看不出差别。
             系统当前没有仓库主数据载体（库里没有仓库表，
             后端无实体/端点、菜单无该面），故这里只把成因说实：不造候选、不放宽必填、不写库。 -->
        <div v-if="!warehouses.length" style="font-size:12px;color:#e6a23c;line-height:1.5">当前没有可选仓库：仓库候选取自本页存货台账行，而存货台账为空、系统尚无仓库主数据入口——需先建立仓库主数据或已有台账行</div>
      </el-form-item>
      <el-form-item label="计价方式" prop="priceMethod">
        <el-select v-model="invForm.priceMethod" style="width:100%">
          <el-option label="移动平均" value="MA" />
          <el-option label="先进先出" value="FIFO" />
          <el-option label="月末加权" value="WAVG" />
        </el-select>
      </el-form-item>
      <el-form-item :label="invMode === 'IN' ? '入库数量' : '数量'" prop="qty"><el-input-number v-model="invQty" :min="0" :precision="2" style="width:100%" /></el-form-item>
      <el-form-item v-if="invMode === 'IN'" label="入库单价" prop="price"><el-input-number v-model="invPrice" :min="0" :precision="2" style="width:100%" /></el-form-item>
      <el-form-item v-if="invMode === 'ISSUE'" label="领用用途" prop="extra"><el-input v-model="invExtra" placeholder="如：生产领用 / 项目领用" /></el-form-item>
      <el-form-item v-if="invMode === 'ISSUE'" label="物资归属">
        <el-radio-group v-model="invProjType">
          <el-radio value="PROJECT">项目内</el-radio>
          <el-radio value="NONPROJECT">项目外</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="invMode === 'DISPOSAL' || invMode === 'DISPOSAL_CHECK' || invMode === 'DISPOSAL_EXEC'" label="处置说明" prop="extra"><el-input v-model="invExtra" placeholder="如：报废清理 / 呆滞处理" /></el-form-item>
      <el-form-item v-if="invMode === 'INTERNAL_SALE'" label="目标仓库">
        <el-select v-model="invTargetW" filterable clearable placeholder="请选择目标仓库" style="width:100%">
          <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <!-- 同因第二处：目标仓与本源共用同一份候选（本页台账行派生），台账为空时同样是 0 个选项 -->
        <div v-if="!warehouses.length" style="font-size:12px;color:#e6a23c;line-height:1.5">暂无可选目标仓库（候选来源同上，取自本页存货台账行）</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgInv = false">取消</el-button>
      <el-button type="primary" @click="saveInv">确认</el-button>
    </template>
  </el-dialog>

  <!-- 出入库流水（stock_move 只读回放；没有它，结存对不上时无从逐笔追溯） -->
  <el-drawer v-model="movesVisible" title="出入库流水" size="760px">
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap">
      <el-input v-model="movesItemCode" placeholder="按物料编码过滤" clearable style="width: 200px" @keyup.enter="loadMoves" />
      <el-select v-model="movesWarehouseId" filterable clearable placeholder="按仓库过滤" style="width: 180px">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-button type="primary" @click="loadMoves">查询</el-button>
    </div>
    <el-table :data="movesPagedData" size="small" border stripe v-loading="movesLoading" empty-text="该物料/仓库下暂无出入库流水" max-height="520">
      <el-table-column prop="moveNo" label="流水号" width="150" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">{{ moveTypeName(row.moveType) }}</template>
      </el-table-column>
      <el-table-column prop="itemCode" label="物料编码" width="120" />
      <el-table-column prop="itemName" label="物料名称" min-width="130" />
      <el-table-column label="仓库" width="100">
        <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
      </el-table-column>
      <el-table-column label="数量" width="90" align="right">
        <template #default="{ row }">{{ fmt0(row.qty) }}</template>
      </el-table-column>
      <el-table-column label="单价" width="90" align="right">
        <template #default="{ row }">{{ fmt(row.unitCost) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center" :formatter="(r, c, v) => statusLabel(v)" />
      <el-table-column prop="createTime" label="发生时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
    </el-table>
    <TablePager :pager="movesPager" @change="loadMoves" />
  </el-drawer>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { statusLabel } from '../../constants/billStatus.js'
import { assetApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { useTablePager } from '../../composables/useTablePager'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import TablePager from '../../components/common/TablePager.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const { warehouses, fmt, fmt0, warehouseName, loading } = inject('assetShared')

const inventories = ref([])
const selRows = ref([])
const pager = useTablePager({ mode: 'server', storageKey: 'asset_inventory' })
const { pagedData } = pager

// 出入库流水抽屉（stock_move 只读回放）
const movesVisible = ref(false)
const movesItemCode = ref('')
const movesWarehouseId = ref(null)
const movesPager = useTablePager({ mode: 'server', storageKey: 'asset_inventory_moves' })
// 模板里访问 movesPager.pagedData / .loading 拿到的是 ref 对象本身（嵌套不自动解包），
// 会把 ComputedRef 当表格数据、把 ref 当 loading 布尔 ⇒ 抽屉一渲染就报 "rows is not iterable"。
const { pagedData: movesPagedData, loading: movesLoading } = movesPager

const dlgInv = ref(false)
const invMode = ref('IN')
const invForm = ref({ itemCode: '', itemName: '', spec: '', unit: '', warehouseId: null, priceMethod: 'MA' })
const invQty = ref(0)
const invPrice = ref(0)
const invExtra = ref('')
const invTargetW = ref(null)
const invProjType = ref('PROJECT')

const invFormRef = ref(null)
const { rules: formRules } = useFormRules('INVENTORY_OP')
const { submit: guardInv } = useSubmitGuard(invFormRef)
// 数量/单价/说明散落在各自的 ref 上，校验只认 :model —— 组一个只读视图把散字段并进模型，
// 这样 prop="qty" / prop="price" / prop="extra" 才能解析到值（v-model 仍绑原 ref，不改变写入路径）。
const invModel = computed(() => ({
  ...invForm.value,
  qty: invQty.value,
  price: invPrice.value,
  extra: invExtra.value,
}))

const INV_OP_META = {
  IN: { label: '入库', type: 'success' },
  OUT: { label: '出库', type: 'warning' },
  ISSUE: { label: '领用出库', type: 'primary' },
  DISPOSAL: { label: '存货处置', type: 'danger' },
  DISPOSAL_CHECK: { label: '处置-状态确认', type: 'warning' },
  DISPOSAL_EXEC: { label: '处置-执行账务', type: 'danger' },
  INTERNAL_SALE: { label: '内部销售', type: 'danger' },
}
const invOperationTag = computed(() => INV_OP_META[invMode.value] || { label: invMode.value, type: 'info' })

function priceMethodName(m) { return { MA: '移动平均', FIFO: '先进先出', WAVG: '月末加权' }[m] || m }

async function loadInventory() {
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await assetApi.inventoryPage({ pageNum, pageSize })
      const list = r.list || []
      inventories.value = list
      const wMap = new Map()
      list.forEach((item) => {
        if (item.warehouseId && !wMap.has(item.warehouseId)) {
          wMap.set(item.warehouseId, { id: item.warehouseId, name: item.warehouseName || ('仓库-' + item.warehouseId) })
        }
      })
      warehouses.value = Array.from(wMap.values())
      return { list, total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

function openInv(mode) { invMode.value = mode; invForm.value = { itemCode: '', itemName: '', spec: '', unit: '', warehouseId: null, priceMethod: 'MA' }; invQty.value = 0; invPrice.value = 0; invExtra.value = ''; invTargetW.value = null; invProjType.value = 'PROJECT'; dlgInv.value = true }
async function saveInv() {
  await guardInv(async () => {
    try {
      switch (invMode.value) {
        case 'IN':
          await assetApi.inventoryInStock(invForm.value, invQty.value, invPrice.value)
          ElMessage.success(t('asset.inventory.Stock4'))
          break
        case 'OUT': {
          const cost = await assetApi.inventoryOutStock(invForm.value, invQty.value)
          ElMessage.success('出库完成，发出成本 ' + fmt(cost))
          break
        }
        case 'ISSUE':
          await assetApi.inventoryIssue(invForm.value, invQty.value, invExtra.value, invProjType.value)
          ElMessage.success(t('asset.inventory.Project'))
          break
        case 'DISPOSAL':
          await assetApi.inventoryDisposal(invForm.value, invQty.value, invExtra.value)
          ElMessage.success(t('asset.inventory.Inventory2'))
          break
        case 'DISPOSAL_CHECK':
          await assetApi.inventoryDisposalCheck(invForm.value, invQty.value, invExtra.value)
          ElMessage.success(t('asset.inventory.Disposition3'))
          break
        case 'DISPOSAL_EXEC':
          await assetApi.inventoryDisposalExec(invForm.value, invQty.value, invExtra.value)
          ElMessage.success(t('asset.inventory.Disposition4'))
          break
        case 'INTERNAL_SALE':
          await assetApi.inventoryInternalSale(invForm.value, invQty.value, invTargetW.value)
          ElMessage.success(t('asset.inventory.Internal2'))
          break
        default:
          ElMessage.warning(t('asset.inventory.Unknown'))
          return
      }
      dlgInv.value = false; loadInventory()
    } catch (e) { ElMessage.error(e.message) }
  })
}

const invKey = (row) => ({ itemCode: row.itemCode, itemName: row.itemName, spec: row.spec, unit: row.unit, warehouseId: row.warehouseId, priceMethod: row.priceMethod })
async function doInvScrap(row) {
  try {
    const { value: qty } = await ElMessageBox.prompt('输入报废数量', '存货报废（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: t('asset.inventory.Please') })
    await assetApi.inventoryDisposeScrap(invKey(row), Number(qty))
    ElMessage.success(t('asset.inventory.Inventory3')); loadInventory()
  } catch (e) { /* cancelled */ }
}
async function doInvCheck(row) {
  try {
    const { value: diffQty } = await ElMessageBox.prompt('输入盘点差异数量（正=盘盈/负=盘亏）', '盘点差异处理（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputPattern: /^-?\d+(\.\d+)?$/, inputErrorMessage: t('asset.inventory.Please') })
    await assetApi.inventoryPhysicalCheck(invKey(row), Number(diffQty), '实物盘点差异')
    ElMessage.success(t('asset.inventory.Stocktake')); loadInventory()
  } catch (e) { /* cancelled */ }
}
async function doInvDevalue(row) {
  try {
    const { value: devalueAmount } = await ElMessageBox.prompt('输入跌价准备金额', '存货跌价准备（提交审批）', { confirmButtonText: '提交', cancelButtonText: '取消', inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: t('asset.assetCard.Please') })
    await assetApi.inventoryDevalue(invKey(row), Number(devalueAmount))
    ElMessage.success(t('asset.inventory.Write')); loadInventory()
  } catch (e) { /* cancelled */ }
}

async function queryBalance() {
  try {
    const b = await assetApi.inventoryBalance(invForm.value)
    ElMessage.success('结存：数量 ' + fmt0(b.remainingNum) + '，金额 ' + fmt(b.remainingAmount))
  } catch (e) { ElMessage.error(e.message) }
}

// moveType 中文映射规则与 StockMove 实体注释一致
const MOVE_TYPE_META = { RECEIVE: '入库', ISSUE: '领用出库', SCRAP: '报废', DISPOSAL: '处置', INTERNAL_SALE: '内部销售', CHECK: '盘点', DEVAL: '跌价' }
function moveTypeName(t) { return MOVE_TYPE_META[t] || t || '—' }

function openMoves() { movesVisible.value = true; movesPager.resetPage(); loadMoves() }
async function loadMoves() {
  try {
    await movesPager.load(async ({ pageNum, pageSize }) => {
      const r = await assetApi.inventoryMoves({
        pageNum,
        pageSize,
        itemCode: movesItemCode.value || undefined,
        warehouseId: movesWarehouseId.value || undefined,
      })
      return { list: r.list || [], total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

function openRowDetail(row) {
  return showRowDetail(row, { title: t('common.details') })
}

function handleSelectionChange(val) { selRows.value = val }

function exportCsv() {
  if (!inventories.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'itemCode', label: t('asset.inventory.Material') }, { prop: 'itemName', label: t('asset.inventory.Material2') }, { prop: 'spec', label: t('asset.inventory.Specification') }, { prop: 'unit', label: t('asset.inventory.Unit') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = inventories.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `AssetBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}

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
  a.download = `AssetBoard_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}

onMounted(() => { loadInventory() })

defineExpose({ loadInventory })
</script>
