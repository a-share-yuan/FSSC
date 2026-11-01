<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="wf-eac-page">
    <el-card shadow="never">
      <template #header>
        <div class="eac-header">
          <div class="eac-title-wrap">
            <span class="eac-title">流程收尾动作配置</span>
            <span class="eac-sub">审批办结后的收尾动作按配置表驱动（业务类型 → 收尾动作）· 本页提供只读清单与有限编辑</span>
          </div>
          <div class="eac-tools">
            <el-button size="small" @click="reloadAll">刷新</el-button>
            <el-button size="small" type="primary" plain :loading="snapLoading" @click="openSnapshot">运行期生效快照</el-button>
          </div>
        </div>
      </template>

      <!-- 能力边界要写在脸上：本面刻意不含新增/删除，也不碰决定引擎走向的四列 -->
      <el-alert type="info" :closable="false" show-icon class="eac-alert"
                title="可编辑范围只有「启用状态」和「配置说明」两项"
                description="业务类型、收尾动作、触发时机、执行顺序决定流程办结后跑哪些收尾动作，改动需走评审，本页不提供新增/删除，也不开放这四列；配置行由引擎在应用启动时一次性载入，改完需重启应用才会进入运行期，可用右上角「运行期生效快照」核对当前真正生效的结果。" />
      <el-alert v-if="!canUpdate" type="warning" :closable="false" show-icon class="eac-alert"
                title="当前账号只能查看，不能修改"
                :description="'本页读权限 ' + PERM_LIST + '、写权限 ' + PERM_UPDATE + '；如需启停某条收尾动作，请联系数据配置管理员或流程管理员。'" />

      <el-form :inline="true" size="small" class="eac-filter">
        <el-form-item label="业务类型">
          <el-select v-model="q.bizType" clearable filterable placeholder="全部" style="width: 180px" @change="resetAndLoad">
            <el-option v-for="b in options.bizTypes" :key="b" :label="bizTypeText(b)" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="收尾动作">
          <el-select v-model="q.actionBean" clearable filterable placeholder="全部" style="width: 200px" @change="resetAndLoad">
            <el-option v-for="b in options.actionBeans" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.enabled" clearable placeholder="全部" style="width: 110px" @change="resetAndLoad">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="q.keyword" clearable placeholder="业务类型 / 收尾动作 / 说明" style="width: 210px" @keyup.enter="resetAndLoad" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndLoad">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="eac-summary">
        <span>当前账套可见 {{ options.total || 0 }} 条配置，其中启用 {{ options.enabledCount || 0 }} 条</span>
        <span class="eac-tip">包含当前账套的自有配置与平台预置配置，两者均会生效</span>
      </div>

      <el-table :data="rows" v-loading="loading" size="small" stripe border max-height="520"
                empty-text="没有符合条件的收尾动作配置" @row-dblclick="openRowDetail">
        <el-table-column label="业务类型" width="190">
          <template #default="{ row }">
            <span>{{ bizTypeText(row.bizType) }}</span>
            <div class="eac-code">{{ row.bizType }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="actionBean" label="收尾动作" min-width="180" show-overflow-tooltip />
        <el-table-column label="触发时机" width="130">
          <template #default="{ row }">{{ triggerWhenText(row.triggerWhen) }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="执行顺序" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="isEnabled(row) ? 'success' : 'danger'">{{ isEnabled(row) ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="isSeed(row) ? 'warning' : 'info'">{{ isSeed(row) ? '平台预置' : '本账套' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="配置说明" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="最后修改" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="!canUpdate" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager :pager="pager" @change="load" />
    </el-card>

    <!-- 编辑弹窗：字段级白名单，只有启用状态与配置说明可写 -->
    <el-dialog v-model="edit.visible" title="编辑收尾动作配置" width="620px" destroy-on-close>
      <el-form ref="editFormRef" :model="edit.form" :rules="editRules" label-width="100px" size="default">
        <el-form-item label="业务类型">
          <span class="eac-readonly">{{ bizTypeText(edit.row.bizType) }}（{{ edit.row.bizType }}）</span>
        </el-form-item>
        <el-form-item label="收尾动作">
          <span class="eac-readonly">{{ edit.row.actionBean }}</span>
        </el-form-item>
        <el-form-item label="触发时机">
          <span class="eac-readonly">{{ triggerWhenText(edit.row.triggerWhen) }}</span>
        </el-form-item>
        <el-form-item label="执行顺序">
          <span class="eac-readonly">{{ edit.row.sortOrder }}</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="edit.form.enabled" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="停用" />
          <div class="eac-hint">停用后该行动作不再被分发（引擎只加载启用行），但需应用重启才会反映到运行期；同一业务类型若还有其它启用行，收尾动作照常执行。</div>
        </el-form-item>
        <el-form-item label="配置说明" prop="description">
          <el-input v-model="edit.form.description" type="textarea" :rows="3" maxlength="256" show-word-limit
                    placeholder="这条配置是给谁用的、改它要注意什么" />
          <div class="eac-hint">仅备注性质，不参与引擎判定；清空即不保留说明。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="edit.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行期生效快照：引擎此刻真正在跑的分发结果（只读投影） -->
    <el-dialog v-model="snap.visible" title="运行期生效快照" width="820px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
                :title="`引擎当前已装配 ${snap.data?.bizTypeCount || 0} 个业务类型、${snap.data?.actionCount || 0} 条收尾动作`"
        :description="snap.data?.note || '取自应用启动时构建的分发映射。'" />
      <el-table :data="snap.data?.bizTypes || []" size="small" stripe max-height="420"
                empty-text="引擎未装配任何收尾动作">
        <el-table-column label="业务类型" width="200">
          <template #default="{ row }">{{ bizTypeText(row.bizType) }}（{{ row.bizType }}）</template>
        </el-table-column>
        <el-table-column prop="actionCount" label="动作数" width="80" align="center" />
        <el-table-column label="生效动作（按执行顺序）" min-width="360">
          <template #default="{ row }">
            <span v-for="(a, i) in row.actions" :key="i" class="eac-chain">
              <span v-if="i > 0"> → </span>{{ a.actionBean }}<span class="eac-code-inline">（{{ triggerWhenText(a.triggerWhen) }}，序{{ a.sortOrder }}）</span>
            </span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button size="small" :loading="snapLoading" @click="loadSnapshot">重新加载</el-button>
        <el-button size="small" type="primary" @click="snap.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 流程收尾动作配置管理面（wf_event_action_config）。
 * 表原本只有引擎读环节、没有任何 Controller 与页面，配置只能靠 SQL 手改。
 * 本页＝只读清单（可按状态/收尾动作/业务类型筛）＋字段级有限编辑（启用状态、配置说明）
 * ＋运行期生效快照（引擎启动时构建的分发映射投影）。
 * 不提供新增/删除，也不开放业务类型、收尾动作、触发时机、执行顺序四列——它们决定引擎走向，属评审面。
 */
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { commonRules, validateForm } from '../../composables/useFormRules'
import { wfEventActionApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { bizTypeLabel } from '@/constants/enums.js'
import { formatDateTime, showRowDetail } from '@/utils/format'
import { hasPerm } from '@/utils/perm'

const PERM_LIST = 'wf:config:list'
const PERM_UPDATE = 'wf:config:update'
const canUpdate = hasPerm(PERM_UPDATE)

// 触发时机直接显示英文码不可读，页内自建小映射（枚举主名称表由主窗在管，不改它）
const TRIGGER_WHEN_TEXT = { APPROVED: '仅审批通过', REJECTED: '仅审批驳回', BOTH: '通过与驳回均触发' }
function triggerWhenText(v) {
  const key = String(v == null ? '' : v).trim().toUpperCase()
  return TRIGGER_WHEN_TEXT[key] || (key || '—')
}
/** 业务类型：命中全局名称表出中文，未命中回原码（引擎规则以库为准） */
function bizTypeText(v) {
  if (!v) return '—'
  const label = bizTypeLabel(v)
  return label && label !== v ? label : v
}
function isEnabled(row) { return Number(row?.enabled) === 1 }
/** tenant_id=0 为历史全平台种子行（引擎按租户忽略模式一并加载），页面单列标出归属 */
function isSeed(row) { return Number(row?.tenantId) === 0 }

const q = reactive({ bizType: '', actionBean: '', enabled: null, keyword: '' })
const pager = useTablePager({ mode: 'server', storageKey: 'wf_event_action_config' })
const { rows, loading } = pager
const options = reactive({ bizTypes: [], actionBeans: [], total: 0, enabledCount: 0 })

async function load() {
  await pager.load(async (p) => {
    const r = await wfEventActionApi.page({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      bizType: q.bizType || undefined,
      actionBean: q.actionBean || undefined,
      // 0 是"停用"这个有效筛选值，不能按 falsy 丢掉；非 0/1 一律视为未选
      enabled: (q.enabled === 0 || q.enabled === 1) ? q.enabled : undefined,
      keyword: q.keyword || undefined,
    })
    return { list: r?.list || r?.records || [], total: r?.total || 0 }
  })
}

async function loadOptions() {
  try {
    const r = await wfEventActionApi.options()
    options.bizTypes = r?.bizTypes || []
    options.actionBeans = r?.actionBeans || []
    options.total = r?.total || 0
    options.enabledCount = r?.enabledCount || 0
  } catch (e) { /* 选项字典失败不阻断清单：筛选项退化为空，列表仍可翻页 */ }
}

function resetAndLoad() { pager.resetPage(); load(); loadOptions() }
function resetQuery() {
  Object.assign(q, { bizType: '', actionBean: '', enabled: null, keyword: '' })
  resetAndLoad()
}
function reloadAll() {
  load()
  loadOptions()
  if (snap.visible) loadSnapshot()
}

function openRowDetail(row) { return showRowDetail(row, { title: '收尾动作配置详情' }) }

// 编辑
const edit = reactive({ visible: false, saving: false, row: {}, form: { enabled: 1, description: '' } })
// 编辑弹窗接入——配置说明 256 字兜底；启用状态恒有值（1/0）不建规则
const editFormRef = ref(null)
const editRules = { description: commonRules.maxLength(256, '配置说明最多 256 个字符') }

async function openEdit(row) {
  edit.row = { ...row }
  edit.form = { enabled: isEnabled(row) ? 1 : 0, description: row.description || '' }
  edit.visible = true
  // 详情接口回读一次：清单可能停留在原先的旧值上，编辑前以库内为准
  try {
    const fresh = await wfEventActionApi.detail(row.id)
    if (fresh) {
      edit.row = { ...fresh }
      edit.form = { enabled: isEnabled(fresh) ? 1 : 0, description: fresh.description || '' }
    }
  } catch (e) { /* 回读失败仍可用行内值编辑，保存由后端按 ID + 归属再校验一次 */ }
}

async function saveEdit() {
  edit.saving = true
  try {
    // 配置说明长度兜底（模板 maxlength 只限制输入，绕过 DOM 直接赋值可超长入库）
    if (!(await validateForm(editFormRef.value))) return
    await wfEventActionApi.update({
      id: edit.row.id,
      enabled: edit.form.enabled,
      description: edit.form.description == null ? '' : String(edit.form.description),
    })
    ElMessage.success('已保存（运行期需应用重启后生效）')
    edit.visible = false
    await load()
    await loadOptions()
  } catch (e) { /* 错误文案已由请求拦截器统一提示 */ } finally { edit.saving = false }
}

// 运行期快照
const snapLoading = ref(false)
const snap = reactive({ visible: false, data: null })

async function loadSnapshot() {
  snapLoading.value = true
  try {
    snap.data = await wfEventActionApi.runtimeSnapshot()
  } catch (e) {
    ElMessage.error(e.message || '读取运行期快照失败')
    snap.data = null
  } finally { snapLoading.value = false }
}
function openSnapshot() {
  snap.visible = true
  loadSnapshot()
}

onMounted(() => { load(); loadOptions() })
</script>

<style scoped>
.wf-eac-page { min-height: 100%; }
.eac-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.eac-title-wrap { display: flex; flex-direction: column; gap: 4px; }
.eac-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); }
.eac-sub { font-size: 12px; color: #909399; }
.eac-tools { display: flex; align-items: center; gap: 8px; flex: none; }
.eac-alert { margin-bottom: 10px; }
.eac-filter { margin-bottom: 4px; }
.eac-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; font-size: 12px; color: var(--app-text-secondary, #606266); margin: 0 0 8px; }
.eac-tip { color: #909399; }
.eac-code { font-size: 11px; color: #a8abb2; line-height: 16px; }
.eac-code-inline { font-size: 11px; color: #909399; }
.eac-readonly { color: var(--app-text, #303133); }
.eac-hint { font-size: 12px; color: #909399; line-height: 18px; margin-top: 2px; }
.eac-chain { display: inline-block; margin-right: 4px; }
</style>
