package com.archive.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 操作审计日志表 sys_audit_log。
 */
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人账号 */
    private String username;

    /** 模块: FILE/BORROW/ARCHIVE/SYSTEM */
    private String module;

    /** 动作: UPLOAD/DOWNLOAD/PREVIEW/DELETE/ARCHIVE/APPROVE/APPLY */
    private String action;

    /** 目标对象类型 */
    private String targetType;

    /** 目标对象ID */
    private String targetId;

    /** IP 地址 */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** 详细参数/结果 */
    private String detail;

    /** 结果: SUCCESS/FAILED */
    private String result;

    /** 操作时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
