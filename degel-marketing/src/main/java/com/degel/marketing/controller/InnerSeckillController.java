package com.degel.marketing.controller;

import com.degel.common.core.R;
import com.degel.marketing.service.SeckillProductService;
import com.degel.marketing.service.SeckillSessionService;
import com.degel.marketing.vo.SeckillProductVo;
import com.degel.marketing.vo.SeckillSessionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端秒杀（degel-app 经 Feign 调用；InnerTokenFilter 已保护 /inner/ 前缀——
 * 校验 X-Inner-Token 内部令牌，由 degel-app 的 FeignConfig 统一注入；
 * 网关 internal-urls 拒绝外部经网关访问，Feign 经 Nacos 直连）。
 */
@RestController
@RequestMapping("/inner/seckill")
@RequiredArgsConstructor
public class InnerSeckillController {

    private final SeckillSessionService seckillSessionService;
    private final SeckillProductService seckillProductService;

    /** 当前/即将开卖场次列表（启用且 end_time>now 且 start_time<now+24h，含商品，不含 delFlag） */
    @GetMapping("/current")
    public R<List<SeckillSessionVo>> current() {
        return R.ok(seckillSessionService.listCurrent());
    }

    /** 场次商品单条详情；查无返回 data=null，调用方判空 */
    @GetMapping("/detail")
    public R<SeckillProductVo> detail(
            @RequestParam Long sessionId,
            @RequestParam Long skuId) {
        return R.ok(seckillProductService.getDetail(sessionId, skuId));
    }
}
