package com.archive.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件表 sys_file（中转站+归档区统一存储）。
 */
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 案件ID */
    private Long caseId;

    /** 原始文件名 */
    private String fileName;

    /** MinIO Bucket 名 */
    private String storageBucket;

    /** MinIO Object Key */
    private String storagePath;

    /** SHA-256 指纹 */
    private String fileHash;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 阶段: STAGING-中转站 ARCHIVED-归档区 */
    private String stage;

    /** 版本号 */
    private Integer version;

    /** 是否最新版 */
    private Boolean isLatest;

    /** 预览文件路径（转码后） */
    private String previewPath;

    /** 全文检索提取文本（H2 FULLTEXT 索引列） */
    private String content;

    /** 上传人ID */
    private Long createdBy;

    /** 上传时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记（AGENTS.md：中转站删除为逻辑删除；H2 BOOLEAN 使用 true/false） */
    @TableLogic(value = "false", delval = "true")
    private Boolean isDeleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIsLatest() {
        return isLatest;
    }

    public void setIsLatest(Boolean isLatest) {
        this.isLatest = isLatest;
    }

    public String getPreviewPath() {
        return previewPath;
    }

    public void setPreviewPath(String previewPath) {
        this.previewPath = previewPath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
