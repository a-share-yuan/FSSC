// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.MessageService;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 消息多渠道通知（审批待办等业务事件 → 站内信 + 企业微信 webhook + 邮件）。
 * 渠道可插拔：站内信必达；企微/邮件配置在「系统设置 → 全局参数」（sys_config notify.*，每次实时读取，无需重启）。
 * 邮件为日志模拟（接入真实 SMTP：pom 加 spring-boot-starter-mail + 配置 notify.mail.*，替换 sendMail 实现）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final MessageService messageService;
    private final SysUserMapper userMapper;
    private final SysConfigMapper configMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 读取租户全局参数（sys_config notify.*），无则返回默认 */
    private String getConfig(String key, String def) {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            if (c != null && c.getConfigValue() != null) {
                return c.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("读取通知配置失败 key={}", key, e);
        }
        return def;
    }

    /**
     * 通知用户（多渠道）：站内信必发；企微/邮件按配置启用。
     *
     * <p>{@code sys_message.biz_type} 列的语义是<b>业务域</b>（列注释「业务类型，如 EXP_CLAIM」），
     * 本重载沿用它原先的写法恒记 {@code APPROVAL}，于是 {@code biz_id} 成了无域可依的裸主键——
     * 同一列值既可能是报销单 id 也可能是供应商 id，收件人点进去无法定位。
     * 需要可联查的调用方改走 {@link #notifyUser(Long, String, String, String, String, Long, String)}
     * 并显式交代业务域。
     */
    public void notifyUser(Long userId, String msgType, String title, String content, Long bizId, String linkUrl) {
        notifyUser(userId, msgType, title, content, "APPROVAL", bizId, linkUrl);
    }

    /**
     * 通知用户（多渠道，带业务域）。
     *
     * @param bizType 业务域标识，取 {@code wf_task.biz_type}/{@code wf_cc.biz_type} 原值
     *                （{@code EXP_CLAIM}/{@code SUPPLIER_ADD}/…），与 {@code bizId} 成对使用：
     *                {@code (biz_type, biz_id)} 才唯一定位一张单据（的 biz_id 规则统一）
     */
    public void notifyUser(Long userId, String msgType, String title, String content,
                           String bizType, Long bizId, String linkUrl) {
        // 1) 站内信
        try {
            messageService.send(userId, msgType, title, content,
                    bizType == null || bizType.isBlank() ? "APPROVAL" : bizType, bizId, linkUrl);
        } catch (Exception e) {
            log.warn("站内信发送失败 userId={} title={}", userId, title, e);
        }
        // 2) 企业微信 webhook（配置了 webhook 才启用）
        String wecom = getConfig("notify.wecom_webhook", "");
        if (wecom != null && !wecom.isBlank()) {
            try {
                sendWecom(wecom, title + "\n" + content);
            } catch (Exception e) {
                log.warn("企微通知失败 title={}", title, e);
            }
        }
        // 3) 邮件（用户有邮箱才发；当前为日志模拟，接入真实 SMTP 见类注释）
        SysUser u = userMapper.selectById(userId);
        if (u != null && u.getEmail() != null && !u.getEmail().isBlank()) {
            String host = getConfig("notify.mail.host", "");
            sendMail(u.getEmail(), title, content + (linkUrl == null ? "" : "\n" + linkUrl), host);
        }
        // 4) 短信：sys_config notify.sms.enabled 开关（默认关，关闭时仅记日志）；
        //    开启后按 notify.sms.provider 走服务商（log=日志模拟/aliyun/tencent），凭据 notify.sms.*
        if ("true".equalsIgnoreCase(getConfig("notify.sms.enabled", "false"))) {
            String phone = u != null ? u.getPhone() : null;
            if (phone != null && !phone.isBlank()) {
                sendSms(phone, title + "：" + content);
            }
        }
    }

    /** 企业微信群机器人 webhook（text） */
    public void sendWecom(String webhook, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("msgtype", "text", "text", Map.of("content", text));
        restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
        log.info("[企微通知] 已推送 webhook，content={}", text.substring(0, Math.min(text.length(), 50)));
    }

    /** 邮件发送结果：SENT=已交由 SMTP 发送 / SIMULATED=未配置 SMTP（模拟）/ FAILED=发送失败 */
    public record MailSendResult(String status, String message) {
    }

    /** 邮件：SMTP 取 sys_config notify.mail.host；未配置时模拟发送并返回 SIMULATED。 */
    public MailSendResult sendMail(String to, String subject, String text) {
        return sendMail(to, subject, text, getConfig("notify.mail.host", ""));
    }

    /** 邮件：smtp host 未配置时记日志（模拟）；配置后按 sys_config notify.mail.* 动态构造 JavaMailSenderImpl 真发。 */
    public MailSendResult sendMail(String to, String subject, String text, String smtpHost) {
        if (to == null || to.isBlank()) {
            return new MailSendResult("FAILED", "收件人邮箱为空");
        }
        if (smtpHost == null || smtpHost.isBlank()) {
            log.info("[邮件通知-模拟] to={} subject={} content={}", to, subject, text);
            return new MailSendResult("SIMULATED", "未配置邮件服务器（notify.mail.host），已按模拟发送留痕");
        }
        String port = getConfig("notify.mail.port", "25");
        String username = getConfig("notify.mail.username", "");
        String password = getConfig("notify.mail.password", "");
        String from = getConfig("notify.mail.from", username);
        try {
            // 非 Spring Bean：按 sys_config 动态构造，避免无 SMTP 配置时应用启动失败
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(smtpHost);
            sender.setPort(Integer.parseInt(port.trim()));
            sender.setUsername(username);
            sender.setPassword(password);
            sender.setDefaultEncoding("UTF-8");
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", (username == null || username.isBlank()) ? "false" : "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");
            if (sender.getPort() == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom((from == null || from.isBlank()) ? username : from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            sender.send(msg);
            log.info("[邮件通知] 已发送 smtp={} to={} subject={}", smtpHost, to, subject);
            return new MailSendResult("SENT", null);
        } catch (Exception e) {
            log.warn("[邮件通知] 发送失败 smtp={} to={} 原因={}", smtpHost, to, e.getMessage());
            return new MailSendResult("FAILED", e.getMessage());
        }
    }

    /**
     * 短信通知渠道。sys_config 配置：
     * notify.sms.enabled（默认 false）/ notify.sms.provider（log/aliyun/tencent）/
     * notify.sms.url、access_key、secret、sign、template。
     * 未启用或未配置 URL 时日志模拟，不影响主流程（fail-open）。
     */
    public void sendSms(String phone, String text) {
        String provider = getConfig("notify.sms.provider", "log");
        try {
            switch (provider) {
                case "aliyun", "tencent" -> {
                    String url = getConfig("notify.sms.url", "");
                    if (url == null || url.isBlank()) {
                        log.warn("[短信通知] provider={} 但 notify.sms.url 未配置，降级为日志模拟", provider);
                        break;
                    }
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-Sms-Sign", getConfig("notify.sms.sign", ""));
                    Map<String, Object> body = Map.of(
                            "phone", phone, "text", text,
                            "templateCode", getConfig("notify.sms.template", ""),
                            "accessKey", getConfig("notify.sms.access_key", ""));
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
                    log.info("[短信通知] 已发送 provider={} phone={}", provider, mask(phone));
                }
                default -> log.info("[短信通知-模拟] phone={} content={}", mask(phone), text);
            }
        } catch (Exception e) {
            log.warn("[短信通知] 发送失败 phone={} 原因={}", mask(phone), e.getMessage());
        }
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7 ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
