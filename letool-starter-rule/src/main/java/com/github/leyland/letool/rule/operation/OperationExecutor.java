package com.github.leyland.letool.rule.operation;

/**
 * 运算执行器接口
 * 定义规则运算的标准接口
 *
 * @author leyland
 * @since 2026/02/14
 */
public interface OperationExecutor {

    /**
     * 执行运算
     *
     * @param request 运算请求
     * @return 运算结果
     */
    OperationResult execute(OperationRequest request);

    /**
     * 判断是否支持指定的运算符和类型
     *
     * @param operator  运算符编码
     * @param leftType  左操作数类型
     * @param rightType 右操作数类型
     * @return 是否支持
     */
    boolean supports(String operator, String leftType, String rightType);
}
