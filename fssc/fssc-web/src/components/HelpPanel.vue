<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer v-model="visible" title="上下文帮助" direction="rtl" size="400px" :destroy-on-close="true">
    <div class="help-content">
      <!-- 搜索帮助 -->
      <el-input v-model="search" placeholder="搜索帮助文档..." :prefix-icon="Search" clearable size="small" style="margin-bottom: 12px" />

      <!-- 当前页面上下文帮助 -->
      <div v-if="currentHelp" class="context-help">
        <div class="section-title">
          <el-icon><Document /></el-icon>
          <span>当前页面: {{ currentHelp.title }}</span>
        </div>
        <div class="help-text">{{ currentHelp.desc }}</div>
        <div v-if="currentHelp.tips" class="help-tips">
          <div v-for="(tip, i) in currentHelp.tips" :key="i" class="tip-item">
            <el-icon class="tip-icon"><InfoFilled /></el-icon>
            <span>{{ tip }}</span>
          </div>
        </div>
      </div>

      <el-divider />

      <!-- 常见问题 -->
      <div class="section-title">
        <el-icon><QuestionFilled /></el-icon>
        <span>常见问题</span>
      </div>
      <el-collapse v-model="activeFaq">
        <el-collapse-item v-for="(faq, i) in filteredFaqs" :key="i" :name="i">
          <template #title><span class="faq-q">{{ faq.q }}</span></template>
          <div class="faq-a">{{ faq.a }}</div>
        </el-collapse-item>
      </el-collapse>

      <el-divider />

      <!-- 快捷操作 -->
      <div class="section-title">
        <el-icon><Operation /></el-icon>
        <span>快捷操作</span>
      </div>
      <div class="quick-actions">
        <el-button size="small" @click="$emit('action', 'shortcut')">查看快捷键</el-button>
        <el-button size="small" @click="$emit('action', 'tutorial')">新手教程</el-button>
        <el-button size="small" @click="$emit('action', 'contact')">联系管理员</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Document, InfoFilled, QuestionFilled, Operation } from '@element-plus/icons-vue'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'action'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const route = useRoute()
const search = ref('')
const activeFaq = ref([0])

// 路由 → 帮助内容映射
const HELP_MAP = {
  '/dashboard': { title: '经营驾驶舱', desc: '展示报销、付款、审批等核心指标的图表大屏。', tips: ['点击图表可钻取明细', '支持全屏模式', '可导出 Excel'] },
  '/approval': { title: '我的审批', desc: '处理待审批单据，支持通过、驳回、核减。', tips: ['展开行查看审批轨迹', '核减时三层勾稽自动校验', '批量导出选中记录'] },
  '/expense': { title: '费用报账', desc: '报销单据列表，支持新建、编辑、提交审批。', tips: ['支持差旅、日常、培训等类型', '附件可OCR识别', '提交后自动走流程'] },
  '/pay': { title: '付款管理', desc: '管理付款申请、审批、执行全流程。', tips: ['支持银企直联', '付款状态实时更新', '支持批量付款'] },
  '/sharing/task-pool': { title: '任务池', desc: '共享中心任务调度与分配。', tips: ['折认任务到个人名下', '支持重新分配', 'SLA超时自动预警'] },
}

const currentHelp = computed(() => {
  const path = route.path
  for (const [key, val] of Object.entries(HELP_MAP)) {
    if (path.startsWith(key)) return val
  }
  return { title: route.meta?.title || '当前页面', desc: '暂无该页面的专属帮助内容。', tips: ['如需帮助请联系管理员'] }
})

const faqs = [
  { q: '如何提交报销单？', a: '在"费用报账"页面点击"新建"，选择报销类型，填写金额和事由，上传发票后提交即可自动进入审批流程。' },
  { q: '审批被驳回后怎么办？', a: '驳回的单据会退回到提交人。费用类单据（报销/借款/还款/对公付款/事前申请）在「我的单据」列表点「编辑重提」，原单载入后可直接修改重报；合同、采购订单、付款批次等在列表点「编辑重提」转回草稿后修改；资金计划/付款计划点「重报审批」原单重走流程。无需新建单据绕过。' },
  { q: '如何查看付款进度？', a: '在"付款管理"页面可查看所有付款单的状态，也可在Dashboard的"付款进度"图表中查看汇总。' },
  { q: '快捷键有哪些？', a: 'Ctrl+K 全局搜索，Ctrl+/ 快捷键帮助，Ctrl+D 收藏当前页，Ctrl+B 打开收藏夹，Alt+1~4 快速导航。' },
  { q: '如何切换深色模式？', a: '点击顶部栏的太阳/月亮图标，或按 Ctrl+H 快捷键切换深/浅色模式。' },
  { q: '如何导出报表？', a: '在Dashboard页面点击"导出"按钮，或在报表页面使用导出功能，支持Excel/CSV格式。' },
  { q: '任务池如何抢单？', a: '在"共享运营>任务池"页面，找到待认领任务，点击"抢单"按钮即可将任务分配到自己名下。' },
]

const filteredFaqs = computed(() => {
  if (!search.value) return faqs
  const kw = search.value.toLowerCase()
  return faqs.filter(f => f.q.toLowerCase().includes(kw) || f.a.toLowerCase().includes(kw))
})
</script>

<style scoped>
.help-content { padding: 0 4px; }
.section-title { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.context-help { margin-bottom: 8px; }
.help-text { font-size: 13px; color: var(--app-text-secondary, #606266); line-height: 1.6; margin-bottom: 8px; }
.help-tips { background: rgba(64, 158, 255, 0.12); border-radius: 6px; padding: 8px; }
.tip-item { display: flex; align-items: flex-start; gap: 6px; font-size: 12px; color: #409eff; margin-bottom: 4px; }
.tip-icon { margin-top: 2px; flex-shrink: 0; }
.faq-q { font-size: 13px; font-weight: 500; }
.faq-a { font-size: 12px; color: var(--app-text-secondary, #606266); line-height: 1.6; }
.quick-actions { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
