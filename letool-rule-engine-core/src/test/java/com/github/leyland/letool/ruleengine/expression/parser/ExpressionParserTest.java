package com.github.leyland.letool.ruleengine.expression.parser;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.expression.ast.AstNode;
import com.github.leyland.letool.ruleengine.expression.ast.BetweenNode;
import com.github.leyland.letool.ruleengine.expression.ast.BinaryOperationNode;
import com.github.leyland.letool.ruleengine.expression.ast.FunctionCallNode;
import com.github.leyland.letool.ruleengine.expression.ast.ListLiteralNode;
import com.github.leyland.letool.ruleengine.expression.ast.LiteralNode;
import com.github.leyland.letool.ruleengine.expression.ast.PathNode;
import com.github.leyland.letool.ruleengine.expression.ast.UnaryOperationNode;
import com.github.leyland.letool.ruleengine.expression.lexer.ExpressionLexer;
import com.github.leyland.letool.ruleengine.expression.lexer.LexerResult;
import com.github.leyland.letool.ruleengine.expression.lexer.Token;
import com.github.leyland.letool.ruleengine.expression.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 递归下降解析器的语法、AST 契约和错误边界测试。
 */
class ExpressionParserTest {

    private final ExpressionLexer lexer = new ExpressionLexer();
    private final ExpressionParser parser = new ExpressionParser();

    /** 验证乘法优先级高于加法且二元操作按稳定顺序暴露子节点。 */
    @Test
    void shouldRespectArithmeticPrecedence() {
        BinaryOperationNode add = as(BinaryOperationNode.class, parse("1 + 2 * 3"));

        assertThat(add.operator()).isSameAs(TokenType.PLUS);
        assertThat(add.left()).isInstanceOf(LiteralNode.class);
        BinaryOperationNode multiply = as(BinaryOperationNode.class, add.right());
        assertThat(multiply.operator()).isSameAs(TokenType.MULTIPLY);
        assertThat(add.children()).containsExactly(add.left(), add.right());
        assertThat(add.startPosition()).isZero();
        assertThat(add.endPosition()).isEqualTo(9);
    }

    /** 验证同优先级二元运算符按从左到右结合。 */
    @Test
    void shouldAssociateBinaryOperatorsFromLeftToRight() {
        BinaryOperationNode subtract = as(BinaryOperationNode.class, parse("10 - 3 - 2"));

        assertThat(subtract.operator()).isSameAs(TokenType.MINUS);
        assertThat(subtract.left()).isInstanceOf(BinaryOperationNode.class);
        assertThat(((BinaryOperationNode) subtract.left()).operator()).isSameAs(TokenType.MINUS);
    }

    /** 验证逻辑非只作用于直接操作数，AND 优先级高于 OR。 */
    @Test
    void shouldRespectLogicalPrecedenceAndUnaryBinding() {
        BinaryOperationNode or = as(BinaryOperationNode.class,
                parse("NOT ${active} OR ${admin} AND TRUE"));

        assertThat(or.operator()).isSameAs(TokenType.OR);
        UnaryOperationNode not = as(UnaryOperationNode.class, or.left());
        assertThat(not.operator()).isSameAs(TokenType.NOT);
        assertThat(not.operand()).isInstanceOf(PathNode.class);
        assertThat(((BinaryOperationNode) or.right()).operator()).isSameAs(TokenType.AND);
    }

    /** 验证括号可以改变默认优先级。 */
    @Test
    void shouldRespectParenthesizedGrouping() {
        BinaryOperationNode multiply = as(BinaryOperationNode.class, parse("(1 + 2) * 3"));

        assertThat(multiply.operator()).isSameAs(TokenType.MULTIPLY);
        assertThat(multiply.left()).isInstanceOf(BinaryOperationNode.class);
        assertThat(((BinaryOperationNode) multiply.left()).operator()).isSameAs(TokenType.PLUS);
        assertThat(multiply.startPosition()).isZero();
        assertThat(multiply.endPosition()).isEqualTo(11);
        assertThat(multiply.left().startPosition()).isZero();
        assertThat(multiply.left().endPosition()).isEqualTo(7);
    }

