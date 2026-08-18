package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCacheStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * 导出 XML 双层编译缓存的只读统计。
 *
 * <p>采集过程只读取框架快照，不向外暴露 Caffeine 类型。</p>
 *
 * @author leyland
 */
public final class XmlTemplateCacheMetrics implements MeterBinder {

    /** 只通过公开统计方法读取的编译缓存。 */
    private final XmlTemplateCompilationCache cache;

    /**
     * @param cache XML 双层编译缓存
     */
    public XmlTemplateCacheMetrics(XmlTemplateCompilationCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache 不能为空");
    }

    /**
     * 注册两个固定缓存层的条目、命中和装载统计。
     *
     * @param registry 宿主指标注册表
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry 不能为空");
        register(registry, "letool.print.cache.entries", "template-set",
                XmlTemplateCompilationCacheStats::templateSetEntries);
        register(registry, "letool.print.cache.hits", "template-set",
                XmlTemplateCompilationCacheStats::templateSetHitCount);
        register(registry, "letool.print.cache.misses", "template-set",
                XmlTemplateCompilationCacheStats::templateSetMissCount);
        register(registry, "letool.print.cache.loads.success", "template-set",
                XmlTemplateCompilationCacheStats::templateSetLoadSuccessCount);
        register(registry, "letool.print.cache.loads.failure", "template-set",
                XmlTemplateCompilationCacheStats::templateSetLoadFailureCount);

        register(registry, "letool.print.cache.entries", "template", XmlTemplateCompilationCacheStats::templateEntries);
        register(registry, "letool.print.cache.hits", "template", XmlTemplateCompilationCacheStats::templateHitCount);
        register(registry, "letool.print.cache.misses", "template", XmlTemplateCompilationCacheStats::templateMissCount);
        register(registry, "letool.print.cache.loads.success", "template",
                XmlTemplateCompilationCacheStats::templateLoadSuccessCount);
        register(registry, "letool.print.cache.loads.failure", "template",
                XmlTemplateCompilationCacheStats::templateLoadFailureCount);
    }

    /**
     * 每次采集都读取最新统计快照。
     *
     * @param registry 宿主指标注册表
     * @param name 指标名称
     * @param layer 固定缓存层标签
     * @param value 从缓存快照读取的数值
     */
    private void register(MeterRegistry registry, String name, String layer,
                          ToDoubleFunction<XmlTemplateCompilationCacheStats> value) {
        Gauge.builder(name, cache, current -> value.applyAsDouble(current.stats()))
                .tag("cache", layer)
                .register(registry);
    }
}
