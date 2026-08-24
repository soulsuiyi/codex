package com.archive.auth.service.impl;

import com.archive.auth.entity.SysRole;
import com.archive.auth.entity.SysUser;
import com.archive.auth.entity.SysUserRole;
import com.archive.auth.mapper.SysRoleMapper;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.auth.mapper.SysUserRoleMapper;
import com.archive.auth.service.SystemUserService;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.UserCreateRequest;
import com.archive.common.dto.UserUpdateRequest;
import com.archive.common.dto.UserVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统用户管理服务实现。
 */
@Service
public class SystemUserServiceImpl implements SystemUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public SystemUserServiceImpl(SysUserMapper userMapper,
                                 SysUserRoleMapper userRoleMapper,
                                 SysRoleMapper roleMapper,
                                 PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<UserVO> pageUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public UserVO getUser(Long id) {
        return toVO(requireUser(id));
    }

    @Override
    @Transactional
    public UserVO createUser(UserCreateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        String username = request.getUsername().trim();
        Long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);
        replaceRoles(user.getId(), request.getRoleIds());
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateUser(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
        replaceRoles(id, request.getRoleIds());
        return toVO(user);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        requireUser(id);
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode(password)));
    }

    @Override
    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        replaceRoles(id, roleIds);
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        List<Long> roleIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, user.getId()))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        vo.setRoleIds(roleIds);
        if (roleIds.isEmpty()) {
            vo.setRoleCodes(Collections.emptyList());
        } else {
            vo.setRoleCodes(roleMapper.selectBatchIds(roleIds)
                    .stream()
                    .map(SysRole::getRoleCode)
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}
