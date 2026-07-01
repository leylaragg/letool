package com.github.leyland.letool.monitor.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricsCollector 指标收集器测试")
class MetricsCollectorTest {

    private final MetricsCollector collector = new MetricsCollector();

    @Nested
    @DisplayName("Counter 计数器操作")
    class CounterTests {

        @Test
        @DisplayName("初始计数器值为 0")
        void initialValue() {
            long value = collector.getCounterValue("new.counter");
            assertEquals(0, value);
        }

        @Test
        @DisplayName("increment 返回递增后的值")
        void increment() {
            long result = collector.increment("order.created");
            assertEquals(1, result);
            assertEquals(1, collector.getCounterValue("order.created"));
        }

        @Test
        @DisplayName("多次 increment")
        void incrementMultiple() {
            collector.increment("counter");
            collector.increment("counter");
            long result = collector.increment("counter");
            assertEquals(3, result);
        }

        @Test
        @DisplayName("counter 返回同一个实例")
        void counterSameInstance() {
            java.util.concurrent.atomic.AtomicLong c1 = collector.counter("test");
            java.util.concurrent.atomic.AtomicLong c2 = collector.counter("test");
            assertSame(c1, c2);
        }

        @Test
        @DisplayName("不同名称的计数器独立")
        void differentCountersIndependent() {
            collector.increment("a");
            collector.increment("a");
            collector.increment("b");

            assertEquals(2, collector.getCounterValue("a"));
            assertEquals(1, collector.getCounterValue("b"));
        }

        @Test
        @DisplayName("getCounterNames 返回所有计数器名称")
        void getCounterNames() {
            collector.increment("c1");
            collector.increment("c2");
            assertEquals(2, collector.getCounterNames().size());
            assertTrue(collector.getCounterNames().contains("c1"));
            assertTrue(collector.getCounterNames().contains("c2"));
        }
    }

    @Nested
    @DisplayName("Timer 计时器操作")
    class TimerTests {

        @Test
        @DisplayName("timer 返回同一个实例")
        void timerSameInstance() {
            MetricsCollector.Timer t1 = collector.timer("t");
            MetricsCollector.Timer t2 = collector.timer("t");
            assertSame(t1, t2);
        }

        @Test
        @DisplayName("recordTime 记录单次耗时")
        void recordTime() {
            collector.recordTime("request.latency", 150);
            MetricsCollector.TimerStats stats = collector.getTimerStats("request.latency");
            assertEquals(1, stats.getCount());
            assertEquals(150.0, stats.getAvgMs(), 0.01);
            assertEquals(150, stats.getMinMs());
            assertEquals(150, stats.getMaxMs());
        }

        @Test
        @DisplayName("多次 recordTime 计算统计")
        void recordMultipleTimes() {
            collector.recordTime("task.duration", 100);
            collector.recordTime("task.duration", 200);
            collector.recordTime("task.duration", 300);

            MetricsCollector.TimerStats stats = collector.getTimerStats("task.duration");
            assertEquals(3, stats.getCount());
            assertEquals(200.0, stats.getAvgMs(), 0.01);
            assertEquals(100, stats.getMinMs());
            assertEquals(300, stats.getMaxMs());
        }

        @Test
        @DisplayName("不存在的计时器返回空统计")
        void nonExistentTimer() {
            MetricsCollector.TimerStats stats = collector.getTimerStats("nonexistent");
            assertEquals(0, stats.getCount());
            assertEquals(0.0, stats.getAvgMs(), 0.01);
        }

        @Test
        @DisplayName("getTimerNames 返回所有计时器名称")
        void getTimerNames() {
            collector.recordTime("t1", 10);
            collector.recordTime("t2", 20);
            assertEquals(2, collector.getTimerNames().size());
        }
    }

    @Nested
    @DisplayName("Timer 内部类")
    class TimerInnerTests {

        @Test
        @DisplayName("getName 返回计时器名称")
        void getName() {
            MetricsCollector.Timer timer = new MetricsCollector.Timer("my-timer");
            assertEquals("my-timer", timer.getName());
        }

        @Test
        @DisplayName("getTotalCount 不受窗口大小影响")
        void getTotalCount() {
            MetricsCollector.Timer timer = new MetricsCollector.Timer("t");
            for (int i = 0; i < 100; i++) {
                timer.record(i);
            }
            assertEquals(100, timer.getTotalCount());
        }

        @Test
        @DisplayName("空计时器的 stats 返回零值")
        void emptyTimerStats() {
            MetricsCollector.Timer timer = new MetricsCollector.Timer("empty");
            MetricsCollector.TimerStats stats = timer.stats();
            assertEquals(0, stats.getCount());
            assertEquals(0.0, stats.getAvgMs(), 0.01);
            assertEquals(0, stats.getMinMs());
            assertEquals(0, stats.getMaxMs());
        }
    }

    @Nested
    @DisplayName("TimerStats 统计快照")
    class TimerStatsTests {

        @Test
        @DisplayName("getter 方法")
        void getters() {
            MetricsCollector.TimerStats stats = new MetricsCollector.TimerStats(10, 25.5, 5, 100);
            assertEquals(10, stats.getCount());
            assertEquals(25.5, stats.getAvgMs(), 0.01);
            assertEquals(5, stats.getMinMs());
            assertEquals(100, stats.getMaxMs());
        }

        @Test
        @DisplayName("toString 包含关键信息")
        void toStringContainsInfo() {
            MetricsCollector.TimerStats stats = new MetricsCollector.TimerStats(5, 30.0, 10, 80);
            String str = stats.toString();
            assertTrue(str.contains("5"));
            assertTrue(str.contains("30.00"));
            assertTrue(str.contains("10"));
            assertTrue(str.contains("80"));
            assertTrue(str.contains("TimerStats"));
        }
    }

    @Nested
    @DisplayName("getAllMetrics 全量指标导出")
    class AllMetricsTests {

        @Test
        @DisplayName("空收集器返回空 Map")
        void emptyCollector() {
            Map<String, Object> metrics = collector.getAllMetrics();
            assertTrue(metrics.isEmpty());
        }

        @Test
        @DisplayName("包含计数器和计时器")
        void containsCountersAndTimers() {
            collector.increment("order.count");
            collector.increment("order.count");
            collector.recordTime("order.time", 50);

            Map<String, Object> metrics = collector.getAllMetrics();
            assertEquals(2, metrics.size());
            assertEquals(2L, metrics.get("order.count"));
            assertTrue(metrics.get("order.time") instanceof MetricsCollector.TimerStats);
        }
    }
}
