package com.github.leyland.letool.print.pipeline;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.PrintArtifact;
import com.github.leyland.letool.print.api.PrintRequest;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.exception.PrintPipelineException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印管线只读注册表的契约测试。
 *
 * @author leyland
 */
class PrintPipelineRegistryTest {

    /** 用来证明注册表不会把输出能力和模板格式混为一谈。 */
    private static final OutputFormat HTML = new OutputFormat("html", "text/html", "html");

    /** 验证同一模板格式只能注册一条完整管线。 */
    @Test
    void shouldRejectDuplicateTemplateFormat() {
        PrintPipeline first = pipeline(TemplateFormat.LETOOL_XML, Set.of(OutputFormat.PDF));
        PrintPipeline duplicate = pipeline(TemplateFormat.LETOOL_XML, Set.of(HTML));

        assertThatThrownBy(() -> new PrintPipelineRegistry(List.of(first, duplicate)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_004")
                .hasMessageContaining("letool-xml");
    }

    /** 验证管线必须声明至少一种输出能力。 */
    @Test
    void shouldRejectPipelineWithNoOutput() {
        assertThatThrownBy(() -> new PrintPipelineRegistry(List.of(
                pipeline(TemplateFormat.LETOOL_XML, Set.of()))))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_008");
    }

    /** 创建只用于注册契约的最小假管线。 */
    private static PrintPipeline pipeline(TemplateFormat format, Set<OutputFormat> outputs) {
        return new PrintPipeline() {
            @Override
            public TemplateFormat templateFormat() {
                return format;
            }

            @Override
            public Set<OutputFormat> supportedOutputs() {
                return outputs;
            }

            @Override
            public PrintArtifact render(PrintRequest request) {
                throw new UnsupportedOperationException("注册测试不会执行渲染");
            }
        };
    }
}
