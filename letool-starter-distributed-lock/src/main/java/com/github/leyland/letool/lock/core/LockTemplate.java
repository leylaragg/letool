package com.github.leyland.letool.lock.core;

import com.github.leyland.letool.lock.exception.LockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁操作模板 —— 提供函数式编程风格的锁操作 API。
 *
 * <p>这是用户使用分布式锁的<b>推荐入口</b>。相较于直接使用 {@link DistributedLock}
 * 接口手动管理 tryLock/unlock，本模板自动处理以下细节：</p>
 *
 * <ul>
 *   <li><b>自动释放锁</b>：通过 try-finally 保证锁一定被释放，避免死锁</li>
 *   <li><b>超时异常</b>：获取锁失败时抛出统一的 {@link LockException}</li>
 *   <li><b>简洁 API</b>：提供多个重载方法，支持 Lambda 表达式、默认超时参数等</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <p><b>1. 有返回值 + 自定义超时：</b></p>
 * <pre>{@code
 * String result = lockTemplate.execute("order:123", 5, 30, TimeUnit.SECONDS, () -> {
 *     // 业务逻辑
 *     return processOrder(123);
 * });
 * }</pre>
 *
 * <p><b>2. 无返回值 + 默认超时（3s 等待 / 30s 持锁）：</b></p>
 * <pre>{@code
 * lockTemplate.execute("order:123", () -> {
 *     // 业务逻辑
 *     processOrder(123);
 * });
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 * @see DistributedLock
 * @see LockException
 */
public class LockTemplate {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(LockTemplate.class);

    // ======================== 依赖 ========================

    /** 底层分布式锁实现 */
    private final DistributedLock lock;

    // ======================== 构造方法 ========================

    /**
     * 构造锁操作模板。
     *
     * @param lock 分布式锁实现实例（不可为 null）
     */
    public LockTemplate(DistributedLock lock) {
        this.lock = lock;
    }

    // ======================== 核心方法：有返回值 + 完整参数 ========================

    /**
     * 在分布式锁保护下执行带返回值的操作（完整参数版本）。
     *
     * <p>这是最底层的模板方法，其他重载均委托至此。执行流程：</p>
     * <ol>
     *   <li>尝试获取锁（在 waitTime 内等待）</li>
     *   <li>获取失败 → 抛出 {@link LockException}</li>
     *   <li>获取成功 → 执行 {@code supplier.get()}</li>
     *   <li>无论成功或失败，finally 中释放锁</li>
     * </ol>
     *
     * @param <T>       返回值类型
     * @param key       锁的唯一标识（业务 key）
     * @param waitTime  等待获取锁的最长时间，超时抛异常
     * @param leaseTime 持锁租约时间（到期自动释放，防止死锁）
     * @param unit      时间单位
     * @param supplier  需要加锁执行的业务逻辑
     * @return 业务逻辑的返回值
     * @throws LockException 获取锁超时
     */
    public <T> T execute(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        boolean locked = lock.tryLock(key, waitTime, leaseTime, unit);
        if (!locked) {
            throw new LockException("Failed to acquire lock: " + key);
        }
        try {
            return supplier.get();
        } finally {
            lock.unlock(key);
        }
    }

    // ======================== 便捷方法：无返回值 + 完整参数 ========================

    /**
     * 在分布式锁保护下执行无返回值的操作（完整参数版本）。
     *
     * <p>内部将 {@link Runnable} 适配为 {@link Supplier} 后委托至核心方法。</p>
     *
     * @param key       锁的唯一标识（业务 key）
     * @param waitTime  等待获取锁的最长时间
     * @param leaseTime 持锁租约时间
     * @param unit      时间单位
     * @param runnable  需要加锁执行的业务逻辑
     * @throws LockException 获取锁超时
     */
    public void execute(String key, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable) {
        execute(key, waitTime, leaseTime, unit, () -> { runnable.run(); return null; });
    }

    // ======================== 便捷方法：有返回值 + 默认超时 ========================

    /**
     * 在分布式锁保护下执行带返回值的操作（使用默认超时：等待 3 秒，持锁 30 秒）。
     *
     * <p>适用于大多数常规场景，无需每次指定超时参数。</p>
     *
     * @param <T>      返回值类型
     * @param key      锁的唯一标识（业务 key）
     * @param supplier 需要加锁执行的业务逻辑
     * @return 业务逻辑的返回值
     * @throws LockException 获取锁超时
     */
    public <T> T execute(String key, Supplier<T> supplier) {
        return execute(key, 3, 30, TimeUnit.SECONDS, supplier);
    }

    // ======================== 便捷方法：无返回值 + 默认超时 ========================

    /**
     * 在分布式锁保护下执行无返回值的操作（使用默认超时：等待 3 秒，持锁 30 秒）。
     *
     * <p>这是最简洁的调用方式，适合仅需锁保护、无返回值的场景。</p>
     *
     * @param key      锁的唯一标识（业务 key）
     * @param runnable 需要加锁执行的业务逻辑
     * @throws LockException 获取锁超时
     */
    public void execute(String key, Runnable runnable) {
        execute(key, 3, 30, TimeUnit.SECONDS, () -> { runnable.run(); return null; });
    }
}
