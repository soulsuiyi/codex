package com.archive.core.service.impl;

import com.archive.core.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现。
 */
@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    private final MinioClient minioClient;

    @Value("${minio.buckets.transit}")
    private String transitBucket;

    @Value("${minio.buckets.archive}")
    private String archiveBucket;

    public StorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void ensureBuckets() {
        for (String bucket : List.of(transitBucket, archiveBucket)) {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO 已创建桶: {}", bucket);
                }
            } catch (Exception e) {
                throw new IllegalStateException("MinIO 桶检查失败: " + bucket, e);
            }
        }
    }

    @Override
    public void putFile(String bucket, String objectName, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 上传失败: " + bucket + "/" + objectName, e);
        }
    }

    @Override
    public void copyObject(String sourceBucket, String sourceObject, String targetBucket, String targetObject) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .source(CopySource.builder().bucket(sourceBucket).object(sourceObject).build())
                    .bucket(targetBucket)
                    .object(targetObject)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "MinIO 对象复制失败: " + sourceBucket + "/" + sourceObject + " -> " + targetBucket + "/" + targetObject, e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(5, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 预签名 URL 生成失败: " + bucket + "/" + objectName, e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 读取失败: " + bucket + "/" + objectName, e);
        }
    }

    @Override
    public boolean objectExists(String bucket, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new IllegalStateException("MinIO 对象检查失败: " + bucket + "/" + objectName, e);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 对象检查失败: " + bucket + "/" + objectName, e);
        }
    }

    @Override
    public void removeObject(String bucket, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 删除失败: " + bucket + "/" + objectName, e);
        }
    }
}