    /** 验证 UTF-16 补充字符经过括号扩展后仍保持准确父子范围。 */
    @Test
    void shouldPreserveUtf16RangesWhenParenthesesExpandRoot() {
        String source = "('😀' + 1)";

        BinaryOperationNode root = as(BinaryOperationNode.class, parse(source));

        assertThat(root.startPosition()).isZero();
        assertThat(root.endPosition()).isEqualTo(source.length());
        assertThat(source.substring(root.startPosition(), root.endPosition())).isEqualTo(source);
        assertThat(root.left()).isInstanceOf(LiteralNode.class);
        assertThat(source.substring(root.left().startPosition(), root.left().endPosition()))
                .isEqualTo("'😀'");
        assertThat(source.substring(root.right().startPosition(), root.right().endPosition()))
                .isEqualTo("1");
        assertThat(root.children()).containsExactly(root.left(), root.right());
    }

    /** 验证全部基础字面量和显式时间字面量只保存安全规范字符串。 */
    @Test
    void shouldCreateLiteralNodesWithNormalizedTextAndExactTemporalRange() {
        List<String> sources = List.of(
                "'a\\n'", "\"text\"", "TRUE", "FALSE", "NULL", "0012", "01.2300",
                "DATE '2026-08-13'", "DATETIME '2026-08-13T10:30:00'",
                "INSTANT '2026-08-13T02:30:00Z'");
        List<TokenType> types = List.of(
                TokenType.STRING, TokenType.STRING, TokenType.BOOLEAN, TokenType.BOOLEAN,
                TokenType.NULL, TokenType.INTEGER, TokenType.DECIMAL, TokenType.DATE,
                TokenType.DATETIME, TokenType.INSTANT);
        List<String> values = List.of(
                "a\n", "text", "true", "false", "null", "12", "1.23",
                "2026-08-13", "2026-08-13T10:30:00", "2026-08-13T02:30:00Z");

        for (int index = 0; index < sources.size(); index++) {
            LiteralNode literal = as(LiteralNode.class, parse(sources.get(index)));
            assertThat(literal.literalType()).isSameAs(types.get(index));
            assertThat(literal.normalizedValue()).isEqualTo(values.get(index));
            assertThat(literal.startPosition()).isZero();
            assertThat(literal.endPosition()).isEqualTo(sources.get(index).length());
            assertThat(literal.children()).isEmpty();
        }
    }

