package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML 安全定位异常的消息和原因链测试。
 *
 * @author leyland
 */
class XmlDiagnosticExceptionsTest {

    /** 测试使用的已编译节点位置。 */
    private static final CompiledXmlNode NODE = new CompiledXmlNode(
            "paragraph", Map.of(), List.of(), "", 7, 9,
            "/document/page/page-body/paragraph", null);

    /** 验证源码位置异常的完整消息和可选原因链。 */
    @Test
    void shouldCreateSourceExceptions() {
        PrintCompilationException plain = XmlDiagnosticExceptions.source(
                "contract", 2, 3, "XML 解析失败");
        IllegalStateException cause = new IllegalStateException("不应公开");
        PrintCompilationException caused = XmlDiagnosticExceptions.source(
                "contract", 2, 3, "XML 解析失败", cause);

        assertThat(plain)
                .hasMessage("[PRINT_009] 打印模板编译失败：contract：第 2 行，第 3 列：XML 解析失败");
        assertThat(plain.getCause()).isNull();
        assertThat(caused)
                .hasMessage("[PRINT_009] 打印模板编译失败：contract：第 2 行，第 3 列：XML 解析失败");
        assertThat(caused.getCause()).isSameAs(cause);
    }

    /** 验证显式标签路径和节点路径生成相同的安全位置格式。 */
    @Test
    void shouldCreatePathExceptions() {
        IllegalArgumentException cause = new IllegalArgumentException("不应公开");
        String message = "[PRINT_009] 打印模板编译失败：contract："
                + "/document/page/page-body/paragraph，第 7 行，第 9 列：属性不合法";
        PrintCompilationException pathCaused = XmlDiagnosticExceptions.path(
                "contract", NODE.tagPath(), 7, 9, "属性不合法", cause);
        PrintCompilationException nodeCaused = XmlDiagnosticExceptions.path(
                "contract", NODE, "属性不合法", cause);

        assertThat(XmlDiagnosticExceptions.path(
                "contract", NODE.tagPath(), 7, 9, "属性不合法"))
                .hasMessage(message);
        assertThat(XmlDiagnosticExceptions.path(
                "contract", NODE.tagPath(), 7, 9, "属性不合法").getCause()).isNull();
        assertThat(pathCaused).hasMessage(message);
        assertThat(pathCaused.getCause()).isSameAs(cause);
        assertThat(XmlDiagnosticExceptions.path("contract", NODE, "属性不合法"))
                .hasMessage(message);
        assertThat(XmlDiagnosticExceptions.path(
                "contract", NODE, "属性不合法").getCause()).isNull();
        assertThat(nodeCaused).hasMessage(message);
        assertThat(nodeCaused.getCause()).isSameAs(cause);
    }

    /** 验证绑定异常沿用文档校验错误码并保留原因链。 */
    @Test
    void shouldCreateBindingExceptions() {
        RuntimeException cause = new RuntimeException("不应公开");
        String message = "[PRINT_005] 文档模型不合法：contract："
                + "/document/page/page-body/paragraph，第 7 行，第 9 列：绑定失败";
        PrintValidationException plain = XmlDiagnosticExceptions.binding(
                "contract", NODE, "绑定失败");
        PrintValidationException caused = XmlDiagnosticExceptions.binding(
                "contract", NODE, "绑定失败", cause);

        assertThat(plain).hasMessage(message);
        assertThat(plain.getCause()).isNull();
        assertThat(caused).hasMessage(message);
        assertThat(caused.getCause()).isSameAs(cause);
    }
}
