package com.archive.core.service;

import java.io.InputStream;

/**
 * 音视频转码服务（FFmpeg 转 HLS）。
 */
public interface MediaConvertService {

    /**
     * 是否支持该 MIME 类型（MP4/AVI/MOV 与常见音频）。
     */
    boolean supports(String mimeType);

    /**
     * 转码为 HLS（m3u8 + ts 分片），返回临时目录结果；失败时内部清理并抛异常。
     */
    HlsResult convertToHls(String fileName, InputStream source);
}
