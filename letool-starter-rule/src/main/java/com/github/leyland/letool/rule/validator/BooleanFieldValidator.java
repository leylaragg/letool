package com.github.leyland.letool.rule.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 布尔字段验证器
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class BooleanFieldValidator implements FieldTypeValidator {

    /**
     * 支持的运算符
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "EQ", "NEQ", "IS_NULL", "IS_NOT_NULL"
    );

    @Override
    public String getSupportedType() {
        return "BOOLEAN";
    }

    @Override
    public ValidationResult validate(Object value, String operator) {
        if (value == null) {
            return ValidationResult.success();
        }

        // 检查运算符是否支持
        if (!SUPPORTED_OPERATORS.contains(operator)) {
            return ValidationResult.error("INVALID_OPERATOR", 
                    String.format("布尔类型不支持运算符: %s", operator));
        }

        // 验证布尔值
        if (value instanceof Boolean) {
            return ValidationResult.success();
        }

        String strValue = value.toString().trim().toLowerCase();
        if ("true".equals(strValue) || "false".equals(strValue) ||
            "1".equals(strValue) || "0".equals(strValue) ||
            "yes".equals(strValue) || "no".equals(strValue)) {
            return ValidationResult.success();
        }

        return ValidationResult.error("INVALID_BOOLEAN", 
                String.format("无效的布尔值: %s", value));
    }

    @Override
    public ValidationResult validateCompareValue(Object compareValue, String operator) {
        if (compareValue == null) {
            if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) {
                return ValidationResult.success();
            }
            return ValidationResult.error("COMPARE_VALUE_NULL", "比较值不能为空");
        }

        // 验证布尔值
        if (compareValue instanceof Boolean) {
            return ValidationResult.success();
        }

        String strValue = compareValue.toString().trim().toLowerCase();
        if ("true".equals(strValue) || "false".equals(strValue) ||
            "1".equals(strValue) || "0".equals(strValue) ||
            "yes".equals(strValue) || "no".equals(strValue)) {
            return ValidationResult.success();
        }

        return ValidationResult.error("INVALID_BOOLEAN", 
                String.format("无效的布尔比较值: %s", compareValue));
    }
}
