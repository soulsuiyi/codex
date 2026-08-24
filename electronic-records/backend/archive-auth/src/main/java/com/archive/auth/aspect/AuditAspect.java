package com.archive.auth.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.auth.async.AuditLogWriter;
import com.archive.auth.entity.SysAuditLog;
import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.common.annotation.AuditLog;
import com.archive.common.dto.CaseVO;
import com.archive.common.dto.FileVO;
import com.archive.common.dto.ArchiveVO;
import com.archive.common.dto.ApiKeyVO;
import com.archive.common.dto.BorrowApplyVO;
import com.archive.common.dto.DictVO;
import com.archive.common.dto.MenuVO;
import com.archive.common.dto.RoleVO;
import com.archive.common.dto.UserVO;
import com.archive.common.response.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面：拦截 @AuditLog 标注的方法，采集操作上下文并异步写入 sys_audit_log。
 */
@Aspect
@Component
public class AuditAspect {

    private static final int DETAIL_MAX_LENGTH = 2000;

    private final AuditLogWriter auditLogWriter;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditLogWriter auditLogWriter, SysUserMapper sysUserMapper, ObjectMapper objectMapper) {
        this.auditLogWriter = auditLogWriter;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result = null;
        String resultStatus = "SUCCESS";
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            resultStatus = "FAILED";
            throw e;
        } finally {
            auditLogWriter.write(buildAuditLog(joinPoint, auditLog, result, resultStatus));
        }
    }

    private SysAuditLog buildAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                                      Object result, String resultStatus) {
        SysAuditLog audit = new SysAuditLog();
        audit.setUserId(currentUserId());
        if (audit.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(audit.getUserId());
            if (user != null) {
                audit.setUsername(user.getUsername());
            }
        }
        audit.setModule(auditLog.module());
        audit.setAction(auditLog.action());
        if (result instanceof Result<?> response && response.getData() != null) {
            Object data = response.getData();
            if (data instanceof CaseVO caseVO && caseVO.getId() != null) {
                audit.setTargetId(String.valueOf(caseVO.getId()));
            } else if (data instanceof FileVO fileVO && fileVO.getId() != null) {
                audit.setTargetId(String.valueOf(fileVO.getId()));
            } else if (data instanceof ArchiveVO archiveVO && archiveVO.getId() != null) {
                audit.setTargetId(String.valueOf(archiveVO.getId()));
            } else if (data instanceof BorrowApplyVO borrowApplyVO && borrowApplyVO.getId() != null) {
                audit.setTargetId(String.valueOf(borrowApplyVO.getId()));
            } else if (data instanceof UserVO userVO && userVO.getId() != null) {
                audit.setTargetId(String.valueOf(userVO.getId()));
            } else if (data instanceof RoleVO roleVO && roleVO.getId() != null) {
                audit.setTargetId(String.valueOf(roleVO.getId()));
            } else if (data instanceof MenuVO menuVO && menuVO.getId() != null) {
                audit.setTargetId(String.valueOf(menuVO.getId()));
            } else if (data instanceof DictVO dictVO && dictVO.getId() != null) {
                audit.setTargetId(String.valueOf(dictVO.getId()));
            } else if (data instanceof ApiKeyVO apiKeyVO && apiKeyVO.getId() != null) {
                audit.setTargetId(String.valueOf(apiKeyVO.getId()));
            } else {
                audit.setTargetId(extractTargetId(joinPoint));
            }
        } else {
            audit.setTargetId(extractTargetId(joinPoint));
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String forwarded = request.getHeader("X-Forwarded-For");
            audit.setIp(StringUtils.hasText(forwarded) ? forwarded : request.getRemoteAddr());
            audit.setUserAgent(request.getHeader("User-Agent"));
        }
        audit.setDetail(buildDetail(joinPoint));
        audit.setResult(resultStatus);
        audit.setCreatedAt(LocalDateTime.now());
        return audit;
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String extractTargetId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (parameterNames != null && i < parameterNames.length
                    && ("id".equals(parameterNames[i]) || "caseId".equals(parameterNames[i]))
                    && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private String buildDetail(ProceedingJoinPoint joinPoint) {
        try {
            String json = objectMapper.writeValueAsString(joinPoint.getArgs());
            return json.length() > DETAIL_MAX_LENGTH ? json.substring(0, DETAIL_MAX_LENGTH) : json;
        } catch (Exception e) {
            return joinPoint.getArgs().length + " params";
        }
    }
}
