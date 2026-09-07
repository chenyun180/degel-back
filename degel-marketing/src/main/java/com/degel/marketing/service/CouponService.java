package com.degel.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.marketing.entity.Coupon;
import com.degel.marketing.vo.CouponCreateVo;
import com.degel.marketing.vo.CouponVO;

import java.util.List;

/**
 * 券模板：创建/停发/管理端分页/C 端可领列表。
 */
public interface CouponService extends IService<Coupon> {

    /**
     * 平台端创建（funder_type=1 平台券 / 3 分摊券，创建即生效 status=1、audit_status=2）。
     * 分摊券由 vo.funderType=3 + shopId + platformAmount/shopAmount 标识。
     */
    CouponVO createPlatformCoupon(CouponCreateVo vo, Long userId);

    /**
     * 店铺券创建（funder_type=2）。三期起走平台审核：创建后 audit_status=1 待审核、
     * status=0 未生效，平台审核通过才可领（audit_status=2 + status=1）。
     * 防滥用上限（设计 §5.1）：总量 ≤ 5000、每人限领 ≤ 5。
     */
    CouponVO createShopCoupon(CouponCreateVo vo, Long shopId, Long userId);

    /** 平台审核店铺券：audit_status 1→2(通过,同时 status=1)/3(驳回,必填理由)。幂等校验当前状态 */
    void auditCoupon(Long couponId, boolean passed, String rejectReason);

    /** 停发：status 1→2，已发出的券仍可用。shopId>0 时校验归属（店铺只能停自己的券） */
    void stopCoupon(Long couponId, Long shopId);

    /** 平台管理端分页（全部券型；auditStatus 过滤供审核工作台 Tab 用） */
    IPage<CouponVO> pagePlatformCoupons(Page<Coupon> page, String name, Integer status, Integer auditStatus);

    /** 店铺工作台分页（强制 funder_type=2 + 本店） */
    IPage<CouponVO> pageShopCoupons(Page<Coupon> page, Long shopId, String name, Integer status);

    /**
     * C 端可领列表：进行中、可领时间内、未领完。
     * shopId=null → 仅平台券；shopId>0 → 该店店铺券 + 平台券（下单页按店/商品详情页用）。
     * userId 非空时额外过滤已达到每人限领的券（未登录传 null 跳过过滤）。
     */
    List<CouponVO> listReceivable(Long shopId, Long userId);
}
