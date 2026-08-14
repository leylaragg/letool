package com.github.leyland.letool.ruleengine.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 规则引擎异常契约测试。
 */
class RuleEngineExceptionTest {

    /**
     * 验证错误码枚举只包含预期成员，且机器码没有重复。
     */
    @Test
    void shouldDefineOnlyExpectedUniqueErrorCodes() {
        assertThat(RuleEngineErrorCode.values())
                .containsExactly(
                        RuleEngineErrorCode.SOURCE_LIMIT_EXCEEDED,
                        RuleEngineErrorCode.TOKEN_LIMIT_EXCEEDED,
                        RuleEngineErrorCode.AST_DEPTH_EXCEEDED,
                        RuleEngineErrorCode.FUNCTION_CALL_LIMIT_EXCEEDED,
                        RuleEngineErrorCode.REGISTRATION_CONFLICT,
                        RuleEngineErrorCode.INVALID_ARGUMENT,
                        RuleEngineErrorCode.COMPILATION_FAILED,
                        RuleEngineErrorCode.EVALUATION_FAILED);
        assertThat(Arrays.stream(RuleEngineErrorCode.values())
                        .map(RuleEngineErrorCode::getCode))
                .doesNotHaveDuplicates();
    }

    /**
     * 验证全部公开错误码保持精确稳定。
     *
     * @param errorCode 待验证的错误码枚举
     * @param expectedCode 预期的机器可读标识
     */
    @ParameterizedTest
    @MethodSource("stableErrorCodes")
    void shouldExposeStableErrorCodes(
            RuleEngineErrorCode errorCode,
            String expectedCode) {
        assertThat(errorCode.getCode()).isEqualTo(expectedCode);
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    /**
     * 验证全部公开异常工厂映射到指定错误码，并使用固定默认消息作为兜底。
     *
     * @param exceptionFactory 待调用的异常工厂
     * @param expectedErrorCode 预期错误码
     */
    @ParameterizedTest
    @MethodSource("exceptionFactories")
    void shouldMapFactoriesToStableSafeErrors(
            Supplier<RuleEngineException> exceptionFactory,
            RuleEngineErrorCode expectedErrorCode) {
        RuleEngineException exception = exceptionFactory.get();

        assertThat(exception.getErrorCode()).isSameAs(expectedErrorCode);
        assertThat(exception.getMessageArgs()).isEmpty();
        assertThat(exception.getCustomMessage()).isNull();
        assertThat(exception.getFallbackMessage())
                .isEqualTo(expectedErrorCode.getDefaultMessage());
        assertThat(exception.getMessage())
                .isEqualTo("[" + expectedErrorCode.getCode() + "] "
                        + expectedErrorCode.getDefaultMessage());
    }

    /**
     * 验证求值失败保留原始原因，但不向异常消息泄漏敏感内容。
     */
    @Test
    void shouldRetainEvaluationCauseWithoutLeakingSensitiveMessage() {
        IllegalStateException cause = new IllegalStateException("secret-patient-token");

        RuleEngineException exception = RuleEngineException.evaluationFailed(cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.EVALUATION_FAILED);
        assertThat(exception.getMessageArgs()).isEmpty();
        assertThat(exception.getCustomMessage()).isNull();
        assertThat(exception.getFallbackMessage())
                .isEqualTo(RuleEngineErrorCode.EVALUATION_FAILED.getDefaultMessage());
        assertThat(exception.getMessage())
                .isEqualTo("[RULE_ENGINE_RUNTIME_001] 规则表达式求值失败");
    }

    /**
     * 验证编译失败保留原始原因，但不向异常消息泄漏敏感内容。
     */
    @Test
    void shouldRetainCompilationCauseWithoutLeakingSensitiveMessage() {
        IllegalStateException cause = new IllegalStateException("secret-compiler-token");

        RuleEngineException exception = RuleEngineException.compilationFailed(cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.COMPILATION_FAILED);
        assertThat(exception.getMessageArgs()).isEmpty();
        assertThat(exception.getCustomMessage()).isNull();
        assertThat(exception.getFallbackMessage())
                .isEqualTo(RuleEngineErrorCode.COMPILATION_FAILED.getDefaultMessage());
        assertThat(exception.getMessage())
                .isEqualTo("[RULE_ENGINE_COMPILE_001] 规则表达式编译失败");
    }

    /**
     * 验证求值失败工厂拒绝空原因。
     */
    @Test
    void shouldRejectNullEvaluationCause() {
        assertThatThrownBy(() -> RuleEngineException.evaluationFailed(null))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 验证编译失败工厂拒绝空原因。
     */
    @Test
    void shouldRejectNullCompilationCause() {
        assertThatThrownBy(() -> RuleEngineException.compilationFailed(null))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 验证非法参数工厂不保留消息参数或自定义消息，并使用固定默认消息。
     */
    @Test
    void shouldCreateInvalidArgumentWithFixedFallbackOnly() {
        RuleEngineException exception = RuleEngineException.invalidArgument();

        assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getMessageArgs()).isEmpty();
        assertThat(exception.getCustomMessage()).isNull();
        assertThat(exception.getFallbackMessage())
                .isEqualTo(RuleEngineErrorCode.INVALID_ARGUMENT.getDefaultMessage());
        assertThat(exception.getMessage())
                .isEqualTo("[RULE_ENGINE_API_002] 规则引擎参数不合法");
    }

    /**
     * 创建全部稳定错误码的预期映射。
     *
     * @return 错误码枚举与精确标识
     */
    private static Stream<Arguments> stableErrorCodes() {
        return Stream.of(
                Arguments.of(RuleEngineErrorCode.SOURCE_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_001"),
                Arguments.of(RuleEngineErrorCode.TOKEN_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_002"),
                Arguments.of(RuleEngineErrorCode.AST_DEPTH_EXCEEDED, "RULE_ENGINE_LIMIT_003"),
                Arguments.of(RuleEngineErrorCode.FUNCTION_CALL_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_004"),
                Arguments.of(RuleEngineErrorCode.REGISTRATION_CONFLICT, "RULE_ENGINE_API_001"),
                Arguments.of(RuleEngineErrorCode.INVALID_ARGUMENT, "RULE_ENGINE_API_002"),
                Arguments.of(RuleEngineErrorCode.COMPILATION_FAILED, "RULE_ENGINE_COMPILE_001"),
                Arguments.of(RuleEngineErrorCode.EVALUATION_FAILED, "RULE_ENGINE_RUNTIME_001"));
    }

    /**
     * 创建全部公开异常工厂的错误码预期映射。
     *
     * @return 异常工厂与预期错误码
     */
    private static Stream<Arguments> exceptionFactories() {
        return Stream.of(
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::sourceLimitExceeded,
                        RuleEngineErrorCode.SOURCE_LIMIT_EXCEEDED),
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::tokenLimitExceeded,
                        RuleEngineErrorCode.TOKEN_LIMIT_EXCEEDED),
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::astDepthExceeded,
                        RuleEngineErrorCode.AST_DEPTH_EXCEEDED),
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::functionCallLimitExceeded,
                        RuleEngineErrorCode.FUNCTION_CALL_LIMIT_EXCEEDED),
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::registrationConflict,
                        RuleEngineErrorCode.REGISTRATION_CONFLICT),
                Arguments.of(
                        (Supplier<RuleEngineException>) RuleEngineException::invalidArgument,
                        RuleEngineErrorCode.INVALID_ARGUMENT),
                Arguments.of(
                        (Supplier<RuleEngineException>) () -> RuleEngineException.compilationFailed(
                                new IllegalStateException("secret-compiler-token")),
                        RuleEngineErrorCode.COMPILATION_FAILED),
                Arguments.of(
                        (Supplier<RuleEngineException>) () -> RuleEngineException.evaluationFailed(
                                new IllegalStateException("secret-patient-token")),
                        RuleEngineErrorCode.EVALUATION_FAILED));
    }
}
