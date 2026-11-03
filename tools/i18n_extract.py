# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
# -*- coding: utf-8 -*-
"""
i18n_extract.py — 界面文案全量英文抽取（一条龙，可重复执行）

跑完这一条，模板属性位 + JS 侧 UI 文案全部改为 $t() / t() 调用，
两侧语言包（zh-CN.js / en-US.js）同步补齐，useI18n 接线补上。

用法:
    python tools/i18n_extract.py            # 在已恢复基线的源码上跑
    bash  scripts/i18n_full_extract.sh      # 恢复基线 + 抽取 + ，一条龙

设计约束（每条都是踩过坑之后定下来的）：
  · 只在 **UI 上下文**位置替换（模板可见属性 / ElMessage / 表格列 / 对象字面量）；
    数据字典（enums.js / billStatus.js / permMappings.js / bpmn-zh.js）一律不碰。
  · 字符串**拼接**场景绝不拆：`'中文' + x` 必须保持原样。
    判据＝「闭合引号之后（允许空白）不能是续接符 + - * / ,」。
     曾两次踩坑：字符类里 `+` 不转义会被当成量词；前瞻位置在闭合引号之后、
       其后可能有空格 ⇒ 必须写成 `(?!\s*[+\-*/%,])` 而不是 `(?![+...])`。
  · 模板字符串含插值时，只把**纯中文片段**抽成键，插值部分原样保留。
  · 语言包写入用**行数组 splice**（不用字符偏移）——
    偏移拼接会错位闭合行缩进，还可能把新块插到 `export default` 对象之外被静默丢弃。
  · vue-i18n 把 `{x}` 当占位符、`|` 当复数分隔符 ⇒ 文案里的花括号必须转义，
    且转义形式的引号要与外层字符串不同（外层单引号 ⇒ 用 {"{"}；外层双引号 ⇒ 用 {'{'}）。
  · 键名一律静态：`<域>.<模块>.<语义>`，语义由英文译文首词派生，同一中文恒定同键。
"""
import os
import re
import sys
import json
import collections
import importlib.util

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "fssc", "fssc-web", "src")
LOCALES = os.path.join(SRC, "i18n", "locales")
ZH = os.path.join(LOCALES, "zh-CN.js")
EN = os.path.join(LOCALES, "en-US.js")
TOOLS = os.path.join(REPO, "tools")


