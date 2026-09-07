-- 优惠券一期：degel_order 库订单侧加列
-- 设计文档：docs/优惠券功能设计方案.md §4.3
USE degel_order;

ALTER TABLE order_info
    ADD COLUMN coupon_id        BIGINT        DEFAULT NULL COMMENT '使用的用户券id（mk_user_coupon.id）',
    ADD COLUMN platform_subsidy DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台补贴（平台承担部分）',
    ADD COLUMN shop_subsidy     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '店铺补贴（店铺承担部分）';

ALTER TABLE order_item
    ADD COLUMN coupon_discount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '该商品分摊的券优惠额（退款取数依据）';

-- 说明：discount_amount 沿用现有语义 = platform_subsidy + shop_subsidy；
--       pay_amount = total_amount + freight_amount - discount_amount（一期运费为0）。
