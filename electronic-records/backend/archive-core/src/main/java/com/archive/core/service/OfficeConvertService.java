package com.archive.core.service;

import java.io.InputStream;

/**
 * Office 文档转 PDF 服务（LibreOffice CLI）。
 */
public interface OfficeConvertService {

    /**
     * 是否支持该 MIME 类型（Word/Excel/PPT）。
     */
    boolean supports(String mimeType);

    /**
     * 转换文档为 PDF 字节流（临时目录中转，调用方无需关心清理）。
     */
    InputStream convertToPdf(String fileName, InputStream source);
}
