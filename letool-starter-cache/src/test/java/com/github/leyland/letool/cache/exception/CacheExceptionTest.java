package com.github.leyland.letool.cache.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheException 异常测试")
class CacheExceptionTest {

    @Test
    @DisplayName("message 构造函数")
    void testMessageConstructor() {
        CacheException ex = new CacheException("缓存未找到");
        assertEquals("缓存未找到", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("message + cause 构造函数")
    void testMessageAndCauseConstructor() {
        Throwable cause = new RuntimeException("Redis 连接失败");
        CacheException ex = new CacheException("序列化失败", cause);
        assertEquals("序列化失败", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("继承 RuntimeException")
    void testInheritsRuntimeException() {
        CacheException ex = new CacheException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
