package com.github.leyland.letool.cache.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheStats 缓存统计测试")
class CacheStatsTest {

    private CacheStats stats;

    @BeforeEach
    void setUp() {
        stats = new CacheStats();
    }

    @Nested
    @DisplayName("初始状态")
    class InitialStateTests {

        @Test
        @DisplayName("初始所有计数为 0")
        void testAllZeros() {
            assertEquals(0, stats.getL1HitCount());
            assertEquals(0, stats.getL2HitCount());
            assertEquals(0, stats.getMissCount());
            assertEquals(0, stats.getLoadCount());
            assertEquals(0, stats.getLoadSuccessCount());
            assertEquals(0, stats.getLoadFailureCount());
            assertEquals(0, stats.getEvictionCount());
            assertEquals(0, stats.getL2DegradedCount());
        }

        @Test
        @DisplayName("命中率为 0")
        void testHitRatesZero() {
            assertEquals(0.0, stats.getL1HitRate());
            assertEquals(0.0, stats.getL2HitRate());
            assertEquals(0.0, stats.getTotalHitRate());
        }

        @Test
        @DisplayName("总请求数为 0")
        void testTotalRequestsZero() {
            assertEquals(0, stats.getTotalRequests());
        }
    }

    @Nested
    @DisplayName("记录操作")
    class RecordTests {

        @Test
        @DisplayName("recordL1Hit 递增")
        void testRecordL1Hit() {
            stats.recordL1Hit();
            assertEquals(1, stats.getL1HitCount());
            stats.recordL1Hit();
            assertEquals(2, stats.getL1HitCount());
        }

        @Test
        @DisplayName("recordL2Hit 递增")
        void testRecordL2Hit() {
            stats.recordL2Hit();
            assertEquals(1, stats.getL2HitCount());
            stats.recordL2Hit();
            stats.recordL2Hit();
            assertEquals(3, stats.getL2HitCount());
        }

        @Test
        @DisplayName("recordMiss 递增")
        void testRecordMiss() {
            stats.recordMiss();
            assertEquals(1, stats.getMissCount());
        }

        @Test
        @DisplayName("recordLoad / recordLoadSuccess / recordLoadFailure")
        void testLoadOperations() {
            stats.recordLoad();
            stats.recordLoadSuccess();
            stats.recordLoadFailure();
            assertEquals(1, stats.getLoadCount());
            assertEquals(1, stats.getLoadSuccessCount());
            assertEquals(1, stats.getLoadFailureCount());
        }

        @Test
        @DisplayName("recordEviction 递增")
        void testRecordEviction() {
            stats.recordEviction();
            stats.recordEviction();
            assertEquals(2, stats.getEvictionCount());
        }

        @Test
        @DisplayName("recordL2Degraded 递增")
        void testRecordL2Degraded() {
            stats.recordL2Degraded();
            assertEquals(1, stats.getL2DegradedCount());
        }
    }

    @Nested
    @DisplayName("命中率计算")
    class HitRateTests {

        @Test
        @DisplayName("全部 L1 命中 - 100%")
        void testAllL1Hit() {
            stats.recordL1Hit();
            stats.recordL1Hit();
            stats.recordL1Hit();
            assertEquals(3, stats.getTotalRequests());
            assertEquals(1.0, stats.getL1HitRate(), 0.001);
            assertEquals(0.0, stats.getL2HitRate(), 0.001);
            assertEquals(1.0, stats.getTotalHitRate(), 0.001);
        }

        @Test
        @DisplayName("混合命中 - L1 + L2 + Miss")
        void testMixedHits() {
            stats.recordL1Hit();
            stats.recordL1Hit();
            stats.recordL2Hit();
            stats.recordMiss();
            assertEquals(4, stats.getTotalRequests());
            assertEquals(0.5, stats.getL1HitRate(), 0.001);
            assertEquals(0.25, stats.getL2HitRate(), 0.001);
            assertEquals(0.75, stats.getTotalHitRate(), 0.001);
        }

        @Test
        @DisplayName("全部 Miss - 0%")
        void testAllMiss() {
            stats.recordMiss();
            stats.recordMiss();
            assertEquals(2, stats.getTotalRequests());
            assertEquals(0.0, stats.getL1HitRate(), 0.001);
            assertEquals(0.0, stats.getL2HitRate(), 0.001);
            assertEquals(0.0, stats.getTotalHitRate(), 0.001);
        }

        @Test
        @DisplayName("仅 L2 命中")
        void testOnlyL2Hits() {
            stats.recordL2Hit();
            stats.recordL2Hit();
            assertEquals(2, stats.getTotalRequests());
            assertEquals(0.0, stats.getL1HitRate(), 0.001);
            assertEquals(1.0, stats.getL2HitRate(), 0.001);
            assertEquals(1.0, stats.getTotalHitRate(), 0.001);
        }
    }
}
