package io.github.leylaragg.letool.print.xml.expression;

/**
 * 由可信 Java 代码注册的条件表达式提供方。
 *
 * @author leyland
 */
public interface PrintConditionExpression {

    /**
     * 返回稳定的小写语言名。
     *
     * @return 表达式语言名
     */
    String language();

    /**
     * 把静态表达式编译为不可变计划。
     *
     * @param context 编译上下文
     * @return 可并发复用的表达式计划
     */
    PrintExpressionPlan compile(ExpressionCompileContext context);
}
