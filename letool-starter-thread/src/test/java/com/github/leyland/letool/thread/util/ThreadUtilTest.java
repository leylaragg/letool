package com.github.leyland.letool.thread.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("线程工具测试")
class ThreadUtilTest {

    @Nested
    @DisplayName("sleep")
    class SleepTests {

        @Test
        @DisplayName("sleep 毫秒级正常执行")
        void sleepMillis() {
            long start = System.currentTimeMillis();
            ThreadUtil.sleep(10);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 8, "sleep 时长应至少接近指定毫秒数");
        }

        @Test
        @DisplayName("sleep 支持 TimeUnit")
        void sleepWithTimeUnit() {
            long start = System.currentTimeMillis();
            ThreadUtil.sleep(10, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 8);
        }
    }

    @Nested
    @DisplayName("runAsync")
    class RunAsyncTests {

        @Test
        @DisplayName("异步执行任务")
        void runAsyncTask() throws Exception {
            Executor executor = Executors.newSingleThreadExecutor();
            CountDownLatch latch = new CountDownLatch(1);

            ThreadUtil.runAsync(() -> latch.countDown(), executor).get(2, TimeUnit.SECONDS);

            assertEquals(0, latch.getCount());
        }
    }

    @Nested
    @DisplayName("supplyAsync")
    class SupplyAsyncTests {

        @Test
        @DisplayName("异步返回结果")
        void supplyAsyncWithResult() throws Exception {
            Executor executor = Executors.newSingleThreadExecutor();

            String result = ThreadUtil.supplyAsync(() -> "hello", executor).get(2, TimeUnit.SECONDS);

            assertEquals("hello", result);
        }
    }

    @Nested
    @DisplayName("虚拟线程检测")
    class VirtualThreadDetection {

        @Test
        @DisplayName("isVirtualThreadsSupported 应与 JDK 版本一致")
        void virtualThreadsSupported() {
            int feature = Runtime.version().feature();
            if (feature >= 21) {
                assertTrue(ThreadUtil.isVirtualThreadsSupported());
            } else {
                assertFalse(ThreadUtil.isVirtualThreadsSupported());
            }
        }
    }
}
