package com.archive.search.service;

import java.io.InputStream;

/**
 * 文本提取服务：文档类走 Apache Tika，图片类走本机 Tesseract OCR。
 */
public interface TextExtractionService {

    /**
     * 提取文本；音视频或无法提取时返回空串。
     */
    String extractText(InputStream inputStream, String mimeType, String originalName);
}
