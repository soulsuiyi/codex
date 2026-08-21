package com.archive.common.dto;

/**
 * 新建案件请求。caseNo/caseName/categoryId 必填，其余可选。
 */
public class CaseCreateRequest {

    /** 案号（唯一） */
    private String caseNo;

    /** 案件名称 */
    private String caseName;

    /** 分类ID */
    private Long categoryId;

    /** 案件类型 */
    private String caseType;

    /** 承办人ID */
    private Long handlerId;

    /** 备注 */
    private String remark;

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
