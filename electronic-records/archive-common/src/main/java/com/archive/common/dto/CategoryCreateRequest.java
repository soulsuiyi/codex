package com.archive.common.dto;

/**
 * 新建案件分类请求。name 必填，parentId 默认 0（根节点），sortOrder 默认 0。
 */
public class CategoryCreateRequest {

    /** 父级ID，0 表示根节点 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 排序值 */
    private Integer sortOrder;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
