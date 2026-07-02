package com.github.leyland.letool.thread.pool;

import com.github.leyland.letool.thread.config.ThreadPoolProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

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
     * <p>该用例覆盖 {@code ConcurrentHashMap.computeIfAbsent} 回调内部再次写入同一 Map
     * 导致的 recursive update 回归风险。</p>
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
}
