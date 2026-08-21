package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.render.DocumentRenderer;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.render.OutputCapability;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 XML 管线在接触业务上下文前完成静态输出能力检查。
 *
 * @author leyland
 */
class XmlPreBindingCapabilityTest {

    /** 不支持的页面特性应先失败，字段路径不会被绑定器读取。 */
    @Test
    void shouldRejectUnsupportedFeatureBeforeReadingContext() {
        TrackingObjectNode data = new TrackingObjectNode();
        data.put("secret", "business-value");
        TestRenderer renderer = new TestRenderer(Set.of(ParagraphNode.class, TextNode.class));
        TestFixture fixture = fixture(renderer, """
                <document xmlns="https://leyland.github.io/letool/print/v1"
                          context-version="1" outputs="pdf">
                    <page>
                        <page-header><paragraph>页眉</paragraph></page-header>
                        <page-body><paragraph><field path="secret"/></paragraph></page-body>
                    </page>
                </document>
                """);

        assertThatThrownBy(() -> fixture.pipeline().render(
                request(fixture.template(), PrintContext.of(1, data)), output()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("PAGE_HEADER")
                .hasMessageNotContaining("business-value");
        assertThat(data.reads()).hasValue(0);
        assertThat(renderer.renderCalls()).hasValue(0);
    }

    /** 节点能力上界也在绑定前检查，不能依赖渲染器自行忽略。 */
    @Test
    void shouldRejectUnsupportedNodeTypeBeforeBinding() {
        TrackingObjectNode data = new TrackingObjectNode();
        data.put("secret", "business-value");
        TestRenderer renderer = new TestRenderer(Set.of(TextNode.class));
        TestFixture fixture = fixture(renderer, document("<field path=\"secret\"/>"));

        assertThatThrownBy(() -> fixture.pipeline().render(
                request(fixture.template(), PrintContext.of(1, data)), output()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("ParagraphNode")
                .hasMessageNotContaining("business-value");
        assertThat(data.reads()).hasValue(0);
        assertThat(renderer.renderCalls()).hasValue(0);
    }

    /** 能力通过后仍按正常顺序绑定并由当前渲染器完成输出。 */
    @Test
    void shouldBindAndRenderAfterStaticCapabilityPasses() {
        TrackingObjectNode data = new TrackingObjectNode();
        data.put("name", "Leyland");
        TestRenderer renderer = new TestRenderer(Set.of(ParagraphNode.class, TextNode.class));
        TestFixture fixture = fixture(renderer, document("<field path=\"name\"/>"));

        PrintResult result = fixture.pipeline().render(
                request(fixture.template(), PrintContext.of(1, data)), output());

        assertThat(data.reads().get()).isPositive();
        assertThat(renderer.renderCalls()).hasValue(1);
        assertThat(result.outputFormat()).isEqualTo(OutputFormat.PDF);
    }

    /** 未分类的第三方渲染异常使用格式级错误，并保留受控原因链。 */
    @Test
    void shouldConvertUnknownRendererFailureSafely() {
        IllegalStateException failure = new IllegalStateException("secret-renderer-detail");
        TestRenderer renderer = new TestRenderer(
                Set.of(ParagraphNode.class, TextNode.class), failure);
        TestFixture fixture = fixture(renderer, document("正文"));

        assertThatThrownBy(() -> fixture.pipeline().render(
                request(fixture.template(), PrintContext.of(
                        1, JsonNodeFactory.instance.objectNode())), output()))
                .isInstanceOf(PrintRenderingException.class)
                .hasCause(failure)
                .hasMessageNotContaining("secret-renderer-detail");
    }

    /** 创建包含单个正文段落的模板。 */
    private String document(String inline) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\" outputs=\"pdf\"><page><page-body>"
                + "<paragraph>" + inline + "</paragraph></page-body></page></document>";
    }

    /** 发布模板并组合只包含当前测试渲染器的管线。 */
    private TestFixture fixture(DocumentRenderer renderer, String source) {
        TemplateRepository repository = new InMemoryTemplateRepository();
        PrintTemplate template = new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, XmlDsl.VERSION, 1, 1,
                source.getBytes(StandardCharsets.UTF_8));
        new TemplateSetPublisher(repository, List.of()).publishAndActivate(
                1, List.of(new TemplateDefinition(TemplateType.DOCUMENT, template)));
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(
                new XmlTemplateSetCompiler(new XmlTemplateCompiler()));
        XmlPrintPipeline pipeline = new XmlPrintPipeline(
                new XmlTemplateCompilationService(repository, cache),
                new XmlTemplateBinder(),
                new DocumentRendererRegistry(List.of(renderer)),
                1);
        return new TestFixture(template, pipeline);
    }

    /** 创建使用指定上下文的同步 PDF 请求。 */
    private PrintRequest request(PrintTemplate template, PrintContext context) {
        return new PrintRequest(template, context, OutputFormat.PDF, Locale.CHINA,
                ZoneId.of("Asia/Shanghai"), RenderOptions.defaults());
    }

    /** 创建测试使用的受控内存输出。 */
    private PrintOutput output() {
        return new PrintOutput(
                new ByteArrayOutputStream(), RenderOptions.DEFAULT_MAX_OUTPUT_BYTES);
    }

    /** 测试模板与对应管线的不可变组合。 */
    private static final class TestFixture {

        /** 已发布模板。 */
        private final PrintTemplate template;

        /** 使用同一仓库快照的 XML 管线。 */
        private final XmlPrintPipeline pipeline;

        /** 保存本次测试的模板和管线。 */
        private TestFixture(PrintTemplate template, XmlPrintPipeline pipeline) {
            this.template = template;
            this.pipeline = pipeline;
        }

        /** @return 已发布模板 */
        private PrintTemplate template() {
            return template;
        }

        /** @return 待测 XML 管线 */
        private XmlPrintPipeline pipeline() {
            return pipeline;
        }
    }

    /** 记录绑定路径读取次数，但不影响测试输入的 JSON 语义。 */
    @SuppressWarnings("unchecked")
    private static final class TrackingObjectNode extends ObjectNode {

        /** 业务字段读取次数。 */
        private final AtomicInteger reads = new AtomicInteger();

        /** 创建使用标准 Jackson 工厂的对象节点。 */
        private TrackingObjectNode() {
            super(JsonNodeFactory.instance);
        }

        /** 保持测试探针身份，便于观察 PrintContext 之后的读取。 */
        @Override
        public ObjectNode deepCopy() {
            return this;
        }

        /** 记录绑定器对业务字段的实际读取。 */
        @Override
        public com.fasterxml.jackson.databind.JsonNode get(String propertyName) {
            reads.incrementAndGet();
            return super.get(propertyName);
        }

        /** @return 业务字段读取次数 */
        private AtomicInteger reads() {
            return reads;
        }
    }

    /** 可限制能力并记录调用次数的测试渲染器。 */
    private static final class TestRenderer implements DocumentRenderer {

        /** 当前测试声明的输出能力。 */
        private final OutputCapability capability;

        /** 可选的未知渲染失败。 */
        private final RuntimeException failure;

        /** render 实际调用次数。 */
        private final AtomicInteger renderCalls = new AtomicInteger();

        /** 创建不支持高级特性的成功渲染器。 */
        private TestRenderer(Set<Class<? extends DocumentNode>> nodeTypes) {
            this(nodeTypes, null);
        }

        /** 创建可按需失败的测试渲染器。 */
        private TestRenderer(
                Set<Class<? extends DocumentNode>> nodeTypes, RuntimeException failure) {
            this.capability = new OutputCapability(nodeTypes);
            this.failure = failure;
        }

        /** @return PDF 输出格式 */
        @Override
        public OutputFormat outputFormat() {
            return OutputFormat.PDF;
        }

        /** @return 当前测试声明的输出能力 */
        @Override
        public OutputCapability capability() {
            return capability;
        }

        /** 写入最小测试产物，或抛出调用方安排的未知异常。 */
        @Override
        public PrintResult render(
                DocumentModel document, RenderOptions options, PrintOutput output) {
            renderCalls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            output.write("%PDF-test".getBytes(StandardCharsets.US_ASCII));
            return output.complete(OutputFormat.PDF, Map.of());
        }

        /** @return render 实际调用次数 */
        private AtomicInteger renderCalls() {
            return renderCalls;
        }
    }
}
