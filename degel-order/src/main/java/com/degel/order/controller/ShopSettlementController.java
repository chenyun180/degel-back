package com.degel.order.controller;

import com.degel.common.core.R;
import com.degel.order.entity.SettlementAccountLog;
import com.degel.order.entity.SettlementOrder;
import com.degel.order.entity.SettlementWithdraw;
import com.degel.order.mapper.SettlementAccountLogMapper;
import com.degel.order.service.ISettlementService;
import com.degel.order.service.ISettlementWithdrawService;
import com.degel.order.vo.ShopSettlementAccountVo;
import com.degel.order.vo.WithdrawApplyVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店铺结算（店铺工作台「资金结算」页）
 * 鉴权：X-Shop-Id 由网关 AuthFilter 注入（店铺账号），shopId<=0 即平台/未登录
 */
@RestController
@RequestMapping("/shop/settlement")
@RequiredArgsConstructor
public class ShopSettlementController {

    private final ISettlementService settlementService;
    private final ISettlementWithdrawService withdrawService;
    private final SettlementAccountLogMapper accountLogMapper;

    /** 余额卡片：当前余额 / 累计结算 / 累计提现 */
    @GetMapping("/account")
    public R<ShopSettlementAccountVo> account(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        return R.ok(settlementService.getShopAccount(shopId));
    }

    /** 结算明细分页 */
    @GetMapping("/detail/page")
    public R<IPage<SettlementOrder>> detailPage(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        return R.ok(settlementService.pageDetail(new Page<>(current, size), shopId, status));
    }

    /** 账户流水分页（余额变动记录） */
    @GetMapping("/log/page")
    public R<IPage<SettlementAccountLog>> logPage(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        return R.ok(accountLogMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SettlementAccountLog>()
                        .eq(SettlementAccountLog::getShopId, shopId)
                        .orderByDesc(SettlementAccountLog::getCreateTime)));
    }

    /** 提现单分页 */
    @GetMapping("/withdraw/page")
    public R<IPage<SettlementWithdraw>> withdrawPage(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        return R.ok(withdrawService.page(new Page<>(current, size), shopId, status));
    }

    /** 申请提现 */
    @PostMapping("/withdraw")
    public R<Void> applyWithdraw(
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
            @Validated @RequestBody WithdrawApplyVo vo) {
        if (shopId == null || shopId <= 0) {
            return R.fail("店铺身份缺失");
        }
        withdrawService.apply(shopId, vo);
        return R.ok();
    }
}
