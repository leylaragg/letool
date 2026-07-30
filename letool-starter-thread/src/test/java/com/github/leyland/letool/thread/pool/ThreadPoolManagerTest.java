package com.github.leyland.letool.thread.pool;

import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.thread.config.ThreadPoolProperties;
import com.github.leyland.letool.thread.util.ThreadUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolManagerTest {

    private final ThreadPoolManager manager = new ThreadPoolManager();

    @AfterEach
    void tearDown() {
        manager.shutdownAll();
    }

    /**
     * 验证普通线程池通过 {@link ThreadPoolManager#getOrCreate(String, ThreadPoolProperties.PoolConfig)}
     * 首次创建、后续复用。
     *
     * <p>该用例覆盖并发 Map 原子创建回调内部不得再次写入同一注册表，
     * 避免递归更新异常。</p>
     */
    @Test
    void getOrCreateShouldCreatePoolWithoutRecursiveMapUpdate() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setThreadNamePrefix("test-");

        ExecutorService first = assertDoesNotThrow(() -> manager.getOrCreate("testPool", config));
        ExecutorService second = manager.getOrCreate("testPool", config);

        assertSame(first, second);
    }

    /**
     * 验证多个调用方并发获取同名线程池时只会得到一个实例。
     *
     * @throws Exception 当并发任务等待超时或执行失败时抛出
     */
    @Test
    void getOrCreateShouldBeAtomicForConcurrentCallers() throws Exception {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setThreadNamePrefix("concurrent");
        int callerCount = 32;
        ExecutorService callers = Executors.newFixedThreadPool(callerCount);
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<ExecutorService>> futures = new ArrayList<>();
            for (int index = 0; index < callerCount; index++) {
                futures.add(callers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    return manager.getOrCreate("sharedPool", config);
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            ExecutorService expected = futures.get(0).get(2, TimeUnit.SECONDS);
            for (Future<ExecutorService> future : futures) {
                assertSame(expected, future.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, manager.getPools().size());
        } finally {
            callers.shutdownNow();
        }
    }

    /**
     * 验证显式创建同名线程池时不会覆盖旧实例并泄漏线程资源。
     */
    @Test
    void createShouldRejectDuplicatePoolName() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        ThreadPoolExecutor existing = manager.create("duplicatePool", config);

        BaseException exception = assertThrows(
                BaseException.class,
                () -> manager.create("duplicatePool", config)
        );

        assertEquals("THREAD_002", exception.getCode());
        assertSame(existing, manager.get("duplicatePool"));
        assertFalse(existing.isShutdown());
    }

    /**
     * 验证非法线程池配置通过模块统一异常暴露稳定错误码。
     */
    @Test
    void createShouldRejectInvalidPoolConfiguration() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setCorePoolSize(10);
        config.setMaxPoolSize(5);

        BaseException exception = assertThrows(
                BaseException.class,
                () -> manager.create("invalidPool", config)
        );

        assertEquals("THREAD_001", exception.getCode());
    }

    /**
     * 验证扩容时会先提升最大线程数，避免核心线程数短暂超过旧最大值。
     */
    @Test
    void resizeShouldSupportGrowingCoreAndMaximumPoolSizeTogether() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setCorePoolSize(2);
        config.setMaxPoolSize(4);
        manager.create("resizablePool", config);

        assertDoesNotThrow(() -> manager.resize("resizablePool", 6, 8));

        ThreadPoolExecutor executor = manager.get("resizablePool");
        assertEquals(6, executor.getCorePoolSize());
        assertEquals(8, executor.getMaximumPoolSize());
    }

    /**
     * 验证对不存在的线程池执行调整时返回模块统一错误。
     */
    @Test
    void resizeShouldRejectMissingPool() {
        BaseException exception = assertThrows(
                BaseException.class,
                () -> manager.resize("missingPool", 1, 2)
        );

        assertEquals("THREAD_003", exception.getCode());
    }

    /**
     * 验证线程池注册表只以不可修改快照形式对外暴露。
     */
    @Test
    void getPoolsShouldReturnImmutableSnapshot() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        ThreadPoolExecutor executor = manager.create("snapshotPool", config);
        Map<String, ThreadPoolExecutor> snapshot = manager.getPools();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        manager.shutdown("snapshotPool");

        assertSame(executor, snapshot.get("snapshotPool"));
        assertTrue(manager.getPools().isEmpty());
    }

    /**
     * 验证关闭线程池后，同名的后续获取会创建一个全新的可用实例。
     */
    @Test
    void getOrCreateShouldRecreatePoolAfterShutdown() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        ExecutorService first = manager.getOrCreate("restartablePool", config);

        manager.shutdown("restartablePool");
        ExecutorService second = manager.getOrCreate("restartablePool", config);

        assertTrue(first.isShutdown());
        assertFalse(second.isShutdown());
        assertNotSame(first, second);
    }

    /**
     * 验证管理器支持标准关闭协议，便于 Spring 和独立应用统一释放线程池。
     *
     * @throws Exception 当标准关闭协议执行失败时抛出
     */
    @Test
    void managerShouldImplementAutoCloseableLifecycle() throws Exception {
        ExecutorService executor = manager.getOrCreate(
                "closeablePool",
                new ThreadPoolProperties.PoolConfig()
        );

        assertTrue(manager instanceof AutoCloseable);
        ((AutoCloseable) manager).close();

        assertTrue(executor.isShutdown());
        assertTrue(manager.getPools().isEmpty());
    }

    /**
     * 验证开启虚拟线程配置后，在 Java 17 等不支持虚拟线程的环境中会复用降级后的平台线程池。
     *
     * <p>如果只检查虚拟线程注册表，降级线程池会被放入普通线程池注册表，后续调用可能重复创建。
     * 该用例用于保证降级路径也满足 get-or-create 语义。</p>
     */
    @Test
    void getOrCreateShouldReuseVirtualFallbackPool() {
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setVirtualThreads(true);

        ExecutorService first = assertDoesNotThrow(() -> manager.getOrCreate("virtualPool", config));
        ExecutorService second = manager.getOrCreate("virtualPool", config);

        assertSame(first, second);
    }

    /**
     * 验证不支持虚拟线程时，降级平台线程池仍然遵循用户提供的容量和命名配置。
     */
    @Test
    void virtualThreadFallbackShouldHonorPoolConfiguration() {
        assumeFalse(ThreadUtil.isVirtualThreadsSupported());
        ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
        config.setCorePoolSize(3);
        config.setMaxPoolSize(7);
        config.setQueueCapacity(11);
        config.setThreadNamePrefix("fallback");
        config.setKeepAliveSeconds(23);
        config.setVirtualThreads(true);

        ExecutorService service = manager.getOrCreate("virtualPool", config);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) service;

        assertEquals(3, executor.getCorePoolSize());
        assertEquals(7, executor.getMaximumPoolSize());
        assertEquals(11, executor.getQueue().remainingCapacity());
        assertEquals(23, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertTrue(executor.getThreadFactory().newThread(() -> {
        }).getName().startsWith("fallback-"));
    }
}
