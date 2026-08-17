package io.github.leylaragg.letool.thread.pool;

import io.github.leylaragg.letool.thread.config.ThreadPoolProperties;
import io.github.leylaragg.letool.thread.exception.ThreadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
public class ThreadPoolManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolManager.class);

    /** 全部执行器的统一注册表，虚拟线程池和平台线程池共享名称空间。 */
    private final ConcurrentMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    /** 协调单池操作与全量关闭，保证全量关闭返回时旧执行器均已移出注册表。 */
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

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
     * @throws ThreadException 当配置不合法或同名线程池已经存在时抛出
     */
    public ThreadPoolExecutor create(String name, ThreadPoolProperties.PoolConfig config) {
        validate(name, config);
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            ThreadPoolExecutor executor = createPlatformExecutor(config);
            ExecutorService existing = executors.putIfAbsent(name, executor);
            if (existing != null) {
                executor.shutdown();
                throw ThreadException.poolAlreadyExists();
            }
            log.info("Thread pool '{}' created: core={}, max={}, queue={}",
                    name, config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
            return executor;
        } finally {
            readLock.unlock();
        }
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
     * @throws ThreadException 当名称或配置不合法时抛出
     */
    public ExecutorService getOrCreate(String name, ThreadPoolProperties.PoolConfig config) {
        validate(name, config);
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            return executors.compute(name, (poolName, existing) -> {
                if (isAvailable(existing)) {
                    return existing;
                }
                ExecutorService created = config.isVirtualThreads()
                        ? createVirtualExecutor(poolName, config)
                        : createPlatformExecutor(config);
                log.info("Thread pool '{}' created: core={}, max={}, queue={}, virtual={}",
                        poolName,
                        config.getCorePoolSize(),
                        config.getMaxPoolSize(),
                        config.getQueueCapacity(),
                        config.isVirtualThreads());
                return created;
            });
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 尝试创建虚拟线程执行器。
     *
     * <p>通过反射调用 {@code Executors.newVirtualThreadPerTaskExecutor()}，
     * 如果 JDK 不支持虚拟线程（Java 17 以下），则自动降级为平台线程池。</p>
     *
     * @param name 线程池名称
     * @param config 降级为平台线程池时使用的配置
     * @return 虚拟线程或平台线程执行器
     */
    private ExecutorService createVirtualExecutor(
            String name,
            ThreadPoolProperties.PoolConfig config) {
        try {
            ExecutorService executor = (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
            log.info("Virtual thread executor '{}' created (Java 21+)", name);
            return executor;
        } catch (ReflectiveOperationException | SecurityException exception) {
            log.warn("Virtual threads not available (requires Java 21+), falling back to platform threads for '{}'", name);
            return createPlatformExecutor(config);
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
        ExecutorService executor = executors.get(name);
        return executor instanceof ThreadPoolExecutor
                ? (ThreadPoolExecutor) executor
                : null;
    }

    /**
     * 按名称获取任意类型的执行器。
     *
     * <p>与 {@link #get(String)} 不同，该方法也可以返回纯虚拟线程执行器。</p>
     *
     * @param name 线程池名称
     * @return 已注册执行器，未找到时返回 {@code null}
     */
    public ExecutorService getExecutor(String name) {
        return executors.get(name);
    }

    /**
     * 动态调整线程池的核心线程数和最大线程数。
     *
     * <p>仅对平台线程池生效，虚拟线程池不支持调整。</p>
     *
     * @param name         线程池名称
     * @param corePoolSize 新的核心线程数
     * @param maxPoolSize  新的最大线程数
     * @throws ThreadException 当参数不合法或线程池不存在、不可调整时抛出
     */
    public void resize(String name, int corePoolSize, int maxPoolSize) {
        validatePoolSizes(corePoolSize, maxPoolSize);
        ThreadPoolExecutor executor = get(name);
        if (executor == null) {
            throw ThreadException.poolNotFound();
        }
        synchronized (executor) {
            int currentMaximum = executor.getMaximumPoolSize();
            if (maxPoolSize > currentMaximum) {
                // 扩容时必须先提升最大线程数，避免新核心线程数超过旧最大值。
                executor.setMaximumPoolSize(maxPoolSize);
            }
            executor.setCorePoolSize(corePoolSize);
            if (maxPoolSize < currentMaximum) {
                // 缩容时必须先降低核心线程数，避免核心线程数暂时超过新最大值。
                executor.setMaximumPoolSize(maxPoolSize);
            }
        }
        log.info("Thread pool '{}' resized: core={}, max={}", name, corePoolSize, maxPoolSize);
    }

    /**
     * 关闭指定名称的线程池（包括平台和虚拟线程池）。
     *
     * @param name 线程池名称
     */
    public void shutdown(String name) {
        validateName(name);
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            executors.computeIfPresent(name, (poolName, executor) -> {
                executor.shutdown();
                log.info("Thread pool '{}' shutdown", poolName);
                return null;
            });
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 关闭所有已注册的线程池。
     */
    public void shutdownAll() {
        Lock writeLock = lifecycleLock.writeLock();
        writeLock.lock();
        try {
            executors.forEach((name, executor) -> {
                executor.shutdown();
                log.info("Thread pool '{}' shutdown", name);
            });
            executors.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 通过标准关闭协议释放全部已注册线程池。
     *
     * <p>Spring 容器和独立应用都可以自动识别该协议，无需额外维护专用销毁适配器。</p>
     */
    @Override
    public void close() {
        shutdownAll();
    }

    /**
     * 获取所有平台线程池的不可修改快照。
     *
     * <p>返回值不会随注册表后续变化而变化，调用方也无法修改内部注册状态。</p>
     *
     * @return 所有平台线程池的不可修改快照
     */
    public Map<String, ThreadPoolExecutor> getPools() {
        Map<String, ThreadPoolExecutor> snapshot = new LinkedHashMap<>();
        executors.forEach((name, executor) -> {
            if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
                snapshot.put(name, threadPoolExecutor);
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * 创建配置指定的平台线程池，但不写入注册表。
     *
     * @param config 已校验的线程池配置
     * @return 尚未注册的平台线程池
     */
    private ThreadPoolExecutor createPlatformExecutor(ThreadPoolProperties.PoolConfig config) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new NamedThreadFactory(config.getThreadNamePrefix()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(config.isAllowCoreThreadTimeout());
        return executor;
    }

    /**
     * 判断已注册执行器是否仍可复用。
     *
     * @param executor 待检查执行器
     * @return 执行器存在且尚未进入关闭流程时返回 {@code true}
     */
    private boolean isAvailable(ExecutorService executor) {
        return executor != null && !executor.isShutdown() && !executor.isTerminated();
    }

    /**
     * 校验线程池名称和完整创建配置。
     *
     * @param name 线程池名称
     * @param config 线程池配置
     * @throws ThreadException 当任一参数不合法时抛出
     */
    private void validate(String name, ThreadPoolProperties.PoolConfig config) {
        validateName(name);
        if (config == null) {
            throw ThreadException.configurationInvalid("config");
        }
        validatePoolSizes(config.getCorePoolSize(), config.getMaxPoolSize());
        if (config.getQueueCapacity() <= 0) {
            throw ThreadException.configurationInvalid("queueCapacity");
        }
        if (config.getKeepAliveSeconds() <= 0) {
            throw ThreadException.configurationInvalid("keepAliveSeconds");
        }
        if (config.getThreadNamePrefix() == null
                || config.getThreadNamePrefix().isBlank()) {
            throw ThreadException.configurationInvalid("threadNamePrefix");
        }
    }

    /**
     * 校验线程池名称。
     *
     * @param name 线程池名称
     * @throws ThreadException 当名称为空白时抛出
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ThreadException.configurationInvalid("name");
        }
    }

    /**
     * 校验核心线程数和最大线程数的组合关系。
     *
     * @param corePoolSize 核心线程数
     * @param maxPoolSize 最大线程数
     * @throws ThreadException 当线程数范围或大小关系不合法时抛出
     */
    private void validatePoolSizes(int corePoolSize, int maxPoolSize) {
        if (corePoolSize < 0) {
            throw ThreadException.configurationInvalid("corePoolSize");
        }
        if (maxPoolSize <= 0 || corePoolSize > maxPoolSize) {
            throw ThreadException.configurationInvalid("maxPoolSize");
        }
    }
}
