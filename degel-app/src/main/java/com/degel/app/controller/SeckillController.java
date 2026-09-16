package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.exception.BusinessException;
import com.degel.app.service.SeckillService;
import com.degel.app.util.RedisRateLimiter;
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

import javax.servlet.http.HttpServletRequest;
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
 * 40023 超出限购 / 40024 资格已过期 / 40025 资格校验失败 /
 * 40026 抢购尝试过于频繁 / 40028 请求过于频繁（公开端点 IP 限流）。
 */
@RestController
@RequestMapping("/app/seckill")
@RequiredArgsConstructor
public class SeckillController {

    /** reserve 防刷：单用户 10s 内最多 5 次尝试（含失败——挡的是无效请求对 Redis/网关的消耗） */
    private static final int RESERVE_LIMIT = 5;
    private static final int RESERVE_WINDOW_SECONDS = 10;
    /** 场次列表（匿名公开）：单 IP 60 次/分钟，接口有 30s 静态缓存，正常用户够用 */
    private static final int SESSIONS_IP_LIMIT = 60;
    private static final int SESSIONS_WINDOW_SECONDS = 60;

    private final SeckillService seckillService;
    private final RedisRateLimiter rateLimiter;

    /**
     * S-01: 场次列表（含商品与实时余量；匿名可看）
     */
    @GetMapping("/sessions")
    public R<List<SeckillSessionVO>> listSessions(HttpServletRequest request) {
        String ip = RedisRateLimiter.clientIp(request);
        if (!rateLimiter.allow("rl:pub:seckill-sessions:" + ip, SESSIONS_IP_LIMIT, SESSIONS_WINDOW_SECONDS)) {
            throw BusinessException.of(40028, "请求过于频繁，请稍后再试");
        }
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
        if (!rateLimiter.allow("seckill:rl:reserve:" + userId, RESERVE_LIMIT, RESERVE_WINDOW_SECONDS)) {
            throw BusinessException.of(40026, "抢购尝试过于频繁，请稍后再试");
        }
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
