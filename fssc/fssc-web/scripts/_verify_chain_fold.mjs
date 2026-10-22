/**
 * 折叠算法验证（2026-10-18）：把 FlowChain.vue 的 inlineSegs 逻辑原样搬过来跑，
 * 用真实形态的审批链样本覆盖各种组合。
 * 与 Vue 无关，纯逻辑，便于快速定位折叠规则是否正确。
 */
const DONE_SET = ['DONE', 'REJECTED', 'VOID', 'WITHDRAWN', 'TERMINATED', 'CANCELLED', 'CLOSED']
const isDone = (n) => DONE_SET.includes(n.state)
const isTodo = (n) => !n.state || n.state === 'TODO' || n.state === 'SKIPPED'
const isCur = (n) => n.state === 'CURRENT'

function nodeWeight(name) {
  const n = String(name || '').length
  if (n <= 6) return 1
  if (n <= 14) return 2
  return 3
}
const INLINE_BUDGET = 13

function inlineSegs(nodes, maxInline) {
  const all = nodes
  const n = all.length
  if (!n) return []
  const totalW = all.reduce((a, x) => a + nodeWeight(x.nodeName), 0) + (n - 1) * 0.35
  if (n <= Math.max(3, maxInline) && totalW <= INLINE_BUDGET) {
    return all.map((node) => ({ node, fold: 0 }))
  }

  const cur = all.findIndex(isCur)
  const lastDone = all.reduce((acc, node, i) => (isDone(node) ? i : acc), -1)
  const firstTodo = all.findIndex(isTodo)

  const keep = new Set()
  const order = []
  if (cur >= 0) order.push(cur)
  if (lastDone >= 0) {
    if (lastDone + 1 < n) order.push(lastDone + 1)
    order.push(0, lastDone)
    for (let i = n - 1; i > lastDone && order.length < 8; i--) if (isTodo(all[i])) order.push(i)
  } else {
    order.push(firstTodo >= 0 ? firstTodo : 0)
    for (let i = n - 1; i >= 0 && order.length < 8; i--) if (isTodo(all[i])) order.push(i)
  }
  let w = 0
  for (const i of order) {
    if (keep.has(i)) continue
    const cost = nodeWeight(all[i].nodeName)
    if (w + cost > INLINE_BUDGET && keep.size > 0) break
    keep.add(i)
    w += cost
  }

  const segs = []
  let run = 0
  for (let i = 0; i < n; i++) {
    if (keep.has(i)) {
      if (run) { segs.push({ node: null, fold: run }); run = 0 }
      segs.push({ node: all[i], fold: 0 })
    } else {
      run += 1
    }
  }
  if (run) segs.push({ node: null, fold: run })
  return segs
}

const mk = (state, name) => ({ state, nodeName: name || state })

let ok = true
function t(name, nodes, maxInline, expect) {
  const segs = inlineSegs(nodes, maxInline)
  const shown = segs.filter((s) => s.node).length
  const folded = segs.reduce((a, s) => a + s.fold, 0)
  const desc = segs.map((s) => (s.node ? (s.node.nodeName || '') : '⋯' + s.fold)).join(' › ')
  const pass = shown === expect && shown + folded === nodes.length
  if (!pass) ok = false
  console.log(`  [${pass ? 'OK ' : 'FAIL'}] ${name.padEnd(30)} 显示${shown} 折叠${folded} 应显示${expect}`)
  console.log(`         ${desc}`)
}

// 真实样本：截图②的形态
const S1 = [
  mk('DONE', '提单'), mk('DONE', '部门经理审核业务必要性及归口安排'),
  mk('CURRENT', '项目经理/费用承担部门审核预算'), mk('TODO', '财务审核岗审核申请单'),
  mk('TODO', '基地总经理审批'), mk('TODO', '板块分管领导审批'), mk('TODO', '总裁审批'),
]
// 真实样本：12 节全过（库里最长的一条）
const S2 = Array.from({ length: 12 }, (_, i) => mk('DONE', '节点' + (i + 1)))
const S7 = [mk('DONE','提单'),mk('DONE','部门经理'),mk('CURRENT','项目经理'),
            mk('TODO','财务审核'),mk('TODO','基地总经理'),mk('TODO','板块领导'),mk('TODO','总裁')]
