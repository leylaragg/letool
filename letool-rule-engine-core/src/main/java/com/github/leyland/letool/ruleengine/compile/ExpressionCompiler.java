package com.github.leyland.letool.ruleengine.compile;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.function.FunctionRegistry;
import com.github.leyland.letool.ruleengine.type.FactContract;

/**
 * 把阶段一表达式编译为不可变执行产物的公共门面。
 */
public interface ExpressionCompiler {

    /**
     * 编译表达式。
     *
     * @param source 表达式源文本
     * @param factContract 事实类型契约
     * @param functionRegistry 函数目录
     * @param limits 资源限制
     * @return 编译产物或结构化诊断
     */
    CompilationResult<CompiledExpression> compile(
            String source,
            FactContract factContract,
            FunctionRegistry functionRegistry,
            EngineLimits limits);
}
