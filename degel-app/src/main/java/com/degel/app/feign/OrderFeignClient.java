package com.degel.app.feign;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.OrderFeignFallback;
import com.degel.app.vo.AfterSaleInfoVO;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.dto.AfterSaleCreateInnerReqVO;
import com.degel.app.vo.dto.OrderCreateInnerReqVO;
import com.degel.app.vo.dto.OrderStatusUpdateVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 订单服务 Feign 客户端
 * 对应 degel-order 内部接口
 */
@FeignClient(name = "degel-order", path = "/inner/order", configuration = FeignConfig.class, fallback = OrderFeignFallback.class)
public interface OrderFeignClient {

    /**
     * 创建订单（内部）
     */
    @PostMapping("/create")
    R<Long> createOrder(@RequestBody OrderCreateInnerReqVO reqVO);

    /**
     * 查询订单详情（内部）
     */
    @GetMapping("/{orderId}")
    R<OrderInfoVO> getOrder(@PathVariable("orderId") Long orderId);

    /**
     * 分页查询订单列表（内部）
     */
    @GetMapping("/page")
    R<IPage<OrderInfoVO>> pageOrders(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    );

    /**
     * 更新订单状态（内部）
     */
    @PutMapping("/{orderId}/status")
    R<Void> updateOrderStatus(
            @PathVariable("orderId") Long orderId,
            @RequestBody OrderStatusUpdateVO updateVO
    );

    /**
     * 创建售后单（内部）
     */
    @PostMapping("/aftersale")
    R<Long> createAfterSale(@RequestBody AfterSaleCreateInnerReqVO reqVO);

    /**
     * 分页查询售后单列表（内部）
     */
    @GetMapping("/aftersale/page")
    R<IPage<AfterSaleInfoVO>> pageAfterSales(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    );

    /**
     * 精确查重：检查指定订单是否存在进行中的售后单（status IN 0,1）
     * 替代分页拉 100 条内存过滤，避免超 100 条时漏判
     */
    @GetMapping("/aftersale/check")
    R<Boolean> existsActiveAfterSale(
            @RequestParam("orderId") Long orderId,
            @RequestParam("userId") Long userId
    );

    /**
     * 精确查询售后单详情（内部）
     */
    @GetMapping("/aftersale/{id}")
    R<AfterSaleInfoVO> getAfterSaleById(@PathVariable("id") Long id);
}
