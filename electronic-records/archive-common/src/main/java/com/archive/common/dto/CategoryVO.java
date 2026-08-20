package com.archive.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 案件分类视图对象（树形）。
 */
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Integer level;
    private List<CategoryVO> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<CategoryVO> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryVO> children) {
        this.children = children;
    }
}
