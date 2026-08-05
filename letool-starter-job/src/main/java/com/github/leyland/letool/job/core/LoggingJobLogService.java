package com.github.leyland.letool.job.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 向独立 {@code letool.job} logger 输出安全结构化摘要的默认实现。
 */
public class LoggingJobLogService implements JobLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger("letool.job");

    /**
     * 输出不包含业务参数的执行摘要。
     *
     * @param record 不可变执行记录
     */
    @Override
    public void record(JobExecutionRecord record) {
        LOGGER.info(
                "jobName={}, executionId={}, shard={}/{}, triggerType={}, retryCount={}, status={}, "
                        + "schedulerInstanceId={}, durationMs={}, error={}",
                record.getJobName(),
                record.getExecutionId(),
                record.getShardIndex(),
                record.getShardTotal(),
                record.getTriggerType(),
                record.getRetryCount(),
                record.getStatus(),
                record.getSchedulerInstanceId(),
                record.getDurationMs(),
                record.getErrorMessage());
    }
}
