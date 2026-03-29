package com.degel.app.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后/退款详情 VO
 * 在 AfterSaleListVO 基础上追加：merchantRemark + payLog（status=1时）
 */
@Data
public class AfterSaleDetailVO {

    /** 售后单ID */
    private Long id;
    /** 订单ID */
    private Long orderId;
    /** 订单编号 */
    private String orderNo;
    /** 类型：1=仅退款 */
    private Integer type;
    /** 状态：0=待审核 1=已同意 2=已拒绝 */
    private Integer status;
    /** 状态描述 */
    private String statusDesc;
    /** 申请原因 */
    private String reason;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 商家备注（审核时填写）*/
    private String merchantRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 退款流水（status=1 已同意时查询，direction=refund）
     */
    private PayLogVO payLog;
}
