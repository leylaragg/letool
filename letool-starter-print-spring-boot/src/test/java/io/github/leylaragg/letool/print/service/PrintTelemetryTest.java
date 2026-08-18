package io.github.leylaragg.letool.print.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印门面安全观测契约测试。
 *
 * @author leyland
 */
class PrintTelemetryTest {

    /** 成功快照只记录低基数运行数据，不保存业务请求。 */
    @Test
    void shouldRecordSafeSuccessSnapshot() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        PrintArtifact artifact = PrintArtifact.of(OutputFormat.PDF, new byte[]{1, 2, 3}, Map.of("pageCount", "2"));
        PrintService service = service(request -> artifact, telemetry);

        PrintArtifact rendered = service.render("invoice", "secret-business-value");

        assertThat(rendered).isSameAs(artifact);
        assertThat(telemetry.snapshot.get()).satisfies(snapshot -> {
            assertThat(snapshot.success()).isTrue();
            assertThat(snapshot.outputFormat()).isEqualTo("pdf");
            assertThat(snapshot.failure()).isEqualTo(PrintFailureCategory.NONE);
            assertThat(snapshot.durationNanos()).isPositive();
            assertThat(snapshot.pageCount()).isEqualTo(2);
            assertThat(snapshot.outputBytes()).isEqualTo(3);
            assertThat(snapshot.toString()).doesNotContain("secret-business-value");
        });
    }

    /** 渲染失败保持原异常，并记录不含底层消息的稳定分类。 */
    @Test
    void shouldRecordRenderingFailureWithoutChangingException() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        PrintRenderingException expected = PrintRenderingException.renderFailed(
                OutputFormat.PDF, new IllegalStateException("secret-render-message"));
        PrintService service = service(request -> {
            throw expected;
        }, telemetry);

        assertThatThrownBy(() -> service.render("invoice", "secret-business-value"))
                .isSameAs(expected);
        assertThat(telemetry.snapshot.get()).satisfies(snapshot -> {
            assertThat(snapshot.success()).isFalse();
            assertThat(snapshot.failure()).isEqualTo(PrintFailureCategory.RENDERING);
            assertThat(snapshot.outputBytes()).isZero();
            assertThat(snapshot.pageCount()).isZero();
            assertThat(snapshot.toString())
                    .doesNotContain("secret-render-message", "secret-business-value");
        });
    }

    /** IO 原因统一归为资源故障，不把路径或异常类型变成指标标签。 */
    @Test
    void shouldClassifyIoCauseAsResourceFailure() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        PrintService service = service(request -> {
            throw PrintRenderingException.renderFailed(
                    OutputFormat.PDF, new IOException("secret-file-path"));
        }, telemetry);

        assertThatThrownBy(() -> service.render("invoice", 1L))
                .isInstanceOf(PrintRenderingException.class);
        assertThat(telemetry.snapshot.get().failure()).isEqualTo(PrintFailureCategory.RESOURCE);
        assertThat(telemetry.snapshot.get().toString()).doesNotContain("secret-file-path");
    }

    /** 观测器故障不能改变成功产物或原有打印异常。 */
    @Test
    void shouldIgnoreTelemetryFailureOnBothOutcomes() {
        PrintTelemetry brokenTelemetry = snapshot -> {
            throw new IllegalStateException("telemetry unavailable");
        };
        PrintArtifact artifact = PrintArtifact.of(OutputFormat.PDF, new byte[]{1}, Map.of());
        assertThat(service(request -> artifact, brokenTelemetry)
                .render("invoice", 1L)).isSameAs(artifact);

        PrintRenderingException expected = PrintRenderingException.renderFailed(
                OutputFormat.PDF, new IllegalStateException("render failed"));
        PrintService failingService = service(request -> {
            throw expected;
        }, brokenTelemetry);
        assertThatThrownBy(() -> failingService.render("invoice", 1L)).isSameAs(expected);
    }

    /** 组装含一个活动模板和业务定义的打印门面。 */
    private PrintService service(PrintEngine engine, PrintTelemetry telemetry) {
        TemplateRepository repository = new InMemoryTemplateRepository();
        new TemplateSetPublisher(repository, List.of())
                .publishAndActivate(1, List.of(template()));
        PrintDefinition<Object> definition = PrintDefinition.of(
                "invoice", "invoice-template", Object.class,
                request -> PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                        .put("value", String.valueOf(request))));
        PrintRuntimeSettings settings = new PrintRuntimeSettings(
                1, Locale.CHINA, ZoneId.of("Asia/Shanghai"), RenderOptions.defaults());
        PrintDefinitionRegistry definitions = new PrintDefinitionRegistry(List.of(definition));
        return new PrintService(repository, definitions, engine, settings, telemetry);
    }

    /** 创建业务定义引用的最小文档模板。 */
    private TemplateDefinition template() {
        PrintTemplate template = new PrintTemplate(
                "invoice-template", TemplateFormat.LETOOL_XML, 1, 1, 1,
                "<document/>".getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }

    /** 保存最近一次不可变观测快照。 */
    private static final class CapturingTelemetry implements PrintTelemetry {

        /** 每个测试只执行一次打印，原子引用也方便检查可见性。 */
        private final AtomicReference<PrintExecutionSnapshot> snapshot = new AtomicReference<>();

        @Override
        public void record(PrintExecutionSnapshot snapshot) {
            this.snapshot.set(snapshot);
        }
    }
}
