package com.degel.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.order.entity.OrderAfterSale;
import com.degel.order.entity.OrderInfo;
import com.degel.order.mapper.OrderAfterSaleMapper;
import com.degel.order.mapper.OrderInfoMapper;
import com.degel.order.service.IOrderAfterSaleService;
import com.degel.order.vo.AfterSaleHandleVo;
import com.degel.order.vo.AfterSaleInfoVo;
import com.degel.order.vo.inner.AfterSaleCreateInnerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleServiceImpl extends ServiceImpl<OrderAfterSaleMapper, OrderAfterSale> implements IOrderAfterSaleService {

    // 注入 Mapper 而非 Service，避免与 OrderInfoServiceImpl 形成构造器循环依赖
    private final OrderInfoMapper orderInfoMapper;
    private final com.degel.order.feign.MarketingFeignClient marketingFeignClient;

    @Override
    public IPage<OrderAfterSale> pageAfterSales(IPage<OrderAfterSale> page, Long shopId, Integer status, Integer type) {
        LambdaQueryWrapper<OrderAfterSale> wrapper = new LambdaQueryWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getShopId, shopId)
                .eq(status != null, OrderAfterSale::getStatus, status)
                .eq(type != null, OrderAfterSale::getType, type)
                .orderByDesc(OrderAfterSale::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(AfterSaleHandleVo vo, Long shopId) {
        OrderAfterSale afterSale = getById(vo.getAfterSaleId());
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 0) {
            throw new BusinessException("售后单状态不允许操作");
        }

        LambdaUpdateWrapper<OrderAfterSale> updateWrapper = new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, vo.getAfterSaleId());

        if ("agree".equals(vo.getAction())) {
            if (afterSale.getType() == 1) {
                updateWrapper.set(OrderAfterSale::getStatus, 3);
            } else {
                updateWrapper.set(OrderAfterSale::getStatus, 1);
            }
        } else if ("reject".equals(vo.getAction())) {
            updateWrapper.set(OrderAfterSale::getStatus, 5);
        } else {
            throw new BusinessException("无效的操作类型");
        }

        if (vo.getMerchantRemark() != null) {
            updateWrapper.set(OrderAfterSale::getMerchantRemark, vo.getMerchantRemark());
        }
        update(updateWrapper);

        // 整单退款完成（agree + type=1 仅退款）→ 退回优惠券（2→未过期?4:5，幂等）。
        // 补贴记账冲销口径：报表按售后状态剔除该单补贴，一期不加冲销列。
        // ⚠️ Feign 在事务内 best-effort：失败仅记日志（券状态可人工/重试修复），不影响售后主流程
        if ("agree".equals(vo.getAction()) && afterSale.getType() == 1) {
            try {
                java.util.Map<String, Long> req = new java.util.HashMap<>(1);
                req.put("orderId", afterSale.getOrderId());
                marketingFeignClient.returnCoupon(req);
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(OrderAfterSaleServiceImpl.class)
                        .error("[handle] 整单退回券失败（可人工补偿）afterSaleId={} orderId={}",
                                afterSale.getId(), afterSale.getOrderId(), ex);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long afterSaleId, Long shopId) {
        OrderAfterSale afterSale = getById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!afterSale.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 2) {
            throw new BusinessException("售后单状态不允许确认收货");
        }

        update(new LambdaUpdateWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getId, afterSaleId)
                .set(OrderAfterSale::getStatus, 3));
    }

    // ==================== C 端内部接口实现 ====================

    @Override
    public Long createInnerAfterSale(AfterSaleCreateInnerVo vo) {
        OrderAfterSale afterSale = new OrderAfterSale();
        afterSale.setOrderId(vo.getOrderId());
        afterSale.setUserId(vo.getUserId());
        afterSale.setShopId(vo.getShopId());
        afterSale.setType(vo.getType());
        afterSale.setStatus(0);
        afterSale.setReason(vo.getReason());
        afterSale.setRefundAmount(vo.getRefundAmount());
        save(afterSale);
        return afterSale.getId();
    }

    @Override
    public IPage<AfterSaleInfoVo> pageInnerAfterSales(Long userId, Integer status, Integer page, Integer pageSize) {
        IPage<OrderAfterSale> salePage = page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<OrderAfterSale>()
                        .eq(OrderAfterSale::getUserId, userId)
                        .eq(status != null, OrderAfterSale::getStatus, status)
                        .orderByDesc(OrderAfterSale::getCreateTime));

        Page<AfterSaleInfoVo> result = new Page<>(salePage.getCurrent(), salePage.getSize(), salePage.getTotal());
        List<OrderAfterSale> records = salePage.getRecords();
        if (records.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        // 关联订单号
        Map<Long, String> orderNoMap = orderInfoMapper.selectBatchIds(
                        records.stream().map(OrderAfterSale::getOrderId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(OrderInfo::getId, OrderInfo::getOrderNo, (a, b) -> a));

        result.setRecords(records.stream()
                .map(s -> toInnerVo(s, orderNoMap.get(s.getOrderId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public boolean existsActiveAfterSale(Long orderId, Long userId) {
        return count(new LambdaQueryWrapper<OrderAfterSale>()
                .eq(OrderAfterSale::getOrderId, orderId)
                .eq(OrderAfterSale::getUserId, userId)
                .in(OrderAfterSale::getStatus, Arrays.asList(0, 1))) > 0;
    }

    @Override
    public AfterSaleInfoVo getInnerAfterSale(Long id) {
        OrderAfterSale afterSale = getById(id);
        if (afterSale == null) {
            return null;
        }
        OrderInfo order = orderInfoMapper.selectById(afterSale.getOrderId());
        return toInnerVo(afterSale, order != null ? order.getOrderNo() : null);
    }

    private AfterSaleInfoVo toInnerVo(OrderAfterSale afterSale, String orderNo) {
        AfterSaleInfoVo vo = new AfterSaleInfoVo();
        vo.setId(afterSale.getId());
        vo.setOrderId(afterSale.getOrderId());
        vo.setOrderNo(orderNo);
        vo.setUserId(afterSale.getUserId());
        vo.setShopId(afterSale.getShopId());
        vo.setType(afterSale.getType());
        vo.setStatus(afterSale.getStatus());
        vo.setReason(afterSale.getReason());
        vo.setRefundAmount(afterSale.getRefundAmount());
        vo.setMerchantRemark(afterSale.getMerchantRemark());
        vo.setCreateTime(afterSale.getCreateTime());
        vo.setUpdateTime(afterSale.getUpdateTime());
        return vo;
    }
}
