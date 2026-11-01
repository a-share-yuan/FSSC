// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bcp;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * BCP 门户开放接口（{@code /api/open/linku/**}）<b>出站</b>调用配置。
 *
 * <p>本通道承载 FSSC（内网）→ BCP（门户 8501）的受控 HTTP 调用，用于「BCP 异议审核」页
 * 拉取伙伴提交的行级异议、并回写审核结论。方向与既有 {@code fssc.openapi.linku}
 * BCP → FSSC 入站 相反，两侧共用同一套 HMAC-SHA256 约定。</p>
 *
 * <h3>密钥注入</h3>
 * 取 {@code fssc.bcp.secret}（环境变量 {@code FSSC_BCP_SECRET}），必须与 BCP 侧
 * {@code bcp.linku.secret}（环境变量 {@code BCP_LINKU_SECRET}）<b>同值</b>。
 * 为空时 fail-closed：本页的拉取与回写一律拒绝，绝不写入任何默认密钥，日志也不打印密钥值。
 */
@Slf4j
@Component
public class BcpOpenProperties {

    /** BCP 门户基址（门户进程默认 8501）。 */
    @Value("${fssc.bcp.base-url:http://127.0.0.1:8501}")
    private String baseUrl;

    /** 与 BCP 侧 bcp.linku.secret 同值的共享密钥。 */
    @Value("${fssc.bcp.secret:}")
    private String secret;

    /** 签名时间窗（秒）：|now − X-BCP-Timestamp| 超过即被 BCP 侧以 400 拒绝（防重放）。 */
    @Value("${fssc.bcp.max-skew-seconds:300}")
    private long maxSkewSeconds;

    @Value("${fssc.bcp.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${fssc.bcp.read-timeout-ms:15000}")
    private int readTimeoutMs;

    /** 运维提示规则（不含密钥值）。 */
    private static final String SECRET_HINT =
            "未注入 BCP 共享密钥：fssc.bcp.secret（环境变量 FSSC_BCP_SECRET）为空，"
                    + "须与 BCP 侧 BCP_LINKU_SECRET 配置为同一把密钥";

    @PostConstruct
    public void checkSecret() {
        if (!hasSecret()) {
            log.warn("{} —— 「BCP 异议审核」页的拉取与回写将以 400 拒绝（fail-closed）；"
                    + "需对接该页时注入密钥并重启。", SECRET_HINT);
        }
    }

    public boolean hasSecret() {
        return StringUtils.hasText(secret);
    }

    public String secretHint() {
        return SECRET_HINT;
    }

    /** 基址去掉尾部 '/'，避免拼接出双斜杠。 */
    public String baseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String b = baseUrl.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    public String secret() {
        return secret;
    }

    public long maxSkewSeconds() {
        return maxSkewSeconds;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }
}
