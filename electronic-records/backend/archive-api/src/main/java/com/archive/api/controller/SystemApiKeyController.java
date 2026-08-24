package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.ApiKeyCreateRequest;
import com.archive.common.dto.ApiKeyUpdateRequest;
import com.archive.common.dto.ApiKeyVO;
import com.archive.common.dto.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-开放 API 密钥接口。
 */
@RestController
@RequestMapping("/api/v1/system/api-keys")
@Tag(name = "系统管理-API密钥", description = "开放 API 密钥管理（创建时明文返回一次 Secret）")
@SaCheckRole("ADMIN")
public class SystemApiKeyController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemApiKeyController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping
    @Operation(summary = "密钥列表（分页）", description = "Secret 始终掩码返回")
    public Result<PageResult<ApiKeyVO>> list(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(systemAdminFacade.pageApiKeys(page, size, keyword));
    }

    @PostMapping
    @Operation(summary = "新建密钥", description = "自动生成 ak_ 前缀 Access Key 与 Secret，Secret 仅本次返回")
    @AuditLog(module = "SYSTEM", action = "CREATE")
    public Result<ApiKeyVO> create(@RequestBody ApiKeyCreateRequest request) {
        return Result.success(systemAdminFacade.createApiKey(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新密钥", description = "可更新应用名/IP白名单/状态/过期时间")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<ApiKeyVO> update(@PathVariable Long id, @RequestBody ApiKeyUpdateRequest request) {
        return Result.success(systemAdminFacade.updateApiKey(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除密钥", description = "删除后外部系统将无法再调用开放接口")
    @AuditLog(module = "SYSTEM", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        systemAdminFacade.deleteApiKey(id);
        return Result.success();
    }
}
