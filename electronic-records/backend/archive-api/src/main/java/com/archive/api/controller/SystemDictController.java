package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.DictRequest;
import com.archive.common.dto.DictVO;
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

import java.util.List;

/**
 * 系统管理-数据字典接口。
 */
@RestController
@RequestMapping("/api/v1/system/dicts")
@Tag(name = "系统管理-字典", description = "数据字典分页、按类型查询与维护")
@SaCheckRole("ADMIN")
public class SystemDictController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemDictController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping
    @Operation(summary = "字典分页", description = "dictType 精确过滤，keyword 按编码/标签/值模糊匹配")
    public Result<PageResult<DictVO>> list(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String dictType,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(systemAdminFacade.pageDicts(page, size, dictType, keyword));
    }

    @GetMapping("/type/{dictType}")
    @Operation(summary = "按类型查询字典", description = "供下拉/枚举场景使用")
    public Result<List<DictVO>> listByType(@PathVariable String dictType) {
        return Result.success(systemAdminFacade.listDictByType(dictType));
    }

    @PostMapping
    @Operation(summary = "新建字典项", description = "dictType + dictCode 组合唯一")
    @AuditLog(module = "SYSTEM", action = "CREATE")
    public Result<DictVO> create(@RequestBody DictRequest request) {
        return Result.success(systemAdminFacade.createDict(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新字典项")
    @AuditLog(module = "SYSTEM", action = "UPDATE")
    public Result<DictVO> update(@PathVariable Long id, @RequestBody DictRequest request) {
        return Result.success(systemAdminFacade.updateDict(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典项")
    @AuditLog(module = "SYSTEM", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        systemAdminFacade.deleteDict(id);
        return Result.success();
    }
}
