package com.archive.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 借阅申请表 sys_borrow_apply。
 */
@TableName("sys_borrow_apply")
public class SysBorrowApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 案件ID */
    private Long caseId;

    /** 案号（冗余） */
    private String caseNo;

    /** 申请人ID */
    private Long applicantId;

    /** 借阅文件ID列表（逗号分隔） */
    private String fileIds;

    /** 借阅理由 */
    private String reason;

    /** 是否需要下载权限 */
    private Boolean needDownload;

    /** 期望到期时间 */
    private LocalDateTime expireTime;

    /** 流程状态: PENDING_SECRETARY/PENDING_ADMIN/ACTIVE/RETURNED/REJECTED/EXPIRED */
    private String status;

    /** 申请时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getFileIds() {
        return fileIds;
    }

    public void setFileIds(String fileIds) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
