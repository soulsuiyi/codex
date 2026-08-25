package com.archive.core.mapper;

import com.archive.core.entity.SysFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 文件表 Mapper。
 */
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 物理删除记录（绕过 @TableLogic 逻辑删除，仅供 30 天到期清理任务使用）。
     */
    @Delete("DELETE FROM sys_file WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
