package com.fssc.connector.adapter;

import com.fssc.connector.api.ChannelType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 通道适配器注册表：按能力域路由到专属适配器。
 *
 * <p>Spring 启动时自动收集全部 {@link ChannelAdapter} Bean；未注册适配器的能力域返回 {@code null}，
 * 由 {@code DefaultConnectorFacade} 回落通用网关（保证既有能力零影响）。</p>
 */
@Component
public class ChannelAdapterRegistry {

    private final Map<ChannelType, ChannelAdapter> byType;

    public ChannelAdapterRegistry(List<ChannelAdapter> adapters) {
        Map<ChannelType, ChannelAdapter> m = new EnumMap<>(ChannelType.class);
        if (adapters != null) {
            for (ChannelAdapter a : adapters) {
                if (a != null && a.type() != null) {
                    m.put(a.type(), a);
                }
            }
        }
        this.byType = Collections.unmodifiableMap(m);
    }

    /** 取该能力域的适配器；未注册返回 null。 */
    public ChannelAdapter find(ChannelType type) {
        return type == null ? null : byType.get(type);
    }

    /** 是否已为某能力域注册专属适配器。 */
    public boolean has(ChannelType type) {
        return type != null && byType.containsKey(type);
    }

    /** 已注册适配器数量（运维诊断用）。 */
    public int size() {
        return byType.size();
    }
}
