package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.document.node.ImageNode;
import io.github.leylaragg.letool.print.document.node.PageNumberNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TableNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import io.github.leylaragg.letool.print.template.inspection.TemplatePathUsageKind;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagPlan;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import io.github.leylaragg.letool.print.xml.tag.TagCompileContext;
import io.github.leylaragg.letool.print.xml.tag.TagContentModel;
import io.github.leylaragg.letool.print.xml.tag.TagPlacement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 编译结果公开的路径、引用和输出能力检查测试。
 *
 * @author leyland
 */
class XmlTemplateInspectionTest {

    /** inspection 应保守收集全部分支、循环、片段和页面静态语义。 */
    @Test
    void shouldInspectEveryPossibleTemplatePathAndFeature() {
        CompiledXmlTemplate template = compile(
                definition(TemplateType.DOCUMENT, "main", """
                        <document xmlns="https://leyland.github.io/letool/print/v1"
                                  context-version="1" outputs="pdf">
                            <styles>
                                <text-style name="small" font-size="9pt"/>
                                <paragraph-style name="header" text-style="small"/>
                            </styles>
                            <page>
                                <page-header>
                                    <paragraph style="header">第 <page-number/> 页</paragraph>
                                </page-header>
                                <page-body>
                                    <if path="contract.enabled" operator="truthy">
                                        <then><paragraph><field path="contract.code"/></paragraph></then>
                                        <else><image resource-path="contract.logo" alt="标志"
                                                     width="10mm" height="10mm"/></else>
                                    </if>
                                    <include template="items">
                                        <with name="rows" path="contract.items"/>
                                        <with name="title" path="contract.title"/>
                                    </include>
                                </page-body>
                            </page>
                        </document>
                        """),
                definition(TemplateType.FRAGMENT, "items", """
                        <fragment xmlns="https://leyland.github.io/letool/print/v1" parameters="rows,title">
                            <paragraph><field path="$title"/></paragraph>
                            <for-each items="$rows" var="row">
                                <paragraph><field path="$row.tags" formatter="join"/></paragraph>
                                <table><body><row><cell><paragraph>项目</paragraph></cell></row></body></table>
                            </for-each>
                        </fragment>
                        """));

        TemplateInspection inspection = template.inspection();

        assertThat(inspection.templateCode()).isEqualTo("main");
        assertThat(inspection.declaredOutputs()).containsExactly("pdf");
        assertThat(inspection.pathUsages())
                .extracting(usage -> usage.kind() + ":" + usage.dataPath())
                .containsExactly(
                        TemplatePathUsageKind.CONDITION + ":contract.enabled",
                        TemplatePathUsageKind.FIELD + ":contract.code",
                        TemplatePathUsageKind.IMAGE_RESOURCE + ":contract.logo",
                        TemplatePathUsageKind.INCLUDE_ARGUMENT + ":contract.items",
                        TemplatePathUsageKind.INCLUDE_ARGUMENT + ":contract.title",
                        TemplatePathUsageKind.FIELD + ":$title",
                        TemplatePathUsageKind.LOOP + ":$rows",
                        TemplatePathUsageKind.FIELD + ":$row.tags");
        assertThat(inspection.pathUsages())
                .filteredOn(usage -> usage.kind() == TemplatePathUsageKind.INCLUDE_ARGUMENT)
                .extracting(usage -> usage.location().line())
                .doesNotHaveDuplicates();
        assertThat(inspection.includeUsages()).singleElement().satisfies(include -> {
            assertThat(include.sourceTemplateCode()).isEqualTo("main");
            assertThat(include.targetTemplateCode()).isEqualTo("items");
            assertThat(include.arguments()).containsExactly(
                    java.util.Map.entry("rows", "contract.items"),
                    java.util.Map.entry("title", "contract.title"));
        });
        assertThat(inspection.fragmentParameters())
                .containsEntry("items", List.of("rows", "title"));
        assertThat(inspection.nodeTypes()).contains(
                ParagraphNode.class, TextNode.class, ImageNode.class, PageNumberNode.class,
                TableNode.class);
        assertThat(inspection.features()).contains(
                DocumentFeature.PAGE_HEADER, DocumentFeature.NAMED_STYLES);
        assertThat(inspection.formatters()).containsExactly("join");
        assertThatThrownBy(() -> inspection.pathUsages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 自定义标签的路径和节点声明应进入检查结果，并遵守同一套路径语法。 */
    @Test
    void shouldValidateAndCollectCustomTagContribution() {
        TemplateInspectionContribution contribution = TemplateInspectionContribution.builder()
                .dataPath("customer.name").nodeType(ParagraphNode.class).build();
        PrintTagHandler valid = tag("profile", contribution);

        CompiledXmlTemplate template = customCompiler(valid).compile(new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, 1, 1, 1, document("<profile/>")));

        assertThat(template.inspection().pathUsages())
                .extracting(usage -> usage.kind() + ":" + usage.dataPath())
                .containsExactly(TemplatePathUsageKind.CUSTOM_TAG + ":customer.name");
        assertThat(template.inspection().nodeTypes()).contains(ParagraphNode.class);

        PrintTagHandler invalid = tag("unsafe", TemplateInspectionContribution.builder()
                .dataPath("customer[0]").nodeType(ParagraphNode.class).build());
        assertThatThrownBy(() -> customCompiler(invalid).compile(new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, 1, 1, 1, document("<unsafe/>"))))
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageContaining("自定义标签编译失败")
                .hasMessageNotContaining("customer[0]");
    }

