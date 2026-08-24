package com.archive.common.dto;

import java.util.List;

/**
 * 用户分配角色请求。
 */
public class AssignRolesRequest {

    private List<Long> roleIds;

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
