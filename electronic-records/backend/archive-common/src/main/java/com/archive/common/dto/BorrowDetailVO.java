package com.archive.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 借阅申请详情：申请信息 + 借阅文件 + 审批记录。
 */
public class BorrowDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BorrowApplyVO apply;
    private List<FileVO> files;
    private List<BorrowApprovalVO> approvals;

    public BorrowApplyVO getApply() {
        return apply;
    }

    public void setApply(BorrowApplyVO apply) {
        this.apply = apply;
    }

    public List<FileVO> getFiles() {
        return files;
    }

    public void setFiles(List<FileVO> files) {
        this.files = files;
    }

    public List<BorrowApprovalVO> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<BorrowApprovalVO> approvals) {
        this.approvals = approvals;
    }
}
