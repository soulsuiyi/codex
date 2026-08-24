package com.archive.auth.service;

import com.archive.common.dto.RoleRequest;
import com.archive.common.dto.RoleVO;

import java.util.List;

/**
 * 系统角色管理服务。
 */
public interface SystemRoleService {

    List<RoleVO> listRoles();

    RoleVO createRole(RoleRequest request);

    RoleVO updateRole(Long id, RoleRequest request);

    void deleteRole(Long id);
}
