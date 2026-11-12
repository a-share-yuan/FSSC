// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
/**
 * check_i18n_keys.mjs — i18n 键对齐检查（完整路径口径）
 *
 * 规则：en-US.js 的每个键必须在 zh-CN.js 存在（英文缺键=漏翻译，阻断）；
 *       zh-CN 多出的键只警告（中文先行、英文后补是正常工作流）。
 *   --strict 时 zh 多出的键也阻断（发布前要求两边齐平）。
 *
 * 为什么必须用真实 import 而不是正则扫 "key:"：
 *   正则只取叶子键名会把命名空间路径丢掉。本仓语言包里有 251 种重名叶子键
 *   （pageTitle 出现 25 次），正则口径下 `expense.workbench.tabMineDocuments`
 *   这类「英文缺整条路径」的真缺失会被折叠成叶子名相同而误判为对齐。
 *   实测：正则口径报 3641/3640 键，真实完整路径键集是 3919/3919。
 *
 * 退出码：0 通过；1 有不一致。
 */
import path from 'path';
import { fileURLToPath } from 'url';
import { spawnSync } from 'child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const strict = process.argv.includes('--strict');

/** 用 Node 真 import 语言包并导出完整路径扁平键集（与 scripts/i18n_check.py 同源）。 */
function keys() {
  const dump = path.join(root, 'scripts', 'i18n_dump_keys.mjs');
  const r = spawnSync(process.execPath, [dump], { cwd: root, encoding: 'utf8' });
  if (r.status !== 0) {
    console.error(`  ✗ 无法导出语言包键集（i18n_dump_keys.mjs 退出码 ${r.status}）`);
    console.error(`    ${(r.stderr || '').split('\n').slice(0, 3).join('\n    ')}`);
    process.exit(1);
  }
  try {
    const data = JSON.parse(r.stdout);
    return { zh: new Set(data['zh-CN'] || []), en: new Set(data['en-US'] || []) };
  } catch (e) {
    console.error(`  ✗ 键集 JSON 解析失败：${e.message}`);
    process.exit(1);
  }
}

const { zh, en } = keys();

const enOnly = [...en].filter(k => !zh.has(k));
const zhOnly = [...zh].filter(k => !en.has(k));

console.log(`  zh-CN: ${zh.size} 键 | en-US: ${en.size} 键（完整路径口径）`);
let fail = false;
if (enOnly.length) {
  console.log(`  ✗ en 有 ${enOnly.length} 个键在 zh 缺失（漏翻译）:`, enOnly.slice(0, 10));
  fail = true;
} else {
  console.log('  ✓ en 键全部有中文对应');
}
if (zhOnly.length) {
  console.log(`  ${strict ? '✗' : '注意'} zh 多出 ${zhOnly.length} 个键（英文未补）:`, zhOnly.slice(0, 10));
  if (strict) fail = true;
}
if (fail) process.exit(1);
console.log('  == i18n 键对齐 PASS ==');
