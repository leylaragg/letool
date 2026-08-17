package io.github.leylaragg.letool.ruleengine.expression.parser;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.expression.lexer.ExpressionLexer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Parser 深度和长输入资源边界测试。
 */
class ExpressionParserDepthTest {

    private final ExpressionLexer lexer = new ExpressionLexer();
    private final ExpressionParser parser = new ExpressionParser();

    /** 验证每个 AST 节点计一层，正好达到限制成功，多一层返回资源诊断。 */
    @Test
    void shouldEnforceExactAstDepthBoundaryForUnaryNodes() {
        EngineLimits limitFour = limits(4, 100);

        ParserResult exact = parse("NOT NOT NOT TRUE", limitFour);
        ParserResult exceeded = parse("NOT NOT NOT NOT TRUE", limitFour);

        assertThat(exact.isSuccessful()).isTrue();
        assertThat(depth(exact.requireRoot())).isEqualTo(4);
        assertDepthFailure(exceeded);
    }

    /** 验证函数嵌套和左结合链构造的 AST 深度同样受限。 */
    @Test
    void shouldBoundFunctionNestingAndLeftAssociativeChains() {
        EngineLimits limitThree = limits(3, 100);

        assertThat(parse("$F($F(1))", limitThree).isSuccessful()).isTrue();
        assertDepthFailure(parse("$F($F($F(1)))", limitThree));
        assertThat(parse("1 + 2 + 3", limitThree).isSuccessful()).isTrue();
        assertDepthFailure(parse("1 + 2 + 3 + 4", limitThree));
    }

    /** 验证大量括号在进入更深递归前被资源预算阻断。 */
    @Test
    void shouldBoundParenthesisNestingBeforeRecursing() {
        EngineLimits limits = limits(8, 100);
        String exact = "(".repeat(8) + "1" + ")".repeat(8);
        String exceeded = "(".repeat(9) + "1" + ")".repeat(9);

        assertThat(parse(exact, limits).isSuccessful()).isTrue();
        assertDepthFailure(parse(exceeded, limits));
    }

