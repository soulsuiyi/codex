package com.archive.job.audit;

import com.archive.core.service.AuditArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计日志冷存储归档：每日 04:00 归档超期日志（默认 365 天）。
 */
@Component
public class AuditLogArchiveJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogArchiveJob.class);

    private final AuditArchiveService auditArchiveService;

    public AuditLogArchiveJob(AuditArchiveService auditArchiveService) {
        this.auditArchiveService = auditArchiveService;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void archiveExpiredAuditLogs() {
        try {
            auditArchiveService.archiveExpiredAuditLogs();
        } catch (Exception e) {
            log.warn("审计日志归档执行失败: {}", e.getMessage());
        }
    }
}
