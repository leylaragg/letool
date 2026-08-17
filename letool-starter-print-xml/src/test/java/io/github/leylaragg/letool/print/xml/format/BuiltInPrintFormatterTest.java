package io.github.leylaragg.letool.print.xml.format;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内置数字和时间格式化器测试。
 *
 * @author leyland
 */
class BuiltInPrintFormatterTest {

    /** Starter 可以取得内置实例快照，并与宿主格式化器一次性合并。 */
    @Test
    void shouldExposeImmutableBuiltInFormatterList() {
        assertThat(BuiltInPrintFormatters.formatters())
                .extracting(PrintValueFormatter::name)
                .containsExactly("number", "date", "datetime");
        assertThatThrownBy(() -> BuiltInPrintFormatters.formatters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证数字格式化器支持固定区域、模式和舍入方式。 */
    @Test
    void shouldFormatNumberWithStaticOptions() {
        PrintFormatPlan plan = compile("number", Map.of(
                "pattern", "#,##0.00",
                "locale", "en-US",
                "rounding-mode", "HALF_UP"));

        assertThat(plan.format(JsonNodeFactory.instance.numberNode(new BigDecimal("1234.567"))))
                .isEqualTo("1,234.57");
    }

    /** 验证数字默认格式不会输出科学计数法或无意义尾零。 */
    @Test
    void shouldUseStableDefaultNumberText() {
        PrintFormatPlan plan = compile("number", Map.of());

        assertThat(plan.format(JsonNodeFactory.instance.numberNode(new BigDecimal("1230.00"))))
                .isEqualTo("1230");
    }

    /** 验证日期支持 ISO 输入和静态输出模式。 */
    @Test
    void shouldFormatIsoDate() {
        PrintFormatPlan plan = compile("date", Map.of("pattern", "yyyy/MM/dd"));

        assertThat(plan.format(JsonNodeFactory.instance.textNode("2026-08-13")))
                .isEqualTo("2026/08/13");
    }

    /** 验证日期时间可以按 epoch millis 和目标时区输出。 */
    @Test
    void shouldFormatEpochMillisInConfiguredZone() {
        PrintFormatPlan plan = compile("datetime", Map.of(
                "pattern", "yyyy-MM-dd HH:mm",
                "zone-id", "Asia/Shanghai"));

        assertThat(plan.format(JsonNodeFactory.instance.numberNode(0L)))
                .isEqualTo("1970-01-01 08:00");
    }

    /** 验证日期时间缺省支持三种 ISO 字符串，并统一转换到 UTC。 */
    @Test
    void shouldFormatSupportedIsoDateTimeForms() {
        PrintFormatPlan plan = compile("datetime", Map.of("pattern", "yyyy-MM-dd HH:mm XXX"));

        assertThat(plan.format(JsonNodeFactory.instance.textNode("2026-08-13T12:00:00Z")))
                .isEqualTo("2026-08-13 12:00 Z");
        assertThat(plan.format(JsonNodeFactory.instance.textNode("2026-08-13T20:00:00+08:00")))
                .isEqualTo("2026-08-13 12:00 Z");
        assertThat(plan.format(JsonNodeFactory.instance.textNode("2026-08-13T12:00:00")))
                .isEqualTo("2026-08-13 12:00 Z");
    }

    /** 验证自定义输入模式在编译计划中固定。 */
    @Test
    void shouldUseCustomTemporalInputPattern() {
        PrintFormatPlan plan = compile("datetime", Map.of(
                "input-pattern", "yyyy/MM/dd HH:mm",
                "pattern", "dd-MM-yyyy HH:mm"));

        assertThat(plan.format(JsonNodeFactory.instance.textNode("2026/08/13 09:30")))
                .isEqualTo("13-08-2026 09:30");
    }

    /** 验证缺省区域和时区不受 JVM 全局默认值影响。 */
    @Test
    void shouldIgnoreJvmDefaultLocaleAndZone() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

            assertThat(compile("datetime", Map.of("pattern", "yyyy-MM-dd HH:mm"))
                    .format(JsonNodeFactory.instance.numberNode(0L)))
                    .isEqualTo("1970-01-01 00:00");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    /** 验证非法选项在计划编译阶段失败。 */
    @Test
    void shouldRejectUnknownAndInvalidOptions() {
        assertThatThrownBy(() -> compile("number", Map.of("unknown", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选项");
        assertThatThrownBy(() -> compile("datetime", Map.of("zone-id", "Invalid/Zone")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("时区");
        assertThatThrownBy(() -> compile("number", Map.of("locale", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale");
        assertThatThrownBy(() -> compile("date", Map.of("pattern", "unterminated'")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pattern");
        assertThatThrownBy(() -> compile("number", Map.of("rounding-mode", "UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rounding-mode");
    }

    /** 验证格式化器不会接受错误 JSON 类型。 */
    @Test
    void shouldRejectWrongValueTypeWithoutEchoingValue() {
        PrintFormatPlan plan = compile("number", Map.of());

        assertThatThrownBy(() -> plan.format(JsonNodeFactory.instance.textNode("secret-value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-value");
        PrintFormatPlan dateTime = compile("datetime", Map.of());
        assertThatThrownBy(() -> dateTime.format(
                JsonNodeFactory.instance.numberNode(new BigDecimal("1.5"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("1.5");
    }

    /** 编译一个内置格式化计划。 */
    private static PrintFormatPlan compile(String name, Map<String, String> options) {
        return BuiltInPrintFormatters.registry().require(name).compile(
                options, new FormatCompileContext("test", "/document/page/field", 1, 1));
    }
}
