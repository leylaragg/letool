package io.github.leylaragg.letool.redis.lock;

import io.github.leylaragg.letool.lock.core.LockHandle;
import io.github.leylaragg.letool.lock.exception.LockException;
import org.redisson.api.RLock;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 绑定一次 Redisson 锁获取结果的所有权句柄。
 */
public final class RedissonLockHandle implements LockHandle {

    private final String key;
    private final RLock lock;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param key 调用方提交的业务锁 key
     * @param lock 本次成功获取的 Redisson 锁对象
     */
    public RedissonLockHandle(String key, RLock lock) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
    }

    /** @return 调用方提交的业务锁 key */
    @Override
    public String key() {
        return key;
    }

    /** @return Redisson 判断当前线程是否仍持有该锁 */
    @Override
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * 只释放当前线程仍拥有的锁，并保证重复关闭不会重复调用 Redisson。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException exception) {
            throw new LockException("Failed to release lock: " + key, exception);
        }
    }
}
