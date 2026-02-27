package com.degel.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper {

    void insertBatch(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    void deleteByRoleId(@Param("roleId") Long roleId);

    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    List<Long> selectMenuIdsByRoleIds(@Param("roleIds") List<Long> roleIds);
}
