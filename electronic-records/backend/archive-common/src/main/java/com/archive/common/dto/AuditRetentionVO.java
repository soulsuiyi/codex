package com.archive.common.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 审计日志保留与冷存储归档情况视图对象。
 */
public class AuditRetentionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 保留天数 */
    private int retentionDays;

    /** 冷存储归档文件数 */
    private long archivedFiles;

    /** 最近归档日期（无归档时为 null） */
    private LocalDate lastArchivedAt;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getArchivedFiles() {
        return archivedFiles;
    }

    public void setArchivedFiles(long archivedFiles) {
        this.archivedFiles = archivedFiles;
    }

    public LocalDate getLastArchivedAt() {
        return lastArchivedAt;
    }

    public void setLastArchivedAt(LocalDate lastArchivedAt) {
        this.lastArchivedAt = lastArchivedAt;
    }
}
