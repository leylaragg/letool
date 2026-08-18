package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.autoconfigure.PrintAutoConfiguration;
import io.github.leylaragg.letool.print.autoconfigure.PrintMetricsAutoConfiguration;
import io.github.leylaragg.letool.print.autoconfigure.PrintSpelAutoConfiguration;
import io.github.leylaragg.letool.print.service.PrintExecutionSnapshot;
import io.github.leylaragg.letool.print.service.PrintFailureCategory;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.service.PrintTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印 Micrometer 条件装配与低基数指标测试。
 *
 * @author leyland
 */
class PrintMetricsAutoConfigurationTest {

    /** 加载打印主链路和指标自动配置，但不预设指标注册表。 */
    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrintSpelAutoConfiguration.class,
                    PrintAutoConfiguration.class, PrintMetricsAutoConfiguration.class));

    /** 在基础上下文中加入供指标测试使用的内存注册表。 */
    private final ApplicationContextRunner contextRunner = baseContextRunner
            .withUserConfiguration(MeterRegistryConfiguration.class);

    /** 注册表存在时提供观测端口和双层缓存统计。 */
    @Test
    void shouldRecordSafeRenderAndCacheMetrics() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PrintService.class);
            assertThat(context).hasSingleBean(MicrometerPrintTelemetry.class);
            assertThat(context).hasSingleBean(XmlTemplateCacheMetrics.class);

            PrintTelemetry telemetry = context.getBean(PrintTelemetry.class);
            telemetry.record(PrintExecutionSnapshot.success("pdf", 12_000, 2, 128));
            telemetry.record(PrintExecutionSnapshot.failure("pdf", PrintFailureCategory.RENDERING, 8_000));

            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertThat(registry.find("letool.print.render.duration")
                    .tags("output", "pdf", "result", "success", "failure", "none")
                    .timer().count()).isEqualTo(1);
            assertThat(registry.find("letool.print.render.failures")
                    .tags("output", "pdf", "failure", "rendering")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.find("letool.print.output.bytes")
                    .tag("output", "pdf").summary().totalAmount()).isEqualTo(128);
            assertThat(registry.find("letool.print.output.pages")
                    .tag("output", "pdf").summary().totalAmount()).isEqualTo(2);
            List<String> cacheMetrics = List.of("letool.print.cache.entries", "letool.print.cache.hits",
                    "letool.print.cache.misses", "letool.print.cache.loads.success",
                    "letool.print.cache.loads.failure");
            for (String metric : cacheMetrics) {
                assertThat(registry.find(metric).tag("cache", "template-set").gauge()).isNotNull();
                assertThat(registry.find(metric).tag("cache", "template").gauge()).isNotNull();
            }
        });
    }

    /** 打印门面的失败会经过自动装配的观测端口进入指标注册表。 */
    @Test
    void shouldConnectPrintServiceToMicrometerTelemetry() {
        contextRunner.run(context -> {
            PrintService service = context.getBean(PrintService.class);
            assertThatThrownBy(() -> service.render("missing-definition", 1L))
                    .isInstanceOf(RuntimeException.class);

            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertThat(registry.find("letool.print.render.duration")
                    .tags("output", "pdf", "result", "failure", "failure", "validation")
                    .timer().count()).isEqualTo(1);
            assertThat(registry.find("letool.print.render.failures")
                    .tags("output", "pdf", "failure", "validation")
                    .counter().count()).isEqualTo(1);
        });
    }

    /** 显式关闭指标后，打印门面回退到无操作观测端口。 */
    @Test
    void shouldBackOffWhenMetricsAreDisabled() {
        contextRunner.withPropertyValues("letool.print.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PrintService.class);
                    assertThat(context).doesNotHaveBean(MicrometerPrintTelemetry.class);
                    assertThat(context).doesNotHaveBean(XmlTemplateCacheMetrics.class);
                });
    }

    /** Micrometer 不在运行时类路径时，主打印链路仍可启动。 */
    @Test
    void shouldStartWithoutMicrometerClasses() {
        contextRunner.withClassLoader(new FilteredClassLoader("io.micrometer.core"))
                .run(context -> {
                    assertThat(context).hasSingleBean(PrintService.class);
                    assertThat(context).doesNotHaveBean(MicrometerPrintTelemetry.class);
                });
    }

    /** 只有 Micrometer API、没有指标注册表时，指标装配应主动退让。 */
    @Test
    void shouldBackOffWhenMeterRegistryBeanIsMissing() {
        baseContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PrintService.class);
            assertThat(context).doesNotHaveBean(MicrometerPrintTelemetry.class);
            assertThat(context).doesNotHaveBean(XmlTemplateCacheMetrics.class);
        });
    }

    /** 提供测试使用的内存指标注册表。 */
    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        /** @return 不依赖外部系统的指标注册表 */
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
