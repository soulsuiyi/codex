package com.archive.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 借阅审批记录表 sys_borrow_approval。
 */
@TableName("sys_borrow_approval")
public class SysBorrowApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请ID */
    private Long applyId;

    /** 审批人ID */
    private Long approverId;

    /** 审批环节: SECRETARY-仲裁秘书 ADMIN-档案管理员 */
    private String approvalStep;

    /** 结果: APPROVED-通过 REJECTED-驳回 */
    private String result;

    /** 审批意见 */
    private String comment;

    /** 审批时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
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
