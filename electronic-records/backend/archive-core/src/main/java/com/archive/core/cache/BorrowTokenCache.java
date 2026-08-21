package com.archive.core.cache;

import java.time.LocalDateTime;

/**
 * 借阅授权 Token 缓存对象（进程内 Caffeine）。
 */
public class BorrowTokenCache {

    private final Long applyId;
    private final Long userId;
    private final String fileIds;
    private final Boolean allowDownload;
    private final LocalDateTime expireTime;

    public BorrowTokenCache(Long applyId, Long userId, String fileIds,
                            Boolean allowDownload, LocalDateTime expireTime) {
        this.applyId = applyId;
        this.userId = userId;
        this.fileIds = fileIds;
        this.allowDownload = allowDownload;
        this.expireTime = expireTime;
    }

    public Long getApplyId() {
        return applyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFileIds() {
        return fileIds;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }
}
