package com.github.leyland.letool.job.core;

/**
 * 任务处理器——定义任务实际执行逻辑的函数式接口.
 *
 * <p>所有任务必须实现此接口的 {@link #execute(JobContext)} 方法，
 * 在该方法中编写核心业务逻辑. 支持 Lambda 表达式或方法引用简化实现.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Lambda 表达式
 * JobHandler handler = context -> {
 *     String param = (String) context.getParam("key");
 *     System.out.println("执行任务：" + context.getJobName());
 *     // 业务逻辑...
 * };
 *
 * // 方法引用
 * class MyService {
 *     public void doTask(JobContext context) { ... }
 * }
 * JobHandler handler = myService::doTask;
 * }</pre>
 *
 * <p>注意：execute 方法中抛出的异常会被 {@link JobScheduler} 捕获，
 * 并触发重试逻辑（参见 {@link com.github.leyland.letool.job.retry.RetryPolicy}）.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobContext
 * @see com.github.leyland.letool.job.core.JobScheduler
 */
@FunctionalInterface
public interface JobHandler {

    // ======================== 核心方法 ========================

    /**
     * 执行任务逻辑.
     *
     * <p>实现类在此方法中编写任务的核心业务逻辑. 方法执行完成后正常返回即视为成功；
     * 抛出异常则视为执行失败，由调度器捕获处理.</p>
     *
     * @param context 任务执行上下文，包含任务名、执行ID、分片信息、参数等
     * @throws Exception 任务执行失败时抛出，调度器根据重试策略决定是否重试
     */
    void execute(JobContext context) throws Exception;
}
