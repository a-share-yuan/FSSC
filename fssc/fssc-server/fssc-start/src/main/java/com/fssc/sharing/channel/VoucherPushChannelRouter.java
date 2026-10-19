// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 凭证推送通道运行时路由器：让「用哪个推送通道」成为管理员可配置项，改后即生效、无需重启。
 *
 * <p>取值优先级：sys_config {@code voucher.push.channel}（系统设置→全局参数，值 local/http）
 * → 为空回退配置项 {@code fssc.voucher.push.channel}（yml 默认 local）。
 * 未知值一律安全回退 local，绝不因配置错误导致推送链路不可用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherPushChannelRouter {

    public static final String CFG_KEY = "voucher.push.channel";

    private final LocalVoucherPushChannel localChannel;
    private final HttpVoucherPushChannel httpChannel;
    private final SysConfigMapper configMapper;

    /** yml 默认通道（sys_config 未设置时生效） */
    @Value("${fssc.voucher.push.channel:local}")
    private String defaultChannel;

    /** 当前生效通道名（local/http），供配置页回显 */
    public String currentChannelName() {
        String v = readConfig();
        if (v == null || v.isBlank()) {
            v = defaultChannel;
        }
        v = v == null ? "local" : v.trim().toLowerCase();
        return "http".equals(v) ? "http" : "local";
    }

    /** 当前生效通道实例 */
    public VoucherPushChannel current() {
        return "http".equals(currentChannelName()) ? httpChannel : localChannel;
    }

    private String readConfig() {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, CFG_KEY)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            return c == null ? null : c.getConfigValue();
        } catch (Exception e) {
            log.warn("读取 voucher.push.channel 失败，回退默认通道", e);
            return null;
        }
    }
}
