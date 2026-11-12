// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysNotifyTemplate;
import com.fssc.system.mapper.SysNotifyTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息模板维护（sys_notify_template）。
 *
 * <p>页面只负责「改文案、改跳转、开关」，因此本服务把三条硬约束挡在写入之前，
 * 免得配错的模板在运行期才暴露：
 * <ol>
 *   <li>{@code trigger_code} 是代码契约（见 {@code NotifyTriggers}），<b>建后不可改</b>——
 *       改了就与调用方的常量失联，通知会静默不发；</li>
 *   <li>占位符只允许 {@code ${name}}。残留未闭合的 {@code ${} 会让用户收到半截文案，
 *       故写入前直接判非法；</li>
 *   <li>{@code link_tpl} 必须是站内绝对路径（以 {@code /} 开头）。深链最终进前端路由，
 *       放开 {@code javascript:} 之类前缀等于给配置面开一个跳转注入入口。</li>
 * </ol>
 *
 * <p>不提供删除：一个触发点对应一段代码，删掉模板行只会让通知静默消失，
 * 想关就「停用」（{@code enabled=0}），配置还在、随时能开回来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNotifyTemplateService {

    /** 触发点编码：大写字母开头，允许大写字母/数字/下划线 */
    private static final Pattern TRIGGER_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Pattern VAR = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    private static final Set<String> MSG_TYPES = Set.of("NOTICE", "APPROVAL", "ALERT");

    private static final int MAX_CODE = 64;
    private static final int MAX_NAME = 128;
    private static final int MAX_TITLE = 200;
    private static final int MAX_CONTENT = 1000;
    private static final int MAX_LINK = 255;
    private static final int MAX_REMARK = 500;

    private final SysNotifyTemplateMapper templateMapper;

    public PageResult<SysNotifyTemplate> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysNotifyTemplate> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysNotifyTemplate::getTriggerCode, keyword)
                    .or().like(SysNotifyTemplate::getTriggerName, keyword));
        }
        qw.orderByAsc(SysNotifyTemplate::getId);
        Page<SysNotifyTemplate> p = templateMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(SysNotifyTemplate t) {
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板内容不能为空");
        }
        String code = trim(t.getTriggerCode());
        AssertUtil.notBlank(code, ErrorCode.PARAM_INVALID, "请填写触发点编码");
        if (!TRIGGER_CODE.matcher(code).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "触发点编码只能是大写字母、数字与下划线，且以字母开头: " + code);
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<SysNotifyTemplate>()
                .eq(SysNotifyTemplate::getTriggerCode, code)) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "触发点编码已存在: " + code + "（一个触发点只能有一条模板，如需换文案请直接编辑）");
        }
        t.setId(null);
        t.setTriggerCode(code);
        if (t.getMsgType() == null || t.getMsgType().isBlank()) {
            t.setMsgType("NOTICE");
        }
        if (t.getEnabled() == null) {
            t.setEnabled(1);
        }
        // 页面新增的模板一律不是「内置」：内置位只由升级脚本写入，用于表达「升级不覆盖」
        t.setIsBuiltin(0);
        validate(t);
        templateMapper.insert(t);
        log.info("消息模板已新增: trigger={} by=admin", code);
        return t.getId();
    }

    /**
     * 更新模板。{@code trigger_code} 不可改：它是与代码常量对齐的契约，
     * 换码等于新建一个触发点，应走「新增」，不要原地改。
     */
    public void update(SysNotifyTemplate t) {
        if (t == null || t.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少模板 ID");
        }
        SysNotifyTemplate old = templateMapper.selectById(t.getId());
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板不存在: " + t.getId());
        }
        String incoming = trim(t.getTriggerCode());
        if (incoming != null && !incoming.isEmpty() && !incoming.equals(old.getTriggerCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "触发点编码不可修改（与代码常量一一对应）；需要新触发点请点「新增」");
        }
        // 回填不可改字段，避免被请求体覆盖
        t.setTriggerCode(null);
        t.setIsBuiltin(null);
        validate(t);
        templateMapper.updateById(t);
        log.info("消息模板已更新: id={} trigger={}", t.getId(), old.getTriggerCode());
    }

    public void enable(Long id, boolean enable) {
        SysNotifyTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板不存在: " + id);
        }
        SysNotifyTemplate upd = new SysNotifyTemplate();
        upd.setId(id);
        upd.setEnabled(enable ? 1 : 0);
        templateMapper.updateById(upd);
        log.info("消息模板已{}: trigger={}", enable ? "启用" : "停用", t.getTriggerCode());
    }

    // ---------- 写入前校验 ----------

    private void validate(SysNotifyTemplate t) {
        String name = trim(t.getTriggerName());
        AssertUtil.notBlank(name, ErrorCode.PARAM_INVALID, "请填写触发点名称");
        checkLen(name, MAX_NAME, "触发点名称");
        t.setTriggerName(name);

        String title = trim(t.getTitleTpl());
        AssertUtil.notBlank(title, ErrorCode.PARAM_INVALID, "请填写标题模板");
        checkLen(title, MAX_TITLE, "标题模板");
        assertPlaceholders(title, "标题模板");
        t.setTitleTpl(title);

        String content = trim(t.getContentTpl());
        AssertUtil.notBlank(content, ErrorCode.PARAM_INVALID, "请填写正文模板");
        checkLen(content, MAX_CONTENT, "正文模板");
        assertPlaceholders(content, "正文模板");
        t.setContentTpl(content);

        String link = trim(t.getLinkTpl());
        if (link != null && !link.isEmpty()) {
            checkLen(link, MAX_LINK, "跳转链接");
            assertInternalLink(link);
            t.setLinkTpl(link);
        }
        String remark = trim(t.getRemark());
        if (remark != null && !remark.isEmpty()) {
            checkLen(remark, MAX_REMARK, "备注");
            t.setRemark(remark);
        }

        String msgType = trim(t.getMsgType());
        if (msgType == null || msgType.isEmpty()) {
            t.setMsgType("NOTICE");
        } else if (!MSG_TYPES.contains(msgType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "消息类型只能是 NOTICE／APPROVAL／ALERT: " + msgType);
        }
    }

    /**
     * 占位符校验：允许 {@code ${name}}，但写入前必须能把它们全部吃掉。
     * 剩下孤零零的 {@code ${} 说明没闭合，渲染后会把半截占位符发给用户。
     */
    private static void assertPlaceholders(String tpl, String field) {
        Matcher m = VAR.matcher(tpl);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            rest.append(tpl, last, m.start());
            last = m.end();
        }
        rest.append(tpl.substring(last));
        if (rest.indexOf("${") >= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + "里的占位符未闭合，请写成 ${变量名} 的形式");
        }
    }

    /** 深链必须是站内绝对路径：渲染结果会进前端路由，不能放开外部或脚本前缀 */
    private static void assertInternalLink(String link) {
        if (!link.startsWith("/") || link.startsWith("//")) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "跳转链接必须是站内路径（以单个 / 开头），如 /expense/my?billNo=${billNo}");
        }
        if (link.chars().anyMatch(Character::isWhitespace) || link.indexOf('\\') >= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "跳转链接不能包含空白或反斜杠");
        }
    }

    private static void checkLen(String v, int max, String field) {
        if (v != null && v.length() > max) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + "不能超过 " + max + " 个字符（当前 " + v.length() + "）");
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
