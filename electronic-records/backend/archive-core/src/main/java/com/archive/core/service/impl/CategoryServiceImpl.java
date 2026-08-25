package com.archive.core.service.impl;

import com.archive.common.dto.CategoryCreateRequest;
import com.archive.common.dto.CategoryUpdateRequest;
import com.archive.common.dto.CategoryVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.entity.SysCase;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.service.CategoryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 案件分类服务实现。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final SysCaseCategoryMapper sysCaseCategoryMapper;
    private final SysCaseMapper sysCaseMapper;

    public CategoryServiceImpl(SysCaseCategoryMapper sysCaseCategoryMapper, SysCaseMapper sysCaseMapper) {
        this.sysCaseCategoryMapper = sysCaseCategoryMapper;
        this.sysCaseMapper = sysCaseMapper;
    }

    @Override
    public List<CategoryVO> tree() {
        List<SysCaseCategory> all = sysCaseCategoryMapper.selectList(
                Wrappers.<SysCaseCategory>lambdaQuery()
                        .orderByAsc(SysCaseCategory::getSortOrder)
                        .orderByAsc(SysCaseCategory::getId));
        Map<Long, CategoryVO> map = new LinkedHashMap<>();
        for (SysCaseCategory category : all) {
            map.put(category.getId(), toVO(category));
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (SysCaseCategory category : all) {
            CategoryVO vo = map.get(category.getId());
            if (category.getParentId() != null
                    && category.getParentId() != 0L
                    && map.containsKey(category.getParentId())) {
                map.get(category.getParentId()).getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    @Override
    public CategoryVO createCategory(CategoryCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类名称不能为空");
        }
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        int level = 1;
        if (parentId != 0L) {
            SysCaseCategory parent = sysCaseCategoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父分类不存在");
            }
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        }
        SysCaseCategory entity = new SysCaseCategory();
        entity.setParentId(parentId);
        entity.setName(request.getName());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setLevel(level);
        sysCaseCategoryMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public CategoryVO updateCategory(Long id, CategoryUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类名称不能为空");
        }
        SysCaseCategory entity = requireCategory(id);
        entity.setName(request.getName());
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        sysCaseCategoryMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    public void deleteCategory(Long id) {
        requireCategory(id);
        Long children = sysCaseCategoryMapper.selectCount(
                Wrappers.<SysCaseCategory>lambdaQuery().eq(SysCaseCategory::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "存在子分类，请先删除子分类");
        }
        Long used = sysCaseMapper.selectCount(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCategoryId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该分类已被案件引用，禁止删除");
        }
        sysCaseCategoryMapper.deleteById(id);
    }

    private SysCaseCategory requireCategory(Long id) {
        SysCaseCategory category = sysCaseCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private CategoryVO toVO(SysCaseCategory entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setSortOrder(entity.getSortOrder());
        vo.setLevel(entity.getLevel());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
