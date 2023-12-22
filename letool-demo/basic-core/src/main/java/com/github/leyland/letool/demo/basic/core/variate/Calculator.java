package com.github.leyland.letool.demo.basic.core.variate;

/**
 * {@code Calculator} 类提供基本的算术操作。
 * 该类能够执行加法、减法、乘法和除法运算。
 * 还包括获取结果和清空计算器的方法。
 *
 * <p>示例用法：
 * <pre>
 * {@code
 * Calculator calculator = new Calculator();
 * calculator.add(5);
 * calculator.subtract(2);
 * int result = calculator.getResult();
 * }
 * </pre>
 *
 * @ClassName <h2>Calculator</h2>
 * @author leyland
 * @version 1.0
 * @since 2023-01-01
 */
public class Calculator {

    private int result;

    /**
     * 构造一个新的 {@code Calculator}，初始结果为 0。
     */
    public Calculator() {
        this.result = 0;
    }

    /**
     * 将一个数字添加到当前结果。
     *
     * @param num 要添加的数字。
     */
    public void add(int num) {
        result += num;
    }

    /**
     * 从当前结果中减去一个数字。
     *
     * @param num 要减去的数字。
     */
    public void subtract(int num) {
        result -= num;
    }

    /**
     * 将当前结果乘以一个数字。
     *
     * @param num 要乘以的数字。
     */
    public void multiply(int num) {
        result *= num;
    }

    /**
     * 将当前结果除以一个数字。
     *
     * @param num 要除以的数字（不能为零）。
     * @throws ArithmeticException 如果指定的数字为零。
     */
    public void divide(int num) {
        if (num == 0) {
            throw new ArithmeticException("不能除以零");
        }
        result /= num;
    }

    /**
     * 获取当前结果。
     *
     * @return 当前结果。
     */
    public int getResult() {
        return result;
    }

    /**
     * 清空计算器，将结果重置为 0。
     */
    public void clear() {
        result = 0;
    }
}
