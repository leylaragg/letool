package com.github.leyland.letool.job.annotation;

import com.github.leyland.letool.job.core.MisfirePolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个由 Letool 注册到 Quartz 的 Spring 任务 Bean。
 *
 * <p>任务类必须是 Spring Bean，并且必须且只能有一个
 * {@link JobHandler} 处理方法。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LetoolJob {

    /** @return 全局唯一逻辑任务名称 */
    String name();

    /** @return Quartz Cron；空字符串表示仅手动触发 */
    String cron() default "";

    /** @return 时区 ID；空字符串表示使用 Quartz 默认时区 */
    String zone() default "";

    /** @return 任务说明 */
    String description() default "";

    /** @return 分片总数 */
    int shardTotal() default 1;

    /** @return 最大额外重试次数 */
    int maxRetries() default 0;

    /** @return 第一次重试延迟毫秒数 */
    long backoffMs() default 1_000;

    /** @return 重试退避倍率 */
    double backoffMultiplier() default 2.0;

    /** @return 单次重试最大延迟毫秒数 */
    long maxBackoffMs() default 60_000;

    /** @return 是否允许同一分片并发执行 */
    boolean concurrent() default false;

    /** @return Cron 错过触发策略 */
    MisfirePolicy misfirePolicy() default MisfirePolicy.DO_NOTHING;

    /** @return 是否请求 Quartz 在节点故障后恢复执行 */
    boolean requestRecovery() default false;
}
