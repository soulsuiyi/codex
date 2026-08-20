package com.archive.core.service;

import com.archive.common.dto.CaseCreateRequest;
import com.archive.common.dto.CaseUpdateRequest;
import com.archive.common.dto.CaseVO;
import com.archive.common.dto.PageResult;

/**
 * 案件服务。
 */
public interface CaseService {

    /**
     * 分页查询案件列表，keyword 对案号/案件名称模糊匹配。
     */
    PageResult<CaseVO> pageCases(long page, long size, String keyword);

    /**
     * 案件详情。
     */
    CaseVO getCaseById(Long id);

    /**
     * 新建案件。
     */
    CaseVO createCase(CaseCreateRequest request);

    /**
     * 更新案件（caseNo 与 status 不可修改，ARCHIVED 案件禁止更新）。
     */
    CaseVO updateCase(Long id, CaseUpdateRequest request);
}
