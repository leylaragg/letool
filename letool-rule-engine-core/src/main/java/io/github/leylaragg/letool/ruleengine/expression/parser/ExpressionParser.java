package io.github.leylaragg.letool.ruleengine.expression.parser;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BetweenNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BinaryOperationNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.FunctionCallNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.ListLiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.PathNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode;
import io.github.leylaragg.letool.ruleengine.expression.lexer.LexerResult;
import io.github.leylaragg.letool.ruleengine.expression.lexer.Token;
import io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 阶段一表达式的确定性递归下降解析器。
 *
 * <p>解析器只构建语法结构，不查询事实契约、函数目录或运行时数据。</p>
 */
public final class ExpressionParser {

    /**
     * Parser 自身允许的最大递归和 AST 深度。
     *
     * <p>宿主限制即使配置为极大值，也不得绕过该 JVM 栈安全边界。</p>
     */
    private static final int INTERNAL_MAX_DEPTH = 256;

    /**
     * 解析成功 Lexer 结果。
     *
     * @param lexerResult 成功且携带唯一末尾 EOF 的 Lexer 结果
     * @param limits 资源限制
     * @return AST 或单个阻断语法诊断
     * @throws RuleEngineException 参数为空或 Lexer 失败时抛出
     */
    public ParserResult parse(LexerResult lexerResult, EngineLimits limits) {
        if (lexerResult == null || limits == null || !lexerResult.isSuccessful()) {
            throw RuleEngineException.invalidArgument();
        }
        List<Token> snapshot = lexerResult.requireTokens();
        if (snapshot.size() > limits.getMaxTokens()) {
            Token firstExceeded = snapshot.get(limits.getMaxTokens() - 1);
            Token eof = snapshot.get(snapshot.size() - 1);
            return ParserResult.failure(diagnostic(
                    RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED,
                    firstExceeded.startPosition(), eof.endPosition()));
        }
        int effectiveDepth = Math.min(limits.getMaxAstDepth(), INTERNAL_MAX_DEPTH);
        return new ParseSession(snapshot, effectiveDepth).parse();
    }

    /** 创建 Parser 阶段阻断诊断。 */
    private static RuleDiagnostic diagnostic(
            RuleDiagnosticCode code, int startPosition, int endPosition) {
        return new RuleDiagnostic(
                code, DiagnosticSeverity.ERROR, DiagnosticPhase.SYNTAX,
                startPosition, endPosition, List.of(), null);
    }

    /**
     * 单次解析调用私有的可变会话。
     */
    private static final class ParseSession {

        /** 位于逻辑层和算术层之间的比较类运算符。 */
        private static final Set<TokenType> COMPARISON_OPERATORS = Set.of(
                TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GE,
                TokenType.LT, TokenType.LE, TokenType.IN, TokenType.NOT_IN,
                TokenType.BETWEEN, TokenType.IS_NULL, TokenType.IS_NOT_NULL);

        /** 包含唯一末尾 EOF 的 Token 快照。 */
        private final List<Token> tokens;

        /** AST 节点深度和无节点递归共同遵守的预算。 */
        private final int maxAstDepth;

        /** 当前尚未消费的 Token 索引。 */
        private int position;

        /** 首个阻断语法诊断。 */
        private RuleDiagnostic failure;

        /**
         * 创建解析会话。
         *
         * @param tokens 已校验 Token 快照
         * @param maxAstDepth 最大 AST 和无节点递归嵌套深度
         */
        private ParseSession(List<Token> tokens, int maxAstDepth) {
            this.tokens = tokens;
            this.maxAstDepth = maxAstDepth;
        }

        /** 执行完整解析。 */
        private ParserResult parse() {
            Parsed root = parseOr(0);
            if (failure == null && root == null) {
                failMissingOperand();
            }
            if (failure == null && current().type() != TokenType.EOF) {
                fail(RuleDiagnosticCode.UNEXPECTED_TOKEN, current());
            }
            return failure == null
                    ? ParserResult.success(root.node)
                    : ParserResult.failure(failure);
        }

        /** 解析 OR 层。 */
        private Parsed parseOr(int recursionDepth) {
            Parsed left = parseAnd(recursionDepth);
            while (failure == null && match(TokenType.OR)) {
                Token operator = previous();
                Parsed right = requireOperand(recursionDepth);
                left = binary(operator, left, right);
            }
            return left;
        }

        /** 解析 AND 层。 */
        private Parsed parseAnd(int recursionDepth) {
            Parsed left = parseComparison(recursionDepth);
            while (failure == null && match(TokenType.AND)) {
                Token operator = previous();
                Parsed right = parseComparison(recursionDepth);
                if (right == null) {
                    failMissingOperand();
                    return left;
                }
                left = binary(operator, left, right);
            }
            return left;
        }

