package com.github.leyland.letool.rule.operation;

import lombok.Builder;
import lombok.Data;

/**
 * 运算请求对象
 * 封装运算所需的参数
 *
 * @author leyland
 * @since 2026/02/14
 */
@Data
@Builder
public class OperationRequest {

    /**
     * 左操作数（实际值）
     */
    private Object leftOperand;

    /**
     * 左操作数类型
     */
    private String leftType;

    /**
     * 右操作数（比较值）
     */
    private Object rightOperand;

    /**
     * 右操作数类型
     */
    private String rightType;

    /**
     * 运算符编码
     */
    private String operator;

    /**
     * 字段名称（用于日志）
     */
    private String fieldName;

    /**
     * 规则描述（用于日志）
     */
    private String ruleDescription;
}
