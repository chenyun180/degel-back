package com.degel.app.feign.fallback;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.AfterSaleInfoVO;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.vo.dto.AfterSaleCreateInnerReqVO;
import com.degel.app.vo.dto.OrderCreateInnerReqVO;
import com.degel.app.vo.dto.OrderStatusUpdateVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OrderFeignClient 降级实现
 */
@Slf4j
@Component
public class OrderFeignFallback implements OrderFeignClient {

    @Override
    public R<Long> createOrder(OrderCreateInnerReqVO reqVO) {
        log.error("[OrderFeignFallback] createOrder 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<OrderInfoVO> getOrder(Long orderId) {
        log.error("[OrderFeignFallback] getOrder orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<IPage<OrderInfoVO>> pageOrders(Long userId, Integer status, Integer page, Integer pageSize) {
        log.error("[OrderFeignFallback] pageOrders 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Void> updateOrderStatus(Long orderId, OrderStatusUpdateVO updateVO) {
        log.error("[OrderFeignFallback] updateOrderStatus orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Long> createAfterSale(AfterSaleCreateInnerReqVO reqVO) {
        log.error("[OrderFeignFallback] createAfterSale 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<IPage<AfterSaleInfoVO>> pageAfterSales(Long userId, Integer status, Integer page, Integer pageSize) {
        log.error("[OrderFeignFallback] pageAfterSales 降级");
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<Boolean> existsActiveAfterSale(Long orderId, Long userId) {
        log.error("[OrderFeignFallback] existsActiveAfterSale orderId={} 降级", orderId);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }

    @Override
    public R<AfterSaleInfoVO> getAfterSaleById(Long id) {
        log.error("[OrderFeignFallback] getAfterSaleById id={} 降级", id);
        return R.fail(50001, "订单服务暂不可用，请稍后重试");
    }
}
