package com.archive.core.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.common.dto.FileVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysFile;
import com.archive.core.event.FileUploadedEvent;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.service.FileService;
import com.archive.core.service.StorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 文件服务实现（中转站）。
 */
@Service
public class FileServiceImpl implements FileService {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    private final SysFileMapper sysFileMapper;
    private final SysCaseMapper sysCaseMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${minio.buckets.transit}")
    private String transitBucket;

    public FileServiceImpl(SysFileMapper sysFileMapper,
                           SysCaseMapper sysCaseMapper,
                           StorageService storageService,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.sysFileMapper = sysFileMapper;
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
        SysCase caseEntity = sysCaseMapper.selectOne(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, caseNo));
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件不存在");
        }

        String fileHash = sha256(file);
        SysFile existing = sysFileMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getCaseId, caseEntity.getId())
                .eq(SysFile::getFileHash, fileHash)
                .eq(SysFile::getStage, "STAGING")
                .last("LIMIT 1"));
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

        SysFile entity = new SysFile();
        entity.setCaseId(caseEntity.getId());
        entity.setFileName(StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : objectName);
        entity.setStorageBucket(transitBucket);
        entity.setStoragePath(objectName);
        entity.setFileHash(fileHash);
        entity.setFileSize(file.getSize());
        entity.setMimeType(file.getContentType());
        entity.setStage("STAGING");
        entity.setVersion(1);
        entity.setIsLatest(true);
        entity.setCreatedBy(currentUserId());
        sysFileMapper.insert(entity);
        applicationEventPublisher.publishEvent(new FileUploadedEvent(entity.getId(), caseNo));
        return toVO(sysFileMapper.selectById(entity.getId()), caseNo);
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
    public void delete(Long fileId) {
        SysFile file = requireFile(fileId);
        if (!"STAGING".equals(file.getStage())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅中转站文件可删除");
        }
        sysFileMapper.deleteById(fileId);
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
}
