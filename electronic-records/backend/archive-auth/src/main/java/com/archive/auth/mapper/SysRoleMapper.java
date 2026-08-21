package com.archive.auth.mapper;

import com.archive.auth.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表 Mapper。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户拥有的角色编码列表。
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
