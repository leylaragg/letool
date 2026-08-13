package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受限 XML 数据路径的编译契约测试。
 *
 * @author leyland
 */
class XmlDataPathTest {

    /** 验证根路径可以用于行内字段。 */
    @Test
    void shouldCompileRootFieldPath() {
        CompiledXmlTemplate compiled = compile(
                "<page><paragraph>姓名：<field path=\"policy.holder.name\"/></paragraph></page>");

        assertThat(compiled.templateCode()).isEqualTo("contract");
    }

    /** 验证路径不接受数组、通配符、方法和表达式语法。 */
    @Test
    void shouldRejectExecutableOrAmbiguousPaths() {
        List<String> paths = List.of(
                "policy.coverages[0]", "policy.*", "policy..name",
                "policy.getName()", "T(java.lang.Runtime)", "@service");

        for (String path : paths) {
            assertThatThrownBy(() -> compile(
                    "<page><paragraph><field path=\"" + path + "\"/></paragraph></page>"))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列")
                    .hasMessageNotContaining("java.lang.Runtime");
        }
    }

    /** 验证路径长度和字段段数的 Governor 边界。 */
    @Test
    void shouldLimitPathLengthAndSegments() {
        String atSegmentLimit = String.join(".", java.util.Collections.nCopies(32, "a"));
        String overSegmentLimit = atSegmentLimit + ".a";
        String atLengthLimit = "a".repeat(256);
        String overLength = "a".repeat(257);

        assertThat(compile("<page><paragraph><field path=\"" + atSegmentLimit
                + "\"/></paragraph></page>").templateCode()).isEqualTo("contract");
        assertThat(compile("<page><paragraph><field path=\"" + atLengthLimit
                + "\"/></paragraph></page>").templateCode()).isEqualTo("contract");
        for (String path : List.of(overSegmentLimit, overLength)) {
            assertThatThrownBy(() -> compile(
                    "<page><paragraph><field path=\"" + path + "\"/></paragraph></page>"))
                    .isInstanceOf(PrintCompilationException.class);
        }
    }

    /** 验证变量路径不能以空字段段结尾。 */
    @Test
    void shouldRejectTrailingVariablePathSeparator() {
        assertThatThrownBy(() -> compile("""
                <page><for-each items="items" var="item">
                    <paragraph><field path="$item."/></paragraph>
                </for-each></page>
                """))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("contract");
    }

    /** 编译指定页面内容。 */
    private CompiledXmlTemplate compile(String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return new XmlTemplateCompiler().compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
