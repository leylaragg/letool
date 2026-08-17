package io.github.leylaragg.letool.print.render;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档渲染器注册表的不可变路由契约测试。
 *
 * @author leyland
 */
class DocumentRendererRegistryTest {

    /** 验证输出格式能够稳定找到构造时冻结的渲染器。 */
    @Test
    void shouldFindRendererByOutputFormat() {
        DocumentRenderer pdf = renderer(OutputFormat.PDF);
        DocumentRendererRegistry registry = new DocumentRendererRegistry(List.of(pdf));

        assertThat(registry.require(OutputFormat.PDF)).isSameAs(pdf);
        assertThat(registry.registeredFormats()).containsExactly(OutputFormat.PDF);
        assertThatThrownBy(() -> registry.registeredFormats().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 同一种输出只能由一个渲染器负责，避免装配顺序决定结果。 */
    @Test
    void shouldRejectDuplicateOutputFormat() {
        assertThatThrownBy(() -> new DocumentRendererRegistry(List.of(
                renderer(OutputFormat.PDF), renderer(OutputFormat.PDF))))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_008")
                .hasMessageContaining("pdf");
    }

    /** 空注册表无法形成可用管线，应在装配阶段直接拒绝。 */
    @Test
    void shouldRejectEmptyRendererCollection() {
        assertThatThrownBy(() -> new DocumentRendererRegistry(List.of()))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_008");
    }

    /** 未注册的输出格式走稳定打印异常，而不是返回空值。 */
    @Test
    void shouldRejectUnknownOutputFormat() {
        OutputFormat html = new OutputFormat("html", "text/html", "html");
        DocumentRendererRegistry registry = new DocumentRendererRegistry(
                List.of(renderer(OutputFormat.PDF)));

        assertThatThrownBy(() -> registry.require(html))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_003")
                .hasMessageContaining("html");
    }

    /** 创建只参与注册表测试的最小渲染器。 */
    private static DocumentRenderer renderer(OutputFormat outputFormat) {
        return new DocumentRenderer() {
            @Override
            public OutputFormat outputFormat() {
                return outputFormat;
            }

            @Override
            public OutputCapability capability() {
                return new OutputCapability(Set.of(TextNode.class));
            }

            @Override
            public RenderedDocument render(DocumentModel document, RenderOptions options) {
                return new RenderedDocument(outputFormat, new byte[]{1}, Map.of());
            }
        };
    }
}
