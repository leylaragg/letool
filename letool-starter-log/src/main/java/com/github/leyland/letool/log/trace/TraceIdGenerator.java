package com.github.leyland.letool.log.trace;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TraceId 生成器 —— 支持 UUID 短版和迷你雪花算法两种策略.
 *
 * <h3>两种策略对比</h3>
 * <table>
 *   <tr><th>策略</th><th>长度</th><th>特点</th><th>适用场景</th></tr>
 *   <tr><td>uuidShort()</td><td>16 字符</td><td>全局唯一、无依赖</td><td>默认策略，通用场景</td></tr>
 *   <tr><td>snowflakeShort()</td><td>16 字符 Base62</td><td>趋势递增、含时间信息</td><td>需要按时间排序的场景</td></tr>
 * </table>
 */
public final class TraceIdGenerator {

    /** Base62 字符集 —— 用于将雪花算法 ID 编码为可读字符串，不含特殊字符，URL 安全 */
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 自增计数器 —— 用于雪花算法中的序列号部分，位宽 12 bit（0~4095），溢出自动回绕 */
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /** 雪花算法起始时间戳 —— 2023-11-01 00:00:00 UTC（毫秒），保证生成的 ID 在可预见的未来不溢出 */
    private static final long EPOCH = 1700000000000L;

    /** Worker ID —— 机器标识（0~31），JVM 启动时随机生成，避免分布式环境下 ID 冲突 */
    private static final long WORKER_ID = ThreadLocalRandom.current().nextInt(32);

    private TraceIdGenerator() {}

    /**
     * UUID 去横线短版 —— 取 UUID 前 16 位十六进制字符，兼顾唯一性和可读性（默认策略）.
     * 示例: {@code "a1b2c3d4e5f6g7h8"}
     */
    public static String uuidShort() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 全量 UUID —— 32 位十六进制字符，无横线，唯一性最高但长度较长.
     * 示例: {@code "a1b2c3d4e5f67890abcdef1234567890"}
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 迷你雪花算法 ID —— 时间戳(42bit) + WorkerId(5bit) + 序列号(12bit) 编码为 16 位 Base62.
     * 趋势递增，适合需要按生成时间排序的场景.
     */
    public static String snowflakeShort() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        long seq = COUNTER.incrementAndGet() & 0xFFF;  // 序列号掩码 12 bit
        long id = (timestamp << 17) | (WORKER_ID << 12) | seq;
        return base62(id);
    }

    /**
     * 将 long 值编码为 Base62 字符串 —— 最小 12 位，不足位左侧补 0.
     */
    private static String base62(long val) {
        StringBuilder sb = new StringBuilder(16);
        while (val > 0) {
            sb.append(BASE62.charAt((int) (val % 62)));
            val /= 62;
        }
        while (sb.length() < 12) sb.append('0');
        return sb.reverse().toString();
    }
}
