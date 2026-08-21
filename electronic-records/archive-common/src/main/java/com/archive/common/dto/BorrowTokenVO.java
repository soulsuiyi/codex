package com.archive.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 借阅授权 Token 视图对象。
 */
public class BorrowTokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tokenValue;
    private LocalDateTime expireTime;
    private Boolean allowDownload;

    public BorrowTokenVO() {
    }

    public BorrowTokenVO(String tokenValue, LocalDateTime expireTime, Boolean allowDownload) {
        this.tokenValue = tokenValue;
        this.expireTime = expireTime;
        this.allowDownload = allowDownload;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }
}
