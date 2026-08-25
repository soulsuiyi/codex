package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.FileVO;
import com.archive.common.dto.FileStreamVO;
import com.archive.common.dto.VersionListVO;
import com.archive.common.response.Result;
import com.archive.core.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/files/upload/chunk")
    @Operation(summary = "分片上传", description = "上传单个分片到本地临时目录")
    public Result<Void> uploadChunk(@RequestParam("file") MultipartFile file,
                                    @RequestParam("caseNo") String caseNo,
                                    @RequestParam("identifier") String identifier,
                                    @RequestParam("chunkIndex") int chunkIndex,
                                    @RequestParam("totalChunks") int totalChunks) {
        fileService.saveChunk(caseNo, identifier, chunkIndex, totalChunks, file);
        return Result.success();
    }

    @PostMapping("/files/upload/chunk/merge")
    @Operation(summary = "分片合并", description = "校验分片齐全后合并上传并落库（含秒传）")
    @AuditLog(module = "FILE", action = "UPLOAD")
    public Result<FileVO> mergeChunks(@RequestParam("caseNo") String caseNo,
                                      @RequestParam("identifier") String identifier,
                                      @RequestParam("fileName") String fileName,
                                      @RequestParam("totalChunks") int totalChunks) {
        return Result.success(fileService.mergeChunks(caseNo, identifier, fileName, totalChunks));
    }

    @GetMapping("/cases/{caseNo}/files")
    @Operation(summary = "获取案件文件列表", description = "未删除文件按上传时间倒序")
    public Result<List<FileVO>> list(@PathVariable String caseNo) {
        return Result.success(fileService.listByCaseNo(caseNo));
    }

    @GetMapping("/files/{id}/download")
    @Operation(summary = "文件下载", description = "图片/PDF 返回带水印流，其他类型返回 Pre-signed URL")
    public ResponseEntity<?> download(@PathVariable Long id) {
        FileStreamVO vo = fileService.downloadStream(id);
        if (vo.getStream() != null) {
            return streamResponse(vo, false);
        }
        return ResponseEntity.ok(Result.success(vo.getPresignedUrl()));
    }

    @GetMapping("/files/{id}/preview")
    @Operation(summary = "文件在线预览", description = "PDF/图片返回带水印流，其他类型返回 400")
    public ResponseEntity<?> preview(@PathVariable Long id) {
        return streamResponse(fileService.previewStream(id), true);
    }

    @GetMapping("/files/{id}/hls/playlist.m3u8")
    @Operation(summary = "HLS 播放列表", description = "音视频转码后的 m3u8，分片相对路径基于本接口目录")
    public ResponseEntity<?> hlsPlaylist(@PathVariable Long id) {
        return streamResponse(fileService.hlsPlaylist(id), true);
    }

    @GetMapping("/files/{id}/hls/{segment}")
    @Operation(summary = "HLS 分片", description = "播放列表引用的 ts 分片")
    public ResponseEntity<?> hlsSegment(@PathVariable Long id, @PathVariable String segment) {
        return streamResponse(fileService.hlsSegment(id, segment), true);
    }

    @GetMapping("/files/{id}/versions")
    @Operation(summary = "获取文件版本列表", description = "返回当前文件与历史版本")
    public Result<VersionListVO> versions(@PathVariable Long id) {
        return Result.success(fileService.versions(id));
    }

    @DeleteMapping("/files/{id}")
    @Operation(summary = "删除中转站文件", description = "逻辑删除，物理文件保留")
    @AuditLog(module = "FILE", action = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }

    private ResponseEntity<InputStreamResource> streamResponse(FileStreamVO vo, boolean inline) {
        MediaType mediaType = vo.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(vo.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, inline ? "inline" : "attachment")
                .body(new InputStreamResource(vo.getStream()));
    }
}
