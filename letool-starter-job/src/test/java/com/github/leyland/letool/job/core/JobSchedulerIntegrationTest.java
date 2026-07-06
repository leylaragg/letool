package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.config.JobProperties;
import com.github.leyland.letool.job.exception.JobException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Local execution lifecycle tests for {@link JobScheduler}.
 */
class JobSchedulerIntegrationTest {

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
    private final JobLogService logService = new JobLogService();
    private final JobProperties properties = new JobProperties();
    private final JobScheduler scheduler = new JobScheduler(executor, logService, properties);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Verifies manual trigger lifecycle, running-state tracking, and execution logging.
     */
    @Test
    void shouldTrackManualTriggerUntilExecutionCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition job = JobDefinition.builder()
                .jobName("manualJob")
                .maxRetries(0)
                .handler(context -> {
                    started.countDown();
                    release.await(2, TimeUnit.SECONDS);
                })
                .build();
        scheduler.register(job);

        JobResult result = scheduler.trigger("manualJob");

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.isRunning("manualJob")).isTrue();
        assertThat(scheduler.getRunningJobs()).containsExactly("manualJob");

        release.countDown();
        awaitUntil(() -> result.isSuccess() && !scheduler.isRunning("manualJob"));

        assertThat(scheduler.isRunning("manualJob")).isFalse();
        assertThat(result.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(logService.getLastExecution("manualJob")).isSameAs(result);
    }

    /**
     * Verifies a completed execution does not clear the running state of a newer same-name execution.
     */
    @Test
    void shouldKeepRunningStateWhenSameJobHasNewerExecution() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        JobDefinition job = JobDefinition.builder()
                .jobName("concurrentJob")
                .maxRetries(0)
                .handler(context -> {
                    int executionOrder = order.incrementAndGet();
                    bothStarted.countDown();
                    if (executionOrder == 1) {
                        firstRelease.await(2, TimeUnit.SECONDS);
                    } else {
                        secondRelease.await(2, TimeUnit.SECONDS);
                    }
                    completed.incrementAndGet();
                })
                .build();
        scheduler.register(job);

        JobResult first = scheduler.trigger("concurrentJob");
        JobResult second = scheduler.trigger("concurrentJob");

        assertThat(bothStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.isRunning("concurrentJob")).isTrue();

        firstRelease.countDown();
        awaitUntil(() -> completed.get() == 1);

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isFalse();
        assertThat(scheduler.isRunning("concurrentJob")).isTrue();

        secondRelease.countDown();
        awaitUntil(() -> second.isSuccess() && !scheduler.isRunning("concurrentJob"));

        assertThat(first.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(second.getStatus()).isEqualTo(JobStatus.SUCCESS);
    }

    /**
     * Verifies failed Cron scheduling does not leave a half-registered job behind.
     */
    @Test
    void shouldRollbackRegistrationWhenCronScheduleFails() {
        JobDefinition job = JobDefinition.builder()
                .jobName("invalidCronJob")
                .cron("invalid")
                .handler(context -> {
                })
                .build();

        assertThatThrownBy(() -> scheduler.register(job))
                .isInstanceOf(JobException.class)
                .hasMessageContaining("Cron 表达式解析失败");

        assertThat(scheduler.getJob("invalidCronJob")).isNull();
        assertThat(scheduler.getJobCount()).isZero();
    }

    /**
     * Verifies common start/step Cron fields are accepted by the scheduler.
     */
    @Test
    void shouldScheduleStartStepCronExpressions() {
        JobDefinition secondsStepJob = JobDefinition.builder()
                .jobName("secondsStepJob")
                .cron("0/5 * * * * ?")
                .handler(context -> {
                })
                .build();
        JobDefinition minutesStepJob = JobDefinition.builder()
                .jobName("minutesStepJob")
                .cron("0 0/2 * * * ?")
                .handler(context -> {
                })
                .build();

        scheduler.register(secondsStepJob);
        scheduler.register(minutesStepJob);

        assertThat(scheduler.getJob("secondsStepJob")).isSameAs(secondsStepJob);
        assertThat(scheduler.getJob("minutesStepJob")).isSameAs(minutesStepJob);
    }

    /**
     * Verifies common Spring Cron expressions are translated into the real next execution time.
     */
    @Test
    void shouldCalculateCronNextExecutionTime() throws Exception {
        Object secondsSchedule = parseCron("0/5 * * * * ?");
        Object minutesSchedule = parseCron("0 0/2 * * * ?");
        Object exactHourSchedule = parseCron("0 0 6 * * ?");
        Object listedHoursSchedule = parseCron("0 0 9,12,18 * * ?");

        LocalDateTime nextSecondStep = nextExecutionTime(secondsSchedule);
        LocalDateTime nextMinuteStep = nextExecutionTime(minutesSchedule);
        LocalDateTime nextExactHour = nextExecutionTime(exactHourSchedule);
        LocalDateTime nextListedHour = nextExecutionTime(listedHoursSchedule);

        assertThat(nextSecondStep.getSecond() % 5).isZero();
        assertThat(nextMinuteStep.getSecond()).isZero();
        assertThat(nextMinuteStep.getMinute() % 2).isZero();
        assertThat(nextExactHour.getSecond()).isZero();
        assertThat(nextExactHour.getMinute()).isZero();
        assertThat(nextExactHour.getHour()).isEqualTo(6);
        assertThat(nextListedHour.getSecond()).isZero();
        assertThat(nextListedHour.getMinute()).isZero();
        assertThat(nextListedHour.getHour()).isIn(9, 12, 18);
    }

    /**
     * Verifies list and range expressions are accepted as lightweight Spring Cron syntax.
     */
    @Test
    void shouldScheduleRangeAndListCronExpressions() {
        JobDefinition rangeJob = JobDefinition.builder()
                .jobName("rangeJob")
                .cron("0 0 9-17 * * ?")
                .handler(context -> {
                })
                .build();
        JobDefinition listJob = JobDefinition.builder()
                .jobName("listJob")
                .cron("0 15 9,12,18 * * ?")
                .handler(context -> {
                })
                .build();

        scheduler.register(rangeJob);
        scheduler.register(listJob);

        assertThat(scheduler.getJob("rangeJob")).isSameAs(rangeJob);
        assertThat(scheduler.getJob("listJob")).isSameAs(listJob);
    }

    /**
     * Verifies successful executions still expose how many retries were needed.
     */
    @Test
    void shouldRecordRetryCountWhenExecutionEventuallySucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        JobDefinition job = JobDefinition.builder()
                .jobName("retryJob")
                .maxRetries(2)
                .backoffMs(1)
                .backoffMultiplier(1.0)
                .handler(context -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("temporary failure");
                    }
                })
                .build();
        scheduler.register(job);

        JobResult result = scheduler.trigger("retryJob");
        awaitUntil(result::isSuccess);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(result.getRetryCount()).isEqualTo(2);
        assertThat(logService.getLastExecution("retryJob")).isSameAs(result);
    }

    /**
     * Verifies standalone scheduler shutdown clears scheduled work and closes its executor.
     */
    @Test
    void shouldShutdownSchedulerAndExecutor() {
        JobDefinition job = JobDefinition.builder()
                .jobName("scheduledJob")
                .cron(nextSecondCron())
                .handler(context -> {
                })
                .build();
        scheduler.register(job);

        assertThat(executor.getQueue()).isNotEmpty();

        scheduler.shutdown();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.getQueue()).isEmpty();
        assertThat(scheduler.getRunningJobs()).isEmpty();
    }

    private String nextSecondCron() {
        int nextSecond = (java.time.LocalDateTime.now().getSecond() + 1) % 60;
        return nextSecond + " * * * * ?";
    }

    private Object parseCron(String cron) throws Exception {
        Method method = JobScheduler.class.getDeclaredMethod("parseCron", String.class);
        method.setAccessible(true);
        return method.invoke(scheduler, cron);
    }

    private LocalDateTime nextExecutionTime(Object schedule) throws Exception {
        Field field = schedule.getClass().getDeclaredField("nextExecutionTime");
        field.setAccessible(true);
        return (LocalDateTime) field.get(schedule);
    }

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
