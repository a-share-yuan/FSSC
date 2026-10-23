// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
/**
 * check_i18n_keys.mjs — i18n 键对齐检查
 *   规则：en-US.js 的每个键必须在 zh-CN.js 存在（英文缺键=漏翻译，阻断）；
 *         zh-CN 多出的键只警告（中文先行、英文后补是正常工作流）。
 *   --strict 时 zh 多出的键也阻断（发布前要求两边齐平）。
 * 退出码：0 通过；1 有不一致。
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const strict = process.argv.includes('--strict');

function keys(file) {
  const txt = fs.readFileSync(path.join(root, file), 'utf8');
  const set = new Set();
  // 形如  key: '值' / 'key': '值'，取行首缩进后的键名
  const re = /^\s*'?([A-Za-z0-9_.]+)'?\s*:/gm;
  let m;
  while ((m = re.exec(txt)) !== null) set.add(m[1]);
  return set;
}

const zh = keys('fssc/fssc-web/src/i18n/locales/zh-CN.js');
const en = keys('fssc/fssc-web/src/i18n/locales/en-US.js');

const enOnly = [...en].filter(k => !zh.has(k));
const zhOnly = [...zh].filter(k => !en.has(k));

console.log(`  zh-CN: ${zh.size} 键 | en-US: ${en.size} 键`);
let fail = false;
if (enOnly.length) {
  console.log(`  ✗ en 有 ${enOnly.length} 个键在 zh 缺失（漏翻译）:`, enOnly.slice(0, 10));
  fail = true;
} else {
  console.log('  ✓ en 键全部有中文对应');
}
if (zhOnly.length) {
  console.log(`  ${strict ? '✗' : ''} zh 多出 ${zhOnly.length} 个键（英文未补）:`, zhOnly.slice(0, 10));
  if (strict) fail = true;
}
if (fail) process.exit(1);
console.log('  == i18n 键对齐 PASS ==');
