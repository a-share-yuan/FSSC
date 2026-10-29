<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="std-page">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>报销标准(专属页 · 费用类型 × 岗位职级 × 城市等级 三维标准 + 生效期/优先级/控制方式)</span>
          <div class="ops">
            <el-select v-model="q.cityLevel" size="small" style="width: 130px" placeholder="城市等级" clearable @change="search">
              <el-option v-for="(v, k) in CITY_LEVELS" :key="k" :label="v" :value="k" />
            </el-select>
            <el-select v-model="q.itemId" size="small" style="width: 170px" placeholder="费用类型" clearable filterable @change="search">
              <el-option v-for="i in itemOptions" :key="i.id" :label="i.name" :value="i.id" />
            </el-select>
            <el-input v-model="q.keyword" size="small" style="width: 150px" placeholder="编码/名称" clearable @keyup.enter="search" />
            <el-button size="small" type="primary" @click="search">查询</el-button>
            <el-button size="small" @click="doExport">导出</el-button>
            <el-button size="small" type="success" @click="openForm()">新增标准</el-button>
          </div>
        </div>
      </template>

      <SkeletonList v-if="loading" :rows="10" :cols="8" />
      <el-table v-else :data="rows" size="small" border stripe @row-dblclick="openForm">
        <el-table-column prop="stdCode" label="标准编码" width="110" show-overflow-tooltip />
        <el-table-column prop="stdName" label="标准名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="费用类型" min-width="120">
          <template #default="{ row }">{{ itemName(row.itemId) }}</template>
        </el-table-column>
        <el-table-column label="岗位职级区间" width="120" align="center">
          <template #default="{ row }">{{ levelRange(row) }}</template>
        </el-table-column>
        <el-table-column label="城市/地区等级" width="110" align="center">
          <template #default="{ row }">{{ CITY_LEVELS[row.cityLevel] || row.cityLevel || '不限' }}</template>
        </el-table-column>
        <el-table-column label="标准类型" width="90" align="center">
          <template #default="{ row }">{{ STD_TYPES[row.stdType] || row.stdType }}</template>
        </el-table-column>
        <el-table-column label="标准金额" width="110" align="right">
          <template #default="{ row }">{{ fmtMoney(row.amount) }}<span class="unit">{{ row.currencyCode === 'CNY' ? '元' : row.currencyCode }}</span></template>
        </el-table-column>
        <el-table-column label="控制方式" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.controlType === 'BLOCK' || row.controlType === 'HARD' ? 'danger' : 'warning'">{{ CONTROL_TEXT[row.controlType] || row.controlType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效期" width="190">
          <template #default="{ row }">
            <span :class="{ 'expiring': expiringSoon(row) }">{{ (row.effectDate || '').slice(0, 10) || '长期' }} ~ {{ (row.expireDate || '').slice(0, 10) || '长期' }}</span>
            <el-tag v-if="expired(row)" size="small" type="info" style="margin-left:4px">已失效</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="70" align="center" sortable />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'info'">{{ row.status === 'NORMAL' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link size="small" type="danger" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:10px; justify-content:flex-end"
        v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="() => { pageNum = 1; load() }" @current-change="load" />
    </el-card>

    <!-- 新增/编辑标准 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑报销标准' : '新增报销标准'" width="580px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px" size="small">
        <div class="form-row">
          <el-form-item label="标准编码" prop="stdCode" class="half"><el-input v-model="form.stdCode" :disabled="!!form.id" /></el-form-item>
          <el-form-item label="标准名称" prop="stdName" class="half"><el-input v-model="form.stdName" placeholder="如:住宿-总监级-一线城市" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="费用类型" prop="itemId" class="half">
            <el-select v-model="form.itemId" filterable style="width:100%" placeholder="选择费用类型">
              <el-option v-for="i in itemOptions" :key="i.id" :label="i.name" :value="i.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="适用组织" class="half">
            <el-select v-model="form.companyId" clearable filterable style="width:100%" placeholder="空=全组织">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="岗位职级下限" class="half"><el-input-number v-model="form.postLevelMin" :min="0" style="width:100%" placeholder="可空" /></el-form-item>
          <el-form-item label="岗位职级上限" class="half"><el-input-number v-model="form.postLevelMax" :min="0" style="width:100%" placeholder="可空" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="城市/地区等级" class="half">
            <el-select v-model="form.cityLevel" clearable style="width:100%" placeholder="空=不限">
              <el-option v-for="(v, k) in CITY_LEVELS" :key="k" :label="v" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item label="标准类型" prop="stdType" class="half">
            <el-select v-model="form.stdType" style="width:100%">
              <el-option v-for="(v, k) in STD_TYPES" :key="k" :label="v" :value="k" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="标准金额" prop="amount" class="half"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
          <el-form-item label="币种" class="half">
            <el-select v-model="form.currencyCode" filterable style="width:100%">
              <el-option v-for="c in ['CNY', 'USD', 'EUR']" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="控制方式" prop="controlType" class="half">
            <el-radio-group v-model="form.controlType">
              <el-radio value="WARN">提示(可超)</el-radio>
              <el-radio value="BLOCK">硬控(拦截)</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="优先级" class="half"><el-input-number v-model="form.priority" :min="0" style="width:100%" /><div class="hint">数值越大越优先</div></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="生效日期" class="half"><el-date-picker v-model="form.effectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="失效日期" class="half"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 报销标准专属页(41号审查 主数据深化第一批)
 * 三维标准表:费用类型 × 岗位职级区间 × 城市等级 + 生效期/失效期 + 优先级 + 控制方式(提示/硬控),
 * 替代原 MasterConfigTable 通用 4 列表;失效标准自动着色标注。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { masterApi, orgApi } from '../../api'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'

