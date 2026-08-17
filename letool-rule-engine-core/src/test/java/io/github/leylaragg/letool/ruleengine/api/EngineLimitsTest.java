package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 规则引擎资源限制契约测试。
 */
class EngineLimitsTest {

    @Test
    @DisplayName("组合限制时九个维度均应逐项取更严格值")
    void shouldTakeStricterValueForEveryDimension() {
        EngineLimits left = new EngineLimits(9, 2, 8, 4, 7, 6, 3, 10, 5);
        EngineLimits right = new EngineLimits(1, 9, 3, 8, 2, 7, 6, 4, 10);

        EngineLimits combined = EngineLimits.stricterOf(left, right);

        assertThat(combined.getMaxSourceLength()).isEqualTo(1);
        assertThat(combined.getMaxTokens()).isEqualTo(2);
        assertThat(combined.getMaxAstDepth()).isEqualTo(3);
        assertThat(combined.getMaxFunctionCalls()).isEqualTo(4);
        assertThat(combined.getMaxTraceNodes()).isEqualTo(2);
        assertThat(combined.getMaxSummaryLength()).isEqualTo(6);
        assertThat(combined.getMaxFactDepth()).isEqualTo(3);
        assertThat(combined.getMaxFactNodes()).isEqualTo(4);
        assertThat(combined.getMaxContainerSize()).isEqualTo(5);
    }

    /**
     * 验证默认限制都是有限正数。
     */
    @Test
    void shouldProvideFinitePositiveDefaults() {
        EngineLimits limits = EngineLimits.defaults();

        assertThat(limits.getMaxSourceLength()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxTokens()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxAstDepth()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxFunctionCalls()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxTraceNodes()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxSummaryLength()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxFactDepth()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxFactNodes()).isPositive().isLessThan(Integer.MAX_VALUE);
        assertThat(limits.getMaxContainerSize()).isPositive().isLessThan(Integer.MAX_VALUE);
    }

    @Test
    void shouldRetainFactNormalizationLimits() {
        EngineLimits limits = new EngineLimits(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(limits.getMaxFactDepth()).isEqualTo(7);
        assertThat(limits.getMaxFactNodes()).isEqualTo(8);
        assertThat(limits.getMaxContainerSize()).isEqualTo(9);
    }

    /**
     * 验证自定义构造参数与六个访问器逐一对应。
     */
    @Test
    void shouldRetainEveryCustomLimitValue() {
        EngineLimits limits = new EngineLimits(101, 202, 303, 404, 505, 606);

        assertThat(limits.getMaxSourceLength()).isEqualTo(101);
        assertThat(limits.getMaxTokens()).isEqualTo(202);
        assertThat(limits.getMaxAstDepth()).isEqualTo(303);
        assertThat(limits.getMaxFunctionCalls()).isEqualTo(404);
        assertThat(limits.getMaxTraceNodes()).isEqualTo(505);
        assertThat(limits.getMaxSummaryLength()).isEqualTo(606);
    }

    /**
     * 验证每个限制字段都拒绝零和负数。
     *
     * @param invalidCase 待验证的非法构造器调用
     */
    @ParameterizedTest
    @MethodSource("invalidLimitCases")
    void shouldRejectNonPositiveLimits(IntFunction<EngineLimits> invalidCase) {
        for (int invalidValue : new int[]{0, -1}) {
            assertThatThrownBy(() -> invalidCase.apply(invalidValue))
                    .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
        }
    }

    /**
     * 创建六个限制字段的非法值构造器调用。
     *
     * @return 每个函数仅替换一个限制字段
     */
    private static Stream<IntFunction<EngineLimits>> invalidLimitCases() {
        return Stream.of(
                value -> new EngineLimits(value, 256, 64, 128, 1_024, 512),
                value -> new EngineLimits(8_192, value, 64, 128, 1_024, 512),
                value -> new EngineLimits(8_192, 256, value, 128, 1_024, 512),
                value -> new EngineLimits(8_192, 256, 64, value, 1_024, 512),
                value -> new EngineLimits(8_192, 256, 64, 128, value, 512),
                value -> new EngineLimits(8_192, 256, 64, 128, 1_024, value),
                value -> new EngineLimits(8_192, 256, 64, 128, 1_024, 512,
                        value, 10_000, 1_000),
                value -> new EngineLimits(8_192, 256, 64, 128, 1_024, 512,
                        64, value, 1_000),
                value -> new EngineLimits(8_192, 256, 64, 128, 1_024, 512,
                        64, 10_000, value));
    }
}
