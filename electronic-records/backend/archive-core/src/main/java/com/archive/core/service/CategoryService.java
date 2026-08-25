package com.archive.core.service;

import com.archive.common.dto.CategoryCreateRequest;
import com.archive.common.dto.CategoryUpdateRequest;
import com.archive.common.dto.CategoryVO;

import java.util.List;

/**
 * 案件分类服务。
 */
public interface CategoryService {

    /**
     * 案件分类树（parent_id=0 为根，sort_order 升序）。
     */
    List<CategoryVO> tree();

    /**
     * 新建案件分类。
     */
    CategoryVO createCategory(CategoryCreateRequest request);

    /**
     * 更新案件分类（名称与排序；父级与层级不可改）。
     */
    CategoryVO updateCategory(Long id, CategoryUpdateRequest request);

    /**
     * 删除案件分类（存在子分类或已被案件引用时禁止）。
     */
    void deleteCategory(Long id);
}
