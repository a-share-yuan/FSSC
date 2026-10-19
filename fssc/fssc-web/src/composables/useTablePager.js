// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useTablePager - 通用表格分页 composable
 *
 * 背景（第 6 轮体检 P0-1）：系统内大量列表页一次性全量拉取、无分页，
 * 数据量增长后必然卡死。本项目存在两类列表：
 *
 *   1) 服务端分页：后端接口支持 pageNum/pageSize（推荐，明细列表用）
 *   2) 本地分页  ：后端返回聚合结果/小全集（报表、看板、配置项），
 *                  前端按页切片，避免一次渲染上百行 DOM
 *
 * 用法 A（服务端分页）：
 *   const { rows, total, pageNum, pageSize, load, onPageChange, onSizeChange } = useTablePager({ mode: 'server' })
 *   async function load() {
 *     await load(async (p) => {
 *       const r = await someApi.page({ pageNum: p.pageNum, pageSize: p.pageSize, ...query })
 *       return { list: r.list || [], total: r.total || 0 }
 *     })
 *   }
 *
 * 用法 B（本地分页）：
 *   const { pagedData, total, pageNum, pageSize, onPageChange, onSizeChange } = useTablePager({ mode: 'local' })
 *   watchEffect(() => pagedData)   // 传入全量源
 *   const view = useTablePager({ mode: 'local' })
 *   view.setSource(allRows.value)
 *   <el-table :data="view.pagedData">
 *
 * @module composables/useTablePager
 */

import { computed, ref, watch } from 'vue'

/** 每页条数候选（对标 SAP/用友/金蝶：10/20/50/100/200） */
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100, 200]

/** 默认每页条数 */
export const DEFAULT_PAGE_SIZE = 20

/**
 * @param {Object}  opts
 * @param {'server'|'local'} [opts.mode='server'] 分页模式
 * @param {number}  [opts.pageSize=20] 初始每页条数
 * @param {string}  [opts.storageKey]  传入则记忆用户上次的每页条数（P2-2 预留）
 */
export function useTablePager(opts = {}) {
  const { mode = 'server', pageSize: initSize = DEFAULT_PAGE_SIZE, storageKey } = opts

  const pageNum = ref(1)
  const pageSize = ref(readSize(storageKey, initSize))
  const total = ref(0)
  const loading = ref(false)

  // ---- 服务端分页数据 ----
  const rows = ref([])

  // ---- 本地分页数据 ----
  const source = ref([])
  const pagedData = computed(() => {
    if (mode !== 'local') return rows.value
    const from = (pageNum.value - 1) * pageSize.value
    return source.value.slice(from, from + pageSize.value)
  })

  function readSize(key, fallback) {
    if (!key) return fallback
    try {
      const v = Number(localStorage.getItem(`fssc_pagesize_${key}`))
      return PAGE_SIZE_OPTIONS.includes(v) ? v : fallback
    } catch { return fallback }
  }

  function persistSize() {
    if (!storageKey) return
    try { localStorage.setItem(`fssc_pagesize_${storageKey}`, String(pageSize.value)) } catch { /* ignore */ }
  }

  /** 本地分页：设置全量数据源（会自动 clamp 页码） */
  function setSource(list) {
    source.value = Array.isArray(list) ? list : []
    total.value = source.value.length
    clampPage()
  }

  /** 服务端分页：执行加载 */
  async function load(fetcher) {
    loading.value = true
    try {
      const res = await fetcher({ pageNum: pageNum.value, pageSize: pageSize.value })
      rows.value = res?.list || []
      total.value = Number(res?.total) || rows.value.length
      clampPage()
    } finally {
      loading.value = false
    }
  }

  function clampPage() {
    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
    if (pageNum.value < 1) pageNum.value = 1
  }

  /** 查询条件变化时回到第 1 页 */
  function resetPage() { pageNum.value = 1 }

  function onPageChange(p) { pageNum.value = p; clampPage() }
  function onSizeChange(s) { pageSize.value = s; pageNum.value = 1; persistSize() }

  // 服务端模式下 pageSize 变化需重新加载：交由页面 watch
  watch(pageSize, () => { if (mode === 'server') resetPage() })

  return {
    // 通用
    pageNum, pageSize, total, loading, onPageChange, onSizeChange, resetPage,
    // 服务端
    rows, load,
    // 本地
    source, pagedData, setSource,
  }
}

export default useTablePager
