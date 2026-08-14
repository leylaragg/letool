package com.github.leyland.letool.ruleengine.diagnostic;

import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 诊断基础文案与安全参数的统一拼接契约测试。
 */
class DiagnosticMessageFormatterTest {

    /** 没有动态参数时不追加多余标点。 */
    @Test
    void shouldFormatBaseMessageWithoutArgumentSuffix() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of());

        assertThat(DiagnosticMessageFormatter.format(diagnostic, "基础消息"))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] 基础消息");
    }

    /** 参数按原顺序稳定输出，小数不退回科学计数法。 */
    @Test
    void shouldAppendSafeArgumentsInStableOrder() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of(
                "ROUND",
                Boolean.TRUE,
                new BigInteger("12345678901234567890"),
                new BigDecimal("1E+3"),
                LocalDate.parse("2026-08-14"),
                LocalDateTime.parse("2026-08-14T10:30:45"),
                Instant.parse("2026-08-14T02:30:45Z")));

        assertThat(DiagnosticMessageFormatter.format(diagnostic, "基础消息"))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] 基础消息："
                        + "ROUND，true，12345678901234567890，1000，2026-08-14，"
                        + "2026-08-14T10:30:45，2026-08-14T02:30:45Z");
    }

    /** 控制字符等长替换，继续服从字符串参数长度上限。 */
    @Test
    void shouldReplaceIsoControlCharactersWithoutBreakingLengthBound() {
        String unsafeText = "x\n\u0085y".repeat(
                RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH / 4);
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of(unsafeText));

        String formatted = DiagnosticMessageFormatter.format(diagnostic, "基础\r消息");
        String prefix = "[RULE_ENGINE_COMPILE_LEXICAL_004] 基础 消息：";

        assertThat(formatted)
                .isEqualTo(prefix + "x  y".repeat(
                        RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH / 4))
                .hasSize(prefix.length() + RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH)
                .doesNotContain("\n", "\r", "\u0085");
    }

    /** 基础文案最多二千零四十八字符，超出一个字符也必须稳定拒绝。 */
    @Test
    void shouldBoundBaseMessageBeforeRendering() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of());
        String boundary = "x".repeat(2048);

        assertThat(DiagnosticMessageFormatter.format(diagnostic, boundary))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] " + boundary);
        assertInvalid(() -> DiagnosticMessageFormatter.format(
                diagnostic, "x".repeat(2049)));
    }

    /** Unicode 行分隔符和孤立代理项被替换，合法代理对保持原样。 */
    @Test
    void shouldSanitizeUnicodeSeparatorsAndIsolatedSurrogates() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of(
                "a\u2028b\u2029c\uD83Dd\uDC00e\uD83D\uDE00f"));

        assertThat(DiagnosticMessageFormatter.format(diagnostic, "基础\u2028消息"))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] 基础 消息："
                        + "a b c d e\uD83D\uDE00f");
    }

    /** 外部消息源返回的空文案统一收敛为固定非法参数错误。 */
    @Test
    void shouldRejectNullOrBlankPublicInputsWithStableException() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of());

        assertInvalid(() -> DiagnosticMessageFormatter.format(null, "基础消息"));
        assertInvalid(() -> DiagnosticMessageFormatter.format(diagnostic, null));
        assertInvalid(() -> DiagnosticMessageFormatter.format(diagnostic, ""));
        assertInvalid(() -> DiagnosticMessageFormatter.format(diagnostic, " \t\r\n"));
        assertInvalid(() -> DiagnosticMessageFormatter.format(diagnostic, "\u0000\u0085"));
    }

    /** 宿主对象在格式化前被拒绝，且不触发其 {@code toString()}。 */
    @Test
    void shouldNotInvokeArbitraryHostObjectToString() {
        HostileArgument hostile = new HostileArgument();

        assertInvalid(() -> DiagnosticMessageFormatter.format(
                diagnosticWithArguments(List.of(hostile)), "基础消息"));
        assertThat(hostile.toStringInvoked).isFalse();
    }

    /**
     * 构造统一词法诊断，让测试只关注参数格式化行为。
     *
     * @param arguments 已经由测试场景选择的参数
     * @return 可交给格式化器的结构化诊断
     */
    private static RuleDiagnostic diagnosticWithArguments(List<Object> arguments) {
        return new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL,
                0,
                1,
                arguments,
                null);
    }

    /**
     * 断言边界失败时只暴露统一非法参数错误。
     *
     * @param operation 预期被安全拒绝的调用
     */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                });
    }

    /** 模拟可能通过文本表示泄漏宿主数据的参数对象。 */
    private static final class HostileArgument {

        /** 记录安全边界是否误执行了宿主对象方法。 */
        private boolean toStringInvoked;

        /**
         * 被调用时留下痕迹，用于识别不安全渲染。
         *
         * @return 不应进入诊断消息的敏感占位文本
         */
        @Override
        public String toString() {
            toStringInvoked = true;
            return "secret-host-value";
        }
    }
}
