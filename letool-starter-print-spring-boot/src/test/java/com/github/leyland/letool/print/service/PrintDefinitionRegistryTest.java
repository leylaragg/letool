package com.github.leyland.letool.print.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.leyland.letool.print.context.PrintContext;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务打印定义及其不可变注册表测试。
 *
 * @author leyland
 */
class PrintDefinitionRegistryTest {

    /** 定义保留可信 Java 请求类型，并且不用 record 暴露扩展契约。 */
    @Test
    void shouldKeepTypedRegularDefinition() {
        PrintDefinition<Long> definition = PrintDefinition.of(
                "invoice", "invoice-template", Long.class, this::context);
        PrintDefinitionRegistry registry = new PrintDefinitionRegistry(List.of(definition));

        assertThat(PrintDefinition.class.isRecord()).isFalse();
        assertThat(definition.code()).isEqualTo("invoice");
        assertThat(definition.templateCode()).isEqualTo("invoice-template");
        assertThat(definition.requestType()).isEqualTo(Long.class);
        assertThat(registry.require("invoice")).isSameAs(definition);
        assertThat(registry.registeredCodes()).containsExactly("invoice");
        assertThatThrownBy(() -> registry.registeredCodes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 空业务项目允许启动，重复定义则不能依赖 Spring 顺序覆盖。 */
    @Test
    void shouldAllowEmptyRegistryAndRejectDuplicateCode() {
        assertThat(new PrintDefinitionRegistry(List.of()).registeredCodes()).isEmpty();
        PrintDefinition<Long> first = PrintDefinition.of(
                "invoice", "first", Long.class, this::context);
        PrintDefinition<Long> duplicate = PrintDefinition.of(
                "invoice", "second", Long.class, this::context);

        assertThatThrownBy(() -> new PrintDefinitionRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice");
    }

    /** 未知定义和错误请求类型使用稳定的打印请求异常。 */
    @Test
    void shouldRejectUnknownDefinitionAndWrongRequestType() {
        PrintDefinition<Long> definition = PrintDefinition.of(
                "invoice", "invoice-template", Long.class, this::context);
        PrintDefinitionRegistry registry = new PrintDefinitionRegistry(List.of(definition));

        assertThatThrownBy(() -> registry.require("secret-business-code"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("PRINT_001")
                .hasMessageNotContaining("secret-business-code");
        assertThatThrownBy(() -> definition.load("wrong"))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("请求类型");
    }

    /** 生成最小版本上下文。 */
    private PrintContext context(Long request) {
        return PrintContext.of(1, JsonNodeFactory.instance.objectNode().put("id", request));
    }
}
