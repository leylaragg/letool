package com.github.leyland.letool.monitor.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiStatsSummary API 统计汇总测试")
class ApiStatsSummaryTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("默认构造函数创建空对象")
        void defaultConstructor() {
            ApiStatsSummary summary = new ApiStatsSummary();
            assertNull(summary.getPath());
            assertEquals(0, summary.getTotalRequests());
            assertNotNull(summary.getErrorBreakdown());
            assertTrue(summary.getErrorBreakdown().isEmpty());
        }

        @Test
        @DisplayName("完整参数构造函数")
        void fullConstructor() {
            Map<String, Long> errors = new HashMap<>();
            errors.put("BusinessException", 5L);
            LocalDateTime start = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2026, 7, 1, 10, 5);

            ApiStatsSummary summary = new ApiStatsSummary(
                    "/order/create", "POST", 1000, 995,
                    0.995, 42.5, 1, 500, 35, 100, 200, 380,
                    errors, start, end);

            assertEquals("/order/create", summary.getPath());
            assertEquals("POST", summary.getMethod());
            assertEquals(1000, summary.getTotalRequests());
            assertEquals(995, summary.getSuccessCount());
            assertEquals(0.995, summary.getSuccessRate(), 0.001);
            assertEquals(42.5, summary.getAvgResponseTimeMs(), 0.001);
            assertEquals(1, summary.getMinMs());
            assertEquals(500, summary.getMaxMs());
            assertEquals(35, summary.getP50Ms());
            assertEquals(100, summary.getP90Ms());
            assertEquals(200, summary.getP95Ms());
            assertEquals(380, summary.getP99Ms());
            assertEquals(1, summary.getErrorBreakdown().size());
            assertEquals(start, summary.getWindowStart());
            assertEquals(end, summary.getWindowEnd());
        }

        @Test
        @DisplayName("errorBreakdown 为 null 时默认为空 Map")
        void nullErrorBreakdownDefaultsToEmptyMap() {
            ApiStatsSummary summary = new ApiStatsSummary(
                    "/path", "GET", 10, 10, 1.0, 10, 1, 20, 5, 10, 15, 20,
                    null, null, null);
            assertNotNull(summary.getErrorBreakdown());
            assertTrue(summary.getErrorBreakdown().isEmpty());
        }
    }

    @Nested
    @DisplayName("getErrorCount 错误次数计算")
    class ErrorCountTests {

        @Test
        @DisplayName("totalRequests - successCount = errorCount")
        void errorCount() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setTotalRequests(100);
            summary.setSuccessCount(97);
            assertEquals(3, summary.getErrorCount());
        }

        @Test
        @DisplayName("全部成功时 getErrorCount 返回 0")
        void allSuccess() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setTotalRequests(100);
            summary.setSuccessCount(100);
            assertEquals(0, summary.getErrorCount());
        }

        @Test
        @DisplayName("全部失败时 getErrorCount 返回 totalRequests")
        void allFail() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setTotalRequests(50);
            summary.setSuccessCount(0);
            assertEquals(50, summary.getErrorCount());
        }
    }

    @Nested
    @DisplayName("getTotalErrorBreakdownCount")
    class ErrorBreakdownTotalTests {

        @Test
        @DisplayName("汇总所有异常类型次数")
        void totalErrorBreakdownCount() {
            ApiStatsSummary summary = new ApiStatsSummary();
            Map<String, Long> errors = new HashMap<>();
            errors.put("BizException", 3L);
            errors.put("SysException", 2L);
            summary.setErrorBreakdown(errors);

            assertEquals(5, summary.getTotalErrorBreakdownCount());
        }

        @Test
        @DisplayName("空 errorBreakdown 返回 0")
        void emptyErrorBreakdown() {
            ApiStatsSummary summary = new ApiStatsSummary();
            assertEquals(0, summary.getTotalErrorBreakdownCount());
        }
    }

    @Nested
    @DisplayName("getter / setter")
    class GetterSetterTests {

        @Test
        @DisplayName("setPath / setMethod")
        void pathAndMethod() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setPath("/user/query");
            summary.setMethod("GET");
            assertEquals("/user/query", summary.getPath());
            assertEquals("GET", summary.getMethod());
        }

        @Test
        @DisplayName("setSuccessRate / setAvgResponseTimeMs")
        void rates() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setSuccessRate(0.99);
            summary.setAvgResponseTimeMs(25.5);
            assertEquals(0.99, summary.getSuccessRate(), 0.001);
            assertEquals(25.5, summary.getAvgResponseTimeMs(), 0.001);
        }

        @Test
        @DisplayName("百分位延迟字段")
        void percentiles() {
            ApiStatsSummary summary = new ApiStatsSummary();
            summary.setP50Ms(10);
            summary.setP90Ms(50);
            summary.setP95Ms(100);
            summary.setP99Ms(500);

            assertEquals(10, summary.getP50Ms());
            assertEquals(50, summary.getP90Ms());
            assertEquals(100, summary.getP95Ms());
            assertEquals(500, summary.getP99Ms());
        }

        @Test
        @DisplayName("窗口时间字段")
        void windowTime() {
            ApiStatsSummary summary = new ApiStatsSummary();
            LocalDateTime start = LocalDateTime.now().minusMinutes(5);
            LocalDateTime end = LocalDateTime.now();
            summary.setWindowStart(start);
            summary.setWindowEnd(end);

            assertEquals(start, summary.getWindowStart());
            assertEquals(end, summary.getWindowEnd());
        }
    }
}
