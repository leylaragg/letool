package io.github.leylaragg.letool.lock.core;

import java.time.Duration;

/**
 * 一次分布式锁获取请求。
 *
 * <p>{@code leaseTime} 为空表示由后端看门狗续期；非空表示使用调用方明确给出的固定租约。</p>
 *
 * @param key 业务锁 key，不包含具体后端的命名前缀
 * @param waitTime 等待锁的最长时间，允许为零
 * @param leaseTime 固定租约；{@code null} 表示看门狗模式
 */
public record LockRequest(String key, Duration waitTime, Duration leaseTime) {

    /**
     * 在请求进入后端前完成不变量校验。
     *
     * @param key 业务锁 key，不包含具体后端的命名前缀
     * @param waitTime 等待锁的最长时间，允许为零
     * @param leaseTime 固定租约；{@code null} 表示看门狗模式
     */
    public LockRequest {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("锁等待时间不能为负数");
        }
        if (leaseTime != null && (leaseTime.isZero() || leaseTime.isNegative())) {
            throw new IllegalArgumentException("固定锁租约必须大于零");
        }
    }

    /**
     * 创建由后端看门狗维护租约的请求。
     *
     * @param key 业务锁 key
     * @param waitTime 等待锁的最长时间
     * @return 看门狗锁请求
     */
    public static LockRequest watchdog(String key, Duration waitTime) {
        return new LockRequest(key, waitTime, null);
    }

    /**
     * 创建使用固定租约的请求。
     *
     * @param key 业务锁 key
     * @param waitTime 等待锁的最长时间
     * @param leaseTime 固定租约
     * @return 固定租约锁请求
     */
    public static LockRequest fixedLease(String key, Duration waitTime, Duration leaseTime) {
        return new LockRequest(key, waitTime, leaseTime);
    }

    /** @return {@code true} 表示租约应交给后端看门狗维护 */
    public boolean usesWatchdog() {
        return leaseTime == null;
    }
}
