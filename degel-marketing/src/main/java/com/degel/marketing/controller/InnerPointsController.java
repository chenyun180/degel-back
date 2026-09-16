package com.degel.marketing.controller;

import com.degel.common.core.R;
import com.degel.marketing.service.PointsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 积分内部接口（/inner/points/**，InnerTokenFilter 校验 X-Inner-Token，Feign 直连）。
 * 调用方：degel-app（下单冻结/取消回补/支付落定/确认收货得分/退款回收）。
 * 除 freeze 外全部幂等（按 type+orderId 查重），Feign 重试安全。
 */
@RestController
@RequestMapping("/inner/points")
@RequiredArgsConstructor
public class InnerPointsController {

    private final PointsService pointsService;

    /** 下单冻结（拆单一次冻结，写每子单一条 freeze 流水） */
    @PostMapping("/freeze")
    public R<Void> freeze(@RequestBody @Valid FreezeReq req) {
        pointsService.freeze(req.getUserId(), req.getItems());
        return R.ok();
    }

    @Data
    public static class FreezeReq {
        @NotNull
        private Long userId;
        /** [{orderId, orderNo, points}] */
        @NotNull
        private List<Map<String, Object>> items;
    }

    /** 取消/超时回补 */
    @PostMapping("/unfreeze")
    public R<Boolean> unfreeze(@RequestParam Long userId, @RequestParam String orderNo) {
        return R.ok(pointsService.unfreeze(userId, orderNo));
    }

    /** 支付成功：freeze → redeem 落定 */
    @PostMapping("/redeem-settle")
    public R<Void> redeemSettle(@RequestParam Long userId, @RequestParam String orderNo) {
        pointsService.redeemSettle(userId, orderNo);
        return R.ok();
    }

    /** 确认收货发放（points = floor(payAmount × earn-rate)，按实付） */
    @PostMapping("/earn")
    public R<Integer> earn(@RequestBody @Valid EarnReq req) {
        return R.ok(pointsService.grantEarn(req.getUserId(), req.getOrderId(), req.getOrderNo(), req.getPayAmount()));
    }

    /** 退款：退回该单抵扣积分 */
    @PostMapping("/return-redeem")
    public R<Boolean> returnRedeem(@RequestParam Long userId, @RequestParam String orderNo) {
        return R.ok(pointsService.returnRedeem(userId, orderNo));
    }

    /** 退款：回收该单已发积分（余额不足扣至 0，返回实际回收数） */
    @PostMapping("/reclaim-earn")
    public R<Integer> reclaimEarn(@RequestBody @Valid RefundReq req) {
        return R.ok(pointsService.reclaimEarn(req.getUserId(), req.getOrderId(), req.getOrderNo()));
    }

    @Data
    public static class EarnReq {
        @NotNull
        private Long userId;
        private Long orderId;
        @NotNull
        private String orderNo;
        @NotNull
        private java.math.BigDecimal payAmount;
    }

    @Data
    public static class RefundReq {
        @NotNull
        private Long userId;
        private Long orderId;
        @NotNull
        private String orderNo;
    }

    // ==================== C 端 BFF 查询（degel-app 透传） ====================

    /** 余额 */
    @GetMapping("/balance")
    public R<Integer> balance(@RequestParam Long userId) {
        return R.ok(pointsService.balance(userId));
    }

    /** 抵扣试算（确认页） */
    @GetMapping("/preview")
    public R<com.degel.marketing.vo.PointsPreviewVO> preview(@RequestParam Long userId,
                                                             @RequestParam java.math.BigDecimal amount) {
        return R.ok(pointsService.preview(userId, amount));
    }

    /** 明细分页 */
    @GetMapping("/logs")
    public R<com.baomidou.mybatisplus.core.metadata.IPage<com.degel.marketing.vo.PointsLogVO>> logs(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return R.ok(pointsService.pageLogs(userId, page, size));
    }

    /** 今日签到状态 */
    @GetMapping("/checkin/today")
    public R<com.degel.marketing.vo.CheckinVO> checkinToday(@RequestParam Long userId) {
        return R.ok(pointsService.todayStatus(userId));
    }

    /** 每日签到（重复签到抛 BusinessException → R.fail） */
    @PostMapping("/checkin")
    public R<com.degel.marketing.vo.CheckinVO> checkin(@RequestParam Long userId) {
        return R.ok(pointsService.checkin(userId));
    }
}