        /** 解析单次比较、集合、区间或空值判断。 */
        private Parsed parseComparison(int recursionDepth) {
            Parsed left = parseAdditive(recursionDepth);
            if (failure != null || left == null || !COMPARISON_OPERATORS.contains(current().type())) {
                return left;
            }
            Token operator = advance();
            Parsed result;
            if (operator.type() == TokenType.IS_NULL
                    || operator.type() == TokenType.IS_NOT_NULL) {
                result = unary(operator, left, left.node.startPosition(), operator.endPosition());
            } else if (operator.type() == TokenType.BETWEEN) {
                result = parseBetween(left, recursionDepth);
            } else if (operator.type() == TokenType.IN
                    || operator.type() == TokenType.NOT_IN) {
                Parsed list = parseInList(recursionDepth);
                result = list == null ? left : binary(operator, left, list);
            } else {
                Parsed right = parseAdditive(recursionDepth);
                if (right == null) {
                    failMissingOperand();
                    return left;
                }
                result = binary(operator, left, right);
            }
            if (failure == null && COMPARISON_OPERATORS.contains(current().type())) {
                fail(RuleDiagnosticCode.UNEXPECTED_TOKEN, current());
            }
            return result;
        }

        /** 解析 BETWEEN 的下界、分隔 AND 和上界。 */
        private Parsed parseBetween(Parsed value, int recursionDepth) {
            Parsed lower = parseAdditive(recursionDepth);
            if (lower == null) {
                failMissingOperand();
                return value;
            }
            if (!match(TokenType.AND)) {
                fail(RuleDiagnosticCode.MISSING_BETWEEN_AND, current());
                return value;
            }
            Parsed upper = parseAdditive(recursionDepth);
            if (upper == null) {
                failMissingOperand();
                return value;
            }
            int depth = checkedDepth(1 + Math.max(value.depth,
                    Math.max(lower.depth, upper.depth)), previous());
            if (failure != null) {
                return value;
            }
            return new Parsed(new BetweenNode(
                    value.node, lower.node, upper.node,
                    value.node.startPosition(), upper.node.endPosition()), depth);
        }

        /** 解析 IN 右侧的非空括号表达式列表。 */
        private Parsed parseInList(int recursionDepth) {
            if (!match(TokenType.LPAREN)) {
                fail(RuleDiagnosticCode.MISSING_PARENTHESIS, current());
                return null;
            }
            Token open = previous();
            if (current().type() == TokenType.RPAREN) {
                failMissingOperand();
                return null;
            }
            if (!allowRecursiveEntry(recursionDepth + 1)) {
                return null;
            }
            List<Parsed> elements = new ArrayList<>();
            elements.add(parseOr(recursionDepth + 1));
            while (failure == null && match(TokenType.COMMA)) {
                if (current().type() == TokenType.RPAREN || current().type() == TokenType.EOF) {
                    failMissingOperand();
                    return null;
                }
                elements.add(parseOr(recursionDepth + 1));
            }
            if (failure != null) {
                return null;
            }
            if (!match(TokenType.RPAREN)) {
                fail(RuleDiagnosticCode.MISSING_PARENTHESIS, current());
                return null;
            }
            Token close = previous();
            int childDepth = elements.stream().mapToInt(element -> element.depth).max().orElse(0);
            int depth = checkedDepth(childDepth + 1, open);
            if (failure != null) {
                return null;
            }
            return new Parsed(new ListLiteralNode(
                    elements.stream().map(element -> element.node).toList(),
                    open.startPosition(), close.endPosition()), depth);
        }

        /** 解析加减层。 */
        private Parsed parseAdditive(int recursionDepth) {
            Parsed left = parseMultiplicative(recursionDepth);
            while (failure == null && match(TokenType.PLUS, TokenType.MINUS)) {
                Token operator = previous();
                Parsed right = parseMultiplicative(recursionDepth);
                if (right == null) {
                    failMissingOperand();
                    return left;
                }
                left = binary(operator, left, right);
            }
            return left;
        }

        /** 解析乘除余数层。 */
        private Parsed parseMultiplicative(int recursionDepth) {
            Parsed left = parseUnary(recursionDepth);
            while (failure == null
                    && match(TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO)) {
                Token operator = previous();
                Parsed right = parseUnary(recursionDepth);
                if (right == null) {
                    failMissingOperand();
                    return left;
                }
                left = binary(operator, left, right);
            }
            return left;
        }

