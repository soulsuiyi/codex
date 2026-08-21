package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.ArchiveRequest;
import com.archive.common.dto.ArchiveVO;
import com.archive.common.dto.PageResult;
import com.archive.common.response.Result;
import com.archive.core.service.ArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归档管理接口（codeplan 5.3）。
 */
@RestController
@RequestMapping("/api/v1/archives")
@Tag(name = "归档管理", description = "一键归档与归档记录查询")
@SaCheckRole(value = {"ARCHIVIST", "ADMIN"}, mode = SaMode.OR)
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/archive")
    @Operation(summary = "一键归档", description = "完整性校验与 Hash 比对后归档案件全部中转站文件")
    @AuditLog(module = "ARCHIVE", action = "ARCHIVE")
    public Result<ArchiveVO> archive(@RequestBody ArchiveRequest request) {
        return Result.success(archiveService.archive(request.getCaseNo()));
    }

    @GetMapping
    @Operation(summary = "归档记录列表", description = "page/size 分页，caseNo 可选过滤")
    public Result<PageResult<ArchiveVO>> list(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String caseNo) {
        return Result.success(archiveService.pageArchives(page, size, caseNo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "归档详情")
    public Result<ArchiveVO> detail(@PathVariable Long id) {
        return Result.success(archiveService.getArchiveById(id));
    }
}
