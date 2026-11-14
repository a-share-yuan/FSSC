package com.fssc.framework.notify;

import com.fssc.framework.spi.DistributedState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 运维告警通道。
 *
 * <p>与 {@link NotifyService} 的分工：NotifyService 面向<b>业务用户</b>（审批待办、SLA 超时推给具体的人）；
 * 本服务面向<b>运维/值班</b>，接收系统级事件——定时任务连续失败、健康检查探测到组件不可用。
 * 告警走全局企微群 webhook + 运维邮件，不依赖任何租户上下文（调度线程可安全调用）。
 *
 * <p>三个设计要点：
 * <ol>
 *   <li><b>绝不因告警本身抛出异常</b>：任何渠道失败只记日志，不能反过来把调用方（如心跳任务）带崩；</li>
 *   <li><b>限流防雪崩</b>：同一标题告警在 {@link #WINDOW} 内最多 {@link #MAX_PER_WINDOW} 条，
 *       计数走 {@link DistributedState}（多实例共享同一窗口，Redis 模式跨进程一致）；
 *       限流设施不可用时按 fail-open 放行告警——宁可多推一条，不可漏掉故障；</li>
 *   <li><b>渠道可缺省</b>：webhook 未配置则回退到 {@code fssc.notify.wecom-webhook}，
 *       仍为空则只落 ERROR 日志（保证离线环境可跑，不因未配置告警而启动失败）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAlertService {

    private static final String RATE_KEY_PREFIX = "fssc:opsalert:";
    /** 同标题告警在窗口内最多推送条数 */
    private static final int MAX_PER_WINDOW = 5;
    /** 限流窗口 */
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final NotifyService notifyService;
    private final DistributedState distributedState;

    /** 运维告警 webhook（企微群机器人） */
    @Value("${fssc.notify.ops-webhook:}")
    private String opsWebhook;

    /** 回退 webhook：未单独配置运维群时复用业务通知群 */
    @Value("${fssc.notify.wecom-webhook:}")
    private String fallbackWebhook;

    /** 运维邮件接收人（逗号分隔，可空） */
    @Value("${fssc.notify.ops-mail-to:}")
    private String opsMailTo;

    /** 告警总开关 */
    @Value("${fssc.notify.ops-alert-enabled:true}")
    private boolean enabled;

    /** SMTP 主机（空则邮件降级为日志，不报错） */
    @Value("${spring.mail.host:}")
    private String smtpHost;

    /**
     * 推送一条运维告警。
     *
     * @param level ERROR / WARN / INFO（仅用于标题前缀与日志级别）
     * @param title 告警标题（同时作为限流维度，同标题同窗口合并）
     * @param content 告警正文（建议含影响面 + 已做动作 + 建议动作）
     */
    public void alert(String level, String title, String content) {
        if (!enabled) {
            log.debug("[运维告警] 告警开关关闭，跳过：{}", title);
            return;
        }
        if (!allowByRateLimit(level, title)) {
            log.warn("[运维告警] 触发限流，本窗口内不再重复推送：{}", title);
            return;
        }

        log.error("[运维告警][{}] {} | {}", level, title, content);

        String text = "【FSSC 运维告警·" + level + "】\n" + title + "\n" + content;

        // 1) 企微群机器人
        String webhook = (opsWebhook != null && !opsWebhook.isBlank()) ? opsWebhook : fallbackWebhook;
        if (webhook != null && !webhook.isBlank()) {
            try {
                notifyService.sendWecom(webhook, text);
            } catch (Exception e) {
                log.warn("[运维告警] 企微推送失败 title={} 原因={}", title, e.getMessage());
            }
        } else {
            log.warn("[运维告警] 未配置 fssc.notify.ops-webhook / wecom-webhook，仅落日志");
        }

        // 2) 运维邮件（SMTP 未配置时 NotifyService 内部降级为日志）
        if (opsMailTo != null && !opsMailTo.isBlank()) {
            for (String to : opsMailTo.split(",")) {
                String addr = to.trim();
                if (addr.isEmpty()) {
                    continue;
                }
                try {
                    notifyService.sendMail(addr, "[FSSC 运维告警] " + title, content, smtpHost);
                } catch (Exception e) {
                    log.warn("[运维告警] 邮件推送失败 to={} 原因={}", addr, e.getMessage());
                }
            }
        }
    }

    /**
     * 同标题限流。返回 true 表示放行。
     * <p>限流存储异常时 fail-open（放行），确保故障告警绝不因限流设施故障而丢失。
     */
    private boolean allowByRateLimit(String level, String title) {
        try {
            String key = RATE_KEY_PREFIX + Integer.toHexString((level + "|" + title).hashCode());
            return distributedState.tryAcquireRate(key, MAX_PER_WINDOW, WINDOW);
        } catch (Exception e) {
            log.warn("[运维告警] 限流判定异常，放行本条告警", e);
            return true;
        }
    }
}
