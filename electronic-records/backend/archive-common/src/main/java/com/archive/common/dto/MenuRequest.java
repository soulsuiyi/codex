package com.archive.common.dto;

/**
 * 新建/更新菜单请求。
 */
public class MenuRequest {

    /** 父级ID，0 表示根节点 */
    private Long parentId;

    private String menuName;

    /** 类型：M-目录 C-菜单 B-按钮 */
    private String menuType;

    private String perms;
    private String path;
    private String component;
    private Integer sortOrder;

    /** 是否可见：1-可见 0-隐藏 */
    private Integer visible;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getPerms() {
        return perms;
    }

    public void setPerms(String perms) {
        this.perms = perms;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }
}
