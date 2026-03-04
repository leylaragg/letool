package com.github.leyland.letool.rule.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 规则验证结果
 * 单条数据的验证结果
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class RuleValidationResult {

    /**
     * 数据标识
     */
    private String dataId;

    /**
     * 数据名称
     */
    private String dataName;

    /**
     * 最终结果
     */
    private boolean passed;

    /**
     * 是否被排除
     */
    private boolean excluded;

    /**
     * 验证时间
     */
    private Date validationTime;

    /**
     * 执行时间（毫秒）
     */
    private long executionTime;

    /**
     * 规则组验证结果列表
     */
    private List<RuleGroupValidationResult> groupResults = new ArrayList<>();

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 原始数据
     */
    private Object originalData;

    /**
     * 规则组验证结果
     */
    @Data
    public static class RuleGroupValidationResult {
        /**
         * 规则组ID
         */
        private Long ruleGroupId;

        /**
         * 规则组名称
         */
        private String ruleGroupName;

        /**
         * 规则组类型（0-纳入，1-排除）
         */
        private Integer groupType;

        /**
         * 是否通过
         */
        private boolean passed;

        /**
         * 失败时是否停止
         */
        private Integer stopOnFailure;

        /**
         * 执行时间（毫秒）
         */
        private long executionTime;

        /**
         * 规则验证结果列表
         */
        private List<RuleExpressionResult> expressionResults = new ArrayList<>();
    }

    /**
     * 规则表达式验证结果
     */
    @Data
    public static class RuleExpressionResult {
        /**
         * 表达式ID
         */
        private Long expressionId;

        /**
         * 规则名称
         */
        private String ruleName;

        /**
         * 字段名称
         */
        private String fieldName;

        /**
         * 运算符
         */
        private String operatorName;

        /**
         * 运算符符号
         */
        private String operatorSymbol;

        /**
         * 比较值
         */
        private Object compareValue;

        /**
         * 实际值
         */
        private Object actualValue;

        /**
         * 是否匹配
         */
        private boolean matched;

        /**
         * 失败时是否停止
         */
        private Integer stopOnFailure;

        /**
         * 执行时间（毫秒）
         */
        private long executionTime;

        /**
         * 错误消息
         */
        private String errorMessage;
    }

    /**
     * 创建成功结果
     */
    public static RuleValidationResult success(String dataId) {
        RuleValidationResult result = new RuleValidationResult();
        result.setDataId(dataId);
        result.setPassed(true);
        result.setExcluded(false);
        result.setValidationTime(new Date());
        return result;
    }

    /**
     * 创建失败结果
     */
    public static RuleValidationResult failure(String dataId, String errorMessage) {
        RuleValidationResult result = new RuleValidationResult();
        result.setDataId(dataId);
        result.setPassed(false);
        result.setExcluded(false);
        result.setValidationTime(new Date());
        result.setErrorMessage(errorMessage);
        return result;
    }
}
