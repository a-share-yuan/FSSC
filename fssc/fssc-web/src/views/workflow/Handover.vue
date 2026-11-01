<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="handover-page">
    <el-card shadow="never">
      <template #header>
        <span class="header-title">流程移交</span>
        <span class="header-note">员工离职/换岗时把名下在办审批任务批量交给接手人；逐单独立成败，失败明细照实返回。</span>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 1. 批量移交 -->
        <el-tab-pane label="批量移交" name="execute">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
            title="「指定任务」与「原办理人」二选一：都不选会报错。选定任务＝逐单移交；只选「原办理人」＝移交其名下全部在办任务。操作人取当前登录用户。" />
          <el-form ref="handoverFormRef" :model="form" :rules="formRules" label-width="130px" size="small" style="max-width: 620px">
            <el-form-item label="接收人" prop="toUserId">
              <!-- 默认只加载同公司人员 -->
              <UserSelect v-model="form.toUserId" scope="same-company" placeholder="请选择接手人" />
            </el-form-item>
            <el-form-item label="原办理人">
              <UserSelect v-model="form.fromUserId" scope="same-company" placeholder="按其在办任务批量移交（可选）" />
            </el-form-item>
            <el-form-item label="指定任务">
              <el-select v-model="form.taskIds" multiple filterable remote reserve-keyword clearable
                         :remote-method="searchTasks" :loading="taskLoading"
                         :placeholder="form.fromUserId ? '不选则移交该办理人全部在办任务' : '按单号/标题搜索在办任务（可多选）'"
                         style="width: 100%">
                <el-option v-for="t in taskOptions" :key="t.taskId" :label="taskLabel(t)" :value="t.taskId" />
              </el-select>
            </el-form-item>
            <el-form-item label="移交原因" prop="reason">
              <el-input v-model="form.reason" placeholder="离职 / 换岗 / 工作交接等" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="['wf:manage:intervene', 'wf:task:reassign']" type="primary" :loading="execLoading" @click="doExecute">执行移交</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="execResult" :column="4" border size="small" style="margin-top: 10px">
            <el-descriptions-item label="原办理人">{{ execResult.fromUserName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接收人">{{ execResult.toUserName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="请求条数">{{ execResult.requested ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="成功/失败">{{ execResult.successCount ?? 0 }} / {{ execResult.failCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="说明" :span="4">{{ execResult.note || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="execResult?.failed?.length" :data="execResult.failed" size="small" border max-height="260" style="margin-top: 10px">
            <el-table-column prop="taskId" label="任务ID" width="180" />
            <el-table-column prop="reason" label="失败原因" min-width="240" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- 2. 移交日志 -->
        <el-tab-pane label="移交日志" name="log">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item label="原办理人">
              <div style="width: 180px"><UserSelect v-model="logQ.fromUserId" scope="same-company" placeholder="全部" /></div>
            </el-form-item>
            <el-form-item label="接收人">
              <div style="width: 180px"><UserSelect v-model="logQ.toUserId" scope="same-company" placeholder="全部" /></div>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="logReset">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="logRows" v-loading="logLoading" size="small" stripe border max-height="480" empty-text="暂无移交记录">
            <el-table-column prop="fromUserName" label="原办理人" width="110" />
            <el-table-column prop="toUserName" label="接收人" width="110" />
            <el-table-column prop="taskCount" label="请求" width="70" align="right" />
            <el-table-column prop="successCount" label="成功" width="70" align="right" />
            <el-table-column prop="failCount" label="失败" width="70" align="right" />
            <el-table-column prop="reason" label="移交原因" min-width="150" show-overflow-tooltip />
            <el-table-column prop="operUserName" label="操作人" width="110" />
            <el-table-column prop="operTime" label="操作时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column prop="failedDetail" label="失败明细" min-width="160" show-overflow-tooltip />
          </el-table>
          <TablePager :pager="logPager" @change="loadLog" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { wfOpsApi, flowApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime } from '@/utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'

/**
 * 流程批量移交（G3）：数据源 WfHandoverController（/api/tenant/wf/handover/**）。
 * execute 需 wf:manage:intervene 或 wf:task:reassign；日志读视图接受 wf:manage:instance 或 wf:instance:list。
 */
const tab = ref('execute')
const loadedTabs = new Set()

const handoverFormRef = ref(null)
const form = reactive({ toUserId: null, fromUserId: null, taskIds: [], reason: '', remark: '' })
const formRules = reactive({
  toUserId: commonRules.select('请选择接收人'),
  reason: commonRules.required('请输入移交原因'),
})
const execLoading = ref(false)
const execResult = ref(null)

/** 在办任务候选（单号/标题远程检索）：选定即按该任务移交给接收人 */
const taskOptions = ref([])
const taskLoading = ref(false)
async function searchTasks(keyword) {
  taskLoading.value = true
  try {
    const r = await flowApi.taskPage({
      pageNum: 1, pageSize: 20, status: 'PENDING',
      assigneeId: form.fromUserId || undefined,
      keyword: keyword || undefined,
    })
    taskOptions.value = r?.list || []
  } catch { taskOptions.value = [] } finally { taskLoading.value = false }
}
function taskLabel(t) {
  return [t.bizNo || t.title || '', t.nodeName, t.assigneeName].filter(Boolean).join(' · ') || t.taskId
}
// 换了原办理人，之前挑的任务已不属于他，清空重拉
watch(() => form.fromUserId, () => { form.taskIds = []; searchTasks('') })

async function doExecute() {
  if (!(await validateForms([handoverFormRef]))) return
  const taskIds = form.taskIds || []
  if (!taskIds.length && !form.fromUserId) {
    ElMessage.warning('请选择「指定任务」或「原办理人」之一')
    return
  }
  execLoading.value = true
  try {
    execResult.value = await wfOpsApi.handoverExecute({
      toUserId: form.toUserId,
      fromUserId: form.fromUserId || null,
      taskIds: taskIds.length ? taskIds : null,
      reason: form.reason,
      remark: form.remark || null,
    })
    ElMessage.success(`移交完成：成功 ${execResult.value?.successCount ?? 0} 条，失败 ${execResult.value?.failCount ?? 0} 条`)
    if (loadedTabs.has('log')) loadLog()
  } catch (e) { ElMessage.error(e.message) } finally { execLoading.value = false }
}

const logQ = reactive({ fromUserId: null, toUserId: null })
const logPager = useTablePager({ mode: 'server', storageKey: 'wf_handover_log' })
const { rows: logRows, loading: logLoading } = logPager

function logReset() { logPager.resetPage(); loadLog() }
async function loadLog() {
  await logPager.load(async (p) => {
    const r = await wfOpsApi.handoverLogPage({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      fromUserId: logQ.fromUserId || undefined,
      toUserId: logQ.toUserId || undefined,
    })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}

function onTabChange(name) {
  if (loadedTabs.has(name)) return
  loadedTabs.add(name)
  if (name === 'log') loadLog()
}

onMounted(() => { loadedTabs.add('execute'); searchTasks('') })
</script>

<style scoped>
.header-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); margin-right: 10px; }
.header-note { font-size: 12px; color: #909399; }
</style>