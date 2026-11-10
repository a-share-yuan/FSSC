# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
# -*- coding: utf-8 -*-
"""
md2docx.py — Markdown → docx 转换器（以现役 docx 为样式模板）

用法:
    python tools/md2docx.py <md文件> [<docx输出>] [--template <模板docx>]

为什么用"现役 docx 当模板"而不是从空白 Document() 起：
  · 现役 docx 带着一整套样式定义（Heading 1-3 / Source Code / Block Text /
    Compact / Table）与页面设置；空白 Document() 只有 Word 内置样式，
    出来的观感与既有 22 篇 docx 完全不是一套。
  · 以模板新建文档再清空正文，样式表、编号、页眉页脚、主题全部继承。

映射规则（与现役 docx 的实际用样式对齐）：
  # H1        → Heading 1        ## H2 → Heading 2      ### H3 → Heading 3
  ``` 代码块   → Source Code（每行一段，与现役一致）
  > 引用      → Block Text
  - 列表      → List Paragraph；  1. 有序 → List Number
  | 表格 |    → Table（Table Grid 样式化）
  段落        → 正文（首个段落用 First Paragraph，其后 Body Text）
  **粗体** / `行内码` → run 级格式
"""
import os
import re
import sys
import shutil
import copy


# ── Markdown 解析 ─────────────────────────────────────────────────────
INLINE_CODE = re.compile(r"`([^`]+)`")
BOLD = re.compile(r"\*\*([^*]+)\*\*")
ITALIC = re.compile(r"(?<!\*)\*([^*\n]+)\*(?!\*)")
LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")


def parse_blocks(md_lines):
    """把 markdown 行列表切成块：(kind, payload)"""
    blocks = []
    i = 0
    n = len(md_lines)
    while i < n:
        line = md_lines[i]
        stripped = line.strip()

        # 代码块
        if stripped.startswith("```"):
            lang = stripped[3:].strip()
            i += 1
            code = []
            while i < n and not md_lines[i].strip().startswith("```"):
                code.append(md_lines[i])
                i += 1
            i += 1                      # 跳过结束围栏
            blocks.append(("code", (lang, code)))
            continue

        # 引用内的表格：> | a | b |  且下一行是 > |---|（版本选择表就长这样）
        if stripped.startswith(">") and i + 1 < n:
            def _q(x):
                x = x.strip()
                return x[1:].strip() if x.startswith(">") else x
            _cur, _nxt = _q(line), _q(md_lines[i + 1])
            if _cur.startswith("|") and re.match(r"^\|[\s:|-]+\|$", _nxt):
                def qcells(s):
                    s = s.strip()
                    if s.startswith("|"):
                        s = s[1:]
                    if s.endswith("|"):
                        s = s[:-1]
                    return [c.strip() for c in s.split("|")]
                header = qcells(_cur)
                i += 2
                rows = []
                while i < n and _q(md_lines[i]).startswith("|"):
                    rows.append(qcells(_q(md_lines[i])))
                    i += 1
                blocks.append(("table", (header, rows)))
                continue

        # 表格：| a | b |  且下一行是分隔行
        if stripped.startswith("|") and i + 1 < n and re.match(r"^\s*\|[\s:|-]+\|\s*$", md_lines[i + 1]):
            def cells(s):
                s = s.strip()
                if s.startswith("|"):
                    s = s[1:]
                if s.endswith("|"):
                    s = s[:-1]
                return [c.strip() for c in s.split("|")]
            header = cells(line)
            i += 2                      # 跳过表头与分隔行
            rows = []
            while i < n and md_lines[i].strip().startswith("|"):
                rows.append(cells(md_lines[i]))
                i += 1
            blocks.append(("table", (header, rows)))
            continue

        # 标题
        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            blocks.append(("h", (len(m.group(1)), m.group(2).strip())))
            i += 1
            continue

        # 水平线
        if re.match(r"^\s*(-{3,}|\*{3,}|_{3,})\s*$", line):
            blocks.append(("hr", None))
            i += 1
            continue

        # 引用（连续行合并成一块）；遇到「> |」表格头就停手，交给上面的表格分支
        if stripped.startswith(">"):
            def _qq(x):
                x = x.strip()
                return x[1:].strip() if x.startswith(">") else x
            quote = []
            while i < n and md_lines[i].strip().startswith(">"):
                _c = _qq(md_lines[i])
                if (_c.startswith("|") and i + 1 < n
                        and re.match(r"^\|[\s:|-]+\|$", _qq(md_lines[i + 1]))):
                    break
                quote.append(_c)
                i += 1
            if quote:
                blocks.append(("quote", " ".join(x for x in quote if x)))
            continue

        # 列表（连续同类型行合并成一块）
        if re.match(r"^\s*([-*+]|\d+\.)\s+", line):
            items = []
            ordered = bool(re.match(r"^\s*\d+\.\s+", line))
            while i < n and re.match(r"^\s*([-*+]|\d+\.)\s+", md_lines[i]):
                items.append(re.sub(r"^\s*([-*+]|\d+\.)\s+", "", md_lines[i]))
                i += 1
            blocks.append(("ul" if not ordered else "ol", items))
            continue

        # 空行
        if not stripped:
            i += 1
            continue

        # 段落（连续非空行合并）
        para = []
        while i < n and md_lines[i].strip() and not re.match(
                r"^\s*(#{1,6}\s|[-*+]\s|\d+\.\s|>|\||```)", md_lines[i]):
            para.append(md_lines[i].strip())
            i += 1
        if para:
            blocks.append(("p", " ".join(para)))
        else:
            i += 1
    return blocks


