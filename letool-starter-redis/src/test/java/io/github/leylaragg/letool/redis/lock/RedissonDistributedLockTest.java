package io.github.leylaragg.letool.redis.lock;

import io.github.leylaragg.letool.lock.core.LockHandle;
import io.github.leylaragg.letool.lock.core.LockRequest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redisson 后端正确区分看门狗与固定租约。 */
class RedissonDistributedLockTest {

    /** 看门狗请求必须调用不带 leaseTime 的 Redisson 重载。 */
    @Test
    void watchdogRequestShouldUseTryLockWithoutLease() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("letool:lock:order:1")).thenReturn(lock);
        when(lock.tryLock(3_000, TimeUnit.MILLISECONDS)).thenReturn(true);

        Optional<LockHandle> handle = new RedissonDistributedLock(
                client, "letool:lock:", false).tryAcquire(
                        LockRequest.watchdog("order:1", Duration.ofSeconds(3)));

        assertTrue(handle.isPresent());
        verify(lock).tryLock(3_000, TimeUnit.MILLISECONDS);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    /** 固定租约和公平锁配置必须原样传给 Redisson。 */
    @Test
    void fixedLeaseRequestShouldPassExplicitLeaseToFairLock() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getFairLock("letool:lock:order:1")).thenReturn(lock);
        when(lock.tryLock(3_000, 30_000, TimeUnit.MILLISECONDS)).thenReturn(true);

        Optional<LockHandle> handle = new RedissonDistributedLock(
                client, "letool:lock:", true).tryAcquire(
                        LockRequest.fixedLease(
                                "order:1", Duration.ofSeconds(3), Duration.ofSeconds(30)));

        assertTrue(handle.isPresent());
        verify(lock).tryLock(3_000, 30_000, TimeUnit.MILLISECONDS);
    }

    /** 等待线程被中断时应恢复中断标记并返回未获取。 */
    @Test
    void interruptionShouldRestoreThreadFlag() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("letool:lock:order:1")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("stop"));

        try {
            Optional<LockHandle> handle = new RedissonDistributedLock(
                    client, "letool:lock:", false).tryAcquire(
                            LockRequest.watchdog("order:1", Duration.ZERO));
            assertFalse(handle.isPresent());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
