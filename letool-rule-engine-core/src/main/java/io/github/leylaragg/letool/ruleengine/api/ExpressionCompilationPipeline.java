package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.lexer.ExpressionLexer;
import io.github.leylaragg.letool.ruleengine.expression.lexer.LexerResult;
import io.github.leylaragg.letool.ruleengine.expression.parser.ExpressionParser;
import io.github.leylaragg.letool.ruleengine.expression.parser.ParserResult;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.internal.EngineExecutionModelFactory;
import io.github.leylaragg.letool.ruleengine.type.ExpressionTypeAnalyzer;
import io.github.leylaragg.letool.ruleengine.type.FactContract;

import java.util.List;

/**
 * 严格按输入限制、词法、语法、语义和产物构建顺序执行的包内编译流水线。
 */
final class ExpressionCompilationPipeline {

    /** 无状态词法分析器。 */
    private final ExpressionLexer lexer;

    /** 无状态语法分析器。 */
    private final ExpressionParser parser;

    /** 无状态类型与依赖分析器。 */
    private final ExpressionTypeAnalyzer analyzer;

    /** 创建无可变共享状态的编译流水线。 */
    ExpressionCompilationPipeline() {
        this.lexer = new ExpressionLexer();
        this.parser = new ExpressionParser();
        this.analyzer = new ExpressionTypeAnalyzer();
    }

    /**
     * 使用当前限制和函数目录建立成套执行模型，供包内测试和完整引擎初始化复用。
     */
    CompilationResult<CompiledExpression> compile(
            String source, FactContract factContract,
            FunctionRegistry functionRegistry, EngineLimits limits) {
        return compile(source, factContract, functionRegistry, limits,
                EngineExecutionModelFactory.create(limits, functionRegistry));
    }

    /**
     * 按固定的词法、语法和类型流水线编译表达式。
     *
     * @param source 表达式源文本
     * @param factContract 事实类型契约
     * @param functionRegistry 当前引擎冻结的函数目录
     * @param limits 当前引擎资源限制
     * @return 成功产物或结构化诊断
     */
    /**
     * 使用门面已经冻结的执行模型进入唯一编译主流程。
     *
     * @param source 表达式源文本
     * @param factContract 事实类型契约
     * @param functionRegistry 当前引擎冻结的函数目录
     * @param limits 当前引擎资源限制
     * @param executionModel 当前引擎完整执行模型
     * @return 成功产物或结构化诊断
     */
    CompilationResult<CompiledExpression> compile(
            String source,
            FactContract factContract,
            FunctionRegistry functionRegistry,
            EngineLimits limits,
            ExecutionModelDescriptor executionModel) {
        if (source == null || factContract == null || functionRegistry == null || limits == null) {
            throw RuleEngineException.invalidArgument();
        }
        if (executionModel == null) {
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
                semantic.functionDependencies(), semantic.dependencyCoverage(),
                executionModel.languageVersion(),
                executionModel.semanticVersion(), factContract.contractDigest(),
                executionModel.environmentDigest());
        return CompilationResult.success(compiled, List.of());
    }
}
