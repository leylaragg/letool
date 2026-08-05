package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.exception.JobErrorCode;
import com.github.leyland.letool.job.exception.JobException;
import com.github.leyland.letool.job.quartz.QuartzJobMapper;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.simpl.RAMJobStore;
import org.springframework.beans.factory.ListableBeanFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 面向业务代码的 Quartz 任务管理便捷门面。
 *
 * <p>调度生命周期、线程池、持久化和集群均由 Spring Boot Quartz 管理。</p>
 */
public class JobScheduler {

    private final Scheduler scheduler;
    private final QuartzJobMapper mapper;
    private final JobHandlerRegistry handlerRegistry;
    private final ListableBeanFactory beanFactory;

    /**
     * 创建 Quartz 任务管理门面。
     *
     * @param scheduler 原生 Quartz 调度器
     * @param mapper Quartz 持久化映射器
     * @param handlerRegistry 当前节点处理器注册表
     * @param beanFactory Spring Bean 查询入口
     */
    public JobScheduler(
            Scheduler scheduler,
            QuartzJobMapper mapper,
            JobHandlerRegistry handlerRegistry,
            ListableBeanFactory beanFactory) {
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.handlerRegistry = handlerRegistry;
        this.beanFactory = beanFactory;
    }

    /**
     * 注册引用 Spring Bean 的集群安全任务。
     *
     * @param definition 任务定义
     * @param handlerBeanName 所有节点都具备的处理器 Bean 名称
     */
    public void register(JobDefinition definition, String handlerBeanName) {
        boolean registeredHere = ensureBeanHandler(definition.getJobName(), handlerBeanName);
        try {
            registerDefinition(definition, handlerBeanName);
        } catch (RuntimeException exception) {
            if (registeredHere) {
                handlerRegistry.unregister(definition.getJobName());
            }
            throw exception;
        }
    }

    /**
     * 仅在 RAMJobStore 中注册当前节点 Lambda 处理器。
     *
     * @param definition 任务定义
     * @param handler 本地处理器
     */
    public void registerLocal(JobDefinition definition, JobHandler handler) {
        try {
            if (scheduler.getMetaData().isJobStoreClustered()
                    || !RAMJobStore.class.isAssignableFrom(scheduler.getMetaData().getJobStoreClass())) {
                throw new JobException(JobErrorCode.CLUSTER_UNSAFE_HANDLER,
                        definition.getJobName(), definition.getJobName());
            }
        } catch (SchedulerException exception) {
            throw schedulerFailure(definition.getJobName(), "检查 JobStore", exception);
        }
        handlerRegistry.register(definition.getJobName(), handler);
        try {
            registerDefinition(definition, "local:" + definition.getJobName());
        } catch (RuntimeException exception) {
            handlerRegistry.unregister(definition.getJobName());
            throw exception;
        }
    }

    /**
     * 显式替换逻辑任务的全部分片。
     *
     * @param definition 新任务定义
     * @param handlerBeanName 处理器 Bean 名称
     */
    public void replace(JobDefinition definition, String handlerBeanName) {
        boolean registeredHere = ensureBeanHandler(definition.getJobName(), handlerBeanName);
        boolean replacementScheduled = false;
        try {
            List<JobKey> existingKeys = findJobKeys(definition.getJobName());
            List<JobKey> replacementKeys = schedule(definition, handlerBeanName, true);
            replacementScheduled = true;
            List<JobKey> surplusKeys = existingKeys.stream()
                    .filter(key -> !replacementKeys.contains(key))
                    .toList();
            if (!surplusKeys.isEmpty()) {
                scheduler.deleteJobs(surplusKeys);
            }
        } catch (SchedulerException exception) {
            if (registeredHere && !replacementScheduled) {
                handlerRegistry.unregister(definition.getJobName());
            }
            throw schedulerFailure(definition.getJobName(), "替换任务", exception);
        }
    }

    /**
     * 手动触发逻辑任务的全部分片。
     *
     * @param jobName 逻辑任务名称
     * @return 每分片一个不可变触发回执
     */
    public List<JobTriggerReceipt> trigger(String jobName) {
        List<JobKey> keys = requiredJobKeys(jobName);
        List<JobTriggerReceipt> receipts = new ArrayList<>(keys.size());
        for (JobKey key : keys) {
            receipts.add(triggerKey(jobName, key));
        }
        return List.copyOf(receipts);
    }

