package com.github.leyland.letool.ruleengine.expression.lexer;

/**
 * 阶段一表达式语法的词法单元类型。
 */
public enum TokenType {

    /** 字符串字面量。 */
    STRING,
    /** 布尔字面量。 */
    BOOLEAN,
    /** 整数字面量。 */
    INTEGER,
    /** 小数字面量。 */
    DECIMAL,
    /** 空值字面量。 */
    NULL,
    /** 日期类型前缀。 */
    DATE,
    /** 日期时间类型前缀。 */
    DATETIME,
    /** 时刻类型前缀。 */
    INSTANT,
    /** 事实路径。 */
    PATH,
    /** 函数编码。 */
    FUNCTION,
    /** 阶段一不允许使用的裸标识符。 */
    IDENTIFIER,

    /** 等于。 */
    EQ,
    /** 不等于。 */
    NE,
    /** 大于。 */
    GT,
    /** 大于或等于。 */
    GE,
    /** 小于。 */
    LT,
    /** 小于或等于。 */
    LE,
    /** 包含。 */
    IN,
    /** 不包含。 */
    NOT_IN,
    /** 闭区间判断。 */
    BETWEEN,
    /** 为空判断。 */
    IS_NULL,
    /** 非空判断。 */
    IS_NOT_NULL,

    /** 逻辑与。 */
    AND,
    /** 逻辑或。 */
    OR,
    /** 逻辑非。 */
    NOT,
    /** 加法。 */
    PLUS,
    /** 减法或一元负号。 */
    MINUS,
    /** 乘法。 */
    MULTIPLY,
    /** 除法。 */
    DIVIDE,
    /** 取余。 */
    MODULO,
    /** 左括号。 */
    LPAREN,
    /** 右括号。 */
    RPAREN,
    /** 参数分隔逗号。 */
    COMMA,
    /** 输入结束标记。 */
    EOF
}
