package com.github.leyland.letool.job.annotation;

import java.lang.annotation.*;

/**
 * 任务处理方法注解——标记任务类中实际执行任务逻辑的方法.
 *
 * <p>此注解与 {@link LetoolJob} 配合使用：</p>
 * <ul>
 *   <li>{@link LetoolJob} 标注在类上，声明该类为一个任务</li>
 *   <li>{@code @JobHandler} 标注在方法上，标记具体执行逻辑</li>
 * </ul>
 *
 * <p>被标注的方法必须接收 {@link com.github.leyland.letool.job.core.JobContext} 作为唯一参数，
 * 返回值为 {@code void}. 框架会自动将标记的方法包装为
 * {@link com.github.leyland.letool.job.core.JobHandler} 接口实例.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @LetoolJob(name = "dataSyncJob", cron = "0 0/5 * * * ?")
 * public class DataSyncJob {
 *
 *     @JobHandler
 *     public void execute(JobContext context) {
 *         // 根据分片信息进行数据同步
 *         int shardIndex = context.getShardIndex();
 *         int shardTotal = context.getShardTotal();
 *         syncService.syncByShard(shardIndex, shardTotal);
 *     }
 * }
 * }</pre>
 *
 * <p>注意：一个任务类中只应有一个 {@code @JobHandler} 方法.
 * 如果存在多个，框架仅使用第一个找到的方法.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see LetoolJob
 * @see com.github.leyland.letool.job.core.JobHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobHandler {
}
