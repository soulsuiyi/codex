package com.archive.core.service;

import com.archive.common.dto.CategoryCreateRequest;
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
}
