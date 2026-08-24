package com.archive.auth.service;

import com.archive.common.dto.ApiKeyCreateRequest;
import com.archive.common.dto.ApiKeyUpdateRequest;
import com.archive.common.dto.ApiKeyVO;
import com.archive.common.dto.PageResult;

/**
 * 开放 API 密钥管理服务。
 */
public interface SystemApiKeyService {

    PageResult<ApiKeyVO> pageApiKeys(long page, long size, String keyword);

    ApiKeyVO createApiKey(ApiKeyCreateRequest request);

    ApiKeyVO updateApiKey(Long id, ApiKeyUpdateRequest request);

    void deleteApiKey(Long id);
}
