package com.degel.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.exception.BusinessException;
import com.degel.marketing.config.PointsProperties;
import com.degel.marketing.entity.MkCheckin;
import com.degel.marketing.entity.MkPointsAccount;
import com.degel.marketing.entity.MkPointsLog;
import com.degel.marketing.mapper.MkCheckinMapper;
import com.degel.marketing.mapper.MkPointsAccountMapper;
import com.degel.marketing.mapper.MkPointsLogMapper;
import com.degel.marketing.service.PointsService;
import com.degel.marketing.vo.CheckinVO;
import com.degel.marketing.vo.PointsLogVO;
import com.degel.marketing.vo.PointsPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 积分域实现。要点：
 * - 账户余额只经原子 UPDATE 变动（deduct 带余额条件防超扣，credit 回补）；
 * - 每笔变动写流水，业务动作按 (type, orderId) 幂等（Feign 重试/任务重跑安全）；
 * - 签到 Redis SETNX 快路径 + mk_checkin 唯一索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 连续签到最长回看窗口（天）——超出视为断签，足够覆盖 cap 封顶周期 */
    private static final int STREAK_WINDOW_DAYS = 60;

    private final MkPointsAccountMapper accountMapper;
    private final MkPointsLogMapper logMapper;
    private final MkCheckinMapper checkinMapper;
    private final PointsProperties props;
    private final StringRedisTemplate redisTemplate;

    // ==================== 余额 ====================

    @Override
    public int balance(Long userId) {
        MkPointsAccount acc = accountMapper.selectOne(
                new LambdaQueryWrapper<MkPointsAccount>().eq(MkPointsAccount::getUserId, userId));
        return acc != null && acc.getBalance() != null ? acc.getBalance() : 0;
    }

    /** 懒创建账户：首次获得积分时 INSERT（并发下撞唯一键则重查，幂等） */
    private MkPointsAccount ensureAccount(Long userId) {
        MkPointsAccount acc = accountMapper.selectOne(
                new LambdaQueryWrapper<MkPointsAccount>().eq(MkPointsAccount::getUserId, userId));
        if (acc != null) {
            return acc;
        }
        MkPointsAccount created = new MkPointsAccount();
        created.setUserId(userId);
        created.setBalance(0);
        try {
            accountMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return accountMapper.selectOne(
                    new LambdaQueryWrapper<MkPointsAccount>().eq(MkPointsAccount::getUserId, userId));
        }
    }

    // ==================== 下单冻结 / 取消回补 / 支付落定 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId, Iterable<Map<String, Object>> items) {
        int total = 0;
        for (Map<String, Object> item : items) {
            total += (Integer) item.get("points");
        }
        if (total <= 0) {
            return;
        }
        if (!props.isEnabled()) {
            throw new BusinessException("积分功能已关闭");
        }
        ensureAccount(userId);
        int rows = accountMapper.deduct(userId, total);
        if (rows == 0) {
            throw new BusinessException("积分不足");
        }
        for (Map<String, Object> item : items) {
            int pts = (Integer) item.get("points");
            if (pts <= 0) {
                continue;
            }
            insertLog(userId, "freeze", -pts,
                    (Long) item.get("orderId"), (String) item.get("orderNo"), "下单积分抵扣冻结");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfreeze(Long userId, String orderNo) {
        MkPointsLog freezeLog = findLog("freeze", orderNo);
        if (freezeLog == null || logExists("unfreeze", orderNo)) {
            return false;
        }
        int pts = Math.abs(freezeLog.getPoints());
        ensureAccount(userId);
        accountMapper.credit(userId, pts);
        insertLog(userId, "unfreeze", pts, freezeLog.getOrderId(), orderNo, "订单取消回补积分");
        return true;
    }

    @Override
    public void redeemSettle(Long userId, String orderNo) {
        MkPointsLog freezeLog = findLog("freeze", orderNo);
        if (freezeLog == null || logExists("redeem", orderNo)) {
            return;
        }
        insertLog(userId, "redeem", freezeLog.getPoints(), freezeLog.getOrderId(), orderNo, "支付成功积分抵扣落定");
    }

    // ==================== 得分 / 退款回收 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantEarn(Long userId, Long orderId, String orderNo, BigDecimal payAmount) {
        if (logExists("earn", orderNo)) {
            return 0;
        }
        // 按实付计算（✅ 决策：抵扣部分不生分）；rate=1 元/分
        int points = payAmount.multiply(BigDecimal.valueOf(props.getEarnRate()))
                .setScale(0, RoundingMode.DOWN).intValue();
        if (points <= 0) {
            return 0;
        }
        ensureAccount(userId);
        accountMapper.credit(userId, points);
        insertLog(userId, "earn", points, orderId, orderNo, "确认收货获得积分");
        return points;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean returnRedeem(Long userId, String orderNo) {
        if (logExists("redeem_return", orderNo)) {
            return false;
        }
        MkPointsLog freezeLog = findLog("freeze", orderNo);
        if (freezeLog == null) {
            return false; // 本单没用积分
        }
        int pts = Math.abs(freezeLog.getPoints());
        ensureAccount(userId);
        accountMapper.credit(userId, pts);
        insertLog(userId, "redeem_return", pts, freezeLog.getOrderId(), orderNo, "退款退回积分抵扣");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reclaimEarn(Long userId, Long orderId, String orderNo) {
        if (logExists("earn_reclaim", orderNo)) {
            return 0;
        }
        MkPointsLog earnLog = findLog("earn", orderNo);
        int earnedPoints = earnLog != null ? earnLog.getPoints() : 0;
        if (earnedPoints <= 0) {
            return 0; // 未发放过（确认收货前退款），无需回收
        }
        int reclaimable = Math.min(earnedPoints, balance(userId));
        String remark = "退款回收积分";
        if (reclaimable < earnedPoints) {
            remark = "退款回收积分（余额不足，" + earnedPoints + "分仅回收" + reclaimable + "分）";
        }
        if (reclaimable > 0) {
            accountMapper.deduct(userId, reclaimable);
        }
        insertLog(userId, "earn_reclaim", -reclaimable, orderId, orderNo, remark);
        return reclaimable;
    }

    // ==================== 试算 ====================

    @Override
    public PointsPreviewVO preview(Long userId, BigDecimal amount) {
        PointsPreviewVO vo = new PointsPreviewVO();
        int balance = balance(userId);
        // 仅支持整百抵扣（100分=1元），余额先向下取整百
        int available = balance / props.getRedeemRate() * props.getRedeemRate();
        // 抵扣上限：应付金额 × redeemMaxPercent%，换算回积分数向下取整
        int capPoints = amount.multiply(BigDecimal.valueOf(props.getRedeemMaxPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(props.getRedeemRate()))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        int max = Math.min(available, Math.max(capPoints, 0));
        max = max / props.getRedeemRate() * props.getRedeemRate();
        vo.setAvailable(balance);
        vo.setMaxRedeemPoints(max);
        vo.setMaxDeductAmount(BigDecimal.valueOf(max)
                .divide(BigDecimal.valueOf(props.getRedeemRate()), 2, RoundingMode.DOWN));
        vo.setRule(props.getRedeemRate() + "积分=1元，最多抵订单金额的" + props.getRedeemMaxPercent() + "%");
        return vo;
    }

    // ==================== 签到 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinVO checkin(Long userId) {
        if (!props.isEnabled()) {
            throw new BusinessException("签到活动未开放");
        }
        LocalDate today = LocalDate.now();
        String redisKey = "checkin:" + userId + ":" + today.format(DATE_FMT);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofHours(26));
        if (Boolean.FALSE.equals(first)) {
            throw new BusinessException("今日已签到");
        }

        Set<LocalDate> dates = recentDates(userId);
        boolean alreadyInDb = dates.contains(today);
        if (alreadyInDb) {
            throw new BusinessException("今日已签到");
        }

        // 连续递增：今天将是"截至昨天连续数 + 1"天
        int streakUptoYesterday = countStreak(dates, today.minusDays(1));
        int todayPoints = Math.min(
                props.getCheckinBase() + streakUptoYesterday * props.getCheckinInc(),
                props.getCheckinCap());

        MkCheckin record = new MkCheckin();
        record.setUserId(userId);
        record.setCheckinDate(today);
        record.setPoints(todayPoints);
        try {
            checkinMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("今日已签到");
        }

        ensureAccount(userId);
        accountMapper.credit(userId, todayPoints);
        insertLog(userId, "checkin", todayPoints, null, null,
                "每日签到（连续第" + (streakUptoYesterday + 1) + "天）");

        CheckinVO vo = new CheckinVO();
        vo.setChecked(true);
        vo.setPoints(todayPoints);
        vo.setContinuous(streakUptoYesterday + 1);
        vo.setNextPoints(nextPointsAfter(streakUptoYesterday + 1));
        vo.setBalance(balance(userId));
        vo.setRecentDates(new ArrayList<>(dates));
        vo.getRecentDates().add(today);
        return vo;
    }

    @Override
    public CheckinVO todayStatus(Long userId) {
        Set<LocalDate> dates = recentDates(userId);
        boolean checked = dates.contains(LocalDate.now());
        int continuous = checked
                ? countStreak(dates, LocalDate.now())
                : countStreak(dates, LocalDate.now().minusDays(1));
        CheckinVO vo = new CheckinVO();
        vo.setChecked(checked);
        vo.setContinuous(continuous);
        if (checked) {
            vo.setPoints(props.getCheckinBase() + Math.max(continuous - 1, 0) * props.getCheckinInc());
            vo.setPoints(Math.min(vo.getPoints(), props.getCheckinCap()));
            vo.setNextPoints(nextPointsAfter(continuous));
        } else {
            vo.setPoints(nextPointsAfter(continuous));
            vo.setNextPoints(nextPointsAfter(continuous + 1));
        }
        vo.setRecentDates(new ArrayList<>(dates));
        return vo;
    }

    /** 第 (streak+1) 天将得的分 */
    private int nextPointsAfter(int streak) {
        return Math.min(props.getCheckinBase() + streak * props.getCheckinInc(), props.getCheckinCap());
    }

    /** 最近 STREAK_WINDOW_DAYS 天的签到日期集合 */
    private Set<LocalDate> recentDates(Long userId) {
        List<MkCheckin> list = checkinMapper.selectList(new LambdaQueryWrapper<MkCheckin>()
                .eq(MkCheckin::getUserId, userId)
                .ge(MkCheckin::getCheckinDate, LocalDate.now().minusDays(STREAK_WINDOW_DAYS)));
        return new TreeSet<>(list.stream()
                .map(MkCheckin::getCheckinDate).collect(Collectors.toSet()));
    }

    /** 从 endDay（含）向前数连续签到天数 */
    private int countStreak(Set<LocalDate> dates, LocalDate endDay) {
        int streak = 0;
        LocalDate d = endDay;
        while (dates.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    // ==================== 明细 ====================

    @Override
    public IPage<PointsLogVO> pageLogs(Long userId, Integer page, Integer size) {
        IPage<MkPointsLog> p = logMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MkPointsLog>()
                        .eq(MkPointsLog::getUserId, userId)
                        .orderByDesc(MkPointsLog::getId));
        Page<PointsLogVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(l -> {
            PointsLogVO vo = new PointsLogVO();
            vo.setId(l.getId());
            vo.setType(l.getType());
            vo.setPoints(l.getPoints());
            vo.setOrderId(l.getOrderId());
            vo.setOrderNo(l.getOrderNo());
            vo.setRemark(l.getRemark());
            vo.setCreateTime(l.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    // ==================== 内部工具 ====================

    private MkPointsLog findLog(String type, String orderNo) {
        return logMapper.selectOne(new LambdaQueryWrapper<MkPointsLog>()
                .eq(MkPointsLog::getType, type)
                .eq(MkPointsLog::getOrderNo, orderNo)
                .last("LIMIT 1"));
    }

    private boolean logExists(String type, String orderNo) {
        return findLog(type, orderNo) != null;
    }

    private void insertLog(Long userId, String type, int points, Long orderId, String orderNo, String remark) {
        MkPointsLog entry = new MkPointsLog();
        entry.setUserId(userId);
        entry.setType(type);
        entry.setPoints(points);
        entry.setOrderId(orderId);
        entry.setOrderNo(orderNo);
        entry.setRemark(remark);
        entry.setCreateTime(LocalDateTime.now());
        logMapper.insert(entry);
    }
}
