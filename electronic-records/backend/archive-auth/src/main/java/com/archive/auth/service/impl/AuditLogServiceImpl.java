package com.archive.auth.service.impl;

import com.archive.auth.entity.SysAuditLog;
import com.archive.auth.mapper.SysAuditLogMapper;
import com.archive.auth.service.AuditLogService;
import com.archive.common.dto.AuditLogVO;
import com.archive.common.dto.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作审计日志查询服务实现。
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public PageResult<AuditLogVO> pageAuditLogs(long page, long size, String module, String action,
                                                String username, String result,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysAuditLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(module)) {
            wrapper.eq(SysAuditLog::getModule, module.trim());
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(SysAuditLog::getAction, action.trim());
        }
        if (StringUtils.hasText(username)) {
            wrapper.like(SysAuditLog::getUsername, username.trim());
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(SysAuditLog::getResult, result.trim());
        }
        if (startTime != null) {
            wrapper.ge(SysAuditLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysAuditLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(SysAuditLog::getCreatedAt).orderByDesc(SysAuditLog::getId);

        Page<SysAuditLog> resultPage = auditLogMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        List<AuditLogVO> records = resultPage.getRecords().stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    private AuditLogVO toVO(SysAuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setModule(entity.getModule());
        vo.setAction(entity.getAction());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setDetail(entity.getDetail());
        vo.setResult(entity.getResult());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
