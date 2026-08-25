package com.archive.core.facade;

import com.archive.auth.service.AuthService;
import com.archive.common.dto.LoginRequest;
import com.archive.common.dto.LoginResponse;
import com.archive.common.dto.ProfileUpdateRequest;
import com.archive.common.dto.ProfileVO;
import com.archive.common.dto.ChangePasswordRequest;
import org.springframework.stereotype.Service;

/**
 * 认证门面实现：委托 archive-auth 的认证服务。
 */
@Service
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;

    public AuthFacadeImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        return authService.login(request);
    }

    @Override
    public void logout() {
        authService.logout();
    }

    @Override
    public Long currentUserId() {
        return authService.currentUserId();
    }

    @Override
    public ProfileVO profile() {
        return authService.profile();
    }

    @Override
    public ProfileVO updateProfile(ProfileUpdateRequest request) {
        return authService.updateProfile(request);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        authService.changePassword(request);
    }
}
