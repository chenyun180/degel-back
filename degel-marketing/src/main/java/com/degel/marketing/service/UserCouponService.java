package com.degel.marketing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.marketing.entity.UserCoupon;
import com.degel.marketing.vo.LockReqVO;
import com.degel.marketing.vo.LockRespVO;
import com.degel.marketing.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户券：领取/我的/可用 + 下单链路 inner 四件套（lock/unlock/confirm/return）。
 * 所有 inner 操作幂等：状态不匹配时返回成功（防重试场景误报）。
 */
public interface UserCouponService extends IService<UserCoupon> {

    /** 领取：mk_coupon 原子 UPDATE 防超发 + per_user_limit 校验 + 金额快照 + expire_time 落库 */
    void receive(Long couponId, Long userId);

    /** 我的券（惰性过期：查询前把过期的 0 置 3）。status 可选过滤 */
    List<UserCouponVO> mine(Long userId, Integer status);

    /**
     * 下单可用券：status∈{0,4}、未过期、门槛≤totalAmount。
     * shopId=null → 仅平台券（全场下单口径）；shopId>0 → 平台券 + 该店店铺券（按店子单口径）
     */
    List<UserCouponVO> usable(Long userId, BigDecimal totalAmount, Long shopId);

    /** 锁券（0/4→1，原子 UPDATE）。失败抛 BusinessException（下单阻断） */
    LockRespVO lock(LockReqVO req);

    /** 释放（1→0）。orderNo 定位，幂等 */
    void unlock(String orderNo);

    /** 核销（1→2，回填 orderId）。orderNo 定位，幂等 */
    void confirm(Long orderId, String orderNo);

    /** 整单退回（2→未过期?4:5）。orderId 定位，幂等 */
    void returnByOrder(Long orderId);
}
