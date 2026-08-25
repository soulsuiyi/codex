package com.archive.common.dto;

/**
 * 更新案件分类请求（父级与层级不可修改，仅名称与排序）。
 */
public class CategoryUpdateRequest {

    private String name;
    private Integer sortOrder;

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
