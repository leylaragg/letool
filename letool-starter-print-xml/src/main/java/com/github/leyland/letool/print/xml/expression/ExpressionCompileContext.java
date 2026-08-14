package com.github.leyland.letool.print.xml.expression;

import java.util.Objects;

/**
 * 条件表达式提供方使用的不可变编译上下文。
 *
 * @author leyland
 */
public final class ExpressionCompileContext {

    /** 已注册的表达式语言。 */
    private final String language;

    /** 待编译的静态表达式正文。 */
    private final String expression;

    /** 不包含模板正文的安全位置说明。 */
    private final String location;

    /**
     * 创建表达式编译上下文。
     *
     * @param language 表达式语言
     * @param expression 静态表达式正文
     * @param location 安全位置说明
     */
    public ExpressionCompileContext(String language, String expression, String location) {
        this.language = Objects.requireNonNull(language, "language 不能为空");
        this.expression = Objects.requireNonNull(expression, "expression 不能为空");
        this.location = Objects.requireNonNull(location, "location 不能为空");
    }

    /** @return 表达式语言 */
    public String language() {
        return language;
    }

    /** @return 静态表达式正文 */
    public String expression() {
        return expression;
    }

    /** @return 安全位置说明 */
    public String location() {
        return location;
    }
}
