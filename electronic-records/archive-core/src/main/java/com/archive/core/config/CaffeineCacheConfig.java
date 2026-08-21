package com.archive.core.config;

import com.archive.core.cache.BorrowTokenCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine 本地缓存配置：借阅授权 Token 缓存（替代 Redis，符合技术栈）。
 */
@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, BorrowTokenCache> borrowTokenCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(2))
                .build();
    }
}
