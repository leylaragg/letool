package io.github.leylaragg.letool.ruleengine.diagnostic;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 结构化诊断安全性和延迟渲染测试。
 */
class RuleDiagnosticTest {

    /**
     * 验证诊断保存零基、左闭右开的位置和不可变参数副本。
     */
    @Test
    void shouldCreateImmutableDiagnosticWithZeroBasedRange() {
        List<Object> arguments = new ArrayList<>();
        arguments.add("value");
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL,
                2,
                3,
                arguments,
                "${value}");

        arguments.set(0, "changed");

        assertThat(diagnostic.code()).isSameAs(RuleDiagnosticCode.UNKNOWN_CHARACTER);
        assertThat(diagnostic.severity()).isSameAs(DiagnosticSeverity.ERROR);
        assertThat(diagnostic.phase()).isSameAs(DiagnosticPhase.LEXICAL);
        assertThat(diagnostic.startPosition()).isEqualTo(2);
        assertThat(diagnostic.endPosition()).isEqualTo(3);
        assertThat(diagnostic.arguments()).containsExactly("value");
        assertThat(diagnostic.suggestedExpression()).isEqualTo("${value}");
        assertThatThrownBy(() -> diagnostic.arguments().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证诊断参数只接受可以稳定安全渲染的标量类型。
     */
    @Test
    void shouldAcceptOnlySafeStableDiagnosticArguments() {
        RuleDiagnostic diagnostic = new RuleDiagnostic(
                RuleDiagnosticCode.ARGUMENT_TYPE_MISMATCH,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.SEMANTIC,
                0,
                1,
                List.of(
                        "text", Boolean.TRUE, BigInteger.TEN, new BigDecimal("1.20"),
                        LocalDate.parse("2026-08-13"),
                        LocalDateTime.parse("2026-08-13T10:30:00"),
                        Instant.parse("2026-08-13T02:30:00Z"),
                        DiagnosticSeverity.WARNING),
                null);

        assertThat(diagnostic.arguments()).hasSize(8);
        assertInvalid(() -> diagnosticWithArguments(List.of(new Object())));
        assertInvalid(() -> diagnosticWithArguments(List.of(new IllegalStateException("secret"))));
        assertInvalid(() -> diagnosticWithArguments(List.of("x".repeat(257))));
    }

    /**
     * 验证可覆盖文本表示的数字子类不能进入诊断参数。
     */
    @Test
    void shouldRejectNumericSubclassesWithExecutableTextRendering() {
        BigInteger hostile = new BigInteger("1") {
            @Override
            public String toString() {
                throw new IllegalStateException("secret-number");
            }
        };

        assertInvalid(() -> diagnosticWithArguments(List.of(hostile)));
    }

    /**
     * 验证整数诊断参数的位长度边界严格且可安全渲染。
     */
    @Test
    void shouldBoundBigIntegerDiagnosticArgumentsByBitLength() {
        BigInteger accepted = BigInteger.ONE.shiftLeft(767);
        BigInteger rejected = BigInteger.ONE.shiftLeft(768);

        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of(accepted));

        assertThat(diagnostic.arguments()).containsExactly(accepted);
        assertThat(new ChineseDiagnosticMessageResolver().resolve(diagnostic, Locale.ROOT))
                .hasSizeLessThan(1024);
        assertInvalid(() -> diagnosticWithArguments(List.of(rejected)));
        assertInvalid(() -> diagnosticWithArguments(List.of(rejected.negate().subtract(BigInteger.ONE))));
    }

