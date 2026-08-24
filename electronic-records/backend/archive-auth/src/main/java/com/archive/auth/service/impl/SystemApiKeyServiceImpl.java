package com.archive.auth.service.impl;

import com.archive.auth.entity.SysApiKey;
import com.archive.auth.mapper.SysApiKeyMapper;
import com.archive.auth.service.SystemApiKeyService;
import com.archive.common.dto.ApiKeyCreateRequest;
import com.archive.common.dto.ApiKeyUpdateRequest;
import com.archive.common.dto.ApiKeyVO;
import com.archive.common.dto.PageResult;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 开放 API 密钥管理服务实现。
 */
@Service
public class SystemApiKeyServiceImpl implements SystemApiKeyService {

    private static final String SECRET_MASK = "******";

    private final SysApiKeyMapper apiKeyMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public SystemApiKeyServiceImpl(SysApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public PageResult<ApiKeyVO> pageApiKeys(long page, long size, String keyword) {
        LambdaQueryWrapper<SysApiKey> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysApiKey::getAppName, keyword)
                    .or().like(SysApiKey::getApiKey, keyword));
        }
        wrapper.orderByDesc(SysApiKey::getCreatedAt);
        Page<SysApiKey> result = apiKeyMapper.selectPage(new Page<>(page, size), wrapper);
        List<ApiKeyVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ApiKeyVO createApiKey(ApiKeyCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getAppName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "应用名称不能为空");
        }
        SysApiKey key = new SysApiKey();
        key.setAppName(request.getAppName().trim());
        key.setApiKey(generateUniqueKey());
        key.setApiSecret(randomHex(48));
        key.setIpWhitelist(request.getIpWhitelist());
        key.setStatus(1);
        key.setExpireTime(request.getExpireTime());
        apiKeyMapper.insert(key);

        ApiKeyVO vo = toVO(key);
        vo.setApiSecret(key.getApiSecret());
        return vo;
    }

    @Override
    public ApiKeyVO updateApiKey(Long id, ApiKeyUpdateRequest request) {
        SysApiKey key = requireKey(id);
        if (StringUtils.hasText(request.getAppName())) {
            key.setAppName(request.getAppName().trim());
        }
        if (request.getIpWhitelist() != null) {
            key.setIpWhitelist(request.getIpWhitelist());
        }
        if (request.getStatus() != null) {
            key.setStatus(request.getStatus());
        }
        key.setExpireTime(request.getExpireTime());
        apiKeyMapper.updateById(key);
        return toVO(key);
    }

    @Override
    public void deleteApiKey(Long id) {
        requireKey(id);
        apiKeyMapper.deleteById(id);
    }

    private SysApiKey requireKey(Long id) {
        SysApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "API 密钥不存在");
        }
        return key;
    }

    private String generateUniqueKey() {
        for (int i = 0; i < 10; i++) {
            String candidate = "ak_" + randomHex(16);
            Long count = apiKeyMapper.selectCount(
                    Wrappers.<SysApiKey>lambdaQuery().eq(SysApiKey::getApiKey, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "密钥生成失败，请重试");
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private ApiKeyVO toVO(SysApiKey key) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(key.getId());
        vo.setAppName(key.getAppName());
        vo.setApiKey(key.getApiKey());
        vo.setApiSecret(SECRET_MASK);
        vo.setIpWhitelist(key.getIpWhitelist());
        vo.setStatus(key.getStatus());
        vo.setExpireTime(key.getExpireTime());
        vo.setCreatedAt(key.getCreatedAt());
        return vo;
    }
}
