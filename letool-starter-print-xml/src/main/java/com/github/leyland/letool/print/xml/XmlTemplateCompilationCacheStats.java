package com.github.leyland.letool.print.xml;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * XML 模板双层编译缓存的只读统计快照。
 *
 * <p>快照不可变且线程安全，不暴露 Caffeine 类型。</p>
 *
 * @author leyland
 */
public final class XmlTemplateCompilationCacheStats {

    /** 模板集合编译缓存的估算条目数。 */
    private final long templateSetEntries;
    /** 模板集合编译缓存命中次数。 */
    private final long templateSetHitCount;
    /** 模板集合编译缓存未命中次数。 */
    private final long templateSetMissCount;
    /** 模板集合编译成功装载次数。 */
    private final long templateSetLoadSuccessCount;
    /** 模板集合编译失败次数。 */
    private final long templateSetLoadFailureCount;
    /** 已解析模板缓存的估算条目数。 */
    private final long templateEntries;
    /** 已解析模板缓存命中次数。 */
    private final long templateHitCount;
    /** 已解析模板缓存未命中次数。 */
    private final long templateMissCount;
    /** 已解析模板成功装载次数。 */
    private final long templateLoadSuccessCount;
    /** 已解析模板装载失败次数。 */
    private final long templateLoadFailureCount;

    /**
     * 根据两个内部缓存创建一次统计快照。
     *
     * @param templateSetEntries 模板集合缓存估算条目数
     * @param templateSetStats 模板集合缓存统计
     * @param templateEntries 已解析模板缓存估算条目数
     * @param templateStats 已解析模板缓存统计
     */
    private XmlTemplateCompilationCacheStats(
            long templateSetEntries,
            CacheStats templateSetStats,
            long templateEntries,
            CacheStats templateStats) {
        this.templateSetEntries = templateSetEntries;
        this.templateSetHitCount = templateSetStats.hitCount();
        this.templateSetMissCount = templateSetStats.missCount();
        this.templateSetLoadSuccessCount = templateSetStats.loadSuccessCount();
        this.templateSetLoadFailureCount = templateSetStats.loadFailureCount();
        this.templateEntries = templateEntries;
        this.templateHitCount = templateStats.hitCount();
        this.templateMissCount = templateStats.missCount();
        this.templateLoadSuccessCount = templateStats.loadSuccessCount();
        this.templateLoadFailureCount = templateStats.loadFailureCount();
    }

    /**
     * 把内部缓存的当前状态复制为框架统计模型。
     *
     * @param templateSetCache 模板集合编译缓存
     * @param resolvedTemplateCache 已解析模板缓存
     * @return 当前计数的不可变副本
     */
    static XmlTemplateCompilationCacheStats from(
            Cache<?, ?> templateSetCache, Cache<?, ?> resolvedTemplateCache) {
        return new XmlTemplateCompilationCacheStats(
                templateSetCache.estimatedSize(), templateSetCache.stats(),
                resolvedTemplateCache.estimatedSize(), resolvedTemplateCache.stats());
    }

    /** @return 模板集合编译缓存的估算条目数 */
    public long templateSetEntries() {
        return templateSetEntries;
    }
    /** @return 模板集合编译缓存命中次数 */
    public long templateSetHitCount() {
        return templateSetHitCount;
    }
    /** @return 模板集合编译缓存未命中次数 */
    public long templateSetMissCount() {
        return templateSetMissCount;
    }
    /** @return 模板集合编译成功装载次数 */
    public long templateSetLoadSuccessCount() {
        return templateSetLoadSuccessCount;
    }
    /** @return 模板集合编译失败次数 */
    public long templateSetLoadFailureCount() {
        return templateSetLoadFailureCount;
    }
    /** @return 已解析模板缓存的估算条目数 */
    public long templateEntries() {
        return templateEntries;
    }
    /** @return 已解析模板缓存命中次数 */
    public long templateHitCount() {
        return templateHitCount;
    }
    /** @return 已解析模板缓存未命中次数 */
    public long templateMissCount() {
        return templateMissCount;
    }
    /** @return 已解析模板成功装载次数 */
    public long templateLoadSuccessCount() {
        return templateLoadSuccessCount;
    }
    /** @return 已解析模板装载失败次数 */
    public long templateLoadFailureCount() {
        return templateLoadFailureCount;
    }
}
