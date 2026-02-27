package com.degel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.admin.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
