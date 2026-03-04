package com.github.leyland.letool.rule.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 字符串字段验证器
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class StringFieldValidator implements FieldTypeValidator {

    /**
     * 支持的运算符
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "LIKE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"
    );

    /**
     * SQL注入检测关键词
     */
    private static final String[] SQL_INJECTION_KEYWORDS = {
            "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "DROP ",
            "UNION ", "EXEC ", "EXECUTE ", "XP_", "SP_"
    };

    @Override
    public String getSupportedType() {
        return "STRING";
    }

    @Override
    public ValidationResult validate(Object value, String operator) {
        if (value == null) {
            return ValidationResult.success();
        }

        // 转换为字符串
        String strValue = value.toString();

        // 检查运算符是否支持
        if (!SUPPORTED_OPERATORS.contains(operator)) {
            return ValidationResult.error("INVALID_OPERATOR", 
                    String.format("字符串类型不支持运算符: %s", operator));
        }

        // 长度检查
        if (strValue.length() > 4000) {
            return ValidationResult.error("VALUE_TOO_LONG", 
                    String.format("字符串长度超过限制: %d > 4000", strValue.length()));
        }

        return ValidationResult.success();
    }

    @Override
    public ValidationResult validateCompareValue(Object compareValue, String operator) {
        if (compareValue == null) {
            // IS_NULL 和 IS_NOT_NULL 允许空值
            if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) {
                return ValidationResult.success();
            }
            return ValidationResult.error("COMPARE_VALUE_NULL", "比较值不能为空");
        }

        String strValue = compareValue.toString();

        // SQL注入检查
        String upperValue = strValue.toUpperCase();
        for (String keyword : SQL_INJECTION_KEYWORDS) {
            if (upperValue.contains(keyword)) {
                log.warn("检测到可能的SQL注入: {}", strValue);
                return ValidationResult.error("SQL_INJECTION", 
                        "比较值包含非法字符，请检查输入");
            }
        }

        // IN/NOT_IN 运算符格式检查
        if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
            if (!strValue.contains(",")) {
                log.warn("IN运算符的比较值格式不正确: {}", strValue);
                // 允许单个值
            }
        }

        return ValidationResult.success();
    }
}
