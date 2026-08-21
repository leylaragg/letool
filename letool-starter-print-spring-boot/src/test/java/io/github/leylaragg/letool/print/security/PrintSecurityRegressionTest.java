package io.github.leylaragg.letool.print.security;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagPlan;
import io.github.leylaragg.letool.print.xml.tag.TagCompileContext;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 从 Starter 公开入口覆盖模板发布、数据绑定和 PDF 渲染的安全边界。
 *
 * @author leyland
 */
class PrintSecurityRegressionTest {

    /** 放在模块构建目录下的独立工作区，避免依赖操作系统临时目录权限。 */
    private final Path temporaryRoot = Path.of(
            "target", "security-workspace", UUID.randomUUID().toString())
            .toAbsolutePath().normalize();

    /** 加载真实打印自动配置和测试业务定义。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.github.leylaragg.letool.print.autoconfigure.PrintSpelAutoConfiguration.class,
                    io.github.leylaragg.letool.print.autoconfigure.PrintAutoConfiguration.class))
            .withUserConfiguration(SecurityConfiguration.class);

    /** XML 外部能力和可能发起二级请求的样式都在发布阶段被拒绝。 */
    @Test
    void shouldRejectExternalXmlFeaturesThroughPublisher() {
        List<String> sources = List.of(
                "<!DOCTYPE document [<!ENTITY secret SYSTEM 'file:///business-secret'>]>"
                        + documentBody("<paragraph>&secret;</paragraph>"),
                documentBody("<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" "
                        + "href=\"file:///business-secret\"/>"),
                "<document xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"" + XmlDsl.NAMESPACE_V1
                        + " file:///business-secret\" context-version=\"1\"><page><page-body>"
                        + "<paragraph>正文</paragraph></page-body></page></document>",
                documentBody("<paragraph style=\"background:url(https://example.invalid/secret.css)\">"
                        + "正文</paragraph>"));

        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            for (String source : sources) {
                assertThatThrownBy(() -> publisher.publish(1, List.of(definition(
                        "security-template", TemplateType.DOCUMENT, 1, source))))
                        .isInstanceOf(PrintValidationException.class)
                        .hasMessageNotContaining("business-secret")
                        .hasMessageNotContaining("file:")
                        .hasMessageNotContaining(source);
            }
        });
    }

    /** 深度、节点数和文本量都要经过 Starter 发布入口的中央治理。 */
    @Test
    void shouldRejectOversizedXmlThroughPublisher() {
        String deep = "<section>".repeat(XmlDsl.MAX_NODE_DEPTH)
                + "<paragraph>正文</paragraph>"
                + "</section>".repeat(XmlDsl.MAX_NODE_DEPTH);
        String manyNodes = "<paragraph/>".repeat(XmlDsl.MAX_NODE_COUNT + 1);
        String longText = "<paragraph>" + "x".repeat(XmlDsl.MAX_TEXT_CHARACTERS + 1)
                + "</paragraph>";

        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            for (String body : List.of(deep, manyNodes, longText)) {
                assertThatThrownBy(() -> publisher.publish(1, List.of(document(1, body))))
                        .isInstanceOf(PrintValidationException.class)
                        .hasMessageNotContaining(body)
                        .hasMessageNotContaining("x".repeat(256));
            }
        });
    }

    /** include 环在集合发布校验阶段终止，不会进入运行时递归。 */
    @Test
    void shouldRejectCyclicIncludeThroughPublisher() {
        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            List<TemplateDefinition> definitions = List.of(
                    definition("security-template", TemplateType.DOCUMENT, 1,
                            documentBody("<include template=\"fragment-a\"/>")),
                    definition("fragment-a", TemplateType.FRAGMENT, 1,
                            fragmentBody("<include template=\"fragment-b\"/>")),
                    definition("fragment-b", TemplateType.FRAGMENT, 1,
                            fragmentBody("<include template=\"fragment-a\"/>")));

            assertThatThrownBy(() -> publisher.publish(1, definitions))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("循环")
                    .hasMessageNotContaining("<include")
                    .hasMessageNotContaining("template=\"");
        });
    }

    /** 受限 SpEL 在真实发布入口拒绝类型、构造器、Bean、Java 元数据和深括号。 */
    @Test
    void shouldRejectDangerousSpelThroughPublisher() {
        List<String> expressions = List.of(
                "T(java.lang.Runtime).getRuntime() != null",
                "new java.lang.String('secret-expression') == 'x'",
                "@environment != null",
                "customer.class != null",
                "(".repeat(33) + "true" + ")".repeat(33));

        contextRunner.withPropertyValues("letool.print.spel.enabled=true").run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            for (String expression : expressions) {
                String body = "<if expression-language=\"spel\" test=\"" + expression
                        + "\"><paragraph>secret-body</paragraph></if>";
                assertThatThrownBy(() -> publisher.publish(1, List.of(definition(
                        "security-template", TemplateType.DOCUMENT, 1, documentBody(body)))))
                        .isInstanceOf(PrintValidationException.class)
                        .hasMessageNotContaining("secret-expression")
                        .hasMessageNotContaining("secret-body")
                        .hasMessageNotContaining(expression);
            }
        });
    }

    /** 缺失路径和标量后的非法遍历使用不同安全错误，且都不回显业务值。 */
    @Test
    void shouldDistinguishUnsafeDataPathsWithoutLeakingValues() {
        assertRenderFailure("<paragraph><field path=\"missing\"/></paragraph>", "不存在");
        assertRenderFailure("<paragraph><field path=\"secret.value\"/></paragraph>", "无法继续遍历");
    }

    /** POJONode 不能穿过扩展数据视图，自定义标签也不能绕开绑定容量。 */
    @Test
    void shouldGovernTrustedExtensionsThroughStarter() {
        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            PrintService service = context.getBean(PrintService.class);

            publisher.publishAndActivate(1, List.of(document(
                    1, "<data-probe/>")));
            assertThatThrownBy(() -> service.render("security", 99L))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageNotContaining("business-secret")
                    .hasMessageNotContaining(SecretValue.class.getName());
        });

        contextRunner.run(context -> {
            TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
            publisher.publishAndActivate(1, List.of(document(1, "<oversized/>")));

            assertThatThrownBy(() -> context.getBean(PrintService.class).render("security", 1L))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("数量超过限制")
                    .hasMessageNotContaining("x".repeat(128));
        });
    }

    /** 外部地址只会作为逻辑资源 ID 进入模型，PDF 能力检查不会尝试读取它。 */
    @Test
    void shouldRejectExternalResourceIdentifiersWithoutIo() {
        List<String> resourceIds = List.of(
                "https://example.invalid/secret.png",
                "file:///business-secret.png",
                "\\\\server\\share\\business-secret.png");
        for (String resourceId : resourceIds) {
            contextRunner.run(context -> {
                String image = "<image resource-id=\"" + resourceId
                        + "\" alt=\"图片\" width=\"10mm\" height=\"10mm\"/>";
                context.getBean(TemplateSetPublisher.class)
                        .publishAndActivate(1, List.of(document(1, image)));

                assertThatThrownBy(() -> context.getBean(PrintService.class)
                        .render("security", 1L))
                        .isInstanceOf(PrintValidationException.class)
                        .hasMessageNotContaining("business-secret")
                        .hasMessageNotContaining(resourceId);
            });
        }
    }

    /** 页数限制从 Starter 配置进入真实 PDF 管线，错误中不包含正文。 */
    @Test
    void shouldEnforcePageLimitThroughStarter() {
        contextRunner.withPropertyValues("letool.print.max-pages=1").run(context -> {
            String body = "<paragraph>secret-first-page</paragraph><page-break/>"
                    + "<paragraph>第二页</paragraph>";
            context.getBean(TemplateSetPublisher.class)
                    .publishAndActivate(1, List.of(document(1, body)));

            assertThatThrownBy(() -> context.getBean(PrintService.class)
                    .render("security", 1L))
                    .isInstanceOf(PrintRenderingException.class)
                    .hasMessageNotContaining("secret-first-page");
        });
    }

    /** 字体实现失败后清理分段工作区，用户错误不包含供应器消息或临时路径。 */
    @Test
    void shouldCleanWorkspaceAndSanitizeRenderingFailure() throws IOException {
        Files.createDirectories(temporaryRoot);
        try {
            contextRunner.withUserConfiguration(BrokenFontConfiguration.class)
                    .withPropertyValues("letool.print.temporary-directory=" + temporaryRoot)
                    .run(context -> {
                        String body = "<table-of-contents/><heading id=\"chapter\" level=\"1\">"
                                + "章节</heading><paragraph>正文</paragraph>";
                        context.getBean(TemplateSetPublisher.class)
                                .publishAndActivate(1, List.of(document(1, body)));

                        assertThatThrownBy(() -> context.getBean(PrintService.class)
                                .render("security", 1L))
                                .isInstanceOf(PrintRenderingException.class)
                                .hasMessageNotContaining("secret-font-path")
                                .hasMessageNotContaining(temporaryRoot.toString());
                        assertDirectoryEmpty(temporaryRoot);
                    });
        } finally {
            Files.deleteIfExists(temporaryRoot);
        }
    }

    /** 成功渲染和发布失败都不会在配置目录下遗留请求工作区。 */
    @Test
    void shouldKeepWorkspaceCleanAfterSuccessAndCompilationFailure() throws IOException {
        Files.createDirectories(temporaryRoot);
        try {
            contextRunner.withPropertyValues("letool.print.temporary-directory=" + temporaryRoot)
                    .run(context -> {
                        TemplateSetPublisher publisher = context.getBean(TemplateSetPublisher.class);
                        publisher.publishAndActivate(1, List.of(document(1, "<paragraph>正文</paragraph>")));
                        assertThat(context.getBean(PrintService.class).render("security", 1L).contentLength())
                                .isPositive();
                        assertDirectoryEmpty(temporaryRoot);

                        assertThatThrownBy(() -> publisher.publish(2, List.of(document(
                                2, "<paragraph>secret-unclosed"))))
                                .isInstanceOf(PrintValidationException.class)
                                .hasMessageNotContaining("secret-unclosed");
                        assertDirectoryEmpty(temporaryRoot);
                    });
        } finally {
            Files.deleteIfExists(temporaryRoot);
        }
    }

    /** 使用独立上下文发布并渲染一份路径错误模板。 */
    private void assertRenderFailure(String body, String expectedDetail) {
        contextRunner.run(context -> {
            context.getBean(TemplateSetPublisher.class)
                    .publishAndActivate(1, List.of(document(1, body)));

            assertThatThrownBy(() -> context.getBean(PrintService.class)
                    .render("security", 1L))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining(expectedDetail)
                    .hasMessageNotContaining("business-secret-value");
        });
    }

    /** 创建安全测试使用的完整文档定义。 */
    private static TemplateDefinition document(long version, String body) {
        return definition("security-template", TemplateType.DOCUMENT, version, documentBody(body));
    }

    /** 创建指定类型和代码的 XML 模板定义。 */
    private static TemplateDefinition definition(
            String code, TemplateType type, long version, String source) {
        PrintTemplate template = new PrintTemplate(
                code, TemplateFormat.LETOOL_XML, XmlDsl.VERSION, version, 1,
                source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(type, template);
    }

    /** 把页内节点包装为完整文档。 */
    private static String documentBody(String body) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>" + body + "</page-body></page></document>";
    }

    /** 把块级节点包装为可 include 的片段。 */
    private static String fragmentBody(String body) {
        return "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\">" + body + "</fragment>";
    }

    /** 确认渲染结束后没有残留请求目录。 */
    private static void assertDirectoryEmpty(Path directory) {
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        } catch (IOException exception) {
            throw new AssertionError("无法检查测试临时目录", exception);
        }
    }

    /** 创建测试自定义标签，所有契约都在注册前显式给出。 */
    private static PrintTagHandler handler(String name, Function<TagCompileContext, PrintTagPlan> compile) {
        return new PrintTagHandler() {
            @Override
            public String tagName() {
                return name;
            }

            @Override
            public TagPlacement placement() {
                return TagPlacement.BLOCK;
            }

            @Override
            public TagContentModel contentModel() {
                return TagContentModel.EMPTY;
            }

            @Override
            public Set<String> allowedAttributes() {
                return Set.of();
            }

            @Override
            public PrintTagPlan compile(TagCompileContext context) {
                return compile.apply(context);
            }
        };
    }

    /** 提供安全回归使用的业务适配器和可信扩展。 */
    @Configuration(proxyBeanMethods = false)
    static class SecurityConfiguration {

        /** @return 根据请求编号构造普通 JSON 或恶意 POJONode 的测试定义 */
        @Bean
        PrintDefinition<Long> securityDefinition() {
            return PrintDefinition.of("security", "security-template", Long.class, request -> {
                ObjectNode root = JsonNodeFactory.instance.objectNode()
                        .put("id", request)
                        .put("secret", "business-secret-value");
                if (request == 99L) {
                    root.putPOJO("pojo", new SecretValue("business-secret"));
                }
                return PrintContext.of(1, root);
            });
        }

        /** @return 会触发扩展只读数据视图检查的数据探针 */
        @Bean
        PrintTagHandler dataProbe() {
            return handler("data-probe", compile -> PrintTagPlan.of(
                    ParagraphNode.class, binding -> new ParagraphNode(
                            "", List.of(new TextNode(binding.data().root().toString())))));
        }

        /** @return 用于证明扩展结果仍受中央文本上限约束的标签 */
        @Bean
        PrintTagHandler oversized() {
            return handler("oversized", compile -> PrintTagPlan.of(
                    ParagraphNode.class, binding -> new ParagraphNode("", List.of(
                            new TextNode("x".repeat(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS + 1))))));
        }
    }

    /** 只在渲染失败用例中提供会抛出私有实现消息的字体。 */
    @Configuration(proxyBeanMethods = false)
    static class BrokenFontConfiguration {

        /** @return 打开时失败的测试字体 */
        @Bean
        PdfFont brokenFont() {
            return new PdfFont("Broken Font", () -> {
                throw new IllegalStateException("secret-font-path");
            }, true);
        }
    }

    /** 模拟不能穿过标准 JSON 边界的宿主业务对象。 */
    private static final class SecretValue {

        /** 不应进入扩展视图或用户异常的业务值。 */
        private final String value;

        /** @param value 测试业务值 */
        private SecretValue(String value) {
            this.value = value;
        }

        /** @return 测试业务值 */
        @SuppressWarnings("unused")
        private String value() {
            return value;
        }
    }
}
