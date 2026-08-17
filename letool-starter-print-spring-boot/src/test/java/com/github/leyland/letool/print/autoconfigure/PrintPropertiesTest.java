package com.github.leyland.letool.print.autoconfigure;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.service.PrintRuntimeSettings;
import com.github.leyland.letool.print.xml.XmlTemplateCompilationCache;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Locale;

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
        properties.setIncludeDocumentMetadata(false);

        PrintRuntimeSettings settings = properties.toRuntimeSettings();

        assertThat(settings.rendererProfileVersion()).isEqualTo(3);
        assertThat(settings.locale()).isEqualTo(Locale.US);
        assertThat(settings.zoneId()).isEqualTo(ZoneId.of("UTC"));
        assertThat(settings.renderOptions())
                .isEqualTo(new RenderOptions(100, 10L * 1024 * 1024, false));
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
    }
}
