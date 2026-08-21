package io.github.leylaragg.letool.verification;

import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 从独立 Maven 项目验收打印 Starter，避免测试误用 Reactor 源码。
 *
 * @author leyland
 */
@SpringBootTest
class PrintConsumerTest {

    /** 普通模板使用独立版本，避免与 SpEL 场景共享仓储状态。 */
    private static final long PLAIN_VERSION = 101L;

    /** 可选表达式模板使用独立版本。 */
    private static final long SPEL_VERSION = 102L;

    /** 版本快照测试保留的历史集合。 */
    private static final long HISTORICAL_VERSION = 103L;

    /** 版本快照测试最终激活的集合。 */
    private static final long CURRENT_VERSION = 104L;

    /** Spring Starter 提供的模板发布入口。 */
    @Autowired
    private TemplateSetPublisher publisher;

    /** 宿主通过业务定义调用的打印门面。 */
    @Autowired
    private PrintService printService;

    /** 读取 profile 传入的显式 SpEL 开关。 */
    @Autowired
    private Environment environment;

    /** 独立消费者能够通过流式入口生成可重新读取的 PDF。 */
    @Test
    void shouldRenderReadablePdfFromMavenDependency() throws IOException {
        publisher.publishAndActivate(PLAIN_VERSION, List.of(template(PLAIN_VERSION,
                "<paragraph>Consumer <field path=\"name\"/></paragraph>")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintResult result = printService.renderTo("consumer", 7L, output);
        byte[] content = output.toByteArray();

        assertThat(result.contentLength()).isEqualTo(content.length);
        assertThat(content).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(content)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Consumer document-7");
        }
    }

    /** 活动集合切换后，当前调用与历史重打仍各自使用明确快照。 */
    @Test
    void shouldKeepCurrentAndHistoricalTemplateSnapshots() throws IOException {
        publisher.publishAndActivate(HISTORICAL_VERSION, List.of(template(HISTORICAL_VERSION,
                "<paragraph>Historical <field path=\"name\"/></paragraph>")));
        publisher.publishAndActivate(CURRENT_VERSION, List.of(template(CURRENT_VERSION,
                "<paragraph>Current <field path=\"name\"/></paragraph>")));

        String currentText = renderText(null, 9L);
        String historicalText = renderText(HISTORICAL_VERSION, 9L);

        assertThat(currentText).contains("Current document-9").doesNotContain("Historical");
        assertThat(historicalText).contains("Historical document-9").doesNotContain("Current");
    }

    /** 默认拒绝 SpEL；profile 同时增加模块和开关后才进入真实渲染链路。 */
    @Test
    void shouldKeepSpelAsExplicitOptionalCapability() throws IOException {
        boolean spelEnabled = environment.getProperty("letool.print.spel.enabled", Boolean.class, false);
        if (!spelEnabled) {
            assertThat(spelModuleAvailable()).isFalse();
            assertThatThrownBy(() -> publisher.publish(SPEL_VERSION, List.of(spelTemplate())))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("条件表达式语言未注册");
            return;
        }

        assertThat(spelModuleAvailable()).isTrue();
        publisher.publish(SPEL_VERSION, List.of(spelTemplate()));
        assertThat(renderText(SPEL_VERSION, 8L)).contains("Approved document-8");
    }

    /**
     * 通过同一流式入口读取当前或历史版本的 PDF 文本。
     *
     * @param version 模板集合版本，传入 {@code null} 时读取活动集合
     * @param request 文档请求标识
     * @return PDFBox 提取的正文
     */
    private String renderText(Long version, long request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (version == null) {
            printService.renderTo("consumer", request, output);
        } else {
            printService.renderTo(version, "consumer", request, output);
        }
        try (PDDocument document = Loader.loadPDF(output.toByteArray())) {
            return new PDFTextStripper().getText(document);
        }
    }

    /**
     * 包装一份可以直接交给模板集合发布器的 XML 文档。
     *
     * @param version 模板版本
     * @param body 页面正文
     * @return 文档模板定义
     */
    private TemplateDefinition template(long version, String body) {
        String xml = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>"
                + body + "</page-body></page></document>";
        PrintTemplate source = new PrintTemplate(
                "consumer-template", TemplateFormat.LETOOL_XML, XmlDsl.VERSION,
                version, 1, xml.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, source);
    }

    /** @return 只有选装模块能够编译的 SpEL 文档 */
    private TemplateDefinition spelTemplate() {
        return template(SPEL_VERSION,
                "<if expression-language=\"spel\" test=\"approved == true\">"
                        + "<then><paragraph>Approved <field path=\"name\"/></paragraph></then></if>");
    }

    /** @return 当前消费者运行时是否真正包含 SpEL 实现类 */
    private boolean spelModuleAvailable() {
        try {
            Class.forName("io.github.leylaragg.letool.print.spel.RestrictedSpelConditionExpression");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
