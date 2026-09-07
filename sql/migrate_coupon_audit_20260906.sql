-- 优惠券三期：店铺券平台审核（驳回理由列）
USE degel_marketing;

ALTER TABLE mk_coupon
    ADD COLUMN reject_reason VARCHAR(200) DEFAULT NULL COMMENT '审核驳回理由' AFTER audit_status;