# ── 翻译表 ────────────────────────────────────────────────────────────
def _load_map(name):
    p = os.path.join(TOOLS, "i18n_map_%s.py" % name)
    spec = importlib.util.spec_from_file_location("_map_%s" % name, p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m.T


TRANS = {}
for _n in ("1", "2", "3", "4", "5"):
    TRANS.update(_load_map(_n))


# ── 复用表：命中即用既有键，不新建 ────────────────────────────────────
REUSE = {
    "状态": "common.status", "操作": "common.operation", "备注": "common.remark",
    "暂无数据": "common.empty", "全部": "common.all", "编辑": "common.edit",
    "删除": "common.delete", "保存": "common.save", "提交": "common.submit",
    "重置": "common.reset", "查询": "common.search", "刷新": "common.refresh",
    "关闭": "common.close", "返回": "common.back", "下一步": "common.next",
    "上一步": "common.prev", "确定": "common.confirm", "取消": "common.cancel",
    "提示": "common.tip", "创建时间": "common.createTime",
    "更新时间": "common.updateTime", "创建人": "common.creator",
    "请选择": "common.pleaseSelect", "请输入": "common.pleaseInput",
    "是": "common.yes", "否": "common.no", "知道了": "common.gotIt",
    "加载中...": "common.loading", "操作成功": "common.success",
    "操作失败": "common.failed", "金额": "common.amount",
    # 高频动作按钮（跨域复用，避免每个域重复造键）
    "导出": "common.export", "导入": "common.import", "新增": "common.add",
    "生成": "common.generate", "清除": "common.clear", "应用": "common.apply",
    "重试": "common.retry", "获取": "common.get", "展开": "common.expand",
    "收起": "common.collapse", "跳过": "common.skip", "通过": "common.pass",
    "发起": "common.start", "办结": "common.closeout", "详情": "common.details",
}

EXCLUDE_FILES = {
    "constants/enums.js", "constants/billStatus.js", "i18n/bpmn-zh.js",
    "utils/permMappings.js", "i18n/index.js", "i18n/element-locale.js",
}

VIS_ATTRS = [
    "label", "placeholder", "title", "text", "content", "description",
    "tip", "confirm", "empty-text", "emptyText", "sub-text", "subText",
    "header", "footer", "ok-text", "cancel-text", "aria-label",
    "tooltip", "summary", "unit", "active-text", "inactive-text",
    "loading-text", "no-data-text", "no-match-text", "no-match-tip",
]
ATTR_RE = re.compile(
    r'(?<![\w:.-])(' + "|".join(re.escape(a) for a in VIS_ATTRS) + r')="([^"]*[\u4e00-\u9fa5][^"]*)"'
)

# 模板文本节点：>文字</el-button> / >文字</b> / >文字</span> / >文字</a> 等
# 只认"整段就是中文"的节点，避免误伤夹带插值或混排的段落。
TEXT_TAGS = ("el-button", "el-button-link", "b", "strong", "span", "a", "h1", "h2", "h3", "h4")
TEXT_NODE_RE = re.compile(
    r"(>([^<>{}]*[\u4e00-\u9fa5][^<>{}]*)</(" + "|".join(TEXT_TAGS) + r")>)")

CJK = re.compile(r"[\u4e00-\u9fa5]")

UI_CTX = re.compile(
    r"(ElMessage|ElMessageBox|ElNotification|ElLoading|ElFormItem|el-form-item|"
    r"el-table-column|el-button|el-descriptions-item|el-dialog|el-drawer|"
    r"confirm\(|\$message|\$notify|notify\(|message:|title:|label:|placeholder:|"
    r"text:|content:|description:|tip:|emptyText|success\(|error\(|warning\(|info\()"
)

#  拼接保护（见文件头说明，四条约束缺一不可）：
#    ① 字符类里 + 必须转义或放末尾，否则 +* 被当量词，负向前瞻静默失效；
#    ② 前瞻位置在**闭合引号之后**，其后可能还有空格 ⇒ 必须是
#       (?!\s*[+\-*/%,])（空白之后不是续接符），不能只判紧邻字符；
#    ③ 不能排除 ) —— 那是正常实参闭合；排除它会让纯字面量也匹配不上；
#    ④ 必须排除 : 与 ? —— 三元表达式 `f(c ? 'A' : 'B')` 里若替换了 'A'，
#       会把 `f(t('k') : 'B')` 变成语法错误（Unexpected token, expected ","）。
_NO_CONCAT = r"(?!\s*[+\-*/%,:?])"

# 属性形态 / 调用实参：**只处理单引号与双引号**，反引号交给 TPL_RE。
# 理由：模板字符串里含 ${...} 插值，若按普通字符串匹配会越界吃到后面的内容
# （曾把 `{ role:'ai', text:`本次没有执行任何系统操作：${e.message ||` 整段当文案抽走）。
PROP_RE = re.compile(
    r"(?<![\w$.])(\w+)(:\s*)(['\"])([^'\"`\n]*[\u4e00-\u9fa5][^'\"`\n]*)\3" + _NO_CONCAT)
CALL_RE = re.compile(
    r"(\b[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\("
    r"[^()\n`]*?(['\"])([^'\"`\n]*[\u4e00-\u9fa5][^'\"`\n]*)\2" + _NO_CONCAT)
TPL_RE = re.compile(r"`([^`]*[\u4e00-\u9fa5][^`]*)`")


# ── 通用工具 ──────────────────────────────────────────────────────────
def strip_comments(text):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    out = []
    for line in text.split("\n"):
        i = line.find("//")
        if i >= 0:
            line = line[:i]
        out.append(line)
    return "\n".join(out)


def ns_pair(rel, top_level):
    """返回 (域, 模块)。

    · views/<域>/<文件>           → 域=<目录>
    · components/<子目录>/<文件>   → 域=<子目录>
    · components/<文件>（根目录）  → 域=<文件名语义>
    · 域与模块同名（含复数）       → 模块=main（去掉冗余段）
    · views/common/<文件> 且 <模块> 是语言包既有顶层段 → 域=<该顶层段>（对齐既有结构）
    """
    parts = rel.replace("\\", "/").split("/")
    base = parts[-1]
    stem = os.path.splitext(base)[0]
    if len(parts) >= 3 and parts[0] == "components":
        dom = parts[1]
    elif len(parts) >= 2 and parts[0] == "components":
        dom = stem
    elif len(parts) >= 2 and parts[0] in ("views", "composables"):
        dom = parts[1]
    else:
        dom = parts[0] if parts else "misc"
    dom = os.path.splitext(dom)[0]

    mod = stem
    for suf in ("Page", "Board", "Workbench", "List", "Index", "View",
                "Manage", "Center", "Config", "Table", "Tab"):
        if mod.endswith(suf) and len(mod) > len(suf):
            mod = mod[: -len(suf)]
            break
    mod = re.sub(r"^H5", "", mod) or "main"

    dl, ml = dom.lower(), mod.lower()
    same = (dl == ml or dl + "s" == ml or ml + "s" == dl)
    if same:
        mod = "main"
    if dom == "common" and not same and ("common." + ml) not in top_level and ml in top_level:
        dom, mod = ml, "main"
    return dom, mod[0].lower() + mod[1:]


def semantic_of(text):
    """由中文文本派生稳定的英文语义键名（取译文首词，不用哈希，避免不可读）。"""
    en = TRANS.get(text, "")
    if not en:
        return None
    words = re.findall(r"[A-Za-z][A-Za-z0-9]*", en)
    if not words:
        return None
    skip = {"the", "a", "an", "of", "for", "to", "in", "on", "by", "and", "or"}
    while words and words[0].lower() in skip:
        words = words[1:]
    if not words:
        words = re.findall(r"[A-Za-z][A-Za-z0-9]*", en)
    if not words:
        return None
    w = words[0]
    return w[0].upper() + w[1:]


class Alloc:
    """键名分配器：同一中文恒定同键；冲突加序号。"""

    def __init__(self, existing):
        self.existing = set(existing)
        self.new = {}

    def alloc(self, dom, mod, text, semantic):
        if text in REUSE:
            return REUSE[text]
        if text in self.new:
            return self.new[text]
        base = "%s.%s.%s" % (dom, mod, semantic)
        key, i = base, 2
        while key in self.existing or key in self.new.values():
            key = "%s%d" % (base, i)
            i += 1
        self.new[text] = key
        return key


def iter_src_files():
    for root, dirs, fs in os.walk(SRC):
        dirs[:] = [d for d in dirs if d not in ("node_modules", "dist", "dist36", "i18n")]
        for f in fs:
            if not f.endswith((".vue", ".js")):
                continue
            p = os.path.join(root, f)
            rel = os.path.relpath(p, SRC).replace("\\", "/")
            if rel in EXCLUDE_FILES or "/i18n/" in "/" + rel:
                continue
            yield p, rel


# ── 语言包写入（行数组 splice，无偏移量） ────────────────────────────
def _top_blocks(lines):
    out = []
    i = 0
    while i < len(lines):
        m = re.match(r"^  ([A-Za-z0-9_$]+):\s*\{[ \t]*$", lines[i])
        if m:
            j = i + 1
            end = None
            while j < len(lines):
                if re.match(r"^  \},?\s*$", lines[j]):
                    end = j
                    break
                j += 1
            if end is not None:
                out.append((m.group(1), i, end))
                i = end + 1
                continue
        i += 1
    return out


def _mod_blocks(lines, start, end):
    out = []
    i = start
    while i < end:
        m = re.match(r"^    ([A-Za-z0-9_$]+):\s*\{[ \t]*$", lines[i])
        if m:
            j = i + 1
            e = None
            while j < end:
                if re.match(r"^    \},?\s*$", lines[j]):
                    e = j
                    break
                j += 1
            if e is not None:
                out.append((m.group(1), i, e))
                i = e + 1
                continue
        i += 1
    return out


def append_entries(lines, entries, lang):
    """entries: {"dom.mod": {sem: (zh, en)}}；就地修改 lines，返回新增键数。"""
    nested = {}
    for k, v in entries.items():
        if "." in k:
            dom, mod = k.split(".", 1)
            nested.setdefault(dom, {})[mod] = v
        else:
            nested.setdefault(k, {}).update(v)

    added = 0
    for dom, mods in nested.items():
        blocks = {n: (s, e) for n, s, e in _top_blocks(lines)}
        if dom not in blocks:
            # 新建顶层段：插到最后一个顶层段**之前**（插在闭合行之后会落到 export 外）
            newlines = ["  %s: {" % dom]
            for mod, sems in sorted(mods.items()):
                newlines.append("    %s: {" % mod)
                for sem, vals in sorted(sems.items()):
                    v = vals[0] if lang == "zh" else vals[1]
                    newlines.append("      %s: %s," % (sem, json.dumps(v, ensure_ascii=False)))
                newlines.append("    },")
            newlines.append("  },")
            if blocks:
                pos = max(blocks.values(), key=lambda se: se[1])[0]
            else:
                pos = next((i for i, l in enumerate(lines)
                            if l.strip().startswith("export default")), -1) + 1
            lines[pos:pos] = newlines
            added += sum(len(s) for s in mods.values())
            continue

        ts, te = blocks[dom]
        mblocks = {n: (s, e) for n, s, e in _mod_blocks(lines, ts, te)}
        for mod, sems in sorted(mods.items()):
            if mod in mblocks:
                ms, me = mblocks[mod]
                existing = set()
                for ln in lines[ms + 1:me]:
                    mm = re.match(r"^\s*([A-Za-z0-9_$]+)\s*:", ln)
                    if mm:
                        existing.add(mm.group(1))
                ins = []
                for sem, vals in sorted(sems.items()):
                    if sem in existing:
                        continue
                    v = vals[0] if lang == "zh" else vals[1]
                    ins.append("      %s: %s," % (sem, json.dumps(v, ensure_ascii=False)))
                if ins:
                    lines[me:me] = ins
                    added += len(ins)
            else:
                newlines = ["    %s: {" % mod]
                for sem, vals in sorted(sems.items()):
                    v = vals[0] if lang == "zh" else vals[1]
                    newlines.append("      %s: %s," % (sem, json.dumps(v, ensure_ascii=False)))
                newlines.append("    },")
                lines[te:te] = newlines
                added += len(sems)
            blocks = {n: (s, e) for n, s, e in _top_blocks(lines)}
            ts, te = blocks[dom]
            mblocks = {n: (s, e) for n, s, e in _mod_blocks(lines, ts, te)}
    return added


def apply_to_file(path, entries, lang):
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read()
    nl = "\r\n" if "\r\n" in raw else "\n"
    lines = raw.replace("\r\n", "\n").split("\n")
    added = append_entries(lines, entries, lang)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(nl.join(lines))
    return added


# ── 阶段一：模板可见属性位 ────────────────────────────────────────────
def stage1(existing, top_level, alloc, new_entries):
    """抽取模板可见属性位。

     **不能因为文件已含 $t() 就跳过**——很多文件只接了 i18n（哪怕一处），
       模板里仍有大量未抽的中文属性（实测 ArchiveBoard.vue 有 200+ 处）。
       跳过条件只看「属性值本身是否已是取词调用」。
    """
    changed = attr_replaced = 0
    for path, rel in iter_src_files():
        raw = open(path, encoding="utf-8", errors="ignore").read()
        m = re.search(r"<template>(.*?)</template>", raw, re.S)
        if not m:
            continue
        tpl = m.group(1)
        tpl_start = m.start(1)
        dom, mod = ns_pair(rel, top_level)
        hits = []

        def repl(mm):
            nonlocal hits
            attr, val = mm.group(1), mm.group(2)
            # 值已是取词调用 / 含插值 ⇒ 不动
            if "$t(" in val or "${" in val:
                return mm.group(0)
            text = val.strip()
            if not text or not re.search(r"[\u4e00-\u9fa5]", text):
                return mm.group(0)
            if text not in TRANS:
                return mm.group(0)
            sem = semantic_of(text)
            if not sem:
                return mm.group(0)
            key = alloc.alloc(dom, mod, text, sem)
            if key not in existing and key not in REUSE.values():
                ps = key.split(".")
                if len(ps) == 3:
                    new_entries["%s.%s" % (ps[0], ps[1])][ps[2]] = (text, TRANS[text])
            hits.append((attr, text, key))
            return ":%s=\"$t('%s')\"" % (attr, key)

        new_tpl = ATTR_RE.sub(repl, tpl)

        # ② 模板文本节点：>刷新</el-button> → {{ $t('...') }}
        def text_repl(mm):
            nonlocal hits
            whole, inner, tag = mm.group(1), mm.group(2), mm.group(3)
            text = inner.strip()
            if not text or "$t(" in text or "${" in text:
                return whole
            if not re.search(r"[\u4e00-\u9fa5]", text):
                return whole
            if text not in TRANS:
                return whole
            sem = semantic_of(text)
            if not sem:
                return whole
            key = alloc.alloc(dom, mod, text, sem)
            if key not in existing and key not in REUSE.values():
                ps = key.split(".")
                if len(ps) == 3:
                    new_entries["%s.%s" % (ps[0], ps[1])][ps[2]] = (text, TRANS[text])
            hits.append((tag, text, key))
            return ">{{ $t('%s') }}</%s>" % (key, tag)

        new_tpl = TEXT_NODE_RE.sub(text_repl, new_tpl)

        if new_tpl != tpl and hits:
            new_raw = raw[:tpl_start] + new_tpl + raw[tpl_start + len(tpl):]
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.write(new_raw)
            changed += 1
            attr_replaced += len(hits)
    return changed, attr_replaced


# ── 阶段二：JS 侧 UI 上下文 ──────────────────────────────────────────
def stage2(existing, top_level, alloc, new_entries):
    changed_files = []
    total = 0
    need_wire = []
    for path, rel in iter_src_files():
        raw = open(path, encoding="utf-8", errors="ignore").read()
        m = re.search(r"<script[^>]*>(.*?)</script>", raw, re.S)
        if m:
            script_start, script_end = m.start(1), m.end(1)
            head, tail = raw[:m.start(1)], raw[m.end(1):]
        else:
            if not path.endswith(".js"):
                continue
            script_start, script_end = 0, len(raw)
            head, tail = "", ""
        script = raw[script_start:script_end]
        if not CJK.search(script):
            continue
        dom, mod = ns_pair(rel, top_level)
        used_keys = []
        out_lines = []
        file_hits = 0

        for line in script.split("\n"):
            if (not CJK.search(line) or "$t(" in line
                    or re.search(r"(?<![A-Za-z0-9_$.])t\('", line)
                    or not UI_CTX.search(line)):
                out_lines.append(line)
                continue
            new_line = line

            def tpl_sub(mm):
                nonlocal file_hits
                body = mm.group(1)
                if not CJK.search(body):
                    return mm.group(0)
                # 模板字符串**跨行**时（真实业务里常见），整段跳过——
                # 否则分段后可能把源码片段当文案抽走。
                if "\n" in body:
                    return mm.group(0)
                segs = re.split(r"(\$\{[^}]*\})", body)
                for i, seg in enumerate(segs):
                    if seg.startswith("${"):
                        continue
                    pure = seg.strip()
                    # 只抽**完整中文短语**：不含代码符号（半角+全角标点、括号、引号等）
                    # 也不含未闭合的插值残留，否则会把源码片段当文案抽走。
                    if not pure or not CJK.search(pure):
                        continue
                    if re.search(r"[,;:=(){}\[\]<>|&!@#$%^*`\"'，。；：、（）【】《》！？]", pure):
                        continue
                    if pure not in TRANS:
                        continue
                    sem = semantic_of(pure)
                    if not sem:
                        continue
                    key = alloc.alloc(dom, mod, pure, sem)
                    used_keys.append(key)
                    segs[i] = seg.replace(pure, "${t('%s')}" % key)
                    file_hits += 1
                return "`" + "".join(segs) + "`"

            new_line = TPL_RE.sub(tpl_sub, new_line)

            def prop_sub(mm):
                nonlocal file_hits
                k, colon, q, val = mm.group(1), mm.group(2), mm.group(3), mm.group(4)
                if k in ("if", "for", "while", "return", "case", "else",
                         "const", "let", "var"):
                    return mm.group(0)
                pure = val.strip()
                if not pure or pure not in TRANS:
                    return mm.group(0)
                sem = semantic_of(pure)
                if not sem:
                    return mm.group(0)
                key = alloc.alloc(dom, mod, pure, sem)
                used_keys.append(key)
                file_hits += 1
                return "%s%st('%s')" % (k, colon, key)

            new_line = PROP_RE.sub(prop_sub, new_line)

            def call_sub(mm):
                nonlocal file_hits
                fn, q, val = mm.group(1), mm.group(2), mm.group(3)
                if fn in ("t", "parseInt", "parseFloat", "String", "Number", "Boolean"):
                    return mm.group(0)
                pure = val.strip()
                if not pure or pure not in TRANS:
                    return mm.group(0)
                sem = semantic_of(pure)
                if not sem:
                    return mm.group(0)
                key = alloc.alloc(dom, mod, pure, sem)
                used_keys.append(key)
                file_hits += 1
                #  匹配只覆盖到闭合引号（结尾的 ) 在原文里，未被捕获），
                #    所以这里只能补 t('key') 的**一个**右括号，不能补两个。
                return "%s(t('%s')" % (fn, key)

            new_line = CALL_RE.sub(call_sub, new_line)
            out_lines.append(new_line)

        if file_hits == 0:
            continue
        new_script = "\n".join(out_lines)
        new_raw = (head + new_script + tail) if m else new_script
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(new_raw)
        changed_files.append((rel, file_hits))
        total += file_hits
        for key in used_keys:
            if key in existing or key in REUSE.values():
                continue
            ps = key.split(".")
            if len(ps) != 3:
                continue
            for zh_text, k in alloc.new.items():
                if k == key:
                    new_entries["%s.%s" % (ps[0], ps[1])][ps[2]] = (zh_text, TRANS[zh_text])
                    break
        if "useI18n" not in new_raw:
            need_wire.append(rel)
    return changed_files, total, need_wire


# ── 花括号转义（vue-i18n 消息语法） ──────────────────────────────────
def esc_braces(val, q):
    """把文案里的花括号转义成 vue-i18n 字面量形式。

     四条约束（缺一即坏）：
      ① **已转义的** `{'|'}` / `{'{'}` 形式必须原样保留——
         再转一次会产生嵌套占位符，报 "Not allowed nest placeholder"。
      ② 合法占位符 `{name}`（纯标识符）不动；其它 `{...}` 才转义。
      ③ 转义形式用**与外层字符串相同**的引号：外层单引号 ⇒ `{'{'}`，
         外层双引号 ⇒ `{"{"}。（JS 里 `\'` / `\"` 是合法转义，不冲突。）
      ④ 裸的 `|` 是 vue-i18n 的复数分隔符（会静默截成第一支），要转义成 {'|'}。
    """
    # ③ 转义形式用**双引号**（当外层是单引号时）或单引号（外层双引号时）——
    #    这样写回时只需转义外层同种引号，转义形式本身不受影响。
    other = '"' if q == "'" else "'"
    left = "{" + other + "{" + other + "}"
    right = "{" + other + "}" + other + "}"
    pipe = "{" + other + "|" + other + "}"

    # ① 先把已转义片段保护起来（用占位符标记，最后还原）
    #     内部允许含 { } —— 转义形式 {'{'} / {'}'} 本身就带花括号。
    protected = []

    def protect(m):
        protected.append(m.group(0))
        return "\x00P%d\x00" % (len(protected) - 1)

    val = re.sub(r"\{" + re.escape(q) + r".*?" + re.escape(q) + r"\}", protect, val)

    # ② ④ 转义未转义的花括号与裸竖线
    def rep(m):
        inner = m.group(1)
        if re.fullmatch(r"[A-Za-z0-9_]+", inner):
            return m.group(0)          # 合法占位符 → 保留
        return left + inner + right

    val = re.sub(r"\{([^{}]*)\}", rep, val)
    val = re.sub(r"(?<![\w{])\|(?![\w}])", pipe, val)

    # ③ 还原被保护的片段
    for i, s in enumerate(protected):
        val = val.replace("\x00P%d\x00" % i, s)
    return val


def fix_message_braces():
    total = 0
    for fn in ("zh-CN.js", "en-US.js"):
        p = os.path.join(LOCALES, fn)
        lines = open(p, encoding="utf-8").read().replace("\r\n", "\n").split("\n")
        changed = 0
        for i, l in enumerate(lines):
            #  值部分必须用 (?:[^'\\]|\\.)* 形式：贪婪的 (.*) 会在 \' 上提前截断，
            #    把 `'...{\'|\'}分隔）'` 切成半截，转义就会产生嵌套。
            m = re.match(r"^(\s*[\w$]+:\s*)('(?:[^'\\]|\\.)*'|\"(?:[^\"\\]|\\.)*\")(,?)$", l)
            if not m:
                continue
            head, lit, tail = m.groups()
            q = lit[0]
            # 值里的转义引号 \' / \" 还原成裸引号，转义器才能识别
            # 「已转义片段」并原样保留。
            val = lit[1:-1].replace("\\" + q, q)
            if "{" not in val and "}" not in val and "|" not in val:
                continue
            new = esc_braces(val, q)
            # 写回时把值里的裸引号重新转义，避免提前闭合字符串。
            #  转义形式用的是**另一种**引号（外层单引号⇒转义形式用双引号），
            #    所以这里只需转义与外层同种的引号，不会破坏转义形式。
            new = new.replace(q, "\\" + q)
            if new != lit[1:-1]:
                lines[i] = "%s%s%s%s%s" % (head, q, new, q, tail)
                changed += 1
        open(p, "w", encoding="utf-8", newline="").write("\n".join(lines))
        total += changed
    return total


# ── useI18n 接线 ──────────────────────────────────────────────────────
def ensure_vue_i18n(path):
    """给 .vue（<script setup>）补 useI18n 接线。

     关键：Vue 的 `defineProps` 宏**不能引用 setup 内的局部变量**
       （"defineProps() in <script setup> cannot reference locally declared
       variables"）——而 `const { t } = useI18n()` 就是局部解构。
       所以当文件里有 `defineProps({ ... default: t('...') })` 时，
       必须改用模块级的 `i18n.global.t`（从 @/i18n import）。
    """
    raw = open(path, encoding="utf-8", errors="ignore").read()
    m = re.search(r"<script[^>]*setup[^>]*>(.*?)</script>", raw, re.S)
    if not m:
        return False
    script = m.group(1)
    if re.search(r"const\s*\{[^}]*\bt\b[^}]*\}\s*=\s*useI18n\s*\(", script):
        return False

    # ① defineProps 宏里用到了 t(...) ⇒ 走 i18n.global.t（模块级）
    dp = re.search(r"defineProps\s*\(\s*\{", script)
    in_props = False
    depth = 0
    uses_t_in_props = False
    if dp:
        i = dp.end() - 1
        depth = 0
        while i < len(script):
            if script[i] == "{":
                depth += 1
            elif script[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        block = script[dp.start():i]
        if re.search(r"(?<![\w$.])t\(", block):
            uses_t_in_props = True

    lines = script.split("\n")
    last_import = max((i for i, l in enumerate(lines)
                       if re.match(r"^\s*import\b", l)), default=-1)
    has_import = re.search(r"import\s*\{([^}]*)\}\s*from\s*['\"]vue-i18n['\"]", script)

    if uses_t_in_props:
        # 把 defineProps 块内的 t(' → i18n.global.t('
        new_script = script
        # 定位块范围（按字符）
        dp2 = re.search(r"defineProps\s*\(\s*\{", new_script)
        i = dp2.end() - 1
        depth = 0
        while i < len(new_script):
            if new_script[i] == "{":
                depth += 1
            elif new_script[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        block = new_script[dp2.start():i]
        new_block = re.sub(r"(?<![\w$.])t\(", "i18n.global.t(", block)
        new_script = new_script[:dp2.start()] + new_block + new_script[i:]
        lines = new_script.split("\n")
        last_import = max((i for i, l in enumerate(lines)
                           if re.match(r"^\s*import\b", l)), default=-1)
        # 补 i18n 导入（@/i18n）
        # i18n 导入路径：src/components/X.vue → ../i18n；src/views/x/Y.vue → ../../i18n
        rel_file = os.path.relpath(path, SRC).replace("\\", "/")
        depth = rel_file.count("/")          # src/components/X.vue → 1；src/views/x/Y.vue → 2
        prefix = ("../" * depth) if depth else "./"
        lines.insert(last_import + 1, "import { i18n } from '%si18n'" % prefix)
        new_script = "\n".join(lines)
    else:
        if has_import:
            names = has_import.group(1)
            if re.search(r"(^|[\s,])t($|[\s,])", names):
                lines.insert(last_import + 1, "const { t } = useI18n()")
            else:
                new_names = (names.rstrip() + " " if names.strip() else "") + "t "
                lines[last_import] = has_import.group(0).replace(names, new_names, 1)
                lines.insert(last_import + 1, "const { t } = useI18n()")
        else:
            lines[last_import + 1:last_import + 1] = [
                "import { useI18n } from 'vue-i18n'",
                "const { t } = useI18n()",
            ]
        new_script = "\n".join(lines)

    raw = raw[:m.start(1)] + new_script + raw[m.end(1):]
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(raw)
    return True


def ensure_js_i18n(path):
    """给 .js 模块补 i18n 全局取词。

    模块顶层不能调 useI18n()，改用 i18n.global.t（legacy:false 下可用）。
    同时把本文件里 JS 阶段写入的 t('key') 改成 i18n.global.t('key')。

     必须处理「文件里一条 import 都没有」的情况：
       router/routes/*.js 原本就是纯 `export default [...]`（无任何 import），
       若这里用 `if last_import < 0: return False` 直接放弃，
       结果是**既没插 import、也没把 t( 改成 i18n.global.t(** ⇒
       模块加载即 ReferenceError: t is not defined ⇒ 整个路由表挂掉 ⇒ 白屏。
    """
    raw = open(path, encoding="utf-8", errors="ignore").read()
    if re.search(r"from\s*['\"](\.\./)+i18n['\"]", raw) or "from '@/i18n'" in raw:
        return False
    new_raw = re.sub(r"(?<![\w$.])t\(", "i18n.global.t(", raw)
    lines = new_raw.split("\n")
    # 插入点：最后一条 import 之后；没有 import 则插到首条 import/export default 之前
    last_import = max((i for i, l in enumerate(lines)
                       if re.match(r"^\s*import\b", l)), default=-1)
    rel = os.path.relpath(path, SRC).replace("\\", "/")
    # src/a/b.js → ../i18n（往上 b.js 所在目录的层级）；src/x.js → ./i18n
    depth = rel.count("/")          # src/x.js → 0；src/a/b.js → 1
    prefix = ("../" * depth) if depth else "./"
    imp_line = "import { i18n } from '%si18n'" % prefix
    if last_import >= 0:
        lines.insert(last_import + 1, imp_line)
    else:
        anchor = next((i for i, l in enumerate(lines)
                       if re.match(r"^\s*(import\b|export default|export \{)", l)), None)
        if anchor is None:
            # 连 export 都没有 ⇒ 纯注释文件，无需接线
            return False
        lines.insert(anchor, imp_line)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write("\n".join(lines))
    return True


# ── 已知缺陷修复（与抽取无关，但必须一起做，否则不过） ──────────
def fix_known_defects():
    """修 i18n 会拦下的既有缺陷。

    ① FlowConfig.vue 的 t('system.flow.' + key) 动态拼接——
       拼接会让残留扫描/悬空引用校验/回退全部失效，必拦。
    ② test-setup.js 固定 locale 为 zh-CN——
       i18n 基座的 detectLocale() 按 localStorage / 浏览器语言探测，
       jsdom 里结果不稳定（可能是 en-US），页面文案随环境漂移、断言忽过忽不过。
    """
    fixed = []
    p = os.path.join(SRC, "views", "system", "FlowConfig.vue")
    if not os.path.isfile(p):
        return fixed
    raw = open(p, encoding="utf-8", errors="ignore").read()
    old = """function simTypeLabel(type) {
  const key = { task: 'simTypeTask', gateway: 'simTypeGateway', join: 'simTypeJoin', end: 'simTypeEnd', error: 'simTypeError' }[type]
  return key ? t('system.flow.' + key) : type
}"""
    new = """function simTypeLabel(type) {
  // 静态键映射：i18n 禁止动态拼接 key
  // （拼接会让残留扫描/悬空引用校验/回退全部失效）。
  const key = {
    task: 'system.flow.simTypeTask',
    gateway: 'system.flow.simTypeGateway',
    join: 'system.flow.simTypeJoin',
    end: 'system.flow.simTypeEnd',
    error: 'system.flow.simTypeError',
  }[type]
  return key ? t(key) : type
}"""
    if old in raw:
        raw = raw.replace(old, new, 1)
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(raw)
        fixed.append("FlowConfig.vue 动态 key 拼接")

    tp = os.path.join(SRC, "test-setup.js")
    if os.path.isfile(tp):
        t_raw = open(tp, encoding="utf-8", errors="ignore").read()
        if "config.global.plugins = [ElementPlus, i18n]" in t_raw and \
                "i18n.global.locale.value" not in t_raw:
            t_raw = t_raw.replace(
                "config.global.plugins = [ElementPlus, i18n]",
                "config.global.plugins = [ElementPlus, i18n]\n"
                "// locale 固定基准语言：detectLocale() 在 jsdom 里探测结果不稳定\n"
                "// （可能是 en-US），会让页面文案随环境漂移、断言忽过忽不过。\n"
                "i18n.global.locale.value = 'zh-CN'", 1)
            with open(tp, "w", encoding="utf-8", newline="") as f:
                f.write(t_raw)
            fixed.append("test-setup.js 固定 locale=zh-CN")
    return fixed


# ── main ──────────────────────────────────────────────────────────────
def main():
    import subprocess
    r = subprocess.run(["node", os.path.join(REPO, "scripts", "i18n_dump_keys.mjs")],
                       cwd=REPO, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        print("!! 语言包键集导出失败：", (r.stderr or "")[:500])
        return 1
    existing = set(json.loads(r.stdout)["zh-CN"])
    top_level = {k.split(".", 1)[0] for k in existing}
    alloc = Alloc(existing)
    new_entries = collections.defaultdict(dict)

    c1, n1 = stage1(existing, top_level, alloc, new_entries)
    print("[阶段一] 模板属性位：%d 处 / %d 个文件" % (n1, c1))

    c2, n2, need_wire = stage2(existing, top_level, alloc, new_entries)
    print("[阶段二] JS 侧 UI 文案：%d 处 / %d 个文件" % (n2, len(c2)))
    print("          需补 useI18n 接线：%d 个文件" % len(need_wire))

    zh_add = apply_to_file(ZH, dict(new_entries), "zh")
    en_add = apply_to_file(EN, dict(new_entries), "en")
    print("[语言包] zh-CN +%d 行 | en-US +%d 行 | 新增键 %d 枚"
          % (zh_add, en_add, sum(len(v) for v in new_entries.values())))

    fixed = fix_message_braces()
    print("[转义] 修复花括号 %d 条" % fixed)

    defects = fix_known_defects()
    for d in defects:
        print("[缺陷修复] %s" % d)

    vued = jsd = 0
    for rel in need_wire:
        p = os.path.join(SRC, rel.replace("/", os.sep))
        if not os.path.isfile(p):
            continue
        if p.endswith(".vue"):
            if ensure_vue_i18n(p):
                vued += 1
        else:
            if ensure_js_i18n(p):
                jsd += 1
    print("[接线] .vue %d 个 / .js %d 个" % (vued, jsd))
    return 0


if __name__ == "__main__":
    sys.exit(main())
