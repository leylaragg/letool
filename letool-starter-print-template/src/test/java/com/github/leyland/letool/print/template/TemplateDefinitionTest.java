package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 模板定义值对象测试。
 *
 * @author leyland
 */
class TemplateDefinitionTest {

    /** 类型与核心模板快照应保持不变。 */
    @Test
    void shouldKeepTemplateTypeAndSnapshot() {
        PrintTemplate template = template("main", 1, "<document/>");

        TemplateDefinition definition = new TemplateDefinition(
                TemplateType.DOCUMENT, template);

        assertThat(definition.type()).isEqualTo(TemplateType.DOCUMENT);
        assertThat(definition.template()).isSameAs(template);
    }

    /** 定义缺少类型或模板时应立即拒绝。 */
    @Test
    void shouldRejectNullTypeOrTemplate() {
        PrintTemplate template = template("main", 1, "<document/>");

        assertThatNullPointerException()
                .isThrownBy(() -> new TemplateDefinition(null, template));
        assertThatNullPointerException()
                .isThrownBy(() -> new TemplateDefinition(TemplateType.DOCUMENT, null));
    }

    /** 创建测试使用的模板快照。 */
    private PrintTemplate template(String code, long setVersion, String source) {
        return new PrintTemplate(code, TemplateFormat.LETOOL_XML, 1,
                setVersion, 1, source.getBytes(StandardCharsets.UTF_8));
    }
}
