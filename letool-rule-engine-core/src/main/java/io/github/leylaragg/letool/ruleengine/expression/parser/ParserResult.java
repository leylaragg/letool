package io.github.leylaragg.letool.ruleengine.expression.parser;

import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;

import java.util.List;
import java.util.Objects;

/**
 * 不可变 Parser 结果。
 *
 * <p>成功结果只携带根节点，失败结果只携带一个阻断语法诊断。</p>
 */
public final class ParserResult {

    /** 成功时存在的唯一 AST 根节点。 */
    private final AstNode root;

    /** 失败时包含单个语法错误的不可变列表。 */
    private final List<RuleDiagnostic> diagnostics;

    /** 接收已经校验的成功或失败状态。 */
    private ParserResult(AstNode root, List<RuleDiagnostic> diagnostics) {
        this.root = root;
        this.diagnostics = diagnostics;
    }

    /**
     * 创建成功结果。
     *
     * @param root 唯一 AST 根节点
     * @return 成功结果
     */
    static ParserResult success(AstNode root) {
        if (root == null) {
            throw RuleEngineException.invalidArgument();
        }
        return new ParserResult(root, List.of());
    }

    /**
     * 创建失败结果。
     *
     * @param diagnostic 严重级别为错误的语法诊断
     * @return 失败结果
     */
    static ParserResult failure(RuleDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.severity() != DiagnosticSeverity.ERROR
                || diagnostic.phase() != DiagnosticPhase.SYNTAX) {
            throw RuleEngineException.invalidArgument();
        }
        return new ParserResult(null, List.of(diagnostic));
    }

    /** @return 无诊断时返回 {@code true} */
    public boolean isSuccessful() {
        return root != null;
    }

    /** @return 成功时为根节点，失败时为 {@code null} */
    public AstNode root() {
        return root;
    }

    /** @return 不可变诊断列表 */
    public List<RuleDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * 要求成功并取得根节点。
     *
     * @return 根节点
     * @throws RuleEngineException 当前结果失败时抛出
     */
    public AstNode requireRoot() {
        if (!isSuccessful()) {
            throw RuleEngineException.invalidArgument();
        }
        return root;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ParserResult that)) return false;
        return Objects.equals(root, that.root) && diagnostics.equals(that.diagnostics);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(root, diagnostics);
    }
}
