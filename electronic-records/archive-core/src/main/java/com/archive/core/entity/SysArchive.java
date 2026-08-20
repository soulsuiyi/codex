package com.archive.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 归档记录表 sys_archive。
 */
@TableName("sys_archive")
public class SysArchive {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 案件ID */
    private Long caseId;

    /** 案号（冗余，便于查询） */
    private String caseNo;

    /** 操作人ID（档案管理员） */
    private Long operatorId;

    /** 归档文件数 */
    private Integer fileCount;

    /** 结构化文书数 */
    private Integer structDocCount;

    /** 状态: SUCCESS-成功 FAILED-失败 */
    private String status;

    /** 备注 */
    private String remark;

    /** 归档时间 */
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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
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
