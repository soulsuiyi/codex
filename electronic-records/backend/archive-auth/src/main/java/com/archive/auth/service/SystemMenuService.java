package com.archive.auth.service;

import com.archive.common.dto.MenuRequest;
import com.archive.common.dto.MenuVO;

import java.util.List;

/**
 * 系统菜单管理服务。
 */
public interface SystemMenuService {

    List<MenuVO> tree();

    MenuVO createMenu(MenuRequest request);

    MenuVO updateMenu(Long id, MenuRequest request);

    void deleteMenu(Long id);
}
