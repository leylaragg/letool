package io.github.leylaragg.letool.job.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz 调用 Letool 运行时的分发基类。
 *
 * <p>运行时对象保存在当前节点 SchedulerContext，不进入持久化 JobDataMap。</p>
 */
public abstract class AbstractQuartzDispatchJob implements Job {

    /**
     * 执行一次 Quartz 触发。
     *
     * @param context Quartz 执行上下文
     * @throws JobExecutionException 运行时尚未初始化或执行失败时抛出
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            Object runtime = context.getScheduler().getContext().get(JobRuntime.SCHEDULER_CONTEXT_KEY);
            if (!(runtime instanceof JobRuntime jobRuntime)) {
                throw new JobExecutionException("当前调度节点未初始化 Letool JobRuntime");
            }
            jobRuntime.execute(context);
        } catch (org.quartz.SchedulerException exception) {
            throw new JobExecutionException("读取 Letool JobRuntime 失败", exception);
        }
    }
}
