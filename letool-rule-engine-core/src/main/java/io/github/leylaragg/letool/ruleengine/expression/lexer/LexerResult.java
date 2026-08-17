package io.github.leylaragg.letool.ruleengine.expression.lexer;

import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.List;

/**
 * 不可变 Lexer 结果。
 *
 * <p>成功结果只包含 Token，失败结果只包含诊断。调用方通过
 * {@link #requireTokens()} 显式要求成功结果。</p>
 */
public final class LexerResult {

    /** 成功时包含唯一末尾 EOF 的不可变 Token 列表。 */
    private final List<Token> tokens;

    /** 失败时包含单个词法错误的不可变列表。 */
    private final List<RuleDiagnostic> diagnostics;

    /**
     * 创建 Lexer 结果。
     *
     * @param tokens Token 列表
     * @param diagnostics 诊断列表
     */
    private LexerResult(List<Token> tokens, List<RuleDiagnostic> diagnostics) {
        this.tokens = List.copyOf(tokens);
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建成功结果。
     *
     * @param tokens 包含 EOF 的 Token 列表
     * @return 成功结果
     */
    static LexerResult success(List<Token> tokens) {
        try {
            List<Token> snapshot = List.copyOf(tokens);
            if (snapshot.isEmpty()
                    || snapshot.get(snapshot.size() - 1).type() != TokenType.EOF
                    || !hasUniqueFinalEof(snapshot)) {
                throw RuleEngineException.invalidArgument();
            }
            return new LexerResult(snapshot, List.of());
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /**
     * 校验 Token 列表只在末尾包含一个 EOF，且不含空元素。
     *
     * @param tokens 待校验 Token 列表
     * @return 满足成功结果不变量时返回 {@code true}
     */
    private static boolean hasUniqueFinalEof(List<Token> tokens) {
        int previousEnd = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token == null
                    || token.type() == TokenType.EOF && index != tokens.size() - 1
                    || token.startPosition() < previousEnd) {
                return false;
            }
            previousEnd = token.endPosition();
        }
        return true;
    }

    /**
     * 创建仅包含一个阻断诊断的失败结果。
     *
     * @param diagnostic 诊断
     * @return 失败结果
     */
    static LexerResult failure(RuleDiagnostic diagnostic) {
        if (diagnostic == null
                || diagnostic.severity() != DiagnosticSeverity.ERROR
                || diagnostic.phase() != DiagnosticPhase.LEXICAL) {
            throw RuleEngineException.invalidArgument();
        }
        return new LexerResult(List.of(), List.of(diagnostic));
    }

    /**
     * 当前结果是否可以交给 Parser。
     *
     * @return 无诊断时返回 {@code true}
     */
    public boolean isSuccessful() {
        return diagnostics.isEmpty();
    }

    /**
     * Parser 使用的不可变 Token 快照。
     *
     * @return Token 列表；失败时为空
     */
    public List<Token> tokens() {
        return tokens;
    }

    /**
     * 失败时返回唯一的阻断词法诊断。
     *
     * @return 诊断列表；成功时为空
     */
    public List<RuleDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * 要求当前结果成功并返回 Token。
     *
     * @return Token 列表
     * @throws RuleEngineException 当前结果失败时抛出
     */
    public List<Token> requireTokens() {
        if (!isSuccessful()) {
            throw RuleEngineException.invalidArgument();
        }
        return tokens;
    }
}
