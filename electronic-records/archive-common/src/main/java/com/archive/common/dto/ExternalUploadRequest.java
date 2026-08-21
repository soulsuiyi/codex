package com.archive.common.dto;

/**
 * 开放 API 上传请求（扫描矫正软件接入，codeplan 3.5.2）。
 */
public class ExternalUploadRequest {

    /** 案号 */
    private String caseNo;

    /** 文件名 */
    private String fileName;

    /** 文件内容（Base64 编码） */
    private String fileData;

    /** 分类路径（预留，当前阶段不落库） */
    private String categoryPath;

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileData() {
        return fileData;
    }

    public void setFileData(String fileData) {
        this.fileData = fileData;
    }

    public String getCategoryPath() {
        return categoryPath;
    }

    public void setCategoryPath(String categoryPath) {
        this.categoryPath = categoryPath;
    }
}
