package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.config.JobProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

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
