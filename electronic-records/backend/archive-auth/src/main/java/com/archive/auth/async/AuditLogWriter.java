package com.archive.auth.async;

import com.archive.auth.entity.SysAuditLog;
import com.archive.auth.mapper.SysAuditLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志异步写入器：独立 Bean 保证 @Async 代理生效。
 */
@Service
public class AuditLogWriter {

    private final SysAuditLogMapper sysAuditLogMapper;

    public AuditLogWriter(SysAuditLogMapper sysAuditLogMapper) {
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    @Async
    public void write(SysAuditLog auditLog) {
        if (auditLog == null) {
            return;
        }
        sysAuditLogMapper.insert(auditLog);
    }
}
