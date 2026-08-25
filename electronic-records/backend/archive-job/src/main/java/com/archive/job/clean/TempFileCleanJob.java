package com.archive.job.clean;

import com.archive.core.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临时文件清理：分片目录残留（超过 24 小时）与逻辑删除满 30 天的物理文件。
 */
@Component
public class TempFileCleanJob {

    private static final Logger log = LoggerFactory.getLogger(TempFileCleanJob.class);
    private static final long CHUNK_MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final int PURGE_DAYS = 30;

    private final FileService fileService;

    public TempFileCleanJob(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanTemporaryFiles() {
        try {
            int chunks = fileService.cleanStaleChunks(CHUNK_MAX_AGE_MS);
            int purged = fileService.purgeDeletedFiles(PURGE_DAYS);
            if (chunks > 0 || purged > 0) {
                log.info("临时文件清理: 分片目录 {} 个，物理清理文件 {} 个", chunks, purged);
            }
        } catch (Exception e) {
            log.warn("临时文件清理执行失败: {}", e.getMessage());
        }
    }
}
