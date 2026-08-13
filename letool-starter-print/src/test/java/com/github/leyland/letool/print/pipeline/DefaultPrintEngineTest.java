package com.github.leyland.letool.print.pipeline;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.PrintArtifact;
import com.github.leyland.letool.print.api.PrintRequest;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.exception.PrintErrorCode;
import com.github.leyland.letool.print.exception.PrintPipelineException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认打印引擎的路由、限制和异常边界测试。
 *
 * @author leyland
 */
class DefaultPrintEngineTest {

    /** 验证引擎按模板格式选择管线并返回匹配产物。 */
    @Test
    void shouldRouteByTemplateFormat() {
        PrintRequest request = request(OutputFormat.PDF, 1024 * 1024);
        PrintArtifact expected = PrintArtifact.of(
                OutputFormat.PDF, "pdf".getBytes(StandardCharsets.UTF_8), Map.of());
        PrintPipeline pipeline = pipeline(Set.of(OutputFormat.PDF), ignored -> expected);

        PrintArtifact actual = new DefaultPrintEngine(
                new PrintPipelineRegistry(List.of(pipeline))).render(request);

        assertThat(actual).isSameAs(expected);
    }

    /** 验证不支持输出时不会执行管线。 */
    @Test
    void shouldRejectUnsupportedOutputBeforeRendering() {
        AtomicBoolean called = new AtomicBoolean();
        PrintPipeline pipeline = pipeline(Set.of(OutputFormat.DOCX), ignored -> {
            called.set(true);
            throw new AssertionError("不应执行");
        });

        assertThatThrownBy(() -> new DefaultPrintEngine(
                new PrintPipelineRegistry(List.of(pipeline))).render(
                request(OutputFormat.PDF, 1024 * 1024)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_003");
        assertThat(called).isFalse();
    }

    /** 验证错误产物格式和超限内容都会在统一门面被拒绝。 */
    @Test
    void shouldValidatePipelineArtifact() {
        PrintPipeline wrongFormat = pipeline(Set.of(OutputFormat.PDF), ignored ->
                PrintArtifact.of(OutputFormat.DOCX, new byte[]{1}, Map.of()));
        assertThatThrownBy(() -> engine(wrongFormat).render(request(OutputFormat.PDF, 1024 * 1024)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_006");

        byte[] oversized = new byte[1024 * 1024 + 1];
        PrintPipeline tooLarge = pipeline(Set.of(OutputFormat.PDF), ignored ->
                PrintArtifact.of(OutputFormat.PDF, oversized, Map.of()));
        assertThatThrownBy(() -> engine(tooLarge).render(request(OutputFormat.PDF, 1024 * 1024)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_007");
    }

    /** 验证既有基础异常不包装，未知运行时故障保留原因链。 */
    @Test
    void shouldPreserveKnownExceptionsAndWrapUnknownFailures() {
        BusinessException known = BusinessException.of(PrintErrorCode.INVALID_REQUEST, "known");
        PrintPipeline knownFailure = pipeline(Set.of(OutputFormat.PDF), ignored -> {
            throw known;
        });
        assertThatThrownBy(() -> engine(knownFailure).render(request(OutputFormat.PDF, 1024 * 1024)))
                .isSameAs(known);

        IllegalStateException unknown = new IllegalStateException("secret detail");
        PrintPipeline unknownFailure = pipeline(Set.of(OutputFormat.PDF), ignored -> {
            throw unknown;
        });
        assertThatThrownBy(() -> engine(unknownFailure).render(request(OutputFormat.PDF, 1024 * 1024)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_006")
                .hasCause(unknown)
                .hasMessageNotContaining("secret detail");
    }

    /** 创建默认打印引擎。 */
    private static DefaultPrintEngine engine(PrintPipeline pipeline) {
        return new DefaultPrintEngine(new PrintPipelineRegistry(List.of(pipeline)));
    }

    /** 创建具有受控输出限制的请求。 */
    private static PrintRequest request(OutputFormat format, long maxBytes) {
        PrintTemplate template = new PrintTemplate(
                "test", TemplateFormat.LETOOL_XML, 1, 1, 1,
                "<document/>".getBytes(StandardCharsets.UTF_8));
        return new PrintRequest(
                template,
                PrintContext.of(1, JsonNodeFactory.instance.objectNode()),
                format,
                java.util.Locale.SIMPLIFIED_CHINESE,
                ZoneId.of("Asia/Shanghai"),
                new RenderOptions(100, maxBytes, true));
    }

    /** 创建行为可控的最小假管线。 */
    private static PrintPipeline pipeline(Set<OutputFormat> outputs, RenderAction action) {
        return new PrintPipeline() {
            @Override
            public TemplateFormat templateFormat() {
                return TemplateFormat.LETOOL_XML;
            }

            @Override
            public Set<OutputFormat> supportedOutputs() {
                return outputs;
            }

            @Override
            public PrintArtifact render(PrintRequest request) {
                return action.render(request);
            }
        };
    }

    /** 假管线的可控渲染行为。 */
    @FunctionalInterface
    private interface RenderAction {
        PrintArtifact render(PrintRequest request);
    }
}