    /**
     * 验证小数诊断参数的未缩放值与 scale 均有严格安全边界。
     */
    @Test
    void shouldBoundBigDecimalDiagnosticArgumentsBeforeRendering() {
        BigInteger maximumUnscaled = BigInteger.ONE.shiftLeft(767);
        BigDecimal positiveScaleBoundary = new BigDecimal(maximumUnscaled, 240);
        BigDecimal negativeScaleBoundary = new BigDecimal(maximumUnscaled, -240);

        RuleDiagnostic diagnostic = diagnosticWithArguments(
                List.of(positiveScaleBoundary, negativeScaleBoundary));

        assertThat(new ChineseDiagnosticMessageResolver().resolve(diagnostic, Locale.ROOT))
                .hasSizeLessThan(2048);
        assertInvalid(() -> diagnosticWithArguments(List.of(
                new BigDecimal(BigInteger.ONE.shiftLeft(768), 0))));
        assertInvalid(() -> diagnosticWithArguments(List.of(
                new BigDecimal(BigInteger.ONE, 241))));
        assertInvalid(() -> diagnosticWithArguments(List.of(
                new BigDecimal(BigInteger.ONE, -241))));
        assertInvalid(() -> diagnosticWithArguments(List.of(
                new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE))));
        assertInvalid(() -> diagnosticWithArguments(List.of(
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE))));
    }

    /**
     * 验证参数数量、建议表达式长度和位置范围都有固定边界。
     */
    @Test
    void shouldRejectInvalidRangeAndOversizedDiagnosticData() {
        assertInvalid(() -> diagnosticWithArguments(java.util.Collections.nCopies(17, "x")));
        assertInvalid(() -> new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, -1, 0, List.of(), null));
        assertInvalid(() -> new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 2, 1, List.of(), null));
        assertInvalid(() -> new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 0, 0, List.of(), "x".repeat(513)));
    }

    /**
     * 验证不可信参数集合异常会被净化，不保留原因和敏感文本。
     */
    @Test
    void shouldSanitizeHostileArgumentListFailures() {
        List<Object> hostile = new AbstractList<>() {
            @Override
            public Object get(int index) {
                throw new IllegalStateException("secret-diagnostic");
            }

            @Override
            public int size() {
                return 1;
            }
        };

        assertThatThrownBy(() -> diagnosticWithArguments(hostile))
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("secret-diagnostic");
                });
    }

    /**
     * 验证消息在展示阶段按区域设置延迟渲染，未知区域仍返回确定中文。
     */
    @Test
    void shouldResolveChineseMessageLazilyAndDeterministically() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of("@"));
        DiagnosticMessageResolver resolver = new ChineseDiagnosticMessageResolver();

        String chinese = resolver.resolve(diagnostic, Locale.SIMPLIFIED_CHINESE);
        String unknownLocale = resolver.resolve(diagnostic, Locale.FRENCH);

        assertThat(chinese)
                .isEqualTo(unknownLocale)
                .contains(diagnostic.code().code())
                .contains("@");
        assertThat(diagnostic).hasNoNullFieldsOrPropertiesExcept("suggestedExpression");
    }

    /**
     * 验证枚举参数使用稳定名称而不是可覆盖的 toString 文本。
     */
    @Test
    void shouldRenderEnumByStableName() {
        RuleDiagnostic diagnostic = diagnosticWithArguments(List.of(UnsafeText.SECRET));

        String message = new ChineseDiagnosticMessageResolver()
                .resolve(diagnostic, Locale.ROOT);

        assertThat(diagnostic.arguments()).containsExactly("SECRET");
        assertThat(diagnostic.arguments().get(0)).isInstanceOf(String.class);
        assertThat(message).contains("SECRET").doesNotContain("leaked-value");
        assertInvalid(() -> diagnosticWithArguments(List.of(OversizedEnumName.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA)));
    }

    /**
     * 验证诊断按全部语义字段提供值相等和稳定哈希。
     */
    @Test
    void shouldCompareDiagnosticsByEverySemanticField() {
        RuleDiagnostic baseline = new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("@"), "x");
        RuleDiagnostic same = new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("@"), "x");

        assertThat(same).isEqualTo(baseline).hasSameHashCodeAs(baseline);
        assertThat(new HashSet<>(List.of(baseline, same))).hasSize(1);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.INVALID_ESCAPE, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("@"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.WARNING,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("@"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.SYNTAX, 1, 2, List.of("@"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 0, 2, List.of("@"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 3, List.of("@"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("#"), "x"))
                .isNotEqualTo(baseline);
        assertThat(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 1, 2, List.of("@"), "y"))
                .isNotEqualTo(baseline);
    }

    /**
     * 验证稳定诊断码唯一且默认中文渲染覆盖全集。
     */
    @Test
    void shouldExposeUniqueCodesAndRenderEveryDiagnosticCode() {
        ChineseDiagnosticMessageResolver resolver = new ChineseDiagnosticMessageResolver();
        assertThat(RuleDiagnosticCode.values()).extracting(RuleDiagnosticCode::code)
                .allSatisfy(code -> assertThat(code).isNotBlank())
                .doesNotHaveDuplicates();
        for (RuleDiagnosticCode code : RuleDiagnosticCode.values()) {
            assertThat(code).isInstanceOf(ErrorCode.class);
            ErrorCode errorCode = (ErrorCode) (Object) code;
            assertThat(errorCode.getCode()).isNotBlank().isEqualTo(code.code());
            assertThat(errorCode.getDefaultMessage())
                    .isNotBlank()
                    .doesNotMatch(".*\\{\\d+(?:,[^}]*)?}.*");
            RuleDiagnostic diagnostic = new RuleDiagnostic(
                    code, DiagnosticSeverity.ERROR, DiagnosticPhase.LEXICAL,
                    0, 0, List.of(), null);
            assertThat(resolver.resolve(diagnostic, Locale.ROOT))
                    .isEqualTo("[" + errorCode.getCode() + "] "
                            + errorCode.getDefaultMessage());
        }
    }

    /**
     * 错误码会同时进入异常协议和诊断协议；这里明确限制别名范围，避免新增枚举项时
     * 无意复用机器码，导致调用方无法仅凭编码区分错误语义。
     */
    @Test
    void shouldAllowOnlyDocumentedLimitCodeAliasesAcrossBothEnums() {
        Map<String, List<ErrorCode>> definitionsByCode = Stream.concat(
                        Arrays.stream(RuleEngineErrorCode.values())
                                .map(errorCode -> (ErrorCode) errorCode),
                        Arrays.stream(RuleDiagnosticCode.values())
                                .map(code -> (ErrorCode) (Object) code))
                .collect(Collectors.groupingBy(ErrorCode::getCode));

        assertThat(definitionsByCode.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "RULE_ENGINE_LIMIT_001",
                        "RULE_ENGINE_LIMIT_002",
                        "RULE_ENGINE_LIMIT_003",
                        "RULE_ENGINE_LIMIT_004"));
        assertThat(definitionsByCode)
                .allSatisfy((code, definitions) -> {
                    if (definitions.size() == 2) {
                        assertThat(definitions)
                                .extracting(ErrorCode::getDefaultMessage)
                                .containsOnly(definitions.get(0).getDefaultMessage())
                                .allSatisfy(message -> assertThat(message)
                                        .doesNotMatch(".*\\{\\d+(?:,[^}]*)?}.*"));
                    } else {
                        assertThat(definitions).hasSize(1);
                    }
                });
    }

    /** 每个诊断项的机器码都逐项锁定，枚举增删也必须显式更新协议测试。 */
    @Test
    void shouldExposeExactDiagnosticCodeMapping() {
        Map<RuleDiagnosticCode, String> expected = Map.ofEntries(
                Map.entry(RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_001"),
                Map.entry(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_002"),
                Map.entry(RuleDiagnosticCode.AST_DEPTH_EXCEEDED, "RULE_ENGINE_LIMIT_003"),
                Map.entry(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED, "RULE_ENGINE_LIMIT_004"),
                Map.entry(RuleDiagnosticCode.UNTERMINATED_STRING, "RULE_ENGINE_COMPILE_LEXICAL_001"),
                Map.entry(RuleDiagnosticCode.UNTERMINATED_PATH, "RULE_ENGINE_COMPILE_LEXICAL_002"),
                Map.entry(RuleDiagnosticCode.INVALID_ESCAPE, "RULE_ENGINE_COMPILE_LEXICAL_003"),
                Map.entry(RuleDiagnosticCode.UNKNOWN_CHARACTER, "RULE_ENGINE_COMPILE_LEXICAL_004"),
                Map.entry(RuleDiagnosticCode.UNEXPECTED_TOKEN, "RULE_ENGINE_COMPILE_SYNTAX_001"),
                Map.entry(RuleDiagnosticCode.MISSING_OPERAND, "RULE_ENGINE_COMPILE_SYNTAX_002"),
                Map.entry(RuleDiagnosticCode.MISSING_PARENTHESIS, "RULE_ENGINE_COMPILE_SYNTAX_003"),
                Map.entry(RuleDiagnosticCode.MISSING_BETWEEN_AND, "RULE_ENGINE_COMPILE_SYNTAX_004"),
                Map.entry(RuleDiagnosticCode.BARE_IDENTIFIER, "RULE_ENGINE_COMPILE_SYNTAX_005"),
                Map.entry(RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL, "RULE_ENGINE_COMPILE_SYNTAX_006"),
                Map.entry(RuleDiagnosticCode.INVALID_FACT_PATH, "RULE_ENGINE_COMPILE_SEMANTIC_001"),
                Map.entry(RuleDiagnosticCode.UNKNOWN_FACT_PATH, "RULE_ENGINE_COMPILE_SEMANTIC_002"),
                Map.entry(RuleDiagnosticCode.UNKNOWN_FUNCTION, "RULE_ENGINE_COMPILE_SEMANTIC_003"),
                Map.entry(RuleDiagnosticCode.ARGUMENT_COUNT_MISMATCH, "RULE_ENGINE_FUNCTION_001"),
                Map.entry(RuleDiagnosticCode.ARGUMENT_TYPE_MISMATCH, "RULE_ENGINE_FUNCTION_002"),
                Map.entry(RuleDiagnosticCode.OPERATOR_TYPE_MISMATCH, "RULE_ENGINE_TYPE_001"),
                Map.entry(RuleDiagnosticCode.MISSING_FACT_VALUE, "RULE_ENGINE_EVALUATE_001"),
                Map.entry(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH, "RULE_ENGINE_EVALUATE_002"),
                Map.entry(RuleDiagnosticCode.FINGERPRINT_MISMATCH, "RULE_ENGINE_EVALUATE_003"),
                Map.entry(RuleDiagnosticCode.EVALUATION_ERROR, "RULE_ENGINE_EVALUATE_004"),
                Map.entry(RuleDiagnosticCode.FUNCTION_EXECUTION_ERROR, "RULE_ENGINE_FUNCTION_003"));
        Map<RuleDiagnosticCode, String> actual = Arrays.stream(RuleDiagnosticCode.values())
                .collect(Collectors.toMap(code -> code, RuleDiagnosticCode::getCode));

        assertThat(actual).isEqualTo(expected);
    }

    /**
     * 验证所有公开空值入口统一抛出非法参数错误。
     */
    @Test
    void shouldRejectNullPublicInputs() {
        assertInvalid(() -> new RuleDiagnostic(
                null, DiagnosticSeverity.ERROR, DiagnosticPhase.LEXICAL,
                0, 0, List.of(), null));
        assertInvalid(() -> new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, null, DiagnosticPhase.LEXICAL,
                0, 0, List.of(), null));
        assertInvalid(() -> new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR, null,
                0, 0, List.of(), null));
        assertInvalid(() -> diagnosticWithArguments(null));
        assertInvalid(() -> new ChineseDiagnosticMessageResolver().resolve(null, Locale.ROOT));
        assertInvalid(() -> new ChineseDiagnosticMessageResolver().resolve(
                diagnosticWithArguments(List.of()), null));
    }

    /**
     * 创建通用词法诊断。
     *
     * @param arguments 安全参数
     * @return 诊断
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
     * 断言操作抛出统一非法参数错误。
     *
     * @param operation 待执行操作
     */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                });
    }

    /**
     * 覆盖了不安全文本表示的测试枚举。
     */
    private enum UnsafeText {
        /** 敏感枚举项。 */
        SECRET;

        @Override
        public String toString() {
            return "leaked-value";
        }
    }

    /**
     * 名称超过诊断参数文本上限的测试枚举。
     */
    private enum OversizedEnumName {
        /** 二百五十七字符的枚举名称。 */
        AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
    }
}
