<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-button type="primary" size="small" @click="crud.openCreate()">新增客户</el-button>
      <el-input v-model="crud.query.keyword" placeholder="搜索编码/名称/联系人" style="width: 240px" @keyup.enter="crud.load()" />
      <el-button size="small" @click="crud.load()">查询</el-button>
      <el-button size="small" @click="crud.query.keyword = ''; crud.load()">重置</el-button>
      <el-button size="small" @click="crud.exportCsv()">导出 CSV</el-button>
      <el-button v-if="crud.selRows.value.length" type="success" plain size="small" @click="crud.batchExport()">导出选中({{ crud.selRows.value.length }})</el-button>
    </div>
    <SkeletonList v-if="crud.loading.value" :rows="8" :cols="5" />
    <el-table :data="crud.rows.value" size="small" stripe border v-else v-loading="crud.loading.value" @selection-change="crud.handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="customerCode" label="编码" width="120"  sortable />
      <el-table-column prop="customerName" label="名称"  min-width="200" show-overflow-tooltip sortable />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ customerTypeLabel(row.customerType) }}</template>
      </el-table-column>
      <el-table-column prop="contactName" label="联系人" width="100"  sortable />
      <el-table-column prop="contactPhone" label="电话" width="130"  sortable />
      <el-table-column prop="creditLevel" label="信用" width="80"  sortable />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 'NORMAL' ? 'success' : (row.status === 'PENDING' ? 'warning' : 'info')" size="small">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link size="small" type="success" @click="approveCustomer(row)">核准</el-button>
          <el-button v-permission="'customer:update'" link size="small" @click="crud.openEdit(row)">编辑</el-button>
          <el-button v-permission="'customer:update'" link size="small" :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="crud.toggle(row)">{{ row.status === 'NORMAL' ? '停用' : '启用' }}</el-button>
          <el-button link size="small" type="danger" @click="crud.del(row, '确认删除该客户？')">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="crud.query.pageNum"
      v-model:page-size="crud.query.pageSize"
      :total="crud.total.value"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end;"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />

    <el-dialog v-model="crud.vis.value" :title="crud.form.id ? '编辑客户' : '新增客户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="crud.form" :rules="customerRules" label-width="90px" size="default">
        <el-form-item label="客户编码"><el-input v-model="crud.form.customerCode" readonly placeholder="保存后由系统自动生成" /></el-form-item>
        <el-form-item label="客户名称" prop="customerName"><el-input v-model="crud.form.customerName" /></el-form-item>
        <el-form-item label="往来类型" prop="customerType">
          <el-select v-model="crud.form.customerType" style="width: 100%">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="供应商" value="SUPPLIER" />
            <el-option label="服务商" value="VENDOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="crud.form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="crud.form.contactPhone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="crud.form.email" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="crud.form.address" /></el-form-item>
        <el-form-item label="信用等级">
          <el-select v-model="crud.form.creditLevel" style="width: 120px">
            <el-option label="A" value="A" /><el-option label="B" value="B" /><el-option label="C" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="crud.form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="crud.vis.value = false">取消</el-button>
        <el-button v-permission="['customer:create', 'customer:update']" type="primary" @click="crud.save()">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { customerApi } from '../../api'
import { useCrud } from '../../composables/useCrud'
import { useFormRules } from '../../composables/useFormRules'
import SkeletonList from '../../components/SkeletonList.vue'
import { customerTypeLabel } from '@/constants/enums.js'

const formRef = ref(null)

const crud = useCrud({
  api: customerApi,
  formRef,
  defaultForm: () => ({ id: null, customerCode: '', customerName: '', customerType: 'CUSTOMER', contactName: '', contactPhone: '', email: '', address: '', creditLevel: 'A', status: 'NORMAL', remark: '' }),
  csvColumns: [
    { prop: 'customerCode', label: '编码' },
    { prop: 'customerName', label: '名称' },
    { prop: 'customerType', label: '类型' },
    { prop: 'contactName', label: '联系人' },
    { prop: 'contactPhone', label: '电话' },
    { prop: 'creditLevel', label: '信用' },
    { prop: 'status', label: '状态' },
  ],
  csvName: '客户',
})

function statusLabel(s) {
  return { NORMAL: '正常', PENDING: '未核准', DISABLED: '已停用' }[s] || s || ''
}

async function approveCustomer(row) {
  try {
    await customerApi.approve(row.id)
    ElMessage.success('已核准（ACTIVE）')
    crud.load()
  } catch (e) { ElMessage.error(e?.message || '核准失败') }
}

function handleSizeChange(val) {
  crud.query.pageSize = val
  crud.query.pageNum = 1
  crud.load()
}

function handleCurrentChange(val) {
  crud.query.pageNum = val
  crud.load()
}

// 第1批 P0：录入规则统一走中央框架 useFormRules('CUSTOMER')
// （原页面本地 rules 只有名称/类型必填，联系电话与邮箱裸奔）
const { rules: customerRules } = useFormRules('CUSTOMER')

onMounted(crud.load)
</script>
