package com.github.leyland.letool.ruleengine.fact;

/**
 * 事实值的稳定类型分类。
 */
public enum FactKind {
    /** 空值。 */
    NULL,
    /** 字符串值。 */
    STRING,
    /** 布尔值。 */
    BOOLEAN,
    /** 任意精度整数值。 */
    INTEGER,
    /** 任意精度小数值。 */
    DECIMAL,
    /** 不含时区的日期值。 */
    DATE,
    /** 不含时区的日期时间值。 */
    DATE_TIME,
    /** UTC 时间点值。 */
    INSTANT,
    /** 有序数组值。 */
    ARRAY,
    /** 字符串键对象值。 */
    OBJECT
}
