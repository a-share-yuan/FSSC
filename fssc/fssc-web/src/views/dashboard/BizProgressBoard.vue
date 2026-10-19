<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="biz-board">
    <div class="board-head">
      <div class="board-title">业务实时进度看板</div>
      <div class="board-note">「－」表示该指标暂无取数范围，不代表业务量为零；点击卡片进入对应业务页。</div>
    </div>
    <div class="biz-grid">
      <div v-for="biz in bizList" :key="biz.type" class="biz-card" @click="drillDown(biz)">
        <div class="biz-header">
          <span class="biz-name">{{ biz.name }}</span>
          <el-icon class="biz-arrow"><ArrowRightBold /></el-icon>
        </div>
        <div class="biz-stats">
          <div class="biz-stat">
            <div class="stat-num in-transit">{{ showStat(biz.stats.inTransit) }}</div>
            <div class="stat-text">在途</div>
          </div>
          <div class="biz-stat">
            <div class="stat-num done">{{ showStat(biz.stats.done) }}</div>
            <div class="stat-text">今日完成</div>
          </div>
          <div class="biz-stat">
            <div class="stat-num timeout">{{ showStat(biz.stats.timeout) }}</div>
            <div class="stat-text">超时</div>
          </div>
        </div>
        <div class="biz-note">{{ biz.note }}</div>
        <div class="mini-trend" ref="trendRefs"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRightBold } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { billApi, dashApi } from '../../api'
import { hasAnyPerm } from '@/utils/perm'

const router = useRouter()
const trendRefs = ref([])

/**
 * D-DEC-303：本卡区此前有两个问题——① 未取数的槽位与"取到 0"显示成同一个 0，观感＝系统没数据；
 * ② 全页无权限门控，无 dashboard:view / exp:bill:list 的角色进来会连弹两次「缺少权限」，
 * 而后端的 code=4030 在页面上又伪装成「暂无数据」。现改为：null＝未接通（显示「－」＋原因），
 * 数字＝真实取数结果；取数失败与无权都落到卡片脚注，不再弹全局错误。
 */
function card(type, name, route, routePerm, note) {
  return { type, name, route, routePerm, note, stats: { inTransit: null, done: null, timeout: null } }
}

const NO_SOURCE = '该域暂无汇总数据'
const NO_TODAY = '今日完成/超时无汇总数据'

const bizList = ref([
  card('EXP', '费用报销', '/expense', 'exp:bill:list', ''),
  card('PAY', '采购付款', '/pay', 'op:pay:list', ''),
  card('CONTRACT', '合同管理', '/contract', 'contract:list', ''),
  card('LOAN', '借款管理', '/expense/loan', 'exp:bill:list', ''),
  card('INVOICE', '发票管理', '/invoice', 'inv:list', ''),
  card('ARCHIVE', '档案归档', '/archive', ['compliance:archiveaccess:list', 'compliance:archivedestroy:list'], ''),
])

function of(type) {
  return bizList.value.find((b) => b.type === type)
}

function showStat(v) {
  return v === null || v === undefined ? '－' : v
}

/** 单据域「在途」＝登录人本人数据范围内处于 APPROVING 的该类型单量（myPage 走数据范围过滤） */
async function loadBillType(type, billType, label) {
  const biz = of(type)
  if (!hasAnyPerm([biz.routePerm].flat())) {
    biz.note = `缺少 ${biz.routePerm} 权限，未取数`
    return
  }
  try {
    const page = await billApi.myPage({ pageNum: 1, pageSize: 1, billType, status: 'APPROVING' })
    biz.stats.inTransit = Number(page?.total ?? 0)
    biz.note = `在途＝我的${label}在办单量；${NO_TODAY}`
  } catch (e) {
    biz.note = `在途取数失败：${e?.message || '接口异常'}`
  }
}

/** 付款域「在途」＝待付款记录数（pay/progress 为全租户聚合，无个人数据范围） */
async function loadPay() {
  const biz = of('PAY')
  if (!hasAnyPerm(['dashboard:view'])) {
    biz.note = '缺少 dashboard:view 权限，未取数'
    return
  }
  try {
    const p = await dashApi.pay()
    biz.stats.inTransit = Number(p?.waitingCnt ?? 0)
    biz.note = `在途＝待付记录（全租户）${p?.waitingCnt ?? 0} 笔，累计已付 ${p?.paidCnt ?? 0} 笔；${NO_TODAY}`
  } catch (e) {
    biz.note = `付款进度取数失败：${e?.message || '接口异常'}`
  }
}

