package com.github.leyland.letool.thread.pool;

import com.github.leyland.letool.thread.config.ThreadPoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 线程池管理器，负责线程池的创建、注册、动态调整和销毁。
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li><b>线程池注册</b> — 所有线程池通过名称注册在 {@link ConcurrentHashMap} 中，全局可查</li>
 *   <li><b>虚拟线程支持</b> — Java 21+ 自动使用虚拟线程，不可用则降级为平台线程</li>
 *   <li><b>动态调整</b> — 运行时修改核心/最大线程数，无需重启</li>
 *   <li><b>优雅关闭</b> — 支持单个或全部线程池的 {@code shutdown()}</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ThreadPoolManager manager = new ThreadPoolManager();
 * ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
 * config.setCorePoolSize(10);
 * config.setMaxPoolSize(50);
 *
 * ExecutorService pool = manager.getOrCreate("orderPool", config);
 * manager.resize("orderPool", 20, 100);
 * manager.shutdown("orderPool");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ThreadPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolManager.class);

    /** 平台线程池注册表（名称 → ThreadPoolExecutor） */
    private final Map<String, ThreadPoolExecutor> pools = new ConcurrentHashMap<>();

    /** 虚拟线程池注册表（名称 → ExecutorService），键值可能是 ThreadPoolExecutor（降级场景） */
    private final Map<String, ExecutorService> virtualPools = new ConcurrentHashMap<>();

    /**
     * 根据配置创建平台线程池并注册。
     *
     * <p>使用 {@link LinkedBlockingQueue} 作为任务队列，
     * {@link ThreadPoolExecutor.CallerRunsPolicy} 作为拒绝策略，
     * 且允许核心线程超时回收。</p>
     *
     * @param name   线程池名称
     * @param config 线程池配置
     * @return 创建的 ThreadPoolExecutor
     */
    public ThreadPoolExecutor create(String name, ThreadPoolProperties.PoolConfig config) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new NamedThreadFactory(config.getThreadNamePrefix()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        pools.put(name, executor);
        log.info("Thread pool '{}' created: core={}, max={}, queue={}",
                name, config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
        return executor;
    }

    /**
     * 获取或创建线程池。
     *
     * <p>如果配置了 {@code virtualThreads=true} 且 JDK 支持，则创建虚拟线程池；
     * 否则创建标准平台线程池。已存在的线程池直接返回。</p>
     *
     * @param name   线程池名称
     * @param config 线程池配置
     * @return ExecutorService 实例
     */
    public synchronized ExecutorService getOrCreate(String name, ThreadPoolProperties.PoolConfig config) {
        if (config.isVirtualThreads()) {
            ExecutorService existing = virtualPools.get(name);
            if (existing != null) {
                return existing;
            }
            existing = pools.get(name);
            if (existing != null) {
                return existing;
            }
            return createVirtualExecutor(name);
        }
        ThreadPoolExecutor existing = pools.get(name);
        if (existing != null) {
            return existing;
        }
        return create(name, config);
    }

    /**
     * 尝试创建虚拟线程执行器。
     *
     * <p>通过反射调用 {@code Executors.newVirtualThreadPerTaskExecutor()}，
     * 如果 JDK 不支持虚拟线程（Java 17 以下），则自动降级为平台线程池。</p>
     *
     * @param name 线程池名称
     * @return 虚拟线程或平台线程执行器
     */
    private ExecutorService createVirtualExecutor(String name) {
        try {
            ExecutorService executor = (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
            virtualPools.put(name, executor);
            log.info("Virtual thread executor '{}' created (Java 21+)", name);
            return executor;
        } catch (Exception e) {
            log.warn("Virtual threads not available (requires Java 21+), falling back to platform threads for '{}'", name);
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    10, 200, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1000),
                    new NamedThreadFactory("vt-fallback-" + name + "-"),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            pools.put(name, executor);
            return executor;
        }
    }

    /**
     * 按名称获取线程池。
     *
     * <p>先从平台线程池查找，再尝试虚拟线程池（仅当降级为 ThreadPoolExecutor 时）。</p>
     *
     * @param name 线程池名称
     * @return ThreadPoolExecutor 实例，未找到或为纯虚拟线程池时返回 {@code null}
     */
    public ThreadPoolExecutor get(String name) {
        ThreadPoolExecutor executor = pools.get(name);
        if (executor == null) {
            ExecutorService vt = virtualPools.get(name);
            if (vt instanceof ThreadPoolExecutor) {
                return (ThreadPoolExecutor) vt;
            }
        }
        return executor;
    }

    /**
     * 动态调整线程池的核心线程数和最大线程数。
     *
     * <p>仅对平台线程池生效，虚拟线程池不支持调整。</p>
     *
     * @param name         线程池名称
     * @param corePoolSize 新的核心线程数
     * @param maxPoolSize  新的最大线程数
     */
    public void resize(String name, int corePoolSize, int maxPoolSize) {
        ThreadPoolExecutor executor = pools.get(name);
        if (executor != null) {
            executor.setCorePoolSize(corePoolSize);
            executor.setMaximumPoolSize(maxPoolSize);
            log.info("Thread pool '{}' resized: core={}, max={}", name, corePoolSize, maxPoolSize);
        }
    }

    /**
     * 关闭指定名称的线程池（包括平台和虚拟线程池）。
     *
     * @param name 线程池名称
     */
    public void shutdown(String name) {
        ThreadPoolExecutor executor = pools.remove(name);
        if (executor != null) {
            executor.shutdown();
            log.info("Thread pool '{}' shutdown", name);
        }
        ExecutorService vt = virtualPools.remove(name);
        if (vt != null) {
            vt.shutdown();
        }
    }

    /**
     * 关闭所有已注册的线程池。
     */
    public void shutdownAll() {
        pools.forEach((name, pool) -> { pool.shutdown(); log.info("Pool '{}' shutdown", name); });
        pools.clear();
        virtualPools.forEach((name, pool) -> { pool.shutdown(); });
        virtualPools.clear();
    }

    /** @return 所有平台线程池的不可修改视图 */
    public Map<String, ThreadPoolExecutor> getPools() { return pools; }
}
