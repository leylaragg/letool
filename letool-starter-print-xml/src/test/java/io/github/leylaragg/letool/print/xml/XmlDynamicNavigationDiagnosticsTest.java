package io.github.leylaragg.letool.print.xml;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 动态导航校验保留源码位置，顶层诊断不回显业务值。 */
class XmlDynamicNavigationDiagnosticsTest {

    @Test
    void shouldHideAnInvalidDynamicIdAndLocateItsTemplateNode() {
        String value = "private-patient id";
        ObjectNode data = JsonNodeFactory.instance.objectNode().put("id", value);
        assertThatThrownBy(() -> bind("<paragraph id-path=\"id\">Body</paragraph>", data))
                .isInstanceOf(PrintValidationException.class).hasMessageNotContaining(value)
                .hasMessageContaining("diagnostic").hasMessageContaining("/document/page[1]/page-body/paragraph")
                .hasCauseInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldHideDuplicateDynamicIdsInTheFinalModelDiagnostic() {
        String value = "private-patient-id";
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putArray("items").addObject().put("id", value);
        data.withArray("items").addObject().put("id", value);
        assertThatThrownBy(() -> bind("""
                <for-each items="items" var="item"><paragraph id-path="$item.id">Body</paragraph></for-each>
                """, data)).isInstanceOf(PrintValidationException.class).hasMessageNotContaining(value)
                .hasMessageContaining("diagnostic").hasMessageContaining("/paragraph")
                .hasCauseInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldHideAnInvalidDynamicLinkTarget() {
        String value = "private-patient target";
        ObjectNode data = JsonNodeFactory.instance.objectNode().put("target", value);
        assertThatThrownBy(() -> bind("<paragraph><link target-path=\"target\">Open</link></paragraph>", data))
                .isInstanceOf(PrintValidationException.class).hasMessageNotContaining(value)
                .hasMessageContaining("/paragraph/link").hasCauseInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldHideAMissingDynamicBookmarkParent() {
        String value = "private-patient-parent";
        ObjectNode data = JsonNodeFactory.instance.objectNode().put("parent", value);
        assertThatThrownBy(() -> bind("""
                <paragraph id="body">Body<bookmark id="book" label="Book" target="body"
                    parent-path="parent"/></paragraph>
                """, data)).isInstanceOf(PrintValidationException.class).hasMessageNotContaining(value)
                .hasMessageContaining("/paragraph/bookmark").hasCauseInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldLocateADynamicIdFailureInsideItsFragment() {
        String value = "private-fragment id";
        TemplateDefinition main = new TemplateDefinition(TemplateType.DOCUMENT, template("main", document("""
                <include template="entry"><with name="value" path="item"/></include>
                """)));
        TemplateDefinition fragment = new TemplateDefinition(TemplateType.FRAGMENT, template("entry", """
                <fragment xmlns="https://leyland.github.io/letool/print/v1" parameters="value">
                    <paragraph id-path="$value.id">Body</paragraph>
                </fragment>
                """));
        io.github.leylaragg.letool.print.template.TemplateSet set = new TemplateSetPublisher(new
            InMemoryTemplateRepository(), List.of())
                .publish(1, List.of(main, fragment));
        CompiledXmlTemplate compiled = new XmlTemplateSetCompiler().compile(set).require("main");
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putObject("item").put("id", value);
        assertThatThrownBy(() -> new XmlTemplateBinder().bind(compiled, PrintContext.of(1, data)))
                .isInstanceOf(PrintValidationException.class).hasMessageNotContaining(value)
                .hasMessageContaining("entry").hasMessageContaining("/fragment/paragraph")
                .hasCauseInstanceOf(PrintValidationException.class);
    }

    @Test
    void shouldKeepLegacyStaticIdDiagnostics() {
        assertThatThrownBy(() -> bind("""
                <paragraph id="legacy-id">First</paragraph><paragraph id="legacy-id">Second</paragraph>
                """, JsonNodeFactory.instance.objectNode())).isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("ID 重复：legacy-id");
    }

    private void bind(String body, ObjectNode data) {
        CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(template("diagnostic", document(body)));
        new XmlTemplateBinder().bind(compiled, PrintContext.of(1, data));
    }

    private String document(String body) {
        return "<document xmlns=\"https://leyland.github.io/letool/print/v1\" context-version=\"1\">"
                + "<page><page-body>" + body + "</page-body></page></document>";
    }

    private PrintTemplate template(String code, String source) {
        return new PrintTemplate(code, TemplateFormat.LETOOL_XML, 1, 1, 1,
                source.getBytes(StandardCharsets.UTF_8));
    }
}
