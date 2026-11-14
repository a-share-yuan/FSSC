package com.fssc.framework.cache;

import com.fssc.framework.spi.DistributedState;
import org.springframework.stereotype.Component;

/**
 * 把条件化装配的 {@link DistributedState}（fssc.redis.enabled 决定 Local/Redis 实现）
 * 注入静态缓存门面 {@link TenantCache}，使既有静态调用方零改动。
 */
@Component
public class TenantCacheBinder {

    public TenantCacheBinder(DistributedState state) {
        TenantCache.bind(state);
    }
}
