package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端用户 Mapper
 */
@Mapper
public interface MallUserMapper extends BaseMapper<MallUser> {
}
