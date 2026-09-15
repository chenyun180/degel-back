package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.service.SeckillService;
import com.degel.app.vo.OrderCreateVO;
import com.degel.app.vo.SeckillProductVO;
import com.degel.app.vo.SeckillReserveVO;
import com.degel.app.vo.SeckillSessionVO;
import com.degel.app.vo.dto.SeckillOrderReqVO;
import com.degel.app.vo.dto.SeckillReserveReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀控制器（两段式：reserve 预扣 → order 凭 token 下单）
 * S-01: GET  /app/seckill/sessions          场次列表（公开，AppSecurityFilter 白名单）
 * S-02: GET  /app/seckill/product           场次商品详情（确认页）
 * S-03: POST /app/seckill/reserve           抢购预扣（90s 资格 token）
 * S-04: POST /app/seckill/order             秒杀下单
 * S-05: POST /app/seckill/cancel            主动放弃资格
 *
 * <p>错误码：40020 活动不存在或已停用 / 40021 未开始或已结束 / 40022 已抢光 /
 * 40023 超出限购 / 40024 资格已过期 / 40025 资格校验失败。
 */
@RestController
@RequestMapping("/app/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * S-01: 场次列表（含商品与实时余量；匿名可看）
     */
    @GetMapping("/sessions")
    public R<List<SeckillSessionVO>> listSessions() {
        return R.ok(seckillService.listSessions());
    }

    /**
     * S-02: 场次商品详情（确认页，不缓存）
     */
    @GetMapping("/product")
    public R<SeckillProductVO> getProduct(@RequestParam("sessionId") Long sessionId,
                                          @RequestParam("skuId") Long skuId) {
        return R.ok(seckillService.getProduct(sessionId, skuId));
    }

    /**
     * S-03: 抢购预扣（Lua 原子预扣，成功返回 90s 资格 token）
     */
    @PostMapping("/reserve")
    public R<SeckillReserveVO> reserve(@RequestBody @Validated SeckillReserveReqVO reqVO) {
        Long userId = UserContext.getUserId();
        return R.ok(seckillService.reserve(reqVO, userId));
    }

    /**
     * S-04: 秒杀下单（凭资格 token，两段式第二段）
     */
    @PostMapping("/order")
    public R<OrderCreateVO> createOrder(@RequestBody @Validated SeckillOrderReqVO reqVO) {
        Long userId = UserContext.getUserId();
        return R.ok(seckillService.createOrder(reqVO, userId));
    }

    /**
     * S-05: 主动放弃抢购资格（best-effort）
     */
    @PostMapping("/cancel")
    public R<Void> cancelReserve(@RequestParam("token") String token) {
        Long userId = UserContext.getUserId();
        seckillService.cancelReserve(token, userId);
        return R.ok();
    }
}
