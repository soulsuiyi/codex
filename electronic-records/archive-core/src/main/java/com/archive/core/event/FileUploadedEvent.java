package com.archive.core.event;

/**
 * 文件上传成功事件：由 core 发布，供后续 OCR/索引等异步消费者使用。
 */
public class FileUploadedEvent {

    private final Long fileId;

    private final String caseNo;

    public FileUploadedEvent(Long fileId, String caseNo) {
        this.fileId = fileId;
        this.caseNo = caseNo;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getCaseNo() {
        return caseNo;
    }
}