const CITY_LEVELS = { CITY_1: '一线城市', CITY_2: '二线城市', CITY_3: '三线及以下', ABROAD_1: '海外一类', ABROAD_2: '海外二类' }
const STD_TYPES = { DAY: '每天限额', TIMES: '每次限额', MONTH: '每月限额', MEAL: '餐补/次', TRANSPORT: '交通/次' }
// 库内实值有两套码：种子写 HARD，界面单选写 BLOCK，语义同为「超限拦截」——数据侧规则不一致
const CONTROL_TEXT = { WARN: '提示', BLOCK: '硬控', HARD: '硬控' }

const rows = ref([])
const companies = ref([])
const itemOptions = ref([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const q = reactive({ cityLevel: '', itemId: null, keyword: '' })
const formVisible = ref(false)
const form = reactive({})
const formRef = ref(null)
const guard = useSubmitGuard(formRef)
const formRules = {
  stdCode: [{ required: true, message: '请输入标准编码' }],
  stdName: [{ required: true, message: '请输入标准名称' }],
  itemId: [{ required: true, message: '请选择费用类型' }],
  stdType: [{ required: true, message: '请选择标准类型' }],
  amount: [{ required: true, message: '请输入标准金额' }],
  controlType: [{ required: true, message: '请选择控制方式' }],
}

const itemMap = computed(() => Object.fromEntries(itemOptions.value.map((i) => [i.id, i.name])))

function itemName(id) {
  return id ? (itemMap.value[id] || `项目${id}`) : '不限'
}

function levelRange(row) {
  if (row.postLevelMin == null && row.postLevelMax == null) return '不限'
  return `${row.postLevelMin ?? 0} ~ ${row.postLevelMax ?? '∞'} 级`
}

function expired(row) {
  return row.expireDate && new Date(row.expireDate) < new Date()
}

function expiringSoon(row) {
  if (!row.expireDate || expired(row)) return false
  return new Date(row.expireDate) - new Date() < 90 * 86400000
}

function fmtMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const r = await masterApi.std.page({
      pageNum: pageNum.value, pageSize: pageSize.value,
      cityLevel: q.cityLevel || undefined, itemId: q.itemId || undefined, keyword: q.keyword || undefined,
    })
    rows.value = r.list || r.records || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadOptions() {
  try {
    const [items, ct] = await Promise.all([masterApi.item.tree(), orgApi.companyTree()])
    const walk = (arr) => (arr || []).flatMap((n) => [{ id: n.id, name: n.itemName || n.name || n.label }, ...walk(n.children)])
    itemOptions.value = walk(items)
    const flat = []
    const cw = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) cw(n.children) }) }
    cw(ct)
    companies.value = flat
  } catch { /* 选项加载失败不阻断 */ }
}

function search() { pageNum.value = 1; load() }

function openForm(row) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) Object.assign(form, row)
  else Object.assign(form, { stdType: 'DAY', currencyCode: 'CNY', controlType: 'WARN', priority: 0 })
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await guard.submit(async () => {
      if (form.id) await masterApi.std.update({ ...form })
      else await masterApi.std.create({ ...form })
      ElMessage.success('已保存')
      formVisible.value = false
      await load()
    })
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除标准 [${row.stdName}]?`, '删除', { type: 'warning' })
  try {
    await masterApi.std.delete(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

function doExport() {
  exportCsv(rows.value.map((r) => ({
    ...r,
    itemName: itemName(r.itemId),
    levelRange: levelRange(r),
    cityLevelText: CITY_LEVELS[r.cityLevel] || r.cityLevel || '不限',
    stdTypeText: STD_TYPES[r.stdType] || r.stdType,
    controlText: CONTROL_TEXT[r.controlType] || r.controlType,
    effectText: `${(r.effectDate || '').slice(0, 10) || '长期'} ~ ${(r.expireDate || '').slice(0, 10) || '长期'}`,
  })), [
    { prop: 'stdCode', label: '标准编码' }, { prop: 'stdName', label: '标准名称' }, { prop: 'itemName', label: '费用类型' },
    { prop: 'levelRange', label: '岗位职级区间' }, { prop: 'cityLevelText', label: '城市等级' },
    { prop: 'stdTypeText', label: '标准类型' }, { prop: 'amount', label: '标准金额' }, { prop: 'currencyCode', label: '币种' },
    { prop: 'controlText', label: '控制方式' }, { prop: 'effectText', label: '生效期' },
    { prop: 'priority', label: '优先级' }, { prop: 'status', label: '状态' }, { prop: 'remark', label: '备注' },
  ], 'ExpenseStandard')
}

onMounted(() => { load(); loadOptions() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.form-row { display: flex; gap: 12px; }
.half { flex: 1; }
.unit { font-size: 12px; color: #909399; margin-left: 2px; }
.hint { font-size: 12px; color: #c0c4cc; }
.expiring { color: #e6a23c; }
</style>
