package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.MenuRequest;
import com.archive.common.dto.MenuVO;
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
 * 系统管理-菜单接口。
 */
@RestController
@RequestMapping("/api/v1/system/menus")
@Tag(name = "系统管理-菜单", description = "菜单树与维护（M-目录/C-菜单/B-按钮）")
@SaCheckRole("ADMIN")
public class SystemMenuController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemMenuController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping("/tree")
    @Operation(summary = "菜单树", description = "按 sort_order 升序构建树")
    public Result<List<MenuVO>> tree() {
        return Result.success(systemAdminFacade.menuTree());
    }

    @PostMapping
    @Operation(summary = "新建菜单", description = "parentId 默认 0 表示根节点")
    @AuditLog(module = "SYSTEM", action = "CREATE")
    public Result<MenuVO> create(@RequestBody MenuRequest request) {
        return Result.success(systemAdminFacade.createMenu(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜单", description = "父级菜单不能是自身")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<MenuVO> update(@PathVariable Long id, @RequestBody MenuRequest request) {
        return Result.success(systemAdminFacade.updateMenu(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单", description = "存在子菜单时禁止删除")
    @AuditLog(module = "SYSTEM", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        systemAdminFacade.deleteMenu(id);
        return Result.success();
    }
}
