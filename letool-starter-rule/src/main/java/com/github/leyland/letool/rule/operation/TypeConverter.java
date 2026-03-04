package com.github.leyland.letool.rule.operation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 类型转换器
 * 处理不同数据类型之间的转换
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class TypeConverter {

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

    /**
     * 确保两个操作数类型兼容
     *
     * @param left     左操作数
     * @param leftType 左操作数类型
     * @param right    右操作数
     * @param rightType 右操作数类型
     * @return 兼容后的值数组 [left, right]
     */
    public Object[] ensureCompatibleTypes(Object left, String leftType, Object right, String rightType) {
        if (left == null || right == null) {
            return new Object[]{left, right};
        }

        // 统一转换为合适的类型进行比较
        String leftTypeUpper = leftType != null ? leftType.toUpperCase() : "STRING";
        String rightTypeUpper = rightType != null ? rightType.toUpperCase() : "STRING";

        // 日期类型处理
        if ("DATE".equals(leftTypeUpper) || "DATE".equals(rightTypeUpper)) {
            Date leftDate = convertToDate(left);
            Date rightDate = convertToDate(right);
            return new Object[]{leftDate, rightDate};
        }

        // 数字类型处理
        if ("NUMBER".equals(leftTypeUpper) || "NUMBER".equals(rightTypeUpper) ||
            "INTEGER".equals(leftTypeUpper) || "INTEGER".equals(rightTypeUpper) ||
            "LONG".equals(leftTypeUpper) || "LONG".equals(rightTypeUpper)) {
            BigDecimal leftNum = convertToNumber(left);
            BigDecimal rightNum = convertToNumber(right);
            return new Object[]{leftNum, rightNum};
        }

        // 布尔类型处理
        if ("BOOLEAN".equals(leftTypeUpper) || "BOOLEAN".equals(rightTypeUpper)) {
            Boolean leftBool = convertToBoolean(left);
            Boolean rightBool = convertToBoolean(right);
            return new Object[]{leftBool, rightBool};
        }

        // 默认字符串处理
        return new Object[]{left.toString(), right.toString()};
    }

    /**
     * 转换为日期类型
     */
    public Date convertToDate(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        if (value instanceof Long) {
            return new Date((Long) value);
        }

        String strValue = value.toString().trim();
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                return sdf.parse(strValue);
            } catch (ParseException ignored) {
                // 尝试下一个格式
            }
        }

        log.warn("无法解析日期: {}", strValue);
        return null;
    }

    /**
     * 转换为数字类型
     */
    public BigDecimal convertToNumber(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }

        try {
            String strValue = value.toString().trim();
            return new BigDecimal(strValue);
        } catch (NumberFormatException e) {
            log.warn("无法转换为数字: {}", value);
            return null;
        }
    }

    /**
     * 转换为布尔类型
     */
    public Boolean convertToBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        String strValue = value.toString().trim().toLowerCase();
        if ("true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue) || "y".equals(strValue)) {
            return true;
        }
        if ("false".equals(strValue) || "0".equals(strValue) || "no".equals(strValue) || "n".equals(strValue)) {
            return false;
        }

        log.warn("无法转换为布尔值: {}", value);
        return null;
    }

    /**
     * 转换为字符串
     */
    public String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * 根据类型转换值
     */
    public Object convertByType(Object value, String targetType) {
        if (value == null || targetType == null) {
            return value;
        }

        switch (targetType.toUpperCase()) {
            case "STRING":
                return convertToString(value);
            case "NUMBER":
            case "INTEGER":
            case "LONG":
                return convertToNumber(value);
            case "DATE":
            case "DATETIME":
                return convertToDate(value);
            case "BOOLEAN":
                return convertToBoolean(value);
            default:
                return value;
        }
    }
}