        /** 解析前缀一元操作。 */
        private Parsed parseUnary(int recursionDepth) {
            if (match(TokenType.NOT, TokenType.PLUS, TokenType.MINUS)) {
                Token operator = previous();
                if (!allowRecursiveEntry(recursionDepth + 1)) {
                    return null;
                }
                Parsed operand = parseUnary(recursionDepth + 1);
                if (operand == null) {
                    failMissingOperand();
                    return null;
                }
                return unary(operator, operand,
                        operator.startPosition(), operand.node.endPosition());
            }
            return parsePrimary(recursionDepth);
        }

        /** 解析字面量、路径、函数和普通括号。 */
        private Parsed parsePrimary(int recursionDepth) {
            Token token = current();
            return switch (token.type()) {
                case STRING, BOOLEAN, INTEGER, DECIMAL, NULL -> literal(advance());
                case DATE, DATETIME, INSTANT -> parseTemporalLiteral();
                case PATH -> {
                    advance();
                    yield leaf(new PathNode(token.normalizedValue(),
                            token.startPosition(), token.endPosition()), token);
                }
                case FUNCTION -> parseFunction(recursionDepth);
                case LPAREN -> parseParenthesized(recursionDepth);
                case IDENTIFIER -> {
                    fail(RuleDiagnosticCode.BARE_IDENTIFIER, token);
                    yield null;
                }
                case EOF, RPAREN, COMMA -> null;
                default -> {
                    fail(RuleDiagnosticCode.UNEXPECTED_TOKEN, token);
                    yield null;
                }
            };
        }

        /** 解析显式时间字面量。 */
        private Parsed parseTemporalLiteral() {
            Token prefix = advance();
            if (current().type() != TokenType.STRING) {
                fail(RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL, current());
                return null;
            }
            Token value = advance();
            return leaf(new LiteralNode(
                    prefix.type(), value.normalizedValue(),
                    prefix.startPosition(), value.endPosition()), prefix);
        }

        /** 解析函数调用和参数。 */
        private Parsed parseFunction(int recursionDepth) {
            Token function = advance();
            if (!match(TokenType.LPAREN)) {
                fail(RuleDiagnosticCode.MISSING_PARENTHESIS, current());
                return null;
            }
            List<Parsed> arguments = new ArrayList<>();
            if (current().type() != TokenType.RPAREN) {
                if (!allowRecursiveEntry(recursionDepth + 1)) {
                    return null;
                }
                if (current().type() == TokenType.COMMA) {
                    failMissingOperand();
                    return null;
                }
                arguments.add(parseOr(recursionDepth + 1));
                while (failure == null && match(TokenType.COMMA)) {
                    if (current().type() == TokenType.RPAREN
                            || current().type() == TokenType.EOF) {
                        failMissingOperand();
                        return null;
                    }
                    arguments.add(parseOr(recursionDepth + 1));
                }
            }
            if (failure != null) {
                return null;
            }
            if (!match(TokenType.RPAREN)) {
                fail(RuleDiagnosticCode.MISSING_PARENTHESIS, current());
                return null;
            }
            Token close = previous();
            int childDepth = arguments.stream().mapToInt(argument -> argument.depth).max().orElse(0);
            int depth = checkedDepth(childDepth + 1, function);
            if (failure != null) {
                return null;
            }
            return new Parsed(new FunctionCallNode(
                    function.normalizedValue(),
                    arguments.stream().map(argument -> argument.node).toList(),
                    function.startPosition(), close.endPosition()), depth);
        }

        /** 解析普通括号且不创建额外 AST 节点。 */
        private Parsed parseParenthesized(int recursionDepth) {
            Token open = advance();
            if (!allowRecursiveEntry(recursionDepth + 1)) {
                return null;
            }
            if (current().type() == TokenType.RPAREN) {
                failMissingOperand();
                return null;
            }
            Parsed nested = parseOr(recursionDepth + 1);
            if (failure != null) {
                return null;
            }
            if (!match(TokenType.RPAREN)) {
                fail(RuleDiagnosticCode.MISSING_PARENTHESIS, current());
                return null;
            }
            if (nested == null) {
                fail(RuleDiagnosticCode.MISSING_OPERAND, open);
                return null;
            }
            Token close = previous();
            return new Parsed(withRange(
                    nested.node, open.startPosition(), close.endPosition()), nested.depth);
        }

