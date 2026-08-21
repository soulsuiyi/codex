package com.archive.auth.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.archive.common.response.Result;
import com.archive.common.response.ResultCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 安全异常处理：未认证返回 401，无权限返回 403（与 AGENTS.md 错误码一致）。
 * 必须优先于 common 的兜底异常处理器，否则特定异常会被 Exception 兜底吞掉。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.error(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public Result<Void> handleNotPermissionException(Exception e) {
        return Result.error(ResultCode.FORBIDDEN);
    }
}
