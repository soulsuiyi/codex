package com.archive.auth.service;

import com.archive.common.dto.AuditLogVO;
import com.archive.common.dto.PageResult;

import java.time.LocalDateTime;

/**
 * 操作审计日志查询服务。
 */
public interface AuditLogService {

    /**
     * 审计日志分页查询，支持模块/动作/操作人/结果/时间范围过滤。
     */
    PageResult<AuditLogVO> pageAuditLogs(long page, long size, String module, String action,
                                         String username, String result,
                                         LocalDateTime startTime, LocalDateTime endTime);
}
