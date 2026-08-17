package io.github.leylaragg.letool.print.context;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 只读打印上下文的契约测试。
 *
 * @author leyland
 */
class PrintContextTest {

    /** 验证上下文与调用方传入和取出的可变 JSON 节点隔离。 */
    @Test
    void shouldProtectContextFromCallerMutation() {
        ObjectNode source = JsonNodeFactory.instance.objectNode().put("name", "Alice");
        PrintContext context = PrintContext.of(3, source);

        source.put("name", "Changed");
        ((ObjectNode) context.root()).put("name", "ReturnedChanged");

        assertThat(context.root().path("name").asText()).isEqualTo("Alice");
        assertThat(context.version()).isEqualTo(3);
    }

    /** 验证上下文根节点只能是对象。 */
    @Test
    void shouldOnlyAcceptObjectAsContextRoot() {
        assertThatThrownBy(() -> PrintContext.of(1, TextNode.valueOf("value")))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("PRINT_001");
    }
}