    /**
     * 手动触发指定分片。
     *
     * @param jobName 逻辑任务名称
     * @param shardIndex 分片索引
     * @return 不可变触发回执
     */
    public JobTriggerReceipt trigger(String jobName, int shardIndex) {
        JobKey key = mapper.jobKey(jobName, shardIndex);
        try {
            if (!scheduler.checkExists(key)) {
                throw new JobException(JobErrorCode.JOB_NOT_FOUND, jobName, jobName + "#" + shardIndex);
            }
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "检查任务分片", exception);
        }
        return triggerKey(jobName, key);
    }

    /** @param jobName 逻辑任务名称 */
    public void pause(String jobName) {
        forEachRequiredJob(jobName, key -> scheduler.pauseJob(key), "暂停任务");
    }

    /** @param jobName 逻辑任务名称 */
    public void resume(String jobName) {
        forEachRequiredJob(jobName, key -> scheduler.resumeJob(key), "恢复任务");
    }

    /**
     * 注销逻辑任务及全部分片。
     *
     * @param jobName 逻辑任务名称
     */
    public void unregister(String jobName) {
        try {
            List<JobKey> keys = findJobKeys(jobName);
            if (!keys.isEmpty()) {
                scheduler.deleteJobs(keys);
            }
            handlerRegistry.unregister(jobName);
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "注销任务", exception);
        }
    }

    /**
     * 查询逻辑任务定义。
     *
     * @param jobName 逻辑任务名称
     * @return 存在时返回任务定义
     */
    public Optional<JobDefinition> getJob(String jobName) {
        try {
            List<JobKey> keys = findJobKeys(jobName);
            return keys.isEmpty() ? Optional.empty()
                    : Optional.of(mapper.restoreDefinition(scheduler.getJobDetail(keys.get(0))));
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "查询任务", exception);
        }
    }

    /** @return 所有逻辑任务定义，按名称升序排列 */
    public List<JobDefinition> getAllJobs() {
        try {
            Map<String, JobDefinition> definitions = new LinkedHashMap<>();
            for (JobKey key : allManagedJobKeys()) {
                JobDefinition definition = mapper.restoreDefinition(scheduler.getJobDetail(key));
                definitions.putIfAbsent(definition.getJobName(), definition);
            }
            return definitions.values().stream()
                    .sorted(Comparator.comparing(JobDefinition::getJobName))
                    .toList();
        } catch (SchedulerException exception) {
            throw schedulerFailure(null, "查询全部任务", exception);
        }
    }

    /** @return 逻辑任务数量 */
    public int getJobCount() {
        return getAllJobs().size();
    }

    /**
     * 判断逻辑任务的全部 Cron Trigger 是否处于暂停状态。
     *
     * @param jobName 逻辑任务名称
     * @return 全部可调度分片已暂停时返回 {@code true}
     */
    public boolean isPaused(String jobName) {
        try {
            List<JobKey> keys = requiredJobKeys(jobName);
            boolean hasTrigger = false;
            for (JobKey key : keys) {
                for (Trigger trigger : scheduler.getTriggersOfJob(key)) {
                    hasTrigger = true;
                    if (scheduler.getTriggerState(trigger.getKey()) != Trigger.TriggerState.PAUSED) {
                        return false;
                    }
                }
            }
            return hasTrigger;
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "查询暂停状态", exception);
        }
    }

    /**
     * 判断逻辑任务是否正在任一节点执行。
     *
     * @param jobName 逻辑任务名称
     * @return 正在执行时返回 {@code true}
     */
    public boolean isRunning(String jobName) {
        return getRunningJobs().contains(jobName);
    }

    /** @return 当前节点从 Quartz 查询到的运行中逻辑任务名称 */
    public List<String> getRunningJobs() {
        try {
            return scheduler.getCurrentlyExecutingJobs().stream()
                    .map(JobExecutionContext::getMergedJobDataMap)
                    .map(mapper::readJobName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        } catch (SchedulerException exception) {
            throw schedulerFailure(null, "查询运行状态", exception);
        }
    }

    private void registerDefinition(JobDefinition definition, String handlerBeanName) {
        try {
            List<JobKey> existing = findJobKeys(definition.getJobName());
            if (!existing.isEmpty()) {
                if (matchesExisting(definition, handlerBeanName, existing)) {
                    return;
                }
                throw new JobException(JobErrorCode.DEFINITION_CONFLICT,
                        definition.getJobName(), definition.getJobName());
            }
            schedule(definition, handlerBeanName, false);
        } catch (ObjectAlreadyExistsException exception) {
            try {
                if (matchesExisting(definition, handlerBeanName, findJobKeys(definition.getJobName()))) {
                    return;
                }
            } catch (SchedulerException checkException) {
                exception.addSuppressed(checkException);
            }
            throw new JobException(JobErrorCode.DEFINITION_CONFLICT,
                    definition.getJobName(), exception, definition.getJobName());
        } catch (SchedulerException exception) {
            throw schedulerFailure(definition.getJobName(), "注册任务", exception);
        }
    }

    private List<JobKey> schedule(
            JobDefinition definition,
            String handlerBeanName,
            boolean replace) throws SchedulerException {
        List<JobDetail> details = mapper.createJobDetails(definition, handlerBeanName);
        List<Trigger> triggers = mapper.createTriggers(definition);
        Map<JobDetail, Set<? extends Trigger>> jobsAndTriggers = new LinkedHashMap<>();
        for (int index = 0; index < details.size(); index++) {
            Set<Trigger> triggerSet = index < triggers.size()
                    ? Set.of(triggers.get(index)) : Set.of();
            jobsAndTriggers.put(details.get(index), triggerSet);
        }
        scheduler.scheduleJobs(jobsAndTriggers, replace);
        return details.stream().map(JobDetail::getKey).toList();
    }

    private boolean matchesExisting(
            JobDefinition definition,
            String handlerBeanName,
            List<JobKey> existingKeys) throws SchedulerException {
        List<JobDetail> expected = mapper.createJobDetails(definition, handlerBeanName);
        if (existingKeys.size() != expected.size()) {
            return false;
        }
        Map<JobKey, JobDetail> expectedByKey = new LinkedHashMap<>();
        expected.forEach(detail -> expectedByKey.put(detail.getKey(), detail));
        for (JobKey key : existingKeys) {
            JobDetail actual = scheduler.getJobDetail(key);
            JobDetail expectedDetail = expectedByKey.get(key);
            if (!mapper.hasSameRegistration(actual, expectedDetail)) {
                return false;
            }
        }
        return true;
    }

    private JobTriggerReceipt triggerKey(String jobName, JobKey key) {
        String executionId = UUID.randomUUID().toString().replace("-", "");
        try {
            scheduler.triggerJob(key, mapper.createManualTriggerData(executionId));
            return new JobTriggerReceipt(executionId, jobName, shardIndex(key), Instant.now());
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "手动触发任务", exception);
        }
    }

    private boolean ensureBeanHandler(String jobName, String handlerBeanName) {
        if (handlerRegistry.contains(jobName)) {
            return false;
        }
        if (handlerBeanName == null || handlerBeanName.isBlank() || !beanFactory.containsBean(handlerBeanName)) {
            throw new JobException(JobErrorCode.INVALID_HANDLER, jobName, handlerBeanName);
        }
        Object bean = beanFactory.getBean(handlerBeanName);
        if (!(bean instanceof JobHandler handler)) {
            throw new JobException(JobErrorCode.INVALID_HANDLER, jobName, handlerBeanName);
        }
        handlerRegistry.register(jobName, handler);
        return true;
    }

    private List<JobKey> requiredJobKeys(String jobName) {
        try {
            List<JobKey> keys = findJobKeys(jobName);
            if (keys.isEmpty()) {
                throw new JobException(JobErrorCode.JOB_NOT_FOUND, jobName, jobName);
            }
            return keys;
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, "查询任务分片", exception);
        }
    }

    private List<JobKey> findJobKeys(String jobName) throws SchedulerException {
        String expectedPrefix = jobName + "#";
        return allManagedJobKeys().stream()
                .filter(key -> key.getName().startsWith(expectedPrefix))
                .sorted(Comparator.comparingInt(this::shardIndex))
                .toList();
    }

    private Set<JobKey> allManagedJobKeys() throws SchedulerException {
        return new LinkedHashSet<>(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(mapper.getGroup())));
    }

    private int shardIndex(JobKey key) {
        int separator = key.getName().lastIndexOf('#');
        return Integer.parseInt(key.getName().substring(separator + 1));
    }

    private void forEachRequiredJob(String jobName, SchedulerAction action, String operation) {
        try {
            for (JobKey key : requiredJobKeys(jobName)) {
                action.accept(key);
            }
        } catch (SchedulerException exception) {
            throw schedulerFailure(jobName, operation, exception);
        }
    }

    private JobException schedulerFailure(String jobName, String operation, Exception cause) {
        return new JobException(JobErrorCode.SCHEDULER_OPERATION_FAILED, jobName, cause, operation);
    }

    /**
     * 可以抛出 Quartz 检查异常的 JobKey 操作。
     */
    @FunctionalInterface
    private interface SchedulerAction {
        /** @param key Quartz JobKey @throws SchedulerException 调度操作失败 */
        void accept(JobKey key) throws SchedulerException;
    }
}
