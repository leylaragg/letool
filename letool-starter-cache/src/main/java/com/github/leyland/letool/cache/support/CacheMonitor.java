package com.github.leyland.letool.cache.support;

import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.CacheStats;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 缓存监控组件。
 *
 * <p>该组件面向业务系统和运维排查使用，负责从 {@link CacheManager} 中收集所有
 * KV 缓存实例的统计快照，并以可读日志输出命中率、加载次数、淘汰次数等核心指标。</p>
 */
public class CacheMonitor {

    private static final Logger log = LoggerFactory.getLogger(CacheMonitor.class);

    private final CacheManager cacheManager;

    public CacheMonitor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取所有缓存实例的统计快照。
     *
     * @return key 为缓存名称，value 为对应缓存实例的统计对象
     */
    public Map<String, CacheStats> snapshot() {
        Map<String, CacheStats> result = new LinkedHashMap<>();
        for (MultiLevelCache<?, ?> cache : cacheManager.getAll()) {
            result.put(cache.getName(), cache.stats());
        }
        return result;
    }

    /**
     * 按 INFO 级别打印所有缓存实例的统计摘要。
     *
     * <p>SLF4J 的占位符不支持 {@code {:.2%}} 这类 printf 写法，因此百分比需要先由
     * Java 格式化成字符串，再交给日志框架输出，避免线上看到未渲染的占位符。</p>
     */
    public void logStats() {
        for (Map.Entry<String, CacheStats> entry : snapshot().entrySet()) {
            CacheStats s = entry.getValue();
            log.info("Cache [{}] L1HitRate={} L2HitRate={} TotalRequests={} Loads={} Evictions={}",
                    entry.getKey(),
                    formatPercent(s.getL1HitRate()),
                    formatPercent(s.getL2HitRate()),
                    s.getTotalRequests(),
                    s.getLoadCount(),
                    s.getEvictionCount());
        }
    }

    /**
     * 将 0.0-1.0 的命中率格式化为保留两位小数的百分比。
     */
    private String formatPercent(double rate) {
        return String.format(Locale.ROOT, "%.2f%%", rate * 100.0D);
    }
}
