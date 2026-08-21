package com.archive.core.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.archive.common.dto.BorrowApplyRequest;
import com.archive.common.dto.BorrowApplyVO;
import com.archive.common.dto.BorrowApprovalRequest;
import com.archive.common.dto.BorrowDownloadRequest;
import com.archive.common.dto.BorrowTokenVO;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.FileStreamVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.core.cache.BorrowTokenCache;
import com.archive.core.entity.SysBorrowApply;
import com.archive.core.entity.SysBorrowApproval;
import com.archive.core.entity.SysBorrowToken;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysFile;
import com.archive.core.mapper.SysBorrowApplyMapper;
import com.archive.core.mapper.SysBorrowApprovalMapper;
import com.archive.core.mapper.SysBorrowTokenMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.service.BorrowService;
import com.archive.core.service.StorageService;
import com.archive.core.service.WatermarkService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 借阅服务实现。
 */
@Service
public class BorrowServiceImpl implements BorrowService {

    private final SysBorrowApplyMapper sysBorrowApplyMapper;
    private final SysBorrowApprovalMapper sysBorrowApprovalMapper;
    private final SysBorrowTokenMapper sysBorrowTokenMapper;
    private final SysCaseMapper sysCaseMapper;
    private final SysFileMapper sysFileMapper;
    private final StorageService storageService;
    private final WatermarkService watermarkService;
    private final SysUserMapper sysUserMapper;
    private final Cache<String, BorrowTokenCache> borrowTokenCache;

    public BorrowServiceImpl(SysBorrowApplyMapper sysBorrowApplyMapper,
                             SysBorrowApprovalMapper sysBorrowApprovalMapper,
                             SysBorrowTokenMapper sysBorrowTokenMapper,
                             SysCaseMapper sysCaseMapper,
                             SysFileMapper sysFileMapper,
                             StorageService storageService,
                             WatermarkService watermarkService,
                             SysUserMapper sysUserMapper,
                             Cache<String, BorrowTokenCache> borrowTokenCache) {
        this.sysBorrowApplyMapper = sysBorrowApplyMapper;
        this.sysBorrowApprovalMapper = sysBorrowApprovalMapper;
        this.sysBorrowTokenMapper = sysBorrowTokenMapper;
        this.sysCaseMapper = sysCaseMapper;
        this.sysFileMapper = sysFileMapper;
        this.storageService = storageService;
        this.watermarkService = watermarkService;
        this.sysUserMapper = sysUserMapper;
        this.borrowTokenCache = borrowTokenCache;
    }

