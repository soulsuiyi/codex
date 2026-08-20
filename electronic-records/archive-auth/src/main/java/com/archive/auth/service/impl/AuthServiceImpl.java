package com.archive.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.auth.service.AuthService;
import com.archive.common.dto.LoginRequest;
import com.archive.common.dto.LoginResponse;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 认证服务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        StpUtil.login(user.getId());
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));
        return new LoginResponse(StpUtil.getTokenName(), StpUtil.getTokenValue());
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
