package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.service.PrintRuntimeSettings;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印 Starter 外部化配置与运行时快照测试。
 *
 * @author leyland
 */
class PrintPropertiesTest {

    /** 默认配置与底层打印模块已经评估的边界保持一致。 */
    @Test
    void shouldCreateDefaultRuntimeSettings() {
        PrintProperties properties = new PrintProperties();
        PrintRuntimeSettings settings = properties.toRuntimeSettings();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(settings.rendererProfileVersion()).isEqualTo(1);
        assertThat(settings.locale()).isEqualTo(Locale.forLanguageTag("zh-CN"));
        assertThat(settings.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(settings.renderOptions()).isEqualTo(RenderOptions.defaults());
        assertThat(properties.getTemplateSetCacheCapacity())
                .isEqualTo(XmlTemplateCompilationCache.DEFAULT_TEMPLATE_SET_CAPACITY);
        assertThat(properties.getTemplateCacheCapacity())
                .isEqualTo(XmlTemplateCompilationCache.DEFAULT_TEMPLATE_CAPACITY);
        assertThat(properties.getTemporaryDirectory()).isEmpty();
        assertThat(properties.getMaxAnnotations()).isEqualTo(1_000);
        assertThat(properties.getMaxOutputSize()).isNull();
        assertThat(properties.getMaxTemporarySize()).isNull();
        assertThat(properties.getStartup().isRequireActiveTemplate()).isFalse();
        assertThat(properties.getStartup().isValidateFonts()).isFalse();
        assertThat(properties.getStartup().getFontProbeText()).isEmpty();
        assertThat(properties.getMetrics().isEnabled()).isTrue();
        assertThat(properties.getHealth().isEnabled()).isTrue();
        assertThat(properties.getSpel().isEnabled()).isFalse();
    }

    /** 合法绑定值会在启动时转换成不可变 Java 类型。 */
    @Test
    void shouldConvertConfiguredRuntimeValues() {
        PrintProperties properties = new PrintProperties();
        properties.setRendererProfileVersion(3);
        properties.setLocale("en-US");
        properties.setZoneId("UTC");
        properties.setMaxPages(100);
        properties.setMaxOutputBytes(10L * 1024 * 1024);
        properties.setMaxTemporaryBytes(30L * 1024 * 1024);
        properties.setIncludeDocumentMetadata(false);

        PrintRuntimeSettings settings = properties.toRuntimeSettings();

        assertThat(settings.rendererProfileVersion()).isEqualTo(3);
        assertThat(settings.locale()).isEqualTo(Locale.US);
        assertThat(settings.zoneId()).isEqualTo(ZoneId.of("UTC"));
        assertThat(settings.renderOptions())
                .isEqualTo(new RenderOptions(
                        100, 10L * 1024 * 1024, 30L * 1024 * 1024, false));
    }

    /** 容量单位通过真实 Spring Binder 转换，运行时仍统一使用字节。 */
    @Test
    void shouldBindReadableCapacityUnits() {
        PrintProperties properties = bind(Map.of(
                "letool.print.max-output-size", "20MB",
                "letool.print.max-temporary-size", "60MB"));

        RenderOptions options = properties.toRuntimeSettings().renderOptions();

        assertThat(options.maxOutputBytes()).isEqualTo(20L * 1024 * 1024);
        assertThat(options.maxTemporaryBytes()).isEqualTo(60L * 1024 * 1024);
    }

    /** 同时声明两种写法时，显式容量别名优先于旧字节属性。 */
    @Test
    void shouldPreferReadableSizesOverLegacyByteValues() {
        PrintProperties properties = bind(Map.of(
                "letool.print.max-output-bytes", "1048576",
                "letool.print.max-temporary-bytes", "3145728",
                "letool.print.max-output-size", "20MB",
                "letool.print.max-temporary-size", "60MB"));

        RenderOptions options = properties.toRuntimeSettings().renderOptions();

        assertThat(options.maxOutputBytes()).isEqualTo(20L * 1024 * 1024);
        assertThat(options.maxTemporaryBytes()).isEqualTo(60L * 1024 * 1024);
    }

    /** 旧配置继续参与真实绑定；两个容量别名分别独立覆盖。 */
    @Test
    void shouldRetainLegacyByteSettingsWhenAliasIsAbsent() {
        PrintProperties properties = bind(Map.of(
                "letool.print.max-output-bytes", "10485760",
                "letool.print.max-temporary-bytes", "31457280"));
        assertThat(properties.toRuntimeSettings().renderOptions())
                .isEqualTo(new RenderOptions(2_500, 10L * 1024 * 1024, 30L * 1024 * 1024, true));

        PrintProperties outputAliasOnly = bind(Map.of(
                "letool.print.max-output-size", "20MB",
                "letool.print.max-temporary-bytes", "31457280"));
        assertThat(outputAliasOnly.toRuntimeSettings().renderOptions().maxOutputBytes())
                .isEqualTo(20L * 1024 * 1024);
        assertThat(outputAliasOnly.toRuntimeSettings().renderOptions().maxTemporaryBytes())
                .isEqualTo(30L * 1024 * 1024);

        PrintProperties temporaryAliasOnly = bind(Map.of(
                "letool.print.max-output-bytes", "10485760",
                "letool.print.max-temporary-size", "60MB"));
        assertThat(temporaryAliasOnly.toRuntimeSettings().renderOptions().maxOutputBytes())
                .isEqualTo(10L * 1024 * 1024);
        assertThat(temporaryAliasOnly.toRuntimeSettings().renderOptions().maxTemporaryBytes())
                .isEqualTo(60L * 1024 * 1024);
    }

    /** 只校验实际生效的容量，别名覆盖后旧的零字节值不再参与限制。 */
    @Test
    void shouldValidateOnlyEffectiveCapacityAliases() {
        PrintProperties properties = bind(Map.of(
                "letool.print.max-output-bytes", "0",
                "letool.print.max-temporary-bytes", "0",
                "letool.print.max-output-size", "20MB",
                "letool.print.max-temporary-size", "60MB"));

        assertThat(properties.toRuntimeSettings().renderOptions().maxOutputBytes())
                .isEqualTo(20L * 1024 * 1024);
    }

    /** 快照保留创建时的批注预算，后续修改配置对象不改变运行请求。 */
    @Test
    void shouldBindAnnotationBudgetIntoImmutableRuntimeSettings() {
        PrintProperties properties = bind(Map.of("letool.print.max-annotations", "2001"));
        PrintRuntimeSettings settings = properties.toRuntimeSettings();

        properties.setMaxAnnotations(0);

        assertThat(settings.renderOptions().maxAnnotations()).isEqualTo(2_001);
        assertThat(properties.toRuntimeSettings().renderOptions().maxAnnotations()).isZero();
    }

    @Test
    void shouldRejectNegativeAnnotationBudget() {
        PrintProperties properties = bind(Map.of("letool.print.max-annotations", "-1"));

        assertThatThrownBy(properties::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-annotations");
    }

    @Test
    void shouldBindFontCacheBudgetAndKeepRuntimeSnapshotImmutable() {
        PrintProperties properties = bind(Map.of("letool.print.max-font-cache-bytes", "2048"));
        PrintRuntimeSettings settings = properties.toRuntimeSettings();

        properties.setMaxFontCacheBytes(0);

        assertThat(settings.renderOptions().maxFontCacheBytes()).isEqualTo(2048);
        assertThat(properties.toRuntimeSettings().renderOptions().maxFontCacheBytes()).isZero();
    }

    @Test
    void shouldPreferExplicitFontCacheSizeOverByteBudget() {
        PrintProperties properties = bind(Map.of(
                "letool.print.max-font-cache-bytes", "-1",
                "letool.print.max-font-cache-size", "32MB"));
        assertThat(properties.toRuntimeSettings().renderOptions().maxFontCacheBytes()).isEqualTo(32L * 1024 * 1024);

        PrintProperties disabled = bind(Map.of("letool.print.max-font-cache-size", "0MB"));
        assertThat(disabled.toRuntimeSettings().renderOptions().maxFontCacheBytes()).isZero();
        assertThat(new PrintProperties().getMaxFontCacheSize()).isNull();
        assertThat(new PrintProperties().toRuntimeSettings().renderOptions().maxFontCacheBytes())
                .isEqualTo(64L * 1024 * 1024);
    }

    @Test
    void shouldRejectFontCacheBudgetOutsideSupportedRange() {
        PrintProperties negative = bind(Map.of("letool.print.max-font-cache-bytes", "-1"));
        assertThatThrownBy(negative::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-font-cache-bytes");

        PrintProperties oversized = bind(Map.of("letool.print.max-font-cache-size", "8193MB"));
        assertThatThrownBy(oversized::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-font-cache-size");
    }

    /** 别名同样接受底层容量约束，不把零或临时空间不足解释为无限额。 */
    @Test
    void shouldValidateReadableCapacityBounds() {
        PrintProperties zeroOutput = bind(Map.of("letool.print.max-output-size", "0MB"));
        assertThatThrownBy(zeroOutput::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-output-size");

        PrintProperties insufficientTemporary = bind(Map.of(
                "letool.print.max-output-size", "20MB",
                "letool.print.max-temporary-size", "19MB"));
        assertThatThrownBy(insufficientTemporary::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-temporary-size");
    }

    /** 非法区域、时区和容量只报告属性名，不回显配置内容。 */
    @Test
    void shouldRejectInvalidConfigurationWithoutEchoingValue() {
        PrintProperties invalidLocale = new PrintProperties();
        invalidLocale.setLocale("secret_locale");
        assertThatThrownBy(invalidLocale::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale")
                .hasMessageNotContaining("secret_locale")
                .satisfies(exception -> assertThat(exception.getCause()).isNull());

        PrintProperties invalidZone = new PrintProperties();
        invalidZone.setZoneId("secret-zone");
        assertThatThrownBy(invalidZone::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zone-id")
                .hasMessageNotContaining("secret-zone")
                .satisfies(exception -> assertThat(exception.getCause()).isNull());

        PrintProperties invalidCapacity = new PrintProperties();
        invalidCapacity.setTemplateCacheCapacity(0);
        assertThatThrownBy(invalidCapacity::validateInfrastructureSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template-cache-capacity");

        PrintProperties temporaryBelowOutput = new PrintProperties();
        temporaryBelowOutput.setMaxOutputBytes(10L * 1024 * 1024);
        temporaryBelowOutput.setMaxTemporaryBytes(9L * 1024 * 1024);
        assertThatThrownBy(temporaryBelowOutput::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-temporary-bytes");

        PrintProperties oversizedTemporary = new PrintProperties();
        oversizedTemporary.setMaxTemporaryBytes(8L * 1024 * 1024 * 1024 + 1);
        assertThatThrownBy(oversizedTemporary::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-temporary-bytes");
    }

    /** 非法日志级别只暴露属性名，不保留可能带原始配置值的异常链。 */
    @Test
    void shouldRejectInvalidPdfLogLevelWithoutEchoingValue() {
        PrintProperties properties = bind(Map.of("letool.print.pdf-log-level", "private-log-config"));

        assertThatThrownBy(properties::toRuntimeSettings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("letool.print.pdf-log-level 配置不合法")
                .hasMessageNotContaining("private-log-config")
                .satisfies(exception -> assertThat(exception.getCause()).isNull());
    }

    private PrintProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("letool.print", Bindable.of(PrintProperties.class)).orElseGet(PrintProperties::new);
    }
}
