package com.github.leyland.letool.rule.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleMetrics 测试")
class RuleMetricsTest {

    private RuleMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new RuleMetrics();
    }

    @Nested
    @DisplayName("getter/setter 操作")
    class GetterSetterTests {

        @Test
        @DisplayName("totalExecutions 应正确存取")
        void shouldSetAndGetTotalExecutions() {
            metrics.setTotalExecutions(1000);
            assertEquals(1000, metrics.getTotalExecutions());
        }

        @Test
        @DisplayName("successRate 应正确存取")
        void shouldSetAndGetSuccessRate() {
            metrics.setSuccessRate(99.5);
            assertEquals(99.5, metrics.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("avgDurationMs 应正确存取")
        void shouldSetAndGetAvgDurationMs() {
            metrics.setAvgDurationMs(250);
            assertEquals(250, metrics.getAvgDurationMs());
        }

        @Test
        @DisplayName("chainStats 应正确存取")
        void shouldSetAndGetChainStats() {
            List<RuleMetrics.ChainStat> stats = new ArrayList<>();
            stats.add(new RuleMetrics.ChainStat("chain1", 10, 100, 50, 200, 95.0));
            metrics.setChainStats(stats);

            assertEquals(1, metrics.getChainStats().size());
            assertEquals("chain1", metrics.getChainStats().get(0).getChainName());
        }
    }

    @Nested
    @DisplayName("ChainStat 内部类")
    class ChainStatTests {

        @Test
        @DisplayName("无参构造应创建空对象")
        void shouldCreateEmptyInstance() {
            RuleMetrics.ChainStat stat = new RuleMetrics.ChainStat();
            assertNull(stat.getChainName());
            assertEquals(0, stat.getCount());
        }

        @Test
        @DisplayName("全参构造应设置所有字段")
        void shouldSetAllFields() {
            RuleMetrics.ChainStat stat = new RuleMetrics.ChainStat(
                    "riskChain", 500, 150, 50, 500, 98.5);

            assertEquals("riskChain", stat.getChainName());
            assertEquals(500, stat.getCount());
            assertEquals(150, stat.getAvgMs());
            assertEquals(50, stat.getMinMs());
            assertEquals(500, stat.getMaxMs());
            assertEquals(98.5, stat.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("各个字段的 setter 应正确设置")
        void shouldSetIndividualFields() {
            RuleMetrics.ChainStat stat = new RuleMetrics.ChainStat();
            stat.setChainName("testChain");
            stat.setCount(100);
            stat.setAvgMs(200);
            stat.setMinMs(10);
            stat.setMaxMs(1000);
            stat.setSuccessRate(90.0);

            assertEquals("testChain", stat.getChainName());
            assertEquals(100, stat.getCount());
            assertEquals(200, stat.getAvgMs());
            assertEquals(10, stat.getMinMs());
            assertEquals(1000, stat.getMaxMs());
            assertEquals(90.0, stat.getSuccessRate(), 0.001);
        }
    }
}
