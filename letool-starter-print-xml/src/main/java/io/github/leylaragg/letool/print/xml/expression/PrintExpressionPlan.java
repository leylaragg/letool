package io.github.leylaragg.letool.print.xml.expression;

/**
 * 编译后可并发复用的条件表达式计划。
 *
 * @author leyland
 */
@FunctionalInterface
public interface PrintExpressionPlan {

    /**
     * 使用当前只读数据视图求值。
     *
     * @param context 求值上下文
     * @return 条件是否成立
     */
    boolean evaluate(ExpressionEvaluationContext context);
}
