package com.github.leyland.letool.monitor.alert;

/**
 * 告警条件枚举.
 *
 * <p>定义指标值与阈值之间的比较方式，用于判定是否触发告警规则。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum AlertCondition {

    /** 大于阈值时触发 */
    GREATER_THAN(">"),

    /** 小于阈值时触发 */
    LESS_THAN("<"),

    /** 等于阈值时触发（浮点数比较使用容差 0.0001） */
    EQUAL("=");

    /** 数学符号表示 */
    private final String symbol;

    /**
     * 构造告警条件枚举.
     *
     * @param symbol 数学符号
     */
    AlertCondition(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 获取比较条件的数学符号.
     *
     * @return 符号字符串，如 {@code ">"}, {@code "<"}, {@code "="}
     */
    public String getSymbol() {
        return symbol;
    }
}
