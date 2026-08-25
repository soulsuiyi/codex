package com.archive.core.facade;

import com.archive.common.dto.LoginRequest;
import com.archive.common.dto.LoginResponse;
import com.archive.common.dto.ProfileUpdateRequest;
import com.archive.common.dto.ProfileVO;
import com.archive.common.dto.ChangePasswordRequest;

/**
 * 认证门面：向 archive-api 暴露认证能力。
 * 依据 AGENTS.md 依赖方向（api 不得依赖 auth），由 core 转发 auth 服务。
 */
public interface AuthFacade {

    LoginResponse login(LoginRequest request);

    void logout();

    Long currentUserId();

    ProfileVO profile();

    ProfileVO updateProfile(ProfileUpdateRequest request);

    void changePassword(ChangePasswordRequest request);
}
