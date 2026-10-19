// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.openapi.linku;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LinkU 开放接口（/api/open/linku/**）配置。
 *
 * <p>本通道承载 BCP（外网）→ FSSC（内网）的受控 HTTP 调用，替代原「BCP 直连 FSSC 内网库」。
 * 全部端点以 HMAC-SHA256 验签，未过签不落任何业务表。</p>
 *
 * <h3>密钥注入（F-6）</h3>
 * 优先取 {@code fssc.linku.secret}；未配置时回退既有回调密钥 {@code fssc.linku-callback.secret}
 * （BCP 双侧本就是同一把共享密钥，便于灰度期只配一处）。两者皆空则 <b>fail-closed</b>：
 * 全部开放端点以 401 拒绝。绝不写入任何默认密钥，也不在日志中打印密钥值。
 */
@Slf4j
@Component
public class LinkuOpenProperties {

    /** 开放接口专用共享密钥（推荐配置项） */
    @Value("${fssc.linku.secret:}")
    private String secret;

    /** 既有回调共享密钥（回退项；未配 fssc.linku.secret 时复用） */
    @Value("${fssc.linku-callback.secret:}")
    private String legacySecret;

    /** 缺省租户（请求体未带 tenantId 时使用） */
    @Value("${fssc.linku.tenant-id:1}")
    private Long defaultTenantId;

    /** 签名时间窗（秒） */
    @Value("${fssc.linku.max-skew-seconds:300}")
    private long maxSkewSeconds;

    /** 单次查询/写入的条数上限（防超大结果集） */
    @Value("${fssc.linku.max-page-size:500}")
    private int maxPageSize;

    /** 密钥未注入时的运维提示口径（与既有回调保持一致，便于照提示自查；不含密钥值） */
    private static final String SECRET_HINT =
            "未注入 LinkU 共享密钥：配置 fssc.linku.secret（或回退项 fssc.linku-callback.secret）为空";

    @PostConstruct
    public void checkSecret() {
        if (!hasSecret()) {
            log.warn("{} —— 全部 /api/open/linku/** 开放端点将以 401 拒绝（fail-closed）；"
                    + "需对接 BCP 数据通道时注入密钥并重启。", SECRET_HINT);
        } else if (!StringUtils.hasText(secret)) {
            log.info("fssc.linku.secret 未配置，已回退使用 fssc.linku-callback.secret 作为开放接口共享密钥");
        }
    }

    /** 生效密钥；未配置返回 null（由调用方 fail-closed 拒绝） */
    public String effectiveSecret() {
        return StringUtils.hasText(secret) ? secret : (StringUtils.hasText(legacySecret) ? legacySecret : null);
    }

    public boolean hasSecret() {
        return effectiveSecret() != null;
    }

    public String secretHint() {
        return SECRET_HINT;
    }

    public Long defaultTenantId() {
        return defaultTenantId;
    }

    public long maxSkewSeconds() {
        return maxSkewSeconds;
    }

    public int maxPageSize() {
        return maxPageSize;
    }
}
