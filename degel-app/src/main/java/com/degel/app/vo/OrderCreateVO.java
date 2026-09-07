package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建订单响应 VO（按店铺拆单后：一次提交返回 N 个子订单）。
 *
 * 兼容策略：顶层 orderId/orderNo/payAmount/autoCancelTime 填【首个子单】——
 * 旧前端（taro pay 页跳转）不断裂；多店时余单走订单列表支付。
 * 新前端应使用 orders 数组 + totalPayAmount。
 */
@Data
public class OrderCreateVO {

    /** 订单ID（兼容字段=首单） */
    private Long orderId;
    /** 订单编号（兼容字段=首单） */
    private String orderNo;
    /** 实付金额（兼容字段=首单） */
    private BigDecimal payAmount;
    /** 自动取消时间（兼容字段=首单） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime autoCancelTime;

    /** 按店拆出的全部子订单（保序） */
    private List<SubOrder> orders;

    /** 全部子单实付合计 */
    private BigDecimal totalPayAmount;

    /** 子订单 */
    @Data
    public static class SubOrder {
        private Long orderId;
        private String orderNo;
        /** 子单归属店铺 */
        private Long shopId;
        private BigDecimal payAmount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime autoCancelTime;
    }
}
