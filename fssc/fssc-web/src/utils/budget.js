// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 预算明细行解析（D-P2-01）
// md_item_budget_map 是「全租户单值」映射（费用项 itemId → 某一条 bg_item 主键），
// 该主键可能属于其它公司/部门（实测 行政办公费 → id 20 = company33/一车间）。
// 直接带出既不在制单人可见域内（下拉退化显示原始 id），也会把预算占用记到别的主体。
// 因此统一先按 费用项 + 单据部门 + 单据公司 在可见预算明细里命中真实预算行，
// 映射表主键只在同公司可见时兜底；命中不到返回 null，交给人工选择。
export function pickBudgetLine({ pool, itemId, map, companyId, deptId }) {
  if (itemId == null) return null
  const eq = (a, b) => a != null && b != null && String(a) === String(b)
  const rows = pool || []
  let cand = rows.filter((b) => eq(itemId, b.itemId))
  if (!cand.length) cand = rows.filter((b) => eq(map && map[String(itemId)], b.id))
  if (!cand.length) return null
  const rank = (b) => (eq(deptId, b.deptId) ? 2 : 0) + (eq(companyId, b.companyId) ? 1 : 0)
  const best = cand.slice().sort((a, b) => rank(b) - rank(a))[0]
  // 跨主体宁可留空，也不带出别的公司的预算线
  if (companyId != null && !eq(companyId, best.companyId)) return null
  return best.id
}
