package com.archive.common.dto;

/**
 * 借阅下载请求。
 */
public class BorrowDownloadRequest {

    /** 借阅授权 Token */
    private String tokenValue;

    /** 下载文件ID */
    private Long fileId;

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}
