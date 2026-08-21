package com.archive.core.service.impl;

import com.archive.common.dto.CaseCreateRequest;
import com.archive.common.dto.CaseUpdateRequest;
import com.archive.common.dto.CaseVO;
import com.archive.common.dto.PageResult;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysCaseCategory;
import com.archive.core.mapper.SysCaseCategoryMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.service.CaseService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 案件服务实现。
 */
@Service
public class CaseServiceImpl implements CaseService {

    private final SysCaseMapper sysCaseMapper;
    private final SysCaseCategoryMapper sysCaseCategoryMapper;

    public CaseServiceImpl(SysCaseMapper sysCaseMapper, SysCaseCategoryMapper sysCaseCategoryMapper) {
        this.sysCaseMapper = sysCaseMapper;
        this.sysCaseCategoryMapper = sysCaseCategoryMapper;
    }

    @Override
    public PageResult<CaseVO> pageCases(long page, long size, String keyword) {
        var wrapper = Wrappers.<SysCase>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysCase::getCaseNo, keyword).or().like(SysCase::getCaseName, keyword));
        }
        wrapper.orderByDesc(SysCase::getCreatedAt);
        Page<SysCase> result = sysCaseMapper.selectPage(new Page<>(page, size), wrapper);
        List<CaseVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public CaseVO getCaseById(Long id) {
        return toVO(requireCase(id));
    }

    @Override
    public CaseVO createCase(CaseCreateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getCaseNo())
                || !StringUtils.hasText(request.getCaseName())
                || request.getCategoryId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号、案件名称、分类ID不能为空");
        }
        Long exists = sysCaseMapper.selectCount(
                Wrappers.<SysCase>lambdaQuery().eq(SysCase::getCaseNo, request.getCaseNo()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案号已存在");
        }
        requireCategory(request.getCategoryId());

        SysCase entity = new SysCase();
        entity.setCaseNo(request.getCaseNo());
        entity.setCaseName(request.getCaseName());
        entity.setCategoryId(request.getCategoryId());
        entity.setCaseType(request.getCaseType());
        entity.setHandlerId(request.getHandlerId());
        entity.setRemark(request.getRemark());
        entity.setStatus("ACTIVE");
        sysCaseMapper.insert(entity);
        return toVO(sysCaseMapper.selectById(entity.getId()));
    }

    @Override
    public CaseVO updateCase(Long id, CaseUpdateRequest request) {
        SysCase entity = requireCase(id);
        if ("ARCHIVED".equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已归档案件禁止修改");
        }
        if (request == null
                || !StringUtils.hasText(request.getCaseName())
                || request.getCategoryId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件名称、分类ID不能为空");
        }
        requireCategory(request.getCategoryId());

        entity.setCaseName(request.getCaseName());
        entity.setCategoryId(request.getCategoryId());
        entity.setCaseType(request.getCaseType());
        entity.setHandlerId(request.getHandlerId());
        entity.setRemark(request.getRemark());
        entity.setUpdatedAt(LocalDateTime.now());
        sysCaseMapper.updateById(entity);
        return toVO(entity);
    }

    private SysCase requireCase(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件ID不能为空");
        }
        SysCase entity = sysCaseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "案件不存在");
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        SysCaseCategory category = sysCaseCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "案件分类不存在");
        }
    }

    private CaseVO toVO(SysCase entity) {
        CaseVO vo = new CaseVO();
        vo.setId(entity.getId());
        vo.setCaseNo(entity.getCaseNo());
        vo.setCaseName(entity.getCaseName());
        vo.setCategoryId(entity.getCategoryId());
        vo.setCaseType(entity.getCaseType());
        vo.setHandlerId(entity.getHandlerId());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
