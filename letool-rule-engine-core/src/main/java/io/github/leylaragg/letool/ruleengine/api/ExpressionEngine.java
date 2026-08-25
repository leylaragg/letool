package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompilationRequest;
import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationRequest;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;

/**
 * 纯 Java 表达式编译与求值的稳定底层门面。
 *
 * <p>一个引擎实例完整拥有语言、类型、函数、编译和求值语义，并以不可变快照供
 * 多线程共享。宿主通过事实与函数扩展业务能力，不替换流水线中的单个零件。</p>
 */
public interface ExpressionEngine {

    /**
     * 创建单线程配置使用的引擎构建器。
     *
     * @return 新构建器
     */
    static ExpressionEngineBuilder builder() {
        return new ExpressionEngineBuilder();
    }

    /**
     * 按当前引擎快照编译表达式。
     *
     * @param request 源文本和事实类型契约
     * @return 成功产物或结构化编译诊断
     */
    CompilationResult<CompiledExpression> compile(CompilationRequest request);

    /**
     * 使用便捷参数创建编译请求并进入唯一编译主流程。
     *
     * @param source 表达式源文本
     * @param factContract 事实类型契约
     * @return 成功产物或结构化编译诊断
     */
    default CompilationResult<CompiledExpression> compile(
            String source, FactContract factContract) {
        return compile(new CompilationRequest(source, factContract));
    }

    /**
     * 在当前引擎环境中求值已编译表达式。
     *
     * @param request 编译产物、不可变事实和单次选项
     * @return 成功值或结构化运行期诊断
     */
    ExpressionEvaluationResult evaluate(EvaluationRequest request);

    /**
     * 使用便捷参数创建求值请求并进入唯一求值主流程。
     *
     * @param expression 已编译表达式
     * @param facts 不可变事实快照
     * @param options 单次求值选项
     * @return 成功值或结构化运行期诊断
     */
    default ExpressionEvaluationResult evaluate(
            CompiledExpression expression, RuleFacts facts, EvaluationOptions options) {
        return evaluate(new EvaluationRequest(expression, facts, options));
    }

    /**
     * 返回当前不可变引擎快照的完整语义身份。
     *
     * @return 可纳入宿主编译缓存键的执行模型
     */
    ExecutionModelDescriptor executionModel();
}
