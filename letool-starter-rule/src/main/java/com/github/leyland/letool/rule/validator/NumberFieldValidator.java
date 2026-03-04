package com.github.leyland.letool.rule.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 数字字段验证器
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class NumberFieldValidator implements FieldTypeValidator {

    /**
     * 支持的运算符
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"
    );

    /**
     * 最大值
     */
    private static final BigDecimal MAX_VALUE = new BigDecimal("999999999999999999.9999");

    /**
     * 最小值
     */
    private static final BigDecimal MIN_VALUE = new BigDecimal("-999999999999999999.9999");

    @Override
    public String getSupportedType() {
        return "NUMBER";
    }

    @Override
    public ValidationResult validate(Object value, String operator) {
        if (value == null) {
            return ValidationResult.success();
        }

        // 检查运算符是否支持
        if (!SUPPORTED_OPERATORS.contains(operator)) {
            return ValidationResult.error("INVALID_OPERATOR", 
                    String.format("数字类型不支持运算符: %s", operator));
        }

        // 尝试转换为数字
        BigDecimal numValue;
        try {
            if (value instanceof Number) {
                numValue = new BigDecimal(value.toString());
            } else {
                numValue = new BigDecimal(value.toString().trim());
            }
        } catch (NumberFormatException e) {
            return ValidationResult.error("INVALID_NUMBER", 
                    String.format("无法转换为数字: %s", value));
        }

        // 范围检查
        if (numValue.compareTo(MAX_VALUE) > 0 || numValue.compareTo(MIN_VALUE) < 0) {
            return ValidationResult.error("VALUE_OUT_OF_RANGE", 
                    String.format("数字超出范围: %s", value));
        }

        return ValidationResult.success();
    }

    @Override
    public ValidationResult validateCompareValue(Object compareValue, String operator) {
        if (compareValue == null) {
            if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) {
                return ValidationResult.success();
            }
            return ValidationResult.error("COMPARE_VALUE_NULL", "比较值不能为空");
        }

        // IN/NOT_IN 运算符支持多个值
        if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
            String[] values = compareValue.toString().split(",");
            for (String val : values) {
                try {
                    new BigDecimal(val.trim());
                } catch (NumberFormatException e) {
                    return ValidationResult.error("INVALID_NUMBER_IN_LIST", 
                            String.format("IN列表中包含无效数字: %s", val));
                }
            }
            return ValidationResult.success();
        }

        // 单个数字验证
        try {
            new BigDecimal(compareValue.toString().trim());
        } catch (NumberFormatException e) {
            return ValidationResult.error("INVALID_NUMBER", 
                    String.format("无效的数字格式: %s", compareValue));
        }

        return ValidationResult.success();
    }
}
