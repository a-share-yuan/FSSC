// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 预算明细行解析
// md_item_budget_map 是「全租户单值」映射（费用项 itemId → 某一条 bg_item 主键），
// 该主键可能属于其它公司/部门（行政办公费的映射会指到别的车间）。
// 直接带出既不在制单人可见域内（下拉退化显示原始 id），也会把预算占用记到别的主体。
// 因此统一先按 费用项 + 单据部门 + 单据公司 在可见预算明细里命中真实预算行，
// 映射表主键只在同公司可见时兜底；命中不到返回 null，交给人工选择。
// 年度硬条件：预算行的业务年度必须与单据费用年度一致。
// 服务端 BudgetService.resolveOccupyItem/matchItem 就是按「费用项 + 公司 + 部门 + 业务年度」命中预算行的，
// 且 wantedItemId 只校验公司、不校验年度 ⇒ 前端跨年度取行，占用会落在另一年的预算线上
// （跨年的单据会命中上一年的预算行）。
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
 * 行级预算行重解析：换年度之后，
 * 已挂的预算行若不在「本年预算池」里，就按新条件重取。
 * 保留判据只看「这条预算行还在本年池里」——费用项↔预算行的映射只是新建行时的默认带出，
 * 不是约束：库里已保存的预算行可以绑在另一个费用项上（运输费的单会挂着修理费的预算科目），
 * 判据若在重开草稿时把它清成「请选择」，提交就会被「预算科目必填」拦住，
 * 换费用项要跟着重取这件事由调用方在
 * 费用类型变更时处理（useBill.onItemChange），不在这里反噬已保存值。
 */
export function resolveLineBudgetId(line, { pool, map, companyId, deptId, budgetYear }) {
  if (!line || line.itemId == null) return null
  const eq = (a, b) => a != null && b != null && String(a) === String(b)
  const cur = line.budgetItemId
  const hit = cur == null ? null : (pool || []).find((x) => eq(x.id, cur))
  if (hit && (budgetYear == null || eq(hit.budgetYear, budgetYear))) return cur
  return pickBudgetLine({ pool, itemId: line.itemId, map, companyId, deptId, budgetYear })
}
