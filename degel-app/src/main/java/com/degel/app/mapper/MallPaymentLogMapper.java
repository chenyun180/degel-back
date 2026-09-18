package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallPaymentLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 支付/退款流水 Mapper
 */
@Mapper
public interface MallPaymentLogMapper extends BaseMapper<MallPaymentLog> {

    @Select("SELECT IFNULL(SUM(amount), 0) FROM mall_payment_log WHERE direction = 'pay' AND status = 0")
    BigDecimal sumTotalPay();

    @Select("SELECT IFNULL(SUM(amount), 0) FROM mall_payment_log WHERE direction = 'refund' AND status = 0")
    BigDecimal sumTotalRefund();
}
