package io.github.leylaragg.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印值格式化器注册表契约测试。
 *
 * @author leyland
 */
class PrintFormatterRegistryTest {

    /** 验证注册表会冻结调用方集合并按稳定名称查找。 */
    @Test
    void shouldCreateImmutableFormatterSnapshot() {
        List<PrintValueFormatter> formatters = new ArrayList<>();
        formatters.add(formatter("custom"));
        PrintFormatterRegistry registry = new PrintFormatterRegistry(formatters);

        formatters.clear();

        assertThat(registry.require("custom").name()).isEqualTo("custom");
        assertThat(registry.names()).containsExactly("custom");
        assertThatThrownBy(() -> registry.names().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证重复名称会在构造注册表时立即失败。 */
    @Test
    void shouldRejectDuplicateFormatterNames() {
        assertThatThrownBy(() -> new PrintFormatterRegistry(List.of(
                formatter("custom"), formatter("custom"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    /** 验证格式化器名称只能使用受限标识符。 */
    @Test
    void shouldRejectUnsafeFormatterName() {
        assertThatThrownBy(() -> new PrintFormatterRegistry(List.of(formatter("spel:value"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
        assertThatThrownBy(() -> new PrintFormatterRegistry(List.of(formatter("Custom"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称");
    }

    /** 验证未知格式化器不会被静默忽略。 */
    @Test
    void shouldRejectMissingFormatter() {
        PrintFormatterRegistry registry = new PrintFormatterRegistry(List.of(formatter("custom")));

        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    /** 创建用于注册表测试的无状态格式化器。 */
    private static PrintValueFormatter formatter(String name) {
        return new PrintValueFormatter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public PrintFormatPlan compile(
                    Map<String, String> options, FormatCompileContext context) {
                return JsonNode::asText;
            }
        };
    }
}
