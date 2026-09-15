package com.degel.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.marketing.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;

/** 对齐 BannerMapper：@Mapper 双保险（启动类已 @MapperScan） */
@Mapper
public interface SeckillProductMapper extends BaseMapper<SeckillProduct> {
}
