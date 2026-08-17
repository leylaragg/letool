package io.github.leylaragg.letool.print.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintAdapterException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring 业务门面在模板版本和适配器边界上的测试。
 *
 * @author leyland
 */
class PrintServiceTest {

    /** 当前版本和指定历史版本都会生成锁定模板的 PDF 请求。 */
    @Test
    void shouldRenderCurrentAndExplicitTemplateVersion() {
        TemplateRepository repository = repositoryWithVersions();
        CapturingEngine engine = new CapturingEngine();
        PrintService service = service(repository, engine, request -> context(1, request));

        service.render("invoice", 42L);
        assertThat(engine.request.get().template().templateSetVersion()).isEqualTo(2);
        assertThat(engine.request.get().outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(engine.request.get().locale()).isEqualTo(Locale.CHINA);

        service.render(1, "invoice", 43L);
        assertThat(engine.request.get().template().templateSetVersion()).isEqualTo(1);
        assertThat(engine.request.get().context().root().path("id").longValue()).isEqualTo(43);
    }

    /** 业务异常保持原类型，未知运行时异常只在原因链保留原始消息。 */
    @Test
    void shouldPreserveLetoolExceptionAndHideUnknownAdapterMessage() {
        TemplateRepository repository = repositoryWithVersions();
        PrintValidationException expected = PrintValidationException.invalidRequest("业务状态不允许打印");
        PrintService businessFailure = service(repository, new CapturingEngine(), request -> {
            throw expected;
        });

        assertThatThrownBy(() -> businessFailure.render("invoice", 1L)).isSameAs(expected);

        PrintService unknownFailure = service(repository, new CapturingEngine(), request -> {
            throw new IllegalStateException("secret-business-value");
        });
        assertThatThrownBy(() -> unknownFailure.render("invoice", 1L))
                .isInstanceOf(PrintAdapterException.class)
                .isInstanceOf(BaseException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRINT_012")
                .hasMessageNotContaining("secret-business-value");
    }

    /** 空上下文、版本不匹配、未知定义和错误请求类型都在调用引擎前拒绝。 */
    @Test
    void shouldValidateBusinessRequestBeforeEngineInvocation() {
        TemplateRepository repository = repositoryWithVersions();
        CapturingEngine engine = new CapturingEngine();

        assertThatThrownBy(() -> service(repository, engine, request -> null)
                .render("invoice", 1L))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("上下文");
        assertThatThrownBy(() -> service(repository, engine, request -> context(2, request))
                .render("invoice", 1L))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("版本");
        assertThatThrownBy(() -> service(repository, engine, request -> context(1, request))
                .render("missing", 1L))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> service(repository, engine, request -> context(1, request))
                .render("invoice", "wrong"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("请求类型");
        assertThat(engine.request).hasNullValue();
    }

    /** 组装只依赖公开契约的业务门面。 */
    private PrintService service(
            TemplateRepository repository,
            PrintEngine engine,
            PrintDataAdapter<Long> adapter) {
        PrintDefinition<Long> definition = PrintDefinition.of(
                "invoice", "invoice-template", Long.class, adapter);
        PrintRuntimeSettings settings = new PrintRuntimeSettings(
                1, Locale.CHINA, ZoneId.of("Asia/Shanghai"), RenderOptions.defaults());
        return new PrintService(
                repository,
                new PrintDefinitionRegistry(List.of(definition)),
                engine,
                settings);
    }

    /** 发布两个版本并把版本 2 设为当前集合。 */
    private TemplateRepository repositoryWithVersions() {
        TemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(repository, List.of());
        publisher.publish(1, List.of(template(1)));
        publisher.publishAndActivate(2, List.of(template(2)));
        return repository;
    }

    /** 创建业务定义指向的文档模板。 */
    private TemplateDefinition template(long version) {
        PrintTemplate template = new PrintTemplate(
                "invoice-template", TemplateFormat.LETOOL_XML, 1, version, 1,
                ("version-" + version).getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }

    /** 创建包含请求编号的只读上下文。 */
    private PrintContext context(int version, Long request) {
        return PrintContext.of(
                version, JsonNodeFactory.instance.objectNode().put("id", request));
    }

    /** 捕获业务门面构造的请求并返回最小产物。 */
    private static final class CapturingEngine implements PrintEngine {

        /** 最近一次进入引擎的请求。 */
        private final AtomicReference<PrintRequest> request = new AtomicReference<>();

        @Override
        public PrintArtifact render(PrintRequest request) {
            this.request.set(request);
            return PrintArtifact.of(OutputFormat.PDF, new byte[]{1}, java.util.Map.of());
        }
    }
}
