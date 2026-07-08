package com.github.leyland.letool.cache.support;

import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存监控输出测试。
 *
 * <p>监控日志是其他项目接入后排查缓存命中率的第一入口，因此这里直接捕获日志文本，
 * 避免线上出现占位符、格式化失败或无法阅读的统计值。</p>
 */
@DisplayName("缓存监控输出")
@ExtendWith(OutputCaptureExtension.class)
class CacheMonitorTest {

    @Test
    @DisplayName("logStats 应输出可读百分比而不是日志占位符")
    void logStatsShouldRenderReadablePercentages(CapturedOutput output) {
        CacheManager cacheManager = new CacheManager(null, new JacksonCacheSerializer());
        MultiLevelCache<String, String> cache = cacheManager.getOrCreate(
                CacheConfig.<String, String>builder("monitor-users"));
        CacheMonitor monitor = new CacheMonitor(cacheManager);

        cache.put("u1", "Alice");
        cache.getOrLoad("u1", key -> "fallback");
        monitor.logStats();

        assertTrue(output.getOut().contains("Cache [monitor-users]"));
        assertTrue(output.getOut().contains("L1HitRate=100.00%"));
        assertTrue(output.getOut().contains("L2HitRate=0.00%"));
        assertFalse(output.getOut().contains("{:.2%}"));
    }
}
