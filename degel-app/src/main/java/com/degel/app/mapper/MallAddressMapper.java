package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址 Mapper
 */
@Mapper
public interface MallAddressMapper extends BaseMapper<MallAddress> {
}
