package com.archive.core.facade;

import com.archive.auth.service.SystemApiKeyService;
import com.archive.auth.service.AuditLogService;
import com.archive.auth.service.SystemMenuService;
import com.archive.auth.service.SystemRoleService;
import com.archive.auth.service.SystemUserService;
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
import com.archive.common.dto.UserCreateRequest;
import com.archive.common.dto.UserUpdateRequest;
import com.archive.common.dto.UserVO;
import com.archive.core.service.DictService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 系统管理门面实现：委托 archive-auth 服务与 archive-core 字典服务。
 */
@Service
public class SystemAdminFacadeImpl implements SystemAdminFacade {

    private final SystemUserService systemUserService;
    private final SystemRoleService systemRoleService;
    private final SystemMenuService systemMenuService;
    private final SystemApiKeyService systemApiKeyService;
    private final AuditLogService auditLogService;
    private final DictService dictService;

    public SystemAdminFacadeImpl(SystemUserService systemUserService,
                                 SystemRoleService systemRoleService,
                                 SystemMenuService systemMenuService,
                                 SystemApiKeyService systemApiKeyService,
                                 AuditLogService auditLogService,
                                 DictService dictService) {
        this.systemUserService = systemUserService;
        this.systemRoleService = systemRoleService;
        this.systemMenuService = systemMenuService;
        this.systemApiKeyService = systemApiKeyService;
        this.auditLogService = auditLogService;
        this.dictService = dictService;
    }

    @Override
    public PageResult<UserVO> pageUsers(long page, long size, String keyword) {
        return systemUserService.pageUsers(page, size, keyword);
    }

    @Override
    public UserVO getUser(Long id) {
        return systemUserService.getUser(id);
    }

    @Override
    public UserVO createUser(UserCreateRequest request) {
        return systemUserService.createUser(request);
    }

    @Override
    public UserVO updateUser(Long id, UserUpdateRequest request) {
        return systemUserService.updateUser(id, request);
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        systemUserService.updateStatus(id, status);
    }

    @Override
    public void resetPassword(Long id, String password) {
        systemUserService.resetPassword(id, password);
    }

    @Override
    public void assignRoles(Long id, List<Long> roleIds) {
        systemUserService.assignRoles(id, roleIds);
    }

    @Override
    public List<RoleVO> listRoles() {
        return systemRoleService.listRoles();
    }

    @Override
    public RoleVO createRole(RoleRequest request) {
        return systemRoleService.createRole(request);
    }

    @Override
    public RoleVO updateRole(Long id, RoleRequest request) {
        return systemRoleService.updateRole(id, request);
    }

    @Override
    public void deleteRole(Long id) {
        systemRoleService.deleteRole(id);
    }

    @Override
    public List<MenuVO> menuTree() {
        return systemMenuService.tree();
    }

    @Override
    public MenuVO createMenu(MenuRequest request) {
        return systemMenuService.createMenu(request);
    }

    @Override
    public MenuVO updateMenu(Long id, MenuRequest request) {
        return systemMenuService.updateMenu(id, request);
    }

    @Override
    public void deleteMenu(Long id) {
        systemMenuService.deleteMenu(id);
    }

    @Override
    public PageResult<DictVO> pageDicts(long page, long size, String dictType, String keyword) {
        return dictService.pageDicts(page, size, dictType, keyword);
    }

    @Override
    public List<DictVO> listDictByType(String dictType) {
        return dictService.listByType(dictType);
    }

    @Override
    public DictVO createDict(DictRequest request) {
        return dictService.createDict(request);
    }

    @Override
    public DictVO updateDict(Long id, DictRequest request) {
        return dictService.updateDict(id, request);
    }

    @Override
    public void deleteDict(Long id) {
        dictService.deleteDict(id);
    }

    @Override
    public PageResult<ApiKeyVO> pageApiKeys(long page, long size, String keyword) {
        return systemApiKeyService.pageApiKeys(page, size, keyword);
    }

    @Override
    public ApiKeyVO createApiKey(ApiKeyCreateRequest request) {
        return systemApiKeyService.createApiKey(request);
    }

    @Override
    public ApiKeyVO updateApiKey(Long id, ApiKeyUpdateRequest request) {
        return systemApiKeyService.updateApiKey(id, request);
    }

    @Override
    public void deleteApiKey(Long id) {
        systemApiKeyService.deleteApiKey(id);
    }

    @Override
    public PageResult<AuditLogVO> pageAuditLogs(long page, long size, String module, String action,
                                                String username, String result,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogService.pageAuditLogs(page, size, module, action, username, result, startTime, endTime);
    }
}
