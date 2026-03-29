package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallPaymentLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付/退款流水 Mapper
 */
@Mapper
public interface MallPaymentLogMapper extends BaseMapper<MallPaymentLog> {
}
