-- 秒杀预扣（原子）：时间窗校验 → 余量预扣 → 限购校验 → 写抢购资格 hold
-- KEYS[1] seckill:cfg:{sessionId}:{skuId}    HASH: startMs/endMs/limit
-- KEYS[2] seckill:stock:{sessionId}:{skuId}  STRING: 剩余秒杀库存
-- KEYS[3] seckill:bought:{sessionId}:{skuId} HASH: userId -> 已购数量
-- KEYS[4] seckill:hold:{token}               STRING: 抢购资格（userId:sessionId:skuId）
-- KEYS[5] seckill:hold:zset                  ZSET: member=token:userId:sessionId:skuId score=过期时刻 ms
--        （回滚上下文冗余进 member：hold key 被 TTL 自动删除后值即丢失，
--          兜底清理任务 SeckillHoldCleanupTask 仍能从 member 还原 token/userId/sessionId/skuId 完成回滚）
-- ARGV[1] nowMs  ARGV[2] userId  ARGV[3] holdTtlSec  ARGV[4] holdValue(userId:sessionId:skuId)  ARGV[5] zsetMember(token:userId:sessionId:skuId)
-- 返回 0=成功  -1=未预热(cfg 不存在)  1=未开始或已结束  2=已抢光  3=超出限购
local cfg = redis.call('HGETALL', KEYS[1])
if #cfg == 0 then return -1 end
local startMs, endMs, limit
for i = 1, #cfg, 2 do
  if cfg[i] == 'startMs' then startMs = tonumber(cfg[i + 1])
  elseif cfg[i] == 'endMs' then endMs = tonumber(cfg[i + 1])
  elseif cfg[i] == 'limit' then limit = tonumber(cfg[i + 1]) end
end
local now = tonumber(ARGV[1])
if now < startMs or now >= endMs then return 1 end
local stock = tonumber(redis.call('GET', KEYS[2]) or '-1')
if stock <= 0 then return 2 end
local bought = tonumber(redis.call('HGET', KEYS[3], ARGV[2]) or '0')
if bought + 1 > limit then return 3 end
redis.call('DECR', KEYS[2])
redis.call('HINCRBY', KEYS[3], ARGV[2], 1)
redis.call('SET', KEYS[4], ARGV[4], 'EX', tonumber(ARGV[3]))
redis.call('ZADD', KEYS[5], now + tonumber(ARGV[3]) * 1000, ARGV[5])
return 0
