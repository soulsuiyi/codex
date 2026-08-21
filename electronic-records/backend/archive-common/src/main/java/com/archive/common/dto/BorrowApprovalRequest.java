package com.archive.common.dto;

/**
 * 借阅审批请求。
 */
public class BorrowApprovalRequest {

    /** 结果: APPROVED-通过 / REJECTED-驳回 */
    private String result;

    /** 审批意见 */
    private String comment;

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
}
