package com.degel.app.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预览结算汇总 VO
 */
@Data
public class CartCheckVO {

    /**
     * 结算明细列表
     */
    private List<CartCheckItemVO> items;

    /**
     * 商品总金额
     */
    private BigDecimal totalAmount;

    /**
     * 运费（本期固定 0）
     */
    private BigDecimal freightAmount;

    /**
     * 实付金额 = totalAmount + freightAmount
     */
    private BigDecimal payAmount;
}
