package com.archive.auth.service;

import com.archive.common.dto.LoginRequest;
import com.archive.common.dto.LoginResponse;

/**
 * 认证服务：登录、登出、当前用户。
 */
public interface AuthService {

    /**
     * 账号密码登录，成功后返回 Sa-Token 令牌。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 当前登录会话登出。
     */
    void logout();

    /**
     * 当前登录用户 ID。
     */
    Long currentUserId();
}
