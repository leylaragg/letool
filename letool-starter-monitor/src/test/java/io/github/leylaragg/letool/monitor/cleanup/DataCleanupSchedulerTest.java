package io.github.leylaragg.letool.monitor.cleanup;

import io.github.leylaragg.letool.monitor.exception.MonitorException;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataCleanupScheduler} 真实任务调度与执行报告测试。
 */
class DataCleanupSchedulerTest {

    /** 测试固定触发时间。 */
    private static final Instant TRIGGERED_AT =
            Instant.parse("2026-07-31T00:00:00Z");

    /** 测试固定时钟。 */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TRIGGERED_AT, ZoneOffset.UTC);

    /**
     * 验证调度器根据任务保留时长计算截止时间并生成成功报告。
     */
    @Test
    void shouldCalculateCutoffAndBuildSuccessReport() {
        AtomicReference<CleanupContext> capturedContext = new AtomicReference<>();
        CleanupTask task = cleanupTask(
                "audit-log",
                Duration.ofDays(30),
                context -> {
                    capturedContext.set(context);
                    return 12;
                });
        DataCleanupScheduler scheduler = scheduler(List.of(task), FIXED_CLOCK);

        CleanupRunReport report = scheduler.runOnce();

        assertThat(capturedContext.get().taskName()).isEqualTo("audit-log");
        assertThat(capturedContext.get().triggeredAt()).isEqualTo(TRIGGERED_AT);
        assertThat(capturedContext.get().cutoff())
                .isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(report.overlapSkipped()).isFalse();
        assertThat(report.successCount()).isEqualTo(1);
        assertThat(report.failureCount()).isZero();
        assertThat(report.totalAffectedRows()).isEqualTo(12);
        assertThat(report.executions().get(0).status())
                .isEqualTo(CleanupExecutionStatus.SUCCESS);
    }

    /**
     * 验证缺少任务、重复名称和非法保留时长会在启动前失败。
     */
    @Test
    void shouldRejectInvalidTaskDefinitions() {
        assertMonitorCode(
                () -> scheduler(List.of(), FIXED_CLOCK),
                "MONITOR_CLEANUP_TASK_MISSING");

        CleanupTask first = cleanupTask(
                "duplicate",
                Duration.ofDays(1),
                context -> 1);
        CleanupTask second = cleanupTask(
                "duplicate",
                Duration.ofDays(2),
                context -> 2);
        assertMonitorCode(
                () -> scheduler(List.of(first, second), FIXED_CLOCK),
                "MONITOR_CLEANUP_TASK_DUPLICATED");

        CleanupTask invalidRetention = cleanupTask(
                "invalid-retention",
                Duration.ZERO,
                context -> 0);
        assertMonitorCode(
                () -> scheduler(List.of(invalidRetention), FIXED_CLOCK),
                "MONITOR_CONFIGURATION_INVALID");
    }

    /**
     * 验证单个任务失败会写入报告，但不会阻断后续任务。
     */
    @Test
    void shouldIsolateFailedTaskAndContinueRemainingTasks() {
        AtomicInteger succeedingInvocations = new AtomicInteger();
        CleanupTask failed = cleanupTask(
                "failed",
                Duration.ofDays(1),
                context -> {
                    throw new IllegalStateException("数据库不可用");
                });
        CleanupTask succeeded = cleanupTask(
                "succeeded",
                Duration.ofDays(1),
                context -> {
                    succeedingInvocations.incrementAndGet();
                    return 5;
                });
        DataCleanupScheduler scheduler = scheduler(
                List.of(failed, succeeded),
                FIXED_CLOCK);

        CleanupRunReport report = scheduler.runOnce();

        assertThat(succeedingInvocations).hasValue(1);
        assertThat(report.successCount()).isEqualTo(1);
        assertThat(report.failureCount()).isEqualTo(1);
        assertThat(report.executions())
                .extracting(CleanupExecution::status)
                .containsExactly(
                        CleanupExecutionStatus.FAILED,
                        CleanupExecutionStatus.SUCCESS);
        assertThat(report.executions().get(0).failureType())
                .isEqualTo(IllegalStateException.class.getName());
    }

    /**
     * 验证手动触发与正在执行的清理不会重叠。
     *
     * @throws Exception 等待并发清理结果失败
     */
    @Test
    void shouldSkipOverlappingRun() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CleanupTask blockingTask = cleanupTask(
                "blocking",
                Duration.ofDays(1),
                context -> {
                    entered.countDown();
                    await(release);
                    return 1;
                });
        DataCleanupScheduler scheduler = scheduler(
                List.of(blockingTask),
                FIXED_CLOCK);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<CleanupRunReport> running = executor.submit(scheduler::runOnce);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            CleanupRunReport skipped = scheduler.runOnce();

            assertThat(skipped.overlapSkipped()).isTrue();
            assertThat(skipped.executions()).isEmpty();
            release.countDown();
            assertThat(running.get(2, TimeUnit.SECONDS).successCount())
                    .isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 验证真实 Cron 调度会执行任务，停止后不再创建后续执行。
     *
     * @throws Exception 等待定时任务失败
     */
    @Test
    void shouldScheduleWithCronAndStopGracefully() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        CleanupTask task = cleanupTask(
                "scheduled",
                Duration.ofDays(1),
                context -> {
                    invocationCount.incrementAndGet();
                    invoked.countDown();
                    return 1;
                });
        DataCleanupScheduler scheduler = new DataCleanupScheduler(
                List.of(task),
                "* * * * * *",
                ZoneId.systemDefault(),
                Duration.ofSeconds(2));

        scheduler.start();
        try {
            assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.isRunning()).isTrue();
        } finally {
            scheduler.stop();
        }
        int countAfterStop = invocationCount.get();
        Thread.sleep(1_100);

        assertThat(scheduler.isRunning()).isFalse();
        assertThat(invocationCount).hasValue(countAfterStop);
    }

    /**
     * 验证停止调度器时会在配置期限内等待正在执行的清理任务结束。
     *
     * @throws Exception 等待定时任务和停止结果失败
     */
    @Test
    void shouldWaitForRunningTaskDuringGracefulStop() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        CleanupTask task = cleanupTask(
                "graceful-stop",
                Duration.ofDays(1),
                context -> {
                    entered.countDown();
                    await(release);
                    return 1;
                });
        DataCleanupScheduler scheduler = new DataCleanupScheduler(
                List.of(task),
                "* * * * * *",
                ZoneId.systemDefault(),
                Duration.ofSeconds(2));
        ExecutorService stopExecutor = Executors.newSingleThreadExecutor();

        scheduler.start();
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> stopFuture = stopExecutor.submit(() -> {
                stopStarted.countDown();
                scheduler.stop();
                stopped.countDown();
            });

            assertThat(stopStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stopped.await(150, TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();
            stopFuture.get(2, TimeUnit.SECONDS);
            assertThat(scheduler.isRunning()).isFalse();
        } finally {
            release.countDown();
            scheduler.stop();
            stopExecutor.shutdownNow();
        }
    }

    /**
     * 创建使用给定任务和时钟的调度器。
     *
     * @param tasks 清理任务
     * @param clock 调度时钟
     * @return 待测试调度器
     */
    private static DataCleanupScheduler scheduler(
            List<CleanupTask> tasks,
            Clock clock) {
        return new DataCleanupScheduler(
                tasks,
                "0 0 3 * * ?",
                ZoneOffset.UTC,
                Duration.ofSeconds(2),
                clock,
                new ThreadPoolTaskScheduler());
    }

    /**
     * 创建测试清理任务。
     *
     * @param name 任务名称
     * @param retention 数据保留时长
     * @param operation 清理逻辑
     * @return 清理任务
     */
    private static CleanupTask cleanupTask(
            String name,
            Duration retention,
            CleanupOperation operation) {
        return new CleanupTask() {
            /**
             * 获取测试任务名称。
             *
             * @return 任务名称
             */
            @Override
            public String name() {
                return name;
            }

            /**
             * 获取测试保留时长。
             *
             * @return 数据保留时长
             */
            @Override
            public Duration retention() {
                return retention;
            }

            /**
             * 执行测试清理逻辑。
             *
             * @param context 清理上下文
             * @return 受影响记录数
             */
            @Override
            public long cleanup(CleanupContext context) {
                return operation.cleanup(context);
            }
        };
    }

    /**
     * 断言操作抛出指定监控错误码。
     *
     * @param operation 待执行操作
     * @param expectedCode 期望错误码
     */
    private static void assertMonitorCode(
            Runnable operation,
            String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(MonitorException.class)
                .satisfies(throwable -> assertThat(
                        ((MonitorException) throwable).getCode())
                        .isEqualTo(expectedCode));
    }

    /**
     * 不吞掉中断标记地等待测试闩锁。
     *
     * @param latch 待等待闩锁
     */
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试线程被中断", exception);
        }
    }

    /**
     * 测试清理逻辑。
     */
    @FunctionalInterface
    private interface CleanupOperation {

        /**
         * 执行测试清理。
         *
         * @param context 清理上下文
         * @return 受影响记录数
         */
        long cleanup(CleanupContext context);
    }
}
