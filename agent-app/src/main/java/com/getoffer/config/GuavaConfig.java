package com.getoffer.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Guava 缓存配置类。
 * <p>
 * 配置基于Guava Cache的本地缓存Bean，用于提升系统性能。
 * 默认缓存配置：写入后3秒过期。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Configuration
public class GuavaConfig {

    /**
     * 创建Guava缓存Bean。
     * <p>
     * 缓存配置：写入后3秒自动过期，适用于短期数据缓存场景。
     * </p>
     *
     * @return Guava缓存实例
     */
    @Bean(name = "cache")
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

}
