package com.degel.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.order.entity.SettlementOrder;
import com.degel.order.vo.SettleCandidateVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 结算明细 Mapper。三处关键原子语义：
 * 候选查询三重 NOT EXISTS / markVoidIfPending(CAS 0→2) / markDeducted(CAS 1→2)
 */
@Mapper
public interface SettlementOrderMapper extends BaseMapper<SettlementOrder> {

    /**
     * T+7 结算候选：已完成且确认收货满 N 天。
     * 三重 NOT EXISTS：
     *   a) 已退款完成（a.status=3）→ 永不入账；
     *   b) 售后进行中（a.status IN 0,1,2）→ 本轮跳过，下轮复查（避免"结算完紧接着退款扣回"）；
     *   c) 已有结算明细 → uk_order_id 幂等兜底。
     */
    @Select("SELECT o.id AS orderId, o.order_no AS orderNo, o.shop_id AS shopId, o.user_id AS userId, "
            + "o.pay_amount AS payAmount, o.platform_subsidy AS platformSubsidy, o.points_deduct AS pointsDeduct "
            + "FROM order_info o "
            + "WHERE o.del_flag = 0 AND o.status = 3 "
            + "AND o.receive_time <= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "AND NOT EXISTS (SELECT 1 FROM order_after_sale a "
            + "  WHERE a.order_id = o.id AND a.del_flag = 0 AND a.status = 3) "
            + "AND NOT EXISTS (SELECT 1 FROM order_after_sale a "
            + "  WHERE a.order_id = o.id AND a.del_flag = 0 AND a.status IN (0, 1, 2)) "
            + "AND NOT EXISTS (SELECT 1 FROM settlement_order s "
            + "  WHERE s.order_id = o.id AND s.del_flag = 0) "
            + "LIMIT #{limit}")
    List<SettleCandidateVo> selectSettleCandidates(@Param("days") int days, @Param("limit") int limit);

    /** 退款扣回第一步：抢占"待入账"直接作废（未入账无款可扣，同时封死结算任务随后 0→1 的窗口） */
    @Update("UPDATE settlement_order SET status = 2, deduct_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 0 AND del_flag = 0")
    int markVoidIfPending(@Param("orderId") Long orderId);

    /** 退款扣回第二步：已入账 → 置为已扣回（幂等，非 1 状态返回 0） */
    @Update("UPDATE settlement_order SET status = 2, deduct_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 1 AND del_flag = 0")
    int markDeducted(@Param("orderId") Long orderId);

    /** 结算入账 CAS：0→1（失败=退款钩子已抢先作废，调用方跳过入账） */
    @Update("UPDATE settlement_order SET status = 1, settle_time = NOW() "
            + "WHERE id = #{id} AND status = 0 AND del_flag = 0")
    int markSettled(@Param("id") Long id);

    /** 入账中途失败（余额/流水落库异常）时回退 CAS，行回到待入账，下轮任务自动重试 */
    @Update("UPDATE settlement_order SET status = 0, settle_time = NULL "
            + "WHERE id = #{id} AND status = 1 AND del_flag = 0")
    int revertSettled(@Param("id") Long id);

    /** 兜底对账：近 N 天"售后已退款完成但结算明细仍已入账"的售后单 id（钩子失败的单） */
    @Select("SELECT a.id FROM order_after_sale a "
            + "JOIN settlement_order s ON s.order_id = a.order_id AND s.del_flag = 0 AND s.status = 1 "
            + "WHERE a.del_flag = 0 AND a.status = 3 "
            + "AND a.update_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "LIMIT #{limit}")
    List<Long> selectRefundDeductBacklog(@Param("days") int days, @Param("limit") int limit);

    /** 平台资金总览：已入账明细的佣金合计（status=2 已扣回的不算收入） */
    @Select("SELECT IFNULL(SUM(commission_amount), 0) FROM settlement_order "
            + "WHERE del_flag = 0 AND status = 1")
    java.math.BigDecimal sumCommissionIncome();

    /** 平台资金总览：已入账明细的平台承担补贴合计（券+积分，随基数一起付给了商家） */
    @Select("SELECT IFNULL(SUM(platform_subsidy + points_deduct), 0) FROM settlement_order "
            + "WHERE del_flag = 0 AND status = 1")
    java.math.BigDecimal sumPlatformSubsidyPaid();
}
