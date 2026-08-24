package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.RoleRequest;
import com.archive.common.dto.RoleVO;
import com.archive.common.response.Result;
import com.archive.core.facade.SystemAdminFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-角色接口。
 */
@RestController
@RequestMapping("/api/v1/system/roles")
@Tag(name = "系统管理-角色", description = "角色列表与维护")
@SaCheckRole("ADMIN")
public class SystemRoleController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemRoleController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping
    @Operation(summary = "角色列表", description = "返回全部角色，按ID升序")
    public Result<List<RoleVO>> list() {
        return Result.success(systemAdminFacade.listRoles());
    }

    @PostMapping
    @Operation(summary = "新建角色", description = "roleCode 唯一，如 ADMIN/SECRETARY/ARCHIVIST/CASE_HANDLER")
    @AuditLog(module = "SYSTEM", action = "CREATE")
    public Result<RoleVO> create(@RequestBody RoleRequest request) {
        return Result.success(systemAdminFacade.createRole(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<RoleVO> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        return Result.success(systemAdminFacade.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "已分配给用户的角色禁止删除")
    @AuditLog(module = "SYSTEM", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        systemAdminFacade.deleteRole(id);
        return Result.success();
    }
}
