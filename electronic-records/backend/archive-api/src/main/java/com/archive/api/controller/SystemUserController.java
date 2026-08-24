package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.AssignRolesRequest;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.ResetPasswordRequest;
import com.archive.common.dto.UserCreateRequest;
import com.archive.common.dto.UserStatusRequest;
import com.archive.common.dto.UserUpdateRequest;
import com.archive.common.dto.UserVO;
import com.archive.common.response.Result;
import com.archive.core.facade.SystemAdminFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-用户接口。
 */
@RestController
@RequestMapping("/api/v1/system/users")
@Tag(name = "系统管理-用户", description = "用户分页、新建、更新、启停、重置密码、分配角色")
@SaCheckRole("ADMIN")
public class SystemUserController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemUserController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping
    @Operation(summary = "用户列表（分页）", description = "keyword 按用户名/姓名/手机号模糊匹配")
    public Result<PageResult<UserVO>> list(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(systemAdminFacade.pageUsers(page, size, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情", description = "含角色ID与角色编码")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.success(systemAdminFacade.getUser(id));
    }

    @PostMapping
    @Operation(summary = "新建用户", description = "密码 BCrypt 加密存储")
    @AuditLog(module = "SYSTEM", action = "CREATE")
    public Result<UserVO> create(@RequestBody UserCreateRequest request) {
        return Result.success(systemAdminFacade.createUser(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "账号与密码不可通过本接口修改")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<UserVO> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return Result.success(systemAdminFacade.updateUser(id, request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用用户", description = "status: 1-启用 0-禁用")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<Void> status(@PathVariable Long id, @RequestBody UserStatusRequest request) {
        systemAdminFacade.updateUserStatus(id, request.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "重置密码", description = "管理员重置用户密码，BCrypt 加密存储")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        systemAdminFacade.resetPassword(id, request.getPassword());
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "分配角色", description = "全量覆盖该用户的角色")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest request) {
        systemAdminFacade.assignRoles(id, request.getRoleIds());
        return Result.success();
    }
}
