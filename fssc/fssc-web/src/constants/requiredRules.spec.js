// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import AsyncValidator from 'async-validator'
import { billRules } from './billRequired'
import { commonRules, useFormRules } from '../composables/useFormRules'

/**
 * 必填规则的取值回归：async-validator 只在规则的键"恰好只剩 required"时才用类型无关的
 * required 校验器；一旦带上 trigger/message 之外的键，它改走 string 校验器，
 * 数字/布尔值就会被判为非法——下拉选中年份、开关选"否"这类正常输入将永远提交不过。
 * 这里把取值形状钉死，防止再被写回 `{ required, message, trigger }`。
 */

async function check(rule, value) {
  try {
    await new AsyncValidator({ x: [rule] }).validate({ x: value })
    return true
  } catch {
    return false
  }
}

describe('必填规则取值形状', () => {
  it('纯必填规则（required+message）接受数字/0/布尔/日期串，拒绝空值', async () => {
    const rule = { required: true, message: '必填' }
    for (const v of [2026, 0, 3851, false, 'CNY', '2026-09-24']) {
      expect(await check(rule, v), `值 ${JSON.stringify(v)} 应判为已填`).toBe(true)
    }
    for (const v of ['', null, undefined]) {
      expect(await check(rule, v), `值 ${JSON.stringify(v)} 应判为漏填`).toBe(false)
    }
  })

  it('带 trigger 的三键必填规则会把数字判为非法（故通用规则一律不写 trigger）', async () => {
    expect(await check({ required: true, message: '必填', trigger: 'change' }, 2026)).toBe(false)
  })

  it('带 whitespace 的三键规则同样会误拦数字/布尔（这是 trigger 之外的第二个陷阱）', async () => {
    expect(await check({ required: true, whitespace: true, message: '必填' }, 2026)).toBe(false)
    expect(await check({ required: true, whitespace: true, message: '必填' }, false)).toBe(false)
  })

  it('commonRules：数字/布尔/日期串放行，空串与纯空格拦住', async () => {
    const cases = [
      ['required', commonRules.required('必填')],
      ['select', commonRules.select('必填')],
      ['date', commonRules.date('必填')],
    ]
    for (const [name, [rule]] of cases) {
      for (const v of [2026, 0, 3851, false, 'CNY', '2026-09-24']) {
        expect(await check(rule, v), `${name} 应放行 ${JSON.stringify(v)}`).toBe(true)
      }
      for (const v of ['', '   ', null, undefined]) {
        expect(await check(rule, v), `${name} 应拦住 ${JSON.stringify(v)}`).toBe(false)
      }
    }
  })

  it('commonRules.amountText 接受 el-input 的金额字符串，拒绝非数字与 0', async () => {
    const rule = commonRules.amountText('请输入金额')[0]
    expect(await check(rule, '1000')).toBe(true)
    expect(await check(rule, '0.01')).toBe(true)
    expect(await check(rule, '0')).toBe(false)
    expect(await check(rule, 'abc')).toBe(false)
    expect(await check(rule, '')).toBe(false)
  })

  it('billRules 对数字/下拉/日期类字段的正常取值全部放行', async () => {
    const rules = billRules('CLAIM', '')
    expect(await check(rules['extJson.paperAttachCount'][0], 0)).toBe(true)
    expect(await check(rules['periodYear'][0], 2026)).toBe(true)
    expect(await check(rules['applyUserId'][0], 3851)).toBe(true)
    expect(await check(rules['applyDate'][0], '2026-09-24')).toBe(true)
    expect(await check(rules['title'][0], '')).toBe(false)
    expect(await check(rules['extJson.paperAttachCount'][0], null)).toBe(false)
  })

  it('useFormRules 通用规则：下拉/日期/金额在正常取值下放行', async () => {
    const { rules } = useFormRules('EXPENSE_CLAIM')
    expect(await check(rules.value.periodYear[0], 2026)).toBe(true)
    expect(await check(rules.value.totalAmount[0], 0.01)).toBe(true)
    expect(await check(rules.value.totalAmount[0], 1200.5)).toBe(true)
    expect(await check(rules.value.totalAmount[0], '')).toBe(false)
  })

  // 全仓文件遍历在并行跑测下超过默认 5s，单独放宽到 30s（否则偶发 timeout 误报）
  it('全仓不再残留 required+message+(trigger|whitespace) 的三键必填规则', { timeout: 30000 }, () => {
    // 尾部 [^{}]*\} 用来放过"自带 validator 的规则"（那种写法走自定义校验器，不受 string 分支影响）
    const bad = /\{\s*required:\s*true,\s*(?:whitespace:\s*true,\s*)?message:\s*("[^"]*"|'[^']*'|`[^`]*`)\s*,\s*(?:whitespace:\s*true,\s*)?trigger:[^{}]*\}/
    const src = path.resolve(__dirname, '..')
    const hits = []
    const walk = (d) => fs.readdirSync(d, { withFileTypes: true })
      .flatMap((e) => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)])
    for (const f of walk(src).filter((x) => /\.(vue|js)$/.test(x) && !x.endsWith('.spec.js'))) {
      if (bad.test(fs.readFileSync(f, 'utf8'))) hits.push(path.relative(src, f))
    }
    expect(hits, '这些页面的必填规则会在正常取值下误拦提交：\n' + hits.join('\n')).toEqual([])
  })
})
