-- 消费抢购资格（下单时原子核销：值校验 + 删 hold + 出 zset）
-- KEYS[1] seckill:hold:{token}  KEYS[2] seckill:hold:zset
-- ARGV[1] 期望的 hold 值（userId:sessionId:skuId）  ARGV[2] zset member（token:userId:sessionId:skuId，与 reserve 写入的 member 一致）
-- 返回 0=成功  1=资格不存在(已过期/已核销)  2=值不符(非本人/伪造)
local v = redis.call('GET', KEYS[1])
if v == false then return 1 end
if v ~= ARGV[1] then return 2 end
redis.call('DEL', KEYS[1])
redis.call('ZREM', KEYS[2], ARGV[2])  -- 出队即核销：cleanup 任务以 ZREM 同一 member 抢占回滚权，双方天然互斥
return 0