def add_runs(par, text):
    """把行内 markdown（粗体/斜体/行内码/链接）写进段落的 run。"""
    # 先把行内码保护起来，避免其内部的 * 被当强调
    codes = []

    def stash_code(m):
        codes.append(m.group(1))
        return "\uE000C%d\uE001" % (len(codes) - 1)

    text = INLINE_CODE.sub(stash_code, text)

    tokens = []
    pos = 0
    for m in BOLD.finditer(text):
        tokens.append(("plain", text[pos:m.start()]))
        tokens.append(("bold", m.group(1)))
        pos = m.end()
    tokens.append(("plain", text[pos:]))

    for kind, val in tokens:
        if kind == "plain":
            # 段落内的换行（markdown 硬换行）→ 保持原样
            if not val:
                continue
            par.add_run(val)
        else:
            r = par.add_run(val)
            r.bold = True
            if kind == "bold":
                r.bold = True

    # 还原行内码（用等宽字体）
    for i, c in enumerate(codes):
        ph = "\uE000C%d\uE001" % i
        for r in par.runs:
            if ph in r.text:
                parts = r.text.split(ph)
                # 重建：把该 run 拆成 [前, code(等宽), 后]
                r.text = parts[0]
                new_r = copy.deepcopy(r)
                new_r.text = c
                new_r.font.name = "Consolas"
                r._r.addnext(new_r._r)
                if len(parts) > 1:
                    tail = copy.deepcopy(r)
                    tail.text = parts[1]
                    new_r._r.addnext(tail._r)
                break



def _pick_style(doc, *candidates):
    """按顺序挑第一个模板里存在的样式名（模板样式表有限，需回退）。"""
    have = {st.name for st in doc.styles}
    for c in candidates:
        if c in have:
            return c
    return None


def _add_para(doc, style_candidates):
    """按候选列表建段落，样式不存在时退回普通段落。"""
    name = _pick_style(doc, *style_candidates)
    if name:
        try:
            return doc.add_paragraph(style=name)
        except KeyError:
            pass
    return doc.add_paragraph()


def md_to_docx(md_path, out_path, template=None):
    from docx import Document
    from docx.shared import Pt
    from docx.enum.text import WD_ALIGN_PARAGRAPH

    with open(md_path, encoding="utf-8") as f:
        md = f.read()
    md = md.replace("\r\n", "\n")
    lines = md.split("\n")

    if template and os.path.isfile(template):
        doc = Document(template)
        # 清空正文，但保留样式表 / 页面设置 / 页眉页脚
        body = doc.element.body
        for child in list(body):
            if child.tag.endswith("}sectPr"):
                continue
            body.remove(child)
    else:
        doc = Document()

    blocks = parse_blocks(lines)
    first_p = True

    for kind, payload in blocks:
        if kind == "h":
            lvl, text = payload
            par = _add_para(doc, ["Heading %d" % min(lvl, 3)])
            add_runs(par, text)
            first_p = True

        elif kind == "code":
            lang, code = payload
            for cl in code:
                par = _add_para(doc, ["Source Code"])
                r = par.add_run(cl if cl.strip() else "")
                r.font.name = "Consolas"
                r.font.size = Pt(9)
            first_p = True

        elif kind == "quote":
            par = _add_para(doc, ["Block Text"])
            add_runs(par, payload)
            first_p = True

        elif kind == "ul":
            for it in payload:
                par = _add_para(doc, ["List Paragraph", "Compact", "Body Text"])
                add_runs(par, "• " + it)
            first_p = True

        elif kind == "ol":
            for n, it in enumerate(payload):
                par = _add_para(doc, ["List Number", "Compact", "Body Text"])
                add_runs(par, "%d. %s" % (n + 1, it))
            first_p = True

        elif kind == "table":
            header, rows = payload
            t = doc.add_table(rows=1, cols=len(header))
            for st_name in ("Table Grid", "Table"):
                try:
                    t.style = st_name
                    break
                except KeyError:
                    continue
            for j, h in enumerate(header):
                cell = t.rows[0].cells[j]
                cell.text = ""
                add_runs(cell.paragraphs[0], h)
                for r in cell.paragraphs[0].runs:
                    r.bold = True
            for row in rows:
                cells = t.add_row().cells
                for j, v in enumerate(row[:len(header)]):
                    cells[j].text = ""
                    add_runs(cells[j].paragraphs[0], v)
            first_p = True

        elif kind == "hr":
            par = doc.add_paragraph()
            par.add_run("—" * 30)
            first_p = True

        else:                                    # 普通段落
            par = _add_para(doc, ["First Paragraph"] if first_p else ["Body Text", "Normal"])
            add_runs(par, payload)
            first_p = False

    doc.save(out_path)
    return out_path


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    template = None
    for i, a in enumerate(sys.argv):
        if a == "--template" and i + 1 < len(sys.argv):
            template = sys.argv[i + 1]
    if len(args) < 1:
        print("用法: python tools/md2docx.py <md> [<docx>] [--template <模板docx>]")
        return 1
    md_path = args[0]
    out_path = args[1] if len(args) > 1 else os.path.splitext(md_path)[0] + ".docx"
    if not template:
        # 默认用同目录下的现役 docx 当模板（保留样式表）；没有就退回空白
        cand = os.path.splitext(md_path)[0] + ".docx"
        if os.path.isfile(cand) and os.path.abspath(cand) != os.path.abspath(out_path):
            template = cand
    md_to_docx(md_path, out_path, template)
    print("已生成: %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
