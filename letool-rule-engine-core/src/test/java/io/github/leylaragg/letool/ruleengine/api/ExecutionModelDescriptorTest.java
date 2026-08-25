package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证执行模型摘要完整覆盖影响编译和求值语义的环境维度。
 */
@DisplayName("执行模型描述")
class ExecutionModelDescriptorTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    /** 相同语义输入必须得到可跨进程复算的稳定摘要。 */
    @Test
    @DisplayName("相同执行模型产生相同环境摘要")
    void sameModelProducesSameEnvironmentDigest() {
        ExecutionModelDescriptor first = descriptor(
                "1.0", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_A);
        ExecutionModelDescriptor second = descriptor(
                "1.0", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_A);

        assertThat(first.environmentDigest())
                .isEqualTo(second.environmentDigest())
                .matches("[0-9a-f]{64}");
        assertThat(first.environmentDigest()).isSameAs(first.environmentDigest());
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    /** 任一环境维度变化都必须使旧编译产物失去兼容身份。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("changedModels")
    @DisplayName("任一语义维度变化都会改变环境摘要")
    void changedDimensionChangesEnvironmentDigest(
            String description, ExecutionModelDescriptor changed) {
        ExecutionModelDescriptor baseline = descriptor(
                "1.0", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_A);

        assertThat(changed.environmentDigest())
                .as(description)
                .isNotEqualTo(baseline.environmentDigest());
    }

    /** 描述符在进入摘要前拒绝不稳定版本和非标准摘要文本。 */
    @Test
    @DisplayName("执行模型拒绝无效维度")
    void invalidDimensionsAreRejected() {
        assertThatThrownBy(() -> descriptor(
                " ", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_A))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> descriptor(
                "1.0", "3.0.0", "invalid", DIGEST_A, DIGEST_A))
                .isInstanceOf(RuleEngineException.class);
    }

    /** 为参数化测试提供每次只改变一个维度的执行模型。 */
    private static Stream<Arguments> changedModels() {
        return Stream.of(
                Arguments.of("语言版本", descriptor(
                        "2.0", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_A)),
                Arguments.of("内核语义版本", descriptor(
                        "1.0", "3.1.0", DIGEST_A, DIGEST_A, DIGEST_A)),
                Arguments.of("类型目录", descriptor(
                        "1.0", "3.0.0", DIGEST_B, DIGEST_A, DIGEST_A)),
                Arguments.of("函数目录", descriptor(
                        "1.0", "3.0.0", DIGEST_A, DIGEST_B, DIGEST_A)),
                Arguments.of("编译选项", descriptor(
                        "1.0", "3.0.0", DIGEST_A, DIGEST_A, DIGEST_B)));
    }

    /** 创建测试使用的完整执行模型。 */
    private static ExecutionModelDescriptor descriptor(
            String languageVersion,
            String semanticVersion,
            String typeCatalogDigest,
            String functionCatalogDigest,
            String compilationOptionsDigest) {
        return new ExecutionModelDescriptor(
                languageVersion,
                semanticVersion,
                typeCatalogDigest,
                functionCatalogDigest,
                compilationOptionsDigest);
    }
}
