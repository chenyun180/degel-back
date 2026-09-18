package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.SettlementAccount;
import com.degel.order.entity.SettlementAccountLog;
import com.degel.order.entity.SettlementWithdraw;
import com.degel.order.mapper.SettlementAccountLogMapper;
import com.degel.order.mapper.SettlementAccountMapper;
import com.degel.order.mapper.SettlementWithdrawMapper;
import com.degel.order.service.ISettlementWithdrawService;
import com.degel.order.vo.WithdrawAuditVo;
import com.degel.order.vo.WithdrawApplyVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提现实现。审核通过 = 模拟打款：CAS 状态流转 + 原子扣款（WHERE balance>=?）在同一事务，
 * 余额不足则整体回滚（状态保持待审核），平台改走驳回。
 * 打款凭证 = withdraw 单(status=1+pay_time) + 流水 biz_type=2，不写 mall_payment_log（C 端用户支付域）。
 */
@Slf4j
@Service
public class SettlementWithdrawServiceImpl extends ServiceImpl<SettlementWithdrawMapper, SettlementWithdraw>
        implements ISettlementWithdrawService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_SEQ = new AtomicLong();

    @Autowired
    private SettlementAccountMapper accountMapper;
    @Autowired
    private SettlementAccountLogMapper accountLogMapper;

    @Override
    public void apply(Long shopId, WithdrawApplyVo vo) {
        SettlementAccount account = accountMapper.selectByShopId(shopId);
        BigDecimal balance = account != null && account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        if (vo.getAmount().compareTo(balance) > 0) {
            throw new BusinessException("可提现余额不足");
        }
        SettlementWithdraw withdraw = new SettlementWithdraw();
        withdraw.setWithdrawNo("W" + NO_FORMAT.format(LocalDateTime.now())
                + String.format("%03d", NO_SEQ.incrementAndGet() % 1000));
        withdraw.setShopId(shopId);
        withdraw.setAmount(vo.getAmount());
        withdraw.setStatus(0);
        withdraw.setApplyRemark(vo.getApplyRemark());
        save(withdraw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(WithdrawAuditVo vo, String auditBy) {
        SettlementWithdraw withdraw = getById(vo.getWithdrawId());
        if (withdraw == null) {
            throw new BusinessException("提现单不存在");
        }
        if (vo.getApprove()) {
            String payTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 先 CAS 流转（防并发双审），扣款失败抛异常整体回滚（状态回到待审核）
            if (baseMapper.casAudit(vo.getWithdrawId(), 1, vo.getAuditRemark(), auditBy, payTime) <= 0) {
                throw new BusinessException("该提现单已被处理");
            }
            if (accountMapper.deductGuarded(withdraw.getShopId(), withdraw.getAmount()) <= 0) {
                throw new BusinessException("商家余额不足，无法打款，请驳回该申请");
            }
            SettlementAccount account = accountMapper.selectByShopId(withdraw.getShopId());
            BigDecimal balanceAfter = account != null && account.getBalance() != null
                    ? account.getBalance() : BigDecimal.ZERO;
            try {
                SettlementAccountLog logRow = new SettlementAccountLog();
                logRow.setShopId(withdraw.getShopId());
                logRow.setBizType(2);
                logRow.setBizNo(withdraw.getId());
                logRow.setAmount(withdraw.getAmount().negate());
                logRow.setBalanceAfter(balanceAfter);
                logRow.setRemark("提现打款：" + withdraw.getWithdrawNo());
                accountLogMapper.insert(logRow);
            } catch (DuplicateKeyException e) {
                // 审核接口重试重入：扣款与流水同事务，uk_biz 命中说明已记账，保持幂等
                log.warn("[withdraw] 打款流水重复写入已忽略 withdrawId={}", withdraw.getId());
            }
            log.info("[withdraw] 提现打款完成 withdrawNo={} amount={} shopId={}",
                    withdraw.getWithdrawNo(), withdraw.getAmount(), withdraw.getShopId());
        } else {
            if (vo.getAuditRemark() == null || vo.getAuditRemark().trim().isEmpty()) {
                throw new BusinessException("驳回必须填写审核备注");
            }
            if (baseMapper.casAudit(vo.getWithdrawId(), 2, vo.getAuditRemark(), auditBy, null) <= 0) {
                throw new BusinessException("该提现单已被处理");
            }
        }
    }

    @Override
    public IPage<SettlementWithdraw> page(IPage<SettlementWithdraw> page, Long shopId, Integer status) {
        return page(page, new LambdaQueryWrapper<SettlementWithdraw>()
                .eq(shopId != null, SettlementWithdraw::getShopId, shopId)
                .eq(status != null, SettlementWithdraw::getStatus, status)
                .orderByDesc(SettlementWithdraw::getCreateTime));
    }
}
