package com.archive.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解：标注在需要记录审计日志的方法上，由 archive-auth 的切面异步写入 sys_audit_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 模块：FILE/BORROW/ARCHIVE/SYSTEM 等 */
    String module();

    /** 动作：CREATE/UPDATE/UPLOAD/DELETE/ARCHIVE/APPROVE 等 */
    String action();
}
