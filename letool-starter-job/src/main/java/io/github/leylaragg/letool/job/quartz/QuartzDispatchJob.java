package io.github.leylaragg.letool.job.quartz;

import org.quartz.DisallowConcurrentExecution;

/**
 * 默认禁止同一 Quartz JobKey 并发执行的分发 Job。
 */
@DisallowConcurrentExecution
public final class QuartzDispatchJob extends AbstractQuartzDispatchJob {
}
