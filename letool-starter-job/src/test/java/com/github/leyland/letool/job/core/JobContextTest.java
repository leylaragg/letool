package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link JobContext} 不可变执行上下文测试。
 */
class JobContextTest {

    /**
     * 验证上下文会防御性复制参数并保留集群执行标识。
     */
    @Test
    void shouldCreateImmutableExecutionContext() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("tenant", "default");
        Instant scheduled = Instant.parse("2026-08-05T08:00:00Z");
        Instant started = Instant.parse("2026-08-05T08:00:01Z");

        JobContext context = new JobContext(
                "execution-1", "sync", 1, 4, 2, JobTriggerType.RETRY,
                scheduled, started, "fire-1", "node-a", parameters);
        parameters.put("tenant", "changed");

        assertThat(context.getExecutionId()).isEqualTo("execution-1");
        assertThat(context.getJobName()).isEqualTo("sync");
        assertThat(context.getShardIndex()).isEqualTo(1);
        assertThat(context.getShardTotal()).isEqualTo(4);
        assertThat(context.getRetryCount()).isEqualTo(2);
        assertThat(context.getTriggerType()).isEqualTo(JobTriggerType.RETRY);
        assertThat(context.getScheduledFireTime()).isEqualTo(scheduled);
        assertThat(context.getStartTime()).isEqualTo(started);
        assertThat(context.getFireInstanceId()).isEqualTo("fire-1");
        assertThat(context.getSchedulerInstanceId()).isEqualTo("node-a");
        assertThat(context.getParam("tenant")).isEqualTo("default");
        assertThat(context.getParam("tenant", String.class)).contains("default");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> context.getParams().put("new", "value"));
    }

    /**
     * 验证非法分片和空白标识不会进入业务处理器。
     */
    @Test
    void shouldRejectInvalidContext() {
        assertThatIllegalArgumentException().isThrownBy(() -> context("", 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> context("execution", -1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> context("execution", 1, 1));

        Map<String, String> invalidParameters = new LinkedHashMap<>();
        invalidParameters.put("tenant", null);
        assertThatIllegalArgumentException().isThrownBy(() -> new JobContext(
                "execution", "job", 0, 1, 0, JobTriggerType.MANUAL,
                Instant.now(), Instant.now(), "fire", "node", invalidParameters));
    }

    /**
     * 创建用于校验的执行上下文。
     *
     * @param executionId 执行标识
     * @param shardIndex 分片索引
     * @param shardTotal 分片总数
     * @return 执行上下文
     */
    private JobContext context(String executionId, int shardIndex, int shardTotal) {
        return new JobContext(
                executionId, "job", shardIndex, shardTotal, 0, JobTriggerType.MANUAL,
                Instant.now(), Instant.now(), "fire", "node", Map.of());
    }
}
