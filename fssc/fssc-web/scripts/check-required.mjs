#!/usr/bin/env node
/**
 * 守卫：不许出现「假必填」。
 *
 * 假必填 = 表单项上写了 required（或标签里手打 *），但所属 el-form 没有 :rules。
 * Element Plus 只会画出红星，提交时不做任何拦截 —— 用户以为会被拦，实际空值直接发出去。
 * 这类做法通常被称作「只打 * 号，不做实际判断」。
 *
 * 用法：
 *   node scripts/check-required.mjs                # 扫 ./src
 *   node scripts/check-required.mjs src views      # 扫多个目录
 *   node scripts/check-required.mjs --warn         # 只告警不失败（存量迁移期用）
 *
 * 退出码：0 = 干净；1 = 有假必填。
 */
import fs from 'node:fs'
import path from 'node:path'

const SKIP = new Set(['node_modules', 'dist', 'dist_old', '.git', '.cache'])
const SKIP_RE = /(\.i18n_backup|_old|\.bak)/

const RE_FORM = /<el-form(?![-\w])([^>]*)>/g
const RE_ITEM = /<el-form-item(?![-\w])([^>]*?)\/?>/g
const RE_RULES = /:rules\s*=/
const RE_REQUIRED = /(?<![\w:-])required(?![\w-])/
const RE_PROP = /(?<![\w:-])prop\s*=/
const RE_STAR = /(?<!:)label\s*=\s*"[^"]*[＊*][^"]*"/

function walk(dir, out) {
  let entries
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true })
  } catch {
    return out
  }
  for (const e of entries) {
    const p = path.join(dir, e.name)
    if (SKIP_RE.test(p.replace(/\\/g, '/'))) continue
    if (e.isDirectory()) {
      if (!SKIP.has(e.name)) walk(p, out)
    } else if (e.name.endsWith('.vue')) {
      out.push(p)
    }
  }
  return out
}

/** 找到每个 el-form 的标签串和它覆盖的区间，用于判断 form-item 归属。 */
function formsOf(src) {
  const forms = []
  let m
  RE_FORM.lastIndex = 0
  while ((m = RE_FORM.exec(src))) {
    if (m[1].trimEnd().endsWith('/')) continue
    const open = m.index + m[0].length
    let depth = 1
    let i = open
    let close = -1
    while (i < src.length) {
      const next = src.indexOf('</el-form>', i)
      if (next < 0) break
      const seg = src.slice(i, next)
      depth += (seg.match(/<el-form(?![-\w])/g) || []).length
      depth -= 1
      if (depth <= 0) {
        close = next
        break
      }
      i = next + '</el-form>'.length
    }
    if (close < 0) continue
    forms.push({ attrs: m[1], start: open, end: close, hasRules: RE_RULES.test(m[1]) })
  }
  return forms
}

function check(file, problems) {
  const src = fs.readFileSync(file, 'utf8')
  if (!src.includes('<el-form')) return
  // 文件级豁免：动态生成的表单项（v-for + 运行时字段定义）静态挂不了 rules，
  // 由页面自己在提交前逐项比对。豁免必须在文件里写明理由。
  if (src.includes('check-required-ignore')) return
  const forms = formsOf(src)
  const items = []
  let m
  RE_ITEM.lastIndex = 0
  while ((m = RE_ITEM.exec(src))) {
    items.push({ attrs: m[1] || '', index: m.index })
  }
  for (const it of items) {
    const declared = RE_REQUIRED.test(it.attrs) || RE_STAR.test(it.attrs)
    if (!declared || RE_PROP.test(it.attrs)) continue
    const owner = forms.find((f) => it.index > f.start && it.index < f.end)
    if (!owner || owner.hasRules) continue
    const line = src.slice(0, it.index).split('\n').length
    const label = (it.attrs.match(/(?<!:)label\s*=\s*"([^"]*)"/) || [])[1] || ''
    problems.push({ file, line, label })
  }
}

const args = process.argv.slice(2)
const warnOnly = args.includes('--warn')
const dirs = args.filter((a) => !a.startsWith('--'))
if (dirs.length === 0) dirs.push('src')

const files = []
for (const d of dirs) walk(path.resolve(d), files)

const problems = []
for (const f of files) check(f, problems)

if (problems.length === 0) {
  console.log(`check-required: ${files.length} 个 .vue，无假必填`)
  process.exit(0)
}

console.error(`check-required: 发现 ${problems.length} 处假必填（标了必填但 el-form 没挂 :rules）`)
for (const p of problems) {
  console.error(`  ${path.relative(process.cwd(), p.file)}:${p.line}  ${p.label || '(无标签)'}`)
}
console.error('\n改法：给 el-form 挂 :rules，给 el-form-item 挂 prop；或用 formRules.js 的工厂函数。')
process.exit(warnOnly ? 0 : 1)
