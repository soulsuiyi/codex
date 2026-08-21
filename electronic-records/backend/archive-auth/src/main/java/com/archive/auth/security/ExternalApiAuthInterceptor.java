package com.archive.auth.security;

import com.archive.auth.entity.SysApiKey;
import com.archive.auth.mapper.SysApiKeyMapper;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

/**
 * 开放 API 鉴权：API Key + HMAC-SHA256 签名（时间戳窗口 + nonce 防重放 + IP 白名单）。
 */
@Component
public class ExternalApiAuthInterceptor implements HandlerInterceptor {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;

    private final SysApiKeyMapper sysApiKeyMapper;
    private final Cache<String, Long> externalApiNonceCache;

    public ExternalApiAuthInterceptor(SysApiKeyMapper sysApiKeyMapper,
                                      Cache<String, Long> externalApiNonceCache) {
        this.sysApiKeyMapper = sysApiKeyMapper;
        this.externalApiNonceCache = externalApiNonceCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiKey = request.getHeader("X-Api-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少API鉴权头（X-Api-Key/X-Timestamp/X-Nonce/X-Signature）");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "时间戳格式错误");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请求时间戳超出允许窗口");
        }
        if (externalApiNonceCache.getIfPresent(nonce) != null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "重复请求（nonce 已使用）");
        }
        externalApiNonceCache.put(nonce, System.currentTimeMillis());

        SysApiKey keyEntity = sysApiKeyMapper.selectOne(
                Wrappers.<SysApiKey>lambdaQuery().eq(SysApiKey::getApiKey, apiKey));
        if (keyEntity == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "API Key 无效");
        }
        if (!Integer.valueOf(1).equals(keyEntity.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "API Key 已禁用");
        }
        if (keyEntity.getExpireTime() != null && keyEntity.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "API Key 已过期");
        }
        if (StringUtils.hasText(keyEntity.getIpWhitelist())) {
            String clientIp = clientIp(request);
            Set<String> whitelist = Set.of(keyEntity.getIpWhitelist().split(","));
            if (!whitelist.contains(clientIp)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "IP 不在白名单内");
            }
        }

        String path = request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String canonical = timestamp + "\n" + request.getMethod() + "\n" + path;
        String expected = hmacSha256Hex(keyEntity.getApiSecret(), canonical);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "签名校验失败");
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
