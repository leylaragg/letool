package com.github.leyland.letool.thread.pool;

import com.github.leyland.letool.thread.config.ThreadPoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

public class ThreadPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolManager.class);

    private final Map<String, ThreadPoolExecutor> pools = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> virtualPools = new ConcurrentHashMap<>();

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

    public ExecutorService getOrCreate(String name, ThreadPoolProperties.PoolConfig config) {
        if (config.isVirtualThreads()) {
            return virtualPools.computeIfAbsent(name, k -> createVirtualExecutor(name));
        }
        return pools.computeIfAbsent(name, k -> create(name, config));
    }

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

    public void resize(String name, int corePoolSize, int maxPoolSize) {
        ThreadPoolExecutor executor = pools.get(name);
        if (executor != null) {
            executor.setCorePoolSize(corePoolSize);
            executor.setMaximumPoolSize(maxPoolSize);
            log.info("Thread pool '{}' resized: core={}, max={}", name, corePoolSize, maxPoolSize);
        }
    }

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

    public void shutdownAll() {
        pools.forEach((name, pool) -> { pool.shutdown(); log.info("Pool '{}' shutdown", name); });
        pools.clear();
        virtualPools.forEach((name, pool) -> { pool.shutdown(); });
        virtualPools.clear();
    }

    public Map<String, ThreadPoolExecutor> getPools() { return pools; }
}
