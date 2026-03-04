package com.github.leyland.letool.rule.model;

import com.github.leyland.letool.rule.entity.*;
import lombok.Builder;
import lombok.Data;

/**
 * 规则表达式上下文
 * 封装规则执行所需的完整上下文信息
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
@Builder
public class RuleExpressionContext {

    /**
     * 规则表达式
     */
    private RuleExpression expression;

    /**
     * 规则字典（表名）
     */
    private RuleDict ruleDict;

    /**
     * 字段字典
     */
    private RuleFieldDict fieldDict;

    /**
     * 字段类型
     */
    private RuleFieldType fieldType;

    /**
     * 运算符
     */
    private RuleOperator operator;

    /**
     * 规则流（分类）
     */
    private RuleFlowDict ruleFlow;

    /**
     * 数据源数据
     */
    private Object data;

    /**
     * 从数据中获取字段值
     */
    public Object getFieldValue() {
        if (data == null || fieldDict == null) {
            return null;
        }

        if (data instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> mapData = (java.util.Map<String, Object>) data;
            return mapData.get(fieldDict.getDatabaseFieldCode());
        }

        // 使用反射获取字段值
        try {
            java.lang.reflect.Field field = data.getClass().getDeclaredField(fieldDict.getDatabaseFieldCode());
            field.setAccessible(true);
            return field.get(data);
        } catch (Exception e) {
            return null;
        }
    }
}
