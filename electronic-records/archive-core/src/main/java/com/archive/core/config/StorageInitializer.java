package com.archive.core.config;

import com.archive.core.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 MinIO 桶存在；MinIO 不可用时仅告警，不阻塞应用启动。
 */
@Component
public class StorageInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageInitializer.class);

    private final StorageService storageService;

    public StorageInitializer(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            storageService.ensureBuckets();
        } catch (Exception e) {
            log.warn("MinIO 桶初始化失败，请检查 MinIO 服务: {}", e.getMessage());
        }
    }
}
