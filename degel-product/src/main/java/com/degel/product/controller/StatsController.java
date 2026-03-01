package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.service.StatsService;
import com.degel.product.vo.HotSaleVo;
import com.degel.product.vo.VisitorRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/hot-sale")
    public R<List<HotSaleVo>> hotSale(
            @RequestParam(defaultValue = "week") String period,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(statsService.getHotSaleRank(shopId, period));
    }

    @GetMapping("/visitor-rank")
    public R<List<VisitorRankVo>> visitorRank(
            @RequestParam(defaultValue = "week") String period,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        return R.ok(statsService.getVisitorRank(shopId, period));
    }
}