/**
 * WS-4：通用进度卡取数（在途/今日完成/超时）。timeout 为 null ⇒ 前端显「－」（该域无超时汇总口径）；
 * 门控 dashboard:view（与付款进度卡同尺），取数失败/无权都落到卡片脚注，不弹全局错误。
 */
async function loadProgress(type, fetcher, noteText) {
  const biz = of(type)
  if (!hasAnyPerm(['dashboard:view'])) {
    biz.note = '缺少 dashboard:view 权限，未取数'
    return
  }
  try {
    const d = await fetcher()
    biz.stats.inTransit = Number(d?.inTransit ?? 0)
    biz.stats.done = Number(d?.done ?? 0)
    biz.stats.timeout = d?.timeout === null || d?.timeout === undefined ? null : Number(d.timeout)
    biz.note = noteText
  } catch (e) {
    biz.note = `进度取数失败：${e?.message || '接口异常'}`
  }
}

async function fetchStats() {
  await loadBillType('EXP', 'CLAIM', '费用报销')
  await loadPay()
  await loadBillType('LOAN', 'LOAN', '借款')
  await loadProgress('CONTRACT', dashApi.contractProgress, '在途＝审批中合同；今日完成＝今日生效；超时＝已过合同止期仍未关闭')
  await loadProgress('INVOICE', dashApi.invoiceProgress, '在途＝待核验发票；今日完成＝今日核验通过；超时＝核验失败待处理')
  await loadProgress('ARCHIVE', dashApi.archiveProgress, '在途＝待归档/归档中；今日完成＝今日归档；超时无汇总口径')
  renderMiniTrends()
}

function renderMiniTrends() {
  nextTick(() => {
    trendRefs.value?.forEach((el, i) => {
      if (!el) return
      el.innerHTML = ''
      const biz = bizList.value[i]
      const slots = [biz?.stats.inTransit, biz?.stats.done, biz?.stats.timeout]
      // P1-8：不再用 Math.random 伪造趋势线；三项全部未接通的卡片不绘制（空图同样是在误导）
      if (slots.every((v) => v === null || v === undefined)) return
      const data = slots.map((v) => Number(v) || 0)
      const max = Math.max(...data, 1)
      const canvas = document.createElement('canvas')
      canvas.width = el.offsetWidth || 200
      canvas.height = 30
      const ctx = canvas.getContext('2d')
      const colors = ['#409eff', '#67c23a', '#f56c6c']
      const bw = canvas.width / data.length
      data.forEach((v, j) => {
        const h = v > 0 ? Math.max(2, (v / max) * (canvas.height - 6)) : 0
        ctx.fillStyle = colors[j]
        ctx.fillRect(j * bw + 6, canvas.height - h, bw - 12, h)
      })
      el.appendChild(canvas)
    })
  })
}

function drillDown(biz) {
  const needed = [biz.routePerm].flat()
  if (!hasAnyPerm(needed)) {
    ElMessage.warning(`无权限进入${biz.name}（缺少 ${needed.join(' 或 ')}）`)
    return
  }
  router.push(biz.route)
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.biz-board { padding: 16px; }
.board-head { margin-bottom: 16px; }
.board-title { font-size: 16px; font-weight: 700; }
.board-note { font-size: 12px; color: #909399; margin-top: 4px; }
.biz-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.biz-card {
  background: #fff; border-radius: 10px; padding: 16px;
  cursor: pointer; transition: box-shadow 0.2s;
  border: 1px solid #ebeef5;
}
.biz-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.biz-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px;
}
.biz-name { font-size: 15px; font-weight: 600; }
.biz-arrow { color: #c0c4cc; }
.biz-stats { display: flex; gap: 16px; margin-bottom: 8px; }
.biz-stat { text-align: center; }
.stat-num { font-size: 22px; font-weight: 700; }
.stat-num.in-transit { color: #409eff; }
.stat-num.done { color: #67c23a; }
.stat-num.timeout { color: #f56c6c; }
.stat-text { font-size: 12px; color: #909399; }
.biz-note { font-size: 12px; color: #909399; line-height: 1.5; min-height: 18px; }
.mini-trend { height: 30px; margin-top: 8px; }
.mini-trend canvas { width: 100%; }
</style>
