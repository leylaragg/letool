package com.github.leyland.letool.rule.validator;

/**
 * 字段类型验证器接口
 * 定义字段值验证的标准接口
 *
 * @author leyland
 * @since 2026/02/14
 */
public interface FieldTypeValidator {

    /**
     * 获取支持的字段类型
     *
     * @return 字段类型编码
     */
    String getSupportedType();

    /**
     * 验证字段值
     *
     * @param value    字段值
     * @param operator 运算符
     * @return 验证结果
     */
    ValidationResult validate(Object value, String operator);

    /**
     * 验证比较值格式
     *
     * @param compareValue 比较值
     * @param operator     运算符
     * @return 验证结果
     */
    ValidationResult validateCompareValue(Object compareValue, String operator);
}
