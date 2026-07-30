package com.github.leyland.letool.thread.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link AsyncWithContext} 组合注解契约测试。
 */
class AsyncWithContextTest {

    /**
     * 验证未指定执行器时会委托给模块专用的 {@code letoolTaskExecutor} Bean。
     *
     * @throws NoSuchMethodException 当测试夹具方法不存在时抛出
     */
    @Test
    void shouldExposeDefaultExecutorThroughSpringAsync() throws NoSuchMethodException {
        Method method = AsyncFixture.class.getDeclaredMethod("defaultExecutor");

        Async async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);

        assertNotNull(async);
        assertEquals("letoolTaskExecutor", async.value());
    }

    /**
     * 验证显式执行器名称会映射到 Spring {@link Async#value()}。
     *
     * @throws NoSuchMethodException 当测试夹具方法不存在时抛出
     */
    @Test
    void shouldAliasConfiguredExecutorToSpringAsync() throws NoSuchMethodException {
        Method method = AsyncFixture.class.getDeclaredMethod("customExecutor");

        Async async = AnnotatedElementUtils.findMergedAnnotation(method, Async.class);

        assertNotNull(async);
        assertEquals("letoolIoExecutor", async.value());
    }

    /**
     * 用于验证组合注解元数据的测试夹具。
     */
    static class AsyncFixture {

        /**
         * 使用默认执行器。
         */
        @AsyncWithContext
        void defaultExecutor() {
        }

        /**
         * 使用指定执行器。
         */
        @AsyncWithContext("letoolIoExecutor")
        void customExecutor() {
        }
    }
}
