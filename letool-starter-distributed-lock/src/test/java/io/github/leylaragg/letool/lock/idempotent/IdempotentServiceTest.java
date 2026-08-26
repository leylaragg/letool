package io.github.leylaragg.letool.lock.idempotent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证幂等服务的占位、重复请求和失败回滚语义。 */
class IdempotentServiceTest {

    /** 重复请求不能调用业务回调。 */
    @Test
    void duplicateRequestShouldNotInvokeBusiness() {
        IdempotentStore store = mock(IdempotentStore.class);
        when(store.putIfAbsent("pay:7", Duration.ofHours(1))).thenReturn(false);
        AtomicBoolean invoked = new AtomicBoolean();

        Object result = new IdempotentService(store).execute(
                "pay:7", Duration.ofHours(1), () -> {
                    invoked.set(true);
                    return "unexpected";
                });

        assertNull(result);
        assertFalse(invoked.get());
    }

    /** 首次请求成功时直接返回业务结果。 */
    @Test
    void firstRequestShouldReturnBusinessResult() {
        IdempotentStore store = mock(IdempotentStore.class);
        when(store.putIfAbsent("pay:7", Duration.ofHours(1))).thenReturn(true);

        String result = new IdempotentService(store).execute(
                "pay:7", Duration.ofHours(1), () -> "ok");

        assertEquals("ok", result);
    }

    /** 业务失败时必须撤销占位并传播原始运行时异常。 */
    @Test
    void businessFailureShouldRemoveMarker() {
        IdempotentStore store = mock(IdempotentStore.class);
        when(store.putIfAbsent("pay:7", Duration.ofHours(1))).thenReturn(true);
        IllegalStateException expected = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new IdempotentService(store).execute(
                        "pay:7", Duration.ofHours(1), () -> {
                            throw expected;
                        }));

        assertEquals(expected, thrown);
        verify(store).remove("pay:7");
    }
}
