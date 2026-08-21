package com.archive.search.service;

import com.archive.common.dto.PageResult;
import com.archive.common.dto.SearchResultVO;

/**
 * 全文检索服务。
 * 底层使用 H2 内置全文检索（FULLTEXT 索引 + FT_SEARCH_DATA 相关度查询），不使用 Elasticsearch。
 */
public interface SearchService {

    /**
     * 全文检索文件：按相关度降序 > 上传时间降序排序，page/size 分页，命中片段含 &lt;em&gt; 高亮。
     */
    PageResult<SearchResultVO> search(String keyword, long page, long size);

    /**
     * 文件详情（含高亮片段）。
     */
    SearchResultVO detail(Long id, String keyword);
}
