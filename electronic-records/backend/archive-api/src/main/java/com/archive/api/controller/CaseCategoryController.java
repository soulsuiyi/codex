package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.CategoryCreateRequest;
import com.archive.common.dto.CategoryUpdateRequest;
import com.archive.common.dto.CategoryVO;
import com.archive.common.response.Result;
import com.archive.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/{id}")
    @Operation(summary = "更新分类", description = "仅名称与排序可修改，父级与层级不可改")
    @AuditLog(module = "CATEGORY", action = "UPDATE")
    public Result<CategoryVO> update(@PathVariable Long id, @RequestBody CategoryUpdateRequest request) {
        return Result.success(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类", description = "存在子分类或已被案件引用时禁止删除")
    @AuditLog(module = "CATEGORY", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