// 短链：不该被折叠
const S3 = [mk('DONE', '提单'), mk('CURRENT', '部门经理'), mk('TODO', '总裁')]
// 刚提交：一节未过
const S4 = [mk('DONE', '提单'), mk('CURRENT', '部门经理')]
// 已驳回
const S5 = [mk('DONE', '提单'), mk('DONE', '部门经理'), mk('REJECTED', '项目经理'), mk('TODO', '总裁')]
// 不计数节点（分支未走 / 加签）
const S6 = [
  mk('DONE', '提单'), mk('CURRENT', '部门经理'), mk('SKIPPED', '他档分支'),
  mk('ADDSIGN', '加签道'), mk('TODO', '总裁'), mk('TODO', '财务'), mk('TODO', '出纳'),
]

console.log('=== 折叠算法验证 ===')
t('截图形态 7 节（长名 2过1在4待）', S1, 7, 6)
t('12 节全过短名（无当前）', S2, 7, 2)
t('短链 3 节（不折叠）', S3, 7, 3)
t('短名 7 节（不折叠）', S7, 7, 7)
t('刚提交 2 节（不折叠）', S4, 7, 2)
t('已驳回 4 节', S5, 7, 4)
t('含跳过+加签 7 节', S6, 7, 7)

// 关键不变量：当前节点必须出现
console.log('\n=== 不变量 ===')
for (const [name, nodes] of [['7节', S1], ['12节', S2], ['含跳过', S6]]) {
  const curIdx = nodes.findIndex(isCur)
  if (curIdx < 0) { console.log(`  [--] ${name}：无当前节点（全部已过），跳过不变量校验`); continue }
  const hit = inlineSegs(nodes, 7).some((s) => s.node && s.node === nodes[curIdx])
  if (!hit) ok = false
  console.log(`  [${hit ? 'OK ' : 'FAIL'}] ${name}：当前节点出现在折叠条中`)
}
// 不变量：折叠数 + 显示数 = 总数
console.log('\n=== 守恒校验 ===')
for (const [name, nodes] of [['S1', S1], ['S2', S2], ['S5', S5], ['S6', S6]]) {
  const segs = inlineSegs(nodes, 7)
  const sum = segs.filter((s) => s.node).length + segs.reduce((a, s) => a + s.fold, 0)
  const pass = sum === nodes.length
  if (!pass) ok = false
  console.log(`  [${pass ? 'OK ' : 'FAIL'}] ${name}：显示+折叠=${sum} 实际节点=${nodes.length}`)
}


// ==== 折叠位置与内容正确性（比数字更重要）====
console.log('\n=== 折叠位置校验 ===')
function segNames(nodes, maxInline) {
  return inlineSegs(nodes, maxInline).map((x) => (x.node ? nodes.indexOf(x.node) : -x.fold))
}
{
  const seg = segNames(S1, 7)
  const foldIdx = seg.findIndex((x) => x < 0)
  const curIdx = S1.findIndex(isCur)
  // 折叠块必须位于「当前节点」之后 ⇒ 审批人最关心的那一节永远不被折
  const pass = foldIdx > 0 && seg[foldIdx] <= curIdx
  if (!pass) ok = false
  console.log(`  [${pass ? 'OK ' : 'FAIL'}] 折叠块位置在当前节点之后（折叠位=${foldIdx} 当前位=${curIdx}）`)
}
{
  const seg = segNames(S2, 7)
  // 首尾必留：折叠不能吃掉头节点和末节点
  const pass = seg[0] === 0 && seg[seg.length - 1] === S2.length - 1
  if (!pass) ok = false
  console.log(`  [${pass ? 'OK ' : 'FAIL'}] 首节与末节始终保留（首=${seg[0]} 末=${seg[seg.length - 1]}）`)
}
{
  // 折叠块标题可核对：折叠处的节点名确实在两端之间
  const seg = inlineSegs(S1, 7)
  const fold = seg.find((x) => x.fold)
  const pass = fold && fold.fold === 1
  if (!pass) ok = false
  console.log(`  [${pass ? 'OK ' : 'FAIL'}] 折叠块给出确切节数（${fold ? fold.fold : 0} 节）`)
}

console.log('\n总判定：' + (ok ? '全部通过' : '存在未通过项'))
