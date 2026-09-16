package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.feign.PointsFeignClient;
import com.degel.app.vo.dto.CheckinDTO;
import com.degel.app.vo.dto.PointsLogDTO;
import com.degel.app.vo.dto.PointsPreviewDTO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C 端积分 BFF（/app/points/**、/app/checkin/**）——透传 degel-marketing 积分域。
 * 全部在 AppSecurityFilter 鉴权范围内（需登录）。
 */
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class PointsController {

    private final PointsFeignClient pointsFeignClient;

    /** 余额 + 今日签到状态（个人中心一次拉齐） */
    @GetMapping("/points/balance")
    public R<Map<String, Object>> balance() {
        Long userId = UserContext.getUserId();
        R<Integer> bal = pointsFeignClient.balance(userId);
        R<CheckinDTO> today = pointsFeignClient.checkinToday(userId);
        Map<String, Object> data = new HashMap<>(4);
        data.put("balance", bal.getCode() == 200 ? bal.getData() : 0);
        data.put("checkin", today.getCode() == 200 ? today.getData() : null);
        return R.ok(data);
    }

    /** 抵扣试算（确认页开关） */
    @GetMapping("/points/preview")
    public R<PointsPreviewDTO> preview(@RequestParam("amount") BigDecimal amount) {
        return pointsFeignClient.preview(UserContext.getUserId(), amount);
    }

    /** 明细分页 */
    @GetMapping("/points/log")
    public R<PointsLogDTO.Page> logs(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size) {
        return pointsFeignClient.logs(UserContext.getUserId(), page, size);
    }

    /** 今日签到状态 */
    @GetMapping("/checkin/today")
    public R<CheckinDTO> checkinToday() {
        return pointsFeignClient.checkinToday(UserContext.getUserId());
    }

    /** 每日签到 */
    @PostMapping("/checkin")
    public R<CheckinDTO> checkin() {
        return pointsFeignClient.checkin(UserContext.getUserId());
    }
}
