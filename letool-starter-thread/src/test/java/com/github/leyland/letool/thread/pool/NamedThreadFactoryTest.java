package com.github.leyland.letool.thread.pool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("命名线程工厂测试")
class NamedThreadFactoryTest {

    @Nested
    @DisplayName("线程命名")
    class ThreadNaming {

        @Test
        @DisplayName("创建的线程包含前缀和递增序号")
        void containsPrefixAndCounter() {
            NamedThreadFactory factory = new NamedThreadFactory("order");

            Thread t1 = factory.newThread(() -> {});
            Thread t2 = factory.newThread(() -> {});

            assertEquals("order-1", t1.getName());
            assertEquals("order-2", t2.getName());
        }

        @Test
        @DisplayName("线程优先级为标准优先级")
        void normalPriority() {
            NamedThreadFactory factory = new NamedThreadFactory("task");
            Thread thread = factory.newThread(() -> {});

            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
        }
    }

    @Nested
    @DisplayName("守护线程")
    class DaemonThread {

        @Test
        @DisplayName("默认创建非守护线程")
        void nonDaemonByDefault() {
            NamedThreadFactory factory = new NamedThreadFactory("worker");
            Thread thread = factory.newThread(() -> {});
            assertFalse(thread.isDaemon());
        }

        @Test
        @DisplayName("指定 daemon=true 创建守护线程")
        void daemonWhenSpecified() {
            NamedThreadFactory factory = new NamedThreadFactory("worker", true);
            Thread thread = factory.newThread(() -> {});
            assertTrue(thread.isDaemon());
        }
    }
}
