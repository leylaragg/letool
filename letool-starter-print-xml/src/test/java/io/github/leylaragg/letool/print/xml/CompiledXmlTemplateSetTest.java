package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 模板集合编译快照测试。
 *
 * @author leyland
 */
class CompiledXmlTemplateSetTest {

    /** 文档顺序和来源集合都不应改变已编译快照。 */
    @Test
    void shouldKeepSortedImmutableDocumentSnapshot() {
        Map<String, CompiledXmlTemplate> source = new LinkedHashMap<>();
        source.put("z", compiled("z", 7));
        source.put("a", compiled("a", 7));

        CompiledXmlTemplateSet set = new CompiledXmlTemplateSet(7, "digest", source);
        source.clear();

        assertThat(set.templateSetVersion()).isEqualTo(7);
        assertThat(set.templateSetDigest()).isEqualTo("digest");
        assertThat(set.documentCodes()).containsExactly("a", "z");
        assertThat(set.find("missing")).isEmpty();
        assertThat(set.require("a").templateCode()).isEqualTo("a");
        assertThatThrownBy(() -> set.documents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 缺失文档应沿用打印框架的请求校验异常。 */
    @Test
    void shouldRejectMissingDocument() {
        CompiledXmlTemplateSet set =
                new CompiledXmlTemplateSet(1, "digest", Map.of("main", compiled("main", 1)));

        assertThatThrownBy(() -> set.require("missing"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("missing");
    }

    /** 按给定代码和版本编译一份最小文档。 */
    private CompiledXmlTemplate compiled(String code, long version) {
        String xml = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body><paragraph>ok</paragraph></page-body></page></document>";
        PrintTemplate template = new PrintTemplate(code, TemplateFormat.LETOOL_XML,
                1, version, 1, xml.getBytes(StandardCharsets.UTF_8));
        return new XmlTemplateCompiler().compile(template);
    }
}
