package com.degel.app.service.impl;

import com.degel.app.entity.MallAddress;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.feign.SeckillFeignClient;
import com.degel.app.feign.StockFeignClient;
import com.degel.app.mapper.MallAddressMapper;
import com.degel.app.service.SeckillService;
import com.degel.app.vo.OrderCreateVO;
import com.degel.app.vo.OrderInfoVO;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.app.vo.SeckillProductVO;
import com.degel.app.vo.SeckillReserveVO;
import com.degel.app.vo.SeckillSessionVO;
import com.degel.app.vo.dto.OrderCreateInnerReqVO;
import com.degel.app.vo.dto.SeckillOrderReqVO;
import com.degel.app.vo.dto.SeckillProductDTO;
import com.degel.app.vo.dto.SeckillReserveReqVO;
import com.degel.app.vo.dto.SeckillSessionDTO;
import com.degel.app.vo.dto.StockDeductVO;
import com.degel.app.vo.dto.StockRestoreVO;
import com.degel.common.core.R;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 秒杀服务实现（Lua 预扣 + 两段式下单）。
 *
 * <p>Redis key 布局（全部走 {@link StringRedisTemplate}；仅 seckill:sessions 静态缓存走 redisTemplate）：
 * <ul>
 *   <li>seckill:cfg:{sessionId}:{skuId}    HASH  startMs/endMs/limit（预热写入）</li>
 *   <li>seckill:stock:{sessionId}:{skuId}   STRING 剩余秒杀库存（Lua 原子 DECR/INCR）</li>
 *   <li>seckill:bought:{sessionId}:{skuId}  HASH  userId -> 已购数量（限购依据）</li>
 *   <li>seckill:hold:{token}                STRING 资格值 userId:sessionId:skuId，EX 90s</li>
 *   <li>seckill:hold:zset                   ZSET  member=token:userId:sessionId:skuId score=过期时刻 ms（兜底清理；
 *       回滚上下文冗余进 member，hold key 被 TTL 删除后清理任务仍能回滚）</li>
 *   <li>seckill:warmed:{sessionId}          STRING 预热幂等标记，过期=end+24h</li>
 *   <li>seckill:sessions                    静态场次缓存 30s（redisTemplate，余量不进缓存）</li>
 * </ul>
 *
 * <p>补偿矩阵（createOrder，地址校验在扣库存之前）：
 * <ul>
 *   <li>地址不存在        → rollback Lua（DB 库存未动，无需 restore）</li>
 *   <li>扣库存失败        → rollback Lua（Redis 余量/已购回滚）</li>
 *   <li>建单失败/任意异常 → 先按 orderNo 反查确认未落库，再 restoreStock + rollback Lua</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final String CACHE_SESSIONS = "seckill:sessions";
    private static final long CACHE_TTL_SECONDS = 30;
    private static final String CFG_PREFIX = "seckill:cfg:";
    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String BOUGHT_PREFIX = "seckill:bought:";
    private static final String HOLD_PREFIX = "seckill:hold:";
    private static final String HOLD_ZSET_KEY = "seckill:hold:zset";
    private static final String WARMED_PREFIX = "seckill:warmed:";
    /** 资格有效期（秒），与 Lua SET EX 一致 */
    private static final int HOLD_TTL_SECONDS = 90;
    /** 预热数据统一过期：场次结束 +24h */
    private static final long EXPIRE_AFTER_END_MS = 24L * 3600 * 1000;
    /** 未预热语义值（remaining 前端按"未开始"展示） */
    private static final long NOT_WARMED = -1L;

    /** marketing 侧时间格式（其服务器本地时区序列化，同机部署无差） */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Redis JSON 反序列化回来是 List&lt;LinkedHashMap&gt;，需 convertValue 还原（同 BannerServiceImpl） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SeckillFeignClient seckillFeignClient;
    private final ProductFeignClient productFeignClient;
    private final StockFeignClient stockFeignClient;
    private final OrderFeignClient orderFeignClient;
    private final MallAddressMapper mallAddressMapper;
    /** Lua/stock/hold/配置全字符串操作；静态场次缓存走 redisTemplate */
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // 三个 DefaultRedisScript<Long> Bean 按名注入（SeckillScriptConfig）
    private final DefaultRedisScript<Long> seckillReserveScript;
    private final DefaultRedisScript<Long> seckillConsumeHoldScript;
    private final DefaultRedisScript<Long> seckillRollbackScript;

    @Value("${degel.app.file-base-url:http://localhost:9999}")
    private String fileBaseUrl;

    // =========================================================
    // 场次列表（静态缓存 30s + 实时余量）
    // =========================================================

    @Override
    public List<SeckillSessionVO> listSessions() {
        // 1. 静态缓存命中（fail/空不写缓存，命中必非空）
        Object cached = redisTemplate.opsForValue().get(CACHE_SESSIONS);
        if (cached != null) {
            List<SeckillSessionVO> sessions = OBJECT_MAPPER.convertValue(
                    cached, new TypeReference<List<SeckillSessionVO>>() {});
            fillRealtime(sessions);
            return sessions;
        }

        // 2. Cache miss：Feign 查场次；营销降级/空数据不写缓存，返回空列表（秒杀位不阻塞首页）
        List<SeckillSessionDTO> dtos;
        try {
            R<List<SeckillSessionDTO>> resp = seckillFeignClient.current();
            if (resp == null || resp.getCode() != 200 || resp.getData() == null || resp.getData().isEmpty()) {
                return Collections.emptyList();
            }
            dtos = resp.getData();
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] 场次列表 Feign 调用失败", e);
            return Collections.emptyList();
        }

        List<SeckillSessionVO> sessions = convertSessions(dtos);
        // 3. 写静态缓存（不含实时余量；序列化即刻快照，后续 fillRealtime 原地填充不影响缓存）
        redisTemplate.opsForValue().set(CACHE_SESSIONS, sessions, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        fillRealtime(sessions);
        return sessions;
    }

    /** marketing DTO → C 端 VO（时间 String→epoch ms，status 延后现算）+ 实时补商品展示信息 */
    private List<SeckillSessionVO> convertSessions(List<SeckillSessionDTO> dtos) {
        List<SeckillProductDTO> allProducts = dtos.stream()
                .filter(s -> s.getProducts() != null)
                .flatMap(s -> s.getProducts().stream())
                .collect(Collectors.toList());

        Map<Long, ProductSkuVO> skuMap = batchGetSkuMap(allProducts.stream()
                .map(SeckillProductDTO::getSkuId).collect(Collectors.toList()));
        Map<Long, ProductSpuVO> spuMap = batchGetSpuMap(allProducts.stream()
                .map(SeckillProductDTO::getSpuId).distinct().collect(Collectors.toList()));

        List<SeckillSessionVO> result = new ArrayList<>();
        for (SeckillSessionDTO dto : dtos) {
            SeckillSessionVO vo = new SeckillSessionVO();
            vo.setId(String.valueOf(dto.getId()));
            vo.setName(dto.getName());
            vo.setStartAt(toEpochMs(dto.getStartTime()));
            vo.setEndAt(toEpochMs(dto.getEndTime()));
            vo.setSort(dto.getSort());
            List<SeckillProductVO> products = new ArrayList<>();
            if (dto.getProducts() != null) {
                for (SeckillProductDTO p : dto.getProducts()) {
                    products.add(convertProduct(p, skuMap.get(p.getSkuId()), spuMap.get(p.getSpuId())));
                }
            }
            vo.setProducts(products);
            result.add(vo);
        }
        return result;
    }

    /** 单商品 VO 组装（实时补 skuName/originalPrice/mainImage） */
    private SeckillProductVO convertProduct(SeckillProductDTO p, ProductSkuVO sku, ProductSpuVO spu) {
        SeckillProductVO vo = new SeckillProductVO();
        vo.setId(String.valueOf(p.getId()));
        vo.setSessionId(String.valueOf(p.getSessionId()));
        vo.setSpuId(String.valueOf(p.getSpuId()));
        vo.setSkuId(String.valueOf(p.getSkuId()));
        vo.setSeckillPrice(p.getSeckillPrice());
        vo.setTotalStock(p.getSeckillStock());
        vo.setPerLimit(p.getPerLimit() == null ? 1 : p.getPerLimit());
        if (sku != null) {
            vo.setSkuName(sku.getSkuName());
            vo.setOriginalPrice(sku.getOriginalPrice() != null ? sku.getOriginalPrice() : sku.getPrice());
        }
        if (spu != null && spu.getMainImage() != null) {
            vo.setMainImage(fileUrl(spu.getMainImage()));
        } else if (sku != null) {
            vo.setMainImage(fileUrl(sku.getImage()));
        }
        return vo;
    }

    /** 原地填充实时数据：status 现算 + remaining/percent（MGET stock key，未预热填 -1） */
    private void fillRealtime(List<SeckillSessionVO> sessions) {
        long now = System.currentTimeMillis();
        // 收集 stock key（与产品列表同序，MGET 结果按下标对齐）
        List<String> stockKeys = new ArrayList<>();
        for (SeckillSessionVO s : sessions) {
            // status 现算：0=未开始 1=进行中 2=已结束（缓存 30s 内状态翻转也即时纠正）
            s.setStatus(now < s.getStartAt() ? 0 : (now >= s.getEndAt() ? 2 : 1));
            if (s.getProducts() != null) {
                for (SeckillProductVO p : s.getProducts()) {
                    stockKeys.add(STOCK_PREFIX + p.getSessionId() + ":" + p.getSkuId());
                }
            }
        }
        if (stockKeys.isEmpty()) {
            return;
        }
        List<String> stocks;
        try {
            stocks = stringRedisTemplate.opsForValue().multiGet(stockKeys);
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] 余量 MGET 失败", e);
            return;
        }
        if (stocks == null) {
            return;
        }
        int idx = 0;
        for (SeckillSessionVO s : sessions) {
            if (s.getProducts() == null) {
                continue;
            }
            for (SeckillProductVO p : s.getProducts()) {
                String raw = idx < stocks.size() ? stocks.get(idx++) : null;
                Long remaining;
                if (raw == null) {
                    remaining = NOT_WARMED; // 未预热（key 不存在）→ 前端按未开始展示
                } else {
                    try {
                        remaining = Long.parseLong(raw);
                    } catch (NumberFormatException ex) {
                        remaining = NOT_WARMED;
                    }
                }
                p.setRemaining(remaining);
                p.setPercent(calcPercent(p.getTotalStock(), remaining));
            }
        }
    }

    /** 已抢百分比（向上取整 0-100）；未预热/库存非法返回 null */
    private Integer calcPercent(Integer totalStock, Long remaining) {
        if (remaining == null || remaining < 0 || totalStock == null || totalStock <= 0) {
            return null;
        }
        long sold = Math.max(totalStock - remaining, 0);
        int percent = (int) Math.ceil(sold * 100.0 / totalStock);
        return Math.min(Math.max(percent, 0), 100);
    }

    // =========================================================
    // 商品详情（确认页，不缓存）
    // =========================================================

    @Override
    public SeckillProductVO getProduct(Long sessionId, Long skuId) {
        SeckillProductDTO detail = fetchDetail(sessionId, skuId);
        ProductSkuVO sku = batchGetSkuMap(Collections.singletonList(skuId)).get(skuId);
        ProductSpuVO spu = null;
        if (sku != null && sku.getSpuId() != null) {
            spu = batchGetSpuMap(Collections.singletonList(sku.getSpuId())).get(sku.getSpuId());
        }
        SeckillProductVO vo = convertProduct(detail, sku, spu);
        // 实时余量
        try {
            String raw = stringRedisTemplate.opsForValue().get(STOCK_PREFIX + sessionId + ":" + skuId);
            long remaining = raw == null ? NOT_WARMED : Long.parseLong(raw);
            vo.setRemaining(remaining);
            vo.setPercent(calcPercent(vo.getTotalStock(), remaining));
        } catch (Exception e) {
            log.warn("[SeckillServiceImpl] 商品余量读取失败 sessionId={} skuId={}", sessionId, skuId, e);
        }
        return vo;
    }

    // =========================================================
    // 抢购预扣（Lua）
    // =========================================================

    @Override
    public SeckillReserveVO reserve(SeckillReserveReqVO reqVO, Long userId) {
        Long sessionId = reqVO.getSessionId();
        Long skuId = reqVO.getSkuId();
        // 纯 Redis 热路径：不做前置 detail Feign 校验（避免对 marketing 的逐请求串行依赖）。
        // 场次停用安全边界：停用后已预热的 cfg 到期前 reserve 仍可通过（预扣库存最多被占 90s hold），
        // 但 createOrder Step 2 的 fetchDetail 会以 40020 拦截，最终不会成单。

        String token = UUID.randomUUID().toString().replace("-", "");
        Long result = executeReserve(sessionId, skuId, userId, token);
        // 未预热 → 懒预热后重试一次
        if (result != null && result == -1) {
            try {
                warmSession(sessionId);
            } catch (Exception e) {
                log.error("[SeckillServiceImpl] 懒预热失败 sessionId={}", sessionId, e);
            }
            result = executeReserve(sessionId, skuId, userId, token);
        }
        if (result == null) {
            throw BusinessException.of(50001, "系统繁忙，请稍后重试");
        }
        switch (result.intValue()) {
            case 0:
                SeckillReserveVO vo = new SeckillReserveVO();
                vo.setToken(token);
                vo.setExpireSeconds(HOLD_TTL_SECONDS);
                return vo;
            case -1:
                throw BusinessException.of(50001, "活动未就绪，请稍后重试");
            case 1:
                throw BusinessException.of(40021, "活动未开始或已结束");
            case 2:
                throw BusinessException.of(40022, "已抢光");
            case 3:
                throw BusinessException.of(40023, "超出限购数量");
            default:
                throw BusinessException.of(50001, "系统繁忙，请稍后重试");
        }
    }

    /**
     * Lua 预扣调用。KEYS 顺序与 seckill_reserve.lua 一一对应：
     * cfg/stock/bought/hold:{token}/hold:zset；ARGV: nowMs/userId/token(占位)/holdTtlSec/holdValue/zsetMember
     */
    private Long executeReserve(Long sessionId, Long skuId, Long userId, String token) {
        List<String> keys = Arrays.asList(
                CFG_PREFIX + sessionId + ":" + skuId,
                STOCK_PREFIX + sessionId + ":" + skuId,
                BOUGHT_PREFIX + sessionId + ":" + skuId,
                HOLD_PREFIX + token,
                HOLD_ZSET_KEY);
        return stringRedisTemplate.execute(seckillReserveScript, keys,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(userId),
                token,
                String.valueOf(HOLD_TTL_SECONDS),
                holdValue(userId, sessionId, skuId),
                zsetMember(token, userId, sessionId, skuId));
    }

    // =========================================================
    // 两段式下单（核销资格 → 扣库存 → 建单，失败全补偿）
    // =========================================================

    @Override
    public OrderCreateVO createOrder(SeckillOrderReqVO reqVO, Long userId) {
        String token = reqVO.getToken();

        // Step 1: 读 hold 解析归属（请求只带 token，sessionId/skuId 从资格值还原）
        String holdValue = stringRedisTemplate.opsForValue().get(HOLD_PREFIX + token);
        if (holdValue == null) {
            throw BusinessException.of(40024, "抢购资格已过期，请重新抢购");
        }
        Long sessionId;
        Long skuId;
        try {
            String[] parts = holdValue.split(":");
            if (parts.length != 3 || !String.valueOf(userId).equals(parts[0])) {
                throw BusinessException.of(40025, "资格校验失败");
            }
            sessionId = Long.valueOf(parts[1]);
            skuId = Long.valueOf(parts[2]);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.of(40025, "资格校验失败");
        }

        // Step 2: 配置校验（场次停用/商品移除 → 40020；秒杀价后续做订单快照）
        SeckillProductDTO detail = fetchDetail(sessionId, skuId);

        // Step 3: 核销资格（先 consume；后续任一步失败统一 rollback，不重新持有 hold）
        Long consume = stringRedisTemplate.execute(seckillConsumeHoldScript,
                Arrays.asList(HOLD_PREFIX + token, HOLD_ZSET_KEY), holdValue,
                zsetMember(token, userId, sessionId, skuId));
        if (consume == null || consume == 1) {
            throw BusinessException.of(40024, "抢购资格已过期，请重新抢购");
        }
        if (consume != 0) {
            throw BusinessException.of(40025, "资格校验失败");
        }

        boolean stockDeducted = false;
        try {
            // Step 4: 收货地址校验（同 OrderServiceImpl Step5，含归属校验）。
            // 放在扣库存之前：地址失败时 DB 库存未动，补偿只需 rollback Lua，无需 restoreStock
            MallAddress address = mallAddressMapper.selectOne(
                    new LambdaQueryWrapper<MallAddress>()
                            .eq(MallAddress::getId, reqVO.getAddressId())
                            .eq(MallAddress::getUserId, userId)
                            .eq(MallAddress::getDelFlag, 0));
            if (address == null) {
                throw BusinessException.of(40013, "收货地址不存在");
            }

            // Step 5: 扣 DB 库存（原子 SQL；失败=库存不足 → catch 统一 rollback）
            R<Boolean> deductResp = stockFeignClient.deductStock(new StockDeductVO(skuId, 1));
            if (deductResp == null || deductResp.getCode() != 200 || !Boolean.TRUE.equals(deductResp.getData())) {
                throw BusinessException.of(40022, "已抢光");
            }
            stockDeducted = true;

            // Step 6: 商品实时信息（快照 + shopId）
            ProductSkuVO sku = batchGetSkuMap(Collections.singletonList(skuId)).get(skuId);
            if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw BusinessException.of(40011, "商品已下架，无法下单");
            }
            ProductSpuVO spu = sku.getSpuId() != null
                    ? batchGetSpuMap(Collections.singletonList(sku.getSpuId())).get(sku.getSpuId()) : null;

            // Step 7: 构造内部建单请求（单店单商品，秒杀价快照，无券，运费/优惠为 0）
            BigDecimal seckillPrice = detail.getSeckillPrice();
            LocalDateTime autoCancelTime = LocalDateTime.now().plusMinutes(30);
            String orderNo = generateOrderNo(userId);

            OrderCreateInnerReqVO.OrderItemInnerVO item = new OrderCreateInnerReqVO.OrderItemInnerVO();
            item.setSpuId(sku.getSpuId());
            item.setSkuId(skuId);
            item.setSpuName(spu != null && spu.getName() != null ? spu.getName() : sku.getSpuName());
            item.setSkuSpec(sku.getSpecData());
            String image = spu != null && spu.getMainImage() != null ? spu.getMainImage() : sku.getImage();
            item.setSkuImage(fileUrl(image));
            item.setPrice(seckillPrice);
            item.setQuantity(1);
            item.setTotalAmount(seckillPrice);

            OrderCreateInnerReqVO innerReq = new OrderCreateInnerReqVO();
            innerReq.setUserId(userId);
            innerReq.setShopId(sku.getShopId() != null ? sku.getShopId() : 0L);
            innerReq.setOrderNo(orderNo);
            innerReq.setOrderType(1);
            innerReq.setTotalAmount(seckillPrice);
            innerReq.setFreightAmount(BigDecimal.ZERO);
            innerReq.setDiscountAmount(BigDecimal.ZERO);
            innerReq.setPayAmount(seckillPrice);
            innerReq.setReceiverName(address.getName());
            innerReq.setReceiverPhone(address.getPhone());
            innerReq.setReceiverAddress(address.getProvince() + address.getCity()
                    + address.getDistrict() + address.getDetail());
            innerReq.setAutoCancelTime(autoCancelTime);
            innerReq.setItems(Collections.singletonList(item));

            // Step 8: 建单（Feign 超时/降级 ≠ 落库失败，失败先按 orderNo 反查再决定是否补偿，见 createOrderWithRecheck）
            Long orderId = createOrderWithRecheck(innerReq);

            // Step 9: 返回（单子单，对齐 OrderServiceImpl Step10 组装）
            OrderCreateVO.SubOrder sub = new OrderCreateVO.SubOrder();
            sub.setOrderId(orderId);
            sub.setOrderNo(orderNo);
            sub.setShopId(innerReq.getShopId());
            sub.setPayAmount(seckillPrice);
            sub.setAutoCancelTime(autoCancelTime);

            OrderCreateVO result = new OrderCreateVO();
            result.setOrderId(sub.getOrderId());
            result.setOrderNo(orderNo);
            result.setPayAmount(seckillPrice);
            result.setAutoCancelTime(autoCancelTime);
            result.setOrders(Collections.singletonList(sub));
            result.setTotalPayAmount(seckillPrice);
            return result;
        } catch (BusinessException e) {
            compensateCreateOrder(stockDeducted, skuId, sessionId, userId);
            throw e;
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] 秒杀下单异常 token={}", token, e);
            compensateCreateOrder(stockDeducted, skuId, sessionId, userId);
            throw BusinessException.of(50001, "创建订单失败，请稍后重试");
        }
    }

    /**
     * 下单失败补偿：restoreStock（DB 库存，仅已扣减时）+ rollback Lua（Redis 余量/已购）。
     * 两步独立 best-effort：失败仅记日志——rollback 失败意味着该用户少一次购买机会且
     * Redis 余量少 1（活动结束 key 过期自动消失，无持久影响）。
     */
    private void compensateCreateOrder(boolean stockDeducted, Long skuId, Long sessionId, Long userId) {
        if (stockDeducted) {
            try {
                stockFeignClient.restoreStock(new StockRestoreVO(skuId, 1));
            } catch (Exception ex) {
                log.error("[SeckillServiceImpl] 补偿恢复 DB 库存失败 skuId={}（需人工订正）", skuId, ex);
            }
        }
        rollbackReserve(sessionId, skuId, userId);
    }

    /**
     * 建单调用 + 失败反查（防超卖关键路径）：orderFeignClient.createOrder 超时/降级时，
     * 订单可能实际已落库（请求已到达 degel-order，只是响应丢失）。此时若直接补偿
     * （restoreStock + rollback Lua）会把已成交订单的库存退回去 → 超卖。
     * 故建单失败先按 orderNo 反查（createInnerOrder 已按 orderNo 幂等）：
     * 存在 → 视为成功返回订单 id（不补偿，用户可支付）；不存在 → 抛 50001 交由外层 catch 正常补偿。
     */
    private Long createOrderWithRecheck(OrderCreateInnerReqVO innerReq) {
        String orderNo = innerReq.getOrderNo();
        R<Long> createResp;
        try {
            createResp = orderFeignClient.createOrder(innerReq);
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] 建单 Feign 调用异常 orderNo={}，反查订单是否已落库", orderNo, e);
            return recheckOrCreateFail(orderNo);
        }
        if (createResp == null || createResp.getCode() != 200 || createResp.getData() == null) {
            log.error("[SeckillServiceImpl] 建单调用失败 orderNo={} resp={}，反查订单是否已落库",
                    orderNo, createResp == null ? "null" : createResp.getMsg());
            return recheckOrCreateFail(orderNo);
        }
        return createResp.getData();
    }

    /** 反查订单：已落库 → 返回 orderId（不补偿，用户可支付）；未落库/反查失败 → 抛 50001（外层补偿） */
    private Long recheckOrCreateFail(String orderNo) {
        try {
            R<OrderInfoVO> resp = orderFeignClient.getOrderByNo(orderNo);
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                log.info("[SeckillServiceImpl] 建单调用失败但订单已落库，按成功返回 orderNo={} orderId={}",
                        orderNo, resp.getData().getId());
                return resp.getData().getId();
            }
        } catch (Exception e) {
            // 反查也失败（order 服务整体不可用）：无法证明已落库，按未落库处理走补偿；
            // 若实际已落库，靠 createInnerOrder 的 orderNo 幂等 + 数据对账兜底
            log.error("[SeckillServiceImpl] 反查订单失败 orderNo={}，按未落库补偿", orderNo, e);
        }
        throw BusinessException.of(50001, "创建订单失败，请稍后重试");
    }

    // =========================================================
    // 主动放弃资格
    // =========================================================

    @Override
    public void cancelReserve(String token, Long userId) {
        String holdValue;
        try {
            holdValue = stringRedisTemplate.opsForValue().get(HOLD_PREFIX + token);
            if (holdValue == null) {
                throw BusinessException.of(40024, "抢购资格已过期");
            }
            String[] parts = holdValue.split(":");
            if (parts.length != 3 || !String.valueOf(userId).equals(parts[0])) {
                throw BusinessException.of(40025, "资格校验失败");
            }
            Long sessionId = Long.valueOf(parts[1]);
            Long skuId = Long.valueOf(parts[2]);
            Long consume = stringRedisTemplate.execute(seckillConsumeHoldScript,
                    Arrays.asList(HOLD_PREFIX + token, HOLD_ZSET_KEY), holdValue,
                    zsetMember(token, userId, sessionId, skuId));
            if (consume == null || consume == 1) {
                throw BusinessException.of(40024, "抢购资格已过期");
            }
            if (consume != 0) {
                throw BusinessException.of(40025, "资格校验失败");
            }
            rollbackReserve(sessionId, skuId, userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 放弃资格是 best-effort：失败吞掉（hold 90s 后自动过期，清理任务兜底回滚）
            log.warn("[SeckillServiceImpl] 取消资格失败 token={} userId={}", token, userId, e);
        }
    }

    /**
     * 回滚预扣（rollback Lua：bought-1 / stock+1，幂等）。补偿路径 best-effort，
     * 失败仅记日志——hold 已核销无法重试，由活动结束 key 过期收敛。
     */
    private void rollbackReserve(Long sessionId, Long skuId, Long userId) {
        try {
            stringRedisTemplate.execute(seckillRollbackScript,
                    Arrays.asList(BOUGHT_PREFIX + sessionId + ":" + skuId,
                            STOCK_PREFIX + sessionId + ":" + skuId),
                    String.valueOf(userId));
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] 回滚预扣失败 sessionId={} skuId={} userId={}",
                    sessionId, skuId, userId, e);
        }
    }

    // =========================================================
    // 场次预热（幂等）
    // =========================================================

    @Override
    public void warmSession(Long sessionId) {
        // current() 含场次商品（启用且 end_time>now）；查无该场（停用/过期）直接返回
        R<List<SeckillSessionDTO>> resp = seckillFeignClient.current();
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            return;
        }
        SeckillSessionDTO session = resp.getData().stream()
                .filter(s -> sessionId.equals(s.getId()))
                .findFirst().orElse(null);
        if (session == null || session.getProducts() == null || session.getProducts().isEmpty()) {
            return;
        }

        long startMs = toEpochMs(session.getStartTime());
        long endMs = toEpochMs(session.getEndTime());
        long ttlSec = Math.max(60, (endMs + EXPIRE_AFTER_END_MS - System.currentTimeMillis()) / 1000);

        // 幂等快速路径：已预热直接返回（warmed 只是加速标记，真正的幂等由下方各 key 的写入保证）
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(WARMED_PREFIX + sessionId))) {
            return;
        }

        // DB 实时库存对齐：初始余量 = min(秒杀库存, DB 现有库存)，防止超卖 DB 已售罄的量
        Map<Long, ProductSkuVO> skuMap = batchGetSkuMap(session.getProducts().stream()
                .map(SeckillProductDTO::getSkuId).collect(Collectors.toList()));

        // 先写全部数据 key（每个写入本身幂等：cfg 覆盖同值、stock SETNX、bought 只补 TTL），
        // 全部成功后最后才落 warmed 标记——中途失败时标记未写，下次预热会重跑补齐，不会把整场焊死
        for (SeckillProductDTO p : session.getProducts()) {
            String cfgKey = CFG_PREFIX + sessionId + ":" + p.getSkuId();
            String stockKey = STOCK_PREFIX + sessionId + ":" + p.getSkuId();
            String boughtKey = BOUGHT_PREFIX + sessionId + ":" + p.getSkuId();

            Map<String, String> cfg = new HashMap<>();
            cfg.put("startMs", String.valueOf(startMs));
            cfg.put("endMs", String.valueOf(endMs));
            cfg.put("limit", String.valueOf(p.getPerLimit() == null ? 1 : p.getPerLimit()));
            stringRedisTemplate.opsForHash().putAll(cfgKey, cfg);
            stringRedisTemplate.expire(cfgKey, ttlSec, TimeUnit.SECONDS);

            ProductSkuVO sku = skuMap.get(p.getSkuId());
            int seckillStock = p.getSeckillStock() == null ? 0 : p.getSeckillStock();
            int dbStock = sku != null && sku.getStock() != null ? Math.max(sku.getStock(), 0) : seckillStock;
            stringRedisTemplate.opsForValue()
                    .setIfAbsent(stockKey, String.valueOf(Math.min(seckillStock, dbStock)), ttlSec, TimeUnit.SECONDS);
            // bought 不预热（HINCRBY 惰性建 key）；已存在时补过期
            stringRedisTemplate.expire(boughtKey, ttlSec, TimeUnit.SECONDS);
        }

        // 数据全部就绪后最后落 warmed 标记（活动结束 +24h 随其余 key 一起过期）
        stringRedisTemplate.opsForValue().set(WARMED_PREFIX + sessionId, "1", ttlSec, TimeUnit.SECONDS);
        log.info("[SeckillServiceImpl] 场次预热完成 sessionId={} products={}", sessionId, session.getProducts().size());
    }

    // =========================================================
    // 工具方法
    // =========================================================

    /** detail 查询：fail/null 统一按"活动不存在或已停用"（含 Feign 降级） */
    private SeckillProductDTO fetchDetail(Long sessionId, Long skuId) {
        R<SeckillProductDTO> resp;
        try {
            resp = seckillFeignClient.detail(sessionId, skuId);
        } catch (Exception e) {
            log.error("[SeckillServiceImpl] detail Feign 调用失败 sessionId={} skuId={}", sessionId, skuId, e);
            throw BusinessException.of(40020, "活动不存在或已停用");
        }
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(40020, "活动不存在或已停用");
        }
        return resp.getData();
    }

    private Map<Long, ProductSkuVO> batchGetSkuMap(List<Long> skuIds) {
        try {
            R<List<ProductSkuVO>> resp = productFeignClient.batchGetSku(skuIds);
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().stream()
                        .filter(s -> s.getId() != null)
                        .collect(Collectors.toMap(ProductSkuVO::getId, s -> s, (a, b) -> a));
            }
            log.warn("[SeckillServiceImpl] SKU 批量查询失败: {}", resp == null ? "null" : resp.getMsg());
        } catch (Exception e) {
            log.warn("[SeckillServiceImpl] SKU 批量查询异常", e);
        }
        return Collections.emptyMap();
    }

    private Map<Long, ProductSpuVO> batchGetSpuMap(List<Long> spuIds) {
        try {
            R<List<ProductSpuVO>> resp = productFeignClient.batchGetSpu(spuIds);
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().stream()
                        .filter(s -> s.getId() != null)
                        .collect(Collectors.toMap(ProductSpuVO::getId, s -> s, (a, b) -> a));
            }
            log.warn("[SeckillServiceImpl] SPU 批量查询失败: {}", resp == null ? "null" : resp.getMsg());
        } catch (Exception e) {
            log.warn("[SeckillServiceImpl] SPU 批量查询异常", e);
        }
        return Collections.emptyMap();
    }

    /** 资格值：userId:sessionId:skuId（token 本身是 UUID 无冒号，split 安全） */
    private String holdValue(Long userId, Long sessionId, Long skuId) {
        return userId + ":" + sessionId + ":" + skuId;
    }

    /**
     * zset member：token:userId:sessionId:skuId（token 是 UUID 含 - 不含 :，split 解析安全）。
     * 回滚上下文冗余进 member——hold key 被 TTL 自动删除后值即丢失（旧实现只存 token，
     * 清理任务 GET hold 得 null 只能 ZREM 不回滚），SeckillHoldCleanupTask 从 member 还原回滚信息。
     */
    private String zsetMember(String token, Long userId, Long sessionId, Long skuId) {
        return token + ":" + holdValue(userId, sessionId, skuId);
    }

    /** marketing String 时间 → epoch ms（双方同为服务器本地时区，同机部署无差） */
    private long toEpochMs(String time) {
        return LocalDateTime.parse(time, TS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 订单号：yyyyMMddHHmmss + userId末4位 + 4位随机数（同 OrderServiceImpl） */
    private String generateOrderNo(Long userId) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return dateStr + String.format("%04d", userId % 10000) + String.format("%04d", new Random().nextInt(10000));
    }

    /** objectKey → 绝对 URL；存量完整 URL 原样放行（同 BannerServiceImpl） */
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