        /**
         * 在不创建分组节点的前提下，把括号范围纳入原节点。
         *
         * @param node 原节点
         * @param startPosition 扩展起始位置
         * @param endPosition 扩展结束位置
         * @return 保持原语义字段和子节点的新节点
         */
        private AstNode withRange(
                AstNode node, int startPosition, int endPosition) {
            if (node instanceof LiteralNode literal) {
                return new LiteralNode(literal.literalType(), literal.normalizedValue(),
                        startPosition, endPosition);
            }
            if (node instanceof PathNode path) {
                return new PathNode(path.normalizedPath(), startPosition, endPosition);
            }
            if (node instanceof FunctionCallNode function) {
                return new FunctionCallNode(function.code(), function.arguments(),
                        startPosition, endPosition);
            }
            if (node instanceof UnaryOperationNode unary) {
                return new UnaryOperationNode(unary.operator(), unary.operand(),
                        startPosition, endPosition);
            }
            if (node instanceof BinaryOperationNode binary) {
                return new BinaryOperationNode(binary.operator(), binary.left(), binary.right(),
                        startPosition, endPosition);
            }
            if (node instanceof BetweenNode between) {
                return new BetweenNode(
                        between.value(), between.lowerBound(), between.upperBound(),
                        startPosition, endPosition);
            }
            ListLiteralNode list = (ListLiteralNode) node;
            return new ListLiteralNode(list.elements(), startPosition, endPosition);
        }

        /** 构建深度为一的字面量。 */
        private Parsed literal(Token token) {
            return leaf(new LiteralNode(token.type(), token.normalizedValue(),
                    token.startPosition(), token.endPosition()), token);
        }

        /** 构建并校验叶节点深度。 */
        private Parsed leaf(AstNode node, Token location) {
            int depth = checkedDepth(1, location);
            return failure == null ? new Parsed(node, depth) : null;
        }

        /** 构建二元节点并检查实际 AST 深度。 */
        private Parsed binary(Token operator, Parsed left, Parsed right) {
            if (left == null || right == null || failure != null) {
                return left;
            }
            int depth = checkedDepth(1 + Math.max(left.depth, right.depth), operator);
            if (failure != null) {
                return left;
            }
            return new Parsed(new BinaryOperationNode(
                    operator.type(), left.node, right.node,
                    left.node.startPosition(), right.node.endPosition()), depth);
        }

        /** 构建一元节点并检查实际 AST 深度。 */
        private Parsed unary(
                Token operator, Parsed operand, int startPosition, int endPosition) {
            int depth = checkedDepth(operand.depth + 1, operator);
            if (failure != null) {
                return operand;
            }
            return new Parsed(new UnaryOperationNode(
                    operator.type(), operand.node, startPosition, endPosition), depth);
        }

        /** 在递归下降前执行无节点嵌套预算。 */
        private boolean allowRecursiveEntry(int recursionDepth) {
            if (recursionDepth <= maxAstDepth) {
                return true;
            }
            fail(RuleDiagnosticCode.AST_DEPTH_EXCEEDED, current());
            return false;
        }

        /** 检查即将创建节点的实际 AST 深度。 */
        private int checkedDepth(int depth, Token location) {
            if (depth > maxAstDepth) {
                fail(RuleDiagnosticCode.AST_DEPTH_EXCEEDED, location);
            }
            return depth;
        }

        /** 在二元右侧要求一个操作数。 */
        private Parsed requireOperand(int recursionDepth) {
            Parsed right = parseAnd(recursionDepth);
            if (right == null) {
                failMissingOperand();
            }
            return right;
        }

        /** 保存缺少操作数诊断。 */
        private void failMissingOperand() {
            fail(RuleDiagnosticCode.MISSING_OPERAND, current());
        }

        /** 保存首个阻断语法诊断。 */
        private void fail(RuleDiagnosticCode code, Token token) {
            if (failure == null) {
                failure = new RuleDiagnostic(
                        code, DiagnosticSeverity.ERROR, DiagnosticPhase.SYNTAX,
                        token.startPosition(), token.endPosition(), List.of(), null);
            }
        }

        /** 若当前 Token 为给定任一类型则消费。 */
        private boolean match(TokenType... types) {
            TokenType actual = current().type();
            for (TokenType type : types) {
                if (actual == type) {
                    advance();
                    return true;
                }
            }
            return false;
        }

        /** 指向尚未消费的 Token。 */
        private Token current() {
            return tokens.get(position);
        }

        /** 最近消费的 Token。 */
        private Token previous() {
            return tokens.get(position - 1);
        }

        /** 消费当前 Token，但 EOF 保持在末尾。 */
        private Token advance() {
            Token token = current();
            if (token.type() != TokenType.EOF) {
                position++;
            }
            return token;
        }
    }

    /**
     * 同时携带 AST 节点及预计算深度，避免长链重复遍历。
     */
    private static final class Parsed {

        /** 已构建的 AST 子树根节点。 */
        private final AstNode node;

        /** 子树的预计算深度。 */
        private final int depth;

        /** 绑定节点与预计算深度，供上层组合复用。 */
        private Parsed(AstNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
