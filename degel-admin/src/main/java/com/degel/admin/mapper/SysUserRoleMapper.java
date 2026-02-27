package com.degel.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserRoleMapper {

    void insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    void deleteByUserId(@Param("userId") Long userId);

    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
