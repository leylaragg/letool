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

@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "letool.thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    @Bean
    public ThreadPoolManager threadPoolManager() {
        return new ThreadPoolManager();
    }

    @Bean
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean
    public ThreadPoolMonitor threadPoolMonitor(ThreadPoolManager manager, ThreadPoolProperties properties) {
        return new ThreadPoolMonitor(manager, properties.getMonitoring().isEnabled());
    }

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
