package io.github.leylaragg.letool.job.core;

/**
 * 定义任务实际执行逻辑的函数式接口。
 *
 * <p>编程式本地任务和 Spring Bean 任务都可以实现本接口，
 * 注解任务也可以通过 {@code @JobHandler} 方法由框架适配成本接口。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // Lambda 表达式
 * JobHandler handler = context ->
 *         System.out.println("执行任务：" + context.getJobName());
 * }</pre>
 *
 * <p>方法抛出的普通异常由运行时记录，并根据任务定义安排重试；
 * {@link Error} 等 JVM 严重错误不会被当作普通业务失败处理。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobContext
 * @see io.github.leylaragg.letool.job.core.JobScheduler
 */
@FunctionalInterface
public interface JobHandler {

    /**
     * 执行任务逻辑。
     *
     * <p>方法正常返回视为成功；抛出普通异常视为执行失败。</p>
     *
     * @param context 不可变任务执行上下文
     * @throws Exception 任务业务执行失败时抛出
     */
    void execute(JobContext context) throws Exception;
}
