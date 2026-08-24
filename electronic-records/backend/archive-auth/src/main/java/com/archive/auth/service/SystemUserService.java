package com.archive.auth.service;

import com.archive.common.dto.PageResult;
import com.archive.common.dto.UserCreateRequest;
import com.archive.common.dto.UserUpdateRequest;
import com.archive.common.dto.UserVO;

import java.util.List;

/**
 * 系统用户管理服务。
 */
public interface SystemUserService {

    PageResult<UserVO> pageUsers(long page, long size, String keyword);

    UserVO getUser(Long id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(Long id, UserUpdateRequest request);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String password);

    void assignRoles(Long id, List<Long> roleIds);
}
