package com.archive.api.controller;

import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.BorrowApplyRequest;
import com.archive.common.dto.BorrowApplyVO;
import com.archive.common.dto.BorrowApprovalRequest;
import com.archive.common.dto.BorrowDetailVO;
import com.archive.common.dto.BorrowDownloadRequest;
import com.archive.common.dto.BorrowTokenVO;
import com.archive.common.dto.FileStreamVO;
import com.archive.common.dto.PageResult;
import com.archive.common.response.Result;
import com.archive.core.service.BorrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 借阅管理接口（codeplan 5.4）。
 */
@RestController
@RequestMapping("/api/v1/borrows")
@Tag(name = "借阅管理", description = "借阅申请、双重审批、授权Token与借阅下载")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping("/apply")
    @Operation(summary = "提交借阅申请", description = "仅可申请归档区文件")
    @AuditLog(module = "BORROW", action = "APPLY")
    public Result<BorrowApplyVO> apply(@RequestBody BorrowApplyRequest request) {
        return Result.success(borrowService.apply(request));
    }

    @GetMapping("/my")
    @Operation(summary = "我的借阅申请列表")
    public Result<PageResult<BorrowApplyVO>> my(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.success(borrowService.myList(page, size));
    }

    @GetMapping("/pending")
    @Operation(summary = "待我审批列表", description = "仲裁秘书见初审申请，档案管理员见终审申请，管理员两者可见")
    public Result<PageResult<BorrowApplyVO>> pending(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return Result.success(borrowService.pendingList(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "借阅申请详情", description = "本人或审批人可查看，含借阅文件与审批记录")
    public Result<BorrowDetailVO> detail(@PathVariable Long id) {
        return Result.success(borrowService.detail(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批借阅申请", description = "PENDING_SECRETARY 由仲裁秘书、PENDING_ADMIN 由档案管理员审批")
    @AuditLog(module = "BORROW", action = "APPROVE")
    public Result<BorrowApplyVO> approve(@PathVariable Long id, @RequestBody BorrowApprovalRequest request) {
        return Result.success(borrowService.approve(id, request));
    }

    @GetMapping("/{id}/token")
    @Operation(summary = "获取借阅授权Token")
    public Result<BorrowTokenVO> token(@PathVariable Long id) {
        return Result.success(borrowService.token(id));
    }

    @PostMapping("/{id}/download")
    @Operation(summary = "下载借阅文件", description = "需携带借阅授权Token；图片/PDF 返回带水印流，其他类型返回 Pre-signed URL")
    public ResponseEntity<?> download(@PathVariable Long id, @RequestBody BorrowDownloadRequest request) {
        FileStreamVO vo = borrowService.download(id, request);
        if (vo.getStream() != null) {
            MediaType mediaType = vo.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(vo.getContentType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(new InputStreamResource(vo.getStream()));
        }
        return ResponseEntity.ok(Result.success(vo.getPresignedUrl()));
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "归还借阅文件", description = "申请人归还并撤销授权")
    @AuditLog(module = "BORROW", action = "RETURN")
    public Result<BorrowApplyVO> returnFile(@PathVariable Long id) {
        return Result.success(borrowService.returnFile(id));
    }
}
