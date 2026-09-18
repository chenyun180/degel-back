package com.degel.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.order.entity.SettlementAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 商家结算账户 Mapper。余额变动全部走原子 UPDATE（InnoDB 行锁），
 * 不用"先查后改"——提现与退款扣回并发下推导式有读改写竞态。
 */
@Mapper
public interface SettlementAccountMapper extends BaseMapper<SettlementAccount> {

    @Select("SELECT * FROM settlement_account WHERE shop_id = #{shopId} AND del_flag = 0")
    SettlementAccount selectByShopId(@Param("shopId") Long shopId);

    /** 结算入账：账户不存在则初始化，存在则原子累加（单条 SQL，免先查后插竞态） */
    @Update("INSERT INTO settlement_account (shop_id, balance, total_settled) "
            + "VALUES (#{shopId}, #{amount}, #{amount}) "
            + "ON DUPLICATE KEY UPDATE balance = balance + #{amount}, total_settled = total_settled + #{amount}")
    int credit(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 提现扣款：余额守卫（WHERE balance>=?），affected=0 即余额不足 */
    @Update("UPDATE settlement_account SET balance = balance - #{amount}, total_withdrawn = total_withdrawn + #{amount} "
            + "WHERE shop_id = #{shopId} AND balance >= #{amount} AND del_flag = 0")
    int deductGuarded(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 退款扣回：无条件扣（允许扣成负数=欠款）；仅结算后退款路径使用 */
    @Update("UPDATE settlement_account SET balance = balance - #{amount} "
            + "WHERE shop_id = #{shopId} AND del_flag = 0")
    int deductAllowNegative(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 平台资金总览：全部商家账户余额合计（平台对商家的负债；负值=商家欠款） */
    @Select("SELECT IFNULL(SUM(balance), 0) FROM settlement_account WHERE del_flag = 0")
    BigDecimal sumShopBalance();

    /** 平台资金总览：商家欠款合计（负余额部分的绝对值） */
    @Select("SELECT IFNULL(SUM(-balance), 0) FROM settlement_account WHERE del_flag = 0 AND balance < 0")
    BigDecimal sumShopDebt();
}
