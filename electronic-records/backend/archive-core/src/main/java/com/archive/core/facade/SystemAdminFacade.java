package com.archive.core.facade;

import com.archive.common.dto.ApiKeyCreateRequest;
import com.archive.common.dto.ApiKeyUpdateRequest;
import com.archive.common.dto.ApiKeyVO;
import com.archive.common.dto.AuditLogVO;
import com.archive.common.dto.DictRequest;
import com.archive.common.dto.DictVO;
import com.archive.common.dto.MenuRequest;
import com.archive.common.dto.MenuVO;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.RoleRequest;
import com.archive.common.dto.RoleVO;
import com.archive.common.dto.StatsVO;
import com.archive.common.dto.UserCreateRequest;
import com.archive.common.dto.UserUpdateRequest;
import com.archive.common.dto.UserVO;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 系统管理门面：向 archive-api 暴露用户/角色/菜单/字典/API Key 管理能力。
 * 依据 AGENTS.md 依赖方向（api 不得依赖 auth），由 core 转发 auth 服务。
 */
public interface SystemAdminFacade {

    // ---------- 用户管理 ----------
    PageResult<UserVO> pageUsers(long page, long size, String keyword);

    UserVO getUser(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

    void updateUserStatus(Long id, Integer status);

    void resetPassword(Long id, String password);

    void assignRoles(Long id, List<Long> roleIds);

    // ---------- 角色管理 ----------
    List<RoleVO> listRoles();

    RoleVO createRole(RoleRequest request);

    RoleVO updateRole(Long id, RoleRequest request);

    void deleteRole(Long id);

    // ---------- 菜单管理 ----------
    List<MenuVO> menuTree();

    MenuVO createMenu(MenuRequest request);

    MenuVO updateMenu(Long id, MenuRequest request);

    void deleteMenu(Long id);

    // ---------- 数据字典 ----------
    PageResult<DictVO> pageDicts(long page, long size, String dictType, String keyword);

    List<DictVO> listDictByType(String dictType);

    DictVO createDict(DictRequest request);

    DictVO updateDict(Long id, DictRequest request);

    void deleteDict(Long id);

    // ---------- 开放 API 密钥 ----------
    PageResult<ApiKeyVO> pageApiKeys(long page, long size, String keyword);

    ApiKeyVO createApiKey(ApiKeyCreateRequest request);

    ApiKeyVO updateApiKey(Long id, ApiKeyUpdateRequest request);

    void deleteApiKey(Long id);

    // ---------- 操作审计日志 ----------
    PageResult<AuditLogVO> pageAuditLogs(long page, long size, String module, String action,
                                         String username, String result,
                                         LocalDateTime startTime, LocalDateTime endTime);

    // ---------- 运行统计 ----------
    StatsVO systemStats();
}
