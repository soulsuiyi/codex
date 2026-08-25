package com.archive.core.service;

/**
 * 审计日志归档服务（超期归档至 MinIO 冷存储）。
 */
public interface AuditArchiveService {

    /**
     * 归档超过保留期的审计日志（导出 JSON 至冷桶后删除 H2 记录），返回归档条数。
     */
    int archiveExpiredAuditLogs();
}
