package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobExecutionRecord} 执行记录测试。
 */
class JobExecutionRecordTest {

    /**
     * 验证执行记录根据上下文计算耗时并区分重试中间状态。
     */
    @Test
    void shouldCreateRetryScheduledRecord() {
        Instant started = Instant.parse("2026-08-05T08:00:00Z");
        JobContext context = new JobContext(
                "execution-1", "sync", 0, 2, 1, JobTriggerType.RETRY,
                started.minusSeconds(1), started, "fire-1", "node-a", Map.of());

        JobExecutionRecord record = JobExecutionRecord.retryScheduled(
                context, started.plusMillis(250), "temporary failure");

        assertThat(record.getStatus()).isEqualTo(JobStatus.RETRY_SCHEDULED);
        assertThat(record.getDurationMs()).isEqualTo(250);
        assertThat(record.getExecutionId()).isEqualTo("execution-1");
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getScheduledFireTime()).isEqualTo(started.minusSeconds(1));
        assertThat(record.getErrorMessage()).isEqualTo("temporary failure");
    }
}
