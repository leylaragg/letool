package io.github.leylaragg.letool.print.pipeline;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.exception.core.BusinessException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintErrorCode;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认打印引擎的流式路由、限制和异常边界测试。
 *
 * @author leyland
 */
class DefaultPrintEngineTest {

    /** 模拟由外部渲染器声明的非内置输出。 */
    private static final OutputFormat HTML = new OutputFormat("html", "text/html", "html");

    /** 验证流式入口按模板格式路由，并把实际摘要和长度返回给调用方。 */
    @Test
    void shouldRenderToCallerOutput() {
        PrintPipeline pipeline = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            output.write("pdf".getBytes(StandardCharsets.UTF_8));
            return output.complete(request.outputFormat(), Map.of("pages", "1"));
        });
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        PrintResult result = engine(pipeline).renderTo(request(OutputFormat.PDF, 1024 * 1024), target);

        assertThat(target.toString(StandardCharsets.UTF_8)).isEqualTo("pdf");
        assertThat(result.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(result.contentLength()).isEqualTo(3);
        assertThat(result.metadata()).containsEntry("pages", "1");
    }

    /** 验证内存便捷入口复用流式主链路，并返回独立的产物内容。 */
    @Test
    void shouldBuildMemoryArtifactFromStreamingResult() {
        PrintPipeline pipeline = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            output.write("pdf".getBytes(StandardCharsets.UTF_8));
            return output.complete(request.outputFormat(), Map.of("pages", "1"));
        });

        PrintArtifact artifact = engine(pipeline).render(request(OutputFormat.PDF, 1024 * 1024));

        assertThat(artifact.content()).isEqualTo("pdf".getBytes(StandardCharsets.UTF_8));
        assertThat(artifact.contentLength()).isEqualTo(3);
        assertThat(artifact.metadata()).containsEntry("pages", "1");
    }

    /** 验证不支持输出时不会执行管线，也不会写入调用方目标。 */
    @Test
    void shouldRejectUnsupportedOutputBeforeRendering() {
        AtomicBoolean called = new AtomicBoolean();
        PrintPipeline pipeline = pipeline(Set.of(HTML), (request, output) -> {
            called.set(true);
            throw new AssertionError("不应执行");
        });
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        assertThatThrownBy(() -> engine(pipeline).renderTo(
                request(OutputFormat.PDF, 1024 * 1024), target))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_003");
        assertThat(called).isFalse();
        assertThat(target.size()).isZero();
    }

    /** 验证管线只能返回当前输出自己完成的结果。 */
    @Test
    void shouldRejectMissingOrForeignResult() {
        PrintPipeline missingResult = pipeline(Set.of(OutputFormat.PDF), (request, output) -> null);
        assertThatThrownBy(() -> engine(missingResult).renderTo(
                request(OutputFormat.PDF, 1024 * 1024), new ByteArrayOutputStream()))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_006");

        PrintPipeline foreignResult = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            PrintOutput foreign = new PrintOutput(new ByteArrayOutputStream(), 1024 * 1024);
            foreign.write(1);
            return foreign.complete(OutputFormat.PDF, Map.of());
        });
        assertThatThrownBy(() -> engine(foreignResult).renderTo(
                request(OutputFormat.PDF, 1024 * 1024), new ByteArrayOutputStream()))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_006");
    }

    /** 验证超过最终产物上限时，本批内容不会部分写入调用方目标。 */
    @Test
    void shouldRejectOversizedOutputBeforeBatchWrite() {
        PrintPipeline pipeline = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            output.write(new byte[1024 * 1024 + 1]);
            return output.complete(OutputFormat.PDF, Map.of());
        });
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        assertThatThrownBy(() -> engine(pipeline).renderTo(
                request(OutputFormat.PDF, 1024 * 1024), target))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_007");
        assertThat(target.size()).isZero();
    }

    /** 验证既有基础异常不包装，未知运行时故障保留原因链。 */
    @Test
    void shouldPreserveKnownExceptionsAndWrapUnknownFailures() {
        BusinessException known = BusinessException.of(PrintErrorCode.INVALID_REQUEST, "known");
        PrintPipeline knownFailure = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            throw known;
        });
        assertThatThrownBy(() -> engine(knownFailure).render(
                request(OutputFormat.PDF, 1024 * 1024))).isSameAs(known);

        IllegalStateException unknown = new IllegalStateException("secret detail");
        PrintPipeline unknownFailure = pipeline(Set.of(OutputFormat.PDF), (request, output) -> {
            throw unknown;
        });
        assertThatThrownBy(() -> engine(unknownFailure).render(
                request(OutputFormat.PDF, 1024 * 1024)))
                .isInstanceOf(PrintPipelineException.class)
                .hasMessageContaining("PRINT_006")
                .hasCause(unknown)
                .hasMessageNotContaining("secret detail");
    }

    /** 创建只注册测试管线的打印引擎。 */
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
                new RenderOptions(100, maxBytes, maxBytes * 3, true));
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
            public PrintResult render(PrintRequest request, PrintOutput output) {
                return action.render(request, output);
            }
        };
    }

    /** 假管线的可控渲染行为。 */
    @FunctionalInterface
    private interface RenderAction {

        /**
         * 执行测试指定的管线行为。
         *
         * @param request 打印请求
         * @param output 受框架治理的输出
         * @return 管线生成的结果
         */
        PrintResult render(PrintRequest request, PrintOutput output);
    }
}
