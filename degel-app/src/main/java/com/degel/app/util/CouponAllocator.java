package com.degel.app.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 券优惠额按订单明细分摊（设计方案 §5.4）。
 *
 * 规则：
 *   1. 可分摊基数 = Σ itemTotal（商品小计，不含运费；券不抵运费）
 *   2. discount > 基数时按基数封顶（pay_amount = 总额+运费-优惠 ≥ 0 兜底）
 *   3. 每项 item_discount = ROUND(discount × itemTotal / 基数, 2)（HALF_UP）
 *   4. 尾差 = discount(封顶后) - Σ item_discount，加到金额最大的那一项
 *   5. 单商品订单：直接 item_discount = discount
 *
 * 退款取数依据：item 实付 = item.totalAmount - item.couponDiscount，
 * 部分退按此退且券不返还（行业惯例）。
 */
public final class CouponAllocator {

    private CouponAllocator() {
    }

    /**
     * @param itemTotals 每项商品小计（顺序与订单明细一致）
     * @param discount   券减免额
     * @return 每项分摊额（Σ = 封顶后的实际优惠额），顺序与入参一致
     */
    public static List<BigDecimal> allocate(List<BigDecimal> itemTotals, BigDecimal discount) {
        if (itemTotals == null || itemTotals.isEmpty()) {
            throw new IllegalArgumentException("分摊明细不能为空");
        }

        BigDecimal base = BigDecimal.ZERO;
        for (BigDecimal t : itemTotals) {
            base = base.add(t);
        }
        // 封顶：优惠额不能超过商品合计（运费不参与分摊）
        BigDecimal effective = discount.min(base);

        List<BigDecimal> result = new ArrayList<>(itemTotals.size());
        if (itemTotals.size() == 1) {
            result.add(effective);
            return result;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (BigDecimal t : itemTotals) {
            BigDecimal share = effective.multiply(t)
                    .divide(base, 2, BigDecimal.ROUND_HALF_UP);
            result.add(share);
            allocated = allocated.add(share);
        }

        // 尾差给金额最大项（保证 Σ 分摊 = 实际优惠额）
        BigDecimal tail = effective.subtract(allocated);
        if (tail.compareTo(BigDecimal.ZERO) != 0) {
            int maxIdx = 0;
            for (int i = 1; i < itemTotals.size(); i++) {
                if (itemTotals.get(i).compareTo(itemTotals.get(maxIdx)) > 0) {
                    maxIdx = i;
                }
            }
            result.set(maxIdx, result.get(maxIdx).add(tail));
        }
        return result;
    }
}
