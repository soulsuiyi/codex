package com.archive.core.service.impl;

import com.archive.auth.entity.SysApiKey;
import com.archive.auth.entity.SysUser;
import com.archive.auth.mapper.SysApiKeyMapper;
import com.archive.auth.mapper.SysUserMapper;
import com.archive.common.dto.StatsVO;
import com.archive.core.entity.SysArchive;
import com.archive.core.entity.SysBorrowApply;
import com.archive.core.entity.SysCase;
import com.archive.core.entity.SysFile;
import com.archive.core.mapper.SysArchiveMapper;
import com.archive.core.mapper.SysBorrowApplyMapper;
import com.archive.core.mapper.SysCaseMapper;
import com.archive.core.mapper.SysFileMapper;
import com.archive.core.service.StatsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

/**
 * 系统运行统计服务实现。
 */
@Service
public class StatsServiceImpl implements StatsService {

    private final SysUserMapper sysUserMapper;
    private final SysApiKeyMapper sysApiKeyMapper;
    private final SysCaseMapper sysCaseMapper;
    private final SysFileMapper sysFileMapper;
    private final SysArchiveMapper sysArchiveMapper;
    private final SysBorrowApplyMapper sysBorrowApplyMapper;

    public StatsServiceImpl(SysUserMapper sysUserMapper,
                            SysApiKeyMapper sysApiKeyMapper,
                            SysCaseMapper sysCaseMapper,
                            SysFileMapper sysFileMapper,
                            SysArchiveMapper sysArchiveMapper,
                            SysBorrowApplyMapper sysBorrowApplyMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysApiKeyMapper = sysApiKeyMapper;
        this.sysCaseMapper = sysCaseMapper;
        this.sysFileMapper = sysFileMapper;
        this.sysArchiveMapper = sysArchiveMapper;
        this.sysBorrowApplyMapper = sysBorrowApplyMapper;
    }

    @Override
    public StatsVO systemStats() {
        StatsVO vo = new StatsVO();
        vo.setUserCount(count(sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery())));
        vo.setApiKeyCount(count(sysApiKeyMapper.selectCount(Wrappers.<SysApiKey>lambdaQuery())));
        vo.setCaseCount(count(sysCaseMapper.selectCount(Wrappers.<SysCase>lambdaQuery())));
        // @TableLogic 自动排除 is_deleted=true 的记录
        vo.setFileCount(count(sysFileMapper.selectCount(Wrappers.<SysFile>lambdaQuery())));
        vo.setArchivedFileCount(count(sysFileMapper.selectCount(
                Wrappers.<SysFile>lambdaQuery().eq(SysFile::getStage, "ARCHIVED"))));
        vo.setArchiveRecordCount(count(sysArchiveMapper.selectCount(Wrappers.<SysArchive>lambdaQuery())));
        vo.setBorrowApplyCount(count(sysBorrowApplyMapper.selectCount(Wrappers.<SysBorrowApply>lambdaQuery())));
        vo.setActiveBorrowCount(count(sysBorrowApplyMapper.selectCount(
                Wrappers.<SysBorrowApply>lambdaQuery().eq(SysBorrowApply::getStatus, "ACTIVE"))));
        return vo;
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }
}
