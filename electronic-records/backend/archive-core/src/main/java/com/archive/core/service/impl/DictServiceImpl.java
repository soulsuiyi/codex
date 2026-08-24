package com.archive.core.service.impl;

import com.archive.common.dto.DictRequest;
import com.archive.common.dto.DictVO;
import com.archive.common.dto.PageResult;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.core.entity.SysDict;
import com.archive.core.mapper.SysDictMapper;
import com.archive.core.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典服务实现。
 */
@Service
public class DictServiceImpl implements DictService {

    private final SysDictMapper dictMapper;

    public DictServiceImpl(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public PageResult<DictVO> pageDicts(long page, long size, String dictType, String keyword) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(dictType)) {
            wrapper.eq(SysDict::getDictType, dictType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysDict::getDictCode, keyword)
                    .or().like(SysDict::getDictLabel, keyword)
                    .or().like(SysDict::getDictValue, keyword));
        }
        wrapper.orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortOrder)
                .orderByAsc(SysDict::getId);
        Page<SysDict> result = dictMapper.selectPage(new Page<>(page, size), wrapper);
        List<DictVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<DictVO> listByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字典类型不能为空");
        }
        return dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                        .eq(SysDict::getDictType, dictType.trim())
                        .orderByAsc(SysDict::getSortOrder)
                        .orderByAsc(SysDict::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public DictVO createDict(DictRequest request) {
        checkRequest(request);
        checkUnique(request.getDictType(), request.getDictCode(), null);
        SysDict dict = new SysDict();
        dict.setDictType(request.getDictType().trim());
        dict.setDictCode(request.getDictCode().trim());
        dict.setDictLabel(request.getDictLabel());
        dict.setDictValue(request.getDictValue());
        dict.setSortOrder(request.getSortOrder());
        dict.setRemark(request.getRemark());
        dictMapper.insert(dict);
        return toVO(dict);
    }

    @Override
    public DictVO updateDict(Long id, DictRequest request) {
        checkRequest(request);
        SysDict dict = requireDict(id);
        checkUnique(request.getDictType(), request.getDictCode(), id);
        dict.setDictType(request.getDictType().trim());
        dict.setDictCode(request.getDictCode().trim());
        dict.setDictLabel(request.getDictLabel());
        dict.setDictValue(request.getDictValue());
        dict.setSortOrder(request.getSortOrder());
        dict.setRemark(request.getRemark());
        dictMapper.updateById(dict);
        return toVO(dict);
    }

    @Override
    public void deleteDict(Long id) {
        requireDict(id);
        dictMapper.deleteById(id);
    }

    private void checkRequest(DictRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getDictType())
                || !StringUtils.hasText(request.getDictCode())
                || !StringUtils.hasText(request.getDictLabel())
                || !StringUtils.hasText(request.getDictValue())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字典类型/编码/标签/值不能为空");
        }
    }

    private void checkUnique(String dictType, String dictCode, Long excludeId) {
        LambdaQueryWrapper<SysDict> wrapper = Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, dictType.trim())
                .eq(SysDict::getDictCode, dictCode.trim());
        if (excludeId != null) {
            wrapper.ne(SysDict::getId, excludeId);
        }
        Long count = dictMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同类型下字典编码已存在");
        }
    }

    private SysDict requireDict(Long id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        return dict;
    }

    private DictVO toVO(SysDict dict) {
        DictVO vo = new DictVO();
        vo.setId(dict.getId());
        vo.setDictType(dict.getDictType());
        vo.setDictCode(dict.getDictCode());
        vo.setDictLabel(dict.getDictLabel());
        vo.setDictValue(dict.getDictValue());
        vo.setSortOrder(dict.getSortOrder());
        vo.setRemark(dict.getRemark());
        return vo;
    }
}
