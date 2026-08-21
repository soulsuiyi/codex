package com.archive.api.controller;

import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.CaseVO;
import com.archive.common.dto.ExternalUploadRequest;
import com.archive.common.dto.FileVO;
import com.archive.common.dto.PageResult;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.Result;
import com.archive.common.response.ResultCode;
import com.archive.core.service.CaseService;
import com.archive.core.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.util.Base64;
import java.util.List;

/**
 * 开放 API（codeplan 5.6）：鉴权为 API Key + HMAC 签名，由 ExternalApiAuthInterceptor 处理。
 */
@RestController
@RequestMapping("/api/v1/external")
@Tag(name = "开放API", description = "供外部系统（扫描矫正等）调用，鉴权：API Key + HMAC 签名")
public class ExternalController {

    private final FileService fileService;
    private final CaseService caseService;

    public ExternalController(FileService fileService, CaseService caseService) {
        this.fileService = fileService;
        this.caseService = caseService;
    }

    @PostMapping("/scan/upload")
    @Operation(summary = "扫描矫正软件上传文件", description = "fileData 为 Base64 编码文件内容")
    @AuditLog(module = "FILE", action = "UPLOAD")
    public Result<FileVO> upload(@RequestBody ExternalUploadRequest request) {
        if (request == null || !StringUtils.hasText(request.getFileData())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "fileData 不能为空");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.getFileData());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "fileData 不是合法的 Base64");
        }
        String fileName = StringUtils.hasText(request.getFileName()) ? request.getFileName() : "upload.bin";
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        return Result.success(fileService.uploadBytes(request.getCaseNo(), fileName, mimeType, bytes));
    }

    @GetMapping("/cases")
    @Operation(summary = "获取案件列表")
    public Result<PageResult<CaseVO>> cases(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.success(caseService.pageCases(page, size, null));
    }

    @GetMapping("/files")
    @Operation(summary = "获取案件文件列表")
    public Result<List<FileVO>> files(@RequestParam String caseNo) {
        return Result.success(fileService.listByCaseNo(caseNo));
    }
}
