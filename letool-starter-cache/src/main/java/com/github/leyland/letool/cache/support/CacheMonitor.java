package com.github.leyland.letool.cache.support;

import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.CacheStats;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存监控 —— 收集所有缓存实例的统计信息并输出.
 */
public class CacheMonitor {

    private static final Logger log = LoggerFactory.getLogger(CacheMonitor.class);

    private final CacheManager cacheManager;

    public CacheMonitor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取所有缓存实例的统计快照.
     *
     * @return cacheName → CacheStats
     */
    public Map<String, CacheStats> snapshot() {
        Map<String, CacheStats> result = new LinkedHashMap<>();
        for (MultiLevelCache<?, ?> cache : cacheManager.getAll()) {
            result.put(cache.getName(), cache.stats());
        }
        return result;
    }

    /**
     * 打印所有缓存实例的统计摘要（INFO 级别）.
     */
    public void logStats() {
        for (Map.Entry<String, CacheStats> entry : snapshot().entrySet()) {
            CacheStats s = entry.getValue();
            log.info("Cache [{}] L1HitRate={:.2%} L2HitRate={:.2%} TotalRequests={} Loads={} Evictions={}",
                    entry.getKey(),
                    s.getL1HitRate(),
                    s.getL2HitRate(),
                    s.getTotalRequests(),
                    s.getLoadCount(),
                    s.getEvictionCount());
        }
    }
}
