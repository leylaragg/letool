package io.github.leylaragg.letool.print.template.inspection;

import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.render.DocumentFeature;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板静态检查结果的不可变性与公共边界测试。
 *
 * @author leyland
 */
class TemplateInspectionTest {

    /** 检查结果应保留模板顺序，同时与调用方后续修改完全隔离。 */
    @Test
    void shouldKeepDeterministicImmutableSnapshot() {
        Set<String> variables = new LinkedHashSet<>(List.of("item"));
        Set<String> parameters = new LinkedHashSet<>(List.of("items"));
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("items", "contract.items");
        List<String> declaredParameters = new ArrayList<>(List.of("items"));
        TemplateSourceLocation fieldLocation = location("/document/page/page-body/paragraph/field", 8);
        TemplatePathUsage field = new TemplatePathUsage("$item.name",
                TemplatePathUsageKind.FIELD, variables, parameters, fieldLocation);
        TemplateIncludeUsage include = new TemplateIncludeUsage(
                "contract", "item-list", arguments, location("/document/page/page-body/include", 10));

        TemplateInspection inspection = TemplateInspection
                .builder("contract", TemplateType.DOCUMENT, 1)
                .declaredOutput("pdf")
                .pathUsage(field)
                .includeUsage(include)
                .fragmentParameters("item-list", declaredParameters)
                .nodeType(ParagraphNode.class)
                .nodeType(TextNode.class)
                .feature(DocumentFeature.NAMED_STYLES)
                .formatter("join")
                .expressionLanguage("spel")
                .customTag("badge")
                .build();

        variables.add("later");
        parameters.clear();
        arguments.put("later", "secret");
        declaredParameters.add("later");

        assertThat(inspection.templateCode()).isEqualTo("contract");
        assertThat(inspection.templateType()).isEqualTo(TemplateType.DOCUMENT);
        assertThat(inspection.contextVersion()).isEqualTo(1);
        assertThat(inspection.declaredOutputs()).containsExactly("pdf");
        assertThat(inspection.pathUsages()).containsExactly(field);
        assertThat(field.visibleVariables()).containsExactly("item");
        assertThat(field.fragmentParameters()).containsExactly("items");
        assertThat(include.arguments()).containsExactlyEntriesOf(
                Map.of("items", "contract.items"));
        assertThat(inspection.fragmentParameters().get("item-list")).containsExactly("items");
        assertThat(inspection.nodeTypes()).containsExactly(ParagraphNode.class, TextNode.class);
        assertThat(inspection.features()).containsExactly(DocumentFeature.NAMED_STYLES);
        assertThat(inspection.formatters()).containsExactly("join");
        assertThat(inspection.expressionLanguages()).containsExactly("spel");
        assertThat(inspection.customTags()).containsExactly("badge");

        assertThatThrownBy(() -> inspection.declaredOutputs().add("docx"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> inspection.fragmentParameters().get("item-list").add("later"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 扩展贡献只携带静态路径和能力，不应暴露模板正文或编译对象。 */
    @Test
    void shouldCreateBoundedExtensionContribution() {
        TemplateInspectionContribution contribution = TemplateInspectionContribution.builder()
                .dataPath("contract.code")
                .nodeType(TextNode.class)
                .feature(DocumentFeature.TEXT_FLOW_CONTROL)
                .build();

        assertThat(contribution.dataPaths()).containsExactly("contract.code");
        assertThat(contribution.nodeTypes()).containsExactly(TextNode.class);
        assertThat(contribution.features()).containsExactly(DocumentFeature.TEXT_FLOW_CONTROL);
        assertThatThrownBy(() -> contribution.dataPaths().add("secret"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 无效标识、位置和空类型应在进入编译快照前被拒绝。 */
    @Test
    void shouldRejectInvalidInspectionValues() {
        assertThatThrownBy(() -> TemplateInspection.builder(" ", TemplateType.DOCUMENT, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateInspection.builder(
                "contract", TemplateType.DOCUMENT, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateInspection.builder(
                "contract", TemplateType.DOCUMENT, 1).declaredOutput("PDF"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateSourceLocation("contract", " ", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplateSourceLocation("contract", "/document", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateInspectionContribution.builder().nodeType(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TemplateInspectionContribution.builder()
                .nodeType(DocumentNode.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("具体类型");
    }

    /** 创建测试使用的安全源码位置。 */
    private TemplateSourceLocation location(String tagPath, int line) {
        return new TemplateSourceLocation("contract", tagPath, line, 5);
    }
}
