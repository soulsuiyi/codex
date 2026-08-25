package com.archive.common.dto;

import java.io.Serializable;

/**
 * 系统运行统计视图对象。
 */
public class StatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long userCount;
    private long caseCount;
    private long fileCount;
    private long archivedFileCount;
    private long archiveRecordCount;
    private long borrowApplyCount;
    private long activeBorrowCount;
    private long apiKeyCount;

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public long getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(long caseCount) {
        this.caseCount = caseCount;
    }

    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(long fileCount) {
        this.fileCount = fileCount;
    }

    public long getArchivedFileCount() {
        return archivedFileCount;
    }

    public void setArchivedFileCount(long archivedFileCount) {
        this.archivedFileCount = archivedFileCount;
    }

    public long getArchiveRecordCount() {
        return archiveRecordCount;
    }

    public void setArchiveRecordCount(long archiveRecordCount) {
        this.archiveRecordCount = archiveRecordCount;
    }

    public long getBorrowApplyCount() {
        return borrowApplyCount;
    }

    public void setBorrowApplyCount(long borrowApplyCount) {
        this.borrowApplyCount = borrowApplyCount;
    }

    public long getActiveBorrowCount() {
        return activeBorrowCount;
    }

    public void setActiveBorrowCount(long activeBorrowCount) {
        this.activeBorrowCount = activeBorrowCount;
    }

    public long getApiKeyCount() {
        return apiKeyCount;
    }

    public void setApiKeyCount(long apiKeyCount) {
        this.apiKeyCount = apiKeyCount;
    }
}
