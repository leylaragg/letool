package com.github.leyland.letool.rule.constant;

/**
 * 规则引擎常量定义
 *
 * @author leyland
 * @since 2026/02/14
 */
public final class RuleConstants {

    private RuleConstants() {
    }

    // ==================== 规则类型 ====================

    /**
     * 纳入规则
     */
    public static final int RULE_TYPE_INCLUDE = 0;

    /**
     * 排除规则
     */
    public static final int RULE_TYPE_EXCLUDE = 1;

    // ==================== 规则组类型 ====================

    /**
     * 纳入规则组
     */
    public static final int GROUP_TYPE_INCLUDE = 0;

    /**
     * 排除规则组
     */
    public static final int GROUP_TYPE_EXCLUDE = 1;

    // ==================== 关系类型 ====================

    /**
     * 或关系
     */
    public static final int RELATION_OR = 0;

    /**
     * 且关系
     */
    public static final int RELATION_AND = 1;

    // ==================== 运算符编码 ====================

    public static final String OPERATOR_EQ = "EQ";
    public static final String OPERATOR_NEQ = "NEQ";
    public static final String OPERATOR_GT = "GT";
    public static final String OPERATOR_GTE = "GTE";
    public static final String OPERATOR_LT = "LT";
    public static final String OPERATOR_LTE = "LTE";
    public static final String OPERATOR_LIKE = "LIKE";
    public static final String OPERATOR_IN = "IN";
    public static final String OPERATOR_NOT_IN = "NOT_IN";
    public static final String OPERATOR_IS_NULL = "IS_NULL";
    public static final String OPERATOR_IS_NOT_NULL = "IS_NOT_NULL";

    // ==================== 字段类型编码 ====================

    public static final String FIELD_TYPE_STRING = "STRING";
    public static final String FIELD_TYPE_NUMBER = "NUMBER";
    public static final String FIELD_TYPE_DATE = "DATE";
    public static final String FIELD_TYPE_DATETIME = "DATETIME";
    public static final String FIELD_TYPE_BOOLEAN = "BOOLEAN";

    // ==================== 错误码 ====================

    public static final String ERROR_EXPRESSION_NULL = "EXPRESSION_NULL";
    public static final String ERROR_FIELD_NOT_FOUND = "FIELD_NOT_FOUND";
    public static final String ERROR_OPERATOR_NOT_SUPPORTED = "OPERATOR_NOT_SUPPORTED";
    public static final String ERROR_TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String ERROR_COMPARE_VALUE_INVALID = "COMPARE_VALUE_INVALID";
    public static final String ERROR_DATA_NOT_FOUND = "DATA_NOT_FOUND";
}
