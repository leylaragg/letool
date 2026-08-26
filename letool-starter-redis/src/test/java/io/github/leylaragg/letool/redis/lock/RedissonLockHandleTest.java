package io.github.leylaragg.letool.redis.lock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redisson 锁句柄不会释放当前线程已经失去所有权的锁。 */
class RedissonLockHandleTest {

    /** 当前线程仍持有锁时关闭句柄应释放一次。 */
    @Test
    void closeShouldUnlockOwnedLockOnce() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonLockHandle handle = new RedissonLockHandle("order:1", lock);

        handle.close();
        handle.close();

        verify(lock).unlock();
    }

    /** 当前线程已失去所有权时关闭句柄不能误解锁。 */
    @Test
    void closeShouldNotUnlockWhenOwnershipWasLost() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        RedissonLockHandle handle = new RedissonLockHandle("order:1", lock);

        handle.close();

        assertTrue(handle.key().equals("order:1"));
        verify(lock, never()).unlock();
    }
}
