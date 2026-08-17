package io.github.leylaragg.letool.sample.job;

import io.github.leylaragg.letool.job.annotation.JobHandler;
import io.github.leylaragg.letool.job.annotation.LetoolJob;
import io.github.leylaragg.letool.job.core.JobContext;
import io.github.leylaragg.letool.job.core.MisfirePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 演示注解注册、分片上下文和失败重试配置的示例任务。
 */
@Component
@LetoolJob(
        name = "sampleShardJob",
        cron = "0 0 2 * * ?",
        zone = "Asia/Shanghai",
        description = "Letool 分片任务示例",
        shardTotal = 2,
        maxRetries = 2,
        backoffMs = 1_000,
        backoffMultiplier = 2.0,
        maxBackoffMs = 10_000,
        concurrent = false,
        misfirePolicy = MisfirePolicy.DO_NOTHING)
public class SampleShardJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleShardJob.class);

    /**
     * 执行当前 Quartz 分配的任务分片。
     *
     * @param context 不可变任务执行上下文
     */
    @JobHandler
    public void execute(JobContext context) {
        LOGGER.info(
                "执行示例任务: executionId={}, shard={}/{}, triggerType={}, retryCount={}",
                context.getExecutionId(),
                context.getShardIndex(),
                context.getShardTotal(),
                context.getTriggerType(),
                context.getRetryCount());
    }
}
