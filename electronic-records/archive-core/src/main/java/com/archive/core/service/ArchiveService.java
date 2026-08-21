package com.archive.core.service;

import com.archive.common.dto.ArchiveVO;
import com.archive.common.dto.PageResult;

/**
 * 归档服务。
 */
public interface ArchiveService {

    /**
     * 一键归档：完整性校验 → Hash 比对 → 对象复制到归档桶 → 事务内状态更新 → 写归档记录。
     */
    ArchiveVO archive(String caseNo);

    /**
     * 归档记录分页列表（caseNo 可选过滤，按归档时间倒序）。
     */
    PageResult<ArchiveVO> pageArchives(long page, long size, String caseNo);

    /**
     * 归档详情。
     */
    ArchiveVO getArchiveById(Long id);
}
