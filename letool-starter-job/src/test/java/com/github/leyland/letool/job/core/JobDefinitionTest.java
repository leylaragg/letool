package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link JobDefinition} 不可变任务定义测试。
 */
class JobDefinitionTest {

    /**
     * 验证完整定义能够保留生产调度所需的全部元数据。
     */
    @Test
    void shouldBuildCompleteImmutableDefinition() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("tenant", "default");

        JobDefinition definition = JobDefinition.builder()
                .jobName("daily-report")
                .cron("0 0 6 * * ?")
                .zone("Asia/Shanghai")
                .description("每日报表")
                .shardTotal(4)
                .maxRetries(2)
                .backoffMs(1_000)
                .backoffMultiplier(2.0)
                .maxBackoffMs(60_000)
                .concurrent(false)
                .misfirePolicy(MisfirePolicy.DO_NOTHING)
                .requestRecovery(true)
                .params(parameters)
                .build();

        parameters.put("tenant", "changed");

        assertThat(definition.getJobName()).isEqualTo("daily-report");
        assertThat(definition.getCron()).isEqualTo("0 0 6 * * ?");
        assertThat(definition.getZone()).isEqualTo("Asia/Shanghai");
        assertThat(definition.getShardTotal()).isEqualTo(4);
        assertThat(definition.getMaxRetries()).isEqualTo(2);
        assertThat(definition.getBackoffMs()).isEqualTo(1_000);
        assertThat(definition.getBackoffMultiplier()).isEqualTo(2.0);
        assertThat(definition.getMaxBackoffMs()).isEqualTo(60_000);
        assertThat(definition.isConcurrent()).isFalse();
        assertThat(definition.getMisfirePolicy()).isEqualTo(MisfirePolicy.DO_NOTHING);
        assertThat(definition.isRequestRecovery()).isTrue();
        assertThat(definition.getParams()).containsExactly(Map.entry("tenant", "default"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> definition.getParams().put("new", "value"));
    }

    /**
     * 验证危险或不可持久化的任务定义会在构建阶段失败。
     */
    @Test
    void shouldRejectInvalidDefinition() {
        assertThatIllegalArgumentException().isThrownBy(() -> JobDefinition.builder().jobName(" ").build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().cron("invalid").build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().zone("Mars/Base").build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().shardTotal(0).build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().maxRetries(-1).build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().backoffMs(-1).build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().backoffMultiplier(0).build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().maxBackoffMs(0).build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().param("letool.internal.key", "x").build());
        assertThatIllegalArgumentException().isThrownBy(() -> validBuilder().param("key", null).build());
    }

    /**
     * 创建只包含必填字段的有效任务定义建造器。
     *
     * @return 有效任务定义建造器
     */
    private JobDefinition.Builder validBuilder() {
        return JobDefinition.builder().jobName("valid-job");
    }
}
