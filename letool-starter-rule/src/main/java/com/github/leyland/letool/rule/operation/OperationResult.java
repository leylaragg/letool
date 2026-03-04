package com.github.leyland.letool.rule.operation;

import lombok.Data;

/**
 * 运算结果对象
 * 封装运算执行结果
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
public class OperationResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 是否匹配
     */
    private boolean matched;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 执行时间（毫秒）
     */
    private long executionTime;

    /**
     * 实际值
     */
    private Object actualValue;

    /**
     * 比较值
     */
    private Object compareValue;

    /**
     * 运算符
     */
    private String operator;

    private OperationResult() {
    }

    /**
     * 创建成功结果
     */
    public static OperationResult success(boolean matched) {
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMatched(matched);
        result.setMessage(matched ? "规则匹配成功" : "规则不匹配");
        return result;
    }

    /**
     * 创建成功结果（带消息）
     */
    public static OperationResult success(String message) {
        OperationResult result = new OperationResult();
        result.setSuccess(true);
        result.setMatched(true);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static OperationResult error(String message) {
        OperationResult result = new OperationResult();
        result.setSuccess(false);
        result.setMatched(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 设置执行时间
     */
    public OperationResult executionTime(long time) {
        this.executionTime = time;
        return this;
    }

    /**
     * 设置实际值
     */
    public OperationResult actualValue(Object value) {
        this.actualValue = value;
        return this;
    }

    /**
     * 设置比较值
     */
    public OperationResult compareValue(Object value) {
        this.compareValue = value;
        return this;
    }

    /**
     * 设置运算符
     */
    public OperationResult operator(String operator) {
        this.operator = operator;
        return this;
    }
}
