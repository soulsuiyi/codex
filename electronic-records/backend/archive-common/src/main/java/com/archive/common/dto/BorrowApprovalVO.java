package com.archive.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 借阅审批记录视图对象。
 */
public class BorrowApprovalVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long approverId;
    private String approverName;
    private String approvalStep;
    private String result;
    private String comment;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getApprovalStep() {
        return approvalStep;
    }

    public void setApprovalStep(String approvalStep) {
        this.approvalStep = approvalStep;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
