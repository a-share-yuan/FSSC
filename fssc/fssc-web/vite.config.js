// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'url'

/**
 * /api 代理到后端 fssc-server.jar（端口 8089）。
 * 后端未启动时，vite 默认只在终端刷 ECONNREFUSED，浏览器侧得到的是无响应体错误，
 * 页面表现为"白屏 + 一堆失败请求"。这里兜底返回 503 JSON，
 * 前端响应拦截器即可识别为"后端未连接"并弹出顶部横幅给出明确指引。
 */
const backendProxy = {
  // 后端端口可用 FSSC_API_TARGET 覆盖（例如：set FSSC_API_TARGET=http://127.0.0.1:18080）
  target: process.env.FSSC_API_TARGET || 'http://127.0.0.1:8089',
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('error', (err, _req, res) => {
      const code = err?.code
      const down = code === 'ECONNREFUSED' || code === 'ETIMEDOUT' || code === 'EHOSTUNREACH'
      try {
        if (res && !res.headersSent) {
          res.writeHead(down ? 503 : 502, { 'Content-Type': 'application/json;charset=utf-8' })
          res.end(JSON.stringify({
            code: down ? 503 : 502,
            success: false,
            data: null,
            msg: down
              ? '后端服务未启动：请先运行 fssc-server（java -jar fssc-start/target/fssc-server.jar，端口 8089）'
              : '代理转发失败：' + (err?.message || '未知错误'),
          }))
        } else if (res) {
          res.end()
        }
      } catch { /* 连接已断开，忽略 */ }
    })
  },
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 前端被更多用户直接访问，占用常用端口 8080
    port: 8080,
    proxy: { '/api': backendProxy },
  },
  preview: {
    // 前端预览：8080，代理 /api 到后端 fssc-server.jar（本工程 run-server.sh 默认 8089）
    port: 8080,
    proxy: { '/api': backendProxy },
  },
  build: {
    // 输出目录：前端构建产物统一落在 dist33/
    outDir: 'dist36',
    emptyOutDir: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 手工分包：bpmn 全家桶与 element-plus 各自独立 chunk，避免打进业务页面
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (/[\\/](bpmn-js|bpmn-js-properties-panel|camunda-bpmn-moddle|diagram-js)[\\/]/.test(id)) return 'bpmn'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element'
        },
      },
    },
  },
})
