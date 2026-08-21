package com.archive.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 归档记录视图对象。
 */
public class ArchiveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long caseId;
    private String caseNo;
    private Integer fileCount;
    private Integer structDocCount;
    private String status;
    private String remark;
    private LocalDateTime archivedAt;

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

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Integer getStructDocCount() {
        return structDocCount;
    }

    public void setStructDocCount(Integer structDocCount) {
        this.structDocCount = structDocCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
