package com.fssc.framework.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置（P1-1 主数据缓存）。
 * <p>
 * 用于进程内本地缓存高频只读数据（主数据科目/部门/用户等），默认 5 分钟过期，
 * 最大容量 1000 条目（足够覆盖全租户所有主数据）。
 * <p>
 * 配合 {@link org.springframework.cache.annotation.Cacheable} 注解使用。
 */
@Configuration
public class CacheConfig {

    /**
     * Caffeine 缓存管理器：默认过期时间 5 分钟，最大条目 1000。
     * <p>
     * 条目满时使用 LRU 淘汰最少使用的条目，符合企业级系统主数据缓存场景。
     */
    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats());
        // 允许 null 值（避免缓存穿透，查不到的也缓存空结果）
        manager.setAllowNullValues(true);
        return manager;
    }
}