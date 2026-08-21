package com.archive.auth.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 开放 API 相关缓存：nonce 去重（防重放）。
 */
@Configuration
public class ExternalApiConfig {

    @Bean
    public Cache<String, Long> externalApiNonceCache() {
        return Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }
}
