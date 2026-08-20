package com.archive.auth.security;

import cn.dev33.satoken.stp.StpInterface;
import com.archive.auth.mapper.SysRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限数据源：实现 RBAC 角色/权限查询。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper sysRoleMapper;

    public StpInterfaceImpl(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 说明：codeplan/AGENTS.md 尚未定义 sys_role_menu 关联表，
        // 权限标识暂不返回；待角色-菜单关联明确后实现。
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return sysRoleMapper.selectRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
