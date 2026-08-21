package com.archive.core.service;

import com.archive.common.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务（中转站）。
 */
public interface FileService {

    /**
     * 上传文件至中转站，自动计算 SHA-256 并支持秒传。
     */
    FileVO upload(String caseNo, MultipartFile file);

    /**
     * 案件文件列表（未删除，按上传时间倒序）。
     */
    List<FileVO> listByCaseNo(String caseNo);

    /**
     * 生成文件下载 Pre-signed URL（5 分钟有效）。
     */
    String downloadUrl(Long fileId);

    /**
     * 逻辑删除中转站文件（仅 STAGING 可删）。
     */
    void delete(Long fileId);
}
