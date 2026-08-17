package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;

/**
 * 纯 Java 表达式编译与求值的稳定底层门面。
 *
 * <p>默认实现无可变共享状态，可以并发使用。若构建器注入自定义编译器或求值器，
 * 线程安全性同时取决于这些宿主实现。</p>
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
     * 按当前事实与函数目录编译表达式。
     *
     * @param source 表达式源文本
     * @param factContract 事实类型契约
     * @return 成功产物或结构化编译诊断
     */
    CompilationResult<CompiledExpression> compile(String source, FactContract factContract);

    /**
     * 在当前引擎环境中求值已编译表达式。
     *
     * @param expression 已编译表达式
     * @param facts 不可变事实快照
     * @param options 单次求值选项
     * @return 成功值或结构化运行期诊断
     */
    ExpressionEvaluationResult evaluate(
            CompiledExpression expression, RuleFacts facts, EvaluationOptions options);
}
