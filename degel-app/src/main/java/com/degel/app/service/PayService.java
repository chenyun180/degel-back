package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.PayLogVO;
import com.degel.app.vo.PayResultVO;
import com.degel.app.vo.dto.InnerRefundReqVO;

/**
 * 支付服务接口
 */
public interface PayService {

    /**
     * C-07: 发起模拟支付
     *
     * @param orderId 订单ID
     * @param userId  当前用户ID
     * @return 支付结果
     */
    PayResultVO pay(Long orderId, Long userId);

    /**
     * C-08: 查询支付流水列表（分页）
     *
     * @param userId    用户ID
     * @param direction 方向（pay/refund，可选）
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    IPage<PayLogVO> listPayLog(Long userId, String direction, Integer page, Integer size);

    /**
     * C-09: 内部退款接口（被 degel-order 调用）
     *
     * @param reqVO 退款请求
     * @return 退款流水ID
     */
    Long refund(InnerRefundReqVO reqVO);
}
