<template>
  <div v-if="lines.length" class="role-hint">
    <div v-for="(l, i) in lines" :key="i" :class="l.level">{{ l.text }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { flowApi } from '../../api'

const props = defineProps({ roleCode: { type: String, default: '' } })

const lines = ref([])
let seq = 0

watch(() => props.roleCode, async (code) => {
  const c = (code || '').trim()
  if (!c) { lines.value = []; return }
  const mine = ++seq
  try {
    const r = await flowApi.roleHolders(c)
    if (mine === seq) lines.value = describe(c, r)
  } catch (e) {
    if (mine === seq) lines.value = [{ level: 'muted', text: '持有人体检暂时不可用：' + e.message }]
  }
}, { immediate: true })

/** 后端 roleHolderMatrix → 红/黄/灰三档提示（方案 §11.2 C-2 口径） */
function describe(code, r) {
  if (!r) return []
  const out = []
  if (r.roleExists === false) {
    out.push({ level: 'bad', text: `角色编码 ${code} 在 sys_role 中不存在：挂这个码的节点不会有任何人可办` })
    return out
  }
  const scopeLabel = r.orgScope === 'GROUP' ? '集团级（不分公司）' : '按单据公司取人'
  if ((r.total || 0) === 0) {
    out.push({ level: 'bad', text: `${r.roleName || code}（${scopeLabel}）没有在职的非 admin 持有人：本节点每来一张单据都会走空策略` })
    return out
  }
  out.push({
    level: 'muted',
    text: `${r.roleName || code}（${scopeLabel}）在职非 admin 持有人 ${r.total} 人`
      + (r.activeCompanies == null ? '' : `，覆盖 ${r.coveredCompanies}/${r.activeCompanies} 家在营运公司`),
  })
  if (r.orgScope !== 'GROUP' && r.activeCompanies != null && r.coveredCompanies < r.activeCompanies) {
    out.push({
      level: 'bad',
      text: `还有 ${r.activeCompanies - r.coveredCompanies} 家在营运公司该角色 0 人：这些公司的单据到本节点会走空策略（empty_strategy），要么补人、要么换角色`,
    })
  }
  if ((r.multiHolderCompanies || 0) > 0) {
    out.push({
      level: 'warn',
      text: `${r.multiHolderCompanies} 家公司有 ≥2 名持有人：自动指派按用户主键升序取第一个人，落点由主键顺序决定；需要由上一环节点名请把「取人方式」设为 PICK`,
    })
  }
  const rows = r.companies || []
  if (r.orgScope === 'GROUP') {
    const first = rows.length ? (rows[0].names || [])[0] : null
    if (first) out.push({ level: 'muted', text: `自动指派落点：${first}（全局主键最小）` })
  } else {
    rows.slice(0, 6).forEach((row) => {
      const first = (row.names || [])[0]
      out.push({
        level: 'muted',
        text: `${row.companyName}：${row.holders} 人，自动指派落到 ${first}`
          + (row.namesTruncated ? ` 等${row.holders}人` : `（${(row.names || []).join('、')}）`),
      })
    })
    if (rows.length > 6) {
      out.push({ level: 'muted', text: `另有 ${rows.length - 6} 家有该角色持有人，未逐条列出` })
    }
  }
  return out
}
</script>

<style scoped>
.role-hint { font-size: 12px; line-height: 18px; margin-top: 4px }
.role-hint .bad { color: #f56c6c }
.role-hint .warn { color: #e6a23c }
.role-hint .muted { color: #909399 }
</style>
