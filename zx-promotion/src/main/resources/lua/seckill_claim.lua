-- 优惠券秒杀领取（原子操作：限领判重 + 余量判断 + 扣减 + 记录用户领取）
-- KEYS[1] = 秒杀余量 key：coupon:seckill:stock:{couponId}   （活动预热时写入，未预热=未开始）
-- KEYS[2] = 已领取用户 set：coupon:seckill:users:{couponId} （同时作为对账依据）
-- ARGV[1] = 用户 id
-- ARGV[2] = 每人限领数量
-- 返回： 1=成功  -1=已售罄  -2=重复领取(超限领)  -3=未预热(活动未开始)
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -3
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end
local remain = tonumber(redis.call('GET', KEYS[1]))
if remain <= 0 then
    return -1
end

redis.call('DECRBY', KEYS[1], 1)
redis.call('SADD', KEYS[2], ARGV[1])
return 1
