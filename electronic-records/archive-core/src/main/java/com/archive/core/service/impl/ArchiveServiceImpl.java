package com.archive.core.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.common.dto.ArchiveVO;
import com.archive.common.dto.PageResult;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysArchive;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysFile;
import com.archive.core.mapper.SysArchiveMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.service.ArchiveService;
import com.archive.core.service.StorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 归档服务实现。
 */
@Service
public class ArchiveServiceImpl implements ArchiveService {

    private final SysCaseMapper sysCaseMapper;
    private final SysFileMapper sysFileMapper;
    private final SysArchiveMapper sysArchiveMapper;
    private final StorageService storageService;

    @Value("${minio.buckets.archive}")
    private String archiveBucket;

    public ArchiveServiceImpl(SysCaseMapper sysCaseMapper,
                              SysFileMapper sysFileMapper,
                              SysArchiveMapper sysArchiveMapper,
                              StorageService storageService) {
        this.sysCaseMapper = sysCaseMapper;
        this.sysFileMapper = sysFileMapper;
        this.sysArchiveMapper = sysArchiveMapper;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public ArchiveVO archive(String caseNo) {
        if (!StringUtils.hasText(caseNo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号不能为空");
        }
        SysCase caseEntity = sysCaseMapper.selectOne(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, caseNo));
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件不存在");
        }
        if (!"ACTIVE".equals(caseEntity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅办理中案件可归档");
        }
        List<SysFile> files = sysFileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getCaseId, caseEntity.getId())
                .eq(SysFile::getStage, "STAGING"));
        if (files.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件下没有可归档的文件");
        }
        Long archivedCount = sysFileMapper.selectCount(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getCaseId, caseEntity.getId())
                .eq(SysFile::getStage, "ARCHIVED"));
        if (archivedCount != null && archivedCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件已归档，禁止重复归档");
        }

        // 完整性校验：逐个重算 MinIO 对象 SHA-256 与库中指纹比对
        for (SysFile file : files) {
            String actualHash = sha256Object(file.getStorageBucket(), file.getStoragePath());
            if (!file.getFileHash().equalsIgnoreCase(actualHash)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "文件完整性校验失败: " + file.getFileName());
            }
        }

        // 对象复制到归档桶并更新文件状态
        for (SysFile file : files) {
            String targetObject = caseNo + "/" + UUID.randomUUID().toString().replace("-", "");
            storageService.copyObject(file.getStorageBucket(), file.getStoragePath(), archiveBucket, targetObject);
            SysFile update = new SysFile();
            update.setId(file.getId());
            update.setStage("ARCHIVED");
            update.setStorageBucket(archiveBucket);
            update.setStoragePath(targetObject);
            update.setUpdatedAt(LocalDateTime.now());
            sysFileMapper.updateById(update);
        }

        SysCase caseUpdate = new SysCase();
        caseUpdate.setId(caseEntity.getId());
        caseUpdate.setStatus("ARCHIVED");
        caseUpdate.setUpdatedAt(LocalDateTime.now());
        sysCaseMapper.updateById(caseUpdate);

        SysArchive archive = new SysArchive();
        archive.setCaseId(caseEntity.getId());
        archive.setCaseNo(caseNo);
        archive.setOperatorId(currentUserId());
        archive.setFileCount(files.size());
        archive.setStructDocCount(0);
        archive.setStatus("SUCCESS");
        sysArchiveMapper.insert(archive);
        return toVO(sysArchiveMapper.selectById(archive.getId()));
    }

    @Override
    public PageResult<ArchiveVO> pageArchives(long page, long size, String caseNo) {
        var wrapper = Wrappers.<SysArchive>lambdaQuery();
        if (StringUtils.hasText(caseNo)) {
            wrapper.eq(SysArchive::getCaseNo, caseNo);
        }
        wrapper.orderByDesc(SysArchive::getArchivedAt);
        Page<SysArchive> result = sysArchiveMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        List<ArchiveVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ArchiveVO getArchiveById(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "归档ID不能为空");
        }
        SysArchive archive = sysArchiveMapper.selectById(id);
        if (archive == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "归档记录不存在");
        }
        return toVO(archive);
    }

    private String sha256Object(String bucket, String objectName) {
        try (InputStream in = storageService.getObject(bucket, objectName)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件指纹计算失败");
        }
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private ArchiveVO toVO(SysArchive entity) {
        ArchiveVO vo = new ArchiveVO();
        vo.setId(entity.getId());
        vo.setCaseId(entity.getCaseId());
        vo.setCaseNo(entity.getCaseNo());
        vo.setFileCount(entity.getFileCount());
        vo.setStructDocCount(entity.getStructDocCount());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setArchivedAt(entity.getArchivedAt());
        return vo;
    }
}
