package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单信息 VO（Feign 从 degel-order 获取）
 */
@Data
public class AfterSaleInfoVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long shopId;
    /** 类型：1=仅退款 */
    private Integer type;
    /** 0=待商家处理 1=待买家退货 2=待商家收货 3=退款完成 5=已拒绝 6=平台介入中 7=仲裁维持拒绝 */
    private Integer status;
    private String reason;
    private BigDecimal refundAmount;
    private String merchantRemark;
    /** 平台仲裁意见（status=6/7 时有值） */
    private String platformRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
