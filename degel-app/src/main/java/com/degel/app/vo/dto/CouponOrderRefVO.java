package com.degel.app.vo.dto;

import lombok.Data;

/**
 * 解锁/退回请求（镜像 marketing OrderRefVO）
 */
@Data
public class CouponOrderRefVO {

    private Long orderId;
    private String orderNo;
}
