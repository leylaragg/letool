package com.github.leyland.letool.rule.validator;

import com.github.leyland.letool.rule.entity.*;
import com.github.leyland.letool.rule.model.RuleExpressionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则表达式验证器
 * 对规则表达式进行完整性和有效性校验
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleExpressionValidator {

    private final List<FieldTypeValidator> validators;

    /**
     * 验证器缓存（按类型编码索引）
     */
    private final Map<String, FieldTypeValidator> validatorCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化验证器缓存
        for (FieldTypeValidator validator : validators) {
            validatorCache.put(validator.getSupportedType().toUpperCase(), validator);
            log.info("注册字段验证器: {} -> {}", validator.getSupportedType(), 
                    validator.getClass().getSimpleName());
        }
    }

    /**
     * 验证规则表达式上下文
     *
     * @param context 规则表达式上下文
     * @return 验证结果
     */
    public ValidationResult validate(RuleExpressionContext context) {
        if (context == null) {
            return ValidationResult.error("CONTEXT_NULL", "规则表达式上下文不能为空");
        }

        // 1. 基础校验
        ValidationResult baseResult = validateBase(context);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }

        // 2. 字段-运算符兼容性校验
        ValidationResult compatibilityResult = validateCompatibility(context);
        if (!compatibilityResult.isSuccess()) {
            return compatibilityResult;
        }

        // 3. 比较值格式校验
        ValidationResult compareValueResult = validateCompareValue(context);
        if (!compareValueResult.isSuccess()) {
            return compareValueResult;
        }

        return ValidationResult.success("规则表达式验证通过");
    }

    /**
     * 基础校验
     */
    private ValidationResult validateBase(RuleExpressionContext context) {
        // 检查表达式
        if (context.getExpression() == null) {
            return ValidationResult.error("EXPRESSION_NULL", "规则表达式不能为空");
        }

        RuleExpression expression = context.getExpression();

        // 检查规则ID
        if (expression.getRuleId() == null) {
            return ValidationResult.error("RULE_ID_NULL", "规则ID不能为空");
        }

        // 检查字段ID
        if (expression.getRuleFieldId() == null) {
            return ValidationResult.error("FIELD_ID_NULL", "字段ID不能为空");
        }

        // 检查运算符ID
        if (expression.getOperatorId() == null) {
            return ValidationResult.error("OPERATOR_ID_NULL", "运算符ID不能为空");
        }

        return ValidationResult.success();
    }

    /**
     * 字段-运算符兼容性校验
     */
    private ValidationResult validateCompatibility(RuleExpressionContext context) {
        RuleFieldType fieldType = context.getFieldType();
        RuleOperator operator = context.getOperator();

        if (fieldType == null || operator == null) {
            return ValidationResult.success(); // 允许空值，后续处理
        }

        // 获取字段类型验证器
        FieldTypeValidator validator = validatorCache.get(fieldType.getTypeCode().toUpperCase());
        if (validator == null) {
            log.warn("未找到字段类型验证器: {}", fieldType.getTypeCode());
            return ValidationResult.success(); // 允许未知类型
        }

        // 检查运算符是否支持该字段类型
        String supportedTypes = operator.getSupportedFieldTypes();
        if (supportedTypes != null && !supportedTypes.isEmpty()) {
            String[] types = supportedTypes.split(",");
            boolean supported = false;
            for (String type : types) {
                if (type.trim().equalsIgnoreCase(fieldType.getTypeCode())) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                return ValidationResult.error("OPERATOR_NOT_SUPPORTED", 
                        String.format("运算符 %s 不支持字段类型 %s", 
                                operator.getOperatorName(), fieldType.getTypeName()));
            }
        }

        return ValidationResult.success();
    }

    /**
     * 比较值格式校验
     */
    private ValidationResult validateCompareValue(RuleExpressionContext context) {
        RuleExpression expression = context.getExpression();
        RuleFieldType fieldType = context.getFieldType();
        RuleOperator operator = context.getOperator();

        if (expression == null || fieldType == null || operator == null) {
            return ValidationResult.success();
        }

        // 获取字段类型验证器
        FieldTypeValidator validator = validatorCache.get(fieldType.getTypeCode().toUpperCase());
        if (validator == null) {
            return ValidationResult.success();
        }

        // 检查是否需要比较值
        if (Boolean.FALSE.equals(operator.getRequiresValue())) {
            return ValidationResult.success();
        }

        // 验证比较值
        Object compareValue = expression.getCompareValue();
        if (compareValue == null) {
            return ValidationResult.error("COMPARE_VALUE_NULL", "比较值不能为空");
        }

        return validator.validateCompareValue(compareValue, operator.getOperatorCode());
    }

    /**
     * 获取字段验证器
     */
    public FieldTypeValidator getValidator(String fieldType) {
        return validatorCache.get(fieldType.toUpperCase());
    }
}
