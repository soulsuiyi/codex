package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.CaseCreateRequest;
import com.archive.common.dto.CaseUpdateRequest;
import com.archive.common.dto.CaseVO;
import com.archive.common.dto.PageResult;
import com.archive.common.response.Result;
import com.archive.core.service.CaseService;
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
 * 案件管理接口（codeplan 5.2）。
 */
@RestController
@RequestMapping("/api/v1/cases")
@Tag(name = "案件管理", description = "案件分页列表、详情、新建与更新")
@SaCheckRole(value = {"CASE_HANDLER", "ADMIN"}, mode = SaMode.OR)
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    @Operation(summary = "案件列表（分页）", description = "keyword 可选，按案号/案件名称模糊匹配")
    public Result<PageResult<CaseVO>> list(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(caseService.pageCases(page, size, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "案件详情")
    public Result<CaseVO> detail(@PathVariable Long id) {
        return Result.success(caseService.getCaseById(id));
    }

    @PostMapping
    @Operation(summary = "新建案件")
    @AuditLog(module = "CASE", action = "CREATE")
    public Result<CaseVO> create(@RequestBody CaseCreateRequest request) {
        return Result.success(caseService.createCase(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新案件", description = "案号与状态不可修改，已归档案件禁止更新")
    @AuditLog(module = "CASE", action = "UPDATE")
    public Result<CaseVO> update(@PathVariable Long id, @RequestBody CaseUpdateRequest request) {
        return Result.success(caseService.updateCase(id, request));
    }
}
