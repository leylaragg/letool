package com.github.leyland.letool.monitor.metrics;

import com.github.leyland.letool.monitor.exception.MonitorException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MetricsCollector} Micrometer 委托契约测试。
 */
class MetricsCollectorTest {

    /** 测试使用的内存 MeterRegistry。 */
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /** 待测试的指标便利门面。 */
    private final MetricsCollector collector = new MetricsCollector(registry);

    /**
     * 关闭测试注册表并释放内部资源。
     */
    @AfterEach
    void tearDown() {
        registry.close();
    }

    /**
     * 验证计数器由 MeterRegistry 管理，并按标签隔离。
     */
    @Test
    void shouldIncrementMicrometerCounterWithTags() {
        collector.increment("order.created", "channel", "web");
        collector.increment("order.created", "channel", "web");
        collector.increment("order.created", "channel", "app");

        assertThat(collector.counterValue(
                "order.created",
                "channel",
                "web")).isEqualTo(2.0);
        assertThat(registry.find("order.created")
                .tag("channel", "app")
                .counter()
                .count()).isEqualTo(1.0);
    }

    /**
     * 验证递增 API 只表达副作用，不承诺不同 registry 下的即时累计返回值。
     *
     * @throws NoSuchMethodException 反射查找公开方法失败
     */
    @Test
    void shouldExposeIncrementAsSideEffectOnly()
            throws NoSuchMethodException {
        assertThat(MetricsCollector.class.getMethod(
                "increment",
                String.class,
                String[].class).getReturnType()).isEqualTo(Void.TYPE);
    }

    /**
     * 验证同名同标签计数器和计时器由 Micrometer 复用。
     */
    @Test
    void shouldReuseMetersWithSameIdentity() {
        Counter firstCounter = collector.counter("order.created", "channel", "web");
        Counter secondCounter = collector.counter("order.created", "channel", "web");
        Timer firstTimer = collector.timer("order.latency", "channel", "web");
        Timer secondTimer = collector.timer("order.latency", "channel", "web");

        assertThat(firstCounter).isSameAs(secondCounter);
        assertThat(firstTimer).isSameAs(secondTimer);
    }

    /**
     * 验证 Duration、Runnable 和 Supplier 都会记录到 Micrometer Timer。
     */
    @Test
    void shouldRecordTimerThroughConvenienceMethods() {
        AtomicBoolean actionInvoked = new AtomicBoolean();

        collector.recordTime("order.latency", Duration.ofMillis(150));
        collector.record(
                "order.latency",
                () -> actionInvoked.set(true));
        String result = collector.record(
                "order.latency",
                () -> "done");

        MetricsCollector.TimerSnapshot snapshot =
                collector.timerSnapshot("order.latency");
        assertThat(actionInvoked).isTrue();
        assertThat(result).isEqualTo("done");
        assertThat(snapshot.count()).isEqualTo(3);
        assertThat(snapshot.totalTime()).isGreaterThanOrEqualTo(
                Duration.ofMillis(150));
        assertThat(snapshot.max()).isGreaterThanOrEqualTo(
                Duration.ofMillis(150));
    }

    /**
     * 验证查询不存在的完整身份会注册零值指标并返回零值视图。
     */
    @Test
    void shouldReturnZeroValuesForMissingMeters() {
        assertThat(collector.counterValue("missing.counter")).isZero();
        assertThat(collector.timerSnapshot("missing.timer"))
                .isEqualTo(MetricsCollector.TimerSnapshot.empty());
        assertThat(registry.getMeters()).hasSize(2);
    }

    /**
     * 验证查询遵循 Micrometer 完整身份和公共标签映射，不会任取子集匹配结果。
     */
    @Test
    void shouldQueryMetersByExactRegistryIdentity() {
        registry.config().commonTags("application", "test");
        collector.counter("order.created", "channel", "web").increment(2);
        collector.counter("order.created", "channel", "app").increment(3);
        collector.recordTime(
                "order.latency",
                Duration.ofMillis(100),
                "channel", "web");
        collector.recordTime(
                "order.latency",
                Duration.ofMillis(50),
                "channel", "app");

        assertThat(collector.counterValue(
                "order.created",
                "channel",
                "web")).isEqualTo(2);
        assertThat(collector.counterValue("order.created")).isZero();

        MetricsCollector.TimerSnapshot web =
                collector.timerSnapshot(
                        "order.latency",
                        "channel",
                        "web");
        assertThat(web.count()).isEqualTo(1);
        assertThat(web.totalTime()).isEqualTo(Duration.ofMillis(100));
        assertThat(collector.timerSnapshot("order.latency"))
                .isEqualTo(MetricsCollector.TimerSnapshot.empty());
        assertThat(registry.find("order.created").counters()).hasSize(3);
        assertThat(registry.find("order.latency").timers()).hasSize(3);
    }

    /**
     * 验证指标名称、耗时和标签必须符合生产安全边界。
     */
    @Test
    void shouldRejectInvalidMetricArguments() {
        assertMetricArgumentInvalid(() -> collector.increment(" "));
        assertMetricArgumentInvalid(() -> collector.increment(
                "order.created",
                "channel"));
        assertMetricArgumentInvalid(() -> collector.increment(
                "order.created",
                " ",
                "web"));
        assertMetricArgumentInvalid(() -> collector.recordTime(
                "order.latency",
                Duration.ofMillis(-1)));
        assertMetricArgumentInvalid(() -> collector.record(
                "order.latency",
                (Runnable) null));
    }

    /**
     * 断言操作因指标参数不合法而失败。
     *
     * @param operation 待执行操作
     */
    private static void assertMetricArgumentInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(MonitorException.class)
                .satisfies(throwable -> assertThat(
                        ((MonitorException) throwable).getCode())
                        .isEqualTo("MONITOR_METRIC_ARGUMENT_INVALID"));
    }
}
