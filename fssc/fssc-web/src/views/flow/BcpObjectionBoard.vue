<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.bcpObjection.title') }}</span>
        <div class="ops">
          <el-select v-model="query.direction" size="small" style="width: 170px" @change="load">
            <el-option :label="$t('flow.bcpObjection.dirAll')" value="" />
            <el-option :label="$t('flow.bcpObjection.dirAp')" value="AP" />
            <el-option :label="$t('flow.bcpObjection.dirAr')" value="AR" />
          </el-select>
          <el-button size="small" type="primary" @click="load">{{ $t('common.search') }}</el-button>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
        </div>
      </div>
    </template>

    <el-alert :title="$t('flow.bcpObjection.tip')" type="info" :closable="false" show-icon style="margin-bottom: 10px" />

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="reconNo" :label="$t('flow.bcpObjection.colReconNo')" min-width="165" show-overflow-tooltip />
      <el-table-column prop="partnerId" :label="$t('flow.bcpObjection.colPartner')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="period" :label="$t('flow.bcpObjection.colPeriod')" width="90" />
      <el-table-column :label="$t('flow.bcpObjection.colDirection')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.direction === 'AR' ? 'warning' : 'primary'">
            {{ row.direction === 'AR' ? $t('flow.bcpObjection.dirAr') : $t('flow.bcpObjection.dirAp') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemType" :label="$t('flow.bcpObjection.colItemType')" min-width="100" show-overflow-tooltip />
      <el-table-column prop="sourceBillNo" :label="$t('flow.bcpObjection.colSourceBill')" min-width="150" show-overflow-tooltip />
      <el-table-column :label="$t('flow.bcpObjection.colOrigQty')" width="110" align="right">
        <template #default="{ row }">{{ fmtNum(row.origQuantity) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.bcpObjection.colReqQty')" width="110" align="right">
        <template #default="{ row }">
          <span :class="{ changed: isChanged(row.origQuantity, row.reqQuantity) }">{{ fmtNum(row.reqQuantity) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('flow.bcpObjection.colOrigPrice')" width="110" align="right">
        <template #default="{ row }">{{ fmtNum(row.origUnitPrice) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.bcpObjection.colReqPrice')" width="110" align="right">
        <template #default="{ row }">
          <span :class="{ changed: isChanged(row.origUnitPrice, row.reqUnitPrice) }">{{ fmtNum(row.reqUnitPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reason" :label="$t('flow.bcpObjection.colReason')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="raiseBy" :label="$t('flow.bcpObjection.colRaiseBy')" width="110" show-overflow-tooltip />
      <el-table-column :label="$t('flow.bcpObjection.colRaiseTime')" width="150">
        <template #default="{ row }">{{ formatDateTime(row.raiseTime) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" size="small" @click="openReview(row, true)">{{ $t('flow.bcpObjection.actApprove') }}</el-button>
          <el-button link type="danger" size="small" @click="openReview(row, false)">{{ $t('flow.bcpObjection.actReject') }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="$t('flow.bcpObjection.nonePending')" :image-size="60">
          <div class="empty-tip">{{ $t('flow.bcpObjection.nonePendingTip') }}</div>
        </el-empty>
      </template>
    </el-table>

    <!-- 审核结论：同意＝以伙伴提交值替换该对账明细行；驳回＝明细行不变。两者都写审核意见留痕。 -->
    <el-dialog v-model="dialog.visible" width="560px" destroy-on-close
               :title="dialog.approve ? $t('flow.bcpObjection.approveTitle') : $t('flow.bcpObjection.rejectTitle')">
      <el-descriptions v-if="dialog.row" :column="2" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item :label="$t('flow.bcpObjection.colReconNo')">{{ dialog.row.reconNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colSourceBill')">{{ dialog.row.sourceBillNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colOrigQty')">{{ fmtNum(dialog.row.origQuantity) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colReqQty')">{{ fmtNum(dialog.row.reqQuantity) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colOrigPrice')">{{ fmtNum(dialog.row.origUnitPrice) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colReqPrice')">{{ fmtNum(dialog.row.reqUnitPrice) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.bcpObjection.colReason')" :span="2">{{ dialog.row.reason }}</el-descriptions-item>
      </el-descriptions>
      <el-alert :title="dialog.approve ? $t('flow.bcpObjection.approveConfirm') : $t('flow.bcpObjection.rejectConfirm')"
                :type="dialog.approve ? 'success' : 'warning'" :closable="false" show-icon />
      <el-input v-model="dialog.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
                :placeholder="$t('flow.bcpObjection.reviewRemarkPh')" style="margin-top: 12px" />
      <template #footer>
        <el-button @click="dialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :type="dialog.approve ? 'success' : 'danger'" :loading="submitting" @click="submit">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { bcpObjectionApi } from '../../api'
import { formatDateTime } from '../../utils/format'

const { t } = useI18n()

const rows = ref([])
const loading = ref(false)
const submitting = ref(false)
const query = reactive({ direction: '' })
const dialog = reactive({ visible: false, approve: true, remark: '', row: null })

/** 空值显示为 '—'：原值缺失（v59 上线前的历史行）与"伙伴未提异议"都是这层语义。 */
const fmtNum = (v) => (v === null || v === undefined || v === '' ? '—'
  : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 6 }))

/** 仅当伙伴确实提了这一项、且与原值不同时才高亮——未提的那一项不该被标成"改动"。 */
const isChanged = (orig, req) => req !== null && req !== undefined && String(orig) !== String(req)

async function load() {
  loading.value = true
  try {
    rows.value = (await bcpObjectionApi.pending({ direction: query.direction || undefined, limit: 200 })) || []
  } catch (e) {
    rows.value = []
    ElMessage.error(e.message || t('flow.bcpObjection.loadFail'))
  } finally {
    loading.value = false
  }
}

function openReview(row, approve) {
  dialog.row = row
  dialog.approve = approve
  dialog.remark = ''
  dialog.visible = true
}

async function submit() {
  if (!dialog.row) return
  submitting.value = true
  try {
    await bcpObjectionApi.review(dialog.row.id, dialog.approve, dialog.remark || null)
    ElMessage.success(dialog.approve ? t('flow.bcpObjection.approveOk') : t('flow.bcpObjection.rejectOk'))
    dialog.visible = false
    await load()
  } catch (e) {
    // BCP 侧的状态冲突（已被他人审核，不允许改判）会原样透出，直接给用户看
    ElMessage.error(e.message || t('common.failed'))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; }
.ops { display: flex; align-items: center; gap: 8px; }
.changed { color: #e6a23c; font-weight: 600; }
.empty-tip { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 6px; }
</style>
