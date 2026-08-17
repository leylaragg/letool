package io.github.leylaragg.letool.ruleengine.type;

/**
 * 编译期事实类型分类。
 */
public enum TypeKind {
    /** 因前置错误而暂时无法推导的类型。 */
    UNKNOWN,
    /** 空值类型。 */
    NULL,
    /** 字符串类型。 */
    STRING,
    /** 布尔类型。 */
    BOOLEAN,
    /** 任意精度整数类型。 */
    INTEGER,
    /** 任意精度小数类型。 */
    DECIMAL,
    /** 不含时区的日期类型。 */
    DATE,
    /** 不含时区的日期时间类型。 */
    DATE_TIME,
    /** UTC 时间点类型。 */
    INSTANT,
    /** 具有明确元素类型的数组类型。 */
    ARRAY,
    /** 字符串键对象类型。 */
    OBJECT
}
