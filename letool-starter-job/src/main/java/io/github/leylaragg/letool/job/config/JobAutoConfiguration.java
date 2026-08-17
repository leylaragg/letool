package io.github.leylaragg.letool.job.config;

import io.github.leylaragg.letool.job.core.DefaultJobHandlerRegistry;
import io.github.leylaragg.letool.job.core.JobHandlerRegistry;
import io.github.leylaragg.letool.job.core.JobLogService;
import io.github.leylaragg.letool.job.core.JobScheduler;
import io.github.leylaragg.letool.job.core.LoggingJobLogService;
import io.github.leylaragg.letool.job.quartz.JobRuntime;
import io.github.leylaragg.letool.job.quartz.LetoolJobRegistrar;
import io.github.leylaragg.letool.job.quartz.QuartzJobMapper;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 基于 Spring Boot Quartz 的 Letool Job 自动配置。
 */
@AutoConfiguration(after = QuartzAutoConfiguration.class)
@ConditionalOnClass(Scheduler.class)
@ConditionalOnBean(Scheduler.class)
@ConditionalOnProperty(prefix = "letool.job", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JobProperties.class)
public class JobAutoConfiguration {

    /**
     * 创建默认任务处理器注册表。
     *
     * @return 默认注册表
     */
    @Bean
    @ConditionalOnMissingBean(JobHandlerRegistry.class)
    public JobHandlerRegistry jobHandlerRegistry() {
        return new DefaultJobHandlerRegistry();
    }

    /**
     * 创建 Quartz 元数据映射器。
     *
     * @param properties Job 配置
     * @return Quartz 映射器
     */
    @Bean
    @ConditionalOnMissingBean(QuartzJobMapper.class)
    public QuartzJobMapper quartzJobMapper(JobProperties properties) {
        return new QuartzJobMapper(properties.getGroup());
    }

    /**
     * 创建 Quartz 便捷门面。
     *
     * @param scheduler 原生 Quartz 调度器
     * @param mapper Quartz 映射器
     * @param handlerRegistry 处理器注册表
     * @param beanFactory Spring Bean 查询入口
     * @return Job 调度门面
     */
    @Bean
    @ConditionalOnMissingBean(JobScheduler.class)
    public JobScheduler jobScheduler(
            Scheduler scheduler,
            QuartzJobMapper mapper,
            JobHandlerRegistry handlerRegistry,
            ListableBeanFactory beanFactory) {
        return new JobScheduler(scheduler, mapper, handlerRegistry, beanFactory);
    }

    /**
     * 创建默认结构化日志扩展。
     *
     * @return 默认日志扩展
     */
    @Bean(name = "loggingJobLogService")
    @ConditionalOnMissingBean(name = "loggingJobLogService")
    @ConditionalOnProperty(
            prefix = "letool.job.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingJobLogService loggingJobLogService() {
        return new LoggingJobLogService();
    }

    /**
     * 创建当前节点任务执行运行时。
     *
     * @param handlerRegistry 处理器注册表
     * @param logServices 用户和默认日志扩展
     * @param properties Job 配置
     * @return 任务运行时
     */
    @Bean
    @ConditionalOnMissingBean(JobRuntime.class)
    public JobRuntime jobRuntime(
            JobHandlerRegistry handlerRegistry,
            ObjectProvider<JobLogService> logServices,
            JobProperties properties) {
        List<JobLogService> orderedServices = logServices.orderedStream().toList();
        return new JobRuntime(handlerRegistry, orderedServices, properties);
    }

    /**
     * 在 Quartz 启动前把不可持久化运行时放入本地 SchedulerContext。
     *
     * @param scheduler 原生 Quartz 调度器
     * @param runtime 当前节点任务运行时
     * @return 单例初始化回调
     */
    @Bean(name = "letoolJobRuntimeInitializer")
    @ConditionalOnMissingBean(name = "letoolJobRuntimeInitializer")
    public SmartInitializingSingleton letoolJobRuntimeInitializer(
            Scheduler scheduler,
            JobRuntime runtime) {
        return () -> {
            try {
                scheduler.getContext().put(JobRuntime.SCHEDULER_CONTEXT_KEY, runtime);
            } catch (SchedulerException exception) {
                throw new IllegalStateException("初始化 Letool JobRuntime 失败", exception);
            }
        };
    }

    /**
     * 创建注解任务注册器。
     *
     * @param beanFactory Spring Bean 查询入口
     * @param handlerRegistry 处理器注册表
     * @param jobScheduler Job 调度门面
     * @return 注解任务注册器
     */
    @Bean
    @ConditionalOnMissingBean(LetoolJobRegistrar.class)
    public LetoolJobRegistrar letoolJobRegistrar(
            ListableBeanFactory beanFactory,
            JobHandlerRegistry handlerRegistry,
            JobScheduler jobScheduler) {
        return new LetoolJobRegistrar(beanFactory, handlerRegistry, jobScheduler::register);
    }
}
