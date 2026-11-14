package com.fssc.bank.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.gateway.IntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 银行通道 provider 解析器（域3 · R3）。
 *
 * <p>沿用 ERP 侧既有的 {@code ErpChannelResolver} 范式：从
 * {@code ext_interface_config} 中 {@code category=BANK} 的<b>启用默认实例</b>读
 * {@code provider}，据此选出 {@link BankProviderCodec}。业务层不感知 provider 存在。</p>
 *
 * <p><b>回退策略（刻意不自动改投）</b>：通道配置的 provider 指向一个<b>未注册</b>的编解码器时，
 * <b>不静默改用通用形态</b>，而是记 WARN 并回落 {@link GenericBankCodec}——因为报文形态猜错
 * 会让银行侧拒单，而日志里的 WARN 是排查该问题的唯一线索。真正的强校验（拒绝付款）留待
 * 接入时按银行实际情况决定，此处不替业务做风险判断。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankChannelResolver {

    /** ext_interface_config.category：银行通道（与历史规则一致，配置零迁移）。 */
    public static final String CATEGORY_BANK = "BANK";

    private final IntegrationGateway integrationGateway;
    private final List<BankProviderCodec> codecs;
    private final GenericBankCodec generic;

    private volatile Map<String, BankProviderCodec> registry;

    /** provider（大写）→ 编解码器。未注册返回 null（由 activeCodec 兜底）。 */
    public BankProviderCodec codecOf(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return registry().get(provider.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 当前生效的编解码器（永不返回 null）。
     * provider 未配置 → 通用形态；provider 指向未注册实现 → 通用形态 + WARN。
     */
    public BankProviderCodec activeCodec() {
        String p = activeProvider();
        if (p == null) {
            return generic;
        }
        BankProviderCodec c = codecOf(p);
        if (c == null) {
            log.warn("[银企直联] 通道 provider={} 无对应编解码器，回落通用形态；"
                    + "若该行报文结构非通用，请补一个 BankProviderCodec 实现", p);
            return generic;
        }
        return c;
    }

    /** 当前生效的 provider（大写）；未配置返回 {@link BankProviderCodec#PROVIDER_GENERIC}。 */
    public String activeProvider() {
        ExtInterfaceConfig cfg = activeInstance();
        if (cfg != null && cfg.getProvider() != null && !cfg.getProvider().isBlank()) {
            return cfg.getProvider().trim().toUpperCase(Locale.ROOT);
        }
        return BankProviderCodec.PROVIDER_GENERIC;
    }

    /** 当前生效的通道配置（可读 apiUrl / extraConfig）；未配置返回 null。 */
    public ExtInterfaceConfig activeInstance() {
        return integrationGateway.getConfig(CATEGORY_BANK);
    }

    /** 是否已配置启用的银行通道。 */
    public boolean hasBankInstance() {
        return activeInstance() != null;
    }

    private Map<String, BankProviderCodec> registry() {
        Map<String, BankProviderCodec> m = registry;
        if (m == null) {
            synchronized (this) {
                m = registry;
                if (m == null) {
                    m = new HashMap<>();
                    for (BankProviderCodec c : codecs) {
                        m.put(c.provider().toUpperCase(Locale.ROOT), c);
                    }
                    registry = m;
                    log.info("[银企直联] 已注册银行报文编解码器 {} 个: {}",
                            m.size(), m.keySet());
                }
            }
        }
        return m;
    }
}
