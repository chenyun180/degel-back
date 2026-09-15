-- 回滚预扣（下单失败/资格取消补偿，幂等）：已购 -1、余量 +1
-- KEYS[1] seckill:bought:{sessionId}:{skuId}  KEYS[2] seckill:stock:{sessionId}:{skuId}
-- ARGV[1] userId
-- 返回 0（bought<=0 视为已回滚过，幂等直接成功）
local bought = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
if bought > 0 then
  redis.call('HINCRBY', KEYS[1], ARGV[1], -1)
  redis.call('INCR', KEYS[2])
end
return 0
