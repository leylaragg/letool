package com.github.leyland.letool.rule.operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 核心运算执行器
 * 实现基础的比较运算逻辑
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoreOperationExecutor implements OperationExecutor {

    private final TypeConverter typeConverter;

    /**
     * 支持的运算符集合
     */
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "LIKE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"
    );

    @Override
    public OperationResult execute(OperationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            Object left = request.getLeftOperand();
            Object right = request.getRightOperand();
            String operator = request.getOperator();

            // 处理空值情况
            if (left == null || (right == null && !"IS_NULL".equals(operator) && !"IS_NOT_NULL".equals(operator))) {
                return handleNullOperations(left, right, operator)
                        .executionTime(System.currentTimeMillis() - startTime)
                        .actualValue(left)
                        .compareValue(right)
                        .operator(operator);
            }

            // 确保类型兼容
            Object[] compatibleValues = typeConverter.ensureCompatibleTypes(
                    left, request.getLeftType(),
                    right, request.getRightType()
            );
            left = compatibleValues[0];
            right = compatibleValues[1];

            // 执行运算
            boolean result = evaluate(left, right, operator);

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("运算结果: {} {} {} = {}, 耗时: {}ms", left, operator, right, result, executionTime);

            return OperationResult.success(result)
                    .executionTime(executionTime)
                    .actualValue(left)
                    .compareValue(right)
                    .operator(operator);

        } catch (Exception e) {
            log.error("运算执行失败: {}", e.getMessage(), e);
            return OperationResult.error("运算失败: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public boolean supports(String operator, String leftType, String rightType) {
        return SUPPORTED_OPERATORS.contains(operator);
    }

    /**
     * 执行具体的运算逻辑
     */
    private boolean evaluate(Object left, Object right, String operator) {
        switch (operator) {
            case "EQ":
                return equals(left, right);
            case "NEQ":
                return !equals(left, right);
            case "GT":
                return compare(left, right) > 0;
            case "GTE":
                return compare(left, right) >= 0;
            case "LT":
                return compare(left, right) < 0;
            case "LTE":
                return compare(left, right) <= 0;
            case "LIKE":
                return like(left.toString(), right.toString());
            case "IN":
                return in(left, right.toString());
            case "NOT_IN":
                return !in(left, right.toString());
            default:
                throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
    }

    /**
     * 处理空值运算
     */
    private OperationResult handleNullOperations(Object left, Object right, String operator) {
        switch (operator) {
            case "IS_NULL":
                return OperationResult.success(left == null);
            case "IS_NOT_NULL":
                return OperationResult.success(left != null);
            default:
                // 其他操作符在有空值时通常返回false
                return OperationResult.success(false);
        }
    }

    /**
     * 相等比较
     */
    private boolean equals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    /**
     * 通用比较
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object left, Object right) {
        if (left instanceof Comparable && right instanceof Comparable) {
            return ((Comparable) left).compareTo(right);
        }
        // 默认转换为字符串比较
        return left.toString().compareTo(right.toString());
    }

    /**
     * LIKE运算
     * 支持SQL通配符：
     * - % 任意多个字符
     * - _ 单个字符
     */
    private boolean like(String text, String pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        // 将SQL LIKE模式转换为正则表达式
        String regex = pattern.replace("%", ".*").replace("_", ".");
        return text.matches("(?i)" + regex);
    }

    /**
     * IN运算
     */
    private boolean in(Object value, String valueList) {
        if (value == null || valueList == null) {
            return false;
        }

        String[] values = valueList.split(",");
        String searchValue = value.toString();

        for (String item : values) {
            if (searchValue.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }
}
