package com.archive.core.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 动态水印服务：Graphics2D（图片）/ PDFBox（PDF）内存合成、流式输出、不落盘。
 */
public interface WatermarkService {

    /**
     * 是否支持水印（图片与 PDF）。
     */
    boolean supports(String mimeType);

    /**
     * 图片加水印（保持原格式，不支持格式回退 PNG）。
     */
    InputStream watermarkImage(InputStream source, String mimeType, String text);

    /**
     * PDF 逐页加水印。
     */
    InputStream watermarkPdf(InputStream source, String text);

    /**
     * 水印文案："前缀 - 用户名 - 时间 内部文件-禁止外传"。
     */
    default String buildText(String prefix, String username) {
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now());
        return prefix + " - " + username + " - " + time + " 内部文件-禁止外传";
    }
}
