package com.archive.auth.service.impl;

import com.archive.auth.entity.SysRole;
import com.archive.auth.entity.SysUserRole;
import com.archive.auth.mapper.SysRoleMapper;
import com.archive.auth.mapper.SysUserRoleMapper;
import com.archive.auth.service.SystemRoleService;
import com.archive.common.dto.RoleRequest;
import com.archive.common.dto.RoleVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统角色管理服务实现。
 */
@Service
public class SystemRoleServiceImpl implements SystemRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public SystemRoleServiceImpl(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public RoleVO createRole(RoleRequest request) {
        checkRequest(request);
        Long count = roleMapper.selectCount(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, request.getRoleCode().trim()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode().trim());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        roleMapper.insert(role);
        return toVO(role);
    }

    @Override
    public RoleVO updateRole(Long id, RoleRequest request) {
        checkRequest(request);
        SysRole role = requireRole(id);
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, request.getRoleCode().trim())
                .ne(SysRole::getId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码已存在");
        }
        role.setRoleCode(request.getRoleCode().trim());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);
        return toVO(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        requireRole(id);
        Long bound = userRoleMapper.selectCount(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        if (bound != null && bound > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该角色已分配给用户，禁止删除");
        }
        roleMapper.deleteById(id);
    }

    private void checkRequest(RoleRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getRoleCode())
                || !StringUtils.hasText(request.getRoleName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色编码和角色名称不能为空");
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setCreatedAt(role.getCreatedAt());
        return vo;
    }
}
