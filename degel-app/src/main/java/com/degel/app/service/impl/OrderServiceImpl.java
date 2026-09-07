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
import com.degel.app.util.CouponAllocator;
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

    @org.springframework.beans.factory.annotation.Value("${degel.app.file-base-url}")
    private String fileBaseUrl;

    private final OrderFeignClient orderFeignClient;
    private final ProductFeignClient productFeignClient;
    private final StockFeignClient stockFeignClient;
    private final com.degel.app.feign.MarketingFeignClient marketingFeignClient;
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
        // 已锁券的子单号（catch 补偿 unlock 用）
        List<String> lockedCouponOrderNos = new ArrayList<>();
        // 已落库的子单 id（catch 补偿取消用——任一子单失败则全部回滚）
        List<Long> createdOrderIds = new ArrayList<>();
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

            // Step 6: 组明细并按店铺分组（SKU 已带 shopId；LinkedHashMap 保序 → 子单顺序稳定）
            Map<Long, List<OrderCreateInnerReqVO.OrderItemInnerVO>> shopItems = new LinkedHashMap<>();
            for (Long skuId : skuIds) {
                ProductSkuVO sku = skuMap.get(skuId);
                Integer qty = skuQuantityMap.get(skuId);
                BigDecimal itemTotal = sku.getPrice().multiply(BigDecimal.valueOf(qty));

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
                shopItems.computeIfAbsent(sku.getShopId() != null ? sku.getShopId() : 0L,
                        k -> new ArrayList<>()).add(item);
            }

            // Step 6.5: 券绑定解析——couponBindings 优先（每子单独立选券，店铺券绑对应店）；
            // 兼容老 couponId 字段：单店挂唯一子单，多店挂金额最大子单
            Map<Long, Long> shopCouponMap = new HashMap<>();
            if (reqVO.getCouponBindings() != null) {
                for (OrderCreateReqVO.CouponBinding b : reqVO.getCouponBindings()) {
                    if (b.getShopId() != null && b.getCouponId() != null) {
                        shopCouponMap.put(b.getShopId(), b.getCouponId());
                    }
                }
            } else if (reqVO.getCouponId() != null) {
                shopCouponMap.put(resolveLegacyCouponShop(shopItems), reqVO.getCouponId());
            }

            // Step 7: 逐店建子单（每子单独立 orderNo/金额/券，失败整体回滚见 catch）
            String fullAddress = address.getProvince() + address.getCity()
                    + address.getDistrict() + address.getDetail();
            List<OrderCreateVO.SubOrder> subOrders = new ArrayList<>();
            BigDecimal totalPayAmount = BigDecimal.ZERO;
            LocalDateTime autoCancelTime = LocalDateTime.now().plusMinutes(30);

            for (Map.Entry<Long, List<OrderCreateInnerReqVO.OrderItemInnerVO>> entry : shopItems.entrySet()) {
                Long shopId = entry.getKey();
                List<OrderCreateInnerReqVO.OrderItemInnerVO> itemList = entry.getValue();
                BigDecimal subTotal = BigDecimal.ZERO;
                for (OrderCreateInnerReqVO.OrderItemInnerVO item : itemList) {
                    subTotal = subTotal.add(item.getTotalAmount());
                }
                BigDecimal freightAmount = BigDecimal.ZERO;
                BigDecimal discountAmount = BigDecimal.ZERO;
                String orderNo = generateOrderNo(userId);

                // 锁券（该子单绑定的券；失败直接阻断下单，不能静默原价下单）
                CouponLockRespVO couponLock = null;
                Long boundCouponId = shopCouponMap.get(shopId);
                if (boundCouponId != null) {
                    CouponLockReqVO lockReq = new CouponLockReqVO();
                    lockReq.setUserCouponId(boundCouponId);
                    lockReq.setUserId(userId);
                    lockReq.setOrderNo(orderNo);
                    lockReq.setTotalAmount(subTotal);
                    // 子单归属店铺：店铺券校验本店的依据（平台券忽略）
                    lockReq.setShopId(shopId);
                    R<CouponLockRespVO> lockR = marketingFeignClient.lock(lockReq);
                    if (lockR == null || lockR.getCode() != 200 || lockR.getData() == null) {
                        throw BusinessException.of(40014,
                                lockR != null && lockR.getMsg() != null ? lockR.getMsg() : "优惠券不可用");
                    }
                    couponLock = lockR.getData();
                    lockedCouponOrderNos.add(orderNo);
                    discountAmount = couponLock.getDiscountAmount();

                    // 分摊到子单明细（Σ分摊 = 优惠额，尾差落最大项；退款按 totalAmount - couponDiscount 取数）
                    List<BigDecimal> itemTotals = new ArrayList<>();
                    for (OrderCreateInnerReqVO.OrderItemInnerVO item : itemList) {
                        itemTotals.add(item.getTotalAmount());
                    }
                    List<BigDecimal> shares = CouponAllocator.allocate(itemTotals, discountAmount);
                    for (int i = 0; i < itemList.size(); i++) {
                        itemList.get(i).setCouponDiscount(shares.get(i));
                    }
                }
                BigDecimal payAmount = subTotal.add(freightAmount).subtract(discountAmount);
                // 优惠后不允许负数订单（lock 已按订单额封顶，此处防御性兜底）
                if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
                    throw BusinessException.of(40015, "订单金额异常");
                }

                // Step 8: 构建内部创建请求，调用 degel-order（shopId=真实归属，替换 MVP 写死值）
                OrderCreateInnerReqVO innerReq = new OrderCreateInnerReqVO();
                innerReq.setUserId(userId);
                innerReq.setShopId(shopId);
                innerReq.setOrderNo(orderNo);
                innerReq.setTotalAmount(subTotal);
                innerReq.setFreightAmount(freightAmount);
                innerReq.setDiscountAmount(discountAmount);
                innerReq.setPayAmount(payAmount);
                if (couponLock != null) {
                    innerReq.setCouponId(boundCouponId);
                    innerReq.setPlatformSubsidy(couponLock.getPlatformAmount());
                    innerReq.setShopSubsidy(couponLock.getShopAmount() != null ? couponLock.getShopAmount() : BigDecimal.ZERO);
                }
                innerReq.setReceiverName(address.getName());
                innerReq.setReceiverPhone(address.getPhone());
                innerReq.setReceiverAddress(fullAddress);
                innerReq.setRemark(reqVO.getRemark());
                innerReq.setAutoCancelTime(autoCancelTime);
                innerReq.setItems(itemList);

                R<Long> createResp = orderFeignClient.createOrder(innerReq);
                if (createResp == null || createResp.getCode() != 200 || createResp.getData() == null) {
                    throw BusinessException.of(50001, "创建订单失败，请稍后重试");
                }
                createdOrderIds.add(createResp.getData());

                OrderCreateVO.SubOrder sub = new OrderCreateVO.SubOrder();
                sub.setOrderId(createResp.getData());
                sub.setOrderNo(orderNo);
                sub.setShopId(shopId);
                sub.setPayAmount(payAmount);
                sub.setAutoCancelTime(autoCancelTime);
                subOrders.add(sub);
                totalPayAmount = totalPayAmount.add(payAmount);
            }

            // Step 9: 购物车模式 → 删除购物车记录（全部子单成功后一次删，失败回滚时不删）
            if (hasCart) {
                mallCartMapper.deleteByIdsAndUserId(reqVO.getCartIds(), userId);
            }

            // Step 10: 返回（兼容字段=首单，新前端用 orders/totalPayAmount）
            OrderCreateVO result = new OrderCreateVO();
            result.setOrderId(subOrders.get(0).getOrderId());
            result.setOrderNo(subOrders.get(0).getOrderNo());
            result.setPayAmount(subOrders.get(0).getPayAmount());
            result.setAutoCancelTime(autoCancelTime);
            result.setOrders(subOrders);
            result.setTotalPayAmount(totalPayAmount);
            return result;

        } catch (BusinessException e) {
            // 拆单整体回滚：恢复库存 + 取消已落库子单 + 解锁已锁券
            rollbackSplitOrder(lockedSkuIds, skuQuantityMap, createdOrderIds, lockedCouponOrderNos);
            throw e;
        } catch (Exception e) {
            log.error("[OrderServiceImpl] createOrder 异常", e);
            rollbackSplitOrder(lockedSkuIds, skuQuantityMap, createdOrderIds, lockedCouponOrderNos);
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
     * 兼容老 couponId 字段：单店挂唯一子单，多店挂金额最大子单（正式口径是 couponBindings）
     */
    private Long resolveLegacyCouponShop(Map<Long, List<OrderCreateInnerReqVO.OrderItemInnerVO>> shopItems) {
        if (shopItems.size() == 1) {
            return shopItems.keySet().iterator().next();
        }
        Long maxShop = null;
        BigDecimal max = null;
        for (Map.Entry<Long, List<OrderCreateInnerReqVO.OrderItemInnerVO>> e : shopItems.entrySet()) {
            BigDecimal t = BigDecimal.ZERO;
            for (OrderCreateInnerReqVO.OrderItemInnerVO item : e.getValue()) {
                t = t.add(item.getTotalAmount());
            }
            if (max == null || t.compareTo(max) > 0) {
                max = t;
                maxShop = e.getKey();
            }
        }
        return maxShop;
    }

    /**
     * 拆单整体回滚：恢复库存 + 取消已落库子单（status=4）+ 解锁已锁券。
     * 子单落库到补偿取消在同一请求内完成，用户不可能同时支付未返回的订单，无并发窗口。
     * 各步骤独立 try-catch best-effort：失败仅记日志（券由 marketing 60 分钟兜底任务释放；
     * 已取消子单若残留为待付款，会被现有超时取消任务收敛）。
     */
    private void rollbackSplitOrder(List<Long> lockedSkuIds, Map<Long, Integer> skuQuantityMap,
                                    List<Long> createdOrderIds, List<String> lockedCouponOrderNos) {
        restoreDeductedStock(lockedSkuIds, skuQuantityMap);
        for (Long orderId : createdOrderIds) {
            try {
                OrderStatusUpdateVO cancel = new OrderStatusUpdateVO();
                cancel.setStatus(4);
                cancel.setCancelTime(LocalDateTime.now());
                cancel.setCancelReason("拆单回滚");
                orderFeignClient.updateOrderStatus(orderId, cancel);
            } catch (Exception ex) {
                log.error("[rollbackSplitOrder] 取消已落库子单失败 orderId={}（由超时取消任务收敛）", orderId, ex);
            }
        }
        for (String orderNo : lockedCouponOrderNos) {
            releaseLockedCoupon(orderNo);
        }
    }

    /**
     * 释放已锁定的券（下单失败补偿；幂等，unlock 失败由 marketing 60 分钟僵尸锁任务兜底）
     */
    private void releaseLockedCoupon(String lockedCouponOrderNo) {
        if (lockedCouponOrderNo == null) {
            return;
        }
        try {
            CouponOrderRefVO ref = new CouponOrderRefVO();
            ref.setOrderNo(lockedCouponOrderNo);
            marketingFeignClient.unlock(ref);
        } catch (Exception ex) {
            log.error("[OrderServiceImpl] 券补偿解锁失败 orderNo={}（等 marketing 兜底任务）", lockedCouponOrderNo, ex);
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
        R<Page<OrderInfoVO>> resp = orderFeignClient.pageOrders(userId, status, page, pageSize);
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
            brief.setSkuImage(fileUrl(first.getSkuImage()));
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
        vo.setPayLogId(info.getPayLogId());
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
                item.setSkuImage(fileUrl(i.getSkuImage()));
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

            // 释放订单占用的优惠券（幂等；失败由 marketing 60 分钟僵尸锁任务兜底）
            if (orderInfoVO.getCouponId() != null) {
                releaseLockedCoupon(orderInfoVO.getOrderNo());
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

    /** 图片相对路径拼完整 URL（存储为裸 key，展示时补 file-base-url 前缀） */
    private String fileUrl(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return fileBaseUrl + "/file/view/" + key;
    }
}
