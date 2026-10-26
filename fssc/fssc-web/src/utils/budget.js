// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 预算明细行解析（D-P2-01）
// md_item_budget_map 是「全租户单值」映射（费用项 itemId → 某一条 bg_item 主键），
// 该主键可能属于其它公司/部门（实测 行政办公费 → id 20 = company33/一车间）。
// 直接带出既不在制单人可见域内（下拉退化显示原始 id），也会把预算占用记到别的主体。
// 因此统一先按 费用项 + 单据部门 + 单据公司 在可见预算明细里命中真实预算行，
// 映射表主键只在同公司可见时默认处理；命中不到返回 null，交给人工选择。
//
// D-H5M-06（年度硬条件）：预算行的业务年度必须与单据费用年度一致。
// 服务端 BudgetService.resolveOccupyItem/matchItem 就是按「费用项 + 公司 + 部门 + 业务年度」命中预算行的，
// 且 wantedItemId 只校验公司、不校验年度 ⇒ 前端跨年度取行，占用会落在另一年的预算线上
// （实测 2026 年的单命中 2025 年行：行政办公费 333 → bg_item 1002[budget_year=2025]）。
// 传 budgetYear 时生效；不传（老调用方）保持原行为。
export function pickBudgetLine({ pool, itemId, map, companyId, deptId, budgetYear }) {
  if (itemId == null) return null
  const eq = (a, b) => a != null && b != null && String(a) === String(b)
  let rows = pool || []
  if (budgetYear != null) {
    const inYear = rows.filter((b) => eq(b.budgetYear, budgetYear))
    // 本年度一条都没有 ⇒ 不跨年度取（宁可留空，页面明示「本年度未下达该科目预算」，
    // 提交时服务端仍会按同一年度规则自行定位）
    if (!inYear.length) return null
    rows = inYear
  }
  let cand = rows.filter((b) => eq(itemId, b.itemId))
  if (!cand.length) cand = rows.filter((b) => eq(map && map[String(itemId)], b.id))
  if (!cand.length) return null
  const rank = (b) => (eq(deptId, b.deptId) ? 2 : 0) + (eq(companyId, b.companyId) ? 1 : 0)
  const best = cand.slice().sort((a, b) => rank(b) - rank(a))[0]
  // 跨主体宁可留空，也不带出别的公司的预算线
  if (companyId != null && !eq(companyId, best.companyId)) return null
  return best.id
}

/**
 * 行级预算行重解析（D-H5M-05/D-H5M-06）：换费用项或换年度之后，
 * 已挂的预算行若与「当前费用项 + 当前年度 + 当前池」对不上，就按新条件重取；
 * 人工指定过、且在当前条件下仍然成立的那一条予以保留
 * （电脑端可手选预算科目，不能无脑覆盖；移动端没有手选入口，等价于总是重取）。
 * 移动端与电脑端共用这一份规则，避免两边算出不同的预算行。
 */
export function resolveLineBudgetId(line, { pool, map, companyId, deptId, budgetYear }) {
  if (!line || line.itemId == null) return null
  const eq = (a, b) => a != null && b != null && String(a) === String(b)
  const cur = line.budgetItemId
  if (cur != null) {
    const hit = (pool || []).find((x) => eq(x.id, cur))
    if (hit && eq(hit.itemId, line.itemId)
      && (budgetYear == null || eq(hit.budgetYear, budgetYear))) return cur
  }
  return pickBudgetLine({ pool, itemId: line.itemId, map, companyId, deptId, budgetYear })
}
