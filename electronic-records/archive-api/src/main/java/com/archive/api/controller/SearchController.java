package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.SearchResultVO;
import com.archive.common.response.Result;
import com.archive.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全文检索接口（codeplan 5.5，底层为 H2 FULLTEXT）。
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "全文检索", description = "基于 H2 内置 FULLTEXT 的文件全文检索")
@SaCheckRole(value = {"CASE_HANDLER", "ADMIN"}, mode = SaMode.OR)
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/files")
    @Operation(summary = "全文检索文件", description = "keyword 必填，相关度降序 > 上传时间降序，page/size 分页")
    public Result<PageResult<SearchResultVO>> search(@RequestParam String keyword,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return Result.success(searchService.search(keyword, page, size));
    }

    @GetMapping("/files/{id}")
    @Operation(summary = "获取文件详情（含高亮）")
    public Result<SearchResultVO> detail(@PathVariable Long id,
                                         @RequestParam String keyword) {
        return Result.success(searchService.detail(id, keyword));
    }
}