    @Override
    public BorrowApplyVO apply(BorrowApplyRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getCaseNo())
                || request.getFileIds() == null
                || request.getFileIds().isEmpty()
                || !StringUtils.hasText(request.getReason())
                || request.getExpireTime() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号、文件列表、借阅理由与到期时间不能为空");
        }
        if (!request.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "期望归还时间必须晚于当前时间");
        }
        SysCase caseEntity = sysCaseMapper.selectOne(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, request.getCaseNo()));
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件不存在");
        }
        List<SysFile> files = sysFileMapper.selectBatchIds(request.getFileIds());
        Set<Long> requested = new HashSet<>(request.getFileIds());
        if (files.size() != requested.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "存在无效的文件ID");
        }
        for (SysFile file : files) {
            if (!caseEntity.getId().equals(file.getCaseId()) || !"ARCHIVED".equals(file.getStage())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "仅可借阅本案件归档区文件");
            }
        }

        SysBorrowApply entity = new SysBorrowApply();
        entity.setCaseId(caseEntity.getId());
        entity.setCaseNo(request.getCaseNo());
        entity.setApplicantId(currentUserId());
        entity.setFileIds(request.getFileIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        entity.setReason(request.getReason());
        entity.setNeedDownload(Boolean.TRUE.equals(request.getNeedDownload()));
        entity.setExpireTime(request.getExpireTime());
        entity.setStatus("PENDING_SECRETARY");
        sysBorrowApplyMapper.insert(entity);
        return toVO(sysBorrowApplyMapper.selectById(entity.getId()));
    }

    @Override
    public PageResult<BorrowApplyVO> myList(long page, long size) {
        Page<SysBorrowApply> result = sysBorrowApplyMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.max(size, 1)),
                Wrappers.<SysBorrowApply>lambdaQuery()
                        .eq(SysBorrowApply::getApplicantId, currentUserId())
                        .orderByDesc(SysBorrowApply::getCreatedAt));
        List<BorrowApplyVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public BorrowApplyVO approve(Long id, BorrowApprovalRequest request) {
        SysBorrowApply apply = requireApply(id);
        if (request == null || !StringUtils.hasText(request.getResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批结果不能为空");
        }
        if (!"APPROVED".equals(request.getResult()) && !"REJECTED".equals(request.getResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批结果仅支持 APPROVED/REJECTED");
        }

        String approvalStep;
        String nextStatus;
        if ("PENDING_SECRETARY".equals(apply.getStatus())) {
            StpUtil.checkRole("SECRETARY");
            approvalStep = "SECRETARY";
            nextStatus = "APPROVED".equals(request.getResult()) ? "PENDING_ADMIN" : "REJECTED";
        } else if ("PENDING_ADMIN".equals(apply.getStatus())) {
            StpUtil.checkRole("ARCHIVIST");
            approvalStep = "ADMIN";
            nextStatus = "APPROVED".equals(request.getResult()) ? "ACTIVE" : "REJECTED";
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可审批");
        }

        SysBorrowApproval approval = new SysBorrowApproval();
        approval.setApplyId(id);
        approval.setApproverId(currentUserId());
        approval.setApprovalStep(approvalStep);
        approval.setResult(request.getResult());
        approval.setComment(request.getComment());
        sysBorrowApprovalMapper.insert(approval);

        if ("ACTIVE".equals(nextStatus)) {
            issueToken(apply);
        }

        SysBorrowApply update = new SysBorrowApply();
        update.setId(id);
        update.setStatus(nextStatus);
        update.setUpdatedAt(LocalDateTime.now());
        sysBorrowApplyMapper.updateById(update);
        return toVO(sysBorrowApplyMapper.selectById(id));
    }

    @Override
    public BorrowTokenVO token(Long id) {
        SysBorrowApply apply = requireApply(id);
        if (!"ACTIVE".equals(apply.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "借阅未生效");
        }
        if (!apply.getApplicantId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看他人借阅Token");
        }
        SysBorrowToken token = sysBorrowTokenMapper.selectOne(Wrappers.<SysBorrowToken>lambdaQuery()
                .eq(SysBorrowToken::getApplyId, id)
                .orderByDesc(SysBorrowToken::getId)
                .last("LIMIT 1"));
        if (token == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "借阅Token不存在");
        }
        return new BorrowTokenVO(token.getTokenValue(), token.getExpireTime(), token.getAllowDownload());
    }

    @Override
    public FileStreamVO download(Long id, BorrowDownloadRequest request) {
        SysBorrowApply apply = requireApply(id);
        if (!"ACTIVE".equals(apply.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "借阅未生效或已失效");
        }
        if (request == null || !StringUtils.hasText(request.getTokenValue()) || request.getFileId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Token与文件ID不能为空");
        }
        BorrowTokenCache cache = loadCachedToken(request.getTokenValue());
        if (cache == null || !id.equals(cache.getApplyId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "借阅Token无效");
        }
        if (!cache.getUserId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Token不属于当前用户");
        }
        if (cache.getExpireTime() != null && cache.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "借阅Token已过期");
        }
        if (!Boolean.TRUE.equals(cache.getAllowDownload())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "未授权下载，请重新申请下载权限");
        }
        Set<String> authorizedFiles = new HashSet<>(Arrays.asList(cache.getFileIds().split(",")));
        if (!authorizedFiles.contains(String.valueOf(request.getFileId()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "文件不在借阅授权范围内");
        }
        SysFile file = sysFileMapper.selectById(request.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        if (watermarkService.supports(file.getMimeType())) {
            InputStream source = storageService.getObject(file.getStorageBucket(), file.getStoragePath());
            String text = watermarkService.buildText("已归档", currentUsername());
            InputStream watermarked = "application/pdf".equals(file.getMimeType())
                    ? watermarkService.watermarkPdf(source, text)
                    : watermarkService.watermarkImage(source, file.getMimeType(), text);
            return new FileStreamVO(watermarked, null, file.getMimeType(), file.getFileName());
        }
        return new FileStreamVO(null,
                storageService.presignedGetUrl(file.getStorageBucket(), file.getStoragePath()),
                file.getMimeType(), file.getFileName());
    }

    @Override
    @Transactional
    public BorrowApplyVO returnFile(Long id) {
        SysBorrowApply apply = requireApply(id);
        if (!apply.getApplicantId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人借阅");
        }
        if (!"ACTIVE".equals(apply.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅借阅中的申请可归还");
        }
        SysBorrowToken token = sysBorrowTokenMapper.selectOne(Wrappers.<SysBorrowToken>lambdaQuery()
                .eq(SysBorrowToken::getApplyId, id)
                .orderByDesc(SysBorrowToken::getId)
                .last("LIMIT 1"));
        if (token != null && !Boolean.TRUE.equals(token.getIsRevoked())) {
            SysBorrowToken tokenUpdate = new SysBorrowToken();
            tokenUpdate.setId(token.getId());
            tokenUpdate.setIsRevoked(true);
            sysBorrowTokenMapper.updateById(tokenUpdate);
            borrowTokenCache.invalidate(token.getTokenValue());
        }
        SysBorrowApply update = new SysBorrowApply();
        update.setId(id);
        update.setStatus("RETURNED");
        update.setUpdatedAt(LocalDateTime.now());
        sysBorrowApplyMapper.updateById(update);
        return toVO(sysBorrowApplyMapper.selectById(id));
    }

    @Override
    @Transactional
    public int expireExpiredBorrows() {
        List<SysBorrowToken> expired = sysBorrowTokenMapper.selectList(Wrappers.<SysBorrowToken>lambdaQuery()
                .eq(SysBorrowToken::getIsRevoked, false)
                .lt(SysBorrowToken::getExpireTime, LocalDateTime.now()));
        for (SysBorrowToken token : expired) {
            SysBorrowApply apply = sysBorrowApplyMapper.selectById(token.getApplyId());
            if (apply != null && "ACTIVE".equals(apply.getStatus())) {
                SysBorrowApply applyUpdate = new SysBorrowApply();
                applyUpdate.setId(apply.getId());
                applyUpdate.setStatus("EXPIRED");
                applyUpdate.setUpdatedAt(LocalDateTime.now());
                sysBorrowApplyMapper.updateById(applyUpdate);
            }
            SysBorrowToken tokenUpdate = new SysBorrowToken();
            tokenUpdate.setId(token.getId());
            tokenUpdate.setIsRevoked(true);
            sysBorrowTokenMapper.updateById(tokenUpdate);
            borrowTokenCache.invalidate(token.getTokenValue());
        }
        return expired.size();
    }

    private void issueToken(SysBorrowApply apply) {
        String tokenValue = UUID.randomUUID().toString().replace("-", "");
        SysBorrowToken token = new SysBorrowToken();
        token.setApplyId(apply.getId());
        token.setUserId(apply.getApplicantId());
        token.setTokenValue(tokenValue);
        token.setFileIds(apply.getFileIds());
        token.setAllowDownload(Boolean.TRUE.equals(apply.getNeedDownload()));
        token.setExpireTime(apply.getExpireTime());
        token.setIsRevoked(false);
        sysBorrowTokenMapper.insert(token);
        borrowTokenCache.put(tokenValue, new BorrowTokenCache(
                apply.getId(), apply.getApplicantId(), apply.getFileIds(),
                token.getAllowDownload(), apply.getExpireTime()));
    }

    private BorrowTokenCache loadCachedToken(String tokenValue) {
        BorrowTokenCache cached = borrowTokenCache.getIfPresent(tokenValue);
        if (cached != null) {
            return cached;
        }
        SysBorrowToken token = sysBorrowTokenMapper.selectOne(Wrappers.<SysBorrowToken>lambdaQuery()
                .eq(SysBorrowToken::getTokenValue, tokenValue)
                .last("LIMIT 1"));
        if (token == null || Boolean.TRUE.equals(token.getIsRevoked())) {
            return null;
        }
        BorrowTokenCache cache = new BorrowTokenCache(
                token.getApplyId(), token.getUserId(), token.getFileIds(),
                token.getAllowDownload(), token.getExpireTime());
        borrowTokenCache.put(tokenValue, cache);
        return cache;
    }

    private SysBorrowApply requireApply(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "借阅申请ID不能为空");
        }
        SysBorrowApply apply = sysBorrowApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "借阅申请不存在");
        }
        return apply;
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }

    private String currentUsername() {
        Long userId = currentUserId();
        if (userId == null) {
            return "系统";
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? "未知" : user.getUsername();
    }

    private BorrowApplyVO toVO(SysBorrowApply entity) {
        BorrowApplyVO vo = new BorrowApplyVO();
        vo.setId(entity.getId());
        vo.setCaseId(entity.getCaseId());
        vo.setCaseNo(entity.getCaseNo());
        if (StringUtils.hasText(entity.getFileIds())) {
            vo.setFileIds(Arrays.stream(entity.getFileIds().split(","))
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .toList());
        }
        vo.setReason(entity.getReason());
        vo.setNeedDownload(entity.getNeedDownload());
        vo.setExpireTime(entity.getExpireTime());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
