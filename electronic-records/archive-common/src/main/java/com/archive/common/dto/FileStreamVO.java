package com.archive.common.dto;

import java.io.InputStream;

/**
 * 文件流响应：带水印内容流（stream 非空）或 Pre-signed URL（presignedUrl 非空）。
 */
public class FileStreamVO {

    private InputStream stream;
    private String presignedUrl;
    private String contentType;
    private String fileName;

    public FileStreamVO() {
    }

    public FileStreamVO(InputStream stream, String presignedUrl, String contentType, String fileName) {
        this.stream = stream;
        this.presignedUrl = presignedUrl;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public InputStream getStream() {
        return stream;
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public String getPresignedUrl() {
        return presignedUrl;
    }

    public void setPresignedUrl(String presignedUrl) {
        this.presignedUrl = presignedUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
