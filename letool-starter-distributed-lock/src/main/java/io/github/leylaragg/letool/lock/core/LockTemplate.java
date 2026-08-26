package io.github.leylaragg.letool.lock.core;

import io.github.leylaragg.letool.lock.exception.LockException;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 在分布式锁保护下执行业务回调的统一入口。
 *
 * <p>模板通过一次获取返回的句柄释放锁，因此不需要按 key 重新定位后端状态。</p>
 */
public class LockTemplate {

    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(3);

    private final DistributedLock lock;

    /**
     * @param lock 具体的分布式锁后端
     */
    public LockTemplate(DistributedLock lock) {
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
    }

    /**
     * 获取锁后执行带返回值的业务回调。
     *
     * @param request 锁请求
     * @param supplier 仅在成功获取锁后执行的业务回调
     * @param <T> 业务返回类型
     * @return 业务回调结果
     * @throws LockException 等待超时，没有获得锁
     */
    public <T> T execute(LockRequest request, Supplier<T> supplier) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        LockHandle handle = lock.tryAcquire(request)
                .orElseThrow(() -> new LockException("Failed to acquire lock: " + request.key()));
        try (handle) {
            return supplier.get();
        }
    }

    /**
     * 获取锁后执行无返回值业务回调。
     *
     * @param request 锁请求
     * @param runnable 业务回调
     */
    public void execute(LockRequest request, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        execute(request, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 使用默认等待时间和后端看门狗执行回调。
     *
     * @param key 业务锁 key
     * @param supplier 业务回调
     * @param <T> 业务返回类型
     * @return 业务回调结果
     */
    public <T> T execute(String key, Supplier<T> supplier) {
        return execute(LockRequest.watchdog(key, DEFAULT_WAIT_TIME), supplier);
    }

    /**
     * 使用默认等待时间和后端看门狗执行无返回值回调。
     *
     * @param key 业务锁 key
     * @param runnable 业务回调
     */
    public void execute(String key, Runnable runnable) {
        execute(LockRequest.watchdog(key, DEFAULT_WAIT_TIME), runnable);
    }
}
