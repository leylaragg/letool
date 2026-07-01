package com.github.leyland.letool.cache.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NullSentinel 空值哨兵测试")
class NullSentinelTest {

    @Test
    @DisplayName("toString 返回 NULL_SENTINEL")
    void testToString() {
        NullSentinel.INSTANCE.toString();
        assertEquals("NULL_SENTINEL", NullSentinel.INSTANCE.toString());
    }

    @Test
    @DisplayName("INSTANCE 是单例")
    void testSingleton() {
        assertNotNull(NullSentinel.INSTANCE);
        assertSame(NullSentinel.INSTANCE, NullSentinel.INSTANCE);
    }
}
