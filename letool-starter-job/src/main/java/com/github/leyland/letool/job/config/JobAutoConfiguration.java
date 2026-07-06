package com.github.leyland.letool.job.config;

import com.github.leyland.letool.job.core.JobLogService;
import com.github.leyland.letool.job.core.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * letool-starter-job 自动配置——激活任务调度模块的所有核心组件.
 *
 * <p>当 {@code letool.job.enabled=true}（默认开启）时自动生效，注册以下 Bean：</p>
 * <ul>
 *   <li>{@link JobScheduler} — 任务调度器（核心）</li>
 *   <li>{@link JobLogService} — 任务日志服务</li>
 *   <li>{@link ScheduledThreadPoolExecutor} — 任务执行线程池</li>
 * </ul>
 *
 * <h3>激活方式</h3>
 * <p>引入 {@code letool-starter-job} 依赖后，通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 自动注册.
 * 无需额外配置即可使用.</p>
 *
 * <p>可通过以下配置关闭：</p>
 * <pre>{@code
 * letool:
 *   job:
 *     enabled: false
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobProperties
 * @see JobScheduler
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "letool.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobAutoConfiguration {

    // ======================== 常量 ========================

    private static final Logger log = LoggerFactory.getLogger(JobAutoConfiguration.class);

    // ======================== Bean 定义 ========================

    /**
     * 创建任务执行线程池.
     *
     * <p>根据配置的 {@code letool.job.thread-pool-size} 创建固定大小的调度线程池.
     * 线程以 {@code letool-job-} 为前缀命名.</p>
     *
     * @param properties 任务配置属性
     * @return 调度线程池实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "jobScheduledExecutor")
    public ScheduledThreadPoolExecutor jobScheduledExecutor(JobProperties properties) {
        int poolSize = properties.getThreadPoolSize();
        if (poolSize <= 0) {
            poolSize = 4;
        }
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(poolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "letool-job-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
        log.info("创建任务执行线程池，核心线程数: {}", poolSize);
        return executor;
    }

    /**
     * 创建任务日志服务.
     *
     * @return 日志服务实例
     */
    @Bean
    @ConditionalOnMissingBean(JobLogService.class)
    public JobLogService jobLogService() {
        return new JobLogService();
    }

    /**
     * 创建任务调度器（核心 Bean）.
     *
     * <p>调度器持有已注册任务列表和运行中任务实例，
     * 负责任务的注册、调度、暂停、恢复和手动触发.</p>
     *
     * @param jobScheduledExecutor 任务执行线程池
     * @param jobLogService        任务日志服务
     * @param properties           任务配置属性
     * @return 调度器实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(JobScheduler.class)
    public JobScheduler jobScheduler(ScheduledThreadPoolExecutor jobScheduledExecutor,
                                     JobLogService jobLogService,
                                     JobProperties properties) {
        log.info("初始化 letool-starter-job 任务调度模块");
        return new JobScheduler(jobScheduledExecutor, jobLogService, properties);
    }
}
