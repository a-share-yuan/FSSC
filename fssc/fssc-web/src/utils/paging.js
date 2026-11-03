// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 分页接口全量拉取。
 *
 * <p><b>为什么需要</b>：下拉框/映射表这类「必须全量」的场景，历史上写死
 * {@code pageSize: 2000}。数据量一旦超过上限，接口不会报错，只会静默少给——
 * 表现为「选项里搜不到某个城市/某个用户」，排查时几乎不会怀疑到分页。
 *
 * <p>用法：
 * <pre>
 *   const rows = await fetchAllPages((p) => mdApi.cities(p), { pageSize: 500 })
 * </pre>
 * 约定：入参 {@code p = {pageNum, pageSize}}，出参形如 {@code {list, total}} 或数组。
 * 终止条件：累计条数 ≥ total，或本页不足一页（服务端没给 total 时靠它兜底）。
 */
export async function fetchAllPages(fetcher, { pageSize = 500, maxPages = 200, ...rest } = {}) {
  const out = []
  for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
    const res = await fetcher({ ...rest, pageNum, pageSize })
    const list = Array.isArray(res) ? res : (res?.list || res?.records || res?.rows || [])
    out.push(...list)
    const total = Array.isArray(res) ? null : Number(res?.total)
    if (Number.isFinite(total) && out.length >= total) break
    if (list.length < pageSize) break
  }
  return out
}
