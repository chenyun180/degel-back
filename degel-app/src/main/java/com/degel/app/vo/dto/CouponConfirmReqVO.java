package com.degel.app.vo.dto;

import lombok.Data;

/**
 * 核销请求（镜像 marketing ConfirmReqVO）
 */
@Data
public class CouponConfirmReqVO {

    private Long orderId;
    private String orderNo;
}
