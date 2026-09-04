package com.zhixing.common.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 雪花算法全局唯一 ID 生成器。
 * <p>
 * 用于生成全局唯一订单号，保证分布式环境下无冲突：
 * <pre>
 *   41位时间戳 | 5位数据中心(0) | 5位工作节点 | 12位序列号
 * </pre>
 * 工作节点号由本机 IP 末段与进程 PID 取模派生，避免手动分配。
 */
public class SnowflakeIdGenerator {

    /** 起始时间戳：2021-01-01 00:00:00 */
    private static final long TWEPOCH = 1609459200000L;
    /** 序列号位数 */
    private static final long SEQUENCE_BITS = 12L;
    /** 工作节点位数 */
    private static final long WORKER_ID_BITS = 5L;
    /** 数据中心位数 */
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 全局单例（横跨整个应用进程） */
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(
            resolveWorkerId(), 0);

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 超出范围");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 超出范围");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public static SnowflakeIdGenerator getInstance() {
        return INSTANCE;
    }

    /** 生成下一个唯一 ID */
    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - TWEPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long last) {
        long ts = currentTime();
        while (ts <= last) {
            ts = currentTime();
        }
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }

    /** 从本机 IP 末段 + 进程 PID 派生出稳定的工作节点号（0~31） */
    private static long resolveWorkerId() {
        int ipSuffix = 0;
        try {
            byte[] addr = InetAddress.getLocalHost().getAddress();
            ipSuffix = addr[addr.length - 1] & 0x1F;
        } catch (UnknownHostException ignore) {
        }
        long pid = ProcessHandle.current().pid();
        return ((pid % 32) ^ ipSuffix) & MAX_WORKER_ID;
    }
}