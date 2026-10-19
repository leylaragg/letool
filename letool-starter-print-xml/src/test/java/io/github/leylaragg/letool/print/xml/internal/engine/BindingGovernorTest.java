package io.github.leylaragg.letool.print.xml.internal.engine;

import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证绑定执行内核的节点、文本和动态操作容量边界。 */
class BindingGovernorTest {

    /** 验证节点和文本计数允许恰好上限并拒绝超限。 */
    @Test
    void shouldEnforceGeneratedNodeAndTextLimits() {
        BindingGovernor governor = new BindingGovernor("contract");

        governor.addNodes(XmlDsl.MAX_GENERATED_NODES);
        governor.addText(XmlDsl.MAX_GENERATED_TEXT_CHARACTERS);

        assertThatThrownBy(() -> governor.addNodes(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract");
        assertThatThrownBy(() -> governor.addText(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("contract");
    }

    /** 验证循环数量和动态深度允许恰好上限并拒绝下一次增长。 */
    @Test
    void shouldEnforceLoopAndDynamicDepthBoundaries() {
        BindingGovernor governor = new BindingGovernor("contract");

        governor.checkLoopItems(XmlDsl.MAX_LOOP_ITEMS);
        governor.addDynamicOperations(XmlDsl.MAX_DYNAMIC_OPERATIONS);
        for (int depth = 0; depth < XmlDsl.MAX_DYNAMIC_DEPTH; depth++) {
            governor.enterDynamic();
        }

        assertThatThrownBy(() -> governor.checkLoopItems(XmlDsl.MAX_LOOP_ITEMS + 1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("循环元素");
        assertThatThrownBy(governor::enterDynamic)
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("动态");
        assertThatThrownBy(() -> governor.addDynamicOperations(1))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("动态操作");
    }

}
