<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="my-cc">
    <el-card shadow="never">
      <template #header>
        <div class="mc-head">
          <span>{{ $t('workflow.myCc.My') }}</span>
          <div class="mc-head-right">
            <el-tag size="small" type="warning" v-if="unread > 0">未读 {{ unread }}</el-tag>
            <el-button link type="primary" size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table v-else :data="rows" size="small" stripe v-loading="loading" max-height="560" empty-text="暂无抄送">
        <el-table-column prop="bizNo" label="业务单号" width="170" sortable />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fromNode" label="来源节点" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isRead ? 'info' : 'warning'">{{ row.isRead ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="抄送时间" width="170" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="open(row)">查看</el-button>
            <el-button v-if="!row.isRead" link size="small" @click="markRead(row)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" :total="total" :page-size="pageSize" v-model:current-page="pageNum"
                     layout="total, prev, pager, next, jumper" @current-change="load" style="margin-top: 10px" />
    </el-card>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http, flowApi } from '../../api'
import { goBizPage } from '@/utils/bizEntry'
import { formatDateTime } from '../../utils/format'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const router = useRouter()

// 后端 /wf/query/cc/mine 与 /wf/query/cc/{id}/read 现同为「本人视角免权限码」
// （列表本就无，已读端点若挂 wf:cc:update——只授 CONFIG_ADMIN／FIN_MANAGER／IT_ADMIN，
// 而实际收到抄送的业务岗位皆不持 ⇒ 按钮不渲染、is_read 恒 0）。
// 前端随之撤掉 hasPerm 预判：按钮对收件人本人恒可见，越权由服务端按归属判据拦。

const rows = ref([])
const total = ref(0)
const unread = ref(0)
const pageNum = ref(1)
const pageSize = 20
const loading = ref(false)

const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await flowApi.myCc({ pageNum: pageNum.value, pageSize })
    rows.value = res?.list || res?.records || []
    total.value = res?.total || 0
    // 未读徽标改取服务端总数：按「当前页」里 !isRead 计数的话，
    // 第二页之后的未读从不参与，抄送一多徽标就恒显一页的量（与「标记已读」回流后对不上账）。
    unread.value = await loadUnread()
  } catch (e) {
    rows.value = []
    total.value = 0
    unread.value = 0
  } finally {
    loading.value = false
  }
}

/** 未读抄送总数（pageSize=1 只取 total，不拉明细）；失败回退按当前页计数，不阻断列表 */
async function loadUnread() {
  try {
    const res = await flowApi.myCc({ pageNum: 1, pageSize: 1, isRead: 0 })
    return res?.total || 0
  } catch (e) {
    return rows.value.filter((r) => !r.isRead).length
  }
}

function open(row) {
  // 打开即视为已读：收件人已经看到并进入了这条抄送，
  // 再要求他多点一次「标记已读」只会让 is_read 恒 0——现网 12 行全未读就是这么攒出来的。
  if (!row.isRead) markRead(row, true)
  if (!goBizPage(router, row)) {
    // 抄送抽屉必须按「本条抄送记录的 bizType + bizId」精确取链。
    // 早先兜到 row.id（wf_cc 自身主键）等于把抄送记录 id 当单据主键送去联查，
    // 与他域主键撞号时打开的就是别人的单据全链（含他人审批意见）——宁可不打开，也不能猜关联点。
    const bizId = row.bizId != null ? row.bizId : row.billId
    if (bizId == null || !row.bizType) {
      ElMessage.warning(t('workflow.myCc.This'))
      return
    }
    ElMessage.info(t('common.workbench.Current4'))
    traceBillId.value = bizId
    traceBizType.value = row.bizType
    traceVisible.value = true
  }
}

/**
 * 标记已读。
 * @param silent 静默模式：用于「打开即已读」的附带回写，失败只留 console，
 *               不弹 toast、不打断正在打开的单据——已读写不上是小事，看单是大事。
 */
async function markRead(row, silent = false) {
  try {
    await http.post(`/tenant/wf/query/cc/${row.id}/read`)
    row.isRead = 1
    unread.value = await loadUnread()
  } catch (e) {
    if (silent) console.warn('抄送已读回写失败', row.id, e?.message)
    else ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.my-cc { padding: 4px; }
.mc-head { display: flex; align-items: center; justify-content: space-between; }
.mc-head-right { display: flex; align-items: center; gap: 8px; }
</style>
