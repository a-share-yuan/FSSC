// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useCrud — 通用 CRUD composable（P2-2 交互一致性）。
 * 消除 26 个自造 CRUD 页面重复的 load/create/update/delete/export/batch 逻辑。
 *
 * 用法：
 *   const crud = useCrud({
 *     api: customerApi,        // 含 page/create/update/delete/enable
 *     defaultForm: () => ({ customerCode: '', customerName: '', ... }),
 *     csvColumns: [{ prop: 'customerCode', label: '编码' }, ...],
 *     csvName: '客户',
 *     formRef,                 // el-form 的 ref；传入后 save() 先跑必填校验，不过就不发请求
 *   })
 *   // crud.rows, crud.loading, crud.selRows, crud.vis, crud.form
 *   // crud.load(), crud.openCreate(), crud.openEdit(row), crud.save(),
 *   // crud.del(row), crud.toggle(row), crud.exportCsv(), crud.batchExport()
 */

import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportCsv, exportBatchCsv } from '../utils/csv'
import { useSubmitGuard } from './useSubmitGuard'

export function useCrud(opts) {
  const { api, defaultForm, csvColumns = [], csvName = 'export', formRef = null } = opts
  const guard = useSubmitGuard(formRef)

  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const selRows = ref([])
  const vis = ref(false)
  const form = reactive(defaultForm ? defaultForm() : {})

  // 搜索参数（页面可覆盖）
  const query = reactive({ pageNum: 1, pageSize: 50, keyword: '' })

  async function load() {
    loading.value = true
    try {
      const r = await api.page({ ...query })
      rows.value = r?.list || r?.records || (Array.isArray(r) ? r : [])
      total.value = r?.total || 0
    } catch (e) {
      ElMessage.error(e.message)
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    if (defaultForm) Object.assign(form, defaultForm())
    else Object.keys(form).forEach((k) => (form[k] = null))
    vis.value = true
  }

  function openEdit(row) {
    Object.assign(form, { ...row })
    vis.value = true
  }

  async function save() {
    try {
      await guard.submit(async () => {
        if (form.id) await api.update(form)
        else {
          const id = await api.create(form)
          form.id = id
        }
        ElMessage.success('已保存')
        vis.value = false
        await load()
      })
    } catch (e) {
      ElMessage.error(e.message)
    }
  }

  async function del(row, confirmMsg = '确认删除？') {
    try {
      await ElMessageBox.confirm(confirmMsg, '删除', { type: 'warning' })
      await api.delete(row.id)
      ElMessage.success('已删除')
      await load()
    } catch (e) {
      if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
    }
  }

  async function toggle(row) {
    try {
      // api.enable(id, enable) 的第二个参数是「目标状态」而不是「当前是否启用」：原写法把
      // status==='NORMAL' 直接传进去，启用行传 true、停用行传 false ⇒ 两侧都写回原值，按钮点了不变。
      // ACTIVE/ENABLED 是同类启用码（客户核准写 ACTIVE），一并视为已启用。
      const active = ['NORMAL', 'ACTIVE', 'ENABLED'].includes(row.status)
      await api.enable(row.id, !active)
      ElMessage.success('已更新')
      await load()
    } catch (e) {
      ElMessage.error(e.message)
    }
  }

  function handleSelectionChange(val) {
    selRows.value = val
  }

  function exportCsvAll() {
    exportCsv(rows.value, csvColumns, csvName)
  }

  function exportBatch() {
    exportBatchCsv(selRows.value, csvName)
  }

  return {
    rows,
    total,
    loading,
    selRows,
    vis,
    form,
    query,
    load,
    openCreate,
    openEdit,
    save,
    del,
    toggle,
    handleSelectionChange,
    exportCsv: exportCsvAll,
    batchExport: exportBatch,
  }
}
