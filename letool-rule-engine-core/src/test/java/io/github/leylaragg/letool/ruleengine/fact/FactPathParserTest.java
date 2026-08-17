package io.github.leylaragg.letool.ruleengine.fact;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段一事实路径语法测试。
 */
class FactPathParserTest {

    /**
     * 验证普通路径、数组下标与完整插值包装使用同一规范形式。
     */
    @Test
    void shouldParseAndNormalizeSupportedPaths() {
        FactPath customerAge = FactPathParser.parse("customer.age");
        FactPath wrapped = FactPathParser.parse("${customer.age}");
        FactPath itemPrice = FactPathParser.parse("order.items[0].price");
        FactPath className = FactPathParser.parse("customer.className");

        assertThat(customerAge).isEqualTo(wrapped);
        assertThat(customerAge.hashCode()).isEqualTo(wrapped.hashCode());
        assertThat(customerAge.toString()).isEqualTo("customer.age");
        assertThat(itemPrice.toString()).isEqualTo("order.items[0].price");
        assertThat(itemPrice.segments()).hasSize(4);
        assertThat(itemPrice.segments().get(2)).isEqualTo(new FactPath.IndexSegment(0));
        assertThat(className.toString()).isEqualTo("customer.className");
    }

    /**
     * 验证阶段一不接受歧义、越界或可执行式路径语法。
     *
     * @param source 非法路径文本
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", ".", ".customer", "customer.", "customer..age",
            "customer[-1]", "customer[*]", "customer[]", "customer[",
            "customer[0", "customer[0]tail", "customer[2147483648]",
            "customer()", "customer.age()", "java.lang.String.class",
            "foo.class[0]", "${foo.class[0]}",
            "${}", "${customer.age", "customer.age}", "${customer.age}tail",
            "${${customer.age}}", "customer age", "customer-age", "0customer",
            "customer.$age"
    })
    void shouldRejectUnsupportedOrMalformedPaths(String source) {
        assertThatThrownBy(() -> FactPathParser.parse(source))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 验证空路径引用被统一拒绝。
     */
    @Test
    void shouldRejectNullPath() {
        assertThatThrownBy(() -> FactPathParser.parse(null))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 验证公开路径段自身也维护与解析器一致的不变量。
     */
    @Test
    void shouldRejectInvalidStandaloneSegments() {
        assertThat(FactPath.Segment.class.isSealed()).isTrue();
        assertThat(FactPath.Segment.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(FactPath.PropertySegment.class, FactPath.IndexSegment.class);
        assertThatThrownBy(() -> new FactPath.PropertySegment("customer-name"))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> new FactPath.PropertySegment("0customer"))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> new FactPath.IndexSegment(-1))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }
}
