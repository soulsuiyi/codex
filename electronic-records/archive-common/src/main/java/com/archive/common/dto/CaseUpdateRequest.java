package com.archive.common.dto;

/**
 * 更新案件请求。不含 caseNo 与 status（二者不可通过该接口修改）。
 */
public class CaseUpdateRequest {

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
