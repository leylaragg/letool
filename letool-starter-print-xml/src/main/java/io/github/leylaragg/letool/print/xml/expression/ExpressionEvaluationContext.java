package io.github.leylaragg.letool.print.xml.expression;

import io.github.leylaragg.letool.print.xml.extension.PrintDataView;

import java.util.Objects;

/**
 * 条件表达式计划使用的只读求值上下文。
 *
 * @author leyland
 */
public final class ExpressionEvaluationContext {

    /** 当前绑定作用域的数据视图。 */
    private final PrintDataView data;

    /**
     * 创建表达式求值上下文。
     *
     * @param data 只读数据视图
     */
    public ExpressionEvaluationContext(PrintDataView data) {
        this.data = Objects.requireNonNull(data, "data 不能为空");
    }

    /** @return 当前只读数据视图 */
    public PrintDataView data() {
        return data;
    }
}
