package io.github.leylaragg.letool.redis.lock;

import io.github.leylaragg.letool.lock.core.DistributedLock;
import io.github.leylaragg.letool.lock.core.LockHandle;
import io.github.leylaragg.letool.lock.core.LockRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson {@link RLock} 的分布式锁后端。
 *
 * <p>看门狗请求使用不带固定租约的 {@code tryLock} 重载，以保留 Redisson 自动续期；
 * 显式租约请求则把等待时间和租约统一转换为毫秒传入。</p>
 */
public final class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final boolean fair;

    /**
     * @param redissonClient Redisson 客户端
     * @param keyPrefix 写入 Redis 的锁 key 前缀
     * @param fair 是否使用公平锁
     */
    public RedissonDistributedLock(RedissonClient redissonClient, String keyPrefix, boolean fair) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("锁 key 前缀不能为空");
        }
        this.keyPrefix = keyPrefix;
        this.fair = fair;
    }

    /**
     * 尝试获取 Redisson 锁；线程中断时恢复中断标记并返回未获取。
     *
     * @param request 锁请求
     * @return 成功时返回所有权句柄，等待超时或中断时返回空
     */
    @Override
    public Optional<LockHandle> tryAcquire(LockRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RLock lock = lockFor(request.key());
        try {
            boolean acquired = request.usesWatchdog()
                    ? lock.tryLock(request.waitTime().toMillis(), TimeUnit.MILLISECONDS)
                    : lock.tryLock(
                            request.waitTime().toMillis(),
                            request.leaseTime().toMillis(),
                            TimeUnit.MILLISECONDS);
            return acquired
                    ? Optional.of(new RedissonLockHandle(request.key(), lock))
                    : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * 查询业务锁当前是否被任意线程持有。
     *
     * @param key 业务锁 key
     * @return Redisson 中该锁是否被持有
     */
    @Override
    public boolean isLocked(String key) {
        return lockFor(key).isLocked();
    }

    private RLock lockFor(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
        String fullKey = keyPrefix + key;
        return fair ? redissonClient.getFairLock(fullKey) : redissonClient.getLock(fullKey);
    }
}
