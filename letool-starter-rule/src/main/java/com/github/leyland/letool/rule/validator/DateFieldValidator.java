package com.github.leyland.letool.rule.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * 日期字段验证器
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class DateFieldValidator implements FieldTypeValidator {

    /**
     * 支持的运算符
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"
    );

    /**
     * 支持的日期格式
     */
    private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "yyyyMMdd",
            "yyyyMMddHHmmss"
    };

    @Override
    public String getSupportedType() {
        return "DATE";
    }

    @Override
    public ValidationResult validate(Object value, String operator) {
        if (value == null) {
            return ValidationResult.success();
        }

        // 检查运算符是否支持
        if (!SUPPORTED_OPERATORS.contains(operator)) {
            return ValidationResult.error("INVALID_OPERATOR", 
                    String.format("日期类型不支持运算符: %s", operator));
        }

        // 如果已经是日期类型
        if (value instanceof Date) {
            return ValidationResult.success();
        }

        // 尝试解析日期字符串
        String strValue = value.toString().trim();
        if (parseDate(strValue) == null) {
            return ValidationResult.error("INVALID_DATE_FORMAT", 
                    String.format("无法解析日期: %s", value));
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

        // 如果已经是日期类型
        if (compareValue instanceof Date) {
            return ValidationResult.success();
        }

        String strValue = compareValue.toString().trim();

        // IN/NOT_IN 运算符支持多个日期
        if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
            String[] values = strValue.split(",");
            for (String val : values) {
                if (parseDate(val.trim()) == null) {
                    return ValidationResult.error("INVALID_DATE_IN_LIST", 
                            String.format("IN列表中包含无效日期: %s", val));
                }
            }
            return ValidationResult.success();
        }

        // 单个日期验证
        if (parseDate(strValue) == null) {
            return ValidationResult.error("INVALID_DATE_FORMAT", 
                    String.format("无效的日期格式: %s", compareValue));
        }

        return ValidationResult.success();
    }

    /**
     * 尝试解析日期字符串
     */
    private Date parseDate(String dateStr) {
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {
                // 尝试下一个格式
            }
        }
        return null;
    }
}
