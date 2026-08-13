package com.github.leyland.letool.print.api;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印公开值对象的契约测试。
 *
 * @author leyland
 */
class PrintValueObjectTest {

    /** 验证可扩展公开 API 使用普通不可变类，避免将 record 作为默认建模方式。 */
    @Test
    void shouldPreferRegularClassesForExtensiblePublicApi() {
        assertThat(List.of(
                TemplateFormat.class,
                OutputFormat.class,
                RenderOptions.class,
                PrintRequest.class))
                .allMatch(type -> !type.isRecord());
    }

    /** 验证可扩展格式标识会被规范化。 */
    @Test
    void shouldNormalizeExtensibleFormatIdentifiers() {
        assertThat(new TemplateFormat(" LETOOL-XML ")).isEqualTo(TemplateFormat.LETOOL_XML);
        assertThat(new OutputFormat(" PDF ", " application/pdf ", ".PDF"))
                .isEqualTo(OutputFormat.PDF);
    }

    /** 验证空白或具有路径语义的格式标识会被拒绝。 */
    @Test
    void shouldRejectBlankOrUnsafeFormatIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TemplateFormat(" "));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new OutputFormat("../pdf", "application/pdf", "pdf"));
    }

    /** 验证模板内容与调用方持有的可变数组隔离。 */
    @Test
    void shouldDefensivelyCopyTemplateContent() {
        byte[] content = "<document/>".getBytes(StandardCharsets.UTF_8);
        PrintTemplate template = new PrintTemplate(
                "policy", TemplateFormat.LETOOL_XML, 1, 7L, 2, content);

        content[0] = 'X';
        byte[] returned = template.content();
        returned[0] = 'Y';

        assertThat(new String(template.content(), StandardCharsets.UTF_8))
                .isEqualTo("<document/>");
    }

    /** 验证模板版本、内容和代码边界。 */
    @Test
    void shouldRejectInvalidTemplateBoundaries() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException().isThrownBy(
                () -> new PrintTemplate(" ", TemplateFormat.LETOOL_XML, 1, 1, 1, content));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PrintTemplate("code", TemplateFormat.LETOOL_XML, 0, 1, 1, content));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PrintTemplate("code", TemplateFormat.LETOOL_XML, 1, 1, 1, new byte[0]));
    }

    /** 验证渲染限制只能落在经过评估的安全范围内。 */
    @Test
    void shouldValidateRenderLimits() {
        assertThat(RenderOptions.defaults())
                .isEqualTo(new RenderOptions(2_500, 100L * 1024 * 1024, true));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RenderOptions(0, 10L * 1024 * 1024, true));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RenderOptions(100, 1024, true));
    }

    /** 验证请求拒绝模板与上下文版本不一致。 */
    @Test
    void shouldRequireMatchingContextVersion() {
        PrintTemplate template = new PrintTemplate(
                "policy", TemplateFormat.LETOOL_XML, 1, 7, 2,
                "<document/>".getBytes(StandardCharsets.UTF_8));
        PrintContext context = PrintContext.of(3, JsonNodeFactory.instance.objectNode());

        assertThatThrownBy(() -> new PrintRequest(
                template,
                context,
                OutputFormat.PDF,
                Locale.SIMPLIFIED_CHINESE,
                ZoneId.of("Asia/Shanghai"),
                RenderOptions.defaults()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("PRINT_001");
    }

    /** 验证产物内容、元数据和摘要由产物自身安全管理。 */
    @Test
    void shouldProtectArtifactContentAndMetadata() {
        byte[] content = "pdf".getBytes(StandardCharsets.UTF_8);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pages", "1");

        PrintArtifact artifact = PrintArtifact.of(OutputFormat.PDF, content, metadata);
        content[0] = 'X';
        metadata.put("pages", "2");
        byte[] returned = artifact.content();
        returned[0] = 'Y';

        assertThat(new String(artifact.content(), StandardCharsets.UTF_8)).isEqualTo("pdf");
        assertThat(artifact.contentLength()).isEqualTo(3);
        assertThat(artifact.metadata()).containsExactly(Map.entry("pages", "1"));
        assertThat(artifact.sha256())
                .isEqualTo("c35b21d6ca39aa7cc3b79a705d989f1a6e88b99ab43988d74048799e3db926a3");
        assertThatThrownBy(() -> artifact.metadata().put("pages", "3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
