package com.degel.marketing.vo;

import lombok.Data;

/**
 * unlock（释放）/ return（退回）入参。
 * unlock：orderNo 定位（取消/补偿时 orderId 可能不存在）；
 * return：orderId 定位（整单退款时挂的是已核销券）。
 */
@Data
public class OrderRefVO {

    private Long orderId;

    private String orderNo;
}
