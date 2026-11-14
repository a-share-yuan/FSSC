package com.fssc.framework.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysNotifyTemplate;
import com.fssc.system.mapper.SysNotifyTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息模板渲染：把「触发点 + 变量」变成可直接发给 {@link NotifyService} 的标题/正文/深链。
 *
 * <p><b>为什么不把文案写在调用方</b>：写死在 {@code handleRejected} 里意味着每加一个通知点
 * （退件、质检退回、超期未办）都得改 Java、重新打包；而且文案归业务管，不该被代码锁死。
 * 本服务把「何时通知」留在代码（触发点），把「通知什么」交给配置（模板行）。
 *
 * <p><b>fail-closed 而非 fail-loud</b>：模板缺失/停用/查询异常一律返回 {@code null}，
 * 调用方跳过通知。理由：通知是「附加动作」，不能因为模板被误删就让驳回主链路回滚——
 * 那会把「少一条消息」升级成「单子驳不掉」。
 *
 * <p><b>只做 ${var} 替换</b>：不引入 SpEL/OGNL。模板由管理员在配置面维护，
 * 支持表达式等于把表达式求值入口开到配置面（可读任意 Bean），收益远小于风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyTemplateService {

    /** 占位符：${varName}，允许字母数字下划线 */
    private static final Pattern VAR = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final SysNotifyTemplateMapper templateMapper;

    /**
     * 渲染结果。{@code linkUrl} 为 null/空表示该消息不可点击。
     *
     * @param msgType 消息类型（NOTICE/APPROVAL/ALERT），空则落 NOTICE
     */
    public record Rendered(String msgType, String title, String content, String linkUrl) {
    }

    /**
     * 按触发点渲染模板。
     *
     * @param triggerCode 触发点编码，如 {@code EXP_BILL_REJECTED}
     * @param vars        占位符变量；未提供的变量渲染为空串（不留 {@code ${x}} 残文）
     * @return 渲染结果；模板不存在、已停用或查询异常时返回 {@code null}（调用方应跳过通知）
     */
    public Rendered render(String triggerCode, Map<String, String> vars) {
        if (triggerCode == null || triggerCode.isBlank()) {
            return null;
        }
        try {
            LambdaQueryWrapper<SysNotifyTemplate> q = new LambdaQueryWrapper<SysNotifyTemplate>()
                    .eq(SysNotifyTemplate::getTriggerCode, triggerCode)
                    .eq(SysNotifyTemplate::getDelFlag, 0)
                    .eq(SysNotifyTemplate::getEnabled, 1)
                    .last("limit 1");
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                q.eq(SysNotifyTemplate::getTenantId, tenantId);
            }
            SysNotifyTemplate t = templateMapper.selectOne(q);
            if (t == null) {
                log.debug("消息模板未配置或已停用，跳过通知: trigger={}", triggerCode);
                return null;
            }
            Map<String, String> v = vars == null ? Map.of() : new LinkedHashMap<>(vars);
            String link = fill(t.getLinkTpl(), v);
            return new Rendered(
                    t.getMsgType() == null || t.getMsgType().isBlank() ? "NOTICE" : t.getMsgType(),
                    fill(t.getTitleTpl(), v),
                    fill(t.getContentTpl(), v),
                    link == null || link.isBlank() ? null : link);
        } catch (Exception ex) {
            // 模板表缺失（未执行迁移）也不得阻断业务：降级为「不通知」并留一条 WARN 供排查
            log.warn("消息模板渲染失败，跳过通知: trigger={} err={}", triggerCode, ex.getMessage());
            return null;
        }
    }

    /** 替换 {@code ${var}}；未提供的变量置空串，避免把占位符原样发给用户 */
    private static String fill(String tpl, Map<String, String> vars) {
        if (tpl == null || tpl.isEmpty()) {
            return tpl;
        }
        Matcher m = VAR.matcher(tpl);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = vars.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }
}
