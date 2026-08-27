package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetFactory;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.xml.format.FormatCompileContext;
import io.github.leylaragg.letool.print.xml.format.PrintFormatPlan;
import io.github.leylaragg.letool.print.xml.format.PrintFormatterRegistry;
import io.github.leylaragg.letool.print.xml.format.PrintValueFormatter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证单次 XML 绑定只保留必要的数据快照，并继续隔离用户扩展。 */
class XmlBindingSnapshotTest {

    @Test
    void bindingShouldReuseThePrintContextSnapshotInternally() {
        TrackingObjectNode source = new TrackingObjectNode();
        source.put("title", "清单");
        source.putArray("items").addObject().put("name", "A");

        PrintContext context = PrintContext.of(1, source);
        assertThat(source.deepCopies()).hasValue(1);

        CompiledXmlTemplate template =
                new XmlTemplateSetCompiler()
                        .compile(
                                TemplateSetFactory.standard()
                                        .create(
                                                1,
                                                List.of(
                                                        definition(
                                                                TemplateType.DOCUMENT,
                                                                "main",
                                                                document(
                                                                        "<heading><field path=\"title\"/></heading>"
                                                                                + "<include template=\"rows\"/>")),
                                                        definition(
                                                                TemplateType.FRAGMENT,
                                                                "rows",
                                                                fragment(
                                                                        "<for-each items=\"items\" var=\"item\">"
                                                                                + "<paragraph><field path=\"$item.name\"/></paragraph>"
                                                                                + "</for-each>")))))
                        .require("main");

        DocumentModel document = new XmlTemplateBinder().bind(template, context);

        assertThat(document).isNotNull();
        assertThat(source.deepCopies()).hasValue(2);
    }

    @Test
    void formatterMutationShouldNotChangeTheBindingSnapshot() {
        PrintValueFormatter formatter = new PrintValueFormatter() {
            @Override
            public String name() {
                return "mutating";
            }

            @Override
            public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
                return value -> {
                    ((ObjectNode) value).put("name", "changed");
                    return "formatted";
                };
            }
        };
        XmlTemplateCompiler compiler =
                new XmlTemplateCompiler(new PrintFormatterRegistry(List.of(formatter)));
        CompiledXmlTemplate template =
                compiler.compile(
                        template(
                                "formatted",
                                document(
                                        "<paragraph><field path=\"profile\" formatter=\"mutating\"/>"
                                                + "<field path=\"profile.name\"/></paragraph>")));
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.replace("profile", JsonNodeFactory.instance.objectNode().put("name", "original"));

        DocumentModel document = new XmlTemplateBinder().bind(template, PrintContext.of(1, root));
        ParagraphNode paragraph = (ParagraphNode) XmlTestDocuments.body(document).get(0);

        assertThat(paragraph.children())
                .containsExactly(new TextNode("formatted"), new TextNode("original"));
    }

    /** 将源码包装为集合编译器使用的模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        return new TemplateDefinition(type, template(code, source));
    }

    /** 创建指定编号的 XML 模板。 */
    private PrintTemplate template(String code, String source) {
        return new PrintTemplate(
                code, TemplateFormat.LETOOL_XML, 1, 1, 1, source.getBytes(StandardCharsets.UTF_8));
    }

    /** 将块节点放入最小完整文档。 */
    private String document(String blocks) {
        return "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body>"
                + blocks + "</page-body></page></document>";
    }

    /** 将块节点放入共享片段。 */
    private String fragment(String blocks) {
        return "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1 + "\">" + blocks + "</fragment>";
    }

    /** 记录根对象快照次数，同时让每次复制都产生独立数据。 */
    private static final class TrackingObjectNode extends ObjectNode {

        private final AtomicInteger deepCopies;

        private TrackingObjectNode() {
            this(new AtomicInteger());
        }

        private TrackingObjectNode(AtomicInteger deepCopies) {
            super(JsonNodeFactory.instance);
            this.deepCopies = deepCopies;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TrackingObjectNode deepCopy() {
            deepCopies.incrementAndGet();
            TrackingObjectNode copy = new TrackingObjectNode(deepCopies);
            properties().forEach(entry -> copy.replace(entry.getKey(), entry.getValue().deepCopy()));
            return copy;
        }

        /** @return 这一组根对象实际发生的快照次数 */
        private AtomicInteger deepCopies() {
            return deepCopies;
        }
    }
}
