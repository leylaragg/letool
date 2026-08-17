package io.github.leylaragg.letool.cache.support;

import io.github.leylaragg.letool.cache.core.CacheManager;
import io.github.leylaragg.letool.cache.core.CacheStats;
import io.github.leylaragg.letool.cache.core.MultiLevelCache;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationBacklog;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationRecovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;

/**
 * 缓存监控组件。
 *
 * <p>该组件面向业务系统和运维排查使用，负责从 {@link CacheManager} 中收集所有
 * KV 缓存实例的统计快照，并以可读日志输出命中率、加载次数、淘汰次数等核心指标。</p>
 */
public class CacheMonitor {

    private static final Logger log = LoggerFactory.getLogger(CacheMonitor.class);

    private final CacheManager cacheManager;
    private final CacheInvalidationRecovery invalidationRecovery;

    public CacheMonitor(CacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * 创建同时支持常规缓存统计和 DURABLE Outbox 监控的组件。
     *
     * @param cacheManager 缓存管理器
     * @param invalidationRecovery DURABLE 恢复处理器；非 DURABLE 模式可为 {@code null}
     */
    public CacheMonitor(CacheManager cacheManager, CacheInvalidationRecovery invalidationRecovery) {
        this.cacheManager = cacheManager;
        this.invalidationRecovery = invalidationRecovery;
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
     * 获取 DURABLE Outbox 积压快照。
     *
     * @param now 当前时间
     * @return Outbox 积压；未启用 DURABLE 时返回全零快照
     */
    public CacheInvalidationBacklog outboxBacklog(Instant now) {
        if (invalidationRecovery == null) {
            return new CacheInvalidationBacklog(0, 0, 0, null);
        }
        return invalidationRecovery.backlog(now);
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
