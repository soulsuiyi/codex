package com.archive.api.controller;

import com.archive.common.dto.LoginRequest;
import com.archive.common.dto.LoginResponse;
import com.archive.common.response.Result;
import com.archive.core.facade.AuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证管理接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证管理", description = "登录、登出、当前登录用户")
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @PostMapping("/login")
    @Operation(summary = "账号密码登录", description = "登录成功后返回 Sa-Token 令牌")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authFacade.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出", description = "注销当前登录会话")
    public Result<Void> logout() {
        authFacade.logout();
        return Result.success();
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户ID", description = "返回当前登录用户的用户ID")
    public Result<Long> me() {
        return Result.success(authFacade.currentUserId());
    }
}
