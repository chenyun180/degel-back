package com.degel.order.mapper;

import com.degel.order.vo.DailyGmvVo;
import com.degel.order.vo.ProductGmvRankVo;
import com.degel.order.vo.ShopGmvRankVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台工作台统计（已支付口径：status IN (1,2,3,5)，即排除待付款/已取消；
 * 售后中订单仍计入流水，退款不冲减——order 库无退款冲减字段）
 */
@Mapper
public interface OrderStatsMapper {

    /** 累计总流水 */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5)")
    BigDecimal sumTotalGmv();

    /** 今日流水 */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) AND pay_time >= CURDATE()")
    BigDecimal sumTodayGmv();

    /** 今日订单数 */
    @Select("SELECT COUNT(*) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) AND pay_time >= CURDATE()")
    Integer countTodayOrders();

    /**
     * 本月平台补贴（三期优惠券报表）。
     * 口径同上：已支付且非取消；退款完成的订单（order_after_sale status=3）剔除补贴，
     * 已拒绝/待处理的售后单仍计入（钱未退）。GMV 维持不冲减口径（与流水卡片文案一致）。
     */
    @Select("SELECT IFNULL(SUM(platform_subsidy), 0) FROM order_info o "
            + "WHERE o.del_flag = 0 AND o.status IN (1,2,3,5) "
            + "AND o.pay_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') "
            + "AND NOT EXISTS (SELECT 1 FROM order_after_sale a "
            + "WHERE a.order_id = o.id AND a.status = 3 AND a.del_flag = 0)")
    BigDecimal sumMonthPlatformSubsidy();

    /** 本月指定店铺补贴（店铺工作台；退款完成的订单剔除，同上口径） */
    @Select("SELECT IFNULL(SUM(shop_subsidy), 0) FROM order_info o "
            + "WHERE o.del_flag = 0 AND o.status IN (1,2,3,5) "
            + "AND o.pay_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND o.shop_id = #{shopId} "
            + "AND NOT EXISTS (SELECT 1 FROM order_after_sale a "
            + "WHERE a.order_id = o.id AND a.status = 3 AND a.del_flag = 0)")
    BigDecimal sumMonthShopSubsidy(@Param("shopId") Long shopId);

    /** 待发货订单数（已付款待发货） */
    @Select("SELECT COUNT(*) FROM order_info WHERE del_flag = 0 AND status = 1")
    Integer countPendingShip();

    /** 店铺流水 TOP5 */
    @Select("SELECT shop_id AS shopId, SUM(pay_amount) AS gmv, COUNT(*) AS orderCount "
            + "FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) "
            + "GROUP BY shop_id ORDER BY gmv DESC LIMIT 5")
    List<ShopGmvRankVo> selectShopGmvTop5();

    /** 畅销商品 TOP5（按销售额；商品名取 order_item.spu_name 下单快照） */
    @Select("SELECT oi.spu_id AS spuId, oi.spu_name AS spuName, o.shop_id AS shopId, "
            + "SUM(oi.quantity) AS quantity, SUM(oi.total_amount) AS amount "
            + "FROM order_item oi "
            + "INNER JOIN order_info o ON oi.order_id = o.id "
            + "AND o.del_flag = 0 AND o.status IN (1,2,3,5) "
            + "GROUP BY oi.spu_id, oi.spu_name, o.shop_id "
            + "ORDER BY amount DESC LIMIT 5")
    List<ProductGmvRankVo> selectProductGmvTop5();

    /** 近 N 天按日流水（只含有订单的日期，缺日在 Service 层补零） */
    @Select("SELECT DATE_FORMAT(pay_time, '%Y-%m-%d') AS date, "
            + "SUM(pay_amount) AS gmv, COUNT(*) AS orderCount "
            + "FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) "
            + "AND pay_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(pay_time, '%Y-%m-%d') "
            + "ORDER BY date")
    List<DailyGmvVo> selectDailyGmv(@Param("days") int days);

    // ==================== 店铺工作台看板（店铺维度） ====================

    /** 店铺今日流水 */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) AND pay_time >= CURDATE() "
            + "AND shop_id = #{shopId}")
    BigDecimal sumTodayGmvByShop(@Param("shopId") Long shopId);

    /** 店铺今日订单数 */
    @Select("SELECT COUNT(*) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) AND pay_time >= CURDATE() "
            + "AND shop_id = #{shopId}")
    Integer countTodayOrdersByShop(@Param("shopId") Long shopId);

    /** 店铺昨日流水 */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) "
            + "AND pay_time >= DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND pay_time < CURDATE() "
            + "AND shop_id = #{shopId}")
    BigDecimal sumYesterdayGmvByShop(@Param("shopId") Long shopId);

    /** 店铺昨日订单数 */
    @Select("SELECT COUNT(*) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) "
            + "AND pay_time >= DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND pay_time < CURDATE() "
            + "AND shop_id = #{shopId}")
    Integer countYesterdayOrdersByShop(@Param("shopId") Long shopId);

    /** 店铺本月流水 */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM order_info "
            + "WHERE del_flag = 0 AND status IN (1,2,3,5) "
            + "AND pay_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') "
            + "AND shop_id = #{shopId}")
    BigDecimal sumMonthGmvByShop(@Param("shopId") Long shopId);

    /** 店铺待发货订单数（已付款待发货） */
    @Select("SELECT COUNT(*) FROM order_info "
            + "WHERE del_flag = 0 AND status = 1 AND shop_id = #{shopId}")
    Integer countPendingShipByShop(@Param("shopId") Long shopId);

    /** 店铺待处理售后数（待审核） */
    @Select("SELECT COUNT(*) FROM order_after_sale "
            + "WHERE del_flag = 0 AND status = 0 AND shop_id = #{shopId}")
    Integer countPendingAfterSaleByShop(@Param("shopId") Long shopId);
}
