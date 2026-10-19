package io.github.leylaragg.letool.print.xml.internal.engine;

import io.github.leylaragg.letool.print.document.node.FreeTextNode;
import io.github.leylaragg.letool.print.document.node.InlineGroupNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证扩展返回树继续受绑定容量和循环 ID 约束。 */
class ExtensionNodeGovernorTest {

    /** 自定义标签的完整行内组仍计入文本额度，并遵守循环 ID 限制。 */
    @Test
    void shouldGovernFreeTextInsideCustomInlineGroups() {
        InlineGroupNode group = new InlineGroupNode(List.of(new FreeTextNode("note", "", "", "", "内容")), false)
                .withKeepTogether();
        BindingGovernor governor = new BindingGovernor("flow-note");
        governor.addText(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS - 1L);
        assertThatThrownBy(() -> ExtensionNodeGovernor.govern(group, governor, true))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("文本字符");
        assertThatThrownBy(() -> ExtensionNodeGovernor.govern(group, new BindingGovernor("flow-note"), false))
                .isInstanceOf(PrintValidationException.class).hasMessageContaining("ID");
    }

}
