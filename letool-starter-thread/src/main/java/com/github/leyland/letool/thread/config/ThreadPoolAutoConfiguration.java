package com.github.leyland.letool.thread.config;

import com.github.leyland.letool.thread.monitor.ThreadPoolMonitor;
import com.github.leyland.letool.thread.pool.NamedThreadFactory;
import com.github.leyland.letool.thread.pool.ThreadPoolManager;
import com.github.leyland.letool.thread.propagation.MdcTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池模块自动配置类。
 *
 * <p>当配置 {@code letool.thread.enabled=true}（默认）时自动生效，注册以下 Bean：</p>
 * <ul>
 *   <li>{@link ThreadPoolManager} — 线程池管理器，全局注册和动态调整</li>
 *   <li>{@link MdcTaskDecorator} — MDC 上下文传播装饰器</li>
 *   <li>{@link ThreadPoolMonitor} — 线程池指标采集器</li>
 *   <li>{@code taskExecutor} — 默认任务执行器（CPU 密集型）</li>
 *   <li>{@code ioExecutor} — IO 执行器（IO 密集型，支持虚拟线程）</li>
 * </ul>
 *
 * <p>{@code taskExecutor} 和 {@code ioExecutor} 的默认配置分别来自
 * {@code letool.thread.pools.task-executor} 和 {@code letool.thread.pools.io-executor}，
 * 未配置时使用内置默认值。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "letool.thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    /**
     * 注册线程池管理器 Bean。
     *
     * @return ThreadPoolManager 实例
     */
    @Bean
    public ThreadPoolManager threadPoolManager() {
        return new ThreadPoolManager();
    }

    /**
     * 注册 MDC 上下文传播装饰器，用于 Spring {@code @Async} 线程池。
     *
     * @return MdcTaskDecorator 实例
     */
    @Bean
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * 注册线程池监控器。
     *
     * @param manager    线程池管理器
     * @param properties 线程池配置属性
     * @return ThreadPoolMonitor 实例
     */
    @Bean
    public ThreadPoolMonitor threadPoolMonitor(ThreadPoolManager manager, ThreadPoolProperties properties) {
        return new ThreadPoolMonitor(manager, properties.getMonitoring().isEnabled());
    }

    /**
     * 注册 {@code taskExecutor} Bean，适用于 CPU 密集型任务。
     *
     * <p>默认配置：core=10, max=50, queue=500。</p>
     *
     * @param manager    线程池管理器
     * @param properties 线程池配置属性
     * @return taskExecutor 线程池
     */
    @Bean("taskExecutor")
    public ExecutorService taskExecutor(ThreadPoolManager manager, ThreadPoolProperties properties) {
        ThreadPoolProperties.PoolConfig config = properties.getPools().get("task-executor");
        if (config == null) {
            config = new ThreadPoolProperties.PoolConfig();
            config.setCorePoolSize(10);
            config.setMaxPoolSize(50);
            config.setQueueCapacity(500);
            config.setThreadNamePrefix("task-");
            config.setKeepAliveSeconds(60);
        }
        log.info("Initializing 'taskExecutor': core={}, max={}, virtual={}",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.isVirtualThreads());
        return manager.getOrCreate("taskExecutor", config);
    }

    /**
     * 注册 {@code ioExecutor} Bean，适用于 IO 密集型任务。
     *
     * <p>默认配置：core=20, max=200, queue=1000。支持虚拟线程（Java 21+）。</p>
     *
     * @param manager    线程池管理器
     * @param properties 线程池配置属性
     * @return ioExecutor 线程池
     */
    @Bean("ioExecutor")
    public ExecutorService ioExecutor(ThreadPoolManager manager, ThreadPoolProperties properties) {
        ThreadPoolProperties.PoolConfig config = properties.getPools().get("io-executor");
        if (config == null) {
            config = new ThreadPoolProperties.PoolConfig();
            config.setCorePoolSize(20);
            config.setMaxPoolSize(200);
            config.setQueueCapacity(1000);
            config.setThreadNamePrefix("io-");
            config.setKeepAliveSeconds(60);
        }
        log.info("Initializing 'ioExecutor': core={}, max={}, virtual={}",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.isVirtualThreads());
        return manager.getOrCreate("ioExecutor", config);
    }
}
