package io.github.leylaragg.letool.job.quartz;

import io.github.leylaragg.letool.job.core.JobDefinition;
import io.github.leylaragg.letool.job.core.JobTriggerType;
import io.github.leylaragg.letool.job.core.MisfirePolicy;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * 在 Letool 任务定义与 Quartz 持久化对象之间进行确定性映射。
 */
public class QuartzJobMapper {

    private static final String DATA_VERSION = "1";

    private final String group;

    /**
     * 创建 Quartz 映射器。
     *
     * @param group Letool 管理的 Quartz Job 组名
     */
    public QuartzJobMapper(String group) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("group 不能为空");
        }
        this.group = group.trim();
    }

    /**
     * 为每个分片创建一个 Quartz JobDetail。
     *
     * @param definition 逻辑任务定义
     * @param handlerBeanName 处理器 Bean 名称或本地处理器标识
     * @return 按分片索引升序排列的 JobDetail
     */
    public List<JobDetail> createJobDetails(JobDefinition definition, String handlerBeanName) {
        requireHandlerName(handlerBeanName);
        List<JobDetail> details = new ArrayList<>(definition.getShardTotal());
        for (int shardIndex = 0; shardIndex < definition.getShardTotal(); shardIndex++) {
            JobBuilder builder = JobBuilder.newJob(definition.isConcurrent()
                            ? ConcurrentQuartzDispatchJob.class : QuartzDispatchJob.class)
                    .withIdentity(jobKey(definition.getJobName(), shardIndex))
                    .withDescription(definition.getDescription())
                    .usingJobData(toJobDataMap(definition, handlerBeanName, shardIndex))
                    .requestRecovery(definition.isRequestRecovery());
            if (definition.getCron() == null) {
                builder.storeDurably(true);
            }
            details.add(builder.build());
        }
        return List.copyOf(details);
    }

    /**
     * 为任务定义创建每分片一个 CronTrigger。
     *
     * @param definition 逻辑任务定义
     * @return 按分片索引升序排列的 Trigger；手动任务返回空列表
     */
    public List<Trigger> createTriggers(JobDefinition definition) {
        if (definition.getCron() == null) {
            return List.of();
        }
        List<Trigger> triggers = new ArrayList<>(definition.getShardTotal());
        for (int shardIndex = 0; shardIndex < definition.getShardTotal(); shardIndex++) {
            CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(definition.getCron());
            if (definition.getZone() != null) {
                schedule.inTimeZone(TimeZone.getTimeZone(definition.getZone()));
            }
            schedule = definition.getMisfirePolicy() == MisfirePolicy.FIRE_ONCE_NOW
                    ? schedule.withMisfireHandlingInstructionFireAndProceed()
                    : schedule.withMisfireHandlingInstructionDoNothing();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(definition.getJobName(), shardIndex))
                    .forJob(jobKey(definition.getJobName(), shardIndex))
                    .withSchedule(schedule)
                    .build();
            triggers.add(trigger);
        }
        return List.copyOf(triggers);
    }

    /**
     * 从 Quartz JobDetail 恢复逻辑任务定义。
     *
     * @param detail Letool 创建的 JobDetail
     * @return 恢复后的任务定义
     */
    public JobDefinition restoreDefinition(JobDetail detail) {
        JobDataMap data = detail.getJobDataMap();
        JobDefinition.Builder builder = JobDefinition.builder()
                .jobName(data.getString(JobDataKeys.JOB_NAME))
                .description(emptyToNull(data.getString(JobDataKeys.DESCRIPTION)))
                .shardTotal(data.getInt(JobDataKeys.SHARD_TOTAL))
                .maxRetries(data.getInt(JobDataKeys.MAX_RETRIES))
                .backoffMs(data.getLong(JobDataKeys.BACKOFF_MS))
                .backoffMultiplier(data.getDouble(JobDataKeys.BACKOFF_MULTIPLIER))
                .maxBackoffMs(data.getLong(JobDataKeys.MAX_BACKOFF_MS))
                .concurrent(data.getBoolean(JobDataKeys.CONCURRENT))
                .misfirePolicy(MisfirePolicy.valueOf(data.getString(JobDataKeys.MISFIRE_POLICY)))
                .requestRecovery(data.getBoolean(JobDataKeys.REQUEST_RECOVERY));
        String cron = emptyToNull(data.getString(JobDataKeys.CRON));
        String zone = emptyToNull(data.getString(JobDataKeys.ZONE));
        if (cron != null) {
            builder.cron(cron);
        }
        if (zone != null) {
            builder.zone(zone);
        }
        data.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(JobDataKeys.PARAMETER_PREFIX))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.param(
                        entry.getKey().substring(JobDataKeys.PARAMETER_PREFIX.length()),
                        String.valueOf(entry.getValue())));
        return builder.build();
    }

    /**
     * 计算与运行节点无关的任务定义指纹。
     *
     * @param definition 任务定义
     * @return 小写十六进制 SHA-256 指纹
     */
    public String fingerprint(JobDefinition definition) {
        StringBuilder canonical = new StringBuilder()
                .append(definition.getJobName()).append('|')
                .append(value(definition.getCron())).append('|')
                .append(value(definition.getZone())).append('|')
                .append(definition.getDescription()).append('|')
                .append(definition.getShardTotal()).append('|')
                .append(definition.getMaxRetries()).append('|')
                .append(definition.getBackoffMs()).append('|')
                .append(definition.getBackoffMultiplier()).append('|')
                .append(definition.getMaxBackoffMs()).append('|')
                .append(definition.isConcurrent()).append('|')
                .append(definition.getMisfirePolicy()).append('|')
                .append(definition.isRequestRecovery());
        new TreeMap<>(definition.getParams())
                .forEach((key, parameterValue) -> canonical.append('|').append(key).append('=').append(parameterValue));
        return sha256(canonical.toString());
    }

    /**
     * 创建手动触发所需的运行期元数据。
     *
     * @param executionId 执行标识
     * @return 仅包含可持久化简单类型的 JobDataMap
     */
    public JobDataMap createManualTriggerData(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不能为空");
        }
        JobDataMap data = new JobDataMap();
        data.put(JobDataKeys.EXECUTION_ID, executionId);
        data.put(JobDataKeys.RETRY_COUNT, "0");
        data.put(JobDataKeys.TRIGGER_TYPE, JobTriggerType.MANUAL.name());
        return data;
    }

    /**
     * 从运行期元数据中读取逻辑任务名称。
     *
     * @param data Quartz 任务数据
     * @return 逻辑任务名称；不存在时返回 {@code null}
     */
    public String readJobName(JobDataMap data) {
        return data == null ? null : data.getString(JobDataKeys.JOB_NAME);
    }

    /**
     * 比较两个 JobDetail 是否对应同一注册定义。
     *
     * @param first 第一个 JobDetail
     * @param second 第二个 JobDetail
     * @return 注册指纹相同时返回 {@code true}
     */
    public boolean hasSameRegistration(JobDetail first, JobDetail second) {
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(
                first.getJobDataMap().getString(JobDataKeys.FINGERPRINT),
                second.getJobDataMap().getString(JobDataKeys.FINGERPRINT));
    }

    /**
     * 获取 Letool 管理的 Quartz Job 组名。
     *
     * @return Quartz Job 组名
     */
    public String getGroup() {
        return group;
    }

    /**
     * 创建稳定的 Quartz JobKey。
     *
     * @param jobName 逻辑任务名称
     * @param shardIndex 分片索引
     * @return 稳定 JobKey
     */
    public JobKey jobKey(String jobName, int shardIndex) {
        return JobKey.jobKey(jobName + "#" + shardIndex, group);
    }

    /**
     * 创建稳定的 Quartz TriggerKey。
     *
     * @param jobName 逻辑任务名称
     * @param shardIndex 分片索引
     * @return 稳定 TriggerKey
     */
    public TriggerKey triggerKey(String jobName, int shardIndex) {
        return TriggerKey.triggerKey(jobName + "#" + shardIndex, group + ".trigger");
    }

    private JobDataMap toJobDataMap(JobDefinition definition, String handlerBeanName, int shardIndex) {
        JobDataMap data = new JobDataMap();
        data.put(JobDataKeys.VERSION, DATA_VERSION);
        data.put(JobDataKeys.FINGERPRINT, registrationFingerprint(definition, handlerBeanName));
        data.put(JobDataKeys.JOB_NAME, definition.getJobName());
        data.put(JobDataKeys.HANDLER_BEAN_NAME, handlerBeanName);
        data.put(JobDataKeys.CRON, value(definition.getCron()));
        data.put(JobDataKeys.ZONE, value(definition.getZone()));
        data.put(JobDataKeys.DESCRIPTION, definition.getDescription());
        data.put(JobDataKeys.SHARD_INDEX, String.valueOf(shardIndex));
        data.put(JobDataKeys.SHARD_TOTAL, String.valueOf(definition.getShardTotal()));
        data.put(JobDataKeys.MAX_RETRIES, String.valueOf(definition.getMaxRetries()));
        data.put(JobDataKeys.BACKOFF_MS, String.valueOf(definition.getBackoffMs()));
        data.put(JobDataKeys.BACKOFF_MULTIPLIER, String.valueOf(definition.getBackoffMultiplier()));
        data.put(JobDataKeys.MAX_BACKOFF_MS, String.valueOf(definition.getMaxBackoffMs()));
        data.put(JobDataKeys.CONCURRENT, String.valueOf(definition.isConcurrent()));
        data.put(JobDataKeys.MISFIRE_POLICY, definition.getMisfirePolicy().name());
        data.put(JobDataKeys.REQUEST_RECOVERY, String.valueOf(definition.isRequestRecovery()));
        definition.getParams().forEach((key, parameterValue) ->
                data.put(JobDataKeys.PARAMETER_PREFIX + key, parameterValue));
        return data;
    }

    private String registrationFingerprint(JobDefinition definition, String handlerBeanName) {
        return sha256(fingerprint(definition) + "|handler=" + handlerBeanName);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void requireHandlerName(String handlerBeanName) {
        if (handlerBeanName == null || handlerBeanName.isBlank()) {
            throw new IllegalArgumentException("handlerBeanName 不能为空");
        }
    }
}