    /** 输出白名单应保持声明顺序，并拒绝大小写、重复项和超量配置。 */
    @Test
    void shouldValidateDeclaredOutputIdentifiers() {
        CompiledXmlTemplate valid = new XmlTemplateCompiler().compile(new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, 1, 1, 1,
                documentWithOutputs("pdf,html")));

        assertThat(valid.inspection().declaredOutputs()).containsExactly("pdf", "html");
        for (String outputs : List.of(
                "PDF", "pdf,pdf",
                "a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q")) {
            assertThatThrownBy(() -> new XmlTemplateCompiler().compile(new PrintTemplate(
                    "main", TemplateFormat.LETOOL_XML, 1, 1, 1,
                    documentWithOutputs(outputs))))
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageContaining("outputs");
        }
    }

    /** 创建一个声明静态检查贡献的块级空标签。 */
    private PrintTagHandler tag(String name, TemplateInspectionContribution contribution) {
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
                return PrintTagPlan.of(
                        contribution, binding -> new ParagraphNode("", List.of()));
            }
        };
    }

    /** 创建只注册指定自定义标签的 XML 编译器。 */
    private XmlTemplateCompiler customCompiler(PrintTagHandler handler) {
        return new XmlTemplateCompiler(
                BuiltInPrintFormatters.registry(), new PrintExpressionRegistry(List.of()),
                new PrintTagRegistry(List.of(handler)));
    }

    /** 生成只有一个正文页面的 XML 源码。 */
    private byte[] document(String body) {
        return ("<document xmlns=\"https://leyland.github.io/letool/print/v1\" "
                + "context-version=\"1\"><page><page-body>" + body
                + "</page-body></page></document>").getBytes(StandardCharsets.UTF_8);
    }

    /** 生成带指定输出白名单的最小文档。 */
    private byte[] documentWithOutputs(String outputs) {
        return ("<document xmlns=\"https://leyland.github.io/letool/print/v1\" "
                + "context-version=\"1\" outputs=\"" + outputs
                + "\"><page><page-body/></page></document>")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 编译模板集合并返回主文档。 */
    private CompiledXmlTemplate compile(TemplateDefinition... definitions) {
        TemplateSet set = new TemplateSetPublisher(
                new InMemoryTemplateRepository(), List.of()).publish(1, List.of(definitions));
        return new XmlTemplateSetCompiler().compile(set).require("main");
    }

    /** 创建 XML 模板定义。 */
    private TemplateDefinition definition(TemplateType type, String code, String source) {
        return new TemplateDefinition(type, new PrintTemplate(
                code, TemplateFormat.LETOOL_XML, 1, 1, 1,
                source.getBytes(StandardCharsets.UTF_8)));
    }
}
