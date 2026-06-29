package com.github.leyland.letool.lock.core;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁核心接口 —— 定义分布式锁的标准契约。
 *
 * <p>所有分布式锁实现（如 Redis 悲观锁、ZooKeeper 锁等）均需实现此接口。
 * 该接口抽象了三个核心操作：</p>
 *
 * <ul>
 *   <li><b>尝试获取锁</b> —— 在指定等待时间内尝试获取锁，支持超时机制</li>
 *   <li><b>释放锁</b> —— 释放当前持有的锁</li>
 *   <li><b>检查锁状态</b> —— 查询指定 key 当前是否被锁定</li>
 * </ul>
 *
 * <p>典型使用场景：</p>
 * <pre>{@code
 * @Autowired
 * private DistributedLock lock;
 *
 * if (lock.tryLock("order:123", 3, 30, TimeUnit.SECONDS)) {
 *     try {
 *         // 执行业务逻辑
 *     } finally {
 *         lock.unlock("order:123");
 *     }
 * }
 * }</pre>
 *
 * <p>更推荐使用 {@link LockTemplate} 提供的函数式 API，它能自动处理
 * try-finally 释放锁，避免手动管理带来的遗漏风险。</p>
 *
 * @author leyland
 * @since 1.0.0
 * @see LockTemplate
 * @see RedisPessimisticLock
 */
public interface DistributedLock {

    /**
     * 尝试在指定等待时间内获取分布式锁。
     *
     * <p>如果锁当前被其他线程/进程持有，调用者将等待（自旋）直到满足以下任一条件：</p>
     * <ul>
     *   <li>成功获取到锁</li>
     *   <li>等待时间超过 {@code waitTime}</li>
     *   <li>等待过程中被中断</li>
     * </ul>
     *
     * @param key       锁的唯一标识（业务 key，会在内部拼接 prefix）
     * @param waitTime  等待获取锁的最长时间，超时返回 {@code false}
     * @param leaseTime 持锁的租约时间，超过此时间锁自动释放以防止死锁
     * @param unit      {@code waitTime} 和 {@code leaseTime} 的时间单位
     * @return {@code true} 成功获取锁，{@code false} 超时或中断
     */
    boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 释放指定 key 上的分布式锁。
     *
     * <p>只有当前持有锁的线程才能成功释放。实现应保证释放操作的原子性，
     * 防止误删其他线程持有的锁（通过 token 校验机制）。</p>
     *
     * @param key 锁的唯一标识
     */
    void unlock(String key);

    /**
     * 检查指定 key 当前是否被锁定。
     *
     * @param key 锁的唯一标识
     * @return {@code true} 该 key 当前被锁定，{@code false} 未被锁定
     */
    boolean isLocked(String key);
}
