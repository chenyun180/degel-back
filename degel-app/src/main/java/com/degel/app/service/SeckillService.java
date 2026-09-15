package com.degel.app.service;

import com.degel.app.vo.OrderCreateVO;
import com.degel.app.vo.SeckillProductVO;
import com.degel.app.vo.SeckillReserveVO;
import com.degel.app.vo.SeckillSessionVO;
import com.degel.app.vo.dto.SeckillOrderReqVO;
import com.degel.app.vo.dto.SeckillReserveReqVO;

import java.util.List;

/**
 * 秒杀服务（Lua 预扣 + 两段式下单）
 *
 * <p>流程：GET /sessions 看场次 → POST /reserve 预扣（Redis Lua 原子预扣，返回 90s 资格 token）
 * → POST /order 凭 token 下单（核销资格 + 扣 DB 库存 + 建秒杀订单 orderType=1）。
 */
public interface SeckillService {

    /** 当前/即将开卖场次列表（静态缓存 30s；余量/已抢百分比实时读 Redis 不进缓存） */
    List<SeckillSessionVO> listSessions();

    /** 场次商品详情（确认页用，不缓存） */
    SeckillProductVO getProduct(Long sessionId, Long skuId);

    /** 抢购预扣（Lua 原子：时间窗/余量/限购校验 + 写 90s 资格 hold） */
    SeckillReserveVO reserve(SeckillReserveReqVO reqVO, Long userId);

    /** 两段式下单：核销资格 → 扣库存 → 建单（orderType=1）；失败路径全补偿 */
    OrderCreateVO createOrder(SeckillOrderReqVO reqVO, Long userId);

    /** 主动放弃抢购资格（核销 + 回滚预扣；best-effort） */
    void cancelReserve(String token, Long userId);

    /**
     * 预热场次 Redis 数据（内部方法，幂等：SETNX seckill:warmed:{sessionId}）。
     * 写 cfg(startMs/endMs/limit)/stock(=min(秒杀库存, DB 实时库存))，统一过期 end+24h。
     * 由定时任务（开卖前 10min）与 reserve 未预热懒加载双路径触发。
     */
    void warmSession(Long sessionId);
}
