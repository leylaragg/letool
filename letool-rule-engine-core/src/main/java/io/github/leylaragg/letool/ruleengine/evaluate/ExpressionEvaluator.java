package io.github.leylaragg.letool.ruleengine.evaluate;

import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;

/**
 * 只消费已编译 AST 和不可变事实的表达式求值入口。
 */
public interface ExpressionEvaluator {

    /**
     * 对编译产物执行一次相互隔离的求值。
     *
     * @param expression 已通过语义分析的编译产物
     * @param facts 不可变事实快照
     * @param functionRegistry 只读函数目录
     * @param options 本次求值选项
     * @return 成功值或稳定失败结果
     */
    ExpressionEvaluationResult evaluate(
            CompiledExpression expression,
            RuleFacts facts,
            FunctionRegistry functionRegistry,
            EvaluationOptions options);
}
