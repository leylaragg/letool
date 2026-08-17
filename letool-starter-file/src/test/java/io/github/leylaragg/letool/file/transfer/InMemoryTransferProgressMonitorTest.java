package io.github.leylaragg.letool.file.transfer;

import io.github.leylaragg.letool.file.exception.FileException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认内存传输进度监视器的关键状态机测试。
 */
class InMemoryTransferProgressMonitorTest {

    /**
     * 验证进度按已确认字节单调增长，并在完成时产生完整快照。
     */
    @Test
    void shouldTrackMonotonicProgressUntilCompletion() {
        InMemoryTransferProgressMonitor monitor = monitor(List.of(), new MutableClock());

        monitor.begin("transfer-1", TransferType.UPLOAD, 100, 20);
        TransferProgress running = monitor.update("transfer-1", 60);
        monitor.update("transfer-1", 100);
        TransferProgress completed = monitor.transition(
                "transfer-1", TransferStatus.COMPLETED, null);

        assertThat(running.transferredBytes()).isEqualTo(60);
        assertThat(running.percentage()).isEqualTo(60.0);
        assertThat(completed.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(monitor.find("transfer-1")).contains(completed);
    }

    /**
     * 验证字节回退和终态恢复运行都会被稳定错误码拒绝。
     */
    @Test
    void shouldRejectProgressRollbackAndTerminalTransition() {
        InMemoryTransferProgressMonitor monitor = monitor(List.of(), new MutableClock());
        monitor.begin("transfer-2", TransferType.DOWNLOAD, 100, 0);
        monitor.update("transfer-2", 50);

        assertThatThrownBy(() -> monitor.update("transfer-2", 49))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_010");

        monitor.transition("transfer-2", TransferStatus.CANCELLED, null);
        assertThatThrownBy(() -> monitor.transition(
                "transfer-2", TransferStatus.RUNNING, null))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_010");
    }

    /**
     * 验证单个进度监听器异常不会影响其他监听器和可信进度。
     */
    @Test
    void shouldIsolateProgressListenerFailure() {
        AtomicInteger received = new AtomicInteger();
        TransferProgressListener failing = progress -> {
            throw new IllegalStateException("模拟监听器失败");
        };
        TransferProgressListener healthy = progress -> received.incrementAndGet();
        InMemoryTransferProgressMonitor monitor = monitor(
                List.of(failing, healthy), new MutableClock());

        monitor.begin("transfer-3", TransferType.RANGE_DOWNLOAD, 10, 0);
        monitor.update("transfer-3", 10);

        assertThat(received).hasValueGreaterThanOrEqualTo(1);
        assertThat(monitor.find("transfer-3"))
                .hasValueSatisfying(progress -> assertThat(progress.transferredBytes()).isEqualTo(10));
    }

    /**
     * 验证超过保留期的终态记录会被惰性清理，活动记录不会被误删。
     */
    @Test
    void shouldEvictExpiredTerminalProgress() {
        MutableClock clock = new MutableClock();
        InMemoryTransferProgressMonitor monitor = monitor(List.of(), clock);
        monitor.begin("expired", TransferType.UPLOAD, 1, 0);
        monitor.update("expired", 1);
        monitor.transition("expired", TransferStatus.COMPLETED, null);

        clock.advance(Duration.ofMinutes(6));
        monitor.begin("active", TransferType.UPLOAD, 10, 0);

        assertThat(monitor.find("expired")).isEmpty();
        assertThat(monitor.find("active")).isPresent();
    }

    /**
     * 创建测试使用的监视器。
     *
     * @param listeners 进度监听器
     * @param clock 可控制时钟
     * @return 默认内存监视器
     */
    private InMemoryTransferProgressMonitor monitor(
            List<TransferProgressListener> listeners,
            Clock clock) {
        return new InMemoryTransferProgressMonitor(
                Duration.ofMinutes(5),
                100,
                Duration.ZERO,
                1,
                listeners,
                clock);
    }

    /**
     * 用于验证时间保留策略的可变时钟。
     */
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-06T00:00:00Z");

        /**
         * 推进当前时间。
         *
         * @param duration 推进时长
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
