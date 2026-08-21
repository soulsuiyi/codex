package com.archive.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅申请请求。
 */
public class BorrowApplyRequest {

    /** 案号 */
    private String caseNo;

    /** 借阅文件ID列表（必须是归档区文件） */
    private List<Long> fileIds;

    /** 借阅理由 */
    private String reason;

    /** 是否需要下载权限 */
    private Boolean needDownload;

    /** 期望到期时间 */
    private LocalDateTime expireTime;

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public List<Long> getFileIds() {
        return fileIds;
    }

    public void setFileIds(List<Long> fileIds) {
        this.fileIds = fileIds;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getNeedDownload() {
        return needDownload;
    }

    public void setNeedDownload(Boolean needDownload) {
        this.needDownload = needDownload;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }
}
