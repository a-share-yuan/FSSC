<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-dialog v-model="visible" :title="title" width="92vw" top="3vh" append-to-body destroy-on-close>
    <div class="fv-wrap">
      <div class="fv-list" v-if="files.length > 1">
        <div
          v-for="(f, i) in files" :key="i"
          :class="['fv-item', { active: i === activeIndex }]"
          :title="f.name"
          @click="activeIndex = i"
        >
          <span class="fv-idx">{{ i + 1 }}</span>
          <span class="fv-name">{{ f.name }}</span>
        </div>
      </div>
      <div class="fv-main">
        <div v-if="!current" class="fv-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在加载预览…
        </div>
        <div v-else-if="current._err" class="fv-state fv-err">
          <el-icon><WarningFilled /></el-icon> {{ current._err }}
        </div>
        <OpenFileViewer
          v-else
          :file="current.file"
          :file-name="current.name"
          :plugins="plugins"
          width="100%"
          height="100%"
          fit="contain"
          theme="auto"
        />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { OpenFileViewer } from '@open-file-viewer/vue'
import {
  imagePlugin,
  videoPlugin,
  audioPlugin,
  textPlugin,
  pdfPlugin,
  officePlugin,
  archivePlugin,
  fallbackPlugin
} from '@open-file-viewer/core'
import pdfWorkerSrc from 'pdfjs-dist/build/pdf.worker.mjs?url'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { fileApi } from '../api'

/**
 * 通用影像/附件查看器（统一文件预览）。
 * 基于开源组件 @open-file-viewer（Vue 适配器 + 插件链），覆盖图片 / 音视频 / 文本 / PDF / Office / 压缩包，
 * 替换原 el-image + iframe + SheetJS 三套分散方案。移植自 Knowledge（kb-portal）的同名实现。
 * 暴露 open(fileIds, title)，9 处调用点（单据 / 发票 / 影像 / 文件管理 / 档案）无需改动即可复用。
 */
const plugins = [
  imagePlugin(),
  videoPlugin(),
  audioPlugin(),
  textPlugin(),
  pdfPlugin({ workerSrc: pdfWorkerSrc }),
  officePlugin(),
  archivePlugin(),
  fallbackPlugin()
]

const visible = ref(false)
const title = ref('预览')
const files = ref([])        // [{ name, file: File, _err? }]
const activeIndex = ref(0)

const current = computed(() => files.value[activeIndex.value] || null)

// 单个文件：取元数据 → 申领一次性下载令牌 → 拉取字节 → 包装为 File 交给 open-file-viewer
async function toFile(id) {
  const d = await fileApi.detail(id) || {}
  const name = d.fileName || String(id)
  const tk = await fileApi.createToken(id)
  const url = '/api/tenant/system/file/download?token=' + tk
  const resp = await fetch(url, { credentials: 'include', headers: { Accept: '*/*' } })
  if (!resp.ok) throw new Error('HTTP ' + resp.status)
  const blob = await resp.blob()
  if (!blob || blob.size === 0) throw new Error('文件内容为空')
  const mime = (resp.headers.get('content-type') || '').split(';')[0].trim() || blob.type || ''
  return { name, file: new File([blob], name, { type: mime || undefined }) }
}

async function open(fileIds, t) {
  title.value = t || '预览'
  files.value = []
  activeIndex.value = 0
  visible.value = true
  const ids = Array.isArray(fileIds) ? fileIds : [fileIds]
  const loaded = []
  for (const id of ids) {
    if (id == null) continue
    try {
      loaded.push(await toFile(id))
    } catch (e) {
      console.warn('[FileViewer] 加载失败', id, e)
      loaded.push({ name: '文件 #' + id + ' 加载失败', file: null, _err: e?.message || String(e) })
    }
  }
  files.value = loaded
}

defineExpose({ open })
</script>

<style scoped>
.fv-wrap {
  display: flex;
  height: 78vh;
  background: #f3f4f6;
}
.fv-list {
  width: 220px;
  flex: 0 0 220px;
  border-right: 1px solid var(--el-border-color-lighter);
  overflow-y: auto;
  background: #fff;
}
.fv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.fv-item:hover { background: var(--el-fill-color-light); }
.fv-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.fv-idx { flex: 0 0 18px; text-align: center; color: #909399; }
.fv-item.active .fv-idx { color: var(--el-color-primary); }
.fv-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fv-main { flex: 1; min-width: 0; position: relative; }
.fv-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #6b7280;
  font-size: 14px;
  padding: 0 24px;
  text-align: center;
}
.fv-err { color: #c0392b; }
</style>
