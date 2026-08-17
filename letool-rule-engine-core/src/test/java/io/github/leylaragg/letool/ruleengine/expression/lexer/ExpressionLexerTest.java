package io.github.leylaragg.letool.ruleengine.expression.lexer;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.ChineseDiagnosticMessageResolver;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.AbstractList;
import java.time.Duration;
import java.util.stream.Stream;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 确定性表达式 Lexer 的语法、位置和资源边界测试。
 */
class ExpressionLexerTest {

    private final ExpressionLexer lexer = new ExpressionLexer();

    /**
     * 验证阶段一表达式可生成预期词法单元序列。
     *
     * @param source 源表达式
     * @param expectedTypes 预期类型
     */
    @ParameterizedTest
    @MethodSource("validExpressions")
    void shouldTokenizeExpression(String source, List<TokenType> expectedTypes) {
        LexerResult result = lexer.tokenize(source, EngineLimits.defaults());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens()).extracting(Token::type)
                .containsExactlyElementsOf(expectedTypes);
        assertThat(result.requireTokens()).isSameAs(result.tokens());
    }

    /**
     * 验证单引号、双引号和受控转义保留原文并生成解码值。
     */
    @Test
    void shouldDecodeControlledStringEscapesAndKeepRawText() {
        String source = "'a\\n\\t\\\\\\'' + \"b\\r\\\\\\\"\"";

        LexerResult result = lexer.tokenize(source, EngineLimits.defaults());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.tokens().get(0).rawText()).isEqualTo("'a\\n\\t\\\\\\''");
        assertThat(result.tokens().get(0).normalizedValue()).isEqualTo("a\n\t\\'");
        assertThat(result.tokens().get(2).rawText()).isEqualTo("\"b\\r\\\\\\\"\"");
        assertThat(result.tokens().get(2).normalizedValue()).isEqualTo("b\r\\\"");
    }

    /**
     * 验证关键字只按 ASCII 大小写折叠，字符串内容保持不变。
     */
    @Test
    void shouldNormalizeAsciiKeywordsAndFunctionCodeOnly() {
        LexerResult result = lexer.tokenize(
                "tRuE aNd false OR null $round('MiXeD')",
                EngineLimits.defaults());

        assertThat(result.tokens()).extracting(Token::type).containsExactly(
                TokenType.BOOLEAN, TokenType.AND, TokenType.BOOLEAN, TokenType.OR,
                TokenType.NULL, TokenType.FUNCTION, TokenType.LPAREN,
                TokenType.STRING, TokenType.RPAREN, TokenType.EOF);
        assertThat(result.tokens()).extracting(Token::normalizedValue).containsExactly(
                "true", "AND", "false", "OR", "null", "ROUND", "(",
                "MiXeD", ")", "");
    }

    /**
     * 验证复合关键字跨普通空白被规范为单个词法单元。
     */
    @Test
    void shouldCombineNotInAndNullPredicatesAcrossWhitespace() {
        LexerResult result = lexer.tokenize(
                "${a} NOT \t\r\n IN (1) AND ${b} IS\tNOT\nNULL OR ${c} IS NULL",
                EngineLimits.defaults());

        assertThat(result.tokens()).extracting(Token::type).containsExactly(
                TokenType.PATH, TokenType.NOT_IN, TokenType.LPAREN, TokenType.INTEGER,
                TokenType.RPAREN, TokenType.AND, TokenType.PATH, TokenType.IS_NOT_NULL,
                TokenType.OR, TokenType.PATH, TokenType.IS_NULL, TokenType.EOF);
    }

    /**
     * 验证数字规范值、负号和点号歧义规则。
     */
    @Test
    void shouldNormalizeNumbersAndKeepMinusAsToken() {
        LexerResult result = lexer.tokenize("-00012 + 001.2300", EngineLimits.defaults());

        assertThat(result.tokens()).extracting(Token::type).containsExactly(
                TokenType.MINUS, TokenType.INTEGER, TokenType.PLUS,
                TokenType.DECIMAL, TokenType.EOF);
        assertThat(result.tokens().get(1).normalizedValue()).isEqualTo("12");
        assertThat(result.tokens().get(3).normalizedValue()).isEqualTo("1.23");
        assertDiagnostic(lexer.tokenize(".5", EngineLimits.defaults()),
                RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1);
        assertDiagnostic(lexer.tokenize("1.", EngineLimits.defaults()),
                RuleDiagnosticCode.UNKNOWN_CHARACTER, 1, 2);
    }

    /**
     * 验证时间前缀由 Lexer 独立标记，值文本仍是字符串词法单元。
     */
    @Test
    void shouldTokenizeExplicitTemporalPrefixes() {
        LexerResult result = lexer.tokenize(
                "DATE '2026-08-13', DATETIME \"2026-08-13T10:30:00\", "
                        + "INSTANT '2026-08-13T02:30:00Z'",
                EngineLimits.defaults());

        assertThat(result.tokens()).extracting(Token::type).containsExactly(
                TokenType.DATE, TokenType.STRING, TokenType.COMMA,
                TokenType.DATETIME, TokenType.STRING, TokenType.COMMA,
                TokenType.INSTANT, TokenType.STRING, TokenType.EOF);
    }

    /**
     * 验证原始文本、规范值和 UTF-16 字符位置为零基左闭右开。
     */
    @Test
    void shouldPreserveRawTextNormalizedValueAndSourceRange() {
        String source = "  ${order.items[0].price} >= $round";
        LexerResult result = lexer.tokenize(source, EngineLimits.defaults());

        Token path = result.tokens().get(0);
        Token ge = result.tokens().get(1);
        Token function = result.tokens().get(2);
        Token eof = result.tokens().get(3);
        assertThat(path.rawText()).isEqualTo("${order.items[0].price}");
        assertThat(path.normalizedValue()).isEqualTo("order.items[0].price");
        assertThat(path.startPosition()).isEqualTo(2);
        assertThat(path.endPosition()).isEqualTo(25);
        assertThat(ge.rawText()).isEqualTo(">=");
        assertThat(ge.startPosition()).isEqualTo(26);
        assertThat(function.normalizedValue()).isEqualTo("ROUND");
        assertThat(eof.startPosition()).isEqualTo(source.length());
        assertThat(eof.endPosition()).isEqualTo(source.length());
    }

    /**
     * 验证所有单字符和双字符运算符都可确定识别。
     */
    @Test
    void shouldTokenizeEveryOperatorAndDelimiter() {
        LexerResult result = lexer.tokenize(
                "= != > >= < <= + - * / % ( ) , IN BETWEEN NOT",
                EngineLimits.defaults());

        assertThat(result.tokens()).extracting(Token::type).containsExactly(
                TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GE,
                TokenType.LT, TokenType.LE, TokenType.PLUS, TokenType.MINUS,
                TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO,
                TokenType.LPAREN, TokenType.RPAREN, TokenType.COMMA,
                TokenType.IN, TokenType.BETWEEN, TokenType.NOT, TokenType.EOF);
    }

    /**
     * 验证裸 ASCII 标识符仅被标记，不在 Lexer 阶段伪装成关键字。
     */
    @Test
    void shouldEmitIdentifierForBareNameAndRejectUnicodeConfusables() {
        LexerResult identifier = lexer.tokenize("value AND android", EngineLimits.defaults());

        assertThat(identifier.tokens()).extracting(Token::type).containsExactly(
                TokenType.IDENTIFIER, TokenType.AND, TokenType.IDENTIFIER, TokenType.EOF);
        assertDiagnostic(lexer.tokenize("ſ", EngineLimits.defaults()),
                RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1);
        assertDiagnostic(lexer.tokenize("$ſ", EngineLimits.defaults()),
                RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1);
    }

    /**
     * 验证五类典型非法输入生成单个可定位词法诊断。
     *
     * @param source 非法源文本
     * @param code 预期诊断码
     * @param start 起始位置
     * @param end 结束位置
     */
    @ParameterizedTest
    @MethodSource("invalidExpressions")
    void shouldReturnSingleLocatedDiagnostic(
            String source, RuleDiagnosticCode code, int start, int end) {
        assertDiagnostic(lexer.tokenize(source, EngineLimits.defaults()), code, start, end);
    }

    /**
     * 验证源码限制精确边界：等于限制成功，超过限制返回单个资源诊断。
     */
    @Test
    void shouldEnforceExactSourceLengthBoundary() {
        EngineLimits limits = limits(3, 10);

        assertThat(lexer.tokenize("123", limits).isSuccessful()).isTrue();
        LexerResult exceeded = lexer.tokenize("1234", limits);

        assertDiagnostic(exceeded, RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED, 0, 4);
        assertThat(exceeded.tokens()).isEmpty();
    }

    /**
     * 验证 Token 限制包含 EOF，达到分配边界立即停止并只返回资源诊断。
     */
    @Test
    void shouldCountEofInExactTokenBoundaryAndStopAllocation() {
        assertThat(lexer.tokenize("1", limits(10, 2)).tokens()).extracting(Token::type)
                .containsExactly(TokenType.INTEGER, TokenType.EOF);
        assertThat(lexer.tokenize("", limits(10, 1)).tokens()).extracting(Token::type)
                .containsExactly(TokenType.EOF);

        LexerResult exceeded = lexer.tokenize("1 + 2", limits(10, 3));
        assertDiagnostic(exceeded, RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED, 4, 5);
        assertThat(exceeded.tokens()).hasSizeLessThanOrEqualTo(2);
    }

    /**
     * 验证保留 EOF 预算后，不再扫描任何后续字符串、路径、转义或数字内容。
     */
    @Test
    void shouldEnforceTokenBudgetBeforeScanningNextLexeme() {
        EngineLimits limits = limits(16_384, 2);
        List<String> tails = List.of(
                "'" + "x".repeat(16_380),
                "${" + "x".repeat(16_379),
                "'\\q" + "x".repeat(16_378),
                "9".repeat(16_382));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (String tail : tails) {
                LexerResult result = lexer.tokenize("1 " + tail, limits);
                assertDiagnostic(result, RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED, 2, 3);
                assertThat(result.tokens()).isEmpty();
            }
        });
    }

    /**
     * 验证跳过尾部空白后仍可使用保留预算发出 EOF。
     */
    @Test
    void shouldAllowWhitespaceToReachEofAtExactBudget() {
        assertThat(lexer.tokenize("1 \t\r\n", limits(10, 2)).tokens())
                .extracting(Token::type)
                .containsExactly(TokenType.INTEGER, TokenType.EOF);
        assertThat(lexer.tokenize("", limits(10, 1)).tokens())
                .extracting(Token::type)
                .containsExactly(TokenType.EOF);
        assertThat(lexer.tokenize(" \t", limits(10, 1)).tokens())
                .extracting(Token::type)
                .containsExactly(TokenType.EOF);
        assertDiagnostic(lexer.tokenize("1", limits(10, 1)),
                RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED, 0, 1);
    }

    /**
     * 验证公开 Token 构造器完整维护类型、文本和源码范围不变量。
     */
    @Test
    void shouldValidateEveryPublicTokenInvariant() {
        Token eof = new Token(TokenType.EOF, "", "", 4, 4);
        assertThat(eof.startPosition()).isEqualTo(4);
        assertThat(eof.endPosition()).isEqualTo(4);

        assertInvalid(() -> new Token(null, "x", "x", 0, 1));
        assertInvalid(() -> new Token(TokenType.STRING, null, "x", 0, 1));
        assertInvalid(() -> new Token(TokenType.STRING, "x", null, 0, 1));
        assertInvalid(() -> new Token(TokenType.STRING, "x", "x", -1, 0));
        assertInvalid(() -> new Token(TokenType.STRING, "x", "x", 2, 1));
        assertInvalid(() -> new Token(TokenType.STRING, "x", "x", 0, 2));
        assertInvalid(() -> new Token(TokenType.INTEGER, "", "1", 0, 0));
        assertInvalid(() -> new Token(TokenType.STRING, "", "", 2, 2));
        assertInvalid(() -> new Token(TokenType.EOF, "x", "", 0, 1));
        assertInvalid(() -> new Token(TokenType.EOF, "", "x", 0, 0));
    }

    /**
     * 验证 Token 按全部语义字段提供值相等和稳定哈希。
     */
    @Test
    void shouldCompareTokensByEverySemanticField() {
        Token baseline = new Token(TokenType.STRING, "'x'", "x", 1, 4);
        Token same = new Token(TokenType.STRING, "'x'", "x", 1, 4);

        assertThat(same).isEqualTo(baseline).hasSameHashCodeAs(baseline);
        assertThat(List.of(same)).contains(baseline);
        assertThat(new Token(TokenType.PATH, "'x'", "x", 1, 4)).isNotEqualTo(baseline);
        assertThat(new Token(TokenType.STRING, "\"x\"", "x", 1, 4)).isNotEqualTo(baseline);
        assertThat(new Token(TokenType.STRING, "'x'", "y", 1, 4)).isNotEqualTo(baseline);
        assertThat(new Token(TokenType.STRING, "'x'", "x", 2, 5)).isNotEqualTo(baseline);
    }

    /**
     * 验证长数字、长字符串和长路径均在线性时间内扫描且保持结果有界。
     */
    @Test
    void shouldScanMaximumLengthInputsWithinBoundedTime() {
        EngineLimits limits = limits(16_384, 10);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(lexer.tokenize("9".repeat(16_384), limits).isSuccessful()).isTrue();
            assertThat(lexer.tokenize("'" + "a".repeat(16_382) + "'", limits)
                    .isSuccessful()).isTrue();
            assertThat(lexer.tokenize("${" + "a".repeat(16_381) + "}", limits)
                    .isSuccessful()).isTrue();
        });
    }

    /**
     * 验证关键词边界不把更长标识符拆分成复合关键字。
     */
    @Test
    void shouldRespectKeywordBoundariesForCompositeOperators() {
        LexerResult result = lexer.tokenize(
                "NOT INDEX IS NULLABLE IS NOT NULLIFY",
                EngineLimits.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isSameAs(RuleDiagnosticCode.UNEXPECTED_TOKEN);
            assertThat(diagnostic.startPosition()).isEqualTo(10);
            assertThat(diagnostic.endPosition()).isEqualTo(12);
        });
    }

    /**
     * 验证结果集合不可变，失败结果不能通过 requireTokens 当作成功消费。
     */
    @Test
    void shouldExposeImmutableResultAndRequireSuccessExplicitly() {
        LexerResult success = lexer.tokenize("1", EngineLimits.defaults());
        LexerResult failure = lexer.tokenize("@", EngineLimits.defaults());

        assertThatThrownBy(() -> success.tokens().add(success.tokens().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> failure.diagnostics().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertInvalid(failure::requireTokens);
    }

    /**
     * 验证恶意 Token 集合的访问异常不会越过公开复制边界。
     */
    @Test
    void shouldSanitizeHostileLexerResultCollections() {
        List<Token> hostile = new AbstractList<>() {
            @Override
            public Token get(int index) {
                throw new IllegalStateException("secret-token-list");
            }

            @Override
            public int size() {
                return 1;
            }
        };

        assertThatThrownBy(() -> LexerResult.success(hostile))
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("secret-token-list");
                });
    }

    /**
     * 验证成功结果只能包含唯一末尾 EOF，失败结果不携带部分 Token。
     */
    @Test
    void shouldValidateSuccessAndFailureResultInvariants() {
        Token integer = new Token(TokenType.INTEGER, "1", "1", 0, 1);
        Token eof = new Token(TokenType.EOF, "", "", 1, 1);
        io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic diagnostic =
                new io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic(
                        RuleDiagnosticCode.UNKNOWN_CHARACTER,
                        io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity.ERROR,
                        DiagnosticPhase.LEXICAL, 0, 1, List.of(), null);

        assertInvalid(() -> LexerResult.success(null));
        assertInvalid(() -> LexerResult.success(List.of()));
        assertInvalid(() -> LexerResult.success(List.of(integer)));
        assertInvalid(() -> LexerResult.success(List.of(eof, integer, eof)));
        assertInvalid(() -> LexerResult.success(List.of(eof, eof)));
        assertInvalid(() -> LexerResult.success(java.util.Arrays.asList(integer, null, eof)));
        assertInvalid(() -> LexerResult.failure(null));

        LexerResult failure = LexerResult.failure(diagnostic);
        assertThat(failure.tokens()).isEmpty();
        assertThat(failure.diagnostics()).containsExactly(diagnostic);
        assertThatThrownBy(() -> failure.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
        assertInvalid(() -> LexerResult.failure(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.WARNING,
                DiagnosticPhase.LEXICAL, 0, 1, List.of(), null)));
        assertInvalid(() -> LexerResult.failure(new RuleDiagnostic(
                RuleDiagnosticCode.UNKNOWN_CHARACTER, DiagnosticSeverity.ERROR,
                DiagnosticPhase.SYNTAX, 0, 1, List.of(), null)));
    }

    /**
     * 验证成功 Token 序列允许空白间隙，但拒绝重叠、倒序和倒退 EOF。
     */
    @Test
    void shouldValidateMonotonicNonOverlappingTokenRanges() {
        Token first = new Token(TokenType.INTEGER, "1", "1", 0, 1);
        Token spaced = new Token(TokenType.INTEGER, "2", "2", 3, 4);
        Token eof = new Token(TokenType.EOF, "", "", 6, 6);
        assertThat(LexerResult.success(List.of(first, spaced, eof)).isSuccessful()).isTrue();

        assertInvalid(() -> LexerResult.success(List.of(
                new Token(TokenType.STRING, "ab", "ab", 0, 2),
                new Token(TokenType.INTEGER, "1", "1", 1, 2),
                new Token(TokenType.EOF, "", "", 2, 2))));
        assertInvalid(() -> LexerResult.success(List.of(
                new Token(TokenType.INTEGER, "1", "1", 3, 4),
                new Token(TokenType.INTEGER, "2", "2", 0, 1),
                new Token(TokenType.EOF, "", "", 4, 4))));
        assertInvalid(() -> LexerResult.success(List.of(
                new Token(TokenType.INTEGER, "1", "1", 3, 4),
                new Token(TokenType.EOF, "", "", 2, 2))));
    }

    /**
     * 验证未知 Unicode 字符按完整码点消费，孤立代理项使用安全编码参数。
     */
    @Test
    void shouldLocateUnknownUnicodeCodePointsAndSanitizeLoneSurrogates() {
        LexerResult supplementary = lexer.tokenize("😀", EngineLimits.defaults());
        assertDiagnostic(supplementary, RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 2);
        assertThat(supplementary.diagnostics().get(0).arguments()).containsExactly("😀");
        assertThat(new ChineseDiagnosticMessageResolver().resolve(
                supplementary.diagnostics().get(0), java.util.Locale.ROOT))
                .contains("😀").doesNotContain("�");

        LexerResult high = lexer.tokenize("\uD83D", EngineLimits.defaults());
        LexerResult low = lexer.tokenize("\uDE00", EngineLimits.defaults());
        assertDiagnostic(high, RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1);
        assertDiagnostic(low, RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1);
        assertThat(high.diagnostics().get(0).arguments()).containsExactly("U+D83D");
        assertThat(low.diagnostics().get(0).arguments()).containsExactly("U+DE00");
    }

    /**
     * 验证共享 Lexer 和消息渲染器并发使用时无状态串扰。
     *
     * @throws Exception 并发执行异常时抛出
     */
    @Test
    void shouldUseSharedLexerAndResolverConcurrently() throws Exception {
        ExpressionLexer sharedLexer = new ExpressionLexer();
        ChineseDiagnosticMessageResolver sharedResolver = new ChineseDiagnosticMessageResolver();
        LexerResult expected = sharedLexer.tokenize("${a} >= 1", EngineLimits.defaults());
        RuleDiagnostic expectedDiagnostic = sharedLexer.tokenize("@", EngineLimits.defaults())
                .diagnostics().get(0);
        String expectedMessage = sharedResolver.resolve(
                expectedDiagnostic, java.util.Locale.ROOT);
        List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> (Callable<Boolean>) () -> {
                    LexerResult actual = sharedLexer.tokenize(
                            "${a} >= 1", EngineLimits.defaults());
                    RuleDiagnostic diagnostic = sharedLexer.tokenize(
                            "@", EngineLimits.defaults()).diagnostics().get(0);
                    return actual.tokens().equals(expected.tokens())
                            && diagnostic.equals(expectedDiagnostic)
                            && sharedResolver.resolve(diagnostic, java.util.Locale.FRENCH)
                            .equals(expectedMessage);
                }).toList();
        var executor = Executors.newFixedThreadPool(8);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThat(executor.invokeAll(tasks))
                            .allSatisfy(future -> assertThat(future.get()).isTrue()));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(
                    3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 验证成功结果先建立防御性快照，再针对同一快照检查 EOF 不变量。
     */
    @Test
    void shouldValidateTheSameTokenSnapshotThatResultStores() {
        Token integer = new Token(TokenType.INTEGER, "1", "1", 0, 1);
        Token eof = new Token(TokenType.EOF, "", "", 1, 1);
        java.util.concurrent.atomic.AtomicInteger reads =
                new java.util.concurrent.atomic.AtomicInteger();
        List<Token> changing = new AbstractList<>() {
            @Override
            public Token get(int index) {
                return reads.incrementAndGet() <= 2 ? eof : integer;
            }

            @Override
            public int size() {
                return 1;
            }
        };

        LexerResult result = LexerResult.success(changing);

        assertThat(result.tokens()).extracting(Token::type)
                .containsExactly(TokenType.EOF);
    }

    /**
     * 验证公开空值入口统一抛出不泄漏原因的非法参数异常。
     */
    @Test
    void shouldRejectNullApiInputs() {
        assertInvalid(() -> lexer.tokenize(null, EngineLimits.defaults()));
        assertInvalid(() -> lexer.tokenize("1", null));
    }

    /**
     * 提供成功表达式和预期类型。
     *
     * @return 参数流
     */
    private static Stream<Arguments> validExpressions() {
        return Stream.of(
                Arguments.of("1 + 2 * 3", List.of(
                        TokenType.INTEGER, TokenType.PLUS, TokenType.INTEGER,
                        TokenType.MULTIPLY, TokenType.INTEGER, TokenType.EOF)),
                Arguments.of("${order.amount} >= 100", List.of(
                        TokenType.PATH, TokenType.GE, TokenType.INTEGER, TokenType.EOF)),
                Arguments.of("$ROUND(${amount}, 2)", List.of(
                        TokenType.FUNCTION, TokenType.LPAREN, TokenType.PATH,
                        TokenType.COMMA, TokenType.INTEGER, TokenType.RPAREN, TokenType.EOF)),
                Arguments.of("${value} IS NOT NULL", List.of(
                        TokenType.PATH, TokenType.IS_NOT_NULL, TokenType.EOF)));
    }

    /**
     * 提供非法输入及准确位置。
     *
     * @return 参数流
     */
    private static Stream<Arguments> invalidExpressions() {
        return Stream.of(
                Arguments.of("'abc", RuleDiagnosticCode.UNTERMINATED_STRING, 0, 4),
                Arguments.of("${abc", RuleDiagnosticCode.UNTERMINATED_PATH, 0, 5),
                Arguments.of("'a\\q'", RuleDiagnosticCode.INVALID_ESCAPE, 2, 4),
                Arguments.of("@", RuleDiagnosticCode.UNKNOWN_CHARACTER, 0, 1),
                Arguments.of("${x} IS", RuleDiagnosticCode.UNEXPECTED_TOKEN, 5, 7),
                Arguments.of("${x} IS NOT", RuleDiagnosticCode.UNEXPECTED_TOKEN, 5, 11));
    }

    /**
     * 创建只调整源码和 Token 限制的测试配置。
     *
     * @param maxSourceLength 最大源码长度
     * @param maxTokens 最大 Token 数，包含 EOF
     * @return 测试限制
     */
    private static EngineLimits limits(int maxSourceLength, int maxTokens) {
        return new EngineLimits(maxSourceLength, maxTokens, 128, 1024, 4096, 512);
    }

    /**
     * 断言失败结果只包含预期诊断。
     *
     * @param result Lexer 结果
     * @param code 诊断码
     * @param start 起始位置
     * @param end 结束位置
     */
    private static void assertDiagnostic(
            LexerResult result, RuleDiagnosticCode code, int start, int end) {
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isSameAs(code);
            assertThat(diagnostic.phase()).isSameAs(DiagnosticPhase.LEXICAL);
            assertThat(diagnostic.startPosition()).isEqualTo(start);
            assertThat(diagnostic.endPosition()).isEqualTo(end);
        });
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
}
