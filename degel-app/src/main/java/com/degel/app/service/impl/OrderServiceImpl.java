package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.entity.MallAddress;
import com.degel.app.entity.MallCart;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.feign.StockFeignClient;
import com.degel.app.mapper.MallAddressMapper;
import com.degel.app.mapper.MallCartMapper;
import com.degel.app.service.OrderService;
import com.degel.app.vo.*;
import com.degel.app.vo.dto.*;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现（C-02 ~ C-06）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderFeignClient orderFeignClient;
    private final ProductFeignClient productFeignClient;
    private final StockFeignClient stockFeignClient;
    private final MallCartMapper mallCartMapper;
    private final MallAddressMapper mallAddressMapper;
    private final RedissonClient redissonClient;

    // =========================================================
    // C-02: POST /app/order — 创建订单
    // =========================================================

    @Override
    public OrderCreateVO createOrder(OrderCreateReqVO reqVO, Long userId) {
        // Step 1: 参数校验（cartIds / skuId 二选一）
        boolean hasCart = reqVO.getCartIds() != null && !reqVO.getCartIds().isEmpty();
        boolean hasSku = reqVO.getSkuId() != null;
        if (!hasCart && !hasSku) {
            throw BusinessException.of(40010, "请选择商品");
        }
        if (hasCart && hasSku) {
            throw BusinessException.of(40010, "cartIds 与 skuId 只能选其一");
        }

        // Step 2: 获取 SKU 列表（购物车模式/直购模式）
        // skuId -> quantity 映射
        Map<Long, Integer> skuQuantityMap = new LinkedHashMap<>();
        // skuId -> MallCart.id 映射（购物车模式）
        Map<Long, Long> skuCartIdMap = new HashMap<>();

        if (hasCart) {
            // 购物车模式：查 mall_cart
            LambdaQueryWrapper<MallCart> wrapper = new LambdaQueryWrapper<MallCart>()
                    .eq(MallCart::getUserId, userId)
                    .in(MallCart::getId, reqVO.getCartIds())
                    .eq(MallCart::getDelFlag, 0);
            List<MallCart> carts = mallCartMapper.selectList(wrapper);
            if (carts.isEmpty()) {
                throw BusinessException.of(40010, "购物车记录不存在或已被删除");
            }
            for (MallCart cart : carts) {
                skuQuantityMap.put(cart.getSkuId(), cart.getQuantity());
                skuCartIdMap.put(cart.getSkuId(), cart.getId());
            }
        } else {
            // 直购模式
            skuQuantityMap.put(reqVO.getSkuId(), reqVO.getQuantity() == null ? 1 : reqVO.getQuantity());
        }

        List<Long> skuIds = new ArrayList<>(skuQuantityMap.keySet());

        // Step 3: Feign 批量查 ProductSku，校验 status=1
        R<List<ProductSkuVO>> skuResp = productFeignClient.batchGetSku(skuIds);
        if (skuResp == null || skuResp.getCode() != 200 || skuResp.getData() == null) {
            throw BusinessException.of(50001, "查询商品信息失败，请稍后重试");
        }
        List<ProductSkuVO> skuList = skuResp.getData();
        Map<Long, ProductSkuVO> skuMap = skuList.stream()
                .collect(Collectors.toMap(ProductSkuVO::getId, s -> s));

        for (Long skuId : skuIds) {
            ProductSkuVO sku = skuMap.get(skuId);
            if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw BusinessException.of(40011, "商品已下架，无法下单");
            }
        }

        // Step 4: 分布式锁 + 库存扣减（防超卖）
        List<Long> lockedSkuIds = new ArrayList<>();
        List<RLock> acquiredLocks = new ArrayList<>();
        try {
            for (Long skuId : skuIds) {
                ProductSkuVO sku = skuMap.get(skuId);
                Integer needQty = skuQuantityMap.get(skuId);

                // 校验库存（快速失败预判，在锁外执行）
                // ⚠️ 注意：此处仅作 fast-fail 预判，非最终库存保证。
                // 真正的库存原子性由下方 deductStock Feign 的原子 SQL 提供。
                // 请勿删除 deductStock 调用侧的失败抛异常逻辑，否则会导致超卖。
                if (sku.getStock() == null || sku.getStock() < needQty) {
                    throw BusinessException.of(40012, "库存不足：" + sku.getSkuName());
                }

                // Redisson 分布式锁
                String lockKey = "lock:stock:" + skuId;
                RLock lock = redissonClient.getLock(lockKey);
                boolean locked = false;
                try {
                    locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw BusinessException.of(50001, "系统繁忙，请稍后重试");
                }
                if (!locked) {
                    throw BusinessException.of(50001, "系统繁忙，请稍后重试");
                }
                acquiredLocks.add(lock);
                lockedSkuIds.add(skuId);

                // Feign 扣减库存
                StockDeductVO deductVO = new StockDeductVO(skuId, needQty);
                R<Boolean> deductResp = stockFeignClient.deductStock(deductVO);
                if (deductResp == null || deductResp.getCode() != 200 || !Boolean.TRUE.equals(deductResp.getData())) {
                    throw BusinessException.of(40012, "库存不足，扣减失败");
                }
            }

            // Step 5: Feign 查收货地址，校验归属
            MallAddress address = mallAddressMapper.selectOne(
                    new LambdaQueryWrapper<MallAddress>()
                            .eq(MallAddress::getId, reqVO.getAddressId())
                            .eq(MallAddress::getUserId, userId)
                            .eq(MallAddress::getDelFlag, 0)
            );
            if (address == null) {
                throw BusinessException.of(40013, "收货地址不存在");
            }

            // Step 6: 计算金额
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderCreateInnerReqVO.OrderItemInnerVO> itemList = new ArrayList<>();

            for (Long skuId : skuIds) {
                ProductSkuVO sku = skuMap.get(skuId);
                Integer qty = skuQuantityMap.get(skuId);
                BigDecimal itemTotal = sku.getPrice().multiply(BigDecimal.valueOf(qty));
                totalAmount = totalAmount.add(itemTotal);

                // 构建订单明细（快照）
                OrderCreateInnerReqVO.OrderItemInnerVO item = new OrderCreateInnerReqVO.OrderItemInnerVO();
                item.setSpuId(sku.getSpuId());
                item.setSkuId(skuId);
                // spuName 取 ProductSkuVO.spuName（product 服务批量查 SKU 时一并返回）
                // 若 spuName 为空（旧数据），回退使用 skuName 兜底，避免快照丢失商品名称
                item.setSpuName(sku.getSpuName() != null ? sku.getSpuName() : sku.getSkuName());
                item.setSkuSpec(sku.getSpecData());
                item.setSkuImage(sku.getImage());
                item.setPrice(sku.getPrice());
                item.setQuantity(qty);
                item.setTotalAmount(itemTotal);
                itemList.add(item);
            }
            BigDecimal freightAmount = BigDecimal.ZERO;
            BigDecimal discountAmount = BigDecimal.ZERO;
            BigDecimal payAmount = totalAmount.add(freightAmount).subtract(discountAmount);

            // Step 7: 生成订单号 yyyyMMddHHmmss + userId末4位 + 4位随机数
            String orderNo = generateOrderNo(userId);

            // Step 8: 构建内部创建请求，调用 degel-order
            String fullAddress = address.getProvince() + address.getCity()
                    + address.getDistrict() + address.getDetail();
            OrderCreateInnerReqVO innerReq = new OrderCreateInnerReqVO();
            innerReq.setUserId(userId);
            innerReq.setShopId(1L); // 默认shopId，MVP阶段
            innerReq.setOrderNo(orderNo);
            innerReq.setTotalAmount(totalAmount);
            innerReq.setFreightAmount(freightAmount);
            innerReq.setDiscountAmount(discountAmount);
            innerReq.setPayAmount(payAmount);
            innerReq.setReceiverName(address.getName());
            innerReq.setReceiverPhone(address.getPhone());
            innerReq.setReceiverAddress(fullAddress);
            innerReq.setRemark(reqVO.getRemark());
            innerReq.setAutoCancelTime(LocalDateTime.now().plusMinutes(30));
            innerReq.setItems(itemList);

            R<Long> createResp = orderFeignClient.createOrder(innerReq);
            if (createResp == null || createResp.getCode() != 200 || createResp.getData() == null) {
                throw BusinessException.of(50001, "创建订单失败，请稍后重试");
            }
            Long orderId = createResp.getData();

            // Step 9: 购物车模式 → 删除购物车记录
            if (hasCart) {
                mallCartMapper.deleteByIdsAndUserId(reqVO.getCartIds(), userId);
            }

            // Step 10: 返回 OrderCreateVO
            OrderCreateVO result = new OrderCreateVO();
            result.setOrderId(orderId);
            result.setOrderNo(orderNo);
            result.setPayAmount(payAmount);
            result.setAutoCancelTime(innerReq.getAutoCancelTime());
            return result;

        } catch (BusinessException e) {
            // 如果已扣减的库存需要回滚（加锁失败或后续步骤失败时恢复）
            restoreDeductedStock(lockedSkuIds, skuQuantityMap);
            throw e;
        } catch (Exception e) {
            log.error("[OrderServiceImpl] createOrder 异常", e);
            restoreDeductedStock(lockedSkuIds, skuQuantityMap);
            throw BusinessException.of(50001, "创建订单失败：" + e.getMessage());
        } finally {
            // 释放所有已获取的锁
            for (RLock lock : acquiredLocks) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception ex) {
                    log.warn("[OrderServiceImpl] unlock 异常", ex);
                }
            }
        }
    }

    /**
     * 恢复已扣减的库存（异常回滚）
     */
    private void restoreDeductedStock(List<Long> lockedSkuIds, Map<Long, Integer> skuQuantityMap) {
        for (Long skuId : lockedSkuIds) {
            try {
                Integer qty = skuQuantityMap.get(skuId);
                if (qty != null) {
                    stockFeignClient.restoreStock(new StockRestoreVO(skuId, qty));
                }
            } catch (Exception ex) {
                log.error("[OrderServiceImpl] 恢复库存失败 skuId={}", skuId, ex);
            }
        }
    }

    /**
     * 生成订单号：yyyyMMddHHmmss + userId末4位 + 4位随机数
     */
    private String generateOrderNo(Long userId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String userSuffix = String.format("%04d", userId % 10000);
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        return dateStr + userSuffix + randomSuffix;
    }

    // =========================================================
    // C-03: GET /app/order — 订单列表
    // =========================================================

    @Override
    public IPage<OrderListVO> listOrders(Long userId, Integer status, Integer page, Integer pageSize) {
        R<IPage<OrderInfoVO>> resp = orderFeignClient.pageOrders(userId, status, page, pageSize);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(50001, "查询订单列表失败，请稍后重试");
        }
        IPage<OrderInfoVO> rawPage = resp.getData();

        // 转换为 OrderListVO 分页对象
        IPage<OrderListVO> resultPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        List<OrderListVO> records = rawPage.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    private OrderListVO convertToListVO(OrderInfoVO orderInfoVO) {
        OrderListVO vo = new OrderListVO();
        vo.setOrderId(orderInfoVO.getId());
        vo.setOrderNo(orderInfoVO.getOrderNo());
        vo.setStatus(orderInfoVO.getStatus());
        vo.setStatusDesc(getStatusDesc(orderInfoVO.getStatus()));
        vo.setPayAmount(orderInfoVO.getPayAmount());
        vo.setCreateTime(orderInfoVO.getCreateTime());
        vo.setAutoCancelTime(orderInfoVO.getAutoCancelTime());

        // 第一个商品简略信息
        List<OrderInfoVO.OrderItemInfoVO> items = orderInfoVO.getItems();
        if (items != null && !items.isEmpty()) {
            OrderInfoVO.OrderItemInfoVO first = items.get(0);
            OrderItemBriefVO brief = new OrderItemBriefVO();
            brief.setSpuName(first.getSpuName());
            brief.setSkuSpec(first.getSkuSpec());
            brief.setSkuImage(first.getSkuImage());
            brief.setPrice(first.getPrice());
            brief.setQuantity(first.getQuantity());
            vo.setFirstItem(brief);
            vo.setItemCount(items.size());
        } else {
            vo.setItemCount(0);
        }
        return vo;
    }

    // =========================================================
    // C-04: GET /app/order/{orderId} — 订单详情
    // =========================================================

    @Override
    public OrderDetailVO getOrderDetail(Long orderId, Long userId) {
        OrderInfoVO orderInfoVO = fetchAndValidateOrder(orderId, userId);
        return convertToDetailVO(orderInfoVO);
    }

    /**
     * 查询订单并校验归属，防越权（抛40016）
     */
    private OrderInfoVO fetchAndValidateOrder(Long orderId, Long userId) {
        R<OrderInfoVO> resp = orderFeignClient.getOrder(orderId);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(40015, "订单不存在");
        }
        OrderInfoVO orderInfoVO = resp.getData();
        if (!userId.equals(orderInfoVO.getUserId())) {
            throw BusinessException.of(40016, "无权查看该订单");
        }
        return orderInfoVO;
    }

    private OrderDetailVO convertToDetailVO(OrderInfoVO info) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(info.getId());
        vo.setOrderNo(info.getOrderNo());
        vo.setStatus(info.getStatus());
        vo.setStatusDesc(getStatusDesc(info.getStatus()));
        vo.setRemark(info.getRemark());
        vo.setCancelReason(info.getCancelReason());
        vo.setCreateTime(info.getCreateTime());
        vo.setAutoCancelTime(info.getAutoCancelTime());
        vo.setPayTime(info.getPayTime());
        vo.setShipTime(info.getShipTime());
        vo.setReceiveTime(info.getReceiveTime());
        vo.setCancelTime(info.getCancelTime());
        vo.setTotalAmount(info.getTotalAmount());
        vo.setFreightAmount(info.getFreightAmount());
        vo.setDiscountAmount(info.getDiscountAmount());
        vo.setPayAmount(info.getPayAmount());
        vo.setReceiverName(info.getReceiverName());
        vo.setReceiverPhone(info.getReceiverPhone());
        vo.setReceiverAddress(info.getReceiverAddress());
        vo.setExpressCompany(info.getExpressCompany());
        vo.setExpressNo(info.getExpressNo());

        if (info.getItems() != null) {
            List<OrderItemVO> itemVOs = info.getItems().stream().map(i -> {
                OrderItemVO item = new OrderItemVO();
                item.setId(i.getId());
                item.setSpuId(i.getSpuId());
                item.setSkuId(i.getSkuId());
                item.setSpuName(i.getSpuName());
                item.setSkuSpec(i.getSkuSpec());
                item.setSkuImage(i.getSkuImage());
                item.setPrice(i.getPrice());
                item.setQuantity(i.getQuantity());
                item.setTotalAmount(i.getTotalAmount());
                return item;
            }).collect(Collectors.toList());
            vo.setItems(itemVOs);
        }
        return vo;
    }

    // =========================================================
    // C-05: PUT /app/order/{orderId}/cancel — 取消订单
    // =========================================================

    @Override
    public void cancelOrder(Long orderId, Long userId) {
        // 查询并校验归属
        OrderInfoVO orderInfoVO = fetchAndValidateOrder(orderId, userId);

        // 校验状态：只有 status=0（待付款）才能取消
        if (!Integer.valueOf(0).equals(orderInfoVO.getStatus())) {
            throw BusinessException.of(40017, "仅待付款订单可取消");
        }

        // Redisson 分布式锁防重
        String lockKey = "lock:order:cancel:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.of(50001, "系统繁忙，请稍后重试");
        }
        if (!locked) {
            throw BusinessException.of(50001, "操作频繁，请稍后重试");
        }

        try {
            // 再次查询确认状态（防并发重复取消）
            R<OrderInfoVO> latestResp = orderFeignClient.getOrder(orderId);
            if (latestResp == null || latestResp.getCode() != 200 || latestResp.getData() == null) {
                throw BusinessException.of(40015, "订单不存在");
            }
            OrderInfoVO latest = latestResp.getData();
            if (!Integer.valueOf(0).equals(latest.getStatus())) {
                throw BusinessException.of(40017, "订单状态已变更，无法取消");
            }

            // Feign 更新 status=4（已取消）
            OrderStatusUpdateVO updateVO = new OrderStatusUpdateVO();
            updateVO.setStatus(4);
            updateVO.setCancelTime(LocalDateTime.now());
            updateVO.setCancelReason("用户取消");
            R<Void> updateResp = orderFeignClient.updateOrderStatus(orderId, updateVO);
            if (updateResp == null || updateResp.getCode() != 200) {
                throw BusinessException.of(50001, "取消订单失败，请稍后重试");
            }

            // Feign 批量恢复 SKU 库存
            if (orderInfoVO.getItems() != null) {
                for (OrderInfoVO.OrderItemInfoVO item : orderInfoVO.getItems()) {
                    try {
                        stockFeignClient.restoreStock(new StockRestoreVO(item.getSkuId(), item.getQuantity()));
                    } catch (Exception ex) {
                        log.error("[cancelOrder] 恢复库存失败 skuId={}", item.getSkuId(), ex);
                    }
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // =========================================================
    // C-06: PUT /app/order/{orderId}/receive — 确认收货
    // =========================================================

    @Override
    public void confirmReceive(Long orderId, Long userId) {
        OrderInfoVO orderInfoVO = fetchAndValidateOrder(orderId, userId);

        // 校验状态：只有 status=2（待收货）才能确认收货
        if (!Integer.valueOf(2).equals(orderInfoVO.getStatus())) {
            throw BusinessException.of(40019, "仅待收货订单可确认收货");
        }

        OrderStatusUpdateVO updateVO = new OrderStatusUpdateVO();
        updateVO.setStatus(3);
        updateVO.setReceiveTime(LocalDateTime.now());
        R<Void> resp = orderFeignClient.updateOrderStatus(orderId, updateVO);
        if (resp == null || resp.getCode() != 200) {
            throw BusinessException.of(50001, "确认收货失败，请稍后重试");
        }
    }

    // =========================================================
    // 工具方法
    // =========================================================

    /**
     * 订单状态描述
     */
    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待付款";
            case 1: return "待发货";
            case 2: return "待收货";
            case 3: return "已完成";
            case 4: return "已取消";
            case 5: return "已退款";
            default: return "未知";
        }
    }
}
