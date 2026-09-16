package com.degel.app.feign;

import com.degel.app.config.FeignConfig;
import com.degel.app.vo.dto.CheckinDTO;
import com.degel.app.vo.dto.PointsLogDTO;
import com.degel.app.vo.dto.PointsPreviewDTO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 积分服务 Feign 客户端（degel-marketing /inner/points/**）。
 * 无 fallback：冻结失败必须阻断下单（同券 lock 语义，调用方判 code!=200 抛异常）；
 * 查询失败返回 R.fail 由 BFF 层转空/兜底展示。
 */
@FeignClient(name = "degel-marketing", contextId = "pointsFeignClient",
        path = "/inner/points", configuration = FeignConfig.class)
public interface PointsFeignClient {

    // ===== 业务动作（下单/支付/退款链路调用） =====

    /** 下单冻结（拆单一次冻结总额） */
    @PostMapping("/freeze")
    R<Void> freeze(@RequestBody Map<String, Object> req);

    /** 取消/超时回补（orderNo 幂等键） */
    @PostMapping("/unfreeze")
    R<Boolean> unfreeze(@RequestParam("userId") Long userId, @RequestParam("orderNo") String orderNo);

    /** 支付成功：freeze → redeem 落定 */
    @PostMapping("/redeem-settle")
    R<Void> redeemSettle(@RequestParam("userId") Long userId, @RequestParam("orderNo") String orderNo);

    /** 确认收货发放（points = floor(payAmount×rate) 服务端算），返回实发数 */
    @PostMapping("/earn")
    R<Integer> earn(@RequestBody Map<String, Object> req);

    /** 退款退回抵扣 */
    @PostMapping("/return-redeem")
    R<Boolean> returnRedeem(@RequestParam("userId") Long userId, @RequestParam("orderNo") String orderNo);

    /** 退款回收已发（从 earn 流水取数，余额不足扣至 0） */
    @PostMapping("/reclaim-earn")
    R<Integer> reclaimEarn(@RequestBody Map<String, Object> req);

    // ===== C 端 BFF 查询 =====

    @GetMapping("/balance")
    R<Integer> balance(@RequestParam("userId") Long userId);

    @GetMapping("/preview")
    R<PointsPreviewDTO> preview(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);

    @GetMapping("/logs")
    R<PointsLogDTO.Page> logs(@RequestParam("userId") Long userId,
                              @RequestParam("page") Integer page,
                              @RequestParam("size") Integer size);

    @GetMapping("/checkin/today")
    R<CheckinDTO> checkinToday(@RequestParam("userId") Long userId);

    @PostMapping("/checkin")
    R<CheckinDTO> checkin(@RequestParam("userId") Long userId);
}
