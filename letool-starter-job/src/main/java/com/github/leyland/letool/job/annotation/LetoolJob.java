package com.github.leyland.letool.job.annotation;

import java.lang.annotation.*;

/**
 * 任务标记注解——标注在任务类上，声明该类为一个可被调度器发现和管理的任务.
 *
 * <p>被此注解标记的类会被 Spring 组件扫描发现，并由自动配置类
 * 解析其属性，自动创建 {@link com.github.leyland.letool.job.core.JobDefinition}
 * 并注册到 {@link com.github.leyland.letool.job.core.JobScheduler}.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @LetoolJob(
 *     name = "dailyReportJob",
 *     cron = "0 0 6 * * ?",
 *     description = "每日报表生成任务",
 *     shardTotal = 4,
 *     maxRetries = 3
 * )
 * public class DailyReportJob {
 *
 *     @com.github.leyland.letool.job.annotation.JobHandler
 *     public void execute(JobContext context) {
 *         // 业务逻辑
 *     }
 * }
 * }</pre>
 *
 * <h3>注解属性说明</h3>
 * <ul>
 *   <li>{@link #name} — 任务名称（必填，全局唯一标识）</li>
 *   <li>{@link #cron} — Cron 表达式，为空则不自动调度</li>
 *   <li>{@link #description} — 任务描述文本</li>
 *   <li>{@link #shardTotal} — 总分片数</li>
 *   <li>{@link #maxRetries} — 最大重试次数</li>
 *   <li>{@link #backoffMs} — 退避基础时间（毫秒）</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.job.core.JobScheduler
 * @see com.github.leyland.letool.job.core.JobDefinition
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LetoolJob {

    // ======================== 注解属性 ========================

    /**
     * 任务名称（必填，全局唯一标识）.
     *
     * @return 任务名称
     */
    String name();

    /**
     * Cron 表达式.
     *
     * <p>支持标准 6 位或 7 位 cron 表达式. 为空字符串时，
     * 任务不会自动调度，需通过 API 手动触发.</p>
     *
     * @return Cron 表达式，默认为空字符串
     */
    String cron() default "";

    /**
     * 任务描述文本.
     *
     * @return 任务描述
     */
    String description() default "";

    /**
     * 总分片数.
     *
     * <p>当任务数据量大时，将任务拆分为多个分片并行处理.
     * 默认1表示不分片，每个实例处理全部数据.</p>
     *
     * @return 总分片数，默认1
     */
    int shardTotal() default 1;

    /**
     * 最大重试次数.
     *
     * <p>任务执行失败后自动重试的最大次数. 为0表示不重试.
     * 重试采用指数退避策略（参见 {@link com.github.leyland.letool.job.retry.RetryPolicy}）.</p>
     *
     * @return 最大重试次数，默认3
     */
    int maxRetries() default 3;

    /**
     * 重试退避基础时间（毫秒）.
     *
     * <p>第一次重试等待此毫秒数，后续重试按指数增长.</p>
     *
     * @return 基础退避毫秒数，默认1000
     */
    long backoffMs() default 1000;
}
