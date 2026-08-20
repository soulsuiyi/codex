package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.CategoryCreateRequest;
import com.archive.common.dto.CategoryVO;
import com.archive.common.response.Result;
import com.archive.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 案件分类接口（codeplan 5.2）。
 */
@RestController
@RequestMapping("/api/v1/cases/categories")
@Tag(name = "案件分类", description = "案件分类树与新建分类")
@SaCheckRole(value = {"CASE_HANDLER", "ADMIN"}, mode = SaMode.OR)
public class CaseCategoryController {

    private final CategoryService categoryService;

    public CaseCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "获取分类树")
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @PostMapping
    @Operation(summary = "新建分类", description = "parentId 默认 0 表示根节点，level 自动计算")
    @AuditLog(module = "CATEGORY", action = "CREATE")
    public Result<CategoryVO> create(@RequestBody CategoryCreateRequest request) {
        return Result.success(categoryService.createCategory(request));
    }
}
