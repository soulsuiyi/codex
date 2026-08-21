package com.archive.core.service;

import java.io.InputStream;

/**
 * 对象存储服务（MinIO 封装）。
 */
public interface StorageService {

    /**
     * 确保 transit/archive 两个桶存在（不存在则创建）。
     */
    void ensureBuckets();

    /**
     * 上传对象。
     */
    void putFile(String bucket, String objectName, InputStream inputStream, long size, String contentType);

    /**
     * 生成 GET 预签名 URL（5 分钟有效）。
     */
    String presignedGetUrl(String bucket, String objectName);

    /**
     * 判断对象是否存在。
     */
    boolean objectExists(String bucket, String objectName);

    /**
     * 删除对象。
     */
    void removeObject(String bucket, String objectName);
}
