-- 优惠券核销预扣（原子操作，防超卖/超量）
-- KEYS[1] = 优惠券余量 key：coupon:stock:{couponId}
-- KEYS[2] = 用户已用数量 key：coupon:used:{couponId}:{userId}
-- ARGV[1] = 用户限领/限用数量
-- ARGV[2] = 本次抵扣数量
-- 返回：1=成功  -1=库存不足  -2=超过限用数量
local remain = tonumber(redis.call('GET', KEYS[1]) or '0')
local used = tonumber(redis.call('GET', KEYS[2]) or '0')

if remain < tonumber(ARGV[2]) then
    return -1
end
if used + tonumber(ARGV[2]) > tonumber(ARGV[1]) then
    return -2
end

redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('INCRBY', KEYS[2], ARGV[2])
return 1