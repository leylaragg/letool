package io.github.leylaragg.letool.job.quartz;

import io.github.leylaragg.letool.job.core.JobDefinition;
import io.github.leylaragg.letool.job.core.MisfirePolicy;
import io.github.leylaragg.letool.job.core.JobTriggerType;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;

import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuartzJobMapper} Quartz 元数据映射测试。
 */
class QuartzJobMapperTest {

    private final QuartzJobMapper mapper = new QuartzJobMapper("letool");

    /**
     * 验证逻辑任务会映射为每分片一个 JobDetail 和 CronTrigger。
     */
    @Test
    void shouldMapEveryShardToStableQuartzKeys() {
        JobDefinition definition = definition("0 0 6 * * ?", MisfirePolicy.DO_NOTHING);

        List<JobDetail> details = mapper.createJobDetails(definition, "syncHandler");
        List<Trigger> triggers = mapper.createTriggers(definition);

        assertThat(details).hasSize(3);
        assertThat(details).extracting(detail -> detail.getKey().getName())
                .containsExactly("sync#0", "sync#1", "sync#2");
        assertThat(details).allSatisfy(detail -> {
            assertThat(detail.requestsRecovery()).isTrue();
            assertThat(detail.getJobDataMap().getString(JobDataKeys.FINGERPRINT)).isNotBlank();
            assertThat(detail.getJobDataMap().getString(JobDataKeys.HANDLER_BEAN_NAME)).isEqualTo("syncHandler");
            assertThat(detail.getJobDataMap().values())
                    .allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
        });
        assertThat(triggers).hasSize(3);
        assertThat(triggers).allSatisfy(trigger -> {
            CronTrigger cronTrigger = (CronTrigger) trigger;
            assertThat(cronTrigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone("Asia/Shanghai"));
            assertThat(cronTrigger.getMisfireInstruction())
                    .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        });
    }

    /**
     * 验证手动任务为 durable JobDetail，且立即补执行策略正确映射。
     */
    @Test
    void shouldMapManualAndFireOnceNowDefinitions() {
        JobDefinition manual = definition(null, MisfirePolicy.DO_NOTHING);
        JobDefinition fireOnce = definition("0 0 6 * * ?", MisfirePolicy.FIRE_ONCE_NOW);

        assertThat(mapper.createJobDetails(manual, "handler"))
                .allSatisfy(detail -> assertThat(detail.isDurable()).isTrue());
        assertThat(mapper.createTriggers(manual)).isEmpty();
        assertThat(mapper.createTriggers(fireOnce))
                .allSatisfy(trigger -> assertThat(trigger.getMisfireInstruction())
                        .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW));
    }

    /**
     * 验证定义能够从持久化 JobDataMap 中无损恢复。
     */
    @Test
    void shouldRestoreDefinitionFromJobDetail() {
        JobDefinition source = definition("0 0 6 * * ?", MisfirePolicy.DO_NOTHING);

        JobDefinition restored = mapper.restoreDefinition(mapper.createJobDetails(source, "handler").get(0));

        assertThat(mapper.fingerprint(restored)).isEqualTo(mapper.fingerprint(source));
        assertThat(restored.getParams()).containsEntry("tenant", "default");
    }

    /**
     * 验证运行期元数据也统一通过映射器读写，避免业务门面依赖内部键名。
     */
    @Test
    void shouldCreateAndReadRuntimeMetadata() {
        JobDetail detail = mapper.createJobDetails(
                definition("0 0 6 * * ?", MisfirePolicy.DO_NOTHING), "handler").get(0);

        assertThat(mapper.readJobName(detail.getJobDataMap())).isEqualTo("sync");
        assertThat(mapper.hasSameRegistration(detail, detail)).isTrue();
        assertThat(mapper.createManualTriggerData("execution-1"))
                .containsEntry(JobDataKeys.EXECUTION_ID, "execution-1")
                .containsEntry(JobDataKeys.RETRY_COUNT, "0")
                .containsEntry(JobDataKeys.TRIGGER_TYPE, JobTriggerType.MANUAL.name());
    }

    /**
     * 创建包含三个分片的测试任务。
     *
     * @param cron Cron 表达式
     * @param policy Misfire 策略
     * @return 测试任务定义
     */
    private JobDefinition definition(String cron, MisfirePolicy policy) {
        return JobDefinition.builder()
                .jobName("sync")
                .cron(cron)
                .zone("Asia/Shanghai")
                .description("同步任务")
                .shardTotal(3)
                .maxRetries(2)
                .backoffMs(100)
                .backoffMultiplier(2.0)
                .maxBackoffMs(1_000)
                .concurrent(false)
                .misfirePolicy(policy)
                .requestRecovery(true)
                .param("tenant", "default")
                .build();
    }
}
