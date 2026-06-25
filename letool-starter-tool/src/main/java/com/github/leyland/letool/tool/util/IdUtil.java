package com.github.leyland.letool.tool.util;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.UUID;

/**
 * ID 生成工具——提供三种主流分布式 ID 方案.
 *
 * <h3>三种方案对比</h3>
 * <table>
 *   <tr><th>方案</th><th>类型</th><th>长度</th><th>趋势递增</th><th>适用场景</th></tr>
 *   <tr><td>Snowflake</td><td>Long</td><td>19位</td><td>是</td><td>数据库主键、高性能有序 ID</td></tr>
 *   <tr><td>UUID</td><td>String</td><td>32位</td><td>否</td><td>分布式 TraceId、通用唯一标识</td></tr>
 *   <tr><td>NanoId</td><td>String</td><td>默认21位</td><td>否</td><td>短 URL、文件名、安全随机 ID</td></tr>
 * </table>
 *
 * <p>Snowflake 的 WorkerId 和 DatacenterId 会自动推导（从 PID 和 MAC 地址），无需手动配置.</p>
 */
public final class IdUtil {

    private IdUtil() {}

    // ======================== Snowflake（雪花算法） ========================

    /** 默认全局实例，自动推导 WorkerId / DatacenterId */
    private static final Snowflake SNOWFLAKE = new Snowflake();

    /**
     * 生成雪花算法 Long ID.
     *
     * <p>单机每秒可生成约 26 万个不重复 ID，趋势递增，适合作数据库主键.</p>
     *
     * @return 19 位长整型 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成雪花算法 String ID.
     *
     * @return 长整型 ID 的字符串形式
     */
    public static String nextIdStr() {
        return String.valueOf(SNOWFLAKE.nextId());
    }

    // ======================== UUID ========================

    /**
     * 生成不带横线的 32 位 UUID.
     *
     * @return {@code d2f3a1b4c5d6e7f8a9b0c1d2e3f4a5b6} 格式
     */
    public static String simpleUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成标准 36 位 UUID（含横线）.
     *
     * @return {@code d2f3a1b4-c5d6-e7f8-a9b0-c1d2e3f4a5b6} 格式
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    // ======================== NanoId ========================

    /**
     * NanoId 默认使用的字符表（大小写字母 + 数字，共 62 个字符）.
     *
     * <p>与 UUID 相比更短更友好，碰撞概率在 21 位长度下几乎为零
     * （约 1/10²⁴，每秒生成 100 万个，需要约 1 千年才有一次碰撞）.</p>
     */
    private static final char[] NANO_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int NANO_DEFAULT_SIZE = 21;
    private static final SecureRandom NANO_RANDOM = new SecureRandom();

    /**
     * 生成默认长度（21 位）的 NanoId.
     *
     * @return 21 位随机字符串
     */
    public static String nanoId() {
        return nanoId(NANO_DEFAULT_SIZE);
    }

    /**
     * 生成指定长度的 NanoId.
     *
     * @param size 字符个数，建议 &ge; 12 以保证唯一性
     * @return 指定长度的随机字符串
     */
    public static String nanoId(int size) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = NANO_ALPHABET[NANO_RANDOM.nextInt(NANO_ALPHABET.length)];
        }
        return new String(chars);
    }

    // ======================== Snowflake 实现 ========================

    /**
     * 雪花算法标准实现（64 位）.
     *
     * <pre>
     * 结构：1位保留 + 41位毫秒戳 + 5位数据中心 + 5位工作节点 + 12位序列号
     * EPOCH: 2024-01-01 00:00:00
     * 可用到 2094 年
     * </pre>
     *
     * <p>使用无参构造器时会自动从 PID 和 MAC 地址推导 WorkerId 和 DatacenterId.</p>
     */
    public static class Snowflake {
        private static final long EPOCH = 1704038400000L;
        private static final long WORKER_BITS = 5L;
        private static final long DATACENTER_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long MAX_WORKER = ~(-1L << WORKER_BITS);
        private static final long MAX_DATACENTER = ~(-1L << DATACENTER_BITS);
        private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
        private static final long WORKER_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        /** 自动推导 WorkerId（来自 PID）和 DatacenterId（来自 MAC 地址）. */
        public Snowflake() {
            this(deriveWorkerId(), deriveDatacenterId());
        }

        /**
         * 手动指定 WorkerId 和 DatacenterId.
         *
         * @param workerId     0..31
         * @param datacenterId 0..31
         */
        public Snowflake(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER || workerId < 0)
                throw new IllegalArgumentException("workerId must be 0.." + MAX_WORKER);
            if (datacenterId > MAX_DATACENTER || datacenterId < 0)
                throw new IllegalArgumentException("datacenterId must be 0.." + MAX_DATACENTER);
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        /**
         * 生成下一个 ID（线程安全）.
         *
         * <p>同一毫秒内最多生成 4096 个 ID，超出则等待下一毫秒.</p>
         *
         * @return 唯一 Long ID
         * @throws RuntimeException 如果系统时钟回拨
         */
        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp)
                throw new RuntimeException("Clock moved backwards. Refusing to generate id for "
                        + (lastTimestamp - timestamp) + "ms");
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_SHIFT)
                    | (workerId << WORKER_SHIFT)
                    | sequence;
        }

        /** 自旋等待直到下一毫秒 */
        private long tilNextMillis(long lastTimestamp) {
            long ts = System.currentTimeMillis();
            while (ts <= lastTimestamp) ts = System.currentTimeMillis();
            return ts;
        }

        /** 从 JVM 进程 PID 推导 WorkerId */
        private static long deriveWorkerId() {
            long fallback = Thread.currentThread().getId() & MAX_WORKER;
            try {
                String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
                return Long.parseLong(pid) & MAX_WORKER;
            } catch (Exception e) {
                return fallback;
            }
        }

        /** 从第一个网卡 MAC 地址推导 DatacenterId */
        private static long deriveDatacenterId() {
            try {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                while (nets.hasMoreElements()) {
                    NetworkInterface net = nets.nextElement();
                    byte[] mac = net.getHardwareAddress();
                    if (mac != null) return ((long) (mac[mac.length - 1] & 0xFF)) & MAX_DATACENTER;
                }
            } catch (Exception ignored) {}
            return 1L;
        }
    }
}
