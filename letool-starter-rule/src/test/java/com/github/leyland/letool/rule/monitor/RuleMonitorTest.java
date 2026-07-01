package com.github.leyland.letool.rule.monitor;

import com.github.leyland.letool.rule.model.RuleMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleMonitor 测试")
class RuleMonitorTest {

    private RuleMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new RuleMonitor();
    }

    @Nested
    @DisplayName("初始状态")
    class InitialStateTests {

        @Test
        @DisplayName("初始总执行次数应为 0")
        void shouldStartWithZeroExecutions() {
            assertEquals(0, monitor.getTotalExecutions());
        }

        @Test
        @DisplayName("初始成功次数应为 0")
        void shouldStartWithZeroSuccess() {
            assertEquals(0, monitor.getSuccessCount());
        }

        @Test
        @DisplayName("初始失败次数应为 0")
        void shouldStartWithZeroFailure() {
            assertEquals(0, monitor.getFailureCount());
        }
    }

    @Nested
    @DisplayName("recordExecution 方法")
    class RecordExecutionTests {

        @Test
        @DisplayName("应增加总执行次数")
        void shouldIncrementTotalExecutions() {
            monitor.recordExecution("chain1", 100, true);
            assertEquals(1, monitor.getTotalExecutions());
        }

        @Test
        @DisplayName("成功执行应增加成功计数")
        void shouldIncrementSuccessCount() {
            monitor.recordExecution("chain1", 50, true);
            monitor.recordExecution("chain1", 75, true);

            assertEquals(2, monitor.getSuccessCount());
            assertEquals(0, monitor.getFailureCount());
        }

        @Test
        @DisplayName("失败执行应增加失败计数")
        void shouldIncrementFailureCount() {
            monitor.recordExecution("chain1", 50, true);
            monitor.recordExecution("chain1", 75, false);
            monitor.recordExecution("chain1", 100, false);

            assertEquals(1, monitor.getSuccessCount());
            assertEquals(2, monitor.getFailureCount());
        }

        @Test
        @DisplayName("多链应分开统计")
        void shouldTrackMultipleChains() {
            monitor.recordExecution("chainA", 10, true);
            monitor.recordExecution("chainA", 20, true);
            monitor.recordExecution("chainB", 30, false);

            RuleMetrics metrics = monitor.getMetrics();
            assertEquals(3, metrics.getTotalExecutions());
            assertEquals(2, metrics.getChainStats().size());
        }
    }

    @Nested
    @DisplayName("getMetrics 方法")
    class GetMetricsTests {

        @Test
        @DisplayName("无执行记录时应返回空指标")
        void shouldReturnEmptyMetrics() {
            RuleMetrics metrics = monitor.getMetrics();

            assertEquals(0, metrics.getTotalExecutions());
            assertEquals(0, metrics.getSuccessRate());
            assertEquals(0, metrics.getAvgDurationMs());
            assertTrue(metrics.getChainStats().isEmpty());
        }

        @Test
        @DisplayName("应正确计算成功率")
        void shouldCalculateSuccessRate() {
            monitor.recordExecution("chain1", 10, true);
            monitor.recordExecution("chain1", 20, true);
            monitor.recordExecution("chain1", 30, true);
            monitor.recordExecution("chain1", 40, false);

            RuleMetrics metrics = monitor.getMetrics();
            assertEquals(4, metrics.getTotalExecutions());
            assertEquals(75.0, metrics.getSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("应正确计算平均耗时")
        void shouldCalculateAvgDuration() {
            monitor.recordExecution("chain1", 100, true);
            monitor.recordExecution("chain1", 200, true);

            RuleMetrics metrics = monitor.getMetrics();
            assertEquals(150, metrics.getAvgDurationMs());
        }

        @Test
        @DisplayName("链统计应包含 min/max/avg")
        void shouldContainMinMaxAvg() {
            monitor.recordExecution("chain1", 100, true);
            monitor.recordExecution("chain1", 300, true);
            monitor.recordExecution("chain1", 200, true);

            RuleMetrics metrics = monitor.getMetrics();
            RuleMetrics.ChainStat stat = metrics.getChainStats().get(0);

            assertEquals("chain1", stat.getChainName());
            assertEquals(3, stat.getCount());
            assertEquals(100, stat.getMinMs());
            assertEquals(300, stat.getMaxMs());
            assertEquals(200, stat.getAvgMs());
        }
    }

    @Nested
    @DisplayName("reset 方法")
    class ResetTests {

        @Test
        @DisplayName("reset 应清空所有指标")
        void shouldResetAllMetrics() {
            monitor.recordExecution("chain1", 100, true);
            monitor.recordExecution("chain2", 200, false);

            monitor.reset();

            assertEquals(0, monitor.getTotalExecutions());
            assertEquals(0, monitor.getSuccessCount());
            assertEquals(0, monitor.getFailureCount());

            RuleMetrics metrics = monitor.getMetrics();
            assertTrue(metrics.getChainStats().isEmpty());
        }
    }
}
