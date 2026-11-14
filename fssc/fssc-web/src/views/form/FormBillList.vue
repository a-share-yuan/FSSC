<template>
  <div class="fl-page">
    <el-card shadow="never">
      <template #header>
        <div class="fl-head">
          <span class="fl-title">我的申请</span>
          <div class="fl-filter">
            <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="审批中" value="APPROVING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已撤销" value="CANCELED" />
            </el-select>
            <el-button :loading="loading" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" size="default" border>
        <el-table-column prop="billNo" label="单据编号" min-width="170" />
        <el-table-column prop="formCode" label="表单" min-width="130" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">
              {{ editable(row) ? '编辑' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有发起过单据" :image-size="80" />
        </template>
      </el-table>

      <el-pagination
        class="fl-pager"
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="onPage"
        @size-change="onSize"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { formApi } from '../../api'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: '', pageNum: 1, pageSize: 20 })

const STATUS_TEXT = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELED: '已撤销',
}
const STATUS_TYPE = {
  DRAFT: 'info',
  APPROVING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELED: 'info',
}
const statusText = (s) => STATUS_TEXT[s] || s
const statusType = (s) => STATUS_TYPE[s] || 'info'
const editable = (row) => row.status === 'DRAFT' || row.status === 'REJECTED'

async function load() {
  loading.value = true
  try {
    const res = await formApi.my({
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    const data = res || {}
    rows.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function onPage(p) {
  query.pageNum = p
  load()
}

function onSize(s) {
  query.pageSize = s
  query.pageNum = 1
  load()
}

function open(row) {
  router.push({ path: `/form/${row.formCode}`, query: { id: row.id } })
}

onMounted(load)
</script>

<style scoped>
.fl-page {
  padding: 4px;
}
.fl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.fl-title {
  font-size: 15px;
  font-weight: 500;
}
.fl-filter {
  display: flex;
  gap: 8px;
}
.fl-pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
