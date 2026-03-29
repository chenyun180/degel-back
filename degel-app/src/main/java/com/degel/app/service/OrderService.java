package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.OrderCreateVO;
import com.degel.app.vo.OrderDetailVO;
import com.degel.app.vo.OrderListVO;
import com.degel.app.vo.dto.OrderCreateReqVO;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单（完整10步业务流程）
     *
     * @param reqVO  创建订单请求
     * @param userId 当前用户ID
     * @return 创建结果 VO
     */
    OrderCreateVO createOrder(OrderCreateReqVO reqVO, Long userId);

    /**
     * 分页查询订单列表
     *
     * @param userId   用户ID
     * @param status   订单状态（可选）
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    IPage<OrderListVO> listOrders(Long userId, Integer status, Integer page, Integer pageSize);

    /**
     * 查询订单详情
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID（校验归属）
     * @return 订单详情
     */
    OrderDetailVO getOrderDetail(Long orderId, Long userId);

    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID
     */
    void cancelOrder(Long orderId, Long userId);

    /**
     * 确认收货
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID
     */
    void confirmReceive(Long orderId, Long userId);
}
