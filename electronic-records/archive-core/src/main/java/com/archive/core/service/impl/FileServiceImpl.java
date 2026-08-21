package com.archive.core.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.common.dto.FileVersionVO;
import com.archive.common.dto.FileVO;
import com.archive.common.dto.VersionListVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysFile;
import com.archive.core.entity.SysFileVersion;
import com.archive.core.event.FileUploadedEvent;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.mapper.SysFileVersionMapper;
import com.archive.core.service.FileService;
import com.archive.core.service.StorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 文件服务实现（中转站：上传/秒传/分片/多版本/下载/删除/预览）。
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final String CHUNK_ROOT = "archive-chunks";
    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9_-]{1,64}";

    private final SysFileMapper sysFileMapper;
    private final SysFileVersionMapper sysFileVersionMapper;
    private final SysCaseMapper sysCaseMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${minio.buckets.transit}")
    private String transitBucket;

    public FileServiceImpl(SysFileMapper sysFileMapper,
                           SysFileVersionMapper sysFileVersionMapper,
                           SysCaseMapper sysCaseMapper,
                           StorageService storageService,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.sysFileMapper = sysFileMapper;
        this.sysFileVersionMapper = sysFileVersionMapper;
        this.sysCaseMapper = sysCaseMapper;
        this.storageService = storageService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public FileVO upload(String caseNo, MultipartFile file) {
        if (!StringUtils.hasText(caseNo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件大小不能超过 500MB");
        }
        SysCase caseEntity = requireCaseByNo(caseNo);

        String fileHash = sha256(file);
        SysFile existing = findDedupe(caseEntity.getId(), fileHash);
        if (existing != null) {
            return toVO(existing, caseNo);
        }

        String objectName = caseNo + "/" + UUID.randomUUID().toString().replace("-", "");
        try {
            storageService.putFile(transitBucket, objectName,
                    file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件流读取失败");
        }

        SysFile entity = buildEntity(caseEntity, caseNo,
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : objectName,
                file.getContentType(), file.getSize(), fileHash, objectName);
        Long fileId = insertWithVersion(entity, caseNo);
        return toVO(sysFileMapper.selectById(fileId), caseNo);
    }

    @Override
    public void saveChunk(String caseNo, String identifier, int chunkIndex, int totalChunks, MultipartFile chunk) {
        if (!StringUtils.hasText(caseNo) || !StringUtils.hasText(identifier)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号与分片标识不能为空");
        }
        if (!identifier.matches(IDENTIFIER_PATTERN)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法分片标识");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片不能为空");
        }
        if (chunkIndex < 1 || totalChunks < 1 || chunkIndex > totalChunks) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片参数错误");
        }
        requireCaseByNo(caseNo);
        try {
            Path dir = chunkDir(identifier);
            Files.createDirectories(dir);
            Files.write(dir.resolve("chunk_" + chunkIndex), chunk.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "分片保存失败");
        }
    }

    @Override
    public FileVO mergeChunks(String caseNo, String identifier, String fileName, int totalChunks) {
        if (!StringUtils.hasText(caseNo) || !StringUtils.hasText(identifier) || !StringUtils.hasText(fileName)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号、分片标识与文件名不能为空");
        }
        if (!identifier.matches(IDENTIFIER_PATTERN)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法分片标识");
        }
        if (totalChunks < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片数量错误");
        }
        SysCase caseEntity = requireCaseByNo(caseNo);
        Path dir = chunkDir(identifier);
        for (int i = 1; i <= totalChunks; i++) {
            if (!Files.exists(dir.resolve("chunk_" + i))) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "分片不完整，缺少第 " + i + " 片");
            }
        }

        Path merged = dir.resolve("merged.tmp");
        try (OutputStream out = Files.newOutputStream(merged)) {
            for (int i = 1; i <= totalChunks; i++) {
                Files.copy(dir.resolve("chunk_" + i), out);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "分片合并失败");
        }

        try {
            String fileHash = sha256(merged);
            SysFile existing = findDedupe(caseEntity.getId(), fileHash);
            if (existing != null) {
                return toVO(existing, caseNo);
            }
            String objectName = caseNo + "/" + UUID.randomUUID().toString().replace("-", "");
            String mimeType = guessMimeType(fileName);
            try (InputStream in = Files.newInputStream(merged)) {
                storageService.putFile(transitBucket, objectName, in, Files.size(merged), mimeType);
            }
            SysFile entity = buildEntity(caseEntity, caseNo, fileName, mimeType, Files.size(merged), fileHash, objectName);
            Long fileId = insertWithVersion(entity, caseNo);
            return toVO(sysFileMapper.selectById(fileId), caseNo);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "分片合并处理失败");
        } finally {
            deleteChunkDir(identifier);
        }
    }

    @Override
    public List<FileVO> listByCaseNo(String caseNo) {
        SysCase caseEntity = requireCaseByNo(caseNo);
        return sysFileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                        .eq(SysFile::getCaseId, caseEntity.getId())
                        .orderByDesc(SysFile::getCreatedAt))
                .stream()
                .map(file -> toVO(file, caseNo))
                .toList();
    }

    @Override
    public String downloadUrl(Long fileId) {
        SysFile file = requireFile(fileId);
        return storageService.presignedGetUrl(file.getStorageBucket(), file.getStoragePath());
    }

    @Override
    public String previewUrl(Long fileId) {
        SysFile file = requireFile(fileId);
        String mimeType = file.getMimeType();
        if (mimeType == null || (!mimeType.startsWith("image/") && !"application/pdf".equals(mimeType))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "暂不支持预览该文件类型");
        }
        return storageService.presignedGetUrl(file.getStorageBucket(), file.getStoragePath());
    }

    @Override
    public VersionListVO versions(Long fileId) {
        SysFile file = requireFile(fileId);
        List<FileVersionVO> history = sysFileVersionMapper.selectList(
                        Wrappers.<SysFileVersion>lambdaQuery()
                                .eq(SysFileVersion::getFileId, fileId)
                                .orderByDesc(SysFileVersion::getVersion))
                .stream()
                .map(this::toVersionVO)
                .toList();
        return new VersionListVO(toVO(file, null), history);
    }

    @Override
    public void delete(Long fileId) {
        SysFile file = requireFile(fileId);
        if (!"STAGING".equals(file.getStage())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅中转站文件可删除");
        }
        sysFileMapper.deleteById(fileId);
    }

    private SysFile buildEntity(SysCase caseEntity, String caseNo, String fileName, String mimeType,
                                long size, String fileHash, String objectName) {
        SysFile entity = new SysFile();
        entity.setCaseId(caseEntity.getId());
        entity.setFileName(fileName);
        entity.setStorageBucket(transitBucket);
        entity.setStoragePath(objectName);
        entity.setFileHash(fileHash);
        entity.setFileSize(size);
        entity.setMimeType(mimeType);
        entity.setStage("STAGING");
        entity.setCreatedBy(currentUserId());
        return entity;
    }

    private Long insertWithVersion(SysFile entity, String caseNo) {
        SysFile current = sysFileMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getCaseId, entity.getCaseId())
                .eq(SysFile::getFileName, entity.getFileName())
                .eq(SysFile::getStage, "STAGING")
                .orderByDesc(SysFile::getVersion)
                .last("LIMIT 1"));
        if (current != null) {
            SysFileVersion history = new SysFileVersion();
            history.setFileId(current.getId());
            history.setVersion(current.getVersion());
            history.setStoragePath(current.getStoragePath());
            history.setFileName(current.getFileName());
            history.setFileSize(current.getFileSize());
            history.setChangeDesc("上传覆盖");
            history.setCreatedBy(entity.getCreatedBy());
            sysFileVersionMapper.insert(history);
            current.setVersion((current.getVersion() == null ? 0 : current.getVersion()) + 1);
            current.setStorageBucket(entity.getStorageBucket());
            current.setStoragePath(entity.getStoragePath());
            current.setFileHash(entity.getFileHash());
            current.setFileSize(entity.getFileSize());
            current.setMimeType(entity.getMimeType());
            current.setUpdatedAt(LocalDateTime.now());
            sysFileMapper.updateById(current);
            applicationEventPublisher.publishEvent(new FileUploadedEvent(current.getId(), caseNo));
            return current.getId();
        }
        entity.setVersion(1);
        entity.setIsLatest(true);
        sysFileMapper.insert(entity);
        applicationEventPublisher.publishEvent(new FileUploadedEvent(entity.getId(), caseNo));
        return entity.getId();
    }

    private SysFile findDedupe(Long caseId, String fileHash) {
        return sysFileMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getCaseId, caseId)
                .eq(SysFile::getFileHash, fileHash)
                .eq(SysFile::getStage, "STAGING")
                .last("LIMIT 1"));
    }

    private SysCase requireCaseByNo(String caseNo) {
        if (!StringUtils.hasText(caseNo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号不能为空");
        }
        SysCase caseEntity = sysCaseMapper.selectOne(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, caseNo));
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件不存在");
        }
        return caseEntity;
    }

    private SysFile requireFile(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件ID不能为空");
        }
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private Path chunkDir(String identifier) {
        return Paths.get(System.getProperty("java.io.tmpdir"), CHUNK_ROOT, identifier);
    }

    private void deleteChunkDir(String identifier) {
        try {
            Path dir = chunkDir(identifier);
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 忽略单个文件删除失败
                        }
                    });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            log.warn("分片临时目录清理失败: {}", identifier);
        }
    }

    private String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "计算文件指纹失败");
        }
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "计算文件指纹失败");
        }
    }

    private String guessMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private FileVO toVO(SysFile entity, String caseNo) {
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setCaseId(entity.getCaseId());
        vo.setCaseNo(caseNo);
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setMimeType(entity.getMimeType());
        vo.setStorageBucket(entity.getStorageBucket());
        vo.setStage(entity.getStage());
        vo.setVersion(entity.getVersion());
        vo.setIsLatest(entity.getIsLatest());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private FileVersionVO toVersionVO(SysFileVersion entity) {
        FileVersionVO vo = new FileVersionVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setChangeDesc(entity.getChangeDesc());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
