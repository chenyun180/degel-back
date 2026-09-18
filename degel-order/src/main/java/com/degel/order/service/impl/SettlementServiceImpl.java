package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.SettlementAccount;
import com.degel.order.entity.SettlementAccountLog;
import com.degel.order.entity.SettlementConfig;
import com.degel.order.entity.SettlementOrder;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.mapper.SettlementAccountLogMapper;
import com.degel.order.mapper.SettlementAccountMapper;
import com.degel.order.mapper.SettlementConfigMapper;
import com.degel.order.mapper.SettlementOrderMapper;
import com.degel.order.service.ISettlementService;
import com.degel.order.vo.SettleCandidateVo;
import com.degel.order.vo.SettlementConfigVo;
import com.degel.order.vo.ShopSettlementAccountVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 分账清结算实现。并发控制全靠原子 UPDATE（无 Redisson）：
 * - 结算：insert 明细(uk 兜底) → CAS 0→1 → ON DUPLICATE 入余额 → 流水(uk_biz 兜底)
 * - 扣回：CAS 0→2 作废 或 CAS 1→2 + 无条件扣减（允许负）
 * - 任务多实例/重跑由 uk_order_id + CAS + uk_biz 三层幂等吸收（与现有 @Scheduled 任务同风格）
 */
@Slf4j
@Service
public class SettlementServiceImpl extends ServiceImpl<SettlementOrderMapper, SettlementOrder>
        implements ISettlementService {

    private static final String KEY_COMMISSION_RATE = "commission_rate";
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("5");
    private static final String KEY_AFTERSALE_DAYS = "aftersale_days";
    private static final int DEFAULT_AFTERSALE_DAYS = 7;
    /** 确认收货后满 N 天可结算（T+7，避开售后期） */
    private static final int SETTLE_DAYS = 7;
    private static final int CANDIDATE_LIMIT = 500;

    @Autowired
    private SettlementAccountMapper accountMapper;
    @Autowired
    private SettlementAccountLogMapper accountLogMapper;
    @Autowired
    private SettlementConfigMapper configMapper;
    @Autowired
    private OrderAfterSaleMapper orderAfterSaleMapper;
    @Autowired
    private com.degel.order.mapper.SettlementWithdrawMapper withdrawMapper;
    @Autowired
    private com.degel.order.feign.PayFeignClient payFeignClient;

    // ---------------------------------------------------------------- 结算任务

    /** 每小时 30 分（错开自动收货 15 分 / 退款流水对账的 fixedDelay） */
    @Override
    @Scheduled(cron = "0 30 * * * ?")
    public void settleDueOrders() {
        BigDecimal rate = readCommissionRate();
        List<SettleCandidateVo> candidates = baseMapper.selectSettleCandidates(SETTLE_DAYS, CANDIDATE_LIMIT);
        int created = 0;
        for (SettleCandidateVo c : candidates) {
            SettlementOrder detail = new SettlementOrder();
            detail.setOrderId(c.getOrderId());
            detail.setOrderNo(c.getOrderNo());
            detail.setShopId(c.getShopId());
            detail.setUserId(c.getUserId());
            detail.setPayAmount(c.getPayAmount());
            detail.setPlatformSubsidy(c.getPlatformSubsidy());
            detail.setPointsDeduct(c.getPointsDeduct());
            BigDecimal gross = c.getPayAmount()
                    .add(c.getPlatformSubsidy() == null ? BigDecimal.ZERO : c.getPlatformSubsidy())
                    .add(c.getPointsDeduct() == null ? BigDecimal.ZERO : c.getPointsDeduct());
            BigDecimal commission = gross.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            detail.setGrossAmount(gross);
            detail.setCommissionRate(rate);
            detail.setCommissionAmount(commission);
            detail.setNetAmount(gross.subtract(commission));
            detail.setStatus(0);
            try {
                save(detail);
                created++;
            } catch (DuplicateKeyException e) {
                // 多实例/重跑：该订单已有明细，幂等跳过
            } catch (Exception e) {
                log.error("[settle] 生成结算明细失败 orderId={}", c.getOrderId(), e);
            }
        }

        // 入账：本轮新建的 + 历史遗留待入账的（入账失败自动重试路径）
        List<SettlementOrder> pendings = list(new LambdaQueryWrapper<SettlementOrder>()
                .eq(SettlementOrder::getStatus, 0)
                .orderByAsc(SettlementOrder::getId)
                .last("LIMIT " + CANDIDATE_LIMIT));
        int settled = 0;
        for (SettlementOrder p : pendings) {
            if (trySettleOne(p)) {
                settled++;
            }
        }
        if (created > 0 || settled > 0 || !candidates.isEmpty()) {
            log.info("[settle] 本轮结算完成：新增明细 {} 笔，入账 {} 笔（佣金比例 {}%）", created, settled, rate);
        }
    }

    @Override
    public boolean trySettleOne(SettlementOrder detail) {
        if (baseMapper.markSettled(detail.getId()) <= 0) {
            // 退款钩子已抢先作废（status=2），该单不应入账
            return false;
        }
        try {
            accountMapper.credit(detail.getShopId(), detail.getNetAmount());
            BigDecimal balanceAfter = readBalance(detail.getShopId());
            insertLogSafe(detail.getShopId(), 1, detail.getId(), detail.getNetAmount(), balanceAfter,
                    "T+7结算：" + detail.getOrderNo());
            return true;
        } catch (Exception e) {
            // 回退 CAS 让下轮重试（流水未写成功不会重复入账；流水已写但异常的极端场景由 uk_biz 幂等 + 对账兜底）
            baseMapper.revertSettled(detail.getId());
            log.error("[settle] 入账失败，已回退待入账 detailId={} orderId={}",
                    detail.getId(), detail.getOrderId(), e);
            return false;
        }
    }

    // ---------------------------------------------------------------- 退款扣回

    @Override
    public void onRefundCompleted(OrderAfterSale afterSale) {
        Long orderId = afterSale.getOrderId();
        // ① 抢占"待入账"直接作废：未入账无款可扣，同时封死结算任务随后 0→1 的窗口
        if (baseMapper.markVoidIfPending(orderId) > 0) {
            log.info("[settle] 结算前退款，明细作废 orderId={} afterSaleId={}", orderId, afterSale.getId());
            return;
        }
        // ② 已入账 → 扣回（CAS 1→2 保证同一明细只有一条路径走到扣款）
        SettlementOrder detail = getOne(new LambdaQueryWrapper<SettlementOrder>()
                .eq(SettlementOrder::getOrderId, orderId));
        if (detail == null || baseMapper.markDeducted(orderId) <= 0) {
            return;
        }
        accountMapper.deductAllowNegative(afterSale.getShopId(), detail.getNetAmount());
        BigDecimal balanceAfter = readBalance(afterSale.getShopId());
        insertLogSafe(afterSale.getShopId(), 3, afterSale.getId(), detail.getNetAmount().negate(), balanceAfter,
                "结算后退款扣回：" + detail.getOrderNo());
        log.info("[settle] 结算后退款扣回 orderId={} net={} shopId={}",
                orderId, detail.getNetAmount(), afterSale.getShopId());
    }

    /** 兜底对账：钩子是 best-effort，失败的单在这里按 CAS 幂等补做 */
    @Override
    @Scheduled(fixedDelay = 1_800_000, initialDelay = 300_000)
    public void reconcileRefundDeduct() {
        List<Long> backlog = baseMapper.selectRefundDeductBacklog(30, 200);
        if (backlog.isEmpty()) {
            return;
        }
        log.warn("[settle] 退款扣回兜底对账：发现 {} 笔遗漏", backlog.size());
        for (Long afterSaleId : backlog) {
            try {
                OrderAfterSale afterSale = orderAfterSaleMapper.selectById(afterSaleId);
                if (afterSale != null) {
                    onRefundCompleted(afterSale);
                }
            } catch (Exception e) {
                log.error("[settle] 兜底扣回失败 afterSaleId={}", afterSaleId, e);
            }
        }
    }

    // ---------------------------------------------------------------- 店铺端查询

    @Override
    public ShopSettlementAccountVo getShopAccount(Long shopId) {
        ShopSettlementAccountVo vo = new ShopSettlementAccountVo();
        vo.setShopId(shopId);
        SettlementAccount account = accountMapper.selectByShopId(shopId);
        if (account == null) {
            vo.setBalance(BigDecimal.ZERO);
            vo.setTotalSettled(BigDecimal.ZERO);
            vo.setTotalWithdrawn(BigDecimal.ZERO);
        } else {
            vo.setBalance(account.getBalance());
            vo.setTotalSettled(account.getTotalSettled());
            vo.setTotalWithdrawn(account.getTotalWithdrawn());
        }
        return vo;
    }

    @Override
    public IPage<SettlementOrder> pageDetail(IPage<SettlementOrder> page, Long shopId, Integer status) {
        return page(page, new LambdaQueryWrapper<SettlementOrder>()
                .eq(SettlementOrder::getShopId, shopId)
                .eq(status != null, SettlementOrder::getStatus, status)
                .orderByDesc(SettlementOrder::getCreateTime));
    }

    // ---------------------------------------------------------------- 佣金配置

    @Override
    public BigDecimal readCommissionRate() {
        SettlementConfig config = configMapper.selectOne(new LambdaQueryWrapper<SettlementConfig>()
                .eq(SettlementConfig::getConfigKey, KEY_COMMISSION_RATE));
        if (config == null || config.getConfigValue() == null) {
            log.warn("[settle] 佣金比例配置缺失，回退默认 {}%", DEFAULT_RATE);
            return DEFAULT_RATE;
        }
        try {
            return new BigDecimal(config.getConfigValue());
        } catch (NumberFormatException e) {
            log.error("[settle] 佣金比例配置非法：{}，回退默认 {}%", config.getConfigValue(), DEFAULT_RATE);
            return DEFAULT_RATE;
        }
    }

    @Override
    public int readAftersaleDays() {
        SettlementConfig config = configMapper.selectOne(new LambdaQueryWrapper<SettlementConfig>()
                .eq(SettlementConfig::getConfigKey, KEY_AFTERSALE_DAYS));
        if (config == null || config.getConfigValue() == null) {
            log.warn("[settle] 售后窗口配置缺失，回退默认 {} 天", DEFAULT_AFTERSALE_DAYS);
            return DEFAULT_AFTERSALE_DAYS;
        }
        try {
            return Integer.parseInt(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.error("[settle] 售后窗口配置非法：{}，回退默认 {} 天", config.getConfigValue(), DEFAULT_AFTERSALE_DAYS);
            return DEFAULT_AFTERSALE_DAYS;
        }
    }

    @Override
    public SettlementConfigVo getConfig() {
        SettlementConfigVo vo = new SettlementConfigVo();
        vo.setCommissionRate(readCommissionRate());
        vo.setAftersaleDays(readAftersaleDays());
        return vo;
    }

    @Override
    public void updateConfig(BigDecimal commissionRate) {
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0
                || commissionRate.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("佣金比例须在 0~100 之间");
        }
        upsertConfig(KEY_COMMISSION_RATE, commissionRate.toPlainString(), "全局佣金比例（%），快照到每行结算明细");
    }

    /** 售后窗口（天）：1~90，即时生效（存量售后单不受影响） */
    @Override
    public void updateAftersaleDays(Integer days) {
        if (days == null || days < 1 || days > 90) {
            throw new BusinessException("售后窗口须在 1~90 天之间");
        }
        upsertConfig(KEY_AFTERSALE_DAYS, String.valueOf(days), "售后窗口（天）：确认收货后 N 天内可申请售后");
    }

    private void upsertConfig(String key, String value, String remark) {
        SettlementConfig config = configMapper.selectOne(new LambdaQueryWrapper<SettlementConfig>()
                .eq(SettlementConfig::getConfigKey, key));
        if (config == null) {
            config = new SettlementConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setRemark(remark);
            configMapper.insert(config);
        } else {
            config.setConfigValue(value);
            configMapper.updateById(config);
        }
    }

    // ---------------------------------------------------------------- 平台资金总览

    @Override
    public com.degel.order.vo.PlatformSettlementOverviewVo getPlatformOverview() {
        com.degel.order.vo.PlatformSettlementOverviewVo vo = new com.degel.order.vo.PlatformSettlementOverviewVo();
        vo.setCommissionIncome(baseMapper.sumCommissionIncome());
        vo.setSubsidyPaid(baseMapper.sumPlatformSubsidyPaid());
        vo.setWithdrawPaid(withdrawMapper.sumWithdrawPaid());
        vo.setShopBalanceTotal(accountMapper.sumShopBalance());
        vo.setShopDebt(accountMapper.sumShopDebt());
        // 用户支付净额来自 degel-app 流水（Feign）；失败 best-effort 记 0，不阻塞总览页
        BigDecimal userPayNet = BigDecimal.ZERO;
        try {
            java.math.BigDecimal[] payRefund = payFeignClient.summary().getData();
            if (payRefund != null && payRefund.length == 2) {
                userPayNet = payRefund[0].subtract(payRefund[1]);
            }
        } catch (Exception e) {
            log.error("[overview] 支付汇总 Feign 失败，用户支付净额按 0 展示", e);
        }
        vo.setUserPayNet(userPayNet);
        vo.setNetCashFlow(userPayNet.subtract(vo.getShopBalanceTotal()));
        return vo;
    }

    // ---------------------------------------------------------------- 私有

    private BigDecimal readBalance(Long shopId) {
        SettlementAccount account = accountMapper.selectByShopId(shopId);
        return account != null && account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
    }

    /** 流水写入（uk_biz 幂等：重复插入视为已记账，忽略） */
    private void insertLogSafe(Long shopId, int bizType, Long bizNo, BigDecimal amount,
                               BigDecimal balanceAfter, String remark) {
        try {
            SettlementAccountLog logRow = new SettlementAccountLog();
            logRow.setShopId(shopId);
            logRow.setBizType(bizType);
            logRow.setBizNo(bizNo);
            logRow.setAmount(amount);
            logRow.setBalanceAfter(balanceAfter);
            logRow.setRemark(remark);
            accountLogMapper.insert(logRow);
        } catch (DuplicateKeyException e) {
            log.warn("[settle] 流水重复写入已忽略 shopId={} bizType={} bizNo={}", shopId, bizType, bizNo);
        }
    }
}