    /** 验证最大 Token 规模的平坦表达式在线性有界时间内完成且不栈溢出。 */
    @Test
    void shouldParseLongFlatInputWithinBoundedTime() {
        int operandCount = 900;
        String source = IntStream.range(0, operandCount)
                .mapToObj(index -> "1")
                .reduce((left, right) -> left + " OR " + right)
                .orElseThrow();
        EngineLimits limits = limits(128, 2048);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            ParserResult result = parse(source, limits);
            assertDepthFailure(result);
        });
    }

    /** 验证默认限制下极深不可信输入返回诊断而不是抛出栈溢出。 */
    @Test
    void shouldRejectDeepUntrustedInputWithoutStackOverflow() {
        String source = "NOT ".repeat(200) + "TRUE";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertDepthFailure(parse(source, EngineLimits.defaults())));
    }

    /** 验证宽 Lexer 预算生成的 Token 仍受当前 Parser Token 预算约束。 */
    @Test
    void shouldRecheckTokenBudgetAtParserBoundary() {
        var lexerResult = lexer.tokenize("1 + 2", EngineLimits.defaults());

        ParserResult result = parser.parse(lexerResult, limits(128, 3));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isSameAs(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED);
            assertThat(diagnostic.phase()).isSameAs(DiagnosticPhase.SYNTAX);
            assertThat(diagnostic.startPosition()).isEqualTo(4);
            assertThat(diagnostic.endPosition()).isEqualTo(5);
        });
    }

    /** 验证直接传入大量函数参数 Token 时在解析前快速返回 Token 预算诊断。 */
    @Test
    void shouldRejectLargeDirectTokenListBeforeParsingArguments() {
        var lexerResult = lexer.tokenize(
                "$F(" + "1,".repeat(900) + "1)",
                new EngineLimits(16_384, 2_048, 128, 1024, 4096, 512));

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            ParserResult result = parser.parse(lexerResult, limits(128, 64));
            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.diagnostics()).singleElement()
                    .extracting(diagnostic -> diagnostic.code())
                    .isEqualTo(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED);
        });
    }

    /** 验证宿主配置为极大值时，Parser 自身仍在第 256 层建立硬边界。 */
    @Test
    void shouldApplyInternalDepthCeilingWhenHostLimitIsUnbounded() {
        EngineLimits unbounded = new EngineLimits(
                16_384, 2_048, Integer.MAX_VALUE, 1024, 4096, 512);
        String exactUnary = "NOT ".repeat(255) + "TRUE";
        String exceededUnary = "NOT ".repeat(256) + "TRUE";
        String exactParentheses = "(".repeat(256) + "1" + ")".repeat(256);
        String exceededParentheses = "(".repeat(257) + "1" + ")".repeat(257);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(parse(exactUnary, unbounded).isSuccessful()).isTrue();
            assertDepthFailure(parse(exceededUnary, unbounded));
            assertThat(parse(exactParentheses, unbounded).isSuccessful()).isTrue();
            assertDepthFailure(parse(exceededParentheses, unbounded));
        });
    }

    /** 验证深层函数调用也受内部深度硬边界保护。 */
    @Test
    void shouldBoundDeepFunctionsWithUnboundedHostLimit() {
        EngineLimits unbounded = new EngineLimits(
                16_384, 2_048, Integer.MAX_VALUE, 1024, 4096, 512);
        String exact = "$F(".repeat(255) + "1" + ")".repeat(255);
        String exceeded = "$F(".repeat(256) + "1" + ")".repeat(256);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(parse(exact, unbounded).isSuccessful()).isTrue();
            assertDepthFailure(parse(exceeded, unbounded));
        });
    }

    /** 验证公开 AST 构造器不能绕过 256 层内部硬边界。 */
    @Test
    void shouldRejectPublicAstAtDepthTwoHundredFiftySeven() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            AstNode node = new io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode(
                    io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.INTEGER,
                    "1", 256, 257);
            for (int depth = 2; depth <= 256; depth++) {
                node = new io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode(
                        io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.NOT,
                        node, 257 - depth, 257);
            }
            AstNode maximum = node;
            assertThat(maximum.children()).hasSize(1);
            assertThat(maximum.hashCode()).isNotZero();

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    new io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode(
                            io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.NOT,
                            maximum, 0, 258)))
                    .isInstanceOf(io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.class);
        });
    }

    /** 验证组合节点无法用共享深子树绕过统一 AST 深度校验。 */
    @Test
    void shouldRejectDeepSubtreeThroughEveryCompositeShape() {
        AstNode deep = new io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode(
                io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.INTEGER,
                "1", 255, 256);
        for (int depth = 2; depth <= 256; depth++) {
            deep = new io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode(
                    io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.NOT,
                    deep, 256 - depth, 256);
        }
        AstNode depth256 = deep;
        AstNode leaf = new io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode(
                io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.INTEGER,
                "2", 257, 258);

        assertThatThrownByInvalid(() ->
                new io.github.leylaragg.letool.ruleengine.expression.ast.BinaryOperationNode(
                        io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.PLUS,
                        depth256, leaf, 0, 258));
        assertThatThrownByInvalid(() ->
                new io.github.leylaragg.letool.ruleengine.expression.ast.BetweenNode(
                        depth256, leaf,
                        new io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode(
                                io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType.INTEGER,
                                "3", 259, 260), 0, 260));
        assertThatThrownByInvalid(() ->
                new io.github.leylaragg.letool.ruleengine.expression.ast.FunctionCallNode(
                        "F", List.of(depth256), 0, 258));
        assertThatThrownByInvalid(() ->
                new io.github.leylaragg.letool.ruleengine.expression.ast.ListLiteralNode(
                        List.of(depth256), 0, 258));
    }

    /** 断言公开 AST 构造深度失败使用统一非法参数异常。 */
    private static void assertThatThrownByInvalid(Runnable operation) {
        org.assertj.core.api.Assertions.assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        io.github.leylaragg.letool.ruleengine.exception.RuleEngineException.class,
                        exception -> assertThat(exception.getCause()).isNull());
    }

    /** 执行 Lexer 和 Parser。 */
    private ParserResult parse(String source, EngineLimits limits) {
        return parser.parse(lexer.tokenize(source, limits), limits);
    }

    /** 创建只调整 AST 和 Token 上限的限制。 */
    private static EngineLimits limits(int maxAstDepth, int maxTokens) {
        return new EngineLimits(16_384, maxTokens, maxAstDepth, 1024, 4096, 512);
    }

    /** 计算 AST 实际最大深度。 */
    private static int depth(AstNode node) {
        return 1 + node.children().stream()
                .mapToInt(ExpressionParserDepthTest::depth)
                .max().orElse(0);
    }

    /** 断言单个 AST 深度资源诊断。 */
    private static void assertDepthFailure(ParserResult result) {
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isSameAs(RuleDiagnosticCode.AST_DEPTH_EXCEEDED);
            assertThat(diagnostic.phase()).isSameAs(DiagnosticPhase.SYNTAX);
        });
    }
}
