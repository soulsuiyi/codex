package com.archive.auth.service.impl;

import com.archive.auth.entity.SysMenu;
import com.archive.auth.mapper.SysMenuMapper;
import com.archive.auth.service.SystemMenuService;
import com.archive.common.dto.MenuRequest;
import com.archive.common.dto.MenuVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统菜单管理服务实现。
 */
@Service
public class SystemMenuServiceImpl implements SystemMenuService {

    private final SysMenuMapper menuMapper;

    public SystemMenuServiceImpl(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @Override
    public List<MenuVO> tree() {
        List<SysMenu> menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodeMap.put(menu.getId(), toVO(menu));
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : nodeMap.values()) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L) {
                roots.add(node);
            } else {
                MenuVO parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    @Override
    public MenuVO createMenu(MenuRequest request) {
        checkRequest(request);
        SysMenu menu = new SysMenu();
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setPerms(request.getPerms());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menuMapper.insert(menu);
        return toVO(menu);
    }

    @Override
    public MenuVO updateMenu(Long id, MenuRequest request) {
        checkRequest(request);
        SysMenu menu = requireMenu(id);
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "父级菜单不能是自身");
        }
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setPerms(request.getPerms());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menuMapper.updateById(menu);
        return toVO(menu);
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        requireMenu(id);
        Long children = menuMapper.selectCount(
                Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "存在子菜单，请先删除子菜单");
        }
        menuMapper.deleteById(id);
    }

    private void checkRequest(MenuRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getMenuName())
                || !StringUtils.hasText(request.getMenuType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "菜单名称和菜单类型不能为空");
        }
        String type = request.getMenuType();
        if (!"M".equals(type) && !"C".equals(type) && !"B".equals(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "菜单类型仅支持 M-目录/C-菜单/B-按钮");
        }
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPerms(menu.getPerms());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
