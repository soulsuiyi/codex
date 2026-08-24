package com.archive.core.service;

import com.archive.common.dto.DictRequest;
import com.archive.common.dto.DictVO;
import com.archive.common.dto.PageResult;

import java.util.List;

/**
 * 数据字典服务。
 */
public interface DictService {

    PageResult<DictVO> pageDicts(long page, long size, String dictType, String keyword);

    List<DictVO> listByType(String dictType);

    DictVO createDict(DictRequest request);

    DictVO updateDict(Long id, DictRequest request);

    void deleteDict(Long id);
}
