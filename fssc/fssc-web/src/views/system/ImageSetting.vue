<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>系统设置</b>
        <el-radio-group v-model="tab" @change="load">
          <el-radio-button value="BIZ_TYPE">业务类型设置</el-radio-button>
          <el-radio-button value="FILE_KIND">文件种类识别设置</el-radio-button>
        </el-radio-group>
        <el-input v-model="kw" placeholder="编码/名称" clearable style="width: 180px" @keyup.enter="load" />
        <el-button type="primary" size="small" @click="load">查询</el-button>
        <!-- R5-D-251：写入口按 image:setting:update 前置隐藏（无权限时点了必 4030）；导出属读操作按 list -->
        <el-button v-if="canSettingUpdate" size="small" type="success" @click="openForm()">新增</el-button>
        <el-button v-if="canSettingList" size="small" @click="doExport">导出 CSV</el-button>
      </div>
    </template>

    <!-- R5-D-251：无 image:setting:list 时不渲染表格、也不发必 4030 的请求，直接给可行动空态 -->
    <template v-if="canSettingList">
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" stripe v-else max-height="480" empty-text="暂无数据">
      <el-table-column prop="settingCode" label="编码" width="140" />
      <el-table-column prop="settingName" label="名称" min-width="160" />
      <el-table-column prop="extInfo" label="扩展配置" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">{{ row.status === 'NORMAL' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column v-if="canSettingUpdate" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="openForm(row)">编辑</el-button>
          <el-button link size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="load" />
    </template>
    <el-empty v-else description="无权限查看镜像设置项（缺 image:setting:list），请联系管理员" />

    <el-dialog v-model="vis" :title="form.id ? '编辑设置' : '新增设置'" width="520px" destroy-on-close>
      <el-form :model="form" :rules="settingRules" label-width="90px">
        <el-form-item label="设置类型" prop="settingType"><el-input :model-value="tab === 'BIZ_TYPE' ? '业务类型' : '文件种类识别'" disabled /></el-form-item>
        <el-form-item label="编码" prop="settingCode" required><el-input v-model="form.settingCode" /></el-form-item>
        <el-form-item label="名称" prop="settingName" required><el-input v-model="form.settingName" /></el-form-item>
        <el-form-item label="扩展配置" prop="extInfo"><el-input v-model="form.extInfo" type="textarea" :rows="2" placeholder='JSON，如 {"recognize":"OCR"}' /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="NORMAL" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vis = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { imageApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { hasPerm } from '../../utils/perm'
import { exportCsv } from '../../utils/csv'

// R5-D-251：镜像设置项读写权限收口——此前页面无任何前端权限闸：
// 无 image:setting:list 的账号进来即连环弹 4030；无 update 的账号看得见新增/编辑/删除且点了必失败。
const canSettingList = hasPerm('image:setting:list')
const canSettingUpdate = hasPerm('image:setting:update')

const tab = ref('BIZ_TYPE')
const rows = ref([])
// P0-1: 本地分页（前端切片已拉取的全量设置项，避免一次性渲染全部行）
const pager = useTablePager({ mode: 'server', storageKey: 'system_image_setting' })
const { pagedData } = pager
const loading = ref(false)
const kw = ref('')
const vis = ref(false)
const form = reactive({ id: null, settingType: '', settingCode: '', settingName: '', extInfo: '', status: 'NORMAL', remark: '' })

const settingRules = reactive({
  settingCode: [{ required: true, message: '请输入编码' }],
  settingName: [{ required: true, message: '请输入名称' }],
})

// R5-D-251：同一文案 3 秒内只弹一次（切 Tab/翻页/连续查询时同一个错误会连环弹 toast）
let lastErrMsg = ''
let lastErrAt = 0
function toastOnce(msg) {
  const now = Date.now()
  if (msg === lastErrMsg && now - lastErrAt < 3000) return
  lastErrMsg = msg
  lastErrAt = now
  ElMessage.error(msg)
}

async function load() {
  // R5-D-251：无 list 权限时不发请求（服务端必 4030），直接给空态
  if (!canSettingList) { rows.value = []; return }
  loading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await imageApi.settingPage({ pageNum, pageSize, settingType: tab.value, keyword: kw.value })
      rows.value = r.list || []
      return { list: rows.value, total: r.total || rows.value.length }
    })
  } catch (e) { toastOnce(e?.message || '加载失败') } finally { loading.value = false }
}

function openForm(row) {
  Object.assign(form, row ? { ...row } : { id: null, settingType: tab.value, settingCode: '', settingName: '', extInfo: '', status: 'NORMAL', remark: '' })
  form.settingType = tab.value
  vis.value = true
}

// 41号审查 P1-9：导出当前 Tab 设置项
function doExport() {
  exportCsv(rows.value, [
    { prop: 'settingType', label: '设置类型' }, { prop: 'settingCode', label: '编码' },
    { prop: 'settingName', label: '名称' }, { prop: 'extInfo', label: '扩展配置' },
    { prop: 'status', label: '状态' }, { prop: 'remark', label: '备注' },
  ], 'ImageSetting_' + tab.value)
}

// R5-D-249：系统列（审计/租户/逻辑删）不回传，避免旧行回显触发后端填充跳过或入参伪造
const SYSTEM_COLS = ['createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag', 'tenantId']
function stripSystemCols(o) {
  const c = { ...o }
  SYSTEM_COLS.forEach((k) => delete c[k])
  return c
}

async function save() {
  try {
    if (form.id) await imageApi.settingUpdate(stripSystemCols(form))
    else await imageApi.settingCreate(stripSystemCols(form))
    ElMessage.success('已保存')
    vis.value = false
    load()
  } catch (e) { ElMessage.error(e.message) }
}

async function del(row) {
  await ElMessageBox.confirm(`删除设置 ${row.settingName}？`, '删除', { type: 'warning' })
    .then(async () => {
      await imageApi.settingDelete(row.id)
      ElMessage.success('已删除')
      load()
    }).catch(() => {})
}

onMounted(load)
</script>
