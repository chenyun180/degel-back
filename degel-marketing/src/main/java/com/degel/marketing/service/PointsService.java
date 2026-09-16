package com.degel.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.marketing.vo.CheckinVO;
import com.degel.marketing.vo.PointsLogVO;
import com.degel.marketing.vo.PointsPreviewVO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 积分域服务（doc/积分系统设计.md）。
 * 余额变动全部经此入口：账户原子 UPDATE + 流水；业务动作以 orderNo 为幂等键
 * （冻结发生在建单之前，orderId 尚不存在；orderNo 预生成且全链路唯一）。
 */
public interface PointsService {

    /** 余额（无账户视为 0） */
    int balance(Long userId);

    /**
     * 下单冻结（app 下单链路 Feign 调用）：一次原子扣减总额，写每子单一条 freeze 流水。
     * items: [{orderId(可空), orderNo, points}]，Σpoints = 冻结总额；余额不足抛 BusinessException。
     */
    void freeze(Long userId, Iterable<Map<String, Object>> items);

    /** 取消/超时回补（幂等，已回补返回 false；无 freeze 流水返回 false——本单没用积分） */
    boolean unfreeze(Long userId, String orderNo);

    /** 支付成功落定：freeze → redeem 语义转化（余额已在冻结时扣，仅补流水；幂等） */
    void redeemSettle(Long userId, String orderNo);

    /**
     * 确认收货发放（幂等）：points = floor(payAmount × earn-rate)，按实付（✅ 决策：抵扣部分不生分）。
     * 已发放或积分为 0 返回 0。
     */
    int grantEarn(Long userId, Long orderId, String orderNo, BigDecimal payAmount);

    /** 退款退回抵扣（幂等，无条件退）：按该单 freeze 流水全额退回 */
    boolean returnRedeem(Long userId, String orderNo);

    /** 退款回收已发积分（幂等）：从 earn 流水取发放数，余额不足扣至 0 为止，返回实际回收数 */
    int reclaimEarn(Long userId, Long orderId, String orderNo);

    /** 抵扣试算（确认页） */
    PointsPreviewVO preview(Long userId, BigDecimal amount);

    /** 每日签到（连续递增，双保险防重；重复签到抛 BusinessException） */
    CheckinVO checkin(Long userId);

    /** 今日签到状态（签到页/角标） */
    CheckinVO todayStatus(Long userId);

    /** 明细分页 */
    IPage<PointsLogVO> pageLogs(Long userId, Integer page, Integer size);
}
