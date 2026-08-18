package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.observability.MicrometerPrintTelemetry;
import io.github.leylaragg.letool.print.observability.XmlTemplateCacheMetrics;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.service.PrintTelemetry;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 宿主提供 MeterRegistry 时装配打印执行与编译缓存指标。
 *
 * @author leyland
 */
@AutoConfiguration(
        after = PrintAutoConfiguration.class,
        afterName = "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({PrintService.class, MeterRegistry.class})
@ConditionalOnProperty(
        prefix = "letool.print.metrics",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PrintMetricsAutoConfiguration {

    /** Spring Boot 统一绑定 MeterBinder 的基础设施 Bean。 */
    private static final String METER_REGISTRY_POST_PROCESSOR_BEAN_NAME = "meterRegistryPostProcessor";

    /**
     * @param registry 宿主指标注册表
     * @return 打印门面使用的安全观测端口
     */
    @Bean
    @ConditionalOnMissingBean(PrintTelemetry.class)
    public MicrometerPrintTelemetry micrometerPrintTelemetry(MeterRegistry registry) {
        return new MicrometerPrintTelemetry(registry);
    }

    /**
     * @param cache XML 双层编译缓存
     * @param registry 宿主指标注册表
     * @param beanFactory 用于识别 Boot 是否已经接管 MeterBinder
     * @return 已绑定缓存快照的指标组件
     */
    @Bean
    @ConditionalOnBean(XmlTemplateCompilationCache.class)
    @ConditionalOnMissingBean(XmlTemplateCacheMetrics.class)
    public XmlTemplateCacheMetrics xmlTemplateCacheMetrics(XmlTemplateCompilationCache cache,
                                                            MeterRegistry registry, ListableBeanFactory beanFactory) {
        XmlTemplateCacheMetrics metrics = new XmlTemplateCacheMetrics(cache);
        if (!beanFactory.containsBean(METER_REGISTRY_POST_PROCESSOR_BEAN_NAME)) {
            // 纯 Micrometer 环境没有统一绑定器，需要在这里完成注册。
            metrics.bindTo(registry);
        }
        return metrics;
    }
}