    /** 验证路径和函数调用保留规范编码、位置和不可变参数。 */
    @Test
    void shouldParsePathAndFunctionCallWithZeroOrManyArguments() {
        FunctionCallNode zero = as(FunctionCallNode.class, parse("$NOW()"));
        FunctionCallNode call = as(FunctionCallNode.class,
                parse("$round(${amount}, 2, -1)"));

        assertThat(zero.code()).isEqualTo("NOW");
        assertThat(zero.arguments()).isEmpty();
        assertThat(call.code()).isEqualTo("ROUND");
        assertThat(call.arguments()).hasSize(3).isEqualTo(call.children());
        assertThat(call.arguments().get(0)).isInstanceOf(PathNode.class);
        assertThat(((PathNode) call.arguments().get(0)).normalizedPath()).isEqualTo("amount");
        assertThat(call.arguments().get(2)).isInstanceOf(UnaryOperationNode.class);
        assertThat(call.startPosition()).isZero();
        assertThat(call.endPosition()).isEqualTo(24);
        assertThatThrownBy(() -> call.arguments().add(call.arguments().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);

        PathNode emptyPath = as(PathNode.class, parse("${}"));
        assertThat(emptyPath.normalizedPath()).isEmpty();
    }

    /** 验证比较、空值判断、IN、NOT IN 和 BETWEEN 生成专用结构。 */
    @Test
    void shouldParseEveryComparisonForm() {
        for (String operator : List.of("=", "!=", ">", ">=", "<", "<=")) {
            BinaryOperationNode comparison = as(BinaryOperationNode.class,
                    parse("${amount} " + operator + " 10"));
            assertThat(comparison.operator()).isIn(
                    TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GE,
                    TokenType.LT, TokenType.LE);
        }

        BinaryOperationNode in = as(BinaryOperationNode.class, parse("${x} IN (1, 2 + 3)"));
        assertThat(in.operator()).isSameAs(TokenType.IN);
        ListLiteralNode list = as(ListLiteralNode.class, in.right());
        assertThat(list.elements()).hasSize(2).isEqualTo(list.children());

        BinaryOperationNode notIn = as(BinaryOperationNode.class, parse("${x} NOT IN ('a')"));
        assertThat(notIn.operator()).isSameAs(TokenType.NOT_IN);

        BetweenNode between = as(BetweenNode.class, parse("${x} BETWEEN 1 AND 2 + 3"));
        assertThat(between.children()).containsExactly(
                between.value(), between.lowerBound(), between.upperBound());
        assertThat(between.upperBound()).isInstanceOf(BinaryOperationNode.class);

        assertThat(((UnaryOperationNode) parse("${x} IS NULL")).operator())
                .isSameAs(TokenType.IS_NULL);
        assertThat(((UnaryOperationNode) parse("${x} IS NOT NULL")).operator())
                .isSameAs(TokenType.IS_NOT_NULL);
    }

    /** 验证 Parser 拒绝比较链而不将其误解为嵌套比较。 */
    @Test
    void shouldRejectChainedComparisons() {
        assertFailure("1 < 2 < 3", RuleDiagnosticCode.UNEXPECTED_TOKEN, 6, 7);
        assertFailure("1 BETWEEN 0 AND 2 = 2", RuleDiagnosticCode.UNEXPECTED_TOKEN, 18, 19);
    }

    /** 验证典型语法错误只生成一个稳定且准确定位的诊断。 */
    @Test
    void shouldReturnOneLocatedDiagnosticForInvalidSyntax() {
        assertFailure(")", RuleDiagnosticCode.MISSING_OPERAND, 0, 1);
        assertFailure("1)", RuleDiagnosticCode.UNEXPECTED_TOKEN, 1, 2);
        assertFailure("1 +", RuleDiagnosticCode.MISSING_OPERAND, 3, 3);
        assertFailure("$F(,1)", RuleDiagnosticCode.MISSING_OPERAND, 3, 4);
        assertFailure("$F(1,)", RuleDiagnosticCode.MISSING_OPERAND, 5, 6);
        assertFailure("${x} BETWEEN 1 OR 2", RuleDiagnosticCode.MISSING_BETWEEN_AND, 15, 17);
        assertFailure("value", RuleDiagnosticCode.BARE_IDENTIFIER, 0, 5);
        assertFailure("1 2", RuleDiagnosticCode.UNEXPECTED_TOKEN, 2, 3);
        assertFailure("(1 + 2", RuleDiagnosticCode.MISSING_PARENTHESIS, 6, 6);
        assertFailure("${x} IN ()", RuleDiagnosticCode.MISSING_OPERAND, 9, 10);
        assertFailure("${x} IN (1,)", RuleDiagnosticCode.MISSING_OPERAND, 11, 12);
        assertFailure("DATE 1", RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL, 5, 6);
        assertFailure("INSTANT", RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL, 7, 7);
        assertFailure("", RuleDiagnosticCode.MISSING_OPERAND, 0, 0);
    }

    /** 验证失败 Lexer 不能被 Parser 伪装成语法结果。 */
    @Test
    void shouldRejectFailedLexerResultAndNullApiInputs() {
        LexerResult failed = lexer.tokenize("@", EngineLimits.defaults());

        assertInvalid(() -> parser.parse(failed, EngineLimits.defaults()));
        assertInvalid(() -> parser.parse((LexerResult) null, EngineLimits.defaults()));
        assertInvalid(() -> parser.parse(lexer.tokenize("1", EngineLimits.defaults()), null));
    }

    /** 验证词法、解析结果只能由对应包内实现创建且 Parser 只有一个公开入口。 */
    @Test
    void shouldExposeOnlyTrustedConstructionAndSingleParserEntry() {
        assertThat(Token.class.getDeclaredConstructors())
                .allSatisfy(constructor ->
                        assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse());
        assertThat(LexerResult.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("success")
                        || method.getName().equals("failure"))
                .allSatisfy(method ->
                        assertThat(Modifier.isPublic(method.getModifiers())).isFalse());
        assertThat(ParserResult.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("success")
                        || method.getName().equals("failure"))
                .allSatisfy(method ->
                        assertThat(Modifier.isPublic(method.getModifiers())).isFalse());
        assertThat(ExpressionParser.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("parse")
                        && Modifier.isPublic(method.getModifiers()))
                .singleElement().satisfies(method ->
                        assertThat(method.getParameterTypes())
                                .containsExactly(LexerResult.class, EngineLimits.class));
    }

    /** 验证 ParserResult 的成功、失败、不可变和值语义契约。 */
    @Test
    void shouldEnforceParserResultInvariantsAndValueSemantics() {
        AstNode root = parse("1");
        ParserResult success = ParserResult.success(root);
        RuleDiagnostic diagnostic = syntaxDiagnostic(RuleDiagnosticCode.UNEXPECTED_TOKEN, 0, 1);
        ParserResult failure = ParserResult.failure(diagnostic);

        assertThat(success.isSuccessful()).isTrue();
        assertThat(success.root()).isSameAs(root);
        assertThat(success.requireRoot()).isSameAs(root);
        assertThat(success.diagnostics()).isEmpty();
        assertThat(ParserResult.success(root)).isEqualTo(success).hasSameHashCodeAs(success);
        assertThat(failure.isSuccessful()).isFalse();
        assertThat(failure.root()).isNull();
        assertThat(failure.diagnostics()).containsExactly(diagnostic);
        assertInvalid(failure::requireRoot);
        assertThatThrownBy(() -> failure.diagnostics().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertInvalid(() -> ParserResult.success(null));
        assertInvalid(() -> ParserResult.failure(null));
        assertInvalid(() -> ParserResult.failure(new RuleDiagnostic(
                RuleDiagnosticCode.UNEXPECTED_TOKEN, DiagnosticSeverity.WARNING,
                DiagnosticPhase.SYNTAX, 0, 1, List.of(), null)));
        assertInvalid(() -> ParserResult.failure(new RuleDiagnostic(
                RuleDiagnosticCode.UNEXPECTED_TOKEN, DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL, 0, 1, List.of(), null)));
    }

    /** 验证每种 AST 公开构造器维护范围、子节点和操作符不变量及值语义。 */
    @Test
    void shouldEnforceAstConstructionInvariantsAndValueSemantics() {
        LiteralNode one = new LiteralNode(TokenType.INTEGER, "1", 0, 1);
        LiteralNode same = new LiteralNode(TokenType.INTEGER, "1", 0, 1);
        LiteralNode two = new LiteralNode(TokenType.INTEGER, "2", 2, 3);
        PathNode path = new PathNode("x", 0, 4);

        assertThat(same).isEqualTo(one).hasSameHashCodeAs(one);
        assertThat(new BinaryOperationNode(TokenType.PLUS, one, two, 0, 3).children())
                .containsExactly(one, two);
        assertInvalid(() -> new LiteralNode(null, "x", 0, 1));
        assertInvalid(() -> new LiteralNode(TokenType.PATH, "x", 0, 1));
        assertInvalid(() -> new LiteralNode(TokenType.STRING, null, 0, 1));
        assertInvalid(() -> new PathNode(null, 0, 1));
        assertInvalid(() -> new UnaryOperationNode(TokenType.PLUS, one, 1, 2));
        assertInvalid(() -> new UnaryOperationNode(null, one, 0, 1));
        assertInvalid(() -> new UnaryOperationNode(TokenType.AND, one, 0, 1));
        assertInvalid(() -> new BinaryOperationNode(null, one, two, 0, 3));
        assertInvalid(() -> new BinaryOperationNode(TokenType.PLUS, null, two, 0, 3));
        assertInvalid(() -> new BinaryOperationNode(TokenType.NOT, one, two, 0, 3));
        assertInvalid(() -> new BinaryOperationNode(TokenType.PLUS, two, one, 0, 3));
        assertInvalid(() -> new BetweenNode(one, two, path, 0, 3));
        assertInvalid(() -> new BetweenNode(null, one, two, 0, 3));
        assertInvalid(() -> new ListLiteralNode(List.of(), 0, 1));
        assertInvalid(() -> new ListLiteralNode(Arrays.asList(one, null), 0, 1));
        assertInvalid(() -> new FunctionCallNode("", List.of(), 0, 2));
        assertInvalid(() -> new FunctionCallNode("F", List.of(two, one), 0, 4));

        List<AstNode> mutable = new ArrayList<>(List.of(one));
        ListLiteralNode copied = new ListLiteralNode(mutable, 0, 2);
        mutable.clear();
        assertThat(copied.elements()).containsExactly(one);
        assertThatThrownBy(() -> copied.elements().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证七类 AST 节点均按全部语义字段提供值相等和稳定哈希。 */
    @Test
    void shouldCompareEveryAstNodeByAllSemanticFields() {
        LiteralNode one = new LiteralNode(TokenType.INTEGER, "1", 0, 1);
        LiteralNode oneCopy = new LiteralNode(TokenType.INTEGER, "1", 0, 1);
        LiteralNode two = new LiteralNode(TokenType.INTEGER, "2", 2, 3);
        PathNode path = new PathNode("x", 0, 4);
        PathNode pathCopy = new PathNode("x", 0, 4);
        UnaryOperationNode unary = new UnaryOperationNode(TokenType.MINUS, one, 0, 1);
        UnaryOperationNode unaryCopy = new UnaryOperationNode(TokenType.MINUS, oneCopy, 0, 1);
        BinaryOperationNode binary = new BinaryOperationNode(
                TokenType.PLUS, one, two, 0, 3);
        BinaryOperationNode binaryCopy = new BinaryOperationNode(
                TokenType.PLUS, oneCopy,
                new LiteralNode(TokenType.INTEGER, "2", 2, 3), 0, 3);
        BetweenNode between = new BetweenNode(one, two,
                new LiteralNode(TokenType.INTEGER, "3", 4, 5), 0, 5);
        BetweenNode betweenCopy = new BetweenNode(oneCopy,
                new LiteralNode(TokenType.INTEGER, "2", 2, 3),
                new LiteralNode(TokenType.INTEGER, "3", 4, 5), 0, 5);
        ListLiteralNode list = new ListLiteralNode(List.of(one, two), 0, 4);
        ListLiteralNode listCopy = new ListLiteralNode(List.of(oneCopy,
                new LiteralNode(TokenType.INTEGER, "2", 2, 3)), 0, 4);
        FunctionCallNode function = new FunctionCallNode("F", List.of(one, two), 0, 5);
        FunctionCallNode functionCopy = new FunctionCallNode("F", List.of(oneCopy,
                new LiteralNode(TokenType.INTEGER, "2", 2, 3)), 0, 5);

        assertSameValue(one, oneCopy);
        assertSameValue(path, pathCopy);
        assertSameValue(unary, unaryCopy);
        assertSameValue(binary, binaryCopy);
        assertSameValue(between, betweenCopy);
        assertSameValue(list, listCopy);
        assertSameValue(function, functionCopy);

        assertThat(new LiteralNode(TokenType.DECIMAL, "1", 0, 1)).isNotEqualTo(one);
        assertThat(new PathNode("y", 0, 4)).isNotEqualTo(path);
        assertThat(new UnaryOperationNode(TokenType.PLUS, one, 0, 1)).isNotEqualTo(unary);
        assertThat(new BinaryOperationNode(TokenType.MINUS, one, two, 0, 3))
                .isNotEqualTo(binary);
        assertThat(new BetweenNode(one, two,
                new LiteralNode(TokenType.INTEGER, "4", 4, 5), 0, 5))
                .isNotEqualTo(between);
        assertThat(new ListLiteralNode(List.of(two), 0, 4)).isNotEqualTo(list);
        assertThat(new FunctionCallNode("G", List.of(one, two), 0, 5))
                .isNotEqualTo(function);

        List<AstNode> mutable = new ArrayList<>(List.of(one, two));
        FunctionCallNode copiedFunction = new FunctionCallNode("F", mutable, 0, 5);
        ListLiteralNode copiedList = new ListLiteralNode(mutable, 0, 5);
        mutable.clear();
        assertThat(copiedFunction.children()).containsExactly(one, two);
        assertThat(copiedList.children()).containsExactly(one, two);
        assertThatThrownBy(() -> copiedFunction.children().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copiedList.children().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 验证共享 Lexer 与 Parser 在并发解析中没有会话状态串扰。 */
    @Test
    void shouldParseConcurrentlyWithSharedLexerAndParser() throws Exception {
        ExpressionLexer sharedLexer = new ExpressionLexer();
        ExpressionParser sharedParser = new ExpressionParser();
        String source = "NOT ${active} OR $F(${amount}, 2) > 10";
        ParserResult expected = sharedParser.parse(
                sharedLexer.tokenize(source, EngineLimits.defaults()), EngineLimits.defaults());
        List<Callable<ParserResult>> tasks = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> (Callable<ParserResult>) () -> sharedParser.parse(
                        sharedLexer.tokenize(source, EngineLimits.defaults()),
                        EngineLimits.defaults()))
                .toList();
        var executor = Executors.newFixedThreadPool(8);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThat(executor.invokeAll(tasks))
                            .allSatisfy(future -> assertThat(future.get()).isEqualTo(expected)));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** 获取成功解析的根节点。 */
    private AstNode parse(String source) {
        ParserResult result = parser.parse(lexer.tokenize(source, EngineLimits.defaults()),
                EngineLimits.defaults());
        assertThat(result.diagnostics()).isEmpty();
        return result.requireRoot();
    }

    /** 断言单个语法诊断。 */
    private void assertFailure(
            String source, RuleDiagnosticCode code, int startPosition, int endPosition) {
        ParserResult result = parser.parse(
                lexer.tokenize(source, EngineLimits.defaults()), EngineLimits.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.root()).isNull();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isSameAs(code);
            assertThat(diagnostic.severity()).isSameAs(DiagnosticSeverity.ERROR);
            assertThat(diagnostic.phase()).isSameAs(DiagnosticPhase.SYNTAX);
            assertThat(diagnostic.startPosition()).isEqualTo(startPosition);
            assertThat(diagnostic.endPosition()).isEqualTo(endPosition);
        });
    }

    /** 创建测试语法诊断。 */
    private static RuleDiagnostic syntaxDiagnostic(
            RuleDiagnosticCode code, int startPosition, int endPosition) {
        return new RuleDiagnostic(code, DiagnosticSeverity.ERROR, DiagnosticPhase.SYNTAX,
                startPosition, endPosition, List.of(), null);
    }

    /** 断言对象类型并返回转换值。 */
    private static <T> T as(Class<T> type, Object value) {
        assertThat(value).isInstanceOf(type);
        return type.cast(value);
    }

    /** 断言两个值对象相等且哈希相同。 */
    private static void assertSameValue(Object first, Object second) {
        assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
    }

    /** 断言统一非法参数异常。 */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                });
    }
}
