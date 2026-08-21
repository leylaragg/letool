package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.BookmarkNode;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.InternalLinkNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.SectionNode;
import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TableRow;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagPlan;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import io.github.leylaragg.letool.print.xml.tag.TagCompileContext;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagBindingContext;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 可信自定义标签的编译、绑定和中央治理测试。
 *
 * @author leyland
 */
class XmlCustomTagExtensionTest {

    /** 验证块级标签接收白名单属性、只读数据和框架绑定后的块子节点。 */
    @Test
    void shouldBindBlockTagWithControlledChildrenAndData() {
        PrintTagHandler notice = handler(
                "notice", TagPlacement.BLOCK, TagContentModel.BLOCKS, Set.of("id-prefix"),
                SectionNode.class,
                compile -> binding -> new SectionNode(
                        compile.attribute("id-prefix").orElseThrow()
                                + binding.data().root().path("suffix").asText(),
                        binding.blockChildren()));
        CompiledXmlTemplate template = compile(compiler(notice), """
                <page><page-body><notice id-prefix="notice-"><paragraph>正文</paragraph></notice></page-body></page>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("suffix", "one")));

        assertThat(XmlTestDocuments.body(model)).containsExactly(new SectionNode(
                "notice-one", List.of(new ParagraphNode("", List.of(new TextNode("正文"))))));
    }

    /** 验证行内标签接收框架绑定后的行内子节点。 */
    @Test
    void shouldBindInlineTagWithControlledChildren() {
        PrintTagHandler badge = handler(
                "badge", TagPlacement.INLINE, TagContentModel.INLINE, Set.of("prefix"),
                TextNode.class,
                compile -> binding -> new TextNode(
                        compile.attribute("prefix").orElse("")
                                + binding.inlineChildren().stream()
                                .map(TextNode.class::cast)
                                .map(TextNode::text)
                                .reduce("", String::concat)));
        CompiledXmlTemplate template = compile(compiler(badge), """
                <page><page-body><paragraph><badge prefix="[">名称：<field path="name"/></badge></paragraph></page-body></page>
                """);

        DocumentModel model = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("name", "张三")));

        assertThat(XmlTestDocuments.body(model)).containsExactly(new ParagraphNode(
                "", List.of(new TextNode("[名称：张三"))));
    }

    /** 标签贡献在编译时读取一次，inspection 和绑定都复用同一份快照。 */
    @Test
    void shouldFreezeTagInspectionContribution() {
        AtomicInteger contributionReads = new AtomicInteger();
        TemplateInspectionContribution contribution = TemplateInspectionContribution.builder()
                .nodeType(ParagraphNode.class).build();
        PrintTagHandler stable = new PrintTagHandler() {
            @Override
            public String tagName() {
                return "stable-contribution";
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
                return new PrintTagPlan() {
                    @Override
                    public DocumentNode bind(TagBindingContext binding) {
                        return new ParagraphNode("", List.of(new TextNode("稳定")));
                    }

                    @Override
                    public TemplateInspectionContribution inspectionContribution() {
                        if (contributionReads.incrementAndGet() > 1) {
                            throw new IllegalStateException("贡献不能被重复读取");
                        }
                        return contribution;
                    }
                };
            }
        };
        CompiledXmlTemplate template = compile(compiler(stable),
                "<page><page-body><stable-contribution/></page-body></page>");

        DocumentModel model = new XmlTemplateBinder().bind(template, emptyContext());

        assertThat(template.inspection().nodeTypes()).contains(ParagraphNode.class);
        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("稳定"))));
        assertThat(contributionReads).hasValue(1);
    }

    /** 验证循环变量对自定义标签可见且循环后代不能生成稳定 ID。 */
    @Test
    void shouldExposeLoopVariableAndRejectLoopGeneratedId() throws Exception {
        PrintTagHandler itemName = handler(
                "item-name", TagPlacement.INLINE, TagContentModel.EMPTY, Set.of(),
                TextNode.class,
                compile -> binding -> new TextNode(binding.data().variable("item")
                        .orElseThrow().path("name").asText()));
        CompiledXmlTemplate visible = compile(compiler(itemName), """
                <page><page-body><for-each items="items" var="item">
                    <paragraph><item-name/></paragraph>
                </for-each></page-body></page>
                """);
        DocumentModel model = new XmlTemplateBinder().bind(visible, PrintContext.of(1,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        "{\"items\":[{\"name\":\"A\"},{\"name\":\"B\"}]}")));
        assertThat(XmlTestDocuments.body(model)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("A"))),
                new ParagraphNode("", List.of(new TextNode("B"))));

        PrintTagHandler generatedId = handler(
                "generated-id", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                compile -> binding -> new ParagraphNode("dynamic", List.of()));
        CompiledXmlTemplate rejected = compile(compiler(generatedId), """
                <page><page-body><for-each items="items" var="item"><generated-id/></for-each></page-body></page>
                """);
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(rejected, PrintContext.of(1,
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"items\":[{}]}"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("循环后代")
                .hasMessageNotContaining("dynamic");
    }

    /** 验证未知属性、非法位置、link 和表格行域不能被扩展绕过。 */
    @Test
    void shouldRejectUnsafeTagStructure() {
        PrintTagHandler block = handler(
                "notice", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                compile -> binding -> new ParagraphNode("", List.of()));
        PrintTagHandler inline = handler(
                "badge", TagPlacement.INLINE, TagContentModel.EMPTY, Set.of(),
                TextNode.class,
                compile -> binding -> new TextNode("badge"));
        XmlTemplateCompiler compiler = compiler(block, inline);
        List<String> pages = List.of(
                "<page><page-body><notice secret=\"x\"/></page-body></page>",
                "<page><page-body><paragraph><notice/></paragraph></page-body></page>",
                "<page><page-body><badge/></page-body></page>",
                "<page><page-body><paragraph><link target=\"target\"><badge/></link></paragraph></page-body></page>",
                "<page><page-body><table><body><notice/></body></table></page-body></page>",
                "<page><page-body><notice><paragraph>x</paragraph></notice></page-body></page>");

        for (String page : pages) {
            assertThatThrownBy(() -> compile(compiler, page))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("contract")
                    .hasMessageContaining("行")
                    .hasMessageContaining("列");
        }
    }

    /** 验证处理器编译和绑定异常不会泄漏实现消息、属性或业务值。 */
    @Test
    void shouldSanitizeTagFailures() {
        PrintTagHandler compileFailure = handler(
                "compile-failure", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of("secret"),
                ParagraphNode.class,
                context -> {
                    throw new IllegalArgumentException("private:" + context.attribute("secret"));
                });
        assertThatThrownBy(() -> compile(compiler(compileFailure),
                "<page><page-body><compile-failure secret=\"attribute-value\"/></page-body></page>"))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("自定义标签编译失败")
                .hasMessageNotContaining("attribute-value")
                .hasMessageNotContaining("private")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        PrintTagHandler bindFailure = handler(
                "bind-failure", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                context -> binding -> {
                    throw new IllegalStateException(
                            "private:" + binding.data().root().path("secret").asText());
                });
        CompiledXmlTemplate template = compile(
                compiler(bindFailure), "<page><page-body><bind-failure/></page-body></page>");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("secret", "business-value"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("自定义标签绑定失败")
                .hasMessageNotContaining("business-value")
                .hasMessageNotContaining("private")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** 验证空返回和声明位置不符的返回节点会被中央校验拒绝。 */
    @Test
    void shouldRejectInvalidTagResults() {
        PrintTagHandler nullResult = handler(
                "null-result", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                context -> binding -> null);
        PrintTagHandler wrongType = handler(
                "wrong-type", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                TextNode.class,
                context -> binding -> new TextNode("wrong"));

        for (PrintTagHandler handler : List.of(nullResult, wrongType)) {
            CompiledXmlTemplate template = compile(
                    compiler(handler), "<page><page-body><" + handler.tagName() + "/></page-body></page>");
            assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(
                    1, JsonNodeFactory.instance.objectNode())))
                    .isInstanceOf(PrintValidationException.class)
                    .hasMessageContaining("自定义标签");
        }
    }

    /** 验证扩展返回树仍受文本容量和最终导航校验约束。 */
    @Test
    void shouldGovernExtensionResultTree() {
        PrintTagHandler oversized = handler(
                "oversized", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                context -> binding -> new ParagraphNode("", List.of(
                        new TextNode("x".repeat(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS + 1)))));
        CompiledXmlTemplate oversizedTemplate = compile(
                compiler(oversized), "<page><page-body><oversized/></page-body></page>");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                oversizedTemplate, emptyContext()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("文本字符数量超过限制");

        PrintTagHandler duplicate = handler(
                "duplicate", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                context -> binding -> new ParagraphNode("same", List.of()));
        CompiledXmlTemplate duplicateTemplate = compile(compiler(duplicate),
                "<page><page-body><duplicate/><duplicate/></page-body></page>");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(
                duplicateTemplate, emptyContext()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("自定义标签返回的文档模型校验失败")
                .hasMessageNotContaining("same");

        PrintTagHandler missingLink = handler(
                "missing-link", TagPlacement.INLINE, TagContentModel.EMPTY, Set.of(),
                InternalLinkNode.class,
                context -> binding -> new InternalLinkNode(
                        "missing", List.of(new TextNode("跳转"))));
        CompiledXmlTemplate linkTemplate = compile(compiler(missingLink),
                "<page><page-body><paragraph><missing-link/></paragraph></page-body></page>");
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(linkTemplate, emptyContext()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("自定义标签返回的文档模型校验失败")
                .hasMessageNotContaining("missing");
    }

    /** 验证表格行和单元格计入节点容量，且失败不会污染后续绑定。 */
    @Test
    void shouldCountTableStructureAndRecoverAfterCapacityFailure() {
        PrintTagHandler tableResult = handler(
                "table-result", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                TemplateInspectionContribution.builder()
                        .nodeType(TableNode.class).nodeType(ParagraphNode.class).build(),
                context -> binding -> binding.data().root().path("large").asBoolean()
                        ? oversizedTable()
                        : new ParagraphNode("", List.of(new TextNode("ok"))));
        CompiledXmlTemplate template = compile(
                compiler(tableResult), "<page><page-body><table-result/></page-body></page>");

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("large", true))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("生成节点数量超过限制");

        DocumentModel recovered = new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("large", false)));
        assertThat(XmlTestDocuments.body(recovered)).containsExactly(
                new ParagraphNode("", List.of(new TextNode("ok"))));
    }

    /** 验证嵌套扩展重复插入预绑定子树时按每次出现重新统计容量。 */
    @Test
    void shouldRecountRepeatedPreboundSubtreesForNestedTags() {
        PrintTagHandler inner = handler(
                "large-child", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                context -> binding -> new ParagraphNode("", Collections.nCopies(
                        34_000, new TextNode(""))));
        PrintTagHandler outer = handler(
                "repeat-children", TagPlacement.BLOCK, TagContentModel.BLOCKS, Set.of(),
                SectionNode.class,
                context -> binding -> new SectionNode("", Collections.nCopies(
                        3, binding.blockChildren().get(0))));
        CompiledXmlTemplate template = compile(compiler(inner, outer),
                "<page><page-body><repeat-children><large-child/></repeat-children></page-body></page>");

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, emptyContext()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("生成节点数量超过限制");
    }

    /** 验证扩展依据业务数据生成的 ID 不会从最终模型校验错误中泄漏。 */
    @Test
    void shouldSanitizeFinalValidationForCustomResults() {
        PrintTagHandler dataId = handler(
                "data-id", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                ParagraphNode.class,
                compile -> binding -> new ParagraphNode(
                        binding.data().root().path("secretId").asText(), List.of()));
        CompiledXmlTemplate template = compile(
                compiler(dataId), "<page><page-body><data-id/><data-id/></page-body></page>");

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, PrintContext.of(
                1, JsonNodeFactory.instance.objectNode().put("secretId", "business-secret-id"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("自定义标签返回的文档模型校验失败")
                .hasMessageNotContaining("business-secret-id")
                .hasCauseInstanceOf(PrintValidationException.class);
    }

    /** 扩展返回的节点必须与静态声明一致，不能在绑定时悄悄替换类型。 */
    @Test
    void shouldRejectCustomResultOutsideDeclaredTypes() {
        PrintTagHandler mismatchedType = handler(
                "mismatched-type", TagPlacement.BLOCK, TagContentModel.EMPTY, Set.of(),
                SectionNode.class,
                compile -> binding -> new ParagraphNode("", List.of(new TextNode("正文"))));
        CompiledXmlTemplate template = compile(
                compiler(mismatchedType), "<page><page-body><mismatched-type/></page-body></page>");

        assertThatThrownBy(() -> new XmlTemplateBinder().bind(template, emptyContext()))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("未声明的节点类型");
    }

    /** 创建自定义标签处理器。 */
    private static PrintTagHandler handler(
            String name, TagPlacement placement, TagContentModel contentModel,
            Set<String> attributes, Class<? extends DocumentNode> nodeType,
            java.util.function.Function<TagCompileContext,
                    java.util.function.Function<TagBindingContext, ? extends DocumentNode>> compile) {
        return handler(name, placement, contentModel, attributes,
                TemplateInspectionContribution.builder().nodeType(nodeType).build(), compile);
    }

    /** 创建可声明多个节点类型或附加能力的自定义标签处理器。 */
    private static PrintTagHandler handler(
            String name, TagPlacement placement, TagContentModel contentModel,
            Set<String> attributes, TemplateInspectionContribution contribution,
            java.util.function.Function<TagCompileContext,
                    java.util.function.Function<TagBindingContext, ? extends DocumentNode>> compile) {
        return new PrintTagHandler() {
            @Override
            public String tagName() {
                return name;
            }

            @Override
            public TagPlacement placement() {
                return placement;
            }

            @Override
            public TagContentModel contentModel() {
                return contentModel;
            }

            @Override
            public Set<String> allowedAttributes() {
                return attributes;
            }

            @Override
            public PrintTagPlan compile(TagCompileContext context) {
                return PrintTagPlan.of(contribution, compile.apply(context));
            }
        };
    }

    /** 创建带指定标签处理器的 XML 编译器。 */
    private static XmlTemplateCompiler compiler(PrintTagHandler... handlers) {
        return new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(), new PrintExpressionRegistry(List.of()),
                new PrintTagRegistry(List.of(handlers)));
    }

    /** 编译指定页面内容。 */
    private static CompiledXmlTemplate compile(XmlTemplateCompiler compiler, String page) {
        String xml = """
                <document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
                    %s
                </document>
                """.formatted(page);
        return compiler.compile(new PrintTemplate(
                "contract", TemplateFormat.LETOOL_XML, 1, 9, 1,
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** 创建空的只读上下文。 */
    private static PrintContext emptyContext() {
        return PrintContext.of(1, JsonNodeFactory.instance.objectNode());
    }

    /** 创建仅依靠表格结构节点即可超过中央节点上限的有效表格。 */
    private static TableNode oversizedTable() {
        ParagraphNode paragraph = new ParagraphNode("", List.of(new TextNode("")));
        TableCell cell = new TableCell(List.of(paragraph), 1, 1);
        TableRow row = new TableRow(List.of(cell));
        int rowCount = XmlDsl.MAX_GENERATED_NODES / 4 + 1;
        return new TableNode("", 0, Collections.nCopies(rowCount, row));
    }
}
