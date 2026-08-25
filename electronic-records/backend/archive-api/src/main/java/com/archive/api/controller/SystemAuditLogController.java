package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.dto.AuditRetentionVO;
import com.archive.common.dto.AuditLogVO;
import com.archive.common.dto.PageResult;
import com.archive.common.response.Result;
import com.archive.core.facade.SystemAdminFacade;
import com.archive.core.service.AuditArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 系统管理-操作审计日志接口。
 */
@RestController
@RequestMapping("/api/v1/system/audit-logs")
@Tag(name = "系统管理-操作日志", description = "操作审计日志分页查询")
@SaCheckRole("ADMIN")
public class SystemAuditLogController {

    private final SystemAdminFacade systemAdminFacade;
    private final AuditArchiveService auditArchiveService;

    public SystemAuditLogController(SystemAdminFacade systemAdminFacade,
                                    AuditArchiveService auditArchiveService) {
        this.systemAdminFacade = systemAdminFacade;
        this.auditArchiveService = auditArchiveService;
    }

    @GetMapping
    @Operation(summary = "审计日志分页", description = "支持模块/动作/操作人/结果/时间范围过滤，时间格式 yyyy-MM-dd HH:mm:ss")
    public Result<PageResult<AuditLogVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(systemAdminFacade.pageAuditLogs(
                page, size, module, action, username, result, startTime, endTime));
    }

    @GetMapping("/retention")
    @Operation(summary = "审计保留与冷存储归档情况", description = "保留天数、冷存储归档文件数与最近归档日期")
    public Result<AuditRetentionVO> retention() {
        return Result.success(auditArchiveService.retentionInfo());
    }
}
