package com.archive.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户角色关联表 sys_user_role（联合主键 user_id + role_id）。
 */
@TableName("sys_user_role")
public class SysUserRole {

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
