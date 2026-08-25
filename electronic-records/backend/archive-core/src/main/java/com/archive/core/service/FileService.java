package com.archive.core.service;

import com.archive.common.dto.FileVO;
import com.archive.common.dto.FileStreamVO;
import com.archive.common.dto.VersionListVO;
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
     * 以字节数组上传（供开放 API Base64 接入等场景使用），逻辑与普通上传一致（含秒传/版本）。
     */
    FileVO uploadBytes(String caseNo, String fileName, String mimeType, byte[] bytes);

    /**
     * 保存单个分片到本地临时目录。
     */
    void saveChunk(String caseNo, String identifier, int chunkIndex, int totalChunks, MultipartFile chunk);

    /**
     * 合并分片并完成上传落库（含秒传判断）。
     */
    FileVO mergeChunks(String caseNo, String identifier, String fileName, int totalChunks);

    /**
     * 案件文件列表（未删除，按上传时间倒序）。
     */
    List<FileVO> listByCaseNo(String caseNo);

    /**
     * 预览流：图片/PDF 返回带水印流，其他类型 400。
     */
    FileStreamVO previewStream(Long fileId);

    /**
     * HLS 播放列表：音视频转码（缓存）后返回 m3u8 流。
     */
    FileStreamVO hlsPlaylist(Long fileId);

    /**
     * HLS 分片：返回指定 ts 分片流。
     */
    FileStreamVO hlsSegment(Long fileId, String segment);

    /**
     * 下载：图片/PDF 返回带水印流，其他类型返回 Pre-signed URL。
     */
    FileStreamVO downloadStream(Long fileId);

    /**
     * 文件版本列表（当前文件 + 历史版本）。
     */
    VersionListVO versions(Long fileId);

    /**
     * 逻辑删除中转站文件（仅 STAGING 可删）。
     */
    void delete(Long fileId);

    /**
     * 清理超过指定存活时间（毫秒）的分片临时目录（上传中断残留），返回清理目录数。
     */
    int cleanStaleChunks(long maxAgeMillis);

    /**
     * 物理清理逻辑删除超过指定天数的文件：删除 MinIO 对象、历史版本记录与数据库行，返回清理数。
     */
    int purgeDeletedFiles(int olderThanDays);
}
