package com.archive.common.dto;

import java.time.LocalDateTime;

/**
 * 新建开放 API 密钥请求。
 */
public class ApiKeyCreateRequest {

    private String appName;
    private String ipWhitelist;
    private LocalDateTime expireTime;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(String ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }
}
