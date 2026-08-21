package com.archive.core.service.impl;

import com.archive.common.dto.CategoryCreateRequest;
import com.archive.common.dto.CategoryVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysCaseCategoryMapper;
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

    public CategoryServiceImpl(SysCaseCategoryMapper sysCaseCategoryMapper) {
        this.sysCaseCategoryMapper = sysCaseCategoryMapper;
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
