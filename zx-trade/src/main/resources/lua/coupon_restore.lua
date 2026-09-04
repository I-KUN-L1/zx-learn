-- 优惠券退回（关单/取消后恢复库存）
-- KEYS[1] = 优惠券余量 key
-- KEYS[2] = 用户已用数量 key
-- ARGV[1] = 退回数量
redis.call('INCRBY', KEYS[1], ARGV[1])
redis.call('DECRBY', KEYS[2], ARGV[1])
return 1