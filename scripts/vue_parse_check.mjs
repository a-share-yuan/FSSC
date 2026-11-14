// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
//
// .vue 解析自检（i18n 第 6 项）：逐文件跑 parse() **和** compileTemplate()。
//
// 为什么两个都要：
//   @vue/compiler-sfc 的 parse() 不编译模板，只跑它会让 "Invalid end tag"、
//   "Error parsing JavaScript expression" 这类模板级错误全部蒙混过关——
//   坏文件在 parse() 下全过、vite build 却炸。批量改源码的自动化必须靠这道闸。
//
// 用法：node scripts/vue_parse_check.mjs            # 默认扫 fssc/fssc-web/src
//       node scripts/vue_parse_check.mjs <dir>      # 指定目录
//       node scripts/vue_parse_check.mjs <dir> a.vue b.vue   # 只验指定文件（相对 src）
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { spawnSync } from 'child_process';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

//  前端工程根必须**运行时探测**，不能写死 'fssc/fssc-web'：
//   · 开源仓布局 = <root>/fssc/fssc-web
//   · 开发源布局 = <root>/fssc-web
// 写死会让同一套在另一侧找不到源码目录。
function findWebRoot(root) {
  for (const cand of [
    path.join(root, 'fssc', 'fssc-web'),
    path.join(root, 'fssc-web'),
    path.join(root, 'web'),
  ]) {
    if (fs.existsSync(path.join(cand, 'src'))) return cand;
  }
  throw new Error(
    `找不到前端工程目录。请确认仓库布局：期望 <root>/fssc/fssc-web 或 <root>/fssc-web，实际 root=${root}`,
  );
}

const WEB = findWebRoot(ROOT);
const target = path.resolve(process.argv[2] || path.join(WEB, 'src'));
const only = process.argv.slice(3);

function walk(d, out = []) {
  for (const f of fs.readdirSync(d)) {
    if (['node_modules', 'dist', '.git'].includes(f)) continue;
    const p = path.join(d, f);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (f.endsWith('.vue')) out.push(p);
  }
  return out;
}

const files = only.length ? only.map((f) => path.join(target, f)) : walk(target);

// 脚本必须落在 fssc-web 里跑：Node 以脚本自身位置上溯找 node_modules，
// 放在 scripts/ 下会 ERR_MODULE_NOT_FOUND（@vue/compiler-sfc 装在 fssc-web 里）。
const BODY = `
import { parse, compileTemplate } from '@vue/compiler-sfc';
import fs from 'fs';
const files = JSON.parse(process.argv[2]);
let ok = 0; const bad = [];
for (const f of files) {
  let src;
  try { src = fs.readFileSync(f, 'utf8'); } catch (e) { bad.push([f, 'READ: ' + e.message]); continue; }
  const { descriptor, errors } = parse(src, { filename: f });
  if (errors.length) { bad.push([f, 'SFC: ' + errors[0].message.slice(0, 90)]); continue; }
  if (descriptor.template) {
    const r = compileTemplate({ source: descriptor.template.content, filename: f, id: f });
    if (r.errors.length) { bad.push([f, 'TPL: ' + String(r.errors[0].message || r.errors[0]).slice(0, 90)]); continue; }
  }
  ok++;
}
console.log('  .vue 可编译 ' + ok + ' / 有问题 ' + bad.length);
for (const [f, m] of bad) console.log('    ' + f + ' :: ' + m);
process.exit(bad.length ? 1 : 0);
`;

const tmp = path.join(WEB, '_vue_parse_tmp.mjs');
fs.writeFileSync(tmp, BODY, 'utf8');
let code = 1;
try {
  const r = spawnSync(process.execPath, [tmp, JSON.stringify(files)],
                     { cwd: WEB, stdio: 'inherit' });
  code = r.status === null ? 1 : r.status;
} finally {
  if (fs.existsSync(tmp)) fs.unlinkSync(tmp);
}
process.exit(code);
