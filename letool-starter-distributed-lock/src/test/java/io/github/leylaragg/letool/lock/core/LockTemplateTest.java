package io.github.leylaragg.letool.lock.core;

import io.github.leylaragg.letool.lock.exception.LockException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证锁模板只通过获取到的句柄管理当前锁所有权。 */
class LockTemplateTest {

    /** 成功获取后应返回业务值并且只关闭当前句柄。 */
    @Test
    void shouldCloseAcquiredHandleAfterSuccess() {
        DistributedLock backend = mock(DistributedLock.class);
        LockHandle handle = mock(LockHandle.class);
        LockRequest request = LockRequest.watchdog("order:1", Duration.ofSeconds(3));
        when(backend.tryAcquire(request)).thenReturn(Optional.of(handle));

        String result = new LockTemplate(backend).execute(request, () -> "ok");

        assertEquals("ok", result);
        verify(handle).close();
    }

    /** 业务异常不能跳过句柄关闭。 */
    @Test
    void shouldCloseAcquiredHandleWhenBusinessFails() {
        DistributedLock backend = mock(DistributedLock.class);
        LockHandle handle = mock(LockHandle.class);
        LockRequest request = LockRequest.fixedLease(
                "order:1", Duration.ofSeconds(3), Duration.ofSeconds(30));
        when(backend.tryAcquire(request)).thenReturn(Optional.of(handle));

        assertThrows(IllegalStateException.class,
                () -> new LockTemplate(backend).execute(request, () -> {
                    throw new IllegalStateException("boom");
                }));
        verify(handle).close();
    }

    /** 获取超时应抛出包含业务 key 的稳定异常。 */
    @Test
    void shouldReportKeyWhenAcquireTimesOut() {
        DistributedLock backend = mock(DistributedLock.class);
        LockRequest request = LockRequest.watchdog("order:1", Duration.ZERO);
        when(backend.tryAcquire(request)).thenReturn(Optional.empty());

        LockException thrown = assertThrows(LockException.class,
                () -> new LockTemplate(backend).execute(request, () -> "never"));

        assertTrue(thrown.getMessage().contains("order:1"));
    }
}
