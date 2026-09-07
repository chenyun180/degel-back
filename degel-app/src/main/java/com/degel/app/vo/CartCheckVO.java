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

    /**
     * 按店铺分组（拆单预览：一次提交将按店拆成 N 个子订单）。
     * 保序；旧字段 items/totalAmount 平铺保留（旧前端兼容），新前端用分组渲染。
     */
    private List<ShopGroup> shopGroups;

    /** 店铺子单分组 */
    @Data
    public static class ShopGroup {
        private Long shopId;
        private List<CartCheckItemVO> items;
        /** 本店商品小计（不含运费/优惠） */
        private BigDecimal shopTotalAmount;
    }
}
