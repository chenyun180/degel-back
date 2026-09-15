package com.degel.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.marketing.entity.Banner;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Mapper 属双保险：模块启动类已 @MapperScan("com.degel.marketing.mapper")，
 * 与 CouponMapper（无 @Mapper）同样会被扫描注册，不会重复注册（mybatis-spring-boot
 * 自动扫描在 MapperScan 存在时不生效）。
 */
@Mapper
public interface BannerMapper extends BaseMapper<Banner> {
}
