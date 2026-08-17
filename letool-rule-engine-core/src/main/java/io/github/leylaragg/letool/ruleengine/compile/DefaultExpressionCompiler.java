package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.lexer.ExpressionLexer;
import io.github.leylaragg.letool.ruleengine.expression.lexer.LexerResult;
import io.github.leylaragg.letool.ruleengine.expression.parser.ExpressionParser;
import io.github.leylaragg.letool.ruleengine.expression.parser.ParserResult;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.ExpressionTypeAnalyzer;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeCompatibility;

import java.util.List;

/**
 * 严格按输入限制、词法、语法、语义和产物构建顺序执行的默认编译器。
 */
public final class DefaultExpressionCompiler implements ExpressionCompiler {

    /** 阶段一 DSL 语法与语义版本。 */
    public static final String LANGUAGE_VERSION = "1.0";

    /** 当前核心编译实现版本。 */
    public static final String ENGINE_VERSION = "1.0";

    /** 无状态词法分析器。 */
    private final ExpressionLexer lexer;

    /** 无状态语法分析器。 */
    private final ExpressionParser parser;

    /** 无状态类型与依赖分析器。 */
    private final ExpressionTypeAnalyzer analyzer;

    /** 创建无可变共享状态的默认编译器。 */
    public DefaultExpressionCompiler() {
        this.lexer = new ExpressionLexer();
        this.parser = new ExpressionParser();
        this.analyzer = new ExpressionTypeAnalyzer();
    }

    /** {@inheritDoc} */
    @Override
    public CompilationResult<CompiledExpression> compile(
            String source, FactContract factContract,
            FunctionRegistry functionRegistry, EngineLimits limits) {
        if (source == null || factContract == null || functionRegistry == null || limits == null) {
            throw RuleEngineException.invalidArgument();
        }
        LexerResult lexical = lexer.tokenize(source, limits);
        if (!lexical.isSuccessful()) return CompilationResult.failure(lexical.diagnostics());
        ParserResult syntax = parser.parse(lexical, limits);
        if (!syntax.isSuccessful()) return CompilationResult.failure(syntax.diagnostics());

        ExpressionTypeAnalyzer.Analysis semantic = analyzer.analyze(
                syntax.requireRoot(), factContract, functionRegistry,
                Math.min(limits.getMaxTokens(), 256));
        if (!semantic.isSuccessful()) return CompilationResult.failure(semantic.diagnostics());

        CompiledExpression compiled = new CompiledExpression(
                source, syntax.requireRoot(), semantic.resultType(), semantic.dependencies(),
                semantic.functionDependencies(), LANGUAGE_VERSION,
                TypeCompatibility.TYPE_CATALOG_FINGERPRINT, ENGINE_VERSION,
                factContract.fingerprint(), functionRegistry.fingerprint());
        return CompilationResult.success(compiled, List.of());
    }
}
