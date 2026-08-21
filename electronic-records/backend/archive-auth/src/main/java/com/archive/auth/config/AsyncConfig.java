package com.archive.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用 Spring 异步支持（配合 spring.threads.virtual.enabled=true 使用虚拟线程执行）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
