package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.FileVO;
import com.archive.common.response.Result;
import com.archive.core.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理接口（codeplan 5.1 中转站部分）。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "文件管理", description = "文件上传、案件文件列表、下载、删除（中转站）")
@SaCheckRole(value = {"CASE_HANDLER", "ADMIN"}, mode = SaMode.OR)
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/files/upload")
    @Operation(summary = "文件上传至中转站", description = "普通上传，自动计算 SHA-256 并支持秒传")
    @AuditLog(module = "FILE", action = "UPLOAD")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("caseNo") String caseNo) {
        return Result.success(fileService.upload(caseNo, file));
    }

    @GetMapping("/cases/{caseNo}/files")
    @Operation(summary = "获取案件文件列表", description = "未删除文件按上传时间倒序")
    public Result<List<FileVO>> list(@PathVariable String caseNo) {
        return Result.success(fileService.listByCaseNo(caseNo));
    }

    @GetMapping("/files/{id}/download")
    @Operation(summary = "文件下载", description = "返回 5 分钟有效的 MinIO Pre-signed URL")
    public Result<String> download(@PathVariable Long id) {
        return Result.success(fileService.downloadUrl(id));
    }

    @DeleteMapping("/files/{id}")
    @Operation(summary = "删除中转站文件", description = "逻辑删除，物理文件保留")
    @AuditLog(module = "FILE", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
