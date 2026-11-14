<template>
  <div class="fb-page">
    <el-card shadow="never" class="fb-card">
      <template #header>
        <div class="fb-head">
          <div class="fb-head__left">
            <span class="fb-title">{{ formName || '单据' }}</span>
            <el-tag v-if="billNo" size="small" type="info">{{ billNo }}</el-tag>
            <el-tag v-if="status" size="small" :type="statusType">{{ statusText }}</el-tag>
          </div>
          <div class="fb-head__right">
            <el-button @click="goBack">返回</el-button>
            <el-button v-if="canEdit" type="primary" plain :loading="saving" @click="onSave">保存草稿</el-button>
            <el-button v-if="canEdit" type="primary" :loading="submitting" @click="onSubmit">提交审批</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <DynamicForm
          ref="dfRef"
          :form-code="formCode"
          :model-value="model"
          :readonly="!canEdit"
          label-width="130px"
          @update:model-value="onModelUpdate"
          @loaded="onMetaLoaded"
        />
      </div>

      <el-alert
        v-if="!loading && !canEdit && status"
        class="fb-alert"
        type="info"
        :closable="false"
        :title="readonlyHint"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DynamicForm from '../../components/form/DynamicForm.vue'
import { formApi } from '../../api'

const route = useRoute()
const router = useRouter()

const dfRef = ref(null)
const formCode = computed(() => String(route.params.formCode || ''))
const billId = computed(() => (route.query.id ? Number(route.query.id) : null))

const model = ref({})
const meta = ref({})
const detail = ref({})
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)

const formName = computed(() => detail.value.formName || meta.value.formName || '')
const billNo = computed(() => detail.value.billNo || '')
const status = computed(() => detail.value.status || (billId.value ? '' : 'DRAFT'))

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
const statusText = computed(() => STATUS_TEXT[status.value] || status.value)
const statusType = computed(() => STATUS_TYPE[status.value] || 'info')

/** 只有草稿与驳回件可编辑；审批中与已办结一律只读 */
const canEdit = computed(() => {
  if (!status.value) return true
  if (status.value === 'DRAFT' || status.value === 'REJECTED') {
    return detail.value.editable !== false
  }
  return false
})

const readonlyHint = computed(() => {
  if (status.value === 'APPROVING') return '单据正在审批中，内容不可修改'
  if (status.value === 'APPROVED') return '单据已审批通过，仅可查看'
  if (status.value === 'REJECTED') return '单据已驳回，可修改后重新提交'
  if (status.value === 'CANCELED') return '单据已撤销，仅可查看'
  return '当前状态不可编辑'
})

function onMetaLoaded(m) {
  meta.value = m || {}
}

function onModelUpdate(v) {
  model.value = v
}

async function loadDetail() {
  if (!billId.value) {
    model.value = {}
    detail.value = {}
    return
  }
  loading.value = true
  try {
    const res = await formApi.detail(billId.value)
    detail.value = res || {}
    model.value = detail.value.data || {}
  } finally {
    loading.value = false
  }
}

/** 先跑前端提示层校验，再让服务端按元数据做权威校验（保存接口内部已做） */
async function validateLocal() {
  if (!dfRef.value || !canEdit.value) return true
  try {
    await dfRef.value.validate()
    return true
  } catch (e) {
    ElMessage.warning('请先补全必填项')
    return false
  }
}

async function onSave() {
  if (!(await validateLocal())) return
  saving.value = true
  try {
    const res = await formApi.save(formCode.value, billId.value, model.value)
    const id = res
    if (id && !billId.value) {
      await router.replace({ path: route.path, query: { ...route.query, id } })
      await loadDetail()
    }
    ElMessage.success('已保存草稿')
  } finally {
    saving.value = false
  }
}

async function onSubmit() {
  if (!(await validateLocal())) return
  submitting.value = true
  try {
    const saved = await formApi.save(formCode.value, billId.value, model.value)
    const id = saved || billId.value
    if (!id) {
      ElMessage.error('保存失败，未能取得单据编号')
      return
    }
    await formApi.submit(id)
    ElMessage.success('已提交审批')
    await router.replace({ path: route.path, query: { ...route.query, id } })
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/form/my')
}

onMounted(loadDetail)
watch(() => route.query.id, loadDetail)
</script>

<style scoped>
.fb-page {
  padding: 4px;
}
.fb-card {
  border-radius: 8px;
}
.fb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.fb-head__left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.fb-title {
  font-size: 15px;
  font-weight: 500;
}
.fb-alert {
  margin-top: 12px;
}
</style>
