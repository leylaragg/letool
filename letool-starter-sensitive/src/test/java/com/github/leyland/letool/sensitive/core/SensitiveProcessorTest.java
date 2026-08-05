package com.github.leyland.letool.sensitive.core;

import com.github.leyland.letool.sensitive.exception.SensitiveErrorCode;
import com.github.leyland.letool.sensitive.exception.SensitiveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脱敏处理器关键行为测试。
 */
@DisplayName("脱敏处理器关键行为")
class SensitiveProcessorTest {

    /**
     * 验证默认注册表可以执行内置手机号脱敏策略。
     */
    @Test
    @DisplayName("默认注册表应提供内置脱敏策略")
    void shouldUseBuiltInStrategy() {
        SensitiveProcessor processor = new SensitiveProcessor(SensitiveStrategyRegistry.defaults());

        String masked = processor.mask("13812345678", SensitiveType.PHONE);

        assertThat(masked).isEqualTo("138****5678");
    }

    /**
     * 验证自定义策略只作用于显式使用该注册表的处理器，避免污染全局状态。
     */
    @Test
    @DisplayName("自定义策略应保持实例隔离")
    void shouldKeepCustomStrategyIsolated() {
        SensitiveStrategyRegistry registry = SensitiveStrategyRegistry.builder()
                .register(SensitiveType.PHONE, (value, context) -> "自定义:" + value.substring(value.length() - 4))
                .build();
        SensitiveProcessor customProcessor = new SensitiveProcessor(registry);
        SensitiveProcessor defaultProcessor = new SensitiveProcessor(SensitiveStrategyRegistry.defaults());

        assertThat(customProcessor.mask("13812345678", SensitiveType.PHONE)).isEqualTo("自定义:5678");
        assertThat(defaultProcessor.mask("13812345678", SensitiveType.PHONE)).isEqualTo("138****5678");
    }

    /**
     * 验证策略执行失败时抛出结构化异常并保留原因链，禁止静默返回明文。
     */
    @Test
    @DisplayName("策略失败时不应回退返回明文")
    void shouldFailClosedWhenStrategyFails() {
        IllegalStateException cause = new IllegalStateException("模拟策略故障");
        SensitiveStrategyRegistry registry = SensitiveStrategyRegistry.builder()
                .register(SensitiveType.CUSTOM, (value, context) -> {
                    throw cause;
                })
                .build();
        SensitiveProcessor processor = new SensitiveProcessor(registry);

        assertThatThrownBy(() -> processor.mask("secret", SensitiveType.CUSTOM))
                .isInstanceOfSatisfying(SensitiveException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(SensitiveErrorCode.MASK_FAILED);
                    assertThat(exception.getCause()).isSameAs(cause);
                    assertThat(exception.getMessage()).doesNotContain("secret");
                });
    }

    /**
     * 验证短值或格式异常值仍会被安全遮盖，禁止策略把非空明文直接返回。
     */
    @Test
    @DisplayName("格式异常时应安全遮盖而不是返回明文")
    void shouldMaskMalformedValuesInsteadOfReturningPlaintext() {
        SensitiveProcessor processor = new SensitiveProcessor(SensitiveStrategyRegistry.defaults());

        assertThat(processor.mask("123", SensitiveType.PHONE)).isEqualTo("***");
        assertThat(processor.mask("invalid-email", SensitiveType.EMAIL)).isEqualTo("*************");
        assertThat(processor.mask("not-ip", SensitiveType.IPV4)).isEqualTo("******");
    }

    /**
     * 验证自定义正则策略缺少匹配规则时立即失败，避免无配置情况下放行明文。
     */
    @Test
    @DisplayName("自定义策略缺少正则时应拒绝执行")
    void shouldRejectCustomStrategyWithoutPattern() {
        SensitiveProcessor processor = new SensitiveProcessor(SensitiveStrategyRegistry.defaults());

        assertThatThrownBy(() -> processor.mask("secret", SensitiveType.CUSTOM))
                .isInstanceOfSatisfying(SensitiveException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(SensitiveErrorCode.CONFIGURATION_INVALID));
    }

    /**
     * 验证用户显式指定的替换字符串不会被策略默认值覆盖。
     */
    @Test
    @DisplayName("显式替换字符串应优先于策略默认值")
    void shouldRespectExplicitReplacement() {
        SensitiveProcessor processor = new SensitiveProcessor(SensitiveStrategyRegistry.defaults());
        MaskContext context = MaskContext.DEFAULT.withReplacement("*");

        String masked = processor.mask(
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                SensitiveType.IPV6,
                context);

        assertThat(masked).isEqualTo("2001:*:7334");
    }
}
